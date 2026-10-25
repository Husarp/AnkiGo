package com.husarp.ankigo.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** R19-I (PLAN 244B): hit texts above the target's head, falling back onto the Pokémon; the room the letters keep inside their frame. */
class R19IHitTextTest {
    private fun r(l: Int, t: Int, rr: Int, b: Int) = FloatSpot.Rect(l, t, rr, b)
    private val art = r(150, 120, 250, 220)   // a 100 x 100 picture in a 400 x 300 arena

    private fun head(taken: List<FloatSpot.Rect> = emptyList(), obstacles: List<FloatSpot.Rect> = emptyList(), a: FloatSpot.Rect = art, w: Int = 400, h: Int = 300, tvW: Int = 60, tvH: Int = 24, safe: Int = 4) =
        FloatSpot.placeHead(a, w, h, tvW, tvH, 68, taken, obstacles, 2, 8, safe)

    @Test fun theFirstTextSitsJustAboveTheHeadCentred() {
        val s = head()
        assertFalse(s.onPokemon)
        assertEquals(art.t - 8, s.spot.top + 24)           // its bottom is the lift over the head
        assertEquals(200 - 30, s.spot.left)                // centred on the picture
        assertEquals(68, s.spot.rise)
    }

    @Test fun nextTextsPileUpwardWithoutOverlap() {
        val taken = ArrayList<FloatSpot.Rect>()
        var lastTop = Int.MAX_VALUE
        repeat(3) {
            val s = head(taken)
            assertFalse(s.onPokemon)
            val rect = r(s.spot.left, s.spot.top, s.spot.left + 60, s.spot.top + 24)
            assertTrue(taken.none { it.hits(rect) })
            assertTrue("each one higher than the last", rect.t < lastTop)
            assertTrue(rect.b <= art.t)
            lastTop = rect.t
            taken += rect
        }
    }

    @Test fun noRoomAboveFallsBackOntoThePokemonCentred() {
        val shortArt = r(150, 10, 250, 110)      // head 10 px from the arena top: no room for a 24 px text
        val s = head(a = shortArt, h = 150)
        assertTrue(s.onPokemon)
        assertEquals(200 - 30, s.spot.left)
        assertEquals((10 + 110 - 24) / 2, s.spot.top)
    }

    @Test fun theSafeTopCountsAsNoRoom() {
        // 40 px over the head: one text (24 + 8) fits above a 4 px safe top, with a 30 px safe top it does not
        val a = r(150, 40, 250, 140)
        assertFalse(head(a = a, safe = 4).onPokemon)
        assertTrue(head(a = a, safe = 30).onPokemon)
    }

    @Test fun aStackThatGrowsOutOfTheArenaFallsBackForTheLastOne() {
        val a = r(150, 70, 250, 170)
        val taken = ArrayList<FloatSpot.Rect>()
        val kinds = ArrayList<Boolean>()
        repeat(3) {
            val s = head(taken, a = a)
            kinds += s.onPokemon
            taken += r(s.spot.left, s.spot.top, s.spot.left + 60, s.spot.top + 24)
        }
        assertEquals(listOf(false, false, true), kinds)
    }

    @Test fun aSecondTextOnThePokemonDoesNotPrintOverTheFirst() {
        val a = r(150, 10, 250, 110)
        val first = head(a = a, h = 150)
        val firstRect = r(first.spot.left, first.spot.top, first.spot.left + 60, first.spot.top + 24)
        val second = head(listOf(firstRect), a = a, h = 150)
        assertTrue(second.onPokemon)
        assertFalse(r(second.spot.left, second.spot.top, second.spot.left + 60, second.spot.top + 24).hits(firstRect))
        assertEquals(200 - 30, second.spot.left)
    }

    @Test fun onceOneIsOnThePokemonTheRestOfThePileIsToo() {
        val a = r(150, 60, 250, 160)
        val forced = FloatSpot.placeHead(a, 400, 200, 30, 12, 68, emptyList(), emptyList(), 2, 8, 4, forceOn = true)   // would fit above the head
        assertTrue(forced.onPokemon)
        assertFalse(FloatSpot.placeHead(a, 400, 200, 30, 12, 68, emptyList(), emptyList(), 2, 8, 4).onPokemon)
    }

    @Test fun aCardOverTheHeadSlidesItOverThePictureOrFallsBack() {
        val card = r(100, 70, 215, 118)          // covers the left of the spot above the head
        val s = head(obstacles = listOf(card))
        val rect = r(s.spot.left, s.spot.top, s.spot.left + 60, s.spot.top + 24)
        assertFalse(rect.hits(card))
        val mid = s.spot.left + 30
        assertTrue("still over the picture", mid in art.l..art.r)
        val wall = r(0, 0, 400, 118)             // a card all over the space above the head
        val f = head(obstacles = listOf(wall))
        assertTrue(f.onPokemon)
    }

    @Test fun theRiseNeverReachesIntoACardOrTheSafeTop() {
        val a = r(150, 60, 250, 160)
        val s = head(a = a, safe = 4)
        assertTrue(s.spot.top - s.spot.rise >= 4)
        val card = r(0, 0, 400, 20)
        val t = head(a = a, obstacles = listOf(card))
        assertTrue(t.spot.top - t.spot.rise >= 20)
    }

