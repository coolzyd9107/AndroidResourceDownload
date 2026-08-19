package handler

import (
	"log/slog"

	"github.com/gin-gonic/gin"

	"link.mczihan/webdavbox-backend/internal/config"
	"link.mczihan/webdavbox-backend/internal/dto"
	"link.mczihan/webdavbox-backend/internal/middleware"
	"link.mczihan/webdavbox-backend/internal/pkg/response"
	"link.mczihan/webdavbox-backend/internal/service"
)

// AuthHandler exposes the /auth/* endpoints.
type AuthHandler struct {
	auth    *service.AuthService
	limiter any
	cfg     *config.Config
	log     *slog.Logger
}

// NewAuthHandler constructs an AuthHandler. The limiter argument is
// accepted so future per-route limits can be added without changing the
// constructor signature.
func NewAuthHandler(auth *service.AuthService, limiter any, cfg *config.Config, log *slog.Logger) *AuthHandler {
	return &AuthHandler{auth: auth, limiter: limiter, cfg: cfg, log: log}
}

// SendEmailCode handles POST /api/v1/auth/email/code.
func (h *AuthHandler) SendEmailCode(c *gin.Context) {
	var req dto.EmailCodeRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		writeBadRequest(c, 0, "invalid_request")
		return
	}
	ttl, err := h.auth.SendEmailCode(c.Request.Context(), req.Email)
	if err != nil {
		logRequestError(h.log, c, "send_email_code", err)
		response.Fail(c, err)
		return
	}
	response.OK(c, dto.EmailCodeResult{ExpiresIn: int(ttl.Seconds())})
}

// EmailLogin handles POST /api/v1/auth/email/login.
func (h *AuthHandler) EmailLogin(c *gin.Context) {
	var req dto.EmailLoginRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		writeBadRequest(c, 0, "invalid_request")
		return
	}
	result, err := h.auth.EmailLogin(c.Request.Context(), req.Email, req.Code, req.DeviceID)
	if err != nil {
		logRequestError(h.log, c, "email_login", err)
		response.Fail(c, err)
		return
	}
	response.OK(c, result)
}

// GithubLogin handles POST /api/v1/auth/github/login.
func (h *AuthHandler) GithubLogin(c *gin.Context) {
	var req dto.GitHubLoginRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		writeBadRequest(c, 0, "invalid_request")
		return
	}
	if req.Code == "" {
		writeBadRequest(c, 0, "missing_code")
		return
	}
	result, err := h.auth.GithubLogin(
		c.Request.Context(),
		req.Code,
		req.RedirectURI,
		req.CodeVerifier,
		req.DeviceID,
	)
	if err != nil {
		logRequestError(h.log, c, "github_login", err)
		response.Fail(c, err)
		return
	}
	response.OK(c, result)
}

// Refresh handles POST /api/v1/auth/refresh.
func (h *AuthHandler) Refresh(c *gin.Context) {
	var req dto.RefreshTokenRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		writeBadRequest(c, 0, "invalid_request")
		return
	}
	result, err := h.auth.Refresh(req.RefreshToken)
	if err != nil {
		logRequestError(h.log, c, "refresh", err)
		response.Fail(c, err)
		return
	}
	response.OK(c, result)
}

// Logout handles POST /api/v1/auth/logout.
func (h *AuthHandler) Logout(c *gin.Context) {
	uid := middleware.UserIDFromContext(c)
	var req dto.RefreshTokenRequest
	_ = c.ShouldBindJSON(&req) // body is optional
	if err := h.auth.Logout(c.Request.Context(), uid, req.RefreshToken); err != nil {
		logRequestError(h.log, c, "logout", err)
		response.Fail(c, err)
		return
	}
	response.OK(c, gin.H{"status": "ok"})
}

// Me handles GET /api/v1/auth/me.
func (h *AuthHandler) Me(c *gin.Context) {
	uid := middleware.UserIDFromContext(c)
	user, err := h.auth.Me(uid)
	if err != nil {
		logRequestError(h.log, c, "me", err)
		response.Fail(c, err)
		return
	}
	response.OK(c, user)
}
