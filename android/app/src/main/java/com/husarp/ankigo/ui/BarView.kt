package com.husarp.ankigo.ui

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import android.view.animation.PathInterpolator
import com.husarp.ankigo.R

/**
 * Rounded progress bar (mockup `.hpbar` / `.xpbar` / `.bar2`): `ag_s0` track, coloured fill, corners = half the height.
 * Set its height from the layout (default 8 dp, wrap_content); [set] eases the fill over 450 ms.
 */
class BarView(ctx: Context) : View(ctx) {
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Ui.c(ctx, R.color.ag_s0) }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val unitGradient = Matrix()
    private var gradient: LinearGradient? = null
    private var gradFrom = 0
    private var gradTo = 0

    private var shown = 0f      // fraction currently drawn
    private var color = Ui.c(ctx, R.color.ag_green)
    private var anim: ValueAnimator? = null

    // "Ghost" of an HP drop: the part just lost, drawn behind the fill in a soft darkened red, held a moment, then shrinking away.
    private val ghostPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Bits.blend(Ui.c(ctx, R.color.ag_red), 0xFF000000.toInt(), 0.6f)
        alpha = 165
    }
    private var ghostFrac = 0f
    private var ghostAnim: ValueAnimator? = null

    /** True on the HP bars: a drop leaves a fading ghost of what was lost. */
    var ghostOnDrop = false

    /**
     * Shows the ghost up to [from] (never below where the fill is heading, [dest]); it holds [GHOST_HOLD_MS],
     * then shrinks to the fill. [dest] defaults to what is drawn now; [set] passes the new fraction, because
     * the fill is still at the old one when the drop starts.
     */
    internal fun ghostFrom(from: Float, dest: Float = shown) {
        ghostAnim?.cancel()
        ghostFrac = ghostTop(from, dest)
        if (ghostFrac == 0f) { invalidate(); return }
        ghostAnim = ValueAnimator.ofFloat(1f, 0f).apply {
            startDelay = GHOST_HOLD_MS
            duration = GHOST_SHRINK_MS
            interpolator = PathInterpolator(0.4f, 0f, 0.2f, 1f)
            val top = ghostFrac
            addUpdateListener { ghostFrac = dest + (top - dest) * (it.animatedValue as Float); invalidate() }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: android.animation.Animator) { ghostFrac = 0f; invalidate() }
            })
            start()
        }
        invalidate()
    }

    /** Draws the ghost still (no animation); screenshots use it. */
    internal fun ghostStill(from: Float) { ghostAnim?.cancel(); ghostFrac = from.coerceIn(0f, 1f); invalidate() }

    /** Track colour, `ag_s0` unless changed. */
    fun setTrackColor(argb: Int) { track.color = argb; invalidate() }

    /** Fill as a horizontal gradient (e.g. mauve -> pink for the goal bar) instead of the plain colour from [set]. */
    fun setGradient(fromColor: Int, toColor: Int) {
        gradFrom = fromColor
        gradTo = toColor
        gradient = LinearGradient(0f, 0f, 1f, 0f, fromColor, toColor, Shader.TileMode.CLAMP)
        invalidate()
    }

    /** Back to a plain-colour fill. */
    fun clearGradient() { gradient = null; invalidate() }

    /**
     * Fills [fraction] (0..1) in [fillColor] (ignored while a gradient is set). [animate] eases width and colour
     * over 450 ms with the mockup's cubic-bezier(.4,0,.2,1); false jumps there.
     */
    fun set(fraction: Float, fillColor: Int, animate: Boolean) {
        val to = fraction.coerceIn(0f, 1f)
        anim?.cancel()
        anim = null
        if (ghostOnDrop) {
            if (animate && isAttachedToWindow && to < shown) ghostFrom(maxOf(shown, ghostFrac), to)
            else if (to >= shown || !animate) { ghostAnim?.cancel(); ghostFrac = 0f }
        }
        if (!animate || !isAttachedToWindow || (to == shown && fillColor == color)) {
            shown = to
            color = fillColor
            invalidate()
            return
        }
        val fromFrac = shown
        val fromColor = color
        val ev = ArgbEvaluator()
        anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 450
            interpolator = PathInterpolator(0.4f, 0f, 0.2f, 1f)
            addUpdateListener {
                val t = it.animatedValue as Float
                shown = fromFrac + (to - fromFrac) * t
                color = ev.evaluate(t, fromColor, fillColor) as Int
                invalidate()
            }
            start()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val h = resolveSize(Ui.dp(context, 8), heightMeasureSpec)
        setMeasuredDimension(resolveSize(Ui.dp(context, 100), widthMeasureSpec), h)
    }

    override fun onDetachedFromWindow() {
        anim?.cancel()
        ghostAnim?.cancel()
        ghostFrac = 0f
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val r = h / 2f
        rect.set(0f, 0f, w, h)
        canvas.drawRoundRect(rect, r, r, track)
        if (ghostFrac > shown) {
            rect.set(0f, 0f, maxOf(w * ghostFrac, minOf(h, w)), h)
            canvas.drawRoundRect(rect, r, r, ghostPaint)
        }
        if (shown <= 0f) return
        // At least a round dot so a tiny fraction stays visible.
        val fw = maxOf(w * shown, minOf(h, w))
        rect.set(0f, 0f, fw, h)
        val g = gradient
        if (g != null) {
            unitGradient.setScale(fw, 1f)
            g.setLocalMatrix(unitGradient)
            fill.shader = g
        } else {
            fill.shader = null
            fill.color = color
        }
        canvas.drawRoundRect(rect, r, r, fill)
    }

    internal companion object {
        /** Where the ghost of a drop starts: [from] (0..1), or 0 (no ghost) when it is not above where the fill is heading, [dest]. */
        fun ghostTop(from: Float, dest: Float): Float = from.coerceIn(0f, 1f).let { if (it <= dest) 0f else it }

        const val GHOST_HOLD_MS = 450L
        const val GHOST_SHRINK_MS = 550L
    }
}
