package com.geckoflux.media

import android.content.Context
import android.util.Log
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.MediaSession

/**
 * Connects GeckoView's MediaSession.Delegate to Android's MediaPlaybackService.
 */
object GeckoMediaSessionManager {

    private const val TAG = "GeckoMediaSessionMgr"

    private var activeSession: GeckoSession? = null
    private var activeMediaSession: MediaSession? = null
    private var serviceCallback: Callback? = null

    var isPlaying: Boolean = false
        private set
    var currentTitle: String? = null
        private set
    var currentArtist: String? = null
        private set
    var currentAlbum: String? = null
        private set
    var currentFeatures: Long = 0L
        private set

    interface Callback {
        fun onPlaybackStateChanged(isPlaying: Boolean)
        fun onMetadataChanged(title: String?, artist: String?, album: String?)
        fun onFeaturesChanged(features: Long)
        fun onPlaybackStopped()
    }

    fun setServiceCallback(callback: Callback?) {
        this.serviceCallback = callback
        if (callback != null) {
            // Immediately sync state with freshly connected service
            if (isPlaying) {
                callback.onPlaybackStateChanged(true)
            }
            if (currentTitle != null || currentArtist != null) {
                callback.onMetadataChanged(currentTitle, currentArtist, currentAlbum)
            }
            if (currentFeatures != 0L) {
                callback.onFeaturesChanged(currentFeatures)
            }
        }
    }

    /**
     * Attaches the MediaSession.Delegate to a GeckoSession.
     */
    fun attachToSession(session: GeckoSession, context: Context) {
        activeSession = session

        session.mediaSessionDelegate = object : MediaSession.Delegate {
            override fun onActivated(session: GeckoSession, mediaSession: MediaSession) {
                Log.d(TAG, "MediaSession onActivated")
                activeMediaSession = mediaSession
                MediaPlaybackService.start(context)
            }

            override fun onDeactivated(session: GeckoSession, mediaSession: MediaSession) {
                Log.d(TAG, "MediaSession onDeactivated")
                if (activeMediaSession == mediaSession) {
                    activeMediaSession = null
                }
                isPlaying = false
                serviceCallback?.onPlaybackStopped()
                MediaPlaybackService.stop(context)
            }

            override fun onMetadata(
                session: GeckoSession,
                mediaSession: MediaSession,
                metadata: MediaSession.Metadata
            ) {
                Log.d(TAG, "MediaSession onMetadata: ${metadata.title} - ${metadata.artist}")
                currentTitle = metadata.title
                currentArtist = metadata.artist
                currentAlbum = metadata.album
                serviceCallback?.onMetadataChanged(metadata.title, metadata.artist, metadata.album)
            }

            override fun onFeatures(session: GeckoSession, mediaSession: MediaSession, features: Long) {
                Log.d(TAG, "MediaSession onFeatures: $features")
                currentFeatures = features
                serviceCallback?.onFeaturesChanged(features)
            }

            override fun onPlay(session: GeckoSession, mediaSession: MediaSession) {
                Log.d(TAG, "MediaSession onPlay")
                activeMediaSession = mediaSession
                isPlaying = true
                MediaPlaybackService.start(context)
                serviceCallback?.onPlaybackStateChanged(true)
            }

            override fun onPause(session: GeckoSession, mediaSession: MediaSession) {
                Log.d(TAG, "MediaSession onPause")
                isPlaying = false
                serviceCallback?.onPlaybackStateChanged(false)
            }

            override fun onStop(session: GeckoSession, mediaSession: MediaSession) {
                Log.d(TAG, "MediaSession onStop")
                if (activeMediaSession == mediaSession) {
                    activeMediaSession = null
                }
                isPlaying = false
                serviceCallback?.onPlaybackStopped()
                MediaPlaybackService.stop(context)
            }
        }
    }

    fun detachFromSession(session: GeckoSession) {
        if (activeSession == session) {
            session.mediaSessionDelegate = null
            activeSession = null
            activeMediaSession = null
        }
    }

    fun play() {
        activeMediaSession?.play()
    }

    fun pause() {
        activeMediaSession?.pause()
    }

    fun nextTrack() {
        activeMediaSession?.nextTrack()
    }

    fun previousTrack() {
        activeMediaSession?.previousTrack()
    }

    fun stop() {
        activeMediaSession?.stop()
    }
}
