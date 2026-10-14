package com.husarp.ankigo

/** Files mentioned by a card, including CSS fonts/backgrounds and HTML audio/video sources. */
object CardMedia {
    fun files(html: String, api: List<String>): List<String> {
        val refs = Regex("""(?:src\s*=\s*["']([^"']+)["']|url\(\s*["']?([^)'"\s]+)|\[sound:([^]]+)])""", RegexOption.IGNORE_CASE)
            .findAll(html).map { m -> m.groupValues.drop(1).first { it.isNotEmpty() } }.toList()
        return (api + refs).filter { it.isNotBlank() && !it.contains("://") && !it.startsWith("data:") && !it.startsWith("#") }.distinct()
    }
    fun isSound(name: String) = name.substringAfterLast('.').lowercase() in setOf("mp3", "ogg", "oga", "wav", "m4a")
    /** Card content AnkiGo can't show at all (blocks answering until the user chooses). Speech and image occlusion work now, so nothing is left. */
    fun unsupported(html: String) = emptyList<String>().any { html.contains(it, true) }
    fun clozeExpected(field: String, ord: Int): String =
        Regex("""\{\{c${ord + 1}::(.*?)(?:::[^{}]*?)?}}""", RegexOption.DOT_MATCHES_ALL).findAll(field).joinToString(", ") { it.groupValues[1] }
    /** Character comparison keeps right, wrong and missing letters separately visible. */
    fun comparison(typed: String, expected: String, escape: (String) -> String): String {
        val a = typed.take(1000); val b = expected.take(1000)
        val lengths = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in a.indices.reversed()) for (j in b.indices.reversed())
            lengths[i][j] = if (a[i] == b[j]) 1 + lengths[i + 1][j + 1] else maxOf(lengths[i + 1][j], lengths[i][j + 1])
        val out = StringBuilder()
        fun letter(c: Char, color: String) { out.append("<span style=\"color:$color\">${escape(c.toString())}</span>") }
        var i = 0; var j = 0
        while (i < a.length || j < b.length) {
            when {
                i < a.length && j < b.length && a[i] == b[j] -> { letter(a[i++], "#16803c"); j++ }
                j < b.length && (i == a.length || lengths[i][j + 1] >= lengths[i + 1][j]) -> letter(b[j++], "#999")
                else -> letter(a[i++], "#c53030")
            }
        }
        return out.toString()
    }
}
