package com.husarp.ankigo.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class AppDialogRulesTest {
    @Test fun widthKeepsMarginsAndCapsAt560() {
        assertEquals(380, AppDialogRules.widthDp(412))
        assertEquals(560, AppDialogRules.widthDp(800))
        assertEquals(288, AppDialogRules.widthDp(320))
    }

    @Test fun buttonsThatFitStayInOneRow() {
        assertEquals(listOf(listOf(0, 1, 2)), AppDialogRules.rows(listOf(100, 100, 100), 320, 4))
        assertEquals(listOf(listOf(0)), AppDialogRules.rows(listOf(100), 320, 4))
        assertEquals(listOf(listOf(0, 1)), AppDialogRules.rows(listOf(158, 158), 320, 4))
    }

    @Test fun threeButtonsOnANarrowScreenWrapToTwoRows() {
        assertEquals(listOf(listOf(0, 1), listOf(2)), AppDialogRules.rows(listOf(120, 100, 110), 250, 4))
        assertEquals(listOf(listOf(0), listOf(1), listOf(2)), AppDialogRules.rows(listOf(120, 120, 120), 130, 4))
    }

    @Test fun aButtonWiderThanTheRowStaysAlone() {
        assertEquals(listOf(listOf(0), listOf(1)), AppDialogRules.rows(listOf(400, 50), 300, 4))
        assertEquals(emptyList<List<Int>>(), AppDialogRules.rows(emptyList(), 300, 4))
    }

    @Test fun textOnTheFillPicksTheBetterContrast() {
        val c = { a: Int, b: Int -> TypeInk.contrast(a, b) }
        val lightCrust = 0xFFDCE0E8.toInt()
        val darkCrust = 0xFF11111B.toInt()
        assertEquals(0xFFFFFFFF.toInt(), AppDialogRules.onFill(0xFF8839EF.toInt(), lightCrust, c)) // Latte mauve: white reads
        assertEquals(darkCrust, AppDialogRules.onFill(0xFFCBA6F7.toInt(), darkCrust, c))           // pale Mocha mauve: the dark crust reads
    }
}
