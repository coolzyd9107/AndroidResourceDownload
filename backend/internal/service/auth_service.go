package service

import (
	"context"
	"log/slog"
	"strconv"
	"strings"
	"time"

	"github.com/google/uuid"

	"resdownload.com/backend/internal/dto"
	"resdownload.com/backend/internal/model"
	"resdownload.com/backend/internal/pkg/response"
	"resdownload.com/backend/internal/repository"
)

// AuthService orchestrates the login + refresh + logout flows.
type AuthService struct {
	users      *repository.UserRepo
	identities *repository.IdentityRepo
	adminGH    *repository.AdminGithubRepo
	tokens     *TokenService
	roles      *RoleService
	emails     *EmailService
	github     githubOAuthClient
	log        *slog.Logger
	audit      *repository.AuditLogRepo
}

type githubOAuthClient interface {
	ExchangeAndFetch(ctx context.Context, code, redirectURI, codeVerifier string) (*GitHubUser, error)
}

// NewAuthService constructs an AuthService.
func NewAuthService(
	users *repository.UserRepo,
	identities *repository.IdentityRepo,
	adminGH *repository.AdminGithubRepo,
	tokens *TokenService,
	roles *RoleService,
	emails *EmailService,
	github githubOAuthClient,
	audit *repository.AuditLogRepo,
	log *slog.Logger,
) *AuthService {
	return &AuthService{
		users:      users,
		identities: identities,
		adminGH:    adminGH,
		tokens:     tokens,
		roles:      roles,
		emails:     emails,
		github:     github,
		audit:      audit,
		log:        log,
	}
}

// SendEmailCode validates the email and triggers code generation.
func (s *AuthService) SendEmailCode(ctx context.Context, email string) (time.Duration, error) {
	if !s.roles.Allowed(email) {
		return 0, response.ErrEmailDomainNotAllowed
	}
	_, ttl, err := s.emails.Generate(ctx, email)
	if err != nil {
		return 0, err
	}
	s.auditEvent(ctx, "", "email_code_sent", map[string]string{"email": maskEmail(email)})
	return ttl, nil
}

// EmailLogin verifies the OTP and returns a fresh login result.
func (s *AuthService) EmailLogin(ctx context.Context, email, code, deviceID string) (*dto.LoginResult, error) {
	if !s.roles.Allowed(email) {
		return nil, response.ErrEmailDomainNotAllowed
	}
	if _, err := s.emails.Verify(email, code); err != nil {
		s.auditEvent(ctx, "", "login_failed", map[string]string{"method": "email", "email": maskEmail(email)})
		return nil, err
	}
	role, _ := s.roles.MapEmail(email)
	roleSource := string(model.RoleSourceEmailDomain)

	user, err := s.users.GetByEmail(strings.ToLower(strings.TrimSpace(email)))
	if err != nil {
		return nil, err
	}
	now := time.Now()
	if user == nil {
		user = &model.User{
			ID:         uuid.NewString(),
			Email:      ptrStr(strings.ToLower(strings.TrimSpace(email))),
			Role:       role,
			RoleSource: ptrStr(roleSource),
			Status:     model.UserStatusActive,
			CreatedAt:  now,
			UpdatedAt:  now,
		}
		if err := s.users.Create(user); err != nil {
			return nil, err
		}
	} else if user.Role != role || user.RoleSource == nil || *user.RoleSource != roleSource {
		user.Role = role
		user.RoleSource = ptrStr(roleSource)
		if err := s.users.Update(user); err != nil {
			return nil, err
		}
	}

	if err := s.ensureIdentity(user.ID, "email", strings.ToLower(strings.TrimSpace(email)), &email, nil); err != nil {
		return nil, err
	}

	tokens, err := s.tokens.Issue(user.ID, string(user.Role), deviceID)
	if err != nil {
		return nil, err
	}
	s.auditEvent(ctx, user.ID, "login_success", map[string]string{"method": "email"})
	return buildLoginResult(user, tokens, "EMAIL"), nil
}

