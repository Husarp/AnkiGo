package com.husarp.ankigo.game

import com.husarp.ankigo.game.R17C3.attacks
import com.husarp.ankigo.game.R17C3.cantMove
import com.husarp.ankigo.game.R17C3.effects
import com.husarp.ankigo.game.R17C3.high
import com.husarp.ankigo.game.R17C3.kinds
import com.husarp.ankigo.game.R17C3.lines
import com.husarp.ankigo.game.R17C3.low
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * R17C-3 (PLAN 267, Adam's rule): charge moves charge on the first use and the user's VERY NEXT action is the hit; recharge moves rest after a hit.
 * Fixtures in [R17C3]: the Main "Fastie" moves before the wild "Slowpoke".
 */
class R17C3ChargeTest {
    private val data = R17C3.data
    private fun main(vararg m: String) = R17C3.main(*m)
    private fun wild(vararg m: String) = R17C3.wild(*m)
    private fun round(main: Mon, b: Battle, rng: Random = Random(1), enemyActs: Boolean = false, choice: MoveChoice = MoveChoice.RANDOM) =
        R17C3.round(main, b, rng, enemyActs = enemyActs, choice = choice)

    // ---------------------------------------------------------------- charge moves

    @Test fun theFirstUseOnlyChargesAndTheNextActionIsTheHit() {
        val m = main("skullbash"); val w = wild("splash"); val b = R17C3.battle(w)
        val hp = w.hp
        val e1 = round(m, b)
        assertEquals(hp, w.hp); assertTrue(e1.attacks().isEmpty())
        assertEquals("skullbash", b.player.charging)
        assertEquals(1, e1.kinds(EffectKind.CHARGE).size)
        assertEquals("Fastie tucked in its head!", e1.kinds(EffectKind.CHARGE).single().text)
        val e2 = round(m, b)
        assertEquals(1, e2.attacks().size); assertTrue(w.hp < hp)
        assertNull("the charge is over after the hit", b.player.charging)
    }

    @Test fun theNextActionIsTheHitWhateverElseTheUserKnows() {
        for (choice in MoveChoice.entries) for (seed in 1..25) {
            val m = main("solarbeam", "tackle", "swordsdance"); val w = wild("splash"); val b = R17C3.battle(w)
            b.player.charging = "solarbeam"
            val a = round(m, b, Random(seed), choice = choice).attacks().single()
            assertEquals("$choice $seed", "Solar Beam", a.move)
            assertTrue(a.damage > 0)
        }
    }

    @Test fun theHitIsNotForcedAgainAndTheUserPicksFreelyAfterIt() {
        val picked = HashSet<String>()
        for (seed in 1..40) {
            val m = main("solarbeam", "tackle"); val b = R17C3.battle(wild("splash"))
            b.player.charging = "solarbeam"
            round(m, b, Random(seed))
            assertNull(b.player.charging)
            R17C3.round(m, b, Random(seed + 100), enemyActs = false).attacks().forEach { picked += it.move }
        }
        assertTrue("it can pick Tackle and a fresh Solar Beam (which then only charges): $picked", "Tackle" in picked)
    }

    @Test fun aStatusChargeMoveTakesEffectOnTheNextAction() {
        val m = main("geomancy"); val b = R17C3.battle(wild("splash"))
        R17C3.withRules(R17C3.forcing("geomancy")) {
            val e1 = round(m, b)
            assertEquals("geomancy", b.player.charging)
            assertEquals(0, b.player.boosts[2]); assertEquals(0, b.player.boosts[4])
            assertEquals("Fastie is absorbing power!", e1.kinds(EffectKind.CHARGE).single().text)
            val e2 = round(m, b)
            assertEquals(2, b.player.boosts[2]); assertEquals(2, b.player.boosts[3]); assertEquals(2, b.player.boosts[4])
            assertEquals(1, e2.attacks().size)
            assertNull(b.player.charging)
        }
    }

    @Test fun theChargeTurnOfSkullBashMeteorBeamAndElectroShotRaisesAStat() {
        val cases = mapOf("skullbash" to 1, "meteorbeam" to 2, "electroshot" to 2) // stage index: def, spa, spa
        for ((id, idx) in cases) {
            val m = main(id); val b = R17C3.battle(wild("splash"))
            val e = round(m, b)
            assertEquals(id, 1, b.player.boosts[idx])
            assertEquals("the charge line first, the rise after it", EffectKind.CHARGE, e.effects().first().kind)
            assertEquals(EffectKind.STAT_CHANGE, e.effects()[1].kind)
        }
        // a charge move without a rise raises nothing
        val m = main("solarbeam"); val b = R17C3.battle(wild("splash")); round(m, b)
        assertTrue(b.player.boosts.all { it == 0 })
    }

    @Test fun aRuleThatSkipsTheChargeLetsItHitAtOnceAndTheRiseStillHappens() {
        val skip = object : BattleRules { override fun skipsCharge(r: Round, p: Boolean, move: Move) = move.id == "skullbash" }
        R17C3.withRules(skip) {
            val m = main("skullbash"); val w = wild("splash"); val b = R17C3.battle(w)
            val hp = w.hp
            val e = round(m, b)
            assertTrue(w.hp < hp); assertEquals(1, e.attacks().size)
            assertNull(b.player.charging)
            assertEquals(1, b.player.boosts[1])
        }
    }

    @Test fun theHitHasNormalAccuracyAndAMissStillEndsTheCharge() {
        val m = main("bounce"); val w = wild("splash"); val b = R17C3.battle(w)
        round(m, b, high)
        assertEquals("bounce", b.player.charging)
        val e = round(m, b, high)
        assertTrue(e.attacks().single().missed)
        assertNull("a miss ends the charge too", b.player.charging)
    }

    @Test fun aFailedHitEndsTheChargeToo() {
        val m = main("fly"); val w = wild("splash"); val b = R17C3.battle(w)
        round(m, b)
        val protect = object : BattleRules { override fun blocks(r: Round, p: Boolean, move: Move): Boolean { if (p && move.id == "fly") { r.fx(!p, "Slowpoke protected itself!"); return true }; return false } }
        R17C3.withRules(protect) {
            val e = round(m, b)
            assertTrue(e.attacks().single().failed)
        }
        assertNull(b.player.charging)
    }

    @Test fun theChargeIsLostWhenTheUserCannotActOnThatAction() {
        fun lost(name: String, rng: Random, prepare: (Mon, Battle) -> Unit): GameEvent? {
            val m = main("solarbeam"); val w = wild("splash"); val b = R17C3.battle(w)
            b.player.charging = "solarbeam"
            prepare(m, b)
            val hp = w.hp
            val e = round(m, b, rng)
            assertTrue("$name: no attack", e.attacks().isEmpty())
            assertEquals("$name: no damage", hp, w.hp)
            assertNull("$name: the charge is lost", b.player.charging)
            return e.firstOrNull { it is GameEvent.Effect && it.kind == EffectKind.CANT_MOVE }
        }
        assertEquals("slp", (lost("asleep", high) { _, b -> b.player.status = "slp" } as GameEvent.Effect).stat)
        assertEquals("frz", (lost("frozen", high) { _, b -> b.player.status = "frz" } as GameEvent.Effect).stat)
        assertEquals("par", (lost("paralysed", low) { _, b -> b.player.status = "par" } as GameEvent.Effect).stat)
        assertEquals("flinch", (lost("flinched", high) { _, b -> b.player.volatiles["flinch"] = 1 } as GameEvent.Effect).stat)
        assertEquals("confusion", (lost("confused", low) { _, b -> b.player.volatiles["confusion"] = 4 } as GameEvent.Effect).stat)
        assertEquals("attract", (lost("in love", low) { _, b -> b.player.volatiles["attract"] = 1 } as GameEvent.Effect).stat)
    }

    @Test fun aChargeIsLostWhenTheMoveIsBlocked() {
        val m = main("solarbeam"); val b = R17C3.battle(wild("splash"))
        b.player.charging = "solarbeam"
        val taunt = object : BattleRules { override fun moveBlocked(v: BattleView, p: Boolean, move: Move): String? = if (p) "after the taunt" else null }
        R17C3.withRules(taunt) {
            val e = round(m, b)
            assertTrue("Fastie can't use Solar Beam after the taunt!" in e.lines())
        }
        assertNull(b.player.charging)
    }

    @Test fun aSwitchedOutMainLosesItsCharge() {
        val m = main("solarbeam"); val b = R17C3.battle(wild("splash"))
        b.player.charging = "solarbeam"
        b.enemy.charging = "fly"
        b.switchOutMain(data, m)
        assertNull(b.player.charging)
        assertEquals("the wild one keeps its charge", "fly", b.enemy.charging)
    }

    @Test fun thereIsNoUntouchableTurn() {
        // Dig charging: the wild one's attack still lands in the charge round (Tuning's reason: an answer must never miss because of it)
        val m = main("dig"); val w = wild("tackle"); val b = R17C3.battle(w)
        val hp = m.hp
        val e = R17C3.round(m, b, Random(1), enemyActs = true)
        assertEquals("dig", b.player.charging)
        assertTrue(m.hp < hp)
        assertEquals(1, e.attacks().count { !it.byPlayer })
    }

    @Test fun theWildOneChargesAndHitsLikeTheMain() {
        val m = main("tackle"); val w = wild("solarbeam"); val b = R17C3.battle(w)
        val e1 = R17C3.round(m, b, Random(1), enemyActs = true)
        assertEquals("solarbeam", b.enemy.charging)
        assertTrue(e1.attacks().none { !it.byPlayer })
        val hp = m.hp
        val e2 = R17C3.round(m, b, Random(1), enemyActs = true)
        assertTrue(m.hp < hp); assertNull(b.enemy.charging)
        assertEquals("Solar Beam", e2.attacks().single { !it.byPlayer }.move)
    }

    @Test fun theWildOneReleasesItsChargeInASwitchsFreeHit() {
        val m = main("tackle"); val w = wild("fly", "tackle"); val b = R17C3.battle(w)
        b.enemy.charging = "fly"
        for (choice in MoveChoice.entries) {
            b.enemy.charging = "fly"
            val hp = m.hp
            val e = BattleEngine.switchHit(data, b, m, Random(3), choice)
            assertEquals("$choice", "Fly", e.attacks().single().move)
            assertTrue(m.hp < hp)
            assertNull(b.enemy.charging)
            m.hp = hp
        }
    }

    @Test fun aSwitchsFreeHitLeavesAChargeMoveOutUntilItIsCharged() {
        val m = main("tackle"); val w = wild("fly", "tackle"); val b = R17C3.battle(w)
        for (choice in MoveChoice.entries) for (seed in 1..30) {
            val move = BattleEngine.pickSwitchHitMove(data, b, m, Random(seed), choice)
            assertEquals("$choice $seed", "tackle", move.id)
        }
        // with nothing else the wild one spends the hit charging
        val w2 = wild("fly"); val b2 = R17C3.battle(w2)
        val e = BattleEngine.switchHit(data, b2, m, Random(1), MoveChoice.SMART)
        assertEquals("fly", b2.enemy.charging)
        assertTrue(e.attacks().isEmpty()); assertEquals(1, e.kinds(EffectKind.CHARGE).size)
    }

    @Test fun aRuleThatSkipsTheChargeMakesTheChargeMoveUsableInAFreeHit() {
        val skip = object : BattleRules { override fun skipsCharge(r: Round, p: Boolean, move: Move) = true }
        R17C3.withRules(skip) {
            val m = main("tackle"); val w = wild("solarbeam", "tackle"); val b = R17C3.battle(w)
            val picked = (1..40).map { BattleEngine.pickSwitchHitMove(data, b, m, Random(it), MoveChoice.RANDOM).id }.toSet()
            assertEquals(setOf("solarbeam", "tackle"), picked)
        }
    }

    // ---------------------------------------------------------------- recharge moves

    @Test fun afterAHitTheNextActionIsARestTurn() {
        val m = main("gigaimpact"); val w = wild("splash"); val b = R17C3.battle(w)
        val e1 = round(m, b, high, choice = MoveChoice.SMART)
        assertEquals("Giga Impact", e1.attacks().single().move)
        assertEquals(1, b.player.volatiles["mustrecharge"])
        val hp = w.hp
        val e2 = round(m, b, high, choice = MoveChoice.SMART)
        assertTrue(e2.attacks().isEmpty())
        assertEquals(hp, w.hp)
        assertEquals(listOf("Fastie must recharge!"), e2.cantMove("recharge").map { it.text })
        assertNull(b.player.volatiles["mustrecharge"])
        val e3 = round(m, b, high, choice = MoveChoice.SMART)
        assertEquals(1, e3.attacks().size)
    }

    @Test fun theRestTurnIsForcedInEveryMode() {
        for (choice in MoveChoice.entries) {
            val m = main("gigaimpact", "tackle"); val b = R17C3.battle(wild("splash"))
            b.player.volatiles["mustrecharge"] = 1
            assertSame(CoreRules.RECHARGE, MoveChooser.pickMain(choice, data, b, m, Random(2)))
            val e = round(m, b, Random(2), choice = choice)
            assertTrue("$choice", e.attacks().isEmpty())
            assertEquals("$choice", 1, e.cantMove("recharge").size)
        }
    }

    @Test fun aMissNeedsNoRecharge() {
        val m = main("hyperbeam"); val b = R17C3.battle(wild("splash"))
        val e = round(m, b, high)
        assertTrue(e.attacks().single().missed)
        assertNull(b.player.volatiles["mustrecharge"])
    }

    @Test fun anImmuneTargetNeedsNoRecharge() {
        val m = main("gigaimpact"); val b = R17C3.battle(R17C3.wild("splash", sp = 305)) // Normal vs Ghost
        R17C3.withRules(R17C3.forcing("gigaimpact")) { // (a Main that can't hurt the target would use Struggle instead)
            val e = round(m, b, high)
            assertEquals(0.0, e.attacks().single().effectiveness, 0.0)
            assertNull(b.player.volatiles["mustrecharge"])
        }
    }

    @Test fun aBlockedMoveNeedsNoRecharge() {
        val m = main("gigaimpact"); val b = R17C3.battle(wild("splash"))
        val protect = object : BattleRules { override fun blocks(r: Round, p: Boolean, move: Move): Boolean { if (p) { r.fx(!p, "Slowpoke protected itself!"); return true }; return false } }
        R17C3.withRules(protect) { round(m, b, high) }
        assertNull(b.player.volatiles["mustrecharge"])
    }

    @Test fun theRestComesBeforeSleepAndFlinch() {
        val m = main("gigaimpact"); val b = R17C3.battle(wild("splash"))
        b.player.volatiles["mustrecharge"] = 1; b.player.status = "slp"; b.player.volatiles["flinch"] = 1
        val e = round(m, b, high)
        assertEquals(1, e.cantMove("recharge").size)
        assertTrue(e.cantMove("slp").isEmpty())
    }

    @Test fun theWildOneRechargesToo() {
        val m = R17C3.mon(300, 100, "tackle"); val w = wild("gigaimpact"); val b = R17C3.battle(w)
        R17C3.round(m, b, high, enemyActs = true)
        assertEquals(1, b.enemy.volatiles["mustrecharge"])
        val e = R17C3.round(m, b, high, enemyActs = true)
        assertTrue(e.attacks().none { !it.byPlayer })
        assertEquals(1, e.cantMove("recharge").count { !it.onPlayer })
    }

    @Test fun theRechargeFlagOfTheDataIsEnough() {
        val flagged = Move("flagged", "Flagged", Type.NORMAL, Category.PHYSICAL, 90, 100, flags = setOf("recharge"))
        assertTrue(CoreRules.isRecharge(flagged))
        assertTrue(CoreRules.isRecharge(data.move("hyperbeam")!!))
        assertFalse(CoreRules.isRecharge(data.move("tackle")!!))
    }
}
