package com.geckoflux.media

import android.content.Context
import android.util.Log
import com.geckoflux.navigation.AppType
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.MediaSession

/**
 * Connects GeckoView's MediaSession.Delegate to Android's MediaPlaybackService.
 */
object GeckoMediaSessionManager {

    private const val TAG = "GeckoMediaSessionMgr"

    private data class SessionState(
        val session: GeckoSession,
        val appType: AppType,
        var mediaSession: MediaSession? = null,
        var isPlaying: Boolean = false,
        var title: String? = null,
        var artist: String? = null,
        var album: String? = null,
        var features: Long = 0L
    )

    private val attachedSessions = mutableMapOf<GeckoSession, SessionState>()
    private var activeSession: GeckoSession? = null
    private var activeMediaSession: MediaSession? = null
    private var serviceCallback: Callback? = null

    var currentAppType: AppType = AppType.TUBE
        private set
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
     * Attaches the MediaSession.Delegate to a GeckoSession with its originating app type.
     */
    fun attachToSession(session: GeckoSession, context: Context, appType: AppType = AppType.TUBE) {
        val appContext = context.applicationContext
        val state = SessionState(session = session, appType = appType)
        attachedSessions[session] = state

        if (activeSession == null) {
            activeSession = session
            currentAppType = appType
        }

        session.mediaSessionDelegate = object : MediaSession.Delegate {
            override fun onActivated(session: GeckoSession, mediaSession: MediaSession) {
                Log.d(TAG, "MediaSession onActivated for $appType")
                state.mediaSession = mediaSession
                makeActive(state)
                MediaPlaybackService.start(appContext)
            }

            override fun onDeactivated(session: GeckoSession, mediaSession: MediaSession) {
                Log.d(TAG, "MediaSession onDeactivated for $appType")
                state.isPlaying = false
                if (activeSession == session) {
                    isPlaying = false
                    serviceCallback?.onPlaybackStopped()
                }
            }

            override fun onMetadata(
                session: GeckoSession,
                mediaSession: MediaSession,
                metadata: MediaSession.Metadata
            ) {
                Log.d(TAG, "MediaSession onMetadata: ${metadata.title} - ${metadata.artist}")
                state.mediaSession = mediaSession
                state.title = metadata.title
                state.artist = metadata.artist
                state.album = metadata.album
                if (activeSession == session || activeSession == null) {
                    makeActive(state)
                    serviceCallback?.onMetadataChanged(metadata.title, metadata.artist, metadata.album)
                }
            }

            override fun onFeatures(session: GeckoSession, mediaSession: MediaSession, features: Long) {
                Log.d(TAG, "MediaSession onFeatures: $features")
                state.mediaSession = mediaSession
                state.features = features
                if (activeSession == session) {
                    currentFeatures = features
                    serviceCallback?.onFeaturesChanged(features)
                }
            }

            override fun onPlay(session: GeckoSession, mediaSession: MediaSession) {
                Log.d(TAG, "MediaSession onPlay for $appType")
                state.mediaSession = mediaSession
                state.isPlaying = true
                makeActive(state)
                MediaPlaybackService.start(appContext)
                serviceCallback?.onPlaybackStateChanged(true)
            }

            override fun onPause(session: GeckoSession, mediaSession: MediaSession) {
                Log.d(TAG, "MediaSession onPause for $appType")
                state.mediaSession = mediaSession
                state.isPlaying = false
                if (activeSession == session) {
                    isPlaying = false
                    serviceCallback?.onPlaybackStateChanged(false)
                }
            }

            override fun onStop(session: GeckoSession, mediaSession: MediaSession) {
                Log.d(TAG, "MediaSession onStop for $appType")
                state.isPlaying = false
                if (activeSession == session) {
                    isPlaying = false
                    serviceCallback?.onPlaybackStopped()
                }
            }
        }
    }

    private fun makeActive(state: SessionState) {
        activeSession = state.session
        activeMediaSession = state.mediaSession
        currentAppType = state.appType
        isPlaying = state.isPlaying
        currentTitle = state.title
        currentArtist = state.artist
        currentAlbum = state.album
        currentFeatures = state.features
    }

    fun detachFromSession(session: GeckoSession) {
        session.mediaSessionDelegate = null
        attachedSessions.remove(session)

        if (activeSession == session) {
            // Restore another playing session, or any remaining attached session
            val nextActive = attachedSessions.values.firstOrNull { it.isPlaying }
                ?: attachedSessions.values.firstOrNull()

            if (nextActive != null) {
                makeActive(nextActive)
                serviceCallback?.onPlaybackStateChanged(isPlaying)
                serviceCallback?.onMetadataChanged(currentTitle, currentArtist, currentAlbum)
                serviceCallback?.onFeaturesChanged(currentFeatures)
            } else {
                activeSession = null
                activeMediaSession = null
                isPlaying = false
                currentTitle = null
                currentArtist = null
                currentAlbum = null
                currentFeatures = 0L
                serviceCallback?.onPlaybackStopped()
            }
        }
    }

    fun play() {
        Log.d(TAG, "play() invoked on activeMediaSession: $activeMediaSession")
        activeMediaSession?.play()
    }

    fun pause() {
        Log.d(TAG, "pause() invoked on activeMediaSession: $activeMediaSession")
        activeMediaSession?.pause()
    }

    fun nextTrack() {
        Log.d(TAG, "nextTrack() invoked on activeMediaSession: $activeMediaSession")
        activeMediaSession?.nextTrack()
    }

    fun previousTrack() {
        Log.d(TAG, "previousTrack() invoked on activeMediaSession: $activeMediaSession")
        activeMediaSession?.previousTrack()
    }

    fun stop() {
        Log.d(TAG, "stop() invoked on activeMediaSession: $activeMediaSession")
        activeMediaSession?.stop()
    }
}
