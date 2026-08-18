// Package crypto provides the symmetric primitives used to encrypt WebDAV
// credentials and update URLs (AES-256-GCM with HMAC-SHA256 signatures).
//
// The MVP keeps a single pre-shared key per concern. ECDH-ES (方案 B in
// 后端.md §11.4) is left as a future hook via EncryptWithEphemeralKey stub.
package crypto

import (
	"crypto/aes"
	"crypto/cipher"
	"crypto/hmac"
	"crypto/rand"
	"crypto/sha256"
	"encoding/base64"
	"errors"
	"io"
)

// ErrSignatureMismatch is returned when the HMAC signature does not match.
var ErrSignatureMismatch = errors.New("crypto: signature mismatch")

// ErrDecryptionFailed is returned when the AES-GCM tag verification fails or
// the ciphertext is malformed.
var ErrDecryptionFailed = errors.New("crypto: decryption failed")

// Suite bundles the AES + HMAC secrets used by the credential / update flows.
type Suite struct {
	aesKey  []byte // 32 bytes
	hmacKey []byte
}

// NewSuite derives both keys from the two provided secrets via SHA-256.
func NewSuite(credentialSecret, updateSecret string) *Suite {
	credSum := sha256.Sum256([]byte("webdavbox-credential|" + credentialSecret))
	signSum := sha256.Sum256([]byte("webdavbox-signature|" + updateSecret))
	return &Suite{
		aesKey:  credSum[:],
		hmacKey: signSum[:],
	}
}

// Encrypted is the wire representation of a sealed payload.
type Encrypted struct {
	Ciphertext string // base64-url, no padding
	Nonce      string // base64-url
	ExpiresAt  int64  // unix seconds
	Signature  string // base64-url HMAC-SHA256
}

// Encrypt seals plaintext (JSON-encoded by the caller) with AES-256-GCM and
// returns the encrypted envelope signed with HMAC-SHA256.
func (s *Suite) Encrypt(plaintext []byte, expiresAt int64) (*Encrypted, error) {
	block, err := aes.NewCipher(s.aesKey)
	if err != nil {
		return nil, err
	}
	gcm, err := cipher.NewGCM(block)
	if err != nil {
		return nil, err
	}
	nonce := make([]byte, gcm.NonceSize())
	if _, err := io.ReadFull(rand.Reader, nonce); err != nil {
		return nil, err
	}
	ct := gcm.Seal(nil, nonce, plaintext, nil)

	ctB64 := base64.RawURLEncoding.EncodeToString(ct)
	sig := s.Sign(ctB64, expiresAt)

	return &Encrypted{
		Ciphertext: ctB64,
		Nonce:      base64.RawURLEncoding.EncodeToString(nonce),
		ExpiresAt:  expiresAt,
		Signature:  sig,
	}, nil
}

// Decrypt verifies the signature, the expiry, then opens the AES-GCM seal.
func (s *Suite) Decrypt(enc *Encrypted, now int64) ([]byte, error) {
	if enc == nil {
		return nil, ErrDecryptionFailed
	}
	if !s.Verify(enc.Ciphertext, enc.ExpiresAt, enc.Signature) {
		return nil, ErrSignatureMismatch
	}
	if now > enc.ExpiresAt {
		return nil, ErrDecryptionFailed
	}
	ct, err := base64.RawURLEncoding.DecodeString(enc.Ciphertext)
	if err != nil {
		return nil, ErrDecryptionFailed
	}
	nonce, err := base64.RawURLEncoding.DecodeString(enc.Nonce)
	if err != nil {
		return nil, ErrDecryptionFailed
	}
	block, err := aes.NewCipher(s.aesKey)
	if err != nil {
		return nil, err
	}
	gcm, err := cipher.NewGCM(block)
	if err != nil {
		return nil, err
	}
	pt, err := gcm.Open(nil, nonce, ct, nil)
	if err != nil {
		return nil, ErrDecryptionFailed
	}
	return pt, nil
}

// Sign returns base64-url(HMAC-SHA256(key, ct || "." || expiresAt)).
func (s *Suite) Sign(ctB64 string, expiresAt int64) string {
	mac := hmac.New(sha256.New, s.hmacKey)
	mac.Write([]byte(ctB64))
	mac.Write([]byte{'.'})
	var exp [8]byte
	exp[0] = byte(expiresAt >> 56)
	exp[1] = byte(expiresAt >> 48)
	exp[2] = byte(expiresAt >> 40)
	exp[3] = byte(expiresAt >> 32)
	exp[4] = byte(expiresAt >> 24)
	exp[5] = byte(expiresAt >> 16)
	exp[6] = byte(expiresAt >> 8)
	exp[7] = byte(expiresAt)
	mac.Write(exp[:])
	return base64.RawURLEncoding.EncodeToString(mac.Sum(nil))
}

// Verify checks the signature with a constant-time comparison.
func (s *Suite) Verify(ctB64 string, expiresAt int64, sigB64 string) bool {
	expected := s.Sign(ctB64, expiresAt)
	if len(expected) != len(sigB64) {
		return false
	}
	return hmac.Equal([]byte(expected), []byte(sigB64))
}

// RandomID returns a 16-byte URL-safe random string, suitable for jti values.
func RandomID() string {
	b := make([]byte, 16)
	if _, err := io.ReadFull(rand.Reader, b); err != nil {
		// rand.Reader on Linux never returns an error; fall back to a fixed
		// marker so the caller still gets a deterministic value to log.
		return "jti-unavailable"
	}
	return base64.RawURLEncoding.EncodeToString(b)
}
