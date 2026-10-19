package com.husarp.ankigo.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** ROUND 10 B20: the Pokémon size setting ([PokemonSize]): the stored value, and the holders' growth in the plain layouts. */
class PokemonSizeTest {
    private fun box(x: Float, y: Float, w: Float, h: Float) = ArenaGeo.Box(x, y, w, h)

    // ---------------------------------------------------------------- the stored value

    @Test fun theSliderRunsFrom80To130InStepsOfFive() {
        assertEquals(listOf(80, 85, 90, 95, 100, 105, 110, 115, 120, 125, 130), PokemonSize.VALUES)
        assertEquals(100, PokemonSize.DEFAULT)
    }

    @Test fun nothingStoredOrOutOfRangeIs100AndInRangeSnapsToAStep() {
        assertEquals(100, PokemonSize.parse(null))
        assertEquals(100, PokemonSize.parse(0))
        assertEquals(100, PokemonSize.parse(79))
        assertEquals(100, PokemonSize.parse(131))
        assertEquals(100, PokemonSize.parse(-5))
        assertEquals(100, PokemonSize.parse(1000))
        assertEquals(80, PokemonSize.parse(80))
        assertEquals(130, PokemonSize.parse(130))
        assertEquals(95, PokemonSize.parse(97))      // hand-edited values snap to the nearest step
        assertEquals(100, PokemonSize.parse(98))
        assertEquals(115, PokemonSize.parse(115))
    }

    @Test fun theSliderPositionAndTheSizeMapBothWays() {
        for ((i, v) in PokemonSize.VALUES.withIndex()) {
            assertEquals(i, PokemonSize.index(v))
            assertEquals(v, PokemonSize.at(i))
        }
        assertEquals(4, PokemonSize.index(100))
        assertEquals(80, PokemonSize.at(-3))     // kept inside the slider
        assertEquals(130, PokemonSize.at(99))
        assertEquals(1f, PokemonSize.factor(100), 0f)
        assertEquals(1.3f, PokemonSize.factor(130), 0.0001f)
        assertEquals(0.8f, PokemonSize.factor(80), 0.0001f)
        assertEquals("100%", PokemonSize.label(100))
        assertEquals("130%", PokemonSize.label(130))
        assertEquals("100%", PokemonSize.label(999))
    }

    // ---------------------------------------------------------------- the plain layouts' holders

    @Test fun aHolderScalesAroundItsFeet() {
        val b = box(20f, 40f, 110f, 100f)
        val big = PokemonSize.atFeet(b, 1.3f)
        assertEquals(b.bottom, big.bottom, 0.001f)
        assertEquals(b.x + b.w / 2f, big.x + big.w / 2f, 0.001f)
        assertEquals(143f, big.w, 0.001f); assertEquals(130f, big.h, 0.001f)
        val same = PokemonSize.atFeet(b, 1f)
        assertEquals(b.x, same.x, 0.0001f); assertEquals(b.y, same.y, 0.0001f); assertEquals(b.w, same.w, 0.0001f); assertEquals(b.h, same.h, 0.0001f)
        val moved = PokemonSize.atFeet(b, 1f, 7f)
        assertEquals(b.x + 7f, moved.x, 0.0001f)
    }

    @Test fun at100TheHoldersComeBackUnchanged() {
        val w = box(250f, 20f, 110f, 100f)
        val p = box(20f, 300f, 140f, 128f)
        val s = PokemonSize.fitHolders(400f, 450f, w, p, listOf(box(10f, 10f, 100f, 60f)), 1f)
        assertSame(w, s.wild); assertSame(p, s.player)
        assertEquals(1f, s.f, 0f)
        assertFalse(s.changed)
        assertEquals(0f, s.wildDx, 0f); assertEquals(0f, s.playerDx, 0f)
    }

    @Test fun underOneBothHoldersJustGetSmallerAroundTheirFeet() {
        val w = box(250f, 20f, 110f, 100f)
        val p = box(20f, 300f, 140f, 128f)
        val s = PokemonSize.fitHolders(400f, 450f, w, p, emptyList(), 0.8f)
        assertEquals(0.8f, s.f, 0.0001f)
        assertEquals(88f, s.wild.w, 0.001f); assertEquals(80f, s.wild.h, 0.001f); assertEquals(w.bottom, s.wild.bottom, 0.001f)
        assertEquals(112f, s.player.w, 0.001f); assertEquals(p.bottom, s.player.bottom, 0.001f)
        assertTrue(s.changed)
    }

    @Test fun overOneWithRoomGivesTheWishedSize() {
        val w = box(250f, 100f, 110f, 100f)
        val p = box(60f, 300f, 140f, 128f)
        val s = PokemonSize.fitHolders(420f, 600f, w, p, listOf(box(10f, 10f, 100f, 60f), box(250f, 520f, 150f, 70f)), 1.3f)
        assertEquals(1.3f, s.f, 0.0001f)
        assertEquals(143f, s.wild.w, 0.001f)
        assertEquals(w.bottom, s.wild.bottom, 0.001f)
        assertEquals(0f, s.wildDx, 0f); assertEquals(0f, s.playerDx, 0f)
    }

