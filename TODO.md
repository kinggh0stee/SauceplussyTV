# SaucedplussyTV TODO / Roadmap

## High Priority

### Media3 Migration
- **Status:** Not started
- **Description:** ExoPlayer 2.x is deprecated. Google recommends migrating to `androidx.media3:media3-exoplayer` (latest: 1.10.1)
- **Impact:** Breaking changes - package names change from `com.google.android.exoplayer2` to `androidx.media3`, API changes, PlayerView migration
- **Benefits:** Better lifecycle support, modern APIs, future-proof, continued security updates
- **Files affected:** `PlaybackActivity.java`, `activity_player.xml`, `view_exo_controller.xml`

### Package Namespace Completion
- **Status:** In progress (source code done, assets remaining)
- **Description:** Full rebrand from `ml.bmlzootown.hydravion` to `com.saucedplussytv.androidtv`
- **Remaining:** Update drawable assets (banner.png, icon.png), launcher mipmaps, colors

## Medium Priority

### Dependency Updates
- **AndroidX Leanback:** 1.0.0 → latest
- ~~**Glide:** done (4.16.0)~~
- ~~**Gson:** done (2.11.0)~~
- ~~**ZXing:** removed (QR features unused since Keycloak rewrite)~~
- ~~**OkHttp:** done (4.12.0 stable)~~
- **Volley:** 1.2.1 → latest

### Build & Tooling
- ~~**CompileSdk:** done (35)~~
- **Add unit tests:** Currently none exist
- **CI/CD:** GitHub Actions for automated builds

### Code Quality
- **Coroutine adoption:** Replace raw Threads with structured concurrency
- **SharedPreferences → DataStore:** Modern replacement
- **Remove deprecated APIs:** Address deprecation warnings in build

## Low Priority / Ideas

### Features
- **Picture-in-Picture (PiP):** For Android TV background playback
- **Search integration:** Android TV global search
- **Recommendations:** Leanback recommendations row
- **Multiple user profiles:** Switch between Sauce+ accounts
- **Download/offline:** Cache videos for offline viewing
- **Subtitle support:** CC/Subtitle track selection
- **Playback speed:** 1.25x, 1.5x, 2x speed options

### UI/UX
- **Dark theme variants:** True black for OLED TVs
- **Customizable home screen:** Reorder subscription cards
- **Watch history:** Better progress tracking UI
- **Settings overhaul:** More granular playback settings

### Architecture
- **MVVM/MVI:** Current architecture is mixed; formalize with ViewModels
- **Repository pattern:** Separate data layer from UI
- **Dependency Injection:** Hilt/Koin adoption
- **Navigation Component:** Replace manual intent navigation

## Completed ✓

- [x] Gradle 8.13 → 9.4.1
- [x] Android Gradle Plugin 8.13.1 → 9.2.1
- [x] Kotlin 1.6.21 → 2.1.10
- [x] ExoPlayer 2.17.1 → 2.19.1
- [x] Auth rewrite: Keycloak OIDC → WebView cookie-session
- [x] API host: floatplane.com → sauceplus.com
- [x] App rebrand: Hydravion → SaucedplussyTV
- [x] Package rename: ml.bmlzootown.hydravion → com.saucedplussytv.androidtv
