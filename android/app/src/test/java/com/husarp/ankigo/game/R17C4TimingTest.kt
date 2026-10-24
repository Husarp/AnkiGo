package com.husarp.ankigo.game

import com.husarp.ankigo.game.GameEvent.Attack
import com.husarp.ankigo.game.GameEvent.Effect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * R17C-4: the rules that need a running round: fail and timing rules, the Counter family, Future Sight, the build-ups (Echoed Voice,
 * Rollout, Triple Axel, Beat Up, Rage, Bide), the doubles that read the round (Revenge, Payback, Lash Out ...) and the move choice.
 * Every fight runs with only [MovePower] (plus a script that picks the moves), so other rule sets cannot change the numbers.
 */
open class R17C4TimingTest {
    private val F = R17C4Fixture

    /**
     * The rule sets in play (the script first). Here only [MovePower], so no other set can change the numbers; [R17CFixATimingFullTest] runs every test
     * of this class once more under the full [BattleRules.ALL] (R17C FIX-A, review item 18: Fake Out's flinch, Sucker Punch against Protect, Counter
     * against a Substitute ... meet the other sets).
     */
    internal open fun rulesFor(script: R17C4Fixture.Script): List<BattleRules> = listOf(script, MovePower)
    private val data = F.data()

    private fun mon(sp: Species, vararg moves: String, lv: Int = 50) = TestData.mon(data, sp.num, lv, moves.toList().ifEmpty { listOf("tackle") })

    /** One fight: [main] against [wild]; each [round] scripts both moves. The random numbers are all the lowest ones unless given. */
    private inner class Fight(val main: Mon, val wild: Mon, bigWild: Boolean = false, given: Battle? = null) {
        val battle = given ?: Battle(wild, Tier.NORMAL)
        val script = R17C4Fixture.Script()
        init { if (bigWild) { battle.wildMaxHp = 100_000; wild.hp = 100_000 } }

        fun round(mainMove: String? = null, wildMove: String? = null, enemyActs: Boolean = true, mainActs: Boolean = true, rng: Random = R17C4Fixture.ScriptedRandom()): List<GameEvent> {
            if (mainActs) script.main.addLast(mainMove)
            if (enemyActs) script.wild.addLast(wildMove)
            return under { BattleEngine.playRound(data, battle, main, 1.0, enemyActs, rng, 1.0, mainActs) }
        }

        fun <T> under(block: () -> T): T = BattleRules.using(rulesFor(script), block)
    }

    private fun List<GameEvent>.attacks() = filterIsInstance<Attack>()
    private fun List<GameEvent>.effects() = filterIsInstance<Effect>()
    private fun List<GameEvent>.lines() = effects().map { it.text }
    private fun List<GameEvent>.main() = attacks().first { it.byPlayer }
    private fun List<GameEvent>.wild() = attacks().first { !it.byPlayer }

    // ---------------------------------------------------------------- Fake Out, First Impression

    @Test fun fakeOutAndFirstImpressionWorkOnlyOnTheFirstAction() {
        for (id in listOf("fakeout", "firstimpression")) {
            val f = Fight(mon(F.HITTER), mon(F.TANK))
            val r1 = f.round(id, enemyActs = false)
            assertTrue(id, r1.main().damage > 0)
            val r2 = f.round(id, enemyActs = false)
            assertTrue(id, r2.main().failed); assertTrue("But it failed!" in r2.lines())
            // switched in again: a fresh side, a first action again
            f.battle.switchOutMain(data, f.main)
            assertTrue(id, f.round(id, enemyActs = false).main().damage > 0)
        }
    }

    @Test fun theWildOneUsesFakeOutOnceAndASwitchDoesNotGiveItBack() {
        val f = Fight(mon(F.TANK), mon(F.HITTER, "fakeout"))
        assertTrue(f.round(null, "fakeout", mainActs = false).wild().damage > 0)
        assertTrue(f.round(null, "fakeout", mainActs = false).wild().failed)
        f.battle.switchOutMain(data, f.main)
        assertTrue(f.round(null, "fakeout", mainActs = false).wild().failed) // the wild one has been out all along
    }

    @Test fun aWildFirstActionIsItsFreeHitAfterASwitch() {
        val f = Fight(mon(F.TANK), mon(F.HITTER, "fakeout"))
        f.script.wild.addLast("fakeout")
        val hit = f.under { BattleEngine.switchHit(data, f.battle, f.main, R17C4Fixture.ScriptedRandom()) }
        assertTrue(hit.attacks().single().damage > 0) // its first action in the fight
        assertEquals(1, f.battle.enemy.actions)
    }

    // ---------------------------------------------------------------- Sucker Punch, Thunderclap

    @Test fun suckerPunchAndThunderclapNeedATargetAboutToAttack() {
        for (id in listOf("suckerpunch", "thunderclap")) {
            // the target attacks and has not moved: it works (the user is slower, but priority +1 sends it first)
            assertTrue(id, Fight(mon(F.TANK), mon(F.HITTER)).round(id, "tackle").main().damage > 0)
            // the target picked a status move
            assertTrue(id, Fight(mon(F.TANK), mon(F.HITTER)).round(id, "growl").main().failed)
            // the target does nothing this round
            val idle = Fight(mon(F.TANK), mon(F.HITTER)).round(id, enemyActs = false)
            assertTrue(id, idle.main().failed); assertTrue("But it failed!" in idle.lines())
        }
    }

