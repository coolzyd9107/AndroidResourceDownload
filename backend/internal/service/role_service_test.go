package service

import (
	"testing"

	"github.com/stretchr/testify/assert"

	"resdownload.com/backend/internal/model"
)

func TestRoleService(t *testing.T) {
	s := NewRoleService([]string{"qq.com"}, []string{"admin.example.com"})

	cases := []struct {
		email string
		want  model.Role
		ok    bool
	}{
		{"user@qq.com", model.RoleUser, true},
		{"User@QQ.com", model.RoleUser, true},
		{"admin@admin.example.com", model.RoleAdmin, true},
		{"admin@admin.example.com ", model.RoleAdmin, true},
		{"hacker@gmail.com", "", false},
		{"invalid", "", false},
		{"", "", false},
		{"@qq.com", "", false}, // no local part
	}
	for _, c := range cases {
		got, ok := s.MapEmail(c.email)
		assert.Equalf(t, c.ok, ok, "email=%q", c.email)
		assert.Equalf(t, c.want, got, "email=%q", c.email)
	}
}

func TestRoleServiceAllowed(t *testing.T) {
	s := NewRoleService([]string{"qq.com"}, []string{"admin.example.com"})
	assert.True(t, s.Allowed("u@qq.com"))
	assert.True(t, s.Allowed("a@admin.example.com"))
	assert.False(t, s.Allowed("x@x.com"))
}
