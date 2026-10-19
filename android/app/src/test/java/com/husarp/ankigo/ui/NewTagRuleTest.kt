package com.husarp.ankigo.ui

import com.husarp.ankigo.ui.NewTagRule.Box
import com.husarp.ankigo.ui.NewTagRule.Corner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

/** Round 10 wave 2 (G): the NEW sticker's corner rule. Units are px at density 1 (a dp is a px here). */
class NewTagRuleTest {
    // the sticker: 8 sp "NEW" is about 22 x 12 px unscaled
    private val tw = 22f
    private val th = 12f

    private fun place(box: Box, w: Float, h: Float, k: Float = 1f, ks: Float = k, edge: Float = 0f, avoid: Box? = null) =
        NewTagRule.place(box, w, h, edge, 4f * k, 3f * k, tw, th, ks, avoid)

    private fun inside(b: Box, w: Float, h: Float) = b.l >= -0.01f && b.t >= -0.01f && b.r <= w + 0.01f && b.b <= h + 0.01f

    // ---- geometry ----

    @Test fun anUnturnedStickerIsItsScaledRectangle() {
        val b = NewTagRule.turned(10f, 20f, 22f, 12f, 2f, 0f)
        assertEquals(10f, b.l, 0.001f); assertEquals(20f, b.t, 0.001f); assertEquals(54f, b.r, 0.001f); assertEquals(44f, b.b, 0.001f)
    }

    @Test fun aStickerTurnedMinus20RisesToTheRight() {
        val b = NewTagRule.turned(0f, 0f, 22f, 12f, 1f, -20f)
        // the right end rises by 22 sin 20 = 7.52 above the pivot; the left edge is the pivot (the lower-left corner swings right)
        assertEquals(-22f * Math.sin(Math.toRadians(20.0)).toFloat(), b.t, 0.01f)
        assertEquals(0f, b.l, 0.01f)
        assertEquals(22f * Math.cos(Math.toRadians(20.0)).toFloat() + 12f * Math.sin(Math.toRadians(20.0)).toFloat(), b.r, 0.01f)
    }

    @Test fun plusTwentyHangsDownAndLeftOfThePivotButNeverAboveIt() {
        val b = NewTagRule.turned(50f, 50f, 22f, 12f, 1f, 20f)
        assertEquals(50f, b.t, 0.01f)
        assertTrue(b.l < 50f)
    }

    @Test fun tiltsMirrorWithTheCorner() {
        assertEquals(-20f, NewTagRule.angle(Corner.TOP_LEFT), 0f)
        assertEquals(20f, NewTagRule.angle(Corner.TOP_RIGHT), 0f)
        assertEquals(-20f, NewTagRule.angle(Corner.BOTTOM_RIGHT), 0f)
        assertEquals(20f, NewTagRule.angle(Corner.BOTTOM_LEFT), 0f)
    }

    // ---- the rule ----

    @Test fun aBoxWithRoomAboveKeepsTodaysCorner() {
        val s = place(Box(20f, 60f, 170f, 114f), 412f, 640f)
        assertEquals(Corner.TOP_LEFT, s.corner)
        assertTrue(s.fits)
        assertEquals(-20f, s.angle, 0f)
        assertEquals(16f, s.x, 0.001f); assertEquals(57f, s.y, 0.001f)   // 4 / 3 px over the corner, exactly today's margins
    }

    @Test fun todaysSpotIsKeptInsideTheFrameEdge() {
        val s = place(Box(6f, 40f, 160f, 94f), 412f, 300f, edge = 6f)
        assertEquals(Corner.TOP_LEFT, s.corner)
        assertEquals(6f, s.x, 0.001f)   // 6 - 4 = 2 < the frame's 6
    }

    @Test fun aBoxNearTheTopWouldBeCutSoTheStickerGoesToTheTopRight() {
        // box top 8: today's pivot y = 5, the right end rises 7.5 above that -> y < 0
        val box = Box(10f, 8f, 160f, 62f)
        val today = NewTagRule.turned(6f, 5f, tw, th, 1f, -20f)
        assertTrue("today's sticker is cut (${today.t})", today.t < 0f)
        val s = place(box, 412f, 640f)
        assertEquals(Corner.TOP_RIGHT, s.corner)
        assertTrue(s.fits)
        assertEquals(20f, s.angle, 0f)
        assertTrue(inside(s.bounds, 412f, 640f))
        assertEquals(box.r + 4f, s.bounds.r, 0.01f)   // overhangs the right edge by 4
        assertEquals(box.t - 3f, s.bounds.t, 0.01f)
    }

