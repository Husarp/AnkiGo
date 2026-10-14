package com.husarp.ankigo.shots

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.view.View
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.game.DayLog
import com.husarp.ankigo.game.GameState
import com.husarp.ankigo.ui.GamePage
import com.husarp.ankigo.ui.PageHost
import com.husarp.ankigo.ui.StatsPage
import com.husarp.ankigo.ui.TeamPage
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import kotlin.random.Random

/*
 * TICKET T40: the Stats screen (mid-game with ~16 weeks of made-up history, and a brand-new player) and the Team page
 * trainer card with its Stats chip. Light + dark, PHONE + SPLIT (+ TALL for the whole scrolling page).
 */
class StatsShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    /** A host that has an Activity (so the page bar shows its back arrow), as StatsActivity does. */
    private class ActivityHost(val activity: Activity) : PageHost {
        override val hostActivity: Activity get() = activity
        override fun closePage() = Unit
        override fun startForResult(intent: Intent, onResult: (Int, Intent?) -> Unit) = Unit
    }

    private fun page(ctx: Context): GamePage = StatsPage(ctx, ActivityHost(attach(Activity(), ctx)))

    /** ~16 weeks of made-up study ending today: gaps, partial days, goal days; the last 12 days before today all reach the goal. */
    private fun madeUpHistory(s: GameState, today: LocalDate) {
        val rnd = Random(11)
        s.history.clear()
        for (i in 0 until 16 * 7 + 3) {
            val day = today.minusDays(i.toLong())
            val reviews = when {
                i == 0 -> 86
                i in 1..12 -> 100 + rnd.nextInt(60)
                i in 20..24 || i in 47..52 -> 0                       // gaps
                rnd.nextInt(10) < 2 -> 0
                rnd.nextInt(10) < 3 -> 100 + rnd.nextInt(70)
                else -> 8 + rnd.nextInt(90)
            }
            if (reviews > 0) s.history[day.toString()] = DayLog(reviews, reviews / 9, reviews / 35, if (reviews > 140) 1 else 0)
        }
    }

    private fun midGame(): (Context) -> View = { ctx ->
        val g = GameRepo.game!!
        val s = GameRepo.state!!
        val today = LocalDate.parse(g.dayKey())
        s.day = today.toString()
        s.reviewsToday = 86
        s.streakDays = 12; s.bestStreakDays = 20
        s.lastGoalDay = today.minusDays(1).toString()
        madeUpHistory(s, today)
        s.mons.take(3).forEach { it.shiny = true }
        pageScreen(ctx, page(ctx))
    }

    @Test fun statsMidGame() {
        paparazzi.shootPage("stats-mid", listOf(Variants.PHONE, Variants.SPLIT, Variants.TALL), build = midGame())
    }

    @Test fun statsNewPlayer() {
        paparazzi.shootPage("stats-new", listOf(Variants.PHONE, Variants.SPLIT), scenario = FakeGame.Scenario.EMPTY) { ctx ->
            val s = GameRepo.state!!
            s.day = GameRepo.game!!.dayKey()
            pageScreen(ctx, page(ctx))
        }
    }

    @Test fun statsGoalDone() {
        paparazzi.shootPage("stats-done", listOf(Variants.PHONE)) { ctx ->
            midGame()(ctx)
            val s = GameRepo.state!!
            s.reviewsToday = 124
            s.history[s.day] = DayLog(124, 12, 3, 0)
            s.lastGoalDay = s.day
            pageScreen(ctx, page(ctx))
        }
    }

    @Test fun teamCardWithStatsChip() {
        paparazzi.shootPage("stats-team", listOf(Variants.PHONE, Variants.SPLIT)) { ctx -> pageScreen(ctx, TeamPage(ctx, NoHost)) }
    }
}
