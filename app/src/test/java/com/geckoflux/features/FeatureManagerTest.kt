package com.geckoflux.features

import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureManagerTest {

    @Test
    fun testUblockOriginFeatureIsEnabledByDefault() {
        assertTrue(
            "uBlock Origin should be enabled by default in this flavor",
            FeatureManager.isEnabled(Feature.UBLOCK_ORIGIN)
        )
    }

    @Test
    fun testFeatureEnumValues() {
        val features = Feature.values()
        assertTrue(features.contains(Feature.UBLOCK_ORIGIN))
    }
}
