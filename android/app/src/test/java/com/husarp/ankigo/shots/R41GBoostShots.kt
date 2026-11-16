package com.husarp.ankigo.shots

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.game.AchievementTable
import com.husarp.ankigo.game.PermanentBoosts
import com.husarp.ankigo.stats.AnkiStatsStore
import com.husarp.ankigo.stats.StatsSnapshot
import com.husarp.ankigo.ui.StatsPage
import com.husarp.ankigo.ui.StatsSection
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class R41GBoostShots {
    @get:Rule val paparazzi = Shots.rule()
    @Before fun noExternalFeed() { AnkiStatsStore.enabled = false; AnkiStatsStore.snapshot = StatsSnapshot() }
    @After fun restoreFeed() { AnkiStatsStore.enabled = true; AnkiStatsStore.snapshot = StatsSnapshot() }

    private fun texts(v: View): List<TextView> = (if (v is TextView) listOf(v) else emptyList()) +
        if (v is ViewGroup) (0 until v.childCount).flatMap { texts(v.getChildAt(it)) } else emptyList()

    private fun shoot(tab: String) {
        paparazzi.shootPage("g-${tab.lowercase()}-totals", listOf(Variants.PHONE, Variant("w320", 320, 640))) { ctx ->
            val s = GameRepo.state!!
            s.achTiers.clear(); s.badges.clear(); s.badges += listOf(1, 5, 7, 16, 18)
            s.achTiers["answers"] = MutableList(5) { 1L }
            s.achTiers["defeats"] = MutableList(4) { 1L }
            s.achTiers["types"] = MutableList(2) { 1L }
            s.achTiers[AchievementTable.ROWS.first { it.group == "Secret" }.id] = mutableListOf(1L)
            val page = StatsPage(ctx, NoHost, StatsSection.ACHIEVEMENTS)
            page.achievementsView().setTab(tab)
            val screen = pageScreen(ctx, page)
            call(page, "render")
            val label = texts(screen).single { it.text.toString().startsWith("XP bonus +") }
            assertEquals("XP bonus +5.55%\nMoney bonus +2.5%", label.text.toString())
            // Live totals refresh with new tiers/badges on either tab.
            s.badges += 20
            call(page, "render")
            assertTrue(texts(screen).any { it.text.toString().endsWith(PermanentBoosts.moneyLabel(s)) })
            s.badges -= 20
            call(page, "render")
            screen
        }
    }

    @Test fun achievementTotals() = shoot("All")
    @Test fun badgeTotals() = shoot("Badges")
}
