package com.husarp.ankigo.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** PLAN 215: the list size rule (pure), the stored step, the preview's Pokémon. */
class ListSpriteSizeTest {
    private val rooms = listOf(60 to 60, 70 to 56, 96 to 80, 140 to 112, 200 to 160)

    private fun scale(w: Int, h: Int, cw: Int, ch: Int, step: Int, canvas: Int = 96) = ListSpriteSize.scale(w, h, canvas, canvas, cw, ch, step)

    @Test fun fiveNamedStepsDefaultMedium() {
        assertEquals(listOf("Smallest", "Small", "Medium", "Large", "Largest"), ListSpriteSize.NAMES)
        assertEquals(listOf(1f, 1.25f, 1.5f, 1.75f, 2f), ListSpriteSize.ZOOM.toList())
        assertEquals("Medium", ListSpriteSize.name(ListSpriteSize.DEFAULT))
    }

    @Test fun parseKeepsStepsAndDefaultsTheRest() {
        for (i in 0..4) assertEquals(i, ListSpriteSize.parse(i))
        for (bad in listOf(-1, 5, 99, Int.MIN_VALUE)) assertEquals(ListSpriteSize.DEFAULT, ListSpriteSize.parse(bad))
    }

    @Test fun stepZeroIsExactlyToday() {
        for ((w, h) in rooms) for ((cw, ch) in listOf(30 to 40, 56 to 56, 96 to 96))
            assertEquals("$w x $h", GridMath.tileScale(w, h, 96, 96), scale(w, h, cw, ch, 0), 0f)
    }

    @Test fun growsWithTheStepUntilTheCap() {
        // a small Pokémon in a big room: every step is bigger than the one before
        val s = (0..4).map { scale(200, 160, 30, 40, it) }
        for (i in 1..4) assertTrue("$s", s[i] > s[i - 1])
        // a room where the opaque part fills the room at some step: from then on it stays (never shrinks)
        val m = (0..4).map { scale(70, 56, 56, 56, it) }
        for (i in 1..4) assertTrue("$m", m[i] >= m[i - 1])
    }

    @Test fun neverOverTheRoomForTheOpaquePicture() {
        for ((w, h) in rooms) for ((cw, ch) in listOf(10 to 10, 30 to 40, 56 to 56, 90 to 60, 96 to 96)) for (step in 0..4) {
            val s = scale(w, h, cw, ch, step)
            assertTrue("$w x $h content $cw x $ch step $step: scale $s", cw * s <= w + 0.001f && ch * s <= h + 0.001f)
        }
    }

    @Test fun aCanvasFillingPokemonNeverGrowsPastTheRoom() {
        for ((w, h) in rooms) {
            val base = GridMath.tileScale(w, h, 96, 96)
            val fit = minOf(w / 96f, h / 96f)
            for (step in 0..4) {
                val s = scale(w, h, 96, 96, step)
                assertTrue("$w x $h step $step: $s", s >= base - 0.0001f && s <= fit + 0.0001f)
            }
        }
    }

    @Test fun snapsToAWholeNumberWithinSixPercent() {
        // room 140 x 112, canvas 96 (base 1.1667), content 20 x 20: t = base * zoom
        val c = 20
        val base = GridMath.tileScale(140, 112, 96, 96)
        for (step in 1..4) {
            val t = base * ListSpriteSize.ZOOM[step]
            val n = Math.round(t)
            val got = scale(140, 112, c, c, step)
            if (n >= 1 && Math.abs(n - t) <= 0.06f * t) assertEquals("step $step", n.toFloat(), got, 0f) else assertEquals("step $step", t, got, 0.0001f)
        }
        // 1.5 * 2 = 3.0 exactly when the base is 2: a whole number stays whole
        assertEquals(2f, GridMath.tileScale(192, 192, 96, 96), 0f)
        assertEquals(3f, scale(192, 192, 20, 20, 2), 0f) // Medium = 1.5 x 2
        assertEquals(4f, scale(192, 192, 20, 20, 4), 0f) // Largest = 2 x 2
        assertEquals(2.5f, scale(192, 192, 20, 20, 1), 0f) // Small = 1.25 x 2, not within 6 % of a whole number
        // room 182 (base 1.896, not whole): Medium 2.844 is 5.5 % from 3 -> 3; Large 3.318 is 9.6 % from 3 -> stays
        assertEquals(3f, ListSpriteSize.scale(182, 182, 96, 96, 20, 20, 2), 0f)
        assertEquals(1.75f * 182 / 96f, ListSpriteSize.scale(182, 182, 96, 96, 20, 20, 3), 0.001f)
    }

    @Test fun aWholeNumberThatDoesNotFitIsNotUsed() {
        // content 56 in a room of 100: fitContent = 1.786; t = min(1 * 2, 1.786) = 1.786 -> round 2 > fitContent, stays 1.786
        val s = ListSpriteSize.scale(100, 100, 96, 96, 56, 56, 4)
        assertEquals(100f / 56f, s, 0.0001f)
        assertFalse(s == 2f)
    }

    @Test fun degenerateInputsFallBackToToday() {
        assertEquals(1f, ListSpriteSize.scale(0, 50, 96, 96, 30, 30, 3), 0f)
        assertEquals(GridMath.tileScale(100, 80, 96, 96), ListSpriteSize.scale(100, 80, 96, 96, 0, 0, 3), 0f)
    }

    @Test fun outOfRangeStepIsTheDefault() {
        assertEquals(ListSpriteSize.scale(140, 112, 96, 96, 30, 30, ListSpriteSize.DEFAULT), ListSpriteSize.scale(140, 112, 96, 96, 30, 30, 17), 0f)
    }

    @Test fun previewShowsTheFirstCaughtSpeciesThenTheDefaults() {
        assertEquals(listOf(25, 1, 4, 6), ListSizePreview.pick(emptyList()).map { it.first })
        assertEquals(listOf(7, 25, 1, 4), ListSizePreview.pick(listOf(7 to false, 7 to true)).map { it.first })
        assertEquals(listOf(7 to true, 9 to false, 11 to false, 13 to false), ListSizePreview.pick(listOf(7 to true, 9 to false, 7 to false, 11 to false, 13 to false, 15 to false)))
        assertEquals(listOf(1, 25, 4, 6), ListSizePreview.pick(listOf(1 to false, 25 to false)).map { it.first })
    }
}
