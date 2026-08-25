package service

import (
	"context"
	"encoding/json"
	"errors"
	"net/http"
	"net/http/httptest"
	"net/url"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"resdownload.com/backend/internal/config"
)

func TestGitHubClientExchangeAndFetchSendsRedirectAndPKCE(t *testing.T) {
	var tokenForm url.Values
	mux := http.NewServeMux()
	mux.HandleFunc("/token", func(w http.ResponseWriter, r *http.Request) {
		require.Equal(t, http.MethodPost, r.Method)
		require.Equal(t, "application/json", r.Header.Get("Accept"))
		require.NoError(t, r.ParseForm())
		tokenForm = r.PostForm
		w.Header().Set("Content-Type", "application/json")
		_ = json.NewEncoder(w).Encode(map[string]string{"access_token": "github-token"})
	})
	mux.HandleFunc("/user", func(w http.ResponseWriter, r *http.Request) {
		require.Equal(t, "Bearer github-token", r.Header.Get("Authorization"))
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write([]byte(`{"id":123,"login":"octocat","name":null,"email":null,"avatar_url":"https://avatars.example.com/123"}`))
	})
	server := httptest.NewServer(mux)
	defer server.Close()

	client := NewGitHubClient(&config.GithubConfig{
		ClientID:     "client-id",
		ClientSecret: "client-secret",
		RedirectURI:  testGitHubRedirectURI,
	})
	client.httpClient = server.Client()
	client.tokenURL = server.URL + "/token"
	client.userURL = server.URL + "/user"

	user, err := client.ExchangeAndFetch(
		context.Background(),
		"authorization-code",
		testGitHubRedirectURI,
		testGitHubCodeVerifier,
	)
	require.NoError(t, err)
	require.NotNil(t, user)
	assert.Equal(t, int64(123), user.ID)
	assert.Nil(t, user.Name)
	assert.Nil(t, user.Email)
	require.NotNil(t, tokenForm)
	assert.Equal(t, "client-id", tokenForm.Get("client_id"))
	assert.Equal(t, "client-secret", tokenForm.Get("client_secret"))
	assert.Equal(t, "authorization-code", tokenForm.Get("code"))
	assert.Equal(t, testGitHubRedirectURI, tokenForm.Get("redirect_uri"))
	assert.Equal(t, testGitHubCodeVerifier, tokenForm.Get("code_verifier"))
}

func TestGitHubClientRejectsMismatchedRedirectURIWithoutNetwork(t *testing.T) {
	client := NewGitHubClient(&config.GithubConfig{
		ClientID:     "client-id",
		ClientSecret: "client-secret",
		RedirectURI:  testGitHubRedirectURI,
	})

	_, err := client.ExchangeAndFetch(
		context.Background(),
		"authorization-code",
		"https://attacker.example.com/callback",
		testGitHubCodeVerifier,
	)
	require.Error(t, err)
	assert.ErrorIs(t, err, ErrGithubAuthFailed)
	assert.Contains(t, err.Error(), "redirect URI does not match configuration")
}

func TestGitHubClientRequiresConfiguredRedirectURI(t *testing.T) {
	client := NewGitHubClient(&config.GithubConfig{
		ClientID:     "client-id",
		ClientSecret: "client-secret",
	})

	_, err := client.ExchangeAndFetch(
		context.Background(),
		"authorization-code",
		testGitHubRedirectURI,
		testGitHubCodeVerifier,
	)
	require.Error(t, err)
	assert.ErrorIs(t, err, ErrGithubAuthFailed)
	assert.Contains(t, err.Error(), "redirect URI is not configured")
}

func TestGitHubClientValidatesPKCECodeVerifier(t *testing.T) {
	tests := []struct {
		name     string
		verifier string
	}{
		{name: "missing"},
		{name: "too short", verifier: "short"},
		{name: "invalid character", verifier: "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQ!"},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			client := NewGitHubClient(&config.GithubConfig{
				ClientID:     "client-id",
				ClientSecret: "client-secret",
				RedirectURI:  testGitHubRedirectURI,
			})
			_, err := client.ExchangeAndFetch(
				context.Background(),
				"authorization-code",
				testGitHubRedirectURI,
				tt.verifier,
			)
			require.Error(t, err)
			assert.True(t, errors.Is(err, ErrGithubAuthFailed))
			assert.Contains(t, err.Error(), "invalid PKCE code verifier")
		})
	}
}
