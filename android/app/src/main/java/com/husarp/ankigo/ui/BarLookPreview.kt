package com.husarp.ankigo.ui

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import com.husarp.ankigo.R

/**
 * A small tile showing the info boxes' bars in one [HpBarLook] (Round 10 W8; the look chooser of ticket F puts one per choice): three
 * HP bars (86 / 42 / 14 %, so green, yellow and red) and an XP bar (64 %) on an `ag_card` tile, in the app colours of the current
 * theme. The bars are the real [BarView]s at the sizes of the "number above" info box (7 dp HP, 3 dp XP; the pixel look 2 outline
 * units taller, like in the box), drawn without animation. It takes no touches of its own.
 */
class BarLookPreview(ctx: Context, look: HpBarLook = HpBarLook.TODAY) : LinearLayout(ctx) {
    private val bars = ArrayList<BarView>()

    /** The look shown now. */
    var look: HpBarLook = look
        private set

    init {
        orientation = VERTICAL
        background = Ui.rounded(Ui.c(ctx, R.color.ag_card), 12f, ctx)
        val pad = Ui.dp(ctx, 10)
        setPadding(pad, pad, pad, pad)
        for ((i, f) in HP.withIndex()) add(f, Ui.hpColor(ctx, f), xp = false, first = i == 0)
        add(XP, 0, xp = true, first = false)
        set(look)
    }

    private fun add(f: Float, color: Int, xp: Boolean, first: Boolean) {
        val bar = BarView(context)
        if (xp) bar.setMeaning(BarMeaning.XP, f, false) else bar.set(f, color, false)
        bars += bar
        addView(bar, LayoutParams(LayoutParams.MATCH_PARENT, 0).also { it.topMargin = if (first) 0 else Ui.dp(context, if (xp) 9 else 7) })
    }

    /** Shows the bars in [look]. */
    fun set(look: HpBarLook) {
        this.look = look
        val d = resources.displayMetrics.density
        for ((i, bar) in bars.withIndex()) {
            bar.look = look
            val inner = Ui.dp(context, if (i < HP.size) 7 else 3)
            val lp = bar.layoutParams as LayoutParams
            lp.height = if (look == HpBarLook.PIXEL) PixelBar.outerPx(inner, d) else inner
            bar.layoutParams = lp
        }
        contentDescription = "${look.label} bars"
    }

    /** The bars (tests): three HP bars, then the XP bar. */
    internal fun bars(): List<BarView> = bars

    private companion object {
        val HP = floatArrayOf(0.86f, 0.42f, 0.14f)
        const val XP = 0.64f
    }
}
