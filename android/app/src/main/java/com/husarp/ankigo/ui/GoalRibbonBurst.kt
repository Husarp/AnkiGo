package com.husarp.ankigo.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.husarp.ankigo.R

/** The sample's starburst, confetti, mint ribbon and rising orbs, decoded once per goal overlay. */
internal class GoalRibbonBurst(ctx: Context) {
    private val images = intArrayOf(R.drawable.bar_burst_b_2, R.drawable.bar_burst_b_1,
        R.drawable.bar_burst_b_3, R.drawable.bar_burst_b_4).map { BitmapFactory.decodeResource(ctx.resources, it) }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val rect = RectF()
    private val density = ctx.resources.displayMetrics.density
    var start = 0L
    fun draw(canvas: Canvas, left: Float, top: Float, width: Float, elapsed: Long) {
        for (i in 0 until 12) {
            val delay = when (i) { 0 -> 0; 1 -> 200; 2 -> 450; else -> 400 + (i * 73 % 700) }
            val duration = when (i) { 0 -> 650; 1 -> 1000; 2 -> 1300; else -> 1400 }
            val t = (elapsed - delay).toFloat() / duration
            if (t <= 0f || t >= 1f) continue
            val alpha = if (t < .25f) t / .25f else (1f - t) / .75f
            paint.alpha = (255 * alpha).toInt()
            val image = images[if (i < 3) i else if (i % 2 == 0) 1 else 3]
            val size = minOf(width, (when (i) { 0 -> 170f; 1 -> 250f; 2 -> 220f; else -> 54f }) * density)
            val scale = if (i < 2) .3f + t else .5f + .55f * t
            val x = left + width * when (i) { 0 -> .95f; 1, 2 -> .5f; else -> .1f + .8f * (i * 37 % 100) / 100f }
            val y = top - (if (i >= 2) t * (26 + i % 4 * 8) * density else 0f)
            val w = size * scale
            val h = w * image.height / image.width
            rect.set(x - w / 2, y - h / 2, x + w / 2, y + h / 2)
            canvas.drawBitmap(image, null, rect, paint)
        }
    }
}
