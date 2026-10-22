package com.husarp.ankigo.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R14-A, PLAN 140: the battle frame's safe area ([FrameSafe]): the info boxes stay inside the frame's band, below its icicles and out
 * of the four corner squares, in the plain layouts ([ArenaGeo]) and in picture mode ([StageGeo]), for every frame style.
 */
class FrameSafeTest {
    private fun box(x: Float, y: Float, w: Float, h: Float) = floatArrayOf(x, y, w, h)

    // ---------------------------------------------------------------- the styles

    @Test fun theCornerPatternReachIsTheFarthestDrawnCellOfEveryStyle() {
        assertEquals(0, BattleFrameStyle.NONE.cornerUnits)
        assertEquals(0, BattleFrameStyle.SIMPLE.cornerUnits)
        assertEquals(6, BattleFrameStyle.WOOD.cornerUnits)
        assertEquals(6, BattleFrameStyle.STONE.cornerUnits)
        assertEquals(7, BattleFrameStyle.GOLD.cornerUnits)
        assertEquals(6, BattleFrameStyle.ICE.cornerUnits)      // its pattern is drawn to cell 5 (the 7th row / column is empty)
        assertEquals(6, BattleFrameStyle.METAL.cornerUnits)
        for (s in BattleFrameStyle.entries) assertTrue("${s.name}: the corner square is not thinner than the band", s.cornerUnits == 0 || s.cornerUnits >= s.t)
    }

    @Test fun theSafeAreaIsTheBandTheDeeperTopAndTheCornerSquareInTheGivenUnit() {
        val none = BattleFrame.safe(BattleFrameStyle.NONE, 3f)
        assertTrue(none.none)
        val ice = BattleFrame.safe(BattleFrameStyle.ICE, 3f)
        assertEquals(2 * 3f, ice.edge, 0f)
        assertEquals(6 * 3f, ice.top, 0f)       // the icicles hang 6 units from the top
        assertEquals(6 * 3f, ice.corner, 0f)
        val gold = BattleFrame.safe(BattleFrameStyle.GOLD, 2f)
        assertEquals(6f, gold.edge, 0f)
        assertEquals(6f, gold.top, 0f)
        assertEquals(14f, gold.corner, 0f)
        assertFalse(gold.none)
    }

    // ---------------------------------------------------------------- moving one box

    private val vw = 300f
    private val vh = 200f

    @Test fun noFrameChangesNothing() {
        val b = box(2f, 1f, 160f, 60f)
        FrameSafe.NONE.clearInto(b, vw, vh)
        assertEquals(2f, b[0], 0f); assertEquals(1f, b[1], 0f)
        val g = ArenaGeo.Box(2f, 1f, 160f, 60f)
        assertSame(g, FrameSafe.NONE.clear(g, vw, vh))
        assertTrue(FrameSafe.NONE.isClear(0f, 0f, 160f, 60f, vw, vh))
    }

    @Test fun aBoxInsideTheBandAndOffTheCornersStaysWhereItIs() {
        val s = FrameSafe(6f, 6f, 14f)
        val g = ArenaGeo.Box(100f, 6f, 160f, 60f)     // on the top band's inner edge, between the two corner squares
        assertSame(g, s.clear(g, vw, vh))
        assertTrue(s.isClear(g.x, g.y, g.w, g.h, vw, vh))
        val low = ArenaGeo.Box(6f, 40f, 160f, 60f)    // on the left band, below the top-left square
        assertSame(low, s.clear(low, vw, vh))
    }

    @Test fun aBoxOutsideTheBandIsPulledInsideIt() {
        val s = FrameSafe(6f, 8f, 0f)
        val b = box(0f, 0f, 160f, 60f)
        s.clearInto(b, vw, vh)
        assertEquals(6f, b[0], 0f)
        assertEquals("the top band is the deeper one", 8f, b[1], 0f)
        val far = box(250f, 190f, 160f, 60f)
        s.clearInto(far, vw, vh)
        assertEquals(300f - 6f - 160f, far[0], 0f)
        assertEquals(200f - 6f - 60f, far[1], 0f)
    }

