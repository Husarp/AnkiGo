package com.husarp.ankigo.ui

import java.lang.ref.WeakReference

/** Explicit cleanup registry for extra windows. App return preserves them (PLAN 448).
 * Weak references never keep a closed screen alive. */
internal object AwayWindows {
    private val close = ArrayList<() -> Unit>()

    fun <T : Any> track(window: T, dismiss: (T) -> Unit) {
        when (window) {
            is android.app.Dialog -> window.window?.let { com.husarp.ankigo.sound.ButtonSounds.install(it) }
            is android.widget.PopupWindow -> com.husarp.ankigo.sound.ButtonSounds.install(window)
        }
        val ref = WeakReference(window)
        close += { ref.get()?.let(dismiss); Unit }
    }

    fun dismissAll() {
        val pending = close.toList()
        close.clear()
        pending.forEach { it() }
    }
}
