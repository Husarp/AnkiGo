package com.husarp.ankigo.game

import com.husarp.ankigo.game.R17C3.attacks
import com.husarp.ankigo.game.R17C3.cantMove
import com.husarp.ankigo.game.R17C3.effects
import com.husarp.ankigo.game.R17C3.high
import com.husarp.ankigo.game.R17C3.kinds
import com.husarp.ankigo.game.R17C3.lines
import com.husarp.ankigo.game.R17C3.low
import com.husarp.ankigo.game.save.SaveCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * R17C-3: the lasting move effects of CoreRules: flinch, confusion, love, locked moves, Uproar, fixed sleep, binding, Salt Cure, Syrup Bomb,
 * Sparkling Aria, Glaive Rush, Tri Attack and friends, crash damage and the HP cost of Mind Blown. Fixtures in [R17C3] (the Main is faster).
 */
class R17C3VolatilesTest {
    private val data = R17C3.data
    private fun main(vararg m: String, gender: Char = 'M') = R17C3.main(*m, gender = gender)
    private fun wild(vararg m: String, sp: Int = 301, gender: Char = 'F') = R17C3.wild(*m, sp = sp, gender = gender)

    /** The Main uses [id] on every pick (a Main with only status moves would use Struggle). */
    private fun <T> forced(id: String, block: () -> T): T = R17C3.withRules(R17C3.forcing(id), block = block)

    /** One round; the wild one acts when [wildActs]. */
    private fun rd(main: Mon, b: Battle, rng: Random = Random(1), wildActs: Boolean = false, choice: MoveChoice = MoveChoice.RANDOM, multiplier: Double = 1.0) =
        R17C3.round(main, b, rng, enemyActs = wildActs, choice = choice, multiplier = multiplier)

    // ---------------------------------------------------------------- flinch

    @Test fun aFlinchStopsTheTargetThatHasNotMovedYet() {
        val m = main("bite"); val w = wild("tackle"); val b = R17C3.battle(w)
        val hp = m.hp
        val e = rd(m, b, wildActs = true)
        assertEquals(listOf("Slowpoke flinched and couldn't move!"), e.cantMove("flinch").map { it.text })
        assertFalse(e.cantMove("flinch").single().onPlayer)
        assertTrue("the wild one did not attack", e.attacks().none { !it.byPlayer })
        assertEquals(hp, m.hp)
    }

    @Test fun noFlinchWhenTheTargetAlreadyMoved() {
        val m = R17C3.mon(307, 50, "bite"); val w = wild("tackle"); val b = R17C3.battle(w) // the wild one is faster now
        val e = rd(m, b, wildActs = true)
        assertTrue(e.cantMove("flinch").isEmpty())
        assertEquals(1, e.attacks().count { !it.byPlayer })
        assertNull(b.enemy.volatiles["flinch"])
    }

    @Test fun aFlinchIsGoneAtTheEndOfTheRound() {
        val m = main("bite"); val b = R17C3.battle(wild("tackle"))
        rd(m, b, wildActs = false) // the wild one has no action this round: the flinch would wait
        assertTrue(b.enemy.volatiles.isEmpty())
        val hp = m.hp
        val e = rd(m, b, wildActs = true) // next round it flinches again, but only because Fastie bites first again
        assertEquals(hp, m.hp); assertEquals(1, e.cantMove("flinch").size)
    }

    @Test fun theSecondSecondaryOfTheFangsFlinchesToo() {
        val m = main("firefang"); val w = wild("tackle"); val b = R17C3.battle(w)
        val e = rd(m, b, wildActs = true)
        assertEquals("brn", b.enemy.status)
        assertEquals(1, e.cantMove("flinch").size)
    }

    @Test fun aRuleCanBlockTheFlinch() {
        val innerFocus = object : BattleRules {
            override fun blockVolatile(r: Round, tp: Boolean, key: String, byFoe: Boolean): String? = if (key == "flinch") "" else null
        }
        R17C3.withRules(innerFocus) {
            val m = main("bite"); val w = wild("tackle"); val b = R17C3.battle(w)
            val e = rd(m, b, wildActs = true)
            assertTrue(e.cantMove("flinch").isEmpty())
            assertEquals(1, e.attacks().count { !it.byPlayer })
        }
    }

