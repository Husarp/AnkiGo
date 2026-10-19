package com.husarp.ankigo.study

/**
 * What identifies a card in AnkiDroid's queue and the one rule that tells a write was really saved. AnkiDroid swallows errors and
 * still returns 1 from an answer, so only the card's re-read state proves it. The write itself is the [AnswerQueue] (study/AnswerQueue.kt).
 */
object StudyLoop {
    data class Key(val nid: Long, val ord: Int)

    /** The card moved by exactly one rep and its scheduling changed: a rep change alone, or a schedule change alone, proves nothing. */
    fun saved(before: Map<String, String?>, after: Map<String, String?>?): Boolean {
        if (after == null || before["_id"] == null || before["_id"] != after["_id"]) return false
        val reps = before["reps"]?.toIntOrNull() ?: return false
        return after["reps"]?.toIntOrNull() == reps + 1 &&
            listOf("due", "queue", "last_review_time_secs").any { before[it] != after[it] }
    }
}