    @Test fun suckerPunchFailsWhenTheTargetMovedFirst() {
        // Quick Attack also has +1 priority and the wild one is faster: it moves first
        val f = Fight(mon(F.TANK), mon(F.FAST))
        val ev = f.round("suckerpunch", "quickattack")
        assertEquals(listOf(false, true), ev.attacks().map { it.byPlayer })
        assertTrue(ev.main().failed)
    }

    @Test fun suckerPunchOnTheFreeHitOfASwitchFails() {
        val f = Fight(mon(F.TANK), mon(F.HITTER, "suckerpunch"))
        f.script.wild.addLast("suckerpunch")
        val hit = f.under { BattleEngine.switchHit(data, f.battle, f.main, R17C4Fixture.ScriptedRandom()) }
        assertTrue(hit.attacks().single().failed)
        assertEquals(f.main.hp, StatCalc.maxHp(data, f.main))
    }

    // ---------------------------------------------------------------- Focus Punch

    @Test fun focusPunchLosesItsFocusWhenHitFirst() {
        val f = Fight(mon(F.TANK), mon(F.HITTER))
        val hp = f.wild.hp
        val ev = f.round("focuspunch", "tackle")
        assertTrue("Tank lost its focus and couldn't move!" in ev.lines())
        assertEquals(EffectKind.CANT_MOVE, ev.effects().first { it.text.contains("focus") }.kind)
        assertTrue(ev.attacks().none { it.byPlayer })
        assertEquals(hp, f.wild.hp)
        // nobody hit it: the punch lands
        val g = Fight(mon(F.TANK), mon(F.HITTER))
        assertTrue(g.round("focuspunch", enemyActs = false).main().damage > 0)
    }

    // ---------------------------------------------------------------- Dream Eater, Snore, Poltergeist, Synchronoise, Last Resort

    @Test fun dreamEaterNeedsASleepingTarget() {
        val f = Fight(mon(F.HITTER), mon(F.TANK))
        assertTrue(f.round("dreameater", enemyActs = false).main().failed)
        f.battle.enemy.status = "slp"
        val ev = f.round("dreameater", enemyActs = false)
        assertTrue(ev.main().damage > 0)
    }

    @Test fun snoreNeedsASleepingUser() {
        val f = Fight(mon(F.HITTER), mon(F.TANK))
        assertTrue(f.round("snore", enemyActs = false).main().failed)
        f.battle.player.status = "slp"
        val ev = f.round("snore", enemyActs = false, rng = R17C4Fixture.ScriptedRandom(0.9)) // 0.9: it does not wake up
        assertTrue(ev.main().damage > 0)
        assertEquals("slp", f.battle.player.status) // it keeps sleeping
    }

    @Test fun poltergeistNeedsAnItemOnTheTarget() {
        val f = Fight(mon(F.HITTER), mon(F.TANK))
        val ev = f.round("poltergeist", enemyActs = false)
        assertTrue(ev.main().failed); assertTrue("But it failed!" in ev.lines())
        f.wild.heldItem = "oran-berry"
        assertTrue(f.round("poltergeist", enemyActs = false).main().damage > 0)
    }

    @Test fun synchronoiseNeedsASharedType() {
        assertTrue(Fight(mon(F.HITTER), mon(F.TANK)).round("synchronoise", enemyActs = false).main().failed)
        assertTrue(Fight(mon(F.HITTER), mon(F.FLYER)).round("synchronoise", enemyActs = false).main().damage > 0) // both Normal
    }

    @Test fun lastResortNeedsEveryOtherMoveUsedFirst() {
        val f = Fight(mon(F.HITTER, "lastresort", "tackle", "growl"), mon(F.TANK), bigWild = true)
        assertTrue(f.round("lastresort", enemyActs = false).main().failed)   // nothing used yet
        f.round("tackle", enemyActs = false)
        assertTrue(f.round("lastresort", enemyActs = false).main().failed)   // Growl is still unused
        f.round("growl", enemyActs = false)
        assertTrue(f.round("lastresort", enemyActs = false).main().damage > 0)
        // with no other move it never works
        val alone = Fight(mon(F.HITTER, "lastresort"), mon(F.TANK))
        assertTrue(alone.round("lastresort", enemyActs = false).main().failed)
    }

    // ---------------------------------------------------------------- Counter, Mirror Coat, Metal Burst, Comeuppance, Endeavor, Final Gambit, Psywave

    @Test fun counterReturnsDoubleThePhysicalDamage() {
        val f = Fight(mon(F.TANK), mon(F.HITTER), bigWild = true)
        val ev = f.round("counter", "tackle")
        val taken = ev.wild().damage
        assertTrue(taken > 0)
        assertEquals(taken * 2, ev.main().damage)
        assertEquals(listOf(false, true), ev.attacks().map { it.byPlayer })
    }

    @Test fun counterFailsWithoutAPhysicalHit() {
        assertTrue(Fight(mon(F.TANK), mon(F.HITTER)).round("counter", "watergun").main().failed)   // special
        assertTrue(Fight(mon(F.TANK), mon(F.HITTER)).round("counter", "growl").main().failed)      // no damage
        val idle = Fight(mon(F.TANK), mon(F.HITTER)).round("counter", enemyActs = false)
        assertTrue(idle.main().failed); assertTrue("But it failed!" in idle.lines())
    }

