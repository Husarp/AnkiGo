package com.husarp.ankigo.shots

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.help.*
import com.husarp.ankigo.game.Type
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class R24GHelpShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    private fun views(v: View): List<View> = listOf(v) +
        if (v is ViewGroup) (0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList()

    @Test fun oneLessonAndPairedArrows() = paparazzi.shootPage("r24g-lesson", listOf(Variants.PHONE, Variants.SPLIT, Variant("small", 360, 640))) { ctx ->
        val page = HelpScreen(ctx, NoHost, HelpRoute.Guide(GameGuide.BATTLES), false)
        val root = pageScreen(ctx, page)
        fun texts() = views(root).filterIsInstance<TextView>().map { it.text.toString() }
        fun arrow(name: String) = views(root).first { it.contentDescription == name }
        assertTrue("Lesson 1 of 9" in texts())
        assertFalse(arrow("Previous lesson").isEnabled)
        assertSame(arrow("Previous lesson").parent, arrow("Next lesson").parent)
        assertFalse(texts().any { it.contains("Levels and evolutions") })
        for (ch in GameGuide.chapters().drop(1)) {
            arrow("Next lesson").performClick()
            assertEquals(ch.id, page.currentGuideChapter)
            assertTrue(ch.intro in texts())
            assertFalse(GameGuide.chapters().first().intro in texts())
        }
        assertFalse(arrow("Next lesson").isEnabled)
        repeat(8) { arrow("Previous lesson").performClick() }
        assertEquals(GameGuide.BATTLES, page.currentGuideChapter)
        root
    }

    @Test fun namedTypeIconsAndSelection() = paparazzi.shootPage("r24g-types", listOf(Variants.PHONE, Variant("small", 360, 640))) { ctx ->
        val picker = GuideTypeChart.picker(ctx)
        val cells = views(picker).filter { it.contentDescription?.startsWith("Show ") == true }
        assertEquals(18, cells.size)
        assertEquals(listOf(4, 4, 4, 4, 2), cells.groupBy { it.parent }.values.map { it.size })
        Type.entries.forEach { t ->
            val cell = cells.first { it.contentDescription == "Show ${t.name.lowercase()} matchups" }
            assertTrue(views(cell).filterIsInstance<TextView>().any { it.text == t.label })
            cell.performClick()
            assertTrue(cell.isSelected)
            assertEquals(1, cells.count { it.isSelected })
        }
        cells.first { it.contentDescription == "Show fire matchups" }.performClick()
        picker
    }

    @Test fun colouredChapterChooserRows() = paparazzi.shootPage("r24g-chapters", listOf(Variants.PHONE)) { ctx ->
        val chapters = GameGuide.chapters()
        assertEquals(9, chapters.map { HelpViews.chapterIcon(it.id) }.toSet().size)
        assertEquals(9, chapters.map { HelpViews.chapterTone(it.id) }.toSet().size)
        android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            chapters.forEachIndexed { i, ch -> addView(HelpViews.chapterHeader(ctx, ch, i + 1, false) {}) }
        }
    }
}
