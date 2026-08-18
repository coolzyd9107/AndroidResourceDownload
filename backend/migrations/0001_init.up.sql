-- 0001_init.up.sql -- initial schema for WebDAVBox backend (PostgreSQL).
-- SQLite uses GORM AutoMigrate instead.

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE IF NOT EXISTS users (
    id            VARCHAR(36) PRIMARY KEY,
    email         VARCHAR(255) UNIQUE,
    github_id     BIGINT UNIQUE,
    github_login  VARCHAR(255),
    name          VARCHAR(255),
    avatar_url    TEXT,
    role          VARCHAR(20) NOT NULL DEFAULT 'USER',
    role_source   VARCHAR(30),
    status        VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS auth_identities (
    id                 VARCHAR(36) PRIMARY KEY,
    user_id            VARCHAR(36) NOT NULL REFERENCES users(id),
    provider           VARCHAR(20) NOT NULL,
    provider_user_id   VARCHAR(255) NOT NULL,
    provider_login     VARCHAR(255),
    email              VARCHAR(255),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(provider, provider_user_id)
);
CREATE INDEX IF NOT EXISTS idx_auth_identities_user_id ON auth_identities(user_id);

CREATE TABLE IF NOT EXISTS email_verification_codes (
    id          VARCHAR(36) PRIMARY KEY,
    email       VARCHAR(255) NOT NULL,
    code_hash   VARCHAR(255) NOT NULL,
    purpose     VARCHAR(20) NOT NULL DEFAULT 'LOGIN',
    expires_at  TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    attempts    INT NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_evc_email_expires ON email_verification_codes(email, expires_at);

CREATE TABLE IF NOT EXISTS refresh_tokens (
    id         VARCHAR(36) PRIMARY KEY,
    user_id    VARCHAR(36) NOT NULL REFERENCES users(id),
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    device_id  VARCHAR(255),
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user_id ON refresh_tokens(user_id);

CREATE TABLE IF NOT EXISTS admin_github_users (
    github_id     BIGINT PRIMARY KEY,
    github_login  VARCHAR(255),
    note          TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS app_versions (
    id           VARCHAR(36) PRIMARY KEY,
    version_code BIGINT NOT NULL UNIQUE,
    version_name VARCHAR(50) NOT NULL,
    force_update BOOLEAN NOT NULL DEFAULT false,
    changelog    TEXT,
    target_url   TEXT NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS update_url_logs (
    id             VARCHAR(36) PRIMARY KEY,
    user_id        VARCHAR(36),
    version_code   BIGINT,
    encrypted_url  TEXT NOT NULL,
    resolved       BOOLEAN NOT NULL DEFAULT false,
    ip             VARCHAR(64),
    user_agent     TEXT,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_uul_user_id ON update_url_logs(user_id);

CREATE TABLE IF NOT EXISTS webdav_credential_logs (
    id         VARCHAR(36) PRIMARY KEY,
    user_id    VARCHAR(36),
    role       VARCHAR(20) NOT NULL,
    permission VARCHAR(20) NOT NULL,
    ip         VARCHAR(64),
    user_agent TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_wcl_user_id ON webdav_credential_logs(user_id);

CREATE TABLE IF NOT EXISTS audit_logs (
    id            VARCHAR(36) PRIMARY KEY,
    user_id       VARCHAR(36),
    action        VARCHAR(50) NOT NULL,
    resource_type VARCHAR(50),
    method        VARCHAR(20),
    path          TEXT,
    status_code   INT,
    ip            VARCHAR(64),
    user_agent    TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_al_user_id ON audit_logs(user_id);
CREATE INDEX IF NOT EXISTS idx_al_action ON audit_logs(action);
