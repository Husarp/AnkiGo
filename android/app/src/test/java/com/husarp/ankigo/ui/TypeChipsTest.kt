package com.husarp.ankigo.ui

import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.game.Type
import com.husarp.ankigo.shots.Shots
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** PLAN 33 / 5C: every type has its own symbol and an ink that reads on its chip fill, light and dark. */
class TypeChipsTest {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    @Test fun everyTypeHasItsOwnSymbol() {
        val ids = Type.entries.map { TypeChips.symbol(it) }
        assertEquals(18, ids.size)
        assertEquals(18, ids.toSet().size)
        assertTrue(ids.all { it != 0 })
    }

    @Test fun inkReadsOnTheChipFillInBothThemes() {
        for (t in Type.entries) for (dark in listOf(false, true)) {
            val c = Ui.typeColor(t)
            val ratio = TypeInk.contrast(TypeInk.ink(c, dark), TypeInk.fill(c, dark))
            assertTrue("$t dark=$dark contrast $ratio", ratio >= TypeInk.MIN)
        }
    }

    @Test fun paleTypesGetADarkerInkOnLightAndKeepTheirColourOnDark() {
        val electric = Ui.typeColor(Type.ELECTRIC)
        assertNotEquals(electric, TypeInk.ink(electric, false)) // needed darkening
        assertEquals(electric, TypeInk.ink(electric, true)) // already readable on the dark chip
    }

    @Test fun chipShowsSymbolAndNameInTheChosenStyle() {
        val ctx = Shots.files(paparazzi.context)
        val both = TypeChips.chip(ctx, Type.FIRE, TypeStyle.ICON_TEXT)
        assertEquals("Fire", both.text.toString())
        assertNotNull(both.compoundDrawablesRelative[0])
        val text = TypeChips.chip(ctx, Type.FIRE, TypeStyle.TEXT)
        assertEquals("Fire", text.text.toString())
        assertEquals(null, text.compoundDrawablesRelative[0])
        val icon = TypeChips.chip(ctx, Type.FIRE, TypeStyle.ICON)
        assertEquals("", icon.text.toString())
        assertNotNull(icon.compoundDrawablesRelative[0])
        assertEquals("Fire", icon.contentDescription)
        assertTrue(icon.performLongClick())
        assertEquals("Fire", Ui.lastToast)
    }

    @Test fun iconChipTapWithNothingToForwardToShowsTheName() {
        val ctx = Shots.files(paparazzi.context)
        Ui.lastToast = null
        TypeChips.chip(ctx, Type.WATER, TypeStyle.ICON).performClick()
        assertEquals("Water", Ui.lastToast)
    }
}
