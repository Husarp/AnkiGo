package com.husarp.ankigo.game

import com.husarp.ankigo.game.save.SaveCodec
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** R9-B2: "types" and "highest-level" have 3 tiers now; a save made for the 5-tier ladders is remapped once, quietly. Made-up species only. */
class AchievementLadderTest {
    // 18 made-up species, one per type, so the "types" row can be filled up to 18.
    private val data = TestData.data(Type.entries.mapIndexed { i, t -> TestData.species(100 + i, "Mon${100 + i}", listOf(t)) })
    private val NOW = 1_791_892_800_000L
    private val D = 86_400_000L
    private val d = longArrayOf(1 * D, 2 * D, 3 * D, 4 * D, 5 * D)

    private fun state(types: Int = 0): GameState {
        val s = GameState(createdAt = 1L, day = "2026-10-13", starterChosen = true)
        for (i in 0 until types) s.caught += 100 + i
        return s
    }

    /** A save written for ladder 1: [oldDates] reached tiers for "types" (and the same for "highest-level"), [inferred] old tier indexes flagged. */
    private fun old(types: Int, oldDates: Int, inferred: Set<Int> = emptySet()): GameState = state(types).also { s ->
        s.achLadder = 1
        s.achTiers["types"] = d.take(oldDates).toMutableList()
        s.achTiers["highest-level"] = d.take(oldDates).toMutableList()
        for (t in inferred) { s.achInferred += Achievements.inferredKey("types", t); s.achInferred += Achievements.inferredKey("highest-level", t) }
    }

    private fun check(s: GameState, now: Long = NOW) = mutableListOf<GameEvent>().also { Achievements.check(s, data, now, it) }

    @Test fun onlyTheTwoEasyRowsHaveThreeTiers() {
        val three = AchievementTable.ROWS.filter { it.ankimonBadge == null && it.tiers.size == 3 }.map { it.id }
        assertEquals(listOf("highest-level", "types"), three.sorted())
        assertEquals(listOf(6L, 10L, 18L), AchievementTable.row("types")!!.tiers)
        assertEquals(listOf(25L, 50L, 100L), AchievementTable.row("highest-level")!!.tiers)
        assertEquals(listOf(1, 2, 4), AchievementTable.REMAP["types"])
        // the new thresholds are the old tiers 1, 2, 4
        assertEquals(listOf(3L, 6L, 10L, 15L, 18L).slice(listOf(1, 2, 4)), AchievementTable.row("types")!!.tiers)
        assertEquals(listOf(10L, 25L, 50L, 75L, 100L).slice(listOf(1, 2, 4)), AchievementTable.row("highest-level")!!.tiers)
    }

    @Test fun remapOfAllFiveOldTiers() {
        val s = old(18, 5)
        assertTrue(Achievements.migrate(s))
        assertEquals(listOf(d[1], d[2], d[4]), s.achTiers["types"])
        assertEquals(listOf(d[1], d[2], d[4]), s.achTiers["highest-level"])
        assertEquals(AchievementTable.LADDER, s.achLadder)
    }

    @Test fun remapOfTwoFourAndNoOldTiers() {
        val a = old(6, 2); Achievements.migrate(a)
        assertEquals(listOf(d[1]), a.achTiers["types"])
        val b = old(15, 4); Achievements.migrate(b)
        assertEquals(listOf(d[1], d[2]), b.achTiers["types"])
        val c = old(0, 0); Achievements.migrate(c)
        assertEquals(emptyList<Long>(), c.achTiers["types"])
        val e = old(0, 1); Achievements.migrate(e) // only the old Common (3 types): nothing of it exists any more
        assertEquals(emptyList<Long>(), e.achTiers["types"])
        val f = old(10, 3); Achievements.migrate(f)
        assertEquals(listOf(d[1], d[2]), f.achTiers["types"])
    }

    @Test fun aRowNeverLookedAtStaysAbsent() {
        val s = state(); s.achLadder = 1
        Achievements.migrate(s)
        assertFalse("types" in s.achTiers)
        assertEquals(AchievementTable.LADDER, s.achLadder)
    }

    @Test fun inferredKeysFollowTheirTier() {
        val s = old(18, 5, inferred = setOf(0, 2, 4))
        s.achInferred += "answers/1" // another row: untouched
        Achievements.migrate(s)
        val inferred = s.achInferred.filter { it.startsWith("types/") }.toSet()
        assertEquals(setOf("types/1", "types/2"), inferred) // old 2 -> new 1, old 4 -> new 2; old 0 dropped
        assertEquals(setOf("highest-level/1", "highest-level/2"), s.achInferred.filter { it.startsWith("highest-level/") }.toSet())
        assertTrue("answers/1" in s.achInferred)
    }

    @Test fun aSecondCallDoesNothing() {
        val s = old(18, 5)
        assertTrue(Achievements.migrate(s))
        val tiers = s.achTiers.mapValues { it.value.toList() }
        val inferred = s.achInferred.toSet()
        assertFalse(Achievements.migrate(s))
        assertEquals(tiers, s.achTiers.mapValues { it.value.toList() })
        assertEquals(inferred, s.achInferred)
    }

    @Test fun aNewGameIsAtTheCurrentLadderAndNeverRemaps() {
        val s = state()
        assertEquals(AchievementTable.LADDER, s.achLadder)
        s.achTiers["types"] = mutableListOf(d[0], d[1])
        assertFalse(Achievements.migrate(s))
        assertEquals(listOf(d[0], d[1]), s.achTiers["types"])
    }

