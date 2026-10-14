package com.husarp.ankigo.game.ankimon

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * The pure-Kotlin SQLite writer. Real SQLite (the sqlite3 command-line tool, 3.46) is the judge: every written file must pass
 * `PRAGMA integrity_check` and hold exactly the content of the file it was read from.
 */
class SqliteWriterTest {
    @get:Rule val tmp = TemporaryFolder()

    // ---- helpers ------------------------------------------------------------------------------------------------

    private val exe: String? by lazy { listOf("/usr/bin/sqlite3", "/usr/local/bin/sqlite3", "/bin/sqlite3").firstOrNull { File(it).canExecute() } }

    private fun db(sql: String, name: String = "src.db"): File {
        assumeTrue("sqlite3 is not installed", AnkimonFixture.sqliteAvailable())
        return AnkimonFixture.build(tmp.root, name, sql)
    }

    /** Runs [sql] in the sqlite3 shell against [file] and returns everything it printed (errors included). */
    private fun sqlite(file: File, sql: String): String {
        val p = ProcessBuilder(exe!!, file.absolutePath).redirectErrorStream(true).start()
        p.outputStream.use { it.write(sql.toByteArray(Charsets.UTF_8)) }
        val out = p.inputStream.readBytes().toString(Charsets.UTF_8)
        if (!p.waitFor(300, TimeUnit.SECONDS)) { p.destroyForcibly(); throw AssertionError("sqlite3 timed out") }
        return out
    }

    private fun image(f: File) = SqliteReader.open(f).image()

    private fun q(s: String?) = AnkimonFixture.q(s)

    private fun assertSameValue(label: String, a: Any?, b: Any?) {
        if (a is ByteArray && b is ByteArray) assertArrayEquals(label, a, b) else assertEquals(label, a, b)
    }

    private fun assertSameImage(a: SqliteImage, b: SqliteImage) {
        assertEquals("objects", a.objects, b.objects)
        assertEquals("table names", a.tables.keys.toList(), b.tables.keys.toList())
        assertEquals("page size", a.pageSize, b.pageSize)
        assertEquals("user_version", a.userVersion, b.userVersion)
        assertEquals("application_id", a.applicationId, b.applicationId)
        for ((name, ta) in a.tables) {
            val tb = b.tables.getValue(name)
            assertEquals("$name columns", ta.columns, tb.columns)
            assertEquals("$name alias", ta.rowidAlias, tb.rowidAlias)
            assertEquals("$name row count", ta.rows.size, tb.rows.size)
            for (i in ta.rows.indices) {
                assertEquals("$name row $i rowid", ta.rows[i].rowid, tb.rows[i].rowid)
                for (j in ta.columns.indices) assertSameValue("$name row $i ${ta.columns[j]}", ta.rows[i].values[j], tb.rows[i].values[j])
            }
        }
    }

    /** For every index of every table: a scan in index order equals a scan sorted by the same columns (SQLite itself judges the order). */
    private fun assertIndexOrders(file: File) {
        val tables = sqlite(file, "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%';").lines().filter { it.isNotBlank() }
        for (t in tables) {
            val indexes = sqlite(file, "SELECT name FROM pragma_index_list(${q(t)});").lines().filter { it.isNotBlank() }
            for (ix in indexes) {
                val cols = sqlite(file, "SELECT name FROM pragma_index_info(${q(ix)}) ORDER BY seqno;").lines().filter { it.isNotBlank() }
                val order = cols.joinToString(", ") { "\"$it\"" } + ", rowid"
                val viaIndex = sqlite(file, "SELECT group_concat(rowid) FROM (SELECT rowid FROM \"$t\" INDEXED BY \"$ix\" ORDER BY $order);")
                val sorted = sqlite(file, "SELECT group_concat(rowid) FROM (SELECT rowid FROM \"$t\" NOT INDEXED ORDER BY $order);")
                assertEquals("index $ix on $t", sorted, viaIndex)
            }
        }
    }

    /** image() -> write() -> real SQLite says ok, same dump, same schema, indexes in order; our check and reader agree. Returns the bytes. */
    private fun roundTrip(src: File, label: String = src.name): ByteArray {
        val img = image(src)
        val bytes = SqliteWriter.write(img)
        val out = File(tmp.root, "out-${src.name}").also { it.writeBytes(bytes) }
        assertEquals("$label integrity_check", "ok", sqlite(out, "PRAGMA integrity_check;").trim())
        assertEquals("$label quick_check", "ok", sqlite(out, "PRAGMA quick_check;").trim())
        assertEquals("$label SqliteCheck", emptyList<String>(), SqliteCheck.check(bytes))
        assertSameImage(img, SqliteReader(bytes).image())
        assertEquals("$label dump", sqlite(src, ".dump"), sqlite(out, ".dump"))
        val schema = "SELECT type, name, tbl_name, sql FROM sqlite_master; PRAGMA user_version; PRAGMA application_id; PRAGMA page_size; PRAGMA encoding; PRAGMA journal_mode;"
        assertEquals("$label schema", sqlite(src, schema), sqlite(out, schema))
        assertIndexOrders(out)
        return bytes
    }

    /** Ankimon's tables that are not in AnkimonFixture.SCHEMA (pyobj/database_manager.py _setup_database). */
    private val extraSchema = """
        CREATE TABLE IF NOT EXISTS pending_mobile_battles (
            id            INTEGER PRIMARY KEY AUTOINCREMENT,
            revlog_id     INTEGER UNIQUE NOT NULL,
            card_id       INTEGER NOT NULL,
            ease          INTEGER NOT NULL,
            review_time   INTEGER NOT NULL,
            review_type   INTEGER NOT NULL,
            queued_at     INTEGER NOT NULL,
            resolved      INTEGER NOT NULL DEFAULT 0,
            resolved_at   INTEGER
        );
        CREATE TABLE IF NOT EXISTS mobile_battle_history (
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
        );
        CREATE INDEX IF NOT EXISTS idx_history_timestamp ON mobile_battle_history(timestamp);
        CREATE TABLE IF NOT EXISTS desktop_processed_reviews (
            revlog_id INTEGER PRIMARY KEY,
            card_id   INTEGER
        );
    """.trimIndent()

    private val extraRows = """
        INSERT INTO pending_mobile_battles (revlog_id, card_id, ease, review_time, review_type, queued_at) VALUES (1700000000001, 5, 3, 1200, 1, 1700000001000), (1700000000002, 6, 1, 800, 0, 1700000002000);
        INSERT INTO mobile_battle_history (timestamp, enemy_id, enemy_name, enemy_level, enemy_shiny, outcome) VALUES (30, 25, 'Sparkmouse', 4, 0, 'win'), (10, 1, 'Leafling', 3, 1, 'loss'), (20, 16, 'Birdie', 9, 0, 'win');
        INSERT INTO desktop_processed_reviews VALUES (1700000000100, 7), (1700000000099, NULL);
    """.trimIndent()

    private fun fullAnkimon(pageSize: Int) = "PRAGMA page_size=$pageSize;\n" + AnkimonFixture.smallSave() + "\n" + extraSchema + "\n" + extraRows

    /** [n] Pokémon with JSON of [minPad]..[minPad]+[spread] bytes of padding, names in several scripts, in Ankimon's real table. */
    private fun manyMons(n: Int, pageSize: Int, minPad: Int = 1000, spread: Int = 4000) = """
        PRAGMA page_size=$pageSize;
        ${AnkimonFixture.SCHEMA}
        WITH RECURSIVE c(x) AS (SELECT 1 UNION ALL SELECT x + 1 FROM c WHERE x < $n)
        INSERT INTO captured_pokemon (individual_id, is_main, data)
        SELECT printf('%08x', (x * 2654435761) % 4294967296) || '-' || x, x = 1,
            json_object('id', (x * 7) % 905 + 1,
                'name', CASE x % 5 WHEN 0 THEN 'Pokémon ' || x WHEN 1 THEN 'ポケ ' || x WHEN 2 THEN 'Emoji 🐉 ' || x ELSE 'Mon ' || (x % 311) END,
                'level', x % 100 + 1, 'shiny', json(CASE WHEN x % 13 = 0 THEN 'true' ELSE 'false' END),
                'padding', substr(replace(hex(zeroblob(${minPad + spread})), '0', 'pq'), 1, $minPad + (x * 37) % $spread))
        FROM c;
        INSERT INTO items (id, item_name, quantity) VALUES (4, 'poke-ball', 12), (-1, 'uncatalogued', 1), (900, 'thunderbolt', 1);
        INSERT INTO team (slot_position, individual_id) VALUES (1, 'x'), (2, 'y');
        INSERT INTO pokemon_history (individual_id, data) VALUES ('h1', '{}'), ('h2', '{}'), ('h3', '{}');
        DELETE FROM pokemon_history WHERE individual_id = 'h3';
    """.trimIndent()

    // ---- Ankimon's own schema -----------------------------------------------------------------------------------

    @Test fun ankimonFullSchemaAtEveryPageSize() {
        for (ps in listOf(512, 1024, 2048, 4096, 8192, 32768, 65536)) {
            val bytes = roundTrip(db(fullAnkimon(ps), "ankimon$ps.db"), "page size $ps")
            assertEquals(ps, if (ps == 65536) 65536 else ((bytes[16].toInt() and 0xFF) shl 8) or (bytes[17].toInt() and 0xFF))
        }
    }

