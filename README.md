# IDLIX: Android TV & Android Mobile 🎬📺📱

A modern, high-performance, **100% native Android streaming platform** for movies and TV series from IDLIX with multi-platform support (Android TV & Android Mobile) built using a unified **Multi-Module Architecture**.

Project ini terbagi menjadi modul-modul independen yang saling melengkapi:
1. **Shared Core Data (`core/data/`)**: Modul library Kotlin murni tanpa dependensi UI yang mengelola Retrofit client, redirect-follow resolver mirror IDLIX, model data, deserializer toleran, repository, dan penyimpanan riwayat tontonan lokal (`WatchHistoryStore`).
2. **Android TV Client (`app-tv/`)**: Aplikasi khusus Android TV/Google TV berbasis Jetpack Compose for TV (`androidx.tv.material3`) dan Media3 ExoPlayer dengan antarmuka Leanback (remote D-Pad, 10-foot UI, fixed landscape).
3. **Android Mobile Client (`app-mobile/`)**: Aplikasi khusus smartphone & tablet berbasis Jetpack Compose **Material 3** modern (Bottom Navigation Bar, Swipeable Banner Pager, Inline-to-Fullscreen Player, Touch Gestures, dan **Picture-in-Picture**).

> **Sumber katalog**: canonical source `https://idlix.net/` — aplikasi mengikuti redirect (302 `Location`) ke mirror aktif saat ini dan menurunkan Referer/Origin/URL redeem dari base hasil resolve saat runtime. Tidak ada server self-hosted, tidak ada VPS, tidak ada port lokal yang perlu dijalankan.

---

## 🌟 Fitur Utama (Key Features)

### 📱 Android Mobile Client (`app-mobile`)
* **Material 3 & Edge-to-Edge Display**:
  * Tampilan immersive modern dengan handling `WindowInsets` bebas dari benturan notch / kamera punch-hole.
  * Navigasi intuitif **Bottom Navigation Bar** 5 tab: *Home*, *Movies*, *Series*, *Search*, dan *Library*.
* **Hero Carousel & Infinite Scrolling**:
  * Pager banner horizontal otomatis (`HorizontalPager`) dengan indikator titik dinamis dan tombol aksi cepat.
  * Barisan *Lanjutkan Menonton* (Continue Watching) dengan thumbnail 16:9 dan progress bar tontonan.
  * Grid vertikal 3 kolom (smartphone) dan 5 kolom (tablet) dengan infinite scroll pagination dan filter chips genre/sorting.
* **Dual-Mode Player (Inline ke Fullscreen)**:
  * **Mode Inline (Portrait 16:9)**: Video langsung berputar di atas layar sambil pengguna tetap dapat membaca sinopsis dan memilih episode serial di bawahnya.
  * **Mode Fullscreen (Landscape)**: Transisi mulus ke layar penuh immersive melalui tombol expand atau sensor rotasi.
  * **BackHandler Cerdas**: Menekan tombol Back saat fullscreen akan mengecilkan player kembali ke mode inline (tidak langsung keluar layar).
* **Touch Gestures & Media Controls**:
  * **Double-Tap**: Sisi kiri mundur 10 detik, sisi kanan maju 10 detik disertai animasi ripple.
  * **Vertical Drag**: Sisi kiri untuk Brightness (kecerahan), sisi kanan untuk Volume suara.
  * **Timeline Scrubber**: Scrub horizontal presisi untuk loncat ke menit tertentu.
* **Picture-in-Picture (PiP)**:
  * Otomatis masuk ke mode floating PiP saat pengguna menekan Home atau beralih ke aplikasi lain (`onUserLeaveHint`).
* **Integrasi Subtitle SubDL**:
  * Pemilihan trek subtitle bawaan atau pencarian subtitle Bahasa Indonesia/Inggris via SubDL API (api.subdl.com, key milik pengguna via `local.properties`/env — tanpa default bawaan).

### 📺 Android TV Client (`app-tv`)
* **100% Native Architecture**: Bebas Chromium dan WebView di dalam APK. Menggunakan hardware decoder bawaan TV (`MediaCodec`) via AndroidX Media3 ExoPlayer.
* **Netflix-Style Leanback UI**:
  * Floating navigation sidebar dengan smooth overlay (tanpa re-layout konten).
  * Billboard Hero dinamis dengan preview sinopsis dan tombol Putar Cepat.
  * Baris Swimlane horizontal untuk Tren, Populer, Serial TV, dan Kategori Genre.
  * Fitur *Lanjutkan Menonton* (Continue Watching) dengan bar progress visual.
* **Token Query Preserving HLS Player**:
  * Mengatasi proteksi CDN Majorplay (Cloudflare token authorization `?t=...`) secara otomatis pada sub-playlist dan chunk segmen video (`.webp`, `.jpg`, `.js`, dll.).
  * Buffer kontrol TV adaptif untuk pemutaran instan tanpa stuttering.
* **Manajemen Subtitle & Integrasi SubDL**:
  * Subtitle internal IDLIX otomatis (Indonesian & English).
  * Integrasi third-party SubDL.com untuk pencarian dan pengunduhan subtitle alternatif langsung dari player remote TV.
* **Menu Pengaturan Terintegrasi (In-App Settings)**:
  * Pengaturan custom API Key SubDL & tombol Uji Koneksi *real-time* langsung dari layar TV menggunakan remote D-pad.
* **Bilingual Localization**:
  * Mendukung penuh Bahasa Indonesia (`id`/`in`) dan English (`en`). Otomatis mengikuti bahasa sistem TV.

