# IDLIX for Android TV 🎬📺

A modern, high-performance, **100% native Android TV streaming client** for movies and TV series from IDLIX, built with a lean **multi-module architecture**.

The project is split into independent, complementary modules:
1. **Shared Core Data (`core/data/`)**: pure Kotlin library with no UI dependencies. Owns the HTTP client, the IDLIX mirror redirect-follow resolver, data models, lenient deserializers, repositories, and local watch-history storage (`WatchHistoryStore`).
2. **Android TV Client (`app-tv/`)**: native Android TV / Google TV app built with Jetpack Compose for TV (`androidx.tv.material3`) and Media3 ExoPlayer, with a Leanback interface (D-pad remote, 10-foot UI, fixed landscape).

> **Catalog source**: the canonical source is `https://idlix.net/` — at runtime the app follows its redirect (302 `Location`) to the currently active mirror and derives Referer / Origin / redeem URLs from the resolved base. No self-hosted server, no VPS, nothing to run locally.

---

## 🌟 Key Features

* **100% Native Architecture**: no Chromium or WebView inside the APK. Uses the TV's hardware decoders (`MediaCodec`) via AndroidX Media3 ExoPlayer.
* **Netflix-Style Leanback UI**:
  * Floating navigation sidebar with smooth overlay (no content re-layout).
  * Dynamic Hero billboard with synopsis preview and quick-play button.
  * Horizontal swimlane rows for Trending, Popular, TV Series, and Genre categories.
  * *Continue Watching* with a visual progress bar.
* **Token Query Preserving HLS Player**:
  * Automatically preserves CDN token authorization (`?t=...`) across sub-playlists and media chunk requests (`.webp`, `.jpg`, `.js`, etc.).
  * Adaptive TV buffer control for instant, stutter-free playback.
* **Subtitles & SubDL Integration**:
  * Built-in IDLIX subtitles (Indonesian & English).
  * Third-party SubDL.com search and download, right from the TV remote.
* **In-App Settings**:
  * SubDL API key management with a real-time connection test, all from the TV screen via D-pad.
  * Advanced section (hidden) for diagnostics: domain resolver status, error log, cache controls.
* **In-App Auto-Update**: checks GitHub releases for new versions, downloads the APK on the TV, and installs it — no ADB or manual sideloading needed.
* **Bilingual UI**: full Indonesian (`id`/`in`) and English (`en`) support, following the TV system language.

---

## 📁 Project Structure

```
idlix-tv/
├── core/
│   └── data/                            # [Shared Library] Resolver, HTTP, Repository & DB
│       ├── src/main/java/com/idlix/data/
│       │   ├── net/                     # CoreHttp, IdlixDomainResolver (idlix.net redirect-follow)
│       │   ├── local/                   # WatchHistoryStore (local watch history)
│       │   ├── model/                   # MovieItem, SeriesItem, Subdl, Serializers
│       │   └── repository/              # IdlixRepository & SubdlRepository
│       └── build.gradle.kts
│
├── app-tv/                              # [Application] Android TV / Google TV client
│   ├── src/main/java/com/idlix/tv/
│   │   ├── ui/
│   │   │   ├── components/              # TvNavDrawer, TvMovieCard, TvHeroBillboard, etc.
│   │   │   ├── navigation/              # TV NavHost & routes
│   │   │   ├── screens/                 # Home, Movies, Series, Detail, Player, Settings
│   │   │   └── theme/                   # Leanback Dark Theme
│   │   └── res/                         # TV banner assets & localized strings
│   └── build.gradle.kts
│
├── gradle/                              # Gradle wrapper & version catalog (libs.versions.toml)
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
└── local.properties                     # Local config: Android SDK + signing + SubDL key (git-ignored)
```

---

## 🛠️ Build Guide

No backend to run — the app resolves the IDLIX mirror directly from `https://idlix.net/` at runtime.

### 1. Configure `local.properties`

Copy the sample and fill in your local values (this file is git-ignored, never commit it):

```properties
sdk.dir=/path/to/Android/Sdk
storeFile=/path/to/upload-keystore.jks
storePassword=***
keyAlias=***
keyPassword=***
subdlApiKey=***
```

* `sdk.dir` — Android SDK location.
* `storeFile` / `storePassword` / `keyAlias` / `keyPassword` — release signing. Without these, `assembleRelease` produces an **unsigned** APK (fine for sideloading; required for store-style distribution).
* `subdlApiKey` — your personal SubDL API key (https://subdl.com/api-docs) for third-party subtitle search. Without it, built-in IDLIX subtitles still work; only SubDL search is disabled.

### 2. Build the APK

```bash
# Debug APK
./gradlew :app-tv:assembleDebug
# Output: app-tv/build/outputs/apk/debug/app-tv-debug.apk

# Release APK
./gradlew :app-tv:assembleRelease
# Output: app-tv/build/outputs/apk/release/app-tv-release.apk
```

### 3. Install on Android TV via ADB

```bash
adb connect 192.168.x.tv:5555
adb -s 192.168.x.tv:5555 install -r app-tv/build/outputs/apk/debug/app-tv-debug.apk
```

Non-technical users: see [TV_INSTALLATION_GUIDE.md](./TV_INSTALLATION_GUIDE.md) for sideloading without a computer.

---

## Download Release APK

Grab the latest APK from the [releases page](https://github.com/trefeon/idlix-tv/releases). The app also checks this page automatically and offers in-app updates.

---

## 🔒 Security & License

* Sensitive files such as `local.properties`, signing keystores (`*.jks`), and environment credentials are protected by [`./.gitignore`](./.gitignore).
* Built for education and research into native Android TV architecture and HLS streaming.
