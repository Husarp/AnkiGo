package com.husarp.ankigo.game.save

import com.husarp.ankigo.game.DayLog
import com.husarp.ankigo.game.GameState
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * R4-V11: the history array grows to 17 numbers (dmgDealt, dmgTaken, dmgBlocked, misses after healItems); every existing position
 * stays, a shorter array (Round 3's 13, R3-03's 11, the oldest 4) reads 0 for the missing ones.
 */
class BattleHistorySaveTest {
    private fun state(): GameState {
        val s = GameState(day = "2026-10-13")
        s.history["2026-10-12"] = DayLog(
            100, 7, 2, 1, xp = 1234, cash = 4321, buttons = intArrayOf(1, 20, 30, 40, 9), spent = 2600, healItems = 4,
            dmgDealt = 5500, dmgTaken = 3100, dmgBlocked = 640, misses = 17,
        )
        s.history["2026-10-13"] = DayLog(5, 0, 0, 0)
        return s
    }

    private fun withArray(day: String, array: String): DayLog {
        val o = JSONObject(SaveCodec.encode(state()))
        o.getJSONObject("history").put(day, JSONArray(array))
        return SaveCodec.decode(o.toString()).history.getValue(day)
    }

    @Test fun seventeenNumbersAreWrittenInOrderWithEveryOldPositionKept() {
        val h = JSONObject(SaveCodec.encode(state())).getJSONObject("history")
        assertEquals("[100,7,2,1,1234,4321,1,20,30,40,9,2600,4,5500,3100,640,17]", h.getJSONArray("2026-10-12").toString())
        assertEquals("[5,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0]", h.getJSONArray("2026-10-13").toString())
    }

    @Test fun theFourBattleNumbersRoundTrip() {
        val json = SaveCodec.encode(state())
        val b = SaveCodec.decode(json)
        assertEquals(json, SaveCodec.encode(b))
        val d = b.history.getValue("2026-10-12")
        assertEquals(5500, d.dmgDealt); assertEquals(3100, d.dmgTaken); assertEquals(640, d.dmgBlocked); assertEquals(17, d.misses)
        assertEquals(2600, d.spent); assertEquals(4, d.healItems); assertEquals(1234, d.xp); assertEquals(4321, d.cash)
        assertArrayEquals(intArrayOf(1, 20, 30, 40, 9), d.buttons)
        val twice = SaveCodec.decode(SaveCodec.encode(b))
        assertEquals(5500, twice.history.getValue("2026-10-12").dmgDealt)
        assertEquals(0, twice.history.getValue("2026-10-13").dmgDealt)
    }

    @Test fun aThirteenNumberRound3ArrayLoadsWithZeros() {
        val d = withArray("2026-10-12", "[100,7,2,1,1234,4321,1,20,30,40,9,2600,4]")
        assertEquals(0, d.dmgDealt); assertEquals(0, d.dmgTaken); assertEquals(0, d.dmgBlocked); assertEquals(0, d.misses)
        assertEquals(2600, d.spent); assertEquals(4, d.healItems); assertEquals(1234, d.xp)
    }

    @Test fun anElevenAndAFourNumberArrayLoadWithZeros() {
        val eleven = withArray("2026-10-12", "[100,7,2,1,1234,4321,1,20,30,40,9]")
        assertEquals(0, eleven.spent); assertEquals(0, eleven.dmgDealt); assertEquals(0, eleven.misses)
        assertEquals(1234, eleven.xp)
        val four = withArray("2026-10-12", "[100,7,2,1]")
        assertEquals(100, four.reviews); assertEquals(1, four.shinies)
        assertEquals(0, four.dmgTaken); assertEquals(0, four.dmgBlocked)
    }
}
