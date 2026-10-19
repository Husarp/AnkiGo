package com.husarp.ankigo.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min

/**
 * ROUND 9 D1: the info boxes' automatic rule ([StageGeo]) on made-up numbers (no Pokémon data): the size and gap formulas, the
 * primary side, the spots and their order, the boxes shrinking before the sprites, the frame inset, and an independent
 * straight-line port of the mockup's `autoRule` ([Oracle]) compared with the optimised search over a wide grid.
 */
class StageGeoTest {
    private val frames = floatArrayOf(1f, 0.9f, 0.8f, 0.7f, 0.6f)

    private fun stage(px: Float, py: Float, pf: Float, wx: Float, wy: Float, wf: Float) = Stage(0f, 0f, 1f, 1f, false, px, py, pf, wx, wy, wf)

    private fun fit(id: String, w: Float, h: Float, zoom: Float = 1f) = StageFit.fit(BackdropCatalog.byId(id)!!, w, h, zoom)!!

    private fun inside(vw: Float, vh: Float, inset: Float, b: ArenaGeo.Box) =
        b.x >= inset - 0.01f && b.y >= inset - 0.01f && b.right <= vw - inset + 0.01f && b.bottom <= vh - inset + 0.01f

    private fun grown(b: ArenaGeo.Box, d: Float) = ArenaGeo.Box(b.x - d, b.y - d, b.w + 2f * d, b.h + 2f * d)

    // ---------------------------------------------------------------- the formulas

    @Test fun theTargetShareIsFullFrom260DpAndFallsLinearlyToTheMinimumAt130() {
        assertEquals(1f, StageGeo.targetShare(640f), 0f)
        assertEquals(1f, StageGeo.targetShare(265f), 0f)
        assertEquals(1f, StageGeo.targetShare(260f), 0.0001f)
        assertEquals(0.55f + 16f / 130f * 0.45f, StageGeo.targetShare(146f), 0.0001f)   // the strip: 60.5 %
        assertEquals(0.55f + 4f / 130f * 0.45f, StageGeo.targetShare(134f), 0.0001f)
        assertEquals(0.55f, StageGeo.targetShare(130f), 0.0001f)
        assertEquals("under 130 dp it stays at the minimum", 0.55f, StageGeo.targetShare(100f), 0f)
        assertEquals(0.775f, StageGeo.targetShare(195f), 0.0001f)                          // half way
    }

    // ---------------------------------------------------------------- R9 wave 3 (D12): no text under 8 sp on screen

    @Test fun aTextIsReadableFromTheBoxScaleThatDrawsItOnEightSpOnScreen() {
        assertEquals(0f, StageGeo.textScale(0f, 1f, 8f), 0f)                                   // no text, no floor
        assertEquals(8f / 10.5f, StageGeo.textScale(10.5f, 1f, 0f), 0.0001f)                 // the Study strip: panel scale 1, no raised text
        assertEquals(8f / 9f, StageGeo.textScale(9f, 1f, 8f), 0.0001f)
        assertEquals(1f, StageGeo.textScale(10.5f, 0.6f, 13.4f), 0.01f)                    // a panel at 60 %: PanelFit raised the text to 13.4 sp, it is on 8 sp already
        assertEquals(1f, StageGeo.textScale(6f, 1f, 0f), 0f)                                  // never above 1
        assertEquals(8f / 14f, StageGeo.textScale(14f, 1f, 0f), 0.0001f)
    }

    @Test fun theBoxScalesNeverGoUnderTheReadableOneAndAFullBoxNeedsMoreThanAReducedOne() {
        // the strip with the above style: reduced box 10.5 sp (the HP number), full box 8.5 sp (the type chips)
        val strip = StageGeo.boxScales(146f, 1f, 0f, 1f, 10.5f, 8.5f)
        assertEquals(8f / 10.5f, strip.start, 0.0001f)          // 60.5 % wanted by the height, 76.2 % needed by the text
        assertEquals(8f / 10.5f, strip.min, 0.0001f)
        assertEquals(8f / 10.5f, strip.near, 0.0001f)
        assertEquals(8f / 8.5f, strip.full, 0.0001f)             // the chips are readable from 94 %
        // a roomy panel is unaffected
        val big = StageGeo.boxScales(640f, 1f, 8f, 1.37f, 10.5f, 8.5f)
        assertEquals(1.37f, big.start, 0.0001f)
        assertEquals(1.37f * 0.85f, big.near, 0.0001f)
        assertEquals(max(1.37f * 0.55f, 8f / 10.5f), big.min, 0.0001f)
        // no text sizes = the mockup's scales exactly
        val plain = StageGeo.boxScales(146f, 1f, 8f, 1f)
        assertEquals(0.55f + 16f / 130f * 0.45f, plain.start, 0.0001f)
        assertEquals(0f, plain.full, 0f)
    }