    @Test fun mirrorCoatAnswersOnlySpecialDamage() {
        val f = Fight(mon(F.TANK), mon(F.HITTER), bigWild = true)
        val ev = f.round("mirrorcoat", "watergun")
        assertEquals(ev.wild().damage * 2, ev.main().damage)
        assertTrue(Fight(mon(F.TANK), mon(F.HITTER)).round("mirrorcoat", "tackle").main().failed)
    }

    @Test fun metalBurstAndComeuppanceGiveBackOneAndAHalf() {
        for (id in listOf("metalburst", "comeuppance")) for (hitBy in listOf("tackle", "watergun")) {
            val f = Fight(mon(F.TANK), mon(F.HITTER), bigWild = true)
            val ev = f.round(id, hitBy)
            val taken = ev.wild().damage
            assertEquals("$id after $hitBy", taken * 3 / 2, ev.main().damage)
        }
        assertTrue(Fight(mon(F.TANK), mon(F.HITTER)).round("metalburst", enemyActs = false).main().failed)
    }

    @Test fun aCounterStillCannotHitAnImmuneType() {
        // Counter is Fighting: a Ghost takes nothing
        val f = Fight(mon(F.TANK), mon(F.GHOST, "tackle"))
        val ev = f.round("counter", "lick")
        assertEquals(0.0, ev.main().effectiveness, 0.0)
        assertEquals(0, ev.main().damage)
    }

    @Test fun endeavorBringsTheTargetDownToTheUsersHp() {
        val f = Fight(mon(F.HITTER), mon(F.TANK))
        f.main.hp = 30
        val ev = f.round("endeavor", enemyActs = false)
        assertEquals(30, f.wild.hp)
        assertTrue(ev.main().damage > 0)
        // the target is not above the user: it fails
        val g = Fight(mon(F.HITTER), mon(F.TANK))
        g.wild.hp = 20
        val ev2 = g.round("endeavor", enemyActs = false)
        assertTrue(ev2.main().failed); assertEquals(20, g.wild.hp)
        g.wild.hp = g.main.hp // equal HP: fails too
        assertTrue(g.round("endeavor", enemyActs = false).main().failed)
    }

    @Test fun finalGambitDealsTheUsersHpAndTheUserFaints() {
        val f = Fight(mon(F.HITTER), mon(F.TANK))
        f.main.hp = 50
        val before = f.wild.hp
        val ev = f.round("finalgambit", enemyActs = false)
        assertEquals(before - 50, f.wild.hp)
        assertEquals(0, f.main.hp)
        assertTrue(ev.any { it is GameEvent.Fainted && it.player })
    }

    @Test fun psywaveStaysInHalfToOneAndAHalfTimesTheLevel() {
        val hits = (1..300).map { seed ->
            val f = Fight(mon(F.HITTER, lv = 50), mon(F.TANK))
            f.round("psywave", enemyActs = false, rng = Random(seed)).main().damage
        }
        assertTrue(hits.all { it in 25..75 })
        assertTrue("min ${hits.min()}", hits.min() <= 28)
        assertTrue("max ${hits.max()}", hits.max() >= 72)
        assertTrue("it is not always the same", hits.toSet().size > 20)
    }

    // ---------------------------------------------------------------- Future Sight, Doom Desire

    @Test fun futureSightLandsTwoRoundsLaterAtTheEndOfTheRound() {
        val f = Fight(mon(F.HITTER), mon(F.TANK))
        val hp = f.wild.hp
        val r1 = f.round("futuresight", enemyActs = false)
        assertTrue("Hitter foresaw an attack!" in r1.lines())
        assertEquals(hp, f.wild.hp)                    // nothing now
        assertEquals(0, r1.main().damage)
        val planned = f.battle.field.enemy["mp.fs.dmg"]!!
        assertTrue(planned > 0)
        val r2 = f.round(null, null, mainActs = false, enemyActs = false)
        assertEquals(hp, f.wild.hp); assertTrue(r2.lines().none { it.contains("took the") })
        val r3 = f.round(null, null, mainActs = false, enemyActs = false)
        assertTrue("Tank took the Future Sight attack!" in r3.lines())
        assertEquals(hp - planned, f.wild.hp)
        assertEquals(EffectKind.RESIDUAL, r3.effects().first { it.text.contains("took the") }.kind)
        assertEquals(planned, r3.effects().first { it.text.contains("took the") }.amount)
        // it is over: nothing more lands
        val r4 = f.round(null, null, mainActs = false, enemyActs = false)
        assertTrue(r4.lines().none { it.contains("took the") })
        assertEquals(hp - planned, f.wild.hp)
        assertTrue(f.battle.field.enemy.keys.none { it.startsWith("mp.") })
    }

    @Test fun onlyOneFutureAttackWaitsForASide() {
        val f = Fight(mon(F.HITTER, "futuresight", "doomdesire"), mon(F.TANK))
        f.round("futuresight", enemyActs = false)
        val planned = f.battle.field.enemy["mp.fs.dmg"]
        val second = f.round("doomdesire", enemyActs = false)
        assertTrue(second.main().failed); assertTrue("But it failed!" in second.lines())
        assertEquals(planned, f.battle.field.enemy["mp.fs.dmg"]) // the first is untouched
        assertNull(f.battle.field.enemy["mp.fs.doom"])
    }

