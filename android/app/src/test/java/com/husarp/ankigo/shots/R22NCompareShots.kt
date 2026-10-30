package com.husarp.ankigo.shots

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class R22NCompareShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    private fun all(v: View): List<View> = listOf(v) + if (v is ViewGroup)
        (0 until v.childCount).flatMap { all(v.getChildAt(it)) } else emptyList()
    private fun labels(v: View) = all(v).filterIsInstance<TextView>()
    @Test fun coloursAndFairLevels() {
        paparazzi.shootPage("r22n-compare", listOf(Variants.PHONE)) { ctx ->
            val prefs = ctx.getSharedPreferences("ankigo_ui", 0)
            prefs.edit().remove("compare_colours").putString("compare_mode", "BEST_OF_ALL").apply()
            assertTrue(CompareStore.loadColours(ctx))
            val page = ComparePage(ctx, NoHost, listOf(ComparePick.Owned("t1"), ComparePick.Owned("t2"), ComparePick.Owned("t3")))
            val root = pageScreen(ctx, page).also { page.render() }
            fun tap(text: String) { labels(root).first { it.text.toString() == text }.performClick(); page.render() }
            assertFalse(labels(root).any { it.text.toString() in listOf("Vs reference", "Best of all") })
            assertTrue(labels(root).any { it.text.toString() == "better than the reference" })
            assertFalse(labels(root).any { it.text.toString().matches(Regex("[+−][0-9]+")) })
            tap("Colours")
            assertTrue(labels(root).any { it.text.toString().matches(Regex("[+−][0-9]+")) })
            assertFalse(CompareStore.loadColours(ctx))
            assertFalse(labels(root).any { it.text.toString() == "better than the reference" })
            tap("Colours")
            tap("Fully evolved")
            val levels = labels(root).map { it.text.toString() }.filter { it.matches(Regex("Lv [0-9]+")) }
            assertTrue(levels.isNotEmpty())
            assertEquals(1, levels.distinct().size)
            assertTrue(labels(root).any { it.text.toString().startsWith("Stats at: Lv ") })
            root
        }
    }
}
