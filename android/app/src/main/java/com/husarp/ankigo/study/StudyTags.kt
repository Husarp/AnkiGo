package com.husarp.ankigo.study

/** Visit-scoped cache, including empty reads. An invalidated read can never repopulate the cache. */
internal class StudyTags {
    private val values = mutableMapOf<String, List<String>>()
    var revision = 0; private set
    fun get(deck: String): List<String>? = values[deck]
    fun put(deck: String, tags: List<String>, readRevision: Int): Boolean {
        if (readRevision != revision) return false
        values[deck] = tags
        return true
    }
    fun invalidate() { revision++; values.clear() }
    companion object {
        fun parse(raw: List<String>): List<String> = raw.flatMap { it.split(Regex("\\s+")) }
            .filter(String::isNotBlank).distinct().sortedWith(String.CASE_INSENSITIVE_ORDER)
    }
}