    @Test fun doomDesireSaysItsOwnName() {
        val f = Fight(mon(F.HITTER, "doomdesire"), mon(F.TANK))
        f.round("doomdesire", enemyActs = false)
        f.round(null, null, mainActs = false, enemyActs = false)
        val r3 = f.round(null, null, mainActs = false, enemyActs = false)
        assertTrue("Tank took the Doom Desire attack!" in r3.lines())
    }

    @Test fun futureSightIgnoresAnImmunityAndHitsTheNewcomerAfterASwitch() {
        // the wild one uses it on the Main; a Dark type takes it (Psychic is normally nothing against Dark); the Main is switched meanwhile
        val f = Fight(mon(F.DARK), mon(F.HITTER, "futuresight"))
        f.round(null, "futuresight", mainActs = false)
        assertTrue(f.battle.field.player["mp.fs.dmg"]!! > 0) // stored on the target SIDE, although Psychic does nothing to a Dark type
        val newcomer = mon(F.TANK)
        f.battle.switchOutMain(data, f.main)
        val g = Fight(newcomer, f.wild, given = f.battle)
        val hp = newcomer.hp
        assertTrue(g.round(null, null, enemyActs = false, mainActs = false).lines().none { it.contains("took the") })
        val ev = g.round(null, null, enemyActs = false, mainActs = false)
        assertTrue("Tank took the Future Sight attack!" in ev.lines())
        assertTrue(newcomer.hp < hp)
    }

    @Test fun futureSightCanKnockTheTargetOut() {
        val f = Fight(mon(F.HITTER), mon(F.TANK))
        f.round("futuresight", enemyActs = false)
        f.round(null, null, mainActs = false, enemyActs = false)
        f.wild.hp = 1
        val ev = f.round(null, null, mainActs = false, enemyActs = false)
        assertEquals(0, f.wild.hp)
        assertTrue(ev.any { it is GameEvent.Fainted && !it.player })
    }

    // ---------------------------------------------------------------- Bide

    @Test fun bideStoresTwoActionsThenDealsDoubleWhatItTook() {
        val f = Fight(mon(F.HITTER), mon(F.TANK), bigWild = true)
        val r1 = f.round("bide", "tackle")
        assertTrue("Hitter is storing energy!" in r1.lines())
        assertTrue(r1.attacks().none { it.byPlayer })       // no attack yet
        val d1 = r1.wild().damage
        val r2 = f.round(null, "tackle")                      // forced: no pick needed
        assertTrue("Hitter is storing energy!" in r2.lines())
        val d2 = r2.wild().damage
        val wildHp = f.wild.hp
        val r3 = f.round(null, "tackle")
        val unleash = r3.main()
        assertEquals("bide", unleash.move)
        assertEquals(2 * (d1 + d2), unleash.damage)
        assertEquals(wildHp - 2 * (d1 + d2), f.wild.hp)
        // let loose: the run is over, the next pick is free
        assertTrue(f.battle.player.volatiles.keys.none { it.startsWith("mp.bide") })
        val r4 = f.round("tackle", null, enemyActs = false)
        assertEquals("tackle", r4.main().moveId)
    }

    @Test fun bideFailsWhenItTookNothing() {
        val f = Fight(mon(F.HITTER), mon(F.TANK), bigWild = true)
        f.round("bide", enemyActs = false); f.round(null, enemyActs = false)
        val r3 = f.round(null, enemyActs = false)
        assertTrue(r3.main().failed); assertTrue("But it failed!" in r3.lines())
    }

    @Test fun bideIsBrokenWhenTheUserCouldNotAct() {
        val f = Fight(mon(F.HITTER), mon(F.TANK), bigWild = true)
        f.round("bide", "tackle")
        f.battle.player.status = "slp"
        f.round(null, "tackle", rng = R17C4Fixture.ScriptedRandom(0.9)) // forced Bide, asleep: no action (and no storing)
        f.battle.player.status = null
        // the chain broke: the next pick is free again
        val r = f.round("tackle", null, enemyActs = false)
        assertEquals("tackle", r.main().moveId)
    }

    // ---------------------------------------------------------------- Rage

    @Test fun rageRaisesAttackOnEveryHitUntilAnotherMoveIsUsed() {
        val f = Fight(mon(F.HITTER), mon(F.TANK), bigWild = true)
        val r1 = f.round("rage", "tackle")
        assertEquals(1, f.battle.player.boosts[0])
        assertTrue("Hitter's rage is building!" in r1.lines())
        f.round("rage", "tackle")
        assertEquals(2, f.battle.player.boosts[0])
        f.round("tackle", "tackle")  // another move ends it before the hit
        assertEquals(2, f.battle.player.boosts[0])
        assertNull(f.battle.player.volatiles["mp.rage"])
    }

    // ---------------------------------------------------------------- Wake-Up Slap, Smelling Salts

