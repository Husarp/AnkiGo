package com.husarp.ankigo.game

import com.husarp.ankigo.game.GameEvent.Effect
import com.husarp.ankigo.game.GameEvent.Fainted
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * R18 FIX-1 (PLAN 259, RV-2): a faint ends the round. Once the wild one or the Main has fainted, no rule set's end of round and no
 * burn / poison tick runs (mainline ends a wild battle at the KO; [Game.settleFight] drops the battle in both cases anyway).
 */
class R18FixFaintEndsRoundTest {
    private val data = TestData.world()
    private fun mon(sp: Int, lv: Int, vararg moves: String) = TestData.mon(data, sp, lv, moves.toList())
    private fun round(main: Mon, b: Battle, seed: Int = 1) = BattleEngine.playRound(data, b, main, 1.0, true, Random(seed))
    private fun <T> under(vararg rules: BattleRules, block: () -> T): T = BattleRules.using(rules.toList(), block)

    /** A rule set that counts its end-of-round calls and hurts the Main when it runs. */
    private class Counting : BattleRules {
        var calls = 0
        override fun endOfRound(r: Round) {
            calls++
            r.hurt(true, 3, "The Main is hurt by the weather.", EffectKind.RESIDUAL)
        }
    }

    @Test fun aBurnedMainThatKosTheWildOneIsNotBurnedAfterTheWin() {
        val main = mon(19, 40, "tackle"); val wild = mon(150, 5, "tackle"); wild.hp = 1
        val b = Battle(wild, Tier.NORMAL); b.player.status = "brn"
        val full = main.hp
        val rule = Counting()
        val ev = under(rule) { round(main, b) }
        val faint = ev.indexOfFirst { it is Fainted && !it.player }
        assertTrue("the wild one fainted: $ev", faint >= 0)
        assertEquals("nothing after the KO", emptyList<GameEvent>(), ev.drop(faint + 1))
        assertTrue(ev.filterIsInstance<Effect>().none { it.kind == EffectKind.STATUS_DAMAGE || it.kind == EffectKind.RESIDUAL })
        assertEquals(full, main.hp)
        assertEquals(0, rule.calls)
    }

    @Test fun aBurnedWildOneIsNotBurnedAfterItKosTheMain() {
        val main = mon(19, 5, "tackle"); main.hp = 1
        val wild = mon(150, 40, "tackle")
        val b = Battle(wild, Tier.NORMAL); b.enemy.status = "brn"
        val full = wild.hp
        val rule = Counting()
        val ev = under(rule) { round(main, b) }
        val faint = ev.indexOfFirst { it is Fainted && it.player }
        assertTrue("the Main fainted: $ev", faint >= 0)
        assertEquals("nothing after the KO", emptyList<GameEvent>(), ev.drop(faint + 1))
        assertEquals(full, wild.hp)
        assertEquals(0, rule.calls)
    }

    @Test fun theEndOfRoundStillRunsOnceInARoundWithoutAFaint() {
        val main = mon(19, 40, "tackle"); val wild = mon(150, 20, "tackle")
        val b = Battle(wild, Tier.NORMAL); b.player.status = "brn"
        val rule = Counting()
        val ev = under(rule) { round(main, b) }
        assertTrue(main.hp > 0 && wild.hp > 0)
        assertEquals(1, rule.calls)
        assertEquals(listOf(EffectKind.RESIDUAL, EffectKind.STATUS_DAMAGE), ev.filterIsInstance<Effect>().map { it.kind }.takeLast(2))
        assertFalse(ev.any { it is Fainted })
    }
}
