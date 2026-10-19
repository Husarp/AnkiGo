package com.husarp.ankigo.ui

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.min

/**
 * ROUND 10 B20: the Pokémon size in the info boxes' rule ([StageGeo.plan] with `sizeScale`). 100 % is exactly today's plan; 130 % keeps the
 * sprites' real art inside the arena and the boxes clear of it (or the rule's own least-bad path); 80 % is that share of the 100 % frame.
 * The existing [StageGeoTest] (parity with the mockup's rule, 2322 combinations) is untouched and runs the default size.
 */
class StageGeoSizeTest {
    private val fullArt: (Float) -> ArtShare? = { ArtShare(0f, 0f, 1f, 1f) }
    private val tight: (Float) -> ArtShare? = { ArtShare(0.13f, 0.18f, 0.66f, 0.62f) }

    private fun fit(id: String, w: Float, h: Float) = StageFit.fit(BackdropCatalog.byId(id)!!, w, h, 1f)!!

    private fun same(tag: String, a: StagePlan, b: StagePlan) {
        assertEquals("$tag clear", a.clear, b.clear)
        assertEquals("$tag f", a.spriteScale, b.spriteScale, 0f)
        assertEquals("$tag k", a.k, b.k, 0f)
        assertEquals("$tag reduced", a.reduced, b.reduced)
        assertEquals("$tag level", a.level, b.level)
        for ((x, y) in listOf(a.wildCard to b.wildCard, a.playerCard to b.playerCard, a.wildSprite to b.wildSprite, a.playerSprite to b.playerSprite)) {
            assertEquals("$tag x", x.x, y.x, 0f); assertEquals("$tag y", x.y, y.y, 0f); assertEquals("$tag w", x.w, y.w, 0f); assertEquals("$tag h", x.h, y.h, 0f)
        }
    }

    @Test fun theFrameScalesAt100AreTodaysAndAreTheSameSteps() {
        assertArrayEquals(floatArrayOf(1f, 0.9f, 0.8f, 0.7f, 0.6f), StageGeo.frameScales(1f), 0f)
        assertArrayEquals(floatArrayOf(1f, 0.9f, 0.8f, 0.7f, 0.6f), StageGeo.frameScales(1.00001f), 0f)
    }

    @Test fun aBigSizeIsSizeTimesTheStepsContinuedDownToTheUsualWorstCase() {
        val c = StageGeo.frameScales(1.3f)
        assertArrayEquals(floatArrayOf(1.3f, 1.17f, 1.04f, 0.91f, 0.78f, 0.65f, 0.52f), c, 0.0001f)
        for (i in 1 until c.size) assertTrue("descending at $i", c[i] < c[i - 1])
        assertTrue("the last step is no bigger than the usual worst case", c.last() <= 0.6f)
        val d = StageGeo.frameScales(1.05f)
        assertEquals(1.05f, d.first(), 0.0001f)
        assertEquals(0.525f, d.last(), 0.0001f)
        assertEquals(6, d.size)
        // the biggest size continues until under .6 as well
        assertTrue(StageGeo.frameScales(1.3f).last() <= 0.6f && StageGeo.frameScales(1.15f).last() <= 0.6f)
    }

    @Test fun aSmallSizeIsThatShareOfTheFrameAt100() {
        assertArrayEquals(floatArrayOf(0.8f, 0.72f, 0.64f, 0.56f, 0.48f), StageGeo.frameScales(0.8f), 0.0001f)
        assertArrayEquals(floatArrayOf(0.48f, 0.432f, 0.384f, 0.336f, 0.288f), StageGeo.frameScales(0.8f, 0.6f), 0.0001f)
    }

    @Test fun explicitHundredPercentIsTheSamePlanAsTheDefaultOverAWideGrid() {
        var n = 0
        for (b in BackdropCatalog.ALL) for ((w, h, s) in listOf(Triple(412f, 640f, 1f), Triple(412f, 265f, 1f), Triple(412f, 134f, 0.634f), Triple(412f, 146f, 1f),
            Triple(365f, 266f, 1f), Triple(860f, 253f, 1f), Triple(206f, 136f, 1f), Triple(240f, 130f, 0.61f))) {
            val vw = w / s; val vh = h / s
            val floor = Math.ceil((8f / s * 10f).toDouble()).toFloat() / 10f
            val st = fit(b.id, vw, vh)
            val k0 = if (s < 1f || h <= 400f) 1f else min(vw / 300f, vh / 430f).coerceIn(1f, 1.4f)
            for ((wa, pa) in listOf(fullArt to fullArt, tight to fullArt, null to null)) for (inset in listOf(0f, 6f)) {
                val a = StageGeo.plan(vw, vh, st, 150f, 64f, 156f, 70f, k0, s, floor, inset, wa, pa, 56f, 58f, 10.5f, 8.5f)
                val c = StageGeo.plan(vw, vh, st, 150f, 64f, 156f, 70f, k0, s, floor, inset, wa, pa, 56f, 58f, 10.5f, 8.5f, 1f)
                same("${b.id} ${w}x$h", a, c)
                n++
            }
        }
        assertTrue(n > 300)
    }

