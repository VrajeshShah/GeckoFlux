package com.geckoflux.extensions

import android.os.Handler
import android.os.Looper
import android.util.Log
import org.mozilla.geckoview.GeckoRuntime

/**
 * Manages the built-in enhancer WebExtension (Dark theme + Background playback) for YouTube.
 */
object DarkThemeManager {

    private const val TAG = "DarkThemeManager"
    const val EXTENSION_ID = "enhancer@geckoflux.com"
    private const val LEGACY_ID = "darktheme@geckoflux.com"
    private const val EXTENSION_URI = "resource://android/assets/extensions/dark_theme/"

    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Ensures the built-in WebExtension is cleanly installed or updated in the GeckoRuntime.
     */
    fun ensureInstalled(runtime: GeckoRuntime) {
        mainHandler.post {
            runtime.webExtensionController.list().accept({ list ->
                mainHandler.post {
                    handleExtensionList(runtime, list)
                }
            }, {
                mainHandler.post {
                    installExtension(runtime)
                }
            })
        }
    }

    @androidx.annotation.UiThread
    private fun handleExtensionList(
        runtime: GeckoRuntime,
        list: List<org.mozilla.geckoview.WebExtension>?
    ) {
        // 1. Remove legacy extension if present
        val legacy = list?.find { it.id == LEGACY_ID }
        if (legacy != null) {
            runtime.webExtensionController.uninstall(legacy)
        }

        // 2. Install or update enhancer extension
        val existing = list?.find { it.id == EXTENSION_ID }
        if (existing != null) {
            Log.d(TAG, "Updating built-in enhancer extension...")
            runtime.webExtensionController.update(existing)
        } else {
            Log.d(TAG, "Installing built-in enhancer extension...")
            runtime.webExtensionController.installBuiltIn(EXTENSION_URI).accept(
                { ext -> Log.d(TAG, "Enhancer extension installed: ${ext?.id}") },
                { err -> Log.w(TAG, "Failed to install enhancer extension", err) }
            )
        }
    }

    @androidx.annotation.UiThread
    private fun installExtension(runtime: GeckoRuntime) {
        runtime.webExtensionController.installBuiltIn(EXTENSION_URI)
    }
}