    @Test fun aReducedBoxIsUsedUnderTheScaleItsOptionalPartsAreReadableAt() {
        val st = fit("forest", 412f, 146f)
        // heights: full 75, reduced 65 (the XP number gone): the plan says which it drew, and a reduced plan used the short height
        val p = StageGeo.plan(412f, 146f, st, 160f, 59f, 160f, 75f, 1f, 1f, 0f, 0f, null, null, 60f, 65f, 10.5f, 8.5f)
        assertTrue("reduced in the strip (k ${p.k})", p.reduced)
        assertEquals(8f / 10.5f, p.k, 0.001f)
        assertEquals(65f * p.k, p.playerCard.h, 0.01f)
        assertEquals(60f * p.k, p.wildCard.h, 0.01f)
        // a roomy panel keeps the full box
        val big = StageGeo.plan(412f, 640f, fit("forest", 412f, 640f), 160f, 59f, 160f, 75f, 1.37f, 1f, 8f, 0f, null, null, 60f, 65f, 10.5f, 8.5f)
        assertFalse(big.reduced)
        assertEquals(75f * big.k, big.playerCard.h, 0.01f)
    }

    @Test fun theGapIsEightDpOnScreenInBigPanelsAndNeverUnderFour() {
        assertEquals(8f, StageGeo.gap(640f, 1f), 0.0001f)
        assertEquals(8f, StageGeo.gap(265f, 1f), 0.0001f)
        assertEquals(8f * 146f / 260f, StageGeo.gap(146f, 1f), 0.0001f)
        assertEquals(4f, StageGeo.gap(130f, 1f), 0.0001f)                   // 8 * .5
        assertEquals(4f, StageGeo.gap(100f, 1f), 0.0001f)                   // the floor
        // virtual dp: a panel drawn at 63 % has its gap 1 / .634 times bigger so it is the same on screen
        assertEquals(max(4f, 8f * 134f / 260f) / 0.634f, StageGeo.gap(134f, 0.634f), 0.0001f)
        assertEquals(4f / 0.61f, StageGeo.gap(130f, 0.61f), 0.0001f)
    }

    @Test fun theBoxScalesAtTheNineHostHeights() {
        // Battle tab, phone upright: 412 x 640, roomy k0 = 1.37; the boxes keep their full size
        val battle = StageGeo.boxScales(640f, 1f, 8f, 1.37f)
        assertEquals(1.37f, battle.start, 0.0001f)
        assertEquals(1.37f * 0.85f, battle.near, 0.0001f)
        assertEquals(1.37f * 0.55f, battle.min, 0.0001f)
        // Study panel dragged tall: 265 dp
        assertEquals(1f, StageGeo.boxScales(265f, 1f, 8f, 1f).start, 0.0001f)
        // the strip: 146 dp, k0 = 1 - small boxes from the start (60.5 %), near = start, min 55 %
        val strip = StageGeo.boxScales(146f, 1f, 8f, 1f)
        val t146 = 0.55f + 16f / 130f * 0.45f
        assertEquals(t146, strip.start, 0.0001f)
        assertEquals(t146, strip.near, 0.0001f)
        assertEquals(0.55f, strip.min, 0.0001f)
        // the 220 dp Full preset drawn at 63.4 %: the panel's scale and the raised text (floor 12.7) already shrank the cards
        val s = 0.634f
        val already = s * max(1f, 12.7f / 12f)
        val short = StageGeo.boxScales(134f, s, 12.7f, 1f)
        assertEquals(min(1f, (0.55f + 4f / 130f * 0.45f) / already), short.start, 0.0001f)
        assertEquals(min(short.start, 0.55f / already), short.min, 0.0001f)
        // 130 dp and 100 dp: the target is the minimum share in both
        assertEquals(0.55f, StageGeo.boxScales(130f, 1f, 8f, 1f).start, 0.0001f)
        assertEquals(0.55f, StageGeo.boxScales(100f, 1f, 8f, 1f).start, 0.0001f)
        assertEquals(0.55f, StageGeo.boxScales(100f, 1f, 8f, 1f).min, 0.0001f)
        // k never goes above the roomy scale k0
        assertTrue(StageGeo.boxScales(900f, 0.5f, 8f, 1.4f).start <= 1.4f)
    }

