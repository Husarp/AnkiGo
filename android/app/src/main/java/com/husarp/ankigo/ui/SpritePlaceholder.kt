package com.husarp.ankigo.ui

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable

/** Neutral, code-drawn ball; no Pokémon artwork is packaged in the app. */
class SpritePlaceholder(private val color: Int) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun draw(canvas: Canvas) {
        val r = minOf(bounds.width(), bounds.height()) * 0.35f
        val x = bounds.exactCenterX(); val y = bounds.exactCenterY()
        paint.color = color; paint.style = Paint.Style.FILL
        canvas.drawCircle(x, y, r, paint)
        paint.color = 0xFFEEEEEE.toInt()
        canvas.drawArc(x-r, y-r, x+r, y+r, 0f, 180f, true, paint)
        paint.color = 0xFF555555.toInt(); paint.style = Paint.Style.STROKE; paint.strokeWidth = r * 0.09f
        canvas.drawCircle(x, y, r, paint); canvas.drawLine(x-r, y, x+r, y, paint)
        paint.style = Paint.Style.FILL; canvas.drawCircle(x, y, r * 0.25f, paint)
        paint.color = 0xFFEEEEEE.toInt(); canvas.drawCircle(x, y, r * 0.16f, paint)
    }
    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(filter: ColorFilter?) { paint.colorFilter = filter }
    @Deprecated("Deprecated in Java") override fun getOpacity() = PixelFormat.TRANSLUCENT
}
