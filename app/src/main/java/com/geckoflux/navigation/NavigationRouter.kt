package com.geckoflux.navigation

import java.net.URI

/**
 * Defines which app context is currently evaluating the URL.
 */
enum class AppType {
    TUBE,
    MUSIC
}

/**
 * Action to be taken for a requested navigation URI.
 */
enum class RouteAction {
    /**
     * Load the requested URI in the current GeckoSession.
     */
    LOAD_IN_SESSION,

    /**
     * Hand off navigation to the GeckoTube activity.
     */
    ROUTE_TO_TUBE,

    /**
     * Hand off navigation to the GeckoMusic activity.
     */
    ROUTE_TO_MUSIC,

    /**
     * Hand off navigation to the system default web browser.
     */
    OPEN_EXTERNAL_BROWSER
}

/**
 * Pure, testable URL router enforcing strict domain isolation and cross-app routing.
 */
object NavigationRouter {

    private val GOOGLE_AUTH_HOSTS = setOf(
        "accounts.google.com",
        "accounts.youtube.com",
        "myaccount.google.com",
        "consent.youtube.com",
        "consent.google.com",
        "oauth2.googleapis.com",
        "apis.google.com"
    )

    private val YOUTUBE_HOSTS = setOf(
        "youtube.com",
        "www.youtube.com",
        "m.youtube.com",
        "youtu.be"
    )

    private const val MUSIC_HOST = "music.youtube.com"

    /**
     * Resolves the navigation action for a given URI string in the context of an app.
     */
    fun resolve(currentApp: AppType, uriString: String?): RouteAction {
        if (uriString.isNullOrBlank()) {
            return RouteAction.LOAD_IN_SESSION
        }

        val trimmed = uriString.trim()

        // Non-http(s) browser schemes stay in session (e.g., about:blank, javascript:)
        val scheme = extractScheme(trimmed)
        if (scheme == "about" || scheme == "javascript" || scheme == "data") {
            return RouteAction.LOAD_IN_SESSION
        }

        // Custom android schemes (intent:, market:, mailto:, tel:) go to external handler
        if (scheme != "http" && scheme != "https") {
            return RouteAction.OPEN_EXTERNAL_BROWSER
        }

        val host = extractHost(trimmed) ?: return RouteAction.OPEN_EXTERNAL_BROWSER

        // 1. Google Authentication & Consent flows (Allowed in both apps)
        if (isGoogleAuthHost(host)) {
            return RouteAction.LOAD_IN_SESSION
        }

        // 2. YouTube Music domain
        if (host == MUSIC_HOST) {
            return if (currentApp == AppType.MUSIC) {
                RouteAction.LOAD_IN_SESSION
            } else {
                RouteAction.ROUTE_TO_MUSIC
            }
        }

        // 3. Standard YouTube domains (including m.youtube.com, youtu.be, etc.)
        if (isYouTubeHost(host)) {
            return if (currentApp == AppType.TUBE) {
                RouteAction.LOAD_IN_SESSION
            } else {
                RouteAction.ROUTE_TO_TUBE
            }
        }

        // 4. Any external domain (social media, patreon, external web links)
        return RouteAction.OPEN_EXTERNAL_BROWSER
    }

    private fun extractScheme(url: String): String {
        val colonIdx = url.indexOf(':')
        if (colonIdx > 0) {
            val candidate = url.substring(0, colonIdx).lowercase()
            if (candidate.all { it.isLetterOrDigit() || it == '+' || it == '-' || it == '.' }) {
                return candidate
            }
        }
        return ""
    }

    private fun extractHost(url: String): String? {
        return try {
            val uri = URI(url)
            uri.host?.lowercase()
        } catch (e: Exception) {
            // Fallback manual host extractor
            val withoutScheme = url.substringAfter("://", "")
            if (withoutScheme.isEmpty()) return null
            val authority = withoutScheme.substringBefore('/').substringBefore('?').substringBefore('#')
            authority.substringBefore(':').lowercase().ifEmpty { null }
        }
    }

    private fun isGoogleAuthHost(host: String): Boolean {
        if (host in GOOGLE_AUTH_HOSTS) return true
        return host.endsWith(".google.com") && (host.startsWith("accounts.") || host.startsWith("consent."))
    }

    private fun isYouTubeHost(host: String): Boolean {
        if (host in YOUTUBE_HOSTS) return true
        return host.endsWith(".youtube.com") && host != MUSIC_HOST
    }
}
