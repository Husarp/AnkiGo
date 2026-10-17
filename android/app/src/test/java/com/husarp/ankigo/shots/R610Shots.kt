package com.husarp.ankigo.shots

import android.content.Context
import android.view.View
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.ui.AppPrefs
import com.husarp.ankigo.ui.BattlePanelView
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * R6-10 (items 27, 28): the status bubble of OUR Pokémon draws above both stat cards (the study strip lifts the cards by 2 dp);
 * the swipe turn is a saved facing. PNGs: r610-bubble-study|float-<size>-<theme>, r610-front-<size>-<theme> (turned, from a saved facing).
 */
class R610Shots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    @After fun cleanUp() = BattleShotKit.reset()

    private fun field(o: Any, name: String): Any = o.javaClass.getDeclaredField(name).let { it.isAccessible = true; it.get(o)!! }

    private fun panel(ctx: Context, strip: Boolean): BattlePanelView {
        val b = GameRepo.state!!.battle!!
        b.enemy.status = "par"; b.player.status = "psn"
        val p = BattlePanelView(ctx)
        if (strip) p.arena.setStudyStrip(true) else p.setCompact(true)
        p.renderAll()
        return p
    }

    /** Taps the player's badge and shoots; the bubble must outrank every card (higher z). */
    private fun bubble(name: String, strip: Boolean, sizes: List<Size>) {
        for (size in sizes) for (theme in Theme.entries) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(size, theme))
            val ctx = Shots.files(paparazzi.context)
            FakeGame.install(ctx)
            val p = panel(ctx, strip)
            val frame = android.widget.FrameLayout(ctx).apply { addView(p, android.widget.FrameLayout.LayoutParams(-1, -1)) }
            paparazzi.snapshot(frame, "$name-pre", 0)
            (field(p.arena, "pStatus") as View).performClick()
            val bubble = field(p.arena, "bubble") as View
            assertEquals(View.VISIBLE, bubble.visibility)
            for (card in listOf("enemyCard", "playerCard", "enemyBlock")) {
                val c = field(p.arena, card) as View
                assertTrue("bubble above $card", bubble.elevation > c.elevation)
            }
            // the cards that are direct children of the arena (the player's card) are the ones the bubble could hide behind
            assertTrue(bubble.elevation > (field(p.arena, "playerCard") as View).elevation)
            paparazzi.snapshot(frame, "$name-${size.name.lowercase()}-${theme.name.lowercase()}", 100)
        }
    }

    @Test fun bubbleStudyStrip() = bubble("r610-bubble-study", strip = true, sizes = listOf(Size.SPLIT, Size.PHONE))
    @Test fun bubbleSmallWindow() = bubble("r610-bubble-float", strip = false, sizes = listOf(Size.FLOATB, Size.PIP_169M))

    @Test fun turnedFromTheSavedFacing() = paparazzi.shootWith("r610-front", listOf(Size.PHONE, Size.SPLIT)) { ctx ->
        AppPrefs.setPlayerFront(ctx, true)   // an install that had "Show my Pokémon from the front" on starts like this
        BattleShotKit.bound(ctx).root
    }

    @Test fun theFacingIsTheSavedPrefAndEveryViewReadsIt() {
        val ctx = Shots.files(paparazzi.context)
        FakeGame.install(ctx)
        AppPrefs.setPlayerFront(ctx, false)
        val a = BattlePanelView(ctx); a.renderAll()
        val b = BattlePanelView(ctx); b.renderAll()
        val sa = a.arena.turnSource!!; val sb = b.arena.turnSource!!
        assertFalse(sa.facingFront())
        assertTrue("fixture has a front picture", sa.hasFront())
        sa.turnTo(true, true)
        assertTrue("saved", AppPrefs.playerFront(ctx))
        assertTrue("another view agrees", sb.facingFront())
        sb.turnTo(false, true)
        assertFalse(sa.facingFront())
    }
}
