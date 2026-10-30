package com.husarp.ankigo.ui

import android.animation.ValueAnimator
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.TextView
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Presentation only: retarget from the visible amount, never from an old animation's destination. */
internal class MoneyRoll(private val view: TextView) {
    private var shown: Int? = null
    private var target: Int? = null
    private var animator: ValueAnimator? = null

    init {
        view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) = Unit
            override fun onViewDetachedFromWindow(v: View) {
                animator?.cancel()
                target?.let { draw(it) }
            }
        })
    }

    fun reset(text: CharSequence) {
        animator?.cancel()
        shown = null; target = null
        view.text = text
    }

    fun set(amount: Int, initial: Int? = null) {
        if (target == amount) return
        animator?.cancel()
        val from = shown ?: initial ?: parse(view.text) ?: amount
        target = amount
        draw(from)
        if (from == amount) return
        if (!ValueAnimator.areAnimatorsEnabled() || view.context.getSharedPreferences("study", 0).getBoolean("reduceMotion", false)) {
            draw(amount)
            return
        }
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = MoneyRollMath.duration(from, amount)
            interpolator = DecelerateInterpolator()
            addUpdateListener { draw(MoneyRollMath.value(from, amount, it.animatedValue as Float)) }
            start()
        }
    }

    private fun draw(amount: Int) { shown = amount; view.text = String.format(Locale.US, "%,d", amount) }

    companion object {
        fun parse(text: CharSequence): Int? = text.toString().replace(",", "").toIntOrNull()
    }
}

internal object MoneyRollMath {
    fun duration(from: Int, to: Int): Long = (300L + abs(to.toLong() - from.toLong()).coerceAtMost(10000L) * 700L / 10000L)
    fun value(from: Int, to: Int, fraction: Float): Int =
        (from.toDouble() + (to.toDouble() - from) * fraction.coerceIn(0f, 1f)).roundToInt()
}
