package com.husarp.ankigo.shots

import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.R
import com.husarp.ankigo.game.Achievements
import com.husarp.ankigo.game.AchievementTable
import com.husarp.ankigo.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class R25GIconsTest {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    private fun descendants(v: View): List<View> = listOf(v) + if (v is ViewGroup) (0 until v.childCount).flatMap { descendants(v.getChildAt(it)) } else emptyList()

    @Test fun tmDiscSparkleUsesEveryTierTint() {
        for (theme in listOf(Theme.LIGHT, Theme.DARK)) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.PHONE, theme))
            val ctx = Shots.files(paparazzi.context)
            val gallery = LinearLayout(ctx).apply { setBackgroundColor(Ui.c(ctx, R.color.ag_base)) }
            for (tier in -1..4) {
                gallery.addView(ImageView(ctx).apply {
                    setImageResource(AchStyle.icon("tm"))
                    imageTintList = android.content.res.ColorStateList.valueOf(AchStyle.medalColor(ctx, tier))
                }, LinearLayout.LayoutParams(Ui.dp(ctx, 48), Ui.dp(ctx, 48)))
            }
            paparazzi.snapshot(gallery, "r110-tm-a2-tiers-${theme.name.lowercase()}")
        }
    }

    @Test fun unearnedBadgesStayMutedUntilUnlocked() {
        val ctx = Shots.files(paparazzi.context)
        FakeGame.scenario = FakeGame.Scenario.MID_GAME
        FakeGame.install(ctx)
        val page = StatsPage(ctx, NoHost, StatsSection.ACHIEVEMENTS)
        val st = GameRepo.state!!
        st.badges.clear()
        val badge = AchievementTable.ROWS.first { it.ankimonBadge != null && it.icon == "badge-legendary" }
        fun iconColour(): Int {
            val v = Achievements.view(st, page.data).first { it.id == badge.id }
            val icon = descendants(page.achievementsView().tile(v)).filterIsInstance<ImageView>().first()
            return (icon.colorFilter as android.graphics.PorterDuffColorFilter).color
        }
        assertEquals(AchStyle.medalColor(ctx, -1), iconColour())
        st.badges.add(badge.ankimonBadge!!)
        assertEquals(AchStyle.badgeColor(ctx, badge.icon), iconColour())
    }

    @Test fun uniqueSecretsStayHiddenUntilEarnedAndUseBareIcons() { secrets(Theme.LIGHT) }
    @Test fun darkSecretIcons() { secrets(Theme.DARK) }
    private fun secrets(theme: Theme) {
        paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.PHONE, theme))
        val ctx = Shots.files(paparazzi.context)
        FakeGame.scenario = FakeGame.Scenario.MID_GAME
        FakeGame.install(ctx)
        val page = StatsPage(ctx, NoHost, StatsSection.ACHIEVEMENTS)
        val st = GameRepo.state!!
        st.achTiers.clear()
        val secrets = AchievementTable.ROWS.filter { it.group == "Secret" }
        assertEquals(11, secrets.size)
        assertEquals(secrets.size, secrets.map { AchStyle.icon(it.icon) }.toSet().size)
        assertTrue(secrets.all { it.icon in AchStyle.ICONS })
        val gallery = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 24, 24, 24); setBackgroundColor(Ui.c(ctx, R.color.ag_base)) }
        for ((i, group) in secrets.chunked(3).withIndex()) {
            val line = LinearLayout(ctx)
            for ((j, row) in group.withIndex()) {
                if ((i * 3 + j) % 3 != 1) st.achTiers[row.id] = mutableListOf(123L)
                val v = Achievements.view(st, page.data).first { it.id == row.id }
                val tile = page.achievementsView().tile(v) as ViewGroup
                assertFalse(descendants(tile).any { it is AchMedal })
                assertEquals(Ui.dp(ctx, 34), descendants(tile).filterIsInstance<ImageView>().single().layoutParams.width)
                assertTrue(tile.getChildAt(tile.childCount - 1) is TextView)
                if (v.tier < 0) { assertEquals("???", v.title); assertEquals("unknown", v.icon); assertFalse(tile.contentDescription.contains(row.title)) }
                line.addView(tile, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(6, 6, 6, 6) })
            }
            gallery.addView(line)
        }
        paparazzi.snapshot(gallery, "r25g-secret-icons-mixed-${theme.name.lowercase()}")
        val medals = LinearLayout(ctx)
        secrets.forEach { row -> medals.addView(AchMedal(ctx, 34).apply { set(AchStyle.icon(row.icon), 0, true) }) }
        gallery.addView(medals)
        paparazzi.snapshot(gallery, "r25g-secret-medals-popup-icons-${theme.name.lowercase()}")
    }

    @Test fun pickedIconsAndCompletedBadges() { picked(Theme.LIGHT) }
    @Test fun darkPickedIconsAndBadges() { picked(Theme.DARK) }
    private fun picked(theme: Theme) {
        paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.PHONE, theme))
        val ctx = Shots.files(paparazzi.context)
        FakeGame.scenario = FakeGame.Scenario.MID_GAME
        FakeGame.install(ctx)
        val page = StatsPage(ctx, NoHost, StatsSection.ACHIEVEMENTS)
        val st = GameRepo.state!!
        st.badges.addAll(listOf(7, 18, 8, 9, 10, 5, 6, 16, 19, 20))
        assertEquals(R.drawable.ic_ach_money, AchStyle.icon("coin"))
        assertEquals(AchStyle.icon("legendary"), AchStyle.icon("badge-legendary"))
        assertEquals(R.drawable.ic_ach_tm, AchStyle.icon("tm"))
        val gallery = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 24, 24, 24); setBackgroundColor(Ui.c(ctx, R.color.ag_base)) }
        val views = Achievements.view(st, page.data)
        val selected = views.filter { it.group == "Badges" || it.icon in listOf("coin", "target", "legendary", "tm") }
        selected.chunked(3).forEach { group ->
            val line = LinearLayout(ctx)
            group.forEach { v ->
                val tile = page.achievementsView().tile(v) as ViewGroup
                if (v.oneOff) assertTrue(tile.getChildAt(tile.childCount - 1) is TextView)
                line.addView(tile, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(6, 6, 6, 6) })
            }
            gallery.addView(line)
        }
        paparazzi.snapshot(gallery, "r25g-picked-icons-badges-${theme.name.lowercase()}")
        page.achievementsView().open("answers")
        val screen = pageScreen(ctx, page)
        call(page, "render")
        paparazzi.snapshot(screen, "r25g-detail-no-completion-checks-${theme.name.lowercase()}")
    }
}
