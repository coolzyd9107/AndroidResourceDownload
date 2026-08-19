package repository

import (
	"errors"

	"gorm.io/gorm"

	"link.mczihan/webdavbox-backend/internal/model"
)

// IdentityRepo persists auth_identities.
type IdentityRepo struct{ db *gorm.DB }

func (r *IdentityRepo) Create(i *model.AuthIdentity) error { return r.db.Create(i).Error }

func (r *IdentityRepo) Update(i *model.AuthIdentity) error { return r.db.Save(i).Error }

func (r *IdentityRepo) GetByProvider(provider, providerUserID string) (*model.AuthIdentity, error) {
	var i model.AuthIdentity
	err := r.db.First(&i, "provider = ? AND provider_user_id = ?", provider, providerUserID).Error
	if errors.Is(err, gorm.ErrRecordNotFound) {
		return nil, nil
	}
	if err != nil {
		return nil, err
	}
	return &i, nil
}
