package service

import (
	"time"

	"link.mczihan/webdavbox-backend/internal/config"
	"link.mczihan/webdavbox-backend/internal/model"
)

// webdavCfgAdapter implements WebDAVConfigProvider from the loaded config.
type webdavCfgAdapter struct {
	cfg *config.WebDAVConfig
}

// NewWebDAVConfigAdapter wraps the loaded WebDAV config.
func NewWebDAVConfigAdapter(cfg *config.WebDAVConfig) WebDAVConfigProvider {
	return &webdavCfgAdapter{cfg: cfg}
}

func (a *webdavCfgAdapter) BaseURL() string { return a.cfg.BaseURL }

func (a *webdavCfgAdapter) ForUser(role model.Role) (string, string, string, time.Duration) {
	switch role {
	case model.RoleAdmin:
		return a.cfg.Admin.Username, a.cfg.Admin.Password, a.cfg.Admin.RootPath,
			time.Duration(a.cfg.AdminCredentialTTLSeconds) * time.Second
	default:
		return a.cfg.Readonly.Username, a.cfg.Readonly.Password, a.cfg.Readonly.RootPath,
			time.Duration(a.cfg.UserCredentialTTLSeconds) * time.Second
	}
}
