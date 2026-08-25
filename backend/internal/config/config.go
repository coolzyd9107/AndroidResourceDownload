// Package config loads service configuration from a YAML file with
// environment variable overrides, powered by Viper.
package config

import (
	"fmt"
	"net/url"
	"strings"

	"github.com/spf13/viper"
)

const androidOAuthCallbackURI = "com.resdownload.android://oauth/callback"

// Config is the fully parsed runtime configuration.
type Config struct {
	App                 AppConfig        `mapstructure:"app"`
	Server              ServerConfig     `mapstructure:"server"`
	Database            DatabaseConfig   `mapstructure:"database"`
	JWT                 JWTConfig        `mapstructure:"jwt"`
	Github              GithubConfig     `mapstructure:"github"`
	Qq                  QqConfig         `mapstructure:"qq"`
	Email               EmailConfig      `mapstructure:"email"`
	WebDAV              WebDAVConfig     `mapstructure:"webdav"`
	Credential          CredentialConfig `mapstructure:"credential"`
	Update              UpdateConfig     `mapstructure:"update"`
	AllowedEmailDomains []string         `mapstructure:"allowed-email-domains"`
	AdminEmailDomains   []string         `mapstructure:"admin-email-domains"`
	UserEmailDomains    []string         `mapstructure:"user-email-domains"`
	RateLimit           RateLimitConfig  `mapstructure:"ratelimit"`
}

type AppConfig struct {
	Env  string `mapstructure:"env"`
	Name string `mapstructure:"name"`
}

type ServerConfig struct {
	Port                int `mapstructure:"port"`
	ReadTimeoutSeconds  int `mapstructure:"read-timeout-seconds"`
	WriteTimeoutSeconds int `mapstructure:"write-timeout-seconds"`
}

type DatabaseConfig struct {
	Driver             string `mapstructure:"driver"`
	URL                string `mapstructure:"url"`
	MaxOpenConns       int    `mapstructure:"max-open-conns"`
	MaxIdleConns       int    `mapstructure:"max-idle-conns"`
	ConnMaxLifetimeMin int    `mapstructure:"conn-max-lifetime-minutes"`
}

type JWTConfig struct {
	Secret           string `mapstructure:"secret"`
	AccessTTLSeconds int    `mapstructure:"access-ttl-seconds"`
	RefreshTTLDays   int    `mapstructure:"refresh-ttl-days"`
	Issuer           string `mapstructure:"issuer"`
}

type GithubConfig struct {
	ClientID                 string `mapstructure:"client-id"`
	ClientSecret             string `mapstructure:"client-secret"`
	RedirectURI              string `mapstructure:"redirect-uri"`
	AppRedirectURI           string `mapstructure:"app-redirect-uri"`
	StateTTLSeconds          int    `mapstructure:"state-ttl-seconds"`
	CompletionCodeTTLSeconds int    `mapstructure:"completion-code-ttl-seconds"`
	Mock                     bool   `mapstructure:"mock"`
}

type QqConfig struct {
	AppID       string `mapstructure:"app-id"`
	MeURL       string `mapstructure:"me-url"`
	UserInfoURL string `mapstructure:"user-info-url"`
}

type EmailConfig struct {
	Mode           string     `mapstructure:"mode"`
	OtpTTLSeconds  int        `mapstructure:"otp-ttl-seconds"`
	OtpMaxAttempts int        `mapstructure:"otp-max-attempts"`
	SMTP           SMTPConfig `mapstructure:"smtp"`
}

type SMTPConfig struct {
	Host     string `mapstructure:"host"`
	Port     int    `mapstructure:"port"`
	Username string `mapstructure:"username"`
	Password string `mapstructure:"password"`
	From     string `mapstructure:"from"`
}

