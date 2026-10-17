package com.husarp.ankigo.shots

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.FrameLayout
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.game.DayLog
import com.husarp.ankigo.game.GameState
import com.husarp.ankigo.stats.AnkiAccess
import com.husarp.ankigo.stats.AnkiQueries
import com.husarp.ankigo.stats.AnkiStats
import com.husarp.ankigo.stats.AnkiStatsReader
import com.husarp.ankigo.stats.AnkiStatsStore
import com.husarp.ankigo.stats.StatsSnapshot
import com.husarp.ankigo.ui.GamePage
import com.husarp.ankigo.ui.PageHost
import com.husarp.ankigo.ui.StatsPage
import com.husarp.ankigo.ui.TeamPage
import com.husarp.ankigo.ui.Ui
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import kotlin.random.Random

/*
 * TICKET T40: the Stats screen (mid-game with ~16 weeks of made-up history, and a brand-new player) and the Team page
 * trainer card with its Stats chip. Light + dark, PHONE + SPLIT (+ TALL for the whole scrolling page).
 * TICKET L2-07: the same screen with AnkiDroid's numbers (finished, first history pass still running, cached but not
 * refreshable) and without them (no permission, still checking). The store is switched off and given a made-up snapshot.
 */
class StatsShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    @Before fun storeOff() { AnkiStatsStore.enabled = false; AnkiStatsStore.snapshot = StatsSnapshot() }
    @After fun storeOn() { AnkiStatsStore.enabled = true; AnkiStatsStore.snapshot = StatsSnapshot() }

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

    /** The mid-game state of the screens below (game day = today). */
    private fun prepareMidGame(): LocalDate {
        val g = GameRepo.game!!
        val s = GameRepo.state!!
        val today = LocalDate.parse(g.dayKey())
        s.day = today.toString()
        s.reviewsToday = 86
        s.streakDays = 12; s.bestStreakDays = 20
        s.lastGoalDay = today.minusDays(1).toString()
        madeUpHistory(s, today)
        s.mons.take(3).forEach { it.shiny = true }
        return today
    }

    private fun midGame(): (Context) -> View = { ctx ->
        prepareMidGame()
        pageScreen(ctx, page(ctx))
    }

    /**
     * What AnkiDroid would answer, made up: the per-day counts follow [madeUpHistory]'s shape (a gap in weeks 3 and 7, a
     * streak of full days before today), run through the real reader, so the query strings and the maths are the app's own.
     */
    private fun fakeAnki(today: LocalDate, historyDays: Int = 365, finished: Boolean = true): AnkiStats {
        val rnd = Random(7)
        val perDay = IntArray(365) { k ->
            when {
                k == 0 -> 86
                k in 1..12 -> 100 + rnd.nextInt(60)
                k in 20..24 || k in 47..52 || k > 200 && k % 9 == 0 -> 0
                rnd.nextInt(10) < 2 -> 0
                rnd.nextInt(10) < 3 -> 100 + rnd.nextInt(70)
                else -> 8 + rnd.nextInt(90)
            }
        }
        val answers = mapOf(
            AnkiQueries.NEW to 1840, AnkiQueries.LEARNING to 12, AnkiQueries.RELEARNING to 5, AnkiQueries.YOUNG to 640,
            AnkiQueries.MATURE to 3120, AnkiQueries.SUSPENDED to 41, AnkiQueries.BURIED to 3, AnkiQueries.DUE_NOW to 142,
            AnkiQueries.RETENTION_ANSWERED to 2600, AnkiQueries.RETENTION_AGAIN to 190,
            AnkiQueries.rated(30, 1) to 210, AnkiQueries.rated(30, 2) to 380, AnkiQueries.rated(30, 3) to 2900, AnkiQueries.rated(30, 4) to 610,
            AnkiQueries.introduced(1) to 14, AnkiQueries.introduced(7) to 96, AnkiQueries.introduced(30) to 410, AnkiQueries.introduced(365) to 1900,
        )
        val count = { q: String ->
            when {
                q in answers -> answers.getValue(q)
                q.startsWith("prop:rated=") -> perDay[-q.substringAfter('=').toInt()]
                q.startsWith("prop:due=") -> 150 - 4 * q.substringAfter('=').toInt() + (q.length % 5)
                q.startsWith("rated:") -> 100                      // other windows: not drawn on this screen
                else -> 0
            }
        }
        val now = System.currentTimeMillis() - 5 * 60_000
        val quick = AnkiStatsReader(count).quick(null, "com.ichi2.anki", today.toString(), now)
        val full = AnkiStatsReader(count).history(quick, now)
        val kept = full.perDay.filterKeys { today.toString() >= it && it >= today.minusDays(historyDays.toLong() - 1).toString() }
        return full.copy(perDay = kept, historyCheckedAt = if (finished) now - 60_000 else 0L)
    }

    private fun fakeAnkiGoTime(today: LocalDate): Map<String, Long> =
        (0 until 8).associate { today.minusDays(it.toLong()).toString() to (if (it == 0) 14 * 60_000L + 20_000 else (9 + it * 4) * 60_000L) }

    private fun ankiShot(name: String, variants: List<Variant>, offsetDp: Int? = null, snap: (LocalDate) -> StatsSnapshot) {
        paparazzi.shootPage(name, variants) { ctx ->
            val today = prepareMidGame()
            AnkiStatsStore.snapshot = snap(today)
            val screen = pageScreen(ctx, page(ctx))
            // A window of the page that Paparazzi doesn't scale down (it caps a PNG at 1000 px): the page is laid out
            // 1300 dp tall and moved up by [offsetDp].
            if (offsetDp == null) screen else FrameLayout(ctx).apply {
                addView(screen, FrameLayout.LayoutParams(-1, Ui.dp(ctx, 1300)).also { it.topMargin = -Ui.dp(ctx, offsetDp) })
            }
        }
    }

    @Test fun statsMidGame() {
        paparazzi.shootPage("stats-mid", listOf(Variants.PHONE, Variants.SPLIT, Variants.TALL), build = midGame())
    }

    @Test fun statsWithAnkiDroidNumbers() {
        ankiShot("stats-anki", listOf(Variants.PHONE, Variants.SPLIT, Variants.TALL)) { t ->
            StatsSnapshot(fakeAnki(t), AnkiAccess.OK, timeByDay = fakeAnkiGoTime(t))
        }
    }

    /** The same page in three 500 dp windows at full size, to read every number (the TALL PNG is shrunk to fit 1000 px). */
    @Test fun statsWithAnkiDroidNumbersInWindows() {
        val slice = Variant("slice", 412, 500)
        for ((i, offset) in listOf(0, 480, 960).withIndex()) {
            ankiShot("stats-anki-w${i + 1}", listOf(slice), offset) { t -> StatsSnapshot(fakeAnki(t), AnkiAccess.OK, timeByDay = fakeAnkiGoTime(t)) }
        }
    }

    /** First start: the quick numbers are in, the history pass is at day ~60 of 365. */
    @Test fun statsWhileTheHistoryIsStillBeingRead() {
        ankiShot("stats-anki-reading", listOf(Variants.PHONE, Variants.TALL)) { t ->
            StatsSnapshot(fakeAnki(t, historyDays = 60, finished = false), AnkiAccess.OK, historyRunning = true, timeByDay = fakeAnkiGoTime(t))
        }
    }

    @Test fun statsWithoutPermissionFallBackToTheOwnCount() {
        ankiShot("stats-anki-denied", listOf(Variants.PHONE, Variants.SPLIT, Variants.TALL)) { t ->
            StatsSnapshot(access = AnkiAccess.NO_PERMISSION, timeByDay = fakeAnkiGoTime(t))
        }
    }

    @Test fun statsStillCheckingAndTheOtherReasons() {
        ankiShot("stats-anki-checking", listOf(Variants.PHONE)) { StatsSnapshot() }
        ankiShot("stats-anki-old", listOf(Variants.PHONE)) { StatsSnapshot(access = AnkiAccess.ANKIDROID_TOO_OLD) }
        ankiShot("stats-anki-error", listOf(Variants.PHONE)) { StatsSnapshot(access = AnkiAccess.ERROR, message = "Invalid Anki search") }
    }

    /** Cached numbers from earlier, but this refresh failed: the footnote says so. */
    @Test fun statsCachedButTheRefreshFailed() {
        ankiShot("stats-anki-stale", listOf(Variants.PHONE)) { t ->
            StatsSnapshot(fakeAnki(t).copy(fetchedAt = System.currentTimeMillis() - 26 * 3_600_000L), AnkiAccess.NO_PERMISSION, timeByDay = fakeAnkiGoTime(t))
        }
    }

    /** R4-V10 (pick 11A with cells): section cards in windows at full size (phone, small, split-height), and with the (i) texts open. */
    @Test fun r4SectionCards() {
        val slice = Variant("slice", 412, 500)
        for ((i, offset) in listOf(0, 480, 960).withIndex()) {
            ankiShot("r4v10-stats-w${i + 1}", listOf(slice), offset) { t -> StatsSnapshot(fakeAnki(t), AnkiAccess.OK, timeByDay = fakeAnkiGoTime(t)) }
        }
        ankiShot("r4v10-stats-small", listOf(Variant("small", 360, 640)), 480) { t -> StatsSnapshot(fakeAnki(t), AnkiAccess.OK, timeByDay = fakeAnkiGoTime(t)) }
        ankiShot("r4v10-stats-tablet", listOf(Variant("tablet", 800, 1000)), null) { t -> StatsSnapshot(fakeAnki(t), AnkiAccess.OK, timeByDay = fakeAnkiGoTime(t)) }
        ankiShot("r4v10-stats-nodroid", listOf(Variants.TALL)) { t -> StatsSnapshot(access = AnkiAccess.NO_PERMISSION, timeByDay = fakeAnkiGoTime(t)) }
    }

    @Test fun r4SectionCardsInfoOpen() {
        paparazzi.shootPage("r4v10-stats-info", listOf(Variant("slice", 412, 500))) { ctx ->
            val today = prepareMidGame()
            AnkiStatsStore.snapshot = StatsSnapshot(fakeAnki(today), AnkiAccess.OK, timeByDay = fakeAnkiGoTime(today))
            val p = StatsPage(ctx, ActivityHost(attach(Activity(), ctx))).also { it.infosOpen = true }
            FrameLayout(ctx).apply { addView(pageScreen(ctx, p), FrameLayout.LayoutParams(-1, Ui.dp(ctx, 1300)).also { it.topMargin = -Ui.dp(ctx, 480) }) }
        }
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
