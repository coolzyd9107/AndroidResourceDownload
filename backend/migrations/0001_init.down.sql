-- 0001_init.down.sql -- rollback initial schema.
DROP TABLE IF EXISTS audit_logs;
DROP TABLE IF EXISTS webdav_credential_logs;
DROP TABLE IF EXISTS update_url_logs;
DROP TABLE IF EXISTS app_versions;
DROP TABLE IF EXISTS admin_github_users;
DROP TABLE IF EXISTS refresh_tokens;
DROP TABLE IF EXISTS email_verification_codes;
DROP TABLE IF EXISTS auth_identities;
DROP TABLE IF EXISTS users;
