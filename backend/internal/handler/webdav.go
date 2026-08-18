package handler

import (
	"log/slog"

	"github.com/gin-gonic/gin"

	"link.mczihan/webdavbox-backend/internal/dto"
	"link.mczihan/webdavbox-backend/internal/middleware"
	"link.mczihan/webdavbox-backend/internal/model"
	"link.mczihan/webdavbox-backend/internal/pkg/response"
	"link.mczihan/webdavbox-backend/internal/service"
)

// WebDAVHandler exposes the /webdav/* endpoints.
type WebDAVHandler struct {
	svc *service.CredentialService
	log *slog.Logger
}

// NewWebDAVHandler constructs a WebDAVHandler.
func NewWebDAVHandler(svc *service.CredentialService, log *slog.Logger) *WebDAVHandler {
	return &WebDAVHandler{svc: svc, log: log}
}

// Issue handles POST /api/v1/webdav/credential.
func (h *WebDAVHandler) Issue(c *gin.Context) {
	uid := middleware.UserIDFromContext(c)
	role := model.Role(middleware.RoleFromContext(c))
	if role == "" {
		response.Fail(c, response.ErrUnauthorized)
		return
	}
	// Body is optional in MVP.
	var req dto.CredentialRequest
	_ = c.ShouldBindJSON(&req)

	cred, _, err := h.svc.IssuePlaintext(role)
	if err != nil {
		logRequestError(h.log, c, "webdav_credential", err)
		response.Fail(c, response.ErrCredentialNotFound)
		return
	}
	h.svc.Log(c.Request.Context(), uid, string(role), cred.Permission, clientIP(c), userAgent(c))
	response.OK(c, cred)
}