    @Test fun neverBesideItEveryArenaAndEverySide() {
        for (h in listOf(90, 120, 150, 200, 300, 450)) for (cx in listOf(60, 200, 340)) for (n in 1..5) {
            val w = 400
            val a = r(cx - 45, h - 100, cx + 45, h - 12)
            val taken = ArrayList<FloatSpot.Rect>()
            repeat(n) {
                val s = head(taken, a = a, w = w, h = h, tvW = 70, tvH = 26)
                val rect = r(s.spot.left, s.spot.top, s.spot.left + 70, s.spot.top + 26)
                assertTrue("inside: $rect h=$h", rect.l >= 0 && rect.t >= 0 && rect.r <= w && rect.b <= h)
                assertTrue("over the picture ($rect, art $a)", (rect.l + rect.r) / 2 in a.l..a.r)
                assertTrue("above it or on it", s.onPokemon || rect.b <= a.t)
                assertTrue("rise inside", s.spot.top - s.spot.rise >= 0)
                if (!s.onPokemon) assertTrue("no overlap above", taken.none { it.hits(rect) })
                taken += rect
            }
        }
    }

    // ---- the frame

    /** A made-up heavy italic label: [em] = average letter width in ems. */
    private fun label(chars: Int, em: Float, size: Float) = Pair(chars * em * size, 0.72f * size)

    @Test fun burstKeepsRoomForTheLongestTextAtEverySizeAndWidth() {
        for (d in listOf(1.5f, 2f, 2.6f, 3.5f)) for (fontScale in listOf(1f, 1.3f, 2f)) for (em in listOf(0.55f, 0.65f, 0.75f, 0.9f)) {
            val size = 13f * d * fontScale
            for (chars in listOf(9, 16)) {      // "Critical" length and "SUPER EFFECTIVE!"
                val (tw, th) = label(chars, em, size)
                val b = FloatFrame.burst(tw, th, tw + 0.11f * th + size * 2.7f, th + size * 1.3f, d)
                val room = FloatFrame.burstRoom(FloatFrame.skewedInk(tw, th, b.k), b.w, b.h, d)
                assertTrue("room ${room / d} dp at d=$d scale=$fontScale em=$em chars=$chars k=${b.k}", room >= FloatFrame.ROOM_DP * d - 0.01f)
                assertTrue(b.k in 0.7f..1f)
            }
        }
    }

    @Test fun burstFontShrinksOnlySlightlyAndTheFrameStaysUnlessNeeded() {
        val d = 2.6f; val size = 13f * d
        val (tw, th) = label(16, 0.65f, size)
        val oldW = tw + 0.11f * th + size * 2.7f; val oldH = th + size * 1.3f
        val b = FloatFrame.burst(tw, th, oldW, oldH, d)
        assertTrue("some padding was added by a smaller font", b.k < 1f)
        assertTrue("only slightly smaller (>= 70 %)", b.k >= 0.7f)
        assertEquals(oldW, b.w, 0.01f)
    }

    @Test fun theOldBurstDidTouchTheLetters() {
        // the bug: full font in the old frame leaves less than 1.5 dp (the worst case is a long text)
        val d = 2.6f; val size = 13f * d
        val (tw, th) = label(16, 0.7f, size)
        val room = FloatFrame.burstRoom(FloatFrame.skewedInk(tw, th, 1f), tw + 0.11f * th + size * 2.7f, th + size * 1.3f, d)
        assertTrue("room was ${room / d} dp", room < FloatFrame.ROOM_DP * d)
    }

    @Test fun badgePillKeepsRoomForOneTwoThreeChevrons() {
        for (d in listOf(1.5f, 2.6f, 3.5f)) for (stages in 1..3) for (name in listOf("ATK", "SPA", "ACC", "EVA")) {
            val size = 10.5f * d
            val tw = name.length * 0.8f * size; val th = 0.72f * size
            val chevW = size * 0.62f; val chevH = chevW * 0.5f; val step = chevW * 0.625f; val stroke = maxOf(1.6f * d, chevW * 0.3f)
            val total = chevH + (stages - 1) * step
            val h = maxOf(th + size * 0.55f, total + size * 0.6f, FloatFrame.pillMinH(maxOf(th, total + stroke), d))
            val pad = FloatFrame.pillPad(size * 0.6f, h, d) { p ->
                val w = p + chevW + 3f * d + tw + p
                val cx = -w / 2f + p + chevW / 2f
                w to listOf(FloatFrame.Ink(cx, 0f, chevW + stroke, total + stroke), FloatFrame.Ink(cx + chevW / 2f + 3f * d + tw / 2f, 0f, tw, th))
            }
            val w = pad + chevW + 3f * d + tw + pad
            val cx = -w / 2f + pad + chevW / 2f
            val room = FloatFrame.pillRoom(listOf(FloatFrame.Ink(cx, 0f, chevW + stroke, total + stroke), FloatFrame.Ink(cx + chevW / 2f + 3f * d + tw / 2f, 0f, tw, th)), w, h, d)
            assertTrue("room ${room / d} dp d=$d stages=$stages $name", room >= FloatFrame.ROOM_DP * d - 0.01f)
            assertTrue(pad >= size * 0.6f)
        }
    }
}
