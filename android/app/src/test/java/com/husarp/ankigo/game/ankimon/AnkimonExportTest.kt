package com.husarp.ankigo.game.ankimon

import com.husarp.ankigo.game.Game
import com.husarp.ankigo.game.GameData
import com.husarp.ankigo.game.GameEvent
import com.husarp.ankigo.game.GameState
import com.husarp.ankigo.game.Mon
import com.husarp.ankigo.game.Nature
import com.husarp.ankigo.game.Progression
import com.husarp.ankigo.game.StatCalc
import com.husarp.ankigo.game.Stats
import com.husarp.ankigo.game.TestData
import com.husarp.ankigo.game.Tier
import com.husarp.ankigo.game.save.SaveCodec
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.random.Random

class AnkimonExportTest {
    @get:Rule val tmp = TemporaryFolder()

    private val NOW = 1_800_000_000_000L

    /** TestData.world() plus a regional form, a gen 8 and a gen 9 species. */
    private val data: GameData = TestData.data(
        TestData.world().species.values.toList() + listOf(
            TestData.species(10091, "Rattyalola").copy(form = "Alola", baseNum = 19),
            TestData.species(905, "Gen8mon"),
            TestData.species(906, "Futuremon"),
            TestData.species(300, "Noabil").copy(abilities = emptyList()),
        ),
    )

    private val refs = AnkimonRefs.fromFiles { name ->
        when (name) {
            "items.csv" -> "id,identifier,category_id,cost,fling_power,fling_effect_id\n" +
                "4,poke-ball,34,200,,\n3,great-ball,34,600,,\n17,potion,27,200,,\n50,rare-candy,26,10000,30,\n208,lucky-egg,16,10000,30,7\n"
            "moves.json" -> """{"tackle":{"num":33},"thunderbolt":{"num":85},"slash":{"num":163},"doubleedge":{"num":38},"bodyslam":{"num":34}}"""
            "pokemon.csv" -> "id,identifier,species_id,height,weight,base_experience,order,is_default\n10033,venusaur-mega,3,24,1555,,1,0\n"
            else -> null
        }
    }

    // ---- builders ------------------------------------------------------------------------------------------------

    private fun stat(s: Stats): Map<String, Int> = linkedMapOf("hp" to s.hp, "atk" to s.atk, "def" to s.def, "spa" to s.spa, "spd" to s.spd, "spe" to s.spe)

    /** A complete Ankimon Pokémon (`PokemonObject.to_dict` order, Python's JSON spacing); [over] replaces or appends members. */
    private fun json(id: String, num: Int, vararg over: Pair<String, Any?>): String {
        val sp = data.species.getValue(num)
        val m = linkedMapOf<String, Any?>(
            "name" to sp.id, "nickname" to "", "level" to 10, "gender" to "M", "id" to num, "ability" to "Testability",
            "type" to sp.types.map { it.label }, "base_stats" to stat(sp.base), "stats" to stat(Stats(30, 20, 20, 20, 20, 20)), "cp" to 100,
            "nature" to "Adamant", "ev" to stat(Stats.ZERO), "iv" to stat(Stats(15, 15, 15, 15, 15, 15)), "attacks" to listOf("tackle"),
            "base_experience" to sp.baseExp, "growth_rate" to "medium", "everstone" to false, "evolution_rejected" to false,
            "shiny" to false, "captured_date" to "2026-01-02 03:04:05", "individual_id" to id, "mega" to false, "special_form" to null,
            "xp" to 0, "hp" to 30, "friendship" to 0, "pokemon_defeated" to 0, "tier" to "Normal", "is_favorite" to false,
            "current_hp" to 30, "held_item" to null,
        )
        for ((k, v) in over) m[k] = v
        return PyJson.dumps(m)
    }

    private fun SqliteImage.addMon(id: String, main: Boolean, json: String) {
        table("captured_pokemon")!!.insert(mapOf("individual_id" to id, "is_main" to if (main) 1L else 0L, "data" to json))
    }

    private fun SqliteImage.setTeam(vararg ids: String) {
        val t = table("team")!!
        ids.forEachIndexed { i, id -> t.insert(mapOf("slot_position" to (i + 1).toLong(), "individual_id" to id)) }
    }

    private fun SqliteImage.addItem(id: Long, name: String, qty: Long, dataJson: String? = null, category: Long? = null) {
        table("items")!!.insert(mapOf("id" to id, "item_name" to name, "quantity" to qty, "data" to dataJson, "category_id" to category))
    }

    private fun SqliteImage.put(table: String, key: String, value: String) {
        table(table)!!.insert(mapOf("key" to key, "value" to value))
    }

    private fun SqliteImage.rowsOf(t: String): List<Map<String, Any?>> =
        table(t)!!.let { tb -> tb.rows.map { r -> tb.columns.indices.associate { tb.columns[it] to r.values[it] } } }

    private fun SqliteImage.dataOf(id: String): String =
        rowsOf("captured_pokemon").single { it["individual_id"] == id }["data"] as String

    private fun SqliteImage.has(id: String) = rowsOf("captured_pokemon").any { it["individual_id"] == id }

    private fun SqliteImage.obj(id: String) = JSONObject(dataOf(id))

    private fun SqliteImage.kv(table: String, key: String): String? = rowsOf(table).firstOrNull { it["key"] == key }?.get("value") as String?

    private fun SqliteImage.mains(): List<String> =
        rowsOf("captured_pokemon").filter { it["is_main"] == 1L }.map { it["individual_id"] as String }

    private fun SqliteImage.teamIds(): List<String> =
        rowsOf("team").sortedBy { it["slot_position"] as Long }.map { it["individual_id"] as String }

    private fun SqliteImage.item(name: String): Map<String, Any?>? = rowsOf("items").firstOrNull { it["item_name"] == name }

    /** key -> raw value text of a stored object. */
    private fun members(text: String): Map<String, String> = PyJson.members(text)!!.associate { it.key to text.substring(it.start, it.end) }

    private fun order(text: String): List<String> = PyJson.members(text)!!.map { it.key }

    private fun tablesOf(img: SqliteImage): Map<String, List<Map<String, Any?>>> =
        listOf("captured_pokemon", "team", "items", "user_data", "config").filter { img.table(it) != null }.associateWith { img.rowsOf(it) }

    /** What the real flow does: read the kept file, merge it into the game (Ankimon's team becomes the team). */
    private fun stateOf(img: SqliteImage, state: GameState = GameState()): GameState {
        AnkimonMerge.merge(data, state, AnkimonSaveReader.parse(tablesOf(img)), { refs.forms[it] }, true, NOW)
        state.starterChosen = true
        return state
    }

    private fun export(state: GameState, img: SqliteImage, fresh: Boolean = false) = AnkimonExport.edit(data, state, img, refs, NOW, fresh)

    private fun SqliteImage.copy(): SqliteImage = SqliteImage(
        objects.toMutableList(),
        tables.mapValuesTo(LinkedHashMap()) { (_, t) -> SqlTable(t.name, t.columns, t.rowidAlias, t.rows.map { SqlRow(it.rowid, it.values.toMutableList()) }.toMutableList()) },
        pageSize, userVersion, applicationId,
    )

    /** An Ankimon save as a player would have it after some play on the PC. */
    private fun rich(): SqliteImage {
        val img = AnkimonSchema.fresh()
        img.addMon("m-spark", true, json("m-spark", 25, "nickname" to "Zappy", "level" to 30, "xp" to 100, "shiny" to true, "friendship" to 200,
            "iv" to stat(Stats(31, 20, 10, 0, 5, 31)), "ev" to stat(Stats(4, 252, 0, 0, 0, 252)), "attacks" to listOf("thunderbolt", "quickattack"),
            "pokemon_defeated" to 12, "custom_note" to "keep me"))
        img.addMon("m-leaf", false, json("m-leaf", 1, "level" to 12, "xp" to 5, "nickname" to "Leafling", "attacks" to listOf("tackle", "vinewhip")))
        img.addMon("m-bird", false, json("m-bird", 16, "level" to 5))
        img.setTeam("m-leaf", "m-spark")
        img.addItem(4, "poke-ball", 12, "{}")
        img.addItem(17, "potion", 3)
        img.addItem(20085, "thunderbolt", 1, "{\"type\": \"TM\"}", 37)
        img.addItem(9999, "unknown-thing", 2)
        img.put("user_data", "pokedex_seen", "[1, 2, 25, 16]")
        img.put("user_data", "pokedex_caught", "[25, \"1\", 16]")
        img.put("user_data", "todays_shop", "[1]")
        img.put("config", "trainer.cash", "12400")
        img.put("config", "trainer.name", "Ash")
        img.put("config", "sync.token", "\"SECRET-TOKEN\"")
        img.table("badges")!!.insert(mapOf("badge_id" to "b1", "achieved" to 1L))
        return img
    }

    private fun monOf(state: GameState, ankimonId: String): Mon = state.mons.single { it.ankimonId == ankimonId }

    // ---- the schema of a fresh save ------------------------------------------------------------------------------

    private fun sqlite3(db: File, query: String): String {
        val p = ProcessBuilder("sqlite3", db.absolutePath, query).redirectErrorStream(true).start()
        val out = p.inputStream.readBytes().toString(Charsets.UTF_8).trim()
        if (!p.waitFor(60, TimeUnit.SECONDS)) { p.destroyForcibly(); fail("sqlite3 timed out") }
        return out
    }

    private fun squash(s: String?) = s?.replace(Regex("\\s+"), " ")?.replace(" )", ")")?.replace("( ", "(")

    @Test fun freshSchemaIsWhatSqliteStoresForAnkimonsStatements() {
        assumeTrue("sqlite3 is not installed", AnkimonFixture.sqliteAvailable())
        val statements = AnkimonFixture.SCHEMA + """
            CREATE TABLE IF NOT EXISTS pending_mobile_battles (id INTEGER PRIMARY KEY AUTOINCREMENT, revlog_id INTEGER UNIQUE NOT NULL,
                card_id INTEGER NOT NULL, ease INTEGER NOT NULL, review_time INTEGER NOT NULL, review_type INTEGER NOT NULL,
                queued_at INTEGER NOT NULL, resolved INTEGER NOT NULL DEFAULT 0, resolved_at INTEGER);
            CREATE TABLE IF NOT EXISTS mobile_battle_history (id INTEGER PRIMARY KEY AUTOINCREMENT, timestamp INTEGER NOT NULL,
                enemy_id INTEGER NOT NULL, enemy_name TEXT NOT NULL, enemy_level INTEGER NOT NULL, enemy_shiny INTEGER NOT NULL,
                companion_name TEXT, companion_level INTEGER, outcome TEXT NOT NULL, xp_gained INTEGER DEFAULT 0,
                trainer_xp_gained INTEGER DEFAULT 0, cash_gained INTEGER DEFAULT 0);
            CREATE INDEX IF NOT EXISTS idx_history_timestamp ON mobile_battle_history(timestamp);
            CREATE TABLE IF NOT EXISTS desktop_processed_reviews (revlog_id INTEGER PRIMARY KEY, card_id INTEGER);
        """.trimIndent()
        val ref = SqliteReader.open(AnkimonFixture.build(tmp.root, "ref.db", statements)).image()
        val ours = AnkimonSchema.fresh()
        assertEquals(ref.objects.map { Triple(it.type, it.name, it.tblName) }, ours.objects.map { Triple(it.type, it.name, it.tblName) })
        assertEquals(ref.objects.map { squash(it.sql) }, ours.objects.map { squash(it.sql) })
        assertEquals(ref.tables.keys.toList(), ours.tables.keys.toList())
        for ((name, t) in ref.tables) {
            assertEquals(name, t.columns, ours.tables.getValue(name).columns)
            assertEquals(name, t.rowidAlias, ours.tables.getValue(name).rowidAlias)
        }
    }

