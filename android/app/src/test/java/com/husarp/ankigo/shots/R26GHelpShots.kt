package com.husarp.ankigo.shots

import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.help.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class R26GHelpShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    private fun views(v: View): List<View> = listOf(v) +
        if (v is ViewGroup) (0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList()

    @Test fun dailyGiftHasCompleteHeadingAndExplainsTheReward() = paparazzi.shootPage(
        "r26g-daily-gift", listOf(Variants.PHONE, Variants.SPLIT, Variant("small", 360, 640)),
    ) { ctx ->
        val chapter = GameGuide.chapter(GameGuide.MONEY)!!
        val gift = GuideLessons.details(chapter).single { it.first == "Daily gift and goal streak" }
        assertEquals(chapter.points.slice(4..6), gift.second)
        // Render the actual fold at the top so its expanded text is visible at every test width.
        val fold = HelpViews.chapterBody(ctx, chapter).first { view ->
            views(view).filterIsInstance<TextView>().any { it.text.toString() == "Daily gift and goal streak  ›" }
        }
        views(fold).filterIsInstance<TextView>().first().performClick()
        val text = views(fold).filterIsInstance<TextView>().map { it.text.toString() }
        assertTrue(text.any { it.contains("one random gift per game day") })
        assertTrue(text.any { it.contains("TMs go into your TM collection") })
        assertTrue(text.any { it.contains("common 75%, uncommon 22%, rare 3%") })
        assertTrue(text.any { it.contains("After 30 goals in a streak: common 40%, uncommon 40%, rare 20%") })
        LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; addView(fold) }
    }
}
