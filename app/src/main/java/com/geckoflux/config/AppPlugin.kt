package com.geckoflux.config

/**
 * Modular plugins / capabilities that can be selectively enabled per application profile.
 */
enum class AppPlugin {
    /** Keeps audio/media playing when app is minimized or screen is locked */
    BACKGROUND_AUDIO,

    /** Swipe up for fullscreen, swipe down to exit, swipe brightness/volume & double-tap seek */
    VIDEO_GESTURES,

    /** Material 3 Precision Speed Controller bottom sheet and bridge */
    SPEED_CONTROLLER,

    /** Official uBlock Origin ad-blocker downloaded & updated from Mozilla AMO */
    AD_BLOCKER_UBLOCK,

    /** Removes YouTube Shorts shelves, reels, and bottom tabs */
    SHORTS_CLEANER,

    /** Enforces default video quality based on active network connection (Wi-Fi vs Cellular). */
    NETWORK_QUALITY_DEFAULT,

    /** Replaces standard web prompt dialogs with native Material 3 dialogs. */
    NATIVE_PROMPTS,

    /** Shares authentication & session cookies across sister apps in the same ecosystem (e.g. GeckoTube <-> GeckoMusic) without leaking to standalone apps. */
    SHARED_GOOGLE_SESSION
}
