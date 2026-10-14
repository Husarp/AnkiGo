package com.husarp.ankigo.game.ankimon

import com.husarp.ankigo.game.ankimon.SqlSchema.Gen

// TICKET T51 owns this file.

/**
 * Writes a [SqliteImage] as a complete, new SQLite 3 database file (like VACUUM does): the same schema text, the same rows,
 * fresh pages, every index rebuilt from the rows. Pure Kotlin, so it also works where Android's own SQLite can't open the
 * file (Ankimon's VIRTUAL generated columns need SQLite 3.31) and in JVM tests.
 *
 * The file is what a rollback-journal SQLite 3.46 would hold: UTF-8, no free pages, no gaps, schema format 4. Column
 * affinity is applied to every value as SQLite does on INSERT. NOT NULL and UNIQUE are enforced; indexes over VIRTUAL
 * generated columns are supported for `json_extract(column, '$.key')` only.
 *
 * Refuses (instead of writing a doubtful file): [SqliteFormatException] for schema forms it doesn't support (CHECK, STRICT,
 * WITHOUT ROWID, virtual tables, STORED generated columns, partial / expression / DESC / non-BINARY indexes, ON CONFLICT,
 * automatic indexes that don't match `sqlite_master`), [IllegalArgumentException] for bad data (duplicate rowids, NOT NULL
 * or UNIQUE violations, values SQLite would treat differently than we can reproduce, malformed JSON in an indexed column).
 * The image is never changed.
 * Format reference: https://www.sqlite.org/fileformat2.html
 */
object SqliteWriter {
    fun write(image: SqliteImage): ByteArray = Build(image).run()

    private const val SQLITE_VERSION_NUMBER = 3046001

    /** A row ready for the file: values affinity-applied, the rowid-alias slot holding the rowid (the record gets NULL there). */
    private class Prep(val rowid: Long, val values: Array<Any?>)

    private class Tbl(val obj: SqlObject, val def: SqlSchema.Table, val src: SqlTable) {
        var rows: List<Prep> = emptyList()
    }

    private class Idx(val obj: SqlObject, val tbl: Tbl, val unique: Boolean, val cols: List<Int>)

    private class Build(val img: SqliteImage) {
        val ps = img.pageSize
        val pages = ArrayList<ByteArray?>().also { it.add(null) } // page 1 (sqlite_master's root) is written last

        fun run(): ByteArray {
            require(ps in 512..65536 && ps and (ps - 1) == 0) { "Bad page size $ps" }
            val objs = ArrayList(img.objects)
            checkNames(objs)
            val tbls = collectTables(objs)
            addSequence(objs, tbls)
            for (t in tbls.values) t.rows = prepare(t)
            val idxs = collectIndexes(objs, tbls)
            val gens = generatedValues(tbls, idxs)

            val master = ArrayList<List<Any?>>()
            for (o in objs) {
                var root = 0
                when (o.type) {
                    "table" -> {
                        val t = tbls.getValue(o.name.lowercase())
                        root = tableTree(t.rows.size, { t.rows[it].rowid }, { tableRecord(t, t.rows[it]) }, false)
                    }
                    "index" -> root = indexTree(idxs.getValue(o.name), gens)
                }
                master += listOf(o.type, o.name, o.tblName, root.toLong(), o.sql)
            }
            val root = tableTree(master.size, { it + 1L }, { SqlValues.record(master[it]) }, true)
            check(root == 1)
            return assemble()
        }

        // ---- the schema -----------------------------------------------------------------------------------------------

        fun unsupported(what: String, problems: List<String>) {
            if (problems.isNotEmpty()) throw SqliteFormatException("$what uses something the writer doesn't support: ${problems.distinct().joinToString("; ")}")
        }

        fun checkNames(objs: List<SqlObject>) {
            val seen = HashSet<String>()
            for (o in objs) {
                require(o.type == "table" || o.type == "index" || o.type == "view" || o.type == "trigger") { "Unknown object type '${o.type}'" }
                require(o.name.isNotEmpty()) { "An object has no name" }
                require(seen.add((if (o.type == "trigger") "trigger:" else "") + o.name.lowercase())) { "Two objects are called ${o.name}" }
                if (o.type == "table") require(o.tblName == o.name) { "Table ${o.name} has tbl_name ${o.tblName}" }
                if (o.type != "index") require(o.sql != null) { "${o.type} ${o.name} has no sql" }
            }
        }

