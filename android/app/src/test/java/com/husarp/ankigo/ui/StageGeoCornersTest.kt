package com.husarp.ankigo.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min

/**
 * ROUND 10 W7, the HUD look's corners mode ([StageGeo.plan] with `corners = true`, the design page's `autoPlace(..., corners)`): the two boxes
 * go to two opposite corners of the arena at the start scale, no collision search. The same primary edge as the automatic rule (tall
 * panel: ours at the bottom, the wild one at the top; wide panel: ours at the top, the wild one at the bottom), ours on the LEFT edge, the
 * wild one on the RIGHT edge. Checked on every panel shape the battle has (battle tab, study tall / short / strip, landscape, split,
 * float, PiP) over every picture, with and without a battle frame, and with the Pokémon size; plus [StageGeo.cornerBoxes], the same rule
 * for the plain arena.
 */
class StageGeoCornersTest {
    private val fullArt: (Float) -> ArtShare? = { ArtShare(0f, 0f, 1f, 1f) }
    private val tight: (Float) -> ArtShare? = { ArtShare(0.13f, 0.18f, 0.66f, 0.62f) }

    private fun fit(id: String, w: Float, h: Float) = StageFit.fit(BackdropCatalog.byId(id)!!, w, h, 1f)!!

    /** Panel shapes (w dp, h dp, the panel's draw scale): the nine hosts of D2 and a few more. */
    private val shapes = listOf(
        Triple(412f, 640f, 1f),      // battle tab
        Triple(412f, 869f, 1f),      // battle tab, full phone
        Triple(412f, 265f, 1f),      // study panel, tall
        Triple(412f, 134f, 0.634f),  // study panel, the 220 dp preset drawn at 63 %
        Triple(412f, 146f, 1f),      // study strip
        Triple(365f, 266f, 1f),      // split, a narrower pane
        Triple(860f, 253f, 1f),      // landscape
        Triple(320f, 300f, 1f),      // floating battle window
        Triple(206f, 136f, 1f),      // a narrow pane
        Triple(240f, 130f, 0.61f),   // picture-in-picture
    )

    private class Case(val tag: String, val vw: Float, val vh: Float, val s: Float, val floor: Float, val k0: Float, val st: Stage, val inset: Float)

    private fun cases(): List<Case> {
        val out = ArrayList<Case>()
        for (b in BackdropCatalog.ALL) for ((w, h, s) in shapes) {
            val vw = w / s; val vh = h / s
            val floor = Math.ceil((8f / s * 10f).toDouble()).toFloat() / 10f
            val k0 = if (s < 1f || h <= 400f) 1f else min(vw / 300f, vh / 430f).coerceIn(1f, 1.4f)
            for (inset in listOf(0f, 6f)) out += Case("${b.id} ${w}x$h s=$s inset=$inset", vw, vh, s, floor, k0, fit(b.id, vw, vh), inset)
        }
        return out
    }

    private fun plan(c: Case, corners: Boolean, art: ((Float) -> ArtShare?)?, size: Float = 1f) =
        StageGeo.plan(c.vw, c.vh, c.st, 150f, 64f, 156f, 70f, c.k0, c.s, c.floor, c.inset, art, art, 56f, 58f, 10.5f, 8.5f, size, 0f, corners)

    private fun inside(c: Case, b: ArenaGeo.Box) =
        b.x >= c.inset - 0.011f && b.y >= c.inset - 0.011f && b.right <= c.vw - c.inset + 0.011f && b.bottom <= c.vh - c.inset + 0.011f

