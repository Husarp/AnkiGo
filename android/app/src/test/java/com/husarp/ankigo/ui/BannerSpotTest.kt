package com.husarp.ankigo.ui

import com.husarp.ankigo.ui.FloatSpot.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BannerSpotTest {
    private fun box(s: BannerSpot.Spot, bw: Int, bh: Int) = Rect(s.left, s.top, s.left + bw, s.top + bh)
    private fun inside(r: Rect, w: Int, h: Int) = r.l >= 0 && r.t >= 0 && r.r <= w && r.b <= h

    @Test fun roomAboveTheBallPutsItThereCentredOnTheBall() {
        val ball = Rect(250, 150, 298, 198)
        val s = BannerSpot.place(412, 260, 170, 60, ball, emptyList(), 6, 4)
        assertEquals(BannerSpot.Mode.ABOVE_BALL, s.mode)
        assertEquals(274 - 85, s.left)
        assertEquals(150 - 4 - 60, s.top)
    }

    @Test fun bannerNearTheSideIsClampedInside() {
        val ball = Rect(370, 150, 410, 190)
        val s = BannerSpot.place(412, 260, 170, 60, ball, emptyList(), 6, 4)
        assertEquals(BannerSpot.Mode.ABOVE_BALL, s.mode)
        assertEquals(412 - 170 - 6, s.left)
    }

    @Test fun noRoomAboveTheBallCentresIt() {
        val ball = Rect(300, 20, 348, 68)   // ball near the top edge
        val s = BannerSpot.place(412, 260, 170, 60, ball, emptyList(), 6, 4)
        assertEquals(BannerSpot.Mode.CENTRE, s.mode)
        assertEquals((412 - 170) / 2, s.left)
        assertEquals((260 - 60) / 2, s.top)
    }

    @Test fun anObstacleAboveTheBallMakesItCentre() {
        val ball = Rect(300, 150, 348, 198)
        val sprite = Rect(300, 60, 400, 148)   // the banner would land on it
        val s = BannerSpot.place(412, 260, 170, 60, ball, listOf(sprite), 6, 4)
        assertEquals(BannerSpot.Mode.CENTRE, s.mode)
        assertTrue(!sprite.hits(box(s, 170, 60)))
    }

    @Test fun noBallCentres() {
        val s = BannerSpot.place(300, 200, 120, 50, null, emptyList(), 6, 4)
        assertEquals(BannerSpot.Mode.CENTRE, s.mode)
        assertEquals(90, s.left); assertEquals(75, s.top)
    }

    @Test fun centreTakenFindsTheFreeSpotNearestTheCentre() {
        val w = 400; val h = 300
        val mid = Rect(120, 90, 280, 210)   // a sprite in the middle
        val ball = Rect(300, 230, 340, 270)
        val s = BannerSpot.place(w, h, 150, 50, ball, listOf(mid), 6, 4)
        assertEquals(BannerSpot.Mode.FREE, s.mode)
        val b = box(s, 150, 50)
        assertTrue(!mid.hits(b))
        assertTrue(inside(b, w, h))
    }

    @Test fun neverOverlapsSpritesOrCardsWhenFreeSpaceExists() {
        val w = 412; val h = 400
        val obstacles = listOf(
            Rect(10, 10, 150, 90),     // wild card
            Rect(230, 20, 400, 160),   // wild sprite
            Rect(20, 180, 190, 330),   // player sprite
            Rect(230, 300, 400, 390),  // player card
        )
        for (ball in listOf(null, Rect(300, 130, 348, 160), Rect(300, 5, 348, 40))) {
            val s = BannerSpot.place(w, h, 170, 70, ball, obstacles, 6, 4)
            val b = box(s, 170, 70)
            assertTrue("$ball", obstacles.none { it.hits(b) })
            assertTrue(inside(b, w, h))
        }
    }

    @Test fun tinyArenaKeepsTheBannerInsideAfterFit() {
        val w = 240; val h = 136
        val raw = 170 to 140
        val k = BannerSpot.fitScale(w, h, raw.first, raw.second, 6)
        assertTrue(k < 1f)
        val bw = (raw.first * k).toInt(); val bh = (raw.second * k).toInt()
        val obstacles = listOf(Rect(0, 0, 100, 60), Rect(130, 20, 230, 100), Rect(0, 70, 90, 136))
        val s = BannerSpot.place(w, h, bw, bh, Rect(150, 90, 180, 120), obstacles, 6, 4)
        assertTrue(inside(box(s, bw, bh), w, h))
    }

    @Test fun nothingFreeStillStaysInsideAndCoversLeast() {
        val w = 200; val h = 100
        val everything = Rect(0, 0, 200, 100)
        val s = BannerSpot.place(w, h, 120, 40, null, listOf(everything), 6, 4)
        assertTrue(inside(box(s, 120, 40), w, h))
        val small = Rect(0, 0, 200, 70)
        val s2 = BannerSpot.place(w, h, 120, 40, null, listOf(small), 0, 4)
        assertTrue(s2.top >= 60 - 1 || s2.top + 40 <= 100)
    }

    @Test fun bannerBiggerThanTheArenaClampsToTheCorner() {
        val s = BannerSpot.place(100, 50, 160, 80, null, emptyList(), 6, 4)
        assertEquals(0, s.left); assertEquals(0, s.top)
    }

    @Test fun fitScaleIsOneWhenItFitsAndShrinksOtherwise() {
        assertEquals(1f, BannerSpot.fitScale(412, 260, 170, 60, 6), 0f)
        assertEquals((100f - 12) / 176, BannerSpot.fitScale(100, 400, 176, 60, 6), 0.001f)
        assertEquals((50f - 12) / 80, BannerSpot.fitScale(400, 50, 100, 80, 6), 0.001f)
        assertEquals(1f, BannerSpot.fitScale(400, 50, 0, 0, 6), 0f)
    }

    @Test fun crowdedArenaShrinksTheBannerUntilItFitsClear() {
        val w = 240; val h = 100
        // a free pocket only 110 x 50 px big (right of the sprite, above the card)
        val obstacles = listOf(Rect(0, 0, 120, 100), Rect(120, 55, 240, 100))
        val p = BannerSpot.placeFitting(w, h, 170, 80, null, obstacles, 4, 4)
        assertTrue(p.scale < 1f)
        assertTrue(p.spot.clear)
        val b = box(p.spot, (170 * p.scale).toInt(), (80 * p.scale).toInt())
        assertTrue(obstacles.none { it.hits(b) })
        assertTrue(inside(b, w, h))
    }

    @Test fun roomyArenaKeepsFullSize() {
        val p = BannerSpot.placeFitting(412, 260, 170, 60, Rect(250, 150, 298, 198), emptyList(), 6, 4)
        assertEquals(1f, p.scale, 0f)
        assertEquals(BannerSpot.Mode.ABOVE_BALL, p.spot.mode)
    }

    @Test fun nothingFreeEvenSmallUsesTheSmallestSizeAndStaysInside() {
        val p = BannerSpot.placeFitting(200, 100, 170, 80, null, listOf(Rect(0, 0, 200, 100)), 4, 4)
        assertEquals(0.45f, p.scale, 0.0001f)
        assertTrue(!p.spot.clear)
        assertTrue(inside(box(p.spot, (170 * p.scale).toInt(), (80 * p.scale).toInt()), 200, 100))
    }

    /** R4 fix B10: "Gotcha!" never covers one of our Pokémon or a card: no clear spot even at the smallest size = not shown. */
    @Test fun noClearSpotMeansTheBannerIsNotShown() {
        val p = BannerSpot.placeFitting(200, 100, 170, 80, null, listOf(Rect(0, 0, 200, 100)), 4, 4)
        assertTrue(!p.shown)
        // a clear spot at full size, or after shrinking, shows it
        assertTrue(BannerSpot.placeFitting(412, 260, 170, 60, Rect(250, 150, 298, 198), emptyList(), 6, 4).shown)
        val crowded = listOf(Rect(0, 0, 120, 100), Rect(120, 55, 240, 100))
        assertTrue(BannerSpot.placeFitting(240, 100, 170, 80, null, crowded, 4, 4).shown)
    }

    @Test fun keepsShrinkingBelowOldFloorBeforeGivingUp() {
        // the only free pocket is 120 x 28 px (right of the sprite, above the card): a 120 x 60 banner needs 0.47 of its size at most
        val w = 240; val h = 100
        val obstacles = listOf(Rect(0, 0, 120, 100), Rect(120, 28, 240, 100))
        val p = BannerSpot.placeFitting(w, h, 120, 60, null, obstacles, 0, 0)
        assertTrue("fits at ${p.scale}", p.shown)
        assertTrue("needed less than the old 0.6 floor", p.scale < 0.6f && p.scale >= 0.45f)
        val b = box(p.spot, (120 * p.scale).toInt(), (60 * p.scale).toInt())
        assertTrue(obstacles.none { it.hits(b) })
        assertTrue(inside(b, w, h))
    }

    @Test fun anArenaTooCrowdedEvenForTheSmallestBannerSkipsIt() {
        // the only pocket is 40 px wide: 0.45 of a 120 x 60 banner is 54 x 27
        val obstacles = listOf(Rect(0, 0, 120, 100), Rect(120, 70, 240, 100), Rect(160, 0, 240, 70))
        val p = BannerSpot.placeFitting(240, 100, 120, 60, null, obstacles, 0, 0)
        assertTrue(!p.shown)
    }
}
