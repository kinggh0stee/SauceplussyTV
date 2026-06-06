# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

**SaucedplussyTV** — an unofficial Android TV client for Sauce+ (`https://www.sauceplus.com`). Fork of [Hydravion Android TV](https://github.com/bmlzootown/Hydravion-AndroidTV), repointed from Floatplane to Sauce+. Sauce+ runs the same backend software as Floatplane, so the `/api/v3/...` API contract and socket.io sync mechanism are unchanged — the work is a host swap plus auth rewrite.

**⚠️ Unofficial / unaffiliated with Sauce+ or Floatplane Media Inc.**

## Build & run

```bash
JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:assembleDebug   # build / compile check
JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:lint            # Android lint
```

**Critical:** The system JDK (26) breaks AGP 9.2.1. Always use `JAVA_HOME=/opt/android-studio/jbr`. If `gradlew` is not executable: `chmod +x gradlew`.

There is no unit-test suite. "Verify it works" means it compiles (`assembleDebug`) and behaves correctly on a TV device/emulator with a D-pad.

## Auth model

Sauce+ uses legacy cookie-session auth (not Keycloak/OIDC). Full recon: `reference/RECON.md`.

**Login flow** (all under `https://www.sauceplus.com`):
1. `GET /api/v3/auth/captcha/info` → Cloudflare Turnstile siteKey (delivered at runtime, not hardcoded).
2. `POST /api/v3/auth/login` with `{ username, password, captchaToken }` → sets `__Host-sp-sess` cookie; may return `needs2FA`.
3. If 2FA: `POST /api/v3/auth/checkFor2faLogin` with `{ token }`.

**Cloudflare:** the entire site is behind a bot challenge. API calls must carry a valid `cf_clearance` cookie with a **consistent User-Agent** matching the one that solved the challenge.

**TV login:** `WebLoginActivity` loads `https://www.sauceplus.com/login` in a WebView. The user solves Turnstile + CF naturally. On success the activity harvests `__Host-sp-sess` + `cf_clearance` from `CookieManager` plus the WebView's `userAgentString`, stores them via `AuthManager.saveSession()`, and returns `RESULT_OK`. Success is detected by navigating off `/login` to an app route while `__Host-sp-sess` is present — no separate HTTP verification (CF TLS fingerprinting blocks it).

## Architecture

`app/src/main/java/com/saucedplussytv/androidtv/`

- `browse/` — `MainActivity`, `MainFragment` (Leanback `BrowseSupportFragment`). `MainFragment` is the central coordinator: it holds both `SaucedplussyTVClient` and `SocketClient`, fetches subscriptions, populates Leanback `ArrayObjectAdapter` rows, and owns all click/selection listeners and the socket event loop.
- `client/` — `SaucedplussyTVClient` (API facade, singleton), `RequestTask` (Volley HTTP, adds Cookie + UA headers to every request), `SocketClient` (socket.io live/sync, singleton), `SyncEvent`/`UserSync` (socket event DTOs).
- `authenticate/` — `AuthManager` (stores `__Host-sp-sess`+UA in DataStore, singleton), `WebLoginActivity` (WebView cookie harvest).
- `playback/PlaybackActivity` — Media3 playback screen. Injects `Cookie` + `User-Agent` into the OkHttp-backed Media3 data source so HLS segment and AES-128 key requests carry the session credential.
- `detail/` — `DetailsActivity` + `VideoDetailsFragment` (Leanback details screen).
- `models/`, `creator/`, `post/`, `subscription/` — Gson DTOs.
- `Constants.kt` — DataStore/SharedPreferences key names (`PREF_SESSION_COOKIE`, `PREF_USER_AGENT`).

### Non-obvious cross-cutting patterns

**Singleton access:** All three primary objects share the same `getInstance(context)` factory:
```kotlin
SaucedplussyTVClient.getInstance(context)
AuthManager.getInstance(context)
SocketClient.getInstance(context)
```

**All authed API calls gate on `AuthManager.withValidAccessToken { token -> }`**, which is synchronous (no network, no refresh). An empty cookie calls `onFailure`. Expired/invalid cookies cause downstream 401/403 → "Session Expired" dialog → re-login.

**`RequestTask.VolleyCallback`** has four methods. Only the `sendRequest(uri, token, creatorGUID, callback)` overload triggers `onSuccessCreator`; the standard `sendRequest(uri, token, callback)` triggers `onSuccess`. Implement both but only the relevant one does work per call site.

**`RequestTask`** sets `Cookie` and `User-Agent` headers on every request via `authHeaders()`, reading the UA from `AuthManager.peekUserAgent()` (static, safe before login).

**`SocketClient.initialize()`** injects the session Cookie + User-Agent at the socket.io transport level (via `Manager.EVENT_TRANSPORT` / `Transport.EVENT_REQUEST_HEADERS`). Never log the Cookie value.

**Video delivery:** `SaucedplussyTVClient.getVideo()` calls `/api/v3/delivery/info?scenario=onDemand&entityId=...`, parses `Delivery` → takes `groups[0].origins[0].url` as CDN base, then picks the highest-quality *enabled* variant (sorted `2160 > 1080 > 720 > 480 > 360`). Final URL = `cdn + variant.url`.

**Pagination:** `fetchAfter = (page - 1) * 20`. Do not change this formula — it prevents the load-more from jumping back to latest.

**Debug logging:** `MainFragment.dLog(TAG, msg)` — static helper, guarded by `BuildConfig.DEBUG`. Use it for all debug logs. Never log tokens or full auth responses.

**Logged-out state:** Do not update the UI when `!authManager.isLoggedIn()`. Reset the UI-init flag on logout.

## Tech stack

- **Platform:** Android TV, AndroidX Leanback (D-pad only, no touch). `minSdk 26`, `targetSdk 35`, `compileSdk 37`, Java 17, Gradle 9.4.1 (Groovy DSL), AGP 9.2.1, Kotlin 2.4.0.
- **Languages:** mixed Kotlin + Java — match the surrounding file's language.
- **Playback:** AndroidX Media3 1.10.1 (media3-exoplayer, media3-exoplayer-hls, media3-ui, media3-session, media3-datasource-okhttp).
- **Networking:** Volley + OkHttp 5 + `socket.io-client 2.1.2`; Gson for JSON.
- **UI/misc:** Glide (images), PrettyTime, NanoHTTPD (local server), versioncompare.
- **ZXing** dependency is present but currently unused — QR features were removed with the Keycloak auth rewrite.

## Rebrand status

Complete. All assets are in place:
- `res/drawable/icon.xml` — vector "S+" TV logo (replaces deleted icon.png)
- `res/drawable-xhdpi/banner.png` — Leanback launcher banner
- `res/drawable/ic_saucedplussytv.xml` — settings icon
- `res/mipmap-*/ic_launcher*.webp` — adaptive launcher icons (all densities)
- `res/values/colors.xml` — brand colors (#F5B731 amber accent, #1A1A1A dark surface)

## Reference material

- `reference/Hydravion-AndroidTV-master/` — untouched upstream base code (original Hydravion Android TV). Do not modify.
- `reference/apk/` — official Sauce+ Android app APK (reference only). Do not modify.

## Sub-agents and the review gate (IMPORTANT)

Sub-agents are defined in `.claude/agents/` with explicit model assignments:

| Agent | Model | Role |
|---|---|---|
| `plan` | **opus** | Architecture + approach before any non-trivial work |
| `android-feature-dev` | sonnet | Leanback UI, presenters, playback, lifecycle |
| `api-client` | sonnet | v3 API plumbing, models, auth/socket layer |
| `rebrand-engineer` | haiku | Identity + endpoint changes (mechanical find-replace) |
| `dependency-updater` | haiku | Bumps dependency versions in build.gradle, verifies compile |
| `crash-triager` | sonnet | Maps logcat/stack traces to source, identifies root cause |
| `security-reviewer` | **opus** | Audits auth/cookie/credential handling on auth-touching PRs |
| `senior-reviewer` | sonnet | Final gate — delegates review to Kimi K2.6 |

### Mandatory workflow

1. Route work to the matching specialist agent (or inline for small changes).
2. Build: `JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:assembleDebug`.
3. **Every code change must pass `senior-reviewer` before it is done. No exceptions.**
4. `senior-reviewer` is a **Claude Opus 4.8** agent defined in `.claude/agents/senior-reviewer.md`. Invoke it via the Agent tool with `subagent_type: "senior-reviewer"`.
5. Reviewer returns **LGTM** or **BLOCKER / MAJOR / MINOR** issues. Fix all BLOCKERs and MAJORs and resubmit.

## Conventions

- Match the language (Kotlin vs Java) of the file you touch.
- Prefer reusing `SaucedplussyTVClient`/`RequestTask`/presenter patterns over new mechanisms.
- Make targeted changes — this fork may still track upstream.
- See `TODO.md` for planned upgrades (Media3, dependency updates, architecture improvements).