    @Test fun theBoxScaleCanBeUnderOneAndThePanelsOwnShrinkIsNotCountedTwice() {
        // a panel drawn at 55 % with floor 14.6 is already at .55 * 1.217 = 67 % of the design size: asking 55 % on screen needs only .82 more
        val k = StageGeo.boxScales(130f, 0.55f, 14.6f, 1f)
        assertEquals(0.55f / (0.55f * (14.6f / 12f)), k.start, 0.0001f)
        assertTrue(k.start < 1f)
        // a panel already smaller than the wanted share asks for no more shrinking
        assertEquals(1f, StageGeo.boxScales(260f, 0.3f, 8f, 1f).start, 0f)
    }

    @Test fun tallMeansTallerThanOnePointOneTimesTheWidth() {
        assertTrue(StageGeo.isTall(412f, 640f))
        assertFalse(StageGeo.isTall(412f, 265f))
        assertFalse(StageGeo.isTall(400f, 440f))      // exactly 1.1: not tall
        assertTrue(StageGeo.isTall(400f, 440.5f))
        assertFalse(StageGeo.isTall(860f, 253f))
    }

    // ---------------------------------------------------------------- the spots

    // wide panel 400 x 200 on screen, ours left, the wild one right; art = the default 85 % square
    private val wide = stage(100f, 180f, 80f, 300f, 120f, 60f)

    @Test fun aRoomyWidePanelPutsEachBoxOnItsPokemonsOuterSideCentredOnIt() {
        val p = StageGeo.plan(400f, 200f, wide, 138f, 60f, 142f, 60f, 1f)
        val k = StageGeo.boxScales(200f, 1f, 0f, 1f).start
        assertTrue(p.clear); assertEquals(1f, p.spriteScale, 0f); assertEquals(0, p.level)
        assertEquals(k, p.k, 0.0001f)
        // wide: ours at the top, the wild one at the bottom; both touch the panel's edge (no 8 dp margin), centred on the feet
        assertEquals(0f, p.playerCard.y, 0.001f)
        assertEquals(100f - 142f * k / 2f, p.playerCard.x, 0.001f)
        assertEquals(200f, p.wildCard.bottom, 0.001f)
        assertEquals(300f - 138f * k / 2f, p.wildCard.x, 0.001f)
        assertEquals(142f * k, p.playerCard.w, 0.001f); assertEquals(60f * k, p.playerCard.h, 0.001f)
        assertEquals(StageGeo.gap(200f, 1f), p.gap, 0.0001f)
    }

    // the Battle tab, 412 x 640 dp: ours low left, the wild one high right
    private val tallStage = stage(200f, 480f, 150f, 290f, 250f, 110f)

    @Test fun aRoomyTallPanelPutsOursAtTheBottomAndTheWildOneAtTheTop() {
        val p = StageGeo.plan(412f, 640f, tallStage, 166f, 70f, 170f, 82f, 1.37f)
        assertTrue(p.clear); assertEquals(0, p.level)
        assertEquals(1.37f, p.k, 0.0001f)
        assertEquals(640f, p.playerCard.bottom, 0.001f)
        assertEquals(0f, p.wildCard.y, 0.001f)
        assertEquals(200f - 170f * 1.37f / 2f, p.playerCard.x, 0.001f)
        assertEquals(290f - 166f * 1.37f / 2f, p.wildCard.x, 0.001f)
    }

    @Test fun aBoxNearAnEdgeIsClampedToItAndBecomesEdgeAligned() {
        // ours stands 20 dp from the left edge, the wild one 15 dp from the right: centring would leave the panel
        val s = stage(20f, 180f, 60f, 385f, 120f, 50f)
        val p = StageGeo.plan(400f, 200f, s, 138f, 60f, 142f, 60f, 1f)
        assertEquals(0f, p.playerCard.x, 0.001f)
        assertEquals(400f, p.wildCard.right, 0.001f)
        assertEquals(0f, p.playerCard.y, 0.001f)
        assertEquals(200f, p.wildCard.bottom, 0.001f)
    }

    @Test fun theBattleFrameInsetKeepsTheBoxesInsideAndTouchingTheInsetEdge() {
        val s = stage(20f, 180f, 60f, 385f, 120f, 50f)
        val inset = 7f
        val p = StageGeo.plan(400f, 200f, s, 138f, 60f, 142f, 60f, 1f, inset = inset)
        assertEquals(inset, p.playerCard.x, 0.001f)
        assertEquals(inset, p.playerCard.y, 0.001f)
        assertEquals(400f - inset, p.wildCard.right, 0.001f)
        assertEquals(200f - inset, p.wildCard.bottom, 0.001f)
        // over a grid, with and without a frame: always inside the inset panel (or, in a fallback, no further out than the smallest box allows)
        for (id in BackdropCatalog.ALL.map { it.id }) for ((w, h) in listOf(412f to 640f, 412f to 265f, 412f to 146f, 206f to 136f, 860f to 253f)) for (ins in listOf(0f, 5f, 9f)) {
            val st = fit(id, w, h)
            val q = StageGeo.plan(w, h, st, 150f, 64f, 156f, 70f, 1f, inset = ins)
            if (q.clear) {
                assertTrue("$id ${w}x$h inset $ins wild ${q.wildCard.x},${q.wildCard.y} ${q.wildCard.w}x${q.wildCard.h}", inside(w, h, ins, q.wildCard))
                assertTrue("$id ${w}x$h inset $ins ours", inside(w, h, ins, q.playerCard))
            }
        }
    }

