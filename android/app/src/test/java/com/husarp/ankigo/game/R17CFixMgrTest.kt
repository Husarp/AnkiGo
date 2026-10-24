package com.husarp.ankigo.game

import com.husarp.ankigo.game.R17C5.lines
import com.husarp.ankigo.game.data.Parsers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * R17C FIX (manager, after FIX-B's Shedinja rule): a Pokémon with 1 max HP can't make a Substitute (a quarter of 1 HP is 0, the
 * games make it fail), and a Ghost-type Curse costs it at least 1 HP (so it faints, as in the games). And after FIX-A's Diamond Storm parse:
 * only Diamond Storm's empty `secondary: {}` is dropped; Stone Axe keeps its own, so Sheer Force still boosts it.
 */
class R17CFixMgrTest {
    private val shell = TestData.species(392, "Shell", listOf(Type.BUG, Type.GHOST), TestData.stats(1, 90, 45, 30, 30, 200))
    private val data: GameData =
        TestData.data(TestData.world().species.values.toList() + R17C5.species + shell, TestData.MOVES + R17C5.moves.associateBy { it.id })

    private fun shell(vararg ids: String): Mon = TestData.mon(data, 392, 50, ids.toList())
    private fun wild(): Mon = TestData.mon(data, 301, 100, listOf("splash")).also { it.gender = 'F' }

    private fun round(m: Mon, b: Battle, mainIds: List<String>): List<GameEvent> =
        R17C5.playing(mainIds, listOf("splash")) {
            BattleEngine.playRound(data, b, m, 1.0, true, Random(1), enemyPower = 1.0, moveChoice = MoveChoice.RANDOM)
        }

    @Test fun oneMaxHpCantMakeASubstitute() {
        val m = shell("substitute"); val b = Battle(wild(), Tier.NORMAL)
        assertEquals(1, StatCalc.maxHp(data, m)); assertEquals(1, m.hp)
        val e = round(m, b, listOf("substitute"))
        assertTrue(e.lines().contains("But it does not have enough HP left to make a substitute!"))
        assertNull(b.player.volatiles[SideMoves.SUBSTITUTE])
        assertEquals(1, m.hp)
    }

    @Test fun aGhostCurseCostsAtLeastOneHp() {
        val w = wild(); val m = shell("curse"); val b = Battle(w, Tier.NORMAL)
        round(m, b, listOf("curse"))
        assertEquals("the user pays 1 HP and faints", 0, m.hp)
        assertTrue("the target is still cursed", SideMoves.CURSE in b.enemy.volatiles || w.hp < StatCalc.maxHp(data, w))
    }

    @Test fun onlyDiamondStormLosesItsEmptySecondary() {
        val moves = Parsers.parseMoves("""{
          "diamondstorm": {"num": 591, "accuracy": 95, "basePower": 100, "category": "Physical", "name": "Diamond Storm", "type": "Rock",
            "target": "allAdjacentFoes", "self": {"chance": 50, "boosts": {"def": 2}}, "secondary": {}},
          "stoneaxe": {"num": 830, "accuracy": 90, "basePower": 65, "category": "Physical", "name": "Stone Axe", "type": "Rock",
            "target": "normal", "flags": {"contact": 1}, "secondary": {}}
        }""")
        val ds = moves.getValue("diamondstorm")
        assertEquals(1, ds.allSecondaries.size); assertFalse(ds.secondaryOnHit)
        val axe = moves.getValue("stoneaxe")
        assertEquals("Sheer Force reads allSecondaries", 1, axe.allSecondaries.size); assertTrue(axe.secondaryOnHit)
    }
}
