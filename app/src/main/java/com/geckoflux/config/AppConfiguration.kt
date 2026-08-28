package com.geckoflux.config

/**
 * Declarative configuration model for any GeckoView-powered web application.
 *
 * Fully modular and plugin-driven: every feature is an isolated plugin that can be
 * enabled or disabled per app without any cross-contamination between apps.
 */
data class AppConfiguration(
    val appName: String,
    val targetUrl: String,
    val allowedDomains: List<String>,
    val plugins: Set<AppPlugin> = setOf(
        AppPlugin.BACKGROUND_AUDIO,
        AppPlugin.AD_BLOCKER_UBLOCK,
        AppPlugin.NATIVE_PROMPTS
    ),
    val wifiDefaultQuality: String = "hd720",    // 720p on Wi-Fi
    val cellularDefaultQuality: String = "large", // 480p on Mobile Data
    val uBlockAmoUrl: String = "https://addons.mozilla.org/firefox/downloads/latest/ublock-origin/addon-latest.xpi",
    val customUserAgent: String? = null,
    val customCss: String? = null,
    val enableFullscreenAutoRotate: Boolean = true,
    val enablePiP: Boolean = true,
    val primaryColorHex: String = "#FF0000",
    val sessionGroupId: String? = null
) {
    fun hasPlugin(plugin: AppPlugin): Boolean = plugins.contains(plugin)
    fun isAllowed(url: String): Boolean = allowedDomains.any { url.contains(it, ignoreCase = true) }
}
