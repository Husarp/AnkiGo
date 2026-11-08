package com.husarp.ankigo.game

import com.husarp.ankigo.game.save.SaveCodec
import com.husarp.ankigo.ui.DetailFormat
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class R31JMatchupKnowledgeTest {
    private val data = TestData.world()
    private fun attack(player: Boolean, type: Type? = Type.NORMAL, eff: Double = 1.0) = GameEvent.Attack(
        player, "User", "Target", "Move", type, Category.PHYSICAL, if (eff == 0.0) 0 else 5,
        if (eff == 0.0) 0 else 1, eff, false, false, false, 10, 20, 10, 20,
    )
    private fun battle(world: GameData = data, species: Int = 54) = Battle(TestData.mon(world, species, 30, listOf("watergun"), uid = "wild"), Tier.NORMAL)
    private fun observe(state: GameState, b: Battle, vararg events: GameEvent) = MatchupKnowledge.observe(data, state, b, state.main!!, events.toList())

    @Test fun hitsInBothDirectionsRevealOnlyTheTargetSpeciesEntry() {
        for (eff in listOf(0.0, 0.25, 0.5, 1.0, 2.0, 4.0)) {
            val state = TestData.state(data)
            val b = battle()
            observe(state, b, attack(true, Type.GRASS, eff), attack(false, Type.WATER, eff))
            assertEquals(setOf(Type.GRASS), MatchupKnowledge.seen(data, state, 54))
            assertEquals(setOf(Type.WATER), MatchupKnowledge.seen(data, state, state.main!!.species))
            assertTrue(b.playerAttacked)
        }
    }

    @Test fun missesFailuresStatusStruggleAndBlockedDamageRevealNothing() {
        val state = TestData.state(data)
        val b = battle()
        observe(state, b, attack(true).copy(missed = true), attack(false).copy(failed = true),
            attack(true).copy(category = Category.STATUS), attack(true, null), attack(true).copy(damage = 0))
        assertTrue(state.seenMatchups.isEmpty())
        assertTrue(b.playerAttacked) // an attempted damaging attack opens the section, without revealing a missed type
    }

    @Test fun statusMovesDoNotOpenWildMatchups() {
        val state = TestData.state(data)
        val b = battle()
        observe(state, b, attack(true).copy(category = Category.STATUS), attack(false))
        assertFalse(b.playerAttacked)
        assertNull(WildInfo.of(data, state, b, emptySet())!!.matchups)
    }

    @Test fun catchAndDefeatEventsDoNotRevealAnyEntries() {
        val state = TestData.state(data).also { it.caught += 54; it.defeatedSpecies += 92 }
        val b = battle()
        observe(state, b, GameEvent.Caught("w", 54, "Duckling", 10, false, null, true, false), GameEvent.Defeated(92, "Spooky", 10))
        assertEquals(18, MatchupKnowledge.of(data, state, 54).unknownCount)
        assertEquals(18, MatchupKnowledge.of(data, state, 92).unknownCount)
        assertNull(WildInfo.of(data, state, b, emptySet())!!.matchups)
        assertNotNull(WildInfo.of(data, state, b, emptySet())!!.stats)
    }

    @Test fun firstAttackGateResetsEachEncounterButRememberedTypesDoNot() {
        val state = TestData.state(data)
        val b = battle().also { state.battle = it }
        observe(state, b, attack(true, Type.GRASS, 2.0))
        assertEquals(listOf(Matchup(Type.GRASS, 2.0)), WildInfo.of(data, state, b, emptySet())!!.matchups!!.weakTo)
        assertNull(WildInfo.of(data, state, b, emptySet())!!.stats)
        state.battle = battle()
        assertFalse(MatchupKnowledge.available(data, state, 54))
        assertNull(WildInfo.of(data, state, state.battle!!, emptySet())!!.matchups)
        observe(state, state.battle!!, attack(true, Type.NORMAL))
        assertEquals(16, WildInfo.of(data, state, state.battle!!, emptySet())!!.matchups!!.unknownCount)
    }

    @Test fun normalAndImmunityEntriesAreVisibleWithoutOffensiveOrUnseenWeaknessLeaks() {
        val m = TypeMatchups.discovered(listOf(Type.GHOST, Type.POISON), setOf(Type.NORMAL, Type.FIRE))
        assertEquals(listOf(Matchup(Type.NORMAL, 0.0)), m.immune)
        assertEquals(listOf(Matchup(Type.FIRE, 1.0)), m.normal)
        assertEquals(16, m.unknownCount)
        assertTrue(m.weakTo.isEmpty())
        assertTrue(m.strongAgainst.isEmpty())
        assertEquals("2/18 types seen", DetailFormat.matchupSummary(m))
        val ground = TypeMatchups.discovered(listOf(Type.GHOST, Type.POISON), setOf(Type.GROUND))
        assertEquals("weak to Ground ×2", DetailFormat.matchupSummary(ground))
    }

    @Test fun formsShareSeenTypesButCalculateTheirOwnMultipliers() {
        val form = data.sp(54).copy(num = 10054, baseNum = 54, types = listOf(Type.WATER, Type.GROUND))
        val world = TestData.data(data.species.values.toList() + form)
        val state = TestData.state(world)
        val b = battle(world, 10054)
        MatchupKnowledge.observe(world, state, b, state.main!!, listOf(attack(true, Type.ELECTRIC, 0.0)))
        assertEquals(setOf(Type.ELECTRIC), MatchupKnowledge.seen(world, state, 54))
        assertEquals(listOf(Matchup(Type.ELECTRIC, 2.0)), MatchupKnowledge.of(world, state, 54).weakTo)
        assertEquals(listOf(Matchup(Type.ELECTRIC, 0.0)), MatchupKnowledge.of(world, state, 10054).immune)
        assertTrue(MatchupKnowledge.seen(world, state, 92).isEmpty())
        assertTrue(MatchupKnowledge.seen(world, state, 134).isEmpty()) // same type, another species
    }

    @Test fun saveRoundTripsDiscoveriesAndTheEncounterGateWithIndependentSnapshot() {
        val state = TestData.state(data).also { it.battle = battle() }
        observe(state, state.battle!!, attack(true, Type.GRASS, 2.0), attack(false, Type.WATER))
        val snapshot = SaveCodec.snapshot(state)
        state.seenMatchups[54]!! += Type.FIRE
        state.battle!!.playerAttacked = false
        val text = snapshot()
        val restored = SaveCodec.decode(text)
        assertEquals(setOf(Type.GRASS), MatchupKnowledge.seen(data, restored, 54))
        assertTrue(restored.battle!!.playerAttacked)
        assertEquals(text, SaveCodec.encode(restored))
        assertEquals(setOf(Type.WATER), MatchupKnowledge.seen(data, restored, restored.main!!.species))
    }

    @Test fun oldSavesStartWithNothingSeenEvenWithCaughtAndDefeatedHistory() {
        val state = TestData.state(data).also { it.caught += 54; it.defeatedSpecies += 92; it.battle = battle() }
        val old = JSONObject(SaveCodec.encode(state)).apply {
            remove("seenMatchups"); getJSONObject("battle").remove("playerAttacked")
        }
        val restored = SaveCodec.decode(old.toString())
        assertTrue(restored.seenMatchups.isEmpty())
        assertFalse(restored.battle!!.playerAttacked)
        assertTrue(SpeciesKnowledge.known(data, restored, 54))
        assertTrue(SpeciesKnowledge.known(data, restored, 92))
    }

    @Test fun malformedDiscoveryTypesAreIgnored() {
        val json = JSONObject(SaveCodec.encode(GameState())).apply {
            put("seenMatchups", JSONObject("""{"54":["WATER","bogus",null,"WATER"],"bad":["FIRE"],"-2":["GRASS"],"92":"bad"}"""))
        }
        val restored = SaveCodec.decode(json.toString())
        assertEquals(mapOf(54 to setOf(Type.WATER)), restored.seenMatchups)
    }

    @Test fun realGameRoundRecordsOutgoingAndIncomingHitsBeforeResolvingFight() {
        for (button in listOf(1, 3)) {
            val state = TestData.state(data, 25, 30, listOf("tackle"))
            state.battle = battle()
            val game = Game(data, state, Random(42)) { 1794052800000L }.also { it.roundAnswers = 1 }
            val events = game.onAnswer(Answer(1L, button, 1794052800000L))
            val hit = events.filterIsInstance<GameEvent.Attack>().first { !it.missed && !it.failed && it.category != Category.STATUS }
            val target = if (hit.byPlayer) 54 else 25
            assertEquals(setOf(hit.moveType), MatchupKnowledge.seen(data, state, target))
        }
    }

    @Test fun finalHitRevealsOnlyItsTypeWhileBallCatchWithoutAHitRevealsNothing() {
        for (outcome in listOf(Outcome.CATCH, Outcome.DEFEAT)) {
            val state = TestData.state(data, 25, 30, listOf("tackle"))
            state.battle = battle().also { it.wild.hp = 1; it.override = outcome }
            val game = Game(data, state, Random(7)) { 1794052800000L }.also { it.roundAnswers = 1 }
            val events = game.onAnswer(Answer(1L, 3, 1794052800000L))
            assertTrue(events.any { it is GameEvent.Caught || it is GameEvent.Defeated })
            assertEquals(setOf(Type.NORMAL), MatchupKnowledge.seen(data, state, 54))
        }
        val state = TestData.state(data).also { it.battle = battle(); it.bag["master-ball"] = 1 }
        assertTrue(Game(data, state, Random(7)).throwBall("master-ball").any { it is GameEvent.Caught })
        assertTrue(MatchupKnowledge.seen(data, state, 54).isEmpty())
    }

    @Test fun switchHitRevealsTheIncomingOwnedSpeciesOnly() {
        val state = TestData.state(data, 25, 30, listOf("tackle"))
        val incoming = TestData.mon(data, 19, 30, uid = "incoming")
        state.mons += incoming; state.team += incoming.uid
        state.battle = battle().also { it.rounds = 1 }
        val game = Game(data, state, Random(42)) { 1794052800000L }
        val result = game.setMain(incoming.uid)
        assertTrue(result.ok)
        assertTrue(result.events.any { it is GameEvent.Attack && !it.byPlayer })
        assertEquals(setOf(Type.WATER), MatchupKnowledge.seen(data, state, 19))
        assertTrue(MatchupKnowledge.seen(data, state, 25).isEmpty())
        assertFalse(state.battle!!.playerAttacked)
    }
}
