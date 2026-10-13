package com.husarp.ankigo.game.ankimon

import java.io.File
import java.nio.charset.Charset

// TICKET T19 owns this file.

/** The bytes are not a readable SQLite 3 database (wrong header, cut off, damaged, or a kind of table we don't read). */
class SqliteFormatException(message: String) : java.io.IOException(message)

/**
 * A read-only reader for SQLite 3 files, just enough to read ordinary rowid tables.
 *
 * Why our own: Ankimon's `captured_pokemon` table has VIRTUAL generated columns built on `json_extract`, which the
 * SQLite that ships with Android 8-11 refuses to open, and JVM unit tests have no SQLite at all. A save file is small
 * (callers cap it, see [open]), so it is read fully into memory. Everything in the file is untrusted: every page number
 * and offset is checked, loops are cut by a visited set, and damage ends in a [SqliteFormatException], never a crash or a hang.
 * Format reference: https://www.sqlite.org/fileformat2.html
 */
class SqliteReader(private val b: ByteArray) {
    companion object {
        private val MAGIC = "SQLite format 3\u0000".toByteArray(Charsets.ISO_8859_1)
        private const val MAX_DEPTH = 40       // a real b-tree of a few GB is far shallower
        private const val MAX_COLUMNS = 2_000  // SQLite's own hard limit is 32767; saves have a handful
        private val CONSTRAINT_WORDS = setOf(
            "CONSTRAINT", "PRIMARY", "NOT", "NULL", "UNIQUE", "CHECK", "DEFAULT", "COLLATE", "REFERENCES", "GENERATED", "AS",
        )

        /** Reads [file] fully; refuses files over [maxBytes] (default 64 MB). */
        fun open(file: File, maxBytes: Long = 64L shl 20): SqliteReader {
            val len = file.length()
            if (len > maxBytes) throw SqliteFormatException("The file is too big (${len / 1_000_000} MB)")
            return SqliteReader(file.readBytes())
        }
    }

    private class TableInfo(val name: String, val root: Int, val sql: String?)

    private class ColumnDef(val name: String, val virtual: Boolean, val rowidAlias: Boolean, val real: Boolean)

    private val pageSize: Int
    private val usable: Int
    private val pageCount: Int
    private val charset: Charset
    private var payloadBudget = 0L                            // see walkTable
    private val master = LinkedHashMap<String, TableInfo>() // lower-case name -> table
    private val tableOrder = mutableListOf<String>()        // names as declared, type = table, not sqlite_*

    init {
        if (b.size < 100) throw SqliteFormatException("Not a SQLite file (too short)")
        for (i in MAGIC.indices) if (b[i] != MAGIC[i]) throw SqliteFormatException("Not a SQLite file (wrong header)")
        val ps = u16(16)
        pageSize = if (ps == 1) 65536 else ps
        if (pageSize < 512 || pageSize > 65536 || pageSize and (pageSize - 1) != 0) {
            throw SqliteFormatException("Bad page size")
        }
        usable = pageSize - (b[20].toInt() and 0xFF)
        if (usable < 480) throw SqliteFormatException("Bad reserved space")
        charset = when (u32(56)) {
            0L, 1L -> Charsets.UTF_8
            2L -> Charsets.UTF_16LE
            3L -> Charsets.UTF_16BE
            else -> throw SqliteFormatException("Unknown text encoding")
        }
        if (b.size % pageSize != 0) throw SqliteFormatException("The file is cut off (not a whole number of pages)")
        pageCount = b.size / pageSize
        // The size in the header is only trusted when the header says it is current (change counter == version-valid-for).
        val declared = u32(28)
        if (declared > 0 && u32(24) == u32(92) && declared > pageCount) throw SqliteFormatException("The file is cut off")
        guard { readMaster() }
    }

    /** Names of the user tables (type 'table' in sqlite_master, internal sqlite_* skipped), in file order. */
    fun tableNames(): List<String> = tableOrder.toList()

    /** Declared column names of [table], in order (virtual generated columns included). Unknown table -> empty. */
    fun columns(table: String): List<String> {
        val t = master[table.lowercase()] ?: return emptyList()
        return guard { columnDefs(t.sql).map { it.name } }
    }

