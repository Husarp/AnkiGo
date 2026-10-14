package com.husarp.ankigo.game.ankimon

// TICKET T51 owns this file. CREATE TABLE / CREATE INDEX text -> what the reader, the writer and the check need.
// Parsing never throws for forms it merely doesn't support: they are listed in `problems`, which the reader ignores
// (it only needs the columns) and the writer / check treat as "refuse". Pure Kotlin.

internal object SqlSchema {
    enum class Affinity { TEXT, NUMERIC, INTEGER, REAL, BLOB }

    enum class Gen { VIRTUAL, STORED }

    /** One declared column. [default] is the DEFAULT literal (Long, Double, String or null) when [defaultProblem] is null. */
    class Column(
        val name: String,
        val type: String,
        val affinity: Affinity,
        val notNull: Boolean,
        val collation: String?,
        val gen: Gen?,
        val genExpr: String?,
        val hasDefault: Boolean,
        val default: Any?,
        val defaultProblem: String?,
    ) {
        /** False for VIRTUAL generated columns: they are not in the file. */
        val stored: Boolean get() = gen != Gen.VIRTUAL
    }

    /** One automatic index (PRIMARY KEY / UNIQUE): declared column numbers and the collation of each. */
    class Unique(val columns: List<Int>, val collations: List<String>)

    class Table(
        /** All declared columns, virtual generated ones included. */
        val columns: List<Column>,
        /** Index in [columns] of the INTEGER PRIMARY KEY (rowid alias), or -1. */
        val rowidAlias: Int,
        val autoincrement: Boolean,
        val withoutRowid: Boolean,
        /** The automatic indexes SQLite creates, in creation order (`sqlite_autoindex_<table>_<n>`, n from 1), duplicates removed. */
        val autoIndexes: List<Unique>,
        /** Forms we don't support (CHECK, STRICT, ON CONFLICT, DESC keys, other collations...). Empty = fully understood. */
        val problems: List<String>,
    ) {
        /** The columns that are in the file, in storage order. */
        val storedColumns: List<Column> = columns.filter { it.stored }

        /** Index of the rowid alias among [storedColumns], or -1. */
        val storedAlias: Int = if (rowidAlias < 0) -1 else columns.subList(0, rowidAlias).count { it.stored }

        /** Declared column [name] (case-insensitive), or -1. */
        fun col(name: String): Int = columns.indexOfFirst { it.name.equals(name, ignoreCase = true) }

        /** Position among [storedColumns] of declared column [declared]; -1 for virtual ones. */
        fun storedIndex(declared: Int): Int = if (columns[declared].stored) columns.subList(0, declared).count { it.stored } else -1
    }

    class Term(val column: String, val collation: String?, val desc: Boolean)

    class IndexDef(val unique: Boolean, val table: String, val terms: List<Term>, val problems: List<String>)

    fun autoIndexName(table: String, n: Int) = "sqlite_autoindex_${table}_$n"

    private fun bad(msg: String): Nothing = throw SqliteFormatException(msg)

    private val CONSTRAINT_WORDS = setOf(
        "CONSTRAINT", "PRIMARY", "NOT", "NULL", "UNIQUE", "CHECK", "DEFAULT", "COLLATE", "REFERENCES", "GENERATED", "AS", "ON",
    )
    private val TABLE_CONSTRAINT_WORDS = setOf("CONSTRAINT", "PRIMARY", "UNIQUE", "CHECK", "FOREIGN")
    private val MAX_COLUMNS = 2_000

    // ---- affinity ----------------------------------------------------------------------------------------------------

    /** datatype3 section 3.1: the first rule that matches wins. */
    fun affinityOf(type: String): Affinity {
        val t = type.uppercase()
        return when {
            "INT" in t -> Affinity.INTEGER
            "CHAR" in t || "CLOB" in t || "TEXT" in t -> Affinity.TEXT
            t.isEmpty() || "BLOB" in t -> Affinity.BLOB
            "REAL" in t || "FLOA" in t || "DOUB" in t -> Affinity.REAL
            else -> Affinity.NUMERIC
        }
    }

    // ---- CREATE TABLE ------------------------------------------------------------------------------------------------

    private class Raw(val primary: Boolean, val cols: List<Int>, val collate: List<String?>, val desc: Boolean)