    @Test fun aSwitchsFreeHitNeverMakesTheMainFlinch() {
        val m = main("tackle"); val b = R17C3.battle(wild("bite"))
        BattleEngine.switchHit(data, b, m, high)
        assertNull(b.player.volatiles["flinch"])
        val e = rd(m, b, wildActs = true)
        assertTrue(e.cantMove("flinch").isEmpty())
    }

    // ---------------------------------------------------------------- confusion

    @Test fun confuseRayConfusesWithTheGamesLine() {
        val m = main("confuseray"); val w = wild("splash"); val b = R17C3.battle(w)
        val e = forced("confuseray") { rd(m, b) }
        val start = e.kinds(EffectKind.VOLATILE).single()
        assertEquals("Slowpoke became confused!", start.text)
        assertEquals("Confused", start.label); assertEquals(1, start.stages); assertFalse(start.onPlayer)
        assertTrue(b.enemy.volatiles["confusion"] in 2..5)
        assertFalse(e.attacks().single().failed)
    }

    @Test fun confusionLastsTwoToFiveAttempts() {
        val seen = HashSet<Int>()
        for (seed in 1..150) {
            val b = R17C3.battle(wild("splash"))
            forced("confuseray") { rd(main("confuseray"), b, Random(seed)) }
            seen += b.enemy.volatiles.getValue("confusion")
        }
        assertEquals(setOf(2, 3, 4, 5), seen)
    }

    @Test fun anAlreadyConfusedTargetCannotBeConfusedAgain() {
        val m = main("confuseray"); val b = R17C3.battle(wild("splash"))
        forced("confuseray") { rd(m, b) }; val left = b.enemy.volatiles.getValue("confusion")
        val e = forced("confuseray") { rd(m, b) }
        assertTrue("Slowpoke is already confused!" in e.lines())
        assertTrue(e.attacks().single().failed)
        assertEquals(left, b.enemy.volatiles["confusion"]) // not refreshed
        assertTrue("no second 'But it failed!'", "But it failed!" !in e.lines())
    }

    @Test fun eachActionCountsDownAndTheLastOneSnapsOut() {
        val m = main("tackle"); val b = R17C3.battle(wild("tackle"))
        b.enemy.volatiles["confusion"] = 3
        val e1 = rd(m, b, high, wildActs = true)
        assertTrue("Slowpoke is confused!" in e1.lines()); assertEquals(2, b.enemy.volatiles["confusion"]); assertEquals(1, e1.attacks().count { !it.byPlayer })
        val e2 = rd(m, b, high, wildActs = true)
        assertTrue("Slowpoke is confused!" in e2.lines()); assertEquals(1, b.enemy.volatiles["confusion"]); assertEquals(1, e2.attacks().count { !it.byPlayer })
        val e3 = rd(m, b, high, wildActs = true)
        assertTrue("Slowpoke snapped out of its confusion!" in e3.lines())
        assertTrue("Slowpoke is confused!" !in e3.lines())
        assertNull(b.enemy.volatiles["confusion"]); assertEquals(1, e3.attacks().count { !it.byPlayer })
    }

    @Test fun aConfusedOneHurtsItselfWithAFortyPowerTypelessHit() {
        val m = main("tackle"); val w = wild("tackle"); val b = R17C3.battle(w)
        b.enemy.volatiles["confusion"] = 4
        val before = w.hp
        val e = rd(m, b, low, wildActs = true)
        assertEquals(listOf("It hurt itself in its confusion!"), e.cantMove("confusion").map { it.text })
        assertTrue("it did not attack", e.attacks().none { !it.byPlayer })
        val st = StatCalc.stats(data, w)
        val expected = (42 * 40 * st.atk / st.def) / 50 + 2
        val dmg = e.kinds(EffectKind.RESIDUAL).single { !it.onPlayer }.amount
        assertEquals(maxOf(1, expected * 85 / 100), dmg) // the lowest roll under `low`
        // the Main's own Tackle hit it too; the confusion hit is on top
        assertTrue(w.hp <= before - dmg)
    }

    @Test fun theSelfHitIgnoresTheAnswersFactorAndTheStagesCount() {
        fun hit(multiplier: Double, defStage: Int): Int {
            val m = main("tackle"); val w = wild("tackle"); val b = R17C3.battle(w)
            b.enemy.volatiles["confusion"] = 4; b.enemy.boosts[1] = defStage
            return rd(m, b, low, wildActs = true, multiplier = multiplier).kinds(EffectKind.RESIDUAL).single { !it.onPlayer }.amount
        }
        assertEquals(hit(1.0, 0), hit(3.0, 0)) // not scaled by the answers
        assertTrue(hit(1.0, 2) < hit(1.0, 0)) // its own Defense stage counts
    }

