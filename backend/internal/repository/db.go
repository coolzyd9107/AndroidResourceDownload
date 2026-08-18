// Package repository wires GORM with the dual-database setup (SQLite for
// local dev, PostgreSQL for production) and exposes per-table repos.
package repository

import (
	"fmt"
	"path/filepath"
	"time"

	"github.com/glebarez/sqlite"
	"gorm.io/driver/postgres"
	"gorm.io/gorm"
	"gorm.io/gorm/logger"

	"link.mczihan/webdavbox-backend/internal/config"
	"link.mczihan/webdavbox-backend/internal/model"
)

// Open returns a *gorm.DB initialised for the configured driver.
func Open(cfg *config.Config) (*gorm.DB, error) {
	gormCfg := &gorm.Config{
		Logger: logger.Default.LogMode(logger.Warn),
	}

	switch cfg.Database.Driver {
	case "sqlite":
		abs, err := filepath.Abs(cfg.Database.URL)
		if err != nil {
			return nil, fmt.Errorf("repository: resolve sqlite path: %w", err)
		}
		db, err := gorm.Open(sqlite.Open(abs), gormCfg)
		if err != nil {
			return nil, fmt.Errorf("repository: open sqlite: %w", err)
		}
		sqlDB, _ := db.DB()
		sqlDB.SetMaxOpenConns(cfg.Database.MaxOpenConns)
		sqlDB.SetMaxIdleConns(cfg.Database.MaxIdleConns)
		sqlDB.SetConnMaxLifetime(time.Duration(cfg.Database.ConnMaxLifetimeMin) * time.Minute)
		return db, nil
	case "postgres":
		db, err := gorm.Open(postgres.Open(cfg.Database.URL), gormCfg)
		if err != nil {
			return nil, fmt.Errorf("repository: open postgres: %w", err)
		}
		sqlDB, _ := db.DB()
		sqlDB.SetMaxOpenConns(cfg.Database.MaxOpenConns)
		sqlDB.SetMaxIdleConns(cfg.Database.MaxIdleConns)
		sqlDB.SetConnMaxLifetime(time.Duration(cfg.Database.ConnMaxLifetimeMin) * time.Minute)
		return db, nil
	default:
		return nil, fmt.Errorf("repository: unsupported driver %q", cfg.Database.Driver)
	}
}

// Migrate runs GORM AutoMigrate on all known models.
func Migrate(db *gorm.DB) error {
	return db.AutoMigrate(
		&model.User{},
		&model.AuthIdentity{},
		&model.EmailVerificationCode{},
		&model.RefreshToken{},
		&model.AdminGithubUser{},
		&model.AppVersion{},
		&model.UpdateURLLog{},
		&model.WebDAVCredentialLog{},
		&model.AuditLog{},
	)
}

// Repos bundles every concrete repository for convenient DI.
type Repos struct {
	DB                 *gorm.DB
	Users              *UserRepo
	Identities         *IdentityRepo
	EmailCodes         *EmailCodeRepo
	RefreshTokens      *RefreshTokenRepo
	AdminGithub        *AdminGithubRepo
	AppVersions        *AppVersionRepo
	UpdateURLLogs      *UpdateLogRepo
	CredentialLogs     *CredentialLogRepo
	AuditLogs          *AuditLogRepo
}

// New builds every repo from the supplied GORM handle.
func New(db *gorm.DB) *Repos {
	return &Repos{
		DB:             db,
		Users:          &UserRepo{db: db},
		Identities:     &IdentityRepo{db: db},
		EmailCodes:     &EmailCodeRepo{db: db},
		RefreshTokens:  &RefreshTokenRepo{db: db},
		AdminGithub:    &AdminGithubRepo{db: db},
		AppVersions:    &AppVersionRepo{db: db},
		UpdateURLLogs:  &UpdateLogRepo{db: db},
		CredentialLogs: &CredentialLogRepo{db: db},
		AuditLogs:      &AuditLogRepo{db: db},
	}
}
