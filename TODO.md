# SaucedplussyTV TODO / Roadmap

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
- ~~**CI/CD:** GitHub Actions for automated builds~~

### Code Quality
- **Coroutine adoption:** Replace raw Threads with structured concurrency
- **SharedPreferences → DataStore:** Modern replacement
- ~~**Remove deprecated APIs:** Address deprecation warnings in build~~ 96→90 warnings; remainder blocked on compileSdk bump to 36/37

## Low Priority / Ideas

### Features
- **Picture-in-Picture (PiP):** For Android TV background playback
- **Search integration:** Android TV global search
- **Recommendations:** Leanback recommendations row
- **Multiple user profiles:** Switch between Sauce+ accounts
- **Download/offline:** Cache videos for offline viewing

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
- [x] ExoPlayer 2.x → Media3 1.4.1 (media3-exoplayer, media3-exoplayer-hls, media3-ui, media3-session)
- [x] Auth rewrite: Keycloak OIDC → WebView cookie-session
- [x] API host: floatplane.com → sauceplus.com
- [x] App rebrand: Hydravion → SaucedplussyTV
- [x] Package rename: ml.bmlzootown.hydravion → com.saucedplussytv.androidtv
- [x] Brand colors: res/values/colors.xml updated
- [x] Subtitle / CC track selection (Media3 TrackSelectionOverride)
- [x] Playback speed control (0.5×–2×)
