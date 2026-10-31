package com.husarp.ankigo.game

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class R23MergeHitEffectsTest {
    @Test fun berriesBetweenHitsKeepExactHpAndTheirHitIndexOnBothSides() {
        val base = TestData.world()
        val move = Move("mergedouble", "Double hit", Type.NORMAL, Category.PHYSICAL, 1, null,
            fixedDamage = 20, multihit = 2..2)
        val data = GameData(base.species, base.moves + (move.id to move), base.growth, base.itemPrices)
        for (player in listOf(true, false)) for (triggerHit in 0..1) {
            val user = TestData.mon(data, 19, 50, listOf(move.id), "user").apply { ability = "runaway" }
            val target = TestData.mon(data, 19, 50, listOf("recover"), "target").apply {
                ability = "runaway"; heldItem = "sitrus-berry"
                hp = StatCalc.maxHp(data, this) / 2 + 10 + triggerHit * 20
            }
            val start = target.hp
            val healed = StatCalc.maxHp(data, target) / 4
            val battle = Battle(if (player) target else user, Tier.NORMAL)
            val events = BattleEngine.playRound(data, battle, if (player) user else target,
                1.0, !player, Random(3), mainActs = player)
            val attack = events.filterIsInstance<GameEvent.Attack>().single()
            val berry = events.filterIsInstance<GameEvent.Effect>().single { it.kind == EffectKind.HEAL }
            assertEquals(listOf(20, 20), attack.hitDamages)
            assertEquals(listOf(start - 20 + if (triggerHit == 0) healed else 0, start - 40 + healed), attack.hitTargetHps)
            assertEquals(triggerHit, berry.hitIndex)
            assertEquals(!player, berry.onPlayer)
            assertEquals(attack.hitTargetHps[triggerHit], berry.hp)
            assertEquals(attack.hitTargetHps.last(), target.hp)
            assertNull(target.heldItem)
        }
    }
}
