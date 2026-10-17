package com.husarp.ankigo.game.save

import com.husarp.ankigo.game.DayLog
import com.husarp.ankigo.game.GameState
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/** R3-14: the history array grows to 13 numbers (spent, healItems after the buttons); shorter arrays read 0. */
class SpentHealSaveTest {
    private fun state(): GameState {
        val s = GameState(day = "2026-10-13")
        s.history["2026-10-12"] = DayLog(100, 7, 2, 1, xp = 1234, cash = 4321, buttons = intArrayOf(1, 20, 30, 40, 9), spent = 2600, healItems = 4)
        s.history["2026-10-13"] = DayLog(5, 0, 0, 0)
        return s
    }

    @Test fun spentAndHealItemsAreWrittenInOrder() {
        val h = JSONObject(SaveCodec.encode(state())).getJSONObject("history")
        assertEquals("[100,7,2,1,1234,4321,1,20,30,40,9,2600,4,0,0,0,0]", h.getJSONArray("2026-10-12").toString())
    }

    @Test fun spentAndHealItemsRoundTrip() {
        val json = SaveCodec.encode(state())
        val b = SaveCodec.decode(json)
        assertEquals(json, SaveCodec.encode(b))
        val d = b.history.getValue("2026-10-12")
        assertEquals(2600, d.spent); assertEquals(4, d.healItems)
        assertEquals(1234, d.xp); assertEquals(4321, d.cash); assertArrayEquals(intArrayOf(1, 20, 30, 40, 9), d.buttons)
        assertEquals(0, b.history.getValue("2026-10-13").spent)
    }

    @Test fun anElevenNumberArrayFromR3_03ReadsZeros() {
        val o = JSONObject(SaveCodec.encode(state()))
        o.getJSONObject("history").put("2026-10-12", JSONArray("[100,7,2,1,1234,4321,1,20,30,40,9]"))
        val d = SaveCodec.decode(o.toString()).history.getValue("2026-10-12")
        assertEquals(0, d.spent); assertEquals(0, d.healItems)
        assertEquals(1234, d.xp); assertEquals(4321, d.cash); assertArrayEquals(intArrayOf(1, 20, 30, 40, 9), d.buttons)
    }

    @Test fun anOldFourNumberArrayStillLoads() {
        val o = JSONObject(SaveCodec.encode(state()))
        o.getJSONObject("history").put("2026-10-12", JSONArray("[100,7,2,1]"))
        val d = SaveCodec.decode(o.toString()).history.getValue("2026-10-12")
        assertEquals(100, d.reviews); assertEquals(1, d.shinies)
        assertEquals(0, d.xp); assertEquals(0, d.spent); assertEquals(0, d.healItems)
        assertArrayEquals(IntArray(5), d.buttons)
    }

    @Test fun aSaveWithSpentRoundTripsThroughAnotherEncode() { // spent and healItems survive a second save
        val once = SaveCodec.decode(SaveCodec.encode(state()))
        val twice = SaveCodec.decode(SaveCodec.encode(once))
        assertEquals(2600, twice.history.getValue("2026-10-12").spent)
        assertEquals(4, twice.history.getValue("2026-10-12").healItems)
    }
}
