package com.husarp.ankigo.live

import com.husarp.ankigo.CardState
import com.husarp.ankigo.GUESS
import com.husarp.ankigo.Steps
import com.husarp.ankigo.inferButton
import org.json.JSONArray
import org.json.JSONObject

// Pure Kotlin (no android.*): snapshot codec and the catch-up rules, unit-tested on the JVM.

/** Card map -> JSON array of objects (nullable fields are left out). */
fun encode(map: Map<Long, CardState>): String {
    val arr = JSONArray()
    for (c in map.values) {
        arr.put(
            JSONObject().apply {
                put("id", c.id); put("nid", c.nid); put("did", c.did); put("odid", c.odid)
                put("reps", c.reps); put("lapses", c.lapses); put("type", c.type); put("queue", c.queue); put("due", c.due)
                put("ivl", c.ivl); put("factor", c.factor); put("left", c.left)
                c.stab?.let { put("stab", it) }
                c.diff?.let { put("diff", it) }
                c.lastRev?.let { put("lastRev", it) }
            },
        )
    }
    return arr.toString()
}

/** Inverse of [encode]. Throws on malformed JSON; callers treat that as "no snapshot". */
fun decode(json: String): Map<Long, CardState> {
    val arr = JSONArray(json)
    val out = LinkedHashMap<Long, CardState>()
    for (i in 0 until arr.length()) {
        val o = arr.getJSONObject(i)
        val c = CardState(
            o.getLong("id"), o.getLong("nid"), o.getLong("did"), o.getLong("odid"),
            o.getInt("reps"), o.getInt("lapses"), o.getInt("type"), o.getInt("queue"), o.getLong("due"),
            o.getInt("ivl"), o.getInt("factor"), o.getInt("left"),
            if (o.has("stab") && !o.isNull("stab")) o.getDouble("stab") else null,
            if (o.has("diff") && !o.isNull("diff")) o.getDouble("diff") else null,
            if (o.has("lastRev") && !o.isNull("lastRev")) o.getLong("lastRev") else null,
        )
        out[c.id] = c
    }
    return out
}

/**
 * Answers made while AnkiGo wasn't watching, for every card in [ratedToday] that [baseline] knows (in baseline order).
 * - [restored] knows the card and reps grew by d: d answers (d == 1: the button inferred from the old/new state;
 *   d > 1: all button 0, nothing reliable to infer from). Unchanged or lower reps (undo): nothing.
 * - [restored] doesn't know the card (or is null = no snapshot for today): one answer; reps == 1 means the card was new
 *   before, so infer from [CardState.fresh]; otherwise button 0.
 * All results have catchUp = true.
 */
fun catchUp(
    restored: Map<Long, CardState>?,
    baseline: List<CardState>,
    ratedToday: Set<Long>,
    steps: (CardState) -> Steps?,
    nowSec: Long,
): List<LiveDetector.Event.Answer> {
    val out = ArrayList<LiveDetector.Event.Answer>()
    for (n in baseline) {
        if (n.id !in ratedToday) continue
        val old = restored?.get(n.id)
        val d = if (old == null) 1 else n.reps - old.reps
        if (d <= 0) continue
        var button = 0
        var conf = GUESS
        if (d == 1) {
            val p = old ?: if (n.reps == 1) CardState.fresh(n) else null
            if (p != null) inferButton(p, n, steps(n), nowSec).let { button = it.button; conf = it.conf }
        }
        out += LiveDetector.Event.Answer(n.id, button, conf, true)
        repeat(d - 1) { out += LiveDetector.Event.Answer(n.id, 0, GUESS, true) }
    }
    return out
}

/**
 * [snapshot] plus the own answers [rows] whose game step has not run yet (each card as it is after the answer), for a
 * detector that starts meanwhile: its baseline then counts nothing for them, the absorb still waiting counts each once.
 * No or unreadable snapshot = empty. A card the snapshot already has at the same or a later rep is left alone.
 */
fun withPendingOwn(snapshot: String?, rows: List<CardState>): String? {
    if (rows.isEmpty()) return snapshot
    val map = LinkedHashMap(snapshot?.let { runCatching { decode(it) }.getOrNull() } ?: emptyMap())
    for (r in rows) if ((map[r.id]?.reps ?: -1) < r.reps) map[r.id] = r
    return encode(map)
}

/**
 * [snapshot] as it must be saved while the own answers [rows] (each card as it is after the answer) are not counted by
 * the game yet: those cards are taken back by one rep per row the snapshot already contains (a card that was new
 * before leaves the map). A restart before the game step then catches each up once; rows the snapshot has not reached
 * yet change nothing. Unreadable snapshot: returned as it is.
 */
fun withoutPendingOwn(snapshot: String, rows: List<CardState>): String {
    if (rows.isEmpty()) return snapshot
    val map = try { LinkedHashMap(decode(snapshot)) } catch (e: Exception) { return snapshot }
    for ((id, mine) in rows.groupBy { it.id }) {
        val e = map[id] ?: continue
        val back = e.reps - mine.count { it.reps <= e.reps }
        if (back == e.reps) continue
        if (back <= 0) map.remove(id) else map[id] = e.copy(reps = back)
    }
    return encode(map)
}