    /** Parses a CREATE TABLE statement. Throws SqliteFormatException for text that isn't one (or a virtual table). */
    fun parseTable(sql: String?): Table {
        if (sql == null) bad("A table has no definition")
        val s = stripComments(sql)
        if (Regex("""^\s*CREATE\s+VIRTUAL\s+TABLE\b""", RegexOption.IGNORE_CASE).containsMatchIn(s)) bad("Virtual tables are not supported")
        val open = firstParen(s)
        if (open < 0) bad("A table definition has no column list")
        val close = matchParen(s, open)
        val problems = ArrayList<String>()
        val after = tokens(s.substring(close + 1)).map { it.text.uppercase() }
        val withoutRowid = "WITHOUT" in after && "ROWID" in after
        for (w in after) if (w != "WITHOUT" && w != "ROWID") problems += if (w == "STRICT") "STRICT table" else "table option $w"

        val cols = ArrayList<Column>()
        val raws = ArrayList<Raw>()
        var autoincCol = -1
        for (seg in splitTop(s.substring(open + 1, close))) {
            val t = tokens(seg)
            if (t.isEmpty()) continue
            if (t[0].kind == 'w' && t[0].text.uppercase() in TABLE_CONSTRAINT_WORDS) {
                tableConstraint(t, cols, raws, problems)
                continue
            }
            if (t[0].kind == 'p') bad("A column has no name")
            val idx = cols.size
            var i = 1
            val type = ArrayList<String>()
            while (i < t.size && !(t[i].kind == 'w' && t[i].text.uppercase() in CONSTRAINT_WORDS)) { type += render(t[i]); i++ }
            val typeText = type.joinToString(" ").uppercase()
            var notNull = false
            var collation: String? = null
            var gen: Gen? = null
            var genExpr: String? = null
            var hasDefault = false
            var default: Any? = null
            var defaultProblem: String? = null
            while (i < t.size) {
                val u = if (t[i].kind == 'w') t[i].text.uppercase() else ""
                val word = t[i].text
                i++
                fun next() = t.getOrNull(i)?.takeIf { it.kind == 'w' }?.text?.uppercase()
                when (u) {
                    "CONSTRAINT" -> i++
                    "PRIMARY" -> {
                        if (next() == "KEY") i++
                        var desc = false
                        if (next() == "ASC" || next() == "DESC") { desc = next() == "DESC"; i++ }
                        if (next() == "AUTOINCREMENT") { autoincCol = idx; i++ }
                        raws += Raw(true, listOf(idx), listOf(null), desc)
                    }
                    "NOT" -> if (next() == "NULL") { notNull = true; i++ } else problems += "unknown constraint NOT ${t.getOrNull(i)?.text}"
                    "NULL" -> {}
                    "UNIQUE" -> raws += Raw(false, listOf(idx), listOf(null), false)
                    "CHECK" -> { problems += "CHECK constraint"; if (t.getOrNull(i)?.kind == 'p') i++ }
                    "DEFAULT" -> {
                        hasDefault = true
                        val r = parseDefault(t, i)
                        i = r.first; default = r.second; defaultProblem = r.third
                    }
                    "COLLATE" -> { collation = t.getOrNull(i)?.text?.uppercase(); i++ }
                    "REFERENCES" -> i = skipReferences(t, i)
                    "GENERATED" -> if (next() == "ALWAYS") i++
                    "AS" -> {
                        if (t.getOrNull(i)?.kind != 'p') { problems += "AS without an expression"; continue }
                        genExpr = t[i].text
                        i++
                        gen = Gen.VIRTUAL
                        if (next() == "STORED") { gen = Gen.STORED; i++ } else if (next() == "VIRTUAL") i++
                    }
                    "ON" -> { problems += "ON CONFLICT clause"; i += 2 }
                    else -> problems += "unknown column constraint '$word'"
                }
            }
            cols += Column(
                t[0].text, typeText, affinityOf(typeText), notNull, collation, gen, genExpr, hasDefault, default, defaultProblem,
            )
            if (cols.size > MAX_COLUMNS) bad("Too many columns")
        }
        return finish(cols, raws, autoincCol, withoutRowid, problems)
    }