    @Test fun confusionEndsWhenTheMainIsSwitchedOut() {
        val m = main("tackle"); val b = R17C3.battle(wild("tackle"))
        b.player.volatiles["confusion"] = 3; b.enemy.volatiles["confusion"] = 3
        b.switchOutMain(data, m)
        assertTrue(b.player.volatiles.isEmpty())
        assertEquals(3, b.enemy.volatiles["confusion"])
    }

    @Test fun aRuleCanBlockConfusion() {
        val ownTempo = object : BattleRules {
            override fun blockVolatile(r: Round, tp: Boolean, key: String, byFoe: Boolean): String? =
                if (key == "confusion") "Slowpoke's Own Tempo prevents confusion!" else null
        }
        R17C3.withRules(ownTempo, R17C3.forcing("confuseray")) {
            val b = R17C3.battle(wild("splash"))
            val e = rd(main("confuseray"), b)
            assertNull(b.enemy.volatiles["confusion"])
            assertTrue("Slowpoke's Own Tempo prevents confusion!" in e.lines())
            assertTrue(e.attacks().single().failed)
            assertTrue("But it failed!" !in e.lines())
        }
        // a secondary chance fails quietly
        R17C3.withRules(ownTempo) {
            val b2 = R17C3.battle(wild("splash"))
            val e2 = rd(main("waterpulse"), b2)
            assertNull(b2.enemy.volatiles["confusion"]); assertTrue(e2.lines().none { "Own Tempo" in it })
        }
    }

    @Test fun swaggerRaisesAttackBy2AndConfuses() {
        val b = R17C3.battle(wild("splash"))
        val e = forced("swagger") { rd(main("swagger"), b) }
        assertEquals(2, b.enemy.boosts[0])
        assertTrue(b.enemy.volatiles["confusion"] in 2..5)
        assertFalse(e.attacks().single().failed)
        // already confused: the stat still rises, the line says so, the move did not fail
        val e2 = forced("swagger") { rd(main("swagger"), b) }
        assertEquals(4, b.enemy.boosts[0])
        assertTrue("Slowpoke is already confused!" in e2.lines())
        assertFalse(e2.attacks().single().failed)
    }

    @Test fun flatterRaisesSpecialAttackBy1AndConfuses() {
        val b = R17C3.battle(wild("splash"))
        forced("flatter") { rd(main("flatter"), b) }
        assertEquals(1, b.enemy.boosts[2]); assertTrue("confusion" in b.enemy.volatiles)
    }

    @Test fun aConfusionSecondaryConfusesTheTarget() {
        val b = R17C3.battle(wild("splash"))
        val e = rd(main("waterpulse"), b)
        assertTrue("Slowpoke became confused!" in e.lines())
        assertTrue("confusion" in b.enemy.volatiles)
    }

    @Test fun theSmartChoiceKeepsConfusionMovesForTargetsThatCanStillGetConfused() {
        val m = main("confuseray"); val w = wild("splash"); val b = R17C3.battle(w)
        val view = SimpleView.of(data, b, m)
        val cr = data.move("confuseray")!!
        assertTrue(MoveChooser.statusUseful(MoveChoice.SMART, cr, view, true))
        b.enemy.volatiles["confusion"] = 3
        assertFalse(MoveChooser.statusUseful(MoveChoice.SMART, cr, view, true))
        b.enemy.volatiles.clear(); b.field.terrain = "misty"
        assertFalse(MoveChooser.statusUseful(MoveChoice.SMART, cr, view, true))
        b.field.terrain = null; b.field.enemy["safeguard"] = 5
        assertFalse(MoveChooser.statusUseful(MoveChoice.SMART, cr, view, true))
    }

    // ---------------------------------------------------------------- infatuation

    @Test fun attractOnlyWorksBetweenAMaleAndAFemale() {
        fun attract(mainGender: Char, wildGender: Char): List<GameEvent> {
            val b = R17C3.battle(wild("splash", gender = wildGender))
            return forced("attract") { rd(main("attract", gender = mainGender), b) }
        }
        val ok = attract('M', 'F')
        assertEquals("Slowpoke fell in love!", ok.kinds(EffectKind.VOLATILE).single().text)
        assertEquals("In love", ok.kinds(EffectKind.VOLATILE).single().label)
        assertFalse(ok.attacks().single().failed)
        assertFalse(attract('F', 'M').attacks().single().failed)
        for ((a, b) in listOf('M' to 'M', 'F' to 'F', 'M' to 'N', 'N' to 'F', 'N' to 'N')) {
            val e = attract(a, b)
            assertTrue("$a $b", e.attacks().single().failed)
            assertTrue("$a $b", "But it failed!" in e.lines())
        }
    }

