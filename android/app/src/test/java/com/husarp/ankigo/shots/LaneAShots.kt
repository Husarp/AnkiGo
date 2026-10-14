package com.husarp.ankigo.shots

import android.content.Context
import android.view.View
import android.view.View.MeasureSpec
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

    /** Lays [v] out at the render size so [ArenaView.floatNumber] sees a real arena (it places the number by the arena's size). */
    private fun settle(ctx: Context, v: View): View {
        val dm = ctx.resources.displayMetrics
        repeat(2) {
            v.measure(MeasureSpec.makeMeasureSpec(dm.widthPixels, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(dm.heightPixels, MeasureSpec.EXACTLY))
            v.layout(0, 0, dm.widthPixels, dm.heightPixels)
        }
        return v
    }

    private fun hit(arena: ArenaView) {
        arena.floatNumber(onPlayer = false, "−31", FloatStyle.DAMAGE)
        arena.floatNumber(onPlayer = true, "−12", FloatStyle.DAMAGE)
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
        settle(ctx, b.root)
        hit(b.arena!!)
        b.root
    }

    @Test fun damageSmall() = paparazzi.shootWith("la-dmg", smallSizes.filter { it != Size.PIP_11 } + Size.PIP_11, offsetMillis = 300) { ctx ->
        prefs(ctx, true)
        val p = settle(ctx, bare(ctx)) as BattlePanelView
        hit(p.arena)
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
