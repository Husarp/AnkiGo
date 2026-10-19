package com.husarp.ankigo.shots

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.game.CashKind
import com.husarp.ankigo.game.DuplicateResult
import com.husarp.ankigo.game.GameEvent
import com.husarp.ankigo.game.HealReason
import com.husarp.ankigo.game.Tier
import com.husarp.ankigo.ui.ArenaView
import com.husarp.ankigo.ui.BattlePanelView
import com.husarp.ankigo.ui.EventPlayer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * R9-W6 CX-A flow, through the real [EventPlayer] / [BattlePanelView] / [ArenaView]: the batches a catch really produces
 * (Game.resolveWild: Caught, catch badges, Duplicate, the main's XpGained, level-up events, Healed, trainer events, Encounter).
 * The player's XP burst plays inside the Caught step, whatever sits between; the later XpGained step plays nothing of its own.
 */
class R9CatchXpFlowTest {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    @After fun cleanUp() = BattleShotKit.reset()

    private fun ctx(xpAnimation: Boolean = true): Context {
        paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.PHONE, Theme.LIGHT))
        val c = Shots.files(paparazzi.context)
        FakeGame.install(c)
        c.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", false).commit()
        com.husarp.ankigo.ui.AppPrefs.setXpAnimation(c, xpAnimation)
        return c
    }

    private fun measure(v: View) {
        val d = v.resources.displayMetrics.density
        v.measure(View.MeasureSpec.makeMeasureSpec((412 * d).toInt(), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec((869 * d).toInt(), View.MeasureSpec.EXACTLY))
        v.layout(0, 0, v.measuredWidth, v.measuredHeight)
    }

    private var stop = 0L
    private fun advance(c: Context, v: View? = null) {
        repeat(3) { i ->
            stop += 60_000
            paparazzi.snapshot(FrameLayout(c).apply { if (i == 0 && v != null && v.parent == null) addView(v, FrameLayout.LayoutParams(-1, -1)) }, "r9-catchxp-advance", stop)
        }
    }

    /** Runs the whole chain of waits (each timer posts the next one): snapshots until the player has nothing left. */
    private fun runAll(c: Context, player: EventPlayer, v: View) {
        advance(c, v)
        var n = 0
        while (!player.idle && n++ < 12) advance(c)
    }

    private fun priv(o: Any, name: String): Any? = getField(o, name)
    private fun queueKinds(p: EventPlayer): List<String> = (priv(p, "queue") as Collection<*>).map { item ->
        if (item!!.javaClass.simpleName == "Ev") priv(item, "e")!!.javaClass.simpleName else item.javaClass.simpleName
    }

    private val caught = GameEvent.Caught("w1", 147, "Wildling", 12, false, null, true, true)
    private val counted = GameEvent.Counted(3, 10, 6, 1, false)
    private val encounter = GameEvent.Encounter(1, "Next", 5, false, Tier.NORMAL, 10, 10, false, false, "u2")
    private fun mainXp(uid: String) = GameEvent.XpGained(uid, "Mainy", 40, 7, 30, 100)

    /** Game.resolveWild's auto catch: Caught, catch badges, the swap, the main's XP, the heal, trainer events, the next fight. */
    private fun batch(uid: String, withXp: Boolean = true) = buildList<GameEvent> {
        add(counted); add(caught)
        add(GameEvent.BadgeEarned(7, "First catch"))
        add(GameEvent.Duplicate("Wildling", 12, 10, DuplicateResult.SWAPPED, releasedName = "Old"))
        if (withXp) add(mainXp(uid))
        add(GameEvent.Healed(uid, "Mainy", 5, 20, 30, HealReason.CATCH))
        add(GameEvent.Cash(10, 20, CashKind.DEFEAT))
        add(encounter)
    }

    private fun mainUid() = GameRepo.state!!.main!!.uid

    @Test fun theXpBurstIsFoundBehindTheBadgeAndTheSwapAndLeftInTheQueue() {
        val c = ctx()
        val panel = BattlePanelView(c)
        val player = EventPlayer(panel, continueOnPanel = false)
        measure(panel)
        player.enqueue(batch(mainUid()))
        // Counted played, Caught is playing (its click is a timer away): the XpGained still waits in the queue, behind the badge and the swap
        val kinds = queueKinds(player)
        assertTrue(kinds.toString(), kinds.indexOf("XpGained") > kinds.indexOf("Duplicate") && kinds.indexOf("Duplicate") > kinds.indexOf("BadgeEarned"))
        assertNull("nothing has played yet", priv(player, "xpRun"))
        assertFalse(player.idle)
    }

    @Test fun theBurstPlaysOnceFromTheBallAndTheLaterStepOnlyFinishes() {
        val c = ctx()
        val panel = BattlePanelView(c)
        val player = EventPlayer(panel, continueOnPanel = false)
        panel.renderAll()
        measure(panel)
        val arena = panel.arena
        arena.xpOnScreen = { true }   // no window in the renderer
        player.enqueue(batch(mainUid()))
        runAll(c, player, panel)
        assertTrue("every event played", player.idle)
        assertNull("the burst's mark was consumed by its own step", priv(player, "xpRun"))
        val plan = arena.xpPlan
        assertNotNull("the orbs flew (the arena is on screen)", plan)
        assertTrue("and they left the ball at the click (not 520 ms later from the empty spot): ${plan!!.firstLaunch}", plan.firstLaunch < 100f && plan.lastLaunch <= 600f)
        assertFalse("the ball is gone at the end", arena.ballShown)
    }

    @Test fun aBatchWithoutXpPlaysLikeToday() {
        val c = ctx()
        val panel = BattlePanelView(c)
        val player = EventPlayer(panel, continueOnPanel = false)
        panel.renderAll()
        measure(panel)
        player.enqueue(batch(mainUid(), withXp = false))
        runAll(c, player, panel)
        assertTrue(player.idle)
        assertNull(priv(player, "xpRun"))
        assertNull("no XP, no orbs", panel.arena.xpPlan)
        assertFalse(panel.arena.ballShown)
    }

    @Test fun teamXpAheadOfTheMainsDoesNotHideTheMainsBurst() {
        val c = ctx()
        val panel = BattlePanelView(c)
        val player = EventPlayer(panel, continueOnPanel = false)
        panel.renderAll()
        measure(panel)
        panel.arena.xpOnScreen = { true }
        val events = batch(mainUid()).toMutableList()
        events.add(events.indexOfFirst { it is GameEvent.XpGained }, GameEvent.XpGained("holder", "Helper", 20, 5, 10, 50))
        player.enqueue(events)
        runAll(c, player, panel)
        assertTrue(player.idle)
        val plan = panel.arena.xpPlan
        assertNotNull(plan)
        assertTrue("one burst, from the ball", plan!!.firstLaunch < 100f)
    }

    @Test fun withTheXpAnimationOffTheBarFillsAtTheClickAndNothingPlaysTwice() {
        val c = ctx(xpAnimation = false)
        val panel = BattlePanelView(c)
        val player = EventPlayer(panel, continueOnPanel = false)
        panel.renderAll()
        measure(panel)
        player.enqueue(batch(mainUid()))
        runAll(c, player, panel)
        assertTrue(player.idle)
        assertNull(priv(player, "xpRun"))
        assertNull("no orbs", panel.arena.xpPlan)
        assertFalse(panel.arena.ballShown)
    }

    /** Small steps (100 ms) until [cond]; false = never within [max] steps. The first step puts [v] on a parent. */
    private fun stepUntil(c: Context, v: View, max: Int = 150, cond: () -> Boolean): Boolean {
        repeat(max) { i ->
            stop += 100
            paparazzi.snapshot(FrameLayout(c).apply { if (i == 0 && v.parent == null) addView(v, FrameLayout.LayoutParams(-1, -1)) }, "r9-catchxp-step", stop)
            if (cond()) return true
        }
        return false
    }

    private fun bannerVisible(a: ArenaView) = (priv(a, "banner") as View).visibility == View.VISIBLE

    /** The burst is demonstrably running: the mark is set, the orbs are planned and timed (the renderer detaches the view after every snapshot, which drops the effect itself), the ball is on the field, the banner shows. */
    private fun state(p: EventPlayer, a: ArenaView) = "xpRun=${priv(p, "xpRun") != null} landing=${a.xpLastLandingMs} plan=${a.xpPlan != null} ball=${a.ballShown} banner=${bannerVisible(a)} idle=${p.idle} t=$stop"

    private fun burstRunning(p: EventPlayer, a: ArenaView) =
        priv(p, "xpRun") != null && a.xpLastLandingMs > 0L && a.xpPlan != null && a.ballShown && bannerVisible(a)

    @Test fun continueDuringTheBurstCutsItCleanly() {
        val c = ctx()
        val panel = BattlePanelView(c)
        val player = EventPlayer(panel, continueOnPanel = false)
        panel.renderAll()
        measure(panel)
        val arena = panel.arena
        arena.xpOnScreen = { true }
        player.enqueue(batch(mainUid()))
        val started = stepUntil(c, panel) { burstRunning(player, arena) }
        assertTrue("the burst starts: ${state(player, arena)}", started)
        assertTrue(player.holding)
        assertTrue(player.continueBattle())
        assertNull("the mark goes with the cut", priv(player, "xpRun"))
        runAll(c, player, panel)
        assertTrue(player.idle)
        assertFalse("no ball left on the field", arena.ballShown)
        assertFalse("no banner left", bannerVisible(arena))
        assertNull(priv(player, "xpRun"))
    }

    @Test fun clearDropsTheMark() {
        val c = ctx()
        val panel = BattlePanelView(c)
        val player = EventPlayer(panel, continueOnPanel = false)
        panel.renderAll()
        measure(panel)
        val arena = panel.arena
        arena.xpOnScreen = { true }
        player.enqueue(batch(mainUid()))
        val started = stepUntil(c, panel) { burstRunning(player, arena) }
        assertTrue("the burst starts: ${state(player, arena)}", started)
        player.clear()
        panel.cancelAnimations()   // what the caller does after clear() (it redraws from the state)
        arena.clearBall(); arena.hideBanner()
        assertNull(priv(player, "xpRun"))
        assertTrue(player.idle)
        assertFalse(arena.ballShown)
        // pending callbacks are gone: nothing re-creates the mark / banner / ball later
        advance(c)
        assertNull(priv(player, "xpRun"))
        assertFalse("no banner comes back", bannerVisible(arena))
        assertFalse(arena.ballShown)
    }

    @Test fun xpAnimationOffAfterAnAnimatedCatchUsesTheFallbackNotTheOldPlan() {
        val c = ctx()
        val panel = BattlePanelView(c)
        val player = EventPlayer(panel, continueOnPanel = false)
        panel.renderAll()
        measure(panel)
        val arena = panel.arena
        arena.xpOnScreen = { true }
        player.enqueue(batch(mainUid()))
        runAll(c, player, panel)
        assertTrue(player.idle)
        assertNotNull("the first catch flew orbs", arena.xpPlan)
        com.husarp.ankigo.ui.AppPrefs.setXpAnimation(c, false)
        player.enqueue(batch(mainUid()))
        runAll(c, player, panel)
        assertTrue(player.idle)
        assertNull("no orbs now: the old plan is gone", arena.xpPlan)
        assertFalse(arena.ballShown)
        assertFalse(bannerVisible(arena))
    }
}
