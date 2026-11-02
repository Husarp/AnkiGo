package com.husarp.ankigo.shots

import android.view.View
import android.widget.FrameLayout
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.game.Tier
import com.husarp.ankigo.game.GameEvent
import com.husarp.ankigo.ui.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class R27ADefeatFlowTest {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    @After fun cleanup() = BattleShotKit.reset()
    private var clock = 0L
    private fun setup(animated: Boolean = true): Pair<BattlePanelView, EventPlayer> {
        val c = Shots.files(paparazzi.context)
        FakeGame.install(c)
        c.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", false).commit()
        AppPrefs.setXpAnimation(c, animated)
        val panel = BattlePanelView(c)
        panel.renderAll()
        panel.measure(View.MeasureSpec.makeMeasureSpec(824, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1738, View.MeasureSpec.EXACTLY))
        panel.layout(0, 0, 824, 1738)
        panel.arena.xpOnScreen = { true }
        return panel to EventPlayer(panel, continueOnPanel = false)
    }
    private fun events(xp: Boolean = true) = buildList {
        add(GameEvent.Defeated(147, "Wildling", 40))
        if (xp) add(GameEvent.XpGained(GameRepo.state!!.main!!.uid, "Mainy", 40, 7, 30, 100))
    }
    private fun step(panel: BattlePanelView, name: String = "r27a-defeat-step") {
        clock += 100
        paparazzi.snapshot(FrameLayout(panel.context).apply {
            if (panel.parent == null) addView(panel, FrameLayout.LayoutParams(-1, -1))
        }, name, clock)
    }
    private fun until(panel: BattlePanelView, condition: () -> Boolean) {
        repeat(160) { if (condition()) return; step(panel) }
        assertTrue("condition reached within 16 seconds", condition())
    }
    @Test fun defeatDropsOriginalBallThenStartsOneBallOriginBurstAndCleansUp() {
        val (panel, player) = setup()
        player.enqueue(events())
        assertTrue(panel.arena.ballShown)
        assertNull(panel.arena.xpPlan)
        assertNull(getField(player, "xpRun"))
        until(panel) { getField(player, "xpRun") != null }
        val plan = panel.arena.xpPlan
        assertNotNull(plan)
        assertTrue("original ball-origin launch timing", plan!!.firstLaunch < 100f)
        assertTrue(panel.arena.ballShown)
        until(panel) { player.idle }
        assertSame("no second XP burst", plan, panel.arena.xpPlan)
        assertNull(getField(player, "xpRun"))
        assertFalse(panel.arena.ballShown)
    }
    @Test fun xpDisabledStillShowsBallButNeverStartsOrbs() {
        val (panel, player) = setup(false)
        player.enqueue(events())
        assertTrue(panel.arena.ballShown)
        until(panel) { player.idle }
        assertNull(panel.arena.xpPlan)
        assertFalse(panel.arena.ballShown)
    }
    @Test fun noPlayerXpStillFinishesAndRemovesBall() {
        val (panel, player) = setup()
        player.enqueue(events(false))
        until(panel) { player.idle }
        assertFalse(panel.arena.ballShown)
        assertNull(panel.arena.xpPlan)
    }
    @Test fun clearDuringWobbleCancelsPendingBurst() {
        val (panel, player) = setup()
        player.enqueue(events())
        player.clear(); panel.cancelAnimations(); panel.arena.clearBall()
        repeat(30) { step(panel) }
        assertTrue(player.idle)
        assertNull(getField(player, "xpRun"))
        assertNull(panel.arena.xpPlan)
        assertFalse(panel.arena.ballShown)
    }
    @Test fun continueDuringDefeatWobbleRemovesBallAndDoesNotReplayXp() {
        val (panel, player) = setup()
        val encounter = GameEvent.Encounter(1, "Next", 5, false, Tier.NORMAL, 10, 10, false, false, "next")
        player.enqueue(listOf(GameEvent.Counted(3, 10, 6, 1, false)) + events() + encounter)
        until(panel) { panel.arena.ballShown }
        assertTrue(player.continueBattle())
        until(panel) { player.idle }
        assertNull(getField(player, "xpRun"))
        assertFalse(panel.arena.ballShown)
    }
    @Test fun reducedMotionSettlesWithoutLeavingABallOrBurst() {
        val (panel, player) = setup()
        panel.context.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", true).commit()
        player.enqueue(events())
        until(panel) { player.idle }
        assertFalse(panel.arena.ballShown)
        assertNull(getField(player, "xpRun"))
    }
    @Test fun originalBallAndXpVisuals() {
        for (ball in listOf("poke-ball", "great-ball", "ultra-ball", "master-ball")) {
            paparazzi.shootWith("r27a-defeat-$ball", listOf(Size.PHONE, Size.FLOATBATTLE), offsetMillis = 400) { c ->
                val panel = BattlePanelView(c).apply { renderAll() }
                var done = false
                panel.arena.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
                    if (!done && v.width > 0) {
                        done = true
                        val arena = panel.arena
                        arena.setEnemyShown(true); arena.setEnemyCardShown(false)
                        arena.enemySprite.alpha = 0f
                        arena.dropBall(ball, 1)
                        (getField(arena, "ball") as View).apply { translationY = 0f; rotation = -12f }
                        arena.measure(View.MeasureSpec.makeMeasureSpec(arena.width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(arena.height, View.MeasureSpec.EXACTLY))
                        arena.layout(arena.left, arena.top, arena.right, arena.bottom)
                        arena.xpBurstStill(40, 300f)
                    }
                }
                panel
            }
        }
    }
}
