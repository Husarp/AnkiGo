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

    /** Only real scheduler entries may move ahead of new cards. Searches never supply answer metadata. */
    fun <T> prioritize(scheduled: List<T>, due: List<Map<String, String?>>, expand: () -> List<T>, key: (T) -> StudyLoop.Key): List<T> {
        val ranks = due.mapNotNull { row ->
            val nid = row["note_id"]?.toLongOrNull() ?: return@mapNotNull null
            val ord = row["ord"]?.toIntOrNull() ?: return@mapNotNull null
            val rank = when (row["queue"]) { "1", "3", "4" -> 0; "2" -> 1; else -> return@mapNotNull null }
            StudyLoop.Key(nid, ord) to rank
        }.toMap()
        if (ranks.isEmpty()) return scheduled
        val real = if (scheduled.any { key(it) in ranks }) scheduled else expand()
        return real.sortedBy { ranks[key(it)] ?: 2 }
    }
}