        fun collectTables(objs: List<SqlObject>): LinkedHashMap<String, Tbl> {
            val tbls = LinkedHashMap<String, Tbl>()
            for (o in objs) {
                if (o.type != "table") continue
                val def = SqlSchema.parseTable(o.sql)
                unsupported("Table ${o.name}", def.problems)
                if (def.withoutRowid) throw SqliteFormatException("Table ${o.name} is WITHOUT ROWID, which the writer doesn't support")
                val stored = def.columns.firstOrNull { it.gen == Gen.STORED }
                if (stored != null) throw SqliteFormatException("Table ${o.name}.${stored.name} is a STORED generated column, which the writer doesn't support")
                val src = img.tables[o.name.lowercase()] ?: throw IllegalArgumentException("Table ${o.name} has no content in the image")
                require(src.columns == def.storedColumns.map { it.name }) {
                    "The columns of ${o.name} in the image (${src.columns}) don't match its definition (${def.storedColumns.map { it.name }})"
                }
                require(src.rowidAlias == def.storedAlias) { "The rowid alias of ${o.name} doesn't match its definition" }
                tbls[o.name.lowercase()] = Tbl(o, def, src)
            }
            require(img.tables.size == tbls.size && img.tables.keys.all { it in tbls }) { "The image has tables that sqlite_master doesn't list" }
            return tbls
        }

        /** Every AUTOINCREMENT table needs a sqlite_sequence row with seq >= its largest rowid (never lowered). */
        fun addSequence(objs: MutableList<SqlObject>, tbls: LinkedHashMap<String, Tbl>) {
            val auto = tbls.values.filter { it.def.autoincrement }
            if (auto.isEmpty()) return
            if ("sqlite_sequence" !in tbls) {
                val o = SqlObject("table", "sqlite_sequence", "sqlite_sequence", "CREATE TABLE sqlite_sequence(name,seq)")
                objs += o
                tbls["sqlite_sequence"] = Tbl(o, SqlSchema.parseTable(o.sql), SqlTable("sqlite_sequence", listOf("name", "seq"), -1, ArrayList()))
            }
            val old = tbls.getValue("sqlite_sequence")
            require(old.src.columns.size == 2 && old.src.rowidAlias < 0) { "sqlite_sequence must be (name, seq)" }
            val rows = old.src.rows.mapTo(ArrayList()) { SqlRow(it.rowid, it.values.toMutableList()) }
            for (t in auto) {
                val max = t.src.rows.maxOfOrNull { it.rowid } ?: continue
                if (max <= 0) continue
                val r = rows.firstOrNull { it.values[0] == t.obj.name }
                if (r == null) rows += SqlRow((rows.maxOfOrNull { it.rowid } ?: 0L) + 1, mutableListOf(t.obj.name, max))
                else {
                    val seq = r.values[1] as? Long ?: throw IllegalArgumentException("sqlite_sequence has a seq that is not an integer for ${t.obj.name}")
                    if (seq < max) r.values[1] = max
                }
            }
            tbls["sqlite_sequence"] = Tbl(old.obj, old.def, SqlTable("sqlite_sequence", old.src.columns, -1, rows))
        }

