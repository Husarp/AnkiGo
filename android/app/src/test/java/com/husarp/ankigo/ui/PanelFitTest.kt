package com.husarp.ankigo.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.ceil
import kotlin.math.min

// TICKET U-05: the battle panel's scale, step-out order, text floor and card layout, in pure maths.
class PanelFitTest {
    private fun fit(
        w: Int, h: Int, small: Boolean = false, statsBarSmall: Boolean = true, compact: Boolean = false,
        strip: Boolean = false, corner: Float = 0f, tools: Boolean = true, own: Boolean = false,
    ) = PanelFit.decide(w.toFloat(), h.toFloat(), small, statsBarSmall, compact, strip, corner, tools, own)

    /** What the window keeps: the stats bar (S), the log line (L), the XP numbers (X). */
    private fun kept(f: PanelFit.Fit) = (if (f.stats) "S" else "-") + (if (f.log) "L" else "-") + (if (f.xp) "X" else "-")

    // ---------------------------------------------------------------- scale

    @Test fun roomyWindowsAreNotScaled() {
        for ((w, h) in listOf(412 to 869, 412 to 400, 869 to 412, 800 to 1280)) {
            val f = fit(w, h)
            assertEquals("$w x $h", 1f, f.scale, 0f)
            assertEquals("SLX", kept(f))
            assertEquals(w.toFloat(), f.virtualW, 0.01f)
            assertEquals(h.toFloat(), f.virtualH, 0.01f)
            assertTrue(f.ok)
        }
    }

    @Test fun scaleNeverGrows() {
        val f = fit(2000, 3000)
        assertEquals(1f, f.scale, 0f)
        assertEquals(8f, f.floorSp, 0f)
    }

    @Test fun shortSplitScreenScalesAndKeepsTheStatsBar() {
        // 412 x 300 and 412 x 250 (split screen under AnkiDroid): everything shrinks together, nothing leaves
        for (h in listOf(300, 250)) {
            val f = fit(412, h)
            assertTrue("h=$h scale ${f.scale}", f.scale < 1f && f.scale > 0.7f)
            assertEquals("SLX", kept(f))
            assertEquals(412f / f.scale, f.virtualW, 0.5f)
            assertEquals(h / f.scale, f.virtualH, 0.5f)
            assertTrue(f.ok)
        }
        // the height is about what the parts need: a 212 dp arena + the log card + the stats bar (about 347 dp)
        assertEquals(300f / 347f, fit(412, 300).scale, 0.02f)
    }

    @Test fun narrowPhoneScalesSlightlyForTheStatsBar() {
        // the five chips need 376 dp: a 360 dp phone is drawn at 360 / 376
        val f = fit(360, 800)
        assertEquals(360f / 376f, f.scale, 0.005f)
        assertEquals("SLX", kept(f))
        assertFalse(f.middle) // the sprites shrink a little so the corners still work
    }

    // ---------------------------------------------------------------- floor

    @Test fun floorIsEightSpOverScaleRoundedUp() {
        assertEquals(8f, fit(412, 869).floorSp, 0f)
        for ((w, h) in listOf(412 to 250, 320 to 180, 240 to 190, 340 to 480, 200 to 200)) for (small in listOf(false, true)) {
            val f = fit(w, h, small = small)
            assertEquals(ceil(8f / f.scale * 10f) / 10f, f.floorSp, 0.0501f)
            // a 9 sp chip, a 14 sp name, anything: drawn at least 8 sp on screen
            for (base in listOf(9f, 10f, 11f, 12f, 14f)) assertTrue("$w x $h base $base", TextMath.at(base, f.floorSp) * f.scale >= 8f - 0.001f)
        }
    }

    @Test fun textMath() {
        assertEquals(14f, TextMath.at(14f, 8f), 0f)
        assertEquals(16f, TextMath.at(9f, 16f), 0f)
        assertEquals(1f, TextMath.bar(8f), 0f)
        assertEquals(1.5f, TextMath.bar(18f), 0.001f)
        assertEquals(13.64f, TextMath.line(10f), 0.001f)
    }

    // ---------------------------------------------------------------- step-out order at the listed sizes