    @Test fun theBoxesGoToOppositeCornersOnTheRulesPrimaryEdges() {
        var n = 0
        for (c in cases()) for (art in listOf(fullArt, tight, null)) {
            val p = plan(c, corners = true, art = art)
            val tall = StageGeo.isTall(c.vw, c.vh)
            val tag = c.tag
            // ours on the left edge, the wild one on the right edge
            assertEquals("$tag: ours on the left edge", c.inset, p.playerCard.x, 0.011f)
            assertEquals("$tag: the wild one on the right edge", c.vw - c.inset, p.wildCard.right, 0.011f)
            // the primary side: tall = ours bottom / wild top; wide = ours top / wild bottom
            if (tall) {
                assertEquals("$tag: ours at the bottom", c.vh - c.inset, p.playerCard.bottom, 0.011f)
                assertEquals("$tag: the wild one at the top", c.inset, p.wildCard.y, 0.011f)
            } else {
                assertEquals("$tag: ours at the top", c.inset, p.playerCard.y, 0.011f)
                assertEquals("$tag: the wild one at the bottom", c.vh - c.inset, p.wildCard.bottom, 0.011f)
            }
            // both are inside the arena (inset by the battle frame)
            assertTrue("$tag: ours inside (${p.playerCard.x}, ${p.playerCard.y}, ${p.playerCard.w} x ${p.playerCard.h})", inside(c, p.playerCard))
            assertTrue("$tag: the wild one inside", inside(c, p.wildCard))
            // opposite corners: one on each side, left / right, and (the sides being the two vertical edges) never both on one edge
            assertTrue("$tag: ours is left of the wild one", p.playerCard.x < p.wildCard.x)
            assertTrue("$tag: on different vertical edges", abs(p.playerCard.y - p.wildCard.y) > 1f)
            assertTrue("$tag: a clear plan by definition", p.clear)
            n++
        }
        assertTrue("the grid is wide ($n)", n > 1000)
    }

    private fun abs(v: Float) = if (v < 0f) -v else v

    @Test fun theScaleIsTheStartScaleAndNoTextFallsUnderTheReadableSize() {
        for (c in cases()) {
            val p = plan(c, corners = true, art = null)
            val scales = StageGeo.boxScales(c.vh * c.s, c.s, c.floor, c.k0, 10.5f, 8.5f)
            assertEquals("${c.tag}: the start scale", scales.start, p.k, 0.0001f)
            // the smallest text of a box (the HP number at 10.5 sp; the chips at 8.5 sp in a full one) is on screen >= PanelFit.MIN_SP
            assertTrue("${c.tag}: k ${p.k} is above the text floor", p.k >= StageGeo.textScale(10.5f, c.s, c.floor) - 0.0001f)
            if (!p.reduced) assertTrue("${c.tag}: a full box is above the chip floor", p.k >= StageGeo.textScale(8.5f, c.s, c.floor) - 0.0001f)
            // the heights are the full or the reduced box's, as the normal rule does
            assertEquals("${c.tag}: our height", (if (p.reduced) 58f else 70f) * p.k, p.playerCard.h, 0.01f)
            assertEquals("${c.tag}: wild height", (if (p.reduced) 56f else 64f) * p.k, p.wildCard.h, 0.01f)
            assertEquals("${c.tag}: our width", 156f * p.k, p.playerCard.w, 0.01f)
            assertEquals("${c.tag}: wild width", 150f * p.k, p.wildCard.w, 0.01f)
        }
    }

    @Test fun thePokemonKeepTheirFrameAndTheBoxesDoNotSearchForRoom() {
        // the stage's own frames, whatever sits in the corners: no collision search, no shrinking of the Pokémon
        for (c in cases()) {
            val p = plan(c, corners = true, art = fullArt)
            assertEquals("${c.tag}: frames x1", 1f, p.spriteScale, 0f)
            assertEquals("${c.tag}: wild frame", c.st.wildFrame, p.wildSprite.w, 0.01f)
            assertEquals("${c.tag}: our frame", c.st.playerFrame, p.playerSprite.w, 0.01f)
        }
    }

