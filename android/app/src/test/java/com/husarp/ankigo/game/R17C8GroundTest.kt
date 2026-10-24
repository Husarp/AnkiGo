package com.husarp.ankigo.game

import com.husarp.ankigo.game.R17C8.attacks
import com.husarp.ankigo.game.R17C8.high
import com.husarp.ankigo.game.R17C8.lines
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * R17C-8: grounding and aim: Magnet Rise, Telekinesis, Smack Down, Thousand Arrows, Lock-On / Mind Reader, Foresight / Odor Sleuth and Miracle
 * Eye. The Main acts first; the wild one idles unless a test says so.
 */
class R17C8GroundTest {
    private val data = R17C8.data
    private fun main(vararg m: String) = R17C8.main(*m)
    private fun wild(vararg m: String, sp: Int = 801) = R17C8.wild(*m, sp = sp)
    private fun rd(main: Mon, b: Battle, rng: Random = Random(1), wildActs: Boolean = false, choice: MoveChoice = MoveChoice.RANDOM) =
        R17C8.round(main, b, rng, enemyActs = wildActs, choice = choice)
    private fun <T> forced(id: String, block: () -> T): T = R17C8.withRules(R17C8.forcing(id), block = block)
    private fun List<GameEvent>.failedMain() = attacks().single { it.byPlayer }.failed

    private fun hit(m: Mon, b: Battle, move: String, byMain: Boolean = true): Hit {
        val v = R17C8.view(m, b)
        val mv = data.move(move)!!
        return if (byMain) BattleEngine.newHit(v, true, m, b.player, b.wild, b.enemy, mv) else BattleEngine.newHit(v, false, b.wild, b.enemy, m, b.player, mv)
    }

    /** The effectiveness of [move] used by the Main against the wild one right now. */
    private fun eff(m: Mon, b: Battle, move: String): Double =
        BattleEngine.damage(data, m, b.player, b.wild, b.enemy, data.move(move)!!, Random(1), forceCrit = false, forceRoll = 100, view = R17C8.view(m, b), attackerIsMain = true).effectiveness

    // ---------------------------------------------------------------- Magnet Rise and Telekinesis

    @Test fun magnetRiseMakesGroundMovesMissForFiveRoundsAndThenWearsOff() {
        val m = main("tackle"); val b = R17C8.battle(wild("earthquake"))
        val r1 = forced("magnetrise") { rd(m, b, wildActs = true) }
        assertTrue("Fastie levitated with electromagnetism!" in r1.lines())
        val quake = r1.attacks().single { !it.byPlayer }
        assertTrue(quake.failed); assertEquals(0, quake.damage)
        assertTrue("It doesn't affect Fastie…" in r1.lines())
        assertEquals(4, b.player.volatiles["magnetrise"])
        repeat(3) { assertEquals(0, rd(m, b, wildActs = true).attacks().single { !it.byPlayer }.damage) }
        val r5 = rd(m, b, wildActs = true) // the fifth round: the last one it lasts
        assertEquals(0, r5.attacks().single { !it.byPlayer }.damage)
        assertNull(b.player.volatiles["magnetrise"])
        assertTrue(r5.lines().any { "electromagnetism wore off" in it })
        assertTrue(rd(m, b, wildActs = true).attacks().single { !it.byPlayer }.damage > 0)
    }

    @Test fun magnetRiseFailsUnderGravityIngrainAndTelekinesisAndGravityKeepsTheGround() {
        val m = main("tackle")
        val gravity = R17C8.battle(wild("tackle")); gravity.field.rooms["gravity"] = 5
        assertTrue(forced("magnetrise") { rd(m, gravity) }.failedMain()); assertNull(gravity.player.volatiles["magnetrise"])
        val roots = R17C8.battle(wild("tackle")); roots.player.volatiles["ingrain"] = 1
        assertTrue(forced("magnetrise") { rd(main("tackle"), roots) }.failedMain())
        // a Pokémon that rose before Gravity came down is on the ground again
        val b = R17C8.battle(wild("tackle")); b.player.volatiles["magnetrise"] = 3
        assertFalse(R17C8.view(m, b).grounded(true))
        b.field.rooms["gravity"] = 5
        assertTrue(R17C8.view(m, b).grounded(true))
        b.field.rooms.clear(); b.player.volatiles["ingrain"] = 1
        assertTrue(R17C8.view(m, b).grounded(true))
    }

