package com.geckoflux

import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.geckoflux.databinding.ActivityMainBinding
import com.geckoflux.extensions.UblockManager
import com.geckoflux.features.Feature
import com.geckoflux.features.FeatureManager
import com.geckoflux.media.GeckoMediaSessionManager
import com.geckoflux.navigation.AppType
import com.geckoflux.navigation.NavigationRouter
import com.geckoflux.navigation.RouteAction
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import java.util.Locale

/**
 * Common base activity hosting a GeckoSession on the shared GeckoRuntime.
 */
abstract class BaseGeckoActivity : AppCompatActivity() {

    abstract val appType: AppType
    abstract val defaultTargetUrl: String
    open val enableSwipeDownExitFullscreen: Boolean = false

    protected lateinit var binding: ActivityMainBinding
    protected var geckoSession: GeckoSession? = null
    private var canGoBack: Boolean = false
    private var pageLoaded: Boolean = false
    private var isFullScreen: Boolean = false
    private var lastInsets: Insets = Insets.NONE

    private val ublockListener = object : UblockManager.InstallListener {
        override fun onDownloadStarted() {
            binding.setupOverlay.visibility = View.VISIBLE
            binding.setupErrorSection.visibility = View.GONE
            binding.setupProgressBar.visibility = View.VISIBLE
            binding.setupProgressBar.isIndeterminate = false
            binding.setupProgressBar.progress = 0
            binding.setupStatusText.setText(R.string.setup_ublock_downloading)
            binding.setupProgressDetail.visibility = View.VISIBLE
            binding.setupProgressDetail.text = "0%"
        }

        override fun onDownloadProgress(percent: Int, bytesRead: Long, totalBytes: Long) {
            binding.setupOverlay.visibility = View.VISIBLE
            if (percent >= 0) {
                binding.setupProgressBar.isIndeterminate = false
                binding.setupProgressBar.progress = percent
                val mbRead = bytesRead / (1024.0 * 1024.0)
                val mbTotal = totalBytes / (1024.0 * 1024.0)
                binding.setupProgressDetail.text = String.format(
                    Locale.US,
                    "%.1f MB / %.1f MB (%d%%)",
                    mbRead,
                    mbTotal,
                    percent
                )
            } else {
                binding.setupProgressBar.isIndeterminate = true
                val mbRead = bytesRead / (1024.0 * 1024.0)
                binding.setupProgressDetail.text = String.format(
                    Locale.US,
                    "%.1f MB",
                    mbRead
                )
            }
        }

        override fun onInstalling() {
            binding.setupStatusText.setText(R.string.setup_ublock_installing)
            binding.setupProgressBar.isIndeterminate = true
            binding.setupProgressDetail.visibility = View.GONE
        }

        override fun onReady() {
            binding.setupOverlay.visibility = View.GONE
            loadTargetUrl()
        }

        override fun onError(error: Throwable) {
            binding.setupOverlay.visibility = View.VISIBLE
            binding.setupProgressBar.visibility = View.GONE
            binding.setupProgressDetail.visibility = View.GONE
            binding.setupErrorSection.visibility = View.VISIBLE
            binding.setupErrorMsg.text = getString(R.string.setup_error_network)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enable edge-to-edge drawing and notch cutout support
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWindowInsets()
        setupBackNavigation()
        setupOverlayActions()
        initGeckoView()
        requestNotificationPermissionIfNeeded()
        startAppFlow()
    }

    override fun onStart() {
        super.onStart()
        GeckoRuntimeManager.registerListener(ublockListener)
    }

    override fun onStop() {
        GeckoRuntimeManager.unregisterListener(ublockListener)
        super.onStop()
        if (isFullScreen) {
            geckoSession?.exitFullScreen()
        }
    }

    override fun onDestroy() {
        geckoSession?.let { GeckoMediaSessionManager.detachFromSession(it) }
        geckoSession?.close()
        super.onDestroy()
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or
                WindowInsetsCompat.Type.displayCutout() or
                WindowInsetsCompat.Type.ime()
            )
            lastInsets = insets
            updateLayoutInsets()
            windowInsets
        }
    }

    private fun updateLayoutInsets() {
        if (isFullScreen) {
            binding.geckoContainer.setPadding(0, 0, 0, 0)
        } else {
            binding.geckoContainer.setPadding(
                lastInsets.left,
                lastInsets.top,
                lastInsets.right,
                lastInsets.bottom
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.dataString?.let { uri ->
            loadUriWithPreferences(uri)
        }
    }

    private fun setupOverlayActions() {
        val appName = getString(R.string.app_name)
        binding.setupTitleText.text = getString(R.string.setup_title, appName)

        binding.btnRetry.setOnClickListener {
            binding.setupProgressBar.visibility = View.VISIBLE
            binding.setupProgressDetail.visibility = View.VISIBLE
            binding.setupErrorSection.visibility = View.GONE
            GeckoRuntimeManager.retry(this)
        }

        binding.btnContinue.setOnClickListener {
            binding.setupOverlay.visibility = View.GONE
            loadTargetUrl()
        }
    }

    private fun initGeckoView() {
        val runtime = GeckoRuntimeManager.getRuntime(this)

        val sessionSettings = GeckoSessionSettings.Builder()
            .userAgentMode(FeatureManager.getUserAgentMode())
            .usePrivateMode(false)
            .build()
        val session = GeckoSession(sessionSettings)
        geckoSession = session

        session.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) {
                binding.loadingProgress.visibility = View.VISIBLE
            }

            override fun onPageStop(session: GeckoSession, success: Boolean) {
                binding.loadingProgress.visibility = View.GONE
            }
        }

        session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
                this@BaseGeckoActivity.canGoBack = canGoBack
            }

            override fun onLoadRequest(
                session: GeckoSession,
                request: GeckoSession.NavigationDelegate.LoadRequest
            ): GeckoResult<AllowOrDeny>? {
                val action = NavigationRouter.resolve(appType, request.uri)
                return when (action) {
                    RouteAction.LOAD_IN_SESSION -> {
                        GeckoResult.fromValue(AllowOrDeny.ALLOW)
                    }
                    RouteAction.ROUTE_TO_TUBE -> {
                        navigateToTube(request.uri)
                        GeckoResult.fromValue(AllowOrDeny.DENY)
                    }
                    RouteAction.ROUTE_TO_MUSIC -> {
                        navigateToMusic(request.uri)
                        GeckoResult.fromValue(AllowOrDeny.DENY)
                    }
                    RouteAction.OPEN_EXTERNAL_BROWSER -> {
                        openExternalBrowser(request.uri)
                        GeckoResult.fromValue(AllowOrDeny.DENY)
                    }
                }
            }
        }

        session.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onFullScreen(session: GeckoSession, fullScreen: Boolean) {
                handleFullScreen(fullScreen)
            }
        }

        session.permissionDelegate = object : GeckoSession.PermissionDelegate {
            override fun onContentPermissionRequest(
                session: GeckoSession,
                perm: GeckoSession.PermissionDelegate.ContentPermission
            ): GeckoResult<Int>? {
                return if (perm.permission == GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_AUDIBLE ||
                    perm.permission == GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_INAUDIBLE ||
                    perm.permission == GeckoSession.PermissionDelegate.PERMISSION_MEDIA_KEY_SYSTEM_ACCESS
                ) {
                    GeckoResult.fromValue(GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW)
                } else {
                    GeckoResult.fromValue(GeckoSession.PermissionDelegate.ContentPermission.VALUE_PROMPT)
                }
            }
        }

        session.open(runtime)
        binding.geckoView.setSession(session)
        com.geckoflux.media.GeckoMediaSessionManager.attachToSession(session, this, appType)
    }

    private fun startAppFlow() {
        if (FeatureManager.isEnabled(Feature.UBLOCK_ORIGIN)) {
            val state = GeckoRuntimeManager.currentState
            if (state != GeckoRuntimeManager.SetupState.READY) {
                binding.setupOverlay.visibility = View.VISIBLE
                binding.setupErrorSection.visibility = View.GONE
                binding.setupProgressBar.visibility = View.VISIBLE
                binding.setupProgressBar.isIndeterminate = true
                binding.setupStatusText.setText(R.string.setup_checking)
                binding.setupProgressDetail.visibility = View.GONE
            }
            GeckoRuntimeManager.ensureExtensions(this, ublockListener)
        } else {
            GeckoRuntimeManager.ensureExtensions(this, ublockListener)
            loadTargetUrl()
        }
    }

    protected fun loadTargetUrl() {
        if (pageLoaded) return
        pageLoaded = true

        val targetUrl = intent?.dataString ?: defaultTargetUrl
        loadUriWithPreferences(targetUrl)
    }

    fun loadUriWithPreferences(uri: String) {
        if (FeatureManager.isEnabled(Feature.DARK_THEME)) {
            val loader = GeckoSession.Loader()
                .uri(uri)
                .additionalHeaders(mapOf("Cookie" to "PREF=f6=400"))
                .headerFilter(GeckoSession.HEADER_FILTER_UNRESTRICTED_UNSAFE)
            geckoSession?.load(loader)
        } else {
            geckoSession?.loadUri(uri)
        }
    }

    private fun handleFullScreen(fullScreen: Boolean) {
        if (isFullScreen == fullScreen) return
        isFullScreen = fullScreen

        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        if (fullScreen) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            insetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            insetsController.show(WindowInsetsCompat.Type.systemBars())
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER
        }
        updateLayoutInsets()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && isFullScreen) {
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            insetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isFullScreen) {
                    geckoSession?.exitFullScreen()
                } else if (canGoBack && geckoSession != null) {
                    geckoSession?.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    private var fsDownX = 0f
    private var fsDownY = 0f
    private var fsDownTime = 0L

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (isFullScreen && enableSwipeDownExitFullscreen) {
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    fsDownX = ev.rawX
                    fsDownY = ev.rawY
                    fsDownTime = System.currentTimeMillis()
                }
                MotionEvent.ACTION_UP -> {
                    val deltaX = ev.rawX - fsDownX
                    val deltaY = ev.rawY - fsDownY
                    val duration = System.currentTimeMillis() - fsDownTime
                    if (deltaY > 120 && Math.abs(deltaY) > Math.abs(deltaX) * 1.5 && duration < 500) {
                        geckoSession?.exitFullScreen()
                        return true
                    }
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    protected open fun navigateToTube(uri: String) {
        val intent = Intent(this, TubeActivity::class.java).apply {
            data = Uri.parse(uri)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        if (isActivityResolvable(intent)) {
            startActivity(intent)
        } else {
            openExternalBrowser(uri)
        }
    }

    protected open fun navigateToMusic(uri: String) {
        val intent = Intent(this, MusicActivity::class.java).apply {
            data = Uri.parse(uri)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        if (isActivityResolvable(intent)) {
            startActivity(intent)
        } else {
            openExternalBrowser(uri)
        }
    }

    protected fun openExternalBrowser(uri: String) {
        try {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(browserIntent)
        } catch (e: Exception) {
            Log.e("BaseGeckoActivity", "Could not open external link: $uri", e)
        }
    }

    private fun isActivityResolvable(intent: Intent): Boolean {
        return try {
            val resolveInfo = packageManager.resolveActivity(intent, 0)
            resolveInfo != null && resolveInfo.activityInfo != null && resolveInfo.activityInfo.isEnabled
        } catch (e: Exception) {
            false
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                    101
                )
            }
        }
    }
}
