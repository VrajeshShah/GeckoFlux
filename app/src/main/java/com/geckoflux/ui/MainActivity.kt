package com.geckoflux.ui

import android.app.PictureInPictureParams
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.geckoflux.BuildConfig
import com.geckoflux.R
import com.geckoflux.config.AppPlugin
import com.geckoflux.config.AppProfiles
import com.geckoflux.engine.GeckoEngineManager
import com.geckoflux.speed.SpeedControllerBottomSheet
import org.mozilla.geckoview.GeckoView

/**
 * Main Activity hosting the GeckoView instance, handling PiP, navigation gestures,
 * and presenting native Speed Controls based on active profile plugins.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var geckoView: GeckoView
    private lateinit var loadingProgress: LinearProgressIndicator
    private lateinit var fabSpeedControl: FloatingActionButton

    private lateinit var engineManager: GeckoEngineManager
    private var backPressedTime: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Make window edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(window, false)

        geckoView = findViewById(R.id.gecko_view)
        loadingProgress = findViewById(R.id.loading_progress)
        fabSpeedControl = findViewById(R.id.fab_speed_control)

        // 1. Resolve configuration based on build flavor
        val activeProfile = AppProfiles.fromFlavor(BuildConfig.APP_PROFILE)

        // 2. Initialize GeckoEngineManager
        engineManager = GeckoEngineManager(
            activity = this,
            geckoView = geckoView,
            config = activeProfile,
            onLoadingStateChanged = { isLoading ->
                runOnUiThread {
                    loadingProgress.visibility = if (isLoading) View.VISIBLE else View.GONE
                }
            },
            onFullscreenChanged = { isFullscreen ->
                runOnUiThread {
                    // Hide FAB while in fullscreen video mode or if speed controller plugin is disabled
                    fabSpeedControl.visibility = if (isFullscreen || !activeProfile.hasPlugin(AppPlugin.SPEED_CONTROLLER)) {
                        View.GONE
                    } else {
                        View.VISIBLE
                    }
                }
            }
        )

        engineManager.initialize {
            // Speed controller trigger visibility
            if (activeProfile.hasPlugin(AppPlugin.SPEED_CONTROLLER)) {
                fabSpeedControl.visibility = View.VISIBLE
            }
        }

        // 3. Setup Speed Controller Bottom Sheet Trigger
        fabSpeedControl.setOnClickListener {
            showSpeedControllerBottomSheet()
        }

        // 4. Setup Native Back-Press Handling
        setupBackNavigation()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val activeProfile = AppProfiles.fromFlavor(BuildConfig.APP_PROFILE)
        intent.dataString?.let { url ->
            if (activeProfile.isAllowed(url)) {
                engineManager.loadUrl(url)
            }
        }
    }

    private fun showSpeedControllerBottomSheet() {
        val bottomSheet = SpeedControllerBottomSheet.newInstance(
            currentSpeed = engineManager.currentSpeed
        ) { selectedSpeed ->
            engineManager.setPlaybackSpeed(selectedSpeed)
        }
        bottomSheet.show(supportFragmentManager, SpeedControllerBottomSheet.TAG)
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // 1. If in video fullscreen, exit fullscreen first
                if (engineManager.contentDelegate.isFullscreen) {
                    engineManager.geckoSession?.exitFullScreen()
                    return
                }

                // 2. If browser has internal history, navigate back
                if (engineManager.canGoBack()) {
                    engineManager.goBack()
                    return
                }

                // 3. If at root page, require double tap back to prevent accidental exit
                val currentTime = System.currentTimeMillis()
                if (currentTime - backPressedTime < 2000) {
                    finish()
                } else {
                    backPressedTime = currentTime
                    Toast.makeText(
                        this@MainActivity,
                        getString(R.string.press_again_to_exit),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        })
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Automatic Picture-in-Picture when user presses Home while watching video
        if (engineManager.config.enablePiP) {
            try {
                val pipParams = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build()
                enterPictureInPictureMode(pipParams)
            } catch (e: Exception) {
                // Ignore if PiP is not supported or permitted
            }
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        fabSpeedControl.visibility = if (isInPictureInPictureMode) View.GONE else {
            if (engineManager.config.hasPlugin(AppPlugin.SPEED_CONTROLLER)) View.VISIBLE else View.GONE
        }
    }

    override fun onDestroy() {
        engineManager.close()
        super.onDestroy()
    }
}
