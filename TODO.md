# SaucedplussyTV TODO / Roadmap

## Medium Priority

### Dependency Updates
- ~~**AndroidX Leanback:** done (1.2.0)~~
- ~~**`core-ktx`:** done (1.19.0, compileSdk 37)~~
- ~~**Glide:** done (5.0.7)~~
- ~~**Gson:** done (2.14.0)~~
- ~~**ZXing:** removed (QR features unused since Keycloak rewrite)~~
- ~~**OkHttp:** done (5.3.2)~~
- ~~**Volley:** done (1.2.1, no upstream release since 2021)~~

### Build & Tooling
- ~~**CompileSdk:** done (37)~~
- **Add unit tests:** Currently none exist
- ~~**CI/CD:** GitHub Actions for automated builds~~

### Code Quality
- **Coroutine adoption:** Replace raw Threads with structured concurrency
- **SharedPreferences → DataStore:** Modern replacement
- **Deprecation cleanup:** Partially done (96→90 warnings); remaining warnings need compileSdk 36/37

## Low Priority / Ideas

### Features (from Sauce+ gap analysis)

> Unofficial client — **no** membership, billing, creator-tools, or account-management features.

**Biggest UX holes (TV-appropriate):**
- **Search** (`/api/v3/content/search`) — no search UI at all
- **Subtitles / CC** (`/api/cms/v3/content/upload/texttrack`) — no text track selection
- **Manual quality selection** — auto-picks highest enabled variant; no user override
- **Watch history page** (`/api/v3/content/history`) — no "Continue watching" row

**Playback polish:**
- **Related content** (`/api/v3/content/related`) — "Up next" when a video ends
- **Background audio playback** — keep audio playing when backgrounded
- **Picture-in-Picture (PiP)** — Android TV background video
- **Timeline sprites / trickplay thumbnails** — thumbnail preview while scrubbing
- **Playback speed default / reset shortcut** — no per-user default or quick reset

**Content discovery & social (read-only, questionable on TV):**
- **Creator discovery** (`/api/v3/creator/discover`) — browse unsubscribed creators
- **Tags** (`/api/v3/content/tags`) — tag-based browsing
- **Comments (read-only)** — view comments thread
- **Live chat (read-only)** — chat during livestreams
- **Polls (read-only voting)** — participate in live polls

**Nice-to-have:**
- **Chromecast support**
- **"Up Next" autoplay overlay** — countdown to next video
- **New-content badges** — mark rows with unseen videos
- **True black OLED theme**

### Architecture
- **MVVM/MVI:** Current architecture is mixed; formalize with ViewModels
- **Repository pattern:** Separate data layer from UI
- **Dependency Injection:** Hilt/Koin adoption
- **Navigation Component:** Replace manual intent navigation

### Out of scope (unofficial TV client)
- ❌ Subscription purchase / cancel / change plan
- ❌ Payment methods / invoices / billing
- ❌ Account settings (email, password, 2FA, avatar, deletion)
- ❌ Support tickets
- ❌ Creator tools (upload, analytics, moderation)
- ❌ Push notifications (requires backend infra)

## Completed ✓

- [x] Gradle 8.13 → 9.4.1
- [x] Android Gradle Plugin 8.13.1 → 9.2.1
- [x] Kotlin 1.6.21 → 2.1.10
- [x] ExoPlayer 2.17.1 → 2.19.1
- [x] ExoPlayer 2.x → Media3 1.4.1 (media3-exoplayer, media3-exoplayer-hls, media3-ui, media3-session)
- [x] Auth rewrite: Keycloak OIDC → WebView cookie-session
- [x] API host: floatplane.com → sauceplus.com
- [x] App rebrand: Hydravion → SaucedplussyTV
- [x] Package rename: ml.bmlzootown.hydravion → com.saucedplussytv.androidtv
- [x] Brand colors: res/values/colors.xml updated
- [x] Subtitle / CC track selection (Media3 TrackSelectionOverride)
- [x] Playback speed control (0.5×–2×)
