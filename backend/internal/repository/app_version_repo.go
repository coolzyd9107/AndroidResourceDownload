package repository

import (
	"errors"

	"gorm.io/gorm"

	"resdownload.com/backend/internal/model"
)

// AppVersionRepo persists app_versions.
type AppVersionRepo struct{ db *gorm.DB }

func (r *AppVersionRepo) Create(v *model.AppVersion) error { return r.db.Create(v).Error }

// Latest returns the highest-version-code entry.
func (r *AppVersionRepo) Latest() (*model.AppVersion, error) {
	var v model.AppVersion
	err := r.db.Order("version_code DESC").First(&v).Error
	if errors.Is(err, gorm.ErrRecordNotFound) {
		return nil, nil
	}
	if err != nil {
		return nil, err
	}
	return &v, nil
}

func (r *AppVersionRepo) GetByCode(code int64) (*model.AppVersion, error) {
	var v model.AppVersion
	err := r.db.First(&v, "version_code = ?", code).Error
	if errors.Is(err, gorm.ErrRecordNotFound) {
		return nil, nil
	}
	if err != nil {
		return nil, err
	}
	return &v, nil
}
