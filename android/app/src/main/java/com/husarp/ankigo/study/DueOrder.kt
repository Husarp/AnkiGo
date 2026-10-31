package com.husarp.ankigo.study

/** Fresh due learning/review cards take precedence over new cards, without changing their schedules. */
object DueOrder {
    val COLS = arrayOf("note_id", "ord", "queue", "due")

    fun query(deckName: String): String = PredictRule.learnQuery(deckName).removeSuffix("is:learn") + "is:due -is:new"

    // Keep the provider's order within a group; intraday due values are timestamps,
    // whereas review/interday due values are day numbers and cannot be compared to them.
    fun first(rows: List<Map<String, String?>>): Map<String, String?>? {
        val valid = rows.filter { it["note_id"]?.toLongOrNull() != null && it["ord"]?.toIntOrNull() != null && it["queue"] in setOf("1", "2", "3", "4") }
        return valid.firstOrNull { it["queue"] in setOf("1", "3", "4") } ?: valid.firstOrNull()
    }

    private fun rowKey(row: Map<String, String?>): StudyLoop.Key? {
        val nid = row["note_id"]?.toLongOrNull() ?: return null
        val ord = row["ord"]?.toIntOrNull() ?: return null
        return StudyLoop.Key(nid, ord)
    }

    /** Reorder only real scheduler entries: searches also contain reviews beyond daily limits. */
    fun <T> prioritize(scheduled: List<T>, due: List<Map<String, String?>>, expand: () -> List<T>, key: (T) -> StudyLoop.Key): List<T> {
        fun pick(queue: List<T>): T? {
            val byKey = queue.associateBy(key)
            val available = due.filter { row -> rowKey(row)?.let { it in byKey } == true }
            val first = first(available) ?: return null
            return rowKey(first)?.let { byKey[it] }
        }
        if (first(due) == null) return scheduled
        val review = pick(scheduled) ?: pick(expand())
        if (review == null) {
            check(due.none { it["queue"] in setOf("1", "4") }) {
                "A learning card is due but AnkiDroid hasn't opened it. Open AnkiDroid once, then retry."
            }
            return scheduled
        }
        return listOf(review) + scheduled.filterNot { key(it) == key(review) }
    }
}
