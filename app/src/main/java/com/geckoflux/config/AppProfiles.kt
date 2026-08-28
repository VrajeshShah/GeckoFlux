package com.geckoflux.config

/**
 * Predefined application profiles for different Web Apps.
 * Each profile explicitly declares only the plugins/capabilities it requires.
 */
object AppProfiles {

    /**
     * YouTube App Profile (GeckoTube)
     */
    val YOUTUBE = AppConfiguration(
        appName = "GeckoTube",
        targetUrl = "https://m.youtube.com",
        allowedDomains = listOf(
            "youtube.com",
            "m.youtube.com",
            "youtu.be",
            "accounts.google.com",
            "myaccount.google.com",
            "google.com",
            "gstatic.com",
            "googleusercontent.com"
        ),
        plugins = setOf(
            AppPlugin.BACKGROUND_AUDIO,
            AppPlugin.VIDEO_GESTURES,
            AppPlugin.SPEED_CONTROLLER,
            AppPlugin.AD_BLOCKER_UBLOCK,
            AppPlugin.SHORTS_CLEANER,
            AppPlugin.NETWORK_QUALITY_DEFAULT,
            AppPlugin.NATIVE_PROMPTS,
            AppPlugin.SHARED_GOOGLE_SESSION
        ),
        wifiDefaultQuality = "hd720",
        cellularDefaultQuality = "large",
        enableFullscreenAutoRotate = true,
        enablePiP = true,
        primaryColorHex = "#FF0000",
        sessionGroupId = "google_shared"
    )

    /**
     * YouTube Music App Profile (GeckoMusic)
     */
    val YOUTUBE_MUSIC = AppConfiguration(
        appName = "GeckoMusic",
        targetUrl = "https://music.youtube.com",
        allowedDomains = listOf(
            "music.youtube.com",
            "youtube.com",
            "accounts.google.com",
            "myaccount.google.com",
            "google.com",
            "gstatic.com",
            "googleusercontent.com"
        ),
        plugins = setOf(
            AppPlugin.BACKGROUND_AUDIO,
            AppPlugin.AD_BLOCKER_UBLOCK,
            AppPlugin.NATIVE_PROMPTS,
            AppPlugin.SHARED_GOOGLE_SESSION
            // Intentionally NO Video Gestures, NO Shorts Cleaner, NO Speed Sheet
        ),
        enableFullscreenAutoRotate = false,
        enablePiP = false,
        primaryColorHex = "#FF0000",
        sessionGroupId = "google_shared"
    )

    /**
     * Example: Instagram Profile (GeckoGram)
     * Demonstrates complete isolation: only AdBlocker & Prompts active.
     */
    val INSTAGRAM = AppConfiguration(
        appName = "GeckoGram",
        targetUrl = "https://www.instagram.com",
        allowedDomains = listOf(
            "instagram.com",
            "facebook.com",
            "cdninstagram.com",
            "fbcdn.net"
        ),
        plugins = setOf(
            AppPlugin.AD_BLOCKER_UBLOCK,
            AppPlugin.NATIVE_PROMPTS
            // Zero YouTube code, Zero Google shared auth session, Zero gesture cross-contamination
        ),
        enableFullscreenAutoRotate = false,
        enablePiP = false,
        primaryColorHex = "#E1306C",
        sessionGroupId = null // 100% isolated private profile
    )

    /**
     * Resolves the active configuration profile based on flavor or key.
     */
    fun fromFlavor(flavor: String): AppConfiguration {
        return when (flavor.uppercase()) {
            "MUSIC", "YOUTUBE_MUSIC" -> YOUTUBE_MUSIC
            "INSTAGRAM" -> INSTAGRAM
            else -> YOUTUBE
        }
    }
}
