package com.husarp.ankigo.game.ankimon

import com.husarp.ankigo.game.ankimon.SqlSchema.Affinity
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

// TICKET T51 owns this file. SQLite's value rules in one place: records, affinity, comparison, json_extract.
// Shared by SqliteWriter and SqliteCheck. Everything the code can't reproduce with certainty THROWS
// (IllegalArgumentException) instead of guessing: a refused export beats a file that real SQLite would call damaged.

internal object SqlValues {
    // ---- varints and records (https://www.sqlite.org/fileformat2.html "Record Format") ------------------------------

    fun varintLen(v: Long): Int {
        if (v < 0 || (v ushr 56) != 0L) return 9
        var n = 1
        var x = v ushr 7
        while (x != 0L) { n++; x = x ushr 7 }
        return n
    }

    /** Writes [v] as a SQLite varint at [p]; returns the number of bytes. */
    fun putVarint(b: ByteArray, p: Int, v: Long): Int {
        if (v < 0 || (v ushr 56) != 0L) { // 9 bytes: eight groups of 7 bits, then a whole byte
            b[p + 8] = v.toByte()
            var x = v ushr 8
            for (i in 7 downTo 0) { b[p + i] = ((x and 0x7F) or 0x80).toByte(); x = x ushr 7 }
            return 9
        }
        val n = varintLen(v)
        var x = v
        for (i in n - 1 downTo 0) { b[p + i] = ((x and 0x7F) or (if (i == n - 1) 0L else 0x80L)).toByte(); x = x ushr 7 }
        return n
    }

