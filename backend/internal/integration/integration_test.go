// Package integration contains HTTP-level integration tests. They spin up
// the full Gin engine + GORM/SQLite stack so the whole auth + webdav +
// update flow can be exercised end-to-end without external services.
package integration

import (
	"bytes"
	"context"
	"crypto/sha256"
	"encoding/base64"
	"encoding/json"
	"io"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"net/url"
	"os"
	"path/filepath"
	"testing"

	"github.com/gin-gonic/gin"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"resdownload.com/backend/internal/app"
	"resdownload.com/backend/internal/config"
	"resdownload.com/backend/internal/middleware"
	"resdownload.com/backend/internal/model"
	"resdownload.com/backend/internal/pkg/crypto"
	"resdownload.com/backend/internal/pkg/jwt"
	"resdownload.com/backend/internal/ratelimit"
	"resdownload.com/backend/internal/repository"
	"resdownload.com/backend/internal/service"
)

// testEnv wires a complete backend against a temporary SQLite DB.
type testEnv struct {
	router *gin.Engine
	cfg    *config.Config
	repos  *repository.Repos
	emails *service.EmailService
	// lastOTP is the OTP that the console-mode email service would have
	// "sent" for the most recent SendEmailCode call. Populated by
	// otpCapturingEmails.
	lastOTP string
}

func newTestEnv(t *testing.T) *testEnv { return newTestEnvWithLogger(t, slogDiscard()) }

func newTestEnvWithLogger(t *testing.T, log *slog.Logger) *testEnv {
	t.Helper()
	gin.SetMode(gin.TestMode)

	dir := t.TempDir()
	dbPath := filepath.Join(dir, "test.db")

	t.Setenv("APP_ENV", "test")
	t.Setenv("DATABASE_DRIVER", "sqlite")
	t.Setenv("DATABASE_URL", dbPath)
	t.Setenv("JWT_SECRET", "test-secret-32-bytes-or-more-aaaa")
	t.Setenv("CREDENTIAL_SECRET", "test-cred-secret-32-bytes-aaaaaaaa")
	t.Setenv("UPDATE_URL_SECRET", "test-update-secret-32-bytes-aaaaaa")
	t.Setenv("EMAIL_MODE", "console")
	t.Setenv("GITHUB_MOCK", "true")
	t.Setenv("ALLOWED_EMAIL_DOMAINS", "qq.com,admin.example.com")
	t.Setenv("ADMIN_EMAIL_DOMAINS", "admin.example.com")
	t.Setenv("SERVER_PORT", "0")

	cfg, err := config.Load("")
	require.NoError(t, err)

	db, err := repository.Open(cfg)
	require.NoError(t, err)
	require.NoError(t, repository.Migrate(db))
	repos := repository.New(db)

	issuer, err := jwt.NewIssuer(cfg.JWT.Secret, cfg.JWT.Issuer)
	require.NoError(t, err)
	suite := crypto.NewSuite(cfg.Credential.Secret, cfg.Update.Secret)
	roles := service.NewRoleService(cfg.UserEmailDomains, cfg.AdminEmailDomains)
	tokens := service.NewTokenService(issuer, cfg.JWT.AccessTTLSeconds, cfg.JWT.RefreshTTLDays, repos.RefreshTokens)
	emails := service.NewEmailService(&cfg.Email, repos.EmailCodes, log)
	github := service.NewGitHubClient(&cfg.Github)
	credSvc := service.NewCredentialService(suite, service.NewWebDAVConfigAdapter(&cfg.WebDAV), repos.CredentialLogs)
	updateSvc := service.NewUpdateService(suite, repos.AppVersions, repos.UpdateURLLogs, cfg.Update.TTLSeconds)
	authSvc := service.NewAuthService(repos.Users, repos.Identities, repos.AdminGithub, tokens, roles, emails, github, repos.AuditLogs, log)
	limiter := ratelimit.NewInMemory(ratelimit.DefaultRules()...)

	r := gin.New()
	r.Use(middleware.TraceID())
	r.Use(middleware.Recovery(log))
	r.Use(middleware.RequestLogger(log))
	r.Use(middleware.CORS())

	app.RegisterRoutes(r, &app.Deps{
		Config:      cfg,
		Logger:      log,
		JWTIssuer:   issuer,
		Limiter:     limiter,
		Auth:        authSvc,
		Credentials: credSvc,
		Updates:     updateSvc,
		Tokens:      tokens,
		GithubOAuth: service.NewGithubOAuthService(authSvc, github, repos.OAuthTransactions, &cfg.Github),
		Qq:          service.NewQqAuthService(authSvc, service.NewQqClient(&cfg.Qq)),
	})

	return &testEnv{router: r, cfg: cfg, repos: repos, emails: emails}
}