    private fun finish(cols: List<Column>, raws: List<Raw>, autoincCol: Int, withoutRowid: Boolean, problems: MutableList<String>): Table {
        val pks = raws.filter { it.primary }
        if (pks.size > 1) problems += "more than one PRIMARY KEY"
        val pk = pks.singleOrNull()
        var alias = -1
        if (pk != null && pk.cols.size == 1) {
            val c = cols[pk.cols[0]]
            if (c.type == "INTEGER" && c.gen == null && !pk.desc) alias = pk.cols[0]
        }
        if (autoincCol >= 0 && autoincCol != alias) problems += "AUTOINCREMENT without an INTEGER PRIMARY KEY"
        val auto = ArrayList<Unique>()
        for (r in raws) {
            if (r.desc) problems += "DESC in a key"
            if (r.primary && r.cols.size == 1 && r.cols[0] == alias) continue
            val u = Unique(r.cols, r.cols.indices.map { r.collate[it] ?: cols[r.cols[it]].collation ?: "BINARY" })
            if (u.collations.any { it != "BINARY" }) problems += "a key with the collation ${u.collations.first { it != "BINARY" }}"
            // The same columns with the same collations make one index, whatever constraint asked for it.
            if (auto.none { it.columns == u.columns && it.collations == u.collations }) auto += u
        }
        return Table(cols, alias, autoincCol >= 0 && autoincCol == alias, withoutRowid, auto, problems)
    }

    private fun tableConstraint(t: List<Tok>, cols: List<Column>, raws: MutableList<Raw>, problems: MutableList<String>) {
        var i = if (t[0].text.uppercase() == "CONSTRAINT") 2 else 0
        when (t.getOrNull(i)?.text?.uppercase()) {
            "PRIMARY", "UNIQUE" -> {
                val primary = t[i].text.uppercase() == "PRIMARY"
                i += if (primary) 2 else 1 // PRIMARY KEY
                val list = t.getOrNull(i)
                if (list == null || list.kind != 'p') { problems += "a key without a column list"; return }
                val idx = ArrayList<Int>()
                val coll = ArrayList<String?>()
                var desc = false
                for (term in splitTop(list.text)) {
                    val w = tokens(term)
                    val c = if (w.isNotEmpty() && w[0].kind != 'p') cols.indexOfFirst { it.name.equals(w[0].text, true) } else -1
                    var ok = c >= 0
                    var collate: String? = null
                    var k = 1
                    while (ok && k < w.size) {
                        val u = if (w[k].kind == 'w') w[k].text.uppercase() else ""
                        when (u) {
                            "COLLATE" -> { collate = w.getOrNull(k + 1)?.text?.uppercase(); k += 2 }
                            "ASC" -> k++
                            "DESC" -> { desc = true; k++ }
                            else -> ok = false
                        }
                    }
                    if (!ok) { problems += "an expression or unknown column in a key"; return }
                    idx += c
                    coll += collate
                }
                raws += Raw(primary, idx, coll, desc)
                if (t.drop(i + 1).isNotEmpty()) problems += "ON CONFLICT clause"
            }
            "CHECK" -> problems += "CHECK constraint"
            "FOREIGN" -> {} // no effect on what is stored
            else -> problems += "unknown table constraint '${t.getOrNull(i)?.text}'"
        }
    }

    /** `DEFAULT <literal>`: (next token index, value, problem). Only plain literals are understood; anything else is a problem. */
    private fun parseDefault(t: List<Tok>, start: Int): Triple<Int, Any?, String?> {
        var i = start
        val tok = t.getOrNull(i) ?: return Triple(i, null, "DEFAULT without a value")
        i++
        if (tok.kind == 'p') return Triple(i, null, "DEFAULT expression")
        if (tok.kind == '\'') return Triple(i, tok.text, null)
        if (tok.kind != 'w') return Triple(i, null, "DEFAULT ${tok.text}")
        var text = tok.text
        if ((text == "+" || text == "-") && t.getOrNull(i)?.kind == 'w') { text += t[i].text; i++ }
        return when (text.uppercase()) {
            "NULL" -> Triple(i, null, null)
            "TRUE" -> Triple(i, 1L, null)
            "FALSE" -> Triple(i, 0L, null)
            else -> try {
                val v = SqlValues.numericText(text)
                if (v == null) Triple(i, null, "DEFAULT $text") else Triple(i, v, null)
            } catch (e: IllegalArgumentException) {
                Triple(i, null, "DEFAULT $text")
            }
        }
    }

    /** Skips `REFERENCES tbl [(cols)] [ON DELETE/UPDATE ..] [MATCH x] [[NOT] DEFERRABLE [INITIALLY x]]`. */
    private fun skipReferences(t: List<Tok>, start: Int): Int {
        var i = start + 1 // the table
        if (t.getOrNull(i)?.kind == 'p') i++
        while (i < t.size && t[i].kind == 'w') {
            when (t[i].text.uppercase()) {
                "ON" -> {
                    i += 2
                    i += when (t.getOrNull(i)?.text?.uppercase()) { "SET", "NO" -> 2; else -> 1 }
                }
                "MATCH", "INITIALLY" -> i += 2
                "NOT" -> i += 2
                "DEFERRABLE" -> i += 1
                else -> return i
            }
        }
        return i
    }

