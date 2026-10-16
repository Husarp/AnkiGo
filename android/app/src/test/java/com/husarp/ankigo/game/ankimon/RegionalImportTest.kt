package com.husarp.ankigo.game.ankimon

import com.husarp.ankigo.game.GameData
import com.husarp.ankigo.game.GameState
import com.husarp.ankigo.game.TestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ankimon import and regional forms (T37): a form id the game has stays that form; every other form id maps to its base species. */
class RegionalImportTest {
    private val NOW = 1_800_000_000_000L
    private val ratty = TestData.species(19, "Ratty")
    private val sparkmouse = TestData.species(25, "Sparkmouse")
    private val bull = TestData.species(128, "Hornbull")
    private val data: GameData = TestData.data(listOf(
        ratty, sparkmouse, bull,
        ratty.copy(num = 10091, id = "rattyalola", name = "Alolan Ratty", gen = 7, form = "Alola", baseNum = 19, spriteId = "ratty-alola"),
        bull.copy(num = 10250, id = "hornbullpaldeacombat", name = "Paldean Hornbull", gen = 9, form = "Paldea-Combat", baseNum = 128, spriteId = "hornbull-paldeacombat"),
    ))

    private fun am(id: String, species: Int?, name: String? = null) = AnkimonMon(
        individualId = id, isMain = false, id = species, name = name, nickname = null, level = 12, xp = 0, iv = emptyMap(), ev = emptyMap(),
        nature = null, gender = null, ability = null, attacks = emptyList(), shiny = false, heldItem = null, everstone = false,
        friendship = null, capturedDate = null, defeated = null, tier = null,
    )

    private fun save(vararg mons: AnkimonMon, seen: Set<Int> = emptySet(), caught: Set<Int> = emptySet()) =
        AnkimonSave(mons.toList(), emptyList(), emptyMap(), emptyList(), null, seen, caught, emptyList())

    // Ankimon's pokemon.csv-style map: every form id (> 10000) -> base species number
    private val formBase = { n: Int -> mapOf(10091 to 19, 10250 to 128, 10100 to 25, 10092 to 20).get(n) }

    private fun run(s: AnkimonSave, state: GameState = GameState()): Pair<ImportReport, GameState> =
        AnkimonMerge.merge(data, state, s, formBase, useTeam = false, now = NOW) to state

    @Test fun aKnownRegionalFormStaysThatForm() {
        val (r, st) = run(save(am("a", 10091, "Raticate-Alola"), am("b", 10100, "Sparkmouse-Mega")))
        assertEquals(2, r.added)
        val a = st.mons.first { it.ankimonId == "a" }
        assertEquals(10091, a.species)
        assertEquals("Alolan Ratty", a.name)
        assertEquals(25, st.mons.first { it.ankimonId == "b" }.species) // Mega etc.: today's mapping to the base species
        assertTrue(r.skipped.isEmpty())
    }

    @Test fun anUnknownFormIdFallsBackToTheBaseSpecies() {
        // 10092 is a regional-form id in Ankimon but not in this game's data: formBase gives species 20 (not in the data) -> the name decides
        val (r, st) = run(save(am("a", 10092, "Ratty-Alola")))
        assertEquals(19, st.mons.single().species)
        assertEquals("Ratty", st.mons.single().name)
        assertTrue(r.skipped.isEmpty())
    }

    @Test fun pokedexKeepsTheFormAndMapsOtherForms() {
        val (_, st) = run(save(am("a", 10091), seen = setOf(10091, 10100, 10092), caught = setOf(10091, 10250)))
        assertTrue(10091 in st.seen && 10091 in st.caught)
        assertFalse(19 in st.seen) // the form is its own Pokédex entry: it does not mark the base species
        assertTrue(25 in st.seen) // 10100 is no form of this game: its base species
        assertFalse(10092 in st.seen) // not in the data and its base (20) isn't either: nothing
        assertTrue(10250 in st.caught)
    }

    @Test fun aGenNineFormNeedsGenerationNine() {
        val state = GameState().also { it.settings.gens.remove(9) }
        val (r, st) = run(save(am("p", 10250, "Tauros-Paldea-Combat")), state)
        assertTrue(st.mons.isEmpty())
        assertTrue(r.skipped.isEmpty())
        assertEquals(1, r.unsupported.size)
        assertTrue(r.unsupported.single().contains("generation 9"))
        assertEquals(1, st.unsupported.size)
        val (r2, st2) = run(save(am("p", 10250, "Tauros-Paldea-Combat")), GameState().also { it.settings.gens += 9 })
        assertEquals(10250, st2.mons.single().species)
        assertEquals(1, r2.added)
    }

    @Test fun reImportKeepsTheFormAndChangesNothing() {
        val state = GameState()
        run(save(am("a", 10091)), state)
        val (r, _) = run(save(am("a", 10091)), state)
        assertEquals(0, r.added)
        assertEquals(1, r.unchanged + r.upgraded)
        assertEquals(10091, state.mons.single().species)
    }
}