    @Test fun telekinesisLiftsTheTargetEveryMoveHitsAndGroundMovesMiss() {
        val m = main("earthquake"); val b = R17C8.battle(wild("tackle"))
        forced("telekinesis") { rd(m, b) }
        assertEquals(2, b.enemy.volatiles["telekinesis"]) // 3 at the cast, the round is over
        assertFalse(R17C8.view(m, b).grounded(false))
        assertEquals(50.0, MoveControl.accuracy(hit(m, b, "tackle", byMain = false), 50.0)!!, 0.0) // the lifted one is the wild one: its own moves are normal
        assertNull(MoveControl.accuracy(hit(m, b, "wildswing"), 1.0))
        // forced: a Smart Main no longer picks Earthquake into a lifted target (R17C FIX-A, review item 7), the engine rule is still the same
        val r2 = forced("earthquake") { rd(m, b) }
        assertTrue(r2.attacks().single { it.byPlayer }.failed); assertTrue("It doesn't affect Slowpoke…" in r2.lines())
        // it wears off after the third round
        rd(m, b); assertNull(b.enemy.volatiles["telekinesis"])
    }

    @Test fun aLiftedTargetCannotDodgeEvenWithTheBiggestRandomNumbers() {
        val m = main("wildswing"); val b = R17C8.battle(wild("tackle"))
        assertTrue(rd(m, b, high).attacks().single { it.byPlayer }.missed)
        val m2 = main("wildswing"); val b2 = R17C8.battle(wild("tackle")); b2.enemy.volatiles["telekinesis"] = 3
        assertFalse(rd(m2, b2, high).attacks().single { it.byPlayer }.missed)
    }

    @Test fun gravityBringsEveryLiftedPokemonDownForGood() {
        val m = main("tackle"); val b = R17C8.battle(wild("tackle"))
        b.player.volatiles["magnetrise"] = 3; b.enemy.volatiles["telekinesis"] = 3
        b.field.rooms["gravity"] = 5
        assertTrue(R17C8.view(m, b).grounded(true)); assertTrue(R17C8.view(m, b).grounded(false))
        assertEquals("the lifted one can dodge under Gravity", 50.0, MoveControl.accuracy(hit(m, b, "tackle"), 50.0)!!, 0.0)
        rd(m, b)
        assertNull(b.player.volatiles["magnetrise"]); assertNull(b.enemy.volatiles["telekinesis"])
        b.field.rooms.clear()
        assertTrue("and they stay down", R17C8.view(m, b).grounded(true))
    }

    @Test fun telekinesisFailsOnADigglettAndOnAGroundedTarget() {
        val m = main("tackle")
        assertTrue(forced("telekinesis") { rd(m, R17C8.battle(R17C8.mon(50, 100, "tackle"))) }.failedMain())
        val b = R17C8.battle(wild("tackle")); b.enemy.volatiles["ingrain"] = 1
        assertTrue(forced("telekinesis") { rd(main("tackle"), b) }.failedMain())
        val b2 = R17C8.battle(wild("tackle")); b2.enemy.volatiles["smackdown"] = 1
        assertTrue(forced("telekinesis") { rd(main("tackle"), b2) }.failedMain())
    }

    // ---------------------------------------------------------------- Smack Down and Thousand Arrows

