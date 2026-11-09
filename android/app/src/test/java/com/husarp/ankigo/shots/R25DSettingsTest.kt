package com.husarp.ankigo.shots

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.LinearLayout
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.R
import com.husarp.ankigo.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class R25DSettingsTest {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    private fun texts(v: View): List<TextView> = (if (v is TextView) listOf(v) else emptyList()) +
        if (v is ViewGroup) (0 until v.childCount).flatMap { texts(v.getChildAt(it)) } else emptyList()

    @Test fun settingsSections() {
        for (section in listOf(SettingsSection.LOOK, SettingsSection.STUDY, SettingsSection.BATTLE,
            SettingsSection.GAME, SettingsSection.DOWNLOADS, SettingsSection.SOUND, SettingsSection.REMINDERS, SettingsSection.BACKGROUNDS)) {
            paparazzi.shootPage("r25d-${section.name.lowercase()}", listOf(Variant("tall", 412, 2400))) { ctx ->
                val c = ShotCtx(ctx)
                val page = SettingsPage(c, NoHost)
                pageScreen(c, page).also { root ->
                    page.open(section)
                    if (section == SettingsSection.LOOK) {
                        texts(root).first { it.text == "AnkiDroid dark blue" }.performClick()
                        assertEquals(ShowAnswerColor.ANKIDROID, ShowAnswerColor.get(c))
                        assertTrue(texts(root).any { it.text == "THEME & COLOURS" })
                        assertTrue(texts(root).any { it.text == "TEXT & SIZE" })
                        assertFalse(texts(root).any { it.text == "GENERAL" })
                    }
                }
            }
        }
    }

    @Test fun favouritesUseStarAndStillCollapse() {
        paparazzi.shootPage("r25d-study-favourites", listOf(Variants.PHONE)) { ctx ->
            val c = ShotCtx(ctx)
            val prefs = c.getSharedPreferences("study", 0)
            prefs.edit().putString("favouriteDecks", "5,2").putBoolean("favouritesCollapsed", false).apply()
            val built = StudyDeckKit.compose(c)
            setField(built.home, "decks", StudyDeckKit.decks)
            built.home.render()
            (texts(built.root).first { it.text.startsWith("FAVOURITES") }.parent as View).performClick()
            assertTrue(prefs.getBoolean("favouritesCollapsed", false))
            (texts(built.root).first { it.text.startsWith("FAVOURITES") }.parent as View).performClick()
            assertFalse(prefs.getBoolean("favouritesCollapsed", true))
            built.root
        }
    }

    @Test fun answerColoursPersistAndRender() {
        paparazzi.shootPage("r25d-answer-colours", listOf(Variants.PHONE)) { ctx ->
            val c = ShotCtx(ctx)
            val prefs = c.getSharedPreferences("study", 0)
            prefs.edit().remove("showAnswerColor").apply()
            assertEquals(ShowAnswerColor.DEFAULT, ShowAnswerColor.get(c))
            prefs.edit().putString("showAnswerColor", "unknown").apply()
            assertEquals(ShowAnswerColor.DEFAULT, ShowAnswerColor.get(c))
            LinearLayout(c).apply {
                orientation = LinearLayout.VERTICAL
                for (choice in ShowAnswerColor.entries) {
                    ShowAnswerColor.put(c, choice)
                    assertEquals(choice, ShowAnswerColor.get(c))
                    addView(StudyUi.section(c, choice.label))
                    addView(StudyUi.answer(c, "Show answer", null, R.color.ag_mauve, true, fill = ShowAnswerColor.get(c).fill) {}, StudyUi.lp(-1, Ui.dp(c, 48)))
                }
                addView(StudyUi.section(c, "Favourites", iconRes = R.drawable.ic_star))
                addView(StudyUi.section(c, "Decks"))
            }.also { ShowAnswerColor.put(c, ShowAnswerColor.DEFAULT) }
        }
    }

    @Test fun longPagesHaveNamedCatalogGroups() {
        for (section in listOf(CatalogSection.LOOK, CatalogSection.STUDY, CatalogSection.BATTLE, CatalogSection.GAME)) {
            assertTrue(section.name, SettingsCatalog.bySection(section).all { it.sub.isNotBlank() })
        }
        assertEquals(0xFF22333F.toInt(), ShowAnswerColor.ANKIDROID.fill)
        assertNull(ShowAnswerColor.DEFAULT.fill)
    }
}
