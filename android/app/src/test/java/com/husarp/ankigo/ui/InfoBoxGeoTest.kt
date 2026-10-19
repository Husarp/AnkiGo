package com.husarp.ankigo.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** ROUND 9 wave 3 D12: the info boxes' two designs as pure numbers ([InfoBoxStyle], [InfoBoxGeo]) and the readability they promise. */
class InfoBoxGeoTest {
    private fun line(sp: Float) = TextMath.line(sp)

    @Test fun theChoiceParsesByNameAndAnythingElseIsTheAboveStyle() {
        assertEquals(InfoBoxStyle.ABOVE, InfoBoxStyle.DEFAULT)
        assertEquals(InfoBoxStyle.ABOVE, InfoBoxStyle.parse(null))
        assertEquals(InfoBoxStyle.ABOVE, InfoBoxStyle.parse(""))
        assertEquals(InfoBoxStyle.ABOVE, InfoBoxStyle.parse("ABOVE"))
        assertEquals(InfoBoxStyle.INSIDE, InfoBoxStyle.parse("INSIDE"))
        assertEquals("a name from another version", InfoBoxStyle.ABOVE, InfoBoxStyle.parse("UNDER"))
        assertEquals("names are exact", InfoBoxStyle.ABOVE, InfoBoxStyle.parse("inside"))
        assertEquals("infoBoxStyle", InfoBoxStyle.KEY)
        for (s in InfoBoxStyle.entries) assertEquals(s, InfoBoxStyle.parse(s.name))
    }

    @Test fun theBoxIs160DpWideAndGrowsWithTheRaisedText() {
        assertEquals(160f, InfoBoxGeo.width(0f), 0f)
        assertEquals(160f, InfoBoxGeo.width(8f), 0f)
        assertEquals(160f, InfoBoxGeo.width(12f), 0f)
        assertEquals(160f * 18f / 12f, InfoBoxGeo.width(18f), 0.001f)
    }

    @Test fun theAboveBoxIsTheMockupsSumOfItsLines() {
        // docs/design/round9d.html D12.A: padding 6/7, name line, 3 dp, the number line, 3 dp, the 7 dp bar (+ yours: 3 dp, the XP line)
        val wild = 6f + line(14f) + 3f + line(10.5f) + 3f + 7f + 7f
        val ours = 6f + line(14f) + 3f + line(10.5f) + 3f + 7f + 3f + line(9f) + 7f
        assertEquals(wild, InfoBoxGeo.height(InfoBoxStyle.ABOVE, true, 8f, true, false), 0.01f)
        assertEquals(ours, InfoBoxGeo.height(InfoBoxStyle.ABOVE, false, 8f, true, false), 0.01f)
        // no dead space: the wild box has no XP line, ours ends on the XP line
        assertTrue(ours - wild < 17f)
    }

    @Test fun theInsideBoxIsTheMockupsSumOfItsLines() {
        val wild = 6f + line(14f) + 5f + 14f + 7f
        val ours = wild + 3f + 10f
        assertEquals(wild, InfoBoxGeo.height(InfoBoxStyle.INSIDE, true, 8f, true, false), 0.01f)
        assertEquals(ours, InfoBoxGeo.height(InfoBoxStyle.INSIDE, false, 8f, true, false), 0.01f)
    }

    @Test fun bothAreShorterThanTodaysBoxesAndInsideIsTheShortest() {
        // today's: 87.4 (wild) and 91.1 (ours) at the floor 8, with the status row and the dead space
        for (style in InfoBoxStyle.entries) {
            assertTrue("$style wild", InfoBoxGeo.height(style, true, 8f, true, false) < 87.4f * 0.72f)
            assertTrue("$style ours", InfoBoxGeo.height(style, false, 8f, true, false) < 91.1f * 0.85f)
        }
        assertTrue(InfoBoxGeo.height(InfoBoxStyle.INSIDE, true, 8f, true, false) < InfoBoxGeo.height(InfoBoxStyle.ABOVE, true, 8f, true, false))
        assertTrue(InfoBoxGeo.height(InfoBoxStyle.INSIDE, false, 8f, true, false) < InfoBoxGeo.height(InfoBoxStyle.ABOVE, false, 8f, true, false))
    }

