package com.husarp.ankigo.ui

import java.lang.ref.WeakReference

/** Extra windows are transient when returning from another app. Weak references never keep a closed screen alive. */
internal object AwayWindows {
    private val close = ArrayList<() -> Unit>()

    fun <T : Any> track(window: T, dismiss: (T) -> Unit) {
        val ref = WeakReference(window)
        close += { ref.get()?.let(dismiss); Unit }
    }

    fun dismissAll() {
        val pending = close.toList()
        close.clear()
        pending.forEach { it() }
    }
}
