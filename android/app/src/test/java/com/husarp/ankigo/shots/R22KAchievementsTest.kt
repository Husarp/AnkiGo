package com.husarp.ankigo.shots

import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.game.AchView
import com.husarp.ankigo.ui.AchievementDetail
import com.husarp.ankigo.game.AchCelebration
import com.husarp.ankigo.game.AchievementTable
import com.husarp.ankigo.ui.AchievementNotice
import com.husarp.ankigo.ui.AchievementPopup
import com.husarp.ankigo.ui.BottomNav
import com.husarp.ankigo.ui.SettingsPage
import com.husarp.ankigo.ui.SettingsSection
import com.husarp.ankigo.ui.StatsPage
import com.husarp.ankigo.ui.StatsSection
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import com.husarp.ankigo.stats.AnkiStatsStore
import com.husarp.ankigo.stats.StatsSnapshot
import org.junit.Rule
import org.junit.Test

class R22KAchievementsTest {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    @Before fun isolateStats() { AnkiStatsStore.enabled = false; AnkiStatsStore.snapshot = StatsSnapshot() }
    @After fun restoreStats() { AnkiStatsStore.enabled = true; AnkiStatsStore.snapshot = StatsSnapshot(); AchievementPopup.reset() }
    private fun descendants(v: View): List<View> = listOf(v) + if (v is ViewGroup) (0 until v.childCount).flatMap { descendants(v.getChildAt(it)) } else emptyList()
    private fun text(v: View) = descendants(v).filterIsInstance<TextView>().map { it.text.toString() }
    private fun setup(): android.content.Context {
        val ctx = Shots.files(paparazzi.context)
        FakeGame.scenario = FakeGame.Scenario.MID_GAME
        FakeGame.install(ctx)
        return ctx
    }

    @Test fun gridHasThreeColumnsAndAllTierPipsEvenWhenLocked() {
        val ctx = setup()
        GameRepo.state!!.achTiers.clear()
        val page = StatsPage(ctx, NoHost, StatsSection.ACHIEVEMENTS)
        val screen = pageScreen(ctx, page)
        call(page, "render")
        paparazzi.snapshot(screen, "r22k-grid")
        val tiles = descendants(screen).filter { it.contentDescription?.startsWith("Answers given,") == true }
        assertEquals(1, tiles.size)
        val tile = tiles.single() as LinearLayout
        assertEquals(3, (tile.parent as ViewGroup).childCount)
        assertEquals(AchievementTable.row("answers")!!.tiers.size, (tile.getChildAt(2) as ViewGroup).childCount)
        tile.performClick(); call(page, "render")
        val texts = text(screen)
        for (name in listOf("Common", "Rare", "Epic", "Legendary", "Mythical")) assertTrue(name, texts.any { it.contains(name) })
    }

    @Test fun secretTabIsPresentWithoutAddingUndecidedAchievements() {
        val ctx = setup()
        val page = StatsPage(ctx, NoHost, StatsSection.ACHIEVEMENTS)
        val screen = pageScreen(ctx, page)
        page.achievementsView().setTab("Secret"); call(page, "render")
        assertTrue("Secret" in text(screen))
        assertTrue("Secret · 0 of 0 found" in text(screen))
        assertTrue("No secret achievements yet" in text(screen))
    }

    @Test fun noteGroupsAchievementsAndOpensOnTap() {
        val ctx = setup()
        var tapped = false
        val batch = listOf(AchCelebration("answers", "Answers given", 0, "Common", 500), AchCelebration("catches", "Pokémon caught", 1, "Rare", 100))
        val note = AchievementPopup.noteCard(ctx, batch) { tapped = true }
        assertTrue("2 achievements" in text(note))
        assertTrue(note.isClickable)
        note.performClick(); assertTrue(tapped)
        paparazzi.snapshot(note, "r22k-note-batch")
        val single = AchievementPopup.noteCard(ctx, batch.take(1)) {}
        assertTrue("Common" in text(single)); assertTrue("Answers given" in text(single))
    }

    @Test fun lockedSecretRevealsNeitherTitleNorCondition() {
        val ctx = setup()
        val page = StatsPage(ctx, NoHost, StatsSection.ACHIEVEMENTS)
        pageScreen(ctx, page)
        val hidden = AchView("secret-test", "Secret", "Spoiler title", "Spoiler condition", "sword", true, null,
            -1, null, emptyList(), 0, 1, 0f, listOf(1))
        val tile = page.achievementsView().tile(hidden)
        assertEquals(listOf("???"), text(tile))
        assertFalse(tile.contentDescription.toString().contains("Spoiler"))
        val sheet = AchievementDetail.sheet(ctx, hidden, 0, java.time.ZoneId.of("UTC"))
        assertTrue("Secret" in text(sheet))
        assertFalse(text(sheet).any { it.contains("Spoiler") })
        paparazzi.snapshot(tile, "r22k-secret-lock")
    }

    @Test fun lookSettingSavesTheSelectedNotificationStyle() {
        val ctx = setup()
        val c = ShotCtx(ctx)
        val page = SettingsPage(c, NoHost)
        val screen = pageScreen(c, page)
        page.open(SettingsSection.LOOK)
        assertTrue("Achievement pop-ups" in text(screen))
        for ((label, mode) in listOf("Off" to 2, "Pop-up" to 0, "Small note" to 1)) {
            var target: View = descendants(screen).filterIsInstance<TextView>().first { it.text.toString() == label }
            while (!target.isClickable) target = target.parent as View
            target.performClick()
            assertEquals(mode, AchievementPopup.mode(c))
        }
        assertEquals("Stats, new achievements", BottomNav.describe(BottomNav.Tab.STATS, true))
        assertEquals("Stats", BottomNav.describe(BottomNav.Tab.STATS, false))
    }

    @Test fun notificationPreferenceAndUnreadDotAreIndependent() {
        val ctx = setup()
        ctx.getSharedPreferences("look", 0).edit().clear().commit()
        assertEquals(1, AchievementPopup.mode(ctx))
        for (mode in 0..2) { AchievementPopup.setMode(ctx, mode); assertEquals(mode, AchievementPopup.mode(ctx)) }
        AchievementNotice.seen(ctx); assertFalse(AchievementNotice.unseen(ctx))
        GameRepo.state!!.achTiers.getOrPut("answers") { mutableListOf() }.add(123L)
        assertTrue(AchievementNotice.unseen(ctx))
        AchievementNotice.seen(ctx); assertFalse(AchievementNotice.unseen(ctx))
    }
}
