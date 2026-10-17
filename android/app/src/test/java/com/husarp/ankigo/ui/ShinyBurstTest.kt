package com.husarp.ankigo.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

class ShinyBurstTest {
    private val cx = 100f
    private val cy = 80f
    private val sc = 2f

    @Test fun nothingBeforeStartOrAfterEnd() {
        assertTrue(ShinyBurst.at(-1f, cx, cy, sc).isEmpty())
        assertTrue(ShinyBurst.at(ShinyBurst.DURATION_MS.toFloat(), cx, cy, sc).isEmpty())
        assertTrue(ShinyBurst.at(1250f, cx, cy, sc).isEmpty())
        assertTrue(ShinyBurst.finished(ShinyBurst.DURATION_MS.toFloat()))
        assertFalse(ShinyBurst.finished(500f))
    }

    @Test fun startIsAFlashOnly() {
        // ring 1 starts at q = 0 (not drawn yet); at t = 0 only the flash star exists, 0.3 x 22 units, fully opaque
        val s = ShinyBurst.at(0f, cx, cy, sc)
        assertEquals(1, s.size)
        assertTrue(s[0].flash)
        assertEquals(0.3f * 22f * sc, s[0].r, 0.001f)
        assertEquals(1f, s[0].alpha, 0.001f)
        assertEquals(cx, s[0].x, 0.001f); assertEquals(cy, s[0].y, 0.001f)
    }

    @Test fun flashGrowsAndFadesOver220ms() {
        val s = ShinyBurst.at(110f, cx, cy, sc).first { it.flash }
        assertEquals((0.5f + 0.3f) * 22f * sc, s.r, 0.001f)
        assertEquals(0.5f, s.alpha, 0.001f)
        assertTrue(ShinyBurst.at(220f, cx, cy, sc).none { it.flash })
    }

    @Test fun ringCountsAndSecondRingWaits() {
        assertEquals(8, ShinyBurst.at(50f, cx, cy, sc).count { !it.flash })
        assertEquals(8, ShinyBurst.at(179f, cx, cy, sc).count { !it.flash })
        assertEquals(14, ShinyBurst.at(300f, cx, cy, sc).count { !it.flash })
        // ring 1 ends at 800, ring 2 at 980
        assertEquals(6, ShinyBurst.at(900f, cx, cy, sc).count { !it.flash })
    }

    @Test fun ringOneReachAndFade() {
        val stars = ShinyBurst.at(400f, cx, cy, sc).filter { !it.flash }.take(8)
        val q = 0.5f
        val reach = 46f * sc * (1f - (1f - q) * (1f - q) * (1f - q))
        // star 0 sits at angle 0: straight right of the centre
        assertEquals(cx + reach, stars[0].x, 0.01f)
        assertEquals(cy, stars[0].y, 0.01f)
        // the second one is at 45 degrees, its vertical radius squashed to 0.85
        assertEquals(cy + Math.sin(Math.PI / 4).toFloat() * reach * 0.85f, stars[1].y, 0.01f)
        assertEquals(1f - q * q, stars[0].alpha, 0.001f)
    }

    @Test fun ringTwoIsOffsetByPointFourRadians() {
        val ring2 = ShinyBurst.at(500f, cx, cy, sc).filter { !it.flash }.drop(8)
        val q = (500f - 180f) / 800f
        val reach = 30f * sc * (1f - (1f - q) * (1f - q) * (1f - q))
        assertEquals(6, ring2.size)
        assertEquals(cx + Math.cos(0.4).toFloat() * reach, ring2[0].x, 0.01f)
        assertEquals(cy + Math.sin(0.4).toFloat() * reach * 0.85f, ring2[0].y, 0.01f)
    }

    @Test fun starSizeShrinksWithTwinkle() {
        for (t in listOf(100f, 400f, 700f)) for (s in ShinyBurst.at(t, cx, cy, sc).filter { !it.flash && it.alpha > 0f }) {
            assertTrue("size ${s.r} at $t", s.r in 0f..(7f * sc + 0.001f))
            assertTrue(s.alpha in 0f..1f)
        }
        // twinkle is 0.6 + 0.4 sin(..) = 0.2..1.0: at q = 0.5 the size is 7 * (1 - 0.3) * [0.2, 1] units
        val r = ShinyBurst.at(400f, cx, cy, sc).first { !it.flash }.r
        assertTrue(r >= 7f * sc * 0.7f * 0.2f - 0.001f && r <= 7f * sc * 0.7f + 0.001f)
    }

    @Test fun coloursCycleWhitePaleYellowPaleBlue() {
        val c = ShinyBurst.at(300f, cx, cy, sc).filter { !it.flash }.take(8).map { it.color }
        assertEquals(listOf(0, 1, 2, 0, 1, 2, 0, 1).map { ShinyBurst.COLORS[it] }, c)
        assertEquals(0xFFFFFFFF.toInt(), ShinyBurst.COLORS[0])
        assertEquals(0xFFFFF1A8.toInt(), ShinyBurst.COLORS[1])
        assertEquals(0xFFCFE9FF.toInt(), ShinyBurst.COLORS[2])
    }

    @Test fun scaleFollowsTheSpriteBox() {
        assertEquals(1f, ShinyBurst.scale(72f, 90f), 0.0001f)
        assertEquals(2f, ShinyBurst.scale(300f, 144f), 0.0001f)
        // a smaller sprite box pulls the whole burst in
        val big = ShinyBurst.at(400f, 0f, 0f, ShinyBurst.scale(200f, 200f)).maxOf { hypot(it.x, it.y) }
        val small = ShinyBurst.at(400f, 0f, 0f, ShinyBurst.scale(100f, 100f)).maxOf { hypot(it.x, it.y) }
        assertEquals(2f, big / small, 0.01f)
    }

    @Test fun centreIs55PercentDown() {
        assertEquals(10f + 55f, ShinyBurst.centreY(10f, 100f), 0.001f)
    }

    @Test fun burstStaysNearTheSprite() {
        // reach is 46 units x 0.85 vertically at most + star size: well inside a box about 3 units wider on each side than the sprite
        val sc = ShinyBurst.scale(72f, 72f)
        for (t in 0..980 step 20) for (s in ShinyBurst.at(t.toFloat(), 0f, 0f, sc)) {
            assertTrue(abs(s.x) <= (46f + 7f) * sc + 0.01f)
            assertTrue(abs(s.y) <= (46f * 0.85f + 7f) * sc + 0.01f)
        }
    }
}
