package com.husarp.ankigo.study

/** Read-only content preparation never grants permission to show a card. Only the scheduler does. */
object SchedulerCardRead {
    class Changed : IllegalStateException("AnkiDroid is updating its study queue.")
    data class Result<R, T>(val card: Prepared<T>?, val queue: List<R>, val checks: Int)

    /** A change during HTML/media preparation is resolved before anything reaches the screen. */
    fun <R, T> confirmed(
        initial: List<R>, read: () -> List<R>, key: (R) -> StudyLoop.Key,
        prepare: (R) -> Prepared<T>, refresh: (Prepared<T>, R) -> Prepared<T>,
        valid: (Prepared<T>) -> Boolean = { true }, note: (String) -> Unit = {}, maxChecks: Int = 3,
    ): Result<R, T> {
        var queue = initial
        repeat(maxChecks) { check ->
            val first = queue.firstOrNull() ?: return Result(null, queue, check)
            val card = prepare(first)
            val fresh = read()
            val top = fresh.firstOrNull()
            val sameTop = top != null && key(top) == card.key
            val stateOk = sameTop && valid(card)
            if (top != null && stateOk) {
                note("display confirmed: ${card.key.nid}/${card.key.ord}, check ${check + 1}")
                return Result(refresh(card, top), fresh, check + 1)
            }
            note("display withheld: prepared ${card.key.nid}/${card.key.ord}, scheduler ${top?.let(key)}; state checked=$sameTop unchanged=$stateOk; check ${check + 1}")
            queue = fresh
        }
        throw Changed()
    }
}
