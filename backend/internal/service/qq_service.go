package service

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"strings"
	"time"

	"github.com/google/uuid"

	"resdownload.com/backend/internal/config"
	"resdownload.com/backend/internal/dto"
	"resdownload.com/backend/internal/model"
	"resdownload.com/backend/internal/pkg/response"
)

// QqProfile carries the optional display data Tencent returns per user.
type QqProfile struct {
	Nickname  string
	AvatarURL string
}

// QqVerifiedUser is the identity Tencent confirmed plus optional profile.
type QqVerifiedUser struct {
	OpenID  string
	Profile *QqProfile
}

// ErrQqAuthFailed indicates token verification against Tencent failed.
var ErrQqAuthFailed = errors.New("qq_service: authentication failed")

// QqClient verifies client-side QQ OpenSDK tokens via graph.qq.com.
type QqClient struct {
	cfg        *config.QqConfig
	httpClient *http.Client
}

// NewQqClient builds a client with the configured timeouts.
func NewQqClient(cfg *config.QqConfig) *QqClient {
	return &QqClient{cfg: cfg, httpClient: &http.Client{Timeout: 10 * time.Second}}
}

// Authenticate validates the provider token, requires Tencent to confirm the
// configured AppID, and rejects a submitted OpenID that does not match the
// server-observed one. User info is best-effort and never fails the flow.
func (c *QqClient) Authenticate(ctx context.Context, accessToken, claimedOpenID string) (*QqVerifiedUser, error) {
	if strings.TrimSpace(accessToken) == "" || strings.TrimSpace(claimedOpenID) == "" {
		return nil, fmt.Errorf("%w: missing access token or open id", ErrQqAuthFailed)
	}
	if strings.TrimSpace(c.cfg.AppID) == "" {
		return nil, fmt.Errorf("%w: qq.app-id is not configured", ErrQqAuthFailed)
	}
	openID, err := c.fetchOpenID(ctx, accessToken)
	if err != nil {
		return nil, err
	}
	if !strings.EqualFold(openID, claimedOpenID) {
		return nil, fmt.Errorf("%w: open id mismatch", ErrQqAuthFailed)
	}
	return &QqVerifiedUser{OpenID: openID, Profile: c.fetchUserInfo(ctx, accessToken, openID)}, nil
}

func (c *QqClient) fetchOpenID(ctx context.Context, accessToken string) (string, error) {
	query := url.Values{"access_token": {accessToken}}
	body, err := c.get(ctx, c.cfg.MeURL+"?"+query.Encode(), 64<<10)
	if err != nil {
		return "", fmt.Errorf("%w: %v", ErrQqAuthFailed, err)
	}
	var parsed struct {
		ClientID string `json:"client_id"`
		OpenID   string `json:"openid"`
	}
	if err := unmarshalJSONPObject(body, &parsed); err != nil {
		return "", fmt.Errorf("%w: malformed me response", ErrQqAuthFailed)
	}
	if parsed.OpenID == "" || parsed.ClientID != c.cfg.AppID {
		return "", fmt.Errorf("%w: client_id/openid absent or not matching configuration", ErrQqAuthFailed)
	}
	return parsed.OpenID, nil
}

func (c *QqClient) fetchUserInfo(ctx context.Context, accessToken, openID string) *QqProfile {
	if strings.TrimSpace(c.cfg.UserInfoURL) == "" {
		return nil
	}
	query := url.Values{}
	query.Set("access_token", accessToken)
	query.Set("oauth_consumer_key", c.cfg.AppID)
	query.Set("openid", openID)
	body, err := c.get(ctx, c.cfg.UserInfoURL+"?"+query.Encode(), 1<<20)
	if err != nil {
		return nil
	}
	var parsed struct {
		Ret          int    `json:"ret"`
		Nickname     string `json:"nickname"`
		FigureurlQQ1 string `json:"figureurl_qq_1"`
		FigureurlQQ2 string `json:"figureurl_qq_2"`
	}
	if json.Unmarshal(body, &parsed) != nil || parsed.Ret != 0 || strings.TrimSpace(parsed.Nickname) == "" {
		return nil
	}
	avatar := strings.TrimSpace(parsed.FigureurlQQ2)
	if avatar == "" {
		avatar = strings.TrimSpace(parsed.FigureurlQQ1)
	}
	return &QqProfile{Nickname: strings.TrimSpace(parsed.Nickname), AvatarURL: avatar}
}

