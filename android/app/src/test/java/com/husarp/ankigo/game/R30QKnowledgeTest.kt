package com.husarp.ankigo.game

import com.husarp.ankigo.game.save.SaveCodec
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class R30QKnowledgeTest {
    private val data = TestData.world()
    private fun game(outcome: Outcome): Game {
        val state = TestData.state(data)
        val wild = TestData.mon(data, 54, 5, listOf("watergun"), "wild").also { it.hp = 1 }
        state.battle = Battle(wild, Tier.NORMAL, override = outcome)
        return Game(data, state, Random(7)) { 1_800_000_000_000L }.also { it.roundAnswers = 1 }
    }

    @Test fun defeatUnlocksAndSurvivesSaveAndRelease() {
        val game = game(Outcome.DEFEAT)
        assertFalse(SpeciesKnowledge.known(data, game.state, 54))
        val events = game.onAnswer(Answer(1, 3, 1_800_000_000_000L))
        assertTrue(events.any { it is GameEvent.Defeated && it.species == 54 })
        assertEquals(setOf(54), game.state.defeatedSpecies)
        assertFalse(54 in game.state.caught)
        val restored = SaveCodec.decode(SaveCodec.encode(game.state))
        assertTrue(SpeciesKnowledge.known(data, restored, 54))
    }

    @Test fun autoCatchUnlocks() {
        val game = game(Outcome.CATCH)
        assertTrue(game.onAnswer(Answer(1, 3, 1_800_000_000_000L)).any { it is GameEvent.Caught })
        assertTrue(SpeciesKnowledge.known(data, game.state, 54))
        assertTrue(game.state.defeatedSpecies.isEmpty())
        game.state.mons.removeAll { it.species == 54 }
        assertTrue(SpeciesKnowledge.known(data, game.state, 54))
    }

    @Test fun ballCatchUnlocksBeforeDefeat() {
        val game = game(Outcome.DEFEAT)
        game.state.bag["master-ball"] = 1
        assertTrue(game.throwBall("master-ball").any { it is GameEvent.Caught })
        assertTrue(SpeciesKnowledge.known(data, game.state, 54))
    }

    @Test fun failedThrowDoesNotUnlock() {
        val game = game(Outcome.DEFEAT)
        game.state.bag["poke-ball"] = 1
        val wild = game.state.battle!!.wild
        wild.hp = StatCalc.maxHp(data, wild)
        val noCatch = object : Random() {
            private val seeded = Random(7)
            override fun nextBits(bitCount: Int) = if (bitCount == 0) 0 else seeded.nextInt().ushr(32 - bitCount)
            override fun nextInt(until: Int) = if (until == 100) 99 else seeded.nextInt(until)
        }
        val events = Game(data, game.state, noCatch) { 1_800_000_000_000L }.throwBall("poke-ball")
        assertFalse(events.filterIsInstance<GameEvent.BallThrow>().single().success)
        assertFalse(SpeciesKnowledge.known(data, game.state, 54))
    }

    @Test fun oldSaveKeepsCaughtSpeciesKnownWithoutInventingPastDefeats() {
        val state = TestData.state(data)
        state.caught += 54
        state.seen += 92
        state.defeats = 100
        val old = JSONObject(SaveCodec.encode(state)).apply { remove("defeatedSpecies") }
        val restored = SaveCodec.decode(old.toString())
        assertTrue(restored.defeatedSpecies.isEmpty())
        assertTrue(SpeciesKnowledge.known(data, restored, 54))
        assertFalse(SpeciesKnowledge.known(data, restored, 92))
        assertEquals(100, restored.defeats)
    }

    @Test fun caughtOrDefeatedFormUnlocksOtherFormsButNotEvolutions() {
        val form = data.sp(54).copy(num = 10054, baseNum = 54)
        val world = TestData.data(data.species.values.toList() + form)
        for (caught in listOf(true, false)) for (num in listOf(54, 10054)) {
            val state = GameState()
            if (caught) state.caught += num else state.defeatedSpecies += num
            assertTrue(SpeciesKnowledge.known(world, state, 54))
            assertTrue(SpeciesKnowledge.known(world, state, 10054))
            assertFalse(SpeciesKnowledge.known(world, state, 92))
        }
        val state = GameState().also { it.caught += 1 }
        assertFalse(SpeciesKnowledge.known(world, state, 2))
    }

    @Test fun snapshotOwnsDefeatHistoryAndOwnedOldSavesAreKnown() {
        val state = TestData.state(data)
        state.caught.clear()
        assertTrue(SpeciesKnowledge.known(data, state, 25))
        state.defeatedSpecies += 54
        val snapshot = SaveCodec.snapshot(state)
        state.defeatedSpecies += 92
        assertEquals(setOf(54), SaveCodec.decode(snapshot()).defeatedSpecies)
    }
}
