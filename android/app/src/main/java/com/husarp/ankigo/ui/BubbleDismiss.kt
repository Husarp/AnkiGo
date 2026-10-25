package com.husarp.ankigo.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.FrameLayout
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import java.util.WeakHashMap

/**
 * R19-K (PLAN 287): a small info bubble in the arena (the status note, the conditions list) closes on a tap ANYWHERE else - the arena, a
 * card, the action bar, the rest of the screen - and on Back. The tap still does its own job (it is only watched, never taken); Back
 * only closes the bubble.
 *
 * A bubble [arm]s itself while it is open and [disarm]s when it closes. The watching sits in the window of the screen the view is on: its
 * callback is wrapped once (a proxy that forwards every call, so no method of any Android version is missed) and tells the armed
 * bubbles about each first touch of a gesture ([Hub.touch]) and about Back ([Hub.back]). A view with no activity window (the floating
 * battle window, a test) uses one transparent, non-consuming touch watcher in its root view.
 */
internal object BubbleDismiss {
    /**
     * What an open bubble wants to hear. [onTouch] runs for every first touch of a gesture (before the touched view sees it) with its x and y
     * in the window's coordinates (a touch on the bubble itself must not close it); [onBack] when Back is pressed.
     */
    class Armed(val onTouch: (Float, Float) -> Unit, val onBack: () -> Unit)

    /** The pure part: the bubbles waiting for a tap or Back in one window. */
    class Hub {
        private val armed = ArrayList<Armed>()
        val isArmed: Boolean get() = armed.isNotEmpty()

        fun add(a: Armed) { if (a !in armed) armed += a }
        fun remove(a: Armed) { armed -= a }

        /** A touch began somewhere in the window, at ([x], [y]). */
        fun touch(x: Float = 0f, y: Float = 0f) { armed.toList().forEach { it.onTouch(x, y) } }

        /** Back was pressed: true = a bubble took it (it closes; the screen's own Back does nothing). */
        fun back(): Boolean {
            val list = armed.toList()
            if (list.isEmpty()) return false
            list.forEach { it.onBack() }
            return true
        }
    }

    private val hubs = WeakHashMap<Window, Hub>()
    private val originals = WeakHashMap<Window, Window.Callback>()
    private val overlays = WeakHashMap<ViewGroup, Pair<Hub, View>>()

    private fun activityOf(ctx: Context): Activity? {
        var c: Context? = ctx
        while (c is ContextWrapper) { if (c is Activity) return c; c = c.baseContext }
        return null
    }

    /** Starts one shared watch in the activity window, or in the floating root when there is no activity window. */
    fun arm(view: View, a: Armed) {
        val window = activityOf(view.context)?.window
        if (window == null) {
            val root = view.rootView as? FrameLayout ?: return
            val pair = overlays.getOrPut(root) {
                val hub = Hub()
                val watcher = object : View(root.context) {
                    override fun dispatchTouchEvent(e: MotionEvent): Boolean {
                        if (e.actionMasked == MotionEvent.ACTION_DOWN) {
                            val at = IntArray(2).also { getLocationInWindow(it) }
                            hub.touch(e.x + at[0], e.y + at[1])
                        }
                        return false
                    }
                }.apply {
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    elevation = Ui.dp(root.context, 1000f)
                }
                root.addView(watcher, FrameLayout.LayoutParams(-1, -1).also {
                    it.leftMargin = -root.paddingLeft; it.rightMargin = -root.paddingRight
                    it.topMargin = -root.paddingTop; it.bottomMargin = -root.paddingBottom
                })
                hub to watcher
            }
            pair.first.add(a)
            pair.second.bringToFront()
            return
        }
        hubs.getOrPut(window) { Hub() }.add(a)
        install(window)
    }

    fun disarm(view: View, a: Armed) {
        val window = activityOf(view.context)?.window
        if (window == null) {
            val root = view.rootView as? FrameLayout ?: return
            val pair = overlays[root] ?: return
            pair.first.remove(a)
            if (!pair.first.isArmed) { overlays.remove(root); root.removeView(pair.second) }
            return
        }
        val hub = hubs[window] ?: return
        hub.remove(a)
        if (!hub.isArmed) {
            val original = originals.remove(window)
            if (window.callback is Watching && original != null) window.callback = original
            hubs.remove(window)
        }
    }

    /** True while the callback of [window] is ours (a proxy tagged by [Watching]). */
    private fun install(window: Window) {
        val hub = hubs[window] ?: return
        val old = window.callback ?: return
        if (old is Watching) return
        originals[window] = old
        val handler = InvocationHandler { _, method, args ->
            val a = args ?: emptyArray()
            when (method.name) {
                "dispatchTouchEvent" -> (a[0] as MotionEvent).let { e -> if (e.actionMasked == MotionEvent.ACTION_DOWN && hub.isArmed) hub.touch(e.x, e.y) }
                "dispatchKeyEvent" -> {
                    val e = a[0] as KeyEvent
                    if (e.keyCode == KeyEvent.KEYCODE_BACK && hub.isArmed) {
                        if (e.action == KeyEvent.ACTION_UP && !e.isCanceled) hub.back()
                        return@InvocationHandler true   // Back only closes the bubble (both halves of the key are taken)
                    }
                }
                "hashCode" -> return@InvocationHandler System.identityHashCode(old)
                "equals" -> return@InvocationHandler a[0] === old
            }
            try { method.invoke(old, *a) } catch (t: java.lang.reflect.InvocationTargetException) { throw t.targetException }
        }
        val proxy = Proxy.newProxyInstance(BubbleDismiss::class.java.classLoader, arrayOf(Window.Callback::class.java, Watching::class.java), handler)
        window.callback = proxy as Window.Callback
    }

    /** Marks a callback that already carries the watching. */
    interface Watching
}