    @Test fun anAddedTableComesWithItsIndexesAndTheSequenceTable() {
        val img = AnkimonSchema.fresh()
        img.objects.removeAll { it.tblName == "pokemon_history" || it.tblName == "sqlite_sequence" }
        img.tables.remove("pokemon_history"); img.tables.remove("sqlite_sequence")
        AnkimonSchema.ensure(img, "pokemon_history")
        assertNotNull(img.table("pokemon_history"))
        assertNotNull(img.table("sqlite_sequence"))
        assertEquals(listOf("sqlite_sequence", "pokemon_history", "sqlite_autoindex_pokemon_history_1"), img.objects.takeLast(3).map { it.name })
        AnkimonSchema.ensure(img, "pokemon_history") // already there: nothing happens
        assertEquals(1, img.objects.count { it.name == "pokemon_history" })
    }

    // ---- rows AnkiGo did not change --------------------------------------------------------------------------------

    @Test fun unchangedGameWritesEveryRowBackExactly() {
        val img = rich()
        val before = img.rowsOf("captured_pokemon").associate { it["individual_id"] as String to it["data"] as String }
        val team = img.rowsOf("team"); val items = img.rowsOf("items"); val config = img.rowsOf("config"); val user = img.rowsOf("user_data")
        val e = export(stateOf(img), img)
        for ((id, text) in before) assertEquals(text, img.dataOf(id))
        assertEquals(ExportReport(false, 3, 0, 0, 3, 0, 2, 12400, 3, 4), e.report) // 3 caught: 25, "1", 16
        assertEquals(team, img.rowsOf("team"))
        assertEquals(items, img.rowsOf("items"))
        assertEquals(config, img.rowsOf("config"))
        assertEquals(user, img.rowsOf("user_data"))
        assertEquals(listOf("m-spark"), img.mains())
        assertTrue(e.rows.isEmpty())
    }

    @Test fun anUnchangedPokemonKeepsOddFormatsLevelsAndFormIds() {
        val img = AnkimonSchema.fresh()
        // Level above 100, unknown move names, a Mega form id (maps to species 3), whitespace and key order of its own.
        val odd = "{ \"name\":\"leafkingmega\" ,\"id\":10033,\"level\":150,\"xp\":99999,\"attacks\":[\"Tackle\",\"Moon Hammer\",\"Slash\"],\"nature\":\"serious\",\"gender\":\"M\",\"tier\":\"Fossil\",\"custom\":1.50}"
        img.addMon("odd", true, odd)
        img.setTeam("odd")
        val e = export(stateOf(img), img)
        assertEquals(odd, img.dataOf("odd"))
        assertEquals(0, e.report.updated)
    }

    @Test fun rowsAnkiGoDoesNotHaveComeBackUntouched() {
        val img = rich()
        img.addMon("gen9", false, json("gen9", 906))               // gen 9 is off: skipped at import
        img.addMon("form", false, json("form", 3, "id" to 10999, "name" to "unknownform")) // a form nobody knows
        img.addMon("junk", false, "this is not json")
        img.addMon("future", false, "{\"id\": 25, \"level\": 3, \"x\": [1,2,")
        val keep = listOf("gen9", "form", "junk", "future").associateWith { img.dataOf(it) }
        val e = export(stateOf(img), img)
        for ((id, text) in keep) assertEquals(text, img.dataOf(id))
        assertEquals(7, e.report.mons)
        assertEquals(7, e.report.untouched)
        assertEquals(0, e.report.added)
    }

    // ---- rows AnkiGo changed -----------------------------------------------------------------------------------------

    @Test fun levelXpEvsAndWinsAreWrittenAndNothingElseMoves() {
        val img = rich()
        val old = img.dataOf("m-spark")
        val st = stateOf(img)
        val m = monOf(st, "m-spark")
        m.level = 31; m.xp = 40; m.evs = Stats(8, 252, 0, 0, 0, 250); m.defeats = 13; m.friendship = 201
        val e = export(st, img)
        assertEquals(1, e.report.updated)
        assertEquals(2, e.report.untouched)
        val new = img.dataOf("m-spark")
        assertEquals(order(old), order(new)) // same members, same order
        val before = members(old); val after = members(new)
        val changed = after.keys.filter { before[it] != after[it] }.toSet()
        assertEquals(setOf("level", "xp", "ev", "pokemon_defeated", "friendship", "stats", "cp", "hp", "current_hp"), changed)
        val sp = data.sp(25)
        val o = JSONObject(new)
        assertEquals(31, o.getInt("level"))
        assertEquals(40, o.getInt("xp"))
        assertEquals(13, o.getInt("pokemon_defeated"))
        assertEquals(201, o.getInt("friendship"))
        assertEquals(250, o.getJSONObject("ev").getInt("spe"))
        val want = StatCalc.stats(sp, m)
        assertEquals(want.hp, o.getJSONObject("stats").getInt("hp"))
        assertEquals(want.atk, o.getJSONObject("stats").getInt("atk")) // Adamant: the same formula and nature as Ankimon's
        assertEquals(want.spe, o.getJSONObject("stats").getInt("spe"))
        assertEquals(want.hp, o.getInt("hp"))
        assertEquals(want.hp, o.getInt("current_hp"))
        assertEquals(AnkimonExport.cp(sp.base, m.ivs, m.evs, 31), o.getInt("cp"))
        assertEquals("keep me", o.getString("custom_note"))
        assertEquals(mapOf("is_main" to 1L), mapOf("is_main" to img.rowsOf("captured_pokemon").first { it["individual_id"] == "m-spark" }["is_main"]))
    }

    @Test fun aLevelAbove100StaysAndItsStatsUseIt() {
        val img = AnkimonSchema.fresh()
        img.addMon("big", true, json("big", 1, "level" to 150, "xp" to 777))
        img.setTeam("big")
        val st = stateOf(img)
        val m = monOf(st, "big")
        assertEquals(100, m.level)
        m.evs = Stats(0, 0, 0, 100, 0, 0)
        export(st, img)
        val o = img.obj("big")
        assertEquals(150, o.getInt("level"))
        assertEquals(777, o.getInt("xp"))
        assertEquals(StatCalc.hp(45, 15, 0, 150), o.getJSONObject("stats").getInt("hp"))
        assertEquals(AnkimonExport.cp(data.sp(1).base, m.ivs, m.evs, 150), o.getInt("cp"))
        assertEquals(100, o.getJSONObject("ev").getInt("spa"))
    }

    @Test fun movesAreKeptWhileTheyAreTheSameAndReplacedWhenTheyChange() {
        val img = rich()
        img.addMon("fake", false, json("fake", 16, "attacks" to listOf("Tackle", "Moon Hammer", "Quick Attack")))
        val st = stateOf(img)
        assertEquals(listOf("tackle", "quickattack"), monOf(st, "fake").moves)
        export(st, img)
        assertEquals("[\"Tackle\", \"Moon Hammer\", \"Quick Attack\"]", members(img.dataOf("fake"))["attacks"])
        monOf(st, "fake").moves += "slash"
        export(st, img)
        assertEquals("[\"tackle\", \"quickattack\", \"slash\", \"Moon Hammer\"]", members(img.dataOf("fake"))["attacks"]) // a move AnkiGo doesn't know stays while there is room
    }

    @Test fun anEvolvedPokemonGetsItsNewSpeciesAndADefaultNicknameFollows() { // "leaf-ling" is a default one for Ankimon (_normalize_nick)
        val img = AnkimonSchema.fresh()
        img.addMon("a", true, json("a", 1, "level" to 16, "nickname" to "Leafling"))      // nickname = species name
        img.addMon("b", false, json("b", 1, "level" to 16, "nickname" to "Mr. Bud"))       // a real nickname
        img.addMon("c", false, json("c", 1, "level" to 16, "nickname" to ""))
        img.addMon("d", false, json("d", 1, "level" to 16, "nickname" to " leaf-ling ", "evolution_rejected" to true)) // Ankimon's normaliser
        img.setTeam("a", "b")
        val st = stateOf(img)
        for (id in listOf("a", "b", "c", "d")) {
            val mon = monOf(st, id)
            Progression.evolve(data, st, mon, data.sp(1).evolutions[0], Random(1), mutableListOf<GameEvent>())
            assertEquals(2, mon.species)
        }
        val e = export(st, img)
        assertEquals(4, e.report.updated)
        for (id in listOf("a", "b", "c", "d")) {
            val o = img.obj(id)
            assertEquals(2, o.getInt("id"))
            assertEquals("leafbud", o.getString("name"))
            assertEquals("Grass", o.getJSONArray("type").getString(0))
            assertEquals(60, o.getJSONObject("base_stats").getInt("hp"))
            assertEquals("medium", o.getString("growth_rate"))
            assertEquals(data.sp(2).baseExp, o.getInt("base_experience"))
            assertFalse(o.getBoolean("evolution_rejected"))
            assertEquals(StatCalc.hp(60, 15, 0, 16), o.getJSONObject("stats").getInt("hp"))
            assertEquals(AnkimonExport.cp(data.sp(2).base, Stats(15, 15, 15, 15, 15, 15), Stats.ZERO, 16), o.getInt("cp"))
            assertEquals(0, o.getInt("xp"))
        }
        assertEquals("", img.obj("a").getString("nickname"))
        assertEquals("Mr. Bud", img.obj("b").getString("nickname"))
        assertEquals("", img.obj("c").getString("nickname"))
        assertEquals("", img.obj("d").getString("nickname"))
    }

    @Test fun friendshipAbove255IsNotCutBackByAnkiGosCap() {
        val img = AnkimonSchema.fresh()
        img.addMon("f", true, json("f", 16, "friendship" to 400)); img.setTeam("f")
        val st = stateOf(img)
        val m = monOf(st, "f")
        assertEquals(255, m.friendship)                      // the import cuts it
        export(st, img)
        assertEquals(400, img.obj("f").getInt("friendship")) // equal: untouched
        m.friendship = 262                                   // +7 on the phone
        export(st, img)
        assertEquals(407, img.obj("f").getInt("friendship"))
    }

