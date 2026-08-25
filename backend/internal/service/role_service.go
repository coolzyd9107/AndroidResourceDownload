// Package service hosts the business logic composed from the repositories
// and external clients (GitHub, SMTP). Handlers should only orchestrate
// DTOs and forward errors.
package service

import (
	"strings"

	"resdownload.com/backend/internal/model"
)

// RoleService maps emails / identities to roles per 后端.md §6.
type RoleService struct {
	userDomains  []string
	adminDomains []string
}

// NewRoleService builds a RoleService from raw domain lists.
func NewRoleService(userDomains, adminDomains []string) *RoleService {
	return &RoleService{
		userDomains:  normalize(userDomains),
		adminDomains: normalize(adminDomains),
	}
}

// MapEmail returns the role for the supplied email or false if the domain
// is not whitelisted.
func (s *RoleService) MapEmail(email string) (model.Role, bool) {
	domain := extractDomain(email)
	if domain == "" {
		return "", false
	}
	if contains(s.adminDomains, domain) {
		return model.RoleAdmin, true
	}
	if contains(s.userDomains, domain) {
		return model.RoleUser, true
	}
	return "", false
}

// Allowed returns true if the email domain is whitelisted.
func (s *RoleService) Allowed(email string) bool {
	_, ok := s.MapEmail(email)
	return ok
}

func extractDomain(email string) string {
	idx := strings.LastIndex(email, "@")
	if idx <= 0 || idx == len(email)-1 {
		return ""
	}
	return strings.ToLower(strings.TrimSpace(email[idx+1:]))
}

func contains(list []string, item string) bool {
	for _, v := range list {
		if v == item {
			return true
		}
	}
	return false
}

func normalize(in []string) []string {
	out := make([]string, 0, len(in))
	for _, v := range in {
		v = strings.ToLower(strings.TrimSpace(v))
		if v == "" {
			continue
		}
		out = append(out, v)
	}
	return out
}
