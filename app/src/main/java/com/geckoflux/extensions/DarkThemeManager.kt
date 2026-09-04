package com.geckoflux.extensions

import android.util.Log
import org.mozilla.geckoview.GeckoRuntime

/**
 * Manages the built-in dark theme WebExtension for YouTube.
 */
object DarkThemeManager {

    private const val TAG = "DarkThemeManager"
    private const val EXTENSION_URI = "resource://android/assets/extensions/dark_theme/"

    /**
     * Ensures the dark theme built-in WebExtension is installed in the GeckoRuntime.
     */
    fun ensureInstalled(runtime: GeckoRuntime) {
        runtime.webExtensionController.installBuiltIn(EXTENSION_URI)
            .accept(
                { extension ->
                    Log.d(TAG, "Dark theme extension installed: ${extension?.id}")
                },
                { throwable ->
                    Log.w(TAG, "Failed to install dark theme extension", throwable)
                }
            )
    }
}
