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
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val r = h / 2f
        rect.set(0f, 0f, w, h)
        canvas.drawRoundRect(rect, r, r, track)
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
}
