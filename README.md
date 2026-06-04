# SaucedplussyTV

An unofficial Android TV client for Sauce+ (https://www.sauceplus.com).

**⚠️ DISCLAIMER:** This is an unofficial, third-party client. Not affiliated with, endorsed by, or connected to Sauce+, Floatplane Media Inc., or the original Hydravion project.

## About

SaucedplussyTV is a fork of [Hydravion](https://github.com/bmlzootown/Hydravion-AndroidTV) (an unofficial Floatplane client), heavily modified and repointed for the Sauce+ platform. While it shares lineage with Hydravion, it is a distinct project with substantial changes:

- Complete auth rewrite (WebView cookie-session login replacing Keycloak OIDC)
- Repointed for Sauce+ backend (`sauceplus.com`)
- Major structural and architectural changes
- Independent development path

## Features

- Browse subscriptions and videos
- HLS playback via Media3 ExoPlayer
- Livestream support
- Subtitle / CC track selection
- Playback speed control (0.5×–2×)
- Like / dislike videos
- Resume playback from last position
- D-pad optimized Android TV interface
- Cloudflare Turnstile authentication via WebView

## Tech Stack

- Android TV (Leanback), minSdk 26, compileSdk 34
- Kotlin + Java
- Gradle 9.4.1 + Android Gradle Plugin 9.2.1
- Media3 1.4.1 (ExoPlayer, HLS, Session)
- Volley + OkHttp 4 + socket.io

## Build

```bash
./gradlew :app:assembleDebug
```

Requires JDK 17. On machines where the system JDK is newer, point to a JDK 17 installation:

```bash
JAVA_HOME=/path/to/jdk17 ./gradlew :app:assembleDebug
```

## TODO / Roadmap

See [TODO.md](TODO.md) for planned improvements including dependency updates, architecture improvements, and feature ideas.

## License

MIT License (see LICENSE file)

Original Hydravion project by bmlzootown, NickM-27, Jman012.
