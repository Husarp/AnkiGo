package com.husarp.ankigo.ui

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import android.view.accessibility.AccessibilityNodeInfo
import com.husarp.ankigo.R

/** One deliberate one-second hold. Releasing, leaving the target or detaching cancels it. */
internal object HoldToConfirm {
    const val DURATION_MS = 1000L

    internal interface Timer {
        fun post(task: Runnable, delayMillis: Long)
        fun remove(task: Runnable)
    }

    fun bind(view: View, label: String, allowed: () -> Boolean = { true },
             timer: Timer = object : Timer {
                 override fun post(task: Runnable, delayMillis: Long) { view.postDelayed(task, delayMillis) }
                 override fun remove(task: Runnable) { view.removeCallbacks(task) }
             }, confirm: () -> Unit) {
        val fill = object : Drawable() {
            override fun onLevelChange(level: Int): Boolean { invalidateSelf(); return true }
            val paint = Paint().apply { color = Ui.c(view.context, R.color.ag_green); alpha = 85 }
            override fun draw(canvas: Canvas) { canvas.drawRect(bounds.left.toFloat(), bounds.top.toFloat(), bounds.left + bounds.width() * level / 10000f, bounds.bottom.toFloat(), paint) }
            override fun setAlpha(alpha: Int) { paint.alpha = alpha }
            override fun setColorFilter(filter: ColorFilter?) { paint.colorFilter = filter }
            @Deprecated("Deprecated in Java") override fun getOpacity() = PixelFormat.TRANSLUCENT
        }
        view.foreground = fill
        view.clipToOutline = true
        var animator: ValueAnimator? = null
        var active = false
        var fired = false
        var completion: Runnable? = null
        fun resetHold() {
            active = false
            completion?.let { timer.remove(it) }; completion = null
            animator?.cancel(); animator = null
            fill.level = 0
            view.parent?.requestDisallowInterceptTouchEvent(false)
        }
        fun finish() {
            if (fired || !view.isEnabled || !allowed()) return
            fired = true
            resetHold()
            confirm()
        }
        fun start() {
            if (active || !view.isEnabled || !allowed()) return
            fired = false; active = true
            view.parent?.requestDisallowInterceptTouchEvent(true)
            // A real delay keeps confirmation deliberate even when system animations are disabled.
            completion = Runnable { if (active) finish() }.also { timer.post(it, DURATION_MS) }
            animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = DURATION_MS
                interpolator = android.view.animation.LinearInterpolator()
                addUpdateListener {
                    if (!view.isEnabled || !allowed()) resetHold()
                    else {
                        fill.level = ((it.animatedValue as Float) * 10000).toInt()
                    }
                }
                start()
            }
        }
        view.contentDescription = "Hold to $label. Hold for one second, or use the accessibility long-click action."
        view.isFocusable = true
        view.setOnClickListener { Ui.toast(view.context, "Hold for one second to $label") }
        view.setOnLongClickListener { fired = false; finish(); true }
        view.setAccessibilityDelegate(object : View.AccessibilityDelegate() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.addAction(AccessibilityNodeInfo.AccessibilityAction(AccessibilityNodeInfo.ACTION_LONG_CLICK, "Confirm $label"))
            }
        })
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> start()
                MotionEvent.ACTION_MOVE -> if (event.x < 0 || event.y < 0 || event.x >= view.width || event.y >= view.height) resetHold()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> resetHold()
            }
            true
        }
        view.setOnKeyListener { _, key, event ->
            if (key in listOf(KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_SPACE)) {
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) start()
                if (event.action == KeyEvent.ACTION_UP) resetHold()
                true
            } else false
        }
        view.setOnFocusChangeListener { _, focused -> if (!focused) resetHold() }
        val windowFocus = ViewTreeObserver.OnWindowFocusChangeListener { focused -> if (!focused) resetHold() }
        // A retained dialog can lose window focus without its button losing View focus.
        view.viewTreeObserver.addOnWindowFocusChangeListener(windowFocus)
        var watchingWindowFocus = true
        view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                if (!watchingWindowFocus) {
                    v.viewTreeObserver.addOnWindowFocusChangeListener(windowFocus)
                    watchingWindowFocus = true
                }
            }
            override fun onViewDetachedFromWindow(v: View) {
                resetHold()
                if (v.viewTreeObserver.isAlive) v.viewTreeObserver.removeOnWindowFocusChangeListener(windowFocus)
                watchingWindowFocus = false
            }
        })
    }
}