    /** UTF-8 bytes of [s]; refuses text with unpaired surrogates (Java would silently write '?'). */
    fun utf8(s: String): ByteArray {
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c.isSurrogate()) {
                if (c.isHighSurrogate() && i + 1 < s.length && s[i + 1].isLowSurrogate()) i++
                else throw IllegalArgumentException("Text with an unpaired surrogate can't be stored")
            }
            i++
        }
        return s.toByteArray(Charsets.UTF_8)
    }

    private fun intSize(v: Long): Int = when {
        v == 0L || v == 1L -> 0
        v in -128..127 -> 1
        v in -32768..32767 -> 2
        v in -8388608..8388607 -> 3
        v in -2147483648L..2147483647L -> 4
        v in -140737488355328L..140737488355327L -> 6
        else -> 8
    }

    /**
     * One record: the smallest serial type for every value (0 and 1 as types 8 and 9, REAL as 7), plus the optional
     * trailing integer [tail] (an index entry's rowid). Values are Long, Double, String, ByteArray or null.
     */
    fun record(values: List<Any?>, tail: Long? = null): ByteArray {
        val n = values.size + if (tail != null) 1 else 0
        val types = LongArray(n)
        val data = arrayOfNulls<Any>(n)
        var typesLen = 0
        var body = 0
        for (i in 0 until n) {
            val v = if (i < values.size) values[i] else tail
            var len = 0
            types[i] = when (v) {
                null -> 0
                is Long -> {
                    len = intSize(v)
                    when { v == 0L -> 8L; v == 1L -> 9L; else -> when (len) { 1 -> 1L; 2 -> 2L; 3 -> 3L; 4 -> 4L; 6 -> 5L; else -> 6L } }
                }
                is Double -> { len = 8; 7L }
                is String -> { val b = utf8(v); data[i] = b; len = b.size; 13L + 2L * len }
                is ByteArray -> { len = v.size; 12L + 2L * len }
                else -> throw IllegalArgumentException("Unsupported value type ${v.javaClass.simpleName}")
            }
            typesLen += varintLen(types[i])
            body += len
        }
        var hdr = typesLen + 1
        while (varintLen(hdr.toLong()) != hdr - typesLen) hdr = typesLen + varintLen(hdr.toLong())
        val out = ByteArray(hdr + body)
        var p = putVarint(out, 0, hdr.toLong())
        for (t in types) p += putVarint(out, p, t)
        for (i in 0 until n) {
            val v = if (i < values.size) values[i] else tail
            when (v) {
                is Long -> { val len = intSize(v); for (k in 0 until len) out[p + k] = (v shr (8 * (len - 1 - k))).toByte(); p += len }
                is Double -> { val bits = java.lang.Double.doubleToRawLongBits(v); for (k in 0 until 8) out[p + k] = (bits shr (56 - 8 * k)).toByte(); p += 8 }
                is String -> { val b = data[i] as ByteArray; System.arraycopy(b, 0, out, p, b.size); p += b.size }
                is ByteArray -> { System.arraycopy(v, 0, out, p, v.size); p += v.size }
                else -> {}
            }
        }
        return out
    }

    private class Reader(val b: ByteArray, var pos: Int, val end: Int) {
        fun need(n: Int) { if (n < 0 || pos + n > end) throw IllegalArgumentException("A record runs past its end") }
        fun varint(): Long {
            var v = 0L
            for (i in 0 until 8) {
                need(1)
                val x = b[pos++].toInt() and 0xFF
                v = (v shl 7) or (x and 0x7F).toLong()
                if (x and 0x80 == 0) return v
            }
            need(1)
            return (v shl 8) or (b[pos++].toLong() and 0xFF)
        }
        fun int(n: Int): Long {
            need(n)
            var v = if (b[pos].toInt() < 0) -1L else 0L
            for (i in 0 until n) v = (v shl 8) or (b[pos + i].toLong() and 0xFF)
            pos += n
            return v
        }
    }

    /** Decodes a record into Long / Double / String (strict UTF-8) / ByteArray / null. Throws IllegalArgumentException when malformed. */
    fun decode(payload: ByteArray, from: Int = 0, to: Int = payload.size): List<Any?> {
        val c = Reader(payload, from, to)
        val hdrEnd = c.varint()
        if (hdrEnd < 1 || from + hdrEnd > to) throw IllegalArgumentException("Bad record header")
        val hdr = Reader(payload, c.pos, from + hdrEnd.toInt())
        val types = ArrayList<Long>()
        while (hdr.pos < hdr.end) types += hdr.varint()
        val body = Reader(payload, from + hdrEnd.toInt(), to)
        val out = ArrayList<Any?>(types.size)
        for (t in types) {
            out += when {
                t == 0L -> null
                t in 1L..4L -> body.int(t.toInt())
                t == 5L -> body.int(6)
                t == 6L -> body.int(8)
                t == 7L -> java.lang.Double.longBitsToDouble(body.int(8))
                t == 8L -> 0L
                t == 9L -> 1L
                t == 10L || t == 11L -> throw IllegalArgumentException("Unknown value type $t")
                else -> {
                    val len = if (t % 2 == 0L) (t - 12) / 2 else (t - 13) / 2
                    if (len > to) throw IllegalArgumentException("A value is bigger than its record")
                    body.need(len.toInt())
                    val at = body.pos
                    body.pos += len.toInt()
                    if (t % 2 == 0L) payload.copyOfRange(at, at + len.toInt()) else strictUtf8(payload, at, len.toInt())
                }
            }
        }
        if (body.pos != to) throw IllegalArgumentException("A record has unused bytes at its end")
        return out
    }

    private fun strictUtf8(b: ByteArray, at: Int, len: Int): String {
        var ascii = true
        for (i in at until at + len) if (b[i] < 0) { ascii = false; break }
        if (ascii) return String(b, at, len, Charsets.ISO_8859_1)
        try {
            return Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(b, at, len)).toString()
        } catch (e: java.nio.charset.CharacterCodingException) {
            throw IllegalArgumentException("Text that is not valid UTF-8")
        }
    }

    // ---- affinity (https://www.sqlite.org/datatype3.html section 3) ---------------------------------------------------

    /** Brings Int/Short/Byte to Long and refuses everything SQLite has no storage class for (and NaN / infinity). */
    fun normalize(v: Any?): Any? = when (v) {
        null, is Long, is String, is ByteArray -> v
        is Int -> v.toLong()
        is Short -> v.toLong()
        is Byte -> v.toLong()
        is Double -> if (v.isNaN() || v.isInfinite()) throw IllegalArgumentException("NaN and infinity can't be stored") else v
        else -> throw IllegalArgumentException("Unsupported value type ${v.javaClass.simpleName}")
    }

    /** A whole REAL becomes an INTEGER, as SQLite does for NUMERIC / INTEGER affinity (and in the file for REAL columns). */
    fun intIfWhole(d: Double): Any {
        val ix = d.toLong() // saturates
        return if (d == ix.toDouble() && ix > Long.MIN_VALUE && ix < Long.MAX_VALUE) ix else d
    }

    /**
     * What a column of [a] affinity makes of [value] on INSERT. Long -> TEXT, numeric text -> number, whole REAL -> INTEGER.
     * Throws where SQLite's exact behaviour can't be reproduced: a REAL into a TEXT column, text with spaces around a
     * number, integers beyond 64 bits, decimals that are not exactly a double.
     */
    fun apply(value: Any?, a: Affinity): Any? {
        val v = normalize(value)
        return when (a) {
            Affinity.BLOB -> v
            Affinity.TEXT -> when (v) {
                is Long -> v.toString()
                is Double -> throw IllegalArgumentException("A REAL in a TEXT column can't be written the way SQLite formats it")
                else -> v
            }
            Affinity.NUMERIC, Affinity.INTEGER -> when (v) {
                is Double -> intIfWhole(v)
                is String -> numericText(v) ?: v
                else -> v
            }
            Affinity.REAL -> when (v) {
                is Long -> v.toDouble()
                is String -> numericText(v)?.let { if (it is Long) it.toDouble() else it } ?: v
                else -> v
            }
        }
    }

    /** The value as SQLite stores it in the file: a whole REAL in a REAL column is written as an INTEGER (and read back as REAL). */
    fun forFile(v: Any?, a: Affinity): Any? = if (a == Affinity.REAL && v is Double) intIfWhole(v) else v

    /**
     * How SQLite's NUMERIC affinity reads the text [s]: a Long, a Double, or null when it stays text. Throws when unsure
     * (spaces around a number, an integer that doesn't fit 64 bits, a decimal that is not exactly a double).
     */
    fun numericText(s: String): Any? {
        val t = s.trim { it == ' ' || it == '\t' || it == '\n' || it == '\r' || it == '\u000b' || it == '\u000c' }
        var i = 0
        if (i < t.length && (t[i] == '+' || t[i] == '-')) i++
        val intStart = i
        while (i < t.length && t[i] in '0'..'9') i++
        var digits = i - intStart
        var real = false
        if (i < t.length && t[i] == '.') {
            real = true
            i++
            val f = i
            while (i < t.length && t[i] in '0'..'9') i++
            digits += i - f
        }
        if (digits == 0) return null
        if (i < t.length && (t[i] == 'e' || t[i] == 'E')) {
            var j = i + 1
            if (j < t.length && (t[j] == '+' || t[j] == '-')) j++
            val d = j
            while (j < t.length && t[j] in '0'..'9') j++
            if (j > d) { real = true; i = j } // "1e" is not an exponent: the text stays text
        }
        if (i != t.length) return null
        if (t.length != s.length) throw IllegalArgumentException("Can't tell how SQLite reads the number in '$s' with spaces around it")
        if (!real) return t.removePrefix("+").toLongOrNull() ?: throw IllegalArgumentException("The integer '$s' doesn't fit in 64 bits")
        return intIfWhole(exactDecimal(t))
    }

    /**
     * A decimal as a Double only when that is certainly what SQLite parses it to on every build: the decimal is exactly a
     * double (25.0, 2.5, 1e2; not 0.1: SQLite may round such a number differently by one bit) with up to 15 digits.
     */
    fun exactDecimal(t: String): Double {
        val e = t.indexOfFirst { it == 'e' || it == 'E' }
        val mant = if (e < 0) t else t.substring(0, e)
        if (e >= 0 && t.length - e > 7) throw IllegalArgumentException("Exponent too large in '$t'")
        if (mant.filter { it in '0'..'9' }.trimStart('0').length > 15) throw IllegalArgumentException("The decimal '$t' has more than 15 digits")
        val d = t.toDouble()
        if (d.isInfinite() || d.isNaN()) throw IllegalArgumentException("The decimal '$t' is out of range")
        if (d == 0.0 && t.startsWith("-")) throw IllegalArgumentException("Negative zero can't be reproduced exactly")
        if (java.math.BigDecimal(t).compareTo(java.math.BigDecimal(d)) != 0) throw IllegalArgumentException("The decimal '$t' is not exactly a double, SQLite might round it differently")
        return d
    }

    // ---- comparison (BINARY collation) ---------------------------------------------------------------------------------

    private fun rank(v: Any?): Int = when (v) { null -> 0; is Long, is Double -> 1; is String -> 2; else -> 3 }

    /** SQLite's order: NULL < numbers (compared by value) < text (UTF-8 bytes) < blob. */
    fun compare(a: Any?, b: Any?): Int {
        val ra = rank(a)
        val rb = rank(b)
        if (ra != rb) return if (ra < rb) -1 else 1
        return when (ra) {
            0 -> 0
            1 -> compareNumbers(a!!, b!!)
            2 -> compareText(a as String, b as String)
            else -> compareBytes(a as ByteArray, b as ByteArray)
        }
    }

    private fun compareNumbers(a: Any, b: Any): Int = when {
        a is Long && b is Long -> a.compareTo(b)
        a is Double && b is Double -> if (a < b) -1 else if (a > b) 1 else 0
        a is Long -> intFloat(a, b as Double)
        else -> -intFloat(b as Long, a as Double)
    }

    // sqlite3IntFloatCompare
    private fun intFloat(i: Long, r: Double): Int {
        if (r < -9223372036854775808.0) return 1
        if (r >= 9223372036854775808.0) return -1
        val y = r.toLong()
        if (i < y) return -1
        if (i > y) return 1
        val s = i.toDouble()
        return if (s < r) -1 else if (s > r) 1 else 0
    }

    /** UTF-8 byte order == code point order (UTF-16 order differs for characters outside the BMP, hence the fix-up). */
    private fun compareText(a: String, b: String): Int {
        val n = minOf(a.length, b.length)
        for (i in 0 until n) {
            var x = a[i].code
            var y = b[i].code
            if (x != y) {
                if (x >= 0xD800 && y >= 0xD800) {
                    x += if (x >= 0xE000) -0x800 else 0x2000
                    y += if (y >= 0xE000) -0x800 else 0x2000
                }
                return x - y
            }
        }
        return a.length - b.length
    }

    private fun compareBytes(a: ByteArray, b: ByteArray): Int {
        val n = minOf(a.size, b.size)
        for (i in 0 until n) {
            val d = (a[i].toInt() and 0xFF) - (b[i].toInt() and 0xFF)
            if (d != 0) return d
        }
        return a.size - b.size
    }

    /** Compares two keys (arrays of values) column by column. */
    fun compareKeys(a: Array<Any?>, b: Array<Any?>): Int {
        for (i in 0 until minOf(a.size, b.size)) {
            val c = compare(a[i], b[i])
            if (c != 0) return c
        }
        return a.size - b.size
    }
}

