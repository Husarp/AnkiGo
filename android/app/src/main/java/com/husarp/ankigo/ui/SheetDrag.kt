package com.husarp.ankigo.ui

import android.animation.ValueAnimator
import android.app.Dialog
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.AbsListView
import android.widget.LinearLayout
import android.widget.ScrollView
import com.husarp.ankigo.R

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

    /** The window dim of a Dialog sheet at rest (the same .48 every bottom-sheet dialog sets). */
    const val DIM = 0.48f

    /** The window dim while the sheet is dragged: fades with the sheet like the scrim of a layer sheet does. */
    fun dim(scrimAlpha: Float): Float = DIM * scrimAlpha.coerceIn(0f, 1f)

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
class SheetDragger(private val sheet: View, private val scrim: View?, private val scroll: View?, private val handleZonePx: Int,
                   private val onClose: () -> Unit, private val onScrim: ((Float) -> Unit)? = null,
                   private val handle: View? = null) {
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
        downY = ev.rawY; startY = ev.rawY; inHandle = ev.y < handleZonePx || onVisibleHandle(ev); dragging = false
        tracker!!.addMovement(ev)
    }

    /** R9-W5-FIX: the touch starts on the handle where it is in the sheet right now (its visible part + a finger's margin), wherever the content is scrolled. */
    private fun onVisibleHandle(ev: MotionEvent): Boolean {
        val h = handle ?: return false
        if (h.visibility != View.VISIBLE) return false
        // top of [v] in the sheet's own coordinates, the scroll offsets of the parents taken off
        fun topIn(v: View): Int? {
            var t = 0; var x = v
            while (x !== sheet) { val p = x.parent as? ViewGroup ?: return null; t += x.top - p.scrollY; x = p }
            return t
        }
        var top = topIn(h) ?: return false
        var bottom = top + h.height
        var p = h.parent as? ViewGroup // every parent below the sheet clips the handle to its own box
        while (p != null && p !== sheet) {
            val pt = topIn(p) ?: return false
            top = maxOf(top, pt); bottom = minOf(bottom, pt + p.height)
            p = p.parent as? ViewGroup
        }
        if (bottom <= top) return false
        val m = (12 * sheet.resources.displayMetrics.density).toInt()
        return ev.y >= top - m && ev.y <= bottom + m
    }

    private fun canStart(ev: MotionEvent): Boolean =
        ev.rawY - downY > slop && (inHandle || scroll == null || !scroll.canScrollVertically(-1))

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
        val a = SheetDrag.scrimAlpha(offset, sheet.height.toFloat())
        scrim?.alpha = a
        onScrim?.invoke(a)
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

/**
 * R9-W5-B: one helper for every bottom sheet: the same small handle at the top (added if the sheet has none) and the same drag down to
 * close. A layer sheet (in the page's own layout) calls [layer] once; a sheet in its own Dialog window puts [dialog]'s result in as the
 * content view. The scrolling child is found by itself, so content scrolls first and the drag starts only when it is at its top.
 */
object SheetKit {
    /** The strip at the top of a sheet where a pull always starts the drag (the handle and the space around it). */
    private const val ZONE_DP = 28

    /** A handle bar: a plain View, 4 dp high, 32..44 dp wide (what every sheet in the app draws), or one tagged [HANDLE_TAG]. */
    fun isHandle(v: View): Boolean {
        if (v.tag == HANDLE_TAG) return true
        if (v.javaClass != View::class.java) return false
        val lp = v.layoutParams ?: return false
        return lp.height == Ui.dp(v.context, 4) && lp.width in Ui.dp(v.context, 32)..Ui.dp(v.context, 44)
    }

    /** The first handle bar in the top four levels of [root], or null. */
    fun findHandle(root: View, depth: Int = 0): View? {
        if (isHandle(root)) return root
        if (depth >= 4 || root !is ViewGroup) return null
        for (i in 0 until root.childCount) findHandle(root.getChildAt(i), depth + 1)?.let { return it }
        return null
    }