    @Test fun smallWindowsFullCards() {
        // what stays (S stats bar, L log line, X xp numbers) for floating windows with the full cards (pref off), stats bar pref on;
        // a small window under 260 dp on its short side never gets them (see the next tests)
        val expect = listOf(Triple(340, 480, "SLX"), Triple(412, 400, "SLX"), Triple(412, 869, "SLX"))
        for ((w, h, parts) in expect) {
            val f = fit(w, h, small = true)
            assertEquals("$w x $h", parts, kept(f))
            assertTrue("$w x $h ok", f.ok)
            assertFalse(f.compactCards)
            assertFalse(f.tools)
            assertEquals(1, f.logLines)
        }
    }

    @Test fun smallWindowsUnder260UseCompactCardsWhateverThePref() {
        // picture-in-picture sizes: compact cards even with the pref off, and the same decision as with the pref on
        for ((w, h) in listOf(240 to 135, 320 to 180, 200 to 200, 240 to 190, 412 to 250, 259 to 700)) {
            val off = fit(w, h, small = true, compact = false)
            assertTrue("$w x $h", off.compactCards)
            assertTrue("$w x $h ok", off.ok)
            assertEquals("$w x $h", fit(w, h, small = true, compact = true), off)
        }
        // not in a normal window, and not from 260 dp on: the pref decides
        for ((w, h) in listOf(240 to 135, 200 to 200)) assertFalse(fit(w, h, small = false, compact = false).compactCards)
        for ((w, h) in listOf(340 to 480, 260 to 260, 412 to 400)) {
            assertFalse("$w x $h", fit(w, h, small = true, compact = false).compactCards)
            assertTrue("$w x $h", fit(w, h, small = true, compact = true).compactCards)
        }
    }

    @Test fun smallWindowsCompactCards() {
        // compact cards leave much more room: the stats bar fits down to 320 x 180 and the log down to 240 x 135
        val expect = listOf(
            Triple(200, 200, "-LX"), Triple(240, 135, "-LX"), Triple(320, 180, "SLX"), Triple(240, 190, "-LX"),
            Triple(340, 480, "SLX"), Triple(412, 250, "SLX"), Triple(412, 400, "SLX"), Triple(412, 869, "SLX"),
        )
        for ((w, h, parts) in expect) {
            val f = fit(w, h, small = true, compact = true)
            assertEquals("$w x $h", parts, kept(f))
            assertTrue(f.compactCards)
            assertTrue(f.ok)
        }
    }

    @Test fun statsBarPrefOnlyAffectsSmallWindows() {
        for ((w, h) in listOf(412 to 400, 412 to 250, 340 to 480)) {
            assertTrue("a normal window ignores the pref", fit(w, h, small = false, statsBarSmall = false).stats)
            assertFalse("a small window obeys it", fit(w, h, small = true, statsBarSmall = false).stats)
            assertTrue(fit(w, h, small = true, statsBarSmall = true).stats)
        }
    }

    @Test fun partsStepOutInTheFixedOrder() {
        for (small in listOf(false, true)) for (compact in listOf(false, true)) for (statsBar in listOf(true, false)) {
            for (w in 120..900 step 20) for (h in 100..900 step 20) {
                val f = fit(w, h, small, statsBar, compact)
                val tag = "$w x $h small=$small compact=$compact statsBar=$statsBar"
                // the stats bar goes first, then the log line, then the XP numbers
                if (!f.log) assertFalse("$tag: log gone but stats bar kept", f.stats)
                if (!f.xp) assertFalse("$tag: xp gone but log kept", f.log)
                assertTrue("$tag: scale ${f.scale}", f.scale > 0f && f.scale <= 1f)
                assertEquals(tag, w / f.scale, f.virtualW, 0.01f)
                assertEquals(tag, h / f.scale, f.virtualH, 0.01f)
                // the floor holds for every text from 9 sp up
                assertTrue(tag, TextMath.at(9f, f.floorSp) * f.scale >= 8f - 0.001f)
                if (small && !statsBar) assertFalse(tag, f.stats)
            }
        }
    }

    @Test fun biggerWindowsNeverKeepLess() {
        for (small in listOf(false, true)) for (compact in listOf(false, true)) for (w in listOf(240, 320, 412)) {
            var before = -1
            for (h in 100..700 step 10) {
                val kept = kept(fit(w, h, small, true, compact)).count { it != '-' }
                assertTrue("$w x $h small=$small compact=$compact: $before -> $kept", kept >= before)
                before = kept
            }
        }
    }