// GithubLogin exchanges the OAuth code and signs the user in.
func (s *AuthService) GithubLogin(ctx context.Context, code, redirectURI, codeVerifier, deviceID string) (*dto.LoginResult, error) {
	user, err := s.github.ExchangeAndFetch(ctx, code, redirectURI, codeVerifier)
	if err != nil {
		s.auditEvent(ctx, "", "login_failed", map[string]string{"method": "github", "err": err.Error()})
		return nil, response.ErrGithubAuthFailed
	}

	existing, err := s.users.GetByGithubID(user.ID)
	if err != nil {
		return nil, err
	}
	now := time.Now()
	githubEmail := normalizedOptionalEmail(user.Email)
	userEmail, err := s.availableGithubUserEmail(user.ID, githubEmail)
	if err != nil {
		return nil, err
	}
	githubName := nonEmptyString(user.Name)
	if existing == nil {
		existing = &model.User{
			ID:          uuid.NewString(),
			GithubID:    &user.ID,
			GithubLogin: ptrStr(user.Login),
			Email:       userEmail,
			Name:        githubName,
			AvatarURL:   ptrOrEmpty(user.AvatarURL),
			Status:      model.UserStatusActive,
			CreatedAt:   now,
			UpdatedAt:   now,
		}
	} else {
		existing.GithubLogin = ptrStr(user.Login)
		existing.Email = userEmail
		existing.Name = githubName
		existing.AvatarURL = ptrOrEmpty(user.AvatarURL)
		existing.UpdatedAt = now
	}

	whitelist, err := s.adminGH.Get(user.ID)
	if err != nil {
		return nil, err
	}
	if whitelist != nil {
		existing.Role = model.RoleAdmin
		existing.RoleSource = ptrStr(string(model.RoleSourceGithubWhitelist))
	} else {
		existing.Role = model.RoleUser
		existing.RoleSource = ptrStr(string(model.RoleSourceGithubDefault))
	}

	if err := s.upsertUser(existing); err != nil {
		return nil, err
	}
	if err := s.ensureIdentity(existing.ID, "github", int64ToString(user.ID), githubEmail, ptrOrEmpty(user.Login)); err != nil {
		return nil, err
	}

	tokens, err := s.tokens.Issue(existing.ID, string(existing.Role), deviceID)
	if err != nil {
		return nil, err
	}
	s.auditEvent(ctx, existing.ID, "login_success", map[string]string{"method": "github"})
	return buildLoginResult(existing, tokens, "GITHUB"), nil
}

// ProvisionGithubUser creates or updates the account without issuing tokens.
// The server-owned OAuth callback uses this boundary before creating a grant.
func (s *AuthService) ProvisionGithubUser(user *GitHubUser) (*model.User, error) {
	if user == nil || user.ID <= 0 || strings.TrimSpace(user.Login) == "" {
		return nil, response.ErrGithubAuthFailed
	}
	existing, err := s.users.GetByGithubID(user.ID)
	if err != nil {
		return nil, err
	}
	now := time.Now()
	githubEmail := normalizedOptionalEmail(user.Email)
	userEmail, err := s.availableGithubUserEmail(user.ID, githubEmail)
	if err != nil {
		return nil, err
	}
	if existing == nil {
		existing = &model.User{ID: uuid.NewString(), GithubID: &user.ID, GithubLogin: ptrStr(user.Login), Email: userEmail, Name: nonEmptyString(user.Name), AvatarURL: ptrOrEmpty(user.AvatarURL), Status: model.UserStatusActive, CreatedAt: now, UpdatedAt: now}
	} else {
		existing.GithubLogin = ptrStr(user.Login)
		existing.Email = userEmail
		existing.Name = nonEmptyString(user.Name)
		existing.AvatarURL = ptrOrEmpty(user.AvatarURL)
		existing.UpdatedAt = now
	}
	whitelist, err := s.adminGH.Get(user.ID)
	if err != nil {
		return nil, err
	}
	if whitelist != nil {
		existing.Role = model.RoleAdmin
		existing.RoleSource = ptrStr(string(model.RoleSourceGithubWhitelist))
	} else {
		existing.Role = model.RoleUser
		existing.RoleSource = ptrStr(string(model.RoleSourceGithubDefault))
	}
	if err := s.upsertUser(existing); err != nil {
		return nil, err
	}
	if err := s.ensureIdentity(existing.ID, "github", int64ToString(user.ID), githubEmail, ptrOrEmpty(user.Login)); err != nil {
		return nil, err
	}
	if existing.Status != model.UserStatusActive {
		return nil, response.ErrUnauthorized
	}
	return existing, nil
}

// Me returns the public projection of the user.
func (s *AuthService) Me(userID string) (*dto.UserDTO, error) {
	u, err := s.users.GetByID(userID)
	if err != nil {
		return nil, err
	}
	if u == nil {
		return nil, response.ErrUnauthorized
	}
	loginType := "QQ"
	if u.GithubID != nil {
		loginType = "GITHUB"
	} else if u.Email != nil {
		loginType = "EMAIL"
	}
	return &dto.UserDTO{
		ID:        u.ID,
		Name:      u.Name,
		Email:     u.Email,
		Role:      string(u.Role),
		AvatarURL: u.AvatarURL,
		LoginType: loginType,
	}, nil
}

// Refresh validates a refresh token and issues a fresh pair.
func (s *AuthService) Refresh(refreshToken string) (*dto.RefreshResult, error) {
	record, err := s.tokens.VerifyRefresh(refreshToken)
	if err != nil {
		return nil, response.ErrTokenExpired
	}
	user, err := s.users.GetByID(record.UserID)
	if err != nil {
		return nil, err
	}
	if user == nil {
		return nil, response.ErrUnauthorized
	}
	if err := s.tokens.RevokeRefresh(refreshToken); err != nil {
		return nil, err
	}
	tokens, err := s.tokens.Issue(user.ID, string(user.Role), derefString(record.DeviceID))
	if err != nil {
		return nil, err
	}
	return &dto.RefreshResult{
		AccessToken:  tokens.AccessToken,
		RefreshToken: tokens.RefreshToken,
		ExpiresIn:    tokens.AccessExpiresIn,
	}, nil
}