    @Test fun aReducedBoxDropsTheXpNumberSoItIsShorterAndTheWildSymbolsCostAtMostOneDp() {
        for (style in InfoBoxStyle.entries) {
            assertTrue("$style ours", InfoBoxGeo.height(style, false, 8f, true, true) < InfoBoxGeo.height(style, false, 8f, true, false) - 5f)
            assertEquals("$style: with no XP number the box is reduced already", InfoBoxGeo.height(style, false, 8f, false, false), InfoBoxGeo.height(style, false, 8f, true, true), 0.001f)
        }
        // the wild symbols (15 dp) are a hair taller than the chips (8.5 sp text): the row never moves by more than that
        val a = InfoBoxGeo.height(InfoBoxStyle.ABOVE, true, 8f, true, false)
        val r = InfoBoxGeo.height(InfoBoxStyle.ABOVE, true, 8f, true, true)
        assertTrue("$a vs $r", r >= a && r - a <= 1f)
        assertEquals(InfoBoxGeo.height(InfoBoxStyle.INSIDE, true, 8f, true, false), InfoBoxGeo.height(InfoBoxStyle.INSIDE, true, 8f, true, true), 0.001f)
    }

    @Test fun theCardsFollowTheStyleAndTheCompactCardKeepsItsShape() {
        val above = ArenaGeo.cards(8f, false, true, InfoBoxStyle.ABOVE)
        val inside = ArenaGeo.cards(8f, false, true, InfoBoxStyle.INSIDE)
        assertEquals(InfoBoxGeo.height(InfoBoxStyle.ABOVE, true, 8f, true, false), above.wildH, 0f)
        assertEquals(InfoBoxGeo.height(InfoBoxStyle.INSIDE, false, 8f, true, false), inside.playerH, 0f)
        assertEquals(above.wildW, inside.wildW, 0f)
        // compact: same in both styles, much shorter
        for (style in InfoBoxStyle.entries) {
            val c = ArenaGeo.cards(8f, true, true, style)
            assertEquals(InfoBoxGeo.compactHeight(8f), c.wildH, 0f)
            assertEquals(140f, c.wildW, 0f)
            assertTrue(c.wildH < inside.wildH)
        }
        // the XP number is what the xp flag turns off
        assertTrue(ArenaGeo.cards(8f, false, false, InfoBoxStyle.ABOVE).playerH < above.playerH)
        assertEquals(ArenaGeo.cards(8f, false, true, InfoBoxStyle.ABOVE, reduced = true).playerH, ArenaGeo.cards(8f, false, false, InfoBoxStyle.ABOVE).playerH, 0.001f)
    }

    @Test fun theSmallestTextOfAFullBoxIsTheOptionalPartAndOfAReducedBoxTheHpNumber() {
        assertEquals(8.5f, InfoBoxGeo.smallestSp(InfoBoxStyle.ABOVE, xpText = true, reduced = false), 0f)   // the type chips
        assertEquals(10.5f, InfoBoxGeo.smallestSp(InfoBoxStyle.ABOVE, xpText = true, reduced = true), 0f)  // the HP number
        assertEquals(8f, InfoBoxGeo.smallestSp(InfoBoxStyle.INSIDE, xpText = true, reduced = false), 0f)   // the XP number in its bar
        assertEquals(9.5f, InfoBoxGeo.smallestSp(InfoBoxStyle.INSIDE, xpText = false, reduced = false), 0f)
        assertEquals(9.5f, InfoBoxGeo.smallestSp(InfoBoxStyle.INSIDE, xpText = true, reduced = true), 0f)
    }

    @Test fun noTextOfAnyBoxIsUnderTheReadableSizeAtTheScaleItIsDrawnAt() {
        // the floor each style needs, over the panel scales PanelFit produces: text x panel scale x box scale >= 8 sp
        for (style in InfoBoxStyle.entries) for (s in listOf(1f, 0.9f, 0.75f, 0.6f, 0.55f)) {
            val floor = Math.ceil((8f / s * 10f).toDouble()).toFloat() / 10f
            for (reduced in listOf(false, true)) {
                val sp = InfoBoxGeo.smallestSp(style, xpText = true, reduced = reduced)
                val k = StageGeo.textScale(sp, s, floor)
                assertTrue("$style s=$s reduced=$reduced: ${maxOf(sp, floor) * s * k} sp", maxOf(sp, floor) * s * k >= PanelFit.MIN_SP - 0.01f)
            }
        }
    }
}
