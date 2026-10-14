package com.husarp.ankigo.game.ankimon

// TICKET T51 owns this file. Shared shape between the reader (SqliteReader.image), the Ankimon export (AnkimonExport,
// T52) and the writer (SqliteWriter, T51). Pure Kotlin.

/**
 * One row of `sqlite_master`, without its root page (the writer gives every b-tree new pages).
 * [type] is "table", "index", "view" or "trigger"; [sql] is null for SQLite's automatic indexes (`sqlite_autoindex_*`).
 */
data class SqlObject(val type: String, val name: String, val tblName: String, val sql: String?)

/**
 * One row of a rowid table. [values] are the STORED columns in storage order (virtual generated columns are not stored),
 * each a Long, Double, String, ByteArray or null. The INTEGER PRIMARY KEY column (the rowid alias), if the table has one,
 * holds the rowid itself here (SQLite stores NULL there on disk; the writer does that).
 */
class SqlRow(var rowid: Long, val values: MutableList<Any?>)

/**
 * The content of one rowid table. [columns] are the names of the stored columns in storage order;
 * [rowidAlias] is the index in [columns] of the INTEGER PRIMARY KEY column, or -1.
 */
class SqlTable(val name: String, val columns: List<String>, val rowidAlias: Int, val rows: MutableList<SqlRow>) {
    /** Index of column [name] (case-insensitive) in [columns], or -1. */
    fun col(name: String): Int = columns.indexOfFirst { it.equals(name, ignoreCase = true) }

    operator fun get(row: SqlRow, column: String): Any? = col(column).let { if (it < 0) null else row.values[it] }

    operator fun set(row: SqlRow, column: String, value: Any?) {
        val i = col(column)
        require(i >= 0) { "Table $name has no column $column" }
        row.values[i] = value
        if (i == rowidAlias) row.rowid = (value as? Long) ?: throw IllegalArgumentException("$name.$column must be an integer")
    }

    /**
     * Adds a row from column name -> value (missing columns are null). The rowid is the alias column's value when given,
     * else one above the largest rowid in the table (1 for an empty table), as SQLite picks it.
     */
    fun insert(values: Map<String, Any?>): SqlRow {
        for (k in values.keys) require(col(k) >= 0) { "Table $name has no column $k" }
        val v = columns.map { c -> values.entries.firstOrNull { it.key.equals(c, ignoreCase = true) }?.value }.toMutableList()
        val given = if (rowidAlias >= 0) v[rowidAlias] as? Long else null
        val rowid = given ?: ((rows.maxOfOrNull { it.rowid } ?: 0L) + 1)
        if (rowidAlias >= 0) v[rowidAlias] = rowid
        require(rows.none { it.rowid == rowid }) { "Table $name already has rowid $rowid" }
        return SqlRow(rowid, v).also { rows += it }
    }
}

/** A whole database as plain values: what [SqliteReader.image] returns and [SqliteWriter.write] takes. */
class SqliteImage(
    /** `sqlite_master` rows in file order, internal tables (sqlite_sequence, sqlite_stat1) included. */
    val objects: MutableList<SqlObject>,
    /** Lower-case table name -> content; one entry for every object of type "table" (internal ones too). */
    val tables: MutableMap<String, SqlTable>,
    val pageSize: Int = 4096,
    val userVersion: Int = 0,
    val applicationId: Int = 0,
) {
    fun table(name: String): SqlTable? = tables[name.lowercase()]
}
