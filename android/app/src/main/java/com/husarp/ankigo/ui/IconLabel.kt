package com.husarp.ankigo.ui

import android.content.Context
import android.graphics.Rect
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.roundToInt

/**
 * A small icon before a one-line text (R10-W2-D: the streak card's "Best", the details tags). The icon is centred on the CAPITAL
 * letters' height of the text, not on its line box (whose centre sits lower than what the eye reads as the middle).
 */
class IconLabel(ctx: Context, val icon: ImageView, val label: TextView, iconDp: Int, gapDp: Int) : LinearLayout(ctx) {
    private val capBounds = Rect()

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(icon, LayoutParams(Ui.dp(ctx, iconDp), Ui.dp(ctx, iconDp)).also { it.marginEnd = Ui.dp(ctx, gapDp) })
        addView(label, LayoutParams(-2, -2))
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        label.paint.getTextBounds("H", 0, 1, capBounds)
        val glyphMid = label.top + label.baseline + (capBounds.top + capBounds.bottom) / 2f
        val iconMid = icon.top + icon.height / 2f
        icon.translationY = (glyphMid - iconMid).roundToInt().toFloat()
    }

    /** The y of the icon's centre and of the capitals' centre, both in this view's coordinates (for the render checks). */
    fun centres(): Pair<Float, Float> {
        label.paint.getTextBounds("H", 0, 1, capBounds)
        return (icon.top + icon.translationY + icon.height / 2f) to (label.top + label.baseline + (capBounds.top + capBounds.bottom) / 2f)
    }
}
