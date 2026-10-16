package com.husarp.ankigo.game.ankimon

import com.husarp.ankigo.game.GameData
import com.husarp.ankigo.game.GameState
import com.husarp.ankigo.game.TestData
import com.husarp.ankigo.game.save.SaveCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** PLAN 67: Pokémon AnkiGo can't use yet are kept as "Not supported yet" (raw data kept), and become normal ones later. All data is made up. */
class UnsupportedMonTest {
    private val NOW = 1_800_000_000_000L

    private val data: GameData = TestData.data(TestData.world().species.values.toList() + listOf(TestData.species(906, "Futuremon")))
    /** The same game after an update that knows species 4000 ("Newmon") and a form 10500 of Birdie. */
    private val dataLater: GameData = TestData.data(data.species.values.toList() + listOf(TestData.species(4000, "Newmon", gen = 5)))

    private val rawNew = """{"name": "newmon", "id": 4000, "level": 37, "nickname": "Nova", "shiny": true, "attacks": ["Tackle", "Moon Hammer"], "extra": {"x": 1}}"""
    private val rawFuture = """{"name": "futuremon", "id": 906, "level": 9}"""
    private val rawOk = """{"name": "birdie", "id": 16, "level": 5}"""

    private fun rows(): Map<String, List<Map<String, Any?>>> = mapOf(
        "captured_pokemon" to listOf(
            mapOf("individual_id" to "p-ok", "is_main" to 1L, "data" to rawOk),
            mapOf("individual_id" to "p-new", "is_main" to 0L, "data" to rawNew),
            mapOf("individual_id" to "p-future", "is_main" to 0L, "data" to rawFuture),
        ),
        "team" to listOf(
            mapOf("slot_position" to 1L, "individual_id" to "p-ok"),
            mapOf("slot_position" to 2L, "individual_id" to "p-new"),
        ),
    )

    private fun save(keepRaw: Boolean = true) = AnkimonSaveReader.parse(rows(), keepRaw)

    private fun merge(d: GameData, state: GameState, s: AnkimonSave = save(), useTeam: Boolean = true) =
        AnkimonMerge.merge(d, state, s, { null }, useTeam, NOW)

    @Test fun unknownSpeciesAndGenNineAreKeptWithNameLevelAndRawData() {
        val state = GameState()
        val r = merge(data, state)
        assertEquals(1, r.added)
        assertTrue(r.skipped.isEmpty())
        assertEquals(2, r.unsupported.size)
        assertEquals("keeps 2 as Not supported yet", r.summary().substringAfter("adds 1 Pokémon; ").substringBefore(";"))
        val u = state.unsupported.single { it.ankimonId == "p-new" }
        assertEquals(4000, u.speciesId); assertEquals("newmon", u.name); assertEquals("Nova", u.nickname)
        assertEquals(37, u.level); assertTrue(u.shiny)
        assertEquals(rawNew, u.raw) // exactly as stored
        assertEquals(rawFuture, state.unsupported.single { it.ankimonId == "p-future" }.raw)
    }

    @Test fun theyAreNotPokemonOfTheGame() {
        val state = GameState()
        merge(data, state)
        assertEquals(listOf("Birdie"), state.mons.map { it.name })            // can't battle, be compared or released: not in mons
        assertEquals(state.mons.map { it.uid }, state.team.toList())           // an unsupported Pokémon in Ankimon's team doesn't join
        assertFalse(4000 in state.caught || 4000 in state.seen)
        assertFalse(906 in state.caught)
    }

    @Test fun importingTheSameFileAgainKeepsOneEntryAndReportsNothingNew() {
        val state = GameState()
        merge(data, state)
        val before = SaveCodec.encode(state)
        val r2 = merge(data, state)
        assertEquals(0, r2.added)
        assertTrue(r2.unsupported.isEmpty())
        assertEquals("nothing new", r2.summary())
        assertEquals(2, state.unsupported.size)
        assertEquals(before, SaveCodec.encode(state))
    }

    @Test fun aReimportReadWithoutRawRowsDoesNotWipeTheKeptRawData() {
        val state = GameState()
        merge(data, state)
        merge(data, state, save(keepRaw = false))
        assertEquals(rawNew, state.unsupported.single { it.ankimonId == "p-new" }.raw)
    }