    @Test fun aSpeciesWithoutAbilitiesIsWrittenWithAnkimonsDefaultAbility() {
        val st = GameState(starterChosen = true)
        val m = TestData.mon(data, 300, 5, listOf("tackle"), "u-none"); m.ability = ""
        st.mons += m; st.team += m.uid
        val img = AnkimonSchema.fresh()
        export(st, img, fresh = true)
        assertEquals("Run Away", img.obj("u-none").getString("ability"))
        assertEquals(emptyList<String>(), AnkimonExport.verify(AnkimonExport.build(data, st, null, refs, NOW)))
    }

    @Test fun aFreshBuildPassesItsOwnChecksWithoutSqlite3() {
        val result = AnkimonExport.build(data, playedGame(), null, refs, NOW)
        assertEquals(emptyList<String>(), AnkimonExport.verify(result))
        assertEquals(emptyList<String>(), SqliteCheck.check(result.bytes))
        assertEquals(3, readAll(result.bytes).mons.size)
    }

    @Test fun nicknamesChangedOnThePhoneAreWritten() {
        val img = rich()
        img.addMon("nick", false, json("nick", 16, "nickname" to "Tweety"))
        val st = stateOf(img)
        monOf(st, "m-spark").nickname = "Bolt"
        monOf(st, "nick").nickname = null             // nickname removed
        export(st, img)
        assertEquals("Bolt", img.obj("m-spark").getString("nickname"))
        assertEquals("", img.obj("nick").getString("nickname"))
        assertEquals("Leafling", img.obj("m-leaf").getString("nickname")) // the species name: no nickname for AnkiGo, and no change
        assertEquals("", img.obj("m-bird").getString("nickname"))
    }

    @Test fun heldItemsAndTheEverstoneFlagFollowThePhone() {
        val img = AnkimonSchema.fresh()
        img.addMon("e", true, json("e", 1, "held_item" to "everstone", "everstone" to true))
        img.addMon("n", false, json("n", 16))
        img.addMon("o", false, json("o", 16, "held_item" to "oran-berry"))
        img.setTeam("e")
        val st = stateOf(img)
        monOf(st, "e").heldItem = null
        monOf(st, "n").heldItem = "lucky-egg"
        export(st, img)
        assertNull(img.obj("e").opt("held_item").takeIf { it != JSONObject.NULL })
        assertFalse(img.obj("e").getBoolean("everstone"))
        assertEquals("lucky-egg", img.obj("n").getString("held_item"))
        assertFalse(img.obj("n").getBoolean("everstone"))
        monOf(st, "n").heldItem = "everstone"
        export(st, img)
        assertTrue(img.obj("n").getBoolean("everstone"))
        assertEquals("oran-berry", img.obj("o").getString("held_item")) // AnkiGo can't hold it: never touched
    }

    // ---- new rows ----------------------------------------------------------------------------------------------------