    @Test fun wakeUpSlapWakesTheTargetAndSmellingSaltsCuresIt() {
        val f = Fight(mon(F.HITTER), mon(F.TANK), bigWild = true)
        f.battle.enemy.status = "slp"
        val ev = f.round("wakeupslap", enemyActs = false)
        assertTrue(ev.main().damage > 0)
        assertNull(f.battle.enemy.status)
        assertTrue("Tank woke up!" in ev.lines())
        assertEquals(EffectKind.STATUS_CURED, ev.effects().first { it.text.contains("woke up") }.kind)

        f.battle.enemy.status = "par"
        val ev2 = f.round("smellingsalts", enemyActs = false, rng = R17C4Fixture.ScriptedRandom())
        assertNull(f.battle.enemy.status)
        assertTrue("Tank was cured of paralysis!" in ev2.lines())
        // the wrong status stays
        f.battle.enemy.status = "brn"
        f.round("smellingsalts", enemyActs = false); assertEquals("brn", f.battle.enemy.status)
        f.round("wakeupslap", enemyActs = false); assertEquals("brn", f.battle.enemy.status)
    }

    @Test fun theDoubleOfWakeUpSlapIsInTheDamage() {
        fun damage(status: String?): Int {
            val f = Fight(mon(F.HITTER), mon(F.TANK), bigWild = true)
            f.battle.enemy.status = status
            return f.round("wakeupslap", enemyActs = false).main().damage
        }
        val awake = damage(null); val asleep = damage("slp")
        assertTrue("$asleep vs $awake", asleep >= awake * 18 / 10 && asleep <= awake * 22 / 10)
    }

    // ---------------------------------------------------------------- doubles that read the round

    private fun ratio(a: Int, b: Int) = a.toDouble() / b

    @Test fun revengeAndAvalancheDoubleAfterTheUserWasHit() {
        for (id in listOf("revenge", "avalanche")) {
            val hit = Fight(mon(F.TANK), mon(F.HITTER), bigWild = true).round(id, "tackle").main().damage // the fast wild one hits first
            val plain = Fight(mon(F.TANK), mon(F.HITTER), bigWild = true).round(id, enemyActs = false).main().damage
            assertTrue("$id $hit vs $plain", ratio(hit, plain) in 1.7..2.1)
        }
    }

    @Test fun paybackDoublesWhenTheTargetMovedFirst() {
        val after = Fight(mon(F.TANK), mon(F.HITTER), bigWild = true).round("payback", "splash").main().damage // the faster wild one moved first (a status move counts)
        val plain = Fight(mon(F.TANK), mon(F.HITTER), bigWild = true).round("payback", enemyActs = false).main().damage
        assertTrue("$after vs $plain", ratio(after, plain) in 1.7..2.1)
        val first = Fight(mon(F.TANK), mon(F.HITTER), bigWild = true).also { it.battle.player.boosts[4] = 6 }.round("payback", "splash").main().damage // +6 Speed: the user moves first
        assertEquals(plain, first)
    }

    @Test fun fishiousRendAndBoltBeakDoubleWhenTheUserMovesBeforeAnActingTarget() {
        for (id in listOf("fishiousrend", "boltbeak")) {
            // the same two Pokémon each time: Hitter against the faster Zippy; +6 Speed makes the user move first
            val before = Fight(mon(F.HITTER), mon(F.FAST), bigWild = true).also { it.battle.player.boosts[4] = 6 }.round(id, "tackle").main().damage
            val idle = Fight(mon(F.HITTER), mon(F.FAST), bigWild = true).round(id, enemyActs = false).main().damage   // the target does not move at all
            val after = Fight(mon(F.HITTER), mon(F.FAST), bigWild = true).round(id, "tackle").main().damage           // the target is faster
            assertTrue("$id $before vs $idle", ratio(before, idle) in 1.7..2.1)
            assertEquals(id, idle, after)
        }
    }

    @Test fun lashOutDoublesAfterTheUsersStatWasLoweredThisRound() {
        val lowered = Fight(mon(F.TANK), mon(F.HITTER), bigWild = true).round("lashout", "growl").main().damage // the fast wild Growl comes first
        val plain = Fight(mon(F.TANK), mon(F.HITTER), bigWild = true).round("lashout", enemyActs = false).main().damage
        // Growl also lowers the user's Attack: the double is partly eaten by -1 Attack (x2/3)
        val growlOnly = Fight(mon(F.TANK), mon(F.HITTER), bigWild = true).also { it.battle.player.boosts[0] = -1 }.round("lashout", enemyActs = false).main().damage
        assertTrue("$lowered vs $growlOnly", ratio(lowered, growlOnly) in 1.7..2.1)
        assertTrue(lowered > plain * 12 / 10)
    }

    @Test fun stompingTantrumDoublesAfterAFailedMove() {
        val f = Fight(mon(F.HITTER), mon(F.TANK), bigWild = true)
        f.round("dreameater", enemyActs = false)             // fails (the target is awake)
        assertTrue(f.battle.player.lastMoveFailed)
        val after = f.round("stompingtantrum", enemyActs = false).main().damage
        val g = Fight(mon(F.HITTER), mon(F.TANK), bigWild = true)
        g.round("tackle", enemyActs = false)
        val plain = g.round("stompingtantrum", enemyActs = false).main().damage
        assertTrue("$after vs $plain", ratio(after, plain) in 1.7..2.1)
        // Temper Flare reads the same flag
        val h = Fight(mon(F.HITTER), mon(F.TANK), bigWild = true)
        h.round("dreameater", enemyActs = false)
        assertTrue(h.round("temperflare", enemyActs = false).main().damage > 0)
    }

