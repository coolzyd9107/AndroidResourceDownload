// Package logger configures the global slog logger used by the service.
package logger

import (
	"context"
	"log/slog"
	"os"
	"strings"
)

type ctxKey string

const (
	traceIDKey ctxKey = "trace_id"
	userIDKey  ctxKey = "user_id"
)

// Init returns a *slog.Logger writing JSON to stdout. Level defaults to info.
func Init(env string) *slog.Logger {
	level := slog.LevelInfo
	if strings.EqualFold(env, "dev") {
		level = slog.LevelDebug
	}
	handler := slog.NewJSONHandler(os.Stdout, &slog.HandlerOptions{Level: level})
	logger := slog.New(handler)
	slog.SetDefault(logger)
	return logger
}

// WithTraceID returns a context carrying the trace id.
func WithTraceID(ctx context.Context, traceID string) context.Context {
	return context.WithValue(ctx, traceIDKey, traceID)
}

// TraceID retrieves the trace id, or "" if missing.
func TraceID(ctx context.Context) string {
	if v, ok := ctx.Value(traceIDKey).(string); ok {
		return v
	}
	return ""
}

// WithUserID returns a context carrying the authenticated user id.
func WithUserID(ctx context.Context, userID string) context.Context {
	return context.WithValue(ctx, userIDKey, userID)
}

// UserID retrieves the user id, or "" if missing.
func UserID(ctx context.Context) string {
	if v, ok := ctx.Value(userIDKey).(string); ok {
		return v
	}
	return ""
}
