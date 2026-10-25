package com.husarp.ankigo.game

import com.husarp.ankigo.game.save.SaveCodec
import com.husarp.ankigo.ui.NextFight
import com.husarp.ankigo.ui.StudyCharge
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.TimeZone
import kotlin.random.Random

/**
 * R19-T, pure (real [Game] events, made-up species):
 *  - PLAN 284: the OK of a new card is a neutral move: one of the round's answers, a grey dot, no power. OK + Good = Good's round,
 *    OK + OK = an empty round, the combo never changes.
 *  - PLAN 288: a switch takes none of the round's answers and marks no dot; the wild Pokémon gets exactly one extra attack.
 *  - PLAN 285 (the game side and the pure hold rule): a switch made while a result holds the next fight starts nothing.
 *  - Saves: an old save mid-round still plays; the OK survives a save.
 */
class R19TTurnsTest {
    private val data = TestData.world()
    private val now = ZonedDateTime.of(2026, 10, 24, 12, 0, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli()
    private var card = 1L
    private lateinit var savedZone: TimeZone

    @Before fun utcZone() { savedZone = TimeZone.getDefault(); TimeZone.setDefault(TimeZone.getTimeZone("UTC")) }
    @After fun restoreZone() = TimeZone.setDefault(savedZone)

    /** Main t0, t1 and t2 on the bench, a fight against Ratty (one move: Tackle, so every wild action is one Attack event). */
    private fun game(rounds: Int = 1, seed: Int = 7): Game {
        val s = TestData.state(data, 25, 20, listOf("tackle"))
        s.team.clear(); s.mons.clear()
        for ((i, sp) in listOf(25, 16, 19).withIndex()) {
            val m = TestData.mon(data, sp, 20, listOf("tackle"), uid = "t$i")
            s.mons += m; s.team += m.uid
            s.seen += m.species; s.caught += m.species
        }
        s.settings.moveChoice = MoveChoice.RANDOM
        val wild = TestData.mon(data, 19, 12, listOf("tackle"), uid = "wild")
        wild.hp = StatCalc.maxHp(data, wild)
        s.battle = Battle(wild, Tier.NORMAL, startedAt = now, rounds = rounds)
        return Game(data, s, Random(seed)) { now }
    }

    private fun Game.answer(button: Int, neutral: Boolean = false) = onAnswer(Answer(card++, button, now, false, neutral))
    private fun Game.ok() = answer(1, neutral = true)
    /** The wild one is back at full HP (a round before must not end the fight by chance). */
    private fun Game.refill() { state.battle?.let { it.wild.hp = StatCalc.maxHp(data, it.wild) } }

    private inline fun <reified T : GameEvent> List<GameEvent>.of() = filterIsInstance<T>()
    private fun List<GameEvent>.wildAttacks() = of<GameEvent.Attack>().filter { !it.byPlayer }
    private fun List<GameEvent>.mainAttacks() = of<GameEvent.Attack>().filter { it.byPlayer }

    private fun dots(vararg batches: List<GameEvent>): List<Int> {
        val c = StudyCharge()
        batches.forEach { b -> b.forEach { c.observe(it, Tuning.CARDS_PER_ROUND) } }
        return c.filled
    }

    // ---------------------------------------------------------------- 284: OK is a neutral move with a grey dot

    @Test fun okPlusGoodIsAGoodRoundWithOneGreyDot() {
        val g = game()
        val first = g.ok()
        assertEquals(listOf(GameEvent.Charge(0, 1, 2)), first.of<GameEvent.Charge>())
        val second = g.answer(3)
        val start = second.of<GameEvent.RoundStart>().single()
        assertEquals("Good's strength alone", Tuning.MAIN_POWER[3], start.multiplier, 0.0)
        assertTrue(start.mainActs); assertFalse(start.enemyActs)
        assertEquals(Tuning.POINTS[3], start.points)
        assertTrue("your Pokémon attacks", second.mainAttacks().isNotEmpty())
        assertTrue("the wild one does not", second.wildAttacks().isEmpty())
        assertEquals("one grey dot, one Good dot", listOf(StudyCharge.OK, 3), dots(first, second))
    }

    @Test fun goodPlusOkIsTheSameRoundWithTheGreyDotSecond() {
        val g = game()
        val first = g.answer(3)
        val second = g.ok()
        val start = second.of<GameEvent.RoundStart>().single()
        assertEquals(Tuning.MAIN_POWER[3], start.multiplier, 0.0)
        assertTrue(start.mainActs); assertFalse(start.enemyActs)
        assertEquals(listOf(3, StudyCharge.OK), dots(first, second))
        assertEquals(0, g.state.battle!!.answers); assertTrue(g.state.battle!!.buttons.isEmpty())
    }

    @Test fun okPlusAgainIsAnAgainRoundAndEasyKeepsItsFullPower() {
        val a = game(); a.ok()
        val again = a.answer(1)
        val s1 = again.of<GameEvent.RoundStart>().single()
        assertTrue(s1.enemyActs); assertFalse(s1.mainActs)
        assertEquals(Tuning.WILD_POWER[1], s1.enemyPower, 0.0)
        assertTrue(again.mainAttacks().isEmpty()); assertEquals(1, again.wildAttacks().size)
        val b = game(); b.answer(4)
        val s2 = b.ok().of<GameEvent.RoundStart>().single()
        assertEquals("Easy alone, not Easy + anything", Tuning.MAIN_POWER[4], s2.multiplier, 0.0)
    }

    @Test fun okPlusOkIsAnEmptyRoundWithNoComboChange() {
        val g = game()
        g.answer(3); g.answer(4); g.refill()           // a round, combo 2
        val hpMain = g.state.main!!.hp; val hpWild = g.state.battle!!.wild.hp
        val rounds = g.state.battle!!.rounds
        val streak = g.state.cardStreak
        val first = g.ok()
        val second = g.ok()
        val start = second.of<GameEvent.RoundStart>().single()
        assertFalse(start.mainActs); assertFalse(start.enemyActs)
        assertEquals(0.0, start.multiplier, 0.0); assertEquals(0.0, start.enemyPower, 0.0); assertEquals(0, start.points)
        assertTrue("nobody attacks", (first + second).of<GameEvent.Attack>().isEmpty())
        assertEquals(hpMain, g.state.main!!.hp); assertEquals(hpWild, g.state.battle!!.wild.hp)
        assertEquals("it is a round", rounds + 1, g.state.battle!!.rounds)
        assertEquals(streak, g.state.cardStreak)
        assertEquals(streak, second.of<GameEvent.Counted>().single().cardStreak)
        assertEquals("two grey dots", listOf(StudyCharge.OK, StudyCharge.OK), dots(first, second))
    }

    @Test fun anOkNeverBreaksOrBuildsTheComboInARound() {
        val g = game()
        g.answer(3); g.answer(3); g.refill()
        assertEquals(2, g.state.cardStreak)
        g.ok(); g.answer(3)
        assertEquals("the Good counts, the OK does not", 3, g.state.cardStreak)
    }

    @Test fun theOkCodeIsNotEasyAndTheRulePlansAreRight() {
        assertEquals(RoundRule.plan(listOf(3)), RoundRule.plan(listOf(Tuning.BUTTON_OK, 3)))
        assertEquals(RoundRule.plan(listOf(1)), RoundRule.plan(listOf(Tuning.BUTTON_OK, 1)))
        assertEquals(RoundRule.Plan(false, 0.0, false, 0.0), RoundRule.plan(listOf(Tuning.BUTTON_OK, Tuning.BUTTON_OK)))
        assertEquals("the neutral code is not read as Easy", RoundRule.Plan(false, 0.0, false, 0.0), RoundRule.plan(listOf(Tuning.BUTTON_OK)))
    }

    @Test fun anOkAsTheFirstAnswerOfADayStartsTheFightAndKeepsItsDot() {
        val g = game(); g.state.battle = null
        val ev = g.ok()
        assertEquals(1, ev.of<GameEvent.Encounter>().size)
        assertEquals("the dot survives the Encounter that follows the answer", listOf(StudyCharge.OK), dots(ev))
    }

    @Test fun theOkLineIsTrueNow() {
        val line = com.husarp.ankigo.study.NewCardOk.LOG_LINE
        assertFalse(line, line.contains("No attacks", ignoreCase = true))
        assertTrue(line, line.contains("with no power"))
    }

    // ---------------------------------------------------------------- 288: a switch is one extra wild attack, no dot

    @Test fun anImmediateSwitchMidRoundIsExactlyOneExtraWildAttackAndTouchesNoAnswer() {
        val g = game()
        val first = g.answer(3)
        val b = g.state.battle!!
        val answers = b.answers; val tally = b.tally; val buttons = b.buttons.toList(); val rounds = b.rounds
        val r = g.requestMain("t1", cardShowing = false)
        assertTrue(r.events.any { it is GameEvent.MainChanged })
        assertEquals(1, r.events.of<GameEvent.SwitchHit>().size)
        assertEquals("exactly one extra wild attack", 1, r.events.wildAttacks().size)
        assertTrue(r.events.none { it is GameEvent.Counted || it is GameEvent.Charge || it is GameEvent.RoundStart })
        assertEquals(answers, b.answers); assertEquals(tally, b.tally); assertEquals(buttons, b.buttons); assertEquals(rounds, b.rounds)
        assertEquals("no dot is filled or coloured", listOf(3), dots(first, r.events))
    }

    @Test fun theSwitchTakesNoSlotSoTheRoundStillEndsAtTheSecondAnswer() {
        val g = game()
        g.answer(3)
        g.requestMain("t1", cardShowing = false)
        val second = g.answer(3)
        val start = second.of<GameEvent.RoundStart>().single()
        assertEquals("two answers, Good + Good", 2 * Tuning.MAIN_POWER[3], start.multiplier, 0.0)
        assertEquals(0, g.state.battle!!.answers)
    }

    @Test fun aQueuedSwitchComesInWithOneWildAttackAndLeavesTheAnswerSlotsAlone() {
        val g = game()
        g.requestMain("t1")
        val first = g.answer(3)
        assertTrue(first.of<GameEvent.MainChanged>().single().queued)
        assertEquals(1, first.of<GameEvent.SwitchHit>().size)
        assertEquals(1, first.wildAttacks().size)
        assertTrue("the round goes on", first.none { it is GameEvent.RoundStart })
        assertEquals(1, g.state.battle!!.answers)
        assertEquals("the answer's own colour, nothing mauve", listOf(3), dots(first))
        val second = g.answer(4)
        assertEquals("the round ended with the next answer", 1, second.of<GameEvent.RoundStart>().size)
        assertEquals(listOf(3, 4), dots(first, second))
    }

    @Test fun aSwitchOnTheRoundEndingAnswerStillGivesJustOneExtraWildAttack() {
        val g = game()
        val first = g.answer(3)
        g.requestMain("t1")
        val e = g.answer(4)          // Good + Easy: only the Main attacks in the round, then the switch comes in
        assertEquals(1, e.of<GameEvent.RoundStart>().size)
        assertEquals("the round gave the wild one nothing, the switch gave it one attack", 1, e.wildAttacks().size)
        assertEquals("both dots in the answers' colours", listOf(3, 4), dots(first, e))
    }

    @Test fun anOkCountsAsAnAnswerSoASwitchAfterItCostsTheHit() {
        val g = game(rounds = 0)
        val free = g.requestMain("t1", cardShowing = false)
        assertTrue("nothing answered in this battle yet: free (PLAN 135)", free.events.none { it is GameEvent.SwitchHit })
        g.ok()
        val r = g.requestMain("t2", cardShowing = false)
        assertEquals(1, r.events.of<GameEvent.SwitchHit>().size)
        assertEquals(1, r.events.wildAttacks().size)
        assertEquals("the OK's dot stays grey", listOf(StudyCharge.OK), dots(listOf(GameEvent.Counted(1, 0, 1, 0, false, true), GameEvent.Charge(0, 1, 2)), r.events))
    }

    // ---------------------------------------------------------------- 285: a switch on the Continue screen starts nothing

    @Test fun aSwitchWhileAResultHoldsMakesOnlyAMainChanged() {
        val g = game(rounds = 0)
        val next = g.state.battle!!
        val uid = next.wild.uid
        val r = g.requestMain("t1", cardShowing = false)   // the Study / Battle screens ask with cardShowing = false while a result holds
        assertEquals(listOf<Class<*>>(GameEvent.MainChanged::class.java), r.events.map { it.javaClass })
        assertEquals("the next wild one is still the same one", uid, g.state.battle!!.wild.uid)
        assertTrue(r.events.none { it is GameEvent.Encounter || it is GameEvent.Counted })
        assertFalse("nothing in a switch's events lets the hold go", NextFight.releasesHold(r.events))
    }

    @Test fun onlyAnAnswerOrABallThrowReleasesAHeldFight() {
        val switch = listOf(GameEvent.SwitchQueued("t1", "X"), GameEvent.MainChanged("t1", "X"),
            GameEvent.SwitchHit("Wild Ratty", "X", 10, 20), GameEvent.Effect(false, "x", 0, 0, null, EffectKind.STATUS_APPLIED, null, 0, 0))
        assertFalse(NextFight.releasesHold(switch))
        assertFalse(NextFight.releasesHold(emptyList()))
        assertTrue(NextFight.releasesHold(listOf(GameEvent.Counted(3, 10, 1, 1, false), GameEvent.Charge(10, 1, 2))))
        assertTrue("an OK is an answer too", NextFight.releasesHold(listOf(GameEvent.Counted(1, 0, 1, 0, false, true))))
        assertTrue(NextFight.releasesHold(switch + GameEvent.Counted(3, 10, 1, 1, false)))
    }

    // ---------------------------------------------------------------- saves

    @Test fun theOkSurvivesASave() {
        val g = game()
        g.ok()
        assertEquals(listOf(Tuning.BUTTON_OK), g.state.battle!!.buttons)
        val back = Game(data, SaveCodec.decode(SaveCodec.encode(g.state)), Random(7)) { now }
        assertEquals(listOf(Tuning.BUTTON_OK), back.state.battle!!.buttons)
        assertEquals(1, back.state.battle!!.answers)
        val start = back.answer(3).of<GameEvent.RoundStart>().single()
        assertEquals("Good's strength alone, after the reload", Tuning.MAIN_POWER[3], start.multiplier, 0.0)
        val c = StudyCharge(); c.restore(1, 0, Tuning.CARDS_PER_ROUND, listOf(Tuning.BUTTON_OK))
        assertEquals("the restored dot is grey, not an Again", listOf(StudyCharge.OK), c.filled)
    }

    @Test fun aSavedRoundOfAnOlderVersionStillPlays() {
        // the older app counted no OK: one Good was waiting, the OK in between never entered the round; that save has no saved buttons at all
        val g = game()
        g.answer(3)
        val json = JSONObject(SaveCodec.encode(g.state))
        json.getJSONObject("battle").remove("buttons")
        val old = Game(data, SaveCodec.decode(json.toString()), Random(7)) { now }
        assertEquals(1, old.state.battle!!.answers); assertTrue(old.state.battle!!.buttons.isEmpty())
        val ev = old.ok()
        val start = ev.of<GameEvent.RoundStart>().single()
        assertEquals("the waiting Good is read from the tally, the OK adds nothing", Tuning.MAIN_POWER[3], start.multiplier, 0.0)
        assertEquals(0, old.state.battle!!.answers)
        // the dots of such a save: one answer read from the tally, as before
        val c = StudyCharge(); c.restore(1, 10, Tuning.CARDS_PER_ROUND, emptyList())
        assertEquals(listOf(3), c.filled)
    }

    @Test fun aSavedButtonCodeBeyondTheOkIsStillClamped() {
        val g = game()
        g.answer(3)
        val json = JSONObject(SaveCodec.encode(g.state))
        val arr = json.getJSONObject("battle").getJSONArray("buttons")
        arr.put(0, 9)
        val back = SaveCodec.decode(json.toString())
        assertEquals(listOf(Tuning.BUTTON_OK), back.battle!!.buttons)
        assertNull(back.pendingMain)
    }
}
