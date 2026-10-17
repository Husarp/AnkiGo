package com.husarp.ankigo.game.save

import com.husarp.ankigo.game.Achievements
import com.husarp.ankigo.game.AchievementTable
import com.husarp.ankigo.game.DayLog
import com.husarp.ankigo.game.GameEvent
import com.husarp.ankigo.game.GameState
import com.husarp.ankigo.game.Metric
import com.husarp.ankigo.game.TestData
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** R4-03: counters, achievement tiers, badge dates and the tracking start are saved; an old save loads, is backfilled and starts quiet. */
class AchievementsSaveTest {
    private val data = TestData.world()
    private val NOW = 1_791_892_800_000L

    private fun played(): GameState {
        val s = TestData.state(data, 25, 20, listOf("tackle"))
        s.totalReviews = 5_000
        s.settings.dailyAverage = 50
        s.history["2026-10-10"] = DayLog(120, 5, 2, 1, xp = 900, cash = 700, buttons = intArrayOf(0, 10, 20, 60, 30), spent = 400, healItems = 3)
        s.history["2026-10-11"] = DayLog(40, 1, 0, 0, xp = 90, cash = 200, buttons = intArrayOf(0, 5, 5, 25, 5), spent = 0, healItems = 1)
        s.history["2026-10-12"] = DayLog(0)
        s.history["2026-10-13"] = DayLog(60, 2, 1, 2, xp = 500, cash = 330, buttons = intArrayOf(0, 3, 7, 40, 10), spent = 100, healItems = 0)
        s.bestStreakDays = 2
        s.ballCatches["auto"] = 9; s.ballCatches["great-ball"] = 2; s.ballCatches["poke-ball"] = 3
        return s
    }

    /** The save as an older version wrote it: none of the four new keys. */
    private fun oldSave(s: GameState): String {
        val o = JSONObject(SaveCodec.encode(s))
        for (k in listOf("counters", "achTiers", "badgeAt", "achSince")) o.remove(k)
        return o.toString()
    }

    @Test fun theNewKeysRoundTripExactly() {
        val s = played()
        s.counters[Metric.DAMAGE_DEALT.name] = 4_000_000_000L
        s.counters[Metric.MISSES.name] = 7
        s.counters["FUTURE_COUNTER"] = 3 // a key this version doesn't know stays
        s.achTiers["answers"] = mutableListOf(1_700_000_000_000L, 1_700_000_100_000L)
        s.achTiers["catches"] = mutableListOf()
        s.achTiers["future-row"] = mutableListOf(5L)
        s.badgeAt[7] = 1_700_000_000_123L; s.badgeAt[1] = 9L
        s.achSince = 1_690_000_000_000L
        val json = SaveCodec.encode(s)
        val back = SaveCodec.decode(json)
        assertEquals(json, SaveCodec.encode(back)) // deterministic: encode -> decode -> encode
        assertEquals(s.counters, back.counters)
        assertEquals(s.achTiers, back.achTiers)
        assertEquals(s.badgeAt, back.badgeAt)
        assertEquals(1_690_000_000_000L, back.achSince)
        assertEquals(listOf(1_700_000_000_000L, 1_700_000_100_000L), back.achTiers["answers"])
        assertEquals(emptyList<Long>(), back.achTiers["catches"]) // "looked at, nothing reached" is kept
    }

