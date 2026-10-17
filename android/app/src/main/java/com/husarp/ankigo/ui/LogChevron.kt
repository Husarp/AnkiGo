package com.husarp.ankigo.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import com.husarp.ankigo.R

/** R6-12: the small "you can flip this way" chevron at an end of the battle text bar ([BattlePanelView]); drawn, not a glyph. */
internal class LogChevron(ctx: Context, private val right: Boolean) : View(ctx) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = Ui.dp(ctx, 2f)
        color = Ui.c(ctx, R.color.ag_sub0)
    }
    private val path = Path()

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        visibility = GONE
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val cx = w / 2f; val cy = h / 2f
        val a = Ui.dp(context, 4.5f)       // half the chevron's height
        val b = Ui.dp(context, 2.5f)       // half its depth
        path.reset()
        if (right) { path.moveTo(cx - b, cy - a); path.lineTo(cx + b, cy); path.lineTo(cx - b, cy + a) }
        else { path.moveTo(cx + b, cy - a); path.lineTo(cx - b, cy); path.lineTo(cx + b, cy + a) }
        canvas.drawPath(path, paint)
    }
}