    @Test fun withoutCornersEverythingIsTodaysRuleUnchanged() {
        var n = 0
        for (c in cases()) {
            val a = StageGeo.plan(c.vw, c.vh, c.st, 150f, 64f, 156f, 70f, c.k0, c.s, c.floor, c.inset, fullArt, fullArt, 56f, 58f, 10.5f, 8.5f)
            val b = plan(c, corners = false, art = fullArt)
            assertEquals(c.tag, a.k, b.k, 0f)
            assertEquals(c.tag, a.spriteScale, b.spriteScale, 0f)
            assertEquals(c.tag, a.wildCard.x, b.wildCard.x, 0f); assertEquals(c.tag, a.wildCard.y, b.wildCard.y, 0f)
            assertEquals(c.tag, a.playerCard.x, b.playerCard.x, 0f); assertEquals(c.tag, a.playerCard.y, b.playerCard.y, 0f)
            n++
        }
        assertTrue(n > 100)
    }

    @Test fun thePokemonSizeKeepsTheCornersAndKeepSizedStillDecides() {
        var bigger = 0
        for (c in cases()) {
            val p100 = plan(c, corners = true, art = fullArt)
            for (size in listOf(0.8f, 1.3f)) {
                val p = plan(c, corners = true, art = fullArt, size = size)
                assertEquals("${c.tag} $size: ours on the left", c.inset, p.playerCard.x, 0.011f)
                assertEquals("${c.tag} $size: the wild one on the right", c.vw - c.inset, p.wildCard.right, 0.011f)
                assertTrue("${c.tag} $size: inside", inside(c, p.playerCard) && inside(c, p.wildCard))
                assertTrue("${c.tag} $size: the same boxes (the size does not move them)", p.k == p100.k)
                if (size < 1f) assertTrue("${c.tag}: 80 % is smaller", p.spriteScale <= p100.spriteScale * 0.8f + 0.0001f)
                if (size > 1f && p.spriteScale > 1.0001f) bigger++
                assertTrue("${c.tag} $size: the Pokemon-size rule applies to a corners plan as to any other", StageGeo.keepSized(size, p100, p) || size > 1f)
            }
        }
        assertTrue("130 % gets a bigger frame somewhere ($bigger)", bigger > 0)
    }

    // ---------------------------------------------------------------- the plain arena (no picture)

    @Test fun cornerBoxesPutOursOnTheLeftAndTheWildOneOnTheRight() {
        val wild = ArenaGeo.Box(50f, 50f, 150f, 64f)
        val ours = ArenaGeo.Box(20f, 90f, 156f, 70f)
        // a tall arena: ours at the bottom, the wild one at the top
        val (w1, o1) = StageGeo.cornerBoxes(412f, 640f, wild, ours)
        assertEquals(0f, o1.x, 0f); assertEquals(640f - 70f, o1.y, 0f)
        assertEquals(412f - 150f, w1.x, 0f); assertEquals(0f, w1.y, 0f)
        // a wide one: ours at the top, the wild one at the bottom
        val (w2, o2) = StageGeo.cornerBoxes(860f, 253f, wild, ours)
        assertEquals(0f, o2.x, 0f); assertEquals(0f, o2.y, 0f)
        assertEquals(860f - 150f, w2.x, 0f); assertEquals(253f - 64f, w2.y, 0f)
        // sizes are kept; an inset moves them in
        assertEquals(156f, o2.w, 0f); assertEquals(70f, o2.h, 0f); assertEquals(150f, w2.w, 0f); assertEquals(64f, w2.h, 0f)
        val (w3, o3) = StageGeo.cornerBoxes(412f, 640f, wild, ours, 6f)
        assertEquals(6f, o3.x, 0f); assertEquals(640f - 6f - 70f, o3.y, 0f)
        assertEquals(412f - 6f - 150f, w3.x, 0f); assertEquals(6f, w3.y, 0f)
        // a box bigger than the arena stays at the inset, never off the top left
        val (w4, _) = StageGeo.cornerBoxes(100f, 50f, ArenaGeo.Box(0f, 0f, 150f, 64f), ours)
        assertTrue(w4.x >= 0f && w4.y >= 0f)
        assertFalse(max(w4.x, w4.y) < 0f)
    }
}
