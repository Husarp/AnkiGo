package com.husarp.ankigo.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.animation.PathInterpolator
import android.widget.TextView
import com.husarp.ankigo.R

/** PLAN 386: decoration only. State and click listeners never wait for an animator. */
object UiMotion {
    const val AXIS_MS = 250L
    const val WINDOW_MS = 180L
    const val SHEET_MS = 240L
    const val INDICATOR_MS = 150L
    val curve get() = PathInterpolator(.2f, 0f, 0f, 1f)
    fun enabled(ctx: Context): Boolean = ValueAnimator.areAnimatorsEnabled() &&
        !ctx.getSharedPreferences("study", 0).getBoolean("reduceMotion", false)

    /** Each view owns at most one effect; replacing/detaching finishes it and removes its picture. */
    private val jobs = java.util.WeakHashMap<View, ValueAnimator>()
    fun finish(view: View) { jobs.remove(view)?.end() }
    fun run(view: View, ms: Long, frame: (Float) -> Unit, done: () -> Unit = {}, easing: android.animation.TimeInterpolator = curve) {
        finish(view)
        if (!enabled(view.context)) { frame(1f); done(); return }
        val detach = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {}
            override fun onViewDetachedFromWindow(v: View) { finish(v) }
        }
        val a = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = ms
            interpolator = easing
            addUpdateListener {
                if (!enabled(view.context) && it.animatedFraction < 1f) end() else frame(it.animatedValue as Float)
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (jobs[view] === animation) jobs.remove(view)
                    view.removeOnAttachStateChangeListener(detach)
                    frame(1f); done()
                }
            })
        }
        jobs[view] = a
        view.addOnAttachStateChangeListener(detach)
        frame(0f)
        a.start()
    }

    fun pop(view: View, badge: Boolean = false) {
        run(view, if (badge) 450 else WINDOW_MS, { f ->
            val scale = if (badge) {
                if (f < .65f) .6f + .54f * f / .65f else 1.14f - .14f * (f - .65f) / .35f
            } else 1f + .04f * (1f - kotlin.math.abs(2f * f - 1f))
            view.scaleX = scale; view.scaleY = scale
            if (badge) view.alpha = (f / .65f).coerceAtMost(1f)
        })
    }
    fun press(view: View) = run(view, 150, { f ->
        val scale = if (f < .4f) 1f - .05f * f / .4f else .95f + .05f * (f - .4f) / .6f
        view.scaleX = scale; view.scaleY = scale
    })
    fun notice(view: View, shown: Boolean) {
        val fresh = shown && view.visibility != View.VISIBLE
        if (!shown) finish(view)
        view.visibility = if (shown) View.VISIBLE else View.GONE
        if (fresh) run(view, 180, { f -> view.scaleX = f; view.scaleY = f; view.alpha = f })
    }
    fun windowStyle(ctx: Context, sheet: Boolean = false): Int = if (!enabled(ctx)) 0
        else if (sheet) R.style.MotionSheet else R.style.MotionDialog

    private fun picture(view: View): Bitmap? {
        finish(view)
        if (!enabled(view.context) || view.width <= 0 || view.height <= 0) return null
        return Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
    }
    /** Close the live layer now; only a non-interactive drawable can remain during its exit. */
    fun depart(view: View, close: () -> Unit) {
        val old = picture(view)
        val host = view.rootView as? ViewGroup
        val point = IntArray(2); val base = IntArray(2)
        view.getLocationOnScreen(point); host?.getLocationOnScreen(base)
        val left = point[0] - base[0]; val top = point[1] - base[1]
        val w = view.width; val h = view.height
        close()
        if (old == null || host == null || !host.isAttachedToWindow) { old?.recycle(); return }
        val ghost = BitmapDrawable(view.resources, old)
        host.overlay.add(ghost)
        run(host, SHEET_MS, { f ->
            val y = top + (h * f).toInt()
            ghost.setBounds(left, y, left+w, y+h)
        }, { host.overlay.remove(ghost); old.recycle() })
    }
    fun axis(view: View, forward: Boolean, change: () -> Unit) {
        val old = picture(view)
        change()
        slide(view, old, if (forward) 1 else -1, 32, AXIS_MS)
    }
    private fun slide(view: View, old: Bitmap?, sign: Int, dp: Int, ms: Long) {
        if (!enabled(view.context)) { old?.recycle(); return }
        val host = view.parent as? ViewGroup ?: run { old?.recycle(); return }
        val ghost = old?.let { BitmapDrawable(view.resources, it) }
        if (ghost != null) host.overlay.add(ghost)
        val distance = Ui.dp(view.context, dp).toFloat() * sign
        run(view, ms, { f ->
            view.translationX = distance * (1f - f); view.alpha = f
            ghost?.apply {
                val x = view.left - (distance * f).toInt()
                setBounds(x, view.top, x + view.width, view.top + view.height)
                alpha = ((1f - f) * 255).toInt()
            }
        }, { if (ghost != null) host.overlay.remove(ghost); old?.recycle() })
    }

    // Keep only the destination's tab-bar animation. Capturing the whole outgoing page
    // synchronously and cross-fading it over a newly built Activity stalls the tap and
    // draws two screen-sized layers during the slide.
    private data class TabMove(val target: Class<out Activity>, val from: BottomNav.Tab)
    private var pending: TabMove? = null
    fun prepareTab(a: Activity, from: BottomNav.Tab, to: BottomNav.Tab) {
        pending = if (enabled(a)) TabMove(Nav.target(to.dest).screen, from) else null
    }
    fun arrive(a: Activity) {
        val move = pending ?: return
        if (move.target != a.javaClass) return
        pending = null
        val root = a.window?.decorView?.findViewById<View>(android.R.id.content) ?: return
        root.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                root.viewTreeObserver.removeOnPreDrawListener(this)
                fun indicator(view: View) {
                    if (view is BottomNav.HighlightRow) view.arriveFrom(move.from)
                    if (view is ViewGroup) for (i in 0 until view.childCount) indicator(view.getChildAt(i))
                }
                indicator(root)
                return true
            }
        })
    }
    fun count(view: TextView, finalText: CharSequence, ms: Long = 650) {
        val match = Regex("[0-9]+").find(finalText) ?: return
        val n = match.value.toIntOrNull() ?: return
        run(view, ms, { f ->
            view.text = if (f == 1f) finalText else finalText.toString().replaceRange(match.range, Math.round(n * f).toString())
        }, easing = android.view.animation.DecelerateInterpolator(1.5f))
    }
}
