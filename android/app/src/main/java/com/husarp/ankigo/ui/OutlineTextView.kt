package com.husarp.ankigo.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.widget.TextView

/** A TextView that draws its text twice: a dark stroke behind, then the normal fill, so floating numbers read on any sprite. */
class OutlineTextView(ctx: Context, private val strokePx: Float, private val strokeColor: Int) : TextView(ctx) {
    override fun onDraw(canvas: Canvas) {
        val fill = currentTextColor
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = strokePx * 2
        paint.strokeJoin = Paint.Join.ROUND
        setTextColor(strokeColor)
        super.onDraw(canvas)
        paint.style = Paint.Style.FILL
        paint.strokeWidth = 0f
        setTextColor(fill)
        super.onDraw(canvas)
    }
}
