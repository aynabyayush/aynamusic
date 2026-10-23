# AYNA Music 🎵

**AYNA Music** is a modern, high-performance Android music player and streaming application built completely with **Jetpack Compose**, **Material 3**, and **Jetpack Media3 (ExoPlayer)**.

It seamlessly blends local device audio playback with online catalog streaming, synchronized LRC lyrics, custom equalizer controls, offline downloads, and cloud sync features.

---

## ✨ Features

- 🎧 **Media3 Audio Engine**:
  - Powered by `androidx.media3` (ExoPlayer) with background playback via foreground `PlaybackService`.
  - Rich lockscreen controls, notification controls, and media session integration.
  - Built-in audio equalizer manager with customizable presets.

- 🎨 **Modern Jetpack Compose & Material 3 UI**:
  - Declarative UI built with Jetpack Compose.
  - Dynamic Material 3 styling, adaptive light/dark themes, and smooth transition animations.
  - Interactive Now Playing player sheet, full-screen playback view, track action sheets, and custom UI components.

- 🎼 **Local & Online Music Catalog**:
  - Scans local media files and indices them using Room database.
  - Search and stream online music tracks seamlessly.
  - Playlist management (Favorites, Recent, Custom Playlists).

- 🎤 **Synchronized Lyrics**:
  - Real-time synced lyrics display parsing standard `.lrc` formats.

- 📥 **Offline Downloads & Cloud Sync**:
  - Download tracks for offline listening managed via background repositories.
  - Automated background data synchronization using `WorkManager`.

- 🌐 **Localization**:
  - Multi-language support (English and Hindi).

---

## 🛠️ Tech Stack & Architecture

- **Language**: [Kotlin](https://kotlinlang.org/)
- **UI**: [Jetpack Compose](https://developer.android.com/jetpack/compose) with [Material 3](https://developer.android.com/jetpack/compose/designsystems/material3)
- **Media Engine**: [Jetpack Media3](https://developer.android.com/guide/topics/media/media3) (`media3-exoplayer`, `media3-session`, `media3-datasource`)
- **Database & Storage**: [Room](https://developer.android.com/training/data-storage/room) (with KSP code generation) & [Preferences DataStore](https://developer.android.com/topic/libraries/architecture/datastore)
- **Background Work**: [WorkManager](https://developer.android.com/topic/libraries/architecture/workmanager)
- **Image Loading**: [Coil Compose](https://coil-kt.github.io/coil/compose/)
- **Networking**: [OkHttp](https://square.github.io/okhttp/)
- **Architecture**: MVVM with Kotlin Coroutines & `StateFlow`
- **Gradle**: Kotlin DSL (`build.gradle.kts`), KSP, Target SDK 35, Min SDK 24

---

## 📁 Project Structure

```text
com.ayush.aynamusic/
├── cloud/           # Background cloud sync manager & WorkManager tasks
├── data/            # Data layer (Local Room DB, Remote Music Catalog, Preferences, Repositories)
├── downloads/       # Download manager & receiver for offline audio
├── imaging/         # Artwork fetchers & local image utilities
├── playback/        # Media3 PlaybackService & Equalizer manager
├── ui/              # Compose UI Screens (Home, Player, Search, Library, Playlist, Settings)
├── util/            # Helper utilities (LRC Lyrics parser, formatting)
├── AynaApp.kt       # Main Composable App root
├── MainActivity.kt  # Single Activity entry point
└── MainViewModel.kt # Central StateFlow viewmodel managing application state
```

---

## 🚀 Getting Started

### Prerequisites

- **Android Studio**: Ladybug / 2024.2+ recommended
- **JDK**: Java 17
- **Android SDK**: Compile SDK 35, Min SDK 24

### Build & Run

1. **Clone the repository**:
   ```bash
   git clone https://github.com/your-username/AYNAMusic.git
   cd AYNAMusic
   ```

2. **Build Debug APK**:
   ```bash
   ./gradlew assembleDebug
   ```

3. **Run Unit Tests**:
   ```bash
   ./gradlew test
   ```

---

## 🔑 Release Build Configuration

For signing release builds, set the following environment variables or place your keystore at `keystore/aynamusic-release.jks`:

- `AYNA_STORE_PASSWORD`
- `AYNA_KEY_ALIAS`
- `AYNA_KEY_PASSWORD`

Build the release APK with:
```bash
./gradlew assembleRelease
```

---

## 📄 License

This project is open source and available under the [MIT License](LICENSE).
