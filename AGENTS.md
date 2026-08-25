# Repository Guide

## Boundaries

- This repository contains two independent builds: the root Gradle Android app (`:app`) and the nested Go module in `backend/`. Run Go commands from `backend/`; it is not a Gradle subproject.
- Android starts at `MainActivity` -> `AndroidResourceDownloadRoot`; Hilt bindings are under `app/src/main/java/com/resdownload/android/di/`. The backend is wired in `backend/cmd/server/main.go`, with routes in `backend/internal/app/router.go`.
- `docs/FRONTEND_APPEND_V1_1.md` is the client/client-backend contract and security boundary. `docs/IMPLEMENTATION_PLAN.md` tracks real versus scaffolded work, but its prose can run ahead of code — verify against `router.go` and build files before assuming a feature works end-to-end.

## Verification

- Android release gate: `./gradlew --no-daemon lintRelease testReleaseUnitTest assembleRelease` (JDK 17). Signing requires all four of `ANDROID_KEYSTORE_PATH`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD` at once; a partial set fails the build script.
- Focus one Android test class with `./gradlew testDebugUnitTest --tests 'com.resdownload.android.webdav.WebDavPathTest'`.
- Backend CI-equivalent gate, from `backend/`: `go vet ./...`, `go build ./...`, then `go test -race -count=1 ./...`. `make test` runs the same race-enabled test command.
- Focus backend tests from `backend/` with `go test ./internal/integration -run '^TestHealth$'` or replace the package/test regex. Integration tests use temporary SQLite and mock GitHub; they require no PostgreSQL, Redis, or external WebDAV service.
- Trust `backend/go.mod` for the Go toolchain version (currently Go 1.25.0). Both backend workflows use `go-version-file: backend/go.mod`.
- `go test -race` requires a 48-bit VMA kernel; on 39-bit devices (e.g. Android/Termux proot) it aborts at runtime with `ThreadSanitizer: unsupported VMA range`. Verify locally with plain `go test ./...` and let CI run the race gate.

## Runtime Configuration

- Debug builds default to `DEMO_MODE=true` and an invalid API URL. Inject real mode through uncommitted Gradle properties: `apiBaseUrl` and `demoMode=false` (normally in `~/.gradle/gradle.properties` or `local.properties`). GitHub/QQ secrets belong only on the backend.
- The `qqAppId` Gradle property feeds `BuildConfig.QQ_APP_ID` and the `tencent<AppID>` callback scheme in `AndroidManifest.xml`; the QQ AppID is public, not a secret. The OpenSDK jar lives in `app/libs/`.
- The GitHub OAuth callback is fixed as `com.resdownload.android://oauth/callback` in `AndroidManifest.xml` (re-checked in `MainActivity`/`AuthViewModel`); the backend `GITHUB_APP_REDIRECT_URI` must match exactly.
- Backend configuration is defaults plus environment variables, or YAML only when `CONFIG_FILE` names it. Despite `backend/README.md`, the server does not load `backend/.env`; export/source variables before `make run-sqlite`. SQLite paths are resolved relative to the process working directory, another reason to launch from `backend/`.
- Server startup runs GORM `AutoMigrate`; files in `backend/migrations/` are reference SQL, not the executed migration path.
- Bumping the app version also requires updating root `latest_version.txt`: the app fetches it from raw.githubusercontent (main branch) for update checks and compares the full three-part `VERSION_NAME` (`versionCode` is ignored for this).
- Admins are granted by admin email domain (`ADMIN_EMAIL_DOMAINS`) or the seeded GitHub whitelist: `make seed-admin ID=<github_id> LOGIN=<login>` (from `backend/`).

## Current Integration Traps

- Login is GitHub OAuth + QQ. Android's email-code UI is gone and the backend still serves `/api/v1/auth/email/code|login` (slated for removal — do not extend it). `POST /api/v1/auth/qq/login` is implemented: the backend re-validates the provider token against `graph.qq.com/oauth2.0/me`, requires the configured `QQ_APP_ID` (`qq.app-id`) to match Tencent's `client_id`, and keys accounts by Tencent's OpenID via the generic `auth_identities` table (provider `qq`); QQ users are always `USER` with role source `qq_default`. Integration tests override `QQ_ME_URL`/`QQ_USER_INFO_URL` with a local stub server.
- Real mode navigation is driven by `AuthViewModel` and the file screen uses `FilesViewModel`; Demo mode still owns an in-memory user, file tree, and download list. Check `BuildConfig.DEMO_MODE` branches before treating a visible interaction as real.
- Preserve the credential boundary: auth tokens may live only in `EncryptedSessionStore`; WebDAV username/password stay process-memory-only in `InMemoryWebDavCredentialProvider`. Never place WebDAV credentials in Room, DataStore, files, logs, or exceptions.
- Android talks directly to WebDAV after obtaining credentials; the Go backend only authenticates and issues role credentials. Preserve decoded-segment path validation, per-request Basic Auth, blocked cross-origin redirects, and the single credential refresh/retry on HTTP 401.
- Backend API responses use `{code,message,data}` even when HTTP status is successful. Android calls should continue through the envelope decoder rather than treating HTTP 2xx alone as success.
