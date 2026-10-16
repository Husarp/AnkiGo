package com.husarp.ankigo.study

/**
 * Own answers whose game step has not run yet. The next card no longer waits for the game step, but the end-of-deck
 * summary must show the last answer's rewards, so it runs through [whenIdle]. Main thread only.
 */
class PendingAnswers {
    /** One answer in flight. [finish] may be called any number of times (the real callback and the safety timeout): only the first counts. */
    inner class Ticket internal constructor() {
        private var open = true
        fun finish() {
            if (!open) return
            open = false
            n--
            if (n == 0) release()
        }
    }

    private var n = 0
    private var waiting: (() -> Unit)? = null

    val count get() = n

    fun start(): Ticket { n++; return Ticket() }

    /** Runs [action] now when no answer is in flight, else as soon as the last one finishes. A newer waiting action replaces an older one (its screen is gone). */
    fun whenIdle(action: () -> Unit) { if (n == 0) action() else waiting = action }

    private fun release() { val w = waiting ?: return; waiting = null; w() }

    companion object {
        /** The detector never answers when it was stopped for good; the summary must not wait for ever. */
        const val MAX_WAIT_MS = 20_000L
    }
}

/**
 * How long each part of one answer took, for the one STUDY line in Diagnostics (so the real split of the wait after an
 * answer is visible on the phone). Parts arrive on their own schedule (the next card loads while the game step runs);
 * [put] says when the line is complete. Main thread only.
 */
class AnswerTiming(val tapAt: Long) {
    private val ms = LinkedHashMap<String, Long>()
    private var logged = false
    var note = ""

    /** Records [part]; true exactly once, when both the game step and the shown next card are in. */
    fun put(part: String, value: Long): Boolean {
        ms[part] = value
        if (logged || GAME !in ms || SHOWN !in ms) return false
        logged = true
        return true
    }

    fun line(): String {
        fun v(part: String) = ms[part]?.let { "$it ms" } ?: "-"
        return "answer: save+verify ${v(WRITE)}, detector wait ${v(ABSORB)}, game step ${v(GAME)}, next card ${v(NEXT)} " +
            "(deck counts ${v(COUNTS)}, deck list ${v(LIST)}), tap to next card shown ${v(SHOWN)}${if (note.isEmpty()) "" else "; $note"}"
    }

    companion object {
        const val WRITE = "write"; const val ABSORB = "absorb"; const val GAME = "game"; const val NEXT = "next"
        const val COUNTS = "counts"; const val LIST = "list"; const val SHOWN = "shown"
    }
}

/**
 * Deck names by id (a card of a sub-deck shows its own deck's name). The list is read once and again only for a deck
 * that was not in it (a deck made meanwhile), once per unknown id. Not thread-safe: use it from one thread.
 */
class DeckNames(private val read: () -> Map<Long, String>) {
    private var names: Map<Long, String>? = null
    private val missed = HashSet<Long>()

    fun name(id: Long): String? {
        names?.get(id)?.let { return it }
        if (!missed.add(id)) return null // looked for this deck before
        names = read()
        return names?.get(id)
    }

    /** A deck list was read anyway: keep it. */
    fun remember(all: Map<Long, String>) { names = all }
}
