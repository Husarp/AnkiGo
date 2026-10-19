package com.husarp.ankigo.shots

import android.content.Context
import android.view.View
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.game.Achievements
import com.husarp.ankigo.game.Metric
import com.husarp.ankigo.stats.AnkiStatsStore
import com.husarp.ankigo.stats.StatsSnapshot
import com.husarp.ankigo.ui.AchievementPopup
import com.husarp.ankigo.ui.StatsPage
import com.husarp.ankigo.ui.StatsSection
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.ZoneId

/** R9-W3-B: the D11-A tier colours (Common green ... Mythical red) on the achievements page, light and dark. */
class R9W3TierShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    @Before fun storeOff() { AnkiStatsStore.enabled = false; AnkiStatsStore.snapshot = StatsSnapshot(); AchievementPopup.reset() }
    @After fun storeOn() { AnkiStatsStore.enabled = true; AnkiStatsStore.snapshot = StatsSnapshot(); AchievementPopup.reset() }

    private val NOW = 1_791_892_800_000L
    private val DAY = 24L * 60 * 60 * 1000
    private val st get() = GameRepo.state!!

    private fun seed() {
        val d = GameRepo.data!!
        st.totalReviews = 31_240; st.bestStreakDays = 12; st.streakDays = 12; st.defeats = 312; st.catches = 312; st.evolutions = 12
        st.history.clear(); st.caught.clear(); st.caught += 1..212; st.seen.clear(); st.seen += 1..260
        st.counters[Metric.DAMAGE_DEALT.name] = 8_420
        st.counters[Metric.STUDY_MINUTES.name] = 40_000 // Mythical
        st.counters[Metric.NEW_CARDS.name] = 380
        st.counters[Metric.DAYS_STUDIED.name] = 41
        st.badges.clear(); st.badges += listOf(1, 5, 7, 16)
        st.achTiers.clear(); st.badgeAt.clear(); st.achSince = 0
        Achievements.check(st, d, NOW - 60 * DAY, mutableListOf(), silent = true)
    }

    private fun page(ctx: Context): View {
        seed()
        val p = StatsPage(ctx, NoHost, StatsSection.ACHIEVEMENTS)
        p.achievementsView().now = { NOW }; p.achievementsView().zone = ZoneId.of("UTC")
        return pageScreen(ctx, p).also { call(p, "render") }
    }

    @Test fun page() { paparazzi.shootPage("r9w3-tiers", listOf(Variants.PHONE)) { ctx -> page(ctx) } }
}
