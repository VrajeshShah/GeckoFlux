# GeckoFlux (GeckoTube & GeckoMusic)

A clean, bare-metal Android web client powered by **Mozilla GeckoView** (Firefox engine).

---

## Applications

* **GeckoTube** (`com.geckoflux.tube`): Native GeckoView client targeting `https://m.youtube.com`.
* **GeckoMusic** (`com.geckoflux.music`): Native GeckoView client targeting `https://music.youtube.com`.

---

## Core Architecture (Phase 1 Baseline)

- 🦊 **Mozilla GeckoView**: Genuine Firefox Gecko engine embedding.
- 📱 **Pure Minimal Host**: Lightweight `MainActivity` with attached `GeckoSession`.
- 🌙 **Dark Scheme Integration**: Automatically requests dark theme via `preferredColorScheme`.
- 🧭 **Native Back Navigation**: Integrates with Android's `OnBackPressedDispatcher` to support web back navigation.
- ⚡ **Zero Bloat**: No unneeded third-party wrappers, extra background scripts, or legacy UI widgets.

---

## Build Variants & Commands

### 1. Build GeckoFlux Suite (YouTube + Music Shared)
```bash
./gradlew assembleSuiteDebug
```
Output: `app/build/outputs/apk/suite/debug/app-suite-debug.apk`

### 2. Build GeckoTube (Standalone)
```bash
./gradlew assembleYoutubeDebug
```
Output: `app/build/outputs/apk/youtube/debug/app-youtube-debug.apk`

### 3. Build GeckoMusic (Standalone)
```bash
./gradlew assembleMusicDebug
```
Output: `app/build/outputs/apk/music/debug/app-music-debug.apk`

### 4. Run Lint Checks
```bash
./gradlew lint
```

---

## Project Structure

```text
GeckoFlux/
├── app/
│   ├── src/main/
│   │   ├── java/com/geckoflux/
│   │   │   └── MainActivity.kt          # Minimal GeckoView host activity
│   │   ├── res/
│   │   │   └── layout/
│   │   │       └── activity_main.xml     # GeckoView container layout
│   │   └── AndroidManifest.xml          # Clean application manifest
│   ├── proguard-rules.pro               # Proguard keep rules
│   └── build.gradle.kts                 # Minimal Gradle configuration
├── build.gradle.kts
├── gradle.properties
└── settings.gradle.kts
```