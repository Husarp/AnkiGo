package com.husarp.ankigo.stats

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.husarp.ankigo.Anki
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.Local
import com.husarp.ankigo.Report
import com.husarp.ankigo.game.Achievements
import com.husarp.ankigo.game.GameData
import com.husarp.ankigo.game.GameEvent
import com.husarp.ankigo.game.GameState
import com.husarp.ankigo.game.Metric
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

// TICKET R4-07 (PLAN 79): the two achievements the game save can't know, "Study time" (AnkiGo's own timer files) and "New
// cards learned" (AnkiDroid's history). AnkiStatsStore.refresh calls [maybeFeed] after each stats refresh; the totals are
// worked out on a background thread, then fed to the game on the main thread (Achievements.feed saves and re-renders).

/** The pure parts: the all-time totals from the timer files, the AnkiDroid search, the "is there news" check, the feeding. */
object AchievementFeedMath {
    /** `introduced:N` with a window longer than any collection: every card ever answered for the first time. */
    const val ALL_TIME_DAYS = 36_500

    private val oneDay = { _: Long -> "all" }

    /** Minutes spent answering: reviews.csv lines (`epochMillis,nid,ord,ease,ms,source`) + live-time.csv lines (`epochMillis,ms`), each answer capped as the Stats screen does. */
    fun studyMinutes(reviewLines: Sequence<String>, liveLines: Sequence<String>): Long {
        val ms = (timeInAnkiGo(reviewLines, oneDay)["all"] ?: 0L) + (timeLive(liveLines, oneDay)["all"] ?: 0L)
        return ms / 60_000L
    }

    /** All cards answered for the very first time, through the stats module's official-API search [count]. */
    fun newCardsAllTime(count: (String) -> Int): Long = count(AnkiQueries.introduced(ALL_TIME_DAYS)).toLong()

    /** True when feeding would change anything: a metric never fed before, or a reading above the stored one. [cards] null = AnkiDroid not read. */
    fun hasNews(state: GameState, minutes: Long, cards: Long?): Boolean {
        fun news(m: Metric, v: Long) = m.name !in state.counters || v > (state.counters[m.name] ?: 0L)
        return news(Metric.STUDY_MINUTES, minutes) || (cards != null && news(Metric.NEW_CARDS, cards))
    }

    /** Feeds both totals (the cards only when AnkiDroid could be read); returns the new events, tiers lowest first. */
    fun feed(state: GameState, data: GameData, minutes: Long, cards: Long?, now: Long): List<GameEvent> {
        val out = ArrayList<GameEvent>(Achievements.feed(state, data, Metric.STUDY_MINUTES, minutes, now))
        if (cards != null) out += Achievements.feed(state, data, Metric.NEW_CARDS, cards, now)
        return out
    }

    /** The toast for new tiers, or null when there are none. */
    fun toastText(events: List<GameEvent>): String? {
        val tiers = events.filterIsInstance<GameEvent.AchievementReached>()
        return if (tiers.isEmpty()) null else "Achievement: " + tiers.joinToString { "${it.title} — ${it.tierName}" }
    }
}

object AchievementFeed {
    /** At most one feed per this time (the stats screen refreshes far more often). */
    const val MIN_GAP_MS = 5 * 60_000L

    /** Tests switch this off so nothing reads a phone's files or AnkiDroid. */
    internal var enabled = true

    private val running = AtomicBoolean(false)
    private val lastStartedAt = AtomicLong(0L)
    private val main by lazy { Handler(Looper.getMainLooper()) }
    private val io by lazy { Executors.newSingleThreadExecutor { r -> Thread(r, "ankigo-ach-feed").apply { isDaemon = true } } }

    /**
     * Starts a feed unless one ran in the last [MIN_GAP_MS] or is running. Never blocks, never throws: any failure is logged to the
     * Report and ignored (AnkiDroid not readable -> only the study time is fed). New tiers get a toast (their events are also in the game log).
     */
    fun maybeFeed(ctx: Context) {
        if (!enabled || !AnkiStatsStore.enabled) return
        val now = System.currentTimeMillis()
        if (now - lastStartedAt.get() < MIN_GAP_MS) return
        if (!running.compareAndSet(false, true)) return
        lastStartedAt.set(now)
        val app = ctx.applicationContext
        try {
            io.execute {
                try {
                    val minutes = readMinutes(app)
                    val cards = readNewCards(app)
                    main.post { apply(app, minutes, cards) }
                } catch (t: Throwable) { // a background thread must never take the app down
                    Report.fail("ACH", "reading the totals", t)
                } finally {
                    running.set(false)
                }
            }
        } catch (t: Throwable) {
            running.set(false)
            Report.fail("ACH", "starting the feed", t)
        }
    }

    /** Main thread. Nothing happens while the game is not ready, or when the readings add nothing. */
    private fun apply(app: Context, minutes: Long, cards: Long?) {
        try {
            val state = GameRepo.state
            if (GameRepo.status != GameRepo.Status.READY || state == null) { lastStartedAt.set(0L); return } // the game wasn't loaded yet: try again at the next refresh
            if (!AchievementFeedMath.hasNews(state, minutes, cards)) return
            val events = GameRepo.run(app) { g -> AchievementFeedMath.feed(g.state, g.data, minutes, cards, g.clock()) }
            // new tiers: GameRepo.run offers the events to the achievement popup (no toast)
        } catch (t: Throwable) {
            Report.fail("ACH", "feeding the game", t)
        }
    }

    private fun readMinutes(app: Context): Long {
        fun lines(f: java.io.File): Sequence<String> = if (f.exists()) f.readLines().asSequence() else emptySequence()
        return AchievementFeedMath.studyMinutes(lines(Local.reviewsFile(app)), lines(LiveTimeFile.file(app.filesDir)))
    }

    /** Null when AnkiDroid can't be read (not installed / no permission / an error): the feed then leaves the number alone. */
    private fun readNewCards(app: Context): Long? = try {
        Anki.load(app)
        val access = AnkiAccess.decide(
            installed = Anki.installed(app).any { it.pkg == Anki.pkg },
            supported = Anki.studySupported(app),
            permission = Anki.hasPermission(app),
        )
        if (access != AnkiAccess.OK) null else AchievementFeedMath.newCardsAllTime { q -> Anki.countCards(app, q) }
    } catch (t: Throwable) {
        Report.fail("ACH", "reading the new cards from AnkiDroid", t)
        null
    }
}