    // ---- CREATE INDEX ------------------------------------------------------------------------------------------------

    /** Parses `CREATE [UNIQUE] INDEX [IF NOT EXISTS] name ON table (col [COLLATE x] [ASC|DESC], ...)`; WHERE and expressions become problems. */
    fun parseIndex(sql: String): IndexDef {
        val s = stripComments(sql)
        val m = Regex("""^\s*CREATE\s+(UNIQUE\s+)?INDEX\s+(?:IF\s+NOT\s+EXISTS\s+)?""", RegexOption.IGNORE_CASE).find(s)
            ?: bad("Not a CREATE INDEX statement")
        val t = tokens(s.substring(m.range.last + 1))
        if (t.size < 4 || t[0].kind == 'p' || t[1].text.uppercase() != "ON" || t[2].kind == 'p' || t[3].kind != 'p') bad("Unreadable CREATE INDEX statement")
        val problems = ArrayList<String>()
        if (t.size > 4) problems += "a WHERE clause or other text after the column list (partial index)"
        val terms = ArrayList<Term>()
        for (term in splitTop(t[3].text)) {
            val w = tokens(term)
            if (w.isEmpty() || w[0].kind == 'p') { problems += "an expression in an index"; continue }
            var collate: String? = null
            var desc = false
            var k = 1
            while (k < w.size) {
                when (if (w[k].kind == 'w') w[k].text.uppercase() else "") {
                    "COLLATE" -> { collate = w.getOrNull(k + 1)?.text?.uppercase(); k += 2 }
                    "ASC" -> k++
                    "DESC" -> { desc = true; k++ }
                    else -> { problems += "an expression in an index"; k = w.size }
                }
            }
            terms += Term(w[0].text, collate, desc)
        }
        return IndexDef(m.groupValues[1].isNotEmpty(), t[2].text, terms, problems)
    }

    // ---- text scanning -----------------------------------------------------------------------------------------------

    /** A token of a column definition: 'w' word, 'p' parenthesised group (text = inside), or the opening quote char (text = unquoted). */
    private class Tok(val kind: Char, val text: String)

    private fun render(t: Tok): String = if (t.kind == 'p') "(${t.text})" else t.text

    private fun tokens(s: String): List<Tok> {
        val out = ArrayList<Tok>()
        var i = 0
        while (i < s.length) {
            val ch = s[i]
            when {
                ch.isWhitespace() || ch == ',' -> i++
                isQuote(ch) -> { val e = skipQuoted(s, i); out += Tok(ch, unquote(s.substring(i, e))); i = e }
                ch == '(' -> { val e = matchParen(s, i); out += Tok('p', s.substring(i + 1, e)); i = e + 1 }
                else -> {
                    var j = i
                    while (j < s.length && !s[j].isWhitespace() && !isQuote(s[j]) && s[j] != '(' && s[j] != ',') j++
                    out += Tok('w', s.substring(i, j))
                    i = j
                }
            }
        }
        return out
    }

    private fun unquote(raw: String): String {
        val close = if (raw[0] == '[') ']' else raw[0]
        val terminated = raw.length >= 2 && raw.last() == close
        val inner = if (terminated) raw.substring(1, raw.length - 1) else raw.substring(1)
        return if (close == ']') inner else inner.replace("$close$close", "$close")
    }

    /** Text with comments turned into spaces, quotes kept (SQLite stores the CREATE statement as typed). */
    fun stripComments(sql: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < sql.length) {
            val ch = sql[i]
            when {
                isQuote(ch) -> {
                    val e = skipQuoted(sql, i)
                    sb.append(sql, i, e)
                    i = e
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
    fun skipQuoted(s: String, i: Int): Int {
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

    // ---- generated columns -------------------------------------------------------------------------------------------

    /** `json_extract(<column>, '$.<key>')`: (source column name, key), or null for any other expression. */
    fun jsonExtractOf(expr: String): Pair<String, String>? {
        val m = Regex("""^\s*json_extract\s*\(\s*("(?:[^"]|"")+"|`(?:[^`]|``)+`|\[[^\]]+\]|[A-Za-z_][A-Za-z0-9_]*)\s*,\s*'\$\.([A-Za-z0-9_]+)'\s*\)\s*$""", RegexOption.IGNORE_CASE)
            .find(expr) ?: return null
        val raw = m.groupValues[1]
        val col = if (raw[0] == '"' || raw[0] == '`' || raw[0] == '[') unquote(raw) else raw
        return col to m.groupValues[2]
    }
}