    @Test fun cardsNeverCoverASpriteFromSmallPhoneCornersUp() {
        for (small in listOf(false, true)) for (compact in listOf(false, true)) for (statsBar in listOf(true, false)) {
            for (w in 160..900 step 20) for (h in 120..900 step 20) {
                assertTrue("$w x $h small=$small compact=$compact", fit(w, h, small, statsBar, compact).ok)
            }
        }
    }

    @Test fun tinyOrEmptyWindowsDoNotBreak() {
        for ((w, h) in listOf(0 to 0, 0 to 300, 300 to 0, 20 to 20, 60 to 40)) {
            val f = fit(w, h)
            assertTrue(f.scale > 0f)
            assertTrue(f.floorSp >= 8f)
        }
    }

    // ---------------------------------------------------------------- what costs room

    @Test fun stripGripAndToolsCostRoom() {
        assertTrue(fit(412, 300, strip = true).scale < fit(412, 300).scale)
        // the floating window's grip keeps room free at the right end of the chips: a 340 dp window scales a bit more
        assertTrue(fit(340, 480, small = true, corner = 26f).scale < fit(340, 480, small = true).scale)
        // without the menu and tool buttons the goal row is shorter
        assertTrue(fit(412, 300, tools = false).scale > fit(412, 300, tools = true).scale)
    }

    @Test fun heightsMatchTheViewsAtDesignSize() {
        // measured on the real views at floor 8 (PanelFitShots.calibration keeps this honest at other floors)
        assertEquals(52.6f, PanelFit.Heights.log(2, 8f) - 8f, 0.3f)
        assertEquals(75f, PanelFit.Heights.footer(8f, true), 0.5f)
        assertEquals(356f, PanelFit.Heights.chips(8f), 0.5f)
        val c = ArenaGeo.cards(8f, false, true)
        assertEquals(87.4f, c.wildH, 0.5f)    // HP number beside the bar (R3-X1), the 14 dp status row under it
        assertEquals(91.1f, c.playerH, 0.5f)  // ... and our XP line under the status row
        assertEquals(166f, c.wildW, 0f)
        assertEquals(170f, c.playerW, 0f)
    }

    // ---------------------------------------------------------------- cards: corners or the middle column

    private fun plan(w: Float, h: Float, compact: Boolean = false, floor: Float = 8f, xp: Boolean = true, own: Boolean = false) =
        ArenaGeo.plan(w, h, ArenaGeo.cards(floor, compact, xp), compact, own)

    private fun noCardCoversASprite(p: ArenaGeo.Plan, tag: String) {
        for ((card, name) in listOf(p.wildCard to "wild card", p.playerCard to "player card")) {
            assertFalse("$tag: $name covers the wild sprite", card.hits(p.wildSprite.art()))
            assertFalse("$tag: $name covers our sprite", card.hits(p.playerSprite.art()))
        }
        assertFalse("$tag: the cards touch", p.wildCard.hits(p.playerCard))
    }

    @Test fun roomyArenaKeepsTheCornerLayout() {
        for ((w, h) in listOf(412f to 600f, 412f to 267f, 360f to 530f, 869f to 340f)) {
            val p = plan(w, h)
            assertFalse("$w x $h", p.middle)
            assertTrue(p.fits)
            noCardCoversASprite(p, "$w x $h")
            // the mockup's corners: wild card top-left, ours bottom-right
            assertTrue(p.wildCard.x < w / 4 && p.wildCard.y < 20f)
            assertTrue(p.playerCard.right > w * 0.7f && p.playerCard.bottom > h - 20f)
        }
    }