---

## 📁 Struktur Direktori Multi-Module (Project Structure)

```
idlix-tv/
├── core/
│   └── data/                            # [Shared Library] Resolver, HTTP, Repository & DB
│       ├── src/main/java/com/idlix/data/
│       │   ├── net/                     # CoreHttp, IdlixDomainResolver (idlix.net redirect-follow)
│       │   ├── local/                   # WatchHistoryStore (penyimpanan riwayat tontonan)
│       │   ├── model/                   # MovieItem, SeriesItem, Subdl, Serializers
│       │   └── repository/              # IdlixRepository & SubdlRepository
│       └── build.gradle.kts
│
├── app-tv/                              # [Application] Modul Khusus Android TV / Google TV
│   ├── src/main/java/com/idlix/tv/
│   │   ├── ui/
│   │   │   ├── components/              # TvNavDrawer, TvMovieCard, TvHeroBillboard, dll.
│   │   │   ├── navigation/              # TV NavHost & routes
│   │   │   ├── screens/                 # Home, Movies, Series, Detail, Player, Settings
│   │   │   └── theme/                   # Leanback Dark Theme
│   │   └── res/                         # Asset TV banner & strings localization
│   └── build.gradle.kts
│
├── app-mobile/                          # [Application] Modul Khusus Smartphone & Tablet
│   ├── src/main/java/com/idlix/mobile/
│   │   ├── ui/
│   │   │   ├── components/              # MobileMovieCard, HeroBannerPager, FilterChips, ContinueWatching
│   │   │   ├── navigation/              # Bottom Navigation Bar, NavHost, MainScaffold
│   │   │   ├── screens/                 # Home, Movies, Series, Search, Library, Detail, Settings
│   │   │   └── theme/                   # Material 3 Dark Theme & Edge-to-Edge styling
│   │   └── res/                         # Asset mobile & strings
│   └── build.gradle.kts
│
├── gradle/                              # Gradle wrapper & version catalogs (libs.versions.toml)
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── local.properties                     # Konfigurasi lokal: Android SDK + signing + SubDL key (git-ignored)
└── PORTING_TO_MOBILE_PLAN.md            # Rencana arsitektur & roadmap implementasi teknis (arsip)
```

---

## 🛠️ Panduan Memulai & Kompilasi APK (Build Guide)

Tidak ada backend yang perlu dijalankan — aplikasi menyelesaikan mirror IDLIX langsung dari `https://idlix.net/` saat runtime.

### 1. Konfigurasi `local.properties`

Salin contoh dan isi nilai lokal Anda (file ini git-ignored, jangan commit):

```properties
sdk.dir=/path/ke/Android/Sdk
storeFile=/path/ke/upload-keystore.jks
storePassword=***
keyAlias=***
keyPassword=***
subdlApiKey=***
```

* `sdk.dir` — lokasi Android SDK.
* `storeFile` / `storePassword` / `keyAlias` / `keyPassword` — signing release (wajib untuk `assembleRelease`; tanpa ini build release gagal dengan pesan yang jelas).
* `subdlApiKey` — API key SubDL pribadi (https://api.subdl.com) untuk subtitle pihak ketiga. Tanpa key ini, subtitle bawaan IDLIX tetap berfungsi; hanya pencarian SubDL yang nonaktif.

### 2. Membangun APK Android TV (`app-tv`)

#### Build APK Debug / Release:
```bash
# Debug APK
./gradlew :app-tv:assembleDebug
# Output: app-tv/build/outputs/apk/debug/app-tv-debug.apk

# Release APK
./gradlew :app-tv:assembleRelease
# Output: app-tv/build/outputs/apk/release/app-tv-release.apk
```

#### Install ke Android TV via ADB:
```bash
adb connect 192.168.x.tv:5555
adb -s 192.168.x.tv:5555 install -r app-tv/build/outputs/apk/debug/app-tv-debug.apk
```

---

### 3. Membangun APK Android Mobile (`app-mobile`)

#### Build APK Debug / Release:
```bash
# Debug APK
./gradlew :app-mobile:assembleDebug
# Output: app-mobile/build/outputs/apk/debug/app-mobile-debug.apk

# Release APK
./gradlew :app-mobile:assembleRelease
# Output: app-mobile/build/outputs/apk/release/app-mobile-release.apk
```

#### Install ke Perangkat Smartphone / Tablet via ADB:
```bash
# Cek ID perangkat mobile
adb devices

# Install ke perangkat target
adb -s <device-id> install -r app-mobile/build/outputs/apk/debug/app-mobile-debug.apk

# Jalankan aplikasi mobile
adb -s <device-id> shell am start -n com.idlix.mobile.debug/com.idlix.mobile.MainActivity
```

> **Catatan untuk Perangkat Xiaomi / MIUI / HyperOS**:
> Pastikan opsi **Install via USB** telah diaktifkan di *Developer Options*, dan tekan **Install / Allow** saat jendela konfirmasi muncul di layar ponsel.

---

## Download Release Apk

Look at this [page](https://github.com/trefeon/idlix-tv/releases).

---

## 🔒 Keamanan & Lisensi

* File sensitif seperti `local.properties`, sertifikat signing keystore (`*.jks`), dan credential environment dilindungi di [`./.gitignore`](./.gitignore).
* Proyek ini dibuat untuk keperluan edukasi dan riset arsitektur multi-module Android Native (TV & Mobile) serta streaming HLS.
