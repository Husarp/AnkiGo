package com.husarp.ankigo.shots

import android.view.View
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.game.Answer
import com.husarp.ankigo.game.GameEvent
import com.husarp.ankigo.ui.ActionBarView
import com.husarp.ankigo.ui.BattlePanelView
import com.husarp.ankigo.ui.MonActions
import com.husarp.ankigo.ui.TeamPage
import com.husarp.ankigo.ui.TeamSection
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * R14-D (PLAN 206 + 191): a switch made while a card shows waits for the next answer. The action bar says "Switching to X after this
 * card…" (set by the event, and derived from the saved game so a queue made in Team shows when you come back); the queued Pokémon's
 * Team row says "After this card"; the Team menu row says "Make main" with "After this card · the wild attacks once" in a fight.
 * PNGs: r14d-bar-queued, r14d-team-queued, r14d-menu-make, r14d-menu-cancel (phone, light + dark).
 */
class R14DSwitchQueueShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    @Before fun freshNotifier() = forgetQueuedNotify()

    @After fun cleanUp() {
        BattleShotKit.reset()
        forgetQueuedNotify()
    }

    /** GameRepo coalesces its listener calls behind a "queued" flag; a test that ends before the posted call ran leaves it set (as R10SwitchHitShots takes care of). */
    private fun forgetQueuedNotify() {
        GameRepo::class.java.getDeclaredField("notifyQueued").apply { isAccessible = true }.setBoolean(GameRepo, false)
    }

    private val st get() = GameRepo.state!!

    private fun bar(panel: BattlePanelView): ActionBarView =
        BattlePanelView::class.java.getDeclaredField("logBox").let { it.isAccessible = true; it.get(panel) as ActionBarView }

    private val sizes = listOf(Size.PHONE, Size.SPLIT)

    // ---------------------------------------------------------------- the bar

    @Test fun theQueueEventPutsTheLineOnTheBar() {
        paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.PHONE, Theme.LIGHT))
        val ctx = Shots.files(paparazzi.context)
        FakeGame.install(ctx)
        val b = BattleShotKit.bound(ctx)
        val logBox = bar(b.panel!!)
        assertNull(logBox.queuedShown())
        val r = GameRepo.run(ctx) { it.requestMain("t2") }
        val queued = r.events.single() as GameEvent.SwitchQueued
        b.panel!!.observeStudyEvent(queued)
        assertEquals("Switching to {{${queued.name}}} after this card…", logBox.queuedShown())
        assertTrue(logBox.lineShown())
        assertEquals("t1", st.main!!.uid) // nothing came in yet
    }

    @Test fun aCancelTakesTheLineAwayAgain() {
        paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.PHONE, Theme.LIGHT))
        val ctx = Shots.files(paparazzi.context)
        FakeGame.install(ctx)
        val b = BattleShotKit.bound(ctx)
        val logBox = bar(b.panel!!)
        b.panel!!.observeStudyEvent(GameRepo.run(ctx) { it.requestMain("t2") }.events.single())
        assertNotNull(logBox.queuedShown())
        b.panel!!.observeStudyEvent(GameRepo.run(ctx) { it.requestMain("t1") }.events.single())
        assertNull(logBox.queuedShown())
        assertFalse(logBox.lineShown())
    }

    @Test fun theBarFollowsTheSavedGameWhenAQueueWasMadeElsewhere() {
        paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.PHONE, Theme.LIGHT))
        val ctx = Shots.files(paparazzi.context)
        FakeGame.install(ctx)
        val b = BattleShotKit.bound(ctx)
        val logBox = bar(b.panel!!)
        GameRepo.run(ctx) { it.requestMain("t2") } // the Team list did this; no event reached the bar
        assertNull(logBox.queuedShown())
        logBox.syncQueued() // what the bar does on every change of the saved game and when it comes back to the front
        assertTrue(logBox.queuedShown()!!.contains("after this card"))
        // the queue went without a switch (cancelled / stale): the line goes too
        st.pendingMain = null
        logBox.syncQueued()
        assertNull(logBox.queuedShown())
    }

    @Test fun anAnswerThatAppliesTheQueueKeepsTheLineUntilTheSwitchPlays() {
        paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.PHONE, Theme.LIGHT))
        val ctx = Shots.files(paparazzi.context)
        FakeGame.install(ctx)
        val b = BattleShotKit.bound(ctx)
        val logBox = bar(b.panel!!)
        b.panel!!.observeStudyEvent(GameRepo.run(ctx) { it.requestMain("t2") }.events.single())
        val events = GameRepo.run(ctx) { it.onAnswer(Answer(77L, 3, System.currentTimeMillis())) }
        assertEquals("t2", st.main!!.uid)
        assertNull(st.pendingMain)
        logBox.syncQueued() // the game changed with the answer: the main is another one now, so the line is not dropped by the state
        assertNotNull(logBox.queuedShown())
        // the events play: the answer's own, then the switch coming in ends the line
        for (e in events) { b.panel!!.observeStudyEvent(e); if (e is GameEvent.MainChanged) break }
        assertNull(logBox.queuedShown())
    }

    @Test fun barQueued() = paparazzi.shootWith("r14d-bar-queued", sizes, offsetMillis = 1000) { ctx ->
        val b = BattleShotKit.bound(ctx)
        val r = GameRepo.run(ctx) { it.requestMain("t2") }
        b.panel!!.observeStudyEvent(r.events.single())
        b.root
    }

    // ---------------------------------------------------------------- Team

    @Test fun teamRowMarksTheQueuedPokemon() {
        paparazzi.shootPage("r14d-team-queued", listOf(Variants.PHONE)) { ctx ->
            GameRepo.run(ctx) { it.requestMain("t2") }
            val p = TeamPage(ctx, NoHost)
            p.preset(TeamSection.TEAM)
            pageScreen(ctx, p)
        }
    }

    @Test fun menuMakeMainSaysAfterThisCardInAFight() {
        paparazzi.shootPage("r14d-menu-make", listOf(Variants.PHONE)) { ctx ->
            MenuKit.scene(ctx, MenuKit.capture { MonActions(TeamPage(ctx, NoHost)).menu(st.mon("t3")!!, View(ctx)) })
        }
    }

    @Test fun menuOfTheQueuedPokemonOffersToCancel() {
        paparazzi.shootPage("r14d-menu-cancel", listOf(Variants.PHONE)) { ctx ->
            GameRepo.run(ctx) { it.requestMain("t3") }
            MenuKit.scene(ctx, MenuKit.capture { MonActions(TeamPage(ctx, NoHost)).menu(st.mon("t3")!!, View(ctx)) })
        }
    }
}