    @Test fun aLoveStruckOneIsImmobilizedHalfTheTime() {
        val m = main("tackle"); val b = R17C3.battle(wild("tackle"))
        b.enemy.volatiles["attract"] = 1
        val stuck = rd(m, b, low, wildActs = true)
        assertTrue("Slowpoke is in love with Fastie!" in stuck.lines())
        assertEquals(listOf("Slowpoke is immobilized by love!"), stuck.cantMove("attract").map { it.text })
        assertTrue(stuck.attacks().none { !it.byPlayer })
        val free = rd(m, b, high, wildActs = true)
        assertTrue("Slowpoke is in love with Fastie!" in free.lines())
        assertTrue(free.cantMove("attract").isEmpty()); assertEquals(1, free.attacks().count { !it.byPlayer })
    }

    @Test fun loveEndsWhenTheMainLeavesAndAlreadyInLoveFails() {
        val m = main("attract"); val b = R17C3.battle(wild("splash"))
        forced("attract") { rd(m, b) }
        assertTrue("attract" in b.enemy.volatiles)
        val again = forced("attract") { rd(m, b) }
        assertTrue(again.attacks().single().failed); assertTrue("Slowpoke is already in love!" in again.lines())
        b.switchOutMain(data, m)
        assertFalse("attract" in b.enemy.volatiles)
    }

    @Test fun theSmartChoiceSkipsAttractWhenTheGendersDoNotFit() {
        val b = R17C3.battle(wild("splash", gender = 'M'))
        val view = SimpleView.of(data, b, main("attract", gender = 'M'))
        assertFalse(MoveChooser.statusUseful(MoveChoice.SMART, data.move("attract")!!, view, true))
        val b2 = R17C3.battle(wild("splash", gender = 'F'))
        assertTrue(MoveChooser.statusUseful(MoveChoice.SMART, data.move("attract")!!, SimpleView.of(data, b2, main("attract")), true))
    }

    // ---------------------------------------------------------------- locked moves, Uproar, fixed sleep

    @Test fun outrageIsUsedTwoOrThreeTimesThenTheUserIsConfusedByFatigue() {
        val totals = HashSet<Int>()
        for (seed in 1..40) {
            val m = main("outrage"); val b = R17C3.battle(wild("splash"))
            var uses = 0; var fatigue = false
            for (i in 1..4) {
                val e = rd(m, b, Random(seed))
                uses += e.attacks().count { it.byPlayer }
                if ("Fastie became confused due to fatigue!" in e.lines()) { fatigue = true; break }
            }
            assertTrue("seed $seed", fatigue)
            assertTrue("confused for 2-5 attempts", b.player.volatiles["confusion"] in 2..5)
            assertNull(b.player.volatiles["lockedmove"])
            totals += uses
        }
        assertEquals(setOf(2, 3), totals)
    }

    @Test fun theLockedMoveIsForcedOverTheMovesPick() {
        for (choice in MoveChoice.entries) for (seed in 1..10) {
            val m = main("outrage", "tackle", "swordsdance"); val b = R17C3.battle(wild("splash"))
            b.player.volatiles["lockedmove"] = 2; b.player.notes["lockedmove"] = "outrage"
            assertEquals("$choice $seed", "Outrage", rd(m, b, Random(seed), choice = choice).attacks().single().move)
            assertEquals(1, b.player.volatiles["lockedmove"])
        }
    }

    @Test fun aMissEndsTheLockWithoutConfusion() {
        val m = main("outrage"); val b = R17C3.battle(wild("splash"))
        rd(m, b)
        assertNotNull(b.player.volatiles["lockedmove"])
        b.enemy.boosts[6] = 6 // +6 evasion: a `high` roll misses
        val e = rd(m, b, high)
        assertTrue(e.attacks().single().missed)
        assertNull(b.player.volatiles["lockedmove"]); assertNull(b.player.volatiles["confusion"])
        assertTrue(e.lines().none { "fatigue" in it })
    }

