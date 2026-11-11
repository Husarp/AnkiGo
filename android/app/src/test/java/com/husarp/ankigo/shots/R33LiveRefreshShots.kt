package com.husarp.ankigo.shots

import android.graphics.drawable.BitmapDrawable
import android.os.Handler
import android.widget.ImageView
import android.widget.TextView
import com.husarp.ankigo.Anki
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.StudyActivity
import com.husarp.ankigo.StudyDayRefresh
import com.husarp.ankigo.game.Game
import com.husarp.ankigo.game.StatCalc
import com.husarp.ankigo.stats.AnkiStatsStore
import com.husarp.ankigo.ui.*
import org.junit.After
import org.junit.Before
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class R33LiveRefreshShots {
    @get:Rule val paparazzi = Shots.rule()
    private val oldWidgets = GameRepo.widgetRefresh
    private lateinit var oldClock: () -> Long
    private val pages = mutableListOf<StatsPage>()
    @Before fun rememberClock() { oldClock = StudyDayRefresh.clock }
    @After fun clean() {
        GameRepo.widgetRefresh = oldWidgets
        StudyDayRefresh.clock = oldClock
        pages.forEach { it.onHide(); it.onGone() }
        val wake = getField(StudyDayRefresh, "wake") as? Runnable
        if (wake != null) (getField(StudyDayRefresh, "main") as Handler).removeCallbacks(wake)
        setField(StudyDayRefresh, "wake", null); setField(StudyDayRefresh, "armedAt", null)
        AnkiStatsStore.enabled = true
        BattleShotKit.reset()
    }

    @Test fun deckListAfterLearningBecomesDue() = paparazzi.shootWith("e-decks-learning-due", listOf(Size.PHONE)) { ctx ->
        HomeShotKit.home(ShotCtx(ctx), HomeView.STUDY, StudyDeckKit.decks.map {
            when (it.id) { 1L -> it.copy(learn = 16); 2L -> it.copy(learn = 15); else -> it }
        }).root
    }

    @Test fun studyCountsAfterAnswerAndLearningDue() = paparazzi.shootWith("e-study-live-counts", listOf(Size.PHONE)) { ctx ->
        val act = attach(StudyActivity(), ShotCtx(ctx))
        setField(act, "deckId", 2L); setField(act, "deckName", "Geography::Capitals")
        val root = act.buildContent()
        setField(act, "card", Anki.Review(500, 0, 4, listOf("1m", "6m", "1d", "4d"), emptyList()))
        setField(act, "locked", false)
        (getField(act, "subtitle") as TextView).text = "Card 1 · card 8"
        act.showQueueCounts(Anki.Deck(2, "Geography::Capitals", 15, 20, 7, false), 1)
        (getField(act, "panel") as BattlePanelView).renderAll()
        call(act, "layout"); call(act, "showButtons")
        root
    }

    private fun roll(ctx: android.content.Context, scheduled: Boolean = true, missed: Boolean = true, before: () -> Unit = {}): Long {
        val state = GameRepo.state!!
        var now = java.time.LocalDateTime.of(2026, 11, 10, 3, 59, 59).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        state.settings.dayStartHour = 4
        state.day = "2026-11-09"; state.reviewsToday = if (missed) 24 else 124
        state.lastGoalDay = if (missed) "2026-11-08" else "2026-11-09"; state.streakDays = 12
        state.bag.remove("streak-freeze")
        val g = Game(GameRepo.data!!, state, clock = { now })
        setField(GameRepo, "current", g)
        StudyDayRefresh.clock = { now }
        var widgets = 0
        GameRepo.widgetRefresh = { widgets++ }
        before()
        StudyDayRefresh.schedule(ctx, force = true)
        assertEquals(now + 1000, getField(StudyDayRefresh, "armedAt"))
        val wake = getField(StudyDayRefresh, "wake") as Runnable
        StudyDayRefresh.schedule(ctx)
        assertSame("ordinary game changes keep the scheduled boundary", wake, getField(StudyDayRefresh, "wake"))
        now += 1000
        if (scheduled) wake.run() else StudyDayRefresh.refresh(ctx)
        val notify = getField(GameRepo, "notifyRunnable") as Runnable
        (call(GameRepo, "getHandler") as Handler).removeCallbacks(notify)
        notify.run()
        assertEquals("2026-11-10", state.day)
        assertEquals(0, state.reviewsToday)
        assertEquals(if (missed) 0 else 12, state.streakDays)
        assertTrue("rollover refreshes widgets without an answer", widgets > 0)
        assertTrue("rollover arms the next day", (getField(StudyDayRefresh, "armedAt") as Long) > now)
        val snap = WidgetLogic.summarize(state, now) { StatCalc.maxHp(GameRepo.data!!, it) }
        assertEquals(0, snap.today); assertEquals(if (missed) 0 else 12, snap.streak)
        return now
    }

    @Test fun scheduledRolloverResetsStatsGoalAndStreak() = paparazzi.shootWith("e-stats-rollover", listOf(Size.PHONE)) { ctx ->
        AnkiStatsStore.enabled = false
        lateinit var page: StatsPage
        lateinit var root: android.view.View
        roll(ctx, before = {
            page = StatsPage(ctx, NoHost).also { pages += it }
            root = pageScreen(ctx, page)
            call(page, "doRender")
        })
        root
    }

    @Test fun scheduledRolloverResetsBattleGoal() = paparazzi.shootWith("e-battle-rollover", listOf(Size.PHONE)) { ctx ->
        val b = HomeShotKit.home(ShotCtx(ctx), HomeView.BATTLE)
        val panel = b.panel!!
        @Suppress("UNCHECKED_CAST")
        val watch = getField(panel, "gameWatch") as () -> Unit
        GameRepo.addListener(watch)
        try { roll(ctx, missed = false, before = { panel.renderFooter(GameRepo.state!!) }) }
        finally { GameRepo.removeListener(watch) }
        assertEquals("2026-11-10", getField(b.panel!!, "goalDay"))
        val label = getField(panel, "goalText") as TextView
        assertTrue(label.text.toString().startsWith("0 /"))
        b.root
    }

    @Test fun queuedAnswerPlaybackCannotUndoTheScheduledGoalReset() {
        val ctx = Shots.files(paparazzi.context)
        FakeGame.install(ctx)
        for (neutral in listOf(false, true)) {
            val panel = BattlePanelView(ctx)
            val player = EventPlayer(panel)
            @Suppress("UNCHECKED_CAST")
            val watch = getField(panel, "gameWatch") as () -> Unit
            GameRepo.addListener(watch)
            try {
                setField(player, "busy", true)
                roll(ctx, missed = false, before = {
                    panel.renderFooter(GameRepo.state!!)
                    player.enqueue(listOf(
                        com.husarp.ankigo.game.GameEvent.Counted(3, 10, 124, 5, catchUp = true, neutral = neutral),
                        com.husarp.ankigo.game.GameEvent.Charge(10, 1, 3),
                    ))
                })
                setField(player, "busy", false)
                call(player, "pump")
                assertFalse("the old answer actually played, with its charge still in progress", player.idle)
                assertTrue((getField(panel, "goalText") as TextView).text.toString().startsWith("0 /"))
            } finally { player.clear(); GameRepo.removeListener(watch) }
        }
    }

    @Test fun changingRolloverHourOrWallClockReplacesTheScheduledBoundary() {
        val ctx = Shots.files(paparazzi.context)
        FakeGame.install(ctx)
        var now = java.time.LocalDateTime.of(2026, 11, 10, 3, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        StudyDayRefresh.clock = { now }
        val st = GameRepo.state!!
        st.settings.dayStartHour = 4
        StudyDayRefresh.schedule(ctx, force = true)
        val wake = getField(StudyDayRefresh, "wake")
        st.settings.dayStartHour = 6
        StudyDayRefresh.schedule(ctx)
        assertNotSame(wake, getField(StudyDayRefresh, "wake"))
        assertEquals(com.husarp.ankigo.nextStudyDayAt(now, 6), getField(StudyDayRefresh, "armedAt"))
        now += 86_400_000
        StudyDayRefresh.schedule(ctx, force = true)
        assertEquals(com.husarp.ankigo.nextStudyDayAt(now, 6), getField(StudyDayRefresh, "armedAt"))
    }

    @Test fun detachedBattlePanelCatchesUpWhenReattachedAfterBackgroundRollover() {
        val ctx = Shots.files(paparazzi.context)
        FakeGame.install(ctx)
        val panel = BattlePanelView(ctx)
        val root = android.widget.FrameLayout(ctx).apply { addView(panel, android.widget.FrameLayout.LayoutParams(-1, -1)) }
        roll(ctx, before = {
            panel.renderFooter(GameRepo.state!!)
            paparazzi.snapshot(root, "r33e-goal-before-background", 0)
        })
        assertEquals("2026-11-09", getField(panel, "goalDay"))
        paparazzi.snapshot(root, "r33e-goal-after-background", 0)
        assertEquals("2026-11-10", getField(panel, "goalDay"))
        assertTrue((getField(panel, "goalText") as TextView).text.toString().startsWith("0 /"))
    }

    @Test fun scheduledRolloverResetsWidgetAndResumeAlsoCatchesUp() = paparazzi.shootWith("e-widget-rollover", listOf(Size.PHONE)) { ctx ->
        val now = roll(ctx, scheduled = false)
        val s = WidgetLogic.summarize(GameRepo.state!!, now) { StatCalc.maxHp(GameRepo.data!!, it) }
        ImageView(ctx).apply {
            setImageDrawable(BitmapDrawable(resources, WidgetRender.render(ctx, WidgetRender.Kind.WIDE, s, 360f, 170f, night = (ctx.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES)))
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
    }
}
