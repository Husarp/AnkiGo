package com.husarp.ankigo.study

/** Due searches prioritize learning/review entries already supplied by the scheduler. */
object DueOrder {
    val COLS = arrayOf("note_id", "ord", "queue", "due")

    fun query(deckName: String): String = PredictRule.learnQuery(deckName).removeSuffix("is:learn") + "is:due -is:new"

    // Keep the provider's order within a group; intraday due values are timestamps,
    // whereas review/interday due values are day numbers and cannot be compared to them.
    fun first(rows: List<Map<String, String?>>): Map<String, String?>? {
        val valid = rows.filter { it["note_id"]?.toLongOrNull() != null && it["ord"]?.toIntOrNull() != null && it["queue"] in setOf("1", "2", "3", "4") }
        return valid.firstOrNull { it["queue"] in setOf("1", "3", "4") } ?: valid.firstOrNull()
    }

    private fun intradayKeys(due: List<Map<String, String?>>): Set<StudyLoop.Key> {
        return due.filter { it["queue"] in setOf("1", "4") }.mapNotNull {
            val nid = it["note_id"]?.toLongOrNull() ?: return@mapNotNull null
            val ord = it["ord"]?.toIntOrNull() ?: return@mapNotNull null
            StudyLoop.Key(nid, ord)
        }.toSet()
    }

    /** A cached learning cutoff can leave due intraday cards after hundreds of main-queue entries. */
    fun <T> expandLearning(due: List<Map<String, String?>>, read: (Int) -> List<T>, rebuild: () -> Boolean = { false }, key: (T) -> StudyLoop.Key): List<T> {
        val learning = intradayKeys(due)
        var limit = 100
        var rebuilt = false
        while (true) {
            val cards = read(limit)
            if (cards.any { key(it) in learning } || learning.isEmpty()) return cards
            // Rebuild before scanning a large main queue. The old learn-ahead window may exclude the card entirely.
            if (!rebuilt) { rebuilt = true; if (rebuild()) { limit = 100; continue } }
            if (cards.size < limit) return cards
            if (limit >= 100_000) throw SchedulerCardRead.Changed()
            limit *= 10
        }
    }

    /** Only real scheduler entries may move ahead of new cards. Searches never supply answer metadata. */
    fun <T> prioritize(scheduled: List<T>, due: List<Map<String, String?>>, expand: () -> List<T>, key: (T) -> StudyLoop.Key): List<T> {
        val ranks = due.mapNotNull { row ->
            val nid = row["note_id"]?.toLongOrNull() ?: return@mapNotNull null
            val ord = row["ord"]?.toIntOrNull() ?: return@mapNotNull null
            val rank = when (row["queue"]) { "1", "3", "4" -> 0; "2" -> 1; else -> return@mapNotNull null }
            StudyLoop.Key(nid, ord) to rank
        }.toMap()
        if (ranks.isEmpty()) return scheduled
        val learning = intradayKeys(due)
        val best = ranks.values.minOrNull()
        val hasPreferred = if (learning.isNotEmpty()) scheduled.any { key(it) in learning }
            else scheduled.any { ranks[key(it)] == best }
        val real = if (hasPreferred) scheduled else expand()
        return real.sortedWith(compareBy<T> { if (key(it) in learning) -1 else ranks[key(it)] ?: 2 })
    }
}