    /**
     * Rows as column-name -> value (Long, Double, String, ByteArray or null), at most [limit]. Virtual generated
     * columns are not stored in the file and are left out. Unknown table -> empty.
     */
    fun rows(table: String, limit: Int = 100_000): List<Map<String, Any?>> {
        val t = master[table.lowercase()] ?: return emptyList()
        if (limit <= 0) return emptyList()
        return guard {
            val sql = t.sql ?: throw SqliteFormatException("Table ${t.name} has no definition")
            if (Regex("""\bWITHOUT\s+ROWID\b""", RegexOption.IGNORE_CASE).containsMatchIn(afterColumns(sql))) {
                throw SqliteFormatException("Table ${t.name} is WITHOUT ROWID, which this reader doesn't support")
            }
            val stored = columnDefs(sql).filter { !it.virtual }
            val out = ArrayList<Map<String, Any?>>()
            walkTable(t.root, limit) { rowid, record ->
                val row = LinkedHashMap<String, Any?>()
                for ((i, c) in stored.withIndex()) {
                    val v = if (c.rowidAlias) rowid else record.getOrNull(i)
                    // SQLite stores a REAL that is a whole number as an integer; the column's type brings it back.
                    row[c.name] = if (c.real && v is Long) v.toDouble() else v
                }
                out += row
            }
            out
        }
    }

    // ---- error safety net ---------------------------------------------------------------------------------------

    /** Turns any surprise from damaged input (bad index, negative size...) into a SqliteFormatException. */
    private inline fun <T> guard(f: () -> T): T = try {
        f()
    } catch (e: SqliteFormatException) {
        throw e
    } catch (e: RuntimeException) {
        throw SqliteFormatException("The database is damaged (${e.javaClass.simpleName})")
    }

    // ---- raw reads ----------------------------------------------------------------------------------------------

    private fun bad(msg: String): Nothing = throw SqliteFormatException(msg)

    private fun u16(o: Int): Int = ((b[o].toInt() and 0xFF) shl 8) or (b[o + 1].toInt() and 0xFF)

    private fun u32(o: Int): Long =
        ((b[o].toLong() and 0xFF) shl 24) or ((b[o + 1].toLong() and 0xFF) shl 16) or
            ((b[o + 2].toLong() and 0xFF) shl 8) or (b[o + 3].toLong() and 0xFF)

    private fun pageStart(page: Int): Int {
        if (page < 1 || page > pageCount) bad("Page $page is outside the file")
        return (page - 1) * pageSize
    }

    /** A cursor over a byte array with SQLite's big-endian varints; every read is bounds-checked. */
    private inner class Cursor(val buf: ByteArray, var pos: Int, val end: Int) {
        fun need(n: Int) { if (n < 0 || pos + n > end) bad("A record runs past its page") }

        fun varint(): Long {
            var v = 0L
            for (i in 0 until 8) {
                need(1)
                val x = buf[pos++].toInt() and 0xFF
                v = (v shl 7) or (x and 0x7F).toLong()
                if (x and 0x80 == 0) return v
            }
            need(1)
            return (v shl 8) or (buf[pos++].toLong() and 0xFF)
        }

        fun intBE(n: Int): Long {
            need(n)
            var v = if (buf[pos].toInt() < 0) -1L else 0L // sign-extend
            for (i in 0 until n) v = (v shl 8) or (buf[pos + i].toLong() and 0xFF)
            pos += n
            return v
        }
    }

    // ---- sqlite_master ------------------------------------------------------------------------------------------

    private fun readMaster() {
        walkTable(1, Int.MAX_VALUE) { _, r ->
            val type = r.getOrNull(0) as? String
            val name = r.getOrNull(1) as? String
            val root = (r.getOrNull(3) as? Long)?.toInt() ?: 0
            val sql = r.getOrNull(4) as? String
            if (type == "table" && !name.isNullOrBlank() && !name.startsWith("sqlite_", ignoreCase = true)) {
                if (master.putIfAbsent(name.lowercase(), TableInfo(name, root, sql)) == null) tableOrder += name
            }
        }
    }

    // ---- b-tree walking -----------------------------------------------------------------------------------------

