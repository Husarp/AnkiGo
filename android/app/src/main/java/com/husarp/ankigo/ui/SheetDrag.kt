package com.husarp.ankigo.ui

import android.animation.ValueAnimator
import android.content.Context
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import android.widget.LinearLayout
import android.widget.ScrollView

// R6-07 (item 18): a bottom sheet that really drags down to close. The numbers live in [SheetDrag] (pure, JVM-tested); [DragSheet] is the
// sheet's view and [SheetDragger] follows the finger.

/** The thresholds of a drag-to-close sheet. Pure. */
object SheetDrag {
    /** Released past this share of the sheet's height = close. */
    const val CLOSE_FRACTION = 0.30f
    /** A downward fling faster than this (dp per second) closes at any distance; an upward one this fast always springs back. */
    const val FLING_DP_PER_S = 800f

    /** The sheet only follows the finger downwards: never above its resting place. */
    fun offset(raw: Float): Float = raw.coerceAtLeast(0f)

    /** How far down the sheet is, 0..1 (0 if the height is not known yet). */
    fun fraction(offsetPx: Float, heightPx: Float): Float = if (heightPx <= 0f) 0f else (offsetPx / heightPx).coerceIn(0f, 1f)

    /** On release: slide out and close (true) or spring back (false). [velocityPxPerS] > 0 = moving down. */
    fun shouldClose(offsetPx: Float, heightPx: Float, velocityPxPerS: Float, density: Float): Boolean {
        if (offsetPx <= 0f || heightPx <= 0f) return false
        val fling = FLING_DP_PER_S * density
        if (velocityPxPerS >= fling) return true
        if (velocityPxPerS <= -fling) return false
        return fraction(offsetPx, heightPx) >= CLOSE_FRACTION
    }

    /** The scrim fades with the sheet: opaque at rest, 0 when the sheet is gone. */
    fun scrimAlpha(offsetPx: Float, heightPx: Float): Float = 1f - fraction(offsetPx, heightPx)

    /** How long the slide-out takes: shorter when little of the way is left (120..220 ms). */
    fun slideMs(offsetPx: Float, heightPx: Float): Long = (120 + 100 * (1f - fraction(offsetPx, heightPx))).toLong()
}

/** The linear layout of a sheet; hands its touches to [dragger] (the drag starts on the handle strip, or on a downward pull when the content is at its top). */
open class DragSheet(ctx: Context) : LinearLayout(ctx) {
    var dragger: SheetDragger? = null
    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = dragger?.intercept(ev) ?: super.onInterceptTouchEvent(ev)
    override fun onTouchEvent(ev: MotionEvent): Boolean = dragger?.touch(ev) ?: super.onTouchEvent(ev)

    /** The sheet left its window (scrim tap, screen closed): the drag stops, nothing animates or calls back into the closed sheet. */
    override fun onDetachedFromWindow() {
        dragger?.dispose()
        super.onDetachedFromWindow()
    }
}

/**
 * Makes [sheet] follow the finger. [scrim] fades with it; [scroll] (the sheet's scrolling content, if any) lets a downward pull start the drag
 * only when it is at its top. [handleZonePx] = the strip at the top of the sheet where a pull always starts the drag.
 * Released: slides out and calls [onClose], or springs back. With reduced motion there is no slide: it just closes (or snaps back).
 */
class SheetDragger(private val sheet: View, private val scrim: View?, private val scroll: ScrollView?, private val handleZonePx: Int,
                   private val onClose: () -> Unit) {
    private val slop = ViewConfiguration.get(sheet.context).scaledTouchSlop
    private var tracker: VelocityTracker? = null
    private var downY = 0f
    private var startY = 0f
    private var inHandle = false
    private var dragging = false
    private var anim: ValueAnimator? = null
    private var closed = false // onClose runs at most once
    private var disposed = false

    private fun close() { if (!closed && !disposed) { closed = true; onClose() } }

    /** From the sheet's onDetachedFromWindow: cancels the running slide, recycles the velocity tracker, and silences every later callback. */
    fun dispose() {
        disposed = true
        anim?.cancel(); anim = null
        tracker?.recycle(); tracker = null
        dragging = false
    }

    private fun still(): Boolean = !ValueAnimator.areAnimatorsEnabled() || sheet.context.getSharedPreferences("study", 0).getBoolean("reduceMotion", false)

    private fun begin(ev: MotionEvent) {
        if (disposed) return
        anim?.cancel()
        tracker?.recycle(); tracker = VelocityTracker.obtain()
        downY = ev.rawY; startY = ev.rawY; inHandle = ev.y < handleZonePx; dragging = false
        tracker!!.addMovement(ev)
    }

    private fun canStart(ev: MotionEvent): Boolean =
        ev.rawY - downY > slop && (inHandle || scroll == null || scroll.scrollY <= 0)

    private fun move(ev: MotionEvent) {
        tracker?.addMovement(ev)
        if (!dragging) {
            if (!canStart(ev)) return
            dragging = true; startY = ev.rawY // the drag starts where it was recognised: no jump
        }
        apply(SheetDrag.offset(ev.rawY - startY))
    }

    private fun apply(offset: Float) {
        sheet.translationY = offset
        scrim?.alpha = SheetDrag.scrimAlpha(offset, sheet.height.toFloat())
    }

    private fun end(cancelled: Boolean) {
        val t = tracker; tracker = null
        if (!dragging) { t?.recycle(); return }
        dragging = false
        t?.computeCurrentVelocity(1000)
        val vy = t?.yVelocity ?: 0f
        t?.recycle()
        val off = sheet.translationY
        val h = sheet.height.toFloat()
        if (!cancelled && SheetDrag.shouldClose(off, h, vy, sheet.resources.displayMetrics.density)) {
            if (still()) { close(); return }
            slide(off, h, SheetDrag.slideMs(off, h)) { close() }
        } else {
            if (still()) apply(0f) else slide(off, 0f, 200) {}
        }
    }

    private fun slide(from: Float, to: Float, ms: Long, done: () -> Unit) {
        anim = ValueAnimator.ofFloat(from, to).apply {
            duration = ms
            interpolator = DecelerateInterpolator()
            addUpdateListener { apply(it.animatedValue as Float) }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                var cancelled = false
                override fun onAnimationCancel(a: android.animation.Animator) { cancelled = true }
                override fun onAnimationEnd(a: android.animation.Animator) { if (!cancelled && !disposed) done() }
            })
            start()
        }
    }

    /** From the sheet's onInterceptTouchEvent. */
    fun intercept(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> begin(ev)
            MotionEvent.ACTION_MOVE -> move(ev)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> end(ev.actionMasked == MotionEvent.ACTION_CANCEL)
        }
        return dragging
    }

    /** From the sheet's onTouchEvent (the events nobody below took, and the ones after the drag was taken over). */
    fun touch(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> begin(ev)
            MotionEvent.ACTION_MOVE -> move(ev)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> end(ev.actionMasked == MotionEvent.ACTION_CANCEL)
        }
        return true
    }
}
