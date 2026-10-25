package com.husarp.ankigo.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min

/**
 * R19-A (PLAN 277 + 276), the pure half: the wild info box stands on its outer edge in the tall panel and the NEW sticker is on its
 * corner, the sticker moves with the box, the "Bigger info boxes" setting makes the boxes bigger only where the arena has room and
 * never over a Pokémon. (The layout half, that the boxes really follow the plan when only the arena asks for a new layout, is
 * `shots.R19AInfoBoxShots`.) The other-side-last rule itself is in [StageGeoTest].
 */
class R19AInfoBoxTest {
    private val c = ArenaGeo.cards(0f, false, true)
    private val cr = ArenaGeo.cards(0f, false, true, reduced = true)

    /** A tall art box like a Shelmet's shell: nearly the whole frame. */
    private val tallArt: (Float) -> ArtShare? = { ArtShare(0.12f, 0.04f, 0.76f, 0.96f) }
    private val midArt: (Float) -> ArtShare? = { ArtShare(0.15f, 0.2f, 0.7f, 0.8f) }

    private fun fit(id: String, w: Float, h: Float) = StageFit.fit(BackdropCatalog.byId(id)!!, w, h, 1f)!!

    private fun k0(w: Float, h: Float) = min(w / 300f, h / 430f).coerceIn(1f, 1.4f)

    private fun plan(id: String, w: Float, h: Float, boost: Float = 1f, wild: (Float) -> ArtShare? = tallArt, ours: (Float) -> ArtShare? = midArt) =
        StageGeo.plan(w, h, fit(id, w, h), c.wildW, c.wildH, c.playerW, c.playerH, k0(w, h), wildArt = wild, playerArt = ours,
            wildCardHReduced = cr.wildH, playerCardHReduced = cr.playerH, boost = boost)

    private val tallPanels = listOf(412f to 692f, 360f to 623f, 412f to 760f, 800f to 1100f)

    private fun grown(b: ArenaGeo.Box, d: Float) = ArenaGeo.Box(b.x - d, b.y - d, b.w + 2f * d, b.h + 2f * d)

    // ---------------------------------------------------------------- 277: the box on its edge, NEW on its corner

    @Test fun theWildBoxStandsOnTheTopEdgeOfEveryTallPanelWithATallArtBox() {
        for (b in BackdropCatalog.ALL) for ((w, h) in tallPanels) {
            val p = plan(b.id, w, h)
            val tag = "${b.id} ${w}x$h"
            assertTrue("$tag clear", p.clear)
            assertEquals("$tag: on the top edge", 0f, p.wildCard.y, 0.01f)
            assertEquals("$tag: its own side, centred on its Pokémon", 0, p.level)
        }
    }

    /** The NEW sticker's own size (8 sp, "NEW") and the numbers [ArenaView.updateNewTag] gives the rule, in px = dp here. */
    private fun place(box: NewTagRule.Box, panelW: Float, panelH: Float, k: Float) =
        NewTagRule.place(box, panelW, panelH, 0f, 4f * k, 3f * k, 26f, 11f, k, null)

    @Test fun theStickerSitsOnACornerOfTheWildBoxAtTheTopEdge() {
        for (b in BackdropCatalog.ALL) for ((w, h) in tallPanels) {
            val p = plan(b.id, w, h)
            val box = NewTagRule.Box(p.wildCard.x, p.wildCard.y, p.wildCard.right, p.wildCard.bottom)
            val s = place(box, w, h, p.k)
            val tag = "${b.id} ${w}x$h"
            assertTrue("$tag: the whole sticker is inside the panel", s.fits)
            assertTrue("$tag: the sticker is on the box (${s.bounds} vs $box)", s.bounds.hits(box.grow(2f * p.k)))
            // today's corner (the top left) is cut by the panel's top edge, so the rule's next corner is used
            assertFalse("$tag: not the corner that would leave the panel", s.corner == NewTagRule.Corner.TOP_LEFT)
        }
    }

