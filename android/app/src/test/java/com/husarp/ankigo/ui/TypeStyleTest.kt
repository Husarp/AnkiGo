package com.husarp.ankigo.ui

import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.shots.Shots
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test

/** "Show types as" (PLAN 33): default icon + text, stored through AppLook, listed in the Settings catalog. Uses the shots rule only for a Context. */
class TypeStyleTest {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    @Test fun defaultIsIconPlusTextAndTheChoiceIsRemembered() {
        val ctx = Shots.files(paparazzi.context)
        try {
            assertEquals(TypeStyle.ICON_TEXT, AppPrefs.typeStyle(ctx))
            AppLook.setTypeStyle(ctx, TypeStyle.ICON)
            assertEquals(TypeStyle.ICON, AppPrefs.typeStyle(ctx))
        } finally {
            AppLook.setTypeStyle(ctx, TypeStyle.ICON_TEXT)
        }
    }

    @Test fun settingsRowIsInTheLookSectionAndSearchFindsIt() {
        val info = SettingsCatalog.byTitle("Show types as")
        assertNotNull(info)
        assertEquals(CatalogSection.LOOK, info!!.section)
        assertEquals(listOf("Show types as"), SettingsSearch.match("show types").map { it.info.title })
        assertEquals(true, SettingsSearch.match("icons").any { it.info.title == "Show types as" })
    }
}