    @Test fun smackDownBringsAFlyingTargetToTheGroundForTheFight() {
        val m = main("smackdown"); val b = R17C8.battle(wild("tackle", sp = 806)) // a pure Flying type
        assertEquals(0.0, eff(m, b, "earthquake"), 0.0)
        val e = rd(m, b)
        assertTrue("Birdy fell straight down!" in e.lines())
        assertTrue(R17C8.view(m, b).grounded(false)); assertTrue("smackdown" in b.enemy.volatiles)
        assertTrue("Earthquake hits it now", eff(m, b, "earthquake") > 0.0)
        rd(m, b); assertTrue("for the fight", "smackdown" in b.enemy.volatiles)
    }

    @Test fun smackDownDoesNothingToATargetThatIsOnTheGroundAndEndsMagnetRise() {
        val m = main("smackdown"); val b = R17C8.battle(wild("tackle"))
        rd(m, b); assertFalse("smackdown" in b.enemy.volatiles)
        val m2 = main("smackdown"); val b2 = R17C8.battle(wild("tackle")); b2.enemy.volatiles["magnetrise"] = 4
        val e = rd(m2, b2)
        assertNull(b2.enemy.volatiles["magnetrise"]); assertTrue("smackdown" in b2.enemy.volatiles)
        assertTrue("Slowpoke fell straight down!" in e.lines())
        // Telekinesis ends the same way
        val b3 = R17C8.battle(wild("tackle")); b3.enemy.volatiles["telekinesis"] = 3
        rd(main("smackdown"), b3)
        assertNull(b3.enemy.volatiles["telekinesis"]); assertTrue(R17C8.view(main("tackle"), b3).grounded(false))
    }

    @Test fun thousandArrowsHitsFlyingTypesAndGroundsThem() {
        val m = main("thousandarrows"); val b = R17C8.battle(wild("tackle", sp = 806))
        val e = forced("thousandarrows") { rd(m, b) }
        val a = e.attacks().single { it.byPlayer }
        assertTrue(a.damage > 0); assertEquals(1.0, a.effectiveness, 0.0)
        assertTrue(R17C8.view(m, b).grounded(false))
        // an ordinary Ground move still can't hit a Flying type that is up
        assertEquals(0.0, eff(m, R17C8.battle(wild("tackle", sp = 806)), "earthquake"), 0.0)
    }

    // ---------------------------------------------------------------- Lock-On

    @Test fun lockOnMakesTheNextMoveHitAndOnlyThatOne() {
        val m = main("wildswing"); val b = R17C8.battle(wild("tackle"))
        val r1 = forced("lockon") { rd(m, b, high) }
        assertTrue("Fastie took aim at Slowpoke!" in r1.lines())
        assertEquals(1, b.player.volatiles["lockon"])
        val r2 = rd(m, b, high)
        assertFalse("a 1 % move hits with the biggest random numbers", r2.attacks().single { it.byPlayer }.missed)
        assertNull(b.player.volatiles["lockon"])
        assertTrue("the next one misses again", rd(m, b, high).attacks().single { it.byPlayer }.missed)
    }

    @Test fun mindReaderDoesTheSameAndALostActionEndsIt() {
        val m = main("wildswing"); val b = R17C8.battle(wild("tackle"))
        forced("mindreader") { rd(m, b, high) }
        assertEquals(1, b.player.volatiles["lockon"])
        b.player.status = "slp" // asleep for the next action: it is lost, the aim with it
        rd(m, b, high)
        b.player.status = null
        assertTrue(rd(m, b, high).attacks().single { it.byPlayer }.missed)
    }

    // ---------------------------------------------------------------- Foresight, Odor Sleuth, Miracle Eye