    /** Calls [onRow] for every row of the table b-tree at [root], in rowid order, until [limit] rows were seen. */
    private fun walkTable(root: Int, limit: Int, onRow: (Long, List<Any?>) -> Unit) {
        val visited = HashSet<Int>()
        var count = 0
        // Every byte of a healthy file belongs to one cell at most, so the payloads of one walk can't add up to more than
        // the file. Damaged cells that overlap or share overflow pages would otherwise make a small file cost gigabytes.
        payloadBudget = b.size.toLong() + 4096
        fun walk(page: Int, depth: Int): Boolean { // false = stop (limit reached)
            if (depth > MAX_DEPTH) bad("The b-tree is too deep")
            if (!visited.add(page)) bad("The b-tree loops back on itself")
            val base = pageStart(page)
            val h = base + if (page == 1) 100 else 0
            when (b[h].toInt() and 0xFF) {
                0x0D -> {
                    val cells = u16(h + 3)
                    val ptrs = h + 8
                    if (ptrs + 2 * cells > base + pageSize) bad("Cell list runs past the page")
                    for (i in 0 until cells) {
                        val cell = base + u16(ptrs + 2 * i)
                        if (cell < ptrs || cell >= base + pageSize) bad("Cell offset outside its page")
                        val c = Cursor(b, cell, base + pageSize)
                        val payload = c.varint()
                        val rowid = c.varint()
                        onRow(rowid, parseRecord(readPayload(c, payload)))
                        if (++count >= limit) return false
                    }
                    return true
                }
                0x05 -> {
                    val cells = u16(h + 3)
                    val ptrs = h + 12
                    if (ptrs + 2 * cells > base + pageSize) bad("Cell list runs past the page")
                    for (i in 0 until cells) {
                        val cell = base + u16(ptrs + 2 * i)
                        if (cell < ptrs || cell + 4 > base + pageSize) bad("Cell offset outside its page")
                        if (!walk(u32(cell).toInt(), depth + 1)) return false
                    }
                    return walk(u32(h + 8).toInt(), depth + 1)
                }
                0x0A, 0x02 -> bad("Expected a table but found an index b-tree (WITHOUT ROWID table?)")
                else -> bad("Page $page is not a table b-tree page")
            }
        }
        walk(root, 0)
    }

    /** The cell's payload, with its overflow pages followed (SQLite file format, "Cell Payload Overflow Pages"). */
    private fun readPayload(c: Cursor, size: Long): ByteArray {
        if (size < 0 || size > b.size) bad("A record is bigger than the file")
        payloadBudget -= size
        if (payloadBudget < 0) bad("The cells overlap (damaged file)")
        val p = size.toInt()
        val x = usable - 35
        val local = if (p <= x) p else {
            val m = ((usable - 12) * 32 / 255) - 23
            val k = m + ((p - m) % (usable - 4))
            if (k <= x) k else m
        }
        c.need(local + if (local < p) 4 else 0)
        val out = ByteArray(p)
        System.arraycopy(b, c.pos, out, 0, local)
        if (local < p) {
            var next = u32(c.pos + local)
            var done = local
            while (done < p) {
                if (next < 1 || next > pageCount) bad("Overflow page $next is outside the file")
                val at = pageStart(next.toInt())
                val take = minOf(p - done, usable - 4)
                System.arraycopy(b, at + 4, out, done, take)
                done += take
                if (done < p) next = u32(at) // the chain is bounded by p, which is bounded by the file size
            }
        }
        return out
    }

    /** One record (SQLite file format, "Record Format") as a list of Long / Double / String / ByteArray / null. */
    private fun parseRecord(payload: ByteArray): List<Any?> {
        val c = Cursor(payload, 0, payload.size)
        val headerEnd = c.varint()
        if (headerEnd < 1 || headerEnd > payload.size) bad("Bad record header")
        val types = ArrayList<Long>()
        val hdr = Cursor(payload, c.pos, headerEnd.toInt())
        while (hdr.pos < hdr.end) {
            types += hdr.varint()
            if (types.size > MAX_COLUMNS) bad("Too many columns in a record")
        }
        val body = Cursor(payload, headerEnd.toInt(), payload.size)
        val out = ArrayList<Any?>(types.size)
        for (t in types) {
            out += when {
                t == 0L -> null
                t in 1L..4L -> body.intBE(t.toInt())
                t == 5L -> body.intBE(6)
                t == 6L -> body.intBE(8)
                t == 7L -> java.lang.Double.longBitsToDouble(body.intBE(8))
                t == 8L -> 0L
                t == 9L -> 1L
                t == 10L || t == 11L -> bad("Unknown value type $t")
                else -> {
                    val len = if (t % 2 == 0L) (t - 12) / 2 else (t - 13) / 2
                    if (len > payload.size) bad("A value is bigger than its record")
                    body.need(len.toInt())
                    val bytes = payload.copyOfRange(body.pos, body.pos + len.toInt())
                    body.pos += len.toInt()
                    if (t % 2 == 0L) bytes else String(bytes, charset)
                }
            }
        }
        return out
    }

