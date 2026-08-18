// Package middleware contains Gin middlewares shared by all routes.
package middleware

import (
	"log/slog"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/google/uuid"

	"link.mczihan/webdavbox-backend/internal/pkg/logger"
	"link.mczihan/webdavbox-backend/internal/pkg/response"
)

const (
	HeaderTraceID = "X-Trace-Id"
	ctxUserKey    = "auth.user_id"
	ctxRoleKey    = "auth.role"
	ctxJTIKey     = "auth.jti"
)

// TraceID injects a per-request id and stores it on the response header.
func TraceID() gin.HandlerFunc {
	return func(c *gin.Context) {
		id := c.GetHeader(HeaderTraceID)
		if id == "" {
			id = uuid.NewString()
		}
		c.Set("trace_id", id)
		c.Writer.Header().Set(HeaderTraceID, id)
		c.Next()
	}
}

// Recovery converts panics into a 500 response without crashing the process.
func Recovery(log *slog.Logger) gin.HandlerFunc {
	return func(c *gin.Context) {
		defer func() {
			if r := recover(); r != nil {
				log.Error("panic recovered",
					slog.Any("err", r),
					slog.String("path", c.Request.URL.Path),
					slog.String("trace_id", c.GetString("trace_id")),
				)
				response.Fail(c, response.ErrInternal)
			}
		}()
		c.Next()
	}
}

// RequestLogger emits one structured log line per request, with sensitive
// headers redacted.
func RequestLogger(log *slog.Logger) gin.HandlerFunc {
	return func(c *gin.Context) {
		start := time.Now()
		c.Next()
		cost := time.Since(start)

		fields := []any{
			slog.String("trace_id", c.GetString("trace_id")),
			slog.String("method", c.Request.Method),
			slog.String("path", c.Request.URL.Path),
			slog.Int("status", c.Writer.Status()),
			slog.Int64("cost_ms", cost.Milliseconds()),
			slog.String("ip", c.ClientIP()),
		}
		if uid := c.GetString(ctxUserKey); uid != "" {
			fields = append(fields, slog.String("user_id", uid))
		}
		log.Info("http request", fields...)
	}
}

// CORS allows the Android app to call the API from any origin in dev.
func CORS() gin.HandlerFunc {
	return func(c *gin.Context) {
		c.Writer.Header().Set("Access-Control-Allow-Origin", "*")
		c.Writer.Header().Set("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS")
		c.Writer.Header().Set("Access-Control-Allow-Headers", "Authorization,Content-Type,X-Trace-Id")
		if c.Request.Method == "OPTIONS" {
			c.AbortWithStatus(204)
			return
		}
		c.Next()
	}
}

// UserIDFromContext returns the authenticated user id, or "" if absent.
func UserIDFromContext(c *gin.Context) string { return c.GetString(ctxUserKey) }

// RoleFromContext returns the authenticated role, or "" if absent.
func RoleFromContext(c *gin.Context) string { return c.GetString(ctxRoleKey) }

// setUserContext stores authenticated identity on the gin context. Exported
// only via Auth middleware.
func setUserContext(c *gin.Context, userID, role, jti string) {
	c.Set(ctxUserKey, userID)
	c.Set(ctxRoleKey, role)
	c.Set(ctxJTIKey, jti)
	c.Request = c.Request.WithContext(logger.WithUserID(c.Request.Context(), userID))
}
