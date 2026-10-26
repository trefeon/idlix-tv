# IDLIX for Android TV 🎬📺

[![Release](https://img.shields.io/github/v/release/trefeon/idlix-tv)](https://github.com/trefeon/idlix-tv/releases)
[![CI](https://github.com/trefeon/idlix-tv/actions/workflows/android-ci.yml/badge.svg)](https://github.com/trefeon/idlix-tv/actions/workflows/android-ci.yml)
[![License](https://img.shields.io/github/license/trefeon/idlix-tv)](./LICENSE)

A modern, **100% native Android TV streaming client** for movies and TV series from IDLIX — Leanback UI, hardware decoding via Media3 ExoPlayer, and in-app auto-updates. No backend to run, no account needed.

> **New here?** Grab the latest APK from [Releases](https://github.com/trefeon/idlix-tv/releases) and follow the [installation guide](./TV_INSTALLATION_GUIDE.md) — no computer required.

---

## ✨ Features

| Area | What you get |
|---|---|
| **Leanback UI** | Floating nav sidebar, full-bleed Hero billboard, Top-10 rail, swimlane rows, Continue Watching with progress |
| **Player** | Token-preserving HLS playback, aspect-aware subtitles (transparent background, outlined white text), embedded + SubDL external subtitles, next-episode prompt |
| **Settings** | Subtitle language, SubDL API key with connection test, cache/history management, hidden advanced diagnostics (resolver status, error log) |
| **Self-maintenance** | Startup self-repair (corrupt prefs, cache), domain resolver with cached fallback, daily background update checks |
| **Auto-update** | Checks GitHub releases, downloads and installs new versions on the TV itself |

**Catalog source:** the app resolves the live IDLIX mirror at runtime from the canonical `https://idlix.net/` (redirect-follow, allowlisted hosts, fail-closed). No self-hosted server, no VPS, nothing to deploy.

---

## 📥 Install

1. Download `idlix-tv-v*.apk` from the [releases page](https://github.com/trefeon/idlix-tv/releases).
2. Follow **[TV_INSTALLATION_GUIDE.md](./TV_INSTALLATION_GUIDE.md)** — three sideload methods (phone transfer, USB stick, direct download), written for non-technical users.
3. On first launch the app resolves the current mirror automatically; if a check fails, see *Troubleshooting* in the guide.

> ⚠️ **Upgrading from v1.0.0?** Uninstall the old app first — v1.0.0 was unsigned and Android refuses to install the signed v1.0.1+ over it. Local watch history is cleared on uninstall.

---

## 🛠️ Build from source

**Prerequisites:** JDK 21, Android SDK (API 35), ~4 GB free disk.

```bash
git clone https://github.com/trefeon/idlix-tv.git
cd idlix-tv
cp local.properties.sample local.properties   # then fill in your values
./gradlew :app-tv:assembleDebug                # debug APK
./gradlew :app-tv:assembleRelease              # release APK (signed if a keystore is configured, else unsigned)
```

### `local.properties` keys

| Key | Required for | Notes |
|---|---|---|
| `sdk.dir` | all builds | Android SDK location (or set `ANDROID_HOME`) |
| `storeFile` / `storePassword` / `keyAlias` / `keyPassword` | signed releases | Without these, `assembleRelease` emits an **unsigned** APK and warns |
| `subdlApiKey` | SubDL subtitles | Personal key from [subdl.com/api-docs](https://subdl.com/api-docs); built-in IDLIX subtitles work without it |

APK outputs: `app-tv/build/outputs/apk/{debug,release}/`. Install via ADB:

```bash
adb connect <tv-ip>:5555
adb install -r app-tv/build/outputs/apk/debug/app-tv-debug.apk
```

Run the checks before pushing: `./gradlew :core:data:testDebugUnitTest :app-tv:testDebugUnitTest`.

---

## 📁 Project structure

```
idlix-tv/
├── core/data/        # Shared library: HTTP client, idlix.net resolver, models,
│                      # repositories, SubDL client, watch-history store
├── app-tv/           # Android TV app: Leanback UI, player, settings, updater, self-heal
├── gradle/           # Version catalog (libs.versions.toml) + wrapper
└── .github/workflows/# android-ci.yml (build+test) · android-release.yml (signed APKs + GitHub release)
```

---

## 🤝 Contributing

Issues and pull requests are welcome. Please: keep the TV UI D-pad navigable (every interactive element focusable with a visible ring), keep overscan-safe 48dp margins, add unit tests for new logic in `core/data`, and never commit `local.properties`, keystores, or API keys.

---

## ⚖️ License & disclaimer

MIT — see [LICENSE](./LICENSE). This is an **unofficial third-party client**, not affiliated with or endorsed by IDLIX. All content, artwork, and trademarks belong to their respective owners. Use it in compliance with your local laws and your streaming providers' terms.
