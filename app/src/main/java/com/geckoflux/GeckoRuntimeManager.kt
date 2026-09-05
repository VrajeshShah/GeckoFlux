package com.geckoflux

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.geckoflux.extensions.DarkThemeManager
import com.geckoflux.extensions.UblockManager
import com.geckoflux.features.Feature
import com.geckoflux.features.FeatureManager
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Process-wide singleton managing the shared GeckoRuntime, cookie store,
 * and shared WebExtensions (uBlock Origin & Dark Theme).
 */
object GeckoRuntimeManager {

    private const val TAG = "GeckoRuntimeManager"

    private var runtime: GeckoRuntime? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArrayList<UblockManager.InstallListener>()

    enum class SetupState {
        IDLE,
        CHECKING,
        DOWNLOADING,
        INSTALLING,
        READY,
        ERROR
    }

    var currentState: SetupState = SetupState.IDLE
        private set
    var lastProgressPercent: Int = 0
        private set
    var lastBytesRead: Long = 0L
        private set
    var lastTotalBytes: Long = 0L
        private set
    var lastError: Throwable? = null
        private set

    /**
     * Obtains the shared GeckoRuntime instance, creating it if needed.
     */
    @Synchronized
    fun getRuntime(context: Context): GeckoRuntime {
        if (runtime == null) {
            Log.d(TAG, "Creating shared GeckoRuntime instance...")
            val runtimeSettings = GeckoRuntimeSettings.Builder()
                .preferredColorScheme(GeckoRuntimeSettings.COLOR_SCHEME_DARK)
                .build()
            runtime = GeckoRuntime.create(context.applicationContext, runtimeSettings)
        }
        return runtime!!
    }

    /**
     * Registers a listener to observe uBlock Origin download/install events.
     * If already ready or error, immediately notifies the listener.
     */
    fun registerListener(listener: UblockManager.InstallListener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }

        // Catch up new listener with current state
        when (currentState) {
            SetupState.DOWNLOADING -> {
                listener.onDownloadStarted()
                listener.onDownloadProgress(lastProgressPercent, lastBytesRead, lastTotalBytes)
            }
            SetupState.INSTALLING -> {
                listener.onInstalling()
            }
            SetupState.READY -> {
                listener.onReady()
            }
            SetupState.ERROR -> {
                lastError?.let { listener.onError(it) }
            }
            else -> {}
        }
    }

    /**
     * Unregisters a previously registered listener.
     */
    fun unregisterListener(listener: UblockManager.InstallListener) {
        listeners.remove(listener)
    }

    private var isNetworkMonitoring = false

    private fun registerNetworkCallback(context: Context) {
        if (isNetworkMonitoring) return
        isNetworkMonitoring = true
        val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager ?: return
        val request = android.net.NetworkRequest.Builder()
            .addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        try {
            cm.registerNetworkCallback(request, object : android.net.ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: android.net.Network) {
                    if (currentState == SetupState.ERROR) {
                        Log.d(TAG, "Network connectivity restored. Retrying uBlock download in background...")
                        mainHandler.post {
                            retry(context.applicationContext)
                        }
                    }
                }
            })
        } catch (e: Exception) {
            Log.w(TAG, "Could not register network callback for background retry", e)
        }
    }

    /**
     * Initializes extensions for the shared runtime.
     * Ensures uBlock Origin is downloaded & installed once, and Dark Theme is injected.
     */
    fun ensureExtensions(context: Context, listener: UblockManager.InstallListener? = null) {
        registerNetworkCallback(context)
        listener?.let { registerListener(it) }

        if (currentState == SetupState.READY) {
            listener?.onReady()
            return
        }

        if (currentState == SetupState.CHECKING ||
            currentState == SetupState.DOWNLOADING ||
            currentState == SetupState.INSTALLING
        ) {
            return
        }

        val rt = getRuntime(context)
        if (!FeatureManager.isEnabled(Feature.UBLOCK_ORIGIN)) {
            currentState = SetupState.READY
            if (FeatureManager.isEnabled(Feature.DARK_THEME)) {
                DarkThemeManager.ensureInstalled(rt)
            }
            notifyReady()
            return
        }

        currentState = SetupState.CHECKING
        UblockManager.ensureInstalled(context, rt, internalUblockListener(context))
    }

    /**
     * Retries failed extension download/install.
     */
    fun retry(context: Context) {
        currentState = SetupState.DOWNLOADING
        val rt = getRuntime(context)
        UblockManager.retry(context, rt, internalUblockListener(context))
    }

    private fun internalUblockListener(context: Context): UblockManager.InstallListener {
        return object : UblockManager.InstallListener {
            override fun onDownloadStarted() {
                currentState = SetupState.DOWNLOADING
                mainHandler.post {
                    for (l in listeners) l.onDownloadStarted()
                }
            }

            override fun onDownloadProgress(percent: Int, bytesRead: Long, totalBytes: Long) {
                currentState = SetupState.DOWNLOADING
                lastProgressPercent = percent
                lastBytesRead = bytesRead
                lastTotalBytes = totalBytes
                mainHandler.post {
                    for (l in listeners) l.onDownloadProgress(percent, bytesRead, totalBytes)
                }
            }

            override fun onInstalling() {
                currentState = SetupState.INSTALLING
                mainHandler.post {
                    for (l in listeners) l.onInstalling()
                }
            }

            override fun onReady() {
                currentState = SetupState.READY
                val rt = getRuntime(context)
                if (FeatureManager.isEnabled(Feature.DARK_THEME)) {
                    DarkThemeManager.ensureInstalled(rt)
                }
                notifyReady()
            }

            override fun onError(error: Throwable) {
                currentState = SetupState.ERROR
                lastError = error
                mainHandler.post {
                    for (l in listeners) l.onError(error)
                }
            }
        }
    }

    private fun notifyReady() {
        mainHandler.post {
            for (l in listeners) l.onReady()
        }
    }
}
