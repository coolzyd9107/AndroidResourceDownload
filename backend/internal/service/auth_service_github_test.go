package service

import (
	"context"
	"io"
	"log/slog"
	"path/filepath"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"resdownload.com/backend/internal/config"
	"resdownload.com/backend/internal/model"
	"resdownload.com/backend/internal/pkg/jwt"
	"resdownload.com/backend/internal/repository"
)

const (
	testGitHubRedirectURI  = "https://api.example.com/callback"
	testGitHubCodeVerifier = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-._~"
)

type githubOAuthStub struct {
	user         *GitHubUser
	err          error
	code         string
	redirectURI  string
	codeVerifier string
}

func (s *githubOAuthStub) ExchangeAndFetch(_ context.Context, code, redirectURI, codeVerifier string) (*GitHubUser, error) {
	s.code = code
	s.redirectURI = redirectURI
	s.codeVerifier = codeVerifier
	return s.user, s.err
}

func TestGithubLoginWhitelistAdmin(t *testing.T) {
	githubUser := &GitHubUser{ID: 101, Login: "admin-user"}
	auth, repos, github := newGithubAuthTestService(t, githubUser)
	require.NoError(t, repos.AdminGithub.Create(&model.AdminGithubUser{GithubID: githubUser.ID}))

	result, err := auth.GithubLogin(
		context.Background(),
		"authorization-code",
		testGitHubRedirectURI,
		testGitHubCodeVerifier,
		"device-1",
	)
	require.NoError(t, err)
	assert.Equal(t, string(model.RoleAdmin), result.User.Role)
	assert.Equal(t, "authorization-code", github.code)
	assert.Equal(t, testGitHubRedirectURI, github.redirectURI)
	assert.Equal(t, testGitHubCodeVerifier, github.codeVerifier)

	stored, err := repos.Users.GetByGithubID(githubUser.ID)
	require.NoError(t, err)
	require.NotNil(t, stored)
	assert.Equal(t, model.RoleAdmin, stored.Role)
	require.NotNil(t, stored.RoleSource)
	assert.Equal(t, string(model.RoleSourceGithubWhitelist), *stored.RoleSource)
}

func TestGithubLoginNonWhitelistUserAcceptsArbitraryEmail(t *testing.T) {
	name := "Outside Domain"
	email := " Person@Example.ORG "
	githubUser := &GitHubUser{
		ID:        202,
		Login:     "outside-user",
		Name:      &name,
		Email:     &email,
		AvatarURL: "https://avatars.example.com/202",
	}
	auth, repos, _ := newGithubAuthTestService(t, githubUser)

	result, err := auth.GithubLogin(
		context.Background(),
		"authorization-code",
		testGitHubRedirectURI,
		testGitHubCodeVerifier,
		"",
	)
	require.NoError(t, err)
	assert.Equal(t, string(model.RoleUser), result.User.Role)
	require.NotNil(t, result.User.Email)
	assert.Equal(t, "person@example.org", *result.User.Email)
	require.NotNil(t, result.User.Name)
	assert.Equal(t, name, *result.User.Name)

	stored, err := repos.Users.GetByGithubID(githubUser.ID)
	require.NoError(t, err)
	require.NotNil(t, stored)
	assert.Equal(t, model.RoleUser, stored.Role)
	require.NotNil(t, stored.RoleSource)
	assert.Equal(t, string(model.RoleSourceGithubDefault), *stored.RoleSource)

	identity, err := repos.Identities.GetByProvider("github", int64ToString(githubUser.ID))
	require.NoError(t, err)
	require.NotNil(t, identity)
	require.NotNil(t, identity.Email)
	assert.Equal(t, "person@example.org", *identity.Email)
	require.NotNil(t, identity.ProviderLogin)
	assert.Equal(t, githubUser.Login, *identity.ProviderLogin)
}

func TestGithubLoginDowngradesRemovedWhitelistAdmin(t *testing.T) {
	githubUser := &GitHubUser{ID: 303, Login: "former-admin"}
	auth, repos, _ := newGithubAuthTestService(t, githubUser)
	require.NoError(t, repos.AdminGithub.Create(&model.AdminGithubUser{GithubID: githubUser.ID}))

	first, err := auth.GithubLogin(
		context.Background(),
		"first-code",
		testGitHubRedirectURI,
		testGitHubCodeVerifier,
		"",
	)
	require.NoError(t, err)
	assert.Equal(t, string(model.RoleAdmin), first.User.Role)

	require.NoError(t, repos.DB.Delete(&model.AdminGithubUser{}, "github_id = ?", githubUser.ID).Error)
	second, err := auth.GithubLogin(
		context.Background(),
		"second-code",
		testGitHubRedirectURI,
		testGitHubCodeVerifier,
		"",
	)
	require.NoError(t, err)
	assert.Equal(t, string(model.RoleUser), second.User.Role)

	stored, err := repos.Users.GetByGithubID(githubUser.ID)
	require.NoError(t, err)
	require.NotNil(t, stored)
	assert.Equal(t, model.RoleUser, stored.Role)
	require.NotNil(t, stored.RoleSource)
	assert.Equal(t, string(model.RoleSourceGithubDefault), *stored.RoleSource)
}

