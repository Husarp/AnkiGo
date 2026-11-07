package com.husarp.ankigo.sound

import android.graphics.Rect
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.Window
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy

/** Default clicks for windows, including custom dialogs. Dedicated UI sounds take priority, even when muted. */
object ButtonSounds {
    interface Watching

    fun install(window: Window) {
        val old = window.callback ?: return
        if (old is Watching) return
        val gesture = Gesture()
        window.callback = Proxy.newProxyInstance(ButtonSounds::class.java.classLoader,
            arrayOf(Window.Callback::class.java, Watching::class.java)) { _, method, args ->
            val a = args ?: emptyArray()
            val root = window.decorView
            val serial = GameSound.uiSerial
            val touch = if (method.name == "dispatchTouchEvent") a[0] as MotionEvent else null
            if (touch != null) gesture.touch(root, touch)
            val focus = root.findFocus()
            when (method.name) {
                "hashCode" -> return@newProxyInstance System.identityHashCode(old)
                "equals" -> return@newProxyInstance a[0] === old
            }
            val result = try { method.invoke(old, *a) } catch (t: InvocationTargetException) { throw t.targetException }
            if (touch?.actionMasked == MotionEvent.ACTION_UP) gesture.completed(serial)
            if (method.name == "dispatchKeyEvent") {
                val e = a[0] as KeyEvent
                if (e.action == KeyEvent.ACTION_UP && !e.isCanceled && e.keyCode in listOf(KeyEvent.KEYCODE_ENTER,
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_SPACE)) {
                    focus?.takeIf { it.isClickable && it.isEnabled }?.let { fallback(it, serial) }
                }
            }
            result
        } as Window.Callback
    }

    fun install(popup: android.widget.PopupWindow) {
        val gesture = Gesture()
        popup.setTouchInterceptor { _, event ->
            val serial = GameSound.uiSerial
            popup.contentView?.let { gesture.touch(it, event) }
            if (event.actionMasked == MotionEvent.ACTION_UP) {
                // The interceptor runs before the popup dispatches its queued click.
                android.os.Handler(android.os.Looper.getMainLooper()).post { gesture.completed(serial) }
            }
            false
        }
    }

    fun click(view: View, action: () -> Boolean): Boolean {
        val serial = GameSound.uiSerial
        val handled = action()
        if (handled && GameSound.uiSerial == serial) GameSound.ui(view.context, SoundId.DEFAULT_TAP)
        return handled
    }

    private fun fallback(view: View, serial: Long) {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            if (GameSound.uiSerial == serial) GameSound.ui(view.context, SoundId.DEFAULT_TAP)
        }
    }

    class Gesture {
        private var target: View? = null
        private var pending: View? = null
        private var x = 0f
        private var y = 0f
        fun completed(serial: Long) {
            pending?.let { fallback(it, serial) }
            pending = null
        }
        fun touch(root: View, e: MotionEvent) {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    pending = null
                    target = hit(root, e.rawX.toInt(), e.rawY.toInt())
                    x = e.rawX; y = e.rawY
                }
                MotionEvent.ACTION_MOVE -> {
                    val slop = ViewConfiguration.get(root.context).scaledTouchSlop
                    if (kotlin.math.abs(e.rawX - x) > slop || kotlin.math.abs(e.rawY - y) > slop) target = null
                }
                MotionEvent.ACTION_CANCEL -> target = null
                MotionEvent.ACTION_UP -> {
                    val view = target
                    target = null
                    if (view != null && view === hit(root, e.rawX.toInt(), e.rawY.toInt())) pending = view
                }
            }
        }
        private fun hit(view: View, x: Int, y: Int): View? {
            if (!view.isShown || !view.isEnabled) return null
            val rect = Rect()
            if (!view.getGlobalVisibleRect(rect)) return null
            val origin = IntArray(2).also { view.rootView.getLocationOnScreen(it) }
            rect.offset(origin[0], origin[1])
            if (!rect.contains(x, y)) return null
            if (view is ViewGroup) for (i in view.childCount - 1 downTo 0) hit(view.getChildAt(i), x, y)?.let { return it }
            return view.takeIf { it.isClickable }
        }
    }
}