    @Test fun theStickerMovesWithTheBoxNeverAloneWhereverTheRuleLaysTheBox() {
        val k = 1.37f
        val w = 412f; val h = 692f
        for (top in listOf(0f, 3f, 20f, 60f, 140f, 221f, 300f, 450f, 600f)) for (left in listOf(0f, 100f, 192f, 252f)) {
            val box = NewTagRule.Box(left, top, left + 160f * k, top + 59f * k)
            val s = place(box, w, h, k)
            assertTrue("box at ($left, $top): the sticker ${s.bounds} is on the box", s.bounds.hits(box.grow(3f * k + 1f)))
        }
        // the same box lower down: the sticker goes back to today's corner and rides along
        val high = place(NewTagRule.Box(192f, 0f, 412f, 81f), w, h, k)
        val low = place(NewTagRule.Box(192f, 221f, 412f, 302f), w, h, k)
        assertEquals(NewTagRule.Corner.TOP_LEFT, low.corner)
        assertEquals(188f, low.x + 0f, 6f)
        assertTrue("moved down with it", low.y > high.y + 150f)
    }

    // ---------------------------------------------------------------- 276: Bigger info boxes

    @Test fun theSettingNeedsRoomOnTheScreenAndIsOffByDefaultInTheNumbers() {
        assertEquals(1f, InfoBoxPrefs.factor(false, 412f, 692f), 0f)
        assertEquals(InfoBoxPrefs.BIG, InfoBoxPrefs.factor(true, 412f, 692f), 0f)
        assertEquals("a 360 x 640 phone's Battle tab (463 dp)", InfoBoxPrefs.BIG, InfoBoxPrefs.factor(true, 360f, 463f), 0f)
        // the Study panel's presets, the strip, picture-in-picture, the floating window, a narrow split-screen strip: no room
        for ((w, h) in listOf(412f to 265f, 412f to 210f, 412f to 146f, 240f to 135f, 320f to 180f, 367f to 212f, 206f to 136f, 869f to 330f)) {
            assertEquals("${w}x$h has no room", 1f, InfoBoxPrefs.factor(true, w, h), 0f)
        }
        assertEquals("a window drawn at 60 %: its on-screen size counts, not its virtual one", 1f, InfoBoxPrefs.factor(true, 412f * 0.6f, 692f * 0.6f), 0f)
    }

    @Test fun withTheSettingOnTheTallPanelsBoxesAreBiggerAndNeverOverAPokemon() {
        var bigger = 0
        for (b in BackdropCatalog.ALL) for ((w, h) in tallPanels) for ((wild, ours) in listOf(tallArt to midArt, midArt to tallArt)) {
            val off = plan(b.id, w, h, 1f, wild, ours)
            val on = plan(b.id, w, h, InfoBoxPrefs.factor(true, w, h), wild, ours)
            val tag = "${b.id} ${w}x$h"
            assertTrue("$tag: nothing is in the way with the setting on", on.clear)
            assertTrue("$tag: never smaller than without ${on.k} < ${off.k}", on.k >= off.k - 0.001f)
            assertTrue("$tag: the Pokémon are not made smaller for it (${on.spriteScale} < ${off.spriteScale})", on.spriteScale >= off.spriteScale - 0.001f)
            if (on.k > off.k + 0.05f) bigger++
            // the existing rules stay: inside the arena, a gap from both art boxes, ours off the wild box
            for ((name, box) in listOf("wild" to on.wildCard, "ours" to on.playerCard)) {
                assertTrue("$tag: $name box inside", box.x >= -0.01f && box.y >= -0.01f && box.right <= w + 0.01f && box.bottom <= h + 0.01f)
                assertFalse("$tag: $name box on the wild art", grown(box, on.gap - 0.01f).hits(on.wildArt))
                assertFalse("$tag: $name box on our art", grown(box, on.gap - 0.01f).hits(on.playerArt))
            }
            assertFalse("$tag: the two boxes touch", grown(on.playerCard, on.gap - 0.01f).hits(on.wildCard))
            assertEquals("$tag: the wild box is still on the top edge", 0f, on.wildCard.y, 0.01f)
        }
        println("R19-A: $bigger of 280 tall plans have bigger boxes with the setting on")
        assertTrue("the setting makes boxes bigger in many tall panels ($bigger)", bigger > 60)
    }

