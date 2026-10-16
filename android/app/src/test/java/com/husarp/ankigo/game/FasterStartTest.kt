package com.husarp.ankigo.game

import com.husarp.ankigo.game.save.SaveCodec
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class FasterStartTest {
    private val data = TestData.world()
    /** Real-style level-5 starter: neutral nature/IVs, Tackle plus Growl, versus a normal early-route opponent. */
    private fun answers(level: Int, faster: Boolean, seed: Int): Int {
        val main = TestData.mon(data, 1, level, if (level < 7) listOf("tackle", "growl") else listOf("tackle", "growl", "vinewhip", "absorb"))
        val wild = TestData.mon(data, 16, level)
        val hp = Tuning.wildHp(wild.hp, level, faster)
        wild.hp = hp
        val battle = Battle(wild, Tier.NORMAL, wildMaxHp = hp)
        val rng = Random(seed)
        var rounds = 0
        while (wild.hp > 0 && rounds < 200) {
            BattleEngine.playRound(data, battle, main, 1.0, false, rng)
            rounds++
        }
        assertTrue("battle finished", wild.hp == 0)
        return rounds * Tuning.CARDS_PER_ROUND
    }
    @Test fun levelFiveAverageIsSixToTenAnswersWithoutChangingDefaultRules() {
        val slow = (0 until 1000).map { answers(5, false, it) }.average()
        val fast = (0 until 1000).map { answers(5, true, it) }.average()
        println("Lv5: Ankimon=$slow answers, Faster start=$fast")
        assertTrue("Ankimon average $slow", slow in 15.0..25.0)
        assertTrue("Faster average $fast", fast in 6.0..10.0)
    }
    @Test fun levelFifteenIsIdenticalWithEitherSetting() {
        val slow = (0 until 300).map { answers(15, false, it) }
        val fast = (0 until 300).map { answers(15, true, it) }
        println("Lv15: ${slow.average()} answers with either setting")
        assertEquals(slow, fast)
        assertEquals(30, Tuning.wildHp(30, 15, true))
        assertEquals(12, Tuning.wildHp(30, 14, true))
    }
    @Test fun newEncounterPersistsCapAndCaughtPokemonRegainsNormalStats() {
        val state = TestData.state(data, 1, 5, listOf("tackle", "growl"))
        state.settings.fasterStart = true
        state.nextSpecies = 16
        val game = Game(data, state, Random(7)) { 1_800_000_000_000L }
        val encounter = game.ensureEncounter().filterIsInstance<GameEvent.Encounter>().single()
        val battle = state.battle!!
        assertEquals(Tuning.wildHp(StatCalc.maxHp(data, battle.wild), 5, true), encounter.maxHp)
        assertEquals(encounter.maxHp, encounter.hp)
        val restored = SaveCodec.decode(SaveCodec.encode(state))
        assertEquals(encounter.maxHp, restored.battle!!.maxWildHp(data))
        assertEquals(encounter.maxHp, restored.battle!!.wild.hp)
        assertTrue(restored.settings.fasterStart)
        battle.wild.hp = 1
        repeat(300) { if (state.catches == 0) game.onAnswer(Answer(it.toLong(), 3, 1_800_000_000_000L)) }
        val caught = state.mons.first { it.uid != state.main!!.uid }
        assertEquals(StatCalc.maxHp(data, caught), caught.hp)
    }
    @Test fun oldSavesKeepAnkimonPaceAndTheirExistingBattle() {
        assertFalse(Settings().fasterStart)
        assertFalse(GameState().settings.fasterStart)
        val state = TestData.state(data, 1, 5)
        state.battle = Battle(TestData.mon(data, 16, 5), Tier.NORMAL)
        val old = JSONObject(SaveCodec.encode(state))
        old.getJSONObject("settings").remove("fasterStart")
        old.getJSONObject("battle").remove("wildMaxHp")
        val restored = SaveCodec.decode(old.toString())
        assertFalse(restored.settings.fasterStart)
        assertNull(restored.battle!!.wildMaxHp)
        assertEquals(StatCalc.maxHp(data, restored.battle!!.wild), restored.battle!!.maxWildHp(data))
        old.remove("settings")
        assertFalse(SaveCodec.decode(old.toString()).settings.fasterStart)
    }
    @Test fun oldFasterStartKeyIsIgnoredAndTheNewKeyRoundTrips() {
        val state = TestData.state(data, 1, 5)
        state.settings.fasterStart = true
        // old save: fasterStart true under the old key only -> off
        val old = JSONObject(SaveCodec.encode(state))
        old.getJSONObject("settings").remove("fasterStartV2")
        old.getJSONObject("settings").put("fasterStart", true)
        assertFalse(SaveCodec.decode(old.toString()).settings.fasterStart)
        // new key round-trips both ways and the old key is no longer written
        val json = SaveCodec.encode(state)
        assertFalse(JSONObject(json).getJSONObject("settings").has("fasterStart"))
        assertTrue(SaveCodec.decode(json).settings.fasterStart)
        state.settings.fasterStart = false
        assertFalse(SaveCodec.decode(SaveCodec.encode(state)).settings.fasterStart)
    }
    @Test fun togglingDoesNotResizeAnOngoingFight() {
        val state = TestData.state(data, 1, 5)
        state.settings.fasterStart = true
        val game = Game(data, state, Random(6)) { 1_800_000_000_000L }
        game.ensureEncounter()
        val hp = state.battle!!.wild.hp
        state.settings.fasterStart = false
        game.ensureEncounter()
        assertEquals(hp, state.battle!!.wild.hp)
        state.battle = null
        game.ensureEncounter()
        assertNull(state.battle!!.wildMaxHp)
    }
}