/**
 * `json_extract(text, '$.key')` for the top-level keys of a JSON object, as SQLite 3.46 gives it: string -> TEXT (unescaped),
 * integer -> INTEGER, real -> REAL, true / false -> 1 / 0, null or missing -> NULL, object / array -> its minified text.
 * Strict RFC 8259 only. Throws IllegalArgumentException for malformed JSON, a top level that is not an object,
 * duplicate top-level keys, escapes in top-level keys, \u0000, unpaired surrogates, integers beyond 64 bits and reals that
 * can't be reproduced exactly (not exactly a double, more than 15 digits, negative zero).
 */
internal object SqlJson {
    private const val MAX_DEPTH = 64

    fun extract(text: String, keys: Set<String>): Map<String, Any?> = Parser(text, keys).run()

    private class Parser(val s: String, val keys: Set<String>) {
        var i = 0
        var escaped = false
        val out = HashMap<String, Any?>()

        fun fail(msg: String): Nothing = throw IllegalArgumentException("JSON: $msg")

        fun ws() { while (i < s.length && (s[i] == ' ' || s[i] == '\t' || s[i] == '\n' || s[i] == '\r')) i++ }

        fun run(): Map<String, Any?> {
            ws()
            if (i >= s.length || s[i] != '{') fail("the text is not an object")
            i++
            ws()
            val seen = HashSet<String>()
            if (i < s.length && s[i] == '}') i++ else while (true) {
                ws()
                if (i >= s.length || s[i] != '"') fail("an object key must be a string")
                escaped = false
                val k = string(true)!!
                if (escaped) fail("an escape in a key")
                if (!seen.add(k)) fail("duplicate key '$k'")
                ws()
                if (i >= s.length || s[i] != ':') fail("':' expected")
                i++
                ws()
                val v = value(k in keys, 1)
                if (k in keys) out[k] = v
                ws()
                if (i >= s.length) fail("unterminated object")
                val c = s[i++]
                if (c == '}') break
                if (c != ',') fail("',' or '}' expected")
            }
            ws()
            if (i != s.length) fail("text after the value")
            return out
        }