    @Test fun anActionThatCannotHappenEndsALock() {
        val m = main("outrage"); val b = R17C3.battle(wild("splash"))
        b.player.volatiles["lockedmove"] = 2; b.player.notes["lockedmove"] = "outrage"; b.player.status = "slp"
        rd(m, b, high)
        assertNull(b.player.volatiles["lockedmove"]); assertNull(b.player.notes["lockedmove"])
    }

    @Test fun uproarLastsThreeUsesAndNobodyFallsAsleepWhileItLasts() {
        val m = main("uproar"); val w = wild("hypnosis"); val b = R17C3.battle(w)
        for (i in 1..3) {
            val e = rd(m, b, low, wildActs = true)
            assertEquals("round $i", "Uproar", e.attacks().single { it.byPlayer }.move)
            assertNull("round $i", b.player.status)
            assertTrue("round $i", "But the uproar kept Fastie awake!" in e.lines())
        }
        assertNull("the uproar is over after its third use", b.player.volatiles["uproar"])
        assertNull(b.player.notes["uproar"])
        // without an uproar the same Hypnosis puts the Main to sleep
        val b2 = R17C3.battle(wild("hypnosis")); val m2 = main("tackle")
        rd(m2, b2, low, wildActs = true)
        assertEquals("slp", b2.player.status)
    }

    @Test fun aSleeperWakesAtTheEndOfARoundWithAnUproar() {
        val m = main("uproar"); val w = wild("tackle"); val b = R17C3.battle(w)
        b.enemy.status = "slp"
        val e = rd(m, b, high, wildActs = true)
        assertTrue("Slowpoke is fast asleep." in e.lines())
        assertTrue("Slowpoke woke up in the uproar!" in e.lines())
        assertNull(b.enemy.status)
    }

    @Test fun aFixedSleepCountSleepsExactlyThatManyAttemptsWhateverTheRoll() {
        for (rng in listOf(low, high)) {
            val m = main("tackle"); val w = wild("tackle"); val b = R17C3.battle(w)
            b.enemy.status = "slp"; b.enemy.volatiles["sleepTurns"] = 2
            val e1 = rd(m, b, rng, wildActs = true)
            val e2 = rd(m, b, rng, wildActs = true)
            assertTrue("Slowpoke is fast asleep." in e1.lines() && "Slowpoke is fast asleep." in e2.lines())
            assertTrue(e1.attacks().none { !it.byPlayer } && e2.attacks().none { !it.byPlayer })
            val e3 = rd(m, b, rng, wildActs = true)
            assertTrue("Slowpoke woke up!" in e3.lines())
            assertEquals(1, e3.attacks().count { !it.byPlayer })
            assertNull(b.enemy.status); assertNull(b.enemy.volatiles["sleepTurns"])
        }
    }

    @Test fun aSleepUsableMoveCanBeUsedWhileTheSleepStillCountsDown() {
        val m = main("tackle"); val w = wild("snore"); val b = R17C3.battle(w)
        b.enemy.status = "slp"; b.enemy.volatiles["sleepTurns"] = 2
        val e1 = rd(m, b, high, wildActs = true)
        assertEquals(1, e1.attacks().count { !it.byPlayer }); assertEquals("slp", b.enemy.status); assertEquals(1, b.enemy.volatiles["sleepTurns"])
        rd(m, b, high, wildActs = true)
        assertEquals(0, b.enemy.volatiles["sleepTurns"])
        val e3 = rd(m, b, high, wildActs = true)
        assertTrue("Slowpoke woke up!" in e3.lines())
    }

    // ---------------------------------------------------------------- binding and the lasting secondaries

    @Test fun wrapHoldsForFourOrFiveRoundsAndHurtsAnEighthEachTime() {
        val totals = HashSet<Int>()
        for (seed in 1..40) {
            val m = main("wrap"); val w = wild("splash"); val b = R17C3.battle(w)
            var hurts = 0; var freed = false
            for (i in 1..8) {
                val e = rd(m, b, Random(seed))
                if (i == 1) assertEquals("Slowpoke was wrapped by Fastie!", e.kinds(EffectKind.VOLATILE).first().text)
                e.kinds(EffectKind.RESIDUAL).filter { it.text == "Slowpoke is hurt by Wrap!" }.forEach {
                    hurts++
                    assertEquals(R17C3.data.let { StatCalc.maxHp(it, w) } / 8, it.amount)
                }
                if ("Slowpoke was freed from Wrap!" in e.lines()) { freed = true; break }
            }
            assertTrue(freed)
            totals += hurts
        }
        assertEquals(setOf(4, 5), totals)
    }

