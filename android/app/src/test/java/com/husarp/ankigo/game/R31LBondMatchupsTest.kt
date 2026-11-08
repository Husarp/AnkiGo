package com.husarp.ankigo.game

import com.husarp.ankigo.game.save.SaveCodec
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class R31LBondMatchupsTest {
    private val data = TestData.world()
    private val now = 1794052800000L
    private fun state() = TestData.state(data, 25, 30, listOf("dragonrage")).also {
        it.battle = Battle(TestData.mon(data, 16, 20, listOf("splash"), "wild"), Tier.NORMAL)
    }
    private fun reveals(events: List<GameEvent>) = events.filterIsInstance<GameEvent.Info>().filter { it.text.startsWith("You know ") }

    @Test fun answeredCardRevealsAtLevelThreeAlongsideBondAlert() {
        val s = state(); s.main!!.bondPoints = 249
        s.seenMatchups[25] = mutableSetOf(Type.NORMAL)
        val game = Game(data, s, Random(1)) { now }.apply { roundAnswers = 10000 }
        val events = game.onAnswer(Answer(1, 3, now))
        assertEquals(3, Bond.level(s.main!!.bondPoints))
        assertEquals(Type.entries.toSet(), MatchupKnowledge.seen(data, s, 25))
        assertEquals("You know ${s.main!!.shownName}'s matchups now", reveals(events).single().text)
        assertEquals(s.main!!.uid, reveals(events).single().uid)
        assertTrue(events.filterIsInstance<GameEvent.Info>().any { it.text.startsWith("Bond grew!") })
        assertTrue(MatchupKnowledge.seen(data, s, 16).isEmpty())
        assertTrue(reveals(game.onAnswer(Answer(2, 3, now))).isEmpty())
    }

    @Test fun defeatAutoCatchAndBallRewardsRevealAtTheWinThreshold() {
        for (kind in listOf("defeat", "auto", "ball")) {
            val s = state(); s.main!!.bondPoints = 247
            s.battle!!.wild.hp = 1
            s.battle!!.override = if (kind == "defeat") Outcome.DEFEAT else Outcome.CATCH
            val game = Game(data, s, Random(1)) { now }.apply { roundAnswers = 1 }
            val events = if (kind == "ball") {
                s.bag["master-ball"] = 1
                game.throwBall("master-ball")
            } else game.onAnswer(Answer(1, 3, now))
            assertEquals(kind, 0, MatchupKnowledge.of(data, s, 25).unknownCount)
            assertEquals(kind, 1, reveals(events).size)
        }
    }

    @Test fun alreadyFullyDiscoveredSpeciesGetsNoRevealAlertOnLevelUp() {
        val s = state(); s.main!!.bondPoints = 249
        s.seenMatchups[25] = Type.entries.toMutableSet()
        val events = Game(data, s, Random(1)) { now }.onAnswer(Answer(1, 3, now))
        assertTrue(reveals(events).isEmpty())
        assertTrue(events.filterIsInstance<GameEvent.Info>().any { it.text.startsWith("Bond grew!") })
    }

    @Test fun oldSavesMigrateBoxedAndHigherBondsOncePerSpeciesAndPersist() {
        for (points in listOf(250, 500, 800)) {
            val s = state()
            s.mons += TestData.mon(data, 54, 20, uid = "boxed").apply { bondPoints = points }
            s.mons += TestData.mon(data, 54, 20, uid = "duplicate").apply { bondPoints = points }
            val old = JSONObject(SaveCodec.encode(s)).apply { remove("seenMatchups"); remove("bondMatchups") }
            val restored = SaveCodec.decode(old.toString())
            val game = Game(data, restored) { now }
            assertEquals(0, MatchupKnowledge.of(data, restored, 54).unknownCount)
            assertEquals(18, MatchupKnowledge.of(data, restored, 25).unknownCount)
            game.tick() // widget/day bookkeeping must not consume the battle alert
            assertEquals("boxed", reveals(game.ensureEncounter()).single().uid)
            assertTrue(game.ensureEncounter().isEmpty())
            val reloaded = SaveCodec.decode(SaveCodec.encode(restored))
            assertTrue(reveals(Game(data, reloaded).ensureEncounter()).isEmpty())
        }
    }

    @Test fun migrationRevealsFormsEverywhereBeforeWildAttackWithoutUnlockingStats() {
        val form = data.sp(54).copy(num = 10054, baseNum = 54, types = listOf(Type.WATER, Type.GROUND))
        val world = TestData.data(data.species.values.toList() + form)
        val s = TestData.state(world, 10054, 20).also { it.main!!.bondPoints = 250 }
        s.mons += TestData.mon(world, 54, 20, uid = "base")
        s.battle = Battle(TestData.mon(world, 54, 20, uid = "wild"), Tier.NORMAL)
        s.caught.clear(); s.defeatedSpecies.clear()
        val game = Game(world, SaveCodec.decode(SaveCodec.encode(s)))
        val restored = game.state
        restored.mons.clear(); restored.team.clear() // matchup knowledge alone must not unlock stats
        assertFalse(restored.battle!!.playerAttacked)
        assertTrue(MatchupKnowledge.available(world, restored, 54))
        assertEquals(0, WildInfo.of(world, restored, restored.battle!!, emptySet())!!.matchups!!.unknownCount)
        assertNull(WildInfo.of(world, restored, restored.battle!!, emptySet())!!.stats)
        assertNull(WildInfo.of(world, restored, restored.battle!!, emptySet())!!.base)
        assertEquals(listOf(Matchup(Type.ELECTRIC, 2.0)), MatchupKnowledge.of(world, restored, 54).weakTo.filter { it.type == Type.ELECTRIC })
        assertEquals(listOf(Matchup(Type.ELECTRIC, 0.0)), MatchupKnowledge.of(world, restored, 10054).immune.filter { it.type == Type.ELECTRIC })
        assertEquals(1, reveals(game.ensureEncounter()).size)
    }

    @Test fun bondUnlockRemainsVisibleAfterItsOwnedCopyLeavesAndSaveReloads() {
        val s = state(); s.main!!.bondPoints = 249
        Game(data, s, Random(1)) { now }.onAnswer(Answer(1, 3, now))
        s.mons.clear(); s.team.clear()
        s.battle = Battle(TestData.mon(data, 25, 20, uid = "wild"), Tier.NORMAL)
        val restored = SaveCodec.decode(SaveCodec.encode(s))
        assertTrue(MatchupKnowledge.available(data, restored, 25))
        assertEquals(0, WildInfo.of(data, restored, restored.battle!!, emptySet())!!.matchups!!.unknownCount)
        assertTrue(reveals(Game(data, restored).ensureEncounter()).isEmpty())
    }

    @Test fun evolutionRevealsTheNewSpeciesAtLevelThreeWithoutWaitingForReload() {
        for (points in listOf(125, 250, 800)) for (kind in listOf(EvoKind.LEVEL, EvoKind.ITEM)) {
            val s = state(); val mon = s.main!!.apply { bondPoints = points }
            s.seenMatchups[25] = Type.entries.toMutableSet()
            val events = mutableListOf<GameEvent>()
            Progression.evolve(data, s, mon, Evolution(26, kind, level = 20, item = if (kind == EvoKind.ITEM) "thunder-stone" else null), Random(1), events, now)
            assertEquals(26, mon.species)
            assertEquals(if (points >= 250) 0 else 18, MatchupKnowledge.of(data, s, 26).unknownCount)
            assertEquals(if (points >= 250) 1 else 0, reveals(events).size)
            if (points >= 250) assertEquals(mon.uid, reveals(events).single().uid)
            assertEquals(0, MatchupKnowledge.of(data, s, 25).unknownCount)
            val restored = SaveCodec.decode(SaveCodec.encode(s))
            assertTrue(reveals(Game(data, restored).ensureEncounter()).isEmpty())
        }
    }

    @Test fun lowerBondAndWildBondDoNotMigrate() {
        val s = state(); s.main!!.bondPoints = 249; s.battle!!.wild.bondPoints = 800
        val game = Game(data, s)
        assertTrue(s.seenMatchups.isEmpty())
        assertTrue(game.ensureEncounter().isEmpty())
        assertFalse(MatchupKnowledge.available(data, s, 16))
    }
}
