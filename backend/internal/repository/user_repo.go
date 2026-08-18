package repository

import (
	"errors"
	"time"

	"gorm.io/gorm"

	"link.mczihan/webdavbox-backend/internal/model"
)

// UserRepo persists users.
type UserRepo struct{ db *gorm.DB }

func (r *UserRepo) Create(u *model.User) error { return r.db.Create(u).Error }

func (r *UserRepo) GetByID(id string) (*model.User, error) {
	var u model.User
	err := r.db.First(&u, "id = ?", id).Error
	if errors.Is(err, gorm.ErrRecordNotFound) {
		return nil, nil
	}
	if err != nil {
		return nil, err
	}
	return &u, nil
}

func (r *UserRepo) GetByEmail(email string) (*model.User, error) {
	var u model.User
	err := r.db.First(&u, "email = ?", email).Error
	if errors.Is(err, gorm.ErrRecordNotFound) {
		return nil, nil
	}
	if err != nil {
		return nil, err
	}
	return &u, nil
}

func (r *UserRepo) GetByGithubID(githubID int64) (*model.User, error) {
	var u model.User
	err := r.db.First(&u, "github_id = ?", githubID).Error
	if errors.Is(err, gorm.ErrRecordNotFound) {
		return nil, nil
	}
	if err != nil {
		return nil, err
	}
	return &u, nil
}

func (r *UserRepo) Update(u *model.User) error {
	now := time.Now()
	u.UpdatedAt = now
	return r.db.Save(u).Error
}
