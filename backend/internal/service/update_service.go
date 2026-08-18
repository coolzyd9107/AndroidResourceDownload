package service

import (
	"encoding/json"
	"errors"
	"fmt"
	"time"

	"github.com/google/uuid"

	"link.mczihan/webdavbox-backend/internal/dto"
	"link.mczihan/webdavbox-backend/internal/model"
	"link.mczihan/webdavbox-backend/internal/pkg/crypto"
	"link.mczihan/webdavbox-backend/internal/repository"
)

// UpdateService prepares and resolves encrypted update URLs.
type UpdateService struct {
	suite  *crypto.Suite
	repo   *repository.AppVersionRepo
	logs   *repository.UpdateLogRepo
	ttl    time.Duration
}

// NewUpdateService constructs an UpdateService.
func NewUpdateService(suite *crypto.Suite, repo *repository.AppVersionRepo, logs *repository.UpdateLogRepo, ttlSeconds int) *UpdateService {
	return &UpdateService{
		suite: suite,
		repo:  repo,
		logs:  logs,
		ttl:   time.Duration(ttlSeconds) * time.Second,
	}
}

// Info returns the latest version info with an encrypted URL.
func (s *UpdateService) Info() (*dto.UpdateInfoResponse, error) {
	v, err := s.repo.Latest()
	if err != nil {
		return nil, err
	}
	if v == nil {
		return nil, errors.New("update_service: no app version configured")
	}
	now := time.Now()
	expiresAt := now.Add(s.ttl).Unix()
	payload := &dto.UpdateURLPayload{
		VersionCode: v.VersionCode,
		TargetURL:   v.TargetURL,
		ExpiresAt:   expiresAt,
		JTI:         uuid.NewString(),
	}
	plaintext, err := json.Marshal(payload)
	if err != nil {
		return nil, fmt.Errorf("update_service: marshal: %w", err)
	}
	enc, err := s.suite.Encrypt(plaintext, expiresAt)
	if err != nil {
		return nil, fmt.Errorf("update_service: encrypt: %w", err)
	}
	changelog := ""
	if v.Changelog != nil {
		changelog = *v.Changelog
	}
	return &dto.UpdateInfoResponse{
		VersionCode:  v.VersionCode,
		VersionName:  v.VersionName,
		ForceUpdate:  v.ForceUpdate,
		Changelog:    changelog,
		EncryptedURL: composite(enc),
		ExpiresAt:    expiresAt,
		Signature:    enc.Signature,
	}, nil
}

// Resolve verifies the encrypted URL, returns the underlying target URL,
// and persists a log entry. The log records metadata only — never the
// plaintext payload.
func (s *UpdateService) Resolve(encStr string, userID, ip, userAgent string) (*dto.UpdateResolveResponse, error) {
	enc, err := parseComposite(encStr)
	if err != nil {
		return nil, err
	}
	now := time.Now().Unix()
	plaintext, err := s.suite.Decrypt(enc, now)
	if err != nil {
		if errors.Is(err, crypto.ErrSignatureMismatch) {
			return nil, ErrUpdateURLInvalid
		}
		if errors.Is(err, crypto.ErrDecryptionFailed) {
			return nil, ErrUpdateURLExpired
		}
		return nil, err
	}
	payload := &dto.UpdateURLPayload{}
	if err := json.Unmarshal(plaintext, payload); err != nil {
		return nil, ErrUpdateURLInvalid
	}

	resolved := true
	log := &model.UpdateURLLog{
		ID:           uuid.NewString(),
		UserID:       nullableString(userID),
		VersionCode:  &payload.VersionCode,
		EncryptedURL: encStr,
		Resolved:     resolved,
		IP:           nullableString(ip),
		UserAgent:    nullableString(userAgent),
	}
	_ = s.logs.Create(log)

	return &dto.UpdateResolveResponse{
		URL:       payload.TargetURL,
		ExpiresIn: int(s.ttl.Seconds()),
	}, nil
}

// ErrUpdateURLExpired is exported for handler mapping.
var ErrUpdateURLExpired = errors.New("update_service: encrypted url expired")

// ErrUpdateURLInvalid is exported for handler mapping.
var ErrUpdateURLInvalid = errors.New("update_service: encrypted url invalid")

// composite serialises Encrypted to a single URL-safe string: ct.nonce.exp.sig
// (all base64-url-encoded so the dot is the only separator).
func composite(e *crypto.Encrypted) string {
	return e.Ciphertext + "." + e.Nonce + "." + intToString(e.ExpiresAt) + "." + e.Signature
}

func parseComposite(s string) (*crypto.Encrypted, error) {
	parts := splitDots(s)
	if len(parts) != 4 {
		return nil, ErrUpdateURLInvalid
	}
	exp, err := stringToInt(parts[2])
	if err != nil {
		return nil, ErrUpdateURLInvalid
	}
	return &crypto.Encrypted{
		Ciphertext: parts[0],
		Nonce:      parts[1],
		ExpiresAt:  exp,
		Signature:  parts[3],
	}, nil
}

func splitDots(s string) []string {
	out := make([]string, 0, 4)
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

func intToString(v int64) string {
	return fmt.Sprintf("%d", v)
}

func stringToInt(s string) (int64, error) {
	var v int64
	_, err := fmt.Sscanf(s, "%d", &v)
	if err != nil {
		return 0, err
	}
	return v, nil
}
