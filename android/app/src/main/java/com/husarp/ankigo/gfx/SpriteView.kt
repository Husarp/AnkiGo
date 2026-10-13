package com.husarp.ankigo.gfx

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import com.husarp.ankigo.data.SpriteImage
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt

// TICKET T03 owns this file. Public members are fixed (the battle panel / screens use them); add private helpers freely.

/**
 * Draws a pixel-art sprite crisply (nearest-neighbour, integer device-pixel scale), animated if it has frames,
 * bottom-centred in the view. Effects for the battle panel: [flash] (white-ish hit flash), [silhouette],
 * [mirror] (used when only a front sprite exists for the player's back view), and a placeholder (type-coloured
 * circle + text) when there's no image yet. Animation runs only while attached and visible.
 */
class SpriteView @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {
    /** Size multiplier in dp per sprite pixel (mockup: 2). The image never exceeds the view. */
    var scaleDp: Float = 2f
        set(v) { field = v; requestLayout(); invalidate() }
    var mirror: Boolean = false
        set(v) { field = v; invalidate() }
    var silhouette: Boolean = false
        set(v) { field = v; invalidate() }
    /** Colour of the [silhouette] (ARGB). */
    var silhouetteColor: Int = 0x55000000
        set(v) { field = v; invalidate() }
    /** 0..1 hit flash strength; animate it for the flash effect. */
    var flash: Float = 0f
        set(v) { val c = v.coerceIn(0f, 1f); if (c != field) { field = c; invalidate() } }

    private var image: SpriteImage? = null
    private var frame = 0
    private var placeholderText: String? = null
    private var placeholderColor = 0
    private var ticking = false

    // Nearest-neighbour: no bilinear filtering, no anti-aliasing.
    private val paint = Paint().apply { isFilterBitmap = false; isAntiAlias = false; isDither = false }
    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER
    }
    private val src = Rect()
    private val dst = Rect()
    private val tick = Runnable {
        ticking = false
        val img = image ?: return@Runnable
        if (!img.animated) return@Runnable
        frame = (frame + 1) % img.frames.size
        invalidate()
        schedule()
    }

    fun setImage(img: SpriteImage?) {
        image = img
        frame = 0
        removeCallbacks(tick); ticking = false
        requestLayout()
        invalidate()
        updateAnimation()
    }

    fun setPlaceholder(text: String, color: Int) {
        placeholderText = text
        placeholderColor = color
        invalidate()
    }

    /** Height in px the current image is drawn at (for placing damage numbers). */
    fun drawnHeight(): Int {
        val img = image
        if (img != null) return (img.height * scaleFor(img, width, height)).roundToInt()
        if (placeholderText != null) return placeholderDiameter(width, height).roundToInt()
        return 0
    }

    /**
     * Device pixels per sprite pixel: [scaleDp] * density floored to a whole number (>= 1); reduced to fit the view,
     * and only fractional (below 1) if even 1 does not fit.
     */
    private fun scaleFor(img: SpriteImage, vw: Int, vh: Int): Float {
        if (vw <= 0 || vh <= 0) return 0f
        val wanted = maxOf(1, floor(scaleDp * resources.displayMetrics.density).toInt())
        val fit = min(vw.toFloat() / img.width, vh.toFloat() / img.height)
        val whole = min(wanted, floor(fit).toInt())
        return if (whole >= 1) whole.toFloat() else fit
    }

    private fun placeholderDiameter(vw: Int, vh: Int): Float =
        min(min(vw, vh) * 0.8f, 72f * resources.displayMetrics.density).coerceAtLeast(0f)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val img = image
        val wanted = maxOf(1, floor(scaleDp * resources.displayMetrics.density).toInt())
        val dw = if (img != null) img.width * wanted else (72f * resources.displayMetrics.density).roundToInt()
        val dh = if (img != null) img.height * wanted else (72f * resources.displayMetrics.density).roundToInt()
        setMeasuredDimension(resolveSize(dw + paddingLeft + paddingRight, widthMeasureSpec),
            resolveSize(dh + paddingTop + paddingBottom, heightMeasureSpec))
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val vw = width
        val vh = height
        val img = image
        if (img == null) {
            drawPlaceholder(canvas, vw, vh)
            return
        }
        val s = scaleFor(img, vw, vh)
        if (s <= 0f) return
        val bmp = img.frames[frame.coerceIn(0, img.frames.size - 1)]
        // Whole-pixel destination so every sprite pixel is the same size.
        val dw = (bmp.width * s).roundToInt()
        val dh = (bmp.height * s).roundToInt()
        val left = (vw - dw) / 2
        val top = vh - dh
        src.set(0, 0, bmp.width, bmp.height)
        dst.set(left, top, left + dw, top + dh)
        paint.colorFilter = currentFilter()
        val save = canvas.save()
        if (mirror) canvas.scale(-1f, 1f, vw / 2f, vh / 2f)
        canvas.drawBitmap(bmp, src, dst, paint)
        canvas.restoreToCount(save)
    }

    private fun currentFilter(): android.graphics.ColorFilter? {
        if (silhouette) return PorterDuffColorFilter(silhouetteColor, PorterDuff.Mode.SRC_IN)
        val f = flash
        if (f <= 0f) return null
        // CSS brightness(2.2) saturate(.3) at full strength, blended from the identity.
        val sat = 1f + (0.3f - 1f) * f
        val b = 1f + (2.2f - 1f) * f
        val cm = ColorMatrix().apply { setSaturation(sat) }
        cm.postConcat(ColorMatrix(floatArrayOf(
            b, 0f, 0f, 0f, 0f,
            0f, b, 0f, 0f, 0f,
            0f, 0f, b, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )))
        return ColorMatrixColorFilter(cm)
    }

    private fun drawPlaceholder(canvas: Canvas, vw: Int, vh: Int) {
        val text = placeholderText ?: return
        val d = placeholderDiameter(vw, vh)
        if (d <= 0f) return
        val drawable = com.husarp.ankigo.ui.SpritePlaceholder(placeholderColor)
        drawable.setBounds(((vw-d)/2).toInt(), (vh-d).toInt(), ((vw+d)/2).toInt(), vh)
        drawable.draw(canvas)
    }

    // ---- animation: runs only while attached and shown ----

    private fun schedule() {
        val img = image ?: return
        if (ticking) return
        ticking = true
        postDelayed(tick, img.delays[frame.coerceIn(0, img.delays.size - 1)].coerceAtLeast(20).toLong())
    }

    private fun updateAnimation() {
        val img = image
        val run = android.animation.ValueAnimator.areAnimatorsEnabled() && !context.getSharedPreferences("study", 0).getBoolean("reduceMotion", false) && img != null && img.animated && isAttachedToWindow && windowVisibility == VISIBLE && isShown
        if (run) schedule() else { removeCallbacks(tick); ticking = false }
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); updateAnimation() }
    override fun onDetachedFromWindow() { removeCallbacks(tick); ticking = false; super.onDetachedFromWindow() }
    override fun onVisibilityChanged(changedView: View, visibility: Int) { super.onVisibilityChanged(changedView, visibility); updateAnimation() }
    override fun onWindowVisibilityChanged(visibility: Int) { super.onWindowVisibilityChanged(visibility); updateAnimation() }
}
