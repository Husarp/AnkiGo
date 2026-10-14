package com.husarp.ankigo.game.ankimon

// TICKET T51 owns this file.

/**
 * A structural check of a SQLite file, in the spirit of `PRAGMA integrity_check`, written independently of [SqliteWriter]
 * (it walks the bytes itself): header, every page used exactly once, cells that tile their page, b-tree keys in strict order,
 * every record parses, every table row has its columns, and every index holds exactly the keys recomputed from its table.
 * Built for the files our writer produces (UTF-8, no free pages): other valid files may be reported as well.
 */
object SqliteCheck {
    /**
     * The problems found; an empty list means the file is fine. At most 50 are listed. [strictPages] (default) also demands
     * what the writer produces: cells that exactly fill the content area, no free blocks. Pass false for files made by
     * SQLite itself, which leaves free space inside pages after UPDATE and DELETE.
     */
    fun check(bytes: ByteArray, strictPages: Boolean = true): List<String> {
        val c = Check(bytes, strictPages)
        try {
            c.run()
        } catch (e: Exception) {
            c.add(if (e is Damage) e.message ?: "damaged" else "The file is damaged (${e.javaClass.simpleName}: ${e.message})")
        }
        return c.problems
    }

    private class Damage(msg: String) : RuntimeException(msg)

    private class Page(val no: Int, val base: Int, val flag: Int, val n: Int, val ptrs: Int)

    private class RowData(val rowid: Long, val values: List<Any?>)

    private class Obj(val type: String, val name: String, val tbl: String, val root: Int, val sql: String?)

    private class TData(val def: SqlSchema.Table, val rows: List<RowData>) {
        val rowids = LongArray(rows.size) { rows[it].rowid }
        var gens: Map<Int, Array<Any?>>? = null
    }

    private class Check(val b: ByteArray, val strict: Boolean) {
        val problems = ArrayList<String>()
        var ps = 0
        var pageCount = 0
        lateinit var used: IntArray

        fun add(m: String) { if (problems.size < 50) problems += m }

        fun damage(m: String): Nothing = throw Damage(m)

        fun u16(o: Int) = ((b[o].toInt() and 0xFF) shl 8) or (b[o + 1].toInt() and 0xFF)

        fun u32(o: Int): Long = (u16(o).toLong() shl 16) or u16(o + 2).toLong()

        fun run() {
            header()
            used = IntArray(pageCount + 1)
            val master = ArrayList<Obj>()
            val rootRows = walkTable(1, "sqlite_master")
            for (r in rootRows) {
                val v = r.values
                if (v.size != 5 || v[0] !is String || v[1] !is String || v[2] !is String || v[3] !is Long || (v[4] != null && v[4] !is String)) {
                    add("sqlite_master row ${r.rowid} is malformed")
                    continue
                }
                val type = v[0] as String
                if (type !in setOf("table", "index", "view", "trigger")) add("sqlite_master row ${r.rowid} has the type $type")
                if ((type == "view" || type == "trigger") != ((v[3] as Long) == 0L)) add("sqlite_master row ${r.rowid} has a wrong root page")
                if (v[4] == null && type != "index") add("sqlite_master row ${r.rowid} has no sql")
                master += Obj(type, v[1] as String, v[2] as String, (v[3] as Long).toInt(), v[4] as String?)
            }
            val tables = HashMap<String, TData>()
            val failed = HashSet<String>()
            for (o in master) if (o.type == "table") {
                try {
                    val def = SqlSchema.parseTable(o.sql)
                    if (def.withoutRowid) damage("Table ${o.name} is WITHOUT ROWID")
                    val rows = walkTable(o.root, "table ${o.name}")
                    tables[o.name.lowercase()] = TData(def, rows)
                    checkRows(o, def, rows)
                } catch (e: Exception) {
                    failed += o.name.lowercase()
                    add(if (e is Damage) e.message ?: "damaged" else "Table ${o.name}: ${e.message}")
                }
            }
            val auto = HashMap<String, Pair<String, SqlSchema.Unique>>()
            for (o in master) if (o.type == "table") tables[o.name.lowercase()]?.def?.autoIndexes?.forEachIndexed { k, u ->
                auto[SqlSchema.autoIndexName(o.name, k + 1)] = o.name to u
            }
            for (o in master) if (o.type == "index") {
                try {
                    val entries = walkIndex(o.root, "index ${o.name}")
                    if (o.tbl.lowercase() in failed) continue
                    checkIndex(o, entries, tables[o.tbl.lowercase()] ?: damage("Index ${o.name} is on the missing table ${o.tbl}"), auto.remove(o.name))
                } catch (e: Exception) {
                    add(if (e is Damage) e.message ?: "damaged" else "Index ${o.name}: ${e.message}")
                }
            }
            for ((name, _) in auto) add("sqlite_master lacks the automatic index $name")
            for (p in 1..pageCount) if (used[p] == 0) add("Page $p is not part of any b-tree")
            checkSequence(master, tables)
        }