        fun prepare(t: Tbl): List<Prep> {
            val cols = t.def.storedColumns
            val alias = t.def.storedAlias
            val sorted = t.src.rows.sortedBy { it.rowid }
            val out = ArrayList<Prep>(sorted.size)
            var prev = 0L
            for ((n, r) in sorted.withIndex()) {
                require(r.values.size == cols.size) { "A row of ${t.obj.name} has ${r.values.size} values, the table has ${cols.size} stored columns" }
                require(n == 0 || r.rowid != prev) { "Table ${t.obj.name} has the rowid ${r.rowid} twice" }
                prev = r.rowid
                val v = arrayOfNulls<Any>(cols.size)
                for (i in cols.indices) {
                    val c = cols[i]
                    if (i == alias) {
                        require(SqlValues.normalize(r.values[i]) == r.rowid) { "${t.obj.name}.${c.name} (the rowid alias) differs from the rowid ${r.rowid}" }
                        v[i] = r.rowid
                        continue
                    }
                    val x = try {
                        SqlValues.forFile(SqlValues.apply(r.values[i], c.affinity), c.affinity)
                    } catch (e: IllegalArgumentException) {
                        throw IllegalArgumentException("${t.obj.name}.${c.name} (rowid ${r.rowid}): ${e.message}")
                    }
                    if (x == null && c.notNull) throw IllegalArgumentException("NOT NULL constraint failed: ${t.obj.name}.${c.name} (rowid ${r.rowid})")
                    v[i] = x
                }
                out += Prep(r.rowid, v)
            }
            return out
        }

        fun tableRecord(t: Tbl, r: Prep): ByteArray {
            val l = ArrayList<Any?>(r.values.size)
            for (v in r.values) l += v
            if (t.def.storedAlias >= 0) l[t.def.storedAlias] = null
            return SqlValues.record(l)
        }

        fun collectIndexes(objs: List<SqlObject>, tbls: Map<String, Tbl>): Map<String, Idx> {
            val auto = LinkedHashMap<String, Pair<Tbl, SqlSchema.Unique>>()
            for (t in tbls.values) t.def.autoIndexes.forEachIndexed { k, u -> auto[SqlSchema.autoIndexName(t.obj.name, k + 1)] = t to u }
            val out = LinkedHashMap<String, Idx>()
            for (o in objs) {
                if (o.type != "index") continue
                val t = tbls[o.tblName.lowercase()] ?: throw IllegalArgumentException("Index ${o.name} is on an unknown table ${o.tblName}")
                if (o.sql == null) {
                    val (at, u) = auto.remove(o.name)
                        ?: throw SqliteFormatException("Automatic index ${o.name} matches no PRIMARY KEY or UNIQUE of ${o.tblName}")
                    if (at !== t) throw SqliteFormatException("Automatic index ${o.name} belongs to another table")
                    out[o.name] = Idx(o, t, true, u.columns)
                    continue
                }
                val d = SqlSchema.parseIndex(o.sql)
                unsupported("Index ${o.name}", d.problems)
                if (!d.table.equals(o.tblName, ignoreCase = true)) throw SqliteFormatException("Index ${o.name} is on ${d.table}, sqlite_master says ${o.tblName}")
                if (d.terms.isEmpty()) throw SqliteFormatException("Index ${o.name} has no columns")
                val cols = d.terms.map { term ->
                    val c = t.def.col(term.column)
                    if (c < 0) throw SqliteFormatException("Index ${o.name}: ${o.tblName} has no column ${term.column}")
                    if (term.desc) throw SqliteFormatException("Index ${o.name} sorts DESC, which the writer doesn't support")
                    val coll = term.collation ?: t.def.columns[c].collation ?: "BINARY"
                    if (coll != "BINARY") throw SqliteFormatException("Index ${o.name} uses the collation $coll, which the writer doesn't support")
                    c
                }
                out[o.name] = Idx(o, t, d.unique, cols)
            }
            if (auto.isNotEmpty()) throw SqliteFormatException("sqlite_master lacks the automatic index ${auto.keys.first()}")
            return out
        }

        // ---- indexes over generated columns ---------------------------------------------------------------------------

        private class GenCol(val declared: Int, val src: Int, val key: String)

