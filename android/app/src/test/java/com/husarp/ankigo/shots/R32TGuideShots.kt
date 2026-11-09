package com.husarp.ankigo.shots

import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.help.*
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class R32TGuideShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    private fun views(v: View): List<View> = listOf(v) +
        if (v is ViewGroup) (0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList()

    @Test fun battlesAndCatching() {
        for (id in listOf(GameGuide.BATTLES, GameGuide.CATCHING)) {
            paparazzi.shootPage("t-570-$id", listOf(Variant("guide", 412, 1300))) { ctx ->
                pageScreen(ctx, HelpScreen(ctx, NoHost, HelpRoute.Guide(id), false)).also { root ->
                    val intro = GameGuide.chapter(id)!!.intro
                    assertTrue(views(root).filterIsInstance<TextView>().any { it.text.toString() == intro })
                }
            }
        }
    }

    @Test fun dailyGiftExpanded() = paparazzi.shootPage("t-570-daily-gift", listOf(Variant("guide", 412, 1300))) { ctx ->
        val chapter = GameGuide.chapter(GameGuide.MONEY)!!
        val fold = HelpViews.chapterBody(ctx, chapter).first { view ->
            views(view).filterIsInstance<TextView>().any { it.text.toString() == "Daily gift and goal streak  ›" }
        }
        views(fold).filterIsInstance<TextView>().first().performClick()
        val text = views(fold).filterIsInstance<TextView>().map { it.text.toString() }
        assertTrue(text.any { it.contains("one random gift per game day") })
        assertTrue(text.any { it.contains("TMs go into your TM collection") })
        assertTrue(text.any { it.contains("Protected days keep your streak but do not add goals") })
        LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; addView(fold) }
    }
}
