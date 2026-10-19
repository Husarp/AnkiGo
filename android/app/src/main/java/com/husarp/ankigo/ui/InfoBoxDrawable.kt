package com.husarp.ankigo.ui

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable

/**
 * The background of an info box in one of the [InfoBoxLook]s: the layers of [InfoBoxSkin.layers] drawn bottom first on a rounded
 * rectangle. The real boxes ([ArenaView]) and the previews ([BoxLookPreview]) both use this class, so they look the same. Nothing is
 * allocated while drawing (paints, shaders and rectangles are made when the bounds change).
 *
 * [glass]: what the glass look shows through (the blurred picture; null draws nothing for that layer). The look that has no box (the
 * HUD) has no layers, so it draws nothing.
 */
internal class InfoBoxDrawable(
    private val density: Float,
    private val layers: List<InfoBoxSkin.Layer>,
    private val radiusDp: Float,
    private val glass: BoxBackdrop? = null,
) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val ring = RectF()
    private val shaders = arrayOfNulls<Shader>(layers.size)
    private val r = radiusDp * density

    override fun onBoundsChange(bounds: Rect) {
        for ((i, l) in layers.withIndex()) {
            if (l.kind == InfoBoxSkin.Kind.GRADIENT) {
                shaders[i] = LinearGradient(bounds.left.toFloat(), bounds.top.toFloat(), bounds.left.toFloat(), bounds.bottom.toFloat(), l.color, l.color2, Shader.TileMode.CLAMP)
            }
        }
    }

    override fun draw(canvas: Canvas) {
        if (bounds.isEmpty) return
        rect.set(bounds)
        for ((i, l) in layers.withIndex()) {
            when (l.kind) {
                InfoBoxSkin.Kind.BLUR -> glass?.drawBehind(canvas, rect, r)
                InfoBoxSkin.Kind.FILL -> {
                    paint.shader = null; paint.style = Paint.Style.FILL; paint.color = l.color
                    canvas.drawRoundRect(rect, r, r, paint)
                }
                InfoBoxSkin.Kind.GRADIENT -> {
                    paint.shader = shaders[i]; paint.style = Paint.Style.FILL; paint.color = 0xFF000000.toInt()
                    canvas.drawRoundRect(rect, r, r, paint)
                    paint.shader = null
                }
                InfoBoxSkin.Kind.EDGE -> {
                    // a ring just inside the box: the stroke is centred half its width in from the edge, the corner radius shrinks with it
                    val w = l.widthDp * density
                    val inset = l.insetDp * density + w / 2f
                    ring.set(rect.left + inset, rect.top + inset, rect.right - inset, rect.bottom - inset)
                    val rr = (r - inset).coerceAtLeast(0f)
                    paint.shader = null; paint.style = Paint.Style.STROKE; paint.strokeWidth = w; paint.color = l.color
                    canvas.drawRoundRect(ring, rr, rr, paint)
                }
                InfoBoxSkin.Kind.STRIPE -> {
                    // the box's left part: the same rounded rectangle cut to the stripe's width, so it follows the corners
                    val save = canvas.save()
                    canvas.clipRect(rect.left, rect.top, rect.left + l.widthDp * density, rect.bottom)
                    paint.shader = null; paint.style = Paint.Style.FILL; paint.color = l.color
                    canvas.drawRoundRect(rect, r, r, paint)
                    canvas.restoreToCount(save)
                }
            }
        }
    }

    /** The glass look casts a soft drop shadow (the box's elevation); the others stay as flat as the translucent card always was. */
    override fun getOutline(outline: Outline) {
        outline.setRoundRect(bounds, r)
        outline.alpha = if (glass != null) 1f else 0f
    }

    override fun setAlpha(alpha: Int) = Unit
    override fun setColorFilter(colorFilter: ColorFilter?) = Unit

    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

/**
 * The HUD's ring round an HP / XP bar ([InfoBoxSkin.barRing]: white, 1 dp, a soft dark shadow under it), drawn as the bar's foreground
 * OUTSIDE its bounds (the rows that hold the bars stop clipping their children for it). The pixel look has its own outline: no ring.
 */
internal class BarRingDrawable(private val bar: BarView, private val ring: InfoBoxSkin.BarRing, private val density: Float) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val rect = RectF()

    override fun draw(canvas: Canvas) {
        if (bar.look != HpBarLook.TODAY || bounds.isEmpty) return
        val w = ring.widthDp * density
        val s = ring.shadow
        // the shadow: a wider, fainter stroke a little lower, under the ring
        val soft = s.radiusDp * density
        rect.set(bounds.left - w / 2f, bounds.top - w / 2f + s.dyDp * density, bounds.right + w / 2f, bounds.bottom + w / 2f + s.dyDp * density)
        paint.strokeWidth = w + soft
        paint.color = InfoBoxSkin.withAlpha(s.color, (s.color ushr 24) / 3)
        canvas.drawRoundRect(rect, rect.height() / 2f, rect.height() / 2f, paint)
        rect.set(bounds.left - w / 2f, bounds.top - w / 2f, bounds.right + w / 2f, bounds.bottom + w / 2f)
        paint.strokeWidth = w
        paint.color = ring.color
        canvas.drawRoundRect(rect, rect.height() / 2f, rect.height() / 2f, paint)
    }

    override fun setAlpha(alpha: Int) = Unit
    override fun setColorFilter(colorFilter: ColorFilter?) = Unit

    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
