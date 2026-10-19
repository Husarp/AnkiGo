package com.husarp.ankigo.shots

import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.game.GameEvent
import com.husarp.ankigo.ui.AppMenu
import com.husarp.ankigo.ui.Ui
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Rule
import org.junit.Test

/**
 * "Switch Pokémon" in the battle menu (GameActivity.switchMain).
 * PNGs: nextmain-before, nextmain-now (right after the tap), nextmain-done (after the switch animation),
 * nextmain-busy-now / nextmain-busy-done (the tap while a long animation queue is still playing).
 */
class NextMainShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    @After fun cleanUp() = BattleShotKit.reset()

    private val sizes = listOf(Size.PHONE, Size.SPLIT)

    /** nextmain-before: the fight with the first main (Sparkmouse) before the tap. */
    @Test fun before() = paparazzi.shootWith("nextmain-before", sizes) { ctx -> BattleShotKit.bound(ctx).root }

    /** nextmain-now: 60 ms after the tap: the new main's card (name, level, HP, XP) and sprite are already there. */
    @Test fun now() = paparazzi.shootWith("nextmain-now", sizes, offsetMillis = 60) { ctx ->
        val b = BattleShotKit.bound(ctx)
        b.call("switchMain", "t2")
        b.root
    }

    /** nextmain-done: 2 s after the tap, the switch animation is over. */
    @Test fun done() = paparazzi.shootWith("nextmain-done", sizes, offsetMillis = 2000) { ctx ->
        val b = BattleShotKit.bound(ctx)
        b.call("switchMain", "t2")
        b.root
    }

    /** The card already says the new main at the moment the tap returns, with the old queue still full (no toast any more). */
    @Test fun panelFollowsTheTapAtOnceEvenWithAFullQueue() {
        FakeGame.install(Shots.files(paparazzi.context))
        val b = BattleShotKit.bound(Shots.files(paparazzi.context))
        b.player!!.enqueue(listOf(GameEvent.Info("one"), GameEvent.Info("two"), GameEvent.Info("three")))
        assertFalse(b.player!!.idle)
        b.call("switchMain", "t2")
        assertEquals("t2", b.arena!!.playerUid)
        assertEquals("t2", GameRepo.state!!.main!!.uid)
        // R3-X1 removed the "X is your main now." toast on purpose: the card changing (and the short "Go, X!") is the feedback
        assertFalse(Ui.lastToast.orEmpty().contains("is your main now"))
    }

    /** The menu: with a team of 6 the item is enabled; with 1 member it is greyed and the reason sits right under it. */
    @Test fun menuItemIsGreyedWithAReasonWhenBlocked() {
        val ctx = Shots.files(paparazzi.context)
        FakeGame.install(ctx)
        var b = BattleShotKit.bound(ctx)
        var rows = menuRows(b)
        // with a wild Pokémon on stage the last entry flips catch <-> defeat (its label says what a tap does)
        assertEquals(listOf("Pop out", "Pokémon", "Pokédex", "Shop", "Settings", "Switch Pokémon", "Stats", "Battle log", "View settings", "Help"), rows.filter { it.first != 9 }.map { it.second })
        assertTrue(rows.first { it.first == 9 }.second in listOf("Catch this one", "Don't catch this one"))
        assertTrue(rows.first { it.first == 5 }.third)

        FakeGame.scenario = FakeGame.Scenario.EMPTY
        FakeGame.install(ctx)
        b = BattleShotKit.bound(ctx)
        val m = AppMenu(b.root)
        b.activity.fillMenu(m)
        rows = m.describe()
        assertEquals(listOf("Pop out", "Pokémon", "Pokédex", "Shop", "Settings", "Switch Pokémon", "Stats", "Battle log", "View settings", "Help"), rows.filter { it.first != 9 }.map { it.second })
        assertFalse(rows.first { it.first == 5 }.third)
        assertEquals("Needs 2+ Pokémon in your team", m.subOf(5))
        assertEquals("Pop out", rows.first().second)
        assertEquals("Small battle window over other apps", m.subOf(8))
    }

    private fun menuRows(b: BattleShotKit.Built): List<Triple<Int, String, Boolean>> {
        val m = AppMenu(b.root)
        b.activity.fillMenu(m)
        return m.describe()
    }

    /** The tap while three events (about 2.4 s of animation) are still queued. */
    private fun busy(b: BattleShotKit.Built) {
        b.player!!.enqueue(listOf(GameEvent.Info("one"), GameEvent.Info("two"), GameEvent.Info("three")))
        b.call("switchMain", "t2")
    }

    /** nextmain-busy-now: 300 ms after the tap, the queue still plays: the panel must show the new main already. */
    @Test fun busyNow() = paparazzi.shootWith("nextmain-busy-now", sizes, offsetMillis = 300) { ctx ->
        val b = BattleShotKit.bound(ctx)
        busy(b)
        b.root
    }

    /** nextmain-busy-done: 6 s after the tap, everything has played. */
    @Test fun busyDone() = paparazzi.shootWith("nextmain-busy-done", sizes, offsetMillis = 6000) { ctx ->
        val b = BattleShotKit.bound(ctx)
        busy(b)
        b.root
    }
}
