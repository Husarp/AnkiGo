package com.husarp.ankigo.game.save

import com.husarp.ankigo.game.GameState
import com.husarp.ankigo.game.TestData
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** R3-02: the bench-rest fraction (Mon.restFrac, 0..199) is saved per Pokémon; an old save without it loads 0. */
class RestFracSaveTest {
    private val data = TestData.world()

    private fun state(frac: Int): GameState {
        val s = TestData.state(data, 25, 20)
        val bench = TestData.mon(data, 16, 10, uid = "bench")
        bench.restFrac = frac
        s.mons += bench; s.team += bench.uid
        return s
    }

    private fun monJson(json: String, uid: String): JSONObject {
        val a = JSONObject(json).getJSONArray("mons")
        return (0 until a.length()).map { a.getJSONObject(it) }.first { it.getString("uid") == uid }
    }

    @Test fun theFractionRoundTrips() {
        val json = SaveCodec.encode(state(137))
        assertEquals(137, monJson(json, "bench").getInt("restFrac"))
        val back = SaveCodec.decode(json)
        assertEquals(137, back.mon("bench")!!.restFrac)
        assertEquals(0, back.main!!.restFrac)
        assertEquals(json, SaveCodec.encode(back)) // deterministic
    }

    @Test fun anOldSaveWithoutTheFieldLoadsZero() {
        val o = JSONObject(SaveCodec.encode(state(137)))
        val mons = o.getJSONArray("mons")
        for (i in 0 until mons.length()) mons.getJSONObject(i).remove("restFrac")
        assertFalse(o.toString().contains("restFrac"))
        val back = SaveCodec.decode(o.toString())
        assertTrue(back.mons.all { it.restFrac == 0 })
    }

    @Test fun aWildPokemonInABattleKeepsItToo() {
        val s = state(0)
        s.battle = com.husarp.ankigo.game.Battle(TestData.mon(data, 19, 5, uid = "w").also { it.restFrac = 9 }, com.husarp.ankigo.game.Tier.NORMAL)
        assertEquals(9, SaveCodec.decode(SaveCodec.encode(s)).battle!!.wild.restFrac)
    }

    @Test fun outOfRangeValuesAreClamped() {
        val o = JSONObject(SaveCodec.encode(state(5)))
        o.getJSONArray("mons").getJSONObject(1).put("restFrac", 999)
        assertEquals(199, SaveCodec.decode(o.toString()).mon("bench")!!.restFrac)
        o.getJSONArray("mons").getJSONObject(1).put("restFrac", -4)
        assertEquals(0, SaveCodec.decode(o.toString()).mon("bench")!!.restFrac)
    }
}
