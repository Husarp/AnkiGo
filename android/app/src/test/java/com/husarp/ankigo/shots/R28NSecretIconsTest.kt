package com.husarp.ankigo.shots

import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.game.AchievementTable
import com.husarp.ankigo.game.Achievements
import com.husarp.ankigo.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Populated design fixture: production intentionally keeps the Secret tab empty. */
class R28NSecretIconsTest {
    @get:Rule val paparazzi = Shots.rule()

    private fun all(v: View): List<View> = listOf(v) +
        if (v is ViewGroup) (0 until v.childCount).flatMap { all(v.getChildAt(it)) } else emptyList()

    @Test fun selectedIconsLightAndDark() {
        for (theme in Theme.entries) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.SMALL, theme).copy(screenHeight = 1880))
            val ctx = Shots.files(paparazzi.context)
            FakeGame.install(ctx)
            val state = GameRepo.state!!
            state.achTiers.clear()
            AchievementTable.ROWS.filter { it.group == "Secret" }.forEach { state.achTiers[it.id] = mutableListOf(1L) }
            val page = StatsPage(ctx, NoHost, StatsSection.ACHIEVEMENTS)
            val screen = pageScreen(ctx, page)
            val av = page.achievementsView()
            av.setTab("Secret")
            AchievementsView::class.java.getDeclaredField("revealTab").apply { isAccessible = true }.setBoolean(av, true)
            call(page, "doRender")
            page.onGone()
            val col = AchievementsView::class.java.getDeclaredField("col").apply { isAccessible = true }.get(av) as LinearLayout
            assertTrue(all(col).filterIsInstance<TextView>().any { it.text == "No secret achievements yet" })
            while (col.childCount > 3) col.removeViewAt(3)
            all(col).filterIsInstance<TextView>().first { it.text.startsWith("Secret ·") }.text = "Secret · 11 of 11 found"
            val secrets = Achievements.view(state, GameRepo.data!!).filter { it.group == "Secret" }
            assertEquals(11, secrets.size)
            for (chunk in secrets.chunked(3)) {
                val row = LinearLayout(ctx)
                for (i in 0..2) {
                    val tile = chunk.getOrNull(i)?.let { av.tile(it) } ?: View(ctx)
                    row.addView(tile, LinearLayout.LayoutParams(0, -2, 1f).apply { if (i > 0) marginStart = Ui.dp(ctx, 8) })
                }
                col.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = Ui.dp(ctx, 8) })
            }
            assertEquals(11, all(col).count { it.contentDescription?.toString()?.endsWith("Earned") == true ||
                it.contentDescription?.toString()?.endsWith("Earned, new achievement") == true })
            paparazzi.snapshot(screen, "n-secret-all-11-${theme.name.lowercase()}")
        }
    }
}
