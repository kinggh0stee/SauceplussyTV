# CLAUDE.md

Guidance for Claude Code when working in this repository.

## What this is

This is **SaucedplussyTV** — an unofficial Android TV client for Sauce+
(`https://www.sauceplus.com`). It is a fork of the [Hydravion Android TV
app](https://github.com/bmlzootown/Hydravion-AndroidTV) that has been heavily
modified and repointed from Floatplane to Sauce+.

**⚠️ IMPORTANT:** SaucedplussyTV is an unofficial, third-party client. It is not
affiliated with, endorsed by, or connected to Sauce+ or Floatplane Media Inc.

Sauce+ runs the **same backend software as Floatplane**, so the existing
`/api/v3/...` API contract and the socket.io live/sync mechanism are the starting
assumptions. The work is (1) complete the rebrand from Hydravion to
SaucedplussyTV, and (2) build out / adjust Android TV features on top of that
base.

## Auth model

Sauce+ uses legacy cookie-session auth (not Keycloak/OIDC). Confirmed by
decompiling the official Sauce+ Android app (package `com.floatplane.sauceplus`).
Full notes: `reference/RECON.md`.

**API base host: `https://www.sauceplus.com`** (serves all `/api/v3/...`).

**Login flow** (all `POST`/`GET` under `www.sauceplus.com`):
1. `GET /api/v3/auth/captcha/info` → captcha config; variant is **Cloudflare
   Turnstile**, siteKey delivered at runtime (not hardcoded).
2. `POST /api/v3/auth/login` with `{ username, password, captchaToken }`
   (captchaToken = a Turnstile token) → sets the **`sails.sid`** session cookie;
   response may flag **`needs2FA`**.
3. If 2FA: `POST /api/v3/auth/checkFor2faLogin` with `{ token }`.
4. `POST /api/v3/auth/logout` to end the session. (Discord login is the
   `/api/v3/connect/...` OAuth flow.)

**Cloudflare:** the entire site incl. `/api/*` is behind a bot challenge
(`cf-mitigated: challenge`; headless = HTTP 403). Calls must come from a client
that has passed CF — i.e. carry a valid **`cf_clearance`** cookie with a
**consistent User-Agent**.

**TV auth:** `WebLoginActivity` loads `https://www.sauceplus.com/login` in a
`WebView` (user solves Turnstile + CF challenge naturally), then harvests
`sails.sid` + `cf_clearance` from `CookieManager` and uses them — with the
WebView's User-Agent — for all `/api/v3` calls via `RequestTask`/OkHttp.

**Content/playback API is unchanged** — the `/api/v3` content/creator/delivery/
socket endpoints match the Sauce+ app, so that layer is just a **host swap to
`www.sauceplus.com`**. Only the auth layer was rewritten.

## Tech stack

- **Platform:** Android TV, AndroidX **Leanback** (D-pad only, no touch).
  `minSdk 26`, `target/compileSdk 34`, Java 17, Gradle 9.4.1 (Groovy DSL), AGP 9.2.1.
- **Languages:** mixed **Kotlin + Java** — match the surrounding file's language.
- **Playback:** ExoPlayer `2.19.1` (+ mediasession extension).
  **TODO:** Migrate to Media3 (`androidx.media3:media3-exoplayer:1.10.1`).
- **Networking:** Volley + OkHttp 5 (alpha) + `socket.io-client`; Gson for JSON.
- **UI/misc:** Glide (images), PrettyTime, NanoHTTPD (local server), versioncompare.

## Build & run

```bash
./gradlew :app:assembleDebug      # primary build / compile check
./gradlew :app:lint               # Android lint
```

There is no unit-test suite. "Verify it works" means it **compiles**
(`assembleDebug`) and behaves correctly on a TV device/emulator with a D-pad.
Always run `assembleDebug` before handing a change off for review.

## Architecture (where things live)

`app/src/main/java/com/saucedplussytv/androidtv/`

- `browse/` — `MainActivity`, `MainFragment` (Leanback browse), presenters,
  selection/click listeners, settings.
- `card/`, `subscription/`, `detail/` — Leanback presenters, details + playback
  description UI.
- `playback/PlaybackActivity` — ExoPlayer playback screen.
- `client/` — `SaucedplussyTVClient` (API facade), `RequestTask` (Volley +
  `VolleyCallback`), `SocketClient`/`SyncEvent`/`UserSync` (live/sync).
- `authenticate/` — `AuthManager` (session cookies + UA in SharedPreferences),
  `WebLoginActivity` (WebView cookie harvest login). Replaces the removed
  `QrLoginActivity`/Keycloak device-code flow.
- `models/`, `creator/`, `post/` — Gson DTOs and domain types.
- `Constants.kt` — session preference keys.

Key invariants already established in the code (don't regress them):

- Pagination uses `fetchAfter = (page - 1) * 20`; a fix prevents load-more from
  jumping back to the latest video.
- There is explicit logged-in/out state management — **do not update the UI when
  logged out**, and reset the UI-init flag on logout.
- All authed requests go through `AuthManager.withValidAccessToken { token -> }`.
- Debug logging is via `MainFragment.dLog(...)` guarded by `BuildConfig.DEBUG`;
  **never log tokens or full auth responses.**

## Rebrand status

The app identity migration from Hydravion → SaucedplussyTV is **substantially
complete** in code:

- ✅ Package: `com.saucedplussytv.androidtv`
- ✅ App name / user-facing strings: "SaucedplussyTV"
- ✅ API host: `sauceplus.com`
- ✅ Auth: WebView cookie-session (not Keycloak)
- ✅ Class names: `SaucedplussyTVClient` (was `HydravionClient`)
- ✅ Build artifacts: `SaucedplussyTV-*.apk`
- ✅ Documentation: README, CLAUDE.md, TODO.md

**Remaining (asset swaps — requires human/designer):**
- `res/drawable/banner.png` — TV banner
- `res/drawable/icon.png` — app icon
- `res/drawable/ic_saucedplussytv.xml` — vector icon (renamed from `ic_hydravion`)
- Launcher mipmaps in `res/mipmap-*`
- `res/values/colors.xml` — brand colors

## Sub-agents and the review gate (IMPORTANT)

This repo defines sub-agents in `.claude/agents/`:

- **`rebrand-engineer`** — identity + endpoint migration.
- **`api-client`** — v3 API plumbing, models, auth/socket layer.
- **`android-feature-dev`** — Leanback UI, presenters, playback, lifecycle.
- **`senior-reviewer`** — the **final gate** for *all* code.

### Mandatory workflow

1. Route work to the matching specialist agent (rebrand / api / feature). For
   small changes, doing it inline is fine.
2. Build it (`./gradlew :app:assembleDebug`).
3. **Every code change must pass `senior-reviewer` before it is considered
   done.** No exceptions.
4. The `senior-reviewer` agent does not review with a Claude model — it delegates
   to a **skeptical senior engineer running on Kimi K2.6** via the **opencode
   MCP** server (`opencode_ask` → `agent: "reviewer"`, model
   `kimi-for-coding/k2p6`, configured in `.opencode/agent/reviewer.md`).
5. The reviewer returns either **LGTM** (approved) or a list of
   **BLOCKER / MAJOR / MINOR** issues. If it is not LGTM, the change is **not
   done**: fix every BLOCKER and MAJOR and resubmit through `senior-reviewer`
   (it continues the same Kimi session for context).

If the opencode MCP server is unavailable, the change is **not approved** —
surface the failure rather than skipping review.

### Kimi K2.6 reviewer setup

- The Kimi review path requires the **`opencode` MCP server** (defined in
  `.mcp.json`). On first use Claude Code will prompt to approve it.
- The reviewer agent and model are defined in `.opencode/agent/reviewer.md`
  (native opencode agent, `mode: all`, model `kimi-for-coding/k2p6`). The opencode
  CLI must be authenticated for the `kimi-for-coding` provider (it already is on
  this machine; `opencode models | grep kimi` should list `kimi-for-coding/k2p6`).
  Quick check: `opencode run --agent reviewer 'ping'` should respond as the
  reviewer with no "agent not found" warning.

## Conventions

- Match existing style and language (Kotlin vs Java) of the file you touch.
- Prefer reusing the existing `SaucedplussyTVClient`/`RequestTask`/presenter patterns
  over introducing parallel mechanisms.
- This is a fork that may still track upstream — make targeted changes; don't
  do sweeping rewrites unless asked.
- Keep secrets and tokens out of logs and source.
- See `TODO.md` for planned upgrades and feature ideas.
