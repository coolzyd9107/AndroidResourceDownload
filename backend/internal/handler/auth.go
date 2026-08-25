package handler

import (
	"log/slog"
	"net/http"
	"net/url"
	"time"

	"github.com/gin-gonic/gin"

	"link.mczihan/webdavbox-backend/internal/config"
	"link.mczihan/webdavbox-backend/internal/dto"
	"link.mczihan/webdavbox-backend/internal/middleware"
	"link.mczihan/webdavbox-backend/internal/pkg/response"
	"link.mczihan/webdavbox-backend/internal/service"
)

// AuthHandler exposes the /auth/* endpoints.
type AuthHandler struct {
	auth        *service.AuthService
	qq          *service.QqAuthService
	githubOAuth *service.GithubOAuthService
	limiter     any
	cfg         *config.Config
	log         *slog.Logger
}

// NewAuthHandler constructs an AuthHandler. The limiter argument is
// accepted so future per-route limits can be added without changing the
// constructor signature.
func NewAuthHandler(auth *service.AuthService, limiter any, cfg *config.Config, log *slog.Logger, qq *service.QqAuthService, githubOAuth ...*service.GithubOAuthService) *AuthHandler {
	h := &AuthHandler{auth: auth, limiter: limiter, cfg: cfg, log: log, qq: qq}
	if len(githubOAuth) > 0 {
		h.githubOAuth = githubOAuth[0]
	}
	return h
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

// QqLogin handles POST /api/v1/auth/qq/login.
func (h *AuthHandler) QqLogin(c *gin.Context) {
	if h.qq == nil {
		response.Fail(c, response.ErrQqAuthFailed)
		return
	}
	var req dto.QqLoginRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		writeBadRequest(c, 0, "invalid_request")
		return
	}
	result, err := h.qq.Login(c.Request.Context(), req.AccessToken, req.OpenID, req.DeviceID)
	if err != nil {
		logRequestError(h.log, c, "qq_login", err)
		response.Fail(c, err)
		return
	}
	response.OK(c, result)
}

func (h *AuthHandler) GithubStart(c *gin.Context) {
	if h.githubOAuth == nil {
		response.Fail(c, response.ErrGithubAuthFailed)
		return
	}
	if c.Query("code_challenge_method") != "S256" {
		response.Fail(c, response.ErrGithubAuthFailed)
		return
	}
	location, err := h.githubOAuth.Start(c.Query("code_challenge"), c.Query("app_state"), time.Now())
	if err != nil {
		response.Fail(c, response.ErrGithubAuthFailed)
		return
	}
	c.Redirect(http.StatusFound, location)
}

func (h *AuthHandler) GithubCallback(c *gin.Context) {
	if h.githubOAuth == nil {
		response.Fail(c, response.ErrGithubAuthFailed)
		return
	}
	if githubError := c.Query("error"); githubError != "" {
		if redirect, appState, ok := h.githubOAuth.Cancelled(c.Query("state"), time.Now()); ok {
			location := redirect + "?error=access_denied&app_state=" + url.QueryEscape(appState)
			c.Header("Cache-Control", "no-store")
			c.Header("Referrer-Policy", "no-referrer")
			c.Redirect(http.StatusFound, location)
			return
		}
		response.Fail(c, response.ErrGithubAuthFailed)
		return
	}
	redirect, appState, code, err := h.githubOAuth.Callback(c.Request.Context(), c.Query("code"), c.Query("state"), time.Now())
	if err != nil {
		response.Fail(c, response.ErrGithubAuthFailed)
		return
	}
	location := redirect + "?code=" + url.QueryEscape(code) + "&app_state=" + url.QueryEscape(appState)
	c.Header("Cache-Control", "no-store")
	c.Header("Referrer-Policy", "no-referrer")
	c.Redirect(http.StatusFound, location)
}

func (h *AuthHandler) GithubComplete(c *gin.Context) {
	if h.githubOAuth == nil {
		response.Fail(c, response.ErrGithubAuthFailed)
		return
	}
	var req dto.GitHubCompleteRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		writeBadRequest(c, 0, "invalid_request")
		return
	}
	result, err := h.githubOAuth.Complete(c.Request.Context(), req.Code, req.CodeVerifier, req.DeviceID, time.Now())
	if err != nil {
		response.Fail(c, response.ErrGithubAuthFailed)
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