func TestGithubLoginPreservesNullableNameAndEmail(t *testing.T) {
	tests := []struct {
		name       string
		githubName *string
		email      *string
	}{
		{name: "null values"},
		{name: "empty values", githubName: stringPointer(""), email: stringPointer("")},
	}

	for i, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			githubUser := &GitHubUser{
				ID:    int64(400 + i),
				Login: "nullable-user",
				Name:  tt.githubName,
				Email: tt.email,
			}
			auth, repos, _ := newGithubAuthTestService(t, githubUser)

			result, err := auth.GithubLogin(
				context.Background(),
				"authorization-code",
				testGitHubRedirectURI,
				testGitHubCodeVerifier,
				"",
			)
			require.NoError(t, err)
			assert.Nil(t, result.User.Name)
			assert.Nil(t, result.User.Email)

			stored, err := repos.Users.GetByGithubID(githubUser.ID)
			require.NoError(t, err)
			require.NotNil(t, stored)
			assert.Nil(t, stored.Name)
			assert.Nil(t, stored.Email)

			identity, err := repos.Identities.GetByProvider("github", int64ToString(githubUser.ID))
			require.NoError(t, err)
			require.NotNil(t, identity)
			assert.Nil(t, identity.Email)
		})
	}
}

func TestGithubLoginUpdatesNullableProfileFields(t *testing.T) {
	name := "Initial Name"
	email := "initial@example.net"
	githubUser := &GitHubUser{
		ID:    404,
		Login: "changing-user",
		Name:  &name,
		Email: &email,
	}
	auth, repos, _ := newGithubAuthTestService(t, githubUser)

	_, err := auth.GithubLogin(
		context.Background(),
		"first-code",
		testGitHubRedirectURI,
		testGitHubCodeVerifier,
		"",
	)
	require.NoError(t, err)

	githubUser.Name = nil
	githubUser.Email = nil
	result, err := auth.GithubLogin(
		context.Background(),
		"second-code",
		testGitHubRedirectURI,
		testGitHubCodeVerifier,
		"",
	)
	require.NoError(t, err)
	assert.Nil(t, result.User.Name)
	assert.Nil(t, result.User.Email)

	stored, err := repos.Users.GetByGithubID(githubUser.ID)
	require.NoError(t, err)
	require.NotNil(t, stored)
	assert.Nil(t, stored.Name)
	assert.Nil(t, stored.Email)

	identity, err := repos.Identities.GetByProvider("github", int64ToString(githubUser.ID))
	require.NoError(t, err)
	require.NotNil(t, identity)
	assert.Nil(t, identity.Email)
}

func TestGithubLoginAcceptsEmailAlreadyOwnedByAnotherAccount(t *testing.T) {
	email := "shared@example.net"
	githubUser := &GitHubUser{
		ID:    405,
		Login: "shared-email-user",
		Email: &email,
	}
	auth, repos, _ := newGithubAuthTestService(t, githubUser)
	require.NoError(t, repos.Users.Create(&model.User{
		ID:     "existing-email-user",
		Email:  &email,
		Role:   model.RoleUser,
		Status: model.UserStatusActive,
	}))

	result, err := auth.GithubLogin(
		context.Background(),
		"authorization-code",
		testGitHubRedirectURI,
		testGitHubCodeVerifier,
		"",
	)
	require.NoError(t, err)
	assert.Equal(t, string(model.RoleUser), result.User.Role)
	assert.Nil(t, result.User.Email)

	identity, err := repos.Identities.GetByProvider("github", int64ToString(githubUser.ID))
	require.NoError(t, err)
	require.NotNil(t, identity)
	require.NotNil(t, identity.Email)
	assert.Equal(t, email, *identity.Email)
}

func newGithubAuthTestService(t *testing.T, githubUser *GitHubUser) (*AuthService, *repository.Repos, *githubOAuthStub) {
	t.Helper()
	cfg := &config.Config{
		Database: config.DatabaseConfig{
			Driver:             "sqlite",
			URL:                filepath.Join(t.TempDir(), "auth.db"),
			MaxOpenConns:       1,
			MaxIdleConns:       1,
			ConnMaxLifetimeMin: 1,
		},
	}
	db, err := repository.Open(cfg)
	require.NoError(t, err)
	require.NoError(t, repository.Migrate(db))
	repos := repository.New(db)

	issuer, err := jwt.NewIssuer("github-auth-test-secret-32-bytes", "github-auth-test")
	require.NoError(t, err)
	tokens := NewTokenService(issuer, 3600, 30, repos.RefreshTokens)
	github := &githubOAuthStub{user: githubUser}
	log := slog.New(slog.NewTextHandler(io.Discard, nil))
	auth := NewAuthService(
		repos.Users,
		repos.Identities,
		repos.AdminGithub,
		tokens,
		NewRoleService(nil, nil),
		nil,
		github,
		repos.AuditLogs,
		log,
	)
	return auth, repos, github
}

func stringPointer(value string) *string { return &value }