    @Test fun shortArenaUsesTheMiddleColumn() {
        for ((w, h) in listOf(412f to 200f, 477f to 200f, 573f to 200f, 377f to 200f, 344f to 200f)) {
            val p = plan(w, h)
            assertTrue("$w x $h", p.middle)
            noCardCoversASprite(p, "$w x $h")
            // our sprite at the left, the wild one at the right, both cards stacked between them (wild on top)
            assertTrue(p.playerSprite.right <= p.wildCard.x + 0.5f && p.playerSprite.right <= p.playerCard.x + 0.5f)
            assertTrue(p.wildSprite.x >= p.wildCard.right - 0.5f && p.wildSprite.x >= p.playerCard.right - 0.5f)
            assertTrue(p.wildCard.bottom <= p.playerCard.y + 0.5f)
            assertEquals(p.wildCard.x, p.playerCard.x, 0.01f)
            assertEquals(p.wildCard.w, p.playerCard.w, 0.01f)
            assertTrue(p.wildCard.y >= 0f && p.playerCard.bottom <= h + 0.5f)
        }
    }

    @Test fun theSwitchFollowsTheGeometryNotAWindowSize() {
        // same width, only the height changes: corners while the cards clear the sprites, the middle column when they do not
        assertFalse(plan(412f, 300f).middle)
        assertFalse(plan(412f, 230f).middle)
        assertTrue(plan(412f, 190f).middle)
        // same short arena, smaller cards: compact cards keep the corners where the full cards could not
        assertFalse(plan(412f, 190f, compact = true).middle)
        // wider does not help a short arena: the cards still reach into the sprites' height
        assertTrue(plan(700f, 190f).middle)
        // bigger text (a raised floor) makes the cards bigger: the corners give up earlier
        assertFalse(plan(412f, 210f, floor = 8f).middle)
        assertTrue(plan(412f, 210f, floor = 14f).middle)
    }

    @Test fun cornersShrinkTheSpritesALittleBeforeGivingUp() {
        val wide = plan(412f, 600f)
        val narrow = plan(360f, 600f)
        assertFalse(narrow.middle)
        assertTrue(narrow.a < wide.a)
        assertTrue(narrow.a >= 0.85f)
    }

    @Test fun cardsFollowTheTextFloor() {
        val normal = ArenaGeo.cards(8f, false, true)
        val raised = ArenaGeo.cards(16f, false, true)
        assertTrue(raised.wildW > normal.wildW && raised.wildH > normal.wildH && raised.playerH > normal.playerH)
        // compact cards: name + thin HP bar, no types, no XP
        val compact = ArenaGeo.cards(8f, true, true)
        assertTrue(compact.wildH < normal.wildH * 0.7f && compact.playerH < normal.playerH * 0.75f)
        assertTrue(compact.wildW < normal.wildW)
        // the XP numbers are what makes our card wide
        assertTrue(ArenaGeo.cards(8f, false, false).playerW < normal.playerW)
    }

    @Test fun statusRowIsPartOfEveryCardAndGrowsWithTheText() {
        // item 95: the badge row under the HP bar (14 dp, 12 on compact cards) is reserved in every card, even with no status
        assertEquals(14f, ArenaGeo.statusRow(8f, false), 0f)
        assertEquals(12f, ArenaGeo.statusRow(8f, true), 0f)
        assertEquals(21f, ArenaGeo.statusRow(18f, false), 0.01f)
        for (compact in listOf(false, true)) {
            val small = ArenaGeo.cards(8f, compact, true)
            val big = ArenaGeo.cards(18f, compact, true)
            val grow = ArenaGeo.statusRow(18f, compact) - ArenaGeo.statusRow(8f, compact)
            assertTrue("compact=$compact wild", big.wildH - small.wildH > grow)
            assertTrue("compact=$compact player", big.playerH - small.playerH > grow)
        }
    }

    @Test fun planIsCleanWhereverItSaysItFits() {
        for (compact in listOf(false, true)) for (floor in listOf(8f, 11f, 14f, 18f)) for (xp in listOf(true, false)) {
            for (w in 200..800 step 20) for (h in 100..700 step 20) {
                val p = plan(w.toFloat(), h.toFloat(), compact, floor, xp)
                if (!p.fits) continue
                val tag = "$w x $h compact=$compact floor=$floor xp=$xp"
                noCardCoversASprite(p, tag)
                assertTrue(tag, p.wildCard.x >= -0.01f && p.playerCard.right <= w + 0.01f && p.wildCard.y >= -0.01f && p.playerCard.bottom <= h + 0.5f)
            }
        }
    }

