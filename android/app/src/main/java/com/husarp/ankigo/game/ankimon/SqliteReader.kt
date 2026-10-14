package com.husarp.ankigo.game.ankimon

import java.io.File
import java.nio.charset.Charset

// TICKET T19 owns this file; T51 added image() and moved the CREATE TABLE parsing to SqlSchema.

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

        /** Reads [file] fully; refuses files over [maxBytes] (default 64 MB). */
        fun open(file: File, maxBytes: Long = 64L shl 20): SqliteReader {
            val len = file.length()
            if (len > maxBytes) throw SqliteFormatException("The file is too big (${len / 1_000_000} MB)")
            return SqliteReader(file.readBytes())
        }
    }

    private class TableInfo(val name: String, val root: Int, val sql: String?)

    /** One `sqlite_master` row (the root page stays here: the writer gives every b-tree new pages). */
    private class MasterRow(val type: String?, val name: String?, val tblName: String?, val root: Int, val sql: String?)

    private val pageSize: Int
    private val usable: Int
    private val pageCount: Int
    private val charset: Charset
    private var payloadBudget = 0L                            // see walkTable
    private val master = LinkedHashMap<String, TableInfo>() // lower-case name -> table
    private val tableOrder = mutableListOf<String>()        // names as declared, type = table, not sqlite_*
    private val masterRows = mutableListOf<MasterRow>()     // every sqlite_master row, in file order

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
        return guard { SqlSchema.parseTable(t.sql).columns.map { it.name } }
    }

    /**
     * Rows as column-name -> value (Long, Double, String, ByteArray or null), at most [limit]. Virtual generated
     * columns are not stored in the file and are left out. Unknown table -> empty.
     */
    fun rows(table: String, limit: Int = 100_000): List<Map<String, Any?>> {
        val t = master[table.lowercase()] ?: return emptyList()
        if (limit <= 0) return emptyList()
        return guard {
            val def = SqlSchema.parseTable(t.sql)
            if (def.withoutRowid) throw SqliteFormatException("Table ${t.name} is WITHOUT ROWID, which this reader doesn't support")
            val stored = def.storedColumns
            val out = ArrayList<Map<String, Any?>>()
            walkTable(t.root, limit) { rowid, record ->
                val row = LinkedHashMap<String, Any?>()
                for ((i, c) in stored.withIndex()) {
                    val v = if (i == def.storedAlias) rowid else record.getOrNull(i)
                    // SQLite stores a REAL that is a whole number as an integer; the column's type brings it back.
                    row[c.name] = if (c.affinity == SqlSchema.Affinity.REAL && v is Long) v.toDouble() else v
                }
                out += row
            }
            out
        }
    }

    /**
     * The whole database as plain values (see [SqliteImage]): every `sqlite_master` row in file order and every row of every
     * table, internal ones (sqlite_sequence, sqlite_stat1) included. Records shorter than their table (columns added later
     * by ALTER TABLE ADD COLUMN) are completed with the column's DEFAULT, as SQLite itself reads them.
     * Throws [SqliteFormatException] for what can't be copied faithfully: WITHOUT ROWID and virtual tables, a row with more
     * values than columns, a default that is an expression and is needed to complete a row, more than [maxRows] rows
     * in all, tables that share their pages, a damaged file.
     */
    fun image(maxRows: Int = 500_000): SqliteImage = guard {
        val objects = ArrayList<SqlObject>()
        val tables = LinkedHashMap<String, SqlTable>()
        var rowsLeft = maxRows
        var payloads = 0L // healthy tables never share a byte, so all payloads together fit in the file
        for (m in masterRows) {
            val type = m.type
            val name = m.name
            val tbl = m.tblName
            if (type == null || name == null || tbl == null) bad("A row of sqlite_master is damaged")
            objects += SqlObject(type, name, tbl, m.sql)
            if (type != "table") continue
            val t = readTable(m, rowsLeft)
            rowsLeft -= t.rows.size
            payloads += b.size + 4096L - payloadBudget
            if (payloads > b.size + 4096L) bad("The tables overlap (damaged file)")
            if (tables.put(name.lowercase(), t) != null) bad("Table $name is listed twice")
        }
        SqliteImage(objects, tables, pageSize, u32(60).toInt(), u32(68).toInt())
    }

    private fun readTable(m: MasterRow, rowsLeft: Int): SqlTable {
        val name = m.name!!
        val def = SqlSchema.parseTable(m.sql)
        if (def.withoutRowid) bad("Table $name is WITHOUT ROWID, which can't be copied")
        val stored = def.storedColumns
        val alias = def.storedAlias
        val pads = arrayOfNulls<Any>(stored.size)
        val padded = BooleanArray(stored.size)
        fun pad(i: Int): Any? {
            if (!padded[i]) {
                val c = stored[i]
                if (c.hasDefault) {
                    if (c.defaultProblem != null) bad("A row of $name is shorter than the table and ${c.name} has a ${c.defaultProblem}")
                    pads[i] = try { SqlValues.apply(c.default, c.affinity) } catch (e: IllegalArgumentException) { bad("Default of $name.${c.name}: ${e.message}") }
                }
                padded[i] = true
            }
            return pads[i]
        }
        val rows = ArrayList<SqlRow>()
        walkTable(m.root, minOf(rowsLeft, Int.MAX_VALUE - 1) + 1) { rowid, rec ->
            if (rows.size >= rowsLeft) bad("The database has more rows than the limit")
            if (rec.size > stored.size) bad("A row of $name has more values than the table has columns")
            val v = ArrayList<Any?>(stored.size)
            for (i in stored.indices) {
                val x = if (i == alias) rowid else if (i < rec.size) rec[i] else pad(i)
                v += if (stored[i].affinity == SqlSchema.Affinity.REAL && x is Long) x.toDouble() else x
            }
            rows += SqlRow(rowid, v)
        }
        return SqlTable(name, stored.map { it.name }, alias, rows)
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
            masterRows += MasterRow(type, name, r.getOrNull(2) as? String, root, sql)
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
}