type WebDAVConfig struct {
	BaseURL                   string                 `mapstructure:"base-url"`
	Readonly                  WebDAVCredentialConfig `mapstructure:"readonly"`
	Admin                     WebDAVCredentialConfig `mapstructure:"admin"`
	UserCredentialTTLSeconds  int                    `mapstructure:"user-credential-ttl-seconds"`
	AdminCredentialTTLSeconds int                    `mapstructure:"admin-credential-ttl-seconds"`
}

type WebDAVCredentialConfig struct {
	Username string `mapstructure:"username"`
	Password string `mapstructure:"password"`
	RootPath string `mapstructure:"root-path"`
}

type CredentialConfig struct {
	Secret string `mapstructure:"secret"`
}

type UpdateConfig struct {
	Secret     string `mapstructure:"secret"`
	TTLSeconds int    `mapstructure:"ttl-seconds"`
}

type RateLimitConfig struct {
	Enabled bool `mapstructure:"enabled"`
}

// Load reads config.yaml (if present) and applies env overrides. The path
// argument may be empty, in which case only defaults + env are used.
func Load(path string) (*Config, error) {
	v := viper.New()
	setDefaults(v)

	if path != "" {
		v.SetConfigFile(path)
		if err := v.ReadInConfig(); err != nil {
			return nil, fmt.Errorf("config: read %s: %w", path, err)
		}
	}

	v.AutomaticEnv()
	v.SetEnvKeyReplacer(strings.NewReplacer(".", "_", "-", "_"))
	bindEnvs(v)

	cfg := &Config{}
	if err := v.Unmarshal(cfg); err != nil {
		return nil, fmt.Errorf("config: unmarshal: %w", err)
	}
	if err := validate(cfg); err != nil {
		return nil, err
	}
	return cfg, nil
}

func setDefaults(v *viper.Viper) {
	v.SetDefault("app.env", "dev")
	v.SetDefault("app.name", "webdavbox-backend")
	v.SetDefault("server.port", 8080)
	v.SetDefault("server.read-timeout-seconds", 15)
	v.SetDefault("server.write-timeout-seconds", 30)
	v.SetDefault("database.driver", "sqlite")
	v.SetDefault("database.url", "data/dev.db")
	v.SetDefault("database.max-open-conns", 20)
	v.SetDefault("database.max-idle-conns", 5)
	v.SetDefault("database.conn-max-lifetime-minutes", 30)
	v.SetDefault("jwt.secret", "change-me-to-a-32-byte-random-string")
	v.SetDefault("jwt.access-ttl-seconds", 3600)
	v.SetDefault("jwt.refresh-ttl-days", 30)
	v.SetDefault("jwt.issuer", "webdavbox-backend")
	v.SetDefault("github.client-id", "")
	v.SetDefault("github.client-secret", "")
	v.SetDefault("github.redirect-uri", "https://api.example.com/api/v1/auth/github/callback")
	v.SetDefault("github.app-redirect-uri", androidOAuthCallbackURI)
	v.SetDefault("github.state-ttl-seconds", 600)
	v.SetDefault("github.completion-code-ttl-seconds", 90)
	v.SetDefault("github.mock", false)
	v.SetDefault("qq.app-id", "")
	v.SetDefault("qq.me-url", "https://graph.qq.com/oauth2.0/me")
	v.SetDefault("qq.user-info-url", "https://graph.qq.com/user/get_user_info")
	v.SetDefault("email.mode", "console")
	v.SetDefault("email.otp-ttl-seconds", 300)
	v.SetDefault("email.otp-max-attempts", 5)
	v.SetDefault("email.smtp.port", 465)
	v.SetDefault("email.smtp.from", "no-reply@example.com")
	v.SetDefault("webdav.base-url", "https://dav.example.com")
	v.SetDefault("webdav.readonly.username", "readonly_user")
	v.SetDefault("webdav.readonly.password", "readonly_password")
	v.SetDefault("webdav.readonly.root-path", "/")
	v.SetDefault("webdav.admin.username", "admin_user")
	v.SetDefault("webdav.admin.password", "admin_password")
	v.SetDefault("webdav.admin.root-path", "/")
	v.SetDefault("webdav.user-credential-ttl-seconds", 3600)
	v.SetDefault("webdav.admin-credential-ttl-seconds", 900)
	v.SetDefault("credential.secret", "change-me-to-another-32-byte-random-string")
	v.SetDefault("update.secret", "change-me-too-32-bytes")
	v.SetDefault("update.ttl-seconds", 300)
	v.SetDefault("ratelimit.enabled", true)
	v.SetDefault("allowed-email-domains", []string{"qq.com"})
	v.SetDefault("admin-email-domains", []string{})
	v.SetDefault("user-email-domains", []string{"qq.com"})
}

