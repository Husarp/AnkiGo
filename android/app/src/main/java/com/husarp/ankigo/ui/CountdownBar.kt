package com.husarp.ankigo.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View

/** The 3 dp auto-advance bar across the top of the card: [fraction] of it left, in [tone]; a faint track behind. Nothing drawn at a negative fraction. */
class CountdownBar(ctx: Context) : View(ctx) {
    private val paint = Paint()
    var tone = 0xFF888888.toInt(); set(v) { field = v; invalidate() }
    var fraction = -1f; set(v) { field = v; invalidate() }
    override fun onDraw(c: Canvas) {
        if (fraction < 0f) return
        paint.color = (tone and 0x00ffffff) or 0x33000000; c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.color = tone; c.drawRect(0f, 0f, width * fraction, height.toFloat(), paint)
    }
}