        // ---- header -----------------------------------------------------------------------------------------------------

        fun header() {
            if (b.size < 100) damage("The file is too short for a SQLite header")
            val magic = "SQLite format 3\u0000".toByteArray(Charsets.ISO_8859_1)
            for (i in magic.indices) if (b[i] != magic[i]) damage("Wrong magic header")
            val raw = u16(16)
            ps = if (raw == 1) 65536 else raw
            if (ps < 512 || ps > 65536 || ps and (ps - 1) != 0) damage("Bad page size $ps")
            if (b.size % ps != 0) damage("The file size is not a whole number of pages")
            pageCount = b.size / ps
            if (u32(28) != pageCount.toLong()) add("The header says ${u32(28)} pages, the file has $pageCount")
            if (b[18].toInt() != 1 || b[19].toInt() != 1) add("Not a rollback-journal database (versions ${b[18]}/${b[19]})")
            if (b[20].toInt() != 0) add("Reserved bytes per page: ${b[20]}")
            if (b[21].toInt() != 64 || b[22].toInt() != 32 || b[23].toInt() != 32) add("Unusual payload fractions")
            if (u32(24) != u32(92)) add("Change counter and version-valid-for differ")
            if (u32(32) != 0L || u32(36) != 0L) add("The file has a free list")
            if (u32(44) != 4L) add("Schema format ${u32(44)}")
            if (u32(52) != 0L || u32(64) != 0L) add("Auto-vacuum is on")
            if (u32(56) != 1L) add("Text encoding ${u32(56)} is not UTF-8")
        }

        // ---- pages ------------------------------------------------------------------------------------------------------

        fun localSize(p: Int, tableLeaf: Boolean): Int {
            val x = if (tableLeaf) ps - 35 else ((ps - 12) * 64 / 255) - 23
            if (p <= x) return p
            val m = ((ps - 12) * 32 / 255) - 23
            val k = m + ((p - m) % (ps - 4))
            return if (k <= x) k else m
        }

        fun varint(at: Int): Pair<Long, Int> { // value, length
            var v = 0L
            for (i in 0 until 8) {
                if (at + i >= b.size) damage("A varint runs past the end of the file")
                val x = b[at + i].toInt() and 0xFF
                v = (v shl 7) or (x and 0x7F).toLong()
                if (x and 0x80 == 0) return v to i + 1
            }
            if (at + 8 >= b.size) damage("A varint runs past the end of the file")
            return ((v shl 8) or (b[at + 8].toLong() and 0xFF)) to 9
        }

        class Cell(val size: Int, val child: Int, val key: Long, val payload: Int, val payloadAt: Int, val local: Int)

