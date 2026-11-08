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
            fun title(v: View): TextView? {
                if (v is TextView && v.text.toString() == "Best streak") return v
                if (v is ViewGroup) for (i in 0 until v.childCount) title(v.getChildAt(i))?.let { return it }
                return null
            }
            val tile = title(screen)!!.parent as ViewGroup
            val icon = (0 until tile.childCount).map { tile.getChildAt(it) }.filterIsInstance<android.widget.ImageView>().first()
            assertNotNull("The Best streak outline uses the achievement tier colour", icon.colorFilter)
            assertTrue(icon.drawable is android.graphics.drawable.VectorDrawable)
            screen
        }
    }

    @Test fun originalArtworkAndInactiveState() {
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
                val colors = (0 until 32).flatMap { x -> (0 until 32).map { y -> bitmap.getPixel(x, y) } }.filter { android.graphics.Color.alpha(it) > 0 }.toSet()
                assertTrue("The original flame keeps its multicolour artwork", colors.size > 8)
                assertEquals(0f to 0f, AchStyle.nudge(R.drawable.streak_flame_still))
            }
        }
    }
}
