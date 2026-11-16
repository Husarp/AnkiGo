package com.husarp.ankigo.game

import com.husarp.ankigo.game.save.SaveCodec
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class R48UTodayTest {
    @Test fun combinesAnswersAcrossDecksAndCountsFindsRatherThanBagTotals() {
        val s = GameState(day = "2026-11-16", cash = 999)
        StudyStats.record(s, listOf(GameEvent.Counted(1, 0, 1, 0, false), GameEvent.ItemFound("potion", 12), GameEvent.Cash(5, 999, CashKind.ANSWER)))
        StudyStats.record(s, listOf(GameEvent.Counted(3, 0, 2, 1, false), GameEvent.ItemFound("potion", 13), GameEvent.DailyGift("poke-ball", null, 3, 1)))
        StudyStats.record(s, listOf(GameEvent.XpGained("a", "A", 40, 5, 0, 100), GameEvent.XpGained("b", "B", 20, 5, 0, 100)))
        val t = TodaySummary(s, s.day, 120000)
        assertEquals(2, t.reviews); assertEquals("50%", t.correct)
        assertEquals(5, t.log.cash); assertEquals(60, t.log.xp)
        assertEquals(mapOf("potion" to 2, "poke-ball" to 3), t.items)
        val decoded = SaveCodec.decode(SaveCodec.encode(s))
        assertEquals(t.items, decoded.history.getValue(s.day).foundItems)
        assertEquals(120000L, t.studyMillis)
    }

    @Test fun itemOnlyEventsAndTmGiftsSurviveSaveAndOldHistoryStillLoads() {
        val s = GameState(day = "2026-11-16")
        StudyStats.record(s, listOf(GameEvent.ItemFound("potion", 20), GameEvent.DailyGift(null, "tackle", 1, 1)))
        assertEquals(mapOf("potion" to 1, "tm:tackle" to 1), TodaySummary(s, s.day, 0).items)
        val json = JSONObject(SaveCodec.encode(s))
        json.getJSONObject("history").put(s.day, org.json.JSONArray("[10,3,1,0,120,30]"))
        val old = SaveCodec.decode(json.toString())
        assertTrue(old.history.getValue(s.day).foundItems.isEmpty())
        assertEquals("—", TodaySummary(old, s.day, 0).correct)
    }

    @Test fun newGameDayDoesNotReuseYesterdayAndLateCatchesDoNotDoubleCountWins() {
        val s = GameState(day = "2026-11-15", reviewsToday = 9)
        s.history[s.day] = DayLog(reviews = 9, won = 4, caught = 1)
        StudyStats.addLateCatch(s, false)
        assertEquals(2, TodaySummary(s, s.day, 0).defeated)
        val next = TodaySummary(s, "2026-11-16", 0)
        assertEquals(0, next.reviews); assertEquals(0, next.log.cash); assertTrue(next.items.isEmpty())
    }

    @Test fun todayUsesTheConfiguredGameDayBeforeAndAfterReset() {
        val data = TestData.world()
        val s = TestData.state(data, 25, 20, listOf("tackle"))
        s.settings.dayStartHour = 3
        val game = Game(data, s)
        val date = java.time.LocalDate.of(2026, 11, 16)
        val before = date.atTime(2, 59).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val after = date.atTime(3, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        s.day = "2026-11-15"
        s.history[s.day] = DayLog(reviews = 10, cash = 50)
        assertEquals(50, TodaySummary(s, game.dayKey(before), 0).log.cash)
        assertEquals(0, TodaySummary(s, game.dayKey(after), 0).log.cash)
    }

    @Test fun percentagesFollowBothEngineTablesAtEveryStage() {
        assertEquals("+50%", StageText.percent(1))
        assertEquals("−25%", StageText.percent(-1, true))
        assertEquals("−33%", StageText.percent(-1))
        assertEquals("+33%", StageText.percent(1, true))
        for (aim in listOf(false, true)) for (stage in -6..6) {
            val factor = if (aim) BattleEngine.accStageMult(stage) else BattleEngine.stageMult(stage)
            val parsed = StageText.percent(stage, aim).replace("−", "-").removeSuffix("%").toInt()
            assertEquals(kotlin.math.round((factor - 1) * 100).toInt(), parsed)
        }
        assertEquals("+300%", StageText.percent(99)); assertEquals("0%", StageText.percent(0))
    }
}
