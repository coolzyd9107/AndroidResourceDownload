package repository

import (
	"gorm.io/gorm"

	"link.mczihan/webdavbox-backend/internal/model"
)

// UpdateLogRepo persists update_url_logs.
type UpdateLogRepo struct{ db *gorm.DB }

func (r *UpdateLogRepo) Create(l *model.UpdateURLLog) error { return r.db.Create(l).Error }

// CredentialLogRepo persists webdav_credential_logs.
type CredentialLogRepo struct{ db *gorm.DB }

func (r *CredentialLogRepo) Create(l *model.WebDAVCredentialLog) error { return r.db.Create(l).Error }

// AuditLogRepo persists audit_logs.
type AuditLogRepo struct{ db *gorm.DB }

func (r *AuditLogRepo) Create(l *model.AuditLog) error { return r.db.Create(l).Error }