        /** For every table, per row, the values of the VIRTUAL generated columns that some index uses (by declared column number). */
        fun generatedValues(tbls: Map<String, Tbl>, idxs: Map<String, Idx>): Map<Tbl, Map<Int, Array<Any?>>> {
            val out = HashMap<Tbl, Map<Int, Array<Any?>>>()
            for (t in tbls.values) {
                val used = idxs.values.filter { it.tbl === t }.flatMap { it.cols }.filter { !t.def.columns[it].stored }.distinct()
                if (used.isEmpty()) continue
                val gens = used.map { d ->
                    val c = t.def.columns[d]
                    val (src, key) = SqlSchema.jsonExtractOf(c.genExpr ?: "")
                        ?: throw SqliteFormatException("${t.obj.name}.${c.name} is indexed but is not json_extract(column, '\$.key'): ${c.genExpr}")
                    val s = t.def.col(src)
                    if (s < 0 || !t.def.columns[s].stored) throw SqliteFormatException("${t.obj.name}.${c.name} reads ${src}, which is not a stored column")
                    GenCol(d, s, key)
                }
                val values = gens.associate { it.declared to arrayOfNulls<Any>(t.rows.size) }
                val bySource = gens.groupBy { it.src }
                val keys = bySource.mapValues { e -> e.value.map { it.key }.toSet() }
                for ((ri, r) in t.rows.withIndex()) {
                    for ((src, group) in bySource) {
                        val text = r.values[t.def.storedIndex(src)] ?: continue
                        if (text !is String) throw IllegalArgumentException("${t.obj.name}.${t.def.columns[src].name} (rowid ${r.rowid}) is not text, json_extract on it can't be reproduced")
                        try {
                            val m = SqlJson.extract(text, keys.getValue(src))
                            for (g in group) {
                                val aff = t.def.columns[g.declared].affinity
                                values.getValue(g.declared)[ri] = SqlValues.forFile(SqlValues.apply(m[g.key], aff), aff)
                            }
                        } catch (e: IllegalArgumentException) {
                            throw IllegalArgumentException("${t.obj.name}.${t.def.columns[src].name} (rowid ${r.rowid}): ${e.message}")
                        }
                    }
                }
                out[t] = values
            }
            return out
        }

        /** The entries of [idx] sorted as SQLite orders them (key columns, then rowid), as records. UNIQUE is enforced here. */
        fun indexRecords(idx: Idx, gens: Map<Tbl, Map<Int, Array<Any?>>>): List<ByteArray> {
            val t = idx.tbl
            val k = idx.cols.size
            val generated = gens[t]
            val entries = ArrayList<Array<Any?>>(t.rows.size)
            for ((ri, r) in t.rows.withIndex()) {
                val e = arrayOfNulls<Any>(k + 1)
                for ((j, d) in idx.cols.withIndex()) {
                    e[j] = if (t.def.columns[d].stored) r.values[t.def.storedIndex(d)] else generated!!.getValue(d)[ri]
                }
                e[k] = r.rowid
                entries += e
            }
            entries.sortWith { a, b -> SqlValues.compareKeys(a, b) }
            if (idx.unique) {
                for (i in 1 until entries.size) {
                    val a = entries[i - 1]
                    val b = entries[i]
                    if ((0 until k).all { a[it] != null && SqlValues.compare(a[it], b[it]) == 0 }) {
                        throw IllegalArgumentException("UNIQUE constraint failed: ${idx.cols.joinToString(", ") { t.obj.name + "." + t.def.columns[it].name }}")
                    }
                }
            }
            return entries.map { e -> SqlValues.record(e.asList().subList(0, k), e[k] as Long) }
        }

        // ---- pages ------------------------------------------------------------------------------------------------------

        fun alloc(): Int { pages += null; return pages.size }

        fun putU16(b: ByteArray, o: Int, v: Int) { b[o] = (v shr 8).toByte(); b[o + 1] = v.toByte() }

        fun putU32(b: ByteArray, o: Int, v: Int) { for (i in 0 until 4) b[o + i] = (v ushr (24 - 8 * i)).toByte() }

        /** Bytes of a payload of [p] that stay on the b-tree page (file format, "Cell Payload Overflow Pages"). */
        fun localSize(p: Int, tableLeaf: Boolean): Int {
            val x = if (tableLeaf) ps - 35 else ((ps - 12) * 64 / 255) - 23
            if (p <= x) return p
            val m = ((ps - 12) * 32 / 255) - 23
            val k = m + ((p - m) % (ps - 4))
            return if (k <= x) k else m
        }

