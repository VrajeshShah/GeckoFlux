package com.geckoflux.features

/**
 * Public accessor for checking feature status across GeckoFlux applications.
 */
object FeatureManager {

    /**
     * Checks if a given feature is enabled for the active product flavor.
     */
    fun isEnabled(feature: Feature): Boolean = FlavorConfig.isFeatureEnabled(feature)

    /**
     * Returns the user agent mode configured for this flavor.
     */
    fun getUserAgentMode(): Int = FlavorConfig.userAgentMode
}
