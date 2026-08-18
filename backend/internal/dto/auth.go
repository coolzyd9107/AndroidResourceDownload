// Package dto contains the wire-level request / response shapes. JSON tags
// use camelCase to match the Android client conventions.
package dto

// GitHubLoginRequest is the body for POST /api/v1/auth/github/login.
type GitHubLoginRequest struct {
	Code        string `json:"code" binding:"required"`
	RedirectURI string `json:"redirectUri"`
	DeviceID    string `json:"deviceId"`
}

// EmailCodeRequest is the body for POST /api/v1/auth/email/code.
type EmailCodeRequest struct {
	Email string `json:"email" binding:"required,email"`
}

// EmailLoginRequest is the body for POST /api/v1/auth/email/login.
type EmailLoginRequest struct {
	Email    string `json:"email" binding:"required,email"`
	Code     string `json:"code" binding:"required,len=6"`
	DeviceID string `json:"deviceId"`
}

// RefreshTokenRequest is the body for POST /api/v1/auth/refresh.
type RefreshTokenRequest struct {
	RefreshToken string `json:"refreshToken" binding:"required"`
}

// UserDTO is the public projection of a user.
type UserDTO struct {
	ID         string  `json:"id"`
	Name       *string `json:"name"`
	Email      *string `json:"email"`
	Role       string  `json:"role"`
	AvatarURL  *string `json:"avatarUrl"`
	LoginType  string  `json:"loginType"`
}

// LoginResult is the payload returned by login + refresh endpoints.
type LoginResult struct {
	AccessToken  string  `json:"accessToken"`
	RefreshToken string  `json:"refreshToken"`
	ExpiresIn    int     `json:"expiresIn"`
	User         UserDTO `json:"user"`
}

// RefreshResult is the payload returned by /auth/refresh.
type RefreshResult struct {
	AccessToken  string `json:"accessToken"`
	RefreshToken string `json:"refreshToken"`
	ExpiresIn    int    `json:"expiresIn"`
}

// EmailCodeResult is the payload returned by /auth/email/code.
type EmailCodeResult struct {
	ExpiresIn int `json:"expiresIn"`
}