    @Test fun aBoxOnTheTopAndRightEdgeGoesToTheBottomRight() {
        // box flush with the top: both top corners would be cut (box top 2, overhang 3)
        val box = Box(250f, 2f, 400f, 56f)
        val s = place(box, 412f, 640f)
        assertEquals(Corner.BOTTOM_RIGHT, s.corner)
        assertEquals(-20f, s.angle, 0f)
        assertTrue(s.fits)
        assertEquals(box.b + 3f, s.bounds.b, 0.01f)
    }

    @Test fun theHpNumbersAreNeverCovered() {
        val box = Box(250f, 2f, 400f, 56f)
        val hp = Box(340f, 28f, 396f, 42f)   // the number at the bottom right of the card
        val s = place(box, 412f, 640f, avoid = hp)
        assertEquals(Corner.BOTTOM_LEFT, s.corner)   // bottom right would touch the number
        assertTrue(s.fits)
        assertFalse(s.bounds.hits(hp))
    }

    @Test fun anHpBarInsideCoveringTheWholeBottomRulesOutBothBottomCorners() {
        val box = Box(10f, 2f, 160f, 56f)
        val bar = Box(10f, 30f, 160f, 50f)   // the number is drawn inside the bar
        val s = place(box, 412f, 640f, avoid = bar)
        // top corners are cut by the panel's top (box top 2), bottom corners hit the bar -> nothing fits
        assertFalse(s.fits)
        assertTrue("pushed back into the panel: ${s.bounds}", inside(s.bounds, 412f, 640f))
    }

    @Test fun whenNothingFitsTheLeastBadOneIsUsedAndPushedInside() {
        val s = place(Box(0f, 0f, 20f, 10f), 20f, 10f)
        assertFalse(s.fits)   // the sticker (22 x 12 turned) is bigger than the panel: no push, no crash
    }

    @Test fun aTopLeftThatWasPushedInsideIsNotTodaysSpot() {
        // the review case: a 412 x 146 panel, the wild box (262,8)-(412,62), a 22 x 12 sticker, the HP numbers (270,36)-(404,56)
        val box = Box(262f, 8f, 412f, 62f)
        val hp = Box(270f, 36f, 404f, 56f)
        val s = place(box, 412f, 146f, avoid = hp)
        assertTrue("the corrected spot is inside the panel: ${s.bounds}", inside(s.bounds, 412f, 146f))
        assertEquals(Corner.TOP_LEFT, s.corner)   // no corner is free; the least bad one is today's
        assertFalse(s.fits)
        assertFalse("a pushed top-left is applied, not skipped", NewTagRule.keepsTodaysSpot(s))
        // today's spot (258, 5) would have a turned top of 5 - 7.5 = -2.5: the pushed one is lower
        assertTrue("pushed down from 5: y=${s.y}", s.y > 5f)
    }

    @Test fun todaysSpotThatFitsIsKept() {
        val s = place(Box(20f, 70f, 260f, 156f), 412f, 640f)
        assertEquals(Corner.TOP_LEFT, s.corner)
        assertTrue(s.fits)
        assertTrue(NewTagRule.keepsTodaysSpot(s))
    }

    @Test fun aBoxOnTheLeftEdgeKeepsTheTopLeftWhenItHasRoomAbove() {
        val s = place(Box(0f, 40f, 150f, 94f), 412f, 640f)
        assertEquals(Corner.TOP_LEFT, s.corner)
        assertEquals(0f, s.x, 0.001f)   // clamped to the panel edge like today
    }

    @Test fun aBigBoxScaleMovesTheOverhangAndTheStickerWithIt() {
        val k = 1.6f
        val s = place(Box(20f, 70f, 260f, 156f), 412f, 640f, k = k)
        assertEquals(Corner.TOP_LEFT, s.corner)
        assertEquals(20f - 4f * k, s.x, 0.001f); assertEquals(70f - 3f * k, s.y, 0.001f)
        assertTrue(inside(s.bounds, 412f, 640f))
    }

