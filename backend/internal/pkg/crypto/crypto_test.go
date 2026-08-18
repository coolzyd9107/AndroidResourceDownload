// Package crypto provides symmetric primitives. This file contains unit
// tests for the encryption + signature flow.
package crypto

import (
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestSuiteEncryptDecryptRoundTrip(t *testing.T) {
	s := NewSuite("cred-secret", "update-secret")
	plaintext := []byte(`{"hello":"world","n":42}`)
	exp := time.Now().Add(time.Hour).Unix()

	enc, err := s.Encrypt(plaintext, exp)
	require.NoError(t, err)
	require.NotEmpty(t, enc.Ciphertext)
	require.NotEmpty(t, enc.Nonce)
	require.NotEmpty(t, enc.Signature)

	dec, err := s.Decrypt(enc, time.Now().Unix())
	require.NoError(t, err)
	assert.Equal(t, plaintext, dec)
}

func TestSuiteRejectsExpired(t *testing.T) {
	s := NewSuite("cred-secret", "update-secret")
	enc, err := s.Encrypt([]byte("x"), time.Now().Add(-time.Minute).Unix())
	require.NoError(t, err)
	_, err = s.Decrypt(enc, time.Now().Unix())
	assert.ErrorIs(t, err, ErrDecryptionFailed)
}

func TestSuiteRejectsTamperedCiphertext(t *testing.T) {
	s := NewSuite("cred-secret", "update-secret")
	enc, err := s.Encrypt([]byte("payload"), time.Now().Add(time.Hour).Unix())
	require.NoError(t, err)

	// Flip one character in the ciphertext (still valid base64).
	tampered := []byte(enc.Ciphertext)
	tampered[0] = 'A'
	if tampered[0] == enc.Ciphertext[0] {
		tampered[0] = 'B'
	}
	enc.Ciphertext = string(tampered)

	_, err = s.Decrypt(enc, time.Now().Unix())
	assert.Error(t, err)
}

func TestSuiteRejectsBadSignature(t *testing.T) {
	s := NewSuite("cred-secret", "update-secret")
	enc, err := s.Encrypt([]byte("payload"), time.Now().Add(time.Hour).Unix())
	require.NoError(t, err)
	enc.Signature = RandomID() // wrong but well-formed
	_, err = s.Decrypt(enc, time.Now().Unix())
	assert.ErrorIs(t, err, ErrSignatureMismatch)
}

func TestRandomIDUniqueness(t *testing.T) {
	ids := make(map[string]struct{}, 1000)
	for i := 0; i < 1000; i++ {
		ids[RandomID()] = struct{}{}
	}
	assert.Len(t, ids, 1000)
}