    @Test fun aBoundTargetIsNotBoundAgainAndBindingEndsWhenTheBinderLeaves() {
        val m = main("wrap"); val b = R17C3.battle(wild("splash"))
        rd(m, b); val left = b.enemy.volatiles.getValue("partiallytrapped")
        val e = rd(m, b)
        assertEquals(left - 1, b.enemy.volatiles["partiallytrapped"])
        assertTrue(e.lines().none { "was wrapped" in it })
        b.switchOutMain(data, m)
        assertNull(b.enemy.volatiles["partiallytrapped"]); assertNull(b.enemy.notes["partiallytrapped"])
    }

    @Test fun theWildOneBindsTheMainToo() {
        val m = R17C3.mon(300, 100, "tackle"); val b = R17C3.battle(wild("firespin"))
        val hp = m.hp
        val e = rd(m, b, low, wildActs = true)
        assertEquals("Fastie was trapped in the vortex!", e.kinds(EffectKind.VOLATILE).single().text)
        assertTrue(m.hp < hp)
        assertTrue("Fastie is hurt by Fire Spin!" in e.lines())
    }

    @Test fun saltCureHurtsAnEighthAQuarterForWaterAndSteelUntilTheFightEnds() {
        for ((sp, divisor) in listOf(301 to 8, 303 to 4, 304 to 4)) {
            val w = wild("splash", sp = sp); val b = R17C3.battle(w)
            val m = main("saltcure")
            val max = StatCalc.maxHp(data, w)
            var last = 0
            for (i in 1..2) { // (the wild one has 625 HP: the damage must stay below what is left)
                val e = rd(m, b)
                val hurt = e.kinds(EffectKind.RESIDUAL).single { "Salt Cure" in it.text }
                assertEquals("species $sp round $i", max / divisor, hurt.amount)
                last = i
            }
            assertEquals(2, last)
            assertNotNull(b.enemy.volatiles["saltcure"])
        }
    }

    @Test fun syrupBombLowersSpeedAtTheEndOfEachOfThreeRounds() {
        val m = main("syrupbomb"); val b = R17C3.battle(wild("splash"))
        val e1 = rd(m, b); assertEquals(-1, b.enemy.boosts[4]); assertTrue("Slowpoke got covered in sticky syrup!" in e1.lines())
        rd(m, b); assertEquals(-2, b.enemy.boosts[4])
        val e3 = rd(m, b); assertEquals(-3, b.enemy.boosts[4])
        assertNull(b.enemy.volatiles["syrupbomb"])
        assertTrue("Slowpoke is no longer covered in sticky syrup." in e3.lines())
    }

    @Test fun sparklingAriaCuresTheTargetsBurn() {
        val b = R17C3.battle(wild("splash")); b.enemy.status = "brn"
        val e = rd(main("sparklingaria"), b)
        assertNull(b.enemy.status)
        val cured = e.kinds(EffectKind.STATUS_CURED).single()
        assertEquals("brn", cured.stat); assertNull(cured.status)
        // nothing to cure: nothing happens
        val b2 = R17C3.battle(wild("splash")); b2.enemy.status = "psn"
        rd(main("sparklingaria"), b2)
        assertEquals("psn", b2.enemy.status)
    }

    @Test fun psychicNoiseStartsHealBlockForTwoRounds() {
        val b = R17C3.battle(wild("splash"))
        val m = main("psychicnoise")
        val e = rd(m, b)
        assertEquals("2 at the hit; R17C-8 counts it down at the end of each round, so the round it was hit in is the first", 1, b.enemy.volatiles["healblock"])
        assertTrue("Slowpoke was prevented from healing!" in e.lines())
        rd(m, b)
        assertNull(b.enemy.volatiles["healblock"])
    }