    @Test fun smallPanelsGetSmallBoxesFromTheStartNotOnlyAfterACollision() {
        // the strip (146 dp, nothing in the way): the very first box scale is 60.5 %
        val roomy = stage(60f, 130f, 50f, 330f, 95f, 40f)
        val p = StageGeo.plan(412f, 146f, roomy, 138f, 60f, 142f, 60f, 1f)
        assertTrue(p.clear); assertEquals(0, p.level); assertEquals(1f, p.spriteScale, 0f)
        assertEquals(0.55f + 16f / 130f * 0.45f, p.k, 0.0001f)
        // a tall roomy panel: no shrink at all
        val big = StageGeo.plan(412f, 640f, tallStage, 166f, 70f, 170f, 82f, 1.37f)
        assertEquals(1.37f, big.k, 0.0001f)
    }

    // ---------------------------------------------------------------- shrink order: boxes before sprites

    /** A crowded wide panel: both Pokémon fill their frames' art (a full-frame share), so the roomy spots are blocked at full size. */
    private val fullArt: (Float) -> ArtShare? = { ArtShare(0f, 0f, 1f, 1f) }

    @Test fun aCrowdedPanelShrinksTheBoxesBeforeTheSpritesAndKeepsTheFramesWhole() {
        var shrunkBoxes = 0
        var example = ""
        for (id in BackdropCatalog.ALL.map { it.id }) for ((w, h) in listOf(412f to 265f, 300f to 200f, 260f to 180f, 860f to 253f, 412f to 640f)) {
            val st = fit(id, w, h)
            val sc = StageGeo.boxScales(h, 1f, 0f, if (h > 400f) 1.37f else 1f)
            val p = StageGeo.plan(w, h, st, 150f, 64f, 156f, 70f, if (h > 400f) 1.37f else 1f, wildArt = fullArt, playerArt = fullArt)
            if (p.clear && p.spriteScale == 1f && p.k < sc.start - 0.001f) {
                shrunkBoxes++
                if (example.isEmpty()) example = "$id ${w}x$h k ${p.k} of ${sc.start} level ${p.level}"
                assertTrue("$id: not under the smallest box", p.k >= sc.min - 0.001f)
            }
            if (p.spriteScale < 1f) {
                // sprites shrank: even the smallest box at the bigger sprite scale had no ok pair at any spot
                val bigger = frames[frames.indexOfFirst { abs(it - p.spriteScale) < 0.001f } - 1]
                assertFalse("$id ${w}x$h: at f $bigger an ok pair existed at the smallest box", Oracle.anyOk(w, h, st, 150f, 64f, 156f, 70f, sc.min, bigger, StageGeo.gap(h, 1f), 0f, fullArt, fullArt))
            }
        }
        assertTrue("some crowded panels are solved by smaller boxes alone ($shrunkBoxes)", shrunkBoxes > 0)
        println("D1 crowded example: $example ($shrunkBoxes plans)")
    }

    @Test fun theSpritesShrinkOnlyWhenEvenTheSmallestBoxFailsAtEverySpot() {
        var smaller = 0
        for (id in BackdropCatalog.ALL.map { it.id }) for ((w, h) in listOf(260f to 150f, 220f to 140f, 300f to 170f, 206f to 136f)) {
            val st = fit(id, w, h)
            val sc = StageGeo.boxScales(h, 1f, 0f, 1f)
            val p = StageGeo.plan(w, h, st, 160f, 70f, 160f, 70f, 1f, wildArt = fullArt, playerArt = fullArt)
            if (p.spriteScale < 1f - 0.001f) {
                smaller++
                for (cand in frames) {
                    if (cand <= p.spriteScale + 0.001f) break
                    assertFalse("$id ${w}x$h: f=$cand had an ok pair at the smallest box", Oracle.anyOk(w, h, st, 160f, 70f, 160f, 70f, sc.min, cand, StageGeo.gap(h, 1f), 0f, fullArt, fullArt))
                }
            }
        }
        assertTrue("some panels need smaller sprites ($smaller)", smaller > 0)
    }