    @Test fun middleLayoutFitsWhenTheStackAndTheSpritesDo() {
        val c = ArenaGeo.cards(8f, false, true)
        val stack = c.wildH + 6f + c.playerH
        assertTrue(plan(400f, stack + 13f).fits)
        assertFalse(plan(400f, stack + 6f).fits)
        assertFalse(plan(200f, 400f).fits) // 200 dp cannot hold a 170 dp column and two sprites
    }

    // ---------------------------------------------------------------- the arena grows; each card under its own Pokemon

    /** Everything inside the arena, no card on a sprite's picture or on the other card, the two pictures apart. */
    private fun nothingOverlaps(p: ArenaGeo.Plan, w: Float, h: Float, tag: String) {
        noCardCoversASprite(p, tag)
        assertFalse("$tag: the two pictures touch", p.wildSprite.art().hits(p.playerSprite.art()))
        for (b in listOf(p.wildCard, p.playerCard, p.wildSprite.art(), p.playerSprite.art()))
            assertTrue("$tag: outside the arena", b.x >= -0.01f && b.y >= -0.01f && b.right <= w + 0.01f && b.bottom <= h + 0.01f)
    }

    @Test fun tallPhoneArenaGrowsAndEachCardSitsUnderItsOwnPokemon() {
        // the phone's arena (the 412 x 869 window less the log, the stats bar and the bottom bar) and a shorter one
        for ((w, h) in listOf(412f to 680f, 412f to 600f)) {
            val p = plan(w, h, own = true)
            assertEquals("$w x $h", ArenaGeo.Layout.UNDER, p.layout)
            assertTrue(p.fits && !p.middle)
            assertTrue("$w x $h: a ${p.a}", p.a > 1.5f)
            nothingOverlaps(p, w, h, "$w x $h")
            // the wild card in the right half under the wild sprite (right-aligned with it), ours in the left half with our sprite right above it
            assertTrue(p.wildCard.x + p.wildCard.w / 2 > w / 2 && p.playerCard.x + p.playerCard.w / 2 < w / 2)
            assertTrue(p.wildCard.y >= p.wildSprite.bottom && p.wildCard.y < p.wildSprite.bottom + 6f)
            assertEquals(p.wildSprite.right, p.wildCard.right, 0.01f)
            assertTrue(p.playerSprite.bottom <= p.playerCard.y && p.playerCard.y < p.playerSprite.bottom + 6f)
            assertEquals(p.playerSprite.x, p.playerCard.x, 0.01f)
            assertTrue(p.wildSprite.bottom < p.playerSprite.y + p.playerSprite.h / 2)
        }
        // the free height also makes the cards bigger (up to 1.4 x), the sprites first
        val tall = plan(412f, 680f, own = true)
        assertTrue("k ${tall.k}", tall.k > 1.2f)
        assertEquals(tall.wildCard.w, ArenaGeo.cards(8f, false, true).wildW * tall.k, 0.01f)
        assertTrue(plan(412f, 869f, own = true).a >= tall.a)
    }

    @Test fun classicCornersGrowToo() {
        val p = plan(412f, 600f, own = false)
        assertEquals(ArenaGeo.Layout.CORNERS, p.layout)
        assertTrue("a ${p.a}", p.a >= 1.4f)
        nothingOverlaps(p, 412f, 600f, "corners")
        assertTrue(p.wildCard.x < 103f && p.wildCard.y < 20f)
        assertTrue(p.playerCard.right > 412f * 0.7f && p.playerCard.bottom > 600f - 20f)
    }