    @Test fun theyBecomeNormalPokemonWhenAnUpdateKnowsTheSpecies_onLoad() {
        val state = GameState()
        merge(data, state)
        val packed = SaveCodec.decode(SaveCodec.encode(state)) // a save → load round trip on the phone
        val n = AnkimonMerge.reviveUnsupported(dataLater, packed, { null }, NOW)
        assertEquals(1, n)
        val mon = packed.mons.single { it.ankimonId == "p-new" }
        assertEquals(4000, mon.species); assertEquals(37, mon.level); assertEquals("Nova", mon.nickname); assertTrue(mon.shiny)
        assertEquals(listOf("tackle"), mon.moves) // the move AnkiGo doesn't know is not a move here
        assertTrue(4000 in packed.caught)
        assertEquals(listOf("p-future"), packed.unsupported.map { it.ankimonId }) // gen 9 is still off
        assertEquals(0, AnkimonMerge.reviveUnsupported(dataLater, packed, { null }, NOW)) // nothing twice
        assertEquals(2, packed.mons.size)
    }

    @Test fun genNineComesInWhenTheSettingIsTurnedOn() {
        val state = GameState()
        merge(data, state)
        state.settings.gens += 9
        assertEquals(1, AnkimonMerge.reviveUnsupported(data, state, { null }, NOW))
        assertEquals(906, state.mons.last().species)
        assertEquals(listOf("p-new"), state.unsupported.map { it.ankimonId })
    }

    @Test fun theyBecomeNormalPokemonOnTheNextImport() {
        val state = GameState()
        merge(data, state)
        val r = merge(dataLater, state)
        assertEquals(1, r.added)
        assertEquals(listOf("p-future"), state.unsupported.map { it.ankimonId })
        assertEquals(4000, state.mons.single { it.ankimonId == "p-new" }.species)
        assertTrue(merge(dataLater, state).summary().let { it == "nothing new" || it.startsWith("keeps") }) // the gen 9 one stays kept, nothing is doubled
        assertEquals(2, state.mons.size)
    }

    @Test fun anEntryWithoutRawDataWaitsForTheNextImportAndIsNeverDoubled() {
        val state = GameState()
        merge(data, state, save(keepRaw = false))
        assertNull(state.unsupported.single { it.ankimonId == "p-new" }.raw)
        assertEquals(0, AnkimonMerge.reviveUnsupported(dataLater, state, { null }, NOW)) // can't, no raw data
        merge(dataLater, state, save(keepRaw = true))
        assertEquals(1, state.mons.count { it.ankimonId == "p-new" })
        assertTrue(state.unsupported.none { it.ankimonId == "p-new" })
    }

    @Test fun aReleasedIdIsSkippedNotKept() {
        val state = GameState()
        state.releasedAnkimon += "p-new"
        val r = merge(data, state)
        assertEquals(listOf("newmon: released on this phone"), r.skipped)
        assertTrue(state.unsupported.none { it.ankimonId == "p-new" })
    }

    @Test fun anAlreadyOwnedPokemonWhoseSpeciesIsOffNowStaysAsItIs() {
        val state = GameState()
        state.settings.gens += 9
        merge(data, state)
        assertEquals(2, state.mons.size) // Birdie + Futuremon
        state.settings.gens -= 9
        val r = merge(data, state)
        assertTrue(r.unsupported.isEmpty() && r.skipped.isEmpty())
        assertEquals(2, state.mons.size)
        assertTrue(state.unsupported.none { it.ankimonId == "p-future" })
    }

    @Test fun theCapStopsGrowthAndSaysSo() {
        val state = GameState()
        repeat(AnkimonMerge.MAX_UNSUPPORTED) { i -> state.unsupported += com.husarp.ankigo.game.UnsupportedMon("x$i", 1, "x", null, 1, false, "r", null) }
        val r = merge(data, state)
        assertEquals(AnkimonMerge.MAX_UNSUPPORTED, state.unsupported.size)
        assertEquals(2, r.skipped.size)
    }