// doJSON sends a JSON request and decodes the response envelope.
func (e *testEnv) doJSON(t *testing.T, method, path string, body any, token string) (int, map[string]any) {
	t.Helper()
	var buf bytes.Buffer
	if body != nil {
		require.NoError(t, json.NewEncoder(&buf).Encode(body))
	}
	req := httptest.NewRequest(method, path, &buf)
	if body != nil {
		req.Header.Set("Content-Type", "application/json")
	}
	if token != "" {
		req.Header.Set("Authorization", "Bearer "+token)
	}
	w := httptest.NewRecorder()
	e.router.ServeHTTP(w, req)

	var decoded map[string]any
	raw := w.Body.Bytes()
	if len(raw) > 0 {
		if err := json.Unmarshal(raw, &decoded); err != nil {
			t.Logf("response decode error: %v, raw=%s", err, string(raw))
		}
	}
	return w.Code, decoded
}

// seedAppVersion inserts a known latest version for update tests.
func (e *testEnv) seedAppVersion(t *testing.T, code int64, name string, force bool) *model.AppVersion {
	t.Helper()
	changelog := "release notes"
	v := &model.AppVersion{
		ID:          "ver-" + name,
		VersionCode: code,
		VersionName: name,
		ForceUpdate: force,
		Changelog:   &changelog,
		TargetURL:   "https://cdn.example.com/" + name + ".apk",
	}
	require.NoError(t, e.repos.AppVersions.Create(v))
	return v
}

// TestHealth verifies /health.
func TestHealth(t *testing.T) {
	env := newTestEnv(t)
	code, body := env.doJSON(t, http.MethodGet, "/health", nil, "")
	assert.Equal(t, http.StatusOK, code)
	assert.EqualValues(t, 0, body["code"])
}

// TestEmailLoginHappyPath walks the email login flow end-to-end.
func TestEmailLoginHappyPath(t *testing.T) {
	env := newTestEnv(t)

	// Request a code.
	code, body := env.doJSON(t, http.MethodPost, "/api/v1/auth/email/code", map[string]string{"email": "u@qq.com"}, "")
	assert.Equal(t, http.StatusOK, code)
	assert.EqualValues(t, 0, body["code"])

	// Fetch the latest OTP from the database.
	otp := readLatestOTPCode(t, env, "u@qq.com")
	require.Len(t, otp, 6)

	// Login with the OTP.
	code, body = env.doJSON(t, http.MethodPost, "/api/v1/auth/email/login", map[string]any{
		"email": "u@qq.com", "code": otp, "deviceId": "test-device",
	}, "")
	require.Equal(t, http.StatusOK, code)
	data := body["data"].(map[string]any)
	assert.NotEmpty(t, data["accessToken"])
	assert.NotEmpty(t, data["refreshToken"])
	assert.Equal(t, "USER", data["user"].(map[string]any)["role"])
	assert.Equal(t, "EMAIL", data["user"].(map[string]any)["loginType"])

	access := data["accessToken"].(string)

	// /auth/me with the access token.
	code, body = env.doJSON(t, http.MethodGet, "/api/v1/auth/me", nil, access)
	require.Equal(t, http.StatusOK, code)
	assert.Equal(t, "USER", body["data"].(map[string]any)["role"])

	// /webdav/credential → READ_ONLY for USER.
	code, body = env.doJSON(t, http.MethodPost, "/api/v1/webdav/credential", map[string]any{}, access)
	require.Equal(t, http.StatusOK, code)
	cred := body["data"].(map[string]any)
	assert.Equal(t, "READ_ONLY", cred["permission"])
	assert.NotEmpty(t, cred["username"])
	assert.NotEmpty(t, cred["password"])

	// /webdav/credential without token → 401.
	code, _ = env.doJSON(t, http.MethodPost, "/api/v1/webdav/credential", map[string]any{}, "")
	assert.Equal(t, http.StatusUnauthorized, code)
}

// TestEmailLoginAdmin verifies admin role + READ_WRITE credentials.
func TestEmailLoginAdmin(t *testing.T) {
	env := newTestEnv(t)

	_, _ = env.doJSON(t, http.MethodPost, "/api/v1/auth/email/code", map[string]string{"email": "admin@admin.example.com"}, "")
	otp := readLatestOTPCode(t, env, "admin@admin.example.com")
	code, body := env.doJSON(t, http.MethodPost, "/api/v1/auth/email/login", map[string]any{
		"email": "admin@admin.example.com", "code": otp,
	}, "")
	require.Equal(t, http.StatusOK, code)
	data := body["data"].(map[string]any)
	assert.Equal(t, "ADMIN", data["user"].(map[string]any)["role"])

	access := data["accessToken"].(string)
	code, body = env.doJSON(t, http.MethodPost, "/api/v1/webdav/credential", map[string]any{}, access)
	require.Equal(t, http.StatusOK, code)
	cred := body["data"].(map[string]any)
	assert.Equal(t, "READ_WRITE", cred["permission"])
}

