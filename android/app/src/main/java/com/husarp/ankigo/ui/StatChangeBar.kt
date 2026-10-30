package com.husarp.ankigo.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import androidx.core.graphics.ColorUtils
import com.husarp.ankigo.R
import com.husarp.ankigo.game.BattleEngine

/** The normal stat length stays visible; a fight's loss fades and its gain extends smoothly. */
internal class StatChangeBar(ctx: Context, private val normal: Float, private val stage: Int, private val color: Int) : View(ctx) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shape = Path()
    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat(); val radius = h / 2f
        paint.color = Ui.c(context, R.color.ag_s0)
        canvas.drawRoundRect(0f, 0f, w, h, radius, radius, paint)
        val current = (normal * BattleEngine.stageMult(stage)).toFloat().coerceIn(0f, 1f)
        val extent = maxOf(normal, current) * w
        shape.reset(); shape.addRoundRect(RectF(0f, 0f, extent, h), radius, radius, Path.Direction.CW)
        canvas.save(); canvas.clipPath(shape)
        paint.color = if (stage < 0) ColorUtils.setAlphaComponent(color, 65) else color
        canvas.drawRect(0f, 0f, extent, h, paint)
        paint.color = if (stage > 0) ColorUtils.blendARGB(color, android.graphics.Color.WHITE, .45f) else color
        if (stage > 0) {
            paint.shader = android.graphics.LinearGradient(normal * w - h, 0f, extent, 0f,
                intArrayOf(color, paint.color), null, android.graphics.Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, extent, h, paint); paint.shader = null
        } else canvas.drawRoundRect(0f, 0f, current * w, h, radius, radius, paint)
        canvas.restore()
    }
}
