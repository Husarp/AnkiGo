package com.husarp.ankigo.study

import org.json.JSONArray
import org.json.JSONObject

/** Immutable setups: loading a favourite returns a value to edit, never the saved record. */
data class DeckFilter(val id: Long, val name: String, val tags: Set<String> = emptySet(), val ignoreGlobal: Boolean = false, val free: Boolean = false)
data class StudyFilter(val decks: List<DeckFilter> = emptyList(), val tags: Set<String> = emptySet(), val newOnly: Boolean = false) {
    fun search(): String = if (decks.isEmpty()) "cid:0" else decks.joinToString(" or ", "(", ")") { d ->
        val terms = mutableListOf("deck:${quote(d.name)}")
        if (newOnly) terms += listOf("is:new", "-is:suspended", "-is:buried")
        else if (!d.free) terms += listOf(SCHEDULED, "-is:suspended", "-is:buried")
        if (!d.ignoreGlobal) terms += tagQuery(tags)
        terms += tagQuery(d.tags)
        terms.filter { it.isNotEmpty() }.joinToString(" ", "(", ")")
    }
    fun encode(): String = JSONObject().put("newOnly", newOnly).put("tags", JSONArray(tags.sorted())).put("decks", JSONArray(decks.map { d ->
        JSONObject().put("id", d.id).put("name", d.name).put("tags", JSONArray(d.tags.sorted())).put("ignore", d.ignoreGlobal).put("free", d.free)
    })).toString()
    companion object {
        // AnkiDroid, rather than local date arithmetic, decides whether a card is due.
        const val SCHEDULED = "is:due"
        fun quote(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("*", "\\*").replace("_", "\\_") + "\""
        private fun tagQuery(tags: Set<String>) = if (tags.isEmpty()) "" else tags.sorted().joinToString(" or ", "(", ")") { "tag:${quote(it)}" }
        fun decode(text: String): StudyFilter {
            val o = JSONObject(text)
            fun tags(a: JSONArray?) = (0 until (a?.length() ?: 0)).map { a!!.getString(it) }.toSet()
            val ds = o.getJSONArray("decks")
            return StudyFilter((0 until ds.length()).map { i -> ds.getJSONObject(i).let { d ->
                DeckFilter(d.getLong("id"), d.getString("name"), tags(d.optJSONArray("tags")), d.optBoolean("ignore"), d.optBoolean("free"))
            } }, tags(o.optJSONArray("tags")), o.optBoolean("newOnly"))
        }
        /** Tags belong to notes, not cards; sibling templates must not duplicate tags. */
        fun deckTags(noteIds: List<Long>, read: (Long) -> String?): List<String> = noteIds.distinct().flatMap {
            read(it).orEmpty().split(Regex("\\s+")).filter(String::isNotBlank)
        }.distinct().sortedWith(String.CASE_INSENSITIVE_ORDER)
    }
}

data class SavedFilter(val name: String, val filter: StudyFilter)
class FilterLibrary(val recent: List<SavedFilter> = emptyList(), val starred: List<SavedFilter> = emptyList()) {
    fun used(name: String, filter: StudyFilter) = FilterLibrary((listOf(SavedFilter(name, filter)) + recent.filter { it.filter != filter }).take(10), starred)
    fun star(name: String, filter: StudyFilter) = FilterLibrary(recent, starred + listOf(SavedFilter(name, filter)).filter { saved -> starred.none { it.filter == saved.filter } })
    fun unstar(index: Int) = FilterLibrary(recent, starred.filterIndexed { i, _ -> i != index })
    fun encode(): String {
        fun rows(xs: List<SavedFilter>) = JSONArray(xs.map { JSONObject().put("name", it.name).put("filter", JSONObject(it.filter.encode())) })
        return JSONObject().put("recent", rows(recent)).put("starred", rows(starred)).toString()
    }
    companion object {
        fun decode(text: String?): FilterLibrary = runCatching {
            val o = JSONObject(text ?: "{}")
            fun rows(key: String): List<SavedFilter> {
                val a = o.optJSONArray(key) ?: return emptyList()
                return (0 until a.length()).map { a.getJSONObject(it).let { r -> SavedFilter(r.getString("name"), StudyFilter.decode(r.getJSONObject("filter").toString())) } }
            }
            FilterLibrary(rows("recent").take(10), rows("starred"))
        }.getOrDefault(FilterLibrary())
    }
}

/** One explicit gate: local answers have no reference to a provider write operation. */
object FilterAnswer {
    fun route(scheduled: Boolean, save: () -> Unit, local: () -> Unit) { if (scheduled) save() else local() }
}
