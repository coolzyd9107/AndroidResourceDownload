package middleware

import (
	"strings"

	"github.com/gin-gonic/gin"

	"resdownload.com/backend/internal/pkg/jwt"
	"resdownload.com/backend/internal/pkg/response"
)

// Auth enforces a valid Bearer access token. On success, the user id and
// role are stored on the gin context and the request continues.
func Auth(issuer *jwt.Issuer) gin.HandlerFunc {
	return func(c *gin.Context) {
		header := c.GetHeader("Authorization")
		if header == "" || !strings.HasPrefix(header, "Bearer ") {
			response.Fail(c, response.ErrUnauthorized)
			return
		}
		raw := strings.TrimSpace(strings.TrimPrefix(header, "Bearer "))
		if raw == "" {
			response.Fail(c, response.ErrUnauthorized)
			return
		}
		claims, err := issuer.Verify(raw)
		if err != nil {
			response.Fail(c, response.ErrTokenExpired)
			return
		}
		if claims.Type != "access" {
			response.Fail(c, response.ErrUnauthorized)
			return
		}
		setUserContext(c, claims.UserID, claims.Role, claims.JTI)
		c.Next()
	}
}