    @Test fun aBoxInACornerLeavesTheSquareAlongXWhenThatIsAsShortAsTheOtherWay() {
        val s = FrameSafe(6f, 6f, 14f)
        // every corner at the band's inner edges: 8 either way, x wins the tie
        val tl = box(6f, 6f, 160f, 60f); s.clearInto(tl, vw, vh)
        assertEquals(14f, tl[0], 0f); assertEquals(6f, tl[1], 0f)
        val tr = box(300f - 6f - 160f, 6f, 160f, 60f); s.clearInto(tr, vw, vh)
        assertEquals(300f - 14f - 160f, tr[0], 0f); assertEquals(6f, tr[1], 0f)
        val bl = box(6f, 200f - 6f - 60f, 160f, 60f); s.clearInto(bl, vw, vh)
        assertEquals(14f, bl[0], 0f); assertEquals(200f - 6f - 60f, bl[1], 0f)
        val br = box(300f - 6f - 160f, 200f - 6f - 60f, 160f, 60f); s.clearInto(br, vw, vh)
        assertEquals(300f - 14f - 160f, br[0], 0f); assertEquals(200f - 6f - 60f, br[1], 0f)
        for (b in listOf(tl, tr, bl, br)) assertTrue(s.isClear(b[0], b[1], b[2], b[3], vw, vh))
    }

    @Test fun aBoxMovesTheShorterWay() {
        val s = FrameSafe(6f, 6f, 14f)
        val nearTheSide = box(13f, 6f, 160f, 60f)     // 1 to go along x, 8 along y
        s.clearInto(nearTheSide, vw, vh)
        assertEquals(14f, nearTheSide[0], 0f); assertEquals(6f, nearTheSide[1], 0f)
        val nearTheTop = box(6f, 13f, 160f, 60f)      // 8 along x, 1 along y
        s.clearInto(nearTheTop, vw, vh)
        assertEquals(6f, nearTheTop[0], 0f); assertEquals(14f, nearTheTop[1], 0f)
    }

    @Test fun aBoxTooWideToMoveAlongXMovesAlongY() {
        val s = FrameSafe(6f, 6f, 14f)
        val b = box(6f, 6f, 288f, 60f)          // as wide as the picture allows: no way along x
        s.clearInto(b, vw, vh)
        assertEquals(6f, b[0], 0f)
        assertEquals(14f, b[1], 0f)
        assertTrue(s.isClear(b[0], b[1], b[2], b[3], vw, vh))
    }

    @Test fun aBoxThatCannotBeClearIsOnlyKeptInsideTheBand() {
        val s = FrameSafe(6f, 6f, 14f)
        val b = box(6f, 6f, 288f, 188f)         // the whole inside
        s.clearInto(b, vw, vh)
        assertEquals(6f, b[0], 0f); assertEquals(6f, b[1], 0f)
        assertFalse(s.isClear(b[0], b[1], b[2], b[3], vw, vh))
    }

    @Test fun clearReturnsANewBoxOnlyWhenSomethingMoved() {
        val s = FrameSafe(6f, 6f, 14f)
        val moved = s.clear(ArenaGeo.Box(6f, 6f, 160f, 60f), vw, vh)
        assertEquals(14f, moved.x, 0f)
        assertEquals(160f, moved.w, 0f)
        val b = ArenaGeo.Box(40f, 60f, 160f, 60f)
        assertSame(b, s.clear(b, vw, vh))
        assertNotSame(b, s.clear(ArenaGeo.Box(0f, 0f, 160f, 60f), vw, vh))
    }

    @Test fun leavingOneCornerNeverLandsInTheOther() {
        // R14 review: a box as wide as the room between the two top squares allows. Moving right out of the left square lands in the
        // right one, and moving left out of that lands back in the left one; only the move down clears both.
        val s = FrameSafe(6f, 6f, 14f)
        val b = box(6f, 6f, 160f, 60f)
        s.clearInto(b, 180f, 200f)
        assertEquals(6f, b[0], 0f); assertEquals(14f, b[1], 0f)
        assertTrue(s.isClear(b[0], b[1], b[2], b[3], 180f, 200f))
        // the same at the bottom: up, never back and forth
        val low = box(6f, 200f - 6f - 60f, 160f, 60f)
        s.clearInto(low, 180f, 200f)
        assertEquals(6f, low[0], 0f); assertEquals(200f - 14f - 60f, low[1], 0f)
        assertTrue(s.isClear(low[0], low[1], low[2], low[3], 180f, 200f))
    }

