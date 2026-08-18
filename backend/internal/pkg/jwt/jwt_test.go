package jwt

import (
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestIssueAndVerify(t *testing.T) {
	i, err := NewIssuer("this-is-a-test-secret", "test")
	require.NoError(t, err)

	tok, err := i.Sign("u_1", "USER", "access", "jti-1", time.Minute)
	require.NoError(t, err)
	require.NotEmpty(t, tok)

	claims, err := i.Verify(tok)
	require.NoError(t, err)
	assert.Equal(t, "u_1", claims.UserID)
	assert.Equal(t, "USER", claims.Role)
	assert.Equal(t, "access", claims.Type)
	assert.Equal(t, "jti-1", claims.JTI)
	assert.Equal(t, "test", claims.Issuer)
}

func TestExpired(t *testing.T) {
	i, err := NewIssuer("this-is-a-test-secret", "test")
	require.NoError(t, err)

	tok, err := i.Sign("u_1", "USER", "access", "jti-2", -time.Second)
	require.NoError(t, err)
	_, err = i.Verify(tok)
	assert.Error(t, err)
}

func TestWrongSecret(t *testing.T) {
	a, _ := NewIssuer("first-secret-of-32-bytes-or-more", "test")
	b, _ := NewIssuer("another-secret-of-32-bytes-or-more", "test")

	tok, err := a.Sign("u_1", "USER", "access", "jti-3", time.Minute)
	require.NoError(t, err)

	_, err = b.Verify(tok)
	assert.Error(t, err)
}

func TestShortSecretRejected(t *testing.T) {
	_, err := NewIssuer("short", "test")
	assert.Error(t, err)
}
