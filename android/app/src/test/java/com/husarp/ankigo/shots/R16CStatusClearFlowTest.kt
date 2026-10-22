package com.husarp.ankigo.shots

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.game.CashKind
import com.husarp.ankigo.game.GameEvent
import com.husarp.ankigo.game.Tier
import com.husarp.ankigo.ui.BattlePanelView
import com.husarp.ankigo.ui.EventPlayer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * PLAN 216 flow, through the real [EventPlayer] / [BattlePanelView] / [ArenaView]: a status badge goes at once when the fight ends
 * (the wild one faints, is caught, flees) or when its Pokémon faints, while the later steps (XP, money, next fight) are still queued.
 * A ball that breaks free keeps the fight, and so the badges.
 */
class R16CStatusClearFlowTest {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    @After fun cleanUp() = BattleShotKit.reset()

    private fun ctx(): Context {
        paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.PHONE, Theme.LIGHT))
        val c = Shots.files(paparazzi.context)
        FakeGame.install(c)
        c.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", false).commit()
        return c
    }

    private fun measure(v: View) {
        val d = v.resources.displayMetrics.density
        v.measure(View.MeasureSpec.makeMeasureSpec((412 * d).toInt(), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec((869 * d).toInt(), View.MeasureSpec.EXACTLY))
        v.layout(0, 0, v.measuredWidth, v.measuredHeight)
    }

    private var stop = 0L

    /** Small steps (100 ms) until [cond]; false = never within [max] steps. The first step puts [v] on a parent. */
    private fun stepUntil(c: Context, v: View, max: Int = 150, cond: () -> Boolean): Boolean {
        repeat(max) { i ->
            stop += 100
            paparazzi.snapshot(FrameLayout(c).apply { if (i == 0 && v.parent == null) addView(v, FrameLayout.LayoutParams(-1, -1)) }, "r16c-step", stop)
            if (cond()) return true
        }
        return false
    }

    private fun queueKinds(p: EventPlayer): List<String> = (getField(p, "queue") as Collection<*>).map { item ->
        if (item!!.javaClass.simpleName == "Ev") getField(item, "e")!!.javaClass.simpleName else item.javaClass.simpleName
    }

    private val burn = GameEvent.Effect(true, "Mainy is burned!", 20, 30, "brn")
    private val poison = GameEvent.Effect(false, "Wildling is poisoned!", 20, 30, "psn")
    private val xp = GameEvent.XpGained("u1", "Mainy", 40, 7, 30, 100)
    private val cash = GameEvent.Cash(10, 20, CashKind.DEFEAT)
    private val next = GameEvent.Encounter(1, "Next", 5, false, Tier.NORMAL, 10, 10, false, false, "u2")
    private val caught = GameEvent.Caught("w1", 147, "Wildling", 12, false, null, true, true)

    private class Rig(val panel: BattlePanelView, val player: EventPlayer)

    /** Plays [events] after a burn on our side and a poison on the wild one; returns once both badges show. */
    private fun start(c: Context, events: List<GameEvent>): Rig {
        val panel = BattlePanelView(c)
        val player = EventPlayer(panel, continueOnPanel = false)
        panel.renderAll()
        measure(panel)
        player.enqueue(listOf(burn, poison) + events)
        assertTrue("both badges show", stepUntil(c, panel) { panel.arena.enemyStatus == "psn" })
        assertEquals("brn", panel.arena.playerStatus)
        return Rig(panel, player)
    }

    @Test fun theWildOneFaintsBothBadgesGoWhileTheRewardsWait() {
        val c = ctx()
        val r = start(c, listOf(GameEvent.Fainted(false, "Wildling"), GameEvent.Defeated(147, "Wildling", 40), xp, cash, next))
        assertTrue("our badge goes", stepUntil(c, r.panel) { r.panel.arena.playerStatus == null })
        assertEquals("theirs with it", null, r.panel.arena.enemyStatus)
        val kinds = queueKinds(r.player)
        assertTrue(kinds.toString(), "XpGained" in kinds && "Cash" in kinds && "Encounter" in kinds)
    }

    @Test fun anAutoCatchClearsBothBadgesAtTheFaintWhileTheBallAndXpWait() {
        val c = ctx()
        val r = start(c, listOf(GameEvent.Fainted(false, "Wildling"), caught, xp, cash, next))
        assertTrue("our badge goes", stepUntil(c, r.panel) { r.panel.arena.playerStatus == null })
        assertEquals(null, r.panel.arena.enemyStatus)
        val kinds = queueKinds(r.player)
        assertTrue(kinds.toString(), "XpGained" in kinds && "Cash" in kinds)   // Caught itself is already playing (Fainted returned 0)
    }

    @Test fun aCaughtWithoutAFaintAheadClearsTooAndBeforeTheBallPlays() {
        val c = ctx()
        val r = start(c, listOf(caught, xp, cash, next))
        assertTrue("our badge goes", stepUntil(c, r.panel) { r.panel.arena.playerStatus == null })
        assertEquals(null, r.panel.arena.enemyStatus)
        assertTrue(queueKinds(r.player).toString(), "XpGained" in queueKinds(r.player))
    }

    @Test fun aSuccessfulBallThrowClearsBothAtTheThrow() {
        val c = ctx()
        val r = start(c, listOf(GameEvent.BallThrow("pokeball", 80, true, 4, 1), caught, xp, cash, next))
        assertTrue("our badge goes", stepUntil(c, r.panel) { r.panel.arena.playerStatus == null })
        assertEquals(null, r.panel.arena.enemyStatus)
        assertTrue("the ball is on the field, Caught still waits", r.panel.arena.ballShown && "Caught" in queueKinds(r.player))
    }

    @Test fun aBallThatBreaksFreeKeepsTheFightAndTheBadges() {
        val c = ctx()
        val r = start(c, listOf(GameEvent.BallThrow("pokeball", 30, false, 4, 1), GameEvent.Info("Wildling broke free!")))
        assertTrue("the ball shows", stepUntil(c, r.panel) { r.panel.arena.ballShown })
        assertEquals("brn", r.panel.arena.playerStatus)
        assertEquals("psn", r.panel.arena.enemyStatus)
        assertTrue("it broke free", stepUntil(c, r.panel) { !r.panel.arena.ballShown })
        assertEquals("brn", r.panel.arena.playerStatus)
        assertEquals("psn", r.panel.arena.enemyStatus)
    }

    @Test fun ourOwnFaintTakesOnlyOurBadgeAtOnceTheMainHealedTakesTheRest() {
        val c = ctx()
        val r = start(c, listOf(GameEvent.Fainted(true, "Mainy"), GameEvent.MainHealed("u1", "Mainy", 30), next))
        assertTrue("our badge goes with the faint", stepUntil(c, r.panel) { r.panel.arena.playerStatus == null })
        assertEquals("psn", r.panel.arena.enemyStatus)
        assertTrue("MainHealed still waits", "MainHealed" in queueKinds(r.player))
        assertTrue(stepUntil(c, r.panel) { r.panel.arena.enemyStatus == null })
    }

    @Test fun theWildOneFleesBothGo() {
        val c = ctx()
        val r = start(c, listOf(GameEvent.Fled("Wildling"), next))
        assertTrue("our badge goes", stepUntil(c, r.panel) { r.panel.arena.playerStatus == null })
        assertEquals(null, r.panel.arena.enemyStatus)
        assertTrue(queueKinds(r.player).toString(), "Encounter" in queueKinds(r.player))
    }

    @Test fun aSwitchStillClearsOurs() {
        val c = ctx()
        val r = start(c, listOf(GameEvent.MainChanged("u1", "Mainy")))
        assertTrue(stepUntil(c, r.panel) { r.panel.arena.playerStatus == null })
        assertEquals("the wild one keeps its badge", "psn", r.panel.arena.enemyStatus)
    }
}
