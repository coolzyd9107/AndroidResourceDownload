package handler

import (
	"errors"
	"log/slog"

	"github.com/gin-gonic/gin"

	"resdownload.com/backend/internal/dto"
	"resdownload.com/backend/internal/middleware"
	"resdownload.com/backend/internal/pkg/response"
	"resdownload.com/backend/internal/service"
)

// UpdateHandler exposes the /update/* endpoints.
type UpdateHandler struct {
	svc *service.UpdateService
	log *slog.Logger
}

// NewUpdateHandler constructs an UpdateHandler.
func NewUpdateHandler(svc *service.UpdateService, log *slog.Logger) *UpdateHandler {
	return &UpdateHandler{svc: svc, log: log}
}

// Info handles GET /api/v1/update/info.
func (h *UpdateHandler) Info(c *gin.Context) {
	info, err := h.svc.Info()
	if err != nil {
		logRequestError(h.log, c, "update_info", err)
		response.Fail(c, response.ErrInternal)
		return
	}
	response.OK(c, info)
}

// Resolve handles POST /api/v1/update/resolve.
func (h *UpdateHandler) Resolve(c *gin.Context) {
	var req dto.UpdateResolveRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		writeBadRequest(c, 0, "invalid_request")
		return
	}
	uid := middleware.UserIDFromContext(c)
	resolved, err := h.svc.Resolve(req.EncryptedURL, uid, clientIP(c), userAgent(c))
	if err != nil {
		switch {
		case errors.Is(err, service.ErrUpdateURLExpired):
			response.Fail(c, response.ErrUpdateURLExpired)
		case errors.Is(err, service.ErrUpdateURLInvalid):
			response.Fail(c, response.ErrUpdateURLInvalid)
		default:
			logRequestError(h.log, c, "update_resolve", err)
			response.Fail(c, response.ErrInternal)
		}
		return
	}
	response.OK(c, resolved)
}