    @Test fun ankimonSchemaWithNoRowsAndWithEmptyTables() {
        roundTrip(db(AnkimonFixture.SCHEMA + "\n" + extraSchema, "empty.db"))
        roundTrip(db("PRAGMA page_size=512;\n" + AnkimonFixture.SCHEMA + "\n" + extraSchema, "empty512.db"))
        roundTrip(db("CREATE TABLE x (a);\nDROP TABLE x;", "nothing.db")) // a database with no tables at all
        roundTrip(db("CREATE TABLE t (a);", "one.db"))
    }

    @Test fun thousandsOfPokemonOnEveryPageSize() {
        for (ps in listOf(512, 1024, 4096, 65536)) {
            val src = db(manyMons(2500, ps), "many$ps.db")
            val bytes = roundTrip(src, "page size $ps")
            // multi-level trees were exercised: some interior pages exist in the file
            val interior = (1..bytes.size / (if (ps == 65536) 65536 else ps)).count {
                val pg = if (ps == 65536) 65536 else ps
                val h = (it - 1) * pg + if (it == 1) 100 else 0
                bytes[h].toInt() == 0x05 || bytes[h].toInt() == 0x02
            }
            assertTrue("interior pages at page size $ps", interior > 0)
        }
    }

    @Test fun autoindexesAreSortedAndUniqueAcrossLevels() {
        // individual_id is TEXT PRIMARY KEY: its autoindex holds 3000 pseudo-random texts of 40 bytes on 512-byte pages.
        val f = db(manyMons(3000, 512, 10, 20), "autoidx.db")
        val bytes = roundTrip(f)
        val out = File(tmp.root, "autoidx-out.db").also { it.writeBytes(bytes) }
        assertEquals("3000", sqlite(out, "SELECT count(*) FROM captured_pokemon WHERE individual_id >= '';").trim())
        assertEquals("x", sqlite(out, "SELECT individual_id FROM captured_pokemon WHERE individual_id = (SELECT min(individual_id) FROM captured_pokemon) AND 0 UNION ALL SELECT 'x';").trim())
    }

    // ---- JSON in generated columns ------------------------------------------------------------------------------

    private val u = "\\" + "u" // a JSON unicode escape starts with this

    private val jsonRows = listOf(
        """{"id": 25, "name": "Pikachu", "shiny": true, "level": 5}""",
        """{"id": 6, "name": "Pokémon é", "shiny": false, "level": 100}""",
        """{"id": 7, "name": "ポケモン", "shiny": false, "level": 9}""",
        """{"id": 8, "name": "dragon 🐉", "level": 1}""",
        """{"id": 9, "name": "quote \" backslash \\ slash \/ eacute ${u}00e9 tab \t nl \n", "level": 2}""",
        """{"id": 10, "name": "${u}d83d${u}dc09 escaped pair", "level": 3}""",
        """{"id": 3}""",
        """{"id": "25", "name": "string id"}""",
        """{"id": 25.0, "name": "real id"}""",
        """{"id": 25.5, "name": "fractional id"}""",
        """{"id": 1e2, "name": "exponent id", "level": 1E1}""",
        """{"id": 5, "name": "x", "shiny": 1, "level": 7}""",
        """{"id": 5, "name": "x", "shiny": "true", "level": "12"}""",
        """{"id": 5, "name": "x", "shiny": 0.0, "level": null}""",
        """{"id": null, "name": null, "shiny": null, "level": "abc"}""",
        """{"id": 12, "name": 42, "level": 7.5}""",
        """{"id": 13, "name": true}""",
        """{"id": 14, "name": {"k": [1,  2], "z" : "a b"}, "level": [1, 2]}""",
        """{"id": 15, "name": [1, "a b", {"x": null}, true, 2.50, "é${u}00e9"]}""",
        "  {\n\t\"id\" : 16 ,\n\t\"name\" : \"pretty\"\n}\n ",
        """{}""",
        """{"names": "no", "Name": "case matters", "id": -4, "level": -5}""",
        """{"id": 9223372036854775807, "name": "big", "level": -9223372036854775808}""",
        """{"a": {"x": 1, "x": 2}, "name": "nested duplicates are fine", "id": 17}""",
        """{"name": "", "id": 0, "level": 0, "shiny": 0}""",
        """{"id": 18, "name": "ünïcödé ${"x".repeat(900)}", "level": 4}""",
        """{"x": 12345678901234567890, "id": 19, "name": "unwanted big number is fine"}""",
        """{"id": 0.5, "name": "half", "level": 0.25}""",
        """{"id": 21, "name": "x", "level": "0x10"}""",
    )

    @Test fun jsonExtractCasesMatchRealSqlite() {
        val sb = StringBuilder(AnkimonFixture.SCHEMA).append('\n')
        sb.append("CREATE INDEX idx_multi ON captured_pokemon(shiny, name, is_main);\nCREATE UNIQUE INDEX idx_uq ON captured_pokemon(individual_id, level);\n")
        for ((i, j) in jsonRows.withIndex()) sb.append(AnkimonFixture.insertMon("m-%03d".format(i), i == 0, j))
        val f = db(sb.toString(), "json.db")
        for (ps in listOf(4096, 512)) {
            val src = if (ps == 4096) f else db("PRAGMA page_size=$ps;\n$sb", "json$ps.db")
            val bytes = roundTrip(src, "json page size $ps")
            val out = File(tmp.root, "json-read-$ps.db").also { it.writeBytes(bytes) }
            val cols = "SELECT individual_id, quote(name), quote(pokedex_id), quote(shiny), quote(level) FROM captured_pokemon ORDER BY rowid;"
            assertEquals(sqlite(src, cols), sqlite(out, cols))
            for (col in listOf("name", "pokedex_id", "shiny", "level")) {
                val where = "SELECT count(*), group_concat(individual_id) FROM captured_pokemon INDEXED BY idx_pokemon_${if (col == "pokedex_id") "pokedex_id" else col} WHERE $col IS NOT NULL;"
                assertEquals(sqlite(src, where), sqlite(out, where))
            }
        }
    }

    private fun mutated(sql: String, edit: (SqliteImage) -> Unit): SqliteImage = image(db(sql, "mut.db")).also(edit)

    private fun monImage() = mutated(AnkimonFixture.smallSave()) {}

    private fun setData(img: SqliteImage, row: Int, value: Any?) {
        val t = img.table("captured_pokemon")!!
        t[t.rows[row], "data"] = value
    }

