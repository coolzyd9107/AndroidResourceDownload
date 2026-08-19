// Package model contains GORM entity definitions. The same structs back
// both PostgreSQL and SQLite via GORM's automatic column-type mapping.
package model

import "time"

// Role is the canonical user role enum.
type Role string

const (
	RoleUser  Role = "USER"
	RoleAdmin Role = "ADMIN"
)

// RoleSource describes why a user ended up with their role.
type RoleSource string

const (
	RoleSourceGithubWhitelist RoleSource = "github_whitelist"
	RoleSourceGithubDefault   RoleSource = "github_default"
	RoleSourceEmailDomain     RoleSource = "email_domain"
)

// UserStatus is the lifecycle state of a user account.
type UserStatus string

const (
	UserStatusActive   UserStatus = "ACTIVE"
	UserStatusDisabled UserStatus = "DISABLED"
)

// User mirrors 后端.md §14.1 (users).
type User struct {
	ID          string     `gorm:"primaryKey;size:36" json:"id"`
	Email       *string    `gorm:"size:255;uniqueIndex" json:"email,omitempty"`
	GithubID    *int64     `gorm:"uniqueIndex" json:"github_id,omitempty"`
	GithubLogin *string    `gorm:"size:255" json:"github_login,omitempty"`
	Name        *string    `gorm:"size:255" json:"name,omitempty"`
	AvatarURL   *string    `gorm:"type:text" json:"avatar_url,omitempty"`
	Role        Role       `gorm:"size:20;not null;default:USER" json:"role"`
	RoleSource  *string    `gorm:"size:30" json:"role_source,omitempty"`
	Status      UserStatus `gorm:"size:20;not null;default:ACTIVE" json:"status"`
	CreatedAt   time.Time  `json:"created_at"`
	UpdatedAt   time.Time  `json:"updated_at"`
}

func (User) TableName() string { return "users" }

// AuthIdentity links a user to one or more login providers.
type AuthIdentity struct {
	ID             string    `gorm:"primaryKey;size:36" json:"id"`
	UserID         string    `gorm:"size:36;not null;index" json:"user_id"`
	Provider       string    `gorm:"size:20;not null" json:"provider"`
	ProviderUserID string    `gorm:"size:255;not null" json:"provider_user_id"`
	ProviderLogin  *string   `gorm:"size:255" json:"provider_login,omitempty"`
	Email          *string   `gorm:"size:255" json:"email,omitempty"`
	CreatedAt      time.Time `json:"created_at"`
}

func (AuthIdentity) TableName() string { return "auth_identities" }

// EmailVerificationCode mirrors 后端.md §14.3.
type EmailVerificationCode struct {
	ID         string     `gorm:"primaryKey;size:36" json:"id"`
	Email      string     `gorm:"size:255;not null;index" json:"email"`
	CodeHash   string     `gorm:"size:255;not null" json:"-"`
	Purpose    string     `gorm:"size:20;not null;default:LOGIN" json:"purpose"`
	ExpiresAt  time.Time  `gorm:"not null;index" json:"expires_at"`
	ConsumedAt *time.Time `json:"consumed_at,omitempty"`
	Attempts   int        `gorm:"not null;default:0" json:"attempts"`
	CreatedAt  time.Time  `json:"created_at"`
}

func (EmailVerificationCode) TableName() string { return "email_verification_codes" }

// RefreshToken mirrors 后端.md §14.4.
type RefreshToken struct {
	ID        string     `gorm:"primaryKey;size:36" json:"id"`
	UserID    string     `gorm:"size:36;not null;index" json:"user_id"`
	TokenHash string     `gorm:"size:255;not null;uniqueIndex" json:"-"`
	DeviceID  *string    `gorm:"size:255" json:"device_id,omitempty"`
	ExpiresAt time.Time  `gorm:"not null;index" json:"expires_at"`
	RevokedAt *time.Time `json:"revoked_at,omitempty"`
	CreatedAt time.Time  `json:"created_at"`
}

func (RefreshToken) TableName() string { return "refresh_tokens" }

// AdminGithubUser mirrors 后端.md §14.5.
type AdminGithubUser struct {
	GithubID    int64     `gorm:"primaryKey" json:"github_id"`
	GithubLogin *string   `gorm:"size:255" json:"github_login,omitempty"`
	Note        *string   `gorm:"type:text" json:"note,omitempty"`
	CreatedAt   time.Time `json:"created_at"`
}

func (AdminGithubUser) TableName() string { return "admin_github_users" }

// AppVersion mirrors 后端.md §14.6.
type AppVersion struct {
	ID          string    `gorm:"primaryKey;size:36" json:"id"`
	VersionCode int64     `gorm:"not null;uniqueIndex" json:"version_code"`
	VersionName string    `gorm:"size:50;not null" json:"version_name"`
	ForceUpdate bool      `gorm:"not null;default:false" json:"force_update"`
	Changelog   *string   `gorm:"type:text" json:"changelog,omitempty"`
	TargetURL   string    `gorm:"type:text;not null" json:"target_url"`
	CreatedAt   time.Time `json:"created_at"`
}

func (AppVersion) TableName() string { return "app_versions" }

// UpdateURLLog mirrors 后端.md §14.7.
type UpdateURLLog struct {
	ID           string    `gorm:"primaryKey;size:36" json:"id"`
	UserID       *string   `gorm:"size:36;index" json:"user_id,omitempty"`
	VersionCode  *int64    `json:"version_code,omitempty"`
	EncryptedURL string    `gorm:"type:text" json:"encrypted_url"`
	Resolved     bool      `gorm:"not null;default:false" json:"resolved"`
	IP           *string   `gorm:"size:64" json:"ip,omitempty"`
	UserAgent    *string   `gorm:"type:text" json:"user_agent,omitempty"`
	CreatedAt    time.Time `json:"created_at"`
}

func (UpdateURLLog) TableName() string { return "update_url_logs" }

// WebDAVCredentialLog mirrors 后端.md §14.8.
type WebDAVCredentialLog struct {
	ID         string    `gorm:"primaryKey;size:36" json:"id"`
	UserID     *string   `gorm:"size:36;index" json:"user_id,omitempty"`
	Role       string    `gorm:"size:20;not null" json:"role"`
	Permission string    `gorm:"size:20;not null" json:"permission"`
	IP         *string   `gorm:"size:64" json:"ip,omitempty"`
	UserAgent  *string   `gorm:"type:text" json:"user_agent,omitempty"`
	CreatedAt  time.Time `json:"created_at"`
}

func (WebDAVCredentialLog) TableName() string { return "webdav_credential_logs" }

// AuditLog mirrors 后端.md §14.9.
type AuditLog struct {
	ID           string    `gorm:"primaryKey;size:36" json:"id"`
	UserID       *string   `gorm:"size:36;index" json:"user_id,omitempty"`
	Action       string    `gorm:"size:50;not null;index" json:"action"`
	ResourceType *string   `gorm:"size:50" json:"resource_type,omitempty"`
	Method       *string   `gorm:"size:20" json:"method,omitempty"`
	Path         *string   `gorm:"type:text" json:"path,omitempty"`
	StatusCode   *int      `json:"status_code,omitempty"`
	IP           *string   `gorm:"size:64" json:"ip,omitempty"`
	UserAgent    *string   `gorm:"type:text" json:"user_agent,omitempty"`
	CreatedAt    time.Time `json:"created_at"`
}

func (AuditLog) TableName() string { return "audit_logs" }
