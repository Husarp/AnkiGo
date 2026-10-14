package com.husarp.ankigo.game.ankimon

// TICKET T52 owns this file. JSON text the way Ankimon's Python writes it, and an editor that changes single members of a
// stored object without touching the rest of its text (org.json can't: it reorders keys on the JVM and rewrites numbers).

/** Python `json.dumps(x, ensure_ascii=False)` for the value kinds the export writes, plus an in-place editor for stored objects. */
internal object PyJson {
    /** null, Boolean, Int, Long, String, List and Map (String keys, insertion order) as Python writes them (", " and ": "). */
    fun dumps(v: Any?): String = StringBuilder().also { write(it, v) }.toString()

    private fun write(out: StringBuilder, v: Any?) {
        when (v) {
            null -> out.append("null")
            is Boolean -> out.append(v)
            is Int, is Long -> out.append(v)
            is String -> out.append(quote(v))
            is List<*> -> { out.append('['); v.forEachIndexed { i, e -> if (i > 0) out.append(", "); write(out, e) }; out.append(']') }
            is Map<*, *> -> {
                out.append('{')
                var first = true
                for ((k, e) in v) { if (!first) out.append(", "); first = false; out.append(quote(k as String)).append(": "); write(out, e) }
                out.append('}')
            }
            else -> throw IllegalArgumentException("Can't write ${v.javaClass.simpleName} as JSON")
        }
    }

    /** A JSON string literal. Only `"`, `\` and control characters are escaped; a lone surrogate (not valid text) becomes U+FFFD. */
    fun quote(s: String): String {
        val out = StringBuilder(s.length + 2).append('"')
        var i = 0
        while (i < s.length) {
            val c = s[i]
            when {
                c == '"' -> out.append("\\\"")
                c == '\\' -> out.append("\\\\")
                c == '\n' -> out.append("\\n")
                c == '\r' -> out.append("\\r")
                c == '\t' -> out.append("\\t")
                c == '\b' -> out.append("\\b")
                c == '\u000C' -> out.append("\\f")
                c < ' ' -> out.append(String.format("\\u%04x", c.code))
                c.isHighSurrogate() && i + 1 < s.length && s[i + 1].isLowSurrogate() -> { out.append(c).append(s[i + 1]); i++ }
                c.isSurrogate() -> out.append('�')
                else -> out.append(c)
            }
            i++
        }
        return out.append('"').toString()
    }

    /** One member of a stored object: its (decoded) key and where its value's text sits ([start] until [end]). */
    class Member(val key: String, val start: Int, val end: Int)

    /** The members of [text] when it is exactly one strict JSON object (surrounding spaces allowed), else null. */
    fun members(text: String): List<Member>? = try {
        Scan(text).topObject()
    } catch (e: IllegalArgumentException) {
        null
    }

    /** The raw text of every element when [text] is exactly one strict JSON array, else null. */
    fun elements(text: String): List<String>? = try {
        Scan(text).topArray()
    } catch (e: IllegalArgumentException) {
        null
    }

    /** True when [text] is one complete, strict JSON value (what Python's `json.loads` would turn into a non-string). */
    fun isValue(text: String): Boolean = try {
        Scan(text).topValue()
        true
    } catch (e: IllegalArgumentException) {
        false
    }

    /**
     * [text] (a stored JSON object) with each key of [changes] set to its raw JSON text: members that exist are replaced
     * in place (every duplicate), new ones are appended at the end. Everything else stays byte for byte.
     * Throws [IllegalArgumentException] when [text] isn't a strict JSON object.
     */
    fun edit(text: String, changes: Map<String, String>): String {
        val members = members(text) ?: throw IllegalArgumentException("Not a JSON object")
        val replace = members.filter { it.key in changes }.sortedBy { it.start }
        val out = StringBuilder(text.length + 64)
        var at = 0
        for (m in replace) { out.append(text, at, m.start).append(changes.getValue(m.key)); at = m.end }
        val appended = changes.keys.filter { k -> members.none { it.key == k } }
        if (appended.isEmpty()) return out.append(text, at, text.length).toString()
        val close = text.lastIndexOf('}')
        out.append(text, at, close)
        appended.forEachIndexed { i, k ->
            if (members.isNotEmpty() || i > 0) out.append(", ")
            out.append(quote(k)).append(": ").append(changes.getValue(k))
        }
        return out.append(text, close, text.length).toString()
    }

    // ---- a strict recursive-descent scanner (validates; keeps only member positions) --------------------------------

    private class Scan(val s: String) {
        var i = 0

        private fun fail(): Nothing = throw IllegalArgumentException("bad JSON at $i")
        private fun ws() { while (i < s.length && (s[i] == ' ' || s[i] == '\t' || s[i] == '\n' || s[i] == '\r')) i++ }
        private fun peek(): Char = if (i < s.length) s[i] else fail()
        private fun eat(c: Char) { if (peek() != c) fail(); i++ }
        private fun end() { ws(); if (i != s.length) fail() }

        fun topValue() { ws(); value(0); end() }

        fun topObject(): List<Member> { ws(); val m = obj(0); end(); return m }

        fun topArray(): List<String> {
            ws()
            val out = mutableListOf<String>()
            eat('[')
            ws()
            if (peek() == ']') i++ else {
                while (true) {
                    ws(); val st = i; value(1); out += s.substring(st, i); ws()
                    if (peek() == ',') i++ else { eat(']'); break }
                }
            }
            end()
            return out
        }

        private fun value(depth: Int) {
            if (depth > MAX_DEPTH) fail()
            when (peek()) {
                '{' -> obj(depth)
                '[' -> arr(depth)
                '"' -> string()
                else -> literal()
            }
        }

        private fun obj(depth: Int): List<Member> {
            if (depth > MAX_DEPTH) fail()
            eat('{')
            val out = mutableListOf<Member>()
            ws()
            if (peek() == '}') { i++; return out }
            while (true) {
                ws()
                val key = string()
                ws(); eat(':'); ws()
                val st = i
                value(depth + 1)
                out += Member(key, st, i)
                ws()
                if (peek() == ',') i++ else { eat('}'); return out }
            }
        }

        private fun arr(depth: Int) {
            if (depth > MAX_DEPTH) fail()
            eat('[')
            ws()
            if (peek() == ']') { i++; return }
            while (true) {
                ws(); value(depth + 1); ws()
                if (peek() == ',') i++ else { eat(']'); return }
            }
        }

        /** Scans a string literal and returns its decoded text. */
        private fun string(): String {
            eat('"')
            val sb = StringBuilder()
            while (true) {
                val c = peek()
                i++
                when {
                    c == '"' -> return sb.toString()
                    c < ' ' -> fail()
                    c == '\\' -> {
                        val e = peek()
                        i++
                        when (e) {
                            '"', '\\', '/' -> sb.append(e)
                            'b' -> sb.append('\b'); 'f' -> sb.append('\u000C'); 'n' -> sb.append('\n')
                            'r' -> sb.append('\r'); 't' -> sb.append('\t')
                            'u' -> {
                                val hex = s.substring(i, minOf(i + 4, s.length))
                                if (hex.length != 4 || hex.any { it !in HEX }) fail()
                                sb.append(hex.toInt(16).toChar())
                                i += 4
                            }
                            else -> fail()
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }

        private fun literal() {
            val st = i
            while (i < s.length && s[i] !in ",]} \t\r\n") i++
            if (!LITERAL.matches(s.substring(st, i))) { i = st; fail() }
        }
    }

    private const val MAX_DEPTH = 60
    private const val HEX = "0123456789abcdefABCDEF"
    private val LITERAL = Regex("true|false|null|-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?")
}
