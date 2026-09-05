package com.geckoflux.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GeckoMediaSessionManagerTest {

    private var lastIsPlaying: Boolean? = null
    private var lastTitle: String? = null
    private var lastArtist: String? = null
    private var lastAlbum: String? = null
    private var lastFeatures: Long? = null
    private var stoppedCalled = false

    private val callback = object : GeckoMediaSessionManager.Callback {
        override fun onPlaybackStateChanged(isPlaying: Boolean) {
            lastIsPlaying = isPlaying
        }

        override fun onMetadataChanged(title: String?, artist: String?, album: String?) {
            lastTitle = title
            lastArtist = artist
            lastAlbum = album
        }

        override fun onFeaturesChanged(features: Long) {
            lastFeatures = features
        }

        override fun onPlaybackStopped() {
            stoppedCalled = true
        }
    }

    @Before
    fun setUp() {
        lastIsPlaying = null
        lastTitle = null
        lastArtist = null
        lastAlbum = null
        lastFeatures = null
        stoppedCalled = false
        GeckoMediaSessionManager.setServiceCallback(callback)
    }

    @Test
    fun testServiceCallbackDispatch() {
        callback.onPlaybackStateChanged(true)
        assertTrue(lastIsPlaying == true)

        callback.onPlaybackStateChanged(false)
        assertFalse(lastIsPlaying == true)

        callback.onMetadataChanged("Test Song", "Test Artist", "Test Album")
        assertEquals("Test Song", lastTitle)
        assertEquals("Test Artist", lastArtist)
        assertEquals("Test Album", lastAlbum)

        callback.onFeaturesChanged(128L or 256L)
        assertEquals(384L, lastFeatures)

        callback.onPlaybackStopped()
        assertTrue(stoppedCalled)
    }

    @Test
    fun testNextPreviousFeatureBitmask() {
        val NEXT_TRACK = 128L
        val PREVIOUS_TRACK = 256L

        val featuresNone = 0L
        assertFalse((featuresNone and NEXT_TRACK) != 0L)
        assertFalse((featuresNone and PREVIOUS_TRACK) != 0L)

        val featuresNextOnly = NEXT_TRACK
        assertTrue((featuresNextOnly and NEXT_TRACK) != 0L)
        assertFalse((featuresNextOnly and PREVIOUS_TRACK) != 0L)

        val featuresBoth = NEXT_TRACK or PREVIOUS_TRACK
        assertTrue((featuresBoth and NEXT_TRACK) != 0L)
        assertTrue((featuresBoth and PREVIOUS_TRACK) != 0L)
    }
}