    @Test fun glaiveRushLeavesItsUserOpenUntilItsNextAction() {
        // the hit puts the drawback on the user, so the wild move in the same round deals double
        val m = main("glaiverush"); val b = R17C3.battle(wild("tackle"))
        val e = rd(m, b, high, wildActs = true)
        assertEquals(1, b.player.volatiles["glaiverush"])
        val doubled = e.attacks().single { !it.byPlayer }.damage
        val plain = rd(main("tackle"), R17C3.battle(wild("tackle")), high, wildActs = true).attacks().single { !it.byPlayer }.damage
        assertTrue(plain > 0); assertEquals(plain * 2, doubled)
        // the next action of the user ends it: a 1 % move that acts after that action misses again
        val b2 = R17C3.battle(wild("wildswing")); b2.player.volatiles["glaiverush"] = 1
        val e2 = rd(main("tackle"), b2, high, wildActs = true)
        assertNull(b2.player.volatiles["glaiverush"])
        assertTrue(e2.attacks().single { !it.byPlayer }.missed)
        // while it lasts (the wild one is faster here) the 1 % move never misses
        val b3 = R17C3.battle(wild("wildswing")); b3.player.volatiles["glaiverush"] = 1
        val e3 = rd(R17C3.mon(307, 50, "tackle"), b3, high, wildActs = true)
        assertFalse(e3.attacks().single { !it.byPlayer }.missed)
    }

    @Test fun movesAgainstAGlaiveRushUserNeverMiss() {
        // the wild one has the drawback; the Main's 5 % Bounce hit (charged) never misses
        val m = main("bounce"); val w = wild("splash"); val b = R17C3.battle(w)
        b.player.charging = "bounce"; b.enemy.volatiles["glaiverush"] = 1
        val e = rd(m, b, high)
        assertFalse(e.attacks().single().missed)
        assertTrue(w.hp < StatCalc.maxHp(data, w))
    }

    @Test fun triAttackMayBurnParalyzeOrFreeze() {
        val seen = HashSet<String>()
        for (seed in 1..60) {
            val b = R17C3.battle(wild("splash")); rd(main("triattack"), b, Random(seed)); b.enemy.status?.let { seen += it }
        }
        assertEquals(setOf("brn", "par", "frz"), seen)
        // a Fire type can't be burned, an Electric one not paralysed ...
        val noBurn = HashSet<String?>()
        for (seed in 1..60) { val b = R17C3.battle(wild("splash", sp = 306)); rd(main("triattack"), b, Random(seed)); noBurn += b.enemy.status }
        assertFalse("brn" in noBurn)
    }

    @Test fun direClawMayPoisonParalyzeOrPutToSleep() {
        val seen = HashSet<String>()
        for (seed in 1..60) {
            val b = R17C3.battle(wild("splash")); rd(main("direclaw"), b, Random(seed)); b.enemy.status?.let { seen += it }
        }
        assertEquals(setOf("psn", "par", "slp"), seen)
    }

    @Test fun alluringVoiceAndBurningJealousyNeedAStatThatRoseThisRound() {
        // the wild one is faster than "Slowfast" and raises its Attack first
        val m = R17C3.mon(307, 50, "alluringvoice"); val b = R17C3.battle(wild("swordsdance"))
        rd(m, b, high, wildActs = true)
        assertTrue("confusion" in b.enemy.volatiles)
        val m2 = R17C3.mon(307, 50, "burningjealousy"); val b2 = R17C3.battle(wild("swordsdance"))
        rd(m2, b2, high, wildActs = true)
        assertEquals("brn", b2.enemy.status)
        // nothing rose: nothing happens; and a rise of an earlier round does not count
        val b3 = R17C3.battle(wild("tackle"))
        rd(R17C3.mon(307, 50, "alluringvoice"), b3, high, wildActs = true)
        assertFalse("confusion" in b3.enemy.volatiles)
        val b4 = R17C3.battle(wild("splash")); b4.enemy.boosts[0] = 2
        rd(R17C3.mon(307, 50, "burningjealousy"), b4, high, wildActs = true)
        assertNull(b4.enemy.status)
        assertTrue("cleared at the end of the round: ${b2.enemy.notes}", b2.enemy.notes.keys.none { it.startsWith("cr.") })
    }

    // ---------------------------------------------------------------- crash and HP cost

    @Test fun aMissedJumpKickCrashesForHalfTheMaxHp() {
        val m = main("highjumpkick"); val b = R17C3.battle(wild("splash"))
        val max = StatCalc.maxHp(data, m)
        val e = rd(m, b, high)
        assertTrue(e.attacks().single().missed)
        val crash = e.kinds(EffectKind.RECOIL).single()
        assertEquals("Fastie kept going and crashed!", crash.text); assertEquals(max / 2, crash.amount)
        assertEquals(max - max / 2, m.hp)
    }

