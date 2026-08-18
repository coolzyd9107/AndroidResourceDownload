package ratelimit

import (
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
	"golang.org/x/time/rate"
)

func TestInMemoryLimitSingleRule(t *testing.T) {
	l := NewInMemory(Rule{Name: "x", PerKey: rate.Every(time.Second), Burst: 1})
	assert.True(t, l.AllowOne("x", "key1"))
	assert.False(t, l.AllowOne("x", "key1"))
	assert.True(t, l.AllowOne("x", "key2"))
}

func TestAllowAppliesAllRules(t *testing.T) {
	// Burst 0 + zero rate = no tokens ever available.
	strict := Rule{Name: "strict", PerKey: rate.Limit(0), Burst: 0}
	l := NewInMemory(strict)
	assert.False(t, l.Allow("any-key"))
}

func TestRuleIsolation(t *testing.T) {
	short := Rule{Name: "short", PerKey: rate.Every(time.Second), Burst: 1}
	open := Rule{Name: "open", PerKey: rate.Limit(100), Burst: 100}
	l := NewInMemory(short, open)

	// First call passes both.
	assert.True(t, l.Allow("k"))
	// Second call on the same key fails because "short" is exhausted.
	assert.False(t, l.Allow("k"))
}

func TestDefaultRulesStable(t *testing.T) {
	rules := DefaultRules()
	names := map[string]bool{}
	for _, r := range rules {
		names[r.Name] = true
	}
	assert.True(t, names["email_code_per_email"])
	assert.True(t, names["email_code_per_ip"])
	assert.True(t, names["login_per_ip"])
	assert.True(t, names["webdav_per_user"])
	assert.True(t, names["update_per_user"])
}