    /** The first vertically scrolling child (ScrollView / list) in [root], or [root] itself, or null. */
    fun findScroll(root: View): View? {
        if (root is ScrollView || root is AbsListView) return root
        if (root !is ViewGroup) return null
        for (i in 0 until root.childCount) findScroll(root.getChildAt(i))?.let { return it }
        return null
    }

    private fun inside(v: View, ancestor: View): Boolean {
        var p: View? = v
        while (p != null) { if (p === ancestor) return true; p = p.parent as? View }
        return false
    }

    /** Adds the standard handle at the top of [sheet] unless it has one. Only a vertical LinearLayout can take it; returns whether the sheet has a handle now. */
    fun ensureHandle(sheet: View): Boolean {
        if (findHandle(sheet) != null) return true
        if (sheet !is LinearLayout || sheet.orientation != LinearLayout.VERTICAL) return false
        val ctx = sheet.context
        sheet.addView(newHandle(ctx), 0, LinearLayout.LayoutParams(Ui.dp(ctx, 36), Ui.dp(ctx, 4)).also { it.gravity = Gravity.CENTER_HORIZONTAL; it.topMargin = Ui.dp(ctx, 4); it.bottomMargin = Ui.dp(ctx, 8) })
        return true
    }

    /** The handle bar every sheet shows (the look of the achievement sheet's). */
    fun newHandle(ctx: Context): View = View(ctx).apply { background = Ui.rounded(Ui.c(ctx, R.color.ag_ov), 2, ctx); tag = HANDLE_TAG }

    /** The pull-always strip: 0 when the handle sits inside the scrolling child (then the "content at its top" rule covers it), else [ZONE_DP]. */
    private fun zone(ctx: Context, handle: View?, scroll: View?): Int =
        if (handle != null && scroll != null && inside(handle, scroll)) 0 else Ui.dp(ctx, ZONE_DP)

    /** A sheet inside the page's layout ([scrim] = the dark layer behind it, a tap on it is the caller's): handle ensured, drag wired, [onClose] runs when it is dragged away. */
    fun layer(sheet: DragSheet, scrim: View, onClose: () -> Unit) {
        ensureHandle(sheet)
        val scroll = findScroll(sheet)
        sheet.dragger = SheetDragger(sheet, scrim, scroll, zone(sheet.context, findHandle(sheet), scroll), onClose, handle = findHandle(sheet))
    }

    /**
     * The content view for [dialog]'s window (`dialog.setContentView(SheetKit.dialog(dialog, content))`): [content] with a handle (added if
     * missing) and the drag. The window's own content view follows the finger; the window dim fades with it; dragged away it closes through
     * `dialog.cancel()` (= the same as a tap outside: the cancel and the dismiss listeners both run), without the window's exit animation.
     */
    fun dialog(dialog: Dialog, content: View): View = host(dialog.context, content,
        { dialog.window?.setWindowAnimations(0); dialog.cancel() },
        { dim -> dialog.window?.setDimAmount(dim) })

    /** [dialog] without the Dialog (the view part): [close] runs when the sheet is dragged away, [setDim] gets the new window dim while it moves. */
    fun host(ctx: Context, content: View, close: () -> Unit, setDim: (Float) -> Unit): DragSheet {
        val host = DragSheet(ctx).apply { orientation = LinearLayout.VERTICAL }
        if (!ensureHandle(content)) { // a content that cannot take the handle: it goes above, on a card-coloured top
            host.background = GradientDrawable().apply { val r = Ui.dp(ctx, 22f); setColor(Ui.c(ctx, R.color.ag_card)); cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f) }
            host.addView(newHandle(ctx), LinearLayout.LayoutParams(Ui.dp(ctx, 36), Ui.dp(ctx, 4)).also { it.gravity = Gravity.CENTER_HORIZONTAL; it.topMargin = Ui.dp(ctx, 8); it.bottomMargin = Ui.dp(ctx, 4) })
        }
        host.addView(content, LinearLayout.LayoutParams(-1, -2))
        val scroll = findScroll(content)
        host.dragger = SheetDragger(host, null, scroll, zone(ctx, findHandle(host), scroll), close, { a -> setDim(SheetDrag.dim(a)) }, findHandle(host))
        return host
    }

    const val HANDLE_TAG = "sheetHandle"
}