    @Test fun badJsonInAnIndexedGeneratedColumnIsRefused() {
        val bad = listOf(
            """{"name": }""", """[1, 2]""", """"just a string"""", "42", "", "   ", """{"name": "a", "name": "b"}""", """{"id": 1,}""",
            """{'name': 'x'}""", """{"id": NaN}""", """{"id": Infinity}""", """{"id": 12345678901234567890}""", """{"id": 0.1234567890123456789}""",
            """{"id": -0}""", """{"name": 1.5}""", """{"name": "a\u0000b"}""", """{"name": "\ud800"}""", """{"name": "\q"}""", """{"name": "tab	raw"}""",
            """{"name": "x"} trailing""", """{"name": "x"""", """{"na${u}006de": "x"}""", """{/* c */ "name": "x"}""", """{"name": 0x10}""",
            """{"id": " 20"}""", """{"level": 0.1}""", """{"id": 1.5e23}""", """{"id": "1e400"}""", """{"id": "99999999999999999999"}""", """{"id": 1e400}""", """{"id": 1e30}""", """{"level": 1.5e23}""", """{"name": [1, 2}""", """{"name": {"a" 1}}""",
        )
        for ((i, b) in bad.withIndex()) {
            val img = monImage()
            setData(img, 1, b)
            val e = assertThrows("case $i: $b", IllegalArgumentException::class.java) { SqliteWriter.write(img) }
            assertTrue(e.message!!, e.message!!.contains("captured_pokemon"))
        }
        // a data value that is not text
        for (v in listOf(5L, 2.5, byteArrayOf(1, 2))) {
            val img = monImage()
            setData(img, 0, v)
            assertThrows(IllegalArgumentException::class.java) { SqliteWriter.write(img) }
        }
    }

    @Test fun anUnindexedGeneratedColumnIsNeverEvaluated() {
        // A REAL in a TEXT generated column would be refused if an index needed it; with no index nothing is evaluated.
        val f = db("CREATE TABLE t (id INTEGER PRIMARY KEY, data TEXT, name TEXT AS (json_extract(data, '$.name')));\nINSERT INTO t (data) VALUES ('{\"name\":\"a\"}');")
        val img = image(f)
        img.table("t")!!.let { it[it.rows[0], "data"] = "{\"name\": 1.5}" }
        val bytes = SqliteWriter.write(img)
        assertEquals(emptyList<String>(), SqliteCheck.check(bytes))
        val out = File(tmp.root, "o.db").also { it.writeBytes(bytes) }
        assertEquals("ok", sqlite(out, "PRAGMA integrity_check;").trim())
    }

    @Test fun editedDataIsReindexed() {
        // What the Ankimon export does: change plain values in the image, write; real SQLite must find the new keys in the indexes.
        val f = db(AnkimonFixture.smallSave())
        val img = image(f)
        val t = img.table("captured_pokemon")!!
        t[t.rows[1], "data"] = """{"id": 4, "name": "Charling", "level": 99, "shiny": true}"""
        t.insert(mapOf("individual_id" to "new-1", "is_main" to 0L, "data" to """{"id": 1, "name": "Zed", "level": 1}"""))
        val items = img.table("items")!!
        items.rows.removeAt(0)
        items.insert(mapOf("id" to 5L, "item_name" to "great-ball", "quantity" to 3L))
        val out = File(tmp.root, "edited.db")
        val bytes = SqliteWriter.write(img).also { out.writeBytes(it) }
        assertEquals("ok", sqlite(out, "PRAGMA integrity_check;").trim())
        assertEquals(emptyList<String>(), SqliteCheck.check(bytes))
        assertEquals("aaaa1111-0000-4000-8000-000000000002\n", sqlite(out, "SELECT individual_id FROM captured_pokemon INDEXED BY idx_pokemon_level WHERE level = 99;"))
        assertEquals("new-1\n", sqlite(out, "SELECT individual_id FROM captured_pokemon INDEXED BY idx_pokemon_name WHERE name = 'Zed';"))
        assertEquals("1\n", sqlite(out, "SELECT count(*) FROM items WHERE item_name = 'great-ball';"))
        assertIndexOrders(out)
    }

    @Test fun aUniqueIndexOnAGeneratedColumnIsEnforced() {
        val sql = AnkimonFixture.smallSave() + "\nCREATE UNIQUE INDEX uq_name ON captured_pokemon(name);\n"
        roundTrip(db(sql, "uq.db"))
        val img = image(db(sql, "uq2.db"))
        setData(img, 1, """{"id": 5, "name": "Sparkmouse", "level": 3}""") // the same name as row 0
        val e = assertThrows(IllegalArgumentException::class.java) { SqliteWriter.write(img) }
        assertTrue(e.message!!, e.message!!.contains("UNIQUE") && e.message!!.contains("name"))
        // names that are NULL never clash
        val img2 = image(db(sql, "uq3.db"))
        setData(img2, 1, """{"id": 5}""")
        setData(img2, 2, """{"id": 6}""")
        val out = File(tmp.root, "uq-null.db").also { it.writeBytes(SqliteWriter.write(img2)) }
        assertEquals("ok", sqlite(out, "PRAGMA integrity_check;").trim())
    }

    // ---- values -------------------------------------------------------------------------------------------------

    @Test fun everyKindOfValueInPlainTables() {
        val ints = listOf(
            0L, 1L, -1L, 2L, 127L, -128L, 128L, -129L, 32767L, -32768L, 32768L, 8388607L, 8388608L, -8388608L, -8388609L,
            2147483647L, 2147483648L, -2147483648L, -2147483649L, 140737488355327L, 140737488355328L, -140737488355328L, -140737488355329L,
            Long.MAX_VALUE, Long.MIN_VALUE,
        )
        val sb = StringBuilder(
            """
            CREATE TABLE v (id INTEGER PRIMARY KEY, i INTEGER, r REAL, n NUMERIC, t TEXT, b BLOB, x, u INT UNIQUE);
            CREATE INDEX v_i ON v(i);
            CREATE INDEX v_r ON v(r, t);
            CREATE INDEX v_x ON v(x);
            CREATE INDEX v_b ON v(b, n);
            """.trimIndent(),
        ).append('\n')
        for ((k, n) in ints.withIndex()) {
            val lit = if (n == Long.MIN_VALUE) "(-9223372036854775807-1)" else n.toString()
            sb.append("INSERT INTO v (id, i, u) VALUES (${k + 1}, $lit, $lit);\n")
        }
        sb.append(
            """
            INSERT INTO v VALUES (100, 3.25, 3.25, 3.25, 'plain', x'00FF10', NULL, 1000);
            INSERT INTO v VALUES (101, -0.5, -0.5, -0.5, '', x'', 7, 1001);
            INSERT INTO v VALUES (102, 3.0, 3.0, 3.0, ${q("ąęś 日本語 🐉 \"q\" 'single'")}, zeroblob(5), 'text in an untyped column', 1002);
            INSERT INTO v VALUES (103, 'abc', 1e300, 1e300, 'x', x'DEADBEEF', 2.5, 1003);
            INSERT INTO v VALUES (104, NULL, NULL, NULL, NULL, NULL, NULL, NULL);
            INSERT INTO v VALUES (105, NULL, NULL, NULL, NULL, NULL, NULL, NULL);
            INSERT INTO v VALUES (106, '12', '2.5', '0x10', 12, 12, x'41', 1004);
            INSERT INTO v VALUES (107, 0.0, -0.0, -0.0, 0.0, 0.0, 0.0, 1005);
            INSERT INTO v VALUES (108, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1006);
            INSERT INTO v VALUES (109, 9e18, 9e18, 9.3e18, 9e18, 9e18, 1e19, 1007);
            INSERT INTO v VALUES (-5, 5, 5, 5, '5', x'05', 5, 1008);
            INSERT INTO v VALUES (-9000000000, 6, 6, 6, '6', x'06', 6, 1009);
            INSERT INTO v VALUES (9000000000, 7, 7, 7, '7', x'07', 7, 1010);
            INSERT INTO v VALUES (9223372036854775807, 8, 8, 8, '8', x'08', 8, 1011);
            INSERT INTO v VALUES (-9223372036854775808, 9, 9, 9, '9', x'09', 9, 1012);
            INSERT INTO v VALUES (110, 'b', 'b', 'b', 'b', 'b', 'b', 'b');
            INSERT INTO v VALUES (111, 'B', 'a', 'a', 'B', x'62', x'62', x'62');
            """.trimIndent(),
        )
        for (ps in listOf(512, 4096)) roundTrip(db("PRAGMA page_size=$ps;\n$sb", "values$ps.db"))
    }

    @Test fun textThatLooksLikeNumbersInColumnsWithAffinity() {
        // Real SQLite converts these on INSERT; reading and writing them must not disturb anything.
        roundTrip(
            db(
                """
                CREATE TABLE a (id INTEGER PRIMARY KEY, i INTEGER, n NUMERIC, r REAL, t TEXT, x);
                INSERT INTO a VALUES (1, '42', '42', '42', 42, '42');
                INSERT INTO a VALUES (2, '1e3', '1e3', '1e3', 1e3, '1e3');
                INSERT INTO a VALUES (3, '  7  ', ' 7 ', ' 7 ', ' 7 ', ' 7 ');
                INSERT INTO a VALUES (4, '4.50', '4.50', '4.50', 4.5, '4.50');
                INSERT INTO a VALUES (5, '12abc', '0x1F', '1e', '', '+5');
                INSERT INTO a VALUES (6, '-0', '-0.0', '-0', '-0', '-0');
                INSERT INTO a VALUES (7, '9223372036854775807', '9223372036854775808', '123456789012345678', '1', 1);
                """.trimIndent(),
            ),
        )
    }

    /** One column value as Kotlin gives it to the writer and as the same INSERT would say it. */
    private class V(val kotlin: Any?, val sql: String)

    @Test fun affinityIsAppliedLikeSqliteDoesOnInsert() {
        val cols = listOf("t TEXT", "i INTEGER", "n NUMERIC", "r REAL", "b BLOB", "x", "v VARCHAR(20)", "f FLOAT", "d DOUBLE PRECISION", "bo BOOLEAN", "c CLOB", "up UNSIGNED BIG INT")
        val values = listOf(
            V(12L, "12"), V(-7L, "-7"), V(0L, "0"), V(Long.MAX_VALUE, "9223372036854775807"), V("42", "'42'"), V("-42", "'-42'"), V("+42", "'+42'"),
            V("4.0", "'4.0'"), V("4.5", "'4.5'"), V("1e2", "'1e2'"), V("1E2", "'1E2'"), V(".5", "'.5'"), V("5.", "'5.'"), V("007", "'007'"),
            V("abc", "'abc'"), V("", "''"), V("12abc", "'12abc'"), V("0x10", "'0x10'"), V("1e", "'1e'"), V("e5", "'e5'"), V("-", "'-'"), V(".", "'.'"),
            V("Infinity", "'Infinity'"), V("NaN", "'NaN'"), V("1_0", "'1_0'"), V("١٢", "'١٢'"),
            V(7.0, "7.0"), V(7.5, "7.5"), V(-0.0, "-0.0"), V(0.0, "0.0"), V(1e15, "1e15"), V(1e18, "1e18"), V(1e19, "1e19"), V(-9.0e18, "-9.0e18"),
            V(0.1, "0.1"), V(123456.789, "123456.789"), V(Double.MAX_VALUE, "1.7976931348623157e308"),
            V(byteArrayOf(1, 2, 3), "x'010203'"), V(byteArrayOf(), "x''"), V(null, "NULL"), V(3, "3"), V("é日本", "'é日本'"),
        )
        // Doubles can't go into TEXT / CLOB / VARCHAR columns (exact formatting isn't reproduced): left out for those columns.
        val sb = StringBuilder("CREATE TABLE a (id INTEGER PRIMARY KEY, ${cols.joinToString()});\nCREATE INDEX ai ON a(t);\nCREATE INDEX an ON a(n, i);\nCREATE INDEX ar ON a(r);\n")
        val rows = ArrayList<SqlRow>()
        var id = 0L
        for (v in values) {
            id++
            val textual = v.kotlin is Double
            val kv = cols.map { c -> if (textual && (c.startsWith("t ") || c.startsWith("v ") || c.startsWith("c "))) null else v.kotlin }
            val sv = cols.map { c -> if (textual && (c.startsWith("t ") || c.startsWith("v ") || c.startsWith("c "))) "NULL" else v.sql }
            rows += SqlRow(id, (listOf<Any?>(id) + kv).toMutableList())
            sb.append("INSERT INTO a VALUES ($id, ${sv.joinToString()});\n")
        }
        val src = db(sb.toString(), "affinity-src.db")
        // The same statements through SQLite's INSERT give the expected file; ours is built from the Kotlin values.
        val sourceImg = image(src)
        val hand = SqliteImage(
            sourceImg.objects.toMutableList(),
            mutableMapOf("a" to SqlTable("a", sourceImg.table("a")!!.columns, 0, rows)),
        )
        val bytes = SqliteWriter.write(hand)
        val out = File(tmp.root, "affinity-out.db").also { it.writeBytes(bytes) }
        assertEquals("ok", sqlite(out, "PRAGMA integrity_check;").trim())
        assertEquals(emptyList<String>(), SqliteCheck.check(bytes))
        assertEquals(sqlite(src, ".dump"), sqlite(out, ".dump"))
        assertEquals(
            sqlite(src, "SELECT id, typeof(t), typeof(i), typeof(n), typeof(r), typeof(b), typeof(x), typeof(up) FROM a;"),
            sqlite(out, "SELECT id, typeof(t), typeof(i), typeof(n), typeof(r), typeof(b), typeof(x), typeof(up) FROM a;"),
        )
        assertIndexOrders(out)
        assertSameImage(sourceImg, SqliteReader(bytes).image())
    }

    @Test fun valuesTheWriterCantReproduceAreRefused() {
        fun oneRow(type: String, v: Any?): SqliteImage {
            val sql = "CREATE TABLE a (id INTEGER PRIMARY KEY, c $type)"
            return SqliteImage(
                mutableListOf(SqlObject("table", "a", "a", sql)),
                mutableMapOf("a" to SqlTable("a", listOf("id", "c"), 0, mutableListOf(SqlRow(1, mutableListOf(1L, v))))),
            )
        }
        assertThrows(IllegalArgumentException::class.java) { SqliteWriter.write(oneRow("TEXT", 1.5)) }       // REAL formatting
        assertThrows(IllegalArgumentException::class.java) { SqliteWriter.write(oneRow("INTEGER", " 5")) }   // spaces around a number
        assertThrows(IllegalArgumentException::class.java) { SqliteWriter.write(oneRow("INTEGER", "5 ")) }
        assertThrows(IllegalArgumentException::class.java) { SqliteWriter.write(oneRow("INTEGER", "99999999999999999999")) } // beyond 64 bits
        assertThrows(IllegalArgumentException::class.java) { SqliteWriter.write(oneRow("NUMERIC", "0.1234567890123456789")) }  // > 15 digits
        assertThrows(IllegalArgumentException::class.java) { SqliteWriter.write(oneRow("REAL", Double.NaN)) }
        assertThrows(IllegalArgumentException::class.java) { SqliteWriter.write(oneRow("REAL", Double.POSITIVE_INFINITY)) }
        assertThrows(IllegalArgumentException::class.java) { SqliteWriter.write(oneRow("BLOB", true)) }
        assertThrows(IllegalArgumentException::class.java) { SqliteWriter.write(oneRow("BLOB", 1.5f)) }
        assertThrows(IllegalArgumentException::class.java) { SqliteWriter.write(oneRow("TEXT", "a\ud800b")) }     // unpaired surrogate
        SqliteWriter.write(oneRow("TEXT", "fine 🐉")) // a surrogate pair is fine
    }

    @Test fun rowidsGapsNegativeAndLargeKeepTheirOrder() {
        // rows given to the writer in any order come out in rowid order
        val f = db("CREATE TABLE t (id INTEGER PRIMARY KEY, v TEXT);\nINSERT INTO t VALUES (-3,'a'),(5,'b'),(-100,'c'),(9000000000,'d');")
        val img = image(f)
        img.table("t")!!.rows.reverse()
        val bytes = SqliteWriter.write(img)
        assertEquals(listOf(-100L, -3L, 5L, 9000000000L), SqliteReader(bytes).image().table("t")!!.rows.map { it.rowid })
        val out = File(tmp.root, "o.db").also { it.writeBytes(bytes) }
        assertEquals("ok", sqlite(out, "PRAGMA integrity_check;").trim())
    }

    // ---- schema features ----------------------------------------------------------------------------------------

    @Test fun shortRecordsFromAlterTableAreCompletedWithTheDefault() {
        val f = db(
            """
            CREATE TABLE captured_pokemon (
                individual_id TEXT PRIMARY KEY,
                data TEXT NOT NULL,
                name TEXT GENERATED ALWAYS AS (json_extract(data, '${'$'}.name')) VIRTUAL,
                pokedex_id INTEGER GENERATED ALWAYS AS (json_extract(data, '${'$'}.id')) VIRTUAL
            );
            CREATE INDEX idx_pokemon_name ON captured_pokemon(name);
            INSERT INTO captured_pokemon (individual_id, data) VALUES ('old-1', '{"id": 1, "name": "a"}'), ('old-2', '{"id": 2, "name": "b"}');
            ALTER TABLE captured_pokemon ADD COLUMN is_main INTEGER DEFAULT 0;
            ALTER TABLE captured_pokemon ADD COLUMN note TEXT DEFAULT 'none';
            ALTER TABLE captured_pokemon ADD COLUMN ratio REAL DEFAULT 2;
            ALTER TABLE captured_pokemon ADD COLUMN plain;
            ALTER TABLE captured_pokemon ADD COLUMN neg INTEGER DEFAULT -5;
            ALTER TABLE captured_pokemon ADD COLUMN flag BOOLEAN DEFAULT TRUE;
            INSERT INTO captured_pokemon (individual_id, data, is_main) VALUES ('new-3', '{"id": 3, "name": "c"}', 1);
            """.trimIndent(),
        )
        val img = image(f)
        val t = img.table("captured_pokemon")!!
        assertEquals(listOf("individual_id", "data", "is_main", "note", "ratio", "plain", "neg", "flag"), t.columns)
        assertEquals(listOf<Any?>("old-1", "{\"id\": 1, \"name\": \"a\"}", 0L, "none", 2.0, null, -5L, 1L), t.rows[0].values)
        roundTrip(f)
    }

    @Test fun anExpressionDefaultThatIsNeededToCompleteARowIsRefused() {
        // SQLite itself only allows literal defaults in ALTER TABLE ADD COLUMN; the schema text is edited to make the unusual case.
        val withShortRow = db(
            "CREATE TABLE t (a INTEGER);\nINSERT INTO t VALUES (1);\nALTER TABLE t ADD COLUMN b INTEGER DEFAULT 5;\n" +
                ".dbconfig defensive off\nPRAGMA writable_schema = ON;\nUPDATE sqlite_master SET sql = replace(sql, 'DEFAULT 5', 'DEFAULT (1 + 4)') WHERE name = 't';\nPRAGMA writable_schema = OFF;\n",
            "short.db",
        )
        assertThrows(SqliteFormatException::class.java) { image(withShortRow) }
        // the same default is harmless when no row is short
        val fine = db("CREATE TABLE t (a INTEGER, b INTEGER DEFAULT (1 + 2));\nINSERT INTO t VALUES (1, 4);\n", "fine.db")
        roundTrip(fine)
    }

    @Test fun autoincrementSequenceIsRaisedNeverLowered() {
        val f = db(
            """
            CREATE TABLE pokemon_history (id INTEGER PRIMARY KEY AUTOINCREMENT, individual_id TEXT UNIQUE, data TEXT NOT NULL);
            CREATE TABLE other (id INTEGER PRIMARY KEY AUTOINCREMENT, v);
            INSERT INTO pokemon_history (individual_id, data) VALUES ('a','{}'),('b','{}'),('c','{}'),('d','{}'),('e','{}');
            DELETE FROM pokemon_history WHERE id > 3;
            INSERT INTO other (v) VALUES (1), (2);
            """.trimIndent(),
        )
        // as read: seq is 5 although the largest rowid is 3: kept
        roundTrip(f)
        val img = image(f)
        val seq = img.table("sqlite_sequence")!!
        assertEquals(listOf<Any?>("pokemon_history", 5L), seq.rows.single { it.values[0] == "pokemon_history" }.values)
        // lowered to 1: raised to the largest rowid (3); the other table (seq 2 = its max) is untouched
        seq.rows.single { it.values[0] == "pokemon_history" }.values[1] = 1L
        val out = File(tmp.root, "seq.db").also { it.writeBytes(SqliteWriter.write(img)) }
        assertEquals("other|2\npokemon_history|3\n", sqlite(out, "SELECT name, seq FROM sqlite_sequence ORDER BY name;"))
        assertEquals("ok", sqlite(out, "PRAGMA integrity_check;").trim())
        // the row is gone: it is added
        val img2 = image(f)
        img2.table("sqlite_sequence")!!.rows.removeAll { true }
        val out2 = File(tmp.root, "seq2.db").also { it.writeBytes(SqliteWriter.write(img2)) }
        assertEquals("other|2\npokemon_history|3\n", sqlite(out2, "SELECT name, seq FROM sqlite_sequence ORDER BY name;"))
        // new rows in the image: seq follows them (what Ankimon's next INSERT would do)
        val img3 = image(f)
        img3.table("other")!!.insert(mapOf("v" to 3L, "id" to 50L))
        val out3 = File(tmp.root, "seq3.db").also { it.writeBytes(SqliteWriter.write(img3)) }
        assertEquals("other|50\npokemon_history|5\n", sqlite(out3, "SELECT name, seq FROM sqlite_sequence ORDER BY name;"))
        assertEquals("ok", sqlite(out3, "PRAGMA integrity_check;").trim())
    }

    @Test fun anImageWithAnAutoincrementTableButNoSequenceTableGetsOne() {
        val sql = "CREATE TABLE p (id INTEGER PRIMARY KEY AUTOINCREMENT, v TEXT)"
        val img = SqliteImage(
            mutableListOf(SqlObject("table", "p", "p", sql)),
            mutableMapOf("p" to SqlTable("p", listOf("id", "v"), 0, mutableListOf(SqlRow(4, mutableListOf(4L, "x")), SqlRow(9, mutableListOf(9L, "y"))))),
        )
        val out = File(tmp.root, "p.db").also { it.writeBytes(SqliteWriter.write(img)) }
        assertEquals("ok", sqlite(out, "PRAGMA integrity_check;").trim())
        assertEquals("p|9\n", sqlite(out, "SELECT name, seq FROM sqlite_sequence;"))
        assertEquals("1", sqlite(out, "INSERT INTO p (v) VALUES ('z'); SELECT count(*) FROM p WHERE id = 10;").trim())
        assertEquals(1, img.objects.size) // the image is not changed
        assertEquals(2, img.table("p")!!.rows.size)
    }

    @Test fun viewsAndTriggersAreCopiedAndStillWork() {
        val f = db(
            """
            CREATE TABLE a (x INTEGER PRIMARY KEY, y TEXT UNIQUE);
            CREATE TABLE log (m TEXT);
            CREATE VIEW v AS SELECT x, upper(y) AS uy FROM a;
            CREATE TRIGGER tr AFTER INSERT ON a BEGIN INSERT INTO log VALUES ('ins ' || new.x); END;
            CREATE INDEX a_y2 ON a(y, x);
            INSERT INTO a VALUES (1, 'one'), (2, 'two');
            """.trimIndent(),
        )
        val bytes = roundTrip(f)
        val out = File(tmp.root, "vt.db").also { it.writeBytes(bytes) }
        assertEquals("1|ONE\n2|TWO\n", sqlite(out, "SELECT * FROM v;"))
        assertEquals("3\n", sqlite(out, "INSERT INTO a VALUES (3, 'three'); SELECT count(*) FROM log;"))
        assertEquals("ok", sqlite(out, "PRAGMA integrity_check;").trim())
    }

    @Test fun autoindexNamesAndDeduplicationMatchSqlite() {
        val f = db(
            """
            CREATE TABLE t1 (a INTEGER PRIMARY KEY UNIQUE, b);
            CREATE TABLE t2 (a TEXT PRIMARY KEY UNIQUE, b UNIQUE, c, UNIQUE (b), UNIQUE (a, b), UNIQUE (a, b), UNIQUE (c));
            CREATE TABLE t3 (a INTEGER, b, PRIMARY KEY (a), UNIQUE (a));
            CREATE TABLE t4 (a INT PRIMARY KEY NOT NULL, b TEXT UNIQUE);
            CREATE TABLE t5 (a INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT);
            CREATE TABLE t6 ("we ird" TEXT, [x y] INTEGER, `back``tick` TEXT, PRIMARY KEY ("we ird", [x y]), UNIQUE (`back``tick`));
            CREATE TABLE t7 (a, b, c, CONSTRAINT u1 UNIQUE (a), CONSTRAINT u2 UNIQUE (b, c));
            INSERT INTO t1 VALUES (1, 1), (2, 2);
            INSERT INTO t2 VALUES ('a', 'b', 'c'), ('a2', 'b2', NULL), ('a3', 'b3', NULL), ('a4', 'b4', 'c4');
            INSERT INTO t3 VALUES (3, 3);
            INSERT INTO t4 VALUES (9, 'x'), (8, 'y'), (7, NULL), (6, NULL);
            INSERT INTO t6 VALUES ('p', 1, 'q'), ('p', 2, 'r');
            INSERT INTO t7 VALUES (1, 2, 3), (NULL, 2, 4), (NULL, 3, 3), (NULL, NULL, NULL), (5, NULL, NULL), (6, NULL, NULL);
            """.trimIndent(),
        )
        val bytes = roundTrip(f)
        assertEquals(
            image(f).objects.map { it.name },
            SqliteReader(bytes).image().objects.map { it.name },
        )
    }

    @Test fun uniqueNullsAreDistinct() {
        roundTrip(db("CREATE TABLE u (a UNIQUE, b, UNIQUE (b));\nINSERT INTO u VALUES (NULL, NULL), (NULL, NULL), (1, 2), (NULL, 3);"))
    }

    @Test fun unusualButPlainSchemaText() {
        roundTrip(
            db(
                """
                CREATE TABLE "we ird(name" (
                    "col one" TEXT DEFAULT 'a,b(c)' NOT NULL, -- a comment, with a comma ) and a paren (
                    [x y] INTEGER,
                    `back``tick` TEXT COLLATE NOCASE,
                    /* block, comment ) */ amount NUMERIC(10, 2) DEFAULT (cast(1 as int)),
                    PRIMARY KEY ("col one", amount),
                    FOREIGN KEY ([x y]) REFERENCES other(id) ON DELETE SET NULL
                );
                CREATE TABLE other (id INTEGER PRIMARY KEY, ref INTEGER REFERENCES other(id) ON DELETE CASCADE ON UPDATE SET DEFAULT NOT NULL DEFERRABLE INITIALLY DEFERRED);
                CREATE INDEX "idx weird" ON "we ird(name"("col one" ASC, [x y] COLLATE BINARY, amount);
                INSERT INTO "we ird(name" VALUES ('k', 7, 'Bt', 1.5), ('k', 8, 'bT', 2.5), ('j', NULL, NULL, 3);
                """.trimIndent(),
            ),
        )
    }

    @Test fun pragmaHeaderFieldsSurvive() {
        val f = db("PRAGMA user_version = 123456;\nPRAGMA application_id = -42;\nCREATE TABLE t (a);\nINSERT INTO t VALUES (1);")
        val img = image(f)
        assertEquals(123456, img.userVersion)
        assertEquals(-42, img.applicationId)
        val out = File(tmp.root, "o.db").also { it.writeBytes(SqliteWriter.write(img)) }
        assertEquals("123456\n-42\n", sqlite(out, "PRAGMA user_version; PRAGMA application_id;"))
        // the header of a fresh file as the spec lists it
        val b = SqliteWriter.write(img)
        assertEquals("SQLite format 3\u0000", String(b, 0, 16, Charsets.ISO_8859_1))
        assertEquals(listOf(1, 1, 0, 64, 32, 32), (18..23).map { b[it].toInt() })
        assertEquals(1L, u32(b, 24)); assertEquals((b.size / 4096).toLong(), u32(b, 28)); assertEquals(0L, u32(b, 32)); assertEquals(0L, u32(b, 36))
        assertEquals(1L, u32(b, 40)); assertEquals(4L, u32(b, 44)); assertEquals(0L, u32(b, 52)); assertEquals(1L, u32(b, 56)); assertEquals(0L, u32(b, 64))
        assertEquals(1L, u32(b, 92)); assertEquals(3046001L, u32(b, 96))
    }

    private fun u32(b: ByteArray, o: Int): Long = (0 until 4).fold(0L) { acc, i -> (acc shl 8) or (b[o + i].toLong() and 0xFF) }

    @Test fun writingIsDeterministicAndReadingItsOwnOutputIsStable() {
        val f = db(fullAnkimon(1024))
        val first = SqliteWriter.write(image(f))
        val second = SqliteWriter.write(SqliteReader(first).image())
        assertArrayEquals(first, second)
    }

    @Test fun theImageIsNotChangedByWriting() {
        val img = image(db(fullAnkimon(4096)))
        val before = img.table("captured_pokemon")!!.rows.map { it.values.toList() to it.rowid }
        val objects = img.objects.toList()
        SqliteWriter.write(img)
        SqliteWriter.write(img)
        assertEquals(before, img.table("captured_pokemon")!!.rows.map { it.values.toList() to it.rowid })
        assertEquals(objects, img.objects)
    }

    // ---- fuzz ---------------------------------------------------------------------------------------------------

    @Test(timeout = 600_000) fun seededFuzzOfPlainTablesWithMixedTypesAndUniqueIndexes() {
        val types = listOf("INTEGER", "TEXT", "REAL", "BLOB", "NUMERIC", "", "VARCHAR(20)", "BOOLEAN", "DOUBLE", "INT", "DATETIME")
        for (seed in 1..40) {
            val rnd = Random(seed)
            val pageSize = listOf(512, 1024, 4096)[seed % 3]
            val ncols = 3 + rnd.nextInt(4)
            val colTypes = List(ncols) { types[rnd.nextInt(types.size)] }
            val rowCount = rnd.nextInt(400)
            val sb = StringBuilder("PRAGMA page_size=$pageSize;\nCREATE TABLE f (id INTEGER PRIMARY KEY, ")
            sb.append(colTypes.mapIndexed { i, t -> "c$i $t" }.joinToString())
            sb.append(", u TEXT UNIQUE, k1 TEXT, k2 INTEGER, UNIQUE (k1, k2));\n")
            sb.append("CREATE INDEX f_a ON f(c0);\nCREATE INDEX f_b ON f(c1, c2);\n")
            if (ncols > 3) sb.append("CREATE INDEX f_c ON f(c3, c0, c1);\n")
            fun lit(): String = when (rnd.nextInt(9)) {
                0 -> "NULL"
                1 -> (rnd.nextLong() shr rnd.nextInt(64)).toString().let { if (it == "-9223372036854775808") "0" else it }
                2 -> (rnd.nextInt(200) - 100).toString()
                3 -> "%.6f".format(java.util.Locale.ROOT, (rnd.nextDouble() - 0.5) * 10.0.pow(rnd.nextInt(30) - 10))
                4 -> q(List(rnd.nextInt(60)) { "abcXYZ é日🐉 '\"".random(rnd) }.joinToString(""))
                5 -> "x'" + List(rnd.nextInt(30)) { "%02x".format(rnd.nextInt(256)) }.joinToString("") + "'"
                6 -> q(rnd.nextInt(1000).toString())
                7 -> q("z".repeat(rnd.nextInt(700)))
                else -> rnd.nextInt(5).toString()
            }
            var id = 0L
            repeat(rowCount) { r ->
                id += 1 + (if (rnd.nextInt(10) == 0) rnd.nextLong(1000) else 0L)
                if (rnd.nextInt(30) == 0) id = -id.coerceAtLeast(1)
                val cells = List(ncols) { lit() }
                sb.append("INSERT OR IGNORE INTO f VALUES ($id, ${cells.joinToString()}, ${q("u$r-" + rnd.nextInt(100))}, ${q("k" + rnd.nextInt(5))}, $r);\n")
            }
            roundTrip(db(sb.toString(), "fuzz$seed.db"), "fuzz seed $seed")
        }
    }

    private fun Double.pow(n: Int): Double = Math.pow(this, n.toDouble())

    // ---- tree shapes --------------------------------------------------------------------------------------------

    /** A hand-built image of table t(id, v TEXT, w INTEGER) with two indexes and a UNIQUE column, [n] rows, values of [len]. */
    private fun treeImage(pageSize: Int, n: Int, ids: (Int) -> Long, len: (Int) -> Int): SqliteImage {
        val sql = "CREATE TABLE t (id INTEGER PRIMARY KEY, v TEXT, w INTEGER, u TEXT UNIQUE)"
        val rows = ArrayList<SqlRow>()
        for (i in 0 until n) {
            val id = ids(i)
            rows += SqlRow(id, mutableListOf(id, "v" + "x".repeat(len(i)) + (i * 7919 % 1000), (i * 31 % 17).toLong(), "u%06d".format(i * 7919 % 1000003)))
        }
        return SqliteImage(
            mutableListOf(
                SqlObject("table", "t", "t", sql),
                SqlObject("index", "sqlite_autoindex_t_1", "t", null),
                SqlObject("index", "t_v", "t", "CREATE INDEX t_v ON t(v)"),
                SqlObject("index", "t_w", "t", "CREATE INDEX t_w ON t(w, v)"),
            ),
            mutableMapOf("t" to SqlTable("t", listOf("id", "v", "w", "u"), 0, rows)),
            pageSize = pageSize,
        )
    }

    private fun verify(img: SqliteImage, label: String): ByteArray {
        val bytes = SqliteWriter.write(img)
        assertEquals(label, emptyList<String>(), SqliteCheck.check(bytes))
        val f = File(tmp.root, "tree.db").also { it.writeBytes(bytes) }
        assertEquals(label, "ok", sqlite(f, "PRAGMA integrity_check;").trim())
        return bytes
    }

    @Test(timeout = 600_000) fun everySmallRowCountAtSmallPageSizes() {
        assumeTrue(AnkimonFixture.sqliteAvailable())
        for (ps in listOf(512, 1024)) {
            for (n in (0..90) + listOf(119, 120, 121, 127, 128, 129, 255, 256, 257, 300, 511, 512, 513, 700)) {
                verify(treeImage(ps, n, { it + 1L }, { 5 + it % 40 }), "ps $ps n $n short values")
                verify(treeImage(ps, n, { it * 3L - 100 }, { 100 + (it * 37) % 500 }), "ps $ps n $n long values with negative ids")
            }
        }
    }

    @Test fun aSchemaRowTooBigForPageOneMovesToAChildPage() {
        assumeTrue(AnkimonFixture.sqliteAvailable())
        // On 512-byte pages a CREATE TABLE of 440-480 characters doesn't fit next to the 100-byte header: SQLite makes page 1
        // an interior page. One table: page 1 has no cell; with more objects it has one.
        for (n in 20..45) {
            val cols = (0 until n).joinToString(", ") { "cn$it INTEGER" }
            roundTrip(db("PRAGMA page_size=512;\nCREATE TABLE tt ($cols);\nINSERT INTO tt (cn0) VALUES (1);", "wide$n.db"), "$n columns")
            roundTrip(db("PRAGMA page_size=512;\nCREATE TABLE tt ($cols);\nCREATE TABLE t2 ($cols);\nCREATE TABLE t3 (a);", "wide2-$n.db"), "$n columns, three tables")
        }
    }

    @Test(timeout = 600_000) fun hugeAndNegativeRowidsInBigTrees() {
        assumeTrue(AnkimonFixture.sqliteAvailable())
        val ids = (0 until 1500).map { i -> if (i % 3 == 0) -(i * 1_000_003L) - Long.MAX_VALUE / 3 else i * 9_000_000_017L }
        val unique = ids.distinct()
        val img = treeImage(512, unique.size, { unique[it] }, { 20 + it % 90 })
        val bytes = verify(img, "huge rowids")
        assertEquals(unique.sorted(), SqliteReader(bytes).image().table("t")!!.rows.map { it.rowid })
        val extremes = treeImage(512, 4, { listOf(Long.MIN_VALUE, -1L, 0L, Long.MAX_VALUE)[it] }, { 10 })
        verify(extremes, "extreme rowids")
    }

    @Test(timeout = 600_000) fun valuesAroundTheOverflowThresholdOnEveryPageSize() {
        assumeTrue(AnkimonFixture.sqliteAvailable())
        for (ps in listOf(512, 1024, 4096, 65536)) {
            val x = ps - 35                      // table leaf: the largest payload that stays on the page
            val xi = ((ps - 12) * 64 / 255) - 23 // index: the same
            val lens = ((x - 40)..(x + 40)).toList() + ((xi - 40)..(xi + 40)).toList() + listOf(ps - 4 + xi, 2 * (ps - 4) + xi, 3 * ps)
            verify(treeImage(ps, lens.size, { it + 1L }, { lens[it] }), "ps $ps overflow thresholds")
        }
    }

    // ---- other kinds of source files ------------------------------------------------------------------------------

    @Test fun analyzeTablesAndOtherInternalTablesAreCopied() {
        val bytes = roundTrip(db(AnkimonFixture.smallSave() + "\n" + extraSchema + "\nANALYZE;", "analyzed.db"))
        assertTrue(SqliteReader(bytes).image().table("sqlite_stat1") != null)
    }

    @Test fun walAutoVacuumAndUtf16SourcesAreWrittenAsPlainUtf8Files() {
        val flavours = mapOf(
            "wal" to "PRAGMA journal_mode=WAL;\n",
            "autovacuum" to "PRAGMA page_size=1024;\nPRAGMA auto_vacuum=FULL;\n",
            "utf16le" to "PRAGMA encoding='UTF-16le';\n",
            "utf16be" to "PRAGMA encoding='UTF-16be';\n",
        )
        for ((label, pragma) in flavours) {
            val src = db(pragma + AnkimonFixture.smallSave() + "\nDELETE FROM items WHERE id = 17;\n" + monsSql(80) + "\nDELETE FROM captured_pokemon WHERE rowid % 3 = 0;", "$label.db")
            val bytes = SqliteWriter.write(image(src))
            val out = File(tmp.root, "$label-out.db").also { it.writeBytes(bytes) }
            assertEquals(label, "ok", sqlite(out, "PRAGMA integrity_check;").trim())
            assertEquals(label, emptyList<String>(), SqliteCheck.check(bytes))
            assertEquals(label, sqlite(src, ".dump"), sqlite(out, ".dump"))
            assertEquals(label, "1\n0\n0\ndelete\n", sqlite(out, "SELECT 1; PRAGMA auto_vacuum; PRAGMA freelist_count; PRAGMA journal_mode;"))
            assertEquals(label, "UTF-8\n", sqlite(out, "PRAGMA encoding;"))
            assertIndexOrders(out)
        }
    }

    @Test fun textWithNulBytesIsOrderedByAllItsBytes() {
        roundTrip(
            db(
                """
                CREATE TABLE n (id INTEGER PRIMARY KEY, v TEXT UNIQUE, b BLOB);
                CREATE INDEX n_b ON n(b, v);
                INSERT INTO n (v, b) VALUES ('a' || char(0) || 'b', x'0061'), ('a', x'00'), ('a' || char(0), x'0061'), ('a' || char(0) || 'a', zeroblob(3)), ('', x''), ('b', x'61');
                """.trimIndent(),
            ),
        )
    }

    // ---- hostile files ------------------------------------------------------------------------------------------

    @Test fun imageRefusesTooManyRowsAndTablesThatShareTheirPages() {
        val f = db(AnkimonFixture.SCHEMA + "\n" + monsSql(1000), "rows.db")
        assertEquals(1000, SqliteReader.open(f).image(maxRows = 1000).table("captured_pokemon")!!.rows.size)
        assertThrows(SqliteFormatException::class.java) { SqliteReader.open(f).image(maxRows = 999) }
        val sb = StringBuilder(".dbconfig defensive off\nCREATE TABLE a (x TEXT);\nCREATE TABLE b (x TEXT);\n")
        for (i in 1..300) sb.append("INSERT INTO a VALUES (${q("y".repeat(900) + i)});\n")
        sb.append("PRAGMA writable_schema = ON;\nUPDATE sqlite_master SET rootpage = (SELECT rootpage FROM sqlite_master WHERE name = 'a') WHERE name = 'b';\nPRAGMA writable_schema = OFF;\n")
        val e = assertThrows(SqliteFormatException::class.java) { SqliteReader.open(db(sb.toString(), "shared.db")).image() }
        assertTrue(e.message!!, e.message!!.contains("overlap"))
    }

    @Test(timeout = 300_000) fun damagedFilesGiveCleanErrorsFromImageAndWriter() {
        val good = db("PRAGMA page_size=1024;\n" + AnkimonFixture.smallSave() + "\n" + extraSchema + "\n" + monsSql(150), "dmg.db").readBytes()
        var imaged = 0
        var written = 0
        for (seed in 1..400) {
            val rnd = Random(seed)
            val bytes = good.copyOf()
            repeat(1 + rnd.nextInt(20)) {
                val at = if (rnd.nextBoolean()) rnd.nextInt(minOf(bytes.size, 3000)) else rnd.nextInt(bytes.size)
                bytes[at] = rnd.nextInt(256).toByte()
            }
            try {
                val img = SqliteReader(bytes).image()
                imaged++
                val out = SqliteWriter.write(img)
                written++
                assertEquals("seed $seed", emptyList<String>(), SqliteCheck.check(out)) // whatever was written is sound
            } catch (_: SqliteFormatException) {
            } catch (_: IllegalArgumentException) {
            }
        }
        assertTrue("images=$imaged written=$written", imaged > 50 && written > 20)
    }

    // ---- refusals -----------------------------------------------------------------------------------------------

    @Test fun uniqueViolationsNotNullAndDuplicateRowidsAreRefused() {
        val f = db(AnkimonFixture.smallSave())
        val e1 = assertThrows(IllegalArgumentException::class.java) {
            val img = image(f)
            val t = img.table("items")!!
            t[t.rows[1], "item_name"] = t[t.rows[0], "item_name"]
            SqliteWriter.write(img)
        }
        assertTrue(e1.message!!, e1.message!!.contains("UNIQUE") && e1.message!!.contains("item_name"))
        val e2 = assertThrows(IllegalArgumentException::class.java) {
            val img = image(f)
            val t = img.table("captured_pokemon")!!
            t[t.rows[1], "individual_id"] = t[t.rows[0], "individual_id"]
            SqliteWriter.write(img)
        }
        assertTrue(e2.message!!, e2.message!!.contains("UNIQUE"))
        val e3 = assertThrows(IllegalArgumentException::class.java) {
            val img = image(f)
            setData(img, 0, null)
            SqliteWriter.write(img)
        }
        assertTrue(e3.message!!, e3.message!!.contains("NOT NULL"))
        assertThrows(IllegalArgumentException::class.java) { // team.individual_id NOT NULL
            val img = image(f)
            val t = img.table("team")!!
            t[t.rows[0], "individual_id"] = null
            SqliteWriter.write(img)
        }
        assertThrows(IllegalArgumentException::class.java) { // two rows with the same rowid
            val img = image(f)
            val t = img.table("items")!!
            t.rows += SqlRow(t.rows[0].rowid, t.rows[0].values.toMutableList())
            SqliteWriter.write(img)
        }
        assertThrows(IllegalArgumentException::class.java) { // too few values
            val img = image(f)
            img.table("items")!!.rows[0].values.removeAt(3)
            SqliteWriter.write(img)
        }
        assertThrows(IllegalArgumentException::class.java) { // alias value differs from the rowid
            val img = image(f)
            img.table("items")!!.rows[0].values[0] = 12345L
            SqliteWriter.write(img)
        }
        assertThrows(IllegalArgumentException::class.java) { // columns changed
            val img = image(f)
            val t = img.table("items")!!
            img.tables["items"] = SqlTable("items", t.columns.drop(1), -1, t.rows)
            SqliteWriter.write(img)
        }
        assertThrows(IllegalArgumentException::class.java) { // a table that sqlite_master doesn't list
            val img = image(f)
            img.tables["ghost"] = SqlTable("ghost", listOf("a"), -1, ArrayList())
            SqliteWriter.write(img)
        }
        assertThrows(IllegalArgumentException::class.java) { // an object without content
            val img = image(f)
            img.tables.remove("badges")
            SqliteWriter.write(img)
        }
        assertThrows(IllegalArgumentException::class.java) {
            val img = image(f)
            img.objects += img.objects.first { it.name == "badges" }
            SqliteWriter.write(img)
        }
        assertThrows(IllegalArgumentException::class.java) { SqliteWriter.write(SqliteImage(mutableListOf(), mutableMapOf(), pageSize = 1000)) }
        assertThrows(IllegalArgumentException::class.java) { SqliteWriter.write(SqliteImage(mutableListOf(), mutableMapOf(), pageSize = 256)) }
    }

    @Test fun unsupportedSchemaFormsAreRefused() {
        val cases = mapOf(
            "check" to "CREATE TABLE t (a INTEGER CHECK (a > 0));\nINSERT INTO t VALUES (1);",
            "table check" to "CREATE TABLE t (a, b, CHECK (a < b));",
            "strict" to "CREATE TABLE t (a INTEGER) STRICT;",
            "stored generated" to "CREATE TABLE t (a INTEGER, b INTEGER AS (a * 2) STORED);\nINSERT INTO t (a) VALUES (1);",
            "partial index" to "CREATE TABLE t (a, b);\nCREATE INDEX i ON t(a) WHERE b > 1;",
            "expression index" to "CREATE TABLE t (a, b);\nCREATE INDEX i ON t(a + b);",
            "function index" to "CREATE TABLE t (a, b);\nCREATE INDEX i ON t(lower(a));",
            "desc index" to "CREATE TABLE t (a, b);\nCREATE INDEX i ON t(a DESC);",
            "nocase index" to "CREATE TABLE t (a, b);\nCREATE INDEX i ON t(a COLLATE NOCASE);",
            "nocase column index" to "CREATE TABLE t (a TEXT COLLATE NOCASE, b);\nCREATE INDEX i ON t(a);",
            "nocase unique" to "CREATE TABLE t (a TEXT UNIQUE COLLATE NOCASE, b);\nINSERT INTO t VALUES ('a', 1);",
            "on conflict" to "CREATE TABLE t (a UNIQUE ON CONFLICT REPLACE, b);",
            "not null on conflict" to "CREATE TABLE t (a NOT NULL ON CONFLICT IGNORE, b);",
            "pk desc" to "CREATE TABLE t (a INTEGER PRIMARY KEY DESC, b);",
            "unique desc" to "CREATE TABLE t (a, b, UNIQUE (a DESC));",
            "index on other generated" to "CREATE TABLE t (a INTEGER, b INTEGER AS (a + 1));\nCREATE INDEX i ON t(b);\nINSERT INTO t (a) VALUES (1);",
            "json_extract with a path that is not a plain key" to "CREATE TABLE t (d TEXT, b TEXT AS (json_extract(d, '\$.a.b')));\nCREATE INDEX i ON t(b);\nINSERT INTO t (d) VALUES ('{\"a\":{\"b\":1}}');",
            "json_extract on a generated column" to "CREATE TABLE t (d TEXT, g TEXT AS (json_extract(d, '\$.a')), b TEXT AS (json_extract(g, '\$.b')));\nCREATE INDEX i ON t(b);",
            "json_extract with other function" to "CREATE TABLE t (d TEXT, b TEXT AS (lower(json_extract(d, '\$.a'))));\nCREATE INDEX i ON t(b);",
        )
        for ((label, sql) in cases) {
            val img = image(db(sql, "refuse.db"))
            assertThrows(label, SqliteFormatException::class.java) { SqliteWriter.write(img) }
        }
        // a few of those are still readable as images, but these are not:
        assertThrows(SqliteFormatException::class.java) { image(db("CREATE TABLE t (k TEXT PRIMARY KEY, v) WITHOUT ROWID;\nINSERT INTO t VALUES ('a', 1);", "wr.db")) }
        assertThrows(SqliteFormatException::class.java) { image(db("CREATE VIRTUAL TABLE v USING fts5(a);", "fts.db")) }
        assertThrows(SqliteFormatException::class.java) { image(db("CREATE VIRTUAL TABLE r USING rtree(id, a, b);", "rt.db")) }
    }

    @Test fun anAutomaticIndexThatDoesNotMatchTheSchemaIsRefused() {
        val f = db("CREATE TABLE t (a UNIQUE, b UNIQUE);\nINSERT INTO t VALUES (1, 2);")
        val img = image(f)
        img.objects.removeAll { it.name == "sqlite_autoindex_t_2" }
        assertThrows(SqliteFormatException::class.java) { SqliteWriter.write(img) } // missing
        val img2 = image(f)
        img2.objects += SqlObject("index", "sqlite_autoindex_t_3", "t", null)
        assertThrows(SqliteFormatException::class.java) { SqliteWriter.write(img2) } // extra
        val img3 = image(f)
        img3.objects[img3.objects.indexOfFirst { it.name == "sqlite_autoindex_t_2" }] = SqlObject("index", "sqlite_autoindex_t_2", "t2", null)
        assertThrows(Exception::class.java) { SqliteWriter.write(img3) } // wrong table
        val img4 = image(f)
        img4.objects[img4.objects.indexOfFirst { it.name == "sqlite_autoindex_t_1" }] =
            SqlObject("index", "sqlite_autoindex_t_1", "t", "CREATE UNIQUE INDEX sqlite_autoindex_t_1 ON t(zzz)")
        assertThrows(SqliteFormatException::class.java) { SqliteWriter.write(img4) } // unknown column
    }

    // ---- SqliteCheck --------------------------------------------------------------------------------------------

    private val ps1k = 1024

    private fun checkedFile(): ByteArray = SqliteWriter.write(image(db("PRAGMA page_size=$ps1k;\n" + AnkimonFixture.smallSave() + "\n" + monsSql(300), "chk.db")))

    private fun monsSql(n: Int): String = buildString {
        for (i in 1..n) append(AnkimonFixture.insertMon("id-%04d".format((i * 7919) % 100000), false, AnkimonFixture.monJson("id" to i, "name" to "Mon ${i % 40}", "level" to i % 100 + 1, "pad" to "p".repeat(i % 300))))
    }

    private fun pagesOf(b: ByteArray, flag: Int): List<Int> = (2..b.size / ps1k).filter { b[(it - 1) * ps1k].toInt() == flag }

    @Test fun checkAcceptsFilesOfTheWriterAndOfSqlite() {
        val b = checkedFile()
        assertEquals(emptyList<String>(), SqliteCheck.check(b))
        // SQLite's own files (inserts only, so no free space inside pages) pass too: the checker agrees with real SQLite
        for (name in listOf("a.db", "b.db")) {
            val own = db((if (name == "a.db") fullAnkimon(4096) else manyMons(400, 1024)) + "\nVACUUM;", name).readBytes()
            assertEquals(name, emptyList<String>(), SqliteCheck.check(own, strictPages = false)) // SQLite leaves free space in pages
        }
    }

    @Test fun checkFlagsEveryKindOfDamage() {
        val good = checkedFile()
        assertTrue(pagesOf(good, 0x0A).isNotEmpty() && pagesOf(good, 0x02).isNotEmpty() && pagesOf(good, 0x05).isNotEmpty() && pagesOf(good, 0x0D).isNotEmpty())
        fun flagged(label: String, damage: (ByteArray) -> ByteArray) {
            val bytes = damage(good.copyOf())
            assertFalse("$label must be flagged", SqliteCheck.check(bytes).isEmpty())
        }
        val indexLeaf = pagesOf(good, 0x0A)[2]
        val indexInterior = pagesOf(good, 0x02).first()
        val tableLeaf = pagesOf(good, 0x0D)[1]
        val tableInterior = pagesOf(good, 0x05).first()
        fun cellPtr(b: ByteArray, page: Int, i: Int, interior: Boolean): Int = (page - 1) * ps1k + (if (interior) 12 else 8) + 2 * i
        flagged("a flipped byte in an index key") { b ->
            val p = (indexLeaf - 1) * ps1k
            val cell = ((b[p + 8].toInt() and 0xFF) shl 8) or (b[p + 9].toInt() and 0xFF)
            b[p + cell + 6] = (b[p + cell + 6] + 1).toByte(); b
        }
        flagged("truncated by a page") { b -> b.copyOf(b.size - ps1k) }
        flagged("truncated by a byte") { b -> b.copyOf(b.size - 1) }
        flagged("two index cells swapped") { b ->
            val p = cellPtr(b, indexLeaf, 0, false)
            val x0 = b[p]; val x1 = b[p + 1]
            b[p] = b[p + 2]; b[p + 1] = b[p + 3]; b[p + 2] = x0; b[p + 3] = x1; b
        }
        flagged("two table cells swapped") { b ->
            val p = cellPtr(b, tableLeaf, 0, false)
            val x0 = b[p]; val x1 = b[p + 1]
            b[p] = b[p + 2]; b[p + 1] = b[p + 3]; b[p + 2] = x0; b[p + 3] = x1; b
        }
        flagged("an interior index divider changed") { b ->
            val p = (indexInterior - 1) * ps1k
            val cell = ((b[p + 12].toInt() and 0xFF) shl 8) or (b[p + 13].toInt() and 0xFF)
            b[p + cell + 8] = (b[p + cell + 8] + 3).toByte(); b
        }
        flagged("a table divider changed") { b ->
            val p = (tableInterior - 1) * ps1k
            val cell = ((b[p + 12].toInt() and 0xFF) shl 8) or (b[p + 13].toInt() and 0xFF)
            b[p + cell + 4] = (b[p + cell + 4] + 1).toByte(); b
        }
        flagged("a child pointer changed") { b ->
            val p = (tableInterior - 1) * ps1k + 12
            val cell = ((b[p].toInt() and 0xFF) shl 8) or (b[p + 1].toInt() and 0xFF)
            val at = (tableInterior - 1) * ps1k + cell
            b[at + 3] = (b[at + 3] + 1).toByte(); b
        }
        flagged("a zeroed page") { b -> java.util.Arrays.fill(b, (tableLeaf - 1) * ps1k, tableLeaf * ps1k, 0); b }
        flagged("a page added at the end") { b -> b + ByteArray(ps1k) }
        flagged("a wrong magic") { b -> b[3] = 0; b }
        flagged("a wrong page count in the header") { b -> b[31] = (b[31] + 1).toByte(); b }
        flagged("an index page that is a table page") { b -> b[(indexLeaf - 1) * ps1k] = 0x0D; b }
        flagged("a row's JSON changed (generated keys no longer match the indexes)") { b ->
            val s = String(b, Charsets.ISO_8859_1)
            val at = s.indexOf("\"name\":\"Mon 17\"")
            assertTrue(at > 0)
            b[at + 13] = '9'.code.toByte(); b
        }
        flagged("a cell content start moved") { b -> val p = (tableLeaf - 1) * ps1k + 5; b[p + 1] = (b[p + 1] - 2).toByte(); b }
        flagged("a free block announced") { b -> b[(tableLeaf - 1) * ps1k + 2] = 5; b }
        flagged("the cell count raised") { b -> val p = (indexLeaf - 1) * ps1k + 4; b[p] = (b[p] + 1).toByte(); b }
        assertEquals(listOf("The file is too short for a SQLite header"), SqliteCheck.check(ByteArray(10)))
        assertFalse(SqliteCheck.check(ByteArray(0)).isEmpty())
    }

    @Test(timeout = 300_000) fun checkNeverCrashesOnRandomDamage() {
        val good = checkedFile()
        var flaggedCount = 0
        for (seed in 1..300) {
            val rnd = Random(seed)
            val bytes = good.copyOf()
            repeat(1 + rnd.nextInt(8)) { bytes[rnd.nextInt(bytes.size)] = rnd.nextInt(256).toByte() }
            if (SqliteCheck.check(bytes).isNotEmpty()) flaggedCount++
        }
        assertTrue("flagged $flaggedCount of 300", flaggedCount > 150)
    }

    // ---- size ---------------------------------------------------------------------------------------------------

    /** Bytes this thread has allocated so far (HotSpot only; -1 when unavailable). */
    private fun threadAllocatedBytes(): Long = try {
        val bean = Class.forName("java.lang.management.ManagementFactory").getMethod("getThreadMXBean").invoke(null)
        Class.forName("com.sun.management.ThreadMXBean").getMethod("getThreadAllocatedBytes", Long::class.javaPrimitiveType)
            .invoke(bean, Thread.currentThread().id) as Long
    } catch (e: Exception) { -1L }

    @Test(timeout = 900_000) fun twentyThousandPokemonTakeSecondsNotMinutes() {
        val src = db(manyMons(20_000, 4096, 500, 2000), "big.db")
        val t0 = System.nanoTime()
        val img = SqliteReader.open(src).image()
        val t1 = System.nanoTime()
        assertEquals(20_000, img.table("captured_pokemon")!!.rows.size)
        val allocated0 = threadAllocatedBytes()
        val bytes = SqliteWriter.write(img)
        val allocated = threadAllocatedBytes() - allocated0
        val t2 = System.nanoTime()
        val problems = SqliteCheck.check(bytes)
        val t3 = System.nanoTime()
        println("20000 Pokemon: file ${src.length() / 1_000_000} MB, image() ${(t1 - t0) / 1_000_000} ms, write() ${(t2 - t1) / 1_000_000} ms, check() ${(t3 - t2) / 1_000_000} ms, write() allocated ${allocated / 1_000_000} MB for a ${bytes.size / 1_000_000} MB file")
        if (allocated0 >= 0) assertTrue("write() allocated ${allocated / 1_000_000} MB", allocated < 10L * bytes.size) // total allocation, short-lived records included; what stays alive is the pages plus the final array (about 2x the file)
        assertEquals(emptyList<String>(), problems)
        assertTrue("write took ${(t2 - t1) / 1_000_000} ms", (t2 - t1) < 60_000_000_000L)
        val out = File(tmp.root, "big-out.db").also { it.writeBytes(bytes) }
        assertEquals("ok", sqlite(out, "PRAGMA integrity_check;").trim())
        assertEquals(sqlite(src, ".dump").hashCode(), sqlite(out, ".dump").hashCode())
        assertSameImage(img, SqliteReader(bytes).image())
        assertNotEquals(0, bytes.size)
    }
}