    @Test fun foresightLetsNormalAndFightingMovesHitAGhostAndIgnoresEvasion() {
        val m = main("karatechop"); val b = R17C8.battle(wild("tackle", sp = 804))
        assertEquals(0.0, eff(m, b, "karatechop"), 0.0); assertEquals(0.0, eff(m, b, "tackle"), 0.0)
        val e = forced("foresight") { rd(m, b) }
        assertTrue("Fastie identified Boo!" in e.lines())
        assertEquals(1.0, eff(m, b, "karatechop"), 0.0); assertEquals(1.0, eff(m, b, "tackle"), 0.0)
        assertEquals("the other matchups stay as they are", 2.0, eff(m, b, "shadowball"), 0.0)
        assertEquals(1.0, eff(m, b, "psychic"), 0.0)
    }

    @Test fun odorSleuthIsTheSameAsForesight() {
        val m = main("tackle"); val b = R17C8.battle(wild("tackle", sp = 804))
        forced("odorsleuth") { rd(m, b) }
        assertEquals(1.0, eff(m, b, "karatechop"), 0.0)
    }

    @Test fun foresightIgnoresARaisedEvasionAndNothingElse() {
        val m = main("tackle"); val b = R17C8.battle(wild("tackle")); b.enemy.boosts[6] = 6
        assertEquals(100.0, MoveControl.accuracy(hit(m, b, "tackle"), 100.0)!!, 0.0)
        b.enemy.volatiles["foresight"] = 1
        assertEquals(300.0, MoveControl.accuracy(hit(m, b, "tackle"), 100.0)!!, 1e-9) // x3 cancels the -6 stage the engine adds
        b.enemy.boosts[6] = -3 // a lowered evasion is not touched
        assertEquals(100.0, MoveControl.accuracy(hit(m, b, "tackle"), 100.0)!!, 0.0)
        // end to end with the biggest random numbers
        val m1 = main("tackle"); val plain = R17C8.battle(wild("tackle")); plain.enemy.boosts[6] = 6
        assertTrue(rd(m1, plain, high).attacks().single { it.byPlayer }.missed)
        val m2 = main("tackle"); val seen = R17C8.battle(wild("tackle")); seen.enemy.boosts[6] = 6; seen.enemy.volatiles["foresight"] = 1
        assertFalse(rd(m2, seen, high).attacks().single { it.byPlayer }.missed)
        val m3 = main("tackle"); val eye = R17C8.battle(wild("tackle")); eye.enemy.boosts[6] = 6; eye.enemy.volatiles["miracleeye"] = 1
        assertFalse(rd(m3, eye, high).attacks().single { it.byPlayer }.missed)
    }

    @Test fun miracleEyeLetsPsychicMovesHitADarkType() {
        val m = main("psychic"); val b = R17C8.battle(wild("tackle", sp = 805))
        assertEquals(0.0, eff(m, b, "psychic"), 0.0)
        val e = forced("miracleeye") { rd(m, b) }
        assertTrue("Fastie identified Darky!" in e.lines())
        assertEquals(1.0, eff(m, b, "psychic"), 0.0)
        assertEquals("a Normal move was never the issue", 1.0, eff(m, b, "tackle"), 0.0)
    }

    @Test fun theSmartChoiceSeesThroughTheseEffects() {
        // vs a Ghost: a Fighting move can't hurt, so the Smart Main would use Struggle; with Foresight on the target it attacks
        val m = main("karatechop"); val b = R17C8.battle(wild("tackle", sp = 804))
        assertEquals("Struggle", MoveChooser.pickMain(MoveChoice.SMART, data, b, m, Random(1)).name)
        b.enemy.volatiles["foresight"] = 1
        assertEquals("Karate Chop", MoveChooser.pickMain(MoveChoice.SMART, data, b, m, Random(1)).name)
    }

    @Test fun theGroundMovesSayWhatTheyDoInPlainWords() {
        for (id in listOf("magnetrise", "telekinesis", "smackdown", "thousandarrows", "lockon", "mindreader", "foresight", "odorsleuth", "miracleeye")) {
            val text = MoveText.describe(data.move(id)!!)
            assertTrue("$id: $text", text.endsWith(".") && "Has no effect" !in text && "→" !in text)
        }
    }
}
