package com.husarp.ankigo.shots

import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.R
import com.husarp.ankigo.game.*
import com.husarp.ankigo.stats.*
import com.husarp.ankigo.ui.*
import com.husarp.ankigo.update.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class R42IShots {
    @get:Rule val paparazzi = Shots.rule()
    private var oldUpdates: Updates.State? = null
    @Before fun offline() { AnkiStatsStore.enabled = false }
    @After fun reset() {
        AnkiStatsStore.enabled = true; AnkiStatsStore.snapshot = StatsSnapshot()
        oldUpdates?.let { setUpdates(it) }
    }
    private fun texts(v: View): List<String> = (if (v is TextView) listOf(v.text.toString()) else emptyList()) +
        if (v is ViewGroup) (0 until v.childCount).flatMap { texts(v.getChildAt(it)) } else emptyList()
    private fun setUpdates(s: Updates.State) {
        if (oldUpdates == null) oldUpdates = Updates.state
        Updates::class.java.getDeclaredField("state").apply { isAccessible = true }.set(Updates, s)
    }

    @Test fun trainerAndDistinctCardChart() = paparazzi.shootPage("i-stats", listOf(Variant("phone", 412, 1500), Variant("narrow", 320, 1500))) { ctx ->
        val s = GameRepo.state!!; val today = GameRepo.game!!.dayKey()
        s.trainerLevel = 12; s.day = today; s.reviewsToday = 300
        s.history.clear()
        val counts = (0L..6L).associate { i ->
            val day = LocalDate.parse(today).minusDays(i).toString()
            s.history[day] = DayLog(reviews = if (i == 0L) 300 else 60 + i.toInt(), dailyGoal = 200)
            day to if (i == 0L) 170 else 50 + i.toInt()
        }
        val a = AnkiStats("test", 1, today, counts, emptyMap(), emptyMap(), Counts(), 42, List(30) { 1 }, 0, 0, 1)
        AnkiStatsStore.snapshot = StatsSnapshot(stats = a, access = AnkiAccess.OK)
        val page = StatsPage(ctx, NoHost).apply { preset(period = ChartPeriod.D7) }
        val screen = pageScreen(ctx, page)
        call(page, "render")
        val t = texts(screen)
        assertTrue(t.any { it.startsWith("Level 12 · ") })
        assertFalse(t.any { it.startsWith("Trainer level") || it.startsWith("Daily goal · faded") })
        assertTrue("Answers today" in t); assertTrue("300" in t); assertTrue("170" in t)
        assertTrue("Distinct cards · learning repeats count once" in t)
        screen
    }

    @Test fun updatesDateCountAndLastCheck() {
        for (manual in listOf(false, true)) paparazzi.shoot("i-updates-${if (manual) "manual" else "auto"}", sizes = listOf(Size.PHONE)) { ctx ->
            setUpdates(Updates.State(lastCheckedAt = 1794816000000L, lastCheckManual = manual))
            val notes = listOf(
                WhatsNew.Version("1.7.0", listOf(WhatsNew.Item("Clearer item descriptions.")), listOf(WhatsNew.Item("Healing numbers show the HP restored.")), "2026-11-11"),
                WhatsNew.Version("1.6.0", listOf(WhatsNew.Item("Earlier changes.")), releasedOn = "2026-11-09"),
                WhatsNew.Version("1.5.0", listOf(WhatsNew.Item("Earlier changes.")), releasedOn = "2026-11-08"),
                WhatsNew.Version("1.4.0", listOf(WhatsNew.Item("Hidden older version.")), releasedOn = "2026-11-08"))
            val screen = updatesScreen(ShotCtx(ctx), "1.7.0", notes)
            // Render before attachment so assertions do not depend on Paparazzi's first frame.
            fun render(v: View) { if (v is UpdateSection) v.render(Updates.state); if (v is ViewGroup) (0 until v.childCount).forEach { render(v.getChildAt(it)) } }
            render(screen)
            val t = texts(screen)
            assertTrue(t.any { it.startsWith("Last checked:") && it.endsWith(if (manual) "(manual)" else "(auto)") })
            assertTrue("Current · 2 changes" in t)
            assertTrue(listOf("11 Nov 2026", "9 Nov 2026", "8 Nov 2026").all { it in t })
            assertFalse("1.4.0" in t)
            screen
        }
    }

    @Test fun availableUpdateKeepsItsReleaseDateDuringDownload() = paparazzi.shoot("i-update-available", sizes = listOf(Size.PHONE)) { ctx ->
        setUpdates(Updates.State(
            latest = Updates.Latest("1.8.0", "AnkiGo-1.8.0.apk", "https://example.invalid/update", 2202009, "https://example.invalid/release", "2026-11-16T09:00:00Z"),
            phase = Updates.Phase.Downloading(40, 880000, 2202009, 1000), lastCheckedAt = 1794816000000L, lastCheckManual = true))
        val section = UpdateSection(ShotCtx(ctx), "1.7.0", emptyList()).apply { render(Updates.state) }
        assertTrue("Released 16 Nov 2026" in texts(section))
        section
    }

    @Test fun bundledUpdatesShowActualCurrentChangeCountAndDates() = paparazzi.shootPage("i-updates-bundled", listOf(Variant("full", 412, 1700))) { ctx ->
        setUpdates(Updates.State(lastCheckedAt = 1794816000000L, lastCheckManual = false))
        val notes = WhatsNew.load(ctx)
        val screen = updatesScreen(ShotCtx(ctx), "1.7.0", notes)
        val t = texts(screen)
        assertTrue("Current · 18 changes" in t)
        assertTrue(listOf("1.7.0", "1.6.0", "1.5.0", "11 Nov 2026", "9 Nov 2026", "8 Nov 2026").all { it in t })
        assertFalse("1.4.0" in t)
        screen
    }

    @Test fun unknownForecastTimingShowsAnExplicitMessage() = paparazzi.shootPage("i-forecast-unavailable", listOf(Variant("phone", 412, 1300))) { ctx ->
        val s = GameRepo.state!!; s.settings.dayStartHour = 3
        val today = GameRepo.game!!.dayKey(); s.day = today
        val stats = AnkiStats("test", 1, today, mapOf(today to 170), emptyMap(), emptyMap(), Counts(), 42, List(30) { 0 }, 0, 0, 1,
            dayStartHour = 3, forecastAvailable = false)
        AnkiStatsStore.snapshot = StatsSnapshot(stats = stats, access = AnkiAccess.OK, dayStartHour = 3)
        val page = StatsPage(ctx, NoHost).apply { preset(metric = ChartMetric.DUE, period = ChartPeriod.D7) }
        val screen = pageScreen(ctx, page); call(page, "render")
        val t = texts(screen)
        assertTrue(StatsText.FORECAST_UNAVAILABLE in t)
        assertFalse(t.any { it.startsWith("Tomorrow:") || it == "Nothing due in this period." })
        assertTrue("42" in t)
        screen
    }

    @Test fun cachedYesterdayWaitsForTodaysCountWithoutShiftingTheForecast() = paparazzi.shootPage("i-stats-refreshing", listOf(Variant("phone", 412, 1300))) { ctx ->
        val s = GameRepo.state!!; s.settings.dayStartHour = 3
        val today = GameRepo.game!!.dayKey(); s.day = today; s.reviewsToday = 300
        val oldDay = LocalDate.parse(today).minusDays(1).toString()
        val stats = AnkiStats("test", 1, oldDay, mapOf(oldDay to 170), emptyMap(), emptyMap(), Counts(), 42,
            List(30) { if (it == 0) 11 else if (it == 1) 22 else 0 }, 0, 0, 1, dayStartHour = 3)
        AnkiStatsStore.snapshot = StatsSnapshot(stats = stats, access = AnkiAccess.OK, dayStartHour = 3)
        val page = StatsPage(ctx, NoHost).apply { preset(metric = ChartMetric.DUE, period = ChartPeriod.D7) }
        val screen = pageScreen(ctx, page); call(page, "render")
        val t = texts(screen)
        assertTrue("…" in t)
        assertTrue("Tomorrow: 22 · Next 7 days: 22" in t)
        assertFalse(t.any { it.startsWith("Tomorrow: 11") })
        screen
    }

    @Test fun berryItemCard() = paparazzi.shootPage("i-defense-berry", listOf(Variant("phone", 412, 869))) { ctx ->
        val page = ShopPage(ctx, NoHost, bagOnly = true)
        pageScreen(ctx, page)
        val spec = ItemCard.Spec("ganlon-berry")
        LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(ctx, 16), Ui.dp(ctx, 20), Ui.dp(ctx, 16), Ui.dp(ctx, 20))
            setBackgroundColor(Ui.c(ctx, R.color.ag_card))
            addView(ItemCard.header(page, spec) {})
            addView(ItemCard.body(page, spec))
            assertTrue(texts(this).any { "Defense ×1.5" in it && "this fight" in it })
        }
    }
}
