package com.geckoflux.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationRouterTest {

    @Test
    fun testGeckoTubeAllowsYouTubeLinks() {
        assertEquals(
            RouteAction.LOAD_IN_SESSION,
            NavigationRouter.resolve(AppType.TUBE, "https://m.youtube.com")
        )
        assertEquals(
            RouteAction.LOAD_IN_SESSION,
            NavigationRouter.resolve(AppType.TUBE, "https://m.youtube.com/watch?v=dQw4w9WgXcQ")
        )
        assertEquals(
            RouteAction.LOAD_IN_SESSION,
            NavigationRouter.resolve(AppType.TUBE, "https://www.youtube.com/feed/subscriptions")
        )
        assertEquals(
            RouteAction.LOAD_IN_SESSION,
            NavigationRouter.resolve(AppType.TUBE, "https://youtu.be/dQw4w9WgXcQ")
        )
    }

    @Test
    fun testGeckoTubeRoutesMusicLinksToGeckoMusic() {
        assertEquals(
            RouteAction.ROUTE_TO_MUSIC,
            NavigationRouter.resolve(AppType.TUBE, "https://music.youtube.com")
        )
        assertEquals(
            RouteAction.ROUTE_TO_MUSIC,
            NavigationRouter.resolve(AppType.TUBE, "https://music.youtube.com/watch?v=12345")
        )
        assertEquals(
            RouteAction.ROUTE_TO_MUSIC,
            NavigationRouter.resolve(AppType.TUBE, "https://music.youtube.com/playlist?list=PLxyz")
        )
    }

    @Test
    fun testGeckoMusicAllowsMusicLinks() {
        assertEquals(
            RouteAction.LOAD_IN_SESSION,
            NavigationRouter.resolve(AppType.MUSIC, "https://music.youtube.com")
        )
        assertEquals(
            RouteAction.LOAD_IN_SESSION,
            NavigationRouter.resolve(AppType.MUSIC, "https://music.youtube.com/explore")
        )
    }

    @Test
    fun testGeckoMusicRoutesRegularYouTubeToGeckoTube() {
        assertEquals(
            RouteAction.ROUTE_TO_TUBE,
            NavigationRouter.resolve(AppType.MUSIC, "https://m.youtube.com")
        )
        assertEquals(
            RouteAction.ROUTE_TO_TUBE,
            NavigationRouter.resolve(AppType.MUSIC, "https://www.youtube.com/watch?v=dQw4w9WgXcQ")
        )
        assertEquals(
            RouteAction.ROUTE_TO_TUBE,
            NavigationRouter.resolve(AppType.MUSIC, "https://youtu.be/dQw4w9WgXcQ")
        )
    }

    @Test
    fun testGoogleAuthAllowedInBothApps() {
        val authUrls = listOf(
            "https://accounts.google.com/signin/v2/identifier",
            "https://accounts.youtube.com/accounts/SetSID",
            "https://consent.youtube.com/m?continue=https://m.youtube.com",
            "https://consent.google.com",
            "https://oauth2.googleapis.com/token"
        )

        for (url in authUrls) {
            assertEquals(
                "Tube should allow auth URL: $url",
                RouteAction.LOAD_IN_SESSION,
                NavigationRouter.resolve(AppType.TUBE, url)
            )
            assertEquals(
                "Music should allow auth URL: $url",
                RouteAction.LOAD_IN_SESSION,
                NavigationRouter.resolve(AppType.MUSIC, url)
            )
        }
    }

    @Test
    fun testExternalLinksOpenInBrowser() {
        val externalUrls = listOf(
            "https://twitter.com/mkbhd",
            "https://x.com/mkbhd",
            "https://patreon.com/creator",
            "https://instagram.com/profile",
            "https://en.wikipedia.org/wiki/Kotlin",
            "https://github.com/mozilla/geckoview"
        )

        for (url in externalUrls) {
            assertEquals(
                "Tube should open external browser for: $url",
                RouteAction.OPEN_EXTERNAL_BROWSER,
                NavigationRouter.resolve(AppType.TUBE, url)
            )
            assertEquals(
                "Music should open external browser for: $url",
                RouteAction.OPEN_EXTERNAL_BROWSER,
                NavigationRouter.resolve(AppType.MUSIC, url)
            )
        }
    }

    @Test
    fun testCustomSchemesOpenExternal() {
        assertEquals(
            RouteAction.OPEN_EXTERNAL_BROWSER,
            NavigationRouter.resolve(AppType.TUBE, "intent://scan/#Intent;scheme=zxing;package=com.google.zxing.client.android;end")
        )
        assertEquals(
            RouteAction.OPEN_EXTERNAL_BROWSER,
            NavigationRouter.resolve(AppType.MUSIC, "mailto:support@example.com")
        )
    }

    @Test
    fun testInternalBrowserSchemesStayInSession() {
        assertEquals(
            RouteAction.LOAD_IN_SESSION,
            NavigationRouter.resolve(AppType.TUBE, "about:blank")
        )
        assertEquals(
            RouteAction.LOAD_IN_SESSION,
            NavigationRouter.resolve(AppType.MUSIC, "about:neterror")
        )
    }

    @Test
    fun testUnsafeSchemesOpenExternal() {
        assertEquals(
            RouteAction.OPEN_EXTERNAL_BROWSER,
            NavigationRouter.resolve(AppType.MUSIC, "javascript:void(0);")
        )
        assertEquals(
            RouteAction.OPEN_EXTERNAL_BROWSER,
            NavigationRouter.resolve(AppType.TUBE, "data:text/html,<h1>Phishing</h1>")
        )
    }

    @Test
    fun testHostSpoofingWithCredentialsPrevented() {
        val spoofedUrl = "https://youtube.com:password@phishing-attack.com/login"
        assertEquals(
            RouteAction.OPEN_EXTERNAL_BROWSER,
            NavigationRouter.resolve(AppType.TUBE, spoofedUrl)
        )
        assertEquals(
            RouteAction.OPEN_EXTERNAL_BROWSER,
            NavigationRouter.resolve(AppType.MUSIC, spoofedUrl)
        )
    }

    @Test
    fun testNullOrEmptyUrlStaysInSession() {
        assertEquals(
            RouteAction.LOAD_IN_SESSION,
            NavigationRouter.resolve(AppType.TUBE, null)
        )
        assertEquals(
            RouteAction.LOAD_IN_SESSION,
            NavigationRouter.resolve(AppType.TUBE, "")
        )
    }
}
