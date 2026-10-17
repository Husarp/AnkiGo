package com.husarp.ankigo.game

import com.husarp.ankigo.game.save.SaveCodec
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

/** R4 fix batch A1 (lifetime totals never go down), A2 (tiers stored before anything shows them), A3 (inferred dates). Made-up species only. */
class AchievementsFixTest {
    private val data = TestData.world()
    private val NOW = 1_791_892_800_000L
    private val DAY = 24L * 60 * 60 * 1000
    private val UTC = ZoneId.of("UTC")

    private fun state() = TestData.state(data, 25, 20, listOf("tackle")) // Sparkmouse Lv 20 = 8 000 XP

    private fun counter(s: GameState, m: Metric) = s.counters[m.name] ?: 0L
    private fun view(s: GameState, id: String) = Achievements.view(s, data).single { it.id == id }
    private fun check(s: GameState, now: Long = NOW, silent: Boolean = false) =
        mutableListOf<GameEvent>().also { Achievements.check(s, data, now, it, silent) }

    // ---- A1: the lifetime totals are ratcheted into the counters ----

    @Test fun releasingAPokemonDoesNotLowerTheLifetimeXp() {
        val s = state() // an old save: no XP counter at all
        s.mons += TestData.mon(data, 19, 30, uid = "old") // 27 000 XP
        Achievements.onReady(s, data, NOW)
        assertEquals(35_000L, counter(s, Metric.TOTAL_XP)) // the baseline is stored, not only worked out
        s.mons.removeAll { it.uid == "old" }              // released
        assertEquals(35_000L, Achievements.value(Metric.TOTAL_XP, s, data))
        check(s, NOW + 1)
        assertEquals(35_000L, view(s, "total-xp").value)
    }

    @Test fun xpEventsAddOnTopOfTheStoredBaseline() {
        val s = state()
        s.mons += TestData.mon(data, 19, 30, uid = "old")
        check(s)
        Achievements.count(s, listOf(GameEvent.XpGained("main", "Sparkmouse", 50, 20, 0, 100)))
        assertEquals(35_050L, counter(s, Metric.TOTAL_XP))
        s.mons.removeAll { it.uid == "old" }
        check(s, NOW + 1)
        assertEquals(35_050L, Achievements.value(Metric.TOTAL_XP, s, data))
    }

    @Test fun importedShiniesStayWhenYouReleaseThem() {
        val s = state()
        s.counters[Metric.SHINIES.name] = 1
        repeat(3) { s.mons += TestData.mon(data, 19, 5, uid = "sh$it").also { m -> m.shiny = true } } // an import brought three
        check(s, silent = true)
        assertEquals(3L, counter(s, Metric.SHINIES))
        s.mons.removeAll { it.uid.startsWith("sh") }
        assertEquals(3L, Achievements.value(Metric.SHINIES, s, data))
        s.mons += TestData.mon(data, 19, 5, uid = "fresh").also { it.shiny = true }
        Achievements.count(s, listOf(GameEvent.Caught("fresh", 19, "Ratty", 5, true, "poke-ball", true, false)))
        assertEquals(4L, Achievements.value(Metric.SHINIES, s, data)) // +1 on top of the baseline, not max(1, owned)
    }

    @Test fun theRatchetNeverLowersACounter() {
        val s = state()
        s.counters[Metric.TOTAL_XP.name] = 99_999 // a released Pokémon's XP was counted too
        s.counters[Metric.SHINIES.name] = 9
        check(s)
        assertEquals(99_999L, counter(s, Metric.TOTAL_XP))
        assertEquals(9L, counter(s, Metric.SHINIES))
    }

    @Test fun onlyTheTwoTotalsAreRatcheted() {
        val s = state()
        check(s)
        assertEquals(setOf(Metric.TOTAL_XP.name), s.counters.keys) // 8 000 XP; every other counter stays absent, no shinies owned
    }

    // ---- A2: the first quiet look when the game becomes ready ----

    @Test fun tiersAreStoredBeforeAReleaseCanTakeThemAway() {
        val s = state()
        s.mons += TestData.mon(data, 19, 80, uid = "big") // highest level 80: Common, Rare, Epic, Legendary (10, 25, 50, 75)
        assertEquals(3, view(s, "highest-level").tier) // what a page opened right after loading would show
        Achievements.onReady(s, data, NOW)
        assertEquals(4, s.achTiers["highest-level"]!!.size)
        s.mons.removeAll { it.uid == "big" }
        val v = view(s, "highest-level")
        assertEquals(3, v.tier)
        assertEquals("Legendary", v.tierName)
        assertEquals(20L, v.value) // the number is the live one; the tier stays
    }

    @Test fun withoutTheFirstLookTheTierWouldGoAway() {
        val s = state()
        s.mons += TestData.mon(data, 19, 80, uid = "big")
        s.mons.removeAll { it.uid == "big" }
        assertEquals(0, view(s, "highest-level").tier) // nothing was stored: this is the loss the first look prevents
    }

    @Test fun theFirstLookIsQuietAndRepeatable() {
        val s = state()
        s.totalReviews = 6_000
        Achievements.onReady(s, data, NOW)
        assertEquals(NOW, s.achSince)
        assertEquals(listOf(NOW, NOW), s.achTiers["answers"])
        assertTrue(AchievementTable.ROWS.all { it.id in s.achTiers })
        Achievements.onReady(s, data, NOW + 5 * DAY) // a later start of the app: nothing moves
        assertEquals(NOW, s.achSince)
        assertEquals(listOf(NOW, NOW), s.achTiers["answers"])
    }

    // ---- A3: inferred dates ----