    @Test fun anyBoxThatCanBeClearEndsClearForEveryStyle() {
        for (style in BattleFrameStyle.entries) for (unit in listOf(2f, 3f, 4f)) {
            val s = BattleFrame.safe(style, unit)
            for ((w, h) in listOf(300f to 200f, 412f to 640f, 240f to 136f)) for (bw in listOf(80f, 140f, 160f)) for (bh in listOf(40f, 64f, 90f)) {
                var y = 0f
                while (y <= h - bh) {
                    var x = 0f
                    while (x <= w - bw) {
                        val b = box(x, y, bw, bh)
                        s.clearInto(b, w, h)
                        // the band alone always holds; the corner squares whenever there is room beside or below them
                        assertTrue("${style.name} u=$unit ${w}x$h box $bw x $bh from ($x,$y) -> (${b[0]},${b[1]}) is outside the band",
                            b[0] >= s.edge - 0.01f && b[1] >= s.top - 0.01f && b[0] + bw <= w - s.edge + 0.01f && b[1] + bh <= h - s.edge + 0.01f)
                        if (bw <= w - 2f * s.corner && bh <= h - 2f * s.corner) {
                            assertTrue("${style.name} u=$unit ${w}x$h box $bw x $bh from ($x,$y) -> (${b[0]},${b[1]}) touches a corner square",
                                s.isClear(b[0], b[1], bw, bh, w, h))
                        }
                        x += 7f
                    }
                    y += 7f
                }
            }
        }
    }

    // ---------------------------------------------------------------- the layouts

    private fun clearBox(s: FrameSafe, b: ArenaGeo.Box, w: Float, h: Float) = s.isClear(b.x, b.y, b.w, b.h, w, h)

    @Test fun thePlainLayoutsKeepBothCardsClearOfEveryFrame() {
        var checked = 0
        for (style in BattleFrameStyle.entries) for (unit in listOf(2f, 3.3f, 4f)) {
            val s = BattleFrame.safe(style, unit)
            for ((w, h) in listOf(412f to 640f, 412f to 265f, 412f to 190f, 320f to 180f, 240f to 136f, 860f to 253f)) for (own in listOf(true, false)) {
                for (compact in listOf(false, true)) {
                    val c = ArenaGeo.cards(0f, compact, true)
                    val p = ArenaGeo.plan(w, h, c, compact, own, s)
                    if (!p.fits || p.middle) continue
                    checked++
                    assertTrue("${style.name} u=$unit ${w}x$h own=$own compact=$compact: wild card ${p.wildCard.x},${p.wildCard.y} ${p.wildCard.w}x${p.wildCard.h}", clearBox(s, p.wildCard, w, h))
                    assertTrue("${style.name} u=$unit ${w}x$h own=$own compact=$compact: our card ${p.playerCard.x},${p.playerCard.y}", clearBox(s, p.playerCard, w, h))
                }
            }
        }
        assertTrue("the grid held plans that fit ($checked)", checked > 100)
    }

    @Test fun noFrameLeavesThePlainPlanExactlyAsItWas() {
        for ((w, h) in listOf(412f to 640f, 412f to 265f, 240f to 136f)) for (own in listOf(true, false)) {
            val c = ArenaGeo.cards(0f, false, true)
            val a = ArenaGeo.plan(w, h, c, false, own)
            val b = ArenaGeo.plan(w, h, c, false, own, FrameSafe.NONE)
            val z = ArenaGeo.plan(w, h, c, false, own, null)
            for (q in listOf(b, z)) {
                assertEquals(a.layout, q.layout); assertEquals(a.fits, q.fits)
                assertEquals(a.wildCard.x, q.wildCard.x, 0f); assertEquals(a.wildCard.y, q.wildCard.y, 0f)
                assertEquals(a.playerCard.x, q.playerCard.x, 0f); assertEquals(a.playerCard.y, q.playerCard.y, 0f)
                assertEquals(a.k, q.k, 0f); assertEquals(a.a, q.a, 0f)
            }
        }
    }

