package com.husarp.ankigo.game.save

import com.husarp.ankigo.game.*
import org.junit.Assert.*
import org.junit.Test

class R26MAttributionTest {
    @Test fun badgeSnapshotSurvivesChangesAndSave() {
        val data = TestData.world()
        val st = TestData.state(data, 25, 20, listOf("tackle"))
        val mon = st.main!!
        Badges.award(st, 18, mutableListOf(), 123L, mon, data)
        val original = st.achPokemon["badge/18"]!!.single()
        mon.name = "Changed"; mon.species = 133
        Badges.award(st, 18, mutableListOf(), 456L, mon, data)
        st.mons.clear()
        val back = SaveCodec.decode(SaveCodec.encode(st))
        assertEquals(original, back.achPokemon["badge/18"]!!.single())
        assertEquals(123L, back.badgeAt[18])
    }

    @Test fun realCatchRecordsBabyAndCumulativeIdentity() {
        val data = TestData.world()
        val st = TestData.state(data, 1, 20, listOf("tackle"))
        val wild = TestData.mon(data, 172, 5, listOf("tackle"), "wild")
        val caught = Catching.addToCollection(data, st, wild, Tier.BABY, "poke-ball", 123L, mutableListOf())
        assertEquals(caught.uid, st.achPokemon["badge/18"]!!.single().uid)
        assertEquals(172, st.achPokemon["badge/18"]!!.single().num)
        assertEquals(caught.uid, st.achPokemon["catches"]!!.single().uid)
        assertEquals(caught.uid, st.achPokemon["types"]!!.single().uid)
        assertEquals(caught.uid, st.achPokemon["ball-catches"]!!.single().uid)
        val later = Catching.addToCollection(data, st, wild, Tier.BABY, null, 456L, mutableListOf())
        assertNotEquals(caught.uid, later.uid)
        assertEquals(2, st.achPokemon["catches"]!!.size)
        assertEquals(caught.uid, st.achPokemon["badge/18"]!!.single().uid)
        assertEquals(1, st.achPokemon["ball-catches"]!!.size)
    }

    @Test fun oldRecordsAreNeverGuessed() {
        val data = TestData.world()
        val st = TestData.state(data, 25, 20, listOf("tackle"))
        st.badges += 18
        Achievements.onReady(st, data, 123L)
        val back = SaveCodec.decode(SaveCodec.encode(st))
        assertTrue(back.achPokemon.isEmpty())
        assertTrue(Achievements.view(back, data).first { it.ankimonBadge == 18 }.pokemon.isEmpty())
    }

    @Test fun futureCatchSecretKeepsTheActualCatch() {
        val data = TestData.world()
        val st = TestData.state(data)
        Achievements.onReady(st, data, 100L)
        val mon = st.main!!
        Achievements.bump(st, Metric.PERFECT_THROW)
        Achievements.check(st, data, 123L, mutableListOf(GameEvent.Caught(
            mon.uid, mon.species, mon.name, mon.level, mon.shiny, "poke-ball", true, false)))
        assertEquals(mon.uid, st.achPokemon["perfect-throw"]!!.single().uid)
        val original = st.achPokemon["perfect-throw"]!!.single()
        mon.name = "Renamed"
        Achievements.check(st, data, 456L, mutableListOf())
        assertEquals(original, st.achPokemon["perfect-throw"]!!.single())
    }

    @Test fun winSecretUsesIdentityBeforeRewardEvolution() {
        val data = TestData.world()
        val st = TestData.state(data)
        Achievements.onReady(st, data, 100L)
        val mon = st.main!!
        AchievementPokemon.record(st, "defeats", mon, data)
        val earned = st.achPokemon["defeats"]!!.single()
        mon.species = 26; mon.name = "Evolved"
        Achievements.bump(st, Metric.LAST_BREATH)
        Achievements.check(st, data, 123L, mutableListOf(GameEvent.Defeated(133, "Wild", 1)))
        assertEquals(earned, st.achPokemon["last-breath"]!!.single())
    }

    @Test fun lockedSecretsNeverExposeSavedIdentity() {
        val data = TestData.world()
        val st = TestData.state(data)
        AchievementPokemon.record(st, "one-shot", st.main!!, data)
        val v = Achievements.view(st, data).first { it.id == "one-shot" }
        assertEquals(-1, v.tier)
        assertTrue(v.pokemon.isEmpty())
    }

    @Test fun cumulativeContributorsAreDistinctAndMostRecentLast() {
        val data = TestData.world()
        val st = TestData.state(data, 25, 20, listOf("tackle"))
        val first = st.main!!
        val second = TestData.mon(data, first.species, first.level, uid = "second").apply { name = "Second" }
        AchievementPokemon.record(st, "catches", first, data)
        AchievementPokemon.record(st, "catches", second, data)
        AchievementPokemon.record(st, "catches", first, data)
        val back = SaveCodec.decode(SaveCodec.encode(st))
        assertEquals(2, back.achPokemon["catches"]!!.size)
        assertEquals(first.uid, back.achPokemon["catches"]!!.last().uid)
    }
}