        /** Opens b-tree page [no] and checks its header and that its cells exactly fill the content area. */
        fun open(no: Int, what: String): Page {
            if (no < 1 || no > pageCount) damage("$what: page $no is outside the file")
            if (++used[no] > 1) damage("$what: page $no is used twice")
            val base = (no - 1) * ps
            val h = base + if (no == 1) 100 else 0
            val flag = b[h].toInt() and 0xFF
            if (flag != 0x0D && flag != 0x05 && flag != 0x0A && flag != 0x02) damage("$what: page $no is not a b-tree page (type $flag)")
            val interior = flag == 0x05 || flag == 0x02
            if (strict && u16(h + 1) != 0) add("$what: page $no has a free block")
            if (strict && b[h + 7].toInt() != 0) add("$what: page $no has fragmented bytes")
            val n = u16(h + 3)
            val ptrs = h + if (interior) 12 else 8
            if (ptrs + 2 * n > base + ps) damage("$what: page $no: the cell list runs past the page")
            val page = Page(no, base, flag, n, ptrs)
            val content = u16(h + 5).let { if (it == 0) 65536 else it }
            val offs = IntArray(n) { u16(ptrs + 2 * it) }
            val order = (0 until n).sortedBy { offs[it] }
            var pos = content
            var tiled = true
            for (i in order) {
                if (if (strict) offs[i] != pos else offs[i] < pos) { tiled = false; break }
                pos = offs[i] + cell(page, i).size
            }
            if (!tiled || (if (strict) pos != ps else pos > ps)) add("$what: page $no: the cells ${if (strict) "don't fill the content area exactly" else "overlap or leave the page"}")
            return page
        }

        fun cell(p: Page, i: Int): Cell {
            val at = p.base + u16(p.ptrs + 2 * i)
            if (at < p.ptrs + 2 * p.n || at >= p.base + ps) damage("page ${p.no}: cell $i starts outside the cell area")
            var o = at
            var child = 0
            val interior = p.flag == 0x05 || p.flag == 0x02
            if (interior) { child = u32(o).toInt(); o += 4 }
            if (p.flag == 0x05) {
                val (key, len) = varint(o)
                return Cell(o + len - at, child, key, 0, 0, 0)
            }
            val (size, l1) = varint(o)
            o += l1
            var key = 0L
            if (p.flag == 0x0D) { val (k, l2) = varint(o); key = k; o += l2 }
            if (size < 1 || size > b.size) damage("page ${p.no}: a record of $size bytes")
            val local = localSize(size.toInt(), p.flag == 0x0D)
            val end = o + local + if (local < size) 4 else 0
            if (end > p.base + ps) damage("page ${p.no}: cell $i runs past the page")
            return Cell(end - at, child, key, size.toInt(), o, local)
        }

        /** The record bytes of a cell, with its overflow chain followed (and its pages counted). */
        fun payload(c: Cell, what: String): ByteArray {
            val out = ByteArray(c.payload)
            System.arraycopy(b, c.payloadAt, out, 0, c.local)
            if (c.local < c.payload) {
                var next = u32(c.payloadAt + c.local)
                var done = c.local
                while (done < c.payload) {
                    if (next < 1 || next > pageCount) damage("$what: overflow page $next is outside the file")
                    if (++used[next.toInt()] > 1) damage("$what: overflow page $next is used twice")
                    val at = (next.toInt() - 1) * ps
                    val take = minOf(c.payload - done, ps - 4)
                    System.arraycopy(b, at + 4, out, done, take)
                    done += take
                    val following = u32(at)
                    if (done < c.payload) next = following else if (following != 0L) add("$what: an overflow chain is longer than its record")
                }
            }
            return out
        }

        fun record(c: Cell, what: String): List<Any?> = try {
            SqlValues.decode(payload(c, what))
        } catch (e: IllegalArgumentException) {
            damage("$what: ${e.message}")
        }

        fun walkTable(root: Int, what: String): List<RowData> {
            val out = ArrayList<RowData>()
            var leafDepth = -1
            fun walk(no: Int, depth: Int, lo: Long?, hi: Long?) {
                if (depth > 40) damage("$what: the b-tree is too deep")
                val p = open(no, what)
                when (p.flag) {
                    0x0D -> {
                        if (leafDepth < 0) leafDepth = depth else if (leafDepth != depth) add("$what: leaves at different depths")
                        var prev = lo
                        for (i in 0 until p.n) {
                            val c = cell(p, i)
                            if ((prev != null && c.key <= prev) || (hi != null && c.key > hi)) add("$what: rowid ${c.key} is out of order on page $no")
                            prev = c.key
                            out += RowData(c.key, record(c, what))
                        }
                    }
                    0x05 -> {
                        var prev = lo
                        for (i in 0 until p.n) {
                            val c = cell(p, i)
                            if ((prev != null && c.key <= prev) || (hi != null && c.key > hi)) add("$what: divider ${c.key} is out of order on page $no")
                            walk(c.child, depth + 1, prev, c.key)
                            prev = c.key
                        }
                        walk(u32(p.ptrs - 4).toInt(), depth + 1, prev, hi)
                    }
                    else -> damage("$what: page $no is an index page where a table page belongs")
                }
            }
            walk(root, 0, null, null)
            return out
        }

        fun walkIndex(root: Int, what: String): List<List<Any?>> {
            val out = ArrayList<List<Any?>>()
            var leafDepth = -1
            fun entry(p: Page, c: Cell) {
                val e = record(c, what)
                if (out.isNotEmpty() && compareLists(out.last(), e) >= 0) add("$what: entries are out of order on page ${p.no}")
                out += e
            }
            fun walk(no: Int, depth: Int) {
                if (depth > 40) damage("$what: the b-tree is too deep")
                val p = open(no, what)
                when (p.flag) {
                    0x0A -> {
                        if (leafDepth < 0) leafDepth = depth else if (leafDepth != depth) add("$what: leaves at different depths")
                        for (i in 0 until p.n) entry(p, cell(p, i))
                    }
                    0x02 -> {
                        for (i in 0 until p.n) {
                            val c = cell(p, i)
                            walk(c.child, depth + 1)
                            entry(p, c)
                        }
                        walk(u32(p.ptrs - 4).toInt(), depth + 1)
                    }
                    else -> damage("$what: page $no is a table page where an index page belongs")
                }
            }
            walk(root, 0)
            return out
        }

        fun compareLists(a: List<Any?>, b: List<Any?>): Int {
            for (i in 0 until minOf(a.size, b.size)) {
                val c = SqlValues.compare(a[i], b[i])
                if (c != 0) return c
            }
            return a.size - b.size
        }

        // ---- contents ---------------------------------------------------------------------------------------------------

        fun checkRows(o: Obj, def: SqlSchema.Table, rows: List<RowData>) {
            val cols = def.storedColumns
            for (r in rows) {
                if (r.values.size != cols.size) { add("Table ${o.name} row ${r.rowid} has ${r.values.size} values, the table has ${cols.size} stored columns"); return }
                for ((i, c) in cols.withIndex()) {
                    if (i == def.storedAlias) { if (r.values[i] != null) add("Table ${o.name} row ${r.rowid} stores a value in its rowid alias") }
                    else if (c.notNull && r.values[i] == null) add("NOT NULL violated in ${o.name}.${c.name} row ${r.rowid}")
                }
            }
        }

        /** Values of the VIRTUAL json_extract columns, per row, by declared column number. */
        fun generated(t: TData, name: String): Map<Int, Array<Any?>> {
            t.gens?.let { return it }
            val gens = HashMap<Int, Array<Any?>>()
            val specs = t.def.columns.indices.filter { !t.def.columns[it].stored }.mapNotNull { d ->
                SqlSchema.jsonExtractOf(t.def.columns[d].genExpr ?: "")?.let { (src, key) -> Triple(d, t.def.col(src), key) }
            }
            for ((d, _, _) in specs) gens[d] = arrayOfNulls(t.rows.size)
            for ((src, group) in specs.groupBy { it.second }) {
                if (src < 0 || !t.def.columns[src].stored) continue
                val keys = group.map { it.third }.toSet()
                for ((ri, r) in t.rows.withIndex()) {
                    val text = r.values[t.def.storedIndex(src)] ?: continue
                    if (text !is String) damage("Table $name: ${t.def.columns[src].name} of row ${r.rowid} is not text")
                    val m = try { SqlJson.extract(text, keys) } catch (e: IllegalArgumentException) { damage("Table $name row ${r.rowid}: ${e.message}") }
                    for ((d, _, key) in group) {
                        val aff = t.def.columns[d].affinity
                        gens.getValue(d)[ri] = try { SqlValues.forFile(SqlValues.apply(m[key], aff), aff) } catch (e: IllegalArgumentException) { damage("Table $name row ${r.rowid}: ${e.message}") }
                    }
                }
            }
            t.gens = gens
            return gens
        }

        fun checkIndex(o: Obj, entries: List<List<Any?>>, t: TData, auto: Pair<String, SqlSchema.Unique>?) {
            val what = "Index ${o.name}"
            val unique: Boolean
            val cols: List<Int>
            if (o.sql == null) {
                if (auto == null) damage("$what matches no PRIMARY KEY or UNIQUE of ${o.tbl}")
                unique = true
                cols = auto.second.columns
            } else {
                val d = SqlSchema.parseIndex(o.sql)
                if (d.problems.isNotEmpty()) damage("$what: unsupported ${d.problems.first()}")
                unique = d.unique
                cols = d.terms.map { term -> t.def.col(term.column).also { if (it < 0) damage("$what: ${o.tbl} has no column ${term.column}") } }
            }
            val k = cols.size
            if (entries.size != t.rows.size) { add("$what has ${entries.size} entries, table ${o.tbl} has ${t.rows.size} rows"); return }
            val gens = if (cols.any { !t.def.columns[it].stored }) generated(t, o.tbl) else emptyMap()
            for (i in entries.indices) {
                val e = entries[i]
                if (e.size != k + 1 || e[k] !is Long) { add("$what entry $i is malformed"); return }
                val ri = t.rowids.binarySearch(e[k] as Long)
                if (ri < 0) { add("$what entry $i points at the missing rowid ${e[k]}"); return }
                val row = t.rows[ri]
                for ((j, d) in cols.withIndex()) {
                    val c = t.def.columns[d]
                    val want = if (!c.stored) gens[d]?.get(ri) else if (d == t.def.rowidAlias) row.rowid else row.values[t.def.storedIndex(d)]
                    if (SqlValues.compare(e[j], want) != 0) { add("$what entry $i differs from row ${row.rowid} of ${o.tbl} in ${c.name}"); return }
                }
                if (unique && i > 0) {
                    val a = entries[i - 1]
                    if ((0 until k).all { a[it] != null && SqlValues.compare(a[it], e[it]) == 0 }) add("$what: UNIQUE violated at entry $i")
                }
            }
        }

        /** AUTOINCREMENT tables need a sqlite_sequence row with seq >= their largest rowid. */
        fun checkSequence(master: List<Obj>, tables: Map<String, TData>) {
            val auto = master.filter { it.type == "table" && tables[it.name.lowercase()]?.def?.autoincrement == true }
            if (auto.isEmpty()) return
            val seq = tables["sqlite_sequence"] ?: run { add("AUTOINCREMENT tables without sqlite_sequence"); return }
            for (o in auto) {
                val max = tables.getValue(o.name.lowercase()).rows.maxOfOrNull { it.rowid } ?: continue
                if (max <= 0) continue
                val row = seq.rows.firstOrNull { it.values.getOrNull(0) == o.name }
                val v = row?.values?.getOrNull(1)
                if (v !is Long || v < max) add("sqlite_sequence for ${o.name} is ${v ?: "missing"}, the largest rowid is $max")
            }
        }
    }
}