// Logout revokes the supplied refresh token if present.
func (s *AuthService) Logout(ctx context.Context, userID, refreshToken string) error {
	if refreshToken != "" {
		if err := s.tokens.RevokeRefresh(refreshToken); err != nil {
			s.log.Warn("auth_service: revoke refresh failed", slog.String("err", err.Error()))
		}
	}
	s.auditEvent(ctx, userID, "logout", nil)
	return nil
}

func (s *AuthService) availableGithubUserEmail(githubID int64, email *string) (*string, error) {
	if email == nil {
		return nil, nil
	}
	owner, err := s.users.GetByEmail(*email)
	if err != nil {
		return nil, err
	}
	if owner != nil && (owner.GithubID == nil || *owner.GithubID != githubID) {
		return nil, nil
	}
	return cloneString(email), nil
}

func (s *AuthService) ensureIdentity(userID, provider, providerUserID string, email, login *string) error {
	identity, err := s.identities.GetByProvider(provider, providerUserID)
	if err != nil {
		return err
	}
	if identity != nil {
		identity.Email = cloneString(email)
		identity.ProviderLogin = cloneString(login)
		return s.identities.Update(identity)
	}
	return s.identities.Create(&model.AuthIdentity{
		ID:             uuid.NewString(),
		UserID:         userID,
		Provider:       provider,
		ProviderUserID: providerUserID,
		Email:          cloneString(email),
		ProviderLogin:  cloneString(login),
		CreatedAt:      time.Now(),
	})
}

func (s *AuthService) upsertUser(u *model.User) error {
	existing, err := s.users.GetByID(u.ID)
	if err != nil {
		return err
	}
	if existing == nil {
		if err := s.users.Create(u); err != nil {
			s.log.Error("auth_service: create user failed",
				slog.String("user_id", u.ID),
				slog.Any("github_id", u.GithubID),
				slog.String("err", err.Error()),
			)
			return err
		}
		return nil
	}
	return s.users.Update(u)
}

func (s *AuthService) auditEvent(ctx context.Context, userID, action string, extras map[string]string) {
	rec := &model.AuditLog{
		ID:        uuid.NewString(),
		UserID:    nullableString(userID),
		Action:    action,
		CreatedAt: time.Now(),
	}
	if v := ctx.Value(remoteIPKey{}); v != nil {
		if s, ok := v.(string); ok {
			rec.IP = nullableString(s)
		}
	}
	if v := ctx.Value(userAgentKey{}); v != nil {
		if s, ok := v.(string); ok {
			rec.UserAgent = nullableString(s)
		}
	}
	if err := s.audit.Create(rec); err != nil {
		s.log.Warn("auth_service: audit log failed", slog.String("action", action), slog.String("err", err.Error()))
	}
}

// remoteIPKey / userAgentKey live in this package so handlers can attach
// request metadata to the context before calling audit-relevant methods.
type remoteIPKey struct{}
type userAgentKey struct{}

// ContextWithRequestMeta returns a new context carrying ip / user-agent so
// audit logs can be written without handlers passing them around.
func ContextWithRequestMeta(ctx context.Context, ip, userAgent string) context.Context {
	ctx = context.WithValue(ctx, remoteIPKey{}, ip)
	ctx = context.WithValue(ctx, userAgentKey{}, userAgent)
	return ctx
}

func buildLoginResult(u *model.User, tokens *IssueResult, loginType string) *dto.LoginResult {
	return &dto.LoginResult{
		AccessToken:  tokens.AccessToken,
		RefreshToken: tokens.RefreshToken,
		ExpiresIn:    tokens.AccessExpiresIn,
		User: dto.UserDTO{
			ID:        u.ID,
			Name:      u.Name,
			Email:     u.Email,
			Role:      string(u.Role),
			AvatarURL: u.AvatarURL,
			LoginType: loginType,
		},
	}
}

func ptrStr(s string) *string { return &s }

func ptrOrEmpty(s string) *string {
	if s == "" {
		return nil
	}
	return &s
}

func nonEmptyString(value *string) *string {
	if value == nil || strings.TrimSpace(*value) == "" {
		return nil
	}
	return ptrStr(*value)
}

func normalizedOptionalEmail(value *string) *string {
	if value == nil {
		return nil
	}
	email := strings.ToLower(strings.TrimSpace(*value))
	if email == "" {
		return nil
	}
	return &email
}

func cloneString(value *string) *string {
	if value == nil {
		return nil
	}
	return ptrStr(*value)
}

func derefString(p *string) string {
	if p == nil {
		return ""
	}
	return *p
}

func int64ToString(v int64) string { return strconv.FormatInt(v, 10) }

func maskEmail(email string) string {
	at := strings.LastIndex(email, "@")
	if at <= 0 {
		return "***"
	}
	local := email[:at]
	if len(local) <= 2 {
		return local[:1] + "***" + email[at:]
	}
	return local[:2] + "***" + email[at:]
}
