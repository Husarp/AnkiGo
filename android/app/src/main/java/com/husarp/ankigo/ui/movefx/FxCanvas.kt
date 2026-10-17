package com.husarp.ankigo.ui.movefx

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.roundToInt

/**
 * The drawing helper a [FxDraw] proc or a [FxParticleDraw] gets: a thin, reusable wrapper over android.graphics.Canvas that reads
 * like the 2D canvas context of the mockup (`globalAlpha`, `globalCompositeOperation = 'lighter'`, save / restore, translate /
 * rotate / scale, drawImage, stroke, fill). Everything is in **pixels** of the effect layer: multiply stage units with [unit].
 * [alpha] and [additive] apply to the next draw calls and are reset at the start of every proc / particle. Reuse everything,
 * allocate nothing per call (except [gradientPoly] when its gradient changes); the renderer keeps one instance per animation.
 *
 * Colours are ARGB ints; an alpha byte of 0 (a literal like 0x9A7448) counts as opaque, so use the [a] parameter for transparency.
 * Points are flat `FloatArray`s of x, y pairs. For anything missing use [raw] and [paint] directly.
 */
class FxCanvas internal constructor(private val art: FxArt, @JvmField val unit: Float) {
    /** The canvas of the current frame. */
    lateinit var raw: Canvas
        internal set

    /** The shared paint (reset by every helper). */
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    /** Alpha 0..1 for the next draws (the JS `globalAlpha`). */
    var alpha = 1f
    /** Draw additive (the JS `globalCompositeOperation = 'lighter'`) for the next draws. */
    var additive = false

    private val rect = RectF()
    private val path = Path()
    private val add = PorterDuffXfermode(PorterDuff.Mode.ADD)

    internal fun begin() { alpha = 1f; additive = false }

    fun save() { raw.save() }
    fun restore() { raw.restore() }
    fun translate(x: Float, y: Float) { raw.translate(x, y) }
    fun rotate(rad: Float) { raw.rotate(Math.toDegrees(rad.toDouble()).toFloat()) }
    fun scale(sx: Float, sy: Float = sx) { raw.scale(sx, sy) }

    private fun reset(smooth: Boolean = true) {
        paint.reset()
        paint.isAntiAlias = true
        paint.isFilterBitmap = smooth
        paint.isDither = false
        paint.xfermode = if (additive) add else null
    }

    private fun a255(extra: Float = 1f): Int = (FxMath.clamp01(alpha * extra) * 255f + .5f).toInt()

    /** Draws texture [key] (see [FxArt]) tinted with [tint] (0 = none), centred on ([cx], [cy]), [w] x [h] px, in the current transform. */
    fun tex(key: String, tint: Int, cx: Float, cy: Float, w: Float, h: Float) {
        val bmp = art.bitmap(key) ?: return
        bitmap(bmp, tint, cx - w / 2f, cy - h / 2f, w, h)
    }

    /** Draws [bmp] with its left top at ([left], [top]), [w] x [h] px, smoothed, optionally tinted. */
    fun bitmap(bmp: Bitmap, tint: Int, left: Float, top: Float, w: Float, h: Float) {
        reset()
        paint.alpha = a255()
        if (tint != 0) paint.colorFilter = art.tint(tint)
        rect.set(left, top, left + w, top + h)
        raw.drawBitmap(bmp, null, rect, paint)
    }

    /**
     * Draws pixel art [bmp] with its left top at ([left], [top]), every art pixel [artPx] whole device pixels (get it from
     * [FxSpace.artPixels]), without smoothing and snapped to the device pixel grid (the mockup's `blit`). Not for rotated canvases.
     */
    fun crisp(bmp: Bitmap, left: Float, top: Float, artPx: Int) {
        reset(smooth = false)
        paint.alpha = a255()
        val l = left.roundToInt().toFloat()
        val t = top.roundToInt().toFloat()
        rect.set(l, t, l + bmp.width * artPx, t + bmp.height * artPx)
        raw.drawBitmap(bmp, null, rect, paint)
    }

    /** [crisp] for the pixel art with [key] (see [FxArt]); nothing when there is no such art. */
    fun crisp(key: String, left: Float, top: Float, artPx: Int) {
        val bmp = art.bitmap(key) ?: return
        crisp(bmp, left, top, artPx)
    }

    /** The art bitmap for [key] (see [FxArt]), or null. */
    fun bitmapOf(key: String): Bitmap? = art.bitmap(key)

    /** Fills a square of [size] px (a pixel bit) with [color], centred on ([cx], [cy]) and snapped to whole pixels. */
    fun pixelBit(cx: Float, cy: Float, size: Float, color: Int) {
        reset(smooth = false)
        paint.isAntiAlias = false
        paint.color = color or OPAQUE
        paint.alpha = a255()
        val s = size.roundToInt().coerceAtLeast(1).toFloat()
        val l = (cx - s / 2f).roundToInt().toFloat()
        val t = (cy - s / 2f).roundToInt().toFloat()
        raw.drawRect(l, t, l + s, t + s, paint)
    }

    private fun line(pts: FloatArray, count: Int, closed: Boolean) {
        path.rewind()
        path.moveTo(pts[0], pts[1])
        for (i in 1 until count) path.lineTo(pts[i * 2], pts[i * 2 + 1])
        if (closed) path.close()
    }