    @Test fun nothingFitsGivesTheLeastCoverAtTheSmallestBoxAndIsNotClear() {
        // a 100 x 60 dp panel: two boxes of 77 x 33 dp at the smallest size and two full-frame Pokémon do not fit, even at x .6
        val st = stage(30f, 58f, 50f, 70f, 58f, 50f)
        val p = StageGeo.plan(100f, 60f, st, 140f, 60f, 140f, 60f, 1f, wildArt = fullArt, playerArt = fullArt)
        assertFalse(p.clear)
        assertEquals(-1, p.level)
        assertEquals(StageGeo.boxScales(60f, 1f, 0f, 1f).min, p.k, 0.0001f)
        // and it is deterministic
        val q = StageGeo.plan(100f, 60f, st, 140f, 60f, 140f, 60f, 1f, wildArt = fullArt, playerArt = fullArt)
        assertEquals(p.wildCard.x, q.wildCard.x, 0f); assertEquals(p.playerCard.y, q.playerCard.y, 0f); assertEquals(p.spriteScale, q.spriteScale, 0f)
        // the chosen pair is the one the oracle finds
        val o = Oracle.plan(100f, 60f, st, 140f, 60f, 140f, 60f, 1f, 1f, 0f, 0f, fullArt, fullArt, 60f, 60f, 0f, 0f)
        assertFalse(o.ok)
        assertEquals(o.f, p.spriteScale, 0.0001f)
        assertEquals(o.wild.x, p.wildCard.x, 0.001f); assertEquals(o.wild.y, p.wildCard.y, 0.001f)
        assertEquals(o.you.x, p.playerCard.x, 0.001f); assertEquals(o.you.y, p.playerCard.y, 0.001f)
    }

    // ---------------------------------------------------------------- properties over the catalog

    @Test fun everyClearPlanKeepsTheGapFromBothArtBoxesAndBetweenTheBoxes() {
        var clear = 0
        for (b in BackdropCatalog.ALL) for ((w, h) in listOf(412f to 640f, 412f to 265f, 412f to 146f, 365f to 266f, 860f to 253f, 206f to 136f, 393f to 205f)) for (s in listOf(1f, 0.634f, 0.61f)) {
            val vw = w / s; val vh = h / s
            val st = fit(b.id, vw, vh)
            val k0 = if (h > 400f) 1.37f else 1f
            val p = StageGeo.plan(vw, vh, st, 150f, 64f, 156f, 70f, k0, panelScale = s, floorSp = Math.ceil((8f / s * 10f).toDouble()).toFloat() / 10f)
            if (!p.clear) continue
            clear++
            val g = p.gap - 0.02f
            val tag = "${b.id} ${w}x$h s=$s f=${p.spriteScale} k=${p.k}"
            for (box in listOf(p.wildCard, p.playerCard)) {
                assertFalse("$tag: box on the wild art", grown(box, g).hits(p.wildArt))
                assertFalse("$tag: box on our art", grown(box, g).hits(p.playerArt))
                assertTrue("$tag: box inside", inside(vw, vh, 0f, box))
            }
            assertFalse("$tag: ours near the wild box", grown(p.playerCard, g).hits(p.wildCard))
            assertTrue("$tag: k between the smallest and the roomy scale", p.k <= k0 + 0.0001f && p.k > 0.4f)
        }
        assertTrue("many clear plans ($clear)", clear > 30)
    }

    @Test fun theSpriteFramesStandOnTheFeetAtEveryScale() {
        val st = fit("forest", 412f, 265f)
        val p = StageGeo.plan(412f, 265f, st, 150f, 64f, 156f, 70f, 1f, wildArt = fullArt, playerArt = fullArt)
        assertEquals(st.playerFeetX, p.playerSprite.x + p.playerSprite.w / 2f, 0.01f)
        assertEquals(st.playerFeetY, p.playerSprite.bottom, 0.01f)
        assertEquals(st.playerFrame * p.spriteScale, p.playerSprite.w, 0.01f)
        assertEquals(st.wildFeetX, p.wildSprite.x + p.wildSprite.w / 2f, 0.01f)
        assertEquals(st.wildFeetY, p.wildSprite.bottom, 0.01f)
        assertEquals(st.wildFrame * p.spriteScale, p.wildSprite.h, 0.01f)
    }

    // ---------------------------------------------------------------- the real art box in the frame

