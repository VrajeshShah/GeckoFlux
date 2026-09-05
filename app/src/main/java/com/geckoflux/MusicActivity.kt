package com.geckoflux

import com.geckoflux.navigation.AppType

/**
 * Dedicated activity for GeckoMusic (YouTube Music mobile client).
 */
open class MusicActivity : BaseGeckoActivity() {
    override val appType: AppType = AppType.MUSIC
    override val defaultTargetUrl: String = "https://music.youtube.com"
    override val enableSwipeDownExitFullscreen: Boolean = false
}