        /** Writes the rest of [payload] (after [local] bytes) to a chain of overflow pages; returns the first page. */
        fun overflow(payload: ByteArray, local: Int): Int {
            var off = local
            var first = 0
            var prev: ByteArray? = null
            while (off < payload.size) {
                val no = alloc()
                val pg = ByteArray(ps)
                val n = minOf(ps - 4, payload.size - off)
                System.arraycopy(payload, off, pg, 4, n)
                off += n
                if (prev == null) first = no else putU32(prev, 0, no)
                pages[no - 1] = pg
                prev = pg
            }
            return first
        }

        /** A cell of a table leaf ([rowid] given) or an index page (null): payload size, rowid, local payload, overflow pointer. */
        fun cell(payload: ByteArray, rowid: Long?): ByteArray {
            val p = payload.size
            val local = localSize(p, rowid != null)
            val head = SqlValues.varintLen(p.toLong()) + if (rowid != null) SqlValues.varintLen(rowid) else 0
            val out = ByteArray(head + local + if (local < p) 4 else 0)
            var o = SqlValues.putVarint(out, 0, p.toLong())
            if (rowid != null) o += SqlValues.putVarint(out, o, rowid)
            System.arraycopy(payload, 0, out, o, local)
            if (local < p) putU32(out, o + local, overflow(payload, local))
            return out
        }

        fun child(page: Int, rest: ByteArray): ByteArray = ByteArray(4 + rest.size).also { putU32(it, 0, page); System.arraycopy(rest, 0, it, 4, rest.size) }

        /** A b-tree page: [flag] (0x0D, 0x05, 0x0A, 0x02), the [cells] in key order packed at the end, [right] = right-most child. */
        fun page(no: Int, flag: Int, cells: List<ByteArray>, right: Int): ByteArray {
            val pg = ByteArray(ps)
            val h = if (no == 1) 100 else 0
            val interior = flag == 0x05 || flag == 0x02
            val ptrs = h + if (interior) 12 else 8
            pg[h] = flag.toByte()
            putU16(pg, h + 3, cells.size)
            var end = ps
            for ((i, c) in cells.withIndex()) {
                end -= c.size
                System.arraycopy(c, 0, pg, end, c.size)
                putU16(pg, ptrs + 2 * i, end)
            }
            check(ptrs + 2 * cells.size <= end) { "A page overflowed" }
            putU16(pg, h + 5, if (end == 65536) 0 else end)
            if (interior) putU32(pg, h + 8, right)
            return pg
        }

        fun emit(flag: Int, cells: List<ByteArray>, right: Int, no: Int = alloc()): Int {
            pages[no - 1] = page(no, flag, cells, right)
            return no
        }

        /**
         * A rowid b-tree of [n] rows, bulk-loaded in rowid order; returns its root page. [master] = sqlite_master: its root
         * is page 1 (header at byte 100, so its interior pages are planned 100 bytes smaller).
         */
        fun tableTree(n: Int, rowidAt: (Int) -> Long, payloadAt: (Int) -> ByteArray, master: Boolean): Int {
            val loss = if (master) 100 else 0
            val leaves = ArrayList<Pair<Int, Long>>() // page, largest rowid
            var cur = ArrayList<ByteArray>()
            var used = 8
            var last = 0L
            for (i in 0 until n) {
                val rowid = rowidAt(i)
                val c = cell(payloadAt(i), rowid)
                if (cur.isNotEmpty() && used + c.size + 2 > ps) {
                    leaves += emit(0x0D, cur, 0) to last
                    cur = ArrayList()
                    used = 8
                }
                cur += c
                used += c.size + 2
                last = rowid
            }
            if (leaves.isEmpty() && (!master || used + loss <= ps)) return emit(0x0D, cur, 0, if (master) 1 else alloc())
            if (leaves.isEmpty()) { // the only leaf is too big for page 1, which becomes an interior page (as SQLite does)
                if (cur.size == 1) return emit(0x05, emptyList(), emit(0x0D, cur, 0), 1) // an interior page with no cell, just its right child
                leaves += emit(0x0D, cur.subList(0, cur.size - 1), 0) to rowidAt(n - 2)
                cur = ArrayList(cur.subList(cur.size - 1, cur.size))
            }
            leaves += emit(0x0D, cur, 0) to last
            var level: List<Pair<Int, Long>> = leaves
            while (level.size > 1) {
                val ranges = ArrayList<IntArray>() // children a..b share one interior page; a..b-1 get cells
                var a = 0
                while (a < level.size) {
                    var b = a
                    var u = 12
                    while (b < level.size - 1) {
                        val sz = 4 + SqlValues.varintLen(level[b].second) + 2
                        if (u + sz > ps - loss) break
                        u += sz
                        b++
                    }
                    if (b < level.size - 1 && level.size - 1 - b == 1) b-- // never leave a single child for the next page
                    check(b > a) { "An interior page can't hold two children" }
                    ranges += intArrayOf(a, b)
                    a = b + 1
                }
                level = ranges.map { (from, to) ->
                    val cells = (from until to).map { j ->
                        ByteArray(4 + SqlValues.varintLen(level[j].second)).also { putU32(it, 0, level[j].first); SqlValues.putVarint(it, 4, level[j].second) }
                    }
                    emit(0x05, cells, level[to].first, if (master && ranges.size == 1) 1 else alloc()) to level[to].second
                }
            }
            return level[0].first
        }