    @Test fun theBiggerStartIsAbout13TimesTodaysFullSizeAndNothingElseMoves() {
        val off = plan("forest", 412f, 692f)
        val on = plan("forest", 412f, 692f, InfoBoxPrefs.BIG)
        assertEquals("today's full size in the Battle tab", 1.37f, off.k, 0.01f)
        assertTrue("about 1.3 times it (${on.k})", on.k > off.k * 1.15f && on.k <= off.k * InfoBoxPrefs.BIG + 0.01f)
        // where the boxes may shrink to before a Pokémon does is unchanged: only the start is higher
        val a = StageGeo.boxScales(692f, 1f, 8f, 1.37f, 10.5f, 8.5f)
        val b = StageGeo.boxScales(692f, 1f, 8f, 1.37f, 10.5f, 8.5f, InfoBoxPrefs.BIG)
        assertEquals(a.near, b.near, 0f); assertEquals(a.min, b.min, 0f); assertEquals(a.full, b.full, 0f)
        assertTrue(b.start > a.start * 1.29f)
    }

    @Test fun aPanelWithoutRoomIsExactlyWhatItWasWithTheSettingOn() {
        for (b in BackdropCatalog.ALL) for ((w, h) in listOf(412f to 265f, 412f to 146f, 240f to 130f, 206f to 136f, 320f to 212f)) {
            val off = plan(b.id, w, h)
            val on = plan(b.id, w, h, InfoBoxPrefs.factor(true, w, h))
            assertEquals(off.k, on.k, 0f); assertEquals(off.spriteScale, on.spriteScale, 0f)
            assertEquals(off.wildCard.x, on.wildCard.x, 0f); assertEquals(off.wildCard.y, on.wildCard.y, 0f)
            assertEquals(off.playerCard.x, on.playerCard.x, 0f); assertEquals(off.playerCard.y, on.playerCard.y, 0f)
        }
    }

    @Test fun thePlainArenaGetsTheSameHigherTopScaleAndKeepsItsRules() {
        val tall = ArenaGeo.plan(412f, 692f, c, false, true, null, 1f)
        val big = ArenaGeo.plan(412f, 692f, c, false, true, null, InfoBoxPrefs.BIG)
        assertTrue(tall.fits && big.fits)
        assertTrue("bigger cards (${big.k} vs ${tall.k})", big.k > tall.k + 0.05f)
        assertTrue("the Pokémon are not smaller for it (${big.a} vs ${tall.a})", big.a >= tall.a - 0.001f)
        for (box in listOf(big.wildCard, big.playerCard)) assertTrue("inside", box.x >= -0.01f && box.y >= -0.01f && box.right <= 412.01f && box.bottom <= 692.01f)
        assertFalse(big.wildCard.hits(big.playerCard))
        assertFalse(big.wildCard.hits(big.wildSprite.art())); assertFalse(big.wildCard.hits(big.playerSprite.art()))
        assertFalse(big.playerCard.hits(big.wildSprite.art())); assertFalse(big.playerCard.hits(big.playerSprite.art()))
        // no boost = the default call, exactly
        val same = ArenaGeo.plan(412f, 692f, c, false, true)
        assertEquals(same.k, tall.k, 0f); assertEquals(same.a, tall.a, 0f)
        assertEquals(same.wildCard.x, tall.wildCard.x, 0f); assertEquals(same.playerCard.y, tall.playerCard.y, 0f)
        // a small plain arena: unchanged by the factor the arena itself would pass (1)
        val small = ArenaGeo.plan(240f, 135f, c, true, true, null, InfoBoxPrefs.factor(true, 240f, 135f))
        val smallOff = ArenaGeo.plan(240f, 135f, c, true, true)
        assertEquals(smallOff.k, small.k, 0f); assertEquals(smallOff.a, small.a, 0f)
    }
}