        fun value(wanted: Boolean, depth: Int): Any? {
            if (depth > MAX_DEPTH) fail("nested too deeply")
            if (i >= s.length) fail("unexpected end")
            return when (s[i]) {
                '"' -> string(wanted)
                '{', '[' -> { val st = i; container(depth); if (wanted) minify(st, i) else null }
                't' -> literal("true", 1L)
                'f' -> literal("false", 0L)
                'n' -> literal("null", null)
                else -> number(wanted)
            }
        }

        fun literal(word: String, v: Any?): Any? {
            if (!s.startsWith(word, i)) fail("unknown token")
            i += word.length
            return v
        }

        fun container(depth: Int) {
            val open = s[i++]
            val close = if (open == '{') '}' else ']'
            ws()
            if (i < s.length && s[i] == close) { i++; return }
            while (true) {
                ws()
                if (open == '{') {
                    if (i >= s.length || s[i] != '"') fail("an object key must be a string")
                    string(false)
                    ws()
                    if (i >= s.length || s[i] != ':') fail("':' expected")
                    i++
                    ws()
                }
                value(false, depth + 1)
                ws()
                if (i >= s.length) fail("unterminated value")
                val c = s[i++]
                if (c == close) return
                if (c != ',') fail("',' or '$close' expected")
            }
        }

