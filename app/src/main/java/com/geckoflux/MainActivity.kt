package com.geckoflux

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.geckoflux.databinding.ActivityMainBinding
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
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupBackNavigation()
        setupOverlayActions()
        initGeckoView()
        startAppFlow()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.dataString?.let { uri ->
            geckoSession?.loadUri(uri)
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

        // 4. Attach session to GeckoView
        geckoSession?.open(geckoRuntime!!)
        binding.geckoView.setSession(geckoSession!!)
    }

    private fun startAppFlow() {
        if (FeatureManager.isEnabled(Feature.UBLOCK_ORIGIN)) {
            val runtime = geckoRuntime ?: return

            // Show setup overlay immediately so user has visual feedback from frame 1
            binding.setupOverlay.visibility = View.VISIBLE
            binding.setupErrorSection.visibility = View.GONE
            binding.setupProgressBar.visibility = View.VISIBLE
            binding.setupProgressBar.isIndeterminate = true
            binding.setupStatusText.setText(R.string.setup_checking)
            binding.setupProgressDetail.visibility = View.GONE

            UblockManager.ensureInstalled(this, runtime, ublockListener)
        } else {
            loadTargetUrl()
        }
    }

    private fun loadTargetUrl() {
        if (pageLoaded) return
        pageLoaded = true

        val targetUrl = intent?.dataString ?: BuildConfig.TARGET_URL
        geckoSession?.loadUri(targetUrl)
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (canGoBack && geckoSession != null) {
                    geckoSession?.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    override fun onDestroy() {
        geckoSession?.close()
        super.onDestroy()
    }
}