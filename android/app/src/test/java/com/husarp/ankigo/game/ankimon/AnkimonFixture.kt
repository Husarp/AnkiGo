package com.husarp.ankigo.game.ankimon

import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Test helpers for the Ankimon import: builds REAL SQLite files with the `sqlite3` command-line tool (the JVM has no
 * SQLite), using Ankimon's exact CREATE TABLE statements and made-up rows. Tests that need a file skip themselves
 * (Assume) when `sqlite3` isn't installed.
 */
object AnkimonFixture {
    /** Ankimon's schema (pyobj/database_manager.py `_setup_database`), statement for statement. */
    val SCHEMA = """
        CREATE TABLE IF NOT EXISTS captured_pokemon (
            individual_id TEXT PRIMARY KEY,
            is_main INTEGER DEFAULT 0,
            data TEXT NOT NULL,
            name TEXT GENERATED ALWAYS AS (json_extract(data, '${'$'}.name')) VIRTUAL,
            pokedex_id INTEGER GENERATED ALWAYS AS (json_extract(data, '${'$'}.id')) VIRTUAL,
            shiny BOOLEAN GENERATED ALWAYS AS (json_extract(data, '${'$'}.shiny')) VIRTUAL,
            level INTEGER GENERATED ALWAYS AS (json_extract(data, '${'$'}.level')) VIRTUAL
        );
        CREATE INDEX IF NOT EXISTS idx_pokemon_name ON captured_pokemon(name);
        CREATE INDEX IF NOT EXISTS idx_pokemon_pokedex_id ON captured_pokemon(pokedex_id);
        CREATE INDEX IF NOT EXISTS idx_pokemon_shiny ON captured_pokemon(shiny);
        CREATE INDEX IF NOT EXISTS idx_pokemon_level ON captured_pokemon(level);
        CREATE TABLE IF NOT EXISTS items (
            id INTEGER PRIMARY KEY,
            item_name TEXT UNIQUE,
            quantity INTEGER DEFAULT 0,
            data TEXT,
            category_id INTEGER,
            cost INTEGER,
            fling_power INTEGER,
            fling_effect_id INTEGER
        );
        CREATE TABLE IF NOT EXISTS badges (
            badge_id TEXT PRIMARY KEY,
            achieved BOOLEAN DEFAULT 0
        );
        CREATE TABLE IF NOT EXISTS metadata (
            key TEXT PRIMARY KEY,
            value TEXT
        );
        CREATE TABLE IF NOT EXISTS team (
            slot_position INTEGER PRIMARY KEY,
            individual_id TEXT NOT NULL
        );
        CREATE TABLE IF NOT EXISTS pokemon_history (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            individual_id TEXT UNIQUE,
            data TEXT NOT NULL
        );
        CREATE TABLE IF NOT EXISTS user_data (
            key TEXT PRIMARY KEY,
            value TEXT NOT NULL
        );
        CREATE TABLE IF NOT EXISTS config (
            key TEXT PRIMARY KEY,
            value TEXT NOT NULL
        );
    """.trimIndent()

    private val sqlite: String? by lazy {
        listOf("/usr/bin/sqlite3", "/usr/local/bin/sqlite3", "/bin/sqlite3").firstOrNull { File(it).canExecute() }
    }

    fun sqliteAvailable(): Boolean = sqlite != null

    /** Runs [sql] against a new database file [name] in [dir] and returns the file. Fails the test on any SQL error. */
    fun build(dir: File, name: String, sql: String): File {
        val exe = sqlite ?: throw IllegalStateException("sqlite3 is not installed")
        val db = File(dir, name)
        db.delete()
        val p = ProcessBuilder(exe, "-bail", db.absolutePath).redirectErrorStream(true).start()
        p.outputStream.use { it.write(sql.toByteArray(Charsets.UTF_8)) }
        val out = p.inputStream.readBytes().toString(Charsets.UTF_8)
        if (!p.waitFor(60, TimeUnit.SECONDS)) { p.destroyForcibly(); throw AssertionError("sqlite3 timed out") }
        if (p.exitValue() != 0) throw AssertionError("sqlite3 failed: $out")
        return db
    }

    /** SQL text literal. */
    fun q(s: String?): String = if (s == null) "NULL" else "'" + s.replace("'", "''") + "'"

    /** A Pokémon `data` JSON as Ankimon writes it; pass only the fields a test cares about. */
    fun monJson(vararg fields: Pair<String, Any?>): String = JSONObject(mapOf(*fields)).toString()

    fun insertMon(id: String, main: Boolean, json: String) =
        "INSERT INTO captured_pokemon (individual_id, is_main, data) VALUES (${q(id)}, ${if (main) 1 else 0}, ${q(json)});\n"

    /** A small, complete made-up Ankimon save (species numbers from TestData.world()). */
    fun smallSave(): String = buildString {
        append(SCHEMA).append('\n')
        append(insertMon("aaaa1111-0000-4000-8000-000000000001", true, monJson(
            "id" to 25, "name" to "Sparkmouse", "nickname" to "Zappy", "level" to 30, "xp" to 100,
            "iv" to mapOf("hp" to 31, "atk" to 20, "def" to 10, "spa" to 0, "spd" to 5, "spe" to 31),
            "ev" to mapOf("hp" to 4, "atk" to 252, "def" to 0, "spa" to 0, "spd" to 0, "spe" to 252),
            "nature" to "timid", "gender" to "F", "ability" to "Testability", "attacks" to listOf("Thunderbolt", "Quick Attack"),
            "shiny" to true, "friendship" to 200, "captured_date" to "2026-01-02 03:04:05", "pokemon_defeated" to 12, "tier" to "Normal",
        )))
        append(insertMon("aaaa1111-0000-4000-8000-000000000002", false, monJson(
            "id" to 1, "name" to "Leafling", "level" to 12, "xp" to 5, "nature" to "bold", "attacks" to listOf("Tackle", "Vine Whip"),
        )))
        append(insertMon("aaaa1111-0000-4000-8000-000000000003", false, monJson("id" to 16, "name" to "Birdie", "level" to 5)))
        append("INSERT INTO team (slot_position, individual_id) VALUES (1, 'aaaa1111-0000-4000-8000-000000000002'), (2, 'aaaa1111-0000-4000-8000-000000000001');\n")
        append("INSERT INTO items (id, item_name, quantity, data) VALUES (4, 'poke-ball', 12, '{}'), (17, 'potion', 3, '{}'), (18, 'tm-none', 0, '{}');\n")
        append("INSERT INTO items (id, item_name, quantity, data) VALUES (900, 'thunderbolt', 1, '{\"type\": \"TM\"}');\n")
        append("INSERT INTO user_data (key, value) VALUES ('pokedex_seen', '[1, 2, 25, 16]'), ('pokedex_caught', '[25, \"1\"]'), ('todays_shop', '[1]');\n")
        append("INSERT INTO config (key, value) VALUES ('trainer.cash', '12400'), ('trainer.name', '\"Ash\"'), ('sync.token', '\"SECRET-TOKEN\"');\n")
        append("INSERT INTO badges (badge_id, achieved) VALUES ('b1', 1);\n")
    }
}
