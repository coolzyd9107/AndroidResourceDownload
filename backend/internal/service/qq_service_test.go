package service

import (
	"context"
	"io"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"path/filepath"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"link.mczihan/webdavbox-backend/internal/config"
	"link.mczihan/webdavbox-backend/internal/model"
	"link.mczihan/webdavbox-backend/internal/pkg/jwt"
	"link.mczihan/webdavbox-backend/internal/pkg/response"
	"link.mczihan/webdavbox-backend/internal/repository"
)

const (
	testQqAppID  = "100123456"
	testQqOpenID = "0123456789ABCDEF0123456789ABCDEF"
)

type qqAuthStub struct {
	verified *QqVerifiedUser
	err      error
}

func (s *qqAuthStub) Authenticate(_ context.Context, _, _ string) (*QqVerifiedUser, error) {
	return s.verified, s.err
}

func newQqAuthTestService(t *testing.T, qq qqAuthClient) (*AuthService, *repository.Repos) {
	t.Helper()
	cfg := &config.Config{
		Database: config.DatabaseConfig{
			Driver:             "sqlite",
			URL:                filepath.Join(t.TempDir(), "qq-auth.db"),
			MaxOpenConns:       1,
			MaxIdleConns:       1,
			ConnMaxLifetimeMin: 1,
		},
	}
	db, err := repository.Open(cfg)
	require.NoError(t, err)
	require.NoError(t, repository.Migrate(db))
	repos := repository.New(db)

	issuer, err := jwt.NewIssuer("qq-auth-test-secret-32-bytes-aaaa", "qq-auth-test")
	require.NoError(t, err)
	tokens := NewTokenService(issuer, 3600, 30, repos.RefreshTokens)
	log := slog.New(slog.NewTextHandler(io.Discard, nil))
	auth := NewAuthService(
		repos.Users,
		repos.Identities,
		repos.AdminGithub,
		tokens,
		NewRoleService(nil, nil),
		nil,
		&githubOAuthStub{},
		repos.AuditLogs,
		log,
	)
	return auth, repos
}

func TestQqClientAuthenticateVerifiesTokenAndOpenID(t *testing.T) {
	var meQuery, infoQuery string
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		switch r.URL.Path {
		case "/oauth2.0/me":
			meQuery = r.URL.RawQuery
			w.Header().Set("Content-Type", "application/javascript")
			_, _ = w.Write([]byte("callback( {\"client_id\":\"" + testQqAppID + "\",\"openid\":\"" + testQqOpenID + "\"} );"))
		case "/user/get_user_info":
			infoQuery = r.URL.RawQuery
			_, _ = w.Write([]byte(`{"ret":0,"msg":"","nickname":" 小明 ","figureurl_qq_1":"http://qzapp.qlogo.cn/40","figureurl_qq_2":"http://qzapp.qlogo.cn/100"}`))
		default:
			http.NotFound(w, r)
		}
	}))
	defer srv.Close()

	client := NewQqClient(&config.QqConfig{AppID: testQqAppID, MeURL: srv.URL + "/oauth2.0/me", UserInfoURL: srv.URL + "/user/get_user_info"})
	verified, err := client.Authenticate(context.Background(), "provider-token", testQqOpenID)
	require.NoError(t, err)
	assert.Equal(t, testQqOpenID, verified.OpenID)
	require.NotNil(t, verified.Profile)
	assert.Equal(t, "小明", verified.Profile.Nickname)
	assert.Equal(t, "http://qzapp.qlogo.cn/100", verified.Profile.AvatarURL)
	assert.Contains(t, meQuery, "access_token=provider-token")
	assert.Contains(t, infoQuery, "oauth_consumer_key="+testQqAppID)
}

func TestQqClientAuthenticateRejectsOpenIDMismatch(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		_, _ = w.Write([]byte(`callback( {"client_id":"` + testQqAppID + `","openid":"FEDCBA9876543210FEDCBA9876543210"} );`))
	}))
	defer srv.Close()

	client := NewQqClient(&config.QqConfig{AppID: testQqAppID, MeURL: srv.URL + "/me"})
	_, err := client.Authenticate(context.Background(), "provider-token", testQqOpenID)
	require.ErrorIs(t, err, ErrQqAuthFailed)
}

func TestQqClientAuthenticateRejectsForeignAppIDAndGarbage(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Query().Get("access_token") == "bad-token" {
			_, _ = w.Write([]byte("<html>invalid token</html>"))
			return
		}
		_, _ = w.Write([]byte(`callback( {"client_id":"999999999","openid":"` + testQqOpenID + `"} );`))
	}))
	defer srv.Close()

	client := NewQqClient(&config.QqConfig{AppID: testQqAppID, MeURL: srv.URL + "/me"})
	_, err := client.Authenticate(context.Background(), "good-token", testQqOpenID)
	require.ErrorIs(t, err, ErrQqAuthFailed)

	_, err = client.Authenticate(context.Background(), "bad-token", testQqOpenID)
	require.ErrorIs(t, err, ErrQqAuthFailed)
}

func TestQqLoginProvisionsAndReusesAccount(t *testing.T) {
	stub := &qqAuthStub{verified: &QqVerifiedUser{
		OpenID:  testQqOpenID,
		Profile: &QqProfile{Nickname: "小明", AvatarURL: "https://thirdqq.qlogo.cn/100"},
	}}
	auth, repos := newQqAuthTestService(t, stub)
	qq := NewQqAuthService(auth, stub)

	first, err := qq.Login(context.Background(), "token-a", testQqOpenID, "device-1")
	require.NoError(t, err)
	assert.Equal(t, string(model.RoleUser), first.User.Role)
	assert.Equal(t, "QQ", first.User.LoginType)
	require.NotNil(t, first.User.Name)
	assert.Equal(t, "小明", *first.User.Name)

	stub.verified.Profile.Nickname = "新名字"
	second, err := qq.Login(context.Background(), "token-b", testQqOpenID, "device-2")
	require.NoError(t, err)
	assert.Equal(t, first.User.ID, second.User.ID)
	require.NotNil(t, second.User.Name)
	assert.Equal(t, "新名字", *second.User.Name)

	stored, err := repos.Identities.GetByProvider(qqIdentityProvider, testQqOpenID)
	require.NoError(t, err)
	require.NotNil(t, stored)
	assert.Equal(t, first.User.ID, stored.UserID)
}

func TestQqLoginRejectsBlankInputAndFailedVerification(t *testing.T) {
	stub := &qqAuthStub{err: ErrQqAuthFailed}
	auth, _ := newQqAuthTestService(t, stub)
	qq := NewQqAuthService(auth, stub)

	_, err := qq.Login(context.Background(), "", testQqOpenID, "")
	assert.ErrorIs(t, err, response.ErrQqAuthFailed)

	_, err = qq.Login(context.Background(), "token", " ", "")
	assert.ErrorIs(t, err, response.ErrQqAuthFailed)

	_, err = qq.Login(context.Background(), "token", testQqOpenID, "device")
	assert.ErrorIs(t, err, response.ErrQqAuthFailed)
}
