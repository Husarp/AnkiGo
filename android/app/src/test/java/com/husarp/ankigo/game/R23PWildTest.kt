package com.husarp.ankigo.game

import com.husarp.ankigo.game.save.SaveCodec
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class R23PWildTest {
    private fun wild(yield: Stats, level: Int): Pair<GameData, Mon> {
        val data = TestData.data(listOf(TestData.species(16, evYield = yield)))
        return data to Encounters.makeWild(data, Encounters.Roll(16, Tier.NORMAL, false), level, Random(8), 0L)
    }

    @Test fun speciesYieldControlsGrowthAndCaps() {
        for (level in 1..100) {
            val (_, m) = wild(Stats(0, 2, 0, 0, 0, 1), level)
            assertEquals(Stats(0, minOf(252, level * 4), 0, 0, 0, level * 2), m.evs)
            assertTrue(m.evs.total <= 510)
        }
        val (_, capped) = wild(Stats(3, 3, 3, 3, 3, 3), 100)
        assertEquals(510, capped.evs.total)
        assertTrue(capped.evs.toList().all { it in 0..252 })
        assertEquals(Stats.ZERO, wild(Stats.ZERO, 100).second.evs)
    }

    @Test fun catchAndSaveKeepWildEvsAndFullHp() {
        val (data, m) = wild(Stats(1, 0, 1, 0, 0, 1), 70)
        val state = GameState()
        state.battle = Battle(wild = m, tier = Tier.NORMAL)
        val restored = SaveCodec.decode(SaveCodec.encode(state))
        val caught = Catching.addToCollection(data, restored, restored.battle!!.wild, Tier.NORMAL, "poke-ball", 9L, mutableListOf())
        assertEquals(m.evs, caught.evs)
        assertEquals(StatCalc.maxHp(data, caught), caught.hp)
        assertEquals(m.evs, SaveCodec.decode(SaveCodec.encode(restored)).mons.single().evs)
    }

    @Test fun oldSavesKeepExistingEvsAndMissingEvsDefaultToZero() {
        val (_, m) = wild(Stats(1, 0, 0, 0, 0, 0), 20)
        m.evs = Stats(4, 8, 12, 16, 20, 24) // old random spread, preserved verbatim
        val state = GameState().also { it.mons += m; it.battle = Battle(wild = m, tier = Tier.NORMAL) }
        val json = JSONObject(SaveCodec.encode(state))
        val restored = SaveCodec.decode(json.toString())
        assertEquals(m.evs, restored.mons.single().evs)
        assertEquals(m.evs, restored.battle!!.wild.evs)
        json.getJSONArray("mons").getJSONObject(0).remove("evs")
        assertEquals(Stats.ZERO, SaveCodec.decode(json.toString()).mons.single().evs)
    }

    @Test fun allMainLevelsUseTheWholeClampedRange() {
        for (main in 1..100) {
            val expected = maxOf(1, main - 1)..minOf(100, main + 5)
            assertEquals(expected.toSet(), (1..500).map { Encounters.wildLevel(main, Random(main * 1000 + it)) }.toSet())
        }
    }
}
