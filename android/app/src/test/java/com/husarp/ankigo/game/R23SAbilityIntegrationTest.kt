package com.husarp.ankigo.game

import org.junit.Assert.*
import org.junit.Test

class R23SAbilityIntegrationTest {
    @Test fun pressureReducesActualDamageAndMoldBreakerBypassesArmor() {
        fun damage(ab: String, attacker: String = ""): Int {
            val w = R17C9b.wild(ab, 801, "tackle")
            val before = w.hp
            R17C9b.round(R17C9b.main(attacker, "tackle"), R17C9b.battle(w), "tackle")
            return before - w.hp
        }
        assertTrue(damage("Pressure") < damage(""))
        assertEquals(damage(""), damage("Battle Armor", "Mold Breaker"))
    }

    @Test fun killingHitNeverCreatesHalfHpBoost() {
        for (ab in listOf("Moxie", "Soul-Heart", "Beast Boost", "Grim Neigh", "Chilling Neigh")) {
            val m = R17C9b.main(ab, "tackle"); val b = R17C9b.battle(R17C9b.wild("", 801, "tackle"))
            b.wild.hp = 1
            R17C9b.round(m, b, "tackle")
            assertTrue(ab, b.player.boosts.all { it == 0 })
        }
    }

    @Test fun berryActivatesOnDamageAndUnnerveBlocksIt() {
        fun run(foeAbility: String): Pair<Int, String?> {
            val m = R17C9b.main("Cheek Pouch", "splash"); m.heldItem = "sitrus-berry"
            val b = R17C9b.battle(R17C9b.wild(foeAbility, 801, "tackle")); m.hp = R17C9b.maxHp(m) / 2 + 1
            R17C9b.round(m, b, null, "tackle")
            return m.hp to m.heldItem
        }
        val allowed = run(""); val blocked = run("Unnerve")
        assertNull(allowed.second); assertEquals("sitrus-berry", blocked.second); assertTrue(allowed.first > blocked.first)
    }

    @Test fun unburdenDoesNotActivateForAnEmptyStart() {
        val m = R17C9b.main("Unburden", "splash"); val b = R17C9b.battle(R17C9b.wild("", 801, "tackle"))
        val r = BattleEngine.Round(R17C9b.data, b, m, 1.0, 1.0, R17C9b.low)
        AbilityAdaptations.onEnter(r, true)
        AbilityAdaptations.afterMove(r, true, R17C9b.data.move("splash")!!, false)
        assertEquals(1.0, AbilityAdaptations.speedMod(r, true), 0.0)
    }

    @Test fun countersSurviveSwitchingButNotNewFights() {
        val m = R17C9b.main("Sniper", "tackle"); val b = R17C9b.battle(R17C9b.wild("", 801, "tackle"))
        repeat(3) { R17C9b.round(m, b, "tackle") }
        b.switchOutMain(R17C9b.data, m)
        fun factor(battle: Battle): Double {
            val v = SimpleView.of(R17C9b.data, battle, m)
            return AbilityAdaptations.finalMod(BattleEngine.newHit(v, true, m, battle.player, battle.wild, battle.enemy, R17C9b.data.move("tackle")!!))
        }
        assertEquals(1.5, factor(b), 0.0)
        assertEquals(1.0, factor(R17C9b.battle(R17C9b.wild("", 801, "tackle"))), 0.0)
    }

    @Test fun iceFaceDoesNotRegenerateJustBySwitching() {
        val m = R17C9b.main("Ice Face", "splash"); val b = R17C9b.battle(R17C9b.wild("", 801, "tackle"))
        val hp = m.hp
        R17C9b.round(m, b, null, "tackle"); assertEquals(hp, m.hp)
        b.switchOutMain(R17C9b.data, m)
        R17C9b.round(m, b, null, "tackle"); assertTrue(m.hp < hp)
    }
    @Test fun poisonThresholdImmediatelyRaisesSoulHeartAndActivatesBerry() {
        val m = R17C9b.main("Soul-Heart", "splash")
        val w = R17C9b.wild("", 801, "splash")
        val b = R17C9b.battle(w)
        w.hp = R17C9b.maxHp(w) / 2 + 1
        w.heldItem = "sitrus-berry"; b.enemy.status = "psn"
        R17C9b.round(m, b, "splash", null)
        assertEquals(1, b.player.boosts[2])
        assertNull(w.heldItem)
        assertTrue(w.hp > R17C9b.maxHp(w) / 2)
    }

}