    @Test fun roundDoublesAfterTheTargetUsedRound() {
        val twice = Fight(mon(F.TANK, "round"), mon(F.HITTER, "round"), bigWild = true).round("round", "round").main().damage
        val once = Fight(mon(F.TANK, "round"), mon(F.HITTER, "round"), bigWild = true).round("round", "tackle").main().damage
        assertTrue("$twice vs $once", ratio(twice, once) in 1.7..2.1)
    }

    // ---------------------------------------------------------------- build-ups

    @Test fun echoedVoiceBuildsUpAndAnotherMoveEndsIt() {
        val f = Fight(mon(F.HITTER), mon(F.TANK), bigWild = true)
        val powers = (1..5).map { f.round("echoedvoice", enemyActs = false).main().damage }
        assertTrue("grows: $powers", powers[0] < powers[1] && powers[1] < powers[2] && powers[2] < powers[3] && powers[3] < powers[4])
        assertEquals(4, f.battle.player.volatiles["mp.echo"])
        assertTrue("the 5th hit is 200 power, the 1st 40: $powers", ratio(powers[4], powers[0]) > 4.0)
        f.round("tackle", enemyActs = false)
        assertNull(f.battle.player.volatiles["mp.echo"])
    }

    @Test fun furyCutterDoublesPerHitAndAMissStartsOver() {
        val f = Fight(mon(F.HITTER), mon(F.TANK), bigWild = true)
        val d = (1..4).map { f.round("furycutter", enemyActs = false).main().damage }
        assertTrue("$d", d[1] > d[0] && d[2] > d[1] && d[3] == d[2])           // 40, 80, 160, 160
        assertEquals(2, f.battle.player.volatiles["mp.fury"])
        val miss = f.round("furycutter", enemyActs = false, rng = R17C4Fixture.ScriptedRandom(0.99))
        assertTrue(miss.main().missed)
        assertNull(f.battle.player.volatiles["mp.fury"])
    }

    @Test fun rolloutLocksFiveActionsAndDoublesEachHit() {
        for (id in listOf("rollout", "iceball")) {
            val f = Fight(mon(F.HITTER), mon(F.TANK), bigWild = true)
            val first = f.round(id, enemyActs = false)
            val moves = ArrayList<Attack>(); moves += first.main()
            repeat(4) { moves += f.round(null, enemyActs = false).main() } // forced: no pick
            assertEquals(id, List(5) { id }, moves.map { it.move })
            for (i in 1..4) assertTrue("$id hit ${i + 1}: ${moves.map { it.damage }}", moves[i].damage > moves[i - 1].damage * 15 / 10)
            // five uses: the lock is over
            assertEquals("tackle", f.round("tackle", enemyActs = false).main().moveId)
        }
    }

    @Test fun rolloutEndsOnAMiss() {
        val f = Fight(mon(F.HITTER), mon(F.TANK), bigWild = true)
        f.round("rollout", enemyActs = false)
        f.round(null, enemyActs = false)
        val miss = f.round(null, enemyActs = false, rng = R17C4Fixture.ScriptedRandom(0.99))
        assertTrue(miss.main().missed); assertEquals("rollout", miss.main().move)
        assertEquals("tackle", f.round("tackle", enemyActs = false).main().moveId) // free again
    }

    @Test fun defenseCurlDoublesRollout() {
        val plain = Fight(mon(F.HITTER), mon(F.TANK), bigWild = true).round("rollout", enemyActs = false).main().damage
        val f = Fight(mon(F.HITTER), mon(F.TANK), bigWild = true)
        f.battle.player.volatiles["defensecurl"] = 1
        val curled = f.round("rollout", enemyActs = false).main().damage
        assertTrue("$curled vs $plain", ratio(curled, plain) in 1.7..2.1)
    }

    @Test fun tripleAxelHitsThreeTimesWithGrowingPower() {
        val f = Fight(mon(F.HITTER), mon(F.TANK), bigWild = true)
        val a = f.round("tripleaxel", enemyActs = false).main()
        assertEquals(3, a.hits)
        // 20 + 40 + 60 power: more than three hits of the first
        val parts = BattleRules.using(listOf(MovePower)) {
            (0..2).map { i -> BattleEngine.damage(data, f.main, BattleSide(), f.wild, BattleSide(), data.move("tripleaxel")!!, R17C4Fixture.ScriptedRandom(), false, 85, hitIndex = i).damage }
        }
        assertTrue("$parts", parts[0] < parts[1] && parts[1] < parts[2])
        assertEquals(parts.sum(), a.damage)
    }

