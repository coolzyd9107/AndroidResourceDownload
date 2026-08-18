// Package handler hosts the Gin HTTP handlers.
package handler

import (
	"log/slog"
	"net/http"

	"github.com/gin-gonic/gin"

	"link.mczihan/webdavbox-backend/internal/pkg/response"
)

// Health returns a static "ok" payload. Useful for docker-compose health
// checks and uptime monitors.
func Health(c *gin.Context) {
	response.OK(c, gin.H{"status": "ok"})
}

// clientIP returns the request remote IP (used by several handlers).
func clientIP(c *gin.Context) string { return c.ClientIP() }

// userAgent returns the User-Agent header or "".
func userAgent(c *gin.Context) string { return c.GetHeader("User-Agent") }

// writeBadRequest replies with a 400 using the supplied message.
func writeBadRequest(c *gin.Context, code int, message string) {
	response.Fail(c, response.New(http.StatusBadRequest, code, message))
}

// logRequestError emits a structured warn log for unexpected handler failures.
func logRequestError(log *slog.Logger, c *gin.Context, action string, err error) {
	log.Warn("handler error",
		slog.String("action", action),
		slog.String("path", c.Request.URL.Path),
		slog.String("err", err.Error()),
		slog.String("trace_id", c.GetString("trace_id")),
	)
}