    @Test fun theTextFloorKeepsTheStickerBiggerThanASmallBox() {
        // box scale .6 but the sticker drawn at 1.0 (the 8 sp floor): the same rule, a bigger sticker
        val box = Box(20f, 14f, 110f, 46f)
        val s = place(box, 220f, 120f, k = 0.6f, ks = 1f)
        assertTrue(s.fits)
        assertEquals(1f, s.bounds.w / NewTagRule.turned(0f, 0f, tw, th, 1f, s.angle).w, 0.001f)
    }

    // ---- the nine panel shapes (the design's F2 page): wild box where the layouts put it, sizes in dp ----

    private class Shape(val name: String, val w: Float, val h: Float, val box: Box, val k: Float, val expect: Corner, val hp: Box? = null)

    private val shapes = listOf(
        // box rects: the wild card's top-left at the usual place, 150 dp wide, 54 dp tall (number above), scaled by k
        Shape("battle tab", 412f, 640f, Box(12f, 8f, 162f, 62f), 1f, Corner.TOP_RIGHT),
        Shape("study tall", 412f, 265f, Box(10f, 10f, 160f, 64f), 1f, Corner.TOP_RIGHT),
        Shape("study short", 412f, 220f, Box(10f, 10f, 160f, 64f), 1f, Corner.TOP_RIGHT),
        Shape("strip", 412f, 146f, Box(8f, 8f, 158f, 62f), 1f, Corner.TOP_RIGHT),
        Shape("landscape study", 869f, 412f, Box(10f, 10f, 160f, 64f), 1f, Corner.TOP_RIGHT),
        Shape("landscape battle", 869f, 412f, Box(24f, 40f, 204f, 105f), 1.2f, Corner.TOP_LEFT),
        Shape("split", 412f, 400f, Box(300f, 70f, 400f, 106f), 0.67f, Corner.TOP_LEFT),
        Shape("float", 320f, 300f, Box(8f, 6f, 158f, 60f), 1f, Corner.TOP_RIGHT),
        Shape("pip", 240f, 135f, Box(6f, 5f, 96f, 37f), 0.6f, Corner.TOP_RIGHT),
    )

    @Test fun theNineShapesPickTheirCornerAndAlwaysFit() {
        for (s in shapes) {
            val spot = NewTagRule.place(s.box, s.w, s.h, 0f, 4f * s.k, 3f * s.k, tw, th, maxOf(s.k, 1f), s.hp)
            assertTrue("${s.name}: fits", spot.fits)
            assertTrue("${s.name}: inside the panel ${spot.bounds}", inside(spot.bounds, s.w, s.h))
            assertEquals("${s.name}: corner", s.expect, spot.corner)
        }
    }

    // ---- invariants over many random boxes ----

    @Test fun whateverFitsIsInsideAndClearAndTodaysSpotWinsWhenItFits() {
        val rnd = Random(7)
        var moved = 0
        repeat(2000) {
            val w = 200f + rnd.nextInt(700); val h = 120f + rnd.nextInt(700)
            val k = 0.5f + rnd.nextFloat() * 1.3f
            val bw = 80f + rnd.nextInt(150); val bh = 30f + rnd.nextInt(70)
            val l = rnd.nextInt((w - bw).toInt().coerceAtLeast(1)).toFloat(); val t = rnd.nextInt((h - bh).toInt().coerceAtLeast(1)).toFloat()
            val box = Box(l, t, l + bw, t + bh)
            val hp = if (rnd.nextBoolean()) Box(box.r - 50f, box.b - 28f, box.r - 6f, box.b - 8f) else null
            val ks = maxOf(k, 1f)
            val s = NewTagRule.place(box, w, h, 0f, 4f * k, 3f * k, tw, th, ks, hp)
            if (s.fits) {
                assertTrue("inside $box in $w x $h: ${s.bounds}", inside(s.bounds, w, h))
                if (hp != null) assertFalse("clear of the numbers", s.bounds.hits(hp))
            }
            val today = NewTagRule.turned(maxOf(0f, l - 4f * k), maxOf(0f, t - 3f * k), tw, th, ks, -20f)
            val todayFits = inside(today, w, h) && (hp == null || !today.hits(hp))
            if (todayFits) assertEquals("today's corner is kept", Corner.TOP_LEFT, s.corner) else { moved++; if (s.fits) assertNotEquals(Corner.TOP_LEFT, s.corner) }
        }
        assertTrue("some boxes did move ($moved)", moved > 50)
    }
}
