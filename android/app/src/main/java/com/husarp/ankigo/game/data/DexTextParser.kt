package com.husarp.ankigo.game.data

import org.json.JSONException
import org.json.JSONObject

// PLAN item 60: the Pokédex text and category ("Seed Pokémon") of one species, read from a PokeAPI pokemon-species answer.
// Pure parsing (no android.*): unit-tested on made-up samples. The download and the cache are data/DexText.kt.

/** [genus] is the category ("Seed Pokémon"), [text] the Pokédex entry; either is null when the answer has none in English. */
data class DexEntry(val genus: String?, val text: String?) {
    val empty: Boolean get() = genus == null && text == null
}

object DexTextParser {
    /**
     * Reads PokeAPI `pokemon-species/{n}`: the English `genera` entry and the newest English `flavor_text_entries` entry
     * (highest version id from `version.url`, ties and missing ids: the later one in the list). Null when [json] is not a
     * species answer; an entry with neither field is returned empty.
     */
    fun parse(json: String): DexEntry? {
        val root = try { JSONObject(json) } catch (e: JSONException) { return null }
        if (!root.has("genera") && !root.has("flavor_text_entries")) return null
        var genus: String? = null
        root.optJSONArray("genera")?.let { a ->
            for (i in 0 until a.length()) {
                val g = a.optJSONObject(i) ?: continue
                if (isEnglish(g)) genus = clean(g.optString("genus", "")).ifEmpty { null } ?: genus
            }
        }
        var text: String? = null
        var bestId = Int.MIN_VALUE
        root.optJSONArray("flavor_text_entries")?.let { a ->
            for (i in 0 until a.length()) {
                val f = a.optJSONObject(i) ?: continue
                if (!isEnglish(f)) continue
                val t = clean(f.optString("flavor_text", "")).ifEmpty { null } ?: continue
                val id = versionId(f.optJSONObject("version")?.optString("url", "")) ?: Int.MIN_VALUE
                if (id >= bestId) { bestId = id; text = t }
            }
        }
        return DexEntry(genus, text)
    }

    /** Form feeds, line breaks and runs of blanks become single spaces; soft hyphens go; ends trimmed. */
    fun clean(s: String): String =
        s.replace("­", "").replace(Regex("[\\s\\u000C\\u00A0]+"), " ").trim()

    /** The cache file text of one entry. */
    fun encode(e: DexEntry): String = JSONObject().apply {
        e.genus?.let { put("genus", it) }
        e.text?.let { put("text", it) }
    }.toString()

    /** Reads what [encode] wrote; null when [json] is damaged. */
    fun decode(json: String): DexEntry? {
        val o = try { JSONObject(json) } catch (e: JSONException) { return null }
        return DexEntry(o.optString("genus", "").ifEmpty { null }, o.optString("text", "").ifEmpty { null })
    }

    private fun isEnglish(o: JSONObject) = o.optJSONObject("language")?.optString("name", "") == "en"

    /** ".../version/26/" -> 26. */
    private fun versionId(url: String?): Int? = url?.trimEnd('/')?.substringAfterLast('/')?.toIntOrNull()
}
