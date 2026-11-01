package com.husarp.ankigo.shots

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.ui.*
import com.husarp.ankigo.stats.AnkiStatsStore
import com.husarp.ankigo.stats.StatsSnapshot
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class R25CAchievementsTest {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    private fun descendants(v: View): List<View> = listOf(v) + if (v is ViewGroup) (0 until v.childCount).flatMap { descendants(v.getChildAt(it)) } else emptyList()

    @Test fun unreadFollowsEachAchievementAndNewTiers() {
        val ctx = Shots.files(paparazzi.context)
        FakeGame.scenario = FakeGame.Scenario.MID_GAME
        FakeGame.install(ctx)
        ctx.getSharedPreferences("look", 0).edit().clear().commit()
        val st = GameRepo.state!!
        st.achTiers.clear(); st.badges.clear()
        st.achTiers["answers"] = mutableListOf(123L)
        st.achTiers["catches"] = mutableListOf(124L)
        assertTrue(AchievementNotice.unseen(ctx, "answers"))
        AchievementNotice.seen(ctx, "answers")
        assertFalse(AchievementNotice.unseen(ctx, "answers"))
        assertTrue(AchievementNotice.unseen(ctx, "catches"))
        st.achTiers["answers"]!!.add(125L)
        assertTrue(AchievementNotice.unseen(ctx, "answers"))
        AnkiStatsStore.enabled = false
        AnkiStatsStore.snapshot = StatsSnapshot()
        try {
            val page = StatsPage(ctx, NoHost, StatsSection.ACHIEVEMENTS)
            val screen = pageScreen(ctx, page)
            call(page, "render")
            assertTrue(AchievementNotice.unseen(ctx, "answers"))
            assertTrue(descendants(screen).any { it.contentDescription == "Achievements, new achievements" })
            assertTrue(descendants(screen).any { it.contentDescription?.startsWith("Answers given,") == true && it.contentDescription.toString().endsWith("new achievement") })
            paparazzi.snapshot(screen, "r25c-unread-grid")
            page.achievementsView().open("answers"); call(page, "render")
            assertFalse(AchievementNotice.unseen(ctx, "answers"))
            assertTrue(AchievementNotice.unseen(ctx, "catches"))
            assertFalse(descendants(screen).filterIsInstance<TextView>().any { it.text.contains("Counted since the update") })
            paparazzi.snapshot(screen, "r25c-read-detail")
            page.achievementsView().close()
            st.badges.addAll(listOf(7, 18, 8, 9, 10, 5, 6, 16, 19, 20))
            page.achievementsView().setTab("Badges"); call(page, "render")
            assertNotEquals(AchStyle.badgeColor(ctx, "badge-ultra"), AchStyle.badgeColor(ctx, "badge-baby"))
            val baby = descendants(screen).first { it.contentDescription?.startsWith("First Baby Pokémon caught,") == true } as ViewGroup
            assertTrue(baby.getChildAt(baby.childCount - 1) is TextView) // R25g: colour replaces completion checks
            paparazzi.snapshot(screen, "r25c-coloured-badges")
        } finally { AnkiStatsStore.enabled = true; AnkiStatsStore.snapshot = StatsSnapshot() }
    }
}