    // ---- CREATE TABLE parsing ------------------------------------------------------------------------------------

    /** Text with comments turned into spaces, quotes kept (SQLite stores the CREATE statement as typed). */
    private fun stripComments(sql: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < sql.length) {
            val ch = sql[i]
            when {
                ch == '\'' || ch == '"' || ch == '`' || ch == '[' -> {
                    val close = if (ch == '[') ']' else ch
                    var j = i + 1
                    while (j < sql.length) {
                        if (sql[j] == close) {
                            if (close != ']' && j + 1 < sql.length && sql[j + 1] == close) { j += 2; continue } // doubled quote
                            break
                        }
                        j++
                    }
                    val e = minOf(j, sql.length - 1)
                    sb.append(sql, i, e + 1)
                    i = e + 1
                }
                ch == '-' && sql.startsWith("--", i) -> {
                    while (i < sql.length && sql[i] != '\n') i++
                    sb.append(' ')
                }
                ch == '/' && sql.startsWith("/*", i) -> {
                    val e = sql.indexOf("*/", i + 2)
                    i = if (e < 0) sql.length else e + 2
                    sb.append(' ')
                }
                else -> { sb.append(ch); i++ }
            }
        }
        return sb.toString()
    }

    /** Index just past the quoted run starting at [i] (which holds the opening quote). */
    private fun skipQuoted(s: String, i: Int): Int {
        val open = s[i]
        val close = if (open == '[') ']' else open
        var j = i + 1
        while (j < s.length) {
            if (s[j] == close) {
                if (close != ']' && j + 1 < s.length && s[j + 1] == close) { j += 2; continue }
                return j + 1
            }
            j++
        }
        return s.length
    }

    private fun isQuote(ch: Char) = ch == '\'' || ch == '"' || ch == '`' || ch == '['

    /** Index of the first '(' outside quotes, or -1. */
    private fun firstParen(s: String): Int {
        var i = 0
        while (i < s.length) {
            val ch = s[i]
            if (isQuote(ch)) { i = skipQuoted(s, i); continue }
            if (ch == '(') return i
            i++
        }
        return -1
    }

    /** Index of the ')' that closes the '(' at [open]. */
    private fun matchParen(s: String, open: Int): Int {
        var depth = 0
        var i = open
        while (i < s.length) {
            val ch = s[i]
            if (isQuote(ch)) { i = skipQuoted(s, i); continue }
            if (ch == '(') depth++
            if (ch == ')' && --depth == 0) return i
            i++
        }
        bad("Unbalanced parentheses in a table definition")
    }

    /** The text after the column list (for WITHOUT ROWID / STRICT). */
    private fun afterColumns(sql: String): String {
        val s = stripComments(sql)
        val open = firstParen(s)
        if (open < 0) return ""
        return s.substring(matchParen(s, open) + 1)
    }

    /** Splits [s] on commas that are outside quotes and parentheses. */
    private fun splitTop(s: String): List<String> {
        val parts = ArrayList<String>()
        var depth = 0
        var start = 0
        var i = 0
        while (i < s.length) {
            val ch = s[i]
            if (isQuote(ch)) { i = skipQuoted(s, i); continue }
            if (ch == '(') depth++
            if (ch == ')') depth--
            if (ch == ',' && depth == 0) { parts += s.substring(start, i); start = i + 1 }
            i++
        }
        parts += s.substring(start)
        return parts
    }

    /** First token of [s]: an unquoted word, or a quoted identifier with the quotes removed. Returns (token, wasQuoted, rest). */
    private fun firstToken(s: String): Triple<String, Boolean, String> {
        val t = s.trimStart()
        if (t.isEmpty()) return Triple("", false, "")
        if (isQuote(t[0])) {
            val end = skipQuoted(t, 0)
            val close = if (t[0] == '[') ']' else t[0]
            val inner = t.substring(1, maxOf(1, end - 1))
            return Triple(if (close == ']') inner else inner.replace("$close$close", "$close"), true, t.substring(end))
        }
        var j = 0
        while (j < t.length && !t[j].isWhitespace() && t[j] != '(' && t[j] != ',') j++
        return Triple(t.substring(0, j), false, t.substring(j))
    }

    private fun columnDefs(sql: String?): List<ColumnDef> {
        if (sql == null) bad("A table has no definition")
        val s = stripComments(sql)
        val open = firstParen(s)
        if (open < 0) bad("A table definition has no column list")
        val body = s.substring(open + 1, matchParen(s, open))
        val defs = ArrayList<ColumnDef>()
        val types = HashMap<String, String>()
        var tablePk: String? = null
        for (seg in splitTop(body)) {
            val (name, quoted, rest) = firstToken(seg)
            if (name.isEmpty()) continue
            if (!quoted && name.uppercase() in setOf("CONSTRAINT", "PRIMARY", "UNIQUE", "CHECK", "FOREIGN")) {
                // A single-column "PRIMARY KEY(x)" makes x the rowid alias when x is declared INTEGER.
                val m = Regex("""^\s*(?:CONSTRAINT\s+\S+\s+)?PRIMARY\s+KEY\s*\(([^,()]*)\)""", RegexOption.IGNORE_CASE).find(seg)
                if (m != null) tablePk = firstToken(m.groupValues[1]).first
                continue
            }
            val top = topLevelWords(rest)
            val typeEnd = top.indexOfFirst { it.uppercase() in CONSTRAINT_WORDS }
            val type = (if (typeEnd < 0) top else top.take(typeEnd)).joinToString(" ") { it.uppercase() }
            types[name.lowercase()] = type
            val generated = generatedKind(rest)
            val pk = top.windowed(2).any { it[0].equals("PRIMARY", true) && it[1].equals("KEY", true) }
            defs += ColumnDef(
                name, virtual = generated == Generated.VIRTUAL, rowidAlias = pk && type == "INTEGER" && generated == null,
                real = listOf("REAL", "FLOA", "DOUB").any { it in type },
            )
        }
        if (defs.size > MAX_COLUMNS) bad("Too many columns")
        if (tablePk != null && defs.none { it.rowidAlias }) {
            val pk = tablePk
            if (types[pk.lowercase()] == "INTEGER") {
                return defs.map { if (it.name.equals(pk, true)) ColumnDef(it.name, it.virtual, true, it.real) else it }
            }
        }
        return defs
    }

    private enum class Generated { VIRTUAL, STORED }

    /** The words of a column definition that sit outside parentheses and quotes (quoted runs and (...) become one word each). */
    private fun topLevelWords(s: String): List<String> {
        val words = ArrayList<String>()
        val cur = StringBuilder()
        fun flush() { if (cur.isNotEmpty()) { words += cur.toString(); cur.clear() } }
        var i = 0
        while (i < s.length) {
            val ch = s[i]
            when {
                isQuote(ch) -> { val e = skipQuoted(s, i); flush(); words += "'q'"; i = e }
                ch == '(' -> { val e = matchParen(s, i); flush(); words += "(...)"; i = e + 1 }
                ch.isWhitespace() || ch == ',' -> { flush(); i++ }
                else -> { cur.append(ch); i++ }
            }
        }
        flush()
        return words
    }

    /** `[GENERATED ALWAYS] AS (expr) [STORED|VIRTUAL]` -> which kind, or null for an ordinary column. */
    private fun generatedKind(def: String): Generated? {
        val w = topLevelWords(def)
        val i = w.indexOfFirst { it.equals("AS", true) }
        if (i < 0 || w.getOrNull(i + 1) != "(...)") return null
        return if (w.getOrNull(i + 2)?.equals("STORED", true) == true) Generated.STORED else Generated.VIRTUAL
    }
}
