package com.husarp.ankigo.shots

import android.view.View
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.game.GameEvent
import com.husarp.ankigo.ui.ActionBarView
import com.husarp.ankigo.ui.BattlePanelView
import com.husarp.ankigo.ui.MonActions
import com.husarp.ankigo.ui.TeamPage
import com.husarp.ankigo.ui.TeamSection
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * R14-D (PLAN 206 + 191), changed in R18-B (227): a switch made while a card shows waits for the next answer. The action bar keeps its
 * segments (a toast says "Switching to X on your next move."); the queued Pokémon's Team row says "On your next move"; the Team menu
 * row says "Make main" with "On your next move · the wild Pokémon attacks once" in a fight.
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

    @Test fun theQueueEventLeavesTheBarWithItsSegments() {
        paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.PHONE, Theme.LIGHT))
        val ctx = Shots.files(paparazzi.context)
        FakeGame.install(ctx)
        val b = BattleShotKit.bound(ctx)
        val logBox = bar(b.panel!!)
        val r = GameRepo.run(ctx) { it.requestMain("t2") }
        val queued = r.events.single() as GameEvent.SwitchQueued
        b.panel!!.observeStudyEvent(queued)
        assertFalse("R18-B (227): no whole-bar line while a switch waits", logBox.lineShown())
        assertEquals("t1", st.main!!.uid) // nothing came in yet
        assertEquals("Switching to ${queued.name} on your next move.", r.message)
        // cancelling changes nothing on the bar either
        b.panel!!.observeStudyEvent(GameRepo.run(ctx) { it.requestMain("t1") }.events.single())
        assertFalse(logBox.lineShown())
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