    /**
     * Strokes the line through the first [count] points of [pts] (default all), [width] px, round caps and joins, in [color]. With
     * [glow] > 0 two wider, fainter passes go underneath: a cheap stand-in for the JS `shadowBlur` (which a hardware canvas cannot
     * do on shapes); [glow] is the extra width of the widest pass in px.
     */
    fun stroke(pts: FloatArray, width: Float, color: Int, a: Float = 1f, glow: Float = 0f, count: Int = pts.size / 2) {
        if (count < 2) return
        line(pts, count, false)
        if (glow > 0f) {
            strokePath(width + glow, color, a * .22f)
            strokePath(width + glow * .5f, color, a * .3f)
        }
        strokePath(width, color, a)
    }

    private fun strokePath(width: Float, color: Int, a: Float) {
        reset()
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        paint.strokeWidth = width
        paint.color = color or OPAQUE
        paint.alpha = a255(a * alphaOf(color))
        raw.drawPath(path, paint)
    }

    /** One straight line segment, round caps. */
    fun line(x0: Float, y0: Float, x1: Float, y1: Float, width: Float, color: Int, a: Float = 1f) {
        reset()
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = width
        paint.color = color or OPAQUE
        paint.alpha = a255(a * alphaOf(color))
        raw.drawLine(x0, y0, x1, y1, paint)
    }

    /** Fills the polygon through [pts] with [color]. */
    fun fillPoly(pts: FloatArray, color: Int, a: Float = 1f, count: Int = pts.size / 2) {
        if (count < 3) return
        line(pts, count, true)
        reset()
        paint.style = Paint.Style.FILL
        paint.color = color or OPAQUE
        paint.alpha = a255(a * alphaOf(color))
        raw.drawPath(path, paint)
    }

    /** Outlines the polygon through [pts] ([closed] or an open line), [width] px. */
    fun strokePoly(pts: FloatArray, width: Float, color: Int, a: Float = 1f, closed: Boolean = true, count: Int = pts.size / 2) {
        if (count < 2) return
        line(pts, count, closed)
        strokePath(width, color, a)
    }

    /** Fills the polygon with a linear gradient from ([x0], [y0]) to ([x1], [y1]) through [colors] at [stops] (ARGB; their alpha counts, times [alpha]). */
    fun gradientPoly(pts: FloatArray, x0: Float, y0: Float, x1: Float, y1: Float, colors: IntArray, stops: FloatArray) {
        if (pts.size < 6) return
        line(pts, pts.size / 2, true)
        reset()
        paint.style = Paint.Style.FILL
        paint.shader = gradient(x0, y0, x1, y1, colors, stops)
        paint.alpha = a255()
        raw.drawPath(path, paint)
    }

    // the last gradient is kept: one that stays the same from frame to frame (the ice cube's face) is built only once
    private val gradientKey = GradientKey()
    private var gradientShader: Shader? = null

    private fun gradient(x0: Float, y0: Float, x1: Float, y1: Float, colors: IntArray, stops: FloatArray): Shader {
        val kept = gradientShader
        if (kept != null && gradientKey.matches(x0, y0, x1, y1, colors, stops)) return kept
        val made = LinearGradient(x0, y0, x1, y1, colors, stops, Shader.TileMode.CLAMP)
        gradientKey.remember(x0, y0, x1, y1, colors, stops)
        gradientShader = made
        return made
    }

    /** Clips to the polygon through [pts] (inside a [save] / [restore] pair). */
    fun clipPoly(pts: FloatArray) {
        line(pts, pts.size / 2, true)
        raw.clipPath(path)
    }

    /** Fills an ellipse centred on ([cx], [cy]) with radii [rx], [ry], turned by [rot] radians. */
    fun ellipse(cx: Float, cy: Float, rx: Float, ry: Float, rot: Float, color: Int, a: Float = 1f) {
        reset()
        paint.style = Paint.Style.FILL
        paint.color = color or OPAQUE
        paint.alpha = a255(a * alphaOf(color))
        raw.save()
        raw.translate(cx, cy)
        raw.rotate(Math.toDegrees(rot.toDouble()).toFloat())
        rect.set(-rx, -ry, rx, ry)
        raw.drawOval(rect, paint)
        raw.restore()
    }

    /** Fills a rectangle. */
    fun rect(l: Float, t: Float, r: Float, b: Float, color: Int, a: Float = 1f) {
        reset()
        paint.style = Paint.Style.FILL
        paint.color = color or OPAQUE
        paint.alpha = a255(a * alphaOf(color))
        raw.drawRect(l, t, r, b, paint)
    }

    /** Outlines a rectangle, [width] px. */
    fun strokeRect(l: Float, t: Float, r: Float, b: Float, width: Float, color: Int, a: Float = 1f) {
        reset()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = width
        paint.color = color or OPAQUE
        paint.alpha = a255(a * alphaOf(color))
        raw.drawRect(l, t, r, b, paint)
    }

    private fun alphaOf(color: Int): Float = (color ushr 24).let { if (it == 0) 1f else it / 255f }

    private companion object { const val OPAQUE = 0xFF000000.toInt() }
}