    @Test fun theSizeMovesTheFramesTheWayTheSettingSays() {
        var grew = 0
        var cameBackDown = 0
        var smaller = 0
        var kept = 0
        var droppedToOne = 0
        var n = 0
        for (b in BackdropCatalog.ALL) for ((w, h, s) in listOf(Triple(412f, 640f, 1f), Triple(412f, 869f, 1f), Triple(412f, 265f, 1f), Triple(412f, 134f, 0.634f), Triple(412f, 146f, 1f),
            Triple(365f, 266f, 1f), Triple(860f, 253f, 1f), Triple(206f, 136f, 1f), Triple(240f, 130f, 0.61f), Triple(300f, 280f, 1f))) {
            val vw = w / s; val vh = h / s
            val floor = Math.ceil((8f / s * 10f).toDouble()).toFloat() / 10f
            val st = fit(b.id, vw, vh)
            val k0 = if (s < 1f || h <= 400f) 1f else min(vw / 300f, vh / 430f).coerceIn(1f, 1.4f)
            for ((wa, pa) in listOf(fullArt to fullArt, tight to tight)) for (inset in listOf(0f, 6f)) {
                fun at(size: Float, atOne: Float = 0f) = StageGeo.plan(vw, vh, st, 150f, 64f, 156f, 70f, k0, s, floor, inset, wa, pa, 56f, 58f, 10.5f, 8.5f, size, atOne)
                val p100 = at(1f); val p130 = at(1.3f); val p80 = at(0.8f)
                val tag = "${b.id} ${w}x$h s=$s inset=$inset"
                // 80 % is that share of the 100 % frame (so never bigger than it); giving the 100 % scale in is the same as working it out
                assertTrue("$tag: 80 % frame ${p80.spriteScale} vs ${p100.spriteScale}", p80.spriteScale <= p100.spriteScale * 0.8f + 0.0001f)
                assertEquals("$tag: atOne given", p80.spriteScale, at(0.8f, p100.spriteScale).spriteScale, 0f)
                assertTrue("$tag: 80 % frames are smaller", p80.playerSprite.w < p100.playerSprite.w && p80.wildSprite.w < p100.wildSprite.w)
                if (p130.spriteScale > 1.0001f) {
                    grew++
                    // a frame over the stage's own keeps its real art inside the arena (inset by the battle frame) and the two apart
                    for ((name, a) in listOf("wild" to p130.wildArt, "ours" to p130.playerArt)) {
                        assertTrue("$tag: $name art inside ($a)", a.x >= inset - 0.011f && a.y >= inset - 0.011f && a.right <= vw - inset + 0.011f && a.bottom <= vh - inset + 0.011f)
                    }
                    assertFalse("$tag: the two art boxes touch", p130.wildArt.hits(p130.playerArt))
                }
                if (p130.spriteScale < 1.3f - 0.0001f) cameBackDown++
                if (p80.spriteScale < p100.spriteScale) smaller++
                // the rule that decides between the bigger size and 100 % (the arena applies it): a kept one is never a lower step or less clear
                if (StageGeo.keepSized(1.3f, p100, p130)) {
                    kept++
                    assertTrue("$tag: kept, but its step ${p130.spriteScale / 1.3f} is under ${p100.spriteScale}", p130.spriteScale / 1.3f >= p100.spriteScale - 0.001f)
                    assertTrue("$tag: kept, but less clear than 100 %", !p100.clear || p130.clear)
                } else droppedToOne++
                assertTrue(StageGeo.keepSized(0.8f, p100, p80))
                assertTrue(StageGeo.keepSized(1f, p100, p100))
                // the boxes: when the plan is clear, both keep the gap from both art boxes and lie inside the arena (inset)
                for ((tagSize, p) in listOf("130" to p130, "80" to p80)) if (p.clear) {
                    for (card in listOf(p.wildCard, p.playerCard)) {
                        assertTrue("$tag/$tagSize: card inside", card.x >= inset - 0.011f && card.y >= inset - 0.011f && card.right <= vw - inset + 0.011f && card.bottom <= vh - inset + 0.011f)
                        for (art in listOf(p.wildArt, p.playerArt)) {
                            val grown = ArenaGeo.Box(card.x - p.gap, card.y - p.gap, card.w + 2f * p.gap, card.h + 2f * p.gap)
                            assertFalse("$tag/$tagSize: a card within the gap of an art box", grown.hits(art))
                        }
                    }
                }
                n++
            }
        }
        println("B20 grid: $n combos, 130 grew over the stage's frame in $grew, came back down by the rule's steps in $cameBackDown, 80 smaller in $smaller, 130 kept in $kept, dropped to 100 in $droppedToOne")
        assertTrue("the grid reaches growth ($grew)", grew > 0)
        assertTrue("the grid reaches the rule bringing 130 % back down ($cameBackDown)", cameBackDown > 0)
        assertTrue("80 % is smaller somewhere ($smaller)", smaller > 0)
        assertTrue("130 % is kept somewhere ($kept)", kept > 0)
    }

    @Test fun aSmallerSizeNeverMakesAClearPanelUnclear() {
        // the art boxes of smaller frames lie inside the bigger ones (same feet), so whatever was clear at 100 % is clear at 80 %
        var n = 0
        for (b in BackdropCatalog.ALL) for ((w, h) in listOf(412f to 146f, 365f to 200f, 240f to 130f, 206f to 136f, 412f to 265f)) {
            val st = fit(b.id, w, h)
            val p100 = StageGeo.plan(w, h, st, 150f, 64f, 156f, 70f, 1f, wildArt = fullArt, playerArt = fullArt)
            val p80 = StageGeo.plan(w, h, st, 150f, 64f, 156f, 70f, 1f, wildArt = fullArt, playerArt = fullArt, sizeScale = 0.8f)
            if (p100.clear) assertTrue("${b.id} ${w}x$h: clear at 100 %, not at 80 %", p80.clear)
            n++
        }
        assertTrue(n > 50)
    }
}
