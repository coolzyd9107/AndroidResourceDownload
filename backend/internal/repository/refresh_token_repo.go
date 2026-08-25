package repository

import (
	"errors"
	"time"

	"gorm.io/gorm"

	"resdownload.com/backend/internal/model"
)

// RefreshTokenRepo persists refresh_tokens.
type RefreshTokenRepo struct{ db *gorm.DB }

func (r *RefreshTokenRepo) Create(t *model.RefreshToken) error {
	return r.db.Create(t).Error
}

func (r *RefreshTokenRepo) GetByHash(hash string) (*model.RefreshToken, error) {
	var t model.RefreshToken
	err := r.db.First(&t, "token_hash = ?", hash).Error
	if errors.Is(err, gorm.ErrRecordNotFound) {
		return nil, nil
	}
	if err != nil {
		return nil, err
	}
	return &t, nil
}

// Revoke sets revoked_at for the matching hash. Idempotent.
func (r *RefreshTokenRepo) Revoke(hash string, now time.Time) error {
	return r.db.Model(&model.RefreshToken{}).
		Where("token_hash = ? AND revoked_at IS NULL", hash).
		Update("revoked_at", now).Error
}
