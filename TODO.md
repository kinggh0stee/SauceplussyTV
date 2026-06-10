# SaucedplussyTV TODO / Roadmap

## High Priority — Technical Debt

~~All items resolved. ✓~~

## Medium Priority

### Dependency Updates
- ~~**AndroidX Leanback:** done (1.2.0)~~
- ~~**`core-ktx`:** done (1.19.0, compileSdk 37)~~
- ~~**Glide:** done (5.0.7)~~
- ~~**Gson:** done (2.14.0)~~
- ~~**ZXing:** removed (QR features unused since Keycloak rewrite)~~
- ~~**OkHttp:** done (5.4.0)~~
- ~~**Volley:** done (1.2.1, no upstream release since 2021)~~
- ~~**socket.io-client:** done (2.1.2)~~
- ~~**lifecycle:** done (2.10.0)~~
- ~~**prettytime:** done (5.0.9.Final)~~

### Build & Tooling
- ~~**CompileSdk:** done (37)~~
- ~~**Add unit tests:** done — `VideoDetailsViewModelTest` (9 tests) + `MainViewModelTest` (6 tests); JVM-only with synchronous fakes; `testImplementation` deps + `returnDefaultValues = true`~~
- ~~**CI/CD:** GitHub Actions for automated builds~~

### Code Quality
- ~~**Coroutine adoption:** done — AuthManager writes are fire-and-forget `scope.launch`; WebLoginActivity polling migrated; `liveHandler` (last raw Handler) replaced with `launchDelayed` / `viewLifecycleOwner.lifecycleScope`; no raw Threads or Handlers remain~~
- ~~**SharedPreferences → DataStore:** done — AuthManager fully migrated; zero SharedPreferences usages remain~~
- ~~**Remove deprecated APIs:** partially done (deprecated imports + mipmap-anydpi-v26 removed); see Deprecation cleanup above~~

## Low Priority / Ideas

### Features (from Sauce+ gap analysis)

> Unofficial client — **no** membership, billing, creator-tools, or account-management features.

**Biggest UX holes (TV-appropriate):**
- **Search** (`/api/v3/content/search`) — no search UI at all
- ~~**Subtitles / CC** (`/api/cms/v3/content/upload/texttrack`)~~ done
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
- ~~**Dependency Injection — Pass 1:** Hilt 2.59.2 wired; `@Inject constructor` + `@Singleton` on all three client classes; `AppModule` + `SaucedplussyTVApp`; builds clean~~
- ~~**Dependency Injection — Pass 2:** `@AndroidEntryPoint` on all Activities/Fragments; `@Inject` fields replace all `getInstance()` call sites; `SubscriptionHeaderPresenter` via `@EntryPoint`; companions deleted~~
- ~~**MVVM — ViewModels:** `VideoDetailsViewModel` + `MainViewModel` complete; all data/network/lifecycle state extracted from Fragments; `Event<T>` single-shot LiveData; fully private VM fields; `MainFragment` 1032→821 lines~~
- ~~**Repository pattern:** done — `VideoRepository` + `SubscriptionRepository` interfaces; `SaucedplussyVideoRepository` / `SaucedplussySubscriptionRepository` delegates; `RepositoryModule` Hilt `@Binds`; ViewModels inject interfaces~~
- ~~**`liveHandler` → coroutines:** done — `Handler(Looper.getMainLooper())` removed; post-login delay uses `launchDelayed()` (`MainFragmentExt.kt`); dead `setupLiveCheck`/`addLiveToRow` polling deleted (live detection via socket `CONTENT_LIVESTREAM_START` event)~~
- **Navigation Component:** Replace manual `Intent` navigation (high friction with Leanback — evaluate before starting)

### Out of scope (unofficial TV client)
- ❌ Subscription purchase / cancel / change plan
- ❌ Payment methods / invoices / billing
- ❌ Account settings (email, password, 2FA, avatar, deletion)
- ❌ Support tickets
- ❌ Creator tools (upload, analytics, moderation)
- ❌ Push notifications (requires backend infra)

## Completed ✓