    @Test fun theArtBoxIsMappedThroughTheFrameAndMovesWithTheSprite() {
        // an off-centre sprite: its art is the left third of the frame, 40 % high, standing on the bottom
        val left: (Float) -> ArtShare? = { ArtShare(0f, 0.6f, 0.33f, 0.4f) }
        val st = stage(100f, 180f, 80f, 300f, 120f, 60f)
        val p = StageGeo.plan(400f, 200f, st, 138f, 60f, 142f, 60f, 1f, playerArt = left)
        // our frame is [60, 100] .. [140, 180]
        assertEquals(60f, p.playerArt.x, 0.001f)
        assertEquals(100f + 0.6f * 80f, p.playerArt.y, 0.001f)
        assertEquals(0.33f * 80f, p.playerArt.w, 0.001f); assertEquals(0.4f * 80f, p.playerArt.h, 0.001f)
        // the default stays the 85 % bottom-centred square
        val d = ArtShare.DEFAULT.inFrame(0f, 0f, 100f)
        val flat = ArenaGeo.Box(0f, 0f, 100f, 100f).art()
        assertEquals(flat.x, d.x, 0.0001f); assertEquals(flat.y, d.y, 0.0001f); assertEquals(flat.w, d.w, 0.0001f); assertEquals(flat.h, d.h, 0.0001f)
        val bc = ArtShare.bottomCentred(0.5f, 0.4f)
        assertEquals(0.25f, bc.x, 0f); assertEquals(0.6f, bc.y, 0f)
    }

    @Test fun aSmallerRealArtBoxFreesRoomThatTheFlatFrameDidNot() {
        var better = 0
        var example = ""
        for (b in BackdropCatalog.ALL) for ((w, h) in listOf(260f to 150f, 300f to 170f, 220f to 140f, 412f to 146f, 206f to 136f)) {
            val st = fit(b.id, w, h)
            val flat = StageGeo.plan(w, h, st, 150f, 64f, 156f, 70f, 1f, wildArt = fullArt, playerArt = fullArt)
            val real = StageGeo.plan(w, h, st, 150f, 64f, 156f, 70f, 1f, wildArt = { ArtShare(0.3f, 0.35f, 0.4f, 0.65f) }, playerArt = { ArtShare(0.3f, 0.35f, 0.4f, 0.65f) })
            if ((!flat.clear && real.clear) || real.spriteScale > flat.spriteScale + 0.001f || real.k > flat.k + 0.001f) {
                better++
                if (example.isEmpty()) example = "${b.id} ${w}x$h flat f=${flat.spriteScale} k=${flat.k} clear=${flat.clear} -> real f=${real.spriteScale} k=${real.k} clear=${real.clear}"
            }
            assertTrue("${b.id} ${w}x$h: a real art box never needs more room than the whole frame", real.spriteScale >= flat.spriteScale - 0.001f)
        }
        assertTrue("a real art box frees room somewhere ($better)", better > 0)
        println("D1 real art: $example ($better combos better)")
    }

    // ---------------------------------------------------------------- the optimised search equals the straight port of the mockup

    @Test fun theSearchEqualsAnIndependentPortOfTheMockupsAutoRule() {
        val none: (Float) -> ArtShare? = { null }
        val tight: (Float) -> ArtShare? = { ArtShare(0.13f, 0.18f, 0.66f, 0.62f) }
        val wide2: (Float) -> ArtShare? = { ArtShare(0.04f, 0.04f, 0.88f, 0.87f) }
        val arts = listOf(none to none, fullArt to fullArt, tight to wide2)
        // (smallest text of a reduced box, of a full box): no floor, the above style (10.5 / 8.5 sp), the inside style (9.5 / 8 sp)
        val floors = listOf(0f to 0f, 10.5f to 8.5f, 9.5f to 8f)
        var n = 0
        var fallback = 0
        var smallerSprites = 0
        var smallerBoxes = 0
        var reducedPlans = 0
        for (b in BackdropCatalog.ALL) for ((w, h, s) in listOf(Triple(412f, 640f, 1f), Triple(412f, 265f, 1f), Triple(412f, 134f, 0.634f), Triple(412f, 146f, 1f),
            Triple(365f, 266f, 1f), Triple(860f, 253f, 1f), Triple(206f, 136f, 1f), Triple(240f, 130f, 0.61f), Triple(246f, 138f, 0.65f))) {
            val vw = w / s; val vh = h / s
            val floor = Math.ceil((8f / s * 10f).toDouble()).toFloat() / 10f
            val st = fit(b.id, vw, vh)
            val k0 = if (s < 1f || h <= 400f) 1f else min(vw / 300f, vh / 430f).coerceIn(1f, 1.4f)
            for ((wa, pa) in arts) for (inset in listOf(0f, 6f)) for ((minSp, fullSp) in floors) {
                // reduced boxes are shorter (R9 wave 3); without a readability floor they are never used
                val p = StageGeo.plan(vw, vh, st, 150f, 64f, 156f, 70f, k0, s, floor, inset, wa, pa, 56f, 58f, minSp, fullSp)
                val o = Oracle.plan(vw, vh, st, 150f, 64f, 156f, 70f, k0, s, floor, inset, wa, pa, 56f, 58f, minSp, fullSp)
                val tag = "${b.id} ${w}x$h s=$s inset=$inset text $minSp/$fullSp"
                assertEquals("$tag clear", o.ok, p.clear)
                assertEquals("$tag f", o.f, p.spriteScale, 0.0001f)
                assertEquals("$tag k", o.k, p.k, 0.0005f)
                assertEquals("$tag reduced", o.reduced, p.reduced)
                // the readability floor itself: the smallest text of what is drawn stays on 8 sp on screen (when the floor can hold at all)
                if (minSp > 0f) {
                    val sp = if (p.reduced) minSp else fullSp
                    assertTrue("$tag: text ${max(sp, floor) * s * p.k} sp on screen", max(sp, floor) * s * p.k >= PanelFit.MIN_SP - 0.01f)
                }
                for ((name, a, c) in listOf(Triple("wild", o.wild, p.wildCard), Triple("ours", o.you, p.playerCard))) {
                    assertEquals("$tag $name x", a.x, c.x, 0.01f); assertEquals("$tag $name y", a.y, c.y, 0.01f)
                    assertEquals("$tag $name w", a.w, c.w, 0.01f); assertEquals("$tag $name h", a.h, c.h, 0.01f)
                }
                n++
                if (!p.clear) fallback++
                if (p.spriteScale < 1f) smallerSprites++
                if (p.clear && p.k < StageGeo.boxScales(vh * s, s, floor, k0, minSp, fullSp).start - 0.001f) smallerBoxes++
                if (p.reduced) reducedPlans++
            }
        }
        println("D1 oracle: $n combos, $fallback fallbacks, $smallerSprites with smaller sprites, $smallerBoxes with shrunk boxes, $reducedPlans reduced")
        assertTrue(n > 500)
        assertTrue("the grid reaches the shrink steps", smallerSprites > 0 && smallerBoxes > 0)
        assertTrue("the grid reaches reduced boxes", reducedPlans > 0)
    }

