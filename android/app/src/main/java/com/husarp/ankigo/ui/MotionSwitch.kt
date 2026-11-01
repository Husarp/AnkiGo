package com.husarp.ankigo.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.widget.Switch
import com.husarp.ankigo.R

/** Same accessible framework switch and immediate checked state, with the chosen 150 ms visual travel. */
class MotionSwitch(ctx: Context) : Switch(ctx) {
    private var drawn = if (isChecked) 1f else 0f
    private var last = isChecked
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun setChecked(checked: Boolean) {
        super.setChecked(checked)
        if (!isAttachedToWindow) { last = checked; drawn = if (checked) 1f else 0f }
    }
    override fun onDraw(canvas: Canvas) {
        if (last != isChecked) {
            last = isChecked
            val from = drawn
            val to = if (isChecked) 1f else 0f
            UiMotion.run(this, 150, { f -> drawn = from + (to-from)*f; invalidate() })
        }
        val d = resources.displayMetrics.density
        val x = (width - 52*d) / 2f
        val y = (height - 32*d) / 2f
        val off = Ui.c(context, R.color.ag_sw_track)
        val on = Ui.c(context, R.color.ag_mauve)
        ink.color = android.animation.ArgbEvaluator().evaluate(drawn, off, on) as Int
        ink.alpha = if (isEnabled) 255 else 100
        ink.style = Paint.Style.FILL
        canvas.drawRoundRect(x, y, x+52*d, y+32*d, 16*d, 16*d, ink)
        ink.color = Ui.c(context, R.color.ag_sw_line)
        ink.alpha = ((1-drawn) * if (isEnabled) 255 else 100).toInt()
        ink.style = Paint.Style.STROKE; ink.strokeWidth = 2*d
        canvas.drawRoundRect(x+d, y+d, x+51*d, y+31*d, 15*d, 15*d, ink)
        ink.style = Paint.Style.FILL
        ink.color = android.animation.ArgbEvaluator().evaluate(drawn, Ui.c(context, R.color.ag_sw_line), Ui.c(context, R.color.ag_sw_knob_on)) as Int
        ink.alpha = if (isEnabled) 255 else 100
        canvas.drawCircle(x+(16+20*drawn)*d, y+16*d, (8+4*drawn)*d, ink)
    }
}
