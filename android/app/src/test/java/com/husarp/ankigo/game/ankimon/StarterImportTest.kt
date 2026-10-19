package com.husarp.ankigo.game.ankimon

import com.husarp.ankigo.game.Achievements
import com.husarp.ankigo.game.Game
import com.husarp.ankigo.game.GameState
import com.husarp.ankigo.game.Metric
import com.husarp.ankigo.game.TestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** R9-W5 (wave-4 edge): an Ankimon import into a game with a starter clears the starter exemption for what the file really has. Made-up species only. */
class StarterImportTest {
    private val data = TestData.world()
    private val NOW = 1_800_000_000_000L

    private fun fresh(): GameState {
        val s = GameState(createdAt = 1L)
        Game(data, s, Random(1)) { NOW }.pickStarter(1)
        s.battle = null // no wild one met: the starter is the only thing in the Pokédex
        s.seen.retainAll(setOf(1)); s.starterSeen.retainAll(setOf(1))
        return s
    }

    private fun am(id: String, species: Int) = AnkimonMon(
        individualId = id, isMain = false, id = species, name = null, nickname = null, level = 10, xp = null, iv = emptyMap(), ev = emptyMap(),
        nature = null, gender = null, ability = null, attacks = emptyList(), shiny = false, heldItem = null, everstone = false,
        friendship = null, capturedDate = null, defeated = null, tier = null,
    )

    private fun save(vararg mons: AnkimonMon, seen: Set<Int> = emptySet(), caught: Set<Int> = emptySet()) =
        AnkimonSave(mons.toList(), emptyList(), emptyMap(), emptyList(), null, seen, caught, emptyList())

    private fun merge(s: GameState, sv: AnkimonSave) = AnkimonMerge.merge(data, s, sv, { null }, false, NOW)
    private fun v(s: GameState, m: Metric) = Achievements.value(m, s, data)

    @Test fun theFilesPokedexMakesTheStarterSpeciesARealFind() {
        val s = fresh()
        assertEquals(0L, v(s, Metric.DEX_CAUGHT))
        assertEquals(0L, v(s, Metric.DEX_SEEN))
        merge(s, save(caught = setOf(1), seen = setOf(1)))
        assertEquals(1L, v(s, Metric.DEX_CAUGHT))
        assertEquals(1L, v(s, Metric.DEX_SEEN))
        assertTrue(s.starterCaught.isEmpty() && s.starterSeen.isEmpty())
        assertEquals(0L, v(s, Metric.CATCHES)) // the starter itself is still a gift
    }

    @Test fun onlySeenInTheFileClearsOnlyTheSighting() {
        val s = fresh()
        merge(s, save(seen = setOf(1)))
        assertEquals(1L, v(s, Metric.DEX_SEEN))
        assertEquals(0L, v(s, Metric.DEX_CAUGHT))
    }

    @Test fun anotherPokemonOfTheStarterSpeciesInTheFileCounts() {
        val s = fresh()
        merge(s, save(am("a1", 1)))
        assertEquals(1L, v(s, Metric.DEX_CAUGHT))
        assertEquals(1L, v(s, Metric.DEX_SEEN))
        assertTrue(s.starterCaught.isEmpty() && s.starterSeen.isEmpty())
    }

    /** The starter went to Ankimon earlier (export) and comes back: its file's Pokédex may list the species only because of it. */
    @Test fun aFileHoldingTheStarterItselfKeepsTheGift() {
        val s = fresh()
        val starter = s.main!!.uid
        merge(s, save(am(starter, 1), caught = setOf(1), seen = setOf(1)))
        assertEquals(0L, v(s, Metric.DEX_CAUGHT))
        assertEquals(0L, v(s, Metric.DEX_SEEN))
        assertEquals(setOf(1), s.starterCaught)
    }

    /** Exported, then released here, then the export imported: the row is skipped (released) but it is still the starter's own file. */
    @Test fun aReleasedStarterInTheFileStillKeepsTheGift() {
        val s = fresh()
        val starter = s.main!!.uid
        s.mons += TestData.mon(data, 19, 5, uid = "other"); s.team += "other"
        s.mons.removeAll { it.uid == starter }; s.team -= starter; s.releasedAnkimon += starter
        merge(s, save(am(starter, 1), caught = setOf(1), seen = setOf(1)))
        assertEquals(setOf(1), s.starterCaught)
        assertEquals(setOf(1), s.starterSeen)
    }

    @Test fun aGameWithoutAStarterIsUntouched() {
        val s = GameState(createdAt = 1L)
        merge(s, save(am("a1", 1), caught = setOf(1, 4), seen = setOf(1, 4, 7)))
        assertTrue(s.starterCaught.isEmpty() && s.starterSeen.isEmpty())
        assertEquals(s.caught.size.toLong(), v(s, Metric.DEX_CAUGHT))
    }
}
