package com.husarp.ankigo.study

/** Official-provider study transaction. Return counts cannot establish that AnkiDroid saved a write. */
class StudyLoop(private val provider: Provider) {
    data class Key(val nid: Long, val ord: Int)
    enum class Status { SAVED, MOVED, FAILED }
    data class Result(val status: Status, val row: Map<String, String?>? = null)
    interface Provider {
        fun select()
        fun top(): Key?
        fun stats(key: Key): Map<String, String?>?
        fun answer(key: Key, ease: Int, ms: Long)
    }
    fun answer(key: Key, before: Map<String, String?>, ease: Int, ms: Long, expect: () -> Unit = {}): Result {
        require(ease in 1..4)
        repeat(2) {
            provider.select()
            if (provider.top() != key) return Result(Status.MOVED)
            val fresh = provider.stats(key) ?: return Result(Status.FAILED)
            if (fresh["_id"] != before["_id"] || fresh["last_review_time_secs"] != before["last_review_time_secs"] || fresh["reps"] != before["reps"] || fresh["due"] != before["due"] || fresh["queue"] != before["queue"])
                return Result(Status.MOVED)
            expect()
            provider.answer(key, ease, ms.coerceIn(0, 3_600_000))
            val after = provider.stats(key)
            if (saved(before, after)) return Result(Status.SAVED, after)
            // A changed rep count is ambiguous: retrying could answer twice. Stop and ask the user to reload.
            if (after == null || after["reps"] != before["reps"]) return Result(Status.FAILED, after)
        }
        return Result(Status.FAILED)
    }
    companion object {
        fun saved(before: Map<String, String?>, after: Map<String, String?>?): Boolean {
            if (after == null || before["_id"] == null || before["_id"] != after["_id"]) return false
            val reps = before["reps"]?.toIntOrNull() ?: return false
            return after["reps"]?.toIntOrNull() == reps + 1 &&
                listOf("due", "queue", "last_review_time_secs").any { before[it] != after[it] }
        }
    }
}
