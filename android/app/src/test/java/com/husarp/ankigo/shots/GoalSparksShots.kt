package com.husarp.ankigo.shots

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.view.View
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.StudyActivity
import com.husarp.ankigo.stats.AnkiStatsStore
import com.husarp.ankigo.stats.StatsSnapshot
import com.husarp.ankigo.ui.GamePage
import com.husarp.ankigo.ui.PageHost
import com.husarp.ankigo.ui.StatsPage
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/*
 * TICKET R5-08: the daily goal bar's rising sparks (still frame: reduced motion) at 80 %, 112 %, 165 % and 230 % of the goal,
 * on the battle panel footer, the Study summary card and the Stats goal card. Light + dark.
 * PNGs: goalsparks-battle-<pct>-<size>-<theme>, goalsparks-study-<pct>-..., goalsparks-stats-<pct>-...
 */
class GoalSparksShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    @Before fun storeOff() { AnkiStatsStore.enabled = false; AnkiStatsStore.snapshot = StatsSnapshot() }
    @After fun storeOn() { AnkiStatsStore.enabled = true; AnkiStatsStore.snapshot = StatsSnapshot() }

    private val percents = listOf(80, 112, 165, 230)

    /** Reduced motion: the sparks stand still, so the picture is the same every run. */
    private fun still(ctx: Context) { ctx.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", true).commit() }

    private fun setDay(pct: Int) {
        GameRepo.state!!.day = GameRepo.game!!.dayKey()
        GameRepo.state!!.settings.dailyAverage = 100
        GameRepo.state!!.reviewsToday = pct
    }

    @Test fun battleFooter() {
        for (pct in percents) paparazzi.shootWith("goalsparks-battle-$pct", listOf(Size.PHONE, Size.SPLIT, Size.FLOATBATTLE)) { ctx ->
            still(ctx)
            setDay(pct)
            val b = BattleShotKit.bound(ctx)
            b.panel!!.setGoal(pct, 100, false)
            b.root
        }
    }

    @Test fun studySummary() {
        for (pct in percents) paparazzi.shootWith("goalsparks-study-$pct", listOf(Size.PHONE, Size.SPLIT)) { ctx ->
            still(ctx)
            setDay(pct)
            val act = attach(StudyActivity(), ShotCtx(ctx))
            setField(act, "deckId", 2L)
            setField(act, "deckName", "Geography::Capitals")
            val root = act.buildContent()
            call(act, "summary", StudyDeckKit.decks, 0, 3)
            root
        }
    }

    private class ActivityHost(val activity: Activity) : PageHost {
        override val hostActivity: Activity get() = activity
        override fun closePage() = Unit
        override fun startForResult(intent: Intent, onResult: (Int, Intent?) -> Unit) = Unit
    }

    @Test fun statsGoalCard() {
        for (pct in percents) paparazzi.shootPage("goalsparks-stats-$pct", listOf(Variants.PHONE, Variants.SPLIT)) { ctx ->
            still(ctx)
            setDay(pct)
            val page: GamePage = StatsPage(ctx, ActivityHost(attach(Activity(), ctx)))
            pageScreen(ctx, page)
        }
    }
}
