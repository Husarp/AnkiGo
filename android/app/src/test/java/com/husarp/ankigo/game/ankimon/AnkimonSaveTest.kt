package com.husarp.ankigo.game.ankimon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AnkimonSaveTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun json(vararg f: Pair<String, Any?>) = AnkimonFixture.monJson(*f)

    private fun monRow(id: String?, main: Any? = 0L, data: String?) =
        mapOf("individual_id" to id, "is_main" to main, "data" to data)

    private fun parse(
        mons: List<Map<String, Any?>>,
        team: List<Map<String, Any?>>? = null,
        items: List<Map<String, Any?>>? = null,
        userData: List<Map<String, Any?>>? = null,
        config: List<Map<String, Any?>>? = null,
    ): AnkimonSave {
        val t = mutableMapOf<String, List<Map<String, Any?>>>("captured_pokemon" to mons)
        team?.let { t["team"] = it }; items?.let { t["items"] = it }; userData?.let { t["user_data"] = it }; config?.let { t["config"] = it }
        return AnkimonSaveReader.parse(t)
    }

    // ---- one Pokémon ---------------------------------------------------------------------------------------------

    @Test fun aFullPokemonIsReadFieldByField() {
        val save = parse(listOf(monRow("p-1", 1L, json(
            "id" to 25, "name" to "Sparkmouse", "nickname" to "Zappy", "level" to 30, "xp" to 123,
            "iv" to mapOf("hp" to 31, "atk" to 20, "def" to 10, "spa" to 0, "spd" to 5, "spe" to 17),
            "ev" to mapOf("hp" to 4, "atk" to 252),
            "nature" to "timid", "gender" to "F", "ability" to "Static", "attacks" to listOf("Thunderbolt", "Quick Attack"),
            "shiny" to true, "held_item" to "Lucky Egg", "everstone" to false, "friendship" to 200,
            "captured_date" to "2026-01-02 03:04:05", "pokemon_defeated" to 12, "tier" to "Legendary",
            "stats" to mapOf("hp" to 999), "cp" to 5000, // ignored fields
        ))))
        val m = save.mons.single()
        assertEquals("p-1", m.individualId)
        assertTrue(m.isMain)
        assertEquals(25, m.id)
        assertEquals("Sparkmouse", m.name)
        assertEquals("Zappy", m.nickname)
        assertEquals(30, m.level)
        assertEquals(123, m.xp)
        assertEquals(mapOf("hp" to 31, "atk" to 20, "def" to 10, "spa" to 0, "spd" to 5, "spe" to 17), m.iv)
        assertEquals(mapOf("hp" to 4, "atk" to 252), m.ev) // only the keys that are there
        assertEquals("timid", m.nature)
        assertEquals("F", m.gender)
        assertEquals("Static", m.ability)
        assertEquals(listOf("Thunderbolt", "Quick Attack"), m.attacks)
        assertTrue(m.shiny)
        assertEquals("Lucky Egg", m.heldItem)
        assertFalse(m.everstone)
        assertEquals(200, m.friendship)
        assertEquals("2026-01-02 03:04:05", m.capturedDate)
        assertEquals(12, m.defeated)
        assertEquals("Legendary", m.tier)
        assertEquals(emptyList<String>(), save.problems)
    }

    @Test fun everyFieldIsOptional() {
        val m = parse(listOf(monRow("p-1", null, "{}"))).mons.single()
        assertEquals("p-1", m.individualId)
        assertFalse(m.isMain)
        assertNull(m.id); assertNull(m.name); assertNull(m.nickname); assertNull(m.level); assertNull(m.xp)
        assertEquals(emptyMap<String, Int>(), m.iv)
        assertEquals(emptyMap<String, Int>(), m.ev)
        assertNull(m.nature); assertNull(m.gender); assertNull(m.ability)
        assertEquals(emptyList<String>(), m.attacks)
        assertFalse(m.shiny); assertNull(m.heldItem); assertFalse(m.everstone)
        assertNull(m.friendship); assertNull(m.capturedDate); assertNull(m.defeated); assertNull(m.tier)
    }

    @Test fun looselyTypedValuesAreCoerced() {
        val m = parse(listOf(monRow("p-1", "true", json(
            "id" to "25", "level" to 30.0, "xp" to "12.6", "friendship" to " 70 ", "pokemon_defeated" to 4.2,
            "shiny" to 1, "everstone" to "true", "nickname" to "   ", "held_item" to "", "name" to 77,
            "iv" to mapOf("hp" to "31", "atk" to 15.9, "def" to "bad", "spa" to null, "spd" to true),
            "ev" to "not an object", "attacks" to listOf("Tackle", 5, null, "  ", "Growl"),
            "nature" to listOf("x"), "gender" to null,
        )))).mons.single()
        assertTrue(m.isMain)
        assertEquals(25, m.id)
        assertEquals(30, m.level)
        assertEquals(13, m.xp)
        assertEquals(70, m.friendship)
        assertEquals(4, m.defeated)
        assertTrue(m.shiny)
        assertTrue(m.everstone)
        assertNull(m.nickname) // blank
        assertNull(m.heldItem) // empty
        assertNull(m.name) // a number is not a name
        assertEquals(mapOf("hp" to 31, "atk" to 16), m.iv) // unparsable / null / boolean values are left out
        assertEquals(emptyMap<String, Int>(), m.ev)
        assertEquals(listOf("Tackle", "Growl"), m.attacks)
        assertNull(m.nature)
        assertNull(m.gender)
    }

    @Test fun hugeAndOddNumbersDontBreakAnything() {
        val m = parse(listOf(monRow("p-1", 0L, """{"id": 1e30, "level": -5, "xp": 99999999999, "friendship": "NaN", "iv": {"hp": 1e400}}"""))).mons.single()
        assertEquals(Int.MAX_VALUE, m.id)
        assertEquals(-5, m.level) // the merge clamps; the reader just reports
        assertEquals(Int.MAX_VALUE, m.xp)
        assertNull(m.friendship)
        assertEquals(emptyMap<String, Int>(), m.iv)
    }

    @Test fun isMainAcceptsIntsBooleansAndText() {
        val rows = listOf(1L, 0L, 1, "1", "true", "0", null, 1.0, true).mapIndexed { i, v -> monRow("p-$i", v, "{}") }
        assertEquals(listOf(true, false, true, true, true, false, false, true, true), parse(rows).mons.map { it.isMain })
    }

    @Test fun badJsonSkipsTheRowWithAShortProblemLine() {
        val save = parse(listOf(
            monRow("good-1", 0L, json("id" to 1, "name" to "Leafling")),
            monRow("bad-json-0000-1234", 0L, "{not json"),
            monRow("arr", 0L, "[1,2]"),
            monRow("nul", 0L, null),
            monRow("empty", 0L, ""),
            monRow(null, 0L, "{oops"),
            monRow("good-2", 0L, "{}"),
        ))
        assertEquals(listOf("good-1", "good-2"), save.mons.map { it.individualId })
        assertEquals(5, save.problems.size)
        // The message names the row but doesn't repeat any Pokémon data.
        assertTrue(save.problems.any { it.contains("bad-json") })
        assertTrue(save.problems.none { it.contains("oops") || it.contains("Leafling") })
    }

    @Test fun missingRowIdFallsBackToTheJsonThenSkips() {
        val save = parse(listOf(
            monRow(null, 0L, json("individual_id" to "from-json", "id" to 1)),
            monRow("  ", 0L, json("individual_id" to "from-json-2")),
            monRow("", 0L, json("id" to 1)),
            monRow("row-wins", 0L, json("individual_id" to "json-loses")),
            monRow("x".repeat(500), 0L, "{}"),
        ))
        assertEquals(listOf("from-json", "from-json-2", "row-wins"), save.mons.map { it.individualId })
        assertEquals(2, save.problems.size) // one without any id, one oversized
    }

    @Test fun duplicateIdsFirstWins() {
        val save = parse(listOf(
            monRow("dup", 0L, json("id" to 1, "level" to 10)),
            monRow("dup", 1L, json("id" to 2, "level" to 99)),
            monRow("other", 0L, json("id" to 3)),
        ))
        assertEquals(listOf("dup", "other"), save.mons.map { it.individualId })
        assertEquals(10, save.mons[0].level)
        assertFalse(save.mons[0].isMain)
    }

    @Test fun theNumberOfPokemonIsCapped() {
        val rows = (1..AnkimonSaveReader.MAX_MONS + 5).map { monRow("p-$it", 0L, "{}") }
        val save = parse(rows)
        assertEquals(AnkimonSaveReader.MAX_MONS, save.mons.size)
        assertEquals(1, save.problems.size)
    }

    // ---- team, items, user data, config ----------------------------------------------------------------------------

    @Test fun teamIsOrderedBySlot() {
        val save = parse(
            listOf(monRow("a", 0L, "{}")),
            team = listOf(
                mapOf("slot_position" to 3L, "individual_id" to "c"), mapOf("slot_position" to 1L, "individual_id" to "a"),
                mapOf("slot_position" to "2", "individual_id" to "b"), mapOf("slot_position" to 4L, "individual_id" to null),
                mapOf("slot_position" to 5L, "individual_id" to "a"), mapOf("slot_position" to null, "individual_id" to "z"),
            ),
        )
        assertEquals(listOf("a", "b", "c", "z"), save.team)
    }

    @Test fun aMissingTeamTableIsEmpty() {
        val save = parse(listOf(monRow("a", 0L, "{}")))
        assertEquals(emptyList<String>(), save.team)
        assertEquals(emptyMap<String, Int>(), save.items)
        assertEquals(emptyList<String>(), save.tms)
        assertNull(save.cash)
        assertEquals(emptySet<Int>(), save.seen)
        assertEquals(emptySet<Int>(), save.caught)
    }

    @Test fun itemsKeepOnlyPositiveQuantitiesAndTmsAreSeparated() {
        val save = parse(
            listOf(monRow("a", 0L, "{}")),
            items = listOf(
                mapOf("item_name" to "poke-ball", "quantity" to 5L, "data" to "{}"),
                mapOf("item_name" to "potion", "quantity" to 0L, "data" to null),
                mapOf("item_name" to "ultra-ball", "quantity" to -2L, "data" to null),
                mapOf("item_name" to "great-ball", "quantity" to "7", "data" to "not json"),
                mapOf("item_name" to "thunderbolt", "quantity" to 1L, "data" to """{"type": "TM"}"""),
                mapOf("item_name" to "earthquake", "quantity" to 1L, "data" to """{"category": "tm"}"""),
                mapOf("item_name" to "surf", "quantity" to 1L, "data" to """{"is_tm": true}"""),
                mapOf("item_name" to "icebeam", "quantity" to 0L, "data" to """{"type": "TM"}"""),
                mapOf("item_name" to "rare-candy", "quantity" to 2L, "data" to """{"type": "item", "is_tm": false}"""),
                mapOf("item_name" to "  ", "quantity" to 3L, "data" to null),
                mapOf("item_name" to null, "quantity" to 3L, "data" to null),
                mapOf("item_name" to "no-quantity", "quantity" to null, "data" to null),
            ),
        )
        assertEquals(mapOf("poke-ball" to 5, "great-ball" to 7, "rare-candy" to 2), save.items)
        assertEquals(listOf("thunderbolt", "earthquake", "surf"), save.tms)
    }

    @Test fun itemsAreCapped() {
        val items = (1..AnkimonSaveReader.MAX_ITEMS + 50).map { mapOf("item_name" to "item-$it", "quantity" to 1L, "data" to null) }
        assertEquals(AnkimonSaveReader.MAX_ITEMS, parse(listOf(monRow("a", 0L, "{}")), items = items).items.size)
    }

    @Test fun pokedexListsAreCleaned() {
        val save = parse(
            listOf(monRow("a", 0L, "{}")),
            userData = listOf(
                mapOf("key" to "pokedex_seen", "value" to """[1, "2", 3.0, "x", null, -4, 0, [5], {"a": 1}, true, 10100]"""),
                mapOf("key" to "pokedex_caught", "value" to "[25, 25, \"133\"]"),
                mapOf("key" to "todays_shop", "value" to "[900]"),
                mapOf("key" to "pity_tracker", "value" to """{"tokens": "SECRET"}"""),
            ),
        )
        assertEquals(setOf(1, 2, 3, 10100), save.seen)
        assertEquals(setOf(25, 133), save.caught)
        // Garbage values read as empty.
        val bad = parse(
            listOf(monRow("a", 0L, "{}")),
            userData = listOf(mapOf("key" to "pokedex_seen", "value" to "nope"), mapOf("key" to "pokedex_caught", "value" to """{"a": 1}""")),
        )
        assertEquals(emptySet<Int>(), bad.seen)
        assertEquals(emptySet<Int>(), bad.caught)
    }

    @Test fun onlyTheTrainerCashIsTakenFromConfig() {
        val config = listOf(
            mapOf("key" to "trainer.name", "value" to "\"Ash Ketchum\""),
            mapOf("key" to "sync.token", "value" to "\"SECRET-TOKEN-1234\""),
            mapOf("key" to "ankiweb.password", "value" to "\"hunter2\""),
            mapOf("key" to "trainer.cash", "value" to "12400"),
            mapOf("key" to "misc.gen9", "value" to "true"),
        )
        val save = parse(listOf(monRow("a", 0L, "{}")), config = config)
        assertEquals(12400, save.cash)
        // Nothing else of the config table is anywhere in what we keep.
        val dump = save.toString()
        for (secret in listOf("SECRET", "hunter2", "Ketchum", "ankiweb", "sync.token")) assertFalse(secret, dump.contains(secret))
    }

    @Test fun cashAcceptsNumbersAndText() {
        fun cash(v: Any?) = parse(listOf(monRow("a", 0L, "{}")), config = listOf(mapOf("key" to "trainer.cash", "value" to v))).cash
        assertEquals(12400, cash("12400"))
        assertEquals(12400, cash("12400.0"))
        assertEquals(12400, cash("\"12400\""))
        assertEquals(12400, cash(12400L))
        assertEquals(12400, cash(12400.4))
        assertEquals(0, cash("-50"))
        assertNull(cash("lots"))
        assertNull(cash(null))
        assertNull(cash("null"))
    }

    @Test fun theExpShareHolderIsReadInClassicModeOnly() {
        fun holder(value: Any?, mode: Any? = null): String? {
            val config = listOfNotNull(
                mapOf("key" to "trainer.xp_share", "value" to value),
                mode?.let { mapOf("key" to "trainer.xp_share_mode", "value" to it) },
            )
            return parse(listOf(monRow("a", 0L, "{}")), config = config).xpShare
        }
        assertEquals("a1b2-c3", holder("a1b2-c3"))          // Ankimon writes str(value): plain text
        assertEquals("a1b2-c3", holder("\"a1b2-c3\""))
        assertEquals("a1b2-c3", holder(" a1b2-c3 ", "classic"))
        assertEquals("a1b2-c3", holder("a1b2-c3", "\"classic\""))
        assertNull(holder("None"))                           // str(None): turned off
        assertNull(holder("null"))
        assertNull(holder(""))
        assertNull(holder(null))
        assertNull(holder("a1b2-c3", "oras"))                // the whole team shares: no single holder
        assertNull(parse(listOf(monRow("a", 0L, "{}"))).xpShare)
    }

    // ---- not an Ankimon save ----------------------------------------------------------------------------------------

    @Test fun noPokemonTableIsNotAnAnkimonSave() {
        val e = assertThrows(NotAnkimonException::class.java) { AnkimonSaveReader.parse(mapOf("items" to emptyList())) }
        assertEquals("This is not an Ankimon save file", e.message)
        // An existing but empty table is a valid (empty) save.
        assertEquals(0, AnkimonSaveReader.parse(mapOf("captured_pokemon" to emptyList())).mons.size)
    }

    // ---- on a real database file -----------------------------------------------------------------------------------

    private fun dbOf(sql: String): SqliteReader {
        assumeTrue("sqlite3 is not installed", AnkimonFixture.sqliteAvailable())
        return SqliteReader(AnkimonFixture.build(tmp.root, "s.db", sql).readBytes())
    }

    @Test fun readsARealExportFile() {
        val save = AnkimonSaveReader.read(dbOf(AnkimonFixture.smallSave()))
        assertEquals(3, save.mons.size)
        val main = save.mons[0]
        assertEquals("aaaa1111-0000-4000-8000-000000000001", main.individualId)
        assertTrue(main.isMain)
        assertEquals(25, main.id)
        assertEquals("Zappy", main.nickname)
        assertEquals(30, main.level)
        assertEquals(31, main.iv["spe"])
        assertEquals(252, main.ev["atk"])
        assertEquals(listOf("Thunderbolt", "Quick Attack"), main.attacks)
        assertTrue(main.shiny)
        assertEquals("2026-01-02 03:04:05", main.capturedDate)
        assertEquals(listOf("aaaa1111-0000-4000-8000-000000000002", "aaaa1111-0000-4000-8000-000000000001"), save.team)
        assertEquals(mapOf("poke-ball" to 12, "potion" to 3), save.items) // tm-none has quantity 0
        assertEquals(listOf("thunderbolt"), save.tms)
        assertEquals(12400, save.cash)
        assertEquals(setOf(1, 2, 25, 16), save.seen)
        assertEquals(setOf(25, 1), save.caught)
        assertEquals(emptyList<String>(), save.problems)
        // The token that lives in the config table never reaches us.
        assertFalse(save.toString().contains("SECRET"))
        assertFalse(save.toString().contains("Ash"))
    }

    @Test fun readsAnOldSchemaFileWhereIsMainWasAddedLast() {
        val save = AnkimonSaveReader.read(dbOf(
            """
            CREATE TABLE captured_pokemon (individual_id TEXT PRIMARY KEY, data TEXT NOT NULL,
                name TEXT GENERATED ALWAYS AS (json_extract(data, '${'$'}.name')) VIRTUAL);
            INSERT INTO captured_pokemon (individual_id, data) VALUES ('old', '{"id": 1, "level": 3}');
            ALTER TABLE captured_pokemon ADD COLUMN is_main INTEGER DEFAULT 0;
            INSERT INTO captured_pokemon (individual_id, data, is_main) VALUES ('new', '{"id": 2}', 1);
            """.trimIndent(),
        ))
        assertEquals(listOf("old", "new"), save.mons.map { it.individualId })
        assertEquals(listOf(false, true), save.mons.map { it.isMain })
        assertEquals(3, save.mons[0].level)
    }

    @Test fun aFileWithoutTheSavedTablesIsRefused() {
        val e = assertThrows(NotAnkimonException::class.java) {
            AnkimonSaveReader.read(dbOf("CREATE TABLE notes (id INTEGER PRIMARY KEY, text TEXT);\nINSERT INTO notes VALUES (1, 'hello');"))
        }
        assertEquals("This is not an Ankimon save file", e.message)
        // A captured_pokemon table without a data column is not Ankimon's either.
        assertThrows(NotAnkimonException::class.java) {
            AnkimonSaveReader.read(dbOf("CREATE TABLE captured_pokemon (individual_id TEXT, other TEXT);"))
        }
    }

    @Test fun otherTablesMayBeMissing() {
        val save = AnkimonSaveReader.read(dbOf(
            "CREATE TABLE captured_pokemon (individual_id TEXT PRIMARY KEY, is_main INTEGER DEFAULT 0, data TEXT NOT NULL);\n" +
                "INSERT INTO captured_pokemon VALUES ('one', 1, '{\"id\": 7}');",
        ))
        assertEquals(1, save.mons.size)
        assertEquals(emptyList<String>(), save.team)
        assertNull(save.cash)
    }
}
