package com.husarp.ankigo.game

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class R23ZbHitEventsTest {
    private val data = TestData.world()
    @Test fun moveEffectCountExcludesRoundResiduals() {
        val user = TestData.mon(data, 19, 30, listOf("recover")).also { it.hp = 10 }
        val target = TestData.mon(data, 150, 60, listOf("recover"))
        val battle = Battle(user, Tier.NORMAL)
        battle.enemy.status = "psn"
        val events = BattleEngine.playRound(data, battle, target, 1.0, true, Random(1), mainActs = false)
        val at = events.indexOfFirst { it is GameEvent.Attack }
        val attack = events[at] as GameEvent.Attack
        assertEquals(1, attack.moveEffectCount)
        assertEquals(EffectKind.HEAL, (events[at + 1] as GameEvent.Effect).kind)
        assertTrue(events.drop(at + 2).any { it is GameEvent.Effect && it.kind == EffectKind.STATUS_DAMAGE })
    }

    @Test fun afterMoveStatEffectsBelongToTheMoveToo() {
        val move = Move("tailwind", "Tailwind", Type.FLYING, Category.STATUS, 0, null, target = "allySide")
        val world = GameData(data.species, data.moves + (move.id to move), data.growth, data.itemPrices)
        val user = TestData.mon(world, 19, 30, listOf(move.id)).also { it.ability = "windrider" }
        val target = TestData.mon(world, 150, 60, listOf("recover"))
        val events = BattleEngine.switchHit(world, Battle(user, Tier.NORMAL), target, Random(1))
        val at = events.indexOfFirst { it is GameEvent.Attack }
        val attack = events[at] as GameEvent.Attack
        val effects = events.drop(at + 1).take(attack.moveEffectCount).filterIsInstance<GameEvent.Effect>()
        assertTrue(effects.any { it.kind == EffectKind.FIELD })
        assertTrue(effects.any { it.kind == EffectKind.STAT_CHANGE && it.stat == "atk" })
    }

    @Test fun recordedHitsAreExactAndStopAtFaint() {
        for (hp in listOf(1, 200)) for (seed in 1..20) {
            val user = TestData.mon(data, 19, 25, listOf("bulletseed"))
            val target = TestData.mon(data, 150, 60, listOf("recover")).also { it.hp = hp }
            val events = BattleEngine.playRound(data, Battle(target, Tier.NORMAL), user, 1.0, false, Random(seed))
            val attack = events.filterIsInstance<GameEvent.Attack>().first { it.byPlayer }
            assertTrue(attack.multiHit)
            assertEquals(attack.hits, attack.hitDamages.size)
            assertEquals(attack.damage, attack.hitDamages.sum())
            assertEquals(hp - attack.targetHp, attack.hitDamages.sum())
            if (hp == 1) assertEquals(listOf(1), attack.hitDamages)
        }
    }
    @Test fun recoilMetadataUsesRealClampedLossAndRespectsRockHead() {
        for (ability in listOf("runaway", "rockhead", "magicguard")) {
            val user = TestData.mon(data, 19, 30, listOf("doubleedge")).also { it.ability = ability; it.hp = 5 }
            val target = TestData.mon(data, 150, 60, listOf("recover"))
            val events = BattleEngine.playRound(data, Battle(target, Tier.NORMAL), user, 1.0, false, Random(1))
            val attack = events.filterIsInstance<GameEvent.Attack>().first { it.byPlayer }
            val recoil = events.filterIsInstance<GameEvent.Effect>().filter { it.moveRecoil }
            assertTrue(attack.recoilMove)
            assertEquals(recoil.sumOf { it.amount }, 5 - attack.userHp)
            assertEquals(if (ability == "runaway") minOf(5, maxOf(1, attack.damage * 33 / 100)) else 0, 5 - attack.userHp)
        }
    }
    @Test fun hpCostRecoilIsMarkedForExistingCostMoves() {
        val moves = listOf(
            Move("steelbeam", "Steel Beam", Type.STEEL, Category.SPECIAL, 140, null, mindBlownRecoil = true),
            Move("chloroblast", "Chloroblast", Type.GRASS, Category.SPECIAL, 150, null))
        val world = GameData(data.species, data.moves + moves.associateBy { it.id }, data.growth, data.itemPrices)
        for (move in moves) {
            val user = TestData.mon(world, 19, 30, listOf(move.id))
            val target = TestData.mon(world, 150, 60, listOf("recover"))
            val events = BattleEngine.playRound(world, Battle(target, Tier.NORMAL), user, 1.0, false, Random(2))
            assertTrue(events.filterIsInstance<GameEvent.Attack>().first { it.byPlayer }.recoilMove)
            val recoil = events.filterIsInstance<GameEvent.Effect>().single { it.moveRecoil }
            assertTrue(recoil.onPlayer)
            assertEquals((StatCalc.maxHp(world, user) + 1) / 2, recoil.amount)
        }
    }

}
