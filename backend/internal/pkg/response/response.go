// Package response defines the unified API response envelope and error codes
// shared across all HTTP handlers. See 后端.md §8.3 / §8.4.
package response

import (
	"errors"
	"net/http"

	"github.com/gin-gonic/gin"
)

// Envelope is the wire format returned by every endpoint.
type Envelope struct {
	Code    int    `json:"code"`
	Message string `json:"message"`
	Data    any    `json:"data"`
}

// Code values mirror 后端.md §8.4.
const (
	CodeOK                    = 0
	CodeUnauthorized          = 10001
	CodeTokenExpired          = 10002
	CodeForbidden             = 10003
	CodeEmailDomainNotAllowed = 10004
	CodeInvalidEmailCode      = 10005
	CodeEmailCodeExpired      = 10006
	CodeGithubAuthFailed      = 10007
	CodeQqAuthFailed          = 10008
	CodeCredentialNotFound    = 20001
	CodeCredentialExpired     = 20002
	CodeCredentialInvalid     = 20003
	CodeUpdateURLExpired      = 30001
	CodeUpdateURLInvalid      = 30002
	CodeRateLimited           = 40001
	CodeInternal              = 50000
)

// BusinessError is an error the handler layer can return to short-circuit
// with a specific HTTP status + business code.
type BusinessError struct {
	HTTPStatus int
	Code       int
	Message    string
	Detail     string
}

func (e *BusinessError) Error() string {
	if e.Detail != "" {
		return e.Message + ": " + e.Detail
	}
	return e.Message
}

// New builds a BusinessError. HTTPStatus defaults to 400 when zero.
func New(httpStatus, code int, message string) *BusinessError {
	if httpStatus == 0 {
		httpStatus = http.StatusBadRequest
	}
	return &BusinessError{HTTPStatus: httpStatus, Code: code, Message: message}
}

// WithDetail attaches additional context. Returns the receiver for chaining.
func (e *BusinessError) WithDetail(detail string) *BusinessError {
	e.Detail = detail
	return e
}

// Common errors.
var (
	ErrUnauthorized          = New(http.StatusUnauthorized, CodeUnauthorized, "unauthorized")
	ErrTokenExpired          = New(http.StatusUnauthorized, CodeTokenExpired, "token_expired")
	ErrForbidden             = New(http.StatusForbidden, CodeForbidden, "forbidden")
	ErrEmailDomainNotAllowed = New(http.StatusForbidden, CodeEmailDomainNotAllowed, "email_domain_not_allowed")
	ErrInvalidEmailCode      = New(http.StatusUnauthorized, CodeInvalidEmailCode, "invalid_email_code")
	ErrEmailCodeExpired      = New(http.StatusUnauthorized, CodeEmailCodeExpired, "email_code_expired")
	ErrGithubAuthFailed      = New(http.StatusUnauthorized, CodeGithubAuthFailed, "github_auth_failed")
	ErrQqAuthFailed          = New(http.StatusUnauthorized, CodeQqAuthFailed, "qq_auth_failed")
	ErrCredentialNotFound    = New(http.StatusNotFound, CodeCredentialNotFound, "credential_not_found")
	ErrCredentialExpired     = New(http.StatusGone, CodeCredentialExpired, "credential_expired")
	ErrCredentialInvalid     = New(http.StatusBadRequest, CodeCredentialInvalid, "credential_invalid")
	ErrUpdateURLExpired      = New(http.StatusGone, CodeUpdateURLExpired, "update_url_expired")
	ErrUpdateURLInvalid      = New(http.StatusBadRequest, CodeUpdateURLInvalid, "update_url_invalid")
	ErrRateLimited           = New(http.StatusTooManyRequests, CodeRateLimited, "rate_limited")
	ErrInternal              = New(http.StatusInternalServerError, CodeInternal, "internal_error")
)

// OK writes a 200 envelope with the given payload.
func OK(c *gin.Context, data any) {
	c.JSON(http.StatusOK, Envelope{Code: CodeOK, Message: "ok", Data: data})
}

// Fail writes an error envelope based on a BusinessError. If err is not a
// BusinessError, it falls back to 500 / internal_error.
func Fail(c *gin.Context, err error) {
	var be *BusinessError
	if errors.As(err, &be) {
		c.AbortWithStatusJSON(be.HTTPStatus, Envelope{
			Code:    be.Code,
			Message: be.Message,
			Data:    nil,
		})
		return
	}
	c.AbortWithStatusJSON(http.StatusInternalServerError, Envelope{
		Code:    CodeInternal,
		Message: "internal_error",
		Data:    nil,
	})
}
