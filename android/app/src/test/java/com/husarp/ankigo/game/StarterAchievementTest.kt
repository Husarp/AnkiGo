package com.husarp.ankigo.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** R9-W4-B: the starter counts for no achievement (obtaining it is not a catch / sighting / type / shiny). Made-up species only. */
class StarterAchievementTest {
    private val data = TestData.world()
    private var time = 1_791_892_800_000L

    private fun fresh(species: Int = 1, seed: Int = 1): Pair<Game, GameState> {
        val s = GameState(createdAt = 1L)
        val g = Game(data, s, Random(seed)) { time }
        g.pickStarter(species)
        return g to s
    }

    private fun v(s: GameState, m: Metric) = Achievements.value(m, s, data)

    /** The collection numbers right after the pick: only what the starter added is in them, so all must be 0 (the wild one met is a real sighting). */
    @Test fun nothingTheStarterBroughtCounts() {
        val (_, s) = fresh()
        val wildIsStarterSpecies = s.battle!!.wild.species == 1
        assertEquals(0L, v(s, Metric.CATCHES))
        assertEquals(0L, v(s, Metric.DEX_CAUGHT))
        assertEquals(0L, v(s, Metric.TYPES_CAUGHT))
        assertEquals(0L, v(s, Metric.LEGENDARY_MYTHICAL))
        assertEquals(0L, v(s, Metric.HIGHEST_LEVEL))
        assertEquals(0L, v(s, Metric.TOTAL_LEVELS))
        assertEquals(0L, v(s, Metric.TOTAL_XP))
        assertEquals(0L, v(s, Metric.SHINIES))
        assertEquals((s.seen.size - if (wildIsStarterSpecies) 0 else 1).toLong(), v(s, Metric.DEX_SEEN))
        // ... while the game itself still shows the starter as caught and seen
        assertTrue(1 in s.caught && 1 in s.seen)
    }

    @Test fun aShinyStarterIsNotAShinyCatch() {
        val (_, s) = fresh()
        s.main!!.shiny = true
        assertEquals(0L, v(s, Metric.SHINIES))
    }

    @Test fun theTenthRealCatchIsTheTenthNotTheNinth() {
        val (_, s) = fresh()
        repeat(9) { i -> s.mons += TestData.mon(data, 19, 10, uid = "c$i") }
        assertEquals(9L, v(s, Metric.CATCHES))
        assertEquals(0, Achievements.reachedTiers(AchievementTable.ROWS.first { it.id == "catches" }, s, data))
        s.mons += TestData.mon(data, 19, 10, uid = "c9")
        assertEquals(1, Achievements.reachedTiers(AchievementTable.ROWS.first { it.id == "catches" }, s, data))
    }

    @Test fun aLaterCatchOfTheStarterSpeciesCountsAndSoDoesMeetingIt() {
        val (g, s) = fresh(species = 19)
        s.battle = null
        assertEquals(0L, v(s, Metric.DEX_CAUGHT))
        s.markSeen(19, time) // a wild one of the same species shows up
        assertEquals(s.seen.size.toLong(), v(s, Metric.DEX_SEEN))
        assertEquals(0L, v(s, Metric.DEX_CAUGHT))
        Catching.addToCollection(data, s, TestData.mon(data, 19, 4, uid = "w"), Tier.NORMAL, null, time, mutableListOf())
        assertEquals(1L, v(s, Metric.DEX_CAUGHT))
        assertEquals(1L, v(s, Metric.CATCHES))
        assertEquals(1L, v(s, Metric.TYPES_CAUGHT))
        assertTrue(g.state.starterChosen)
    }

    @Test fun anEvolvedStartersNewSpeciesIsNotCaughtButTheEvolutionCounts() {
        val (_, s) = fresh()
        val starter = s.main!!
        Progression.evolve(data, s, starter, data.sp(1).evolutions.first(), Random(2), mutableListOf())
        assertEquals(1L, v(s, Metric.EVOLUTIONS))
        assertEquals(0L, v(s, Metric.DEX_CAUGHT))
        assertEquals(0L, v(s, Metric.TYPES_CAUGHT))
        assertTrue(2 in s.caught && 2 in s.seen)
        // another Pokémon evolving into the same species is a real find
        val other = TestData.mon(data, 1, 10, uid = "o"); s.mons += other
        Progression.evolve(data, s, other, data.sp(1).evolutions.first(), Random(3), mutableListOf())
        assertEquals(1L, v(s, Metric.DEX_CAUGHT))
    }

    @Test fun theStarterLevelsItEarnsStillCount() {
        val (_, s) = fresh()
        s.main!!.level = 9
        assertEquals(9L, v(s, Metric.HIGHEST_LEVEL))
        assertEquals(4L, v(s, Metric.TOTAL_LEVELS))
        assertTrue(v(s, Metric.TOTAL_XP) > 0)
    }

    @Test fun noTierIsGrantedOrCelebratedBecauseOfTheStarter() {
        val (g, s) = fresh()
        // R10-W2-H made SMART the default: fights end faster, so a REAL catch (catches=4, id "types") can reach a tier inside 40 answers.
        // Pin the old random picks so the run stays the same as before; the intent (no tier because of the starter) is unchanged.
        s.settings.moveChoice = MoveChoice.RANDOM
        val collection = setOf("catches", "dex-caught", "dex-seen", "types", "highest-level", "total-levels", "total-xp", "shinies", "legendary-mythical")
        assertTrue(s.achTiers.filterKeys { it in collection }.values.all { it.isEmpty() })
        for (i in 1..40) {
            val ev = g.onAnswer(Answer(i.toLong(), 3, time))
            val hit = ev.filterIsInstance<GameEvent.AchievementReached>().filter { it.id in collection }
            assertTrue("answer $i fired $hit; catches=${s.catches} dexCaught=${v(s, Metric.DEX_CAUGHT)}", hit.isEmpty())
        }
        // the one-offs of real catches still fire: this is the wild one's catch, not the starter's
        assertFalse(s.catches == 0 && 7 in s.badges)
    }

    @Test fun theStarterMarkSurvivesTheSave() {
        val (_, s) = fresh()
        val back = com.husarp.ankigo.game.save.SaveCodec.decode(com.husarp.ankigo.game.save.SaveCodec.encode(s))
        assertEquals(s.starterUid, back.starterUid)
        assertEquals(s.starterSeen, back.starterSeen)
        assertEquals(s.starterCaught, back.starterCaught)
        assertTrue(back.starterUid.isNotEmpty())
        assertEquals(setOf(1), back.starterCaught)
    }
}