func (c *QqClient) get(ctx context.Context, rawURL string, limit int64) ([]byte, error) {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, rawURL, nil)
	if err != nil {
		return nil, err
	}
	resp, err := c.httpClient.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()
	body, err := io.ReadAll(io.LimitReader(resp.Body, limit))
	if err != nil {
		return nil, err
	}
	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("graph.qq.com status %d", resp.StatusCode)
	}
	return body, nil
}

// unmarshalJSONPObject extracts the first JSON object from a JSONP payload
// such as `callback( {"client_id":"...","openid":"..."} );`.
func unmarshalJSONPObject(body []byte, target any) error {
	start := bytes.IndexByte(body, '{')
	end := bytes.LastIndexByte(body, '}')
	if start < 0 || end <= start {
		return errors.New("no JSON object found")
	}
	return json.Unmarshal(body[start:end+1], target)
}

type qqAuthClient interface {
	Authenticate(ctx context.Context, accessToken, openID string) (*QqVerifiedUser, error)
}

const qqIdentityProvider = "qq"

// QqAuthService owns the mobile client-side QQ login: verify with Tencent,
// provision the local account through auth identities, then issue tokens.
type QqAuthService struct {
	auth *AuthService
	qq   qqAuthClient
}

// NewQqAuthService constructs a QqAuthService.
func NewQqAuthService(auth *AuthService, qq qqAuthClient) *QqAuthService {
	return &QqAuthService{auth: auth, qq: qq}
}

// Login validates the provider credential and signs the user in.
func (s *QqAuthService) Login(ctx context.Context, accessToken, openID, deviceID string) (*dto.LoginResult, error) {
	if strings.TrimSpace(accessToken) == "" || strings.TrimSpace(openID) == "" {
		return nil, response.ErrQqAuthFailed
	}
	verified, err := s.qq.Authenticate(ctx, accessToken, openID)
	if err != nil || verified == nil || verified.OpenID == "" {
		extras := map[string]string{"method": "qq"}
		if err != nil {
			extras["err"] = err.Error()
		}
		s.auth.auditEvent(ctx, "", "login_failed", extras)
		return nil, response.ErrQqAuthFailed
	}
	user, err := s.provisionUser(verified)
	if err != nil {
		return nil, err
	}
	if user.Status != model.UserStatusActive {
		return nil, response.ErrUnauthorized
	}
	tokens, err := s.auth.tokens.Issue(user.ID, string(user.Role), deviceID)
	if err != nil {
		return nil, err
	}
	s.auth.auditEvent(ctx, user.ID, "login_success", map[string]string{"method": "qq"})
	return buildLoginResult(user, tokens, "QQ"), nil
}

func (s *QqAuthService) provisionUser(verified *QqVerifiedUser) (*model.User, error) {
	identity, err := s.auth.identities.GetByProvider(qqIdentityProvider, verified.OpenID)
	if err != nil {
		return nil, err
	}
	var user *model.User
	if identity != nil {
		user, err = s.auth.users.GetByID(identity.UserID)
		if err != nil {
			return nil, err
		}
	}
	now := time.Now()
	changed := false
	if user == nil {
		user = &model.User{
			ID:         uuid.NewString(),
			Role:       model.RoleUser,
			RoleSource: ptrStr(string(model.RoleSourceQqDefault)),
			Status:     model.UserStatusActive,
			CreatedAt:  now,
			UpdatedAt:  now,
		}
		changed = true
	}
	if verified.Profile != nil {
		if name := strings.TrimSpace(verified.Profile.Nickname); name != "" && (user.Name == nil || *user.Name != name) {
			user.Name = ptrStr(name)
			changed = true
		}
		if avatar := verified.Profile.AvatarURL; avatar != "" && (user.AvatarURL == nil || *user.AvatarURL != avatar) {
			user.AvatarURL = ptrStr(avatar)
			changed = true
		}
	}
	if changed {
		if err := s.auth.upsertUser(user); err != nil {
			return nil, err
		}
	}
	if identity == nil {
		if err := s.auth.identities.Create(&model.AuthIdentity{
			ID:             uuid.NewString(),
			UserID:         user.ID,
			Provider:       qqIdentityProvider,
			ProviderUserID: verified.OpenID,
			CreatedAt:      now,
		}); err != nil {
			return nil, err
		}
	} else if identity.UserID != user.ID {
		identity.UserID = user.ID
		if err := s.auth.identities.Update(identity); err != nil {
			return nil, err
		}
	}
	return user, nil
}