    @Test fun pictureModeKeepsBothCardsClearOfEveryFrameInEveryLook() {
        var checked = 0
        for (id in BackdropCatalog.ALL.map { it.id }) for ((w, h) in listOf(412f to 640f, 412f to 265f, 412f to 146f, 206f to 136f, 860f to 253f)) {
            val st = StageFit.fit(BackdropCatalog.byId(id)!!, w, h, 1f) ?: continue
            for (style in BattleFrameStyle.entries) for (corners in listOf(false, true)) {
                val s = BattleFrame.safe(style, 3f)
                val q = StageGeo.plan(w, h, st, 150f, 64f, 156f, 70f, 1f, inset = s.edge, corners = corners, safe = s)
                if (!q.clear) continue
                checked++
                assertTrue("$id ${w}x$h ${style.name} corners=$corners: wild ${q.wildCard.x},${q.wildCard.y} ${q.wildCard.w}x${q.wildCard.h}",
                    s.isClear(q.wildCard.x, q.wildCard.y, q.wildCard.w, q.wildCard.h, w, h))
                assertTrue("$id ${w}x$h ${style.name} corners=$corners: ours ${q.playerCard.x},${q.playerCard.y} ${q.playerCard.w}x${q.playerCard.h}",
                    s.isClear(q.playerCard.x, q.playerCard.y, q.playerCard.w, q.playerCard.h, w, h))
            }
        }
        assertTrue("the grid held clear plans ($checked)", checked > 200)
    }

    /** The witness: with only the band in the safe area (as before R14) boxes on the edge did touch the corner squares. */
    @Test fun withOnlyTheBandInTheSafeAreaTheCardsTouchedTheCornerSquares() {
        for (style in listOf(BattleFrameStyle.WOOD, BattleFrameStyle.STONE, BattleFrameStyle.GOLD, BattleFrameStyle.METAL, BattleFrameStyle.ICE)) {
            val s = BattleFrame.safe(style, 3f)
            var touching = 0
            for (id in BackdropCatalog.ALL.map { it.id }) for ((w, h) in listOf(412f to 640f, 412f to 265f, 860f to 253f)) {
                val st = StageFit.fit(BackdropCatalog.byId(id)!!, w, h, 1f) ?: continue
                val q = StageGeo.plan(w, h, st, 150f, 64f, 156f, 70f, 1f, inset = s.edge, corners = true)   // the HUD: boxes on the edges
                if (q.clear && (!s.isClear(q.wildCard.x, q.wildCard.y, q.wildCard.w, q.wildCard.h, w, h) || !s.isClear(q.playerCard.x, q.playerCard.y, q.playerCard.w, q.playerCard.h, w, h))) touching++
            }
            assertTrue("${style.name}: the old rule left cards on a corner square somewhere ($touching)", touching > 0)
        }
    }

    @Test fun pictureModeWithoutASafeAreaIsTheOldPlan() {
        val st = StageFit.fit(BackdropCatalog.byId(BackdropCatalog.ALL.first().id)!!, 412f, 640f, 1f)!!
        val a = StageGeo.plan(412f, 640f, st, 150f, 64f, 156f, 70f, 1f, inset = 6f)
        val b = StageGeo.plan(412f, 640f, st, 150f, 64f, 156f, 70f, 1f, inset = 6f, safe = FrameSafe(6f, 6f, 0f))
        assertEquals(a.wildCard.x, b.wildCard.x, 0f); assertEquals(a.wildCard.y, b.wildCard.y, 0f)
        assertEquals(a.playerCard.x, b.playerCard.x, 0f); assertEquals(a.playerCard.y, b.playerCard.y, 0f)
        assertEquals(a.k, b.k, 0f)
    }
}
