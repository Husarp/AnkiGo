package com.husarp.ankigo.game

import com.husarp.ankigo.game.save.SaveCodec
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class R37VAuraTest {
    private val data = TestData.world()
    private val now = 1_791_892_800_000L

    private fun encounter(tier: Tier, world: GameData = data): Pair<Game, List<GameEvent>> {
        val state = TestData.state(world, 25, 100, listOf("growl"))
        state.reviewsToday = 100
        state.caught += 151
        state.nextSeed = (0L..100_000L).first { seed ->
            val r = Random(seed)
            Encounters.roll(world, state, Encounters.wildLevel(100, r), r).tier == tier
        }
        val game = Game(world, state, Random(37)) { now }
        return game to game.ensureEncounter()
    }

    @Test fun bothRareTiersStartWithFiveNormalStagesAndOneBattleLine() {
        for (tier in listOf(Tier.LEGENDARY, Tier.MYTHICAL)) {
            val (game, events) = encounter(tier)
            val b = game.state.battle!!
            assertArrayEquals(intArrayOf(1, 1, 1, 1, 1, 0, 0), b.enemy.boosts)
            assertArrayEquals(IntArray(7), b.player.boosts)
            assertEquals(tier, b.tier)
            assertTrue(events.first() is GameEvent.Encounter)
            assertEquals("${b.wild.name}'s legendary aura surges!", events.filterIsInstance<GameEvent.Encounter>().single().auraText)
            assertEquals(1.5, BattleEngine.stageMult(b.enemy.boosts[0]), 0.0)
            assertEquals(StatCalc.maxHp(data, b.wild), b.wild.hp)
            assertEquals(Encounters.wildLevel(100, Random(b.encounterSeed!!)), b.wild.level)
        }
    }

    @Test fun auraRaisesBothAttackKindsBothDefensesAndSpeedInTheEngine() {
        val (game, _) = encounter(Tier.LEGENDARY)
        val b = game.state.battle!!
        val main = game.state.main!!
        for (move in listOf(data.move("tackle")!!, data.move("watergun")!!)) {
            fun hit(outgoing: Boolean, aura: Boolean): Int = BattleEngine.damage(
                data, if (outgoing) b.wild else main, if (outgoing && aura) b.enemy else BattleSide(),
                if (outgoing) main else b.wild, if (!outgoing && aura) b.enemy else BattleSide(),
                move, Random(0), forceCrit = false, forceRoll = 100, attackerIsMain = !outgoing,
            ).damage
            assertTrue("${move.category} attack", hit(true, true) > hit(true, false))
            assertTrue("${move.category} defense", hit(false, true) < hit(false, false))
        }
        val round = Round(data, b, main, 1.0, 1.0, Random(0))
        val speed = round.speed(false)
        b.enemy.boosts[4] = 0
        assertEquals(round.speed(false) * 1.5, speed, 0.0)
    }

    @Test fun normalAndBabyEncountersHaveNoAuraOrExtraLine() {
        for (tier in listOf(Tier.NORMAL, Tier.BABY)) {
            val (game, events) = encounter(tier)
            assertArrayEquals(IntArray(7), game.state.battle!!.enemy.boosts)
            assertEquals(1, events.size)
            assertNull((events.single() as GameEvent.Encounter).auraText)
        }
    }

    @Test fun resumeAndRepeatedEnsureDoNotReapplyOrAnnounceAura() {
        val (game, _) = encounter(Tier.LEGENDARY)
        game.state.battle!!.enemy.boosts[0] = -2
        val restored = SaveCodec.decode(SaveCodec.encode(game.state))
        val resumed = Game(data, restored, Random(2)) { now }
        repeat(2) { assertTrue(resumed.ensureEncounter().isEmpty()) }
        assertArrayEquals(intArrayOf(-2, 1, 1, 1, 1, 0, 0), restored.battle!!.enemy.boosts)
    }

    @Test fun nextRareEncounterGetsFreshAuraAfterAReset() {
        val (game, _) = encounter(Tier.MYTHICAL)
        val seed = game.state.battle!!.encounterSeed!!
        game.state.battle!!.enemy.boosts.fill(0)
        game.state.battle = null
        game.state.nextSeed = seed
        assertNotNull((game.ensureEncounter().single() as GameEvent.Encounter).auraText)
        assertArrayEquals(intArrayOf(1, 1, 1, 1, 1, 0, 0), game.state.battle!!.enemy.boosts)
    }

    @Test fun movesLowerAuraStagesAndRespectTheUsualCap() {
        val (game, _) = encounter(Tier.LEGENDARY)
        val b = game.state.battle!!
        R17C5.playing(listOf("growl")) {
            BattleEngine.playRound(data, b, game.state.main!!, 1.0, false, Random(1))
        }
        assertArrayEquals(intArrayOf(0, 1, 1, 1, 1, 0, 0), b.enemy.boosts)
        game.state.main!!.moves.apply { clear(); add("splash") }
        b.wild.moves.apply { clear(); add("swordsdance") }
        R17C5.playing(listOf("splash"), listOf("swordsdance")) {
            repeat(5) { BattleEngine.playRound(data, b, game.state.main!!, 1.0, true, Random(it)) }
        }
        assertEquals(6, b.enemy.boosts[0])
        assertEquals(1, b.enemy.boosts[1])
    }

    @Test fun hazeRemovesAuraAndItDoesNotReturnOnTheNextRound() {
        val world = TestData.data(data.species.values.toList(), TestData.MOVES +
            ("haze" to Move("haze", "Haze", Type.ICE, Category.STATUS, 0, null, target = "all")))
        val (game, _) = encounter(Tier.MYTHICAL, world)
        game.state.main!!.moves.apply { clear(); add("haze") }
        R17C5.playing(listOf("haze")) {
            repeat(2) { BattleEngine.playRound(world, game.state.battle!!, game.state.main!!, 1.0, false, Random(it)) }
        }
        assertArrayEquals(IntArray(7), game.state.battle!!.enemy.boosts)
    }

    @Test fun masterBallCatchKeepsThePokemonStatsAndOwnedBattlesHaveNoAura() {
        for (tier in listOf(Tier.LEGENDARY, Tier.MYTHICAL)) {
            val (game, _) = encounter(tier)
            val b = game.state.battle!!
            val before = StatCalc.stats(data, b.wild)
            game.state.bag["master-ball"] = 1
            b.wild.hp = 1
            assertTrue(game.throwBall("master-ball").any { it is GameEvent.Caught })
            val caught = game.state.mons.last()
            assertEquals(before, StatCalc.stats(data, caught))
            assertEquals(b.wild.ivs, caught.ivs)
            assertEquals(b.wild.evs, caught.evs)
            assertEquals(b.wild.level, caught.level)
            assertEquals(StatCalc.maxHp(data, caught), caught.hp)
            game.state.team.clear(); game.state.team += caught.uid
            game.state.battle = null
            game.ensureEncounter()
            assertArrayEquals(IntArray(7), game.state.battle!!.player.boosts)
        }
    }
}
