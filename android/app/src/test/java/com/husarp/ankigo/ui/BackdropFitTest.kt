package com.husarp.ankigo.ui

import com.husarp.ankigo.ui.BackdropFit.Feet
import com.husarp.ankigo.ui.BackdropFit.Placement
import kotlin.math.hypot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackdropFitTest {
    private val pw = 753
    private val ph = 500

    /** Every arena size we draw in (px at ~2.6 x): the normal arena, the study strip, PiP, floating windows, tablets, odd ones. */
    private val sizes = listOf(
        1080f to 556f, 1080f to 380f, 1080f to 1000f, 630f to 355f, 525f to 525f, 840f to 500f, 630f to 310f, 420f to 420f,
        2100f to 900f, 900f to 2000f, 200f to 200f, 120f to 400f, 2000f to 100f, 1f to 1f,
    )

    private fun feetFor(w: Float, h: Float) = Feet(w * .74f, h * .55f, w * .27f, h * .95f)

    private fun covers(p: Placement, w: Float, h: Float, over: Float = h * BackdropFit.JOLT_SHARE) {
        val eps = 0.001f
        assertTrue("left ${p.left}", p.left <= eps)
        assertTrue("right", p.left + pw * p.scale >= w - eps)
        assertTrue("top ${p.top} over $over", p.top <= -over + eps)
        assertTrue("bottom", p.top + ph * p.scale >= h + over - eps)
    }

    @Test fun theCoverHoldsInEveryArenaSizeWithAndWithoutFeet() {
        for ((w, h) in sizes) {
            covers(BackdropFit.fit(pw, ph, w, h, feetFor(w, h))!!, w, h)
            covers(BackdropFit.fit(pw, ph, w, h, null)!!, w, h)
        }
    }

    @Test fun theCoverHoldsWhateverTheFeetAre() {
        val w = 1080f; val h = 556f
        val odd = listOf(
            Feet(0f, 0f, w, h), Feet(w, h, 0f, 0f), Feet(-300f, -300f, w + 300f, h + 300f), Feet(500f, 500f, 500f, 500f),
            Feet(w, 0f, 0f, h), Feet(10f, 10f, 11f, 11f),
        )
        for (f in odd) covers(BackdropFit.fit(pw, ph, w, h, f)!!, w, h)
    }

    @Test fun aJoltOfSixPercentShowsNoGap() {
        for ((w, h) in sizes) {
            val p = BackdropFit.fit(pw, ph, w, h, feetFor(w, h))!!
            for (j in listOf(-h * 0.06f, h * 0.06f)) {
                assertTrue("top gap, size $w x $h", p.top + j <= 0.001f)
                assertTrue("bottom gap, size $w x $h", p.top + j + ph * p.scale >= h - 0.001f)
            }
        }
    }

    @Test fun theZoomNeverPassesTheLimit() {
        for ((w, h) in sizes) {
            val cover = maxOf(w / pw, (h * (1 + 2 * BackdropFit.JOLT_SHARE)) / ph)
            val p = BackdropFit.fit(pw, ph, w, h, feetFor(w, h))!!
            assertTrue(p.scale >= cover - 1e-4f)
            assertTrue(p.scale <= cover * BackdropFit.MAX_ZOOM + 1e-4f)
        }
    }

    private fun spotAt(p: Placement, fx: Float, fy: Float) = (p.left + fx * pw * p.scale) to (p.top + fy * ph * p.scale)

    @Test fun feetThatMatchTheSpotsLandOnThemExactly() {
        // an arena built from the picture itself at 1.1 x: the spots are where the feet are, so the fit finds that placement
        val s = BackdropFit.SPOTS
        val k = 1.1f
        val w = 600f; val h = 300f
        val ox = -100f; val oy = -150f
        val feet = Feet(ox + s.enemyX * pw * k, oy + s.enemyY * ph * k, ox + s.playerX * pw * k, oy + s.playerY * ph * k)
        val p = BackdropFit.fit(pw, ph, w, h, feet)!!
        covers(p, w, h)
        val (ex, ey) = spotAt(p, s.enemyX, s.enemyY)
        val (px, py) = spotAt(p, s.playerX, s.playerY)
        assertEquals(feet.enemyX, ex, 0.5f); assertEquals(feet.enemyY, ey, 0.5f)
        assertEquals(feet.playerX, px, 0.5f); assertEquals(feet.playerY, py, 0.5f)
        assertEquals(k, p.scale, 0.001f)
    }

    @Test fun theSpotsSitNearerTheFeetThanInAPlainCentredCover() {
        for ((w, h) in sizes) {
            val feet = feetFor(w, h)
            val fit = BackdropFit.fit(pw, ph, w, h, feet)!!
            val centred = BackdropFit.fit(pw, ph, w, h, null)!!
            fun err(p: Placement): Float {
                val s = BackdropFit.SPOTS
                val (ex, ey) = spotAt(p, s.enemyX, s.enemyY)
                val (px, py) = spotAt(p, s.playerX, s.playerY)
                return hypot(ex - feet.enemyX, ey - feet.enemyY) + hypot(px - feet.playerX, py - feet.playerY)
            }
            assertTrue("size $w x $h: ${err(fit)} vs ${err(centred)}", err(fit) <= err(centred) + 0.01f)
        }
    }

    @Test fun inAWideArenaTheSpotsAreNeverFarFromTheFeet() {
        // the arena's shape differs from the picture's, so the two spots cannot both be exact; the miss stays well under a floor oval
        val w = 1080f; val h = 556f
        val feet = Feet(w * .78f, h * .60f, w * .22f, h * .92f)
        val p = BackdropFit.fit(pw, ph, w, h, feet)!!
        val s = BackdropFit.SPOTS
        val (ex, ey) = spotAt(p, s.enemyX, s.enemyY)
        val (px, py) = spotAt(p, s.playerX, s.playerY)
        assertTrue(hypot(ex - feet.enemyX, ey - feet.enemyY) < w * .16f)
        assertTrue(hypot(px - feet.playerX, py - feet.playerY) < w * .16f)
    }

    @Test fun withoutLayoutThePictureJustCoversCentred() {
        val w = 1000f; val h = 400f
        val p = BackdropFit.fit(pw, ph, w, h, null)!!
        val cover = maxOf(w / pw, (h * 1.12f) / ph)
        assertEquals(cover, p.scale, 1e-4f)
        assertEquals((w - pw * cover) / 2f, p.left, 0.01f)
        assertEquals((h - ph * cover) / 2f, p.top, 0.01f)
    }

    @Test fun aTinyArenaStillCoversAndAWideOneCropsTheHeight() {
        val tiny = BackdropFit.fit(pw, ph, 8f, 6f, feetFor(8f, 6f))!!
        covers(tiny, 8f, 6f)
        val wide = BackdropFit.fit(pw, ph, 2000f, 100f, feetFor(2000f, 100f))!!
        assertEquals(2000f / pw, wide.scale, 1e-3f) // the width binds: the picture is much taller than the strip
        assertTrue(ph * wide.scale > 100f * 5)
    }

    @Test fun aTallArenaCropsTheSides() {
        val p = BackdropFit.fit(pw, ph, 300f, 900f, feetFor(300f, 900f))!!
        assertEquals(900f * 1.12f / ph, p.scale, 1e-3f) // the height binds
        assertTrue(pw * p.scale > 300f * 2)
        covers(p, 300f, 900f)
    }

    @Test fun aBeachSizedPictureFitsLikeAnyOther() {
        val w = 1080f; val h = 556f
        val p = BackdropFit.fit(700, 500, w, h, feetFor(w, h))!!
        assertTrue(p.left <= 0.001f && p.left + 700 * p.scale >= w - 0.001f)
        assertTrue(p.top <= -h * BackdropFit.JOLT_SHARE + 0.001f && p.top + 500 * p.scale >= h * (1 + BackdropFit.JOLT_SHARE) - 0.001f)
    }

    @Test fun reversedFeetFallBackToCover() {
        // the wild one left of and below ours: no zoom helps, the picture covers and stays inside
        val w = 1080f; val h = 556f
        val p = BackdropFit.fit(pw, ph, w, h, Feet(w * .2f, h * .95f, w * .8f, h * .5f))!!
        val cover = maxOf(w / pw, (h * 1.12f) / ph)
        assertEquals(cover, p.scale, 1e-4f)
        covers(p, w, h)
    }

    @Test fun nothingToFitGivesNull() {
        assertNull(BackdropFit.fit(0, ph, 100f, 100f, null))
        assertNull(BackdropFit.fit(pw, 0, 100f, 100f, null))
        assertNull(BackdropFit.fit(pw, ph, 0f, 100f, null))
        assertNull(BackdropFit.fit(pw, ph, 100f, 0f, feetFor(100f, 100f)))
        assertNotNull(BackdropFit.fit(pw, ph, 1f, 1f, null))
    }
}