    @Test fun shortArenasKeepEachCardWithItsPokemonOrTheMiddleColumn() {
        // the split-screen arena (about 212 dp): the wild group top-right, ours bottom-left, the sprites a bit under the mockup's size
        for (h in listOf(213f, 230f, 267f)) {
            val p = plan(412f, h, own = true)
            assertEquals("h=$h", ArenaGeo.Layout.UNDER, p.layout)
            assertTrue("h=$h a ${p.a}", p.a >= 0.85f)
            nothingOverlaps(p, 412f, h, "412 x $h")
        }
        // narrow and short: the cards beside the Pokemon (the wild card in the top row, ours in the bottom row)
        val b = plan(310f, 220f, own = true)
        assertEquals(ArenaGeo.Layout.BESIDE, b.layout)
        assertTrue(b.fits)
        nothingOverlaps(b, 310f, 220f, "beside")
        assertTrue(b.wildCard.y < 12f && b.wildCard.right <= b.wildSprite.art().x + 0.01f)
        assertTrue(b.playerCard.bottom > 220f - 14f && b.playerCard.x >= b.playerSprite.art().right - 0.01f)
        // too short for any of that: the middle column as before (also when the pref is off)
        for (own in listOf(true, false)) for ((w, h) in listOf(344f to 200f)) {
            val p = plan(w, h, own = own)
            assertTrue("$w x $h own=$own", p.middle && p.fits)
            assertEquals(1f, p.k, 0f)
        }
    }

    @Test fun tabletSpritesStayUnderFortyPercentOfTheHeightAndCardsUnderOnePointFour() {
        for (own in listOf(true, false)) for ((w, h) in listOf(800f to 1100f, 1200f to 800f, 600f to 1000f, 1000f to 1600f)) {
            val p = plan(w, h, own = own)
            assertTrue("$w x $h own=$own: ${p.layout}", p.fits && !p.middle)
            assertTrue("$w x $h a ${p.a}", p.a <= 0.4f * h / 128f + 0.001f && p.a <= 4f && p.a >= 1.5f)
            assertTrue("$w x $h k ${p.k}", p.k <= 1.4f + 0.001f && p.k >= 1f)
            nothingOverlaps(p, w, h, "$w x $h own=$own")
        }
        val t = plan(800f, 1100f, own = true)
        assertTrue(t.a > 3f && t.k > 1.3f)
    }

    @Test fun narrowAndTinyArenasStillGetTheMiddleColumn() {
        // as before (the classic rule): 200 dp cannot hold a 170 dp column and two sprites
        assertFalse(plan(200f, 400f, own = false).fits)
        for ((w, h) in listOf(160f to 120f, 100f to 60f, 60f to 40f)) assertTrue("$w x $h", plan(w, h, own = true).middle)
    }

    @Test fun anArenaNeverGetsWorseThanTheClassicLayout() {
        for (compact in listOf(false, true)) for (floor in listOf(8f, 14f)) for (w in 200..900 step 20) for (h in 100..900 step 20) {
            val c = ArenaGeo.cards(floor, compact, true)
            val own = ArenaGeo.plan(w.toFloat(), h.toFloat(), c, compact, true)
            val classic = ArenaGeo.plan(w.toFloat(), h.toFloat(), c, compact, false)
            val tag = "$w x $h compact=$compact floor=$floor"
            if (classic.fits) assertTrue("$tag: classic fits, own does not", own.fits)
            if (classic.layout != ArenaGeo.Layout.MIDDLE) assertTrue("$tag: classic has no middle column, own has", !own.middle)
            if (own.layout != ArenaGeo.Layout.MIDDLE) { assertTrue(tag, own.fits); assertTrue(tag, own.k in 1f..1.4001f) }
        }
    }

    @Test fun ownPlansAreCleanAndTheSpritesNeverShrinkUnderTheClassicSize() {
        for (own in listOf(true, false)) for (compact in listOf(false, true)) for (floor in listOf(8f, 14f)) for (w in 200..900 step 20) for (h in 100..1000 step 20) {
            val p = plan(w.toFloat(), h.toFloat(), compact, floor, own = own)
            if (!p.fits || p.middle) continue
            val tag = "$w x $h own=$own compact=$compact floor=$floor ${p.layout}"
            nothingOverlaps(p, w.toFloat(), h.toFloat(), tag)
            assertTrue(tag, p.a >= min((h / ArenaGeo.refH(compact)).coerceIn(0.66f, 1.5f), 0.85f) - 0.001f)
        }
    }

    @Test fun everyWindowStillFitsWithCardsUnderTheirOwnPokemon() {
        for (small in listOf(false, true)) for (compact in listOf(false, true)) for (statsBar in listOf(true, false)) {
            for (w in 160..900 step 20) for (h in 120..900 step 20) {
                assertTrue("$w x $h small=$small compact=$compact", fit(w, h, small, statsBar, compact, own = true).ok)
            }
        }
    }
}
