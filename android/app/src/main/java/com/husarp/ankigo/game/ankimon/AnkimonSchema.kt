package com.husarp.ankigo.game.ankimon

// TICKET T52 owns this file.

/**
 * Ankimon's tables as its add-on creates them (pyobj/database_manager.py `_setup_database`): the layout of a fresh save, and the
 * tables the export adds to a kept file that lacks one (Ankimon makes missing tables itself with CREATE TABLE IF NOT EXISTS).
 * The `sql` texts are what SQLite keeps in `sqlite_master` (no IF NOT EXISTS); the objects come in the order SQLite lists them.
 */
internal object AnkimonSchema {
    private class Spec(
        val name: String,
        val sql: String,
        val columns: List<String>,   // stored columns (virtual generated columns are not stored)
        val rowidAlias: Int = -1,    // index in [columns] of the INTEGER PRIMARY KEY column
        val indexes: List<Pair<String, String?>> = emptyList(), // name -> sql (null: SQLite's automatic index), in creation order
    )

    private const val D = "$"

    private val SPECS: List<Spec> = listOf(
        Spec(
            "captured_pokemon",
            """
            CREATE TABLE captured_pokemon (
                individual_id TEXT PRIMARY KEY,
                is_main INTEGER DEFAULT 0,
                data TEXT NOT NULL,
                name TEXT GENERATED ALWAYS AS (json_extract(data, '$D.name')) VIRTUAL,
                pokedex_id INTEGER GENERATED ALWAYS AS (json_extract(data, '$D.id')) VIRTUAL,
                shiny BOOLEAN GENERATED ALWAYS AS (json_extract(data, '$D.shiny')) VIRTUAL,
                level INTEGER GENERATED ALWAYS AS (json_extract(data, '$D.level')) VIRTUAL
            )
            """.trimIndent(),
            listOf("individual_id", "is_main", "data"),
            indexes = listOf(
                "sqlite_autoindex_captured_pokemon_1" to null,
                "idx_pokemon_name" to "CREATE INDEX idx_pokemon_name ON captured_pokemon(name)",
                "idx_pokemon_pokedex_id" to "CREATE INDEX idx_pokemon_pokedex_id ON captured_pokemon(pokedex_id)",
                "idx_pokemon_shiny" to "CREATE INDEX idx_pokemon_shiny ON captured_pokemon(shiny)",
                "idx_pokemon_level" to "CREATE INDEX idx_pokemon_level ON captured_pokemon(level)",
            ),
        ),
        Spec(
            "items",
            """
            CREATE TABLE items (
                id INTEGER PRIMARY KEY,
                item_name TEXT UNIQUE,
                quantity INTEGER DEFAULT 0,
                data TEXT,
                category_id INTEGER,
                cost INTEGER,
                fling_power INTEGER,
                fling_effect_id INTEGER
            )
            """.trimIndent(),
            listOf("id", "item_name", "quantity", "data", "category_id", "cost", "fling_power", "fling_effect_id"), 0,
            listOf("sqlite_autoindex_items_1" to null),
        ),
        Spec(
            "badges",
            "CREATE TABLE badges (\n    badge_id TEXT PRIMARY KEY,\n    achieved BOOLEAN DEFAULT 0\n)",
            listOf("badge_id", "achieved"), indexes = listOf("sqlite_autoindex_badges_1" to null),
        ),
        Spec(
            "metadata",
            "CREATE TABLE metadata (\n    key TEXT PRIMARY KEY,\n    value TEXT\n)",
            listOf("key", "value"), indexes = listOf("sqlite_autoindex_metadata_1" to null),
        ),
        Spec(
            "team",
            "CREATE TABLE team (\n    slot_position INTEGER PRIMARY KEY,\n    individual_id TEXT NOT NULL\n)",
            listOf("slot_position", "individual_id"), 0,
        ),
        Spec(
            "pokemon_history",
            "CREATE TABLE pokemon_history (\n    id INTEGER PRIMARY KEY AUTOINCREMENT,\n    individual_id TEXT UNIQUE,\n    data TEXT NOT NULL\n)",
            listOf("id", "individual_id", "data"), 0, listOf("sqlite_autoindex_pokemon_history_1" to null),
        ),
        Spec("sqlite_sequence", "CREATE TABLE sqlite_sequence(name,seq)", listOf("name", "seq")),
        Spec(
            "user_data",
            "CREATE TABLE user_data (\n    key TEXT PRIMARY KEY,\n    value TEXT NOT NULL\n)",
            listOf("key", "value"), indexes = listOf("sqlite_autoindex_user_data_1" to null),
        ),
        Spec(
            "config",
            "CREATE TABLE config (\n    key TEXT PRIMARY KEY,\n    value TEXT NOT NULL\n)",
            listOf("key", "value"), indexes = listOf("sqlite_autoindex_config_1" to null),
        ),
        Spec(
            "pending_mobile_battles",
            """
            CREATE TABLE pending_mobile_battles (
                id            INTEGER PRIMARY KEY AUTOINCREMENT,
                revlog_id     INTEGER UNIQUE NOT NULL,
                card_id       INTEGER NOT NULL,
                ease          INTEGER NOT NULL,
                review_time   INTEGER NOT NULL,
                review_type   INTEGER NOT NULL,
                queued_at     INTEGER NOT NULL,
                resolved      INTEGER NOT NULL DEFAULT 0,
                resolved_at   INTEGER
            )
            """.trimIndent(),
            listOf("id", "revlog_id", "card_id", "ease", "review_time", "review_type", "queued_at", "resolved", "resolved_at"), 0,
            listOf("sqlite_autoindex_pending_mobile_battles_1" to null),
        ),
        Spec(
            "mobile_battle_history",
            """
            CREATE TABLE mobile_battle_history (
                id                INTEGER PRIMARY KEY AUTOINCREMENT,
                timestamp         INTEGER NOT NULL,
                enemy_id          INTEGER NOT NULL,
                enemy_name        TEXT NOT NULL,
                enemy_level       INTEGER NOT NULL,
                enemy_shiny       INTEGER NOT NULL,
                companion_name    TEXT,
                companion_level   INTEGER,
                outcome           TEXT NOT NULL,
                xp_gained         INTEGER DEFAULT 0,
                trainer_xp_gained INTEGER DEFAULT 0,
                cash_gained       INTEGER DEFAULT 0
            )
            """.trimIndent(),
            listOf(
                "id", "timestamp", "enemy_id", "enemy_name", "enemy_level", "enemy_shiny", "companion_name", "companion_level",
                "outcome", "xp_gained", "trainer_xp_gained", "cash_gained",
            ), 0,
            listOf("idx_history_timestamp" to "CREATE INDEX idx_history_timestamp ON mobile_battle_history(timestamp)"),
        ),
        Spec(
            "desktop_processed_reviews",
            "CREATE TABLE desktop_processed_reviews (\n    revlog_id INTEGER PRIMARY KEY,\n    card_id   INTEGER\n)",
            listOf("revlog_id", "card_id"), 0,
        ),
    )

    private val AUTOINCREMENT = setOf("pokemon_history", "pending_mobile_battles", "mobile_battle_history")

    /** A new, empty Ankimon save: every table of a fresh install and nothing in it. */
    fun fresh(): SqliteImage = SqliteImage(mutableListOf(), mutableMapOf()).also { img -> SPECS.forEach { add(img, it) } }

    /** Adds [table] (with its indexes, and `sqlite_sequence` for the AUTOINCREMENT ones) when [img] lacks it. */
    fun ensure(img: SqliteImage, table: String) {
        if (img.table(table) != null) return
        val spec = SPECS.firstOrNull { it.name == table } ?: throw IllegalArgumentException("Unknown Ankimon table $table")
        if (table in AUTOINCREMENT && img.table("sqlite_sequence") == null) add(img, SPECS.first { it.name == "sqlite_sequence" })
        add(img, spec)
    }

    private fun add(img: SqliteImage, spec: Spec) {
        img.objects += SqlObject("table", spec.name, spec.name, spec.sql)
        for ((name, sql) in spec.indexes) img.objects += SqlObject("index", name, spec.name, sql)
        img.tables[spec.name] = SqlTable(spec.name, spec.columns, spec.rowidAlias, mutableListOf())
    }
}
