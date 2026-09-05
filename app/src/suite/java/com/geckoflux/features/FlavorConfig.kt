package com.geckoflux.features

import org.mozilla.geckoview.GeckoSessionSettings

/**
 * Feature configuration specific to the GeckoFlux Suite flavor (Tube + Music).
 */
internal object FlavorConfig {

    private val enabledFeatures: Set<Feature> = setOf(
        Feature.UBLOCK_ORIGIN,
        Feature.DARK_THEME,
        Feature.SWIPE_DOWN_EXIT_FULLSCREEN,
    )

    val userAgentMode: Int = GeckoSessionSettings.USER_AGENT_MODE_MOBILE

    fun isFeatureEnabled(feature: Feature): Boolean = feature in enabledFeatures
}
