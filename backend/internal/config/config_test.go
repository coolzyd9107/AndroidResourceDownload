package config

import (
	"os"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestLoadDefaults(t *testing.T) {
	// Make sure no env overrides leak in.
	t.Setenv("APP_ENV", "")
	t.Setenv("SERVER_PORT", "")
	t.Setenv("DATABASE_DRIVER", "")
	t.Setenv("JWT_SECRET", "")
	_ = os.Unsetenv("APP_ENV")

	cfg, err := Load("")
	require.NoError(t, err)
	assert.Equal(t, "dev", cfg.App.Env)
	assert.Equal(t, 8080, cfg.Server.Port)
	assert.Equal(t, "sqlite", cfg.Database.Driver)
}

func TestEnvOverrides(t *testing.T) {
	t.Setenv("SERVER_PORT", "9090")
	t.Setenv("DATABASE_DRIVER", "postgres")
	t.Setenv("DATABASE_URL", "postgres://localhost/x")
	t.Setenv("JWT_SECRET", "a-very-long-secret-string-here")

	cfg, err := Load("")
	require.NoError(t, err)
	assert.Equal(t, 9090, cfg.Server.Port)
	assert.Equal(t, "postgres", cfg.Database.Driver)
	assert.Equal(t, "postgres://localhost/x", cfg.Database.URL)
}

func TestInvalidDriver(t *testing.T) {
	t.Setenv("DATABASE_DRIVER", "oracle")
	_, err := Load("")
	assert.Error(t, err)
}

func TestRejectsStaleAndroidOAuthCallback(t *testing.T) {
	t.Setenv("GITHUB_APP_REDIRECT_URI", "link.mczihan.androidresourcedownload://oauth/callback")

	_, err := Load("")
	require.EqualError(t, err, "config: github.app-redirect-uri must be com.resdownload.android://oauth/callback")
}
