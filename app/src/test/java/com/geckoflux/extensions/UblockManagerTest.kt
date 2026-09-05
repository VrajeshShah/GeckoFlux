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
    fun testMozillaAmoEndpointConfiguration() {
        val amoUrl = "https://addons.mozilla.org/firefox/downloads/latest/ublock-origin/latest.xpi"
        val url = URL(amoUrl)
        assertEquals("https", url.protocol)
        assertEquals("addons.mozilla.org", url.host)
        assertTrue("URL path should point to latest ublock-origin .xpi", url.path.endsWith("/ublock-origin/latest.xpi"))
    }
}
