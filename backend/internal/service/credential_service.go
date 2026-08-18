package service

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"log/slog"
	"time"

	"github.com/google/uuid"

	"link.mczihan/webdavbox-backend/internal/dto"
	"link.mczihan/webdavbox-backend/internal/model"
	"link.mczihan/webdavbox-backend/internal/pkg/crypto"
	"link.mczihan/webdavbox-backend/internal/repository"
)

// CredentialService builds and decrypts WebDAV credentials.
type CredentialService struct {
	suite       *crypto.Suite
	cfg         WebDAVConfigProvider
	logs        *repository.CredentialLogRepo
}

// WebDAVConfigProvider is the slice of config the service needs.
type WebDAVConfigProvider interface {
	BaseURL() string
	ForUser(role model.Role) (username, password, rootPath string, ttl time.Duration)
}

// NewCredentialService builds a CredentialService.
func NewCredentialService(suite *crypto.Suite, cfg WebDAVConfigProvider, logs *repository.CredentialLogRepo) *CredentialService {
	return &CredentialService{suite: suite, cfg: cfg, logs: logs}
}

// BuildPayload composes the plaintext credential payload for the role and
// returns both the encrypted envelope (used by /webdav/credential MVP
// callers) and the decrypted shape (used by tests).
func (s *CredentialService) BuildPayload(role model.Role) (*dto.CredentialPayload, *crypto.Encrypted, error) {
	username, password, root, ttl := s.cfg.ForUser(role)
	if username == "" || password == "" {
		return nil, nil, errors.New("credential_service: webdav account not configured for role")
	}
	now := time.Now()
	expiresAt := now.Add(ttl).Unix()
	jti := uuid.NewString()
	permission := "READ_ONLY"
	if role == model.RoleAdmin {
		permission = "READ_WRITE"
	}
	payload := &dto.CredentialPayload{
		BaseURL:    s.cfg.BaseURL(),
		Username:   username,
		Password:   password,
		RootPath:   root,
		Permission: permission,
		ExpiresAt:  expiresAt,
		JTI:        jti,
	}
	plaintext, err := json.Marshal(payload)
	if err != nil {
		return nil, nil, fmt.Errorf("credential_service: marshal: %w", err)
	}
	enc, err := s.suite.Encrypt(plaintext, expiresAt)
	if err != nil {
		return nil, nil, fmt.Errorf("credential_service: encrypt: %w", err)
	}
	return payload, enc, nil
}

// IssuePlaintext is the MVP /webdav/credential MVP body: HTTPS + JSON.
// The encrypted envelope is also returned so handlers can include it in
// logs / audit if desired.
func (s *CredentialService) IssuePlaintext(role model.Role) (*dto.CredentialResponse, *crypto.Encrypted, error) {
	payload, enc, err := s.BuildPayload(role)
	if err != nil {
		return nil, nil, err
	}
	return &dto.CredentialResponse{
		BaseURL:    payload.BaseURL,
		Username:   payload.Username,
		Password:   payload.Password,
		RootPath:   payload.RootPath,
		Permission: payload.Permission,
		ExpiresAt:  payload.ExpiresAt,
	}, enc, nil
}

// Log records a credential issuance event. Passwords are never stored.
func (s *CredentialService) Log(ctx context.Context, userID, role, permission, ip, userAgent string) {
	log := &model.WebDAVCredentialLog{
		ID:         uuid.NewString(),
		UserID:     nullableString(userID),
		Role:       role,
		Permission: permission,
		IP:         nullableString(ip),
		UserAgent:  nullableString(userAgent),
	}
	if err := s.logs.Create(log); err != nil {
		slog.Default().Error("credential_service: log create failed", slog.String("err", err.Error()))
	}
}

func nullableString(s string) *string {
	if s == "" {
		return nil
	}
	return &s
}
