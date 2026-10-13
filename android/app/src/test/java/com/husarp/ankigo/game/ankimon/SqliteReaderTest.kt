package com.husarp.ankigo.game.ankimon

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.random.Random

/** The pure-Kotlin SQLite reader against real database files built by the sqlite3 tool. */
class SqliteReaderTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun db(sql: String, name: String = "t.db"): File {
        assumeTrue("sqlite3 is not installed", AnkimonFixture.sqliteAvailable())
        return AnkimonFixture.build(tmp.root, name, sql)
    }

    private fun open(sql: String) = SqliteReader(db(sql).readBytes())

    private fun q(s: String?) = AnkimonFixture.q(s)

    /** 1 .. n in a table with an INTEGER PRIMARY KEY, a number and a padded text. */
    private val numbers600 = """
        PRAGMA page_size=1024;
        CREATE TABLE t (id INTEGER PRIMARY KEY, n INTEGER, s TEXT);
        WITH RECURSIVE c(x) AS (SELECT 1 UNION ALL SELECT x+1 FROM c WHERE x<600)
        INSERT INTO t SELECT x, x*3, 'row-'||x||'-'||printf('%050d', x) FROM c;
    """.trimIndent()

    // ---- Ankimon's own schema ----------------------------------------------------------------------------------

    @Test fun ankimonSchemaDefaultPageSize() {
        val r = open(AnkimonFixture.smallSave())
        assertEquals(
            listOf("captured_pokemon", "items", "badges", "metadata", "team", "pokemon_history", "user_data", "config"),
            r.tableNames(),
        )
        // Declared columns include the generated ones...
        assertEquals(
            listOf("individual_id", "is_main", "data", "name", "pokedex_id", "shiny", "level"),
            r.columns("captured_pokemon"),
        )
        // ...but VIRTUAL generated columns are not in the file, so rows leave them out.
        val mons = r.rows("captured_pokemon")
        assertEquals(3, mons.size)
        assertEquals(listOf("individual_id", "is_main", "data"), mons[0].keys.toList())
        assertEquals("aaaa1111-0000-4000-8000-000000000001", mons[0]["individual_id"])
        assertEquals(1L, mons[0]["is_main"])
        assertEquals(0L, mons[1]["is_main"])
        assertTrue((mons[0]["data"] as String).contains("\"Sparkmouse\""))

        val items = r.rows("items")
        assertEquals(4, items.size)
        assertEquals(listOf(4L, 17L, 18L, 900L), items.map { it["id"] }) // INTEGER PRIMARY KEY = rowid, its record slot is NULL
        assertEquals("poke-ball", items[0]["item_name"])
        assertEquals(12L, items[0]["quantity"])
        assertNull(items[0]["category_id"])
        assertEquals(listOf(1L, 2L), r.rows("team").map { it["slot_position"] })
        assertEquals(3, r.rows("user_data").size)
        assertEquals(emptyList<Any>(), r.rows("pokemon_history"))
        assertEquals(emptyList<Any>(), r.rows("metadata"))
    }

    @Test fun ankimonOldSchemaOrderIsMainAddedLast() {
        // Older Ankimon: no is_main column in CREATE TABLE; a migration added it with ALTER TABLE at the very end.
        val r = open(
            """
            CREATE TABLE captured_pokemon (
                individual_id TEXT PRIMARY KEY,
                data TEXT NOT NULL,
                name TEXT GENERATED ALWAYS AS (json_extract(data, '${'$'}.name')) VIRTUAL,
                pokedex_id INTEGER GENERATED ALWAYS AS (json_extract(data, '${'$'}.id')) VIRTUAL
            );
            INSERT INTO captured_pokemon (individual_id, data) VALUES ('old-1', '{"id": 1}'), ('old-2', '{"id": 2}');
            ALTER TABLE captured_pokemon ADD COLUMN is_main INTEGER DEFAULT 0;
            INSERT INTO captured_pokemon (individual_id, data, is_main) VALUES ('new-3', '{"id": 3}', 1);
            """.trimIndent(),
        )
        assertEquals(listOf("individual_id", "data", "name", "pokedex_id", "is_main"), r.columns("captured_pokemon"))
        val rows = r.rows("captured_pokemon")
        assertEquals(listOf("individual_id", "data", "is_main"), rows[0].keys.toList())
        assertEquals(listOf("old-1", "old-2", "new-3"), rows.map { it["individual_id"] })
        assertNull(rows[0]["is_main"]) // records written before the ALTER are shorter: missing trailing values read as null
        assertNull(rows[1]["is_main"])
        assertEquals(1L, rows[2]["is_main"])
        assertEquals("{\"id\": 2}", rows[1]["data"])
    }

    // ---- b-tree shapes ------------------------------------------------------------------------------------------

    @Test fun smallPagesGiveInteriorPagesAndTheRowsComeBackInOrder() {
        val bytes = db(numbers600).readBytes()
        // Prove the test exercises interior pages: with 1 KB pages 600 rows can't fit one leaf.
        val pages = bytes.size / 1024
        assertTrue(pages > 10)
        assertTrue((2..pages).any { bytes[(it - 1) * 1024].toInt() == 0x05 })
        val r = SqliteReader(bytes)
        val rows = r.rows("t")
        assertEquals(600, rows.size)
        for (i in 1..600) {
            val row = rows[i - 1]
            assertEquals(i.toLong(), row["id"])
            assertEquals(i * 3L, row["n"])
            assertEquals("row-$i-" + i.toString().padStart(50, '0'), row["s"])
        }
        assertEquals(listOf(1L, 2L, 3L), r.rows("t", limit = 3).map { it["id"] })
        assertEquals(emptyList<Any>(), r.rows("t", limit = 0))
    }

    @Test fun smallPagesAndABigAnkimonTable() {
        // 350 Pokémon rows in Ankimon's real table on 1 KB pages.
        val sb = StringBuilder("PRAGMA page_size=1024;\n").append(AnkimonFixture.SCHEMA).append('\n')
        for (i in 1..350) {
            sb.append(AnkimonFixture.insertMon("id-%04d".format(i), i == 7, AnkimonFixture.monJson("id" to i, "name" to "Mon $i", "level" to i % 100 + 1)))
        }
        val rows = open(sb.toString()).rows("captured_pokemon")
        assertEquals(350, rows.size)
        // Rows are in rowid order = insertion order, whatever the primary-key index does.
        assertEquals((1..350).map { "id-%04d".format(it) }, rows.map { it["individual_id"] })
        assertEquals(listOf(7), rows.indices.filter { rows[it]["is_main"] == 1L }.map { it + 1 })
    }

    @Test fun overflowChainsAtEverySize() {
        // Payload sizes around the "fits locally" threshold of 1 KB pages, plus long chains.
        val sb = StringBuilder("PRAGMA page_size=1024;\nCREATE TABLE docs (id INTEGER PRIMARY KEY, body TEXT);\n")
        val sizes = (900..1100).toList() + listOf(2000, 3071, 3072, 3073, 20_000, 70_000)
        fun body(n: Int) = buildString { for (i in 0 until n) append('a' + (i * 7 + n) % 26) }
        for ((i, n) in sizes.withIndex()) sb.append("INSERT INTO docs VALUES (${i + 1}, ${q(body(n))});\n")
        val rows = open(sb.toString()).rows("docs")
        assertEquals(sizes.size, rows.size)
        for ((i, n) in sizes.withIndex()) assertEquals("size $n", body(n), rows[i]["body"])
    }

    @Test fun overflowWithDefaultPagesAndUnicode() {
        val text = "ąęłóżź 日本語 🐉 ".repeat(1500) // ~ 30 KB of multi-byte text
        val r = open("CREATE TABLE d (k TEXT, v TEXT);\nINSERT INTO d VALUES ('a', ${q(text)});\nINSERT INTO d VALUES ('b', ${q("short")});")
        val rows = r.rows("d")
        assertEquals(text, rows[0]["v"])
        assertEquals("short", rows[1]["v"])
    }

    @Test fun aBigPokemonJsonOfTwentyKilobytes() {
        val json = AnkimonFixture.monJson("id" to 25, "name" to "Sparkmouse", "padding" to "x".repeat(20_000), "level" to 9)
        val r = open(AnkimonFixture.SCHEMA + "\n" + AnkimonFixture.insertMon("big-1", true, json) + AnkimonFixture.insertMon("big-2", false, "{}"))
        val rows = r.rows("captured_pokemon")
        assertEquals(json, rows[0]["data"])
        assertEquals("{}", rows[1]["data"])
    }

    @Test fun deletedRowsLeaveFreePagesThatAreIgnored() {
        val r = open(numbers600 + "\nDELETE FROM t WHERE id % 2 = 0;\nDELETE FROM t WHERE id > 450;")
        assertEquals((1..450 step 2).map { it.toLong() }, r.rows("t").map { it["id"] })
    }

    @Test fun biggestPageSizeWithOverflow() {
        val bytes = db(
            "PRAGMA page_size=65536;\nCREATE TABLE d (id INTEGER PRIMARY KEY, body TEXT);\n" +
                "INSERT INTO d VALUES (1, ${q("y".repeat(300_000))});\nINSERT INTO d VALUES (2, 'two');",
        ).readBytes()
        assertEquals(1, ((bytes[16].toInt() and 0xFF) shl 8) or (bytes[17].toInt() and 0xFF)) // header value 1 means 65536
        val rows = SqliteReader(bytes).rows("d")
        assertEquals("y".repeat(300_000), rows[0]["body"])
        assertEquals("two", rows[1]["body"])
    }

    @Test fun autoVacuumPointerMapPagesAreNeverVisited() {
        val r = open("PRAGMA page_size=1024;\nPRAGMA auto_vacuum=FULL;\n" + numbers600.removePrefix("PRAGMA page_size=1024;") + "\nDELETE FROM t WHERE id % 3 = 0;")
        assertEquals((1..600).filter { it % 3 != 0 }.map { it.toLong() }, r.rows("t").map { it["id"] })
    }

    @Test fun aWalModeFileOfAFinishedWriterReads() {
        val r = open("PRAGMA journal_mode=WAL;\nCREATE TABLE w (a);\nINSERT INTO w VALUES (42);")
        assertEquals(listOf(42L), r.rows("w").map { it["a"] })
    }

    // ---- values -------------------------------------------------------------------------------------------------

    @Test fun integersOfEverySize() {
        val values = listOf(
            0L, 1L, -1L, 2L, 127L, -128L, 128L, -129L, 32767L, -32768L, 32768L, 40000L, 8388607L, 8388608L, -8388608L, -8388609L,
            2147483647L, 2147483648L, -2147483648L, -2147483649L, 140737488355327L, 140737488355328L, -140737488355328L,
            -140737488355329L, Long.MAX_VALUE, Long.MIN_VALUE,
        )
        val sb = StringBuilder("CREATE TABLE v (id INTEGER PRIMARY KEY, i INTEGER);\n")
        for ((k, n) in values.withIndex()) {
            val lit = if (n == Long.MIN_VALUE) "(-9223372036854775807-1)" else n.toString()
            sb.append("INSERT INTO v VALUES (${k + 1}, $lit);\n")
        }
        val rows = open(sb.toString()).rows("v")
        assertEquals(values, rows.map { it["i"] })
        assertEquals((1L..values.size.toLong()).toList(), rows.map { it["id"] })
    }

    @Test fun floatsTextBlobsNullsAndEmptyValues() {
        val r = open(
            """
            CREATE TABLE v (id INTEGER PRIMARY KEY, r REAL, s TEXT, b BLOB, n);
            INSERT INTO v VALUES (1, 3.25, 'plain', x'00FF10', NULL);
            INSERT INTO v VALUES (2, -0.5, '', x'', 7);
            INSERT INTO v VALUES (3, 3.0, ${q("ąęś 日本語 🐉 \"q\" 'single'")}, zeroblob(5), 'text in an untyped column');
            INSERT INTO v VALUES (4, 1e300, 'x', x'DEADBEEF', 2.5);
            INSERT INTO v VALUES (5, NULL, NULL, NULL, NULL);
            INSERT INTO v VALUES (9000000000, 0, 'big rowid', NULL, 0);
            """.trimIndent(),
        )
        val rows = r.rows("v")
        assertEquals(6, rows.size)
        assertEquals(3.25, rows[0]["r"])
        assertEquals("plain", rows[0]["s"])
        assertArrayEquals(byteArrayOf(0, -1, 0x10), rows[0]["b"] as ByteArray)
        assertNull(rows[0]["n"])
        assertEquals(-0.5, rows[1]["r"])
        assertEquals("", rows[1]["s"])
        assertEquals(0, (rows[1]["b"] as ByteArray).size)
        assertEquals(7L, rows[1]["n"])
        assertEquals(3.0, rows[2]["r"]) // stored as an integer by SQLite; the REAL column type brings it back as a Double
        assertEquals("ąęś 日本語 🐉 \"q\" 'single'", rows[2]["s"])
        assertArrayEquals(ByteArray(5), rows[2]["b"] as ByteArray)
        assertEquals("text in an untyped column", rows[2]["n"])
        assertEquals(1e300, rows[3]["r"])
        assertEquals(2.5, rows[3]["n"])
        assertTrue(listOf("r", "s", "b", "n").all { rows[4][it] == null })
        assertEquals(9_000_000_000L, rows[5]["id"])
        assertEquals("big rowid", rows[5]["s"])
    }

    @Test fun utf16Databases() {
        for (pragma in listOf("UTF-16le", "UTF-16be")) {
            val r = SqliteReader(db("PRAGMA encoding='$pragma';\nCREATE TABLE tëxt (a TEXT, b TEXT);\nINSERT INTO tëxt VALUES ('héllo 日本', 'x');", "u.db").readBytes())
            assertEquals(listOf("tëxt"), r.tableNames())
            assertEquals("héllo 日本", r.rows("tëxt")[0]["a"])
        }
    }

    // ---- CREATE TABLE parsing -----------------------------------------------------------------------------------

    @Test fun storedGeneratedColumnsAreInTheFile() {
        val r = open(
            """
            CREATE TABLE g (
                a INTEGER,
                d INTEGER AS (a * 2) STORED,
                v INTEGER GENERATED ALWAYS AS (a + 100) VIRTUAL,
                w INTEGER AS (a + 1),
                e TEXT
            );
            INSERT INTO g (a, e) VALUES (5, 'five');
            """.trimIndent(),
        )
        assertEquals(listOf("a", "d", "v", "w", "e"), r.columns("g"))
        val row = r.rows("g").single()
        assertEquals(listOf("a", "d", "e"), row.keys.toList()) // v and w are virtual (no GENERATED ALWAYS is fine, default VIRTUAL)
        assertEquals(5L, row["a"])
        assertEquals(10L, row["d"])
        assertEquals("five", row["e"])
    }

    @Test fun quotedNamesConstraintsCommentsAndCommasInDefaults() {
        val r = open(
            """
            CREATE TABLE "we ird(name" (
                "col one" TEXT DEFAULT 'a,b(c)' NOT NULL, -- a comment, with a comma ) and a paren (
                [x y] INTEGER CHECK ("x y" > 0 AND [x y] < (50)),
                `back``tick` TEXT COLLATE NOCASE,
                /* block, comment ) */ amount NUMERIC(10, 2) DEFAULT (cast(1 as int)),
                PRIMARY KEY ("col one", amount),
                UNIQUE (`back``tick`),
                CONSTRAINT fk CHECK (amount > -1)
            );
            INSERT INTO "we ird(name" VALUES ('k', 7, 'bt', 1.5);
            """.trimIndent(),
        )
        assertEquals(listOf("we ird(name"), r.tableNames())
        assertEquals(listOf("col one", "x y", "back`tick", "amount"), r.columns("we ird(name"))
        val row = r.rows("WE IRD(NAME").single() // table names are case-insensitive
        assertEquals(listOf("col one", "x y", "back`tick", "amount"), row.keys.toList())
        assertEquals("k", row["col one"])
        assertEquals(7L, row["x y"])
        assertEquals("bt", row["back`tick"])
        assertEquals(1.5, row["amount"])
    }

    @Test fun aSingleColumnPrimaryKeyConstraintOnAnIntegerIsTheRowid() {
        val r = open(
            """
            CREATE TABLE a (id INTEGER, v TEXT, PRIMARY KEY (id));
            INSERT INTO a VALUES (7, 'seven');
            INSERT INTO a (v) VALUES ('auto');
            CREATE TABLE b (id INT PRIMARY KEY, v TEXT);
            INSERT INTO b VALUES (5, 'five');
            """.trimIndent(),
        )
        assertEquals(listOf(7L, 8L), r.rows("a").map { it["id"] })
        // "INT PRIMARY KEY" is NOT a rowid alias: the value is stored in the record.
        assertEquals(5L, r.rows("b").single()["id"])
    }

    @Test fun withoutRowidTablesAreRefusedButTheRestStillReads() {
        val r = open(
            """
            CREATE TABLE ok (a INTEGER PRIMARY KEY, b TEXT);
            INSERT INTO ok VALUES (1, 'fine');
            CREATE TABLE wr (k TEXT PRIMARY KEY, v TEXT) WITHOUT ROWID;
            INSERT INTO wr VALUES ('a', 'b');
            """.trimIndent(),
        )
        assertEquals(listOf("ok", "wr"), r.tableNames())
        assertEquals("fine", r.rows("ok").single()["b"])
        assertThrows(SqliteFormatException::class.java) { r.rows("wr") }
    }

    @Test fun unknownTablesReadAsEmpty() {
        val r = open("CREATE TABLE a (x);\nINSERT INTO a VALUES (1);")
        assertEquals(emptyList<Any>(), r.rows("nope"))
        assertEquals(emptyList<String>(), r.columns("nope"))
        assertEquals(listOf(1L), r.rows("a").map { it["x"] })
    }

    @Test fun internalTablesAreNotListed() {
        val r = open("CREATE TABLE s (id INTEGER PRIMARY KEY AUTOINCREMENT, v TEXT);\nINSERT INTO s (v) VALUES ('a');\nCREATE INDEX i ON s(v);")
        assertEquals(listOf("s"), r.tableNames()) // no sqlite_sequence, no index
        assertEquals(listOf(1L), r.rows("s").map { it["id"] })
    }

    @Test fun anEmptyDatabaseHasNoTables() {
        val r = open("PRAGMA user_version = 1;")
        assertEquals(emptyList<String>(), r.tableNames())
    }

    // ---- open() -------------------------------------------------------------------------------------------------

    @Test fun openReadsAFileAndRefusesOversizeOnes() {
        val f = db(numbers600)
        assertEquals(600, SqliteReader.open(f).rows("t").size)
        assertThrows(SqliteFormatException::class.java) { SqliteReader.open(f, maxBytes = 1000) }
    }

    // ---- damaged and hostile files ------------------------------------------------------------------------------

    @Test(timeout = 60_000) fun truncatedFilesAreRefused() {
        val bytes = db(numbers600).readBytes()
        for (cut in listOf(0, 50, 99, 100, 512, 1024, 1500, 2048, bytes.size / 2, bytes.size - 1024, bytes.size - 1)) {
            assertThrows("cut at $cut", SqliteFormatException::class.java) {
                val r = SqliteReader(bytes.copyOf(cut))
                r.rows("t")
            }
        }
    }

    @Test fun notADatabase() {
        val random = Random(7).nextBytes(200_000)
        val text = "Hello, this is just a text file.\n".repeat(100).toByteArray()
        for ((label, data) in listOf("random" to random, "text" to text, "empty" to ByteArray(0), "magic only" to "SQLite format 3\u0000".toByteArray())) {
            assertThrows(label, SqliteFormatException::class.java) { SqliteReader(data).tableNames() }
        }
    }

    @Test fun validHeaderThenGarbage() {
        val header = db(numbers600).readBytes().copyOf(100)
        val rnd = Random(11)
        repeat(20) {
            val bytes = header + rnd.nextBytes(1024 * 40 - 100) // 40 pages of 1 KB of noise behind a real header
            try {
                SqliteReader(bytes).let { r -> r.tableNames().forEach { r.rows(it) } }
            } catch (_: SqliteFormatException) {
                // fine: refused cleanly
            }
        }
    }

    @Test(timeout = 120_000) fun randomDamageNeverCrashesOrHangs() {
        val sb = StringBuilder("PRAGMA page_size=1024;\n").append(AnkimonFixture.SCHEMA).append('\n')
        for (i in 1..120) sb.append(AnkimonFixture.insertMon("id-$i", false, AnkimonFixture.monJson("id" to i, "padding" to "p".repeat(i * 13))))
        sb.append("INSERT INTO items (id, item_name, quantity) VALUES (1, 'a', 1);\n")
        val good = db(sb.toString()).readBytes()
        var refused = 0
        var read = 0
        for (seed in 1..400) {
            val rnd = Random(seed)
            val bytes = good.copyOf()
            repeat(1 + rnd.nextInt(30)) {
                // half of the hits go to the first pages (header, sqlite_master, b-tree headers), the rest anywhere
                val at = if (rnd.nextBoolean()) rnd.nextInt(minOf(bytes.size, 3000)) else rnd.nextInt(bytes.size)
                bytes[at] = rnd.nextInt(256).toByte()
            }
            try {
                val r = SqliteReader(bytes)
                for (t in r.tableNames()) { r.columns(t); r.rows(t) }
                read++
            } catch (_: SqliteFormatException) {
                refused++
            }
        }
        assertEquals(400, refused + read)
        assertTrue("some damaged files must be refused (refused=$refused)", refused > 20)
    }

    @Test fun anInteriorPageThatPointsAtItselfIsALoopNotAHang() {
        val bytes = db(numbers600).readBytes()
        val page = (2..bytes.size / 1024).first { bytes[(it - 1) * 1024].toInt() == 0x05 }
        val at = (page - 1) * 1024 + 8 // right-most child pointer
        for (i in 0..3) bytes[at + i] = (page ushr (24 - 8 * i)).toByte()
        val e = assertThrows(SqliteFormatException::class.java) { SqliteReader(bytes).rows("t") }
        assertTrue(e.message!!, e.message!!.contains("loop"))
    }

    @Test fun anOverflowPointerOutsideTheFileIsRefused() {
        val sb = StringBuilder("PRAGMA page_size=1024;\nCREATE TABLE d (id INTEGER PRIMARY KEY, body TEXT);\n")
        sb.append("INSERT INTO d VALUES (1, ${q("z".repeat(5000))});\n")
        val bytes = db(sb.toString()).readBytes()
        // The cell's first-overflow page number sits right after its local payload; find the leaf and break it.
        val pageCount = bytes.size / 1024
        var broken = false
        for (page in 2..pageCount) {
            val base = (page - 1) * 1024
            if (bytes[base].toInt() != 0x0D) continue
            val cell = base + (((bytes[base + 8].toInt() and 0xFF) shl 8) or (bytes[base + 9].toInt() and 0xFF))
            // payload size (2 byte varint) + rowid (1 byte) then local bytes; local = K <= 989
            val payloadSize = ((bytes[cell].toInt() and 0x7F) shl 7) or (bytes[cell + 1].toInt() and 0x7F)
            if (payloadSize < 4000) continue
            val local = localSize(payloadSize, 1024)
            val ptr = cell + 3 + local
            bytes[ptr] = 0x7F; bytes[ptr + 1] = 0; bytes[ptr + 2] = 0; bytes[ptr + 3] = 1 // page 2130706433
            broken = true
        }
        assertTrue(broken)
        assertThrows(SqliteFormatException::class.java) { SqliteReader(bytes).rows("d") }
    }

    private fun localSize(p: Int, u: Int): Int {
        val x = u - 35
        if (p <= x) return p
        val m = ((u - 12) * 32 / 255) - 23
        val k = m + ((p - m) % (u - 4))
        return if (k <= x) k else m
    }

    @Test fun cellsThatShareOverflowPagesAreCutByThePayloadBudget() {
        // A leaf that lists the same huge cell over and over must not cost (cells x size) time and memory.
        val sb = StringBuilder("PRAGMA page_size=1024;\nCREATE TABLE d (id INTEGER PRIMARY KEY, body TEXT);\n")
        sb.append("INSERT INTO d VALUES (1, ${q("z".repeat(60_000))});\n")
        for (i in 2..40) sb.append("INSERT INTO d VALUES ($i, 'small $i');\n")
        val bytes = db(sb.toString()).readBytes()
        val pageCount = bytes.size / 1024
        val leaf = (2..pageCount).first { bytes[(it - 1) * 1024].toInt() == 0x0D }
        val base = (leaf - 1) * 1024
        val cells = ((bytes[base + 3].toInt() and 0xFF) shl 8) or (bytes[base + 4].toInt() and 0xFF)
        assertTrue(cells >= 2)
        // point every cell pointer at the first (huge) cell
        val first = ((bytes[base + 8].toInt() and 0xFF) shl 8) or (bytes[base + 9].toInt() and 0xFF)
        for (i in 1 until cells) {
            bytes[base + 8 + 2 * i] = (first ushr 8).toByte()
            bytes[base + 9 + 2 * i] = first.toByte()
        }
        val e = assertThrows(SqliteFormatException::class.java) { SqliteReader(bytes).rows("d") }
        assertTrue(e.message!!, e.message!!.contains("overlap"))
    }
}
