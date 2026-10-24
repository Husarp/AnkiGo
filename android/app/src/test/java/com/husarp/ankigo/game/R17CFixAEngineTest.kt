package com.husarp.ankigo.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * R17C FIX-A (review items 2, 4 (acts), 5, 6, 8, 10 and the coders' notes): Bide and Rage count only the rounds their user acts in, a free hit
 * clears the round-long conditions it set, Wake-Up Slap clears Rest's sleep count, no Mind Blown cost when Damp stops it, Final Gambit needs a
 * hit, Counter answers what a Substitute took, Lock-On / No Guard reach one-hit KO moves, the Attack event carries the effective type, a called
 * Spit Up ends the stockpile, Spectral Thief / Powder / Parting Shot. Every fight runs under the real rule sets.
 */
class R17CFixAEngineTest {
    private val F = R17CFixA
    private val data = F.data

    private fun List<GameEvent>.attacks() = filterIsInstance<GameEvent.Attack>()
    private fun List<GameEvent>.lines() = filterIsInstance<GameEvent.Effect>().map { it.text }

    // ---------------------------------------------------------------- item 2: Bide, Rage

    @Test fun bideStoresOnlyInTheRoundsItsUserActs() {
        val m = F.main("tackle"); val w = F.wild("bide", "tackle"); val b = F.battle(w)
        val r1 = F.play(m, b, listOf("tackle"), listOf("bide")).single()
        assertTrue("Slowpoke is storing energy!" in r1.lines())
        val d1 = r1.attacks().first { it.byPlayer }.damage
        assertTrue(d1 > 0)
        assertEquals(d1, b.enemy.volatiles["mp.bide.dmg"])
        // three good rounds: the wild one does not act, the hits it takes are NOT stored
        F.play(m, b, listOf("tackle", "tackle", "tackle"), listOf(null, null, null))
        assertEquals(d1, b.enemy.volatiles["mp.bide.dmg"])
        // its second storing round, then the release: double of the two rounds it acted in
        val r5 = F.play(m, b, listOf("tackle"), listOf("bide")).single()
        val d5 = r5.attacks().first { it.byPlayer }.damage
        assertEquals(d1 + d5, b.enemy.volatiles["mp.bide.dmg"])
        val r6 = F.play(m, b, listOf("tackle"), listOf("bide")).single()
        val release = r6.attacks().first { !it.byPlayer }
        assertEquals("bide", release.moveId)
        assertEquals(2 * (d1 + d5), release.damage)
    }

    @Test fun rageGrowsOnlyInTheRoundsItsUserUsesIt() {
        val m = F.main("tackle"); val w = F.wild("rage", "tackle", sp = 903); val b = F.battle(w)
        F.play(m, b, listOf("tackle"), listOf("rage"))     // Zippy rages first, then takes the hit
        assertEquals(1, b.enemy.boosts[0])
        F.play(m, b, listOf("tackle", "tackle", "tackle"), listOf(null, null, null)) // idle rounds: no growth
        assertEquals(1, b.enemy.boosts[0])
        F.play(m, b, listOf("tackle"), listOf("rage"))
        assertEquals(2, b.enemy.boosts[0])
        F.play(m, b, listOf("tackle"), listOf("tackle"))   // another move ends it before the hit
        assertEquals(2, b.enemy.boosts[0])
    }

    // ---------------------------------------------------------------- item 4: who acts

    private class Probe : BattleRules {
        val seen = ArrayList<Triple<Boolean, Boolean, Boolean>>() // (side picking, acts(true), acts(false))
        override fun forcedMove(v: BattleView, p: Boolean): Move? { seen += Triple(p, v.acts(true), v.acts(false)); return null }
    }

    @Test fun aRoundTellsTheHooksWhoActs() {
        val m = F.main("tackle"); val b = F.battle(F.wild("tackle"))
        val probe = Probe()
        BattleRules.using(listOf<BattleRules>(probe) + BattleRules.ALL) {
            BattleEngine.playRound(data, b, m, 1.0, false, Random(1), mainActs = true) // the wild one idles
            BattleEngine.playRound(data, b, m, 1.0, true, Random(1), mainActs = true)
        }
        assertEquals(Triple(true, true, false), probe.seen.first { it.first })           // the Main's pick, round 1: the wild one does not act
        assertTrue(probe.seen.filter { !it.first }.all { it.second && it.third })         // the wild one only picked in round 2: both act
        // without a round, everything acts; the free hit of a switch has no action of the Main
        assertTrue(F.view(m, b).acts(true) && F.view(m, b).acts(false))
        assertFalse(F.view(m, b, mainActs = false).acts(true))
        assertTrue(F.view(m, b, mainActs = false).acts(false))
    }

    @Test fun theFreeHitOfASwitchIsPickedWithoutAnActionOfTheMain() {
        val m = F.main("tackle"); val b = F.battle(F.wild("tackle", "counter"))
        val probe = Probe()
        BattleRules.using(listOf<BattleRules>(probe) + BattleRules.ALL) { BattleEngine.switchHit(data, b, m, Random(1), MoveChoice.SMART, 1.0) }
        assertTrue(probe.seen.isNotEmpty() && probe.seen.all { !it.second }) // acts(Main) = false in every ask of the pick
    }

    // ---------------------------------------------------------------- item 5: a free hit is no round

    private fun afterFreeHit(wildMove: String, wildMoves: List<String> = listOf(wildMove, "tackle"), setup: (Mon, Battle) -> Unit = { _, _ -> }): Pair<Battle, List<GameEvent>> {
        val m = F.main("tackle"); val w = F.wild(*wildMoves.toTypedArray()); val b = F.battle(w)
        setup(w, b)
        val ev = F.freeHit(m, b, wildMove)
        return b to ev
    }

    @Test fun aFreeHitEndsTheGuardsItSet() {
        for (id in listOf("protect", "detect", "endure", "quickguard", "wideguard", "craftyshield")) {
            val (b, ev) = afterFreeHit(id)
            assertTrue("$id was used", ev.lines().isNotEmpty())
            val v = b.enemy.volatiles
            assertTrue("$id: ${v.keys}", SideMoves.PROTECTIONS.none { it in v } && "endure" !in v && "sm.stalled" !in v)
        }
        // so the next real round is not protected against
        val m = F.main("tackle"); val b = F.battle(F.wild("protect", "tackle"))
        F.freeHit(m, b, "protect")
        val r = F.round(m, b, "tackle", null)
        assertTrue(r.attacks().first { it.byPlayer }.damage > 0)
        assertFalse(r.lines().any { "protected itself" in it })
    }

    @Test fun aFreeHitEndsMagicCoatSnatchElectrifyRoostAndIonDeluge() {
        val (b1, _) = afterFreeHit("magiccoat"); assertNull(b1.enemy.volatiles["magiccoat"])
        val (b2, _) = afterFreeHit("snatch"); assertNull(b2.enemy.volatiles["snatch"])
        val (b3, ev3) = afterFreeHit("electrify"); assertNull(b3.player.volatiles["electrify"]); assertTrue(ev3.lines().any { "electrified" in it })
        val (b4, ev4) = afterFreeHit("roost", setup = { w, _ -> w.hp = 10 }); assertNull(b4.enemy.volatiles["roost"]); assertTrue(ev4.lines().any { "regained health" in it })
        val (b5, ev5) = afterFreeHit("iondeluge"); assertNull(b5.field.rooms["iondeluge"]); assertTrue(ev5.lines().any { "ions" in it })
        val (b6, ev6) = afterFreeHit("swordsdance"); assertNull(b6.enemy.notes["cr.raised"]); assertTrue(ev6.lines().any { "rose" in it })
    }

    @Test fun aRoundStillKeepsTheCountdownsItAlwaysHad() {
        // a real round ends the guards by itself (endOfRound), the free hit by afterFreeHit: both leave nothing behind
        val m = F.main("tackle"); val b = F.battle(F.wild("protect", "tackle"))
        F.play(m, b, listOf("tackle"), listOf("protect"))
        assertTrue(SideMoves.PROTECTIONS.none { it in b.enemy.volatiles })
    }

    // ---------------------------------------------------------------- item 6: Wake-Up Slap

    @Test fun wakeUpSlapEndsRestsFixedSleep() {
        val m = F.main("wakeupslap"); val b = F.battle(F.wild("tackle"))
        b.enemy.status = "slp"; b.enemy.statusTurns = 3; b.enemy.volatiles["sleepTurns"] = 2
        val ev = F.round(m, b, "wakeupslap", null)
        assertTrue(ev.attacks().first { it.byPlayer }.damage > 0)
        assertNull(b.enemy.status); assertEquals(0, b.enemy.statusTurns); assertNull(b.enemy.volatiles["sleepTurns"])
        assertTrue("Slowpoke woke up!" in ev.lines())
    }

    // ---------------------------------------------------------------- item 8: Damp and Mind Blown

    @Test fun noMindBlownCostWhenDampStopsIt() {
        val m = F.main("mindblown"); val b = F.battle(F.wild("tackle", ability = "damp"))
        val ev = F.round(m, b, "mindblown", null)
        assertTrue(ev.attacks().first { it.byPlayer }.failed)
        assertEquals(F.maxHp(m), m.hp)
        assertTrue(ev.lines().any { "Damp prevents" in it })
        // without Damp the cost is paid
        val m2 = F.main("mindblown"); val b2 = F.battle(F.wild("tackle"))
        F.round(m2, b2, "mindblown", null)
        assertEquals(F.maxHp(m2) - (F.maxHp(m2) + 1) / 2, m2.hp)
    }

    // ---------------------------------------------------------------- item 10: Final Gambit

    @Test fun finalGambitNeedsAHit() {
        val m = F.main("finalgambit"); val b = F.battle(F.wild("tackle", sp = 902)) // a Ghost type: immune to Fighting
        val ev = F.round(m, b, "finalgambit", null)
        assertTrue(m.hp > 0); assertEquals(0, ev.attacks().first { it.byPlayer }.damage)
        val m2 = F.main("finalgambit"); val w2 = F.wild("tackle"); val b2 = F.battle(w2)
        val hp = m2.hp
        val ev2 = F.round(m2, b2, "finalgambit", null)
        assertEquals(0, m2.hp)
        assertEquals(hp, ev2.attacks().first { it.byPlayer }.damage) // the user's HP as damage
        // Explosion ("always") still takes its user down
        val m3 = F.main("explosion"); F.round(m3, F.battle(F.wild("tackle")), "explosion", null)
        assertEquals(0, m3.hp)
    }

    // ---------------------------------------------------------------- Counter and a Substitute

    @Test fun counterAnswersWhatASubstituteTook() {
        val m = F.main("counter", "tackle"); val b = F.battle(F.wild("tackle"))
        b.player.volatiles["substitute"] = 1000
        val ev = F.round(m, b, "counter", "tackle")
        val taken = 1000 - (b.player.volatiles["substitute"] ?: 0)
        assertTrue(taken > 0)
        assertEquals(m.hp, F.maxHp(m)) // the Pokémon itself took nothing
        val counter = ev.attacks().first { it.byPlayer }
        assertEquals("counter", counter.moveId)
        assertEquals(2 * taken, counter.damage)
    }

    // ---------------------------------------------------------------- Lock-On / No Guard and one-hit KO moves

    @Test fun lockOnAndNoGuardReachOneHitKoMovesButTheLevelRuleStays() {
        fun fight(main: Mon, setup: (Battle) -> Unit = {}): GameEvent.Attack {
            val b = F.battle(F.wild("tackle", lv = 40)); setup(b)
            return F.play(main, b, listOf("sheercold"), listOf(null), rng = F.high).single().attacks().first { it.byPlayer }
        }
        assertTrue("plain: 40 % and a high roll misses", fight(F.main("sheercold")).missed)
        val locked = fight(F.main("sheercold")) { it.player.volatiles["lockon"] = 0 } // "its next action is the one at this action count"
        assertFalse(locked.missed); assertTrue(locked.damage > 0)
        assertFalse(fight(F.main("sheercold", ability = "noguard")).missed)
        val wildNoGuard = F.battle(F.wild("tackle", lv = 40, ability = "noguard")) // the target's No Guard too
        assertFalse(F.play(F.main("sheercold"), wildNoGuard, listOf("sheercold"), listOf(null), rng = F.high).single().attacks().first { it.byPlayer }.missed)
        // Telekinesis does not make a one-hit KO sure
        assertTrue(fight(F.main("sheercold")) { it.enemy.volatiles["telekinesis"] = 3 }.missed)
        // a higher-level target still fails the move, whatever the aim
        val b = F.battle(F.wild("tackle", lv = 60)); b.player.volatiles["lockon"] = 0
        val high = F.play(F.main("sheercold"), b, listOf("sheercold"), listOf(null), rng = F.low).single()
        assertTrue(high.attacks().first { it.byPlayer }.failed); assertTrue("But it failed!" in high.lines())
    }

    @Test fun smartestKnowsAOneHitKoThatNeverMissesIsSure() {
        val m = F.main("sheercold"); val b = F.battle(F.wild("tackle", lv = 40))
        val plain = MoveChooser.expectedDamage(F.view(m, b), true, data.move("sheercold")!!)
        b.player.volatiles["lockon"] = b.player.actions // the next action
        val sure = MoveChooser.expectedDamage(F.view(m, b), true, data.move("sheercold")!!)
        assertTrue("$sure vs $plain", sure > plain * 2) // 100 % instead of 40 %
    }

    // ---------------------------------------------------------------- the Attack event carries the effective type

    @Test fun theAttackEventShowsTheEffectiveType() {
        fun typeOf(ability: String, move: String): Type? {
            val m = F.main(move, ability = ability); val b = F.battle(F.wild("tackle"))
            return F.round(m, b, move, null).attacks().first { it.byPlayer }.moveType
        }
        assertEquals(Type.NORMAL, typeOf("", "tackle"))
        assertEquals(Type.FAIRY, typeOf("pixilate", "tackle"))
        assertEquals(Type.ELECTRIC, typeOf("galvanize", "tackle"))
        assertEquals(Type.NORMAL, typeOf("normalize", "shadowball"))
        assertEquals(Type.GHOST, typeOf("", "shadowball"))
        // Electrify (the faster wild one) turns the Main's move Electric
        val m = F.main("tackle"); val b = F.battle(F.wild("electrify", "tackle", sp = 903))
        val ev = F.play(m, b, listOf("tackle"), listOf("electrify"), rng = F.low).single()
        assertEquals(Type.ELECTRIC, ev.attacks().first { it.byPlayer }.moveType)
    }

    // ---------------------------------------------------------------- a called Spit Up

    @Test fun aCalledSpitUpEndsTheStockpile() {
        val m = F.main("metronome", "stockpile", "spitup"); val b = F.battle(F.wild("tackle"))
        F.round(m, b, "stockpile", null)
        assertEquals(1, b.player.volatiles["stockpile"])
        assertEquals(1, b.player.boosts[1])
        // Spit Up called by another move (the call path of Metronome / Sleep Talk / Copycat)
        BattleRules.using(BattleRules.ALL) {
            val r = BattleEngine.Round(data, b, m, 1.0, 1.0, F.low)
            assertTrue(r.useMove(true, data.move("spitup")!!))
        }
        assertNull(b.player.volatiles["stockpile"])
        assertEquals(0, b.player.boosts[1]) // the Defense it gave is taken back
    }

    // ---------------------------------------------------------------- Spectral Thief

    @Test fun spectralThiefTakesTheTargetsRaisesBeforeItHits() {
        fun fight(raised: Boolean): Triple<Mon, Battle, GameEvent.Attack> {
            val m = F.main("spectralthief"); val b = F.battle(F.wild("tackle", sp = 902)) // a Ghost type: Ghost moves are super effective
            if (raised) { b.enemy.boosts[0] = 2; b.enemy.boosts[4] = 1; b.enemy.boosts[1] = -1 }
            val ev = F.round(m, b, "spectralthief", null)
            return Triple(m, b, ev.attacks().first { it.byPlayer })
        }
        val (_, plain, hitPlain) = fight(false)
        val (_, b, hit) = fight(true)
        assertEquals(2, b.player.boosts[0]); assertEquals(1, b.player.boosts[4]); assertEquals(0, b.player.boosts[1])
        assertEquals(0, b.enemy.boosts[0]); assertEquals(0, b.enemy.boosts[4]); assertEquals(-1, b.enemy.boosts[1]) // only the raises go
        assertTrue(plain.player.boosts.all { it == 0 })
        assertTrue("the stolen Attack counts in the hit: ${hit.damage} vs ${hitPlain.damage}", hit.damage > hitPlain.damage)
    }

    @Test fun spectralThiefStealsNothingWhenItMisses() {
        val m = F.main("spectralthief"); val b = F.battle(F.wild("tackle"))
        b.enemy.boosts[0] = 2
        b.enemy.volatiles["protect"] = 1 // the hit is blocked
        F.round(m, b, "spectralthief", null)
        assertEquals(2, b.enemy.boosts[0]); assertEquals(0, b.player.boosts[0])
    }

    // ---------------------------------------------------------------- Powder, Parting Shot

    @Test fun powderMakesTheTargetsFireMoveFailAndHurtIt() {
        val m = F.main("powder", "tackle"); val w = F.wild("flamethrower", "tackle", sp = 903); val b = F.battle(w)
        val ev = F.round(m, b, "powder", "flamethrower")
        // Powder (priority +1) first, then the wild one's Fire move fails and the powder explodes
        assertTrue("Zippy is covered in powder!" in ev.lines())
        assertTrue(ev.lines().any { "exploded" in it })
        assertTrue(ev.attacks().none { !it.byPlayer && it.moveId == "flamethrower" })
        assertEquals(F.maxHp(m), m.hp)
        val quarter = (F.maxHp(w) + 2) / 4
        assertEquals(F.maxHp(w) - quarter, w.hp)
        assertNull(b.enemy.volatiles["powder"]) // used up
    }

    @Test fun powderLeavesOtherMovesAndGrassTypesAlone() {
        val m = F.main("powder", "tackle"); val w = F.wild("tackle", sp = 903); val b = F.battle(w)
        val ev = F.round(m, b, "powder", "tackle")
        assertTrue(ev.attacks().any { !it.byPlayer && it.moveId == "tackle" && it.damage > 0 })
        assertNull("it ends with the round", b.enemy.volatiles["powder"])
        val b2 = F.battle(F.wild("tackle", sp = 907))
        val ev2 = F.round(F.main("powder"), b2, "powder", null)
        assertTrue(ev2.attacks().first { it.byPlayer }.failed); assertNull(b2.enemy.volatiles["powder"])
    }

    @Test fun partingShotLowersAttackAndSpAtkAndDoesNotSwitch() {
        val m = F.main("partingshot"); val b = F.battle(F.wild("tackle"))
        val ev = F.round(m, b, "partingshot", null)
        assertEquals(-1, b.enemy.boosts[0]); assertEquals(-1, b.enemy.boosts[2])
        assertFalse(ev.attacks().first { it.byPlayer }.failed)
        // at -6 / -6 it fails
        val b2 = F.battle(F.wild("tackle")); b2.enemy.boosts[0] = -6; b2.enemy.boosts[2] = -6
        assertTrue(F.round(F.main("partingshot"), b2, "partingshot", null).attacks().first { it.byPlayer }.failed)
    }
}
