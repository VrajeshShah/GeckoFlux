# GeckoFlux (GeckoTube & GeckoMusic)

A modern, config-driven Android media framework powered by **Mozilla GeckoView** (Firefox engine).

---

## Brand Architecture

* **Framework Engine**: **GeckoFlux** (`com.geckoflux`)
* **Video Client**: **GeckoTube** (`com.geckoflux.tube`)
* **Music Client**: **GeckoMusic** (`com.geckoflux.music`)

---

## Key Features

- 🦊 **Powered by GeckoView**: Genuine Firefox Gecko engine with standard WebExtension APIs and full Google/YouTube authentication & 2FA support.
- 🎵 **Persistent Background Play**: Audio playback continues uninterrupted when minimizing the app or locking the screen (via Page Visibility spoofing).
- 🛡️ **uBlock Origin from Mozilla Store (AMO)**: Downloads and automatically updates the official uBlock Origin extension from Mozilla Add-on servers.
- ⚡ **Material 3 Speed Controller**:
  - Precision slider (`0.25x` – `4.00x` in `0.05x` increments)
  - Quick-preset chips (`0.25x`, `0.5x`, `0.75x`, `1.0x`, `1.25x`, `1.5x`, `1.75x`, `2.0x`, `2.5x`, `3.0x`, `4.0x`)
  - Audio pitch preservation (`preservesPitch = true`)
  - Haptic feedback on speed selection
  - Speed memory preserved across video changes
- 🧭 **Intelligent Link Routing**:
  - YouTube, YouTube Music, and Google Auth links open internally.
  - External links (descriptions, comments, etc.) open in the user's default system browser.
- 📺 **Native App UX & Gestures**:
  - **Swipe Left Side (Fullscreen)** $\to$ Adjust **Screen Brightness** with smooth on-screen HUD.
  - **Swipe Right Side (Fullscreen)** $\to$ Adjust **Media Volume** with smooth on-screen HUD.
  - **Network-Aware Quality Default** $\to$ Defaults to **720p on Wi-Fi** and **480p on Mobile Data** (freely changeable anytime via the player menu).
  - **Native Dark Mode** $\to$ Automatic dark theme integration with GeckoView engine.
  - **Shorts Cleaner / Focus Mode** $\to$ Hides distracting Shorts shelves, tabs, and reels.
  - **Swipe UP on video player** $\to$ Instantly enters landscape fullscreen.
  - **Swipe DOWN on fullscreen video** $\to$ Exits fullscreen back to portrait mode.
  - **Double-Tap Left / Right ($\pm 10\text{s}$)** $\to$ Rewind or fast-forward with visual toast feedback.
  - **Auto-Dismiss Inactivity Dialogs** $\to$ Automatically clicks past "Are you still watching?" prompts for continuous playback.
  - **Material 3 Dialogs (`PromptDelegate`)** $\to$ Native Android dialogs for web authentication & alerts.
  - **Viewport Lock** $\to$ Prevents accidental pinch-to-zoom and layout stretching.
  - Automatic landscape rotation on video fullscreen with immersive sticky system bar hiding.
  - Picture-in-Picture (PiP) support when pressing Home.
  - Clean styling with mobile "Open in App" banner suppression.
  - Double-tap back gesture to exit app.

---

## 🧩 Plugin-Based Architecture

Every feature in **GeckoFlux** is an isolated **`AppPlugin`**. Applications declare only the specific plugins they need, guaranteeing **zero cross-contamination** between different apps:

```kotlin
enum class AppPlugin {
    BACKGROUND_AUDIO,        // Keeps audio alive on screen lock / minimize
    VIDEO_GESTURES,          // Swipe up/down fullscreen, seek, brightness/volume
    SPEED_CONTROLLER,        // Material 3 precision speed bottom sheet
    AD_BLOCKER_UBLOCK,       // uBlock Origin from Mozilla AMO
    SHORTS_CLEANER,          // Removes distracting YouTube Shorts
    NETWORK_QUALITY_DEFAULT, // 720p on Wi-Fi, 480p on Mobile Data
    NATIVE_PROMPTS,          // Native Material 3 dialogs
    SHARED_GOOGLE_SESSION    // Shared session for Google apps
}
```

---

## Example: Adding Instagram (`GeckoGram`) in 1 Minute

Because of the plugin system, adding Instagram takes only declaring what it needs:

```kotlin
val INSTAGRAM = AppConfiguration(
    appName = "GeckoGram",
    targetUrl = "https://www.instagram.com",
    allowedDomains = listOf("instagram.com", "facebook.com", "cdninstagram.com"),
    plugins = setOf(
        AppPlugin.AD_BLOCKER_UBLOCK,
        AppPlugin.NATIVE_PROMPTS
        // Intentionally NO YouTube gestures, NO Shorts cleaners, NO Speed sheets!
    )
)
```

YouTube-specific scripts, gestures, or selectors will **never run on Instagram**.

---

## Build Variants / Flavors

The project includes two product flavors:

| Flavor | App Name | Target URL | Active Plugins |
| :--- | :--- | :--- | :--- |
| **`youtube`** | **GeckoTube** | `https://m.youtube.com` | `BACKGROUND_AUDIO`, `VIDEO_GESTURES`, `SPEED_CONTROLLER`, `AD_BLOCKER_UBLOCK`, `SHORTS_CLEANER`, `NETWORK_QUALITY_DEFAULT`, `NATIVE_PROMPTS`, `SHARED_GOOGLE_SESSION` |
| **`music`** | **GeckoMusic** | `https://music.youtube.com` | `BACKGROUND_AUDIO`, `AD_BLOCKER_UBLOCK`, `NATIVE_PROMPTS`, `SHARED_GOOGLE_SESSION` *(No video gestures, no speed sheet)* |

---

## How to Build and Run

### 1. Build GeckoTube (`GeckoTube.apk`)
```bash
./gradlew assembleYoutubeDebug
```
Output: `app/build/outputs/apk/youtube/debug/app-youtube-debug.apk`

### 2. Build GeckoMusic (`GeckoMusic.apk`)
```bash
./gradlew assembleMusicDebug
```
Output: `app/build/outputs/apk/music/debug/app-music-debug.apk`

### 3. Open in Android Studio
1. Open Android Studio $\to$ Open `GeckoFlux` project folder.
2. Select the **Build Variant** tab in the bottom-left corner of Android Studio.
3. Choose `youtubeDebug` or `musicDebug` and click **Run** (▶).

---

## Adding a New Web App Profile

To add any future web app (e.g. SoundCloud, Twitch):
1. Open `app/src/main/java/com/geckoflux/config/AppProfiles.kt` and declare your profile:
   ```kotlin
   val SOUNDCLOUD = AppConfiguration(
       appName = "FluxSoundCloud",
       targetUrl = "https://soundcloud.com",
       allowedDomains = listOf("soundcloud.com"),
       plugins = setOf(
           AppPlugin.BACKGROUND_AUDIO,
           AppPlugin.AD_BLOCKER_UBLOCK,
           AppPlugin.NATIVE_PROMPTS
       ),
       primaryColorHex = "#FF5500"
   )
   ```
2. Add a new flavor in `app/build.gradle.kts`:
   ```kotlin
   create("soundcloud") {
       dimension = "app"
       applicationId = "com.geckoflux.soundcloud"
       manifestPlaceholders["appName"] = "FluxSoundCloud"
       buildConfigField("String", "APP_PROFILE", "\"SOUNDCLOUD\"")
   }
   ```
