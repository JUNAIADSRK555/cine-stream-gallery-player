# CineStream Gallery Player 🎬

> A premium Media3 ExoPlayer client built with **Kotlin** and **Jetpack Compose** for **Android & Android TV**, featuring a modern **Dark Navy (`#0B1220`)** and **Cyan (`#00E5FF`)** UI.

![Platform](https://img.shields.io/badge/Platform-Android%20%7C%20Android%20TV-00E5FF?style=for-the-badge&logo=android)
![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-purple?style=for-the-badge&logo=kotlin)
![Media3](https://img.shields.io/badge/Media3%20ExoPlayer-1.4.1-blue?style=for-the-badge)
![UI](https://img.shields.io/badge/UI-Dark%20Navy%20%26%20Cyan-0B1220?style=for-the-badge)

---

## ✨ Features

CineStream Gallery Player is designed specifically with **ONLY 3 streamlined tabs** (no cluttered settings tabs):

### 1. 🏠 Home Tab
- **Custom Stream URL**: Stream any HLS (`.m3u8`), DASH (`.mpd`), SmoothStreaming (`.ism`), or Progressive MP4/MKV directly.
- **Custom HTTP Headers**: Injects custom `Cookie`, `Referer`, `Origin`, and `User-Agent` headers into `DefaultHttpDataSource`.
- **DRM Protection**: Complete support for Widevine, ClearKey, and PlayReady DRM with custom license servers.
- **Format Overrides**: Auto-detect, HLS, DASH, SmoothStreaming, or Progressive MP4.
- **Quick Test Presets**: 1-click test streams (Big Buck Bunny MP4, Sintel HLS, Tears of Steel DASH) for instant verification.
- **Cyan Glow Play Button**: Prominent action button with D-pad & touch support.

### 2. 📁 Local Tab
- **Gallery Video Permission Card**: Dynamic permission card for Android 13+ (`READ_MEDIA_VIDEO`) and earlier versions (`READ_EXTERNAL_STORAGE`) with status indicators and 1-click grant.
- **USB / Folder Picker**: Storage Access Framework integration (`OpenDocumentTree` & `OpenDocument`) to browse external USB drives, OTG, and custom directories.
- **Local Gallery Grid**: Queries device `MediaStore` and renders responsive cards with video thumbnails, duration badges, and file sizes.

### 3. 📺 Playlist Tab
- **Empty State by Default**: Starts clean with no pre-loaded channels.
- **`+ Add M3U`**: Modal dialog to load IPTV playlists from a remote URL or local `.m3u` / `.m3u8` file.
- **M3U Parser**: Robust parsing of `#EXTINF`, channel names, `tvg-logo`, and `group-title`.
- **Live Search**: Instant filtering across channels and categories.
- **Category Filter Chips**: Horizontal chip selector (`All`, `Sports`, `News`, `Movies`, etc.).

---

## 🎮 Android & Android TV Navigation

Built from the ground up for hybrid touch screens and 10-foot TV experiences:
- **D-Pad Focus Highlights**: Glowing cyan (`#00E5FF`) border and subtle 1.03x scale animation on focused elements.
- **Android TV Remote Keys**:
  - `D-Pad Left / Right`: Seek backward/forward 10s during playback.
  - `D-Pad Up / Down`: Reveal player controls overlay.
  - `D-Pad Center / OK`: Play/Pause toggle or select focused action.
  - `Back`: Exit player overlay to return to active tab.
- **Touch / Mobile**: Full gestural touch feedback with auto-hiding controls (4-second timeout) and aspect ratio switching (`Fit`, `Fill`, `Zoom`, `Fixed Width`, `Fixed Height`).

---

## 🛠️ Tech Stack & Architecture

- **Language**: Kotlin 2.0.21
- **UI Toolkit**: Jetpack Compose BOM 2024.10.01 + Material3
- **Media Engine**: AndroidX Media3 ExoPlayer 1.4.1 (HLS, DASH, SmoothStreaming, UI)
- **Image & Thumbnail Loading**: Coil 2.7.0 with video frame decoder
- **Architecture**: Single Activity Compose architecture with declarative navigation
- **CI/CD**: GitHub Actions workflow (`.github/workflows/build.yml`) for automated APK compilation

---

## 🚀 Building & Running

### Requirements
- Android Studio Ladybug / Koala or newer
- JDK 17
- Android SDK 35 (minSdk: 24, targetSdk: 35)

### Build Debug APK locally
```bash
./gradlew assembleDebug
```
The output APK will be generated at:
```
app/build/outputs/apk/debug/app-debug.apk
```

---

## 📦 GitHub Actions CI

Every push and pull request to `main` automatically triggers the GitHub Actions workflow to build and package the APK, available as an artifact download under the Actions tab.

---

## 📄 License
MIT License
