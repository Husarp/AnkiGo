package com.husarp.ankigo.shots

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.game.CashKind
import com.husarp.ankigo.game.EffectKind
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
 * PLAN 259 flow: the Main burns, the wild one faints in a round that also has the Main's end-of-round burn damage. That Effect plays
 * AFTER the wild one's Fainted in the same batch and carries status "brn": the badge must not come back (also not after a redraw
 * from the saved game); a new round / the next Encounter starts clean again.
 */
class R18JStatusWinFlowTest {
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

    private fun stepUntil(c: Context, v: View, max: Int = 200, cond: () -> Boolean): Boolean {
        repeat(max) { i ->
            stop += 100
            paparazzi.snapshot(FrameLayout(c).apply { if (i == 0 && v.parent == null) addView(v, FrameLayout.LayoutParams(-1, -1)) }, "r18j-step", stop)
            if (cond()) return true
        }
        return false
    }

    private fun queueKinds(p: EventPlayer): List<String> = (getField(p, "queue") as Collection<*>).map { item ->
        if (item!!.javaClass.simpleName == "Ev") getField(item, "e")!!.javaClass.simpleName else item.javaClass.simpleName
    }

    private val burn = GameEvent.Effect(true, "Mainy is burned!", 20, 30, "brn")
    private val poison = GameEvent.Effect(false, "Wildling is poisoned!", 20, 30, "psn")
    private val burnTick = GameEvent.Effect(true, "Mainy is hurt by its burn!", 18, 30, "brn", EffectKind.STATUS_DAMAGE, amount = 2)
    private val faint = GameEvent.Fainted(false, "Wildling")
    private val defeated = GameEvent.Defeated(147, "Wildling", 40)
    private val xp = GameEvent.XpGained("u1", "Mainy", 40, 7, 30, 100)
    private val cash = GameEvent.Cash(10, 20, CashKind.DEFEAT)
    private val next = GameEvent.Encounter(1, "Next", 5, false, Tier.NORMAL, 10, 10, false, false, "u2")
    private val caught = GameEvent.Caught("w1", 147, "Wildling", 12, false, null, true, true)

    private class Rig(val panel: BattlePanelView, val player: EventPlayer)

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

    @Test fun theBurnTickAfterTheWinDoesNotBringTheBadgeBack() {
        val c = ctx()
        val r = start(c, listOf(faint, burnTick, defeated, xp, cash, next))
        assertTrue("the badge goes with the faint", stepUntil(c, r.panel) { r.panel.arena.playerStatus == null })
        // play on until the burn tick has been played (the queue no longer holds it), then to the end of the queue
        assertTrue("the tick played", stepUntil(c, r.panel) { "Effect" !in queueKinds(r.player) })
        assertEquals("the badge stays gone after the tick", null, r.panel.arena.playerStatus)
        assertTrue("the rest of the batch plays", stepUntil(c, r.panel) { "Cash" !in queueKinds(r.player) && "XpGained" !in queueKinds(r.player) })
        assertEquals(null, r.panel.arena.playerStatus)
        r.panel.refreshMain()
        assertEquals("and after a redraw from the saved game", null, r.panel.arena.playerStatus)
    }

    @Test fun aCatchTheSameWay() {
        val c = ctx()
        val r = start(c, listOf(faint, caught, burnTick, xp, cash, next))
        assertTrue("the badge goes", stepUntil(c, r.panel) { r.panel.arena.playerStatus == null })
        assertTrue("the tick played", stepUntil(c, r.panel) { "Effect" !in queueKinds(r.player) })
        assertEquals(null, r.panel.arena.playerStatus)
        assertTrue("the rest plays", stepUntil(c, r.panel) { "Cash" !in queueKinds(r.player) })
        assertEquals(null, r.panel.arena.playerStatus)
    }

    @Test fun aTickWhileTheFightGoesOnStillKeepsTheBadge() {
        val c = ctx()
        val r = start(c, listOf(burnTick))
        assertTrue("the tick played", stepUntil(c, r.panel) { "Effect" !in queueKinds(r.player) })
        assertEquals("brn", r.panel.arena.playerStatus)
    }

    @Test fun aNewRoundAfterTheEncounterStartsClean() {
        val c = ctx()
        val r = start(c, listOf(faint, defeated, next))
        assertTrue("the badge goes", stepUntil(c, r.panel) { r.panel.arena.playerStatus == null })
        r.player.enqueue(listOf(GameEvent.RoundStart(1.0, 3, true), burn))
        assertTrue("a new burn in the next fight shows again", stepUntil(c, r.panel) { r.panel.arena.playerStatus == "brn" })
    }
}
