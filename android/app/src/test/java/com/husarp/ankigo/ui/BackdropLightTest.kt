package com.husarp.ankigo.ui

import org.junit.Assert.*
import org.junit.Test

class BackdropLightTest {
    @Test fun darkDimsProportionallyButNeverMoreThanTwentyPercent() {
        assertEquals(.2f, BackdropLight.dim(0f), .00001f)
        assertEquals(.1f, BackdropLight.dim(.225f), .00001f)
        assertEquals(.2f, BackdropLight.dim(-1f), .00001f)
        for (i in 0..100) assertTrue(BackdropLight.dim(i / 100f) in 0f.. .2f)
    }
    @Test fun normalBrightAndPlainHaveNoDim() {
        for (v in listOf(.45f, .6f, 1f)) assertEquals(0f, BackdropLight.dim(v), 0f)
        assertEquals(0f, BackdropLight.forScene(null), 0f)
        assertEquals(0f, BackdropLight.forScene("unknown"), 0f)
    }
    @Test fun everyFinalSceneHasOneMeasurement() {
        assertEquals(BackdropCatalog.ALL.map { it.id }.toSet(), BackdropLight.luminance.keys)
        assertTrue(BackdropLight.forScene("dark-forest") > .15f)
        assertEquals(0f, BackdropLight.forScene("normal-meadow"), 0f)
    }
    @Test fun crossfadeAndInterruptedCrossfadeStayContinuous() {
        assertEquals(.1f, BackdropLight.blend(0f, .2f, .5f), .00001f)
        val interrupted = BackdropLight.blend(0f, .2f, .4f)
        assertEquals(interrupted, BackdropLight.blend(interrupted, 0f, 0f), 0f)
        assertEquals(0f, BackdropLight.blend(interrupted, 0f, 1f), 0f)
    }
}
