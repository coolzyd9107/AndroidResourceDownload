package service

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"strings"
	"time"

	"link.mczihan/webdavbox-backend/internal/config"
)

// GitHubUser is the subset of https://api.github.com/user the backend uses.
type GitHubUser struct {
	ID        int64   `json:"id"`
	Login     string  `json:"login"`
	Name      *string `json:"name"`
	Email     *string `json:"email"`
	AvatarURL string  `json:"avatar_url"`
}

// GitHubClient speaks to the GitHub OAuth + REST endpoints. The zero value
// is not usable; call NewGitHubClient.
type GitHubClient struct {
	cfg        *config.GithubConfig
	httpClient *http.Client
	tokenURL   string
	userURL    string
}

// NewGitHubClient builds a client with the configured timeouts.
func NewGitHubClient(cfg *config.GithubConfig) *GitHubClient {
	return &GitHubClient{
		cfg:        cfg,
		httpClient: &http.Client{Timeout: 10 * time.Second},
		tokenURL:   "https://github.com/login/oauth/access_token",
		userURL:    "https://api.github.com/user",
	}
}

// ErrGithubAuthFailed indicates the code exchange or user lookup failed.
var ErrGithubAuthFailed = errors.New("github_service: authentication failed")

// ExchangeAndFetch performs the full OAuth dance. When cfg.Mock is true it
// returns a deterministic fixture instead of calling GitHub.
func (c *GitHubClient) ExchangeAndFetch(ctx context.Context, code, redirectURI, codeVerifier string) (*GitHubUser, error) {
	if err := c.validateOAuthRequest(code, redirectURI, codeVerifier); err != nil {
		return nil, fmt.Errorf("%w: %v", ErrGithubAuthFailed, err)
	}
	if c.cfg.Mock {
		name := "Mock User"
		email := "mock@qq.com"
		return &GitHubUser{
			ID:        99999999,
			Login:     "mock-user",
			Name:      &name,
			Email:     &email,
			AvatarURL: "https://avatars.githubusercontent.com/u/99999999",
		}, nil
	}
	if c.cfg.ClientID == "" || c.cfg.ClientSecret == "" {
		return nil, fmt.Errorf("%w: missing client credentials", ErrGithubAuthFailed)
	}
	accessToken, err := c.exchangeCode(ctx, code, redirectURI, codeVerifier)
	if err != nil {
		return nil, fmt.Errorf("%w: %v", ErrGithubAuthFailed, err)
	}
	user, err := c.fetchUser(ctx, accessToken)
	if err != nil {
		return nil, fmt.Errorf("%w: %v", ErrGithubAuthFailed, err)
	}
	return user, nil
}

// ExchangeAndFetchServer performs the confidential-client exchange using the
// callback URI configured on the server. Request data cannot override it.
func (c *GitHubClient) ExchangeAndFetchServer(ctx context.Context, code string) (*GitHubUser, error) {
	if code == "" || c.cfg.RedirectURI == "" {
		return nil, ErrGithubAuthFailed
	}
	if c.cfg.Mock {
		return &GitHubUser{ID: 99999999, Login: "mock-user", Name: ptr("Mock User"), Email: ptr("mock@qq.com")}, nil
	}
	if c.cfg.ClientID == "" || c.cfg.ClientSecret == "" {
		return nil, fmt.Errorf("%w: missing client credentials", ErrGithubAuthFailed)
	}
	accessToken, err := c.exchangeCode(ctx, code, c.cfg.RedirectURI, "")
	if err != nil {
		return nil, fmt.Errorf("%w: %v", ErrGithubAuthFailed, err)
	}
	user, err := c.fetchUser(ctx, accessToken)
	if err != nil {
		return nil, fmt.Errorf("%w: %v", ErrGithubAuthFailed, err)
	}
	return user, nil
}

func ptr(value string) *string { return &value }

func (c *GitHubClient) validateOAuthRequest(code, redirectURI, codeVerifier string) error {
	if code == "" {
		return errors.New("missing authorization code")
	}
	if c.cfg.RedirectURI == "" {
		return errors.New("redirect URI is not configured")
	}
	if redirectURI != c.cfg.RedirectURI {
		return errors.New("redirect URI does not match configuration")
	}
	if !validPKCECodeVerifier(codeVerifier) {
		return errors.New("invalid PKCE code verifier")
	}
	return nil
}

func validPKCECodeVerifier(codeVerifier string) bool {
	if len(codeVerifier) < 43 || len(codeVerifier) > 128 {
		return false
	}
	for _, ch := range codeVerifier {
		if (ch >= 'a' && ch <= 'z') ||
			(ch >= 'A' && ch <= 'Z') ||
			(ch >= '0' && ch <= '9') ||
			ch == '-' || ch == '.' || ch == '_' || ch == '~' {
			continue
		}
		return false
	}
	return true
}

func (c *GitHubClient) exchangeCode(ctx context.Context, code, redirectURI, codeVerifier string) (string, error) {
	form := url.Values{}
	form.Set("client_id", c.cfg.ClientID)
	form.Set("client_secret", c.cfg.ClientSecret)
	form.Set("code", code)
	form.Set("redirect_uri", redirectURI)
	if codeVerifier != "" {
		form.Set("code_verifier", codeVerifier)
	}

	req, err := http.NewRequestWithContext(ctx, http.MethodPost, c.tokenURL, strings.NewReader(form.Encode()))
	if err != nil {
		return "", err
	}
	req.Header.Set("Accept", "application/json")
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return "", err
	}
	defer resp.Body.Close()

	body, err := io.ReadAll(io.LimitReader(resp.Body, 64<<10))
	if err != nil {
		return "", err
	}
	if resp.StatusCode != http.StatusOK {
		return "", fmt.Errorf("token endpoint status %d: %s", resp.StatusCode, string(body))
	}
	var parsed struct {
		AccessToken string `json:"access_token"`
		Error       string `json:"error"`
		ErrorDesc   string `json:"error_description"`
	}
	if err := json.Unmarshal(body, &parsed); err != nil {
		return "", err
	}
	if parsed.AccessToken == "" {
		return "", fmt.Errorf("token endpoint error: %s %s", parsed.Error, parsed.ErrorDesc)
	}
	return parsed.AccessToken, nil
}

func (c *GitHubClient) fetchUser(ctx context.Context, accessToken string) (*GitHubUser, error) {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, c.userURL, nil)
	if err != nil {
		return nil, err
	}
	req.Header.Set("Accept", "application/vnd.github+json")
	req.Header.Set("Authorization", "Bearer "+accessToken)
	req.Header.Set("X-GitHub-Api-Version", "2022-11-28")

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	body, err := io.ReadAll(io.LimitReader(resp.Body, 1<<20))
	if err != nil {
		return nil, err
	}
	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("user endpoint status %d: %s", resp.StatusCode, string(body))
	}
	var u GitHubUser
	if err := json.Unmarshal(body, &u); err != nil {
		return nil, err
	}
	return &u, nil
}
