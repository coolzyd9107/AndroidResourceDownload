package service

import (
	"crypto/sha256"
	"encoding/hex"
	"errors"
	"time"

	"github.com/google/uuid"
	"gorm.io/gorm"

	"resdownload.com/backend/internal/model"
	"resdownload.com/backend/internal/pkg/jwt"
	"resdownload.com/backend/internal/repository"
)

// TokenService signs and verifies access / refresh tokens.
type TokenService struct {
	issuer        *jwt.Issuer
	accessTTL     time.Duration
	refreshTTL    time.Duration
	refreshTokens *repository.RefreshTokenRepo
}

// NewTokenService constructs a TokenService.
func NewTokenService(issuer *jwt.Issuer, accessTTLSeconds, refreshTTLDays int, refreshTokens *repository.RefreshTokenRepo) *TokenService {
	return &TokenService{
		issuer:        issuer,
		accessTTL:     time.Duration(accessTTLSeconds) * time.Second,
		refreshTTL:    time.Duration(refreshTTLDays) * 24 * time.Hour,
		refreshTokens: refreshTokens,
	}
}

// IssueResult is the return value of Issue.
type IssueResult struct {
	AccessToken      string
	RefreshToken     string
	AccessExpiresIn  int
	RefreshExpiresAt time.Time
}

// Issue signs an access + refresh pair and persists the refresh hash.
func (s *TokenService) Issue(userID, role, deviceID string) (*IssueResult, error) {
	accessJTI := uuid.NewString()
	refreshJTI := uuid.NewString()

	access, err := s.issuer.Sign(userID, role, "access", accessJTI, s.accessTTL)
	if err != nil {
		return nil, err
	}
	refresh, err := s.issuer.Sign(userID, role, "refresh", refreshJTI, s.refreshTTL)
	if err != nil {
		return nil, err
	}

	now := time.Now()
	rt := &model.RefreshToken{
		ID:        uuid.NewString(),
		UserID:    userID,
		TokenHash: HashRefreshToken(refresh),
		DeviceID:  ptrOrNil(deviceID),
		ExpiresAt: now.Add(s.refreshTTL),
		CreatedAt: now,
	}
	if err := s.refreshTokens.Create(rt); err != nil {
		return nil, err
	}
	return &IssueResult{
		AccessToken:      access,
		RefreshToken:     refresh,
		AccessExpiresIn:  int(s.accessTTL.Seconds()),
		RefreshExpiresAt: rt.ExpiresAt,
	}, nil
}

// VerifyRefresh checks the token's signature, type, persistence, and
// revocation status. It returns the persisted record on success.
func (s *TokenService) VerifyRefresh(token string) (*model.RefreshToken, error) {
	claims, err := s.issuer.Verify(token)
	if err != nil {
		return nil, err
	}
	if claims.Type != "refresh" {
		return nil, errors.New("token_service: not a refresh token")
	}
	hash := HashRefreshToken(token)
	record, err := s.refreshTokens.GetByHash(hash)
	if err != nil {
		return nil, err
	}
	if record == nil {
		return nil, errors.New("token_service: refresh token not recognised")
	}
	if record.RevokedAt != nil {
		return nil, errors.New("token_service: refresh token revoked")
	}
	if time.Now().After(record.ExpiresAt) {
		return nil, errors.New("token_service: refresh token expired")
	}
	return record, nil
}

// RevokeRefresh marks the supplied refresh token revoked.
func (s *TokenService) RevokeRefresh(token string) error {
	hash := HashRefreshToken(token)
	return s.refreshTokens.Revoke(hash, time.Now())
}

// VerifyAccess parses and validates an access token. Returns the claims.
func (s *TokenService) VerifyAccess(token string) (*jwt.Claims, error) {
	claims, err := s.issuer.Verify(token)
	if err != nil {
		return nil, err
	}
	if claims.Type != "access" {
		return nil, errors.New("token_service: not an access token")
	}
	return claims, nil
}

// HashRefreshToken returns hex-encoded SHA-256 of the token string. This
// matches the column type we persist as token_hash.
func HashRefreshToken(token string) string {
	sum := sha256.Sum256([]byte(token))
	return hex.EncodeToString(sum[:])
}

func ptrOrNil(s string) *string {
	if s == "" {
		return nil
	}
	return &s
}

// IsNotFound is a small helper around gorm.ErrRecordNotFound for handlers.
func IsNotFound(err error) bool {
	return errors.Is(err, gorm.ErrRecordNotFound)
}
