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
 * answer is visible on the phone). Parts arrive on their own schedule (the answer is written in the background while the
 * next card is already on screen; the page loads and the deck counts are read after the card is shown); [put] says when the
 * line is complete: when every part in [needs] is in. A negative value means "did not happen" and shows as "-". The line
 * is logged once, when complete, or with what it has when the screen goes ([flush]). Main thread only.
 */
class AnswerTiming(val tapAt: Long, private val needs: Set<String> = setOf(GAME, SHOWN)) {
    private val ms = LinkedHashMap<String, Long>()
    private var logged = false
    var note = ""

    /** The detector's poll count at the tap; [polls] = how many polls ran until the next card showed (-1: unknown). */
    var pollsAtTap = 0
    var polls = -1

    /** How many answers waited to be written when this one was tapped (-1: not known). */
    var queueAtTap = -1

    /** How many tries the ANSWER call needed (the line shows it when more than one). */
    var attempts = 1

    /** The next card: [HIT] the predicted card was the real one, [MISS] it was swapped away, [NONE] nothing was predicted. "" = not known. */
    var next = ""

    /** How many times the load of the next card asked AnkiDroid again (-1: not known). */
    var loadRetries = -1
    private var mainWait = -1L

    /** The longest time an io result waited for the main thread (posted -> running) during this answer. */
    fun noteMainWait(waitMs: Long) { if (waitMs > mainWait) mainWait = waitMs }

    fun has(part: String) = part in ms

    /** Records [part]; true exactly once, when every part in [needs] is in. */
    fun put(part: String, value: Long): Boolean {
        ms[part] = value
        if (logged || !needs.all { it in ms }) return false
        logged = true
        return true
    }

    /** True exactly once, when the line was not logged yet: the screen goes, so it is logged with what it has. */
    fun flush(): Boolean {
        if (logged) return false
        logged = true
        return true
    }

    fun line(): String {
        fun v(part: String) = ms[part]?.takeIf { it >= 0 }?.let { "$it ms" } ?: "-"
        val after = listOfNotNull(
            if (PAGE in ms) "page loaded ${v(PAGE)}" else null,
            if (COUNTS in ms) "deck counts read after it ${v(COUNTS)}" else null,
            if (loadRetries >= 0) "load retries $loadRetries" else null,
            if (mainWait >= 0) "main wait $mainWait ms" else null,
            if (polls >= 0) "detector polls meanwhile $polls" else null,
        )
        val head = if (ANSWER in ms) "ANSWER ${v(ANSWER)}${if (attempts > 1) " ($attempts tries)" else ""}, " else ""
        val queue = if (queueAtTap >= 0) "write queue $queueAtTap at the tap, " else ""
        val prediction = if (next.isNotEmpty()) ", next card: $next" + (if (CONFIRMED in ms) ", confirmed after ${v(CONFIRMED)}" else "") else ""
        return "answer: ${head}save+verify ${v(WRITE)}, ${queue}detector wait ${v(ABSORB)}, game step ${v(GAME)}, next card ${v(NEXT)} " +
            "(deck list ${v(LIST)})$prediction, tap to next card shown ${v(SHOWN)}" +
            (if (after.isEmpty()) "" else ", " + after.joinToString(", ")) + (if (note.isEmpty()) "" else "; $note")
    }

    companion object {
        const val WRITE = "write"; const val ABSORB = "absorb"; const val GAME = "game"; const val NEXT = "next"
        const val COUNTS = "counts"; const val LIST = "list"; const val SHOWN = "shown"; const val PAGE = "page"

        /** The provider's ANSWER call alone (the last try); [CONFIRMED] = tap until AnkiDroid confirmed (or swapped) the next card. */
        const val ANSWER = "answer"; const val CONFIRMED = "confirmed"
        const val HIT = "predicted hit"; const val MISS = "predicted miss (swap)"; const val NONE = "none (waited)"

        /** The parts an answer of the background-write flow waits for before its line is logged. */
        val ALL_PARTS = setOf(GAME, SHOWN, PAGE, COUNTS, WRITE, CONFIRMED)
    }
}

