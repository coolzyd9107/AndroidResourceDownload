// Package jwt wraps golang-jwt/jwt/v5 with the project's issuer + secret.
package jwt

import (
	"errors"
	"fmt"
	"time"

	"github.com/golang-jwt/jwt/v5"
)

// Claims is the canonical claim set used for both access and refresh tokens.
type Claims struct {
	UserID string `json:"sub"`
	Role   string `json:"role"`
	Type   string `json:"typ"` // "access" or "refresh"
	JTI    string `json:"jti"`
	jwt.RegisteredClaims
}

// Issuer returns the configured JWT issuer (for RegisteredClaims.Issuer).
type Issuer struct {
	secret []byte
	issuer string
}

// NewIssuer builds an Issuer. Secret must be at least 16 bytes.
func NewIssuer(secret, issuer string) (*Issuer, error) {
	if len(secret) < 16 {
		return nil, errors.New("jwt: secret must be at least 16 bytes")
	}
	if issuer == "" {
		issuer = "webdavbox"
	}
	return &Issuer{secret: []byte(secret), issuer: issuer}, nil
}

// Sign issues a new token of the given type with the given TTL.
func (i *Issuer) Sign(userID, role, tokenType, jti string, ttl time.Duration) (string, error) {
	now := time.Now()
	claims := Claims{
		UserID: userID,
		Role:   role,
		Type:   tokenType,
		JTI:    jti,
		RegisteredClaims: jwt.RegisteredClaims{
			Issuer:    i.issuer,
			Subject:   userID,
			IssuedAt:  jwt.NewNumericDate(now),
			ExpiresAt: jwt.NewNumericDate(now.Add(ttl)),
			NotBefore: jwt.NewNumericDate(now),
		},
	}
	tok := jwt.NewWithClaims(jwt.SigningMethodHS256, claims)
	return tok.SignedString(i.secret)
}

// Verify parses the token and returns the claims on success.
func (i *Issuer) Verify(token string) (*Claims, error) {
	parsed, err := jwt.ParseWithClaims(token, &Claims{}, func(t *jwt.Token) (any, error) {
		if t.Method.Alg() != jwt.SigningMethodHS256.Alg() {
			return nil, fmt.Errorf("unexpected signing method: %v", t.Method.Alg())
		}
		return i.secret, nil
	})
	if err != nil {
		return nil, err
	}
	claims, ok := parsed.Claims.(*Claims)
	if !ok || !parsed.Valid {
		return nil, errors.New("jwt: invalid token")
	}
	return claims, nil
}
