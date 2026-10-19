package com.husarp.ankigo.study

import org.json.JSONArray
import org.json.JSONObject

/**
 * One answer the user made on a CONFIRMED card, waiting to be written to AnkiDroid. [before] is the card as it was shown (the
 * whole [com.husarp.ankigo.CardState.COLS] map in memory, only [AnswerState.JOURNAL] in the journal). [madeAt] is wall clock,
 * [restored] is memory only: true when the entry came back from the journal at start.
 */
data class QueuedAnswer(
    val seq: Long, val deckId: Long, val nid: Long, val ord: Int, val cardId: Long,
    val before: Map<String, String?>, val ease: Int, val tookMs: Long, val madeAt: Long,
    val restored: Boolean = false,
    /** The card's answer-button count (2, 3 or 4): the game step needs it to turn [ease] into the game's button. */
    val buttons: Int = 4,
) {
    val key get() = StudyLoop.Key(nid, ord)
}

/** The fields that say "this is still the same card state" (the same set as [StudyLoop.answer]) and what the journal keeps. */
object AnswerState {
    val COMPARE = listOf("_id", "reps", "due", "queue", "last_review_time_secs")
    val JOURNAL = COMPARE + listOf("note_id", "deck_id")

    /** True when [a] and [b] agree on every [COMPARE] field (a missing field and a null one are the same). */
    fun same(a: Map<String, String?>, b: Map<String, String?>) = COMPARE.all { a[it] == b[it] }

    /** An entry can only be verified when its card id and rep count are readable. */
    fun usable(before: Map<String, String?>) = before["_id"]?.toLongOrNull() != null && before["reps"]?.toIntOrNull() != null
}

/** Where the journal text lives (1b: `filesDir/study-answers.json`, temp file + rename, no fsync). A missing file reads as null. */
interface JournalStore {
    fun read(): String?
    fun write(text: String)

    /** Deletes the journal (no answer is waiting any more). Needs no free space, unlike [write]; a missing file is fine. */
    fun clear()
}

/**
 * The journal of answers not yet written: `{"v":1,"answers":[…]}`, rewritten whole on every change (a handful of entries at most).
 * An unknown or corrupt file reads as "no answers" plus a reason the caller can log; it never throws. Unknown fields are ignored.
 */
object AnswerJournal {
    const val VERSION = 1

    class Decoded(val answers: List<QueuedAnswer>, val problem: String?)

    fun encode(answers: List<QueuedAnswer>): String {
        val arr = JSONArray()
        for (a in answers) {
            val before = JSONObject()
            for (k in AnswerState.JOURNAL) if (a.before.containsKey(k)) before.put(k, a.before[k] ?: JSONObject.NULL)
            arr.put(
                JSONObject().put("seq", a.seq).put("deckId", a.deckId).put("nid", a.nid).put("ord", a.ord).put("cardId", a.cardId)
                    .put("ease", a.ease).put("tookMs", a.tookMs).put("madeAt", a.madeAt).put("buttons", a.buttons).put("before", before),
            )
        }
        return JSONObject().put("v", VERSION).put("answers", arr).toString()
    }

    /** [text] null = no file yet (not a problem). Entries come back sorted by seq, `restored = true`. */
    fun decode(text: String?): Decoded {
        if (text == null) return Decoded(emptyList(), null)
        if (text.isBlank()) return Decoded(emptyList(), "the journal file is empty")
        val root = try { JSONObject(text) } catch (e: Exception) { return Decoded(emptyList(), "the journal is not readable: ${e.message}") }
        val v = root.optInt("v", -1)
        if (v != VERSION) return Decoded(emptyList(), "unknown journal version $v")
        val arr = root.optJSONArray("answers") ?: return Decoded(emptyList(), "the journal has no answers list")
        val out = ArrayList<QueuedAnswer>()
        var bad = 0
        for (i in 0 until arr.length()) {
            val a = one(arr.optJSONObject(i))
            if (a == null || out.any { it.seq == a.seq }) bad++ else out += a
        }
        out.sortBy { it.seq }
        return Decoded(out, if (bad > 0) "$bad unusable journal entries skipped" else null)
    }

    private fun one(o: JSONObject?): QueuedAnswer? {
        o ?: return null
        val b = o.optJSONObject("before") ?: return null
        val before = HashMap<String, String?>()
        for (k in AnswerState.JOURNAL) if (b.has(k)) before[k] = if (b.isNull(k)) null else b.optString(k)
        if (!AnswerState.usable(before)) return null
        val ease = o.optInt("ease", 0)
        if (ease !in 1..4 || !o.has("seq") || !o.has("deckId") || !o.has("nid") || !o.has("ord") || !o.has("cardId")) return null
        val cardId = o.optLong("cardId", 0)
        if (before["_id"]?.toLongOrNull() != cardId) return null
        return QueuedAnswer(
            o.optLong("seq"), o.optLong("deckId"), o.optLong("nid"), o.optInt("ord"), cardId, before, ease,
            o.optLong("tookMs", 0).coerceAtLeast(0), o.optLong("madeAt", 0), restored = true, buttons = o.optInt("buttons", 4).takeIf { it in 2..4 } ?: 4,
        )
    }
}
