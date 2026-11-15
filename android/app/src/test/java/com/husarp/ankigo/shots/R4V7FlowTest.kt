package com.husarp.ankigo.shots

import android.content.Context
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.game.CashKind
import com.husarp.ankigo.game.GameEvent
import com.husarp.ankigo.game.Tier
import com.husarp.ankigo.ui.BattlePanelView
import com.husarp.ankigo.ui.EventPlayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * R4-V7 flow: when the result tiles show and hide, driven through the real [EventPlayer] and [BattlePanelView] (reduced motion, so the
 * events play at once; the rules are the same as with animation). Rendered by Paparazzi only for its Android Context.
 */
class R4V7FlowTest {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    private fun ctx(): Context {
        paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.PHONE, Theme.LIGHT))
        val c = Shots.files(paparazzi.context)
        FakeGame.install(c)
        c.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", true).commit()
        return c
    }

    private val answer = listOf<GameEvent>(GameEvent.Counted(3, 10, 5, 1, false), GameEvent.Charge(10, 1, 2))
    private fun encounter() = GameEvent.Encounter(1, "Next", 5, false, Tier.NORMAL, 10, 10, false, false)
    private fun defeatBatch() = listOf<GameEvent>(
        GameEvent.Counted(3, 10, 6, 1, false), GameEvent.Defeated(147, "Wildling", 40),
        GameEvent.XpGained("m", "Mainy", 40, 7, 0, 100), GameEvent.Cash(10, 20, CashKind.DEFEAT), encounter(),
    )
    private fun catchBatch() = listOf<GameEvent>(
        GameEvent.Counted(3, 10, 6, 1, false), GameEvent.Caught("w1", 147, "Wildling", 12, false, null, true, true),
        GameEvent.Cash(10, 20, CashKind.DEFEAT), encounter(),
    )

    @Test fun aFightResultHoldsTheNextOneUntilContinue() {
        val panel = BattlePanelView(ctx())
        val player = EventPlayer(panel)
        player.enqueue(defeatBatch())
        assertTrue("tiles show after the fight", panel.tilesShown)
        assertTrue("next wild one waits", panel.holdsNextFight)
        player.enqueue(answer)
        assertTrue("an incoming answer keeps the result", panel.tilesShown)
        assertTrue(panel.holdsNextFight)
        assertTrue(player.continueBattle())
        assertFalse(panel.holdsNextFight)
    }

    @Test fun aCatchShowsTilesToo() {
        val panel = BattlePanelView(ctx())
        val player = EventPlayer(panel)
        player.enqueue(catchBatch())
        assertTrue(panel.tilesShown)
        player.clear()
        assertFalse("clear() ends them", panel.tilesShown)
    }

    @Test fun smallWindowShowsThemAndKeepsContinuePending() {
        val panel = BattlePanelView(ctx())
        panel.setCompact(true)
        val player = EventPlayer(panel)
        player.enqueue(defeatBatch())
        assertTrue(panel.tilesShown)
        assertTrue("a small window waits for Continue too", panel.holdsNextFight)
        assertTrue(player.continueBattle())
        assertFalse(panel.holdsNextFight)
    }

    @Test fun batchedResultsStillWaitForContinue() {
        val panel = BattlePanelView(ctx())
        val player = EventPlayer(panel)
        player.enqueue(defeatBatch() + defeatBatch())
        assertTrue(panel.tilesShown)
        assertTrue(panel.holdsNextFight)
        assertTrue(player.continueBattle())
        assertFalse(panel.holdsNextFight)
        assertEquals(true, player.idle)
    }

    @Test fun nothingShowsWithoutAFightEnd() {
        val panel = BattlePanelView(ctx())
        val player = EventPlayer(panel)
        player.enqueue(answer)
        assertFalse(panel.tilesShown)
    }
}
