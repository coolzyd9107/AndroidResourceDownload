package repository

import (
	"errors"

	"gorm.io/gorm"

	"resdownload.com/backend/internal/model"
)

// AdminGithubRepo persists admin_github_users.
type AdminGithubRepo struct{ db *gorm.DB }

func (r *AdminGithubRepo) Create(a *model.AdminGithubUser) error {
	return r.db.Create(a).Error
}

func (r *AdminGithubRepo) Get(githubID int64) (*model.AdminGithubUser, error) {
	var a model.AdminGithubUser
	err := r.db.First(&a, "github_id = ?", githubID).Error
	if errors.Is(err, gorm.ErrRecordNotFound) {
		return nil, nil
	}
	if err != nil {
		return nil, err
	}
	return &a, nil
}
