# Repository Guide

## Boundaries

- This repository contains two independent builds: the root Gradle Android app (`:app`) and the nested Go module in `backend/`. Run Go commands from `backend/`; it is not a Gradle subproject.
- Android starts at `MainActivity` -> `AndroidResourceDownloadRoot`; Hilt bindings are under `app/src/main/java/link/mczihan/androidResourceDownload/di/`. The backend is wired in `backend/cmd/server/main.go`, with routes in `backend/internal/app/router.go`.
- `docs/FRONTEND_APPEND_V1_1.md` is the client/backend contract and security boundary. `docs/IMPLEMENTATION_PLAN.md` tracks what is real versus scaffolded; do not infer completion from an existing screen or model.

## Verification

- Android release gate: `./gradlew --no-daemon lintRelease testReleaseUnitTest assembleRelease` (JDK 17). Local signing requires `ANDROID_KEYSTORE_PATH`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, and `ANDROID_KEY_PASSWORD`; the signed APK is written to `app/build/outputs/apk/release/app-release.apk`.
- Focus one Android test class with `./gradlew testDebugUnitTest --tests 'link.mczihan.androidResourceDownload.webdav.WebDavPathTest'`.
- Backend CI-equivalent gate, from `backend/`: `go vet ./...`, `go build ./...`, then `go test -race -count=1 ./...`. `make test` runs the same race-enabled test command.
- Focus backend tests from `backend/` with `go test ./internal/integration -run '^TestHealth$'` or replace the package/test regex. Integration tests use temporary SQLite and mock GitHub; they require no PostgreSQL, Redis, or external WebDAV service.
- Trust `backend/go.mod` for the Go toolchain version. It currently declares Go 1.25.0; older 1.23 references in prose/workflow config are stale.

## Runtime Configuration

- Debug builds default to `DEMO_MODE=true` and an invalid API URL. Inject real mode through uncommitted Gradle properties: `apiBaseUrl` and `demoMode=false` (normally in `~/.gradle/gradle.properties` or `local.properties`). GitHub client credentials belong only on the backend.
- The App callback is fixed as `link.mczihan.androidresourcedownload://oauth/callback` in `AndroidManifest.xml` and `AuthViewModel`; the backend `GITHUB_APP_REDIRECT_URI` must match it exactly.
- Backend configuration is defaults plus environment variables, or YAML only when `CONFIG_FILE` names it. Despite `backend/README.md`, the server does not load `backend/.env`; export/source variables before `make run-sqlite`. SQLite paths are resolved relative to the process working directory, another reason to launch from `backend/`.
- Server startup runs GORM `AutoMigrate`; files in `backend/migrations/` are reference SQL, not the executed migration path.

## Current Integration Traps

- Real mode navigation is driven by `AuthViewModel` and the file screen uses `FilesViewModel`; Demo mode still owns an in-memory user, file tree, and download list. Check `BuildConfig.DEMO_MODE` branches before treating a visible interaction as real.
- Preserve the credential boundary: auth tokens may live only in `EncryptedSessionStore`; WebDAV username/password stay process-memory-only in `InMemoryWebDavCredentialProvider`. Never place WebDAV credentials in Room, DataStore, files, logs, or exceptions.
- Android talks directly to WebDAV after obtaining credentials; the Go backend only authenticates and issues role credentials. Preserve decoded-segment path validation, per-request Basic Auth, blocked cross-origin redirects, and the single credential refresh/retry on HTTP 401.
- Backend API responses use `{code,message,data}` even when HTTP status is successful. Android calls should continue through the envelope decoder rather than treating HTTP 2xx alone as success.