/**
 * When the deck-tree read for the counts may start (R9 wave 6, item 3). AnkiDroid builds its whole deck tree for it, which competes
 * with the answer write for its collection: never while an answer is unresolved, at most one per [MIN_GAP_MS], plus the first card
 * of a visit (and after an answer that was not written, which made the stand-in counts wrong). The end of the deck reads the
 * deck list for its summary anyway.
 */
object CountsPolicy {
    const val MIN_GAP_MS = 30_000L

    /** [lastStartedAt] = when the last read started (null: none yet in this process), same clock as [now]. */
    fun mayStart(unresolved: Boolean, firstOfVisit: Boolean, now: Long, lastStartedAt: Long?): Boolean = when {
        unresolved -> false
        firstOfVisit || lastStartedAt == null -> true
        else -> now - lastStartedAt >= MIN_GAP_MS
    }
}

/**
 * Reading AnkiDroid's queue for the next card (R9 wave 6, item 2). The query is asked again only when it THROWS, or when it comes
 * back empty although the deck's counts still say cards are due (AnkiDroid not awake yet); never just because it is empty.
 * [maxTries] counts the first ask. Each retry is counted for the STUDY line.
 */
object QueueRead {
    class Result<R>(val items: List<R>, val retries: Int, val dueButEmpty: Boolean)

    /**
     * [query] = one ask; [dueCount] = the deck's due cards, read lazily and at most once, only after an empty answer (null = unknown);
     * [pause] runs before each retry (waits, selects the deck again). Throws the last failure when every try threw.
     */
    fun <R> read(query: () -> List<R>, dueCount: () -> Int?, pause: (retry: Int) -> Unit, maxTries: Int = 3): Result<R> {
        var tries = 0
        var due: Int? = null
        var dueRead = false
        while (true) {
            if (tries > 0) pause(tries)
            tries++
            val items = try { query() } catch (e: Exception) {
                if (tries >= maxTries) throw e
                continue
            }
            if (items.isNotEmpty()) return Result(items, tries - 1, false)
            if (!dueRead) { due = dueCount(); dueRead = true }
            val dueNow = (due ?: 0) > 0
            if (!dueNow) return Result(items, tries - 1, false)
            if (tries >= maxTries) return Result(items, tries - 1, true)
        }
    }
}

/** The texts the study screen says about an answer that is not (yet) saved in AnkiDroid. */
object AnswerNotes {
    const val RETRYING = "Your last answer isn't saved in AnkiDroid yet. AnkiGo keeps it and tries again."
    const val PARKED = "AnkiGo could not save your last answer after many tries. It still keeps it."
    const val SAVED_LATE = "Answer saved"
    const val SAVING = "Saving your last answer…"
    const val WAIT = "One moment — saving your last answer"

    /** Back while a tap on the next card was still held (its card not confirmed yet): that tap was never an answer, so say it was not counted. */
    const val HELD_LOST = "Your last tap wasn't counted: that card is still due in AnkiDroid."
    const val GIVE_UP_ASK = "AnkiGo will stop trying to save this answer. The card will come up again in AnkiDroid."

    /** Once per visit when the backup copy (journal file) of an answer could not be written: studying is not blocked, the answer is still being saved. */
    const val JOURNAL_FAILED = "AnkiGo couldn't keep a backup copy of your answer (is the phone's storage full?). It is still being saved to AnkiDroid."

    /** The toast for an answer that ended NOT written. */
    fun notWritten(reason: AnswerOutcome.Reason): String = when (reason) {
        AnswerOutcome.Reason.NOT_TOP -> "AnkiDroid showed another card first, so that answer wasn't saved. The card comes back later."
        AnswerOutcome.Reason.CHANGED -> "That card changed in AnkiDroid meanwhile, so your answer wasn't saved."
        AnswerOutcome.Reason.UNSURE -> "AnkiGo couldn't confirm your last answer. Check it in AnkiDroid."
        AnswerOutcome.Reason.DISCARDED -> "That answer wasn't saved in AnkiDroid. The card will come up again there."
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
