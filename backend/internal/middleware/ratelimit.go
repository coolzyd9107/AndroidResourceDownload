package middleware

import (
	"github.com/gin-gonic/gin"

	"link.mczihan/webdavbox-backend/internal/pkg/response"
	"link.mczihan/webdavbox-backend/internal/ratelimit"
)

// RateLimitByKey applies the given rule to the key returned by keyFn. If
// the rule denies the key, the request is short-circuited with 429.
func RateLimitByKey(l ratelimit.Limiter, ruleName string, keyFn func(c *gin.Context) string) gin.HandlerFunc {
	return func(c *gin.Context) {
		key := keyFn(c)
		if key == "" {
			c.Next()
			return
		}
		if !l.AllowOne(ruleName, key) {
			response.Fail(c, response.ErrRateLimited)
			return
		}
		c.Next()
	}
}

// RateLimitAll applies every rule in l to the same key. Use sparingly.
func RateLimitAll(l ratelimit.Limiter, keyFn func(c *gin.Context) string) gin.HandlerFunc {
	return func(c *gin.Context) {
		key := keyFn(c)
		if key == "" {
			c.Next()
			return
		}
		if !l.Allow(key) {
			response.Fail(c, response.ErrRateLimited)
			return
		}
		c.Next()
	}
}
