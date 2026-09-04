package com.geckoflux.extensions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL

class UblockManagerTest {

    @Test
    fun testUblockExtensionIdConstant() {
        assertEquals("uBlock0@raymondhill.net", UblockManager.EXTENSION_ID)
    }

    @Test
    fun testMozillaAmoEndpointResolves() {
        val amoUrl = "https://addons.mozilla.org/firefox/downloads/latest/ublock-origin/latest.xpi"
        val connection = URL(amoUrl).openConnection() as HttpURLConnection
        connection.requestMethod = "HEAD"
        connection.instanceFollowRedirects = false
        connection.connectTimeout = 10000
        connection.readTimeout = 10000
        connection.setRequestProperty(
            "User-Agent",
            "Mozilla/5.0 (Android; Mobile; rv:154.0) Gecko/154.0 Firefox/154.0"
        )
        val responseCode = connection.responseCode
        connection.disconnect()

        // AMO endpoint should return either 302 Found (redirecting to CDN) or 200 OK
        assertTrue(
            "Expected redirect (302/301) or OK (200), got: $responseCode",
            responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
                    responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
                    responseCode == HttpURLConnection.HTTP_OK
        )
    }
}