        /**
         * Splits cells (of the given sizes) into runs that each fill one page after [hdr] header bytes; the cell after every
         * run but the last is promoted to the parent. Each run is [start, end).
         */
        fun split(sizes: IntArray, hdr: Int): List<IntArray> {
            val runs = ArrayList<IntArray>()
            var s = 0
            while (true) {
                var e = s
                var used = hdr
                while (e < sizes.size && used + sizes[e] + 2 <= ps) { used += sizes[e] + 2; e++ }
                check(e > s) { "A cell doesn't fit a page" }
                if (e == sizes.size) { runs += intArrayOf(s, e); return runs }
                if (e + 1 == sizes.size) { e--; check(e > s) { "A page can't hold two cells" } } // the last run must not be empty
                runs += intArrayOf(s, e)
                s = e + 1
            }
        }

        /** An index b-tree; every entry is stored once, interior pages hold the entries that divide their children. */
        fun indexTree(idx: Idx, gens: Map<Tbl, Map<Int, Array<Any?>>>): Int {
            val payloads = indexRecords(idx, gens)
            if (payloads.isEmpty()) return emit(0x0A, emptyList(), 0)
            val cells = payloads.map { cell(it, null) }
            var runs = split(IntArray(cells.size) { cells[it].size }, 8)
            var nodes = runs.map { emit(0x0A, cells.subList(it[0], it[1]), 0) }
            var seps = runs.dropLast(1).map { cells[it[1]] }
            while (nodes.size > 1) {
                runs = split(IntArray(seps.size) { seps[it].size + 4 }, 12)
                val up = runs.map { r -> emit(0x02, (r[0] until r[1]).map { child(nodes[it], seps[it]) }, nodes[r[1]]) }
                seps = runs.dropLast(1).map { seps[it[1]] }
                nodes = up
            }
            return nodes[0]
        }

        // ---- the file ---------------------------------------------------------------------------------------------------

        fun assemble(): ByteArray {
            val out = ByteArray(pages.size * ps)
            for (i in pages.indices) {
                val pg = pages[i] ?: throw IllegalStateException("Page ${i + 1} was never written")
                System.arraycopy(pg, 0, out, i * ps, ps)
                pages[i] = null
            }
            val magic = "SQLite format 3\u0000".toByteArray(Charsets.ISO_8859_1)
            System.arraycopy(magic, 0, out, 0, magic.size)
            putU16(out, 16, if (ps == 65536) 1 else ps)
            out[18] = 1; out[19] = 1 // rollback journal, not WAL
            out[21] = 64; out[22] = 32; out[23] = 32
            putU32(out, 24, 1)                  // file change counter
            putU32(out, 28, pages.size)         // size in pages
            putU32(out, 40, 1)                  // schema cookie
            putU32(out, 44, 4)                  // schema format
            putU32(out, 56, 1)                  // UTF-8
            putU32(out, 60, img.userVersion)
            putU32(out, 68, img.applicationId)
            putU32(out, 92, 1)                  // version-valid-for = change counter
            putU32(out, 96, SQLITE_VERSION_NUMBER)
            return out
        }
    }
}