    @Test fun theRawTextIsOnlyKeptWhenAsked() {
        assertNull(save(keepRaw = false).mons.first { it.individualId == "p-new" }.raw)
        assertNotNull(save(keepRaw = true).mons.first { it.individualId == "p-new" }.raw)
        val big = mapOf("captured_pokemon" to listOf(mapOf("individual_id" to "big", "data" to "{\"id\": 4000, \"pad\": \"" + "x".repeat(50_000) + "\"}")))
        assertTrue(AnkimonSaveReader.parse(big, true).mons.single().raw!!.length > 50_000)
    }

    // ---- export ----------------------------------------------------------------------------------------------------

    private fun image(): SqliteImage {
        val img = AnkimonSchema.fresh()
        val t = img.table("captured_pokemon")!!
        for (r in rows().getValue("captured_pokemon")) t.insert(r)
        val team = img.table("team")!!
        for (r in rows().getValue("team")) team.insert(r)
        return img
    }

    private fun SqliteImage.dataOf(id: String): List<String> {
        val t = table("captured_pokemon")!!
        return t.rows.filter { t[it, "individual_id"] == id }.map { t[it, "data"] as String }
    }

    private val refs = AnkimonRefs.NONE

    @Test fun exportBackKeepsTheirRowsExactlyAndDoesNotDoubleThem() {
        val state = GameState()
        val tables = rows()
        merge(data, state, AnkimonSaveReader.parse(tables, true))
        state.starterChosen = true
        val img = image()
        val e1 = AnkimonExport.edit(data, state, img, refs, NOW, false)
        assertEquals(listOf(rawNew), img.dataOf("p-new"))
        assertEquals(listOf(rawFuture), img.dataOf("p-future"))
        assertEquals(3, img.table("captured_pokemon")!!.rows.size)
        assertEquals(0, e1.report.added)
        AnkimonExport.edit(data, state, img, refs, NOW, false) // and again
        assertEquals(listOf(rawNew), img.dataOf("p-new"))
        assertEquals(3, img.table("captured_pokemon")!!.rows.size)
    }

    @Test fun exportRestoresARowTheFileLostFromTheRawDataAndOnlyOnce() {
        val state = GameState()
        merge(data, state)
        state.starterChosen = true
        val fresh = AnkimonSchema.fresh()
        AnkimonExport.edit(data, state, fresh, refs, NOW, true)
        assertEquals(listOf(rawNew), fresh.dataOf("p-new"))
        assertEquals(listOf(rawFuture), fresh.dataOf("p-future"))
        assertEquals(1, fresh.dataOf("p-ok").size.coerceAtLeast(1))
        AnkimonExport.edit(data, state, fresh, refs, NOW, true)
        assertEquals(1, fresh.dataOf("p-new").size)
        assertEquals(1, fresh.dataOf("p-future").size)
    }

    @Test fun exportBuildsAFileThatPassesItsOwnChecks() {
        val state = GameState()
        merge(data, state)
        state.starterChosen = true
        val result = AnkimonExport.build(data, state, null, refs, NOW)
        assertTrue(AnkimonExport.verify(result).isEmpty())
        val back = AnkimonSaveReader.read(SqliteReader(result.bytes))
        assertEquals(setOf("p-ok", "p-new", "p-future"), back.mons.map { it.individualId }.toSet())
        assertEquals(rawNew, back.mons.single { it.individualId == "p-new" }.raw)
    }

    @Test fun aMoveAnkiGoDoesntKnowStaysOnTheRowWhenMovesChange() {
        val state = GameState()
        merge(dataLater, state)
        state.starterChosen = true
        val mon = state.mons.single { it.ankimonId == "p-new" }
        assertEquals(listOf("tackle"), mon.moves)
        mon.moves += "growl"
        val img = image()
        AnkimonExport.edit(dataLater, state, img, refs, NOW, false)
        val attacks = org.json.JSONObject(img.dataOf("p-new").single()).getJSONArray("attacks")
        assertEquals(listOf("tackle", "growl", "Moon Hammer"), (0 until attacks.length()).map { attacks.getString(it) })
        assertTrue(org.json.JSONObject(img.dataOf("p-new").single()).getJSONObject("extra").getInt("x") == 1) // unknown members stay
    }
}
