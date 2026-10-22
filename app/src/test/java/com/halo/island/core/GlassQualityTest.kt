package com.halo.island.core

import com.halo.island.ui.effectiveQuality
import org.junit.Assert.assertEquals
import org.junit.Test

class GlassQualityTest {
    @Test
    fun autoUsesApiCapability() {
        assertEquals(GlassQuality.HIGH, effectiveQuality(GlassQuality.AUTO, 35, false, false))
        assertEquals(GlassQuality.BALANCED, effectiveQuality(GlassQuality.AUTO, 26, false, false))
    }

    @Test
    fun batterySaverAlwaysReducesWork() {
        assertEquals(GlassQuality.LITE, effectiveQuality(GlassQuality.ULTRA, 35, true, false))
    }

    @Test
    fun lowMemoryAutoAvoidsShader() {
        assertEquals(GlassQuality.BALANCED, effectiveQuality(GlassQuality.AUTO, 35, false, true))
    }

    @Test
    fun manualUltraCannotInventGraphicsApi() {
        assertEquals(GlassQuality.BALANCED, effectiveQuality(GlassQuality.ULTRA, 30, false, false))
        assertEquals(GlassQuality.ULTRA, effectiveQuality(GlassQuality.ULTRA, 35, false, false))
    }
}