    @Test fun aHolderAtTheEdgeIsMovedInJustFarEnoughToKeepItsArtInsideTheArena() {
        // the art box is 85 % of the holder, bottom-centred; at 130 % a holder on the left edge would stick out
        val p = box(0f, 300f, 140f, 128f)
        val w = box(250f, 100f, 110f, 100f)
        val s = PokemonSize.fitHolders(420f, 600f, w, p, emptyList(), 1.3f)
        assertEquals(1.3f, s.f, 0.0001f)
        assertTrue("moved right (${s.playerDx})", s.playerDx > 0f)
        assertEquals("art starts exactly at the edge", 0f, s.player.art().x, 0.001f)
        assertEquals(0f, s.wildDx, 0f)
        // and on the right edge the other way
        val r = PokemonSize.fitHolders(420f, 600f, box(310f, 100f, 110f, 100f), box(20f, 300f, 140f, 128f), emptyList(), 1.3f)
        assertTrue("moved left (${r.wildDx})", r.wildDx < 0f)
        assertEquals(420f, r.wild.art().right, 0.001f)
    }

    @Test fun aCardNextToASpriteStopsTheGrowthAtTheLastSizeThatStaysClear() {
        val w = box(250f, 100f, 110f, 100f)   // its art box (85 %, bottom-centred) runs from x 258.25 at 100 %, and from 305 - 46.75 f when grown
        val p = box(60f, 300f, 140f, 128f)
        val far = box(248f, 400f, 30f, 30f)
        val free = PokemonSize.fitHolders(420f, 600f, w, p, listOf(far), 1.3f)
        assertEquals("a card far from both sprites changes nothing", 1.3f, free.f, 0.0001f)
        // a wall whose right edge is exactly at the art's left edge: clear at 100 %, any growth meets it
        val flush = box(248.25f, 20f, 10f, 200f)
        assertEquals(1f, PokemonSize.fitHolders(420f, 600f, w, p, listOf(flush), 1.3f).f, 0f)
        // a wall 8 dp further left: growth is fine until the art would reach it (f < 1.176), so the last step is 1.15
        val wall = box(240f, 20f, 10f, 200f)
        val t = PokemonSize.fitHolders(420f, 600f, w, p, listOf(wall), 1.3f)
        assertEquals(1.15f, t.f, 0.0001f)
        assertFalse(wall.hits(t.wild.art()))
        assertEquals("asking for the step above gives the same", 1.15f, PokemonSize.fitHolders(420f, 600f, w, p, listOf(wall), 1.2f).f, 0.0001f)
        assertEquals("a smaller wish is met", 1.1f, PokemonSize.fitHolders(420f, 600f, w, p, listOf(wall), 1.1f).f, 0.0001f)
    }

    @Test fun twoSpritesThatDidNotTouchAreNotLetTouchByGrowth() {
        val p = box(60f, 300f, 140f, 128f)
        val w = box(200f, 250f, 110f, 100f)   // 18.75 dp clear of ours at 100 %; they meet from 1.176
        assertFalse(w.art().hits(p.art()))
        val s = PokemonSize.fitHolders(420f, 600f, w, p, emptyList(), 1.3f)
        assertEquals(1.15f, s.f, 0.0001f)
        assertFalse(s.wild.art().hits(s.player.art()))
    }

    @Test fun twoSpritesThatAlreadyTouchedMayGrowAsLongAsEverythingElseIsClear() {
        val p = box(60f, 300f, 140f, 128f)
        val w = box(180f, 250f, 110f, 100f)   // overlapping at 100 % already
        assertTrue(w.art().hits(p.art()))
        val s = PokemonSize.fitHolders(420f, 600f, w, p, emptyList(), 1.3f)
        assertEquals(1.3f, s.f, 0.0001f)
    }

    @Test fun anArenaTooSmallForTheArtKeepsTheHundredPercentLayout() {
        val s = PokemonSize.fitHolders(100f, 100f, box(0f, 0f, 130f, 100f), box(0f, 10f, 130f, 90f), emptyList(), 1.3f)
        assertEquals(1f, s.f, 0f)
        assertFalse(s.changed)
    }

    @Test fun whatEverIsReturnedOverOneKeepsBothArtBoxesInsideAndClearOfTheCards() {
        var grown = 0
        for (aw in listOf(300f, 412f, 600f)) for (ah in listOf(180f, 300f, 600f, 869f)) for (size in listOf(1.05f, 1.15f, 1.3f)) {
            val a = (ah / 212f).coerceIn(0.66f, 1.5f)
            val wild = box(aw - 42f * a - 110f * a, 12f * a, 110f * a, 100f * a)
            val player = box(22f * a, ah - 6f * a - 128f * a, 140f * a, 128f * a)
            val cards = listOf(box(10f, 10f, 160f, 70f), box(aw - 170f, ah - 82f, 160f, 70f))
            val s = PokemonSize.fitHolders(aw, ah, wild, player, cards, size)
            assertTrue("$aw x $ah: never bigger than wished (${s.f} vs $size)", s.f <= size + 0.0001f && s.f >= 1f)
            if (s.f > 1f) {
                grown++
                for (art in listOf(s.wild.art(), s.player.art())) {
                    assertTrue("$aw x $ah: art inside", art.x >= -0.011f && art.y >= -0.011f && art.right <= aw + 0.011f && art.bottom <= ah + 0.011f)
                    for (c in cards) assertFalse("$aw x $ah: art clear of a card", c.hits(art))
                }
            }
        }
        assertTrue("the grid reaches real growth ($grown)", grown > 0)
    }
}
