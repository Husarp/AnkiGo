package com.husarp.ankigo.shots

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.game.*
import com.husarp.ankigo.stats.AnkiStatsStore
import com.husarp.ankigo.stats.StatsSnapshot
import com.husarp.ankigo.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class R30XCollectionsShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    private fun descendants(v: View): List<View> = listOf(v) + if (v is ViewGroup) (0 until v.childCount).flatMap { descendants(v.getChildAt(it)) } else emptyList()

    @Test fun collectionsInBothThemes() {
        AnkiStatsStore.enabled = false; AnkiStatsStore.snapshot = StatsSnapshot()
        try {
            for (theme in Theme.entries) {
                paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.PHONE, theme))
                val ctx = Shots.files(paparazzi.context)
                FakeGame.scenario = FakeGame.Scenario.MID_GAME; FakeGame.install(ctx)
                val s = GameRepo.state!!
                s.achTiers.clear(); s.counters.clear()
                for ((metric, items) in ItemCollections.kinds) {
                    val n = if (metric == Metric.PANTRY_KINDS) items.size else items.size / 2
                    items.take(n).forEach { ItemCollections.record(s, metric, it) }
                }
                Achievements.onReady(s, GameRepo.data!!, 1_794_052_800_000L)
                val page = StatsPage(ctx, NoHost, StatsSection.ACHIEVEMENTS)
                val screen = pageScreen(ctx, page)
                call(page, "render")
                descendants(screen).single { it.contentDescription == "Item collections tab" }.performClick()
                call(page, "render")
                for (row in AchievementTable.ROWS.filter { it.group == "Item collections" }) {
                    val tile = descendants(screen).single { it.contentDescription?.startsWith(row.title + ",") == true } as ViewGroup
                    assertEquals(3, (tile.parent as ViewGroup).childCount)
                    assertTrue(AchStyle.ICONS.containsKey(row.icon))
                }
                paparazzi.snapshot(screen, "x-collections-${theme.name.lowercase()}")
                page.achievementsView().open("berry-sampler"); call(page, "render")
                val progress = descendants(screen).filterIsInstance<TextView>().single { it.text.contains("4 / 8 kinds") }
                val dm = ctx.resources.displayMetrics
                screen.measure(View.MeasureSpec.makeMeasureSpec(dm.widthPixels, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(dm.heightPixels, View.MeasureSpec.EXACTLY))
                screen.layout(0, 0, dm.widthPixels, dm.heightPixels)
                val sheet = descendants(screen).filterIsInstance<DragSheet>().single()
                val overlay = sheet.parent as ViewGroup
                // Capture the native sheet's final frame, before Paparazzi detaches and finishes its animators.
                for (i in 0 until overlay.childCount) UiMotion.finish(overlay.getChildAt(i))
                assertEquals(1f, sheet.alpha, .001f)
                assertEquals(0f, sheet.translationY, .001f)
                assertTrue(sheet.height > 0)
                assertTrue(progress.getGlobalVisibleRect(android.graphics.Rect()))
                paparazzi.snapshot(screen, "x-detail-${theme.name.lowercase()}", 1000)
            }
        } finally { AnkiStatsStore.enabled = true; AnkiStatsStore.snapshot = StatsSnapshot() }
    }
}