    @Test fun savedDatesOfADroppedRowAreKeptAndNeverShown() {
        // R4-V2b: "combo", "good" and "easy" left the list; a save that has dates for them keeps them, round-trips and loads fine
        val s = played()
        s.achTiers["combo"] = mutableListOf(1_700_000_000_000L, 1_700_000_100_000L)
        s.achTiers["good"] = mutableListOf(1_700_000_200_000L)
        s.achTiers["easy"] = mutableListOf()
        val json = SaveCodec.encode(s)
        val back = SaveCodec.decode(json)
        assertEquals(json, SaveCodec.encode(back))
        assertEquals(mutableListOf(1_700_000_000_000L, 1_700_000_100_000L), back.achTiers["combo"])
        Achievements.check(back, data, NOW, mutableListOf(), silent = true)
        assertEquals(mutableListOf(1_700_000_200_000L), back.achTiers["good"]) // check() leaves unknown ids alone
        val shown = Achievements.view(back, data).map { it.id }
        assertTrue(shown.none { it in listOf("combo", "good", "easy") })
        assertEquals(AchievementTable.ROWS.map { it.id }, shown)
    }

    @Test fun theKeysAreAlwaysWritten() {
        val o = JSONObject(SaveCodec.encode(GameState()))
        assertEquals(0, o.getJSONObject("counters").length())
        assertEquals(0, o.getJSONObject("achTiers").length())
        assertEquals(0, o.getJSONObject("badgeAt").length())
        assertEquals(0L, o.getLong("achSince"))
    }

    @Test fun aSaveWithCountersIsNotBackfilledAgain() {
        val s = played() // has history, but its (empty) counters are the real ones
        val back = SaveCodec.decode(SaveCodec.encode(s))
        assertTrue(back.counters.isEmpty())
    }

    @Test fun anOldSaveIsBackfilledFromItsHistory() {
        val s = played()
        val back = SaveCodec.decode(oldSave(s))
        fun c(m: Metric) = back.counters[m.name] ?: 0L
        assertEquals(700L + 200 + 330, c(Metric.MONEY_EARNED))
        assertEquals(900L + 90 + 500, c(Metric.TOTAL_XP))
        assertEquals(400L + 100, c(Metric.MONEY_SPENT))
        assertEquals(3L + 1, c(Metric.ITEMS_USED))
        assertEquals(30L + 5 + 10, c(Metric.EASY_PRESSES))
        assertEquals(60L + 25 + 40, c(Metric.GOOD_PRESSES))
        assertEquals(1L + 0 + 2, c(Metric.SHINIES))
        assertEquals(3L, c(Metric.DAYS_STUDIED))        // 10th, 11th, 13th; the 12th had no answers
        assertEquals(2L, c(Metric.GOALS_REACHED))       // 120 and 60 reviews are at the goal of 50; 40 is not
        assertEquals(5L, c(Metric.BALLS_THROWN))        // 2 + 3 thrown-ball catches, not the auto rule's 9
        for (m in listOf(Metric.DAMAGE_DEALT, Metric.DAMAGE_TAKEN, Metric.DAMAGE_BLOCKED, Metric.MISSES, Metric.CRITS, Metric.COMEBACKS, Metric.TMS_TAUGHT, Metric.ITEMS_FOUND)) {
            assertEquals(m.name, 0L, c(m)) // nothing to start from
        }
        // And the derived ones are simply read from the save.
        assertEquals(5_000L, Achievements.value(Metric.ANSWERS, back, data))
    }

    @Test fun theGoalCountAlsoRespectsTheBestStreak() {
        val s = played()
        s.bestStreakDays = 6 // 6 goal days in a row happened, though only some are still in the history
        val back = SaveCodec.decode(oldSave(s))
        assertEquals(6L, back.counters[Metric.GOALS_REACHED.name])
    }

    @Test fun shiniesBackfillNeverFallBelowTheOnesOwned() {
        val s = played()
        repeat(5) { s.mons += TestData.mon(data, 19, 5, uid = "sh$it").also { m -> m.shiny = true } }
        val back = SaveCodec.decode(oldSave(s))
        assertEquals(5L, back.counters[Metric.SHINIES.name]) // history says 3, five are owned
    }

