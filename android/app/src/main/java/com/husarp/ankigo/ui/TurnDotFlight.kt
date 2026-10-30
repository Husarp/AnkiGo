package com.husarp.ankigo.ui

import android.animation.ValueAnimator
import android.graphics.Matrix
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import com.husarp.ankigo.R
import com.husarp.ankigo.gfx.SpriteView

/** Presentation only. One animator starts every dot together; the event queue waits for its 500 ms absorption. */
internal class TurnDotFlight(private val layer: FrameLayout) {
    private var animator: ValueAnimator? = null
    private val copies = ArrayList<View>()

    fun cancel() {
        animator?.cancel(); animator = null
        copies.forEach { layer.removeView(it) }; copies.clear()
    }

    internal fun seek(ms: Long) { animator?.currentPlayTime = ms }

    private fun centre(v: View): FloatArray {
        val toGlobal = Matrix()
        v.transformMatrixToGlobal(toGlobal)
        val cy = if (v is SpriteView && v.drawnHeight() > 0) v.height - v.drawnBottomInset() - v.drawnHeight() / 2f else v.height / 2f
        val pt = floatArrayOf(v.width / 2f, cy)
        toGlobal.mapPoints(pt)
        val layerGlobal = Matrix(); val inverse = Matrix()
        layer.transformMatrixToGlobal(layerGlobal); layerGlobal.invert(inverse); inverse.mapPoints(pt)
        return pt
    }

    fun start(sources: List<View>, targets: List<View>, buttons: List<Int>, ms: Long) {
        cancel()
        val starts = sources.map { centre(it) }
        val ends = targets.map { centre(it) }
        sources.forEachIndexed { i, source ->
            val color = when (buttons[i]) { 1 -> R.color.ag_red; 2 -> R.color.ag_peach; 3 -> R.color.ag_green; 4 -> R.color.ag_blue; else -> R.color.ag_ov }
            val copy = View(layer.context).apply { background = Ui.rounded(Ui.c(context, color), 12, context) }
            layer.addView(copy, FrameLayout.LayoutParams(source.width, source.height))
            copy.layout(0, 0, source.width, source.height)
            copies += copy
        }
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = ms; interpolator = LinearInterpolator()
            addUpdateListener {
                val p = it.animatedValue as Float
                copies.forEachIndexed { i, v ->
                    val fadeOnly = buttons[i] == StudyCharge.OK || buttons[i] == 0
                    val q = p * p * (3f - 2f * p)
                    v.x = starts[i][0] + (if (fadeOnly) 0f else (ends[i][0] - starts[i][0]) * q) - v.width / 2f
                    v.y = starts[i][1] + (if (fadeOnly) 0f else (ends[i][1] - starts[i][1]) * q) - v.height / 2f
                    v.alpha = if (fadeOnly) 1f - p else ((1f - p) / .25f).coerceIn(0f, 1f)
                    val scale = if (fadeOnly) 1f + p * .4f else 1f - p * .85f
                    v.scaleX = scale; v.scaleY = scale
                }
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    copies.forEach { layer.removeView(it) }; copies.clear(); animator = null
                }
            })
            start()
        }
    }
}
