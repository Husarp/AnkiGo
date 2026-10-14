package com.husarp.ankigo.study

import android.os.Handler
import android.os.SystemClock

/** One auto-advance countdown on the main thread: calls [onStep] with the part left (1 down to 0; -1 = nothing to show) and [onDone] once at zero. */
class AutoAdvance(private val handler: Handler, private val stepMs: () -> Long, private val onStep: (Float) -> Unit, private val onDone: () -> Unit) {
    var running = false; private set
    /** Which side (card + front/back) the running countdown belongs to. */
    var side = 0; private set
    private var startedAt = 0L
    private var total = 0L
    private val tick = object : Runnable {
        override fun run() {
            val f = StudyTiming.left(total, startedAt, SystemClock.elapsedRealtime())
            if (f <= 0f) { running = false; onStep(0f); onDone() } else { onStep(f); handler.postDelayed(this, stepMs()) }
        }
    }
    fun start(side: Int, totalMs: Long) {
        handler.removeCallbacks(tick)
        this.side = side; total = totalMs; startedAt = SystemClock.elapsedRealtime(); running = true
        tick.run()
    }
    fun stop() { handler.removeCallbacks(tick); if (running) { running = false }; onStep(-1f) }
}
