package com.husarp.ankigo.study

import android.webkit.JavascriptInterface
import org.json.JSONObject

/** Read-only template actions. UI dispatch belongs to the host; invalid IDs never reach the API. */
class TemplateBridge(private val term: (String, String) -> Unit, private val note: (Long) -> Unit) {
    @JavascriptInterface fun showTerm(key: String, subject: String) {
        if (key.isNotBlank() && key.length <= 500 && dictionaryFile(subject) != null) term(key, subject)
    }
    @JavascriptInterface fun openNote(noteId: String) {
        noteId.toLongOrNull()?.takeIf { it > 0 }?.let(note)
    }
    companion object {
        fun dictionaryFile(subject: String): String? = subject.takeIf { it.matches(Regex("[\\p{L}\\p{N}_-]{1,100}")) }?.let { "_slownik_$it.js" }
        /** A JSON object, optionally assigned to window.slownik / const slownik / another JS name. Never executes code. */
        fun dictionary(source: String): JSONObject? = runCatching {
            val text = source.trim().removeSuffix(";").trim()
            val json = if (text.startsWith("{")) text else {
                val assignment = Regex("""^(?:(?:const|let|var)\s+)?(?:window\.)?[A-Za-z_$][\w$]*\s*=\s*(\{.*})$""", RegexOption.DOT_MATCHES_ALL).matchEntire(text) ?: return null
                assignment.groupValues[1]
            }
            JSONObject(json)
        }.getOrNull()
        fun definition(source: String, key: String): String? {
            val value = dictionary(source)?.opt(key)
            return when (value) {
                is String -> value
                is JSONObject -> value.optString("html").takeIf { it.isNotBlank() }
                else -> null
            }
        }
    }
}
