package com.husarp.ankigo.shots

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.game.CashKind
import com.husarp.ankigo.game.GameEvent
import com.husarp.ankigo.game.Tier
import com.husarp.ankigo.ui.BattlePanelView
import com.husarp.ankigo.ui.EventPlayer
import com.husarp.ankigo.ui.ToastKind
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * R19-T flow, through the real [EventPlayer], [BattlePanelView] and [com.husarp.ankigo.ui.ArenaView] (animations ON, so the events really
 * queue; Paparazzi's looper only moves inside [advance], as in R9NextFightFlowTest):
 *  - PLAN 285: a switch made while a won fight's result holds the next fight plays under the result; the next wild one stays out and
 *    the hold stays until Continue is tapped (a plain answer still releases it).
 *  - PLAN 288: the free hit of a switch raises the "Switched! Wild X gets a free move." card toast; an item's hit does not.
 */
class R19TSwitchHoldTest {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    @After fun cleanUp() = com.husarp.ankigo.data.Backdrops.forget()

    private fun ctx(): Context {
        paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.PHONE, Theme.LIGHT))
        val c = Shots.files(paparazzi.context)
        FakeGame.install(c)
        c.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", false).commit()
        return c
    }

    private fun encounter(uid: String) = GameEvent.Encounter(1, "Next", 5, false, Tier.NORMAL, 10, 10, false, false, uid)
    private fun defeatBatch(uid: String) = listOf<GameEvent>(
        GameEvent.Counted(3, 10, 6, 1, false), GameEvent.Defeated(147, "Wildling", 40),
        GameEvent.XpGained("m", "Mainy", 40, 7, 0, 100), GameEvent.Cash(10, 20, CashKind.DEFEAT), encounter(uid),
    )

    private fun measure(v: View, wDp: Int, hDp: Int) {
        val d = v.resources.displayMetrics.density
        v.measure(View.MeasureSpec.makeMeasureSpec((wDp * d).toInt(), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec((hDp * d).toInt(), View.MeasureSpec.EXACTLY))
        v.layout(0, 0, v.measuredWidth, v.measuredHeight)
    }

    private var stop = 0L
    private fun advance(c: Context, v: View? = null) {
        repeat(3) { i ->
            stop += 60_000
            paparazzi.snapshot(FrameLayout(c).apply { if (i == 0 && v != null && v.parent == null) addView(v, FrameLayout.LayoutParams(-1, -1)) }, "r19t-advance", stop)
        }
    }

    private fun priv(o: Any, name: String): Any? {
        var k: Class<*>? = o.javaClass
        while (k != null) {
            try { return k.getDeclaredField(name).apply { isAccessible = true }.get(o) } catch (_: NoSuchFieldException) { k = k.superclass }
        }
        error("no field $name")
    }
    private fun enemyHolder(panel: BattlePanelView) = priv(panel.arena, "enemyHolder") as View
    private fun parked(p: EventPlayer): Any? = priv(p, "parked")

    /** A won fight whose rewards played to the end: the result holds the next fight (Continue is up, the next wild one is parked). */
    private fun heldResult(c: Context): Pair<BattlePanelView, EventPlayer> {
        val panel = BattlePanelView(c)
        val player = EventPlayer(panel, continueOnPanel = false)
        measure(panel, 412, 869)
        GameRepo.state!!.battle!!.wild.uid = "u2"
        player.enqueue(defeatBatch("u2"))
        advance(c, panel)
        assertTrue("the rewards played to the end", player.idle)
        assertTrue("Continue is up", player.holding)
        assertNotNull("the next fight waits", parked(player))
        assertNotEquals("and is not on stage", View.VISIBLE, enemyHolder(panel).visibility)
        return panel to player
    }

    // ---------------------------------------------------------------- 285

    @Test fun aSwitchOnTheContinueScreenLeavesTheNextFightWaiting() {
        val c = ctx()
        val (panel, player) = heldResult(c)
        val r = GameRepo.run(c) { it.requestMain("t2", cardShowing = false) }   // what the Study screen / Battle tab ask while a result holds
        assertTrue(r.events.any { it is GameEvent.MainChanged })
        player.enqueue(r.events)
        assertTrue("still holding right after the switch is queued", player.holding)
        assertNotNull(parked(player))
        advance(c, panel)
        assertTrue("the switch played", player.idle)
        assertTrue("still holding: Continue is still the way on", player.holding && panel.holdsNextFight)
        assertNotNull("the next fight is still parked", parked(player))
        assertNotEquals("the next wild one never came in", View.VISIBLE, enemyHolder(panel).visibility)
        assertEquals("t2", panel.arena.playerUid)   // the new Pokémon is out
        // Continue starts it
        assertTrue(player.continueBattle())
        advance(c, panel)
        assertFalse(player.holding)
        assertNull(parked(player))
        assertEquals(View.VISIBLE, enemyHolder(panel).visibility)
    }

    @Test fun twoSwitchesInARowDoNotStartItEither() {
        val c = ctx()
        val (panel, player) = heldResult(c)
        player.enqueue(GameRepo.run(c) { it.requestMain("t2", cardShowing = false) }.events)
        advance(c, panel)
        player.enqueue(GameRepo.run(c) { it.requestMain("t3", cardShowing = false) }.events)
        advance(c, panel)
        assertTrue(player.holding); assertNotNull(parked(player))
        assertNotEquals(View.VISIBLE, enemyHolder(panel).visibility)
    }

    @Test fun anAnswerThatArrivesStillStartsTheNextFightAsBefore() {
        val c = ctx()
        val (_, player) = heldResult(c)
        player.enqueue(listOf(GameEvent.Counted(3, 10, 5, 1, false), GameEvent.Charge(10, 1, 2)))
        assertFalse("an answer is the 'next answer starts it' of old", player.holding)
    }

    @Test fun aPendingSwitchFromAnotherScreenPlaysUnderTheResultToo() {
        val c = ctx()
        val (panel, player) = heldResult(c)
        // Team -> "Make main" outside the battle: the events wait in GameRepo, the panel takes them once it is idle
        GameRepo.runOffscreen(c) { it.setMain("t3") }
        assertTrue(player.playPending())
        advance(c, panel)
        assertTrue(player.holding); assertNotNull(parked(player))
    }

    // ---------------------------------------------------------------- 288: the toast

    private fun switchBatch(item: String? = null) = listOf<GameEvent>(
        GameEvent.MainChanged("t2", "Ivysaurus"),
        GameEvent.SwitchHit("Wild Gnarlpup", "Ivysaurus", 40, 60, item),
    )

    @Test fun theFreeHitOfASwitchRaisesTheCardToast() {
        val c = ctx()
        val panel = BattlePanelView(c).also { it.setStudyLayout(false, false); it.renderAll(); it.restoreStudyCharge() }
        measure(panel, 412, 400)
        val player = EventPlayer(panel, continueOnPanel = false)
        player.enqueue(listOf(GameEvent.SwitchHit("Wild Gnarlpup", "Ivysaurus", 40, 60)))   // the hit's own event plays at once (a MainChanged before it takes 800 ms)
        assertEquals(ToastKind.SWITCH, panel.toastLayer.shown?.kind)
        assertEquals("Switched! Wild {{Gnarlpup}} gets a free move.", panel.toastLayer.shown?.text)
    }

    @Test fun anItemsHitIsNotASwitchAndRaisesNoSwitchToast() {
        val c = ctx()
        val panel = BattlePanelView(c).also { it.setStudyLayout(false, false); it.renderAll(); it.restoreStudyCharge() }
        measure(panel, 412, 400)
        val player = EventPlayer(panel, continueOnPanel = false)
        player.enqueue(listOf(GameEvent.SwitchHit("Wild Gnarlpup", "Sparkmouse", 40, 60, "potion")))
        advance(c, panel)
        assertNull(panel.toastLayer.shown)
    }
}
