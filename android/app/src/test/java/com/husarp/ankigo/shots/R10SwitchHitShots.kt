package com.husarp.ankigo.shots

import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.game.GameEvent
import com.husarp.ankigo.game.StatCalc
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Round 10, W10: "Switch Pokémon" (GameActivity.switchMain) with a wild one in the fight. The entrance plays first and the bar still
 * shows the HP from before the hit; the wild one's attack lands after it and the bar ends on the HP the saved game has.
 * PNGs: r10-switchhit-now (right after the tap), r10-switchhit-done (4 s later).
 */
class R10SwitchHitShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    @Before fun freshNotifier() = forgetQueuedNotify()

    @After fun cleanUp() {
        BattleShotKit.reset()
        Shots.files(paparazzi.context).getSharedPreferences("study", 0).edit().remove("reduceMotion").commit()
        forgetQueuedNotify()
    }

    /**
     * GameRepo coalesces its listener calls behind a "queued" flag; a test that ends before the posted call ran leaves it set for the rest
     * of the JVM, so no later change would notify anybody (the same care as R9StaleMainShots takes). Cleared around each test here.
     */
    private fun forgetQueuedNotify() {
        GameRepo::class.java.getDeclaredField("notifyQueued").apply { isAccessible = true }.setBoolean(GameRepo, false)
    }

    private val sizes = listOf(Size.PHONE, Size.SPLIT)

    /** The tap: the saved game has the hit already, the panel shows the new main at the HP it came in with, and the log says who attacks. */
    @Test fun theEntranceShowsTheHpFromBeforeTheHit() {
        paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.PHONE, Theme.LIGHT))
        val ctx = Shots.files(paparazzi.context)
        FakeGame.install(ctx)
        val b = BattleShotKit.bound(ctx)
        val t2 = GameRepo.state!!.mon("t2")!!
        val before = t2.hp
        val max = StatCalc.maxHp(FakeGame.data, t2)
        b.call("switchMain", "t2")
        assertEquals("t2", GameRepo.state!!.main!!.uid)
        assertEquals("t2", b.arena!!.playerUid)
        assertTrue("the wild one hit it: ${t2.hp} of $before", t2.hp < before)
        assertEquals("the bar shows the HP before the hit while the entrance plays", before, b.arena!!.playerHpShown)
        assertTrue(before <= max)
    }

    /** With reduced motion nothing is animated: the panel is drawn from the saved game, which has the hit in it. */
    @Test fun reducedMotionDrawsTheSavedGameWithTheHitInIt() {
        paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.PHONE, Theme.LIGHT))
        val ctx = Shots.files(paparazzi.context)
        FakeGame.install(ctx)
        ctx.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", true).commit()
        val b = BattleShotKit.bound(ctx)
        val t2 = GameRepo.state!!.mon("t2")!!
        val before = t2.hp
        b.call("switchMain", "t2")
        assertTrue(t2.hp < before)
        assertEquals("t2", b.arena!!.playerUid)
        assertEquals(t2.hp, b.arena!!.playerHpShown)
        assertTrue(b.player!!.idle)
    }

    /** The pictures: right after the tap, and 4 s later (phone + split, light + dark). */
    @Test fun now() = paparazzi.shootWith("r10-switchhit-now", sizes, offsetMillis = 60) { ctx ->
        val b = BattleShotKit.bound(ctx)
        b.call("switchMain", "t2")
        b.root
    }

    @Test fun done() = paparazzi.shootWith("r10-switchhit-done", sizes, offsetMillis = 4000) { ctx ->
        val b = BattleShotKit.bound(ctx)
        b.call("switchMain", "t2")
        b.root
    }

    /** The hit shows as events too: MainChanged, the marker, then the wild one's Attack at the new main. */
    @Test fun theEventsAreInOrder() {
        paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.PHONE, Theme.LIGHT))
        val ctx = Shots.files(paparazzi.context)
        FakeGame.install(ctx)
        BattleShotKit.bound(ctx)
        val r = GameRepo.run(ctx) { it.setMain("t3") }
        assertTrue(r.events[0] is GameEvent.MainChanged)
        assertTrue(r.events[1] is GameEvent.SwitchHit)
        assertTrue((r.events[2] as GameEvent.Attack).let { !it.byPlayer && it.target == GameRepo.state!!.main!!.name })
    }
}
