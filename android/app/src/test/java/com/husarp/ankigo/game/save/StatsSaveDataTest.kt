package com.husarp.ankigo.game.save

import com.husarp.ankigo.game.DayLog
import com.husarp.ankigo.game.GameState
import com.husarp.ankigo.game.Mon
import com.husarp.ankigo.game.Nature
import com.husarp.ankigo.game.Stats
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** R3-03: the save keeps the new per-day numbers, catches per ball, first-seen dates and the chosen look; old saves load with defaults. */
class StatsSaveDataTest {
    private fun mon(uid: String, look: Int?) = Mon(
        uid = uid, species = 3, name = "Leafking", level = 40, xp = 0, ivs = Stats(1, 2, 3, 4, 5, 6), evs = Stats.ZERO,
        nature = Nature.HARDY, gender = 'M', shiny = false, ability = "A", moves = mutableListOf("tackle"), hp = 10, look = look,
    )

    private fun state(): GameState {
        val s = GameState(day = "2026-10-13")
        s.mons += mon("a", 1); s.mons += mon("b", null)
        s.team += "a"
        s.history["2026-10-12"] = DayLog(100, 7, 2, 1, xp = 1234, cash = 4321, buttons = intArrayOf(1, 20, 30, 40, 9))
        s.history["2026-10-13"] = DayLog(5, 0, 0, 0)
        s.ballCatches["master-ball"] = 2; s.ballCatches["auto"] = 14; s.ballCatches["ultra-ball"] = 1
        s.seenAt[25] = 1_700_000_000_000L; s.seenAt[1] = 1_800_000_123_456L
        return s
    }

    @Test fun historyWritesSeventeenNumbersPerDay() { // R3-14 appended spent and healItems to R3-03's eleven, R4-V11 the four battle numbers
        val h = JSONObject(SaveCodec.encode(state())).getJSONObject("history")
        assertEquals("[100,7,2,1,1234,4321,1,20,30,40,9,0,0,0,0,0,0]", h.getJSONArray("2026-10-12").toString())
        assertEquals("[5,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0]", h.getJSONArray("2026-10-13").toString())
    }

    @Test fun newFieldsRoundTripExactly() {
        val json = SaveCodec.encode(state())
        val b = SaveCodec.decode(json)
        assertEquals(json, SaveCodec.encode(b))
        val d = b.history.getValue("2026-10-12")
        assertEquals(100, d.reviews); assertEquals(7, d.won); assertEquals(2, d.caught); assertEquals(1, d.shinies)
        assertEquals(1234, d.xp); assertEquals(4321, d.cash)
        assertArrayEquals(intArrayOf(1, 20, 30, 40, 9), d.buttons)
        assertEquals(mapOf("master-ball" to 2, "auto" to 14, "ultra-ball" to 1), b.ballCatches)
        assertEquals(mapOf(25 to 1_700_000_000_000L, 1 to 1_800_000_123_456L), b.seenAt)
        assertEquals(1, b.mon("a")!!.look)
        assertNull(b.mon("b")!!.look)
    }

    @Test fun anOldFourNumberHistoryArrayReadsZeros() {
        val o = JSONObject(SaveCodec.encode(state()))
        o.getJSONObject("history").put("2026-10-12", org.json.JSONArray("[100,7,2,1]"))
        o.getJSONObject("history").put("2026-10-11", org.json.JSONArray("[3,1]"))
        val b = SaveCodec.decode(o.toString())
        val d = b.history.getValue("2026-10-12")
        assertEquals(100, d.reviews); assertEquals(1, d.shinies)
        assertEquals(0, d.xp); assertEquals(0, d.cash)
        assertArrayEquals(IntArray(5), d.buttons)
        assertEquals(3, b.history.getValue("2026-10-11").reviews)
        assertEquals(1, b.history.getValue("2026-10-11").won)
        assertEquals(0, b.history.getValue("2026-10-11").caught)
        assertArrayEquals(IntArray(5), b.history.getValue("2026-10-11").buttons)
    }

    @Test fun anOldSaveWithoutTheNewKeysLoadsWithDefaults() {
        val o = JSONObject(SaveCodec.encode(state()))
        o.remove("ballCatches"); o.remove("seenAt")
        for (i in 0 until o.getJSONArray("mons").length()) o.getJSONArray("mons").getJSONObject(i).remove("look")
        val b = SaveCodec.decode(o.toString())
        assertTrue(b.ballCatches.isEmpty())
        assertTrue(b.seenAt.isEmpty())
        assertNull(b.mon("a")!!.look)
        assertEquals(2, b.mons.size)
    }

    @Test fun aBadSeenAtKeyIsSkippedNotFatal() {
        val o = JSONObject(SaveCodec.encode(state()))
        o.getJSONObject("seenAt").put("oops", 5L)
        val b = SaveCodec.decode(o.toString())
        assertEquals(setOf(1, 25), b.seenAt.keys)
    }
}
