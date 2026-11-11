package com.husarp.ankigo.ui

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Outline
import android.widget.TextView
import android.view.ViewGroup
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
             }, uptime: () -> Long = { android.os.SystemClock.uptimeMillis() }, confirm: () -> Unit) {
        val ctx = view.context
        val ink = Ui.c(ctx, R.color.ag_hold_ink)
        fun tint(v: View) {
            if (v is TextView) v.setTextColor(ink)
            if (v is ViewGroup) for (i in 0 until v.childCount) tint(v.getChildAt(i))
        }
        tint(view)
        if (!view.isEnabled) view.alpha = .45f
        view.minimumHeight = maxOf(view.minimumHeight, Ui.dp(ctx, 56))
        if (view is TextView) view.minHeight = maxOf(view.minHeight, Ui.dp(ctx, 56))
        val fill = object : Drawable() {
            override fun onLevelChange(level: Int): Boolean { invalidateSelf(); return true }
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            override fun draw(canvas: Canvas) {
                val radius = Ui.dp(ctx, 12).toFloat()
                val bottom = bounds.bottom.toFloat()
                paint.color = Ui.c(ctx, R.color.ag_hold_idle)
                canvas.drawRoundRect(bounds.left.toFloat(), bounds.top.toFloat(), bounds.right.toFloat(), bottom, radius, radius, paint)
                val save = canvas.save()
                canvas.clipRect(bounds.left, bounds.top, bounds.left + bounds.width() * level / 10000, bounds.bottom)
                paint.color = Ui.c(ctx, R.color.ag_hold_fill)
                canvas.drawRoundRect(bounds.left.toFloat(), bounds.top.toFloat(), bounds.right.toFloat(), bottom, radius, radius, paint)
                canvas.restoreToCount(save)

            }
            override fun getOutline(outline: Outline) { outline.setRoundRect(bounds, Ui.dp(ctx, 12).toFloat()) }
            override fun setAlpha(alpha: Int) { paint.alpha = alpha }
            override fun setColorFilter(filter: ColorFilter?) { paint.colorFilter = filter }
            @Deprecated("Deprecated in Java") override fun getOpacity() = PixelFormat.TRANSLUCENT
        }
        // The fill stays behind the fixed label, including at 100%.
        view.background = fill
        view.clipToOutline = true
        var frame: Runnable? = null
        var active = false
        var fired = false
        var completion: Runnable? = null
        fun resetHold(retract: Boolean = false) {
            active = false
            completion?.let { timer.remove(it) }; completion = null
            frame?.let { timer.remove(it) }; frame = null
            val from = fill.level
            if (retract && from > 0) {
                val began = uptime()
                frame = object : Runnable {
                    override fun run() {
                        val elapsed = (uptime() - began).coerceAtLeast(0L)
                        fill.level = (from - elapsed * 10000 * 3 / DURATION_MS).coerceAtLeast(0L).toInt()
                        if (fill.level > 0) timer.post(this, 16L) else frame = null
                    }
                }.also { timer.post(it, 16L) }
            } else fill.level = 0
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
            resetHold()
            fired = false; active = true
            fill.level = 1
            view.parent?.requestDisallowInterceptTouchEvent(true)
            // A real delay keeps confirmation deliberate even when system animations are disabled.
            completion = Runnable { if (active) finish() }.also { timer.post(it, DURATION_MS) }
            // Hold progress measures real time, even with system animations disabled or scaled.
            val began = uptime()
            frame = object : Runnable {
                override fun run() {
                    if (!active) return
                    if (!view.isEnabled || !allowed()) { resetHold(); return }
                    fill.level = ((uptime() - began).coerceIn(0L, DURATION_MS) * 10000 / DURATION_MS).toInt()
                    timer.post(this, 16L)
                }
            }.also { timer.post(it, 16L) }
        }
        view.contentDescription = label.replaceFirstChar { it.uppercase() }
        view.isFocusable = true
        view.setOnClickListener { }
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
                MotionEvent.ACTION_MOVE -> if (event.x < 0 || event.y < 0 || event.x >= view.width || event.y >= view.height) resetHold(retract = true)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> resetHold(retract = true)
            }
            true
        }
        view.setOnKeyListener { _, key, event ->
            if (key in listOf(KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_SPACE)) {
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) start()
                if (event.action == KeyEvent.ACTION_UP) resetHold(retract = true)
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
