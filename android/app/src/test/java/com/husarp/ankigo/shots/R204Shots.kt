package com.husarp.ankigo.shots

import android.content.Context
import android.view.View
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.ui.ArenaView
import com.husarp.ankigo.ui.BattlePanelView
import com.husarp.ankigo.ui.FloatStyle
import com.husarp.ankigo.ui.LogKind
import org.junit.After
import org.junit.Rule
import org.junit.Test

/** R2-04: the four float styles (damage, big, heal, miss) in their hold phase (700 ms in), outlined. */
class R204Shots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    @After fun cleanUp() = BattleShotKit.reset()

    private fun hitAfterLayout(arena: ArenaView) {
        var done = false
        arena.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            if (!done && v.width > 0 && v.height > 0) {
                done = true
                arena.floatNumber(false, "−31", FloatStyle.DAMAGE)
                arena.floatNumber(false, "Miss", FloatStyle.MISS)
                arena.floatNumber(true, "−84", FloatStyle.BIG)
                arena.floatNumber(true, "+12", FloatStyle.HEAL)
                arena.measure(View.MeasureSpec.makeMeasureSpec(arena.width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(arena.height, View.MeasureSpec.EXACTLY))
                arena.layout(arena.left, arena.top, arena.right, arena.bottom)
            }
        }
    }

    @Test fun full() = paparazzi.shootWith("r204-float", listOf(Size.PHONE, Size.SPLIT), offsetMillis = 700) { ctx: Context ->
        val b = BattleShotKit.bound(ctx)
        hitAfterLayout(b.arena!!)
        b.root
    }

    @Test fun small() = paparazzi.shootWith("r204-float", listOf(Size.PIP_169, Size.FLOATB), offsetMillis = 700) { ctx: Context ->
        val p = BattlePanelView(ctx)
        p.setCompact(true); p.renderAll()
        p.setLog(LogKind.GOOD, "Good", "Sparkmouse used {{Thunderbolt}}!", false)
        hitAfterLayout(p.arena)
        p
    }
}