    @Test fun everyHitOfTripleAxelRollsItsAccuracy() {
        // the 1st check (before the move) and the 2nd pass, the 3rd misses: two hits land
        val partly = Fight(mon(F.HITTER), mon(F.TANK), bigWild = true).round("tripleaxel", enemyActs = false, rng = R17C4Fixture.ScriptedRandom(0.0, 0.0, 0.99)).main()
        assertEquals(2, partly.hits); assertFalse(partly.missed)
        // the 2nd check misses: one hit
        val one = Fight(mon(F.HITTER), mon(F.TANK), bigWild = true).round("tripleaxel", enemyActs = false, rng = R17C4Fixture.ScriptedRandom(0.0, 0.99)).main()
        assertEquals(1, one.hits)
        // the first check misses: the whole move
        val none = Fight(mon(F.HITTER), mon(F.TANK), bigWild = true).round("tripleaxel", enemyActs = false, rng = R17C4Fixture.ScriptedRandom(0.99)).main()
        assertTrue(none.missed)
        assertTrue(partly.damage > one.damage)
        // Triple Kick the same way
        assertEquals(2, Fight(mon(F.HITTER), mon(F.TANK), bigWild = true).round("triplekick", enemyActs = false, rng = R17C4Fixture.ScriptedRandom(0.0, 0.0, 0.99)).main().hits)
    }

    @Test fun populationBombHitsTenTimesWhenEveryRollPasses() {
        val all = Fight(mon(F.HITTER), mon(F.TANK), bigWild = true).round("populationbomb", enemyActs = false).main()
        assertEquals(10, all.hits)
        val cut = Fight(mon(F.HITTER), mon(F.TANK), bigWild = true).round("populationbomb", enemyActs = false, rng = R17C4Fixture.ScriptedRandom(*DoubleArray(4) { 0.0 }, 0.99)).main()
        assertEquals(4, cut.hits)
    }

    @Test fun beatUpHitsOncePerTeamMemberWithHp() {
        val f = Fight(mon(F.HITTER, "beatup"), mon(F.TANK), bigWild = true)
        val a = mon(F.FAST, lv = 30).also { it.hp = 10 }
        val b = TestData.mon(data, 405, 30, listOf("tackle"), uid = "b")
        val fainted = TestData.mon(data, 406, 30, listOf("tackle"), uid = "c").also { it.hp = 0 }
        f.battle.team = listOf(f.main, a.also { it.uid = "a" }, b, fainted)
        val ev = f.round("beatup", enemyActs = false).main()
        assertEquals(3, ev.hits) // the user, a and b; not the fainted one
        // the wild one is alone
        val g = Fight(mon(F.TANK), mon(F.HITTER, "beatup"))
        g.battle.team = listOf(g.main, a, b)
        assertEquals(1, g.round(null, "beatup", mainActs = false).wild().hits)
        // without a team (no Game): just the user
        val h = Fight(mon(F.HITTER, "beatup"), mon(F.TANK), bigWild = true)
        assertEquals(1, h.round("beatup", enemyActs = false).main().hits)
    }

    // ---------------------------------------------------------------- Magnitude and Present

    @Test fun magnitudeSaysItsLevelAndFollowsTheChances() {
        val counts = HashMap<Int, Int>()
        for (seed in 1..2000) {
            val f = Fight(mon(F.HITTER), mon(F.TANK), bigWild = true)
            val ev = f.round("magnitude", enemyActs = false, rng = Random(seed))
            val line = ev.lines().single { it.startsWith("Magnitude ") }
            val level = line.removePrefix("Magnitude ").removeSuffix("!").toInt()
            counts[level] = (counts[level] ?: 0) + 1
            assertTrue(ev.main().damage > 0)
        }
        assertEquals(setOf(4, 5, 6, 7, 8, 9, 10), counts.keys)
        fun pct(l: Int) = counts.getValue(l) * 100.0 / 2000
        assertEquals(30.0, pct(7), 4.0); assertEquals(20.0, pct(6), 4.0); assertEquals(20.0, pct(8), 4.0)
        assertEquals(10.0, pct(5), 3.0); assertEquals(10.0, pct(9), 3.0)
        assertEquals(5.0, pct(4), 2.5); assertEquals(5.0, pct(10), 2.5)
    }

    @Test fun theMagnitudeLevelSetsThePowerOfThatHit() {
        // power 10 / 30 / 50 / 70 / 90 / 110 / 150: whatever the damage roll, a higher level always hits harder than a lower one
        val byLevel = HashMap<Int, MutableList<Int>>()
        for (seed in 1..800) {
            val f = Fight(mon(F.HITTER), mon(F.TANK), bigWild = true)
            val ev = f.round("magnitude", enemyActs = false, rng = Random(seed))
            if (ev.main().missed) continue
            val level = ev.lines().single { it.startsWith("Magnitude ") }.removePrefix("Magnitude ").removeSuffix("!").toInt()
            byLevel.getOrPut(level) { ArrayList() } += ev.main().damage
        }
        val levels = byLevel.keys.sorted()
        assertEquals((4..10).toList(), levels)
        for ((lo, hi) in levels.zipWithNext()) assertTrue("$lo vs $hi", byLevel.getValue(lo).max() < byLevel.getValue(hi).min())
    }

    @Test fun presentIsAttackOrHealAndHealsAQuarter() {
        var heals = 0; var hits = 0
        for (seed in 1..1500) {
            val f = Fight(mon(F.HITTER), mon(F.TANK), bigWild = true)
            f.wild.hp = 50_000
            val before = f.wild.hp
            val ev = f.round("present", enemyActs = false, rng = Random(seed))
            if (ev.main().missed) continue // Present has 90 accuracy
            if (ev.lines().any { it.endsWith("regained health!") }) {
                heals++
                assertEquals(before + 100_000 / 4, f.wild.hp) // a quarter of the max HP
                assertEquals(0, ev.main().damage)
            } else { hits++; assertTrue(f.wild.hp < before) }
        }
        assertEquals(20.0, heals * 100.0 / (heals + hits), 4.0)
        assertTrue(heals + hits > 1200)
    }