        fun minify(from: Int, to: Int): String {
            val sb = StringBuilder(to - from)
            var inStr = false
            var j = from
            while (j < to) {
                val c = s[j]
                if (inStr) {
                    sb.append(c)
                    if (c == '\\') { sb.append(s[j + 1]); j++ } else if (c == '"') inStr = false
                } else if (c == '"') { inStr = true; sb.append(c) }
                else if (c != ' ' && c != '\t' && c != '\n' && c != '\r') sb.append(c)
                j++
            }
            return sb.toString()
        }

        /** Scans the string at [i] (on the opening quote); returns its unescaped text when [build]. */
        fun string(build: Boolean): String? {
            i++
            val sb = if (build) StringBuilder() else null
            var run = i
            while (true) {
                if (i >= s.length) fail("unterminated string")
                val c = s[i]
                when {
                    c == '"' -> { sb?.append(s, run, i); i++; return sb?.toString() }
                    c < ' ' -> fail("a raw control character in a string")
                    c == '\\' -> {
                        escaped = true
                        sb?.append(s, run, i)
                        if (i + 1 >= s.length) fail("unterminated string")
                        when (val e = s[i + 1]) {
                            '"', '\\', '/' -> { sb?.append(e); i += 2 }
                            'b' -> { sb?.append('\b'); i += 2 }
                            'f' -> { sb?.append('\u000c'); i += 2 }
                            'n' -> { sb?.append('\n'); i += 2 }
                            'r' -> { sb?.append('\r'); i += 2 }
                            't' -> { sb?.append('\t'); i += 2 }
                            'u' -> {
                                val code = hex4(i + 2)
                                if (code == 0) fail("\\u0000 can't be reproduced")
                                if (code in 0xD800..0xDBFF) {
                                    if (!(s.startsWith("\\u", i + 6))) fail("unpaired surrogate")
                                    val low = hex4(i + 8)
                                    if (low !in 0xDC00..0xDFFF) fail("unpaired surrogate")
                                    sb?.append(code.toChar())?.append(low.toChar())
                                    i += 12
                                } else {
                                    if (code in 0xDC00..0xDFFF) fail("unpaired surrogate")
                                    sb?.append(code.toChar())
                                    i += 6
                                }
                            }
                            else -> fail("unknown escape")
                        }
                        run = i
                    }
                    c.isHighSurrogate() -> { if (i + 1 < s.length && s[i + 1].isLowSurrogate()) i += 2 else fail("unpaired surrogate") }
                    c.isLowSurrogate() -> fail("unpaired surrogate")
                    else -> i++
                }
            }
        }

        fun hex4(at: Int): Int {
            if (at + 4 > s.length) fail("bad \\u escape")
            var v = 0
            for (k in 0 until 4) {
                val d = Character.digit(s[at + k], 16)
                if (d < 0 || s[at + k].code > 127) fail("bad \\u escape")
                v = v * 16 + d
            }
            return v
        }

        fun number(wanted: Boolean): Any? {
            val st = i
            if (s[i] == '-') i++
            if (i >= s.length) fail("bad number")
            if (s[i] == '0') i++
            else if (s[i] in '1'..'9') { while (i < s.length && s[i] in '0'..'9') i++ }
            else fail("unknown token")
            var real = false
            if (i < s.length && s[i] == '.') {
                real = true
                i++
                val d = i
                while (i < s.length && s[i] in '0'..'9') i++
                if (i == d) fail("bad number")
            }
            if (i < s.length && (s[i] == 'e' || s[i] == 'E')) {
                real = true
                i++
                if (i < s.length && (s[i] == '+' || s[i] == '-')) i++
                val d = i
                while (i < s.length && s[i] in '0'..'9') i++
                if (i == d) fail("bad number")
            }
            if (!wanted) return null
            val t = s.substring(st, i)
            if (!real) {
                if (t == "-0") fail("negative zero can't be reproduced")
                return t.toLongOrNull() ?: fail("an integer beyond 64 bits can't be reproduced")
            }
            return try { SqlValues.exactDecimal(t) } catch (e: IllegalArgumentException) { fail(e.message ?: "bad real") }
        }
    }
}
