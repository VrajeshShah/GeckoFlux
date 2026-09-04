package com.geckoflux

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
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
import com.geckoflux.extensions.DarkThemeManager
import com.geckoflux.extensions.UblockManager
import com.geckoflux.features.Feature
import com.geckoflux.features.FeatureManager
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import java.util.Locale

/**
 * Bare-metal GeckoView host activity loading YouTube or YouTube Music based on flavor.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var geckoRuntime: GeckoRuntime? = null
    private var geckoSession: GeckoSession? = null
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
            val runtime = geckoRuntime
            if (runtime != null && FeatureManager.isEnabled(Feature.DARK_THEME)) {
                DarkThemeManager.ensureInstalled(runtime)
            }
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
        startAppFlow()
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
            geckoRuntime?.let { runtime ->
                UblockManager.retry(this, runtime, ublockListener)
            }
        }

        binding.btnContinue.setOnClickListener {
            binding.setupOverlay.visibility = View.GONE
            val runtime = geckoRuntime
            if (runtime != null && FeatureManager.isEnabled(Feature.DARK_THEME)) {
                DarkThemeManager.ensureInstalled(runtime)
            }
            loadTargetUrl()
        }
    }

    private fun initGeckoView() {
        // 1. Initialize GeckoRuntime with hardware acceleration and dark theme
        val runtimeSettings = GeckoRuntimeSettings.Builder()
            .preferredColorScheme(GeckoRuntimeSettings.COLOR_SCHEME_DARK)
            .build()
        geckoRuntime = GeckoRuntime.create(this, runtimeSettings)

        // 2. Initialize GeckoSession with flavor-configured user agent mode
        val sessionSettings = GeckoSessionSettings.Builder()
            .userAgentMode(FeatureManager.getUserAgentMode())
            .usePrivateMode(false)
            .build()
        geckoSession = GeckoSession(sessionSettings)

        // 3. Setup Progress and Navigation tracking
        geckoSession?.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) {
                binding.loadingProgress.visibility = View.VISIBLE
            }

            override fun onPageStop(session: GeckoSession, success: Boolean) {
                binding.loadingProgress.visibility = View.GONE
            }
        }

        geckoSession?.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
                this@MainActivity.canGoBack = canGoBack
            }
        }

        // 4. Setup Content delegate for Fullscreen support
        geckoSession?.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onFullScreen(session: GeckoSession, fullScreen: Boolean) {
                handleFullScreen(fullScreen)
            }
        }

        // 5. Attach session to GeckoView
        geckoSession?.open(geckoRuntime!!)
        binding.geckoView.setSession(geckoSession!!)
    }

    private fun startAppFlow() {
        val runtime = geckoRuntime ?: return

        if (FeatureManager.isEnabled(Feature.UBLOCK_ORIGIN)) {
            // Show setup overlay immediately so user has visual feedback from frame 1
            binding.setupOverlay.visibility = View.VISIBLE
            binding.setupErrorSection.visibility = View.GONE
            binding.setupProgressBar.visibility = View.VISIBLE
            binding.setupProgressBar.isIndeterminate = true
            binding.setupStatusText.setText(R.string.setup_checking)
            binding.setupProgressDetail.visibility = View.GONE

            // uBlock installs first. Dark theme extension installs second in onReady().
            UblockManager.ensureInstalled(this, runtime, ublockListener)
        } else {
            if (FeatureManager.isEnabled(Feature.DARK_THEME)) {
                DarkThemeManager.ensureInstalled(runtime)
            }
            loadTargetUrl()
        }
    }

    private fun loadTargetUrl() {
        if (pageLoaded) return
        pageLoaded = true

        val targetUrl = intent?.dataString ?: BuildConfig.TARGET_URL
        loadUriWithPreferences(targetUrl)
    }

    private fun loadUriWithPreferences(uri: String) {
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
        if (isFullScreen && FeatureManager.isEnabled(Feature.SWIPE_DOWN_EXIT_FULLSCREEN)) {
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
                    // Definite downward swipe while in fullscreen:
                    // 1. Swiped downwards by at least 120 pixels
                    // 2. Significantly vertical (vertical delta > 1.5 * horizontal delta)
                    // 3. Fast flick/swipe within 500ms
                    if (deltaY > 120 && Math.abs(deltaY) > Math.abs(deltaX) * 1.5 && duration < 500) {
                        geckoSession?.exitFullScreen()
                        return true
                    }
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onStop() {
        super.onStop()
        if (isFullScreen) {
            geckoSession?.exitFullScreen()
        }
    }

    override fun onDestroy() {
        geckoSession?.close()
        super.onDestroy()
    }
}