package com.husarp.ankigo.study

/** The pretend "process dies now" error: nothing in the code under test may catch it (it is an Error, not an Exception). */
class Die : Error("the test process died")

class FakeClock { var ms = 0L }

/** An in-memory journal file that outlives a pretend process death. */
class MemStore : JournalStore {
    var text: String? = null
    var writes = 0
    var clears = 0
    var failWrites = false // a full disk: writing fails, deleting does not
    var failClears = false
    val history = ArrayList<String>()
    override fun read(): String? = text
    override fun write(text: String) {
        if (failWrites) throw java.io.IOException("disk full")
        this.text = text; writes++; history += text
    }
    override fun clear() {
        if (failClears) throw java.io.IOException("cannot delete")
        text = null; clears++
    }
}

/** What a provider call does this time. DIE_* end the pretend process before / after the call took effect. */
enum class F { NONE, SLOW, THROW_BEFORE, THROW_AFTER, SWALLOW, DIE_BEFORE, DIE_AFTER, NULL, THROW }

/**
 * A pretend AnkiDroid. Decks hold an ordered queue of cards (first = top). ANSWER only applies to the card on top of the SELECTED
 * deck's queue and only when it is the key asked for (else it is swallowed, like AnkiDroid); every applied answer goes to [revlog].
 * Faults come from [plans] (per operation: "answer", "stats", "top", "select"; consumed in order) or from [chaos].
 */
class FakeAnki(val clock: FakeClock = FakeClock()) : AnswerProvider {
    class Card(val id: Long, val nid: Long, val ord: Int, val deck: Long) {
        var reps = 3
        var due = 100L + id
        var queue = 2
        var last: Long? = 1_000L + id
        val key get() = StudyLoop.Key(nid, ord)
    }

    class Rev(val cardId: Long, val ease: Int, val repsAfter: Int, val at: Long, val external: Boolean, val ms: Long)

    val cards = LinkedHashMap<Long, Card>()
    val queues = HashMap<Long, ArrayList<Card>>()
    var selected = 0L
    val revlog = ArrayList<Rev>()
    private var ids = 0L

    // scheduling behaviour
    var againAt: Int? = null // an Again card comes back at this position of the queue (0 = first)
    var buryOnAnswer = false // answering a card buries its siblings (same note)
    var afterApply: ((Card, Int) -> Unit)? = null

    // AnkiDroid quirks
    var answerAnyTop = false // worst case: ANSWER applies to whatever is on top of the selected deck, whatever key was sent
    var topNeedsSelection = false // AnkiDroid #18270: the schedule query shows the SELECTED deck's queue when another deck is asked for

    // faults
    val plans = HashMap<String, java.util.ArrayDeque<F>>()
    var chaos: ((String) -> F)? = null
    var slowMs = 900L
    var duringWrite: (() -> Unit)? = null // runs inside every ANSWER call (the user acts while the write runs)
    val calls = HashMap<String, Int>()
    var notTopWrites = 0 // ANSWER calls whose key was not the top of the selected deck: the engine must never make one
    var swallowed = 0

    fun plan(op: String, vararg f: F) { plans.getOrPut(op) { java.util.ArrayDeque() }.addAll(f.toList()) }
    fun count(op: String) = calls[op] ?: 0

    fun add(deck: Long, nid: Long, ord: Int = 0, atTop: Boolean = false): Card {
        val c = Card(++ids, nid, ord, deck)
        cards[c.id] = c
        val q = queues.getOrPut(deck) { ArrayList() }
        if (atTop) q.add(0, c) else q += c
        return c
    }

    fun card(key: StudyLoop.Key) = cards.values.firstOrNull { it.key == key }
    fun queueOf(deck: Long): List<Card> = queues[deck].orEmpty()

    /** Pretend the user answered [c] in AnkiDroid itself (not through the queue). */
    fun externalAnswer(c: Card, ease: Int) { apply(c, ease, external = true, ms = -1) }

    /** A learning card came due: [c] is now first. */
    fun moveToTop(c: Card) { queues[c.deck]?.let { it.remove(c); it.add(0, c) } }

    /** The card was edited / rescheduled elsewhere. */
    fun reschedule(c: Card) { c.due += 1; c.queue = 3 }

    fun delete(c: Card) { cards.remove(c.id); queues[c.deck]?.remove(c) }

    private fun fault(op: String): F {
        calls[op] = (calls[op] ?: 0) + 1
        return plans[op]?.pollFirst() ?: chaos?.invoke(op) ?: F.NONE
    }

    private fun apply(c: Card, ease: Int, external: Boolean, ms: Long) {
        val dq = queues[c.deck] ?: ArrayList()
        dq.remove(c)
        c.reps++
        c.due += 7L * ease
        c.queue = if (ease == 1) 1 else 2
        c.last = (c.last ?: 1_000L) + 60 + ease
        revlog += Rev(c.id, ease, c.reps, clock.ms, external, ms)
        againAt?.let { if (ease == 1) dq.add(minOf(it, dq.size), c) }
        if (buryOnAnswer) {
            val sib = dq.filter { it.nid == c.nid && it.id != c.id }
            dq.removeAll(sib.toSet())
            sib.forEach { it.queue = -3 }
        }
        afterApply?.invoke(c, ease)
    }

    private fun <R> op(name: String, effect: () -> R): R {
        val f = fault(name)
        when (f) { F.THROW_BEFORE, F.THROW -> throw IllegalStateException("$name failed"); F.DIE_BEFORE -> throw Die(); else -> {} }
        clock.ms += if (f == F.SLOW) slowMs else 5
        val r = effect()
        when (f) { F.THROW_AFTER -> throw IllegalStateException("$name failed after it was done"); F.DIE_AFTER -> throw Die(); else -> {} }
        return r
    }

    override fun select(deckId: Long) = op("select") { selected = deckId }

    override fun top(deckId: Long, limit: Int): List<StudyLoop.Key> = op("top") {
        val dq = if (topNeedsSelection && selected != deckId) queues[selected] else queues[deckId]
        dq.orEmpty().take(limit).map { it.key }
    }

    override fun stats(key: StudyLoop.Key): Map<String, String?>? {
        val f = fault("stats")
        when (f) { F.THROW_BEFORE, F.THROW -> throw IllegalStateException("stats failed"); F.DIE_BEFORE -> throw Die(); F.NULL -> return null; else -> {} }
        clock.ms += if (f == F.SLOW) slowMs else 5
        val c = card(key) ?: return null
        val row = mapOf(
            "_id" to c.id.toString(), "note_id" to c.nid.toString(), "deck_id" to c.deck.toString(), "reps" to c.reps.toString(),
            "due" to c.due.toString(), "queue" to c.queue.toString(), "last_review_time_secs" to c.last?.toString(),
        )
        if (f == F.DIE_AFTER) throw Die()
        return row
    }

    override fun answer(key: StudyLoop.Key, ease: Int, ms: Long) {
        val f = fault("answer")
        when (f) { F.THROW_BEFORE, F.THROW -> throw IllegalStateException("answer failed"); F.DIE_BEFORE -> throw Die(); else -> {} }
        clock.ms += if (f == F.SLOW) slowMs else 20
        duringWrite?.invoke()
        val top = queues[selected]?.firstOrNull()
        if (top == null || top.key != key) notTopWrites++
        val applies = f != F.SWALLOW && top != null && (answerAnyTop || top.key == key)
        if (applies) apply(top!!, ease, external = false, ms = ms) else swallowed++
        when (f) { F.THROW_AFTER -> throw IllegalStateException("answer failed after it was applied"); F.DIE_AFTER -> throw Die(); else -> {} }
    }
}
