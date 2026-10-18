package com.husarp.ankigo.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The dark theme's dusk tint of a battle background (R8-03: the night look is gone; which picture a Pokémon gets is [BackdropCatalogTest]). */
class BackdropTintTest {
    @Test fun dayPicturesAreDrawnAsTheyAreInTheLightTheme() {
        assertNull(BackdropTint.matrix(dark = false))
        assertTrue(BackdropTint.scale(dark = false).all { it == 1f })
    }

    @Test fun theDarkThemeGetsTheMockupsDuskMultiply() {
        // multiply rgba(34,38,96,.42): 1 - .42 + .42 * c / 255
        val s = BackdropTint.scale(dark = true)
        assertEquals(1f - .42f + .42f * 34f / 255f, s[0], 0.005f)
        assertEquals(1f - .42f + .42f * 38f / 255f, s[1], 0.005f)
        assertEquals(1f - .42f + .42f * 96f / 255f, s[2], 0.005f)
    }

    @Test fun duskIsDarkerThanDayAndKeepsMoreBlue() {
        val s = BackdropTint.scale(dark = true)
        for (i in 0..2) assertTrue("channel $i", s[i] < 1f)
        assertTrue(s[2] > s[0] && s[2] > s[1])
    }

    @Test fun aNightPictureGetsNoDuskInTheDarkTheme() {
        // the arena asks for `dark = darkTheme && !bg.dark`: a picture that is a night scene already stays as it is in both themes
        for (bg in BackdropCatalog.ALL.filter { it.dark }) assertNull(bg.id, BackdropTint.matrix(dark = !bg.dark))
        for (bg in BackdropCatalog.ALL.filter { !it.dark }) assertTrue(bg.id, BackdropTint.matrix(dark = !bg.dark) != null)
        assertEquals(10, BackdropCatalog.ALL.count { it.dark })
    }

    @Test fun theMatrixKeepsAlphaAndHasNoOffset() {
        val m = BackdropTint.matrix(dark = true)!!
        assertEquals(20, m.size)
        assertEquals(1f, m[18], 0f)
        for (i in listOf(1, 2, 3, 4, 5, 7, 8, 9, 10, 11, 13, 14, 15, 16, 17, 19)) assertEquals(0f, m[i], 0f)
        assertEquals(BackdropTint.scale(true)[0], m[0], 0f)
        assertEquals(BackdropTint.scale(true)[1], m[6], 0f)
        assertEquals(BackdropTint.scale(true)[2], m[12], 0f)
    }
}
