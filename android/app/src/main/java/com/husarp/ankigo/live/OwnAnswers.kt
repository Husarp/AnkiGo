package com.husarp.ankigo.live

/** Suppress only the expected rep, preserving other answers observed in the same poll. */
class OwnAnswers {
    private class Expected(val rep: Int, var until: Long)
    private val pending = HashMap<Long, MutableList<Expected>>()
    /**
     * [pinned]: the answer is not resolved yet (written or not, nobody knows), so the expectation does not expire however long that
     * takes; it ends by [consume], [cancel], or a later plain [expect] (which sets it back to [now] + 30 s, the saved-answer handoff).
     */
    @Synchronized fun expect(id: Long, rep: Int, now: Long, pinned: Boolean = false) {
        val until = if (pinned) Long.MAX_VALUE else now + 30_000
        val list = pending.getOrPut(id) { arrayListOf() }
        val old = list.firstOrNull { it.rep == rep }
        if (old == null) list += Expected(rep, until) else old.until = until // a write that is tried again keeps its expectation alive
    }
    @Synchronized fun consume(id: Long, from: Int, to: Int, now: Long): Int {
        val list = pending[id] ?: return 0
        list.removeAll { it.until < now }
        val matched = list.filter { it.rep in (from + 1)..to }
        list.removeAll(matched.toSet())
        if (list.isEmpty()) pending.remove(id)
        return matched.size
    }
    @Synchronized fun cancel(id: Long, rep: Int) { pending[id]?.removeAll { it.rep == rep } }
}
