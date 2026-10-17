package com.husarp.ankigo.shots

import android.content.Context
import android.view.View
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.ui.ArenaView
import com.husarp.ankigo.ui.BattlePanelView
import org.junit.After
import org.junit.Rule
import org.junit.Test

/** R5-04: still frames of the shiny star burst (pick 75A) on the wild and on our sprite, at a full and at a floating-window size. */
class R504Shots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    @After fun cleanUp() = BattleShotKit.reset()

    private fun burstAfterLayout(arena: ArenaView, player: Boolean, atMs: Float) {
        var done = false
        arena.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            if (!done && v.width > 0 && v.height > 0) {
                done = true
                arena.shinyBurstStill(player, atMs)
                arena.measure(View.MeasureSpec.makeMeasureSpec(arena.width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(arena.height, View.MeasureSpec.EXACTLY))
                arena.layout(arena.left, arena.top, arena.right, arena.bottom)
            }
        }
    }

    private fun full(ctx: Context, player: Boolean, atMs: Float): View {
        val b = BattleShotKit.bound(ctx)
        burstAfterLayout(b.arena!!, player, atMs)
        return b.root
    }

    private fun small(ctx: Context, player: Boolean, atMs: Float): View {
        val p = BattlePanelView(ctx)
        p.setCompact(true); p.renderAll()
        burstAfterLayout(p.arena, player, atMs)
        return p
    }

    @Test fun wildFull() = paparazzi.shootWith("r504-wild", listOf(Size.PHONE, Size.SPLIT)) { ctx -> full(ctx, false, 300f) }
    @Test fun mineFull() = paparazzi.shootWith("r504-mine", listOf(Size.PHONE, Size.SPLIT)) { ctx -> full(ctx, true, 300f) }
    @Test fun flashFull() = paparazzi.shootWith("r504-flash", listOf(Size.PHONE)) { ctx -> full(ctx, false, 100f) }
    @Test fun wildSmall() = paparazzi.shootWith("r504-wild-small", listOf(Size.PIP_169, Size.FLOATB)) { ctx -> small(ctx, false, 300f) }
    @Test fun mineSmall() = paparazzi.shootWith("r504-mine-small", listOf(Size.PIP_169, Size.FLOATB)) { ctx -> small(ctx, true, 300f) }
}