    @Test fun datesOfAFirstSilentLookAreFlaggedInferred() {
        val s = state()
        s.totalReviews = 6_000
        check(s, silent = true)
        assertEquals(setOf("answers/0", "answers/1"), s.achInferred.filter { it.startsWith("answers/") }.toSet())
        assertEquals(setOf(0, 1), view(s, "answers").inferred)
    }

    @Test fun aFirstExternalFeedWeeksLaterIsNotRecentlyReached() {
        val s = state()
        check(s, NOW - 30 * DAY, silent = true) // tracking began a month ago
        val events = Achievements.feed(s, data, Metric.STUDY_MINUTES, 3_000, NOW) // years of history arrive: 1 h, 10 h, 50 h
        assertTrue(events.none { it is GameEvent.AchievementReached })
        val v = view(s, "study-time")
        assertEquals(2, v.tier)
        assertEquals(NOW, v.dates[2]) // dated today, and not the start of tracking
        assertTrue(2 in v.inferred)
        assertFalse(AchPageMath.recent(v, NOW, s.achSince))
        assertEquals("Reached before 13 Oct 2026", AchPageMath.reachedText(v.dates[2], s.achSince, UTC, 2 in v.inferred))
        assertFalse("answers" in AchPageMath.groups(Achievements.view(s, data), AchFilter.RECENT, NOW, s.achSince).flatMap { g -> g.shown.map { it.id } })
        assertTrue(AchPageMath.groups(Achievements.view(s, data), AchFilter.RECENT, NOW, s.achSince).all { it.empty })
    }

    @Test fun aTierReachedLaterWhileTrackedIsARealDate() {
        val s = state()
        check(s, NOW - 30 * DAY, silent = true)
        Achievements.feed(s, data, Metric.STUDY_MINUTES, 3_000, NOW - 10 * DAY) // the first feed: quiet
        val events = Achievements.feed(s, data, Metric.STUDY_MINUTES, 12_000, NOW) // a live crossing: Legendary
        assertEquals(listOf(3), events.filterIsInstance<GameEvent.AchievementReached>().map { it.tier })
        assertFalse("study-time/3" in s.achInferred)
        val v = view(s, "study-time")
        assertEquals(setOf(0, 1, 2), v.inferred)
        assertTrue(AchPageMath.recent(v, NOW, s.achSince))
        assertEquals("Reached 13 Oct 2026", AchPageMath.reachedText(v.dates[3], s.achSince, UTC, 3 in v.inferred))
    }

    @Test fun aLiveTierThroughAnActionIsARealDate() {
        val s = state()
        check(s, NOW - 1000, silent = true)
        s.totalReviews = 500
        check(s, NOW)
        assertTrue(s.achInferred.none { it.startsWith("answers/") })
        assertTrue(AchPageMath.recent(view(s, "answers"), NOW, s.achSince))
    }

    @Test fun aBadgeWithItsOwnDateIsNotInferredAndOneWithoutIs() {
        val s = state()
        s.badges += setOf(5, 7)
        s.badgeAt[7] = NOW - 3 * DAY // "First Pokémon caught": the badge knows its day
        check(s, NOW)                // badge 5 (levelled up) is an old one with no date
        assertFalse("first-catch/0" in s.achInferred)
        assertEquals(listOf(NOW - 3 * DAY), s.achTiers["first-catch"])
        assertTrue("first-level-up/0" in s.achInferred)
        assertEquals(setOf(0), view(s, "first-level-up").inferred)
        assertTrue(view(s, "first-catch").inferred.isEmpty())
    }

    @Test fun anOldSavesRowsAreAllInferredOnTheFirstLook() {
        val s = state()
        s.history["2026-10-12"] = DayLog(120, xp = 900, cash = 3_000, buttons = intArrayOf(0, 10, 20, 60, 30))
        Achievements.backfill(s)
        check(s, NOW) // not silent, but a row never looked at is quiet and its dates are guesses
        assertTrue("money-earned/0" in s.achInferred) // 3 000 >= 2 000
        assertFalse(AchPageMath.recent(view(s, "money-earned"), NOW, 0L))
    }

    @Test fun theInferredFlagsSurviveASaveAndAnOldSaveHasNone() {
        val s = state()
        s.totalReviews = 6_000
        check(s, silent = true)
        val back = SaveCodec.decode(SaveCodec.encode(s))
        assertEquals(s.achInferred, back.achInferred)
        assertEquals(setOf(0, 1), view(back, "answers").inferred)
        val o = JSONObject(SaveCodec.encode(s)); o.remove("achInferred") // a save from before the flag
        val old = SaveCodec.decode(o.toString())
        assertTrue(old.achInferred.isEmpty())
        assertEquals(s.achTiers, old.achTiers) // the dates themselves are untouched
    }

    @Test fun theLadderAndTheDateTextKnowWhichTiersAreInferred() {
        val s = state()
        check(s, NOW - 30 * DAY, silent = true)
        Achievements.feed(s, data, Metric.STUDY_MINUTES, 600, NOW - 10 * DAY)
        Achievements.feed(s, data, Metric.STUDY_MINUTES, 3_000, NOW - 2 * DAY) // tiers 0, 1 inferred, then 2 live
        val steps = AchPageMath.ladder(view(s, "study-time"))
        assertEquals(listOf(true, true, false), steps.take(3).map { it.inferred })
        assertEquals("Reached before 3 Oct 2026", AchPageMath.reachedText(steps[0].date, s.achSince, UTC, steps[0].inferred))
        assertEquals("Reached 11 Oct 2026", AchPageMath.reachedText(steps[2].date, s.achSince, UTC, steps[2].inferred))
    }
}
