package com.geckoflux.engine

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.geckoflux.config.AppConfiguration
import com.geckoflux.config.AppPlugin
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.WebExtension

/**
 * Manages WebExtensions lifecycle strictly based on active profile plugins.
 */
class ExtensionManager(
    private val context: Context,
    private val runtime: GeckoRuntime,
    private val config: AppConfiguration
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("geckoflux_extensions_pref", Context.MODE_PRIVATE)

    companion object {
        private const val TAG = "ExtensionManager"
        private const val KEY_UBLOCK_INSTALLED = "ublock_installed"
        private const val EXTENSION_BACKGROUND_PLAY = "resource://android/assets/extensions/background_play/"
        private const val EXTENSION_SPEED_BRIDGE = "resource://android/assets/extensions/speed_bridge/"
    }

    var speedBridgeExtension: WebExtension? = null
        private set

    /**
     * Initializes only the extensions declared in the active app profile plugins.
     */
    fun setupExtensions(onComplete: (() -> Unit)? = null) {
        // 1. Install Background Audio Plugin if declared
        if (config.hasPlugin(AppPlugin.BACKGROUND_AUDIO)) {
            installBuiltInExtension(EXTENSION_BACKGROUND_PLAY, "BackgroundAudio")
        }

        // 2. Install Speed Controller Bridge Plugin if declared
        if (config.hasPlugin(AppPlugin.SPEED_CONTROLLER) || config.hasPlugin(AppPlugin.VIDEO_GESTURES)) {
            runtime.webExtensionController
                .installBuiltIn(EXTENSION_SPEED_BRIDGE)
                .accept(
                    { ext ->
                        Log.d(TAG, "SpeedBridge Extension installed successfully: ${ext?.id}")
                        speedBridgeExtension = ext
                        onComplete?.invoke()
                    },
                    { error ->
                        Log.e(TAG, "Failed to install SpeedBridge extension", error)
                        onComplete?.invoke()
                    }
                )
        } else {
            onComplete?.invoke()
        }

        // 3. Install or update uBlock Origin from AMO if declared
        if (config.hasPlugin(AppPlugin.AD_BLOCKER_UBLOCK)) {
            setupUBlockOrigin()
        }
    }

    private fun installBuiltInExtension(assetPath: String, name: String) {
        runtime.webExtensionController
            .installBuiltIn(assetPath)
            .accept(
                { ext ->
                    Log.d(TAG, "Built-in extension [$name] installed: ${ext?.id}")
                },
                { error ->
                    Log.e(TAG, "Failed to install built-in extension [$name]", error)
                }
            )
    }

    private fun setupUBlockOrigin() {
        val isUBlockInstalled = prefs.getBoolean(KEY_UBLOCK_INSTALLED, false)

        if (!isUBlockInstalled) {
            Log.d(TAG, "Installing uBlock Origin from AMO: ${config.uBlockAmoUrl}")
            runtime.webExtensionController
                .install(config.uBlockAmoUrl)
                .accept(
                    { ext ->
                        Log.d(TAG, "uBlock Origin installed successfully from AMO: ${ext?.id}")
                        prefs.edit().putBoolean(KEY_UBLOCK_INSTALLED, true).apply()
                    },
                    { error ->
                        Log.e(TAG, "Error installing uBlock Origin from AMO", error)
                    }
                )
        } else {
            Log.d(TAG, "uBlock Origin already installed. Checking for updates...")
            runtime.webExtensionController.list().accept(
                { extensions ->
                    val ublock = extensions?.find { it.id.contains("ublock", ignoreCase = true) }
                    if (ublock != null) {
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            runtime.webExtensionController.update(ublock).accept(
                                { updated -> Log.d(TAG, "uBlock Origin updated: ${updated?.metaData?.name}") },
                                { _ -> Log.d(TAG, "uBlock Origin is up to date.") }
                            )
                        }
                    }
                },
                { error -> Log.e(TAG, "Failed to list extensions for update", error) }
            )
        }
    }
}
