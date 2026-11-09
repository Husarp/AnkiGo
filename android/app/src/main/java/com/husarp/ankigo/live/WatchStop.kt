package com.husarp.ankigo.live

/** Keep one watch across short Activity/tab handoffs, without a new provider baseline each time. Main thread. */
class WatchStop(private val post: (Runnable, Long) -> Unit, private val remove: (Runnable) -> Unit, stop: () -> Unit) {
    private var pending = false
    private val task = Runnable { if (pending) { pending = false; stop() } }

    fun schedule() {
        if (pending) return
        pending = true
        post(task, 1_000)
    }

    fun cancel() { pending = false; remove(task) }
}