    private fun abs(v: Float) = if (v < 0f) -v else v

    /** A straight, allocation-happy port of `autoRule` in docs/design/boxes-layout.html (Box objects, lists), the independent oracle. */
    private object Oracle {
        class Out(val wild: ArenaGeo.Box, val you: ArenaGeo.Box, val k: Float, val f: Float, val ok: Boolean, val reduced: Boolean = false)

        private class Spot(val top: Boolean, val a: Char, val level: Int)

        private fun spots(primTop: Boolean): List<Spot> {
            val p = primTop
            return listOf(Spot(p, 'c', 0), Spot(p, 'n', 1), Spot(p, 'L', 2), Spot(p, 'R', 2),
                Spot(!p, 'c', 3), Spot(!p, 'n', 3), Spot(!p, 'L', 3), Spot(!p, 'R', 3))
        }

        private fun grow(b: ArenaGeo.Box, d: Float) = ArenaGeo.Box(b.x - d, b.y - d, b.w + 2f * d, b.h + 2f * d)

        private fun area(a: ArenaGeo.Box, b: ArenaGeo.Box): Float {
            val dx = min(a.right, b.right) - max(a.x, b.x)
            val dy = min(a.bottom, b.bottom) - max(a.y, b.y)
            return if (dx > 0f && dy > 0f) dx * dy else 0f
        }

        private fun artOf(of: (Float) -> ArtShare?, fx: Float, fy: Float, side: Float): ArenaGeo.Box =
            (of(side) ?: ArtShare.DEFAULT).inFrame(fx - side / 2f, fy - side, side)

        private fun range(hi: Float, lo: Float): List<Float> {
            val out = ArrayList<Float>()
            var k = hi
            while (k > lo + 0.001f) { out += k; k -= 0.05f }
            out += lo
            return out
        }

        /** The mockup's `place`. */
        private fun place(sp: Spot, feetX: Float, art: ArenaGeo.Box, bw: Float, bh: Float, vw: Float, vh: Float, inset: Float, g: Float): ArenaGeo.Box {
            val x = when (sp.a) {
                'c', 'n' -> max(inset, min(vw - inset - bw, feetX - bw / 2f))
                'L' -> inset
                else -> vw - inset - bw
            }
            val y0 = if (sp.a == 'n') (if (sp.top) art.y - g - bh else art.bottom + g) else if (sp.top) inset else vh - inset - bh
            return ArenaGeo.Box(x, max(inset, min(vh - inset - bh, y0)), bw, bh)
        }

