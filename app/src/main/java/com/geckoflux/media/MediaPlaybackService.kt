package com.geckoflux.media

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaMetadata
import android.media.session.MediaSession as AndroidMediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.geckoflux.R
import org.mozilla.geckoview.MediaSession as GeckoMediaSession

/**
 * Foreground Service establishing GeckoFlux as an ongoing user-visible media task.
 * Acts as the source of truth for playback state and dynamically manages the WakeLock.
 */
class MediaPlaybackService : Service(), GeckoMediaSessionManager.Callback {

    companion object {
        private const val TAG = "MediaPlaybackService"
        const val NOTIFICATION_ID = 2001
        const val CHANNEL_ID = "geckoflux_media_channel"

        const val ACTION_START = "com.geckoflux.media.START"
        const val ACTION_STOP = "com.geckoflux.media.STOP"
        const val ACTION_PLAY = "com.geckoflux.media.PLAY"
        const val ACTION_PAUSE = "com.geckoflux.media.PAUSE"
        const val ACTION_NEXT = "com.geckoflux.media.NEXT"
        const val ACTION_PREV = "com.geckoflux.media.PREV"

        fun start(context: Context) {
            val intent = Intent(context, MediaPlaybackService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, MediaPlaybackService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private var androidMediaSession: AndroidMediaSession? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var notificationManager: NotificationManager? = null

    private var isPlaying: Boolean = false
    private var currentTitle: String = "Playing Media"
    private var currentArtist: String = "GeckoFlux"
    private var currentFeatures: Long = 0L

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "MediaPlaybackService onCreate")
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
        setupAndroidMediaSession()
        GeckoMediaSessionManager.setServiceCallback(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startForegroundWithNotification()
            }
            ACTION_STOP -> {
                teardownAndStop()
            }
            ACTION_PLAY -> {
                GeckoMediaSessionManager.play()
            }
            ACTION_PAUSE -> {
                GeckoMediaSessionManager.pause()
            }
            ACTION_NEXT -> {
                GeckoMediaSessionManager.nextTrack()
            }
            ACTION_PREV -> {
                GeckoMediaSessionManager.previousTrack()
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        GeckoMediaSessionManager.setServiceCallback(null)
        teardownAndStop()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Media Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background media playback and controls"
                setShowBadge(false)
            }
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun setupAndroidMediaSession() {
        androidMediaSession = AndroidMediaSession(this, "GeckoFluxMediaSession").apply {
            setCallback(object : AndroidMediaSession.Callback() {
                override fun onPlay() {
                    GeckoMediaSessionManager.play()
                }

                override fun onPause() {
                    GeckoMediaSessionManager.pause()
                }

                override fun onSkipToNext() {
                    GeckoMediaSessionManager.nextTrack()
                }

                override fun onSkipToPrevious() {
                    GeckoMediaSessionManager.previousTrack()
                }

                override fun onStop() {
                    GeckoMediaSessionManager.stop()
                    teardownAndStop()
                }
            })
            isActive = true
        }
    }

    private fun startForegroundWithNotification() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    // --- Dynamic WakeLock Management (Source of Truth) ---

    @Synchronized
    private fun updateWakeLock(shouldHold: Boolean) {
        if (shouldHold) {
            if (wakeLock == null) {
                val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "GeckoFlux:MediaPlaybackWakeLock"
                ).apply {
                    setReferenceCounted(false)
                }
            }
            if (wakeLock?.isHeld != true) {
                Log.d(TAG, "Acquiring dynamic PARTIAL_WAKE_LOCK")
                wakeLock?.acquire(30 * 60 * 1000L) // 30 min safety timeout
            }
        } else {
            if (wakeLock?.isHeld == true) {
                Log.d(TAG, "Releasing dynamic PARTIAL_WAKE_LOCK")
                wakeLock?.release()
            }
        }
    }

    // --- GeckoMediaSessionManager.Callback Implementation ---

    override fun onPlaybackStateChanged(playing: Boolean) {
        if (this.isPlaying == playing) return // Ignore redundant callbacks
        this.isPlaying = playing
        Log.d(TAG, "onPlaybackStateChanged: isPlaying=$isPlaying")

        updateWakeLock(isPlaying)
        updateAndroidPlaybackState()
        updateNotification()
    }

    override fun onMetadataChanged(title: String?, artist: String?, album: String?) {
        this.currentTitle = title?.ifBlank { "Playing Media" } ?: "Playing Media"
        this.currentArtist = artist?.ifBlank { "GeckoFlux" } ?: "GeckoFlux"

        val metadataBuilder = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, currentTitle)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, currentArtist)
        album?.let { metadataBuilder.putString(MediaMetadata.METADATA_KEY_ALBUM, it) }

