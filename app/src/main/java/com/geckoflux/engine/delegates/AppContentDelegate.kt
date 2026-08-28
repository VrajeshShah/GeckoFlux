package com.geckoflux.engine.delegates

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.geckoflux.config.AppConfiguration
import org.mozilla.geckoview.GeckoSession

/**
 * Handles Web Content events such as Fullscreen video triggers and PiP requests.
 */
class AppContentDelegate(
    private val activity: Activity,
    private val config: AppConfiguration,
    private val onFullscreenChanged: ((Boolean) -> Unit)? = null
) : GeckoSession.ContentDelegate {

    var isFullscreen: Boolean = false
        private set

    override fun onFullScreen(session: GeckoSession, fullScreen: Boolean) {
        isFullscreen = fullScreen
        onFullscreenChanged?.invoke(fullScreen)

        if (!config.enableFullscreenAutoRotate) return

        activity.runOnUiThread {
            val window = activity.window
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)

            if (fullScreen) {
                // Switch to Landscape & Immersive Fullscreen
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
                insetsController.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                // Return to user/portrait orientation & Show system bars
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    override fun onTitleChange(session: GeckoSession, title: String?) {
        // Can be used to track current playing title
    }
}
