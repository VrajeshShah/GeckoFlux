package com.geckoflux

import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.geckoflux.BuildConfig
import com.geckoflux.databinding.ActivityMainBinding
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings

/**
 * Bare-metal GeckoView host activity loading YouTube or YouTube Music based on flavor.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var geckoRuntime: GeckoRuntime? = null
    private var geckoSession: GeckoSession? = null
    private var canGoBack: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupBackNavigation()
        initGeckoView()
    }

    private fun initGeckoView() {
        // 1. Initialize GeckoRuntime with hardware acceleration and dark theme
        val runtimeSettings = GeckoRuntimeSettings.Builder()
            .preferredColorScheme(GeckoRuntimeSettings.COLOR_SCHEME_DARK)
            .build()
        geckoRuntime = GeckoRuntime.create(this, runtimeSettings)

        // 2. Initialize GeckoSession
        val sessionSettings = GeckoSessionSettings.Builder()
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

        // 4. Attach session to GeckoView and load target URL
        geckoSession?.open(geckoRuntime!!)
        binding.geckoView.setSession(geckoSession!!)

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