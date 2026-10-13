package com.husarp.ankigo.live

/** Suppress only the expected rep, preserving other answers observed in the same poll. */
class OwnAnswers {
    private data class Expected(val rep: Int, val until: Long)
    private val pending = HashMap<Long, MutableList<Expected>>()
    @Synchronized fun expect(id: Long, rep: Int, now: Long) {
        val list = pending.getOrPut(id) { arrayListOf() }
        if (list.none { it.rep == rep }) list += Expected(rep, now + 30_000)
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
