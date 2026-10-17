package com.husarp.ankigo.study

import com.husarp.ankigo.Anki

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
 * answer is visible on the phone). Parts arrive on their own schedule (the next card loads while the game step runs, the
 * page loads and the deck counts are read after the card is shown); [put] says when the line is complete: when every part
 * in [needs] is in. A negative value means "did not happen" and shows as "-". Main thread only.
 */
class AnswerTiming(val tapAt: Long, private val needs: Set<String> = setOf(GAME, SHOWN)) {
    private val ms = LinkedHashMap<String, Long>()
    private var logged = false
    var note = ""

    /** The detector's poll count at the tap; [polls] = how many polls ran until the next card showed (-1: unknown). */
    var pollsAtTap = 0
    var polls = -1

    /** Records [part]; true exactly once, when every part in [needs] is in. */
    fun put(part: String, value: Long): Boolean {
        ms[part] = value
        if (logged || !needs.all { it in ms }) return false
        logged = true
        return true
    }

    fun line(): String {
        fun v(part: String) = ms[part]?.takeIf { it >= 0 }?.let { "$it ms" } ?: "-"
        val after = listOfNotNull(
            if (PAGE in ms) "page loaded ${v(PAGE)}" else null,
            if (COUNTS in ms) "deck counts read after it ${v(COUNTS)}" else null,
            if (polls >= 0) "detector polls meanwhile $polls" else null,
        )
        return "answer: save+verify ${v(WRITE)}, detector wait ${v(ABSORB)}, game step ${v(GAME)}, next card ${v(NEXT)} " +
            "(deck list ${v(LIST)}), tap to next card shown ${v(SHOWN)}" +
            (if (after.isEmpty()) "" else ", " + after.joinToString(", ")) + (if (note.isEmpty()) "" else "; $note")
    }

    companion object {
        const val WRITE = "write"; const val ABSORB = "absorb"; const val GAME = "game"; const val NEXT = "next"
        const val COUNTS = "counts"; const val LIST = "list"; const val SHOWN = "shown"; const val PAGE = "page"
    }
}

/**
 * A value read once per visit of the study screen (can this AnkiDroid study / flag cards, the deck's options: reading the
 * options makes AnkiDroid build its whole deck tree, too slow for every card). [get] reads it the first time, [reset] (the
 * screen resumed: AnkiDroid may have changed meanwhile) forgets it. A failed read is not kept. One thread only.
 */
class ScreenMemo<T : Any>(private val read: () -> T) {
    private var value: T? = null
    fun get(): T = value ?: read().also { value = it }
    fun reset() { value = null }
}

/**
 * The studied deck's counts shown between two exact reads. AnkiDroid only gives them with its whole deck tree (slow with
 * a big collection), so they are read after the next card is on screen; until then the last counts, minus the answered
 * card, stand in.
 */
object QueueCounts {
    /** [d] after answering a card from [queue] (0 new, 1 and 3 learning, 2 review; anything else: unchanged), never below 0. */
    fun afterAnswer(d: Anki.Deck?, queue: Int?): Anki.Deck? = when {
        d == null -> null
        queue == 0 -> d.copy(new = (d.new - 1).coerceAtLeast(0))
        queue == 1 || queue == 3 -> d.copy(learn = (d.learn - 1).coerceAtLeast(0))
        queue == 2 -> d.copy(review = (d.review - 1).coerceAtLeast(0))
        else -> d
    }
}

/**
 * R4 fix B1: the deck counts are read on a worker of their own (AnkiDroid builds its whole deck tree for them, which used to hold up
 * answers queued behind the read). This says whether such a read may still be applied: only when it began for the card now on screen
 * ([Ticket.token] = the load that started it) and no answer was started since (a read that began before an answer may predate its
 * save, and the stand-in counts already took that answer off: applying it would count the card twice or not at all). A dropped read
 * costs nothing: the next card reads the counts again. Main thread only.
 */
class CountsGate {
    class Ticket internal constructor(val token: Int, internal val answers: Int)

    private var answers = 0

    /** An answer was started (the button was tapped). */
    fun answerStarted() { answers++ }

    /** For a read that starts now, for the load [token]. */
    fun ticket(token: Int) = Ticket(token, answers)

    /** True when a read made with [t] may be shown while the card of load [currentToken] is on screen. */
    fun accept(t: Ticket, currentToken: Int): Boolean = t.token == currentToken && t.answers == answers
}

/**
 * Deck names by id (a card of a sub-deck shows its own deck's name). The list is read once and again only for a deck
 * that was not in it (a deck made meanwhile), once per unknown id. [name] is for one thread (the io thread); [remember] may
 * also come from the counts worker (it only replaces the list).
 */
class DeckNames(private val read: () -> Map<Long, String>) {
    @Volatile private var names: Map<Long, String>? = null
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
