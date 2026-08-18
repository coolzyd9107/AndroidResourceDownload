package service

import (
	"testing"

	"github.com/stretchr/testify/assert"

	"link.mczihan/webdavbox-backend/internal/model"
)

func TestRoleService(t *testing.T) {
	s := NewRoleService([]string{"qq.com"}, []string{"mczihan.link"})

	cases := []struct {
		email string
		want  model.Role
		ok    bool
	}{
		{"user@qq.com", model.RoleUser, true},
		{"User@QQ.com", model.RoleUser, true},
		{"admin@mczihan.link", model.RoleAdmin, true},
		{"admin@mczihan.link ", model.RoleAdmin, true},
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
	s := NewRoleService([]string{"qq.com"}, []string{"mczihan.link"})
	assert.True(t, s.Allowed("u@qq.com"))
	assert.True(t, s.Allowed("a@mczihan.link"))
	assert.False(t, s.Allowed("x@x.com"))
}
