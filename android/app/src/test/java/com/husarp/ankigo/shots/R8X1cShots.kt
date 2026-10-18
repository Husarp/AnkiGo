package com.husarp.ankigo.shots

import android.app.Activity
import android.content.Context
import android.content.Intent
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
 * TICKET R8-X1c: the daily goal bar, 14 dp tall outside the battle, with the golden outline + glow once the goal is reached.
 * Below (99 %), at (100 %) and above (230 %) the goal on the Study summary card and the Stats goal card, and the battle footer
 * (unchanged size). Light + dark. PNGs: r8x1c-study-<pct>-<size>-<theme>, r8x1c-stats-<pct>-..., r8x1c-battle-<pct>-...
 */
class R8X1cShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    @Before fun storeOff() { AnkiStatsStore.enabled = false; AnkiStatsStore.snapshot = StatsSnapshot() }
    @After fun storeOn() { AnkiStatsStore.enabled = true; AnkiStatsStore.snapshot = StatsSnapshot() }

    private val percents = listOf(99, 100, 230)

    private fun still(ctx: Context) { ctx.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", true).commit() }

    private fun setDay(pct: Int) {
        GameRepo.state!!.day = GameRepo.game!!.dayKey()
        GameRepo.state!!.settings.dailyAverage = 100
        GameRepo.state!!.reviewsToday = pct
    }

    @Test fun battleFooter() {
        for (pct in percents) paparazzi.shootWith("r8x1c-battle-$pct", listOf(Size.PHONE, Size.FLOATBATTLE)) { ctx ->
            still(ctx); setDay(pct)
            val b = BattleShotKit.bound(ctx)
            b.panel!!.setGoal(pct, 100, false)
            b.root
        }
    }

    @Test fun studySummary() {
        for (pct in percents) paparazzi.shootWith("r8x1c-study-$pct", listOf(Size.PHONE, Size.SPLIT)) { ctx ->
            still(ctx); setDay(pct)
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
        for (pct in percents) paparazzi.shootPage("r8x1c-stats-$pct", listOf(Variants.PHONE, Variants.SPLIT)) { ctx ->
            still(ctx); setDay(pct)
            val page: GamePage = StatsPage(ctx, ActivityHost(attach(Activity(), ctx)))
            pageScreen(ctx, page)
        }
    }
}
