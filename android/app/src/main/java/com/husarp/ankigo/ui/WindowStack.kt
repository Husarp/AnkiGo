package com.husarp.ankigo.ui

import android.content.Context
import java.lang.ref.WeakReference

/** Detail cards and item sheets share a stack, scoped to their originating page/overlay. */
interface StackWindow {
    val stackContext: Context
    fun closeFromStack()
    fun stackChanged(stacked: Boolean)
}

internal object WindowStack {
    private val entries = mutableListOf<WeakReference<StackWindow>>()
    private fun windows(context: Context): List<StackWindow> {
        entries.removeAll { it.get() == null }
        return entries.mapNotNull { it.get() }.filter { it.stackContext === context }
    }
    fun add(window: StackWindow) {
        if (windows(window.stackContext).none { it === window }) entries += WeakReference(window)
        update(window.stackContext)
    }
    fun remove(window: StackWindow) {
        entries.removeAll { it.get() == null || it.get() === window }
        update(window.stackContext)
    }
    private fun update(context: Context) {
        val current = windows(context)
        // Covered cards keep their geometry and scroll position while a popup is open.
        // Refresh the exposed card when it becomes the top window again.
        current.lastOrNull()?.stackChanged(current.size >= 2)
    }
    fun stacked(context: Context) = windows(context).size >= 2
    fun closeAll(context: Context) = windows(context).asReversed().forEach { it.closeFromStack() }
}
