package com.geckoflux.engine

import android.app.Activity
import android.util.Log
import com.geckoflux.config.AppConfiguration
import com.geckoflux.config.AppPlugin
import com.geckoflux.engine.delegates.AppContentDelegate
import com.geckoflux.engine.delegates.AppNavigationDelegate
import com.geckoflux.engine.delegates.AppPromptDelegate
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.GeckoView

/**
 * High-level coordinator for GeckoRuntime, GeckoSession, delegates, and extensions.
 */
class GeckoEngineManager(
    private val activity: Activity,
    private val geckoView: GeckoView,
    val config: AppConfiguration,
    private val onLoadingStateChanged: ((Boolean) -> Unit)? = null,
    private val onFullscreenChanged: ((Boolean) -> Unit)? = null
) {
    companion object {
        private const val TAG = "GeckoEngineManager"
    }

    private var geckoRuntime: GeckoRuntime? = null
    var geckoSession: GeckoSession? = null
        private set

    lateinit var extensionManager: ExtensionManager
        private set
    lateinit var contentDelegate: AppContentDelegate
        private set
    lateinit var navigationDelegate: AppNavigationDelegate
        private set

    var currentSpeed: Float = 1.0f
        private set

    /**
     * Initializes the Gecko engine, loads extensions, configures delegates, and opens the session.
     */
    fun initialize(onReady: (() -> Unit)? = null) {
        Log.d(TAG, "Initializing GeckoFlux Engine for: ${config.appName}")

        // 1. Resolve Runtime Context (Shared for Google ecosystem vs Isolated for other apps)
        val runtimeContext = if (config.hasPlugin(AppPlugin.SHARED_GOOGLE_SESSION) && config.sessionGroupId != null) {
            try {
                // Resolve primary shared storage container (handles both debug and release package names)
                val primaryPkg = if (activity.packageName.endsWith(".debug")) "com.geckoflux.tube.debug" else "com.geckoflux.tube"
                if (activity.packageName == primaryPkg) {
                    activity.applicationContext
                } else {
                    activity.createPackageContext(
                        primaryPkg,
                        android.content.Context.CONTEXT_INCLUDE_CODE or android.content.Context.CONTEXT_IGNORE_SECURITY
                    )
                }
            } catch (e: Exception) {
                Log.d(TAG, "Fallback to local context for GeckoRuntime storage", e)
                activity.applicationContext
            }
        } else {
            // Completely isolated private storage (e.g. Instagram / third-party profiles)
            activity.applicationContext
        }

        // 2. Create Runtime Settings (DOM Storage, Hardware Acceleration, Dark Scheme enabled by default)
        val runtimeSettings = GeckoRuntimeSettings.Builder()
            .aboutConfigEnabled(true)
            .preferredColorScheme(GeckoRuntimeSettings.COLOR_SCHEME_DARK)
            .build()

        geckoRuntime = GeckoRuntime.create(runtimeContext, runtimeSettings)

        // 2. Initialize Extension Manager
        extensionManager = ExtensionManager(activity, geckoRuntime!!, config)
        extensionManager.setupExtensions()

        // 3. Create Session Settings (Non-private for persistent Google/YouTube auth)
        val sessionSettings = GeckoSessionSettings.Builder()
            .usePrivateMode(false)
            .build()

        geckoSession = GeckoSession(sessionSettings)

        // 4. Attach Delegates
        contentDelegate = AppContentDelegate(activity, config, onFullscreenChanged)
        navigationDelegate = AppNavigationDelegate(activity, config)

        geckoSession?.apply {
            contentDelegate = this@GeckoEngineManager.contentDelegate
            navigationDelegate = this@GeckoEngineManager.navigationDelegate

            if (config.hasPlugin(AppPlugin.NATIVE_PROMPTS)) {
                this.promptDelegate = AppPromptDelegate(activity)
            }

            progressDelegate = object : GeckoSession.ProgressDelegate {
                override fun onPageStart(session: GeckoSession, url: String) {
                    onLoadingStateChanged?.invoke(true)
                }

                override fun onPageStop(session: GeckoSession, success: Boolean) {
                    onLoadingStateChanged?.invoke(false)
                }
            }

            // 5. Open Session & Bind to View
            open(geckoRuntime!!)
            geckoView.setSession(this)

            // 6. Set custom User-Agent if provided in config
            if (!config.customUserAgent.isNullOrBlank()) {
                settings.userAgentOverride = config.customUserAgent
            }

            // 7. Load target URL (or intent URL if launched with a link)
            val initialUrl = activity.intent?.dataString?.takeIf { config.isAllowed(it) } ?: config.targetUrl
            loadUri(initialUrl)
        }

        onReady?.invoke()
    }

    fun loadUrl(url: String) {
        geckoSession?.loadUri(url)
    }

    /**
     * Applies playback rate to the active YouTube video.
     */
    fun setPlaybackSpeed(speed: Float) {
        currentSpeed = speed
        Log.d(TAG, "Setting playback speed to: ${speed}x")
    }

    fun canGoBack(): Boolean = navigationDelegate.canGoBack

    fun goBack() {
        geckoSession?.goBack()
    }

    fun reload() {
        geckoSession?.reload()
    }

    fun close() {
        geckoSession?.close()
    }
}
