package com.husarp.ankigo.shots

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.LinearLayout
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.R
import com.husarp.ankigo.ui.*
import app.cash.paparazzi.Paparazzi
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class R30YFlameShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    @Test fun achievementAndStudy() {
        paparazzi.shootWith("y-flame", listOf(Size.PHONE)) { ctx ->
            GameRepo.state!!.streakDays = 17
            GameRepo.state!!.lastGoalDay = GameRepo.game!!.dayKey()
            GameRepo.state!!.bestStreakDays = 3
            val root = BattleShotKit.bound(ctx).root
            fun hasStreak(view: View): Boolean = (view is TextView && view.text.toString() == "17 days") ||
                (view is ViewGroup && (0 until view.childCount).any { hasStreak(view.getChildAt(it)) })
            assertTrue("The real panel must contain the streak chip", hasStreak(root))
            root
        }
        paparazzi.shootWith("y-achievements", listOf(Size.PHONE)) { ctx ->
            GameRepo.state!!.bestStreakDays = 3
            val page = StatsPage(ctx, NoHost, StatsSection.ACHIEVEMENTS)
            val screen = pageScreen(ctx, page)
            page.achievementsView().setTab("Study")
            call(page, "render")
            screen
        }
    }

    @Test fun vectorAndInactiveState() {
        paparazzi.shootWith("y-states", listOf(Size.PHONE)) { ctx ->
            LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Ui.c(ctx, R.color.ag_base))
                for (active in listOf(true, false)) {
                    val flame = StreakFlame(ctx).apply { alive = active }
                    assertEquals(if (active) 255 else 115, flame.imageAlpha)
                    addView(Ui.chip(ctx, flame, if (active) R.color.ag_red else R.color.ag_sub0).apply { setText(if (active) "17 days" else "0 days") })
                }
                val bitmap = StreakFlame.stillBitmap(ctx, 32)!!
                assertEquals(32, bitmap.width)
                assertTrue((0 until 32).any { x -> (0 until 32).any { y -> bitmap.getPixel(x, y) == Ui.c(ctx, R.color.ag_red) } })
                assertEquals(0f to 0f, AchStyle.nudge(R.drawable.ic_flame))
            }
        }
    }
}
