package com.husarp.ankigo.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.ceil

// TICKET U-05: the battle panel's scale, step-out order, text floor and card layout, in pure maths.
class PanelFitTest {
    private fun fit(
        w: Int, h: Int, small: Boolean = false, statsBarSmall: Boolean = true, compact: Boolean = false,
        strip: Boolean = false, corner: Float = 0f, tools: Boolean = true,
    ) = PanelFit.decide(w.toFloat(), h.toFloat(), small, statsBarSmall, compact, strip, corner, tools)

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
            assertTrue("the cards use the middle column here", f.middle)
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
            assertEquals(ceil(80f / f.scale) / 10f, f.floorSp, 0.0501f)
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
        // what stays (S stats bar, L log line, X xp numbers) for picture-in-picture / floating windows, stats bar pref on
        val expect = listOf(
            Triple(200, 200, "-LX"), Triple(240, 135, "--X"), Triple(320, 180, "-LX"), Triple(240, 190, "-LX"),
            Triple(340, 480, "SLX"), Triple(412, 250, "SLX"), Triple(412, 400, "SLX"), Triple(412, 869, "SLX"),
        )
        for ((w, h, parts) in expect) {
            val f = fit(w, h, small = true)
            assertEquals("$w x $h", parts, kept(f))
            assertTrue("$w x $h ok", f.ok)
            assertFalse(f.tools)
            assertEquals(1, f.logLines)
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
        assertEquals(80.4f, c.wildH, 0.5f)
        assertEquals(72.1f, c.playerH, 0.5f)
        assertEquals(166f, c.wildW, 0f)
        assertEquals(170f, c.playerW, 0f)
    }

    // ---------------------------------------------------------------- cards: corners or the middle column

    private fun plan(w: Float, h: Float, compact: Boolean = false, floor: Float = 8f, xp: Boolean = true) =
        ArenaGeo.plan(w, h, ArenaGeo.cards(floor, compact, xp), compact)

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
        for ((w, h) in listOf(412f to 213f, 477f to 213f, 573f to 211f, 377f to 212f, 344f to 200f)) {
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
        assertTrue(plan(412f, 213f).middle)
        // same short arena, smaller cards: compact cards keep the corners where the full cards could not
        assertFalse(plan(412f, 213f, compact = true).middle)
        // wider does not help a short arena: the cards still reach into the sprites' height
        assertTrue(plan(700f, 213f).middle)
        // bigger text (a raised floor) makes the cards bigger: the corners give up earlier
        assertFalse(plan(412f, 230f, floor = 8f).middle)
        assertTrue(plan(412f, 230f, floor = 14f).middle)
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
        assertTrue(raised.wildW > normal.wildW && raised.wildH > normal.wildH && raised.playerH > normal.playerH && raised.tagH > normal.tagH)
        // compact cards: name + thin HP bar, no types, no tag, no XP
        val compact = ArenaGeo.cards(8f, true, true)
        assertEquals(0f, compact.tagH, 0f)
        assertTrue(compact.wildH < normal.wildH * 0.65f && compact.playerH < normal.playerH * 0.7f)
        assertTrue(compact.wildW < normal.wildW)
        // the XP numbers are what makes our card wide
        assertTrue(ArenaGeo.cards(8f, false, false).playerW < normal.playerW)
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
        val stack = c.wildH + c.tagH + 6f + c.playerH
        assertTrue(plan(400f, stack + 13f).fits)
        assertFalse(plan(400f, stack + 6f).fits)
        assertFalse(plan(200f, 400f).fits) // 200 dp cannot hold a 170 dp column and two sprites
    }
}