    @Test fun anImmuneTargetOrABlockedMoveCrashesToo_ButAHitDoesNot() {
        val m = main("highjumpkick"); val b = R17C3.battle(wild("splash", sp = 305)) // Fighting vs Ghost
        assertEquals(1, rd(m, b, low).kinds(EffectKind.RECOIL).size)
        val m2 = main("highjumpkick"); val b2 = R17C3.battle(wild("splash"))
        val protect = object : BattleRules { override fun blocks(r: Round, p: Boolean, move: Move): Boolean { if (p) { r.fx(!p, "Slowpoke protected itself!"); return true }; return false } }
        R17C3.withRules(protect) { assertEquals(1, rd(m2, b2, low).kinds(EffectKind.RECOIL).size) }
        val m3 = main("highjumpkick"); val b3 = R17C3.battle(wild("splash"))
        val hit = rd(m3, b3, low)
        assertFalse(hit.attacks().single().missed); assertTrue(hit.kinds(EffectKind.RECOIL).isEmpty())
    }

    @Test fun steelBeamAndChloroblastCostHalfTheMaxHpRoundedUpHitOrMiss() {
        for (id in listOf("steelbeam", "chloroblast")) for (rng in listOf(low, high)) {
            val m = main(id); val b = R17C3.battle(wild("splash"))
            val max = StatCalc.maxHp(data, m)
            val e = rd(m, b, rng)
            val cost = e.kinds(EffectKind.RECOIL).single()
            assertEquals("Fastie lost half of its max HP!", cost.text)
            assertEquals("$id", (max + 1) / 2, cost.amount)
        }
    }

    @Test fun theHpCostCanMakeTheUserFaint() {
        val m = main("steelbeam"); m.hp = 10; val b = R17C3.battle(wild("splash"))
        val e = rd(m, b, low)
        assertEquals(0, m.hp)
        assertTrue(e.any { it is GameEvent.Fainted && it.player })
    }

    // ---------------------------------------------------------------- the save

    @Test fun theConditionsSurviveASaveAndTheLockGoesOn() {
        val m = main("outrage", "tackle"); val b = R17C3.battle(wild("splash"))
        b.player.volatiles["lockedmove"] = 2; b.player.notes["lockedmove"] = "outrage"
        b.player.volatiles["confusion"] = 4; b.enemy.volatiles["partiallytrapped"] = 3; b.enemy.notes["partiallytrapped"] = "wrap"
        b.enemy.volatiles["saltcure"] = 1; b.player.charging = null
        val s = GameState(schema = GameState.SCHEMA)
        s.mons += Mon("u1", 19, "Ratty", 10, 0, Stats(15, 15, 15, 15, 15, 15), Stats.ZERO, Nature.HARDY, 'M', false, "x", mutableListOf("tackle"), 20)
        s.team += "u1"; s.battle = b
        val back = SaveCodec.decode(SaveCodec.encode(s)).battle!!
        assertEquals(b.player.volatiles, back.player.volatiles); assertEquals(b.player.notes, back.player.notes)
        assertEquals(b.enemy.volatiles, back.enemy.volatiles); assertEquals(b.enemy.notes, back.enemy.notes)
        val e = rd(m, back, Random(5))
        assertEquals("Outrage", e.attacks().single { it.byPlayer }.move)
        assertTrue(e.lines().any { "is hurt by Wrap" in it })
        assertEquals(1, back.player.volatiles["lockedmove"])
    }

    // ---------------------------------------------------------------- the move texts

    @Test fun theMoveTextsAreTrueAndPlain() {
        val say = { id: String -> MoveText.describe(data.move(id)!!) }
        assertTrue(say("wrap").startsWith("Holds the target for 4–5 turns"))
        assertFalse("switch" in say("wrap").lowercase()) // binding never blocks a switch: the text says nothing about it
        assertTrue(say("bite").contains("flinch"))
        assertEquals("Raises the target's Attack by 2. Confuses the target.", say("swagger"))
        assertEquals("Makes the target fall in love if one of you is male and the other female. A Pokémon in love can't move half of the time.", say("attract"))
        assertTrue(say("sparklingaria").endsWith("Cures the target's burn."))
        assertTrue(MoveText.describe(data.move("triattack")!!.copy(secondary = Secondary(20))).contains("May burn, paralyze or freeze the target (20 %)."))
        assertTrue(say("alluringvoice").contains("Confuses the target if its stats were raised this turn."))
    }
}