- [x] Per-subscription sidebar rows — each creator appears as a named ListRow with icon in the Leanback header panel; Browse grid and Settings always bookend the list
- [x] Fast incremental row rendering — rows appear on first video response (~2s), remaining creators slot in dynamically via `addOrUpdateSubRow()`
- [x] GUID-keyed creator icon lookup in `SubscriptionHeaderPresenter` — eliminates name-collision and handles async cache fills
- [x] Browse PageRow header now visible — widened cast from `ListRow` to `Row` in `SubscriptionHeaderPresenter`
- [x] `mergeSortAllVideos()` null-safe for empty `releaseDate` fields
- [x] `BrowseGridFragment` crash fix — added `MainFragmentAdapterProvider` interface
- [x] WebView security hardening — `allowFileAccess=false`, `allowContentAccess=false`, `MIXED_CONTENT_NEVER_ALLOW`
- [x] Gradle 8.13 → 9.4.1
- [x] Android Gradle Plugin 8.13.1 → 9.2.1
- [x] Kotlin 1.6.21 → 2.4.0
- [x] ExoPlayer 2.17.1 → 2.19.1
- [x] ExoPlayer 2.x → Media3 1.10.1 (media3-exoplayer, media3-exoplayer-hls, media3-ui, media3-session, media3-datasource-okhttp)
- [x] Auth rewrite: Keycloak OIDC → WebView cookie-session
- [x] API host: floatplane.com → sauceplus.com
- [x] App rebrand: Hydravion → SaucedplussyTV
- [x] Package rename: ml.bmlzootown.hydravion → com.saucedplussytv.androidtv
- [x] Brand colors: res/values/colors.xml updated
- [x] Subtitle / CC track selection (Media3 TrackSelectionOverride)
- [x] Playback speed control (0.5×–2×)
- [x] AndroidX Leanback 1.0.0 → 1.2.0
- [x] AndroidX DataStore migration (SharedPreferences → datastore-preferences 1.1.1)
- [x] WebLoginActivity polling: Handler/Runnable → lifecycleScope coroutines
- [x] Removed deprecated imports and mipmap-anydpi-v26 qualifier
- [x] ZXing dependency removed (was unused since Keycloak rewrite)
- [x] socket.io-client 1.0.1 → 2.1.2
- [x] lifecycle 2.4.1 → 2.10.0 (livedata-ktx + viewmodel-ktx)
- [x] OkHttp 4.12.0 → 5.4.0
- [x] Gson 2.11.0 → 2.14.0
- [x] Glide 4.16.0 → 5.0.7
- [x] prettytime 5.0.0.Final → 5.0.9.Final
- [x] compileSdk 34 → 37
- [x] `getActivity()` null-safety — all async callbacks in MainFragment + VideoDetailsFragment
- [x] Deprecation cleanup — 80 warnings fixed; 8 non-deprecation lint hints remain (layout/perf)
- [x] Hilt DI Pass 1 — `@HiltAndroidApp`, `di/AppModule`, `@Inject constructor` + `@Singleton` on SaucedplussyTVClient / SocketClient / RequestTask; `kotlin-metadata-jvm:2.4.0` forced for Kotlin 2.4.0 compat; KSP 2.3.9
- [x] Hilt DI Pass 2 — `@AndroidEntryPoint` on all Activities/Fragments; `@Inject` fields replace all `getInstance()` call sites; `SubscriptionHeaderPresenter` wired via `@EntryPoint`; all `getInstance()` companions deleted
- [x] VideoDetailsViewModel — MVVM proof-of-concept: resolution picker data chain extracted from `VideoDetailsFragment`; `Event<T>` wrapper for single-shot LiveData (no spurious dialog replay on recreation); in-flight guard against duplicate loads; `fetchVideoUrl` error routing via `_dataError`
- [x] MainViewModel — all data fields (`subscriptions`, `videos`, `strms`, `videoProgress`, `creatorPages`, `creatorNames`, etc.) + subscription loading + pagination + progress fetch + logout clearing moved from `MainFragment`; `subCount`/`fetchProgressAsync` trigger consolidated in VM; `loadGeneration` guard on `checkLive` inner callback; `MainFragment` reduced from 1032 → 821 lines
- [x] MainViewModel debt cleanup — all `@JvmField var` fields made `private`; `adapterInitialized` reverse-coupling eliminated (replaced with VM-owned `initialBatchComplete`); `needsInitRows` removed from `CreatorVideos`; `initialize()` method encapsulates retry reset; `fetchProgressAsync()` made private
- [x] Repository pattern — `VideoRepository` + `SubscriptionRepository` interfaces with Volley threading KDoc; `SaucedplussyVideoRepository` / `SaucedplussySubscriptionRepository` delegate implementations; `RepositoryModule` Hilt `@Binds @Singleton`; both ViewModels inject interfaces instead of `SaucedplussyTVClient` directly
- [x] Unit tests — first JVM test suite: `FakeVideoRepository` + `FakeSubscriptionRepository` synchronous fakes; `VideoDetailsViewModelTest` (9 tests) + `MainViewModelTest` (6 tests); `InstantTaskExecutorRule`; `testOptions.returnDefaultValues = true`; all 15 tests green
- [x] `liveHandler` → coroutines — `Handler(Looper.getMainLooper())` removed from `MainFragment`; post-login 1500ms delay migrated to `launchDelayed()` in `MainFragmentExt.kt` (view-lifecycle scope); dead `setupLiveCheck()`/`addLiveToRow()` polling deleted (live detection handled by socket `CONTENT_LIVESTREAM_START`); no raw `Handler` or `Looper` remains in the browse package
