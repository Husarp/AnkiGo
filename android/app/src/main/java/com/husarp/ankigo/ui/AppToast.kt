package com.husarp.ankigo.ui

import android.animation.ValueAnimator
import android.content.Context
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.TextView
import com.husarp.ankigo.R
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/**
 * The app's own small message popup (replaces the system Toast): a rounded card, bottom centre, above the bottom bar.
 * It is a plain child of the host's FrameLayout, so it takes no touches except its own (a tap hides it) and no animation
 * runs while nothing is shown. One message at a time per host: a new one replaces the one showing. Main thread only
 * ([Ui.toast] posts to it). Timing and placement are in [AppToastRules].
 */
object AppToast {
    /** The weak map's value: it must not reach its key (the host) strongly, or the entry (and the Activity) is never collected. */
    private class Shown(view: TextView, val hide: Runnable) { val view = WeakReference(view) }

    /** The delayed hide; holds the host weakly (a view's delayed post is queued with the view, which the host holds). */
    private class Hide(host: FrameLayout) : Runnable {
        private val ref = WeakReference(host)
        override fun run() { ref.get()?.let { dismiss(it, animate = true) } }
    }

    private val showing = WeakHashMap<FrameLayout, Shown>()
    private val floats = ArrayList<WeakReference<FrameLayout>>()
    private var front: WeakReference<android.app.Activity>? = null

    /** A message asked for by a screen that is closing (saved, then finish()): it shows on the next screen that comes to the front. */
    private var waiting: Pair<String, Long>? = null

    /** An AnkiGo screen came to the front (AnkiGoApp): the place for messages whose caller only has an application context. */
    fun resumed(a: android.app.Activity) {
        front = WeakReference(a)
        val (text, at) = waiting ?: return
        waiting = null
        if (AppToastRules.waitOver(at, android.os.SystemClock.uptimeMillis()) || a.isFinishing || a.isDestroyed) return
        a.window.decorView.post { (a.findViewById<ViewGroup>(android.R.id.content) as? FrameLayout)?.let { show(it, text) } }
    }

    /** An AnkiGo screen left the front. */
    fun paused(a: android.app.Activity) { if (front?.get() === a) front = null }

    /**
     * Registers the root of an overlay window (floating card) as a place for messages from code that has no Activity
     * ([Ui.toast] with a service context). Call once for the window root; it is only used while attached to a window.
     */
    fun attach(root: FrameLayout) {
        floats.removeAll { it.get() == null }
        if (floats.none { it.get() === root }) floats += WeakReference(root)
    }

    /**
     * Shows [text] in the window of [ctx]: the content of its Activity, else the newest attached floating window.
     * False when there is no such window (the caller falls back to the system Toast).
     */
    internal fun show(ctx: Context, text: String): Boolean {
        // an application context (a background callback, a screen that can be recreated meanwhile) has no window of its own: use the screen in front
        val act = Ui.findActivity(ctx) ?: front?.get()
        val other = front?.get()?.takeIf { it !== act && !it.isFinishing && !it.isDestroyed }
        val float = floats.lastOrNull { it.get()?.isAttachedToWindow == true }?.get()
        val place = AppToastRules.place(act != null, act != null && (act.isFinishing || act.isDestroyed), other != null, float != null)
        val host: FrameLayout = when (place) {
            AppToastRules.Place.CALLER -> act!!.findViewById<ViewGroup>(android.R.id.content) as? FrameLayout
            AppToastRules.Place.OTHER_ACTIVITY -> other!!.findViewById<ViewGroup>(android.R.id.content) as? FrameLayout
            AppToastRules.Place.FLOAT -> float
            AppToastRules.Place.WAIT -> { wait(ctx, text); return true }
            AppToastRules.Place.NONE -> null
        } ?: return false
        show(host, text)
        return true
    }

