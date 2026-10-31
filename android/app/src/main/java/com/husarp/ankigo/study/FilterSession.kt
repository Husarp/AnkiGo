package com.husarp.ankigo.study

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** IDs only, kept in private storage so thousands of matches never travel through a Binder extra. */
class FilterSession(val cards: List<Map<String, String?>>, var position: Int = 0, val query: String = "-is:suspended -is:buried") {
    fun encode() = JSONObject().put("position", position).put("query", query).put("cards", JSONArray(cards.map { JSONObject(it) })).toString()
    fun save(file: File) {
        val tmp = File(file.path + ".tmp")
        tmp.writeText(encode())
        check(tmp.renameTo(file)) { "Couldn't save study progress" }
    }
    /** Advancing a large selection writes only its cursor, never the whole card list. */
    fun saveProgress(file: File) {
        val target = File(file.path + ".position")
        val tmp = File(target.path + ".tmp")
        tmp.writeText(position.toString())
        check(tmp.renameTo(target)) { "Couldn't save study progress" }
    }
    companion object {
        fun read(file: File): FilterSession = decode(file.readText()).also { session ->
            val progress = File(file.path + ".position")
            if (progress.exists()) session.position = progress.readText().toInt().coerceIn(0, session.cards.size)
        }

        fun decode(text: String): FilterSession {
            val o = JSONObject(text); val a = o.getJSONArray("cards")
            return FilterSession((0 until a.length()).map { i -> a.getJSONObject(i).let { r -> r.keys().asSequence().associateWith { r.optString(it) } } }, o.optInt("position"), o.optString("query", "-is:suspended -is:buried"))
        }
    }
}