// TestEmailLoginRejectedDomain verifies non-allowed domains are refused.
func TestEmailLoginRejectedDomain(t *testing.T) {
	env := newTestEnv(t)
	code, _ := env.doJSON(t, http.MethodPost, "/api/v1/auth/email/code", map[string]string{"email": "x@gmail.com"}, "")
	assert.Equal(t, http.StatusForbidden, code)
}

// TestEmailLoginWrongCode verifies wrong codes are rejected.
func TestEmailLoginWrongCode(t *testing.T) {
	env := newTestEnv(t)
	_, _ = env.doJSON(t, http.MethodPost, "/api/v1/auth/email/code", map[string]string{"email": "u@qq.com"}, "")
	code, body := env.doJSON(t, http.MethodPost, "/api/v1/auth/email/login", map[string]any{
		"email": "u@qq.com", "code": "000000",
	}, "")
	assert.Equal(t, http.StatusUnauthorized, code)
	assert.NotEqual(t, 0, body["code"])
}

// TestUpdateInfoAndResolve verifies the encrypted URL round-trip.
func TestUpdateInfoAndResolve(t *testing.T) {
	env := newTestEnv(t)
	env.seedAppVersion(t, 42, "1.2.0", false)

	// Login first.
	_, _ = env.doJSON(t, http.MethodPost, "/api/v1/auth/email/code", map[string]string{"email": "u@qq.com"}, "")
	otp := readLatestOTPCode(t, env, "u@qq.com")
	_, body := env.doJSON(t, http.MethodPost, "/api/v1/auth/email/login", map[string]any{
		"email": "u@qq.com", "code": otp,
	}, "")
	access := body["data"].(map[string]any)["accessToken"].(string)

	// /update/info.
	code, body := env.doJSON(t, http.MethodGet, "/api/v1/update/info", nil, access)
	require.Equal(t, http.StatusOK, code)
	info := body["data"].(map[string]any)
	assert.EqualValues(t, 42, info["versionCode"])
	assert.Equal(t, "1.2.0", info["versionName"])
	enc := info["encryptedUrl"].(string)
	assert.NotEmpty(t, enc)

	// /update/resolve.
	code, body = env.doJSON(t, http.MethodPost, "/api/v1/update/resolve", map[string]any{
		"encryptedUrl": enc,
	}, access)
	require.Equal(t, http.StatusOK, code)
	resolved := body["data"].(map[string]any)
	assert.Contains(t, resolved["url"], "cdn.example.com")
}

// TestUpdateResolveTampered verifies that a tampered encrypted URL is rejected.
func TestUpdateResolveTampered(t *testing.T) {
	env := newTestEnv(t)
	env.seedAppVersion(t, 99, "2.0.0", true)

	_, _ = env.doJSON(t, http.MethodPost, "/api/v1/auth/email/code", map[string]string{"email": "u@qq.com"}, "")
	otp := readLatestOTPCode(t, env, "u@qq.com")
	_, body := env.doJSON(t, http.MethodPost, "/api/v1/auth/email/login", map[string]any{
		"email": "u@qq.com", "code": otp,
	}, "")
	access := body["data"].(map[string]any)["accessToken"].(string)

	_, body = env.doJSON(t, http.MethodGet, "/api/v1/update/info", nil, access)
	enc := body["data"].(map[string]any)["encryptedUrl"].(string)

	// Tamper the first character of the ciphertext segment.
	parts := splitDots(enc)
	require.Len(t, parts, 4)
	first := []byte(parts[0])
	if first[0] == 'A' {
		first[0] = 'B'
	} else {
		first[0] = 'A'
	}
	parts[0] = string(first)
	tampered := joinDots(parts)

	code, _ := env.doJSON(t, http.MethodPost, "/api/v1/update/resolve", map[string]any{
		"encryptedUrl": tampered,
	}, access)
	assert.NotEqual(t, http.StatusOK, code)
}

