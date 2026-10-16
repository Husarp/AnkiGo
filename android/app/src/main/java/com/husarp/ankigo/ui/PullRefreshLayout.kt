package com.husarp.ankigo.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import com.husarp.ankigo.R

/**
 * Pull-to-refresh for a scrolling child (own view: androidx swiperefreshlayout is not in the offline build). Pull down at the
 * top: a round spinner follows the finger and the arc grows; release past the threshold: it spins while [onRefresh] reloads,
 * until [setRefreshing] (false) slides it away; release early: it slides back and nothing happens.
 */
class PullRefreshLayout(ctx: Context, private val target: View) : FrameLayout(ctx) {
    var onRefresh: (() -> Unit)? = null
    var refreshing = false; private set
    private val size = Ui.dp(ctx, 40)
    private val threshold = Ui.dp(ctx, 64)
    private val rest = Ui.dp(ctx, 12)        // top offset of the spinner while it spins
    private val slop = ViewConfiguration.get(ctx).scaledTouchSlop
    private var downY = 0f
    private var pull = 0f
    private var dragging = false
    private var slide: ValueAnimator? = null
    private val spinner = Spinner(ctx)

    init {
        addView(target, LayoutParams(-1, -1))
        addView(spinner, LayoutParams(size, size, android.view.Gravity.CENTER_HORIZONTAL or android.view.Gravity.TOP))
        spinner.visibility = View.GONE
    }

    private fun place(top: Float) { spinner.translationY = top; spinner.progress = (top / (threshold * .75f)).coerceIn(0f, 1f) }

    override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downY = e.y; dragging = false }
            MotionEvent.ACTION_MOVE -> if (!refreshing && !target.canScrollVertically(-1) && e.y - downY > slop) { startDrag(e.y); return true }
        }
        return dragging
    }

    private fun startDrag(y: Float) {
        slide?.cancel(); dragging = true; downY = y; pull = 0f
        spinner.visibility = View.VISIBLE; spinner.spinning = false; place(-size.toFloat())
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_MOVE -> if (dragging) {
                pull = ((e.y - downY) * .5f).coerceIn(0f, threshold * 1.6f)
                place(pull - size)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (dragging) {
                dragging = false
                if (e.actionMasked == MotionEvent.ACTION_UP && pull >= threshold) { setRefreshing(true); onRefresh?.invoke() }
                else slideTo(-size.toFloat()) { spinner.visibility = View.GONE }
            }
        }
        return dragging || e.actionMasked == MotionEvent.ACTION_DOWN
    }

    /** Show or hide the spinning spinner (also used when a refresh starts from a button). */
    fun setRefreshing(on: Boolean) {
        if (on == refreshing) return
        refreshing = on
        slide?.cancel()
        if (on) {
            spinner.visibility = View.VISIBLE; spinner.spinning = true; spinner.progress = 1f
            if (spinner.translationY < -size + 1f && !dragging) place(-size.toFloat())
            slideTo(rest.toFloat()) {}
        } else {
            slideTo(-size.toFloat()) { spinner.spinning = false; spinner.visibility = View.GONE }
        }
    }

    private fun slideTo(top: Float, end: () -> Unit) {
        slide?.cancel()
        val from = spinner.translationY
        slide = ValueAnimator.ofFloat(from, top).apply {
            duration = 200
            addUpdateListener { spinner.translationY = it.animatedValue as Float }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                private var cancelled = false
                override fun onAnimationCancel(a: android.animation.Animator) { cancelled = true }
                override fun onAnimationEnd(a: android.animation.Animator) { if (!cancelled) end() }
            })
            start()
        }
    }

    override fun onDetachedFromWindow() { slide?.cancel(); super.onDetachedFromWindow() }

    /** A round card with an accent arc: grows with the pull, rotates while refreshing. */
    private class Spinner(ctx: Context) : View(ctx) {
        var progress = 0f; set(v) { field = v; invalidate() }
        var spinning = false; set(v) { field = v; invalidate() }
        private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; color = Ui.c(ctx, R.color.ag_mauve); strokeWidth = Ui.dp(ctx, 3).toFloat()
        }
        private val box = RectF()
        init { elevation = Ui.dp(ctx, 4).toFloat(); outlineProvider = android.view.ViewOutlineProvider.BACKGROUND; background = Ui.rounded(Ui.c(ctx, R.color.ag_card), 20, ctx) }
        override fun onDraw(c: Canvas) {
            val inset = ring.strokeWidth * 2.2f
            box.set(inset, inset, width - inset, height - inset)
            if (spinning) {
                val t = (SystemClock.uptimeMillis() % 1200) / 1200f
                c.drawArc(box, t * 360f - 90f, 100f + 160f * (.5f + .5f * kotlin.math.sin(t * 2 * Math.PI.toFloat())), false, ring)
                postInvalidateOnAnimation()
            } else {
                c.drawArc(box, -90f + progress * 120f, (40f + 280f * progress).coerceAtMost(300f), false, ring)
            }
        }
    }
}
