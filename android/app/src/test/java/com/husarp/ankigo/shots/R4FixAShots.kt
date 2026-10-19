package com.husarp.ankigo.shots

import android.view.View
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.game.AchFilter
import com.husarp.ankigo.stats.AnkiStatsStore
import com.husarp.ankigo.stats.StatsSnapshot
import com.husarp.ankigo.ui.AchievementPopup
import com.husarp.ankigo.ui.StatsPage
import com.husarp.ankigo.ui.StatsSection
import org.junit.After
import android.view.ViewGroup
import android.widget.TextView
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.ZoneId

/** R4 fix batch B9, redone for R10 W2 (the pinned header is gone): a filter that leaves no group leaves no stale header or row on the page. */
class R4FixAShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    @Before fun storeOff() { AnkiStatsStore.enabled = false; AnkiStatsStore.snapshot = StatsSnapshot(); AchievementPopup.reset() }
    @After fun storeOn() { AnkiStatsStore.enabled = true; AnkiStatsStore.snapshot = StatsSnapshot(); AchievementPopup.reset() }

    private val NOW = 1_791_892_800_000L

    private fun texts(v: View, out: MutableList<String> = ArrayList()): List<String> {
        if (v.visibility == View.GONE) return out
        if (v is TextView) out += v.text.toString()
        if (v is ViewGroup) for (i in 0 until v.childCount) texts(v.getChildAt(i), out)
        return out
    }

    @Test fun aFilterThatLeavesNoGroupClearsTheOldHeaders() {
        paparazzi.shootPage("r4fixa-filter-cleared", listOf(Variants.PHONE), themes = listOf(Theme.LIGHT)) { ctx ->
            val p = StatsPage(ctx, NoHost, StatsSection.ACHIEVEMENTS)
            val av = p.achievementsView()
            av.now = { NOW }; av.zone = ZoneId.of("UTC")
            val screen = pageScreen(ctx, p)
            call(p, "render")
            assertTrue("BATTLE" in texts(screen))
            av.setFilter(AchFilter.RECENT) // nothing was reached in the last days: no group is left
            call(p, "render")
            val t = texts(screen)
            assertFalse(t.toString(), "BATTLE" in t)
            assertTrue(t.toString(), t.any { it.startsWith("Nothing reached") })
            screen
        }
    }
}
