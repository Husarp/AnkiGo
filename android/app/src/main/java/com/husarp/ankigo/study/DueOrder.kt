package com.husarp.ankigo.study

/** Due searches are hints for recovery, never authority to reorder scheduler cards. */
object DueOrder {
    val COLS = arrayOf("note_id", "ord", "queue", "due")

    fun query(deckName: String): String = PredictRule.learnQuery(deckName).removeSuffix("is:learn") + "is:due -is:new"

    // Keep the provider's order within a group; intraday due values are timestamps,
    // whereas review/interday due values are day numbers and cannot be compared to them.
    fun first(rows: List<Map<String, String?>>): Map<String, String?>? {
        val valid = rows.filter { it["note_id"]?.toLongOrNull() != null && it["ord"]?.toIntOrNull() != null && it["queue"] in setOf("1", "2", "3", "4") }
        return valid.firstOrNull { it["queue"] in setOf("1", "3", "4") } ?: valid.firstOrNull()
    }

    /** Search results can detect a stale empty queue, but must never override the scheduler's order. */
    fun <T> prioritize(scheduled: List<T>, due: List<Map<String, String?>>, expand: () -> List<T>, key: (T) -> StudyLoop.Key): List<T> {
        if (scheduled.isNotEmpty() || first(due) == null) return scheduled
        return expand()
    }
}
