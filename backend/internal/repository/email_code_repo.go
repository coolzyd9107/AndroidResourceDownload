package repository

import (
	"errors"
	"time"

	"gorm.io/gorm"

	"link.mczihan/webdavbox-backend/internal/model"
)

// EmailCodeRepo persists email_verification_codes.
type EmailCodeRepo struct{ db *gorm.DB }

func (r *EmailCodeRepo) Create(c *model.EmailVerificationCode) error {
	return r.db.Create(c).Error
}

// LatestUnconsumed returns the most recent unconsumed code for the email
// that has not yet expired.
func (r *EmailCodeRepo) LatestUnconsumed(email string, now time.Time) (*model.EmailVerificationCode, error) {
	var c model.EmailVerificationCode
	err := r.db.
		Where("email = ? AND consumed_at IS NULL AND expires_at > ?", email, now).
		Order("created_at DESC").
		First(&c).Error
	if errors.Is(err, gorm.ErrRecordNotFound) {
		return nil, nil
	}
	if err != nil {
		return nil, err
	}
	return &c, nil
}

// MarkConsumed sets consumed_at = now.
func (r *EmailCodeRepo) MarkConsumed(id string, now time.Time) error {
	return r.db.Model(&model.EmailVerificationCode{}).
		Where("id = ?", id).
		Update("consumed_at", now).Error
}

// IncrementAttempts bumps attempts by 1.
func (r *EmailCodeRepo) IncrementAttempts(id string) error {
	return r.db.Model(&model.EmailVerificationCode{}).
		Where("id = ?", id).
		Update("attempts", gorm.Expr("attempts + 1")).Error
}
