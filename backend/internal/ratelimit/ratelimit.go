// Package ratelimit provides a small key-routed limiter abstraction.
// The default in-memory implementation is enough for MVP and the test
// suite; a Redis backend can be plugged in later via the Limiter interface.
package ratelimit

import (
	"sync"
	"time"

	"golang.org/x/time/rate"
)

// Limiter is the abstraction over rate-limiting backends.
type Limiter interface {
	// Allow consumes one token for the given key. Returns false if the
	// caller should be throttled.
	Allow(key string) bool
	// AllowOne applies a single named rule to a key.
	AllowOne(ruleName, key string) bool
}

// Rule describes a single rate-limiting policy.
type Rule struct {
	Name    string
	PerKey  rate.Limit        // tokens per second
	Burst   int               // bucket capacity
	TTL     time.Duration     // eviction window for inactive keys
}

// InMemoryLimiter holds a per-key *rate.Limiter and evicts idle entries.
type InMemoryLimiter struct {
	rules []Rule
	mu    sync.Mutex
	keys  map[string]map[string]*entry // ruleName -> key -> entry
}

type entry struct {
	limiter *rate.Limiter
	last    time.Time
}

// NewInMemory returns a Limiter that enforces every rule against every key.
// Each rule maintains an independent bucket per key, so a single key can be
// throttled on one rule (e.g. "email_code_per_email") without affecting
// other rules.
func NewInMemory(rules ...Rule) *InMemoryLimiter {
	l := &InMemoryLimiter{
		rules: rules,
		keys:  make(map[string]map[string]*entry),
	}
	return l
}

// Allow walks every rule. A key is rejected if ANY rule denies it.
func (l *InMemoryLimiter) Allow(key string) bool {
	now := time.Now()
	l.mu.Lock()
	defer l.mu.Unlock()

	for _, rule := range l.rules {
		bucket, ok := l.keys[rule.Name]
		if !ok {
			bucket = make(map[string]*entry)
			l.keys[rule.Name] = bucket
		}
		e, ok := bucket[key]
		if !ok {
			e = &entry{limiter: rate.NewLimiter(rule.PerKey, rule.Burst)}
			bucket[key] = e
		}
		if rule.TTL > 0 && now.Sub(e.last) > rule.TTL {
			e.limiter = rate.NewLimiter(rule.PerKey, rule.Burst)
		}
		e.last = now
		if !e.limiter.Allow() {
			return false
		}
	}
	return true
}

// AllowOne applies a single named rule to a key.
func (l *InMemoryLimiter) AllowOne(ruleName, key string) bool {
	now := time.Now()
	l.mu.Lock()
	defer l.mu.Unlock()

	for _, rule := range l.rules {
		if rule.Name != ruleName {
			continue
		}
		bucket, ok := l.keys[rule.Name]
		if !ok {
			bucket = make(map[string]*entry)
			l.keys[rule.Name] = bucket
		}
		e, ok := bucket[key]
		if !ok {
			e = &entry{limiter: rate.NewLimiter(rule.PerKey, rule.Burst)}
			bucket[key] = e
		}
		if rule.TTL > 0 && now.Sub(e.last) > rule.TTL {
			e.limiter = rate.NewLimiter(rule.PerKey, rule.Burst)
		}
		e.last = now
		return e.limiter.Allow()
	}
	return true
}

// DefaultRules returns the rule set recommended in 后端.md §17.4.
func DefaultRules() []Rule {
	return []Rule{
		// email code: 1 per 60s per email, 10 per hour per IP
		{Name: "email_code_per_email", PerKey: rate.Every(60 * time.Second), Burst: 1, TTL: 10 * time.Minute},
		{Name: "email_code_per_ip", PerKey: rate.Every(6 * time.Minute), Burst: 10, TTL: time.Hour},
		// login attempts per IP per minute
		{Name: "login_per_ip", PerKey: rate.Every(3 * time.Second), Burst: 20, TTL: time.Minute},
		// webdav credential issuance per user per minute
		{Name: "webdav_per_user", PerKey: rate.Every(6 * time.Second), Burst: 10, TTL: time.Minute},
		// update resolve per user per minute
		{Name: "update_per_user", PerKey: rate.Every(12 * time.Second), Burst: 5, TTL: time.Minute},
	}
}
