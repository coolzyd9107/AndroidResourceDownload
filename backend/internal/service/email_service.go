package service

import (
	"context"
	"crypto/rand"
	"errors"
	"fmt"
	"log/slog"
	mathrand "math/rand"
	netmail "net/smtp"
	"strings"
	"time"

	"github.com/google/uuid"
	"golang.org/x/crypto/bcrypt"

	"link.mczihan/webdavbox-backend/internal/config"
	"link.mczihan/webdavbox-backend/internal/model"
	"link.mczihan/webdavbox-backend/internal/pkg/response"
	"link.mczihan/webdavbox-backend/internal/repository"
)

// EmailService generates, stores, and verifies OTP codes.
type EmailService struct {
	cfg   *config.EmailConfig
	codes *repository.EmailCodeRepo
	log   *slog.Logger
}

// NewEmailService constructs an EmailService.
func NewEmailService(cfg *config.EmailConfig, codes *repository.EmailCodeRepo, log *slog.Logger) *EmailService {
	return &EmailService{cfg: cfg, codes: codes, log: log}
}

// Generate creates a new OTP, persists its bcrypt hash, and returns the
// plaintext (which the caller must deliver to the user) and the TTL.
func (s *EmailService) Generate(ctx context.Context, email string) (string, time.Duration, error) {
	code := randomDigits(6)
	hash, err := bcrypt.GenerateFromPassword([]byte(code), bcrypt.DefaultCost)
	if err != nil {
		return "", 0, fmt.Errorf("email_service: hash: %w", err)
	}
	now := time.Now()
	ttl := time.Duration(s.cfg.OtpTTLSeconds) * time.Second
	record := &model.EmailVerificationCode{
		ID:        uuid.NewString(),
		Email:     strings.ToLower(strings.TrimSpace(email)),
		CodeHash:  string(hash),
		Purpose:   "LOGIN",
		ExpiresAt: now.Add(ttl),
		CreatedAt: now,
	}
	if err := s.codes.Create(record); err != nil {
		return "", 0, err
	}

	s.deliver(ctx, email, code)
	return code, ttl, nil
}

// Verify checks the supplied plaintext against the latest unconsumed code
// for the email. Increments attempts on mismatch and returns the appropriate
// business error.
func (s *EmailService) Verify(email, code string) (*model.EmailVerificationCode, error) {
	now := time.Now()
	normalized := strings.ToLower(strings.TrimSpace(email))
	record, err := s.codes.LatestUnconsumed(normalized, now)
	if err != nil {
		return nil, err
	}
	if record == nil {
		return nil, response.ErrEmailCodeExpired
	}
	if record.Attempts >= s.cfg.OtpMaxAttempts {
		return nil, response.ErrInvalidEmailCode
	}
	if err := bcrypt.CompareHashAndPassword([]byte(record.CodeHash), []byte(code)); err != nil {
		_ = s.codes.IncrementAttempts(record.ID)
		return nil, response.ErrInvalidEmailCode
	}
	if err := s.codes.MarkConsumed(record.ID, now); err != nil {
		return nil, err
	}
	return record, nil
}

// deliver either sends via SMTP (async) or logs the code to stdout (dev).
func (s *EmailService) deliver(ctx context.Context, to, code string) {
	mode := strings.ToLower(s.cfg.Mode)
	switch mode {
	case "", "console":
		s.log.Info("email_service: dev OTP delivery",
			slog.String("to", to),
			slog.String("code", code),
			slog.Int("ttl_seconds", s.cfg.OtpTTLSeconds),
		)
		return
	case "smtp":
		smtpCfg := s.cfg.SMTP
		addr := fmt.Sprintf("%s:%d", smtpCfg.Host, smtpCfg.Port)
		body := fmt.Sprintf(
			"Subject: WebDAVBox login code\r\nFrom: %s\r\nTo: %s\r\n\r\nYour verification code is: %s\r\nIt expires in %d seconds.\r\n",
			smtpCfg.From, to, code, s.cfg.OtpTTLSeconds,
		)
		var auth netmail.Auth
		if smtpCfg.Username != "" {
			auth = netmail.PlainAuth("", smtpCfg.Username, smtpCfg.Password, smtpCfg.Host)
		}
		go func() {
			if err := netmail.SendMail(addr, auth, smtpCfg.From, []string{to}, []byte(body)); err != nil {
				s.log.Error("email_service: smtp send failed", slog.String("err", err.Error()))
			}
		}()
		_ = ctx
		return
	default:
		s.log.Warn("email_service: unknown delivery mode, falling back to console",
			slog.String("mode", mode),
			slog.String("to", to),
			slog.String("code", code),
		)
	}
}

// ErrUnknownEmailMode is reserved for future use.
var ErrUnknownEmailMode = errors.New("email_service: unknown delivery mode")

func randomDigits(n int) string {
	const digits = "0123456789"
	b := make([]byte, n)
	if _, err := rand.Read(b); err != nil {
		for i := range b {
			b[i] = digits[mathrand.Intn(len(digits))]
		}
		return string(b)
	}
	for i, v := range b {
		b[i] = digits[int(v)%len(digits)]
	}
	return string(b)
}