    /** A finishing screen's message: it waits for the next screen in front; after [AppToastRules.WAIT_MS] with none it goes to the system Toast (AnkiGo is not on screen). */
    private fun wait(ctx: Context, text: String) {
        val w = text to android.os.SystemClock.uptimeMillis()
        waiting = w
        val app = ctx.applicationContext
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (waiting === w) { waiting = null; Ui.systemToast(app, text) }
        }, AppToastRules.WAIT_MS)
    }

    /** Shows [text] on top of [host] (replacing the message showing there). */
    fun show(host: FrameLayout, text: String) {
        val ctx = host.context
        dismiss(host, animate = false)
        val still = AppToastRules.still(
            ValueAnimator.areAnimatorsEnabled(), ctx.getSharedPreferences("study", 0).getBoolean("reduceMotion", false))
        val wDp = (host.width / ctx.resources.displayMetrics.density).toInt().takeIf { it > 0 } ?: ctx.resources.configuration.screenWidthDp
        val hDp = (host.height / ctx.resources.displayMetrics.density).toInt().takeIf { it > 0 } ?: ctx.resources.configuration.screenHeightDp
        val tv = Ui.text(ctx, 14f, 800, R.color.ag_text).apply {
            this.text = text
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            gravity = Gravity.CENTER
            maxWidth = Ui.dp(ctx, AppToastRules.widthDp(wDp))
            setPadding(Ui.dp(ctx, 16), Ui.dp(ctx, 11), Ui.dp(ctx, 16), Ui.dp(ctx, 11))
            background = Ui.rounded(Ui.c(ctx, R.color.ag_card), 14, ctx).apply { setStroke(Ui.dp(ctx, 1), Ui.c(ctx, R.color.ag_s1)) }
            elevation = Ui.dp(ctx, 8f)
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            isClickable = true
        }
        val hide = Hide(host)
        val shown = Shown(tv, hide)
        tv.setOnClickListener { if (showing[host] === shown) dismiss(host, animate = true) }
        // the host goes away before the message is hidden (the screen finishes): drop the entry and the pending hide, take the view out afterwards
        tv.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {}
            override fun onViewDetachedFromWindow(v: View) {
                tv.removeOnAttachStateChangeListener(this)
                if (showing[host] !== shown) return // dismiss() already took it down
                showing.remove(host)
                tv.removeCallbacks(hide)
                tv.animate().cancel()
                android.os.Handler(android.os.Looper.getMainLooper()).post { host.removeView(tv) }
            }
        })
        val lp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
        lp.bottomMargin = Ui.dp(ctx, AppToastRules.bottomDp(hDp, keyboardOverlapDp(host)))
        showing[host] = shown
        host.addView(tv, lp)
        if (still) {
            tv.alpha = 1f
        } else {
            tv.alpha = 0f
            tv.translationY = Ui.dp(ctx, AppToastRules.SLIDE_DP.toFloat())
            tv.animate().alpha(1f).translationY(0f).setDuration(AppToastRules.FADE_MS).start()
        }
        tv.postDelayed(hide, AppToastRules.showMs(text))
    }

    private fun dismiss(host: FrameLayout, animate: Boolean) {
        val s = showing.remove(host) ?: return
        val tv = s.view.get() ?: return
        tv.removeCallbacks(s.hide)
        tv.animate().cancel()
        val still = AppToastRules.still(
            ValueAnimator.areAnimatorsEnabled(), host.context.getSharedPreferences("study", 0).getBoolean("reduceMotion", false))
        if (animate && !still && tv.isAttachedToWindow) {
            tv.isClickable = false
            tv.animate().alpha(0f).setDuration(AppToastRules.FADE_MS).withEndAction { host.removeView(tv) }.start()
        } else {
            host.removeView(tv)
        }
    }

    /** How far (dp) the keyboard covers the bottom of [host]; 0 when it is closed or the window already resized for it. */
    private fun keyboardOverlapDp(host: FrameLayout): Int {
        if (Build.VERSION.SDK_INT < 30) return 0
        return try {
            val ime = host.rootWindowInsets?.getInsets(WindowInsets.Type.ime())?.bottom ?: 0
            if (ime <= 0) return 0
            val at = IntArray(2)
            host.getLocationInWindow(at)
            val overlap = at[1] + host.height - (host.rootView.height - ime)
            (overlap / host.context.resources.displayMetrics.density).toInt().coerceAtLeast(0)
        } catch (e: RuntimeException) { 0 }
    }
}
