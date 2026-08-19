package service

import (
	"context"
	"crypto/rand"
	"crypto/sha256"
	"encoding/base64"
	"encoding/hex"
	"net/url"
	"strings"
	"time"

	"github.com/google/uuid"

	"link.mczihan/webdavbox-backend/internal/config"
	"link.mczihan/webdavbox-backend/internal/dto"
	"link.mczihan/webdavbox-backend/internal/model"
	"link.mczihan/webdavbox-backend/internal/pkg/response"
	"link.mczihan/webdavbox-backend/internal/repository"
)

type githubServerClient interface {
	ExchangeAndFetchServer(context.Context, string) (*GitHubUser, error)
}

// GithubOAuthService owns the browser-based GitHub flow. Browser redirects
// carry only a short-lived, one-time grant; session tokens are returned by
// GithubComplete over the authenticated app API.
type GithubOAuthService struct {
	auth   *AuthService
	github githubServerClient
	repo   *repository.OAuthTransactionRepo
	cfg    *config.GithubConfig
}

func NewGithubOAuthService(auth *AuthService, github githubServerClient, repo *repository.OAuthTransactionRepo, cfg *config.GithubConfig) *GithubOAuthService {
	return &GithubOAuthService{auth: auth, github: github, repo: repo, cfg: cfg}
}

func (s *GithubOAuthService) Start(challenge, appState string, now time.Time) (string, error) {
	if s.cfg == nil || s.repo == nil || (!s.cfg.Mock && s.cfg.ClientID == "") || s.cfg.RedirectURI == "" || s.cfg.AppRedirectURI == "" {
		return "", response.ErrGithubAuthFailed
	}
	if !validPKCEChallenge(challenge) || !validOpaque(appState) {
		return "", response.ErrGithubAuthFailed
	}
	state, err := randomOpaque(32)
	if err != nil {
		return "", err
	}
	tx := &model.OAuthTransaction{
		ID: uuid.NewString(), StateHash: hashOpaque(state), AppState: appState,
		CodeChallenge: challenge, Status: repository.OAuthPending,
		StateExpiresAt: now.Add(time.Duration(s.cfg.StateTTLSeconds) * time.Second),
		CreatedAt:      now, UpdatedAt: now,
	}
	if err := s.repo.Create(tx); err != nil {
		return "", err
	}
	query := url.Values{}
	clientID := s.cfg.ClientID
	if clientID == "" && s.cfg.Mock {
		clientID = "mock-client"
	}
	query.Set("client_id", clientID)
	query.Set("redirect_uri", s.cfg.RedirectURI)
	query.Set("state", state)
	return "https://github.com/login/oauth/authorize?" + query.Encode(), nil
}

func (s *GithubOAuthService) Callback(ctx context.Context, code, state string, now time.Time) (string, string, string, error) {
	if code == "" || state == "" {
		return "", "", "", response.ErrGithubAuthFailed
	}
	tx, err := s.repo.ClaimCallback(hashOpaque(state), now)
	if err != nil || tx == nil {
		return "", "", "", response.ErrGithubAuthFailed
	}
	user, err := s.github.ExchangeAndFetchServer(ctx, code)
	if err != nil {
		return "", "", "", response.ErrGithubAuthFailed
	}
	account, err := s.auth.ProvisionGithubUser(user)
	if err != nil {
		return "", "", "", err
	}
	completion, err := randomOpaque(32)
	if err != nil {
		return "", "", "", err
	}
	expires := now.Add(time.Duration(s.cfg.CompletionCodeTTLSeconds) * time.Second)
	if err := s.repo.MarkReady(tx.ID, account.ID, hashOpaque(completion), expires, now); err != nil {
		return "", "", "", err
	}
	return s.cfg.AppRedirectURI, tx.AppState, completion, nil
}

func (s *GithubOAuthService) Cancelled(state string, now time.Time) (string, string, bool) {
	if s.repo == nil || s.cfg == nil || state == "" {
		return "", "", false
	}
	appState, ok, err := s.repo.AppStateForState(hashOpaque(state), now)
	if err != nil || !ok {
		return "", "", false
	}
	return s.cfg.AppRedirectURI, appState, true
}

func (s *GithubOAuthService) Complete(ctx context.Context, code, verifier, deviceID string, now time.Time) (*dto.LoginResult, error) {
	if !validVerifier(verifier) || code == "" {
		return nil, response.ErrGithubAuthFailed
	}
	digest := sha256.Sum256([]byte(verifier))
	challenge := base64.RawURLEncoding.EncodeToString(digest[:])
	tx, err := s.repo.Consume(hashOpaque(code), challenge, now)
	if err != nil || tx == nil || tx.UserID == nil {
		return nil, response.ErrGithubAuthFailed
	}
	account, err := s.auth.users.GetByID(*tx.UserID)
	if err != nil || account == nil || account.Status != model.UserStatusActive {
		return nil, response.ErrGithubAuthFailed
	}
	tokens, err := s.auth.tokens.Issue(account.ID, string(account.Role), deviceID)
	if err != nil {
		return nil, err
	}
	s.auth.auditEvent(ctx, account.ID, "login_success", map[string]string{"method": "github"})
	return buildLoginResult(account, tokens, "GITHUB"), nil
}

func randomOpaque(size int) (string, error) {
	b := make([]byte, size)
	if _, err := rand.Read(b); err != nil {
		return "", err
	}
	return base64.RawURLEncoding.EncodeToString(b), nil
}

func hashOpaque(value string) string {
	sum := sha256.Sum256([]byte(value))
	return hex.EncodeToString(sum[:])
}

func validOpaque(value string) bool {
	return len(value) >= 22 && len(value) <= 128 && strings.IndexFunc(value, func(r rune) bool {
		return !strings.ContainsRune("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-._~", r)
	}) == -1
}

func validPKCEChallenge(value string) bool { return validOpaque(value) && len(value) >= 43 }

func validVerifier(value string) bool { return validOpaque(value) && len(value) >= 43 }
