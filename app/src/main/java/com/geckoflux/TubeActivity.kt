package com.geckoflux

import com.geckoflux.features.Feature
import com.geckoflux.features.FeatureManager
import com.geckoflux.navigation.AppType

/**
 * Dedicated activity for GeckoTube (YouTube mobile client).
 */
open class TubeActivity : BaseGeckoActivity() {
    override val appType: AppType = AppType.TUBE
    override val defaultTargetUrl: String = "https://m.youtube.com"
    override val enableSwipeDownExitFullscreen: Boolean
        get() = FeatureManager.isEnabled(Feature.SWIPE_DOWN_EXIT_FULLSCREEN)
}
