# ADR-0012 — Native Android client (Kotlin + Jetpack Compose) on the same `/v1` contract

**Status:** Accepted (2026-07-07, Gate MA0 — see Delivery Plan `80` §Android track)
**Related:** ADR-0005 (auth), ADR-0008 (deployment), ADR-0011 (guest learning), Vision NG1 (amended), BRD OOS-1 (amended), PRD `21-prd-android.md`, API contract `43`

## Context
Shikhi launched web-first (Vision NG1 deliberately deferred native apps), but the target
audience — Bengali-speaking learners on mid-range Android phones — lives on Android, and
an installable app with real offline behavior is the natural next step. The backend needs
no changes for this: it is a stateless-JWT REST API under `/v1` with guest onboarding
(ADR-0011), an idempotent progress-sync endpoint designed for flaky connectivity (D2), and
a written OpenAPI contract (`docs/43`). The architecture was explicitly required to "leave
room for NG1"; this ADR exercises that room.

## Decision
Build a **native Android app in Kotlin with Jetpack Compose**, living at **`android/` in
the monorepo as a standalone Gradle build** (own wrapper and version catalog, decoupled
from the backend's Gradle — mirroring how `backend/` and `frontend/` already build
independently, ADR-0010). The app is a **second client of the same `/v1` API**; the
contract at `docs/43-api-contract.openapi.yaml` becomes truly authoritative with two
consumers.

Key choices (fixed):
- **UI:** single-activity Jetpack Compose, Material 3 theme ported from the web design
  tokens (`frontend/src/index.css`), Bengali-default localization (`values/` = bn,
  `values-en/` = en) with Noto Sans Bengali.
- **API client:** generated from `docs/43` via openapi-generator (`kotlin` /
  `jvm-retrofit2` / kotlinx-serialization), regenerated on build, generated code not
  committed. *Fallback* if the OpenAPI 3.1 spec generates poorly (`allOf`,
  `additionalProperties`): hand-written DTOs + Retrofit interfaces (~20 endpoints).
  Either way a hand-written mapping layer produces typed domain models (exercise
  `config` payloads are intentionally schemaless in the contract).
- **Auth:** mirrors the web semantics (ADR-0005/0011) — access token in memory only;
  rotating refresh token at rest in Preferences DataStore encrypted with an Android
  Keystore AES/GCM key (the deprecated `security-crypto` library is avoided). An OkHttp
  `Authenticator` single-flights refreshes behind a mutex and **persists the rotated
  refresh token before retrying**, because the backend revokes the whole token family on
  replay of a rotated token. Guest-first onboarding; claim-in-place upgrade.
- **Offline:** Room for content caching (curriculum/lessons keyed by content version,
  vocabulary per level) and for the progress **outbox**, flushed by a WorkManager worker —
  the same idempotent-event semantics as the web outbox (`frontend/src/api/outbox.ts`).
- **Identity/versioning:** `applicationId com.shikhi.app` (permanent, Play-compatible);
  `versionCode = 10000·major + 100·minor + patch`. **minSdk 26, target/compile latest
  stable** — Android 8.0+ covers effectively all active devices in Bangladesh while giving
  `java.time`, adaptive icons, and full Keystore AES/GCM without desugaring.
- **API base URL** is a build-time `BuildConfig` field including the `/v1` prefix: release
  → hosted backend; debug → `http://10.0.2.2:8080/v1`, overridable for LAN devices.
  Cleartext HTTP is permitted **only** in debug builds via a debug-source-set network
  security config.
- **Distribution:** signed sideloadable APK first; Play Store is a later phase (identifiers,
  signing, and versioning are Play-compatible from day one). Keystore and
  `keystore.properties` are never committed.

## Consequences
- ✅ Reaches the actual target device population with an installable, offline-capable app;
  backend and web app are untouched.
- ✅ Full reuse of the learning stack — guests, lessons, practice, review, sync all work
  through existing endpoints.
- ⚠️ **Two clients now consume `docs/43`** — contract drift becomes a real failure mode
  (the `/vocabulary` endpoint had already drifted and was trued in this change). A CI
  check diffing the runtime spec against `docs/43` is worth adding later.
- ⚠️ A second codebase to maintain, with its own release engineering (signing, versioning,
  eventually Play review) and its own CI job.
- ⚠️ Feature work now lands twice (web + Android) or diverges deliberately; PRD `21`
  defines the Android surface and its non-goals to keep this honest.
- ⚠️ Exercise types without a client renderer must degrade gracefully (the seeded pilot
  content uses MCQ and WORD_BANK only; the app ships an "unsupported exercise" fallback).

## Alternatives considered
- **PWA + Trusted Web Activity:** cheapest and closest to the original NG1 wording, but
  requires a permanently hosted public HTTPS origin (the hosted stack is still on a side
  branch), adds no offline/native capability beyond what a service worker gives the web
  app, and delivers a browser-shell experience. Rejected by product decision in favor of
  a real native client.
- **Capacitor wrapper:** bundles the existing SPA in a WebView — fast to ship, but the SPA
  has no router (no deep links), the API base URL is hardcoded same-origin, and the result
  is still web UX in a shell. Rejected by the same product decision.
- **React Native / Kotlin Multiplatform for code reuse:** the reusable surface (a thin
  fetch client and React UI in a very different idiom) is small; both add a heavy toolchain
  for little sharing. Rejected — clean-room native with the OpenAPI contract as the sharing
  boundary is simpler.