    @Test fun achLadderRoundTrips() {
        val s = old(6, 2)
        val back = SaveCodec.decode(SaveCodec.encode(s))
        assertEquals(1, back.achLadder)
        val s2 = state(); Achievements.migrate(s2)
        assertEquals(AchievementTable.LADDER, SaveCodec.decode(SaveCodec.encode(s2)).achLadder)
        assertEquals(AchievementTable.LADDER, JSONObject(SaveCodec.encode(state())).getInt("achLadder")) // always written
    }

    @Test fun anOldSaveWithoutTheKeyMigratesOnTheFirstLook() {
        val s = old(13, 3)
        val o = JSONObject(SaveCodec.encode(s)); o.remove("achLadder")
        val back = SaveCodec.decode(o.toString())
        assertEquals(1, back.achLadder)
        Achievements.onReady(back, data, NOW)
        assertEquals(AchievementTable.LADDER, back.achLadder)
        assertEquals(listOf(d[1], d[2]), back.achTiers["types"]) // 13 types: Common + Rare, 18 not reached
    }

    @Test fun theFirstLookAfterTheMigrationIsQuiet() {
        // old: 10 types reached 3 old tiers. A save whose first row date is missing for a tier the value already reaches: 18 types, only 2 old dates.
        val s = old(18, 2)
        val events = check(s)
        assertTrue(events.filterIsInstance<GameEvent.AchievementReached>().isEmpty())
        // old [Common, Rare] -> new [Rare date]; 18 types reaches all three: Rare kept, Epic and the missing one added quietly, flagged inferred
        assertEquals(3, s.achTiers["types"]!!.size)
        assertEquals(d[1], s.achTiers["types"]!![0])
        assertEquals(NOW, s.achTiers["types"]!![1])
        assertTrue("types/1" in s.achInferred && "types/2" in s.achInferred)
        assertFalse("types/0" in s.achInferred)
    }

    @Test fun aLaterRealCrossingStillCelebrates() {
        val s = old(13, 3)
        assertTrue(check(s).filterIsInstance<GameEvent.AchievementReached>().isEmpty())
        assertEquals(2, s.achTiers["types"]!!.size)
        for (i in 13 until 18) s.caught += 100 + i
        val got = check(s, NOW + D).filterIsInstance<GameEvent.AchievementReached>().filter { it.id == "types" }
        assertEquals(1, got.size)
        assertEquals(2, got[0].tier)
        assertEquals("Epic", got[0].tierName)
        assertEquals(3, s.achTiers["types"]!!.size)
        assertFalse("types/2" in s.achInferred)
    }

    @Test fun aThreeTierRowTopsOutAtEpicInTheView() {
        val s = state(18)
        Achievements.onReady(s, data, NOW)
        val v = Achievements.view(s, data).single { it.id == "types" }
        assertEquals(2, v.tier)
        assertEquals("Epic", v.tierName)
        assertEquals(null, v.next)
        assertEquals(3, AchPageMath.ladder(v).size)
        assertEquals(listOf("Common", "Rare", "Epic"), AchPageMath.ladder(v).map { it.name })
        // a tier saved beyond the ladder is harmless and never shown
        s.achTiers["types"] = mutableListOf(d[0], d[1], d[2], d[3], d[4])
        assertEquals(3, Achievements.view(s, data).single { it.id == "types" }.dates.size)
    }

    @Test fun aSavedTierIsNeverShownAsReachedWithoutTheValue() {
        // a remapped row: old 4 tiers (15 types) -> new [Rare, Epic date]; if the value dropped (releases), the view still follows max(dates, value) as before
        val s = old(15, 4); Achievements.migrate(s)
        s.caught.clear(); for (i in 0 until 7) s.caught += 100 + i
        val v = Achievements.view(s, data).single { it.id == "types" }
        assertEquals(1, v.tier) // dates kept (never go back down), nothing beyond them
        assertEquals(7L, v.value)
    }

    // ---- R9-W3-B: the "Critical hits" row is gone (crits are off in the engine) ----

    @Test fun theTableHasNoCritsRowButTheMetricStaysCounted() {
        assertTrue(AchievementTable.ROWS.none { it.id == "crits" || it.metric == Metric.CRITS })
        assertTrue(AchievementTable.row("crits") == null)
        assertTrue(Metric.CRITS.counted)
    }

    @Test fun anOldSaveWithCritsStateLoadsAndTheTotalsIgnoreIt() {
        val s = state()
        s.counters[Metric.CRITS.name] = 600
        s.achTiers["crits"] = d.take(3).toMutableList()
        s.achInferred += Achievements.inferredKey("crits", 1)
        val before = Achievements.view(state(), data)
        val loaded = SaveCodec.decode(SaveCodec.encode(s))
        val events = check(loaded)
        assertTrue(events.none { it is GameEvent.AchievementReached && it.id == "crits" })
        val views = Achievements.view(loaded, data)
        assertTrue(views.none { it.id == "crits" })
        assertEquals(before.size, views.size)
        assertEquals(AchPageMath.tiersTotal(before), AchPageMath.tiersTotal(views))
        assertEquals(600L, loaded.counters[Metric.CRITS.name])
    }
}
