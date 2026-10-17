package com.husarp.ankigo.shots

import android.content.Context
import android.view.View
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.game.AchFilter
import com.husarp.ankigo.stats.AnkiStatsStore
import com.husarp.ankigo.stats.StatsSnapshot
import com.husarp.ankigo.ui.AchievementPopup
import com.husarp.ankigo.ui.StatsPage
import com.husarp.ankigo.ui.StatsSection
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.ZoneId

/** R4 fix batch B9: the pinned group header of the Achievements page must not stay on screen when a filter leaves no group. */
class R4FixAShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    @Before fun storeOff() { AnkiStatsStore.enabled = false; AnkiStatsStore.snapshot = StatsSnapshot(); AchievementPopup.reset() }
    @After fun storeOn() { AnkiStatsStore.enabled = true; AnkiStatsStore.snapshot = StatsSnapshot(); AchievementPopup.reset() }

    private val NOW = 1_791_892_800_000L

    private fun layoutNow(ctx: Context, v: View) {
        val dm = ctx.resources.displayMetrics
        v.measure(View.MeasureSpec.makeMeasureSpec(dm.widthPixels, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(dm.heightPixels, View.MeasureSpec.EXACTLY))
        v.layout(0, 0, dm.widthPixels, dm.heightPixels)
    }

    @Test fun aFilterThatLeavesNoGroupClearsThePinnedHeader() {
        paparazzi.shootPage("r4fixa-sticky-cleared", listOf(Variants.PHONE), themes = listOf(Theme.LIGHT)) { ctx ->
            val p = StatsPage(ctx, NoHost, StatsSection.ACHIEVEMENTS)
            val av = p.achievementsView()
            av.now = { NOW }; av.zone = ZoneId.of("UTC")
            av.scrollIntoGroup("Battle", 260)
            val screen = pageScreen(ctx, p)
            call(p, "render")
            layoutNow(ctx, screen)
            assertTrue("the header of the group being read is pinned", av.stickyShown())
            av.setFilter(AchFilter.RECENT) // nothing was reached in the last days: no group is left
            call(p, "render")
            assertFalse("the old header goes as soon as the page is rebuilt", av.stickyShown())
            layoutNow(ctx, screen)
            assertFalse("and it does not come back with the layout", av.stickyShown())
            screen
        }
    }
}