        fun anyOk(vw: Float, vh: Float, st: Stage, ww: Float, wh: Float, pw: Float, ph: Float, k: Float, f: Float, g: Float, inset: Float,
                  wa: (Float) -> ArtShare?, pa: (Float) -> ArtShare?): Boolean {
            val tall = vh > vw * 1.1f
            val wArt = artOf(wa, st.wildFeetX, st.wildFeetY, st.wildFrame * f)
            val pArt = artOf(pa, st.playerFeetX, st.playerFeetY, st.playerFrame * f)
            for (oy in spots(!tall)) for (ow in spots(tall)) {
                val y = place(oy, st.playerFeetX, pArt, pw * k, ph * k, vw, vh, inset, g)
                val w = place(ow, st.wildFeetX, wArt, ww * k, wh * k, vw, vh, inset, g)
                if (ok(y, w, wArt, pArt, g)) return true
            }
            return false
        }

        private fun ok(y: ArenaGeo.Box, w: ArenaGeo.Box, wa: ArenaGeo.Box, pa: ArenaGeo.Box, g: Float) =
            !grow(y, g).hits(wa) && !grow(y, g).hits(pa) && !grow(w, g).hits(wa) && !grow(w, g).hits(pa) && !grow(y, g).hits(w)

        /**
         * [wh2] / [ph2]: the heights of the reduced boxes, [minSp] / [fullSp]: the smallest text of a reduced / a full box (R9 wave 3, the
         * one thing added to the mockup's rule: no scale under the one a text is readable at, and shorter boxes under the full one's).
         */
        fun plan(vw: Float, vh: Float, st: Stage, ww: Float, wh: Float, pw: Float, ph: Float, k0: Float, s: Float, floor: Float, inset: Float,
                 wa: (Float) -> ArtShare?, pa: (Float) -> ArtShare?, wh2: Float, ph2: Float, minSp: Float, fullSp: Float): Out {
            val bigH = vh * s
            val target = max(0.55f, min(1f, 0.55f + (bigH - 130f) / 130f * 0.45f))
            val already = s * max(1f, floor / 12f)
            fun kv(scr: Float) = k0 * min(1f, scr / already)
            fun readable(sp: Float) = if (sp <= 0f) 0f else min(1f, 8f / (max(sp, floor) * max(s, 0.01f)))
            val kRead = readable(minSp)
            val kFull = max(kRead, readable(fullSp))
            val kStart = max(kv(target), kRead)
            val kMin = max(min(kStart, kv(0.55f)), kRead)
            val kNear = max(min(kStart, kv(0.85f)), kRead)
            fun red(k: Float) = k < kFull - 0.0001f
            fun wH(k: Float) = if (red(k)) wh2 else wh
            fun pH(k: Float) = if (red(k)) ph2 else ph
            val g = max(4f, 8f * min(1f, bigH / 260f)) / s
            val tall = vh > vw * 1.1f
            val phases = listOf(Triple(0, kStart, kNear), Triple(1, kStart, kNear), Triple(2, kStart, kNear), Triple(2, kNear, kMin), Triple(3, kStart, kMin))
            val oursSpots = spots(!tall)
            val wildSpots = spots(tall)
            var worst: Out? = null
            var worstScore = Double.MAX_VALUE
            for (f in floatArrayOf(1f, 0.9f, 0.8f, 0.7f, 0.6f)) {
                val wArt = artOf(wa, st.wildFeetX, st.wildFeetY, st.wildFrame * f)
                val pArt = artOf(pa, st.playerFeetX, st.playerFeetY, st.playerFrame * f)
                for ((level, hi, lo) in phases) for (k in range(hi, lo)) for (oy in oursSpots) for (ow in wildSpots) {
                    if (oy.level > level || ow.level > level) continue
                    val y = place(oy, st.playerFeetX, pArt, pw * k, pH(k) * k, vw, vh, inset, g)
                    val w = place(ow, st.wildFeetX, wArt, ww * k, wH(k) * k, vw, vh, inset, g)
                    if (ok(y, w, wArt, pArt, g)) return Out(w, y, k, f, true, red(k))
                }
                for (oy in oursSpots) for (ow in wildSpots) {
                    val y = place(oy, st.playerFeetX, pArt, pw * kMin, pH(kMin) * kMin, vw, vh, inset, g)
                    val w = place(ow, st.wildFeetX, wArt, ww * kMin, wH(kMin) * kMin, vw, vh, inset, g)
                    val cover = area(y, wArt) + area(y, pArt) + area(w, wArt) + area(w, pArt) + area(y, w)
                    val near = area(grow(y, g), wArt) + area(grow(y, g), pArt) + area(grow(w, g), wArt) + area(grow(w, g), pArt) + area(grow(y, g), w)
                    val sc = cover.toDouble() * 1e6 + near
                    if (sc < worstScore) { worstScore = sc; worst = Out(w, y, kMin, f, false, red(kMin)) }
                }
            }
            return worst!!
        }
    }
}
