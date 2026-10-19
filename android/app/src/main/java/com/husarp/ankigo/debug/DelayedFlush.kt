package com.husarp.ankigo.debug

/**
 * Coalesces "please persist" requests: the first [request] schedules [action] after [delayMs]; requests that arrive
 * before it runs ride along (no second run). [schedule] runs the task later (a scheduler in the app, a fake in tests).
 */
class DelayedFlush(private val delayMs: Long, private val schedule: (Long, () -> Unit) -> Unit, private val action: () -> Unit) {
    private var pending = false
    fun request() {
        synchronized(this) { if (pending) return; pending = true }
        schedule(delayMs) { synchronized(this) { pending = false }; action() }
    }
}
