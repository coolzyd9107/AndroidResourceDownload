// Package app wires HTTP routes and the long-running server.
package app

import (
	"context"
	"log/slog"
	"net/http"
	"time"

	"github.com/gin-gonic/gin"

	"link.mczihan/webdavbox-backend/internal/config"
	"link.mczihan/webdavbox-backend/internal/handler"
	"link.mczihan/webdavbox-backend/internal/middleware"
	"link.mczihan/webdavbox-backend/internal/pkg/jwt"
	"link.mczihan/webdavbox-backend/internal/pkg/response"
	"link.mczihan/webdavbox-backend/internal/ratelimit"
	"link.mczihan/webdavbox-backend/internal/service"
)

// Deps is the bag of collaborators every handler needs. Built in main and
// passed to RegisterRoutes.
type Deps struct {
	Config    *config.Config
	Logger    *slog.Logger
	JWTIssuer *jwt.Issuer
	Limiter   *ratelimit.InMemoryLimiter

	Auth        *service.AuthService
	Credentials *service.CredentialService
	Updates     *service.UpdateService
	Tokens      *service.TokenService
	GithubOAuth *service.GithubOAuthService
}

// RegisterRoutes attaches every endpoint to r.
func RegisterRoutes(r *gin.Engine, d *Deps) {
	r.GET("/health", handler.Health)

	api := r.Group("/api/v1")

	// Public auth endpoints.
	authH := handler.NewAuthHandler(d.Auth, d.Limiter, d.Config, d.Logger, d.GithubOAuth)
	api.POST("/auth/email/code", middleware.RateLimitByKey(d.Limiter, "login_per_ip", clientIP), authH.SendEmailCode)
	api.POST("/auth/email/login", middleware.RateLimitByKey(d.Limiter, "login_per_ip", clientIP), authH.EmailLogin)
	api.GET("/auth/github/start", middleware.RateLimitByKey(d.Limiter, "login_per_ip", clientIP), authH.GithubStart)
	api.GET("/auth/github/callback", authH.GithubCallback)
	api.POST("/auth/github/complete", middleware.RateLimitByKey(d.Limiter, "login_per_ip", clientIP), authH.GithubComplete)
	api.POST("/auth/refresh", authH.Refresh)

	// Authenticated endpoints.
	authMW := middleware.Auth(d.JWTIssuer)

	api.POST("/auth/logout", authMW, authH.Logout)
	api.GET("/auth/me", authMW, authH.Me)

	credH := handler.NewWebDAVHandler(d.Credentials, d.Logger)
	api.POST("/webdav/credential", authMW, middleware.RateLimitByKey(d.Limiter, "webdav_per_user", userIDKey), credH.Issue)

	updH := handler.NewUpdateHandler(d.Updates, d.Logger)
	api.GET("/update/info", authMW, updH.Info)
	api.POST("/update/resolve", authMW, middleware.RateLimitByKey(d.Limiter, "update_per_user", userIDKey), updH.Resolve)
}

// Server wraps http.Server so main can call ListenAndServe / Shutdown
// through a single object.
type Server struct {
	Addr    string
	Handler http.Handler
	Log     *slog.Logger
	srv     *http.Server
}

// ListenAndServe starts the HTTP listener.
func (s *Server) ListenAndServe() error {
	s.srv = &http.Server{
		Addr:              s.Addr,
		Handler:           s.Handler,
		ReadTimeout:       15 * time.Second,
		ReadHeaderTimeout: 5 * time.Second,
		WriteTimeout:      30 * time.Second,
		IdleTimeout:       60 * time.Second,
	}
	if err := s.srv.ListenAndServe(); err != nil && err != http.ErrServerClosed {
		return err
	}
	return nil
}

// Shutdown gracefully stops the server.
func (s *Server) Shutdown(ctx context.Context) error {
	if s.srv == nil {
		return nil
	}
	return s.srv.Shutdown(ctx)
}

// clientIP returns the request's remote IP for rate-limiting.
func clientIP(c *gin.Context) string { return c.ClientIP() }

// userIDKey returns the authenticated user id (or "" if anonymous) for
// per-user rate limits.
func userIDKey(c *gin.Context) string { return middleware.UserIDFromContext(c) }

// silence unused-import for response during scaffolding.
var _ = response.CodeOK
