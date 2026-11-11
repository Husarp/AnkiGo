package com.husarp.ankigo.study

/** One background read at a time. Answers/resumes retire old results; a learning deadline wakes an idle screen. */
class LiveRefresh<T>(
    private val now: () -> Long,
    private val schedule: (Long, () -> Unit) -> Unit,
    private val cancel: () -> Unit,
    private val blocked: () -> Boolean = { false },
    private val read: (done: (T?, Long?) -> Unit) -> Unit,
    private val show: (T) -> Unit,
) {
    private var active = false
    private var revision = 0
    private var reading = false

    fun start() { active = true; invalidate() }
    fun stop() { active = false; revision++; cancel() }

    fun invalidate() {
        revision++
        if (active && !reading) { cancel(); schedule(0, ::wake) }
    }

    private fun wake() {
        cancel()
        if (!active || reading) return
        if (blocked()) { schedule(250, ::wake); return }
        reading = true
        val ticket = revision
        read { value, nextDue ->
            reading = false
            if (active) {
                if (ticket == revision && !blocked()) {
                    if (value != null) show(value)
                    val delay = nextDue?.let { (it - now()).coerceAtLeast(1) } ?: MAX_DELAY
                    schedule(delay.coerceAtMost(MAX_DELAY), ::wake)
                } else schedule(0, ::wake)
            }
        }
    }

    companion object {
        const val MAX_DELAY = 30_000L

        /** Only intraday learning/preview due values are timestamps. Use the counts read's start time to retain deadlines crossed during IO. */
        fun nextLearning(rows: List<Map<String, String?>>, now: Long): Long? = rows.asSequence()
            .filter { it["queue"] in setOf("1", "4") }
            .mapNotNull { it["due"]?.toLongOrNull()?.takeIf { due -> due > 0 && due <= Long.MAX_VALUE / 1000 }?.times(1000) }
            .filter { it > now }.minOrNull()
    }
}