        androidMediaSession?.setMetadata(metadataBuilder.build())
        updateNotification()
    }

    override fun onFeaturesChanged(features: Long) {
        if (this.currentFeatures == features) return
        this.currentFeatures = features
        Log.d(TAG, "onFeaturesChanged: features=$features. Rebuilding notification actions.")

        updateAndroidPlaybackState()
        updateNotification()
    }

    override fun onPlaybackStopped() {
        Log.d(TAG, "onPlaybackStopped received. Tearing down service.")
        teardownAndStop()
    }

    private fun updateAndroidPlaybackState() {
        var actions = PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_STOP
        if ((currentFeatures and GeckoMediaSession.Feature.NEXT_TRACK) != 0L) {
            actions = actions or PlaybackState.ACTION_SKIP_TO_NEXT
        }
        if ((currentFeatures and GeckoMediaSession.Feature.PREVIOUS_TRACK) != 0L) {
            actions = actions or PlaybackState.ACTION_SKIP_TO_PREVIOUS
        }

        val state = if (isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED
        val playbackState = PlaybackState.Builder()
            .setActions(actions)
            .setState(state, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1.0f)
            .build()
        androidMediaSession?.setPlaybackState(playbackState)
    }

    private fun updateNotification() {
        val notification = buildNotification()
        notificationManager?.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(): Notification {
        val hasPrev = (currentFeatures and GeckoMediaSession.Feature.PREVIOUS_TRACK) != 0L
        val hasNext = (currentFeatures and GeckoMediaSession.Feature.NEXT_TRACK) != 0L

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_tube)
            .setContentTitle(currentTitle)
            .setContentText(currentArtist)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(isPlaying)
            .setShowWhen(false)

        val compactActionIndices = mutableListOf<Int>()
        var actionIndex = 0

        // 1. Conditional Previous Track Action
        if (hasPrev) {
            val prevIntent = PendingIntent.getService(
                this,
                1,
                Intent(this, MediaPlaybackService::class.java).apply { action = ACTION_PREV },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(android.R.drawable.ic_media_previous, "Previous", prevIntent)
            actionIndex++
        }

        // 2. Play / Pause Action
        val playPauseAction = if (isPlaying) ACTION_PAUSE else ACTION_PLAY
        val playPauseIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val playPauseTitle = if (isPlaying) "Pause" else "Play"
        val playPauseIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, MediaPlaybackService::class.java).apply { action = playPauseAction },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        builder.addAction(playPauseIcon, playPauseTitle, playPauseIntent)
        compactActionIndices.add(actionIndex)
        actionIndex++

        // 3. Conditional Next Track Action
        if (hasNext) {
            val nextIntent = PendingIntent.getService(
                this,
                3,
                Intent(this, MediaPlaybackService::class.java).apply { action = ACTION_NEXT },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(android.R.drawable.ic_media_next, "Next", nextIntent)
            compactActionIndices.add(actionIndex)
            actionIndex++
        }

        // Apply AndroidX MediaStyle notification
        val mediaStyle = androidx.media.app.NotificationCompat.MediaStyle()
        androidMediaSession?.sessionToken?.let { token ->
            mediaStyle.setMediaSession(android.support.v4.media.session.MediaSessionCompat.Token.fromToken(token))
        }
        if (compactActionIndices.isNotEmpty()) {
            mediaStyle.setShowActionsInCompactView(*compactActionIndices.toIntArray())
        }
        builder.setStyle(mediaStyle)

        return builder.build()
    }

    private fun teardownAndStop() {
        Log.d(TAG, "Stopping foreground mode and releasing resources")
        updateWakeLock(false)
        isPlaying = false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }

        androidMediaSession?.isActive = false
        androidMediaSession?.release()
        androidMediaSession = null

        stopSelf()
    }
}
