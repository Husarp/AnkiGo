package com.husarp.ankigo.shots

import android.view.View
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.TextView
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.game.*
import com.husarp.ankigo.stats.*
import com.husarp.ankigo.ui.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class R31ISettingsStatsAlertsTest {
    @get:Rule val paparazzi = Shots.rule()
    @After fun cleanup() { BattleShotKit.reset(); AnkiStatsStore.enabled = true; AnkiStatsStore.snapshot = StatsSnapshot() }
    private fun descendants(v: View): List<View> = listOf(v) + if (v is ViewGroup) (0 until v.childCount).flatMap { descendants(v.getChildAt(it)) } else emptyList()
    private fun texts(v: View) = descendants(v).filterIsInstance<TextView>().map { it.text.toString() }

    @Test fun settingsDescribeEveryCategory() = paparazzi.shootPage("i-settings", listOf(Variant("tall", 412, 1800))) { ctx ->
        val root = pageScreen(ctx, SettingsPage(ctx, NoHost))
        val text = texts(root)
        SettingsSection.entries.forEach { assertTrue(it.name, it.subtitle in text) }
        assertEquals("How the game looks", SettingsSection.LOOK.subtitle)
        root
    }

    @Test fun allChartsAndPeriodsHideInapplicableOptions() {
        AnkiStatsStore.enabled = false
        for (theme in Theme.entries) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.PHONE, theme))
            val ctx = Shots.files(paparazzi.context)
            FakeGame.install(ctx)
            val page = StatsPage(ctx, NoHost)
            pageScreen(ctx, page)
            for (metric in ChartMetric.entries) for (period in ChartPeriod.entries) for (deck in listOf(null, "Test deck")) {
                setField(page, "metric", metric)
                setField(page, "deck", deck)
                val series = ChartData.build(metric, period, deck, StatsSnapshot(), emptyMap(), "2026-11-08")
                val row = call(page, "filterChips", series) as ViewGroup
                val labels = texts(row)
                val label = "$metric/$period/$deck"
                assertEquals(label, series.deckApplies, (deck ?: "All decks") in labels)
                assertFalse(label, (if (deck == null) "Test deck" else "All decks") in labels)
                val grains = ChartData.grains(metric, series.period)
                assertEquals(label, grains.isNotEmpty(), series.grain.label in labels)
                val expected = (if (metric.usesPeriod) 1 else 0) + (if (series.deckApplies) 1 else 0) +
                    (if (grains.isNotEmpty()) 1 else 0) + (if (metric == ChartMetric.BUTTONS) 1 else 0)
                assertEquals(label, expected, row.childCount)
                if (deck != null && period == ChartPeriod.D30 && metric in listOf(ChartMetric.TIME, ChartMetric.REVIEWS, ChartMetric.CARD_STATES, ChartMetric.STREAK_CALENDAR))
                    paparazzi.snapshot(row, "i-options-${metric.name.lowercase()}-${theme.name.lowercase()}")
            }
        }
    }

    @Test fun achievementsUseNewOrderIconsAndNoCatches() = paparazzi.shootPage("i-achievements", listOf(Variant("tall", 412, 1800))) { ctx ->
        AnkiStatsStore.enabled = false
        val page = StatsPage(ctx, NoHost, StatsSection.ACHIEVEMENTS)
        val root = pageScreen(ctx, page)
        call(page, "render")
        val text = texts(root)
        assertFalse("Pokémon caught" in text)
        assertEquals(AchStyle.ICONS["secret-one-shot"], AchStyle.ICONS[AchievementTable.row("goals")!!.icon])
        val tabs = descendants(root).mapNotNull { it.contentDescription?.toString() }.filter { it.endsWith(" tab") }
        assertTrue(tabs.indexOf("Economy tab") < tabs.indexOf("Item collections tab"))
        assertTrue(tabs.indexOf("Special tab") < tabs.indexOf("Item collections tab"))
        root
    }

    @Test fun pokemonNoticesOpenTheirOwnDetails() {
        for (theme in Theme.entries) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.PHONE, theme))
            val ctx = Shots.files(paparazzi.context)
            FakeGame.install(ctx)
            ctx.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", false).commit()
            val mon = GameRepo.state!!.mons.last()
            assertNotEquals(GameRepo.state!!.main?.uid, mon.uid)
            val panel = BattlePanelView(ctx).also {
                it.renderAll()
                it.measure(View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(700, View.MeasureSpec.EXACTLY))
                it.layout(0, 0, 800, 700)
            }
            val events = listOf(GameEvent.LevelUp(mon.uid, mon.name, 21, 50, 60),
                GameEvent.Info("Bond grew! ${mon.name} reached Level 3. bonus", mon.uid),
                GameEvent.Info("${mon.name} is ready to evolve", mon.uid))
            for ((index, event) in events.withIndex()) {
                panel.toastLayer.clear()
                val player = EventPlayer(panel)
                try {
                    player.enqueue(listOf(event))
                    assertEquals(mon.uid, panel.toastLayer.shown?.uid)
                    val dialog = DialogShots.capture { assertTrue(panel.toastLayer.shownCard!!.performClick()) }
                    assertNotNull(dialog)
                    val scene = DialogShots.scene(ctx, dialog!!)
                    assertTrue(texts(scene).any { it.contains(mon.name) })
                    paparazzi.snapshot(scene, "i-notice-details-$index-${theme.name.lowercase()}")
                } finally { player.clear() }
            }
            val card = XpCardView(ctx, TeamXpCards.of(listOf(GameEvent.XpGained(mon.uid, mon.name, 20, mon.level, 1, 100)), "another").single(), false, 10f)
            card.measure(View.MeasureSpec.makeMeasureSpec(380, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            card.layout(0, 0, card.measuredWidth, card.measuredHeight)
            val dialog = DialogShots.capture {
                for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_CANCEL)) {
                    val touch = MotionEvent.obtain(0L, 10L, action, 20f, 20f, 0)
                    try { assertTrue(card.dispatchTouchEvent(touch)) } finally { touch.recycle() }
                }
                assertTrue(card.performClick())
            }
            assertNotNull(dialog)
            paparazzi.snapshot(DialogShots.scene(ctx, dialog!!), "i-xp-details-${theme.name.lowercase()}")
        }
    }

    @Test fun retiredProgressCannotCreateAnUnreadIndicatorOrPopup() {
        val ctx = Shots.files(paparazzi.context)
        FakeGame.install(ctx)
        val state = GameRepo.state!!
        state.achTiers.clear(); state.badges.clear()
        state.achTiers["catches"] = mutableListOf(1L, 2L, 3L)
        assertFalse(AchievementNotice.unseen(ctx, "catches"))
        assertFalse(AchievementNotice.unseen(ctx))
        AchievementPopup.reset()
        AchievementPopup.offer(listOf(GameEvent.AchievementReached("catches", 2, "Pokémon caught", "Epic")))
        assertTrue(AchievementPopup.waiting().isEmpty())
        AchievementPopup.reset()
    }

    @Test fun toastIdentityDistinguishesPokemonWithTheSameName() {
        val queue = ToastQueue()
        assertEquals(ToastQueue.Offer.SHOW, queue.offer(ToastQueue.Item(ToastKind.BOND, "Reached Level 3 Bond", uid = "one"), 0))
        assertEquals(ToastQueue.Offer.QUEUED, queue.offer(ToastQueue.Item(ToastKind.BOND, "Reached Level 3 Bond", uid = "two"), 1))
        assertEquals("two", queue.finish(2)!!.uid)
    }
}