    @Test fun theFirstCheckOfAnOldSaveIsQuietAndDatesWhatIsReached() {
        val back = SaveCodec.decode(oldSave(played()))
        assertEquals(0L, back.achSince)
        assertTrue(back.achTiers.isEmpty())
        val events = mutableListOf<GameEvent>()
        Achievements.check(back, data, NOW, events) // totalReviews 5 000 is Common (500) and Rare (5 000)
        assertTrue(events.none { it is GameEvent.AchievementReached })
        assertEquals(listOf(NOW, NOW), back.achTiers["answers"])
        assertEquals(NOW, back.achSince)
        assertEquals(AchievementTable.ROWS.map { it.id }.toSet(), back.achTiers.keys)
        // from then on a new tier is loud
        back.totalReviews = 25_000
        val later = mutableListOf<GameEvent>()
        Achievements.check(back, data, NOW + 1, later)
        assertEquals(listOf<GameEvent>(GameEvent.AchievementReached("answers", 2, "Answers given", "Epic")), later.filter { it is GameEvent.AchievementReached && it.id == "answers" })
    }

    @Test fun anOldSaveBackfillsOnceThenKeepsItsCounters() {
        val first = SaveCodec.decode(oldSave(played()))
        first.counters[Metric.MISSES.name] = 4
        val json = SaveCodec.encode(first)
        val second = SaveCodec.decode(json)
        assertEquals(json, SaveCodec.encode(second))
        assertEquals(first.counters, second.counters) // the backfill did not run a second time (it would add on top)
    }

    @Test fun anOldBadgeStaysAndTheOneOffIsReachedByIt() {
        val s = played()
        s.badges += setOf(1, 7)
        val back = SaveCodec.decode(oldSave(s))
        assertEquals(setOf(1, 7), back.badges)
        assertTrue(back.badgeAt.isEmpty())
        val events = mutableListOf<GameEvent>()
        Achievements.check(back, data, NOW, events)
        assertEquals(listOf(NOW), back.achTiers["first-catch"])
        assertEquals(1, Achievements.view(back, data).single { it.id == "first-catch" }.value.toInt())
        assertTrue(back.badgeAt.isEmpty()) // a quiet look does not invent a date for an old badge
    }

    @Test fun unknownAchievementIdsSurviveARoundTripAndAreNotShown() {
        val s = played()
        s.achTiers["gone-row"] = mutableListOf(11L, 22L)
        val back = SaveCodec.decode(SaveCodec.encode(s))
        assertEquals(listOf(11L, 22L), back.achTiers["gone-row"])
        Achievements.check(back, data, NOW, mutableListOf())
        assertEquals(listOf(11L, 22L), SaveCodec.decode(SaveCodec.encode(back)).achTiers["gone-row"])
        assertTrue(Achievements.view(back, data).none { it.id == "gone-row" })
    }

    @Test fun badNumbersReadAsZeroInsteadOfBreakingTheSave() {
        val o = JSONObject(SaveCodec.encode(played()))
        o.put("counters", JSONObject().put("MISSES", "lots").put("CRITS", 5))
        o.put("achTiers", JSONObject().put("answers", org.json.JSONArray().put(7).put("x")).put("junk", "not a list"))
        o.put("badgeAt", JSONObject().put("7", "now").put("zz", 5))
        val back = SaveCodec.decode(o.toString())
        assertEquals(0L, back.counters["MISSES"])
        assertEquals(5L, back.counters["CRITS"])
        assertEquals(listOf(7L, 0L), back.achTiers["answers"])
        assertFalse("junk" in back.achTiers)
        assertEquals(mapOf(7 to 0L), back.badgeAt) // "zz" is not a badge id
    }

    @Test fun theSchemaStaysAndOtherFieldsAreUntouched() {
        val s = played()
        val back = SaveCodec.decode(oldSave(s))
        assertEquals(GameState.SCHEMA, back.schema)
        assertEquals(s.totalReviews, back.totalReviews)
        assertEquals(s.history.keys, back.history.keys)
        assertEquals(s.mons.map { it.uid }, back.mons.map { it.uid })
    }
}
