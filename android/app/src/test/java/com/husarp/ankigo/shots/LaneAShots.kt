package com.husarp.ankigo.shots

import android.content.Context
import android.view.View
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.husarp.ankigo.ui.AppPrefs
import com.husarp.ankigo.ui.ArenaView
import com.husarp.ankigo.ui.BattlePanelView
import com.husarp.ankigo.ui.FloatStyle
import com.husarp.ankigo.ui.LogKind
import org.junit.After
import org.junit.Rule
import org.junit.Test

/**
 * Lane A renders: the full battle screen with the stat card under its own Pokémon (la-full-own) and the classic layout
 * (la-full-classic) at PHONE / TALL / SMALL / SPLIT / TABLET; small windows (la-small-own|classic); damage numbers caught
 * mid-rise (la-dmg); the phone at 420 dpi (la-phone420-own|classic-light). PNGs go to app/build/shots.
 */
class LaneAShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    @After fun cleanUp() = BattleShotKit.reset()

    private fun prefs(ctx: Context, own: Boolean) {
        AppPrefs.setCardsUnderOwn(ctx, own)
        AppPrefs.setCompactCards(ctx, false)
    }

    /** A bare panel in small-window mode, as FloatBattleView and picture-in-picture set it up. */
    private fun bare(ctx: Context): BattlePanelView {
        val p = BattlePanelView(ctx)
        p.setCompact(true)
        p.renderAll()
        p.setLog(LogKind.GOOD, "Good", "Sparkmouse used {{Thunderbolt}}! It's super effective.", false)
        return p
    }

    /**
     * Fires the two damage numbers on the arena's first real layout: floatNumber places a number by the arena's size, so it
     * must not run on the unattached view. (A posted runnable never runs in the renderer, so it is called straight from
     * the layout listener, once.)
     */
    private fun hitAfterLayout(arena: ArenaView) {
        var done = false
        arena.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            if (!done && v.width > 0 && v.height > 0) { done = true; hit(arena) }
        }
    }

    private fun hit(arena: ArenaView) {
        arena.floatNumber(onPlayer = false, "\u221231", FloatStyle.DAMAGE)
        arena.floatNumber(onPlayer = true, "\u221212", FloatStyle.DAMAGE)
        // Added during a layout pass: lay the arena out once more so the two new children get their size.
        arena.measure(View.MeasureSpec.makeMeasureSpec(arena.width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(arena.height, View.MeasureSpec.EXACTLY))
        arena.layout(arena.left, arena.top, arena.right, arena.bottom)
    }

    // ---------------------------------------------------------------- full screens

    private val fullSizes = listOf(Size.PHONE, Size.TALL, Size.SMALL, Size.SPLIT, Size.TABLET)

    @Test fun fullOwn() = paparazzi.shootWith("la-full-own", fullSizes) { ctx -> prefs(ctx, true); BattleShotKit.bound(ctx).root }
    @Test fun fullClassic() = paparazzi.shootWith("la-full-classic", fullSizes) { ctx -> prefs(ctx, false); BattleShotKit.bound(ctx).root }

    // ---------------------------------------------------------------- small windows

    private val smallSizes = listOf(Size.PIP_169, Size.PIP_169M, Size.PIP_11, Size.FLOATB)

    @Test fun smallOwn() = paparazzi.shootWith("la-small-own", smallSizes) { ctx -> prefs(ctx, true); bare(ctx) }
    @Test fun smallClassic() = paparazzi.shootWith("la-small-classic", smallSizes) { ctx -> prefs(ctx, false); bare(ctx) }

    // ---------------------------------------------------------------- damage numbers mid-rise (300 ms into the 1 s animation)

    @Test fun damageFull() = paparazzi.shootWith("la-dmg", listOf(Size.PHONE, Size.SPLIT, Size.SPLIT_TINY), offsetMillis = 300) { ctx ->
        prefs(ctx, true)
        val b = BattleShotKit.bound(ctx)
        hitAfterLayout(b.arena!!)
        b.root
    }

    @Test fun damageSmall() = paparazzi.shootWith("la-dmg", smallSizes.filter { it != Size.PIP_11 } + Size.PIP_11, offsetMillis = 300) { ctx ->
        prefs(ctx, true)
        val p = bare(ctx)
        hitAfterLayout(p.arena)
        p
    }

    // ---------------------------------------------------------------- phone at 420 dpi (Adam's density)

    @Test fun phone420() {
        for ((own, name) in listOf(true to "own", false to "classic")) {
            val base = Shots.device(Size.PHONE, Theme.LIGHT)
            paparazzi.unsafeUpdateConfig(deviceConfig = base.copy(density = Density.create(420), xdpi = 420, ydpi = 420, screenWidth = 1082, screenHeight = 2281))
            FakeGame.scenario = FakeGame.Scenario.MID_GAME
            FakeGame.withSprites = true
            val ctx = Shots.files(paparazzi.context)
            FakeGame.install(ctx)
            prefs(ctx, own)
            val frame = android.widget.FrameLayout(ctx).apply { addView(BattleShotKit.bound(ctx).root, android.widget.FrameLayout.LayoutParams(-1, -1)) }
            paparazzi.snapshot(frame, "la-phone420-$name-light", 1000)
        }
    }
}