    @Test fun presentHealsEvenAGhost() {
        // Present is Normal: a Ghost is immune to its damage, but not to its heal
        var healed = false
        for (seed in 1..200) {
            val f = Fight(mon(F.HITTER), mon(F.GHOST), bigWild = true)
            f.wild.hp = 50_000
            val ev = f.round("present", enemyActs = false, rng = Random(seed))
            if (ev.lines().any { it.endsWith("regained health!") }) { healed = true; assertTrue(f.wild.hp > 50_000); break }
        }
        assertTrue(healed)
    }

    // ---------------------------------------------------------------- the move choice

    private fun pick(choice: MoveChoice, main: Mon, wild: Mon, seed: Int, setup: (Battle) -> Unit = {}): Move {
        val b = Battle(wild, Tier.NORMAL).also(setup)
        return BattleRules.using(listOf(MovePower)) { MoveChooser.pickMain(choice, data, b, main, Random(seed)) }
    }

    private fun picks(choice: MoveChoice, main: Mon, wild: Mon, setup: (Battle) -> Unit = {}): Set<String> = (1..120).map { pick(choice, main, wild, it, setup).id }.toSet()

    @Test fun smartChoicesLeaveOutAMoveThatWouldSurelyFail() {
        for (choice in listOf(MoveChoice.SMART, MoveChoice.SMARTEST)) {
            // Dream Eater on an awake target
            assertEquals(choice.name, setOf("tackle"), picks(choice, mon(F.HITTER, "dreameater", "tackle"), mon(F.TANK)))
            assertTrue(choice.name, "dreameater" in picks(choice, mon(F.HITTER, "dreameater", "tackle"), mon(F.TANK)) { it.enemy.status = "slp" })
            // Snore awake / asleep
            assertEquals(setOf("tackle"), picks(choice, mon(F.HITTER, "snore", "tackle"), mon(F.TANK)))
            assertTrue("snore" in picks(choice, mon(F.HITTER, "snore", "tackle"), mon(F.TANK)) { it.player.status = "slp" })
            // Poltergeist: wild Pokémon hold no item, so the Main never picks it (R17C FIX-A: CoreRules.MAIN_NEVER_PICKED, also with an item on the target);
            // the wild one picks it against a Main's item (R17CFixAChoiceTest)
            assertEquals(setOf("tackle"), picks(choice, mon(F.HITTER, "poltergeist", "tackle"), mon(F.TANK)))
            assertEquals(setOf("tackle"), picks(choice, mon(F.HITTER, "poltergeist", "tackle"), mon(F.TANK).also { it.heldItem = "oran-berry" }))
            // Synchronoise without / with a shared type
            assertEquals(setOf("tackle"), picks(choice, mon(F.HITTER, "synchronoise", "tackle"), mon(F.TANK)))
            assertTrue("synchronoise" in picks(choice, mon(F.HITTER, "synchronoise", "tackle"), mon(F.FLYER)))
            // Last Resort not ready
            assertEquals(setOf("tackle"), picks(choice, mon(F.HITTER, "lastresort", "tackle"), mon(F.TANK)))
            // Endeavor when the target has less HP
            assertEquals(setOf("tackle"), picks(choice, mon(F.HITTER, "endeavor", "tackle"), mon(F.TANK).also { it.hp = 5 }))
            assertTrue("endeavor" in picks(choice, mon(F.HITTER, "endeavor", "tackle").also { it.hp = 5 }, mon(F.TANK)))
            // Fake Out after the first action
            assertTrue("fakeout" in picks(choice, mon(F.HITTER, "fakeout", "tackle"), mon(F.TANK)))
            assertEquals(setOf("tackle"), picks(choice, mon(F.HITTER, "fakeout", "tackle"), mon(F.TANK)) { it.player.actions = 1 })
            // Future Sight while one waits
            assertTrue("futuresight" in picks(choice, mon(F.HITTER, "futuresight", "tackle"), mon(F.TANK)))
            assertEquals(setOf("tackle"), picks(choice, mon(F.HITTER, "futuresight", "tackle"), mon(F.TANK)) { it.field.enemy["mp.fs"] = 2 })
        }
    }

    @Test fun smartMainWithOnlyAFailingMoveUsesStruggle() {
        assertEquals("struggle", pick(MoveChoice.SMART, mon(F.HITTER, "dreameater"), mon(F.TANK), 1).id)
        assertEquals("dreameater", pick(MoveChoice.SMART, mon(F.HITTER, "dreameater"), mon(F.TANK), 1) { it.enemy.status = "slp" }.id)
    }

    @Test fun randomStillMayPickAFailingMoveLikeTheGames() {
        assertTrue("dreameater" in picks(MoveChoice.RANDOM, mon(F.HITTER, "dreameater", "tackle"), mon(F.TANK)))
    }

    @Test fun lastResortIsPickedOnceEveryOtherMoveWasUsed() {
        val main = mon(F.HITTER, "lastresort", "tackle")
        assertTrue("lastresort" in picks(MoveChoice.SMART, main, mon(F.TANK)) { it.player.notes["mp.used"] = "tackle" })
    }
}