    private val DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT)

    private fun caught(uid: String = "u-new"): Mon {
        return TestData.mon(data, 26, 20, listOf("thunderbolt", "quickattack"), uid)
    }

    @Test fun aPokemonCaughtOnThePhoneBecomesAFullAnkimonRow() {
        val img = rich()
        val st = stateOf(img)
        val m = Mon(
            uid = "u-new", species = 26, name = "Sparkrat", level = 20, xp = 7, ivs = Stats(31, 0, 12, 30, 5, 20), evs = Stats(10, 0, 0, 252, 0, 6),
            nature = Nature.MODEST, gender = 'F', shiny = true, ability = "Testability", moves = mutableListOf("thunderbolt", "bodyslam"),
            hp = 1, friendship = 70, heldItem = "lucky-egg", caughtAt = NOW, defeats = 4, tier = Tier.ULTRA, nickname = "Volty",
        )
        st.mons += m
        val e = export(st, img)
        assertEquals(1, e.report.added)
        assertEquals(4, e.report.mons)
        val text = img.dataOf("u-new")
        assertEquals(
            listOf(
                "name", "nickname", "level", "gender", "id", "ability", "type", "base_stats", "stats", "cp", "nature", "ev", "iv", "attacks",
                "base_experience", "growth_rate", "everstone", "evolution_rejected", "shiny", "captured_date", "individual_id", "mega",
                "special_form", "xp", "hp", "friendship", "pokemon_defeated", "tier", "is_favorite", "current_hp", "held_item",
            ),
            order(text),
        )
        val sp = data.sp(26)
        val o = JSONObject(text)
        assertEquals("sparkrat", o.getString("name"))
        assertEquals("Volty", o.getString("nickname"))
        assertEquals(20, o.getInt("level"))
        assertEquals("F", o.getString("gender"))
        assertEquals(26, o.getInt("id"))
        assertEquals("Testability", o.getString("ability"))
        assertEquals("Electric", o.getJSONArray("type").getString(0))
        assertEquals(sp.base.spa, o.getJSONObject("base_stats").getInt("spa"))
        val want = StatCalc.stats(sp, m)
        assertEquals(want.spa, o.getJSONObject("stats").getInt("spa")) // Modest: +10% special attack
        assertEquals(want.atk, o.getJSONObject("stats").getInt("atk")) // -10% attack
        assertEquals(AnkimonExport.cp(sp.base, m.ivs, m.evs, 20), o.getInt("cp"))
        assertEquals("Modest", o.getString("nature"))
        assertEquals(252, o.getJSONObject("ev").getInt("spa"))
        assertEquals(30, o.getJSONObject("iv").getInt("spa"))
        assertEquals("thunderbolt", o.getJSONArray("attacks").getString(0))
        assertEquals(2, o.getJSONArray("attacks").length())
        assertEquals(sp.baseExp, o.getInt("base_experience"))
        assertEquals("medium", o.getString("growth_rate"))
        assertFalse(o.getBoolean("everstone"))
        assertFalse(o.getBoolean("evolution_rejected"))
        assertTrue(o.getBoolean("shiny"))
        assertEquals(Instant.ofEpochMilli(NOW).atZone(ZoneId.systemDefault()).format(DATE), o.getString("captured_date"))
        assertEquals("u-new", o.getString("individual_id"))
        assertFalse(o.getBoolean("mega"))
        assertTrue(o.isNull("special_form"))
        assertEquals(7, o.getInt("xp"))
        assertEquals(want.hp, o.getInt("hp"))
        assertEquals(want.hp, o.getInt("current_hp"))
        assertEquals(70, o.getInt("friendship"))
        assertEquals(4, o.getInt("pokemon_defeated"))
        assertEquals("Ultra", o.getString("tier"))
        assertFalse(o.getBoolean("is_favorite"))
        assertEquals("lucky-egg", o.getString("held_item"))
        assertEquals(listOf("m-spark"), img.mains()) // a new Pokémon is never the main by itself
        assertEquals(true, e.rows["u-new"]?.isNew)
    }

    @Test fun aNewPokemonWithoutNicknameOrDateGetsAnEmptyNicknameAndNoDate() {
        val st = GameState(starterChosen = true)
        val m = caught()
        st.mons += m; st.team += m.uid
        val img = AnkimonSchema.fresh()
        export(st, img, fresh = true)
        val o = img.obj("u-new")
        assertEquals("", o.getString("nickname"))
        assertTrue(o.isNull("captured_date"))
        assertTrue(o.isNull("held_item"))
        assertEquals("Normal", o.getString("tier"))
        m.tier = Tier.GMAX
        val img2 = AnkimonSchema.fresh()
        export(st, img2, fresh = true)
        assertEquals("Gmax", img2.obj("u-new").getString("tier"))
    }

    // ---- ids ---------------------------------------------------------------------------------------------------------

    @Test fun anIdIsTheAnkimonIdElseTheUidAndAClashGetsAStableDerivedId() {
        val img = AnkimonSchema.fresh()
        img.addMon("p-1", true, json("p-1", 25))
        val st = stateOf(img)                      // one Pokémon with ankimonId p-1
        val phone = caught("u-1"); st.mons += phone
        val dup = caught("u-2"); dup.ankimonId = "u-1"; st.mons += dup // claims the id the phone Pokémon uses
        export(st, img)
        val derived = UUID.nameUUIDFromBytes("ankigo:u-1#1".toByteArray()).toString()   // the phone Pokémon "u-1" lost the clash
        assertTrue(img.has("p-1")); assertTrue(img.has("u-1")); assertTrue(img.has(derived))
        assertEquals(3, img.rowsOf("captured_pokemon").size)
        assertEquals(derived, img.obj(derived).getString("individual_id"))
        // The same game exported again gives the same ids.
        val img2 = AnkimonSchema.fresh(); img2.addMon("p-1", true, json("p-1", 25))
        export(st, img2)
        assertEquals(img.rowsOf("captured_pokemon").map { it["individual_id"] }.toSet(), img2.rowsOf("captured_pokemon").map { it["individual_id"] }.toSet())
    }

    @Test fun aPokemonOfAnOlderImportThatTheKeptFileLacksIsAddedUnderItsAnkimonId() {
        val img = AnkimonSchema.fresh()
        img.addMon("p-1", true, json("p-1", 25))
        val st = stateOf(img)
        val old = caught("u-old"); old.ankimonId = "from-an-older-import"; st.mons += old
        val e = export(st, img)
        assertEquals(1, e.report.added)
        assertTrue(img.has("from-an-older-import"))
        assertEquals("from-an-older-import", img.obj("from-an-older-import").getString("individual_id"))
    }

    @Test fun aSpeciesTheGameDataLacksStopsTheExportOfANewRowButNotAnOldOne() {
        val img = rich()
        val st = stateOf(img)
        monOf(st, "m-bird").species = 99999                 // an old row: left alone
        export(st, img)
        val bad = caught("u-bad"); bad.species = 99999; st.mons += bad
        try { export(st, img); fail() } catch (e: ExportException) { assertTrue(e.message!!.contains("can't be written safely")) }
    }

    // ---- released on the phone -----------------------------------------------------------------------------------------

    private fun gameOf(st: GameState) = Game(data, st, Random(3)) { NOW }

    @Test fun gameRememberstheReleasedAnkimonIdAndKeepsTheNewest5000() {
        val st = GameState(starterChosen = true)
        val keep = caught("u-keep"); st.mons += keep; st.team += keep.uid
        for (i in 0 until 3) st.mons += caught("x-$i").also { it.ankimonId = "a-$i" }
        st.mons += caught("y")
        val g = gameOf(st)
        assertTrue(g.release("x-1").ok)
        assertTrue(g.release("y").ok)
        assertEquals(listOf("a-1", "y"), st.releasedAnkimon.toList()) // ankimonId when it has one, else the uid
        for (i in 0 until 5_000) st.releasedAnkimon.add("fill-$i")
        st.mons += caught("z"); assertTrue(g.release("z").ok)
        assertEquals(5_000, st.releasedAnkimon.size)
        assertEquals("z", st.releasedAnkimon.last())
        assertFalse("a-1" in st.releasedAnkimon)
    }

    @Test fun releasedIdsSurviveTheSaveCodecInOrderAndAnOldSaveHasNone() {
        val st = GameState(starterChosen = true)
        st.mons += caught(); st.team += "u-new"
        st.releasedAnkimon += listOf("zzz", "aaa", "mmm")
        val back = SaveCodec.decode(SaveCodec.encode(st))
        assertEquals(listOf("zzz", "aaa", "mmm"), back.releasedAnkimon.toList())
        assertEquals(SaveCodec.encode(st), SaveCodec.encode(back))
        val old = SaveCodec.encode(st).replace(Regex(",\"releasedAnkimon\":\\[[^\\]]*\\]"), "")
        assertFalse(old.contains("releasedAnkimon"))
        assertTrue(SaveCodec.decode(old).releasedAnkimon.isEmpty())
    }

    @Test fun aReleasedPokemonLeavesTheSaveWithAHistoryEntryAndNoTeamSlotOrExpShare() {
        val img = rich()
        img.put("config", "trainer.xp_share", "m-leaf")
        img.table("pokemon_history")!!.insert(mapOf("id" to 7L, "individual_id" to "long-gone", "data" to "{}"))
        img.table("sqlite_sequence")!!.insert(mapOf("name" to "pokemon_history", "seq" to 9L))
        val st = stateOf(img)
        val g = gameOf(st)
        assertTrue(g.release(monOf(st, "m-leaf").uid).ok)   // in the team
        assertTrue(g.release(monOf(st, "m-bird").uid).ok)
        st.releasedAnkimon += "not-in-the-file"
        val e = export(st, img)
        assertEquals(2, e.report.released)
        assertEquals(1, e.report.mons)
        assertFalse(img.has("m-leaf")); assertFalse(img.has("m-bird")); assertTrue(img.has("m-spark"))
        assertEquals(listOf("m-spark"), img.teamIds())
        assertEquals(listOf(1L), img.rowsOf("team").map { it["slot_position"] })   // renumbered
        assertNull(img.kv("config", "trainer.xp_share"))
        val hist = img.rowsOf("pokemon_history")
        assertEquals(listOf(7L, 10L, 11L), hist.map { it["id"] })                    // continues after the sequence (9)
        val h = JSONObject(hist[1]["data"] as String)
        assertEquals("m-leaf", h.getString("individual_id"))
        assertEquals("leafling", h.getString("name"))
        assertEquals(1, h.getInt("id"))
        assertFalse(h.getBoolean("shiny"))
        assertEquals(0, h.getInt("pokemon_defeated"))
        assertEquals(Instant.ofEpochMilli(NOW).atZone(ZoneId.systemDefault()).format(DATE), h.getString("released_date"))
        assertEquals(11L, img.rowsOf("sqlite_sequence").single { it["name"] == "pokemon_history" }["seq"])
    }

    @Test fun aHistoryEntryThatExistsIsNotAddedAgainAndAnOwnedIdIsNeverDeleted() {
        val img = rich()
        img.table("pokemon_history")!!.insert(mapOf("id" to 1L, "individual_id" to "m-bird", "data" to "{}"))
        val st = stateOf(img)
        st.releasedAnkimon += "m-bird"
        val e = export(st, img)                              // AnkiGo still owns m-bird: nothing is deleted
        assertEquals(0, e.report.released)
        assertTrue(img.has("m-bird"))
        monOf(st, "m-bird").let { st.mons.remove(it); st.team.remove(it.uid) }
        val e2 = export(st, img)
        assertEquals(1, e2.report.released)
        assertEquals(1, img.rowsOf("pokemon_history").size)  // it was already there
    }

    @Test fun releasingTheMainLeavesExactlyOneMainThatAnkiGoPicks() {
        val img = rich()
        val st = stateOf(img)
        val g = gameOf(st)
        assertEquals("m-spark", st.main?.ankimonId)
        g.setMain(monOf(st, "m-leaf").uid)
        assertTrue(g.release(monOf(st, "m-spark").uid).ok)
        val e = export(st, img)
        assertEquals(1, e.report.released)
        assertEquals(listOf("m-leaf"), img.mains())
        assertEquals(listOf("m-leaf"), img.teamIds())
    }

    // ---- team and main -----------------------------------------------------------------------------------------------

    @Test fun anUnchangedTeamLeavesTheTeamTableAndTheMainFlagAlone() {
        val img = rich()
        val team = img.rowsOf("team")
        val st = stateOf(img)
        assertTrue(monOf(st, "m-bird").uid !in st.team) // a box member
        export(st, img)
        assertEquals(team, img.rowsOf("team"))              // the file's own slot order (leaf, spark) was never rewritten
        assertEquals(listOf("m-spark"), img.mains())
    }

    @Test fun aTeamChangedOnThePhoneIsWrittenWithTheMainFirst() {
        val img = rich()
        val st = stateOf(img)
        val g = gameOf(st)
        assertTrue(g.toTeam(monOf(st, "m-bird").uid).ok)
        assertTrue(g.setMain(monOf(st, "m-bird").uid).ok)    // team: bird, spark, leaf
        export(st, img)
        assertEquals(listOf("m-bird", "m-spark", "m-leaf"), img.teamIds())
        assertEquals(listOf(1L, 2L, 3L), img.rowsOf("team").map { it["slot_position"] })
        assertEquals(listOf("m-bird"), img.mains())
    }

    @Test fun aNewPokemonThatJoinsTheTeamIsInTheTeamTable() {
        val img = rich()
        val st = stateOf(img)
        val m = caught(); st.mons += m; st.team += m.uid
        export(st, img)
        assertEquals(listOf("m-spark", "m-leaf", "u-new"), img.teamIds())
        assertEquals(listOf("m-spark"), img.mains())
    }

    @Test fun aFileWithoutAMainOrWithTwoGetsExactlyOne() {
        val none = rich()
        none.table("captured_pokemon")!!.rows.forEach { it.values[1] = 0L }
        export(stateOf(none), none)
        assertEquals(listOf("m-leaf"), none.mains())             // AnkiGo's main (first of its team) takes over
        val two = rich()
        two.table("captured_pokemon")!!.rows.forEach { it.values[1] = 1L }
        export(stateOf(two), two)
        assertEquals(1, two.mains().size)
    }

    @Test fun teamSlotsThatPointNowhereOrRepeatAreCleanedUp() {
        val img = rich()
        img.table("team")!!.rows.clear()
        val t = img.table("team")!!
        t.insert(mapOf("slot_position" to 2L, "individual_id" to "m-spark"))
        t.insert(mapOf("slot_position" to 5L, "individual_id" to "ghost"))
        t.insert(mapOf("slot_position" to 6L, "individual_id" to "m-spark"))
        t.insert(mapOf("slot_position" to 9L, "individual_id" to "m-leaf"))
        export(stateOf(img), img)
        assertEquals(listOf("m-spark", "m-leaf"), img.teamIds())
        assertEquals(listOf(1L, 2L), img.rowsOf("team").map { it["slot_position"] })
    }

    // ---- items and TMs -----------------------------------------------------------------------------------------------

    @Test fun itemCountsFollowThePhoneAndZeroDeletesTheRow() {
        val img = rich()
        val st = stateOf(img)
        assertEquals(12, st.count("poke-ball"))
        st.bag["poke-ball"] = 8
        st.bag["potion"] = 0
        st.bag["lucky-egg"] = 3                             // new: with the numbers from items.csv
        st.bag["great-ball"] = 4
        st.bag["linking-cord"] = 2                          // not in items.csv: Ankimon's "uncatalogued" numbers
        st.bag["amulet-coin"] = 1
        export(st, img)
        assertEquals(8L, img.item("poke-ball")!!["quantity"])
        assertEquals("{}", img.item("poke-ball")!!["data"])      // the rest of the row is as it was
        assertNull(img.item("potion"))
        val great = img.item("great-ball")!!
        assertEquals(listOf(3L, 4L, null, 34L, 600L), listOf(great["id"], great["quantity"], great["data"], great["category_id"], great["cost"]))
        val egg = img.item("lucky-egg")!!
        assertEquals(listOf(208L, 3L, 16L, 10000L, 30L, 7L), listOf(egg["id"], egg["quantity"], egg["category_id"], egg["cost"], egg["fling_power"], egg["fling_effect_id"]))
        assertEquals(-1L, img.item("amulet-coin")!!["id"])                      // in name order: the first without a number
        assertEquals(-2L, img.item("linking-cord")!!["id"])                     // one below the smallest id
        assertNull(img.item("linking-cord")!!["category_id"])
        assertEquals(2L, img.item("unknown-thing")!!["quantity"])               // not an AnkiGo item: untouched
        assertEquals(1L, img.item("thunderbolt")!!["quantity"])                 // a TM row: untouched
        st.bag["poke-ball"] = 20                                                // more than the file had
        export(st, img)
        assertEquals(20L, img.item("poke-ball")!!["quantity"])
        assertEquals(1, img.rowsOf("items").count { it["item_name"] == "poke-ball" })
    }

    @Test fun aRowWithQuantityZeroIsReusedNotInsertedAgain() {
        val img = rich()
        img.addItem(208, "lucky-egg", 0)
        val st = stateOf(img)
        assertEquals(0, st.count("lucky-egg"))
        st.bag["lucky-egg"] = 3
        export(st, img)
        assertEquals(1, img.rowsOf("items").count { it["item_name"] == "lucky-egg" })
        assertEquals(3L, img.item("lucky-egg")!!["quantity"])
    }

    @Test fun twoRowsOfOneItemKeepTheCountOnTheLargestAndTheOtherNeverGrowsAboveIt() {
        val img = rich()
        img.addItem(5000, "Poke Ball", 5)                   // a second name for the same item (the import takes the larger, 12)
        val st = stateOf(img)
        assertEquals(12, st.count("poke-ball"))
        st.bag["poke-ball"] = 4
        export(st, img)
        assertEquals(4L, img.item("poke-ball")!!["quantity"])
        assertEquals(4L, img.item("Poke Ball")!!["quantity"])   // 5 would have raised the count again on re-import
        st.bag["poke-ball"] = 0
        export(st, img)
        assertNull(img.item("poke-ball")); assertNull(img.item("Poke Ball"))
    }

    @Test fun tmsOfThePhoneAreAddedAsTmRowsAndTheFilesTmRowsStay() {
        val img = rich()
        val st = stateOf(img)
        assertEquals(setOf("thunderbolt"), st.tms)
        st.tms += setOf("slash", "doubleedge")
        st.tms += "notamove"                                // not in the move data: never written
        val e = export(st, img)
        val slash = img.item("slash")!!
        assertEquals(listOf(20163L, 1L, "{\"type\": \"TM\"}", 37L, 1000L, null, null), listOf(slash["id"], slash["quantity"], slash["data"], slash["category_id"], slash["cost"], slash["fling_power"], slash["fling_effect_id"]))
        assertEquals(20038L, img.item("doubleedge")!!["id"])
        assertNull(img.item("notamove"))
        assertEquals(20085L, img.item("thunderbolt")!!["id"])
        assertEquals(6, e.report.items)                     // poke-ball, potion, unknown-thing + 3 TMs
        // A TM that Showdown's file has no number for: uncatalogued.
        st.tms += "bulletseed"
        export(st, img)
        assertTrue(img.item("bulletseed")!!["id"] as Long <= -1)
    }

    // ---- money and Pokédex -------------------------------------------------------------------------------------------

    @Test fun cashIsAPlainIntegerStringAndEqualCashKeepsTheFilesText() {
        val img = rich()
        img.table("config")!!.rows.first { it.values[0] == "trainer.cash" }.values[1] = "12400.0"
        val st = stateOf(img)
        export(st, img)
        assertEquals("12400.0", img.kv("config", "trainer.cash"))          // same value: untouched
        st.cash = 13050
        val e = export(st, img)
        assertEquals("13050", img.kv("config", "trainer.cash"))
        assertEquals(13050, e.report.cash)
        st.cash = -5
        export(st, img)
        assertEquals("0", img.kv("config", "trainer.cash"))
        img.table("config")!!.rows.removeAll { it.values[0] == "trainer.cash" }
        st.cash = 70
        export(st, img)
        assertEquals("70", img.kv("config", "trainer.cash"))
        assertEquals("\"SECRET-TOKEN\"", img.kv("config", "sync.token"))    // other settings stay
        assertEquals("Ash", img.kv("config", "trainer.name"))
    }

    @Test fun pokedexListsKeepTheirTextUntilSomethingIsMissingThenAppendSorted() {
        val img = rich()
        val st = stateOf(img)
        export(st, img)
        assertEquals("[1, 2, 25, 16]", img.kv("user_data", "pokedex_seen"))
        assertEquals("[25, \"1\", 16]", img.kv("user_data", "pokedex_caught"))
        st.seen += setOf(150, 133, 2); st.caught += setOf(133, 3)
        export(st, img)
        assertEquals("[1, 2, 25, 16, 133, 150]", img.kv("user_data", "pokedex_seen"))
        assertEquals("[25, \"1\", 16, 3, 133]", img.kv("user_data", "pokedex_caught"))
        assertEquals("[1]", img.kv("user_data", "todays_shop"))
    }

    @Test fun aFormEntryInTheFilesListCountsAsItsSpeciesAndRegionalFormsAreWrittenAsTheyAre() {
        val img = AnkimonSchema.fresh()
        img.addMon("a", true, json("a", 25))
        img.setTeam("a")
        img.put("user_data", "pokedex_seen", "[25, 10033]")        // Mega form id: counts as species 3
        img.put("user_data", "pokedex_caught", "[25]")
        val st = stateOf(img)
        st.seen += setOf(3, 10091, 19)
        st.caught += 10091
        export(st, img)
        assertEquals("[25, 10033, 19, 10091]", img.kv("user_data", "pokedex_seen")) // 3 is covered by 10033
        assertEquals("[25, 10091]", img.kv("user_data", "pokedex_caught"))
    }

    @Test fun missingPokedexRowsAreCreatedAndAnUnreadableOneIsReplaced() {
        val img = AnkimonSchema.fresh()
        img.addMon("a", true, json("a", 25)); img.setTeam("a")
        img.put("user_data", "pokedex_seen", "garbage")
        val st = stateOf(img)
        st.caught += setOf(26, 25)
        st.seen += 150
        export(st, img)
        assertEquals("[25, 26]", img.kv("user_data", "pokedex_caught"))
        assertEquals("[25, 150]", img.kv("user_data", "pokedex_seen"))
    }

    // ---- a fresh save ------------------------------------------------------------------------------------------------

    private fun playedGame(): GameState {
        val st = GameState(starterChosen = true, cash = 777)
        val a = caught("u-a"); st.mons += a; st.team += a.uid
        val b = TestData.mon(data, 1, 7, listOf("tackle"), "u-b"); st.mons += b; st.team += b.uid
        val c = TestData.mon(data, 16, 3, listOf("tackle"), "u-c"); st.mons += c
        st.bag["poke-ball"] = 5; st.bag["potion"] = 2; st.tms += "slash"
        st.seen += setOf(1, 16, 26, 172); st.caught += setOf(1, 16, 26)
        st.settings.trainerName = "Misty"
        return st
    }

    @Test fun aFreshSaveHoldsTheWholeGame() {
        val img = AnkimonSchema.fresh()
        val st = playedGame()
        val e = export(st, img, fresh = true)
        assertEquals(ExportReport(true, 3, 3, 0, 0, 0, 2, 777, 3, 3), e.report)
        assertEquals(listOf("u-a", "u-b"), img.teamIds())
        assertEquals(listOf("u-a"), img.mains())
        assertEquals("777", img.kv("config", "trainer.cash"))
        assertEquals("Misty", img.kv("config", "trainer.name"))
        assertEquals("true", img.kv("metadata", "base_stats_normalized"))
        assertEquals("[1, 16, 26, 172]", img.kv("user_data", "pokedex_seen"))
        assertEquals("[1, 16, 26]", img.kv("user_data", "pokedex_caught"))
        assertEquals(setOf("poke-ball", "potion", "slash"), img.rowsOf("items").map { it["item_name"] }.toSet())
        assertEquals(4L, img.item("poke-ball")!!["id"])
        assertEquals(17L, img.item("potion")!!["id"])
        assertEquals(20163L, img.item("slash")!!["id"])
        assertTrue(img.rowsOf("pokemon_history").isEmpty())
        assertEquals(AnkimonSchema.fresh().objects, img.objects)
    }

    @Test fun aTrainerNameThatJsonWouldReadAsANumberIsQuoted() {
        for ((name, stored) in listOf("123" to "\"123\"", "true" to "\"true\"", "Ash" to "Ash", "[x" to "[x", "null" to "\"null\"", "A \"b\"" to "A \"b\"")) {
            val img = AnkimonSchema.fresh()
            val st = playedGame(); st.settings.trainerName = name
            export(st, img, fresh = true)
            assertEquals(name, stored, img.kv("config", "trainer.name"))
        }
        val img = AnkimonSchema.fresh()
        val st = playedGame(); st.settings.trainerName = "  "
        export(st, img, fresh = true)
        assertNull(img.kv("config", "trainer.name"))
    }

    @Test fun anEmptyGameMakesAnEmptyButValidFreshSave() {
        val img = AnkimonSchema.fresh()
        val e = export(GameState(), img, fresh = true)
        assertEquals(0, e.report.mons)
        assertEquals(0, e.report.team)
        assertEquals("0", img.kv("config", "trainer.cash"))
        assertTrue(img.mains().isEmpty())
    }

    // ---- Exp. Share holder ----------------------------------------------------------------------------------------------

    @Test fun theExpShareHolderFollowsThePhoneAndAnUnchangedOneKeepsTheFilesText() {
        val img = rich()
        img.put("config", "trainer.xp_share", "\"m-leaf\"")                 // quoted: the file's own text
        val st = stateOf(img)
        assertEquals(monOf(st, "m-leaf").uid, st.settings.xpShareUid)         // the import took it
        val config = img.rowsOf("config")
        export(st, img)
        assertEquals(config, img.rowsOf("config"))                             // same holder: untouched
        st.settings.xpShareUid = monOf(st, "m-spark").uid
        export(st, img)
        assertEquals("m-spark", img.kv("config", "trainer.xp_share"))
        val phone = caught(); st.mons += phone; st.settings.xpShareUid = phone.uid // a phone catch holds it: its uid is the id
        export(st, img)
        assertEquals("u-new", img.kv("config", "trainer.xp_share"))
        assertEquals(1, img.rowsOf("config").count { it["key"] == "trainer.xp_share" })
        st.settings.xpShareUid = null
        export(st, img)
        assertNull(img.kv("config", "trainer.xp_share"))                       // off: the row goes
        export(st, img)
        assertNull(img.kv("config", "trainer.xp_share"))
        st.settings.xpShareUid = monOf(st, "m-bird").uid
        export(st, img)                                                         // no row yet: it is added
        assertEquals("m-bird", img.kv("config", "trainer.xp_share"))
        assertEquals("Ash", img.kv("config", "trainer.name"))                  // other settings stay
        assertEquals("\"SECRET-TOKEN\"", img.kv("config", "sync.token"))
    }

    @Test fun aHolderAnkiGoDoesNotHaveKeepsItsRowUntilThePhoneChoosesAnother() {
        val img = rich()
        img.addMon("m-gen9", false, json("m-gen9", 906, "level" to 5))
        img.put("config", "trainer.xp_share", "m-gen9")
        val st = stateOf(img)
        assertNull(st.settings.xpShareUid)                                     // not in the game: the import sets nothing
        assertTrue(st.mons.none { it.ankimonId == "m-gen9" })
        val config = img.rowsOf("config"); val gen9 = img.dataOf("m-gen9")
        export(st, img)
        assertEquals(config, img.rowsOf("config"))                             // phone has none, file's holder unknown: row stays
        assertEquals(gen9, img.dataOf("m-gen9"))
        st.settings.xpShareUid = monOf(st, "m-leaf").uid
        export(st, img)
        assertEquals("m-leaf", img.kv("config", "trainer.xp_share"))           // the player chose another one
        assertEquals(gen9, img.dataOf("m-gen9"))
    }

    @Test fun ankimonsOrasModeAndAnUnreadableModeAreLeftAlone() {
        for (mode in listOf("oras", "\"oras\"")) {
            val img = rich()
            img.put("config", "trainer.xp_share_mode", mode)
            img.put("config", "trainer.xp_share", "m-leaf")
            val st = stateOf(img)
            assertNull(st.settings.xpShareUid)                                  // everyone shares: no single holder
            val config = img.rowsOf("config")
            st.settings.xpShareUid = monOf(st, "m-spark").uid
            export(st, img)
            assertEquals(config, img.rowsOf("config"))
            st.settings.xpShareUid = null
            export(st, img)
            assertEquals(config, img.rowsOf("config"))
        }
        val img = rich()                                                         // explicit "classic" works like no mode row
        img.put("config", "trainer.xp_share_mode", "classic")
        val st = stateOf(img)
        st.settings.xpShareUid = monOf(st, "m-spark").uid
        export(st, img)
        assertEquals("m-spark", img.kv("config", "trainer.xp_share"))
    }

    @Test fun aFreshSaveGetsTheHolderToo() {
        val st = playedGame()
        val none = AnkimonSchema.fresh()
        export(st, none, fresh = true)
        assertNull(none.kv("config", "trainer.xp_share"))
        st.settings.xpShareUid = "u-b"
        val result = AnkimonExport.build(data, st, null, refs, NOW)
        assertEquals(emptyList<String>(), AnkimonExport.verify(result))
        assertEquals("u-b", readAll(result.bytes).xpShare)
        val back = GameState()
        AnkimonMerge.merge(data, back, readAll(result.bytes), { refs.forms[it] }, true, NOW)
        assertEquals(back.mons.single { it.ankimonId == "u-b" }.uid, back.settings.xpShareUid)
    }

    @Test fun theExpShareHolderRoundTripsThroughExportAndReImportInEveryState() {
        val src = rich()
        src.put("config", "trainer.xp_share", "m-leaf")
        val kept = SqliteWriter.write(src)
        val st = fileState(kept)
        val phone = caught("u-phone"); st.mons += phone; st.seen += phone.species; st.caught += phone.species
        val holders = listOf("m-leaf" to monOf(st, "m-leaf").uid, "m-bird" to monOf(st, "m-bird").uid, "u-phone" to "u-phone", null to null)
        for ((id, uid) in holders) {
            st.settings.xpShareUid = uid
            val result = AnkimonExport.build(data, st, kept, refs, NOW)
            assertEquals(emptyList<String>(), AnkimonExport.verify(result))
            assertEquals(id, readAll(result.bytes).xpShare)
            val saved = SaveCodec.encode(st)
            val rep = AnkimonMerge.merge(data, st, readAll(result.bytes), { refs.forms[it] }, true, NOW)
            assertEquals(id, "nothing new", rep.summary())                       // re-import: nothing new...
            assertEquals(id, saved, SaveCodec.encode(st))                        // ...and nothing changes
            assertTrue(id, result.bytes.contentEquals(AnkimonExport.build(data, st, kept, refs, NOW).bytes)) // twice: same bytes
            val second = AnkimonExport.build(data, st, result.bytes, refs, NOW)  // built on its own output: same again
            assertTrue(id, result.bytes.contentEquals(second.bytes))
        }
    }

    // ---- the import side ---------------------------------------------------------------------------------------------

    @Test fun reImportingAnExportChangesNothingAndMatchesPhoneCatchesByUid() {
        val img = rich()
        val st = stateOf(img)
        val g = gameOf(st)
        monOf(st, "m-spark").let { it.level = 33; it.xp = 12 }
        val m = caught(); st.mons += m; st.seen += m.species; st.caught += m.species
        g.release(monOf(st, "m-bird").uid)
        st.cash = 20000; st.bag["poke-ball"] = 3; st.tms += "slash"
        export(st, img)
        val before = SaveCodec.encode(st)
        val again = AnkimonMerge.merge(data, st, AnkimonSaveReader.parse(tablesOf(img)), { refs.forms[it] }, true, NOW)
        assertEquals("nothing new", again.summary())
        assertEquals(before, SaveCodec.encode(st))
        assertEquals(3, st.mons.size) // spark, leaf, the new one; bird released
        assertEquals(1, st.mons.count { it.uid == "u-new" })
    }

    @Test fun theImportSkipsReleasedIdsExceptForPokemonItStillHas() {
        val img = rich()
        val st = GameState()
        st.releasedAnkimon += setOf("m-bird", "m-leaf")
        val owned = TestData.mon(data, 1, 12, listOf("tackle"), "own-leaf"); owned.ankimonId = "m-leaf"; st.mons += owned
        val r = AnkimonMerge.merge(data, st, AnkimonSaveReader.parse(tablesOf(img)), { null }, false, NOW)
        assertEquals(1, r.added)                                           // only m-spark
        assertEquals(listOf("birdie: released on this phone"), r.skipped)
        assertEquals(2, st.mons.size)
        assertEquals(1, r.unchanged + r.upgraded)                          // m-leaf matched the Pokémon the game still has
    }

    // ---- references --------------------------------------------------------------------------------------------------

    @Test fun refsReadTheDownloadedFilesAndSkipJunk() {
        val r = AnkimonRefs.fromFiles { name ->
            when (name) {
                "items.csv" -> "id,identifier,category_id,cost,fling_power,fling_effect_id\r\n4,poke-ball,34,200,,\r\nx,bad,1,1,,\r\n5,\"quoted\",1,1,,\r\n6,short\r\n,empty,1,1,,\r\n7,potion,27,,,\r\n4,poke-ball,1,1,1,1\r\n"
                "moves.json" -> """{"tackle":{"num":33},"weird":{"num":"x"},"neg":{"num":-1},"list":[1],"str":"s"}"""
                "pokemon.csv" -> "id,identifier,species_id\n1,bulbasaur,1\n10033,venusaur-mega,3\n10034,x,notnum\nbad,line,1\n"
                else -> null
            }
        }
        assertEquals(setOf("poke-ball", "potion"), r.items.keys)
        assertEquals(listOf(4, 34, 200), r.items.getValue("poke-ball").let { listOf(it.id, it.category!!, it.cost!!) })   // first row wins
        assertNull(r.items.getValue("potion").cost)
        assertEquals(mapOf("tackle" to 33), r.moveNums)
        assertEquals(mapOf(10033 to 3), r.forms)
    }

    @Test fun refsToleratesMissingAndBrokenFiles() {
        val none = AnkimonRefs.fromFiles { null }
        assertTrue(none.items.isEmpty() && none.moveNums.isEmpty() && none.forms.isEmpty())
        val bad = AnkimonRefs.fromFiles { "{{{ not what you expect" }
        assertTrue(bad.items.isEmpty() && bad.moveNums.isEmpty() && bad.forms.isEmpty())
        val boom = AnkimonRefs.fromFiles { throw java.io.IOException("disk") }
        assertTrue(boom.items.isEmpty())
        assertTrue(AnkimonRefs.NONE.items.isEmpty())
    }

    // ---- Ankimon's arithmetic ------------------------------------------------------------------------------------------

    @Test fun combatPowerMatchesAnkimonsPythonForKnownCases() {
        // Values computed with business.py's formulas (Python) for the same inputs.
        val base = Stats(45, 49, 49, 65, 65, 45)
        val iv = Stats(31, 20, 10, 0, 5, 31)
        val ev = Stats(4, 252, 0, 0, 0, 252)
        val want = mapOf(1 to 10, 5 to 27, 12 to 147, 30 to 752, 50 to 1681, 100 to 4067, 150 to 5840)
        for ((level, cp) in want) assertEquals("level $level", cp, AnkimonExport.cp(base, iv, ev, level))
        val b2 = Stats(80, 82, 83, 100, 100, 80)
        assertEquals(384, AnkimonExport.cp(b2, Stats(15, 15, 15, 15, 15, 15), Stats.ZERO, 16))
        assertEquals(19476, AnkimonExport.cp(b2, Stats(31, 31, 31, 31, 31, 31), Stats(252, 252, 252, 252, 252, 252), 100))
    }

    // ---- the JSON helper ------------------------------------------------------------------------------------------------

    @Test fun jsonIsWrittenLikePythonAndEditedInPlace() {
        assertEquals("{\"a\": [1, 2, {\"b\": null}], \"c\": true, \"d\": \"x\\\"y\\\\z\\n\\u0001é\u2028\"}", PyJson.dumps(linkedMapOf("a" to listOf(1, 2L, mapOf("b" to null)), "c" to true, "d" to "x\"y\\z\n\u0001é\u2028")))
        assertEquals("\"a\uFFFDb\"", PyJson.quote("a\uD800b"))
        assertEquals("\"\uD83D\uDE00\"", PyJson.quote("\uD83D\uDE00"))
        val text = "  {\"x\" : 1 , \"y\":[1, 2],\"x\":3, \"z\": \"s\"}  "
        val edited = PyJson.edit(text, linkedMapOf("x" to "9", "new" to "\"v\"", "n2" to "[]"))
        assertEquals("  {\"x\" : 9 , \"y\":[1, 2],\"x\":9, \"z\": \"s\", \"new\": \"v\", \"n2\": []}  ", edited)
        assertEquals("{\"a\": 1}", PyJson.edit("{}", linkedMapOf("a" to "1")))
        assertEquals(listOf("1", " \"two\"".trim(), "[3, 4]"), PyJson.elements("[1, \"two\" ,[3, 4]]"))
        assertEquals(emptyList<String>(), PyJson.elements(" [ ] "))
        for (bad in listOf("", "{", "{\"a\"}", "{\"a\":}", "{\"a\":1,}", "[1,]", "{\"a\":01}", "{\"a\":'x'}", "{\"a\":1} x", "{\"a\":NaN}", "{\"a\":\"\t\"}", "{\"a\":\"\\q\"}", "{\"a\":\"\\u12g4\"}", "{a:1}")) {
            assertNull(bad, PyJson.members(bad))
        }
        assertTrue(PyJson.isValue("123")); assertTrue(PyJson.isValue(" true ")); assertTrue(PyJson.isValue("\"s\"")); assertTrue(PyJson.isValue("[1]"))
        assertFalse(PyJson.isValue("Ash")); assertFalse(PyJson.isValue("007")); assertFalse(PyJson.isValue(""))
        val deep = "[".repeat(100) + "]".repeat(100)
        assertFalse(PyJson.isValue(deep))                  // too deep: refused, no stack overflow
        try { PyJson.edit("[1]", mapOf("a" to "1")); fail() } catch (e: IllegalArgumentException) { }
    }

    // ---- real SQLite ------------------------------------------------------------------------------------------------------

    private fun needSqlite() = assumeTrue("sqlite3 is not installed", AnkimonFixture.sqliteAvailable())

    private fun readAll(bytes: ByteArray) = AnkimonSaveReader.read(SqliteReader(bytes))

    private fun write(name: String, bytes: ByteArray): File = File(tmp.root, name).also { it.writeBytes(bytes) }

    private fun fileState(bytes: ByteArray): GameState {
        val st = GameState()
        AnkimonMerge.merge(data, st, readAll(bytes), { refs.forms[it] }, true, NOW)
        st.starterChosen = true
        return st
    }

    @Test fun smallSaveRoundTripThroughRealSqlite() {
        needSqlite()
        val kept = AnkimonFixture.build(tmp.root, "in.db", AnkimonFixture.smallSave()).readBytes()
        val keptCopy = kept.copyOf()
        val st = fileState(kept)
        val g = gameOf(st)
        // play on the phone
        val spark = monOf(st, "aaaa1111-0000-4000-8000-000000000001")
        spark.level = 31; spark.xp = 3; spark.defeats = 14
        val leaf = monOf(st, "aaaa1111-0000-4000-8000-000000000002")
        leaf.nickname = "Vine"
        Progression.evolve(data, st, leaf, data.sp(1).evolutions[0], Random(2), mutableListOf<GameEvent>())
        assertTrue(g.release(monOf(st, "aaaa1111-0000-4000-8000-000000000003").uid).ok)
        val phone = caught("u-phone"); st.mons += phone; st.team += phone.uid; st.seen += 26; st.caught += 26
        st.cash = 99; st.bag["potion"] = 0; st.bag["great-ball"] = 6; st.tms += "slash"; st.seen += 150
        st.bag["linking-cord"] = 2; st.tms += "bulletseed" // no numbers in items.csv / moves.json: Ankimon's "uncatalogued" (negative) ids
        val result = AnkimonExport.build(data, st, kept, refs, NOW)
        assertTrue(kept.contentEquals(keptCopy))                          // the kept file is only read
        assertEquals(emptyList<String>(), AnkimonExport.verify(result))
        val r = result.report
        assertEquals(listOf(false, 3, 1, 2, 0, 1), listOf(r.fresh, r.mons, r.added, r.updated, r.untouched, r.released))

        val out = write("out.db", result.bytes)
        assertEquals("ok", sqlite3(out, "PRAGMA integrity_check;"))
        assertEquals("ok", sqlite3(out, "PRAGMA quick_check;"))
        assertEquals("3", sqlite3(out, "SELECT count(*) FROM captured_pokemon;"))
        // The generated columns and their indexes work in real SQLite.
        assertEquals("Sparkmouse|25|1|31", sqlite3(out, "SELECT name, pokedex_id, shiny, level FROM captured_pokemon WHERE individual_id = 'aaaa1111-0000-4000-8000-000000000001';"))
        assertEquals("leafbud|2", sqlite3(out, "SELECT name, pokedex_id FROM captured_pokemon WHERE individual_id = 'aaaa1111-0000-4000-8000-000000000002';"))
        assertEquals("sparkrat|26|20", sqlite3(out, "SELECT name, pokedex_id, level FROM captured_pokemon WHERE individual_id = 'u-phone';"))
        assertEquals("1", sqlite3(out, "SELECT count(*) FROM captured_pokemon WHERE is_main = 1;"))
        assertEquals("aaaa1111-0000-4000-8000-000000000001", sqlite3(out, "SELECT individual_id FROM captured_pokemon WHERE is_main = 1;"))
        // Everything AnkiGo doesn't own is still there.
        assertEquals("\"SECRET-TOKEN\"", sqlite3(out, "SELECT value FROM config WHERE key = 'sync.token';"))
        assertEquals("1", sqlite3(out, "SELECT achieved FROM badges WHERE badge_id = 'b1';"))
        assertEquals("[1]", sqlite3(out, "SELECT value FROM user_data WHERE key = 'todays_shop';"))
        assertEquals("99", sqlite3(out, "SELECT value FROM config WHERE key = 'trainer.cash';"))
        assertEquals("\"Ash\"", sqlite3(out, "SELECT value FROM config WHERE key = 'trainer.name';"))
        assertEquals("1", sqlite3(out, "SELECT count(*) FROM pokemon_history WHERE individual_id = 'aaaa1111-0000-4000-8000-000000000003';"))
        assertEquals("pokemon_history|1", sqlite3(out, "SELECT name, seq FROM sqlite_sequence;"))
        assertEquals("0", sqlite3(out, "SELECT count(*) FROM items WHERE item_name = 'potion';"))
        assertEquals("1", sqlite3(out, "SELECT quantity FROM items WHERE item_name = 'slash' AND data = '{\"type\": \"TM\"}';"))
        assertEquals("-2|bulletseed\n-1|linking-cord", sqlite3(out, "SELECT id, item_name FROM items WHERE id < 0 ORDER BY id;"))

        // Read it as the import does: nothing lost, the changes present, ids stable.
        val back = readAll(result.bytes)
        assertEquals(3, back.mons.size)
        assertEquals(setOf("aaaa1111-0000-4000-8000-000000000001", "aaaa1111-0000-4000-8000-000000000002", "u-phone"), back.mons.map { it.individualId }.toSet())
        assertEquals(31, back.mons.single { it.individualId.endsWith("1") }.level)
        assertEquals(2, back.mons.single { it.individualId.endsWith("2") }.id)
        assertEquals("Vine", back.mons.single { it.individualId.endsWith("2") }.nickname)
        assertEquals(99, back.cash)
        assertEquals(6, back.items["great-ball"])
        assertTrue("slash" in back.tms && "thunderbolt" in back.tms)
        assertTrue(150 in back.seen)
        // Re-import into the same game: nothing new and the game is unchanged.
        val saved = SaveCodec.encode(st)
        val rep = AnkimonMerge.merge(data, st, back, { refs.forms[it] }, true, NOW)
        assertEquals("nothing new", rep.summary())
        assertEquals(saved, SaveCodec.encode(st))
        // The same game exported again gives the same bytes, and exporting from the export (re-imported on the phone) too.
        assertTrue(result.bytes.contentEquals(AnkimonExport.build(data, st, kept, refs, NOW).bytes))
        val second = AnkimonExport.build(data, st, result.bytes, refs, NOW)
        assertEquals(emptyList<String>(), AnkimonExport.verify(second))
        assertTrue(result.bytes.contentEquals(second.bytes))
        assertEquals(3, second.report.untouched + second.report.added + second.report.updated)
    }

    /** INSERT statements for the given tables of an in-memory image (strings quoted, numbers raw, NULL). */
    private fun insertsOf(img: SqliteImage, vararg tables: String): String = buildString {
        for (t in tables) for (r in img.rowsOf(t)) {
            val cols = r.keys.toList()
            val vals = cols.map { c -> when (val v = r[c]) { null -> "NULL"; is String -> AnkimonFixture.q(v); else -> v.toString() } }
            append("INSERT INTO $t (${cols.joinToString()}) VALUES (${vals.joinToString()});\n")
        }
    }

    @Test fun aRealisticSaveWithEverythingChangedRoundTripsThroughRealSqlite() {
        needSqlite()
        val src = rich()
        src.put("config", "trainer.xp_share", "m-bird")
        val kept = AnkimonFixture.build(tmp.root, "real.db", AnkimonFixture.SCHEMA + "\n" + insertsOf(src, "captured_pokemon", "team", "items", "user_data", "config", "badges")).readBytes()
        val st = fileState(kept)
        val g = gameOf(st)
        monOf(st, "m-spark").let { it.level = 31; it.xp = 9; it.defeats += 2; it.evs = Stats(4, 252, 0, 0, 0, 254); it.nickname = "Bolt" }
        monOf(st, "m-leaf").let { Progression.evolve(data, st, it, data.sp(1).evolutions[0], Random(4), mutableListOf<GameEvent>()); it.level = 16 }
        assertTrue(g.release(monOf(st, "m-bird").uid).ok)
        val phone = caught("u-phone"); phone.heldItem = "lucky-egg"; st.mons += phone; st.team += phone.uid; st.seen += 26; st.caught += 26
        st.cash = 50; st.bag["poke-ball"] = 1; st.bag["potion"] = 9; st.tms += "slash"
        val result = AnkimonExport.build(data, st, kept, refs, NOW)
        val out = write("realistic.db", result.bytes)
        assertEquals("ok", sqlite3(out, "PRAGMA integrity_check;"))
        assertEquals(emptyList<String>(), AnkimonExport.verify(result))
        assertEquals("m-spark", sqlite3(out, "SELECT individual_id FROM captured_pokemon WHERE is_main = 1;"))
        assertEquals("1", sqlite3(out, "SELECT count(*) FROM pokemon_history;"))
        assertEquals("", sqlite3(out, "SELECT value FROM config WHERE key = 'trainer.xp_share';"))
        // Re-import: nothing new.
        val rep = AnkimonMerge.merge(data, st, readAll(result.bytes), { refs.forms[it] }, true, NOW)
        assertEquals("nothing new", rep.summary())
    }

    @Test fun aBiggerSaveWithManyPokemonRoundTrips() {
        needSqlite()
        val sql = StringBuilder(AnkimonFixture.SCHEMA).append('\n')
        for (i in 0 until 400) {
            val num = listOf(1, 16, 19, 25, 54, 92, 133, 150)[i % 8]
            val id = "id-%04d".format(i)
            sql.append(AnkimonFixture.insertMon(id, i == 0, json(
                id, num, "level" to 1 + i % 99, "xp" to i % 7, "nature" to "Bold", "attacks" to listOf("Tackle", "Slash"),
                "nickname" to "Nick $i ポ", "pad" to "x".repeat(300 + i),
            )))
        }
        sql.append("INSERT INTO team (slot_position, individual_id) VALUES (1, 'id-0003'), (2, 'id-0000');\n")
        sql.append("INSERT INTO user_data (key, value) VALUES ('pokedex_seen', '[1]'), ('pokedex_caught', '[1]');\n")
        val kept = AnkimonFixture.build(tmp.root, "big.db", sql.toString()).readBytes()
        val st = fileState(kept)
        for (m in st.mons.take(150)) { m.level = minOf(100, m.level + 1); m.xp = 1 }
        for (i in 0 until 60) st.mons += caught("u-$i")
        val result = AnkimonExport.build(data, st, kept, refs, NOW)
        val out = write("bigout.db", result.bytes)
        assertEquals("ok", sqlite3(out, "PRAGMA integrity_check;"))
        assertEquals("460", sqlite3(out, "SELECT count(*) FROM captured_pokemon;"))
        assertEquals("460", sqlite3(out, "SELECT count(*) FROM captured_pokemon WHERE level >= 1;"))
        assertEquals(400, result.report.untouched + result.report.updated)
        assertEquals(60, result.report.added)
        assertEquals(150, result.report.updated)
        assertEquals(emptyList<String>(), AnkimonExport.verify(result))
    }

    /** The biggest saves Ankimon players have (the import reads at most 20 000 Pokémon): export in seconds, not minutes. */
    @Test fun twentyThousandPokemonAreExportedInSeconds() {
        val src = AnkimonSchema.fresh()
        val t = src.table("captured_pokemon")!!
        val nums = listOf(1, 16, 19, 25, 54, 92, 133, 150)
        for (i in 0 until 20_000) {
            val num = nums[i % 8]
            t.rows += SqlRow(i + 1L, mutableListOf("id-%05d".format(i), if (i == 0) 1L else 0L,
                json("id-%05d".format(i), num, "level" to 1 + i % 99, "xp" to i % 7, "nickname" to "Nick $i ポ", "pad" to "x".repeat(600), "attacks" to listOf("tackle", "slash"))))
        }
        src.table("team")!!.insert(mapOf("slot_position" to 1L, "individual_id" to "id-00000"))
        val kept = SqliteWriter.write(src)
        val st = fileState(kept)
        for (m in st.mons.take(5_000)) { m.level = minOf(100, m.level + 1); m.xp = 0 }
        for (i in 0 until 200) st.mons += caught("u-$i")
        val t0 = System.nanoTime()
        val result = AnkimonExport.build(data, st, kept, refs, NOW)
        val seconds = (System.nanoTime() - t0) / 1e9
        println("T52 export of 20 000 Pokémon: %.1f s, file %d KB".format(seconds, result.bytes.size / 1024))
        assertEquals(20_200, result.report.mons)
        assertEquals(5_000, result.report.updated)
        assertEquals(15_000, result.report.untouched)
        assertEquals(200, result.report.added)
        assertTrue("took $seconds s", seconds < 120)
    }

    @Test fun aFreshSaveThroughRealSqliteReadsBackIntoAnEmptyGame() {
        needSqlite()
        val st = playedGame()
        val result = AnkimonExport.build(data, st, null, refs, NOW)
        assertTrue(result.report.fresh)
        val out = write("fresh.db", result.bytes)
        assertEquals("ok", sqlite3(out, "PRAGMA integrity_check;"))
        assertEquals("u-a|1", sqlite3(out, "SELECT individual_id, is_main FROM captured_pokemon WHERE is_main = 1;"))
        assertEquals("sparkrat|26|20", sqlite3(out, "SELECT name, pokedex_id, level FROM captured_pokemon WHERE individual_id = 'u-a';"))
        assertEquals("Misty", sqlite3(out, "SELECT value FROM config WHERE key = 'trainer.name';"))
        assertEquals("true", sqlite3(out, "SELECT value FROM metadata WHERE key = 'base_stats_normalized';"))
        assertEquals(emptyList<String>(), AnkimonExport.verify(result))
        assertEquals(AnkimonSchema.fresh().objects.map { it.name }, sqlite3(out, "SELECT name FROM sqlite_master ORDER BY rowid;").lines())
        val into = fileState(result.bytes)
        assertEquals(3, into.mons.size)
        assertEquals(777, into.cash)
        assertEquals(2, into.team.size)
        assertEquals("u-a", into.main?.ankimonId)
        assertEquals(5, into.count("poke-ball"))
        assertEquals(setOf("slash"), into.tms)
        assertTrue(into.seen.containsAll(setOf(1, 16, 26, 172)))
    }

    @Test fun anExportOfAnUntouchedGameKeepsEveryRowOfARealFileExactly() {
        needSqlite()
        val kept = AnkimonFixture.build(tmp.root, "in2.db", AnkimonFixture.smallSave()).readBytes()
        val result = AnkimonExport.build(data, fileState(kept), kept, refs, NOW)
        val a = write("a.db", kept); val b = write("b.db", result.bytes)
        for (q in listOf("SELECT individual_id, is_main, data FROM captured_pokemon ORDER BY individual_id;", "SELECT * FROM team ORDER BY 1;",
            "SELECT * FROM items ORDER BY id;", "SELECT * FROM user_data WHERE key <> 'pokedex_caught' ORDER BY key;", "SELECT * FROM config ORDER BY key;",
            "SELECT * FROM badges;")) {
            assertEquals(q, sqlite3(a, q), sqlite3(b, q))
        }
        // The one list that changed: the released-looking species 16 was caught here, so it is appended.
        assertEquals("[25, \"1\"]", sqlite3(a, "SELECT value FROM user_data WHERE key = 'pokedex_caught';"))
        assertEquals("[25, \"1\", 16]", sqlite3(b, "SELECT value FROM user_data WHERE key = 'pokedex_caught';"))
        assertEquals(3, result.report.untouched)
        assertEquals(0, result.report.updated)
    }

    @Test fun aDamagedOrForeignKeptFileStopsTheExportWithAPlainMessage() {
        val st = playedGame()
        for (bytes in listOf(ByteArray(10), ByteArray(5000) { 7 }, "SQLite format 3\u0000".toByteArray() + ByteArray(200))) {
            try { AnkimonExport.build(data, st, bytes, refs, NOW); fail() } catch (e: ExportException) {
                assertEquals("Your kept Ankimon file can't be read. Import your Ankimon save again, then export.", e.message)
            }
        }
    }

    @Test fun aKeptFileThatIsNotAnAnkimonSaveStopsTheExport() {
        needSqlite()
        val other = AnkimonFixture.build(tmp.root, "other.db", "CREATE TABLE notes (id INTEGER PRIMARY KEY, body TEXT); INSERT INTO notes VALUES (1, 'hi');").readBytes()
        try { AnkimonExport.build(data, playedGame(), other, refs, NOW); fail() } catch (e: ExportException) {
            assertEquals("Some data in your Ankimon save can't be written safely, so nothing was exported.", e.message)
        }
    }

    @Test fun verifyCatchesDamageAndWrongContent() {
        val result = AnkimonExport.build(data, playedGame(), null, refs, NOW)
        assertEquals(emptyList<String>(), AnkimonExport.verify(result))
        assertTrue(AnkimonExport.verify(ExportResult(ByteArray(100), result.report)).isNotEmpty())
        assertTrue(AnkimonExport.verify(ExportResult(result.bytes.copyOf(result.bytes.size - 4096), result.report)).isNotEmpty())
        val noMagic = result.bytes.copyOf().also { it[0] = 'X'.code.toByte() }
        assertTrue(AnkimonExport.verify(ExportResult(noMagic, result.report)).isNotEmpty())
        val badPage = result.bytes.copyOf().also { it[16] = 0; it[17] = 7 }
        assertTrue(AnkimonExport.verify(ExportResult(badPage, result.report)).isNotEmpty())
        // A file that is fine but isn't what the report says (checked without the build's own expectations).
        assertTrue(AnkimonExport.verify(ExportResult(result.bytes, result.report.copy(mons = 9))).any { it.contains("Pokémon read back") })
        assertTrue(AnkimonExport.verify(ExportResult(result.bytes, result.report.copy(cash = 1))).any { it.contains("Cash") })
        assertTrue(AnkimonExport.verify(ExportResult(result.bytes, result.report.copy(team = 1))).any { it.contains("Team") })
        assertTrue(AnkimonExport.verify(ExportResult(result.bytes, result.report.copy(caught = 1))).any { it.contains("Pokédex") })
        assertTrue(AnkimonExport.verify(ExportResult(result.bytes, result.report.copy(items = 1))).any { it.contains("Items") })
    }

    @Test fun aFileThatIsNotAnAnkimonSaveDoesNotVerify() {
        needSqlite()
        val report = AnkimonExport.build(data, playedGame(), null, refs, NOW).report
        val other = SqliteWriter.write(SqliteReader(AnkimonFixture.build(tmp.root, "o.db", "CREATE TABLE t (a); INSERT INTO t VALUES (1);").readBytes()).image())
        assertTrue(AnkimonExport.verify(ExportResult(other, report)).any { it.contains("captured_pokemon") })
    }

    /** The checks on the rows the export wrote: the file is made valid SQLite again by our own writer, so only the content is wrong. */
    @Test fun verifyChecksTheRowsItWroteForRequiredKeysAndTypes() {
        val img = AnkimonSchema.fresh()
        val e = export(playedGame(), img, fresh = true)
        fun problems(change: (SqliteImage) -> Unit): List<String> {
            val bad = img.copy().also(change)
            return AnkimonExport.verify(ExportResult(SqliteWriter.write(bad), e.report).also { it.rows = e.rows })
        }
        fun SqliteImage.editData(id: String, from: String, to: String) {
            val r = table("captured_pokemon")!!.rows.single { it.values[0] == id }
            assertTrue(from, (r.values[2] as String).contains(from))
            r.values[2] = (r.values[2] as String).replace(from, to)
        }
        assertEquals(emptyList<String>(), problems { })
        assertTrue(problems { it.editData("u-a", ", \"tier\": \"Normal\"", "") }.any { it.contains("lacks tier") })
        assertTrue(problems { it.editData("u-a", "\"level\": 20", "\"level\": \"x\"") }.any { it.contains("level has a wrong value") })
        assertTrue(problems { it.editData("u-a", "\"level\": 20", "\"level\": 20.5") }.any { it.contains("level has a wrong value") })
        assertTrue(problems { it.editData("u-a", "\"gender\": \"M\"", "\"gender\": \"X\"") }.any { it.contains("gender has a wrong value") })
        assertTrue(problems { it.editData("u-a", "\"shiny\": false", "\"shiny\": 0") }.any { it.contains("shiny has a wrong value") })
        assertTrue(problems { it.editData("u-a", "\"hp\": ", "\"hp\": -1, \"x\": ") }.any { it.contains("hp has a wrong value") })
        assertTrue(problems { it.editData("u-a", "\"nature\": \"Hardy\"", "\"nature\": null") }.any { it.contains("nature is empty") })
        assertTrue(problems { it.editData("u-a", "\"individual_id\": \"u-a\"", "\"individual_id\": \"u-b\"") }.any { it.contains("individual_id differs") })
        try { problems { it.editData("u-a", "{", "this is not json") }; fail() } catch (e: IllegalArgumentException) { } // the writer itself refuses it
        assertTrue(problems { it.table("captured_pokemon")!!.rows.forEach { r -> r.values[1] = 1L } }.any { it.contains("exactly one main") })
        assertTrue(problems { it.table("captured_pokemon")!!.rows.forEach { r -> r.values[1] = 0L } }.any { it.contains("exactly one main") })
        assertTrue(problems { it.table("team")!!.insert(mapOf("slot_position" to 3L, "individual_id" to "ghost")) }.any { it.contains("isn't in the file") })
        assertTrue(problems { it.table("team")!!.rows.last().let { r -> it.table("team")!!.set(r, "slot_position", 4L) } }.any { it.contains("Team slots") })
        assertTrue(problems { it.table("team")!!.insert(mapOf("slot_position" to 3L, "individual_id" to "u-a")) }.any { it.contains("twice") })
    }

    @Test fun anOldKeptFileIsExportedTooWhenAColumnWasAddedLater() {
        needSqlite()
        // Ankimon's own migration: is_main added by ALTER TABLE, so older rows are shorter than the table.
        val noMain = AnkimonFixture.SCHEMA.replace("is_main INTEGER DEFAULT 0,\n", "")
        assertNotEquals(AnkimonFixture.SCHEMA, noMain)
        val sql = noMain + "\nINSERT INTO captured_pokemon (individual_id, data) VALUES ('old-1', ${AnkimonFixture.q(AnkimonFixture.monJson("id" to 25, "name" to "sparkmouse", "level" to 9))});\n" +
            "ALTER TABLE captured_pokemon ADD COLUMN is_main INTEGER DEFAULT 0;\n" +
            "INSERT INTO captured_pokemon (individual_id, is_main, data) VALUES ('new-1', 1, ${AnkimonFixture.q(AnkimonFixture.monJson("id" to 1, "name" to "leafling", "level" to 4))});\n"
        val kept = AnkimonFixture.build(tmp.root, "old.db", sql).readBytes()
        val st = fileState(kept)
        val result = AnkimonExport.build(data, st, kept, refs, NOW)
        val out = write("oldout.db", result.bytes)
        assertEquals("ok", sqlite3(out, "PRAGMA integrity_check;"))
        assertEquals("old-1|0\nnew-1|1", sqlite3(out, "SELECT individual_id, is_main FROM captured_pokemon ORDER BY rowid;"))
    }
}
