# Shikhi Android

Native Android client (Kotlin + Jetpack Compose) for the Shikhi `/v1` API — see
ADR-0012, PRD `docs/21-prd-android.md`, and milestones MA0–MA4 in `docs/80-delivery-plan.md` §12.

This is a **standalone Gradle build** (own wrapper), independent of `backend/` Gradle.

## Requirements

- JDK 17+ (21 works)
- Android SDK (`local.properties` → `sdk.dir=…`, or `ANDROID_HOME`)
- Backend for the debug app to talk to (see below)

## Build

```bash
./gradlew testDebugUnitTest assembleDebug     # tests + debug APK
./gradlew assembleRelease                     # needs keystore.properties (see example file)
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk` (application id `com.shikhi.app.debug`).

## API base URL

Compiled in via `BuildConfig.API_BASE_URL` (always includes `/v1/`):

| Build | Default | Override |
|---|---|---|
| debug | `http://10.0.2.2:8080/v1/` (emulator → host) | `-PapiBaseUrl=http://192.168.x.x:8080/v1/` |
| release | hosted backend (Render) | `-PreleaseApiBaseUrl=…` |

Cleartext HTTP is allowed **only** in debug builds (`src/debug/res/xml/network_security_config.xml`).

## Run end-to-end against a local backend

```bash
# 1. Data tier + backend (repo root)
docker compose up -d
(cd backend && ./gradlew bootRun)

# 2a. Emulator: reaches the host via 10.0.2.2 — no extra setup
~/Android/Sdk/emulator/emulator -avd Pixel_8 &
./gradlew installDebug

# 2b. Physical device via USB: forward the port instead
adb reverse tcp:8080 tcp:8080
./gradlew installDebug -PapiBaseUrl=http://127.0.0.1:8080/v1/
```

Then: onboarding → “start without signing up” → home shows the health badge and greeting.
Kill and relaunch: the session must resume silently (refresh-token rotation).

## Auth invariants (do not break)

The backend rotates refresh tokens and **revokes the whole token family if a rotated
token is replayed**. `TokenAuthenticator` therefore single-flights refreshes and persists
the rotated token *before* retrying; `TokenAuthenticatorTest` pins this behavior. The
access token lives in memory only; the refresh token is AES/GCM-encrypted at rest with an
Android Keystore key (`RefreshTokenCipher`).

## Signing

Copy `keystore.properties.example` → `keystore.properties` (gitignored), point it at a
keystore outside the repo. Never commit keystores (docs/70 §13).