func bindEnvs(v *viper.Viper) {
	keys := []string{
		"app.env", "app.name",
		"server.port", "server.read-timeout-seconds", "server.write-timeout-seconds",
		"database.driver", "database.url",
		"database.max-open-conns", "database.max-idle-conns", "database.conn-max-lifetime-minutes",
		"jwt.secret", "jwt.access-ttl-seconds", "jwt.refresh-ttl-days", "jwt.issuer",
		"github.client-id", "github.client-secret", "github.redirect-uri", "github.app-redirect-uri",
		"github.state-ttl-seconds", "github.completion-code-ttl-seconds", "github.mock",
		"qq.app-id", "qq.me-url", "qq.user-info-url",
		"email.mode", "email.otp-ttl-seconds", "email.otp-max-attempts",
		"email.smtp.host", "email.smtp.port", "email.smtp.username", "email.smtp.password", "email.smtp.from",
		"webdav.base-url",
		"webdav.readonly.username", "webdav.readonly.password", "webdav.readonly.root-path",
		"webdav.admin.username", "webdav.admin.password", "webdav.admin.root-path",
		"webdav.user-credential-ttl-seconds", "webdav.admin-credential-ttl-seconds",
		"credential.secret",
		"update.secret", "update.ttl-seconds",
		"ratelimit.enabled",
	}
	for _, k := range keys {
		_ = v.BindEnv(k)
	}

	// Comma-separated email-domain lists.
	_ = v.BindEnv("allowed-email-domains")
	_ = v.BindEnv("admin-email-domains")
	_ = v.BindEnv("user-email-domains")
}

func validate(cfg *Config) error {
	if cfg.JWT.AccessTTLSeconds <= 0 {
		return fmt.Errorf("config: jwt.access-ttl-seconds must be > 0")
	}
	if cfg.JWT.RefreshTTLDays <= 0 {
		return fmt.Errorf("config: jwt.refresh-ttl-days must be > 0")
	}
	if cfg.Database.Driver != "sqlite" && cfg.Database.Driver != "postgres" {
		return fmt.Errorf("config: database.driver must be sqlite or postgres")
	}
	if cfg.Github.StateTTLSeconds <= 0 || cfg.Github.CompletionCodeTTLSeconds <= 0 {
		return fmt.Errorf("config: github OAuth TTLs must be > 0")
	}
	if cfg.Github.AppRedirectURI != androidOAuthCallbackURI {
		return fmt.Errorf("config: github.app-redirect-uri must be %s", androidOAuthCallbackURI)
	}
	if cfg.App.Env == "prod" && !cfg.Github.Mock {
		if cfg.Github.ClientID == "" || cfg.Github.ClientSecret == "" {
			return fmt.Errorf("config: GitHub client credentials are required in prod")
		}
		if cfg.Qq.AppID == "" {
			return fmt.Errorf("config: qq.app-id is required in prod")
		}
		callback, err := url.Parse(cfg.Github.RedirectURI)
		if err != nil || callback.Scheme != "https" || callback.Host == "" {
			return fmt.Errorf("config: github.redirect-uri must be an absolute HTTPS URL in prod")
		}
	}
	return nil
}
