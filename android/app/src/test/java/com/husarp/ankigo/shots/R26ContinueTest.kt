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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** PLAN 388: lifecycle, offscreen switches and encounter selection through the real battle event player. */
class R26ContinueTest {
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
            paparazzi.snapshot(FrameLayout(c).apply { if (i == 0 && v != null && v.parent == null) addView(v, FrameLayout.LayoutParams(-1, -1)) }, "r26b-advance", stop)
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
    private fun heldResult(c: Context, rewardsFinished: Boolean = true): Pair<BattlePanelView, EventPlayer> {
        val panel = BattlePanelView(c)
        val player = EventPlayer(panel, continueOnPanel = false)
        measure(panel, 412, 869)
        GameRepo.state!!.battle = null
        GameRepo.state!!.nextSeed = 388L
        val next = GameRepo.game!!.ensureEncounter().single() as GameEvent.Encounter
        player.enqueue(defeatBatch(next.wildUid).dropLast(1) + next)
        if (!rewardsFinished) return panel to player
        advance(c, panel)
        assertTrue("the rewards played to the end", player.idle)
        assertTrue("Continue is up", player.holding)
        assertNotNull("the next fight waits", parked(player))
        assertNotEquals("and is not on stage", View.VISIBLE, enemyHolder(panel).visibility)
        return panel to player
    }

    @Test fun leavingAndReturningKeepsContinue() {
        val c = ctx()
        val (panel, player) = heldResult(c)
        player.pauseHeldResult() // StudyActivity.onStop
        panel.renderAll() // returning to the screen
        assertTrue("return still waits for Continue", player.holding)
        assertNotNull(parked(player))
    }

    @Test fun switchingThroughTeamKeepsContinue() {
        val c = ctx()
        val (panel, player) = heldResult(c)
        player.pauseHeldResult() // leave for Team
        GameRepo.runOffscreen(c) { it.setMain("t6") }
        player.playPending()
        advance(c, panel)
        assertTrue("switching screens must not start a battle", player.holding)
        assertEquals("t6", GameRepo.state!!.main!!.uid)
        (panel.parent as? android.view.ViewGroup)?.removeView(panel)
        paparazzi.snapshot(panel, "r26b-switch-waits", stop + 1000)
    }

    @Test fun continuePicksWildForTheNewWeakerMain() {
        val c = ctx()
        val (panel, player) = heldResult(c)
        val followingSeed = GameRepo.state!!.nextSeed
        GameRepo.state!!.mon("t6")!!.apply {
            level = 5
            hp = com.husarp.ankigo.game.StatCalc.maxHp(FakeGame.data, this)
        }
        player.enqueue(GameRepo.run(c) { it.requestMain("t6", cardShowing = false) }.events)
        advance(c, panel)
        assertTrue(player.continueBattle())
        advance(c, panel)
        assertTrue("level must follow level 5 main, not the previous level 26 main",
            GameRepo.state!!.battle!!.wild.level < 15)
        assertEquals(com.husarp.ankigo.game.Encounters.wildLevel(5, kotlin.random.Random(388L)), GameRepo.state!!.battle!!.wild.level)
        assertEquals("the following encounter seed is preserved", followingSeed, GameRepo.state!!.nextSeed)
        assertFalse(player.holding)
        (panel.parent as? android.view.ViewGroup)?.removeView(panel)
        paparazzi.snapshot(panel, "r26b-weaker-main-next-wild", stop + 1000)
    }

    @Test fun continueDuringRewardsUsesTheChangedMain() {
        val c = ctx()
        val (panel, player) = heldResult(c, rewardsFinished = false)
        assertFalse(player.idle)
        GameRepo.state!!.mon("t6")!!.apply {
            level = 5
            hp = com.husarp.ankigo.game.StatCalc.maxHp(FakeGame.data, this)
        }
        player.enqueue(GameRepo.run(c) { it.requestMain("t6", cardShowing = false) }.events)
        assertTrue(player.continueBattle())
        advance(c, panel)
        assertFalse(player.holding)
        assertTrue(GameRepo.state!!.battle!!.wild.level < 15)
    }

    @Test fun reducedMotionAndRepeatedSwitchesStillWaitForContinue() {
        val c = ctx()
        val (panel, player) = heldResult(c)
        c.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", true).commit()
        GameRepo.state!!.mon("t6")!!.apply {
            level = 5
            hp = com.husarp.ankigo.game.StatCalc.maxHp(FakeGame.data, this)
        }
        for (uid in listOf("t3", "t2", "t6")) {
            player.enqueue(GameRepo.run(c) { it.requestMain(uid, cardShowing = false) }.events)
            assertTrue(player.holding)
        }
        player.pauseHeldResult()
        assertTrue(player.holding)
        assertTrue(player.continueBattle())
        assertFalse(player.holding)
        assertTrue(GameRepo.state!!.battle!!.wild.level < 15)
    }

    @Test fun continueWithoutASwitchKeepsTheSameEncounter() {
        val c = ctx()
        val (panel, player) = heldResult(c)
        val uid = GameRepo.state!!.battle!!.wild.uid
        player.pauseHeldResult()
        assertTrue(player.continueBattle())
        advance(c, panel)
        assertEquals(uid, GameRepo.state!!.battle!!.wild.uid)
    }

    @Test fun stoppingDuringRewardsSettlesThemButKeepsContinue() {
        val c = ctx()
        val (panel, player) = heldResult(c, rewardsFinished = false)
        player.pauseHeldResult()
        assertTrue(player.idle)
        assertTrue(player.holding)
        assertNotNull(parked(player))
        player.pauseHeldResult()
        assertTrue(player.continueBattle())
        advance(c, panel)
        assertFalse(player.holding)
    }

    @Test fun anActiveOrDifferentBattleCannotBeReselected() {
        ctx()
        val game = GameRepo.game!!
        val battle = GameRepo.state!!.battle!! // FakeGame's active, answered fight
        assertTrue(game.reselectHeldEncounter(battle.wild.uid).isEmpty())
        assertTrue(battle === GameRepo.state!!.battle)
        battle.rounds = 0; battle.answers = 0
        assertTrue(game.reselectHeldEncounter("some-other-fight").isEmpty())
        assertTrue(battle === GameRepo.state!!.battle)
    }
}