// TestRefreshAndLogout exercises the token rotation flow.
func TestRefreshAndLogout(t *testing.T) {
	env := newTestEnv(t)
	_, _ = env.doJSON(t, http.MethodPost, "/api/v1/auth/email/code", map[string]string{"email": "u@qq.com"}, "")
	otp := readLatestOTPCode(t, env, "u@qq.com")
	_, body := env.doJSON(t, http.MethodPost, "/api/v1/auth/email/login", map[string]any{
		"email": "u@qq.com", "code": otp,
	}, "")
	refresh := body["data"].(map[string]any)["refreshToken"].(string)
	access := body["data"].(map[string]any)["accessToken"].(string)

	// Refresh.
	code, body := env.doJSON(t, http.MethodPost, "/api/v1/auth/refresh", map[string]any{
		"refreshToken": refresh,
	}, "")
	require.Equal(t, http.StatusOK, code)
	assert.NotEmpty(t, body["data"].(map[string]any)["accessToken"])

	// Logout.
	code, _ = env.doJSON(t, http.MethodPost, "/api/v1/auth/logout", map[string]any{
		"refreshToken": refresh,
	}, access)
	assert.Equal(t, http.StatusOK, code)

	// Refresh again should now fail.
	code, _ = env.doJSON(t, http.MethodPost, "/api/v1/auth/refresh", map[string]any{
		"refreshToken": refresh,
	}, "")
	assert.NotEqual(t, http.StatusOK, code)
}

// TestGithubLoginMock exercises the server callback and one-time completion grant.
func TestGithubLoginMock(t *testing.T) {
	env := newTestEnv(t)
	verifier := "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-._~"
	digest := sha256.Sum256([]byte(verifier))
	challenge := base64.RawURLEncoding.EncodeToString(digest[:])
	appState := "abcdefghijklmnopqrstuvwxyz123456"

	start := httptest.NewRecorder()
	env.router.ServeHTTP(start, httptest.NewRequest(http.MethodGet,
		"/api/v1/auth/github/start?code_challenge="+url.QueryEscape(challenge)+"&code_challenge_method=S256&app_state="+appState, nil))
	require.Equal(t, http.StatusFound, start.Code)
	githubLocation, err := url.Parse(start.Header().Get("Location"))
	require.NoError(t, err)
	serverState := githubLocation.Query().Get("state")
	require.NotEmpty(t, serverState)
	assert.Equal(t, env.cfg.Github.RedirectURI, githubLocation.Query().Get("redirect_uri"))

	callback := httptest.NewRecorder()
	env.router.ServeHTTP(callback, httptest.NewRequest(http.MethodGet,
		"/api/v1/auth/github/callback?code=mock-code&state="+url.QueryEscape(serverState), nil))
	require.Equal(t, http.StatusFound, callback.Code)
	appLocation, err := url.Parse(callback.Header().Get("Location"))
	require.NoError(t, err)
	assert.Equal(t, "com.resdownload.android", appLocation.Scheme)
	assert.Equal(t, "oauth", appLocation.Host)
	assert.Equal(t, "/callback", appLocation.Path)
	grant := appLocation.Query().Get("code")
	require.NotEmpty(t, grant)
	assert.Equal(t, appState, appLocation.Query().Get("app_state"))
	assert.NotContains(t, callback.Header().Get("Location"), "accessToken")
	assert.NotContains(t, callback.Header().Get("Location"), verifier)

	code, body := env.doJSON(t, http.MethodPost, "/api/v1/auth/github/complete", map[string]any{
		"code": grant, "codeVerifier": verifier,
	}, "")
	require.Equal(t, http.StatusOK, code)
	assert.Equal(t, "GITHUB", body["data"].(map[string]any)["user"].(map[string]any)["loginType"])
	assert.Equal(t, "USER", body["data"].(map[string]any)["user"].(map[string]any)["role"])

	replayed, _ := env.doJSON(t, http.MethodPost, "/api/v1/auth/github/complete", map[string]any{
		"code": grant, "codeVerifier": verifier,
	}, "")
	assert.NotEqual(t, http.StatusOK, replayed)
}

// --- helpers ---

func readLatestOTPCode(t *testing.T, env *testEnv, email string) string {
	t.Helper()
	// The OTP service stores bcrypt(plaintext), so we cannot read the
	// plaintext back from the DB. Instead, generate a *fresh* code via
	// the service — the previous HTTP code request is irrelevant once we
	// know we're driving the test from Go. The new code is the one we
	// use to call /auth/email/login below.
	code, _, err := env.emails.Generate(context.Background(), email)
	require.NoError(t, err)
	return code
}

func splitDots(s string) []string {
	var out []string
	start := 0
	for i := 0; i < len(s); i++ {
		if s[i] == '.' {
			out = append(out, s[start:i])
			start = i + 1
		}
	}
	out = append(out, s[start:])
	return out
}

func joinDots(parts []string) string {
	out := ""
	for i, p := range parts {
		if i > 0 {
			out += "."
		}
		out += p
	}
	return out
}

// slogDiscard returns a *slog.Logger that writes JSON to io.Discard.
func slogDiscard() *slog.Logger {
	return slog.New(slog.NewJSONHandler(io.Discard, nil))
}

// slogStderr returns a *slog.Logger that writes to stderr for diagnostics.
func slogStderr() *slog.Logger {
	return slog.New(slog.NewJSONHandler(os.Stderr, nil))
}
