# CLAUDE.md

Guidance for Claude Code when working in this repository.

## What this is

This is **Sauce+ for Android TV** — a fork of the [Hydravion Android TV
app](https://github.com/bmlzootown/Hydravion-AndroidTV) (an unofficial
**Floatplane** client) being rebranded and repointed to **Sauce+**
(`https://www.sauceplus.com`, discover page: `https://www.sauceplus.com/discover`).

Sauce+ runs the **same backend software as Floatplane**, so the existing
`/api/v3/...` API contract and the socket.io live/sync mechanism are the starting
assumptions. The work is (1) rebrand the app's identity and endpoints from
Floatplane/Hydravion to Sauce+, and (2) build out / adjust Android TV features on
top of that base.

The upstream package is still `ml.bmlzootown.hydravion` and many user-facing and
endpoint references still say "Hydravion" / "floatplane.com". Treat those as the
rebrand backlog, not as the intended final state.

## Auth model — IMPORTANT (differs from upstream Hydravion)

Confirmed 2026-06-01 by decompiling the official Sauce+ Android app (package
`com.floatplane.sauceplus`, a React Native / white-label Floatplane app). Full
notes: `reference/RECON.md`.

**Sauce+ = white-label Floatplane, legacy cookie-session auth — NOT Keycloak.**
The current upstream TV code uses an OIDC device-code/QR flow
(`QrLoginActivity.java` → `auth.floatplane.com/realms/floatplane`). **Sauce+ has no
such host/realm; that flow must be replaced.** (`link.sauceplus.com` is SMTP2GO
email tracking, not a device-link page — ignore it.)

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

**Recommended TV auth design (replaces `QrLoginActivity`/OIDC):** a **WebView
login**. Load the Sauce+ login page in a `WebView` (user solves Turnstile + the CF
challenge naturally there), then harvest `sails.sid` + `cf_clearance` from
`CookieManager` and use them — with the WebView's User-Agent — for all `/api/v3`
calls via the existing `RequestTask`/OkHttp path.

**Content/playback API is unchanged** — the `/api/v3` content/creator/delivery/
socket endpoints `HydravionClient` already calls match the Sauce+ app, so that
layer is just a **host swap to `www.sauceplus.com`**. Only the auth layer is a
rewrite.

Still worth a one-time live HAR to confirm exact JSON field casing and whether
`/api/v3` needs `cf_clearance` in addition to `sails.sid`.

## Tech stack

- **Platform:** Android TV, AndroidX **Leanback** (D-pad only, no touch).
  `minSdk 26`, `target/compileSdk 34`, Java 17, Gradle (Groovy DSL).
- **Languages:** mixed **Kotlin + Java** — match the surrounding file's language.
- **Playback:** ExoPlayer `2.17.1` (+ mediasession extension).
- **Networking:** Volley + OkHttp 5 (alpha) + `socket.io-client`; Gson for JSON.
- **UI/misc:** Glide (images), PrettyTime, ZXing (QR login), NanoHTTPD (local
  server), versioncompare.

## Build & run

```bash
./gradlew :app:assembleDebug      # primary build / compile check
./gradlew :app:lint               # Android lint
```

There is no unit-test suite. "Verify it works" means it **compiles**
(`assembleDebug`) and behaves correctly on a TV device/emulator with a D-pad.
Always run `assembleDebug` before handing a change off for review.

## Architecture (where things live)

`app/src/main/java/ml/bmlzootown/hydravion/`

- `browse/` — `MainActivity`, `MainFragment` (Leanback browse), presenters,
  selection/click listeners, settings.
- `card/`, `subscription/`, `detail/` — Leanback presenters, details + playback
  description UI.
- `playback/PlaybackActivity` — ExoPlayer playback screen.
- `client/` — `HydravionClient` (API facade), `RequestTask` (Volley +
  `VolleyCallback`), `SocketClient`/`SyncEvent`/`UserSync` (live/sync).
- `authenticate/` — `AuthManager` (OIDC tokens in SharedPreferences),
  `QrLoginActivity` (Keycloak device-code flow), `LogoutRequestTask`. **NOTE:**
  this whole OIDC flow is upstream-Floatplane and does not match Sauce+ — see the
  "Auth model" section; it must be reworked to username/password session login.
- `models/`, `creator/`, `post/` — Gson DTOs and domain types.
- `Constants.kt` — token preference keys.

Key invariants already established in the code (don't regress them):

- Pagination uses `fetchAfter = (page - 1) * 20`; a fix prevents load-more from
  jumping back to the latest video.
- There is explicit logged-in/out state management — **do not update the UI when
  logged out**, and reset the UI-init flag on logout.
- All authed requests go through `AuthManager.withValidAccessToken { token -> }`.
- Debug logging is via `MainFragment.dLog(...)` guarded by `BuildConfig.DEBUG`;
  **never log tokens or full auth responses.**

## Rebrand surface (Floatplane/Hydravion → Sauce+)

Audit with `grep -rin 'hydravion\|floatplane\|bmlzootown' app/src`. Notable spots:

- Endpoints: `HydravionClient.kt` (`SITE`, all `/api/v3/...`), `SocketClient.kt`
  (`SOCKET_URI`, `Origin` header).
- Auth: `AuthManager.kt`, `QrLoginActivity.java`, `LogoutRequestTask.java` —
  currently `https://auth.floatplane.com/realms/floatplane/...` (Keycloak OIDC).
  **Sauce+ has no such realm** — this is a rewrite to username/password session
  login + Discord OAuth, behind Cloudflare Turnstile (see "Auth model").
- Identity: `applicationId` + `namespace` in `app/build.gradle`; `app_name` /
  `browse_title` in `res/values/strings.xml`.
- Update check: `LATEST` GitHub URL points at the upstream Hydravion repo.
- Assets: `res/drawable/banner.png`, `icon.png`, `ic_hydravion.xml`, launcher
  mipmaps, `colors.xml`.

## Sub-agents and the review gate (IMPORTANT)

This repo defines sub-agents in `.claude/agents/`:

- **`rebrand-engineer`** — identity + endpoint migration to Sauce+.
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
- Prefer reusing the existing `HydravionClient`/`RequestTask`/presenter patterns
  over introducing parallel mechanisms.
- This is a fork that may still track upstream — make targeted changes; don't
  do sweeping rewrites unless asked.
- Keep secrets and tokens out of logs and source.
