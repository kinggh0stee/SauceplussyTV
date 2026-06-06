# SaucedplussyTV

An unofficial Android TV client for [Sauce+](https://www.sauceplus.com).

> **⚠️ Disclaimer:** This is an unofficial, third-party client. It is not affiliated with, endorsed by, or connected to Sauce+, Floatplane Media Inc., or the original Hydravion project.

---

## Overview

SaucedplussyTV brings Sauce+ content to Android TV devices with a lean, D-pad optimized interface. It is a fork of [Hydravion](https://github.com/bmlzootown/Hydravion-AndroidTV) (an unofficial Floatplane Android TV client), substantially modified and repointed for the Sauce+ platform.

While it shares lineage with Hydravion, SaucedplussyTV is a distinct project with major architectural differences:

- **Authentication:** Complete rewrite from Keycloak OIDC to WebView cookie-session login (required for Sauce+'s Cloudflare-protected backend)
- **Backend:** Repointed from `floatplane.com` to `sauceplus.com`
- **Player:** Migrated from ExoPlayer 2.x to AndroidX Media3
- **Architecture:** Ongoing structural improvements and modernization

## Features

- Browse subscriptions and video content
- HLS playback with quality adaptation via Media3/ExoPlayer
- Livestream support with real-time notifications
- Subtitle / CC track selection
- Resume playback from saved progress
- Like/dislike video interactions
- D-pad optimized interface for Android TV
- Cloudflare Turnstile authentication via WebView

## Requirements

- Android TV or Android device with D-pad/remote input
- Android 8.0+ (API 26+)
- Sauce+ account
- Active Sauce+ subscription for premium content

## Installation

Pre-built APKs are available on the [Releases](https://github.com/kinggh0stee/Sauce-AndroidTV/releases) page.

1. Download the latest APK from Releases
2. Install via `adb install` or sideload onto your Android TV device
3. Launch the app and sign in via the WebView login flow

## Building from Source

### Prerequisites

- Android Studio with Android SDK
- JDK 17+ (note: system JDK 26+ is incompatible with AGP 9.2.1; use Android Studio's bundled JBR)

### Build

```bash
# Use Android Studio's bundled JDK
JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:assembleDebug
```

### Lint

```bash
JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:lint
```

> **Note:** There is no unit test suite. Verification is done via compilation (`assembleDebug`) and on-device testing.

## Tech Stack

| Component | Technology |
|-----------|-----------|
| Platform | Android TV (AndroidX Leanback) |
| Languages | Kotlin, Java |
| Build System | Gradle 9.4.1 (Groovy DSL) |
| Android Gradle Plugin | 9.2.1 |
| Compile SDK | 37 |
| Target SDK | 35 |
| Min SDK | 26 |
| Player | AndroidX Media3 (ExoPlayer-based) |
| Networking | Volley 1.2.1, OkHttp 5.3.2, socket.io-client 2.1.2 |
| Serialization | Gson |
| Images | Glide 5 |

## Architecture

The app follows a pragmatic architecture suited to a Leanback TV application:

- **`browse/`** — MainActivity and MainFragment (central coordinator)
- **`client/`** — API facade (SaucedplussyTVClient), HTTP layer (RequestTask), socket.io sync (SocketClient)
- **`authenticate/`** — AuthManager (session storage), WebLoginActivity (WebView cookie harvest)
- **`playback/`** — Media3 playback screen with cookie-injected HLS streaming
- **`detail/`** — Leanback details screen with video metadata and actions

See [CLAUDE.md](CLAUDE.md) for detailed architectural notes, auth flow, and conventions.

## Development

### Roadmap

See [TODO.md](TODO.md) for planned improvements, feature gaps, and ongoing work.

### Contributing

This is a personal fork project. While contributions are welcome, please note:

- The primary goal is a stable, lean TV experience
- Features should be appropriate for D-pad/remote navigation
- No membership, billing, or creator-tool features (this is an unofficial client)
- All code changes must compile (`assembleDebug`) and pass Android lint

## License

MIT License — see [LICENSE](LICENSE) for details.

Copyright (c) 2020 Brandon Lee (original Hydravion)

## Acknowledgments

- Original [Hydravion](https://github.com/bmlzootown/Hydravion-AndroidTV) project by bmlzootown, NickM-27, and Jman012
- Sauce+ platform by Floatplane Media Inc. (this project is unaffiliated)

---

*SaucedplussyTV is provided as-is with no warranty or guarantee of service. Use at your own risk.*
