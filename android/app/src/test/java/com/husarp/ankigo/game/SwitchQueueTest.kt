package com.husarp.ankigo.game

import com.husarp.ankigo.game.save.SaveCodec
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.TimeZone
import kotlin.random.Random

/**
 * PLAN 206 (R14-B1-D): a switch made while a card shows is queued and comes in right after that card is answered. Made-up species and
 * moves only (TestData.world). The mechanics of the hit itself are SwitchHitTest's; here: what is queued, when it is applied, that it
 * is applied once and counted once, and that the queue is saved.
 */
class SwitchQueueTest {
    private val base = TestData.world()
    private val data = GameData(
        base.species,
        TestData.MOVES + listOf(Move("wildswing", "Wild Swing", Type.NORMAL, Category.PHYSICAL, 60, 1)).associateBy { it.id },
        base.growth, base.itemPrices,
    )
    private val now = ZonedDateTime.of(2026, 10, 13, 12, 0, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli()
    private lateinit var savedZone: TimeZone

    @Before fun utcZone() {
        savedZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After fun restoreZone() = TimeZone.setDefault(savedZone)

    /** A team of [team] (t0 = the main) and [box] box Pokémon (b3, b4 ...), in a fight against a wild Ratty with a round already played (so a switch costs the hit). */
    private fun game(seed: Int = 7, team: Int = 3, box: Int = 2, fight: Boolean = true): Game {
        val s = TestData.state(data, 25, 20, listOf("tackle"))
        s.team.clear(); s.mons.clear()
        val species = listOf(25, 16, 19, 54, 92, 133, 129, 63, 16)
        for (i in 0 until team + box) {
            val m = TestData.mon(data, species[i % species.size], 10 + i, listOf("tackle"), uid = if (i < team) "t$i" else "b$i")
            s.mons += m
            if (i < team) s.team += m.uid
            s.seen += m.species; s.caught += m.species
        }
        s.settings.moveChoice = MoveChoice.RANDOM
        val g = Game(data, s, Random(seed)) { now }
        if (fight) s.battle = Battle(TestData.mon(data, 19, 12, listOf("tackle"), uid = "wild"), Tier.NORMAL, startedAt = now, rounds = 1)
        return g
    }

    private inline fun <reified T : GameEvent> List<GameEvent>.of() = filterIsInstance<T>()
    private fun answer(g: Game, id: Long = 1, button: Int = 3, catchUp: Boolean = false) = g.onAnswer(Answer(id, button, now, catchUp))
    private fun noWildAttack(e: List<GameEvent>) = e.none { it is GameEvent.SwitchHit || (it is GameEvent.Attack && !it.byPlayer) }

    // ---------------------------------------------------------------- queued, not done

    @Test fun aSwitchDuringAFightIsQueuedAndTheMainStaysOut() {
        val g = game()
        val t1 = g.state.mon("t1")!!
        val wild = g.state.battle!!.wild
        val r = g.requestMain("t1")
        assertTrue(r.ok)
        assertEquals(listOf<GameEvent>(GameEvent.SwitchQueued("t1", t1.name)), r.events)
        assertEquals("t0", g.state.main!!.uid)
        assertEquals(listOf("t0", "t1", "t2"), g.state.team.toList())
        assertEquals("t1", g.state.pendingMain)
        assertEquals("t1", g.state.queuedMain!!.uid)
        assertSame(wild, g.state.battle!!.wild) // the fight goes on, nothing was hit
        assertEquals("${t1.name} comes in after this card · the wild attacks once", r.message)
    }

    @Test fun theWordingNeverSaysFreeHitOrCostsYourTurn() {
        val g = game()
        val msgs = listOf(g.requestMain("t1").message, g.requestMain("t0").message)
        for (m in msgs + listOf(SwitchQueue.MENU_SUB, SwitchQueue.ROW_MARK, SwitchQueue.queuedText("X"), SwitchQueue.cancelledText("X"))) {
            assertFalse(m, m.contains("free hit", ignoreCase = true) || m.contains("costs your turn", ignoreCase = true) || m.contains("skips your move", ignoreCase = true))
        }
    }

    @Test fun anOlderQueueIsReplacedByTheNewerPick() {
        val g = game()
        g.requestMain("t1")
        val r = g.requestMain("t2")
        assertEquals(listOf<GameEvent>(GameEvent.SwitchQueued("t2", g.state.mon("t2")!!.name)), r.events)
        assertEquals("t2", g.state.pendingMain)
        val e = answer(g)
        assertEquals("t2", g.state.main!!.uid)
        assertEquals(1, e.of<GameEvent.MainChanged>().size)
        assertEquals("t2", e.of<GameEvent.MainChanged>().single().uid)
    }

    @Test fun pickingTheMainAgainCancelsTheQueue() {
        val g = game()
        g.requestMain("t1")
        val r = g.requestMain("t0")
        val name = g.state.mon("t0")!!.name
        assertTrue(r.ok)
        assertEquals(listOf<GameEvent>(GameEvent.SwitchQueued(null, name)), r.events)
        assertEquals("$name stays in.", r.message)
        assertNull(g.state.pendingMain)
        val e = answer(g)
        assertEquals("t0", g.state.main!!.uid)
        assertTrue(e.none { it is GameEvent.MainChanged })
        assertTrue(noWildAttack(e))
    }

    @Test fun pickingTheMainWithNothingQueuedIsTheOldAlreadyYourMain() {
        val g = game()
        val r = g.requestMain("t0")
        assertTrue(r.ok)
        assertTrue(r.events.isEmpty())
        assertTrue(r.message.contains("already your main"))
        assertNull(g.state.pendingMain)
    }

    @Test fun anUnknownPokemonIsRefused() {
        val g = game()
        assertFalse(g.requestMain("nobody").ok)
        assertNull(g.state.pendingMain)
    }

    // ---------------------------------------------------------------- applied after the answer

    @Test fun afterTheNextAnswerTheSwitchComesInWithTheWildOnesOneAttack() {
        val g = game()
        val t1 = g.state.mon("t1")!!
        val hpBefore = t1.hp
        g.requestMain("t1")
        val e = answer(g)
        assertEquals("t1", g.state.main!!.uid)
        assertNull(g.state.pendingMain)
        // in order: the answer's own events first, then the switch and the wild one's one attack
        val counted = e.indexOfFirst { it is GameEvent.Counted }
        val charge = e.indexOfFirst { it is GameEvent.Charge }
        val changed = e.indexOfFirst { it is GameEvent.MainChanged }
        val hit = e.indexOfFirst { it is GameEvent.SwitchHit }
        val attack = e.indexOfFirst { it is GameEvent.Attack }
        assertTrue("$e", counted in 0 until charge && charge < changed && changed < hit && hit < attack)
        assertEquals(GameEvent.MainChanged("t1", t1.name), e[changed])
        assertEquals(GameEvent.SwitchHit("Ratty", t1.name, hpBefore, StatCalc.maxHp(data, t1)), e[hit])
        assertEquals(1, e.of<GameEvent.Attack>().count { !it.byPlayer })
        assertTrue(e.of<GameEvent.Attack>().none { it.byPlayer })
        assertEquals(hpBefore - e.of<GameEvent.Attack>().single().damage, t1.hp)
        assertTrue(e.none { it is GameEvent.RoundStart }) // the answer was one of two: no round
    }

    @Test fun theSwitchComesInOnlyOnceAndTheNextAnswersAreOrdinary() {
        val g = game()
        g.requestMain("t1")
        answer(g, 1)
        val second = answer(g, 2)
        assertTrue(second.none { it is GameEvent.MainChanged || it is GameEvent.SwitchHit || it is GameEvent.SwitchQueued })
        assertEquals("t1", g.state.main!!.uid)
        assertNull(g.state.pendingMain)
    }

    @Test fun theAttackIsCountedOnceInTheStatsAndAchievements() {
        val g = game()
        g.requestMain("t1")
        val dayBefore = g.state.history[g.state.day]?.dmgTaken ?: 0
        val counterBefore = g.state.counters[Metric.DAMAGE_TAKEN.name] ?: 0L
        val e = answer(g)
        val damage = e.of<GameEvent.Attack>().single { !it.byPlayer }.damage
        assertTrue(damage > 0)
        assertEquals(dayBefore + damage, g.state.history[g.state.day]!!.dmgTaken)
        assertEquals(counterBefore + damage, g.state.counters[Metric.DAMAGE_TAKEN.name]!!)
    }

    @Test fun theSwitchEndsTheRoundThatTheAnswerStarted() {
        // the queued switch comes in after a whole round was played (the second answer)
        val g = game()
        g.state.battle!!.answers = 1; g.state.battle!!.tally = Tuning.POINTS[3]; g.state.battle!!.buttons += 3
        g.state.battle!!.wild.hp = StatCalc.maxHp(data, g.state.battle!!.wild) // far from fainting
        g.requestMain("t1")
        val e = answer(g, 2, button = 3)
        val round = e.indexOfFirst { it is GameEvent.RoundStart }
        val changed = e.indexOfFirst { it is GameEvent.MainChanged }
        assertTrue("$e", round in 0 until changed)
        assertEquals("t1", g.state.main!!.uid)
        assertEquals(0, g.state.battle!!.answers) // the round is over; the switch's attack is its own action
        assertEquals(2, g.state.battle!!.rounds)
    }

    @Test fun onTheFirstCardOfAFightTheSwitchWaitsForTheFirstAnswerAndThenAttacksOnce() {
        val g = game()
        g.state.battle!!.rounds = 0 // nothing answered in this fight yet
        val r = g.requestMain("t1")
        assertEquals("t1", g.state.pendingMain) // queued even on the first card: no peeking before the first answer either
        assertTrue(r.events.single() is GameEvent.SwitchQueued)
        assertEquals("t0", g.state.main!!.uid)
        val e = answer(g)
        assertEquals("t1", g.state.main!!.uid)
        assertEquals(1, e.of<GameEvent.SwitchHit>().size) // the first answer was given: the switch costs the attack
        assertEquals(1, e.of<GameEvent.Attack>().count { !it.byPlayer })
    }

    @Test fun anAnswerThatEndsTheFightLetsTheSwitchInWithoutAnAttack() {
        for (seed in listOf(7, 8, 9)) {
            val g = game(seed)
            val b = g.state.battle!!
            b.answers = 1; b.tally = Tuning.POINTS[3]; b.buttons += 3
            b.wild.hp = 1 // the round's attack finishes it
            g.requestMain("t1")
            val e = answer(g, 2, button = 3)
            assertTrue("seed $seed: $e", e.any { it is GameEvent.Defeated || it is GameEvent.Caught })
            assertEquals("seed $seed", "t1", g.state.main!!.uid)
            assertNull(g.state.pendingMain)
            assertNotSame(b, g.state.battle) // the next wild one is there, nothing answered against it yet
            assertTrue("seed $seed: $e", noWildAttack(e))
            // the switch events come after the fight's own events
            assertTrue("seed $seed: $e", e.indexOfFirst { it is GameEvent.MainChanged } > e.indexOfFirst { it is GameEvent.Defeated || it is GameEvent.Caught })
            assertEquals(1, e.of<GameEvent.MainChanged>().size)
        }
    }

    @Test fun aMainThatFaintsInTheRoundIsHealedAndTheQueuedSwitchStillComesInFree() {
        val g = game()
        val b = g.state.battle!!
        b.answers = 1; b.tally = Tuning.POINTS[1]; b.buttons += 1
        g.state.main!!.hp = 1 // the round's wild attack faints it
        g.requestMain("t1")
        val e = answer(g, 2, button = 1)
        assertTrue("$e", e.any { it is GameEvent.MainHealed })
        assertEquals("t1", g.state.main!!.uid)
        assertNull(g.state.pendingMain)
        assertEquals(1, e.of<GameEvent.MainChanged>().size)
        assertTrue(e.none { it is GameEvent.SwitchHit })
    }

    @Test fun theCompanionAndCatchUpAnswersApplyItToo() {
        // onAnswer is the one entry for the study screen's own answers, the live detector and the catch-up burst
        val g = game()
        g.requestMain("t1")
        val e = answer(g, catchUp = true)
        assertEquals("t1", g.state.main!!.uid)
        assertEquals(1, e.of<GameEvent.MainChanged>().size)
        // and a burst: the switch comes in after the first answer, the second one is fought by the new main
        val h = game()
        h.requestMain("t2")
        val burst = answer(h, 1, catchUp = true) + answer(h, 2, catchUp = true)
        assertEquals(1, burst.of<GameEvent.MainChanged>().size)
        assertEquals("t2", h.state.main!!.uid)
    }

    @Test fun anIgnoredAnswerAppliesNothingAndTheQueueWaitsForARealOne() {
        val g = game()
        g.onUndo(5L, 1)
        g.requestMain("t1")
        val ignored = answer(g, 5L)
        assertTrue(ignored.any { it is GameEvent.Ignored })
        assertTrue(ignored.none { it is GameEvent.Counted || it is GameEvent.MainChanged })
        assertEquals("t0", g.state.main!!.uid)
        assertEquals("t1", g.state.pendingMain)
        val real = answer(g, 6L)
        assertEquals("t1", g.state.main!!.uid)
        assertEquals(1, real.of<GameEvent.MainChanged>().size)
        assertNull(g.state.pendingMain)
    }

    @Test fun anAnswerWithoutAStarterAppliesNothing() {
        val g = game()
        g.state.pendingMain = "t1"
        g.state.team.clear()
        val e = answer(g)
        assertTrue(e.none { it is GameEvent.Counted || it is GameEvent.MainChanged })
    }

    // ---------------------------------------------------------------- when it is done at once, as today

    @Test fun withNoCardShowingTheSwitchIsImmediateAsToday() {
        val g = game()
        val t1 = g.state.mon("t1")!!
        val r = g.requestMain("t1", cardShowing = false)
        assertEquals("t1", g.state.main!!.uid)
        assertNull(g.state.pendingMain)
        assertEquals(GameEvent.MainChanged("t1", t1.name), r.events[0])
        assertTrue(r.events[1] is GameEvent.SwitchHit)
        assertTrue(r.events.none { it is GameEvent.SwitchQueued })
    }

    @Test fun anImmediatePickReplacesAnOlderQueue() {
        val g = game()
        g.requestMain("t1")
        g.requestMain("t2", cardShowing = false)
        assertEquals("t2", g.state.main!!.uid)
        assertNull(g.state.pendingMain)
        val e = answer(g)
        assertTrue(e.none { it is GameEvent.MainChanged }) // nothing left to come in
    }

    @Test fun withoutAFightTheSwitchIsImmediate() {
        val g = game(fight = false)
        g.state.battle = null
        val r = g.requestMain("t1")
        assertEquals("t1", g.state.main!!.uid)
        assertNull(g.state.pendingMain)
        assertEquals(listOf<GameEvent>(GameEvent.MainChanged("t1", g.state.main!!.name)), r.events)
    }

    @Test fun aWildOneWithNoHpLeftOrAFaintedMainIsImmediate() {
        val a = game()
        a.state.battle!!.wild.hp = 0
        a.requestMain("t1")
        assertEquals("t1", a.state.main!!.uid)
        assertNull(a.state.pendingMain)
        val b = game()
        b.state.main!!.hp = 0
        b.requestMain("t1")
        assertEquals("t1", b.state.main!!.uid)
        assertNull(b.state.pendingMain)
    }

    @Test fun aBoxPokemonIsSwitchedAtOnceTheCallerAddsItToTheTeamFirst() {
        val g = game()
        val r = g.requestMain("b3") // not in the team: the old setMain (a free team place, or the last member's)
        assertEquals("b3", g.state.main!!.uid)
        assertTrue(r.ok)
        assertNull(g.state.pendingMain)
        val h = game()
        assertTrue(h.toTeam("b3").ok)
        val q = h.requestMain("b3")
        assertEquals("b3", h.state.pendingMain) // added to the team first: now it is queued
        assertTrue(q.events.single() is GameEvent.SwitchQueued)
        assertEquals("t0", h.state.main!!.uid)
    }

    @Test fun theCycleIsQueuedWhenACardShowsAndImmediateOtherwise() {
        val g = game()
        val r = g.cycleMain()
        assertEquals("t1", g.state.pendingMain)
        assertEquals("t0", g.state.main!!.uid)
        assertTrue(r.events.single() is GameEvent.SwitchQueued)
        answer(g)
        assertEquals("t1", g.state.main!!.uid)
        val h = game()
        val at = h.cycleMain(cardShowing = false)
        assertEquals("t1", h.state.main!!.uid)
        assertTrue(at.events.first() is GameEvent.MainChanged)
        assertNull(h.state.pendingMain)
    }

    @Test fun threeQueuedCyclesRotateTheFirstThreeLikeImmediateOnes() {
        // R14 review: a queued cycle came in through setMain ([A,B,C] -> [B,A,C]), so repeated cycles swapped A and B and never reached C
        var g = game(team = 4, box = 0)
        g.roundAnswers = 1000 // no battle round: the fight stays on, so every cycle is queued
        val orders = mutableListOf<List<String>>()
        for (i in 1..3) {
            assertTrue(SwitchQueue.fightOn(g.state))
            assertTrue(g.cycleMain().events.single() is GameEvent.SwitchQueued)
            assertTrue(g.state.pendingCycle)
            if (i == 2) g = Game(data, SaveCodec.decode(SaveCodec.encode(g.state)), Random(5)) { now }.also { it.roundAnswers = 1000 } // saved and loaded while queued
            val e = answer(g, id = i.toLong())
            assertEquals(1, e.of<GameEvent.MainChanged>().size)
            assertNull(g.state.pendingMain); assertFalse(g.state.pendingCycle)
            orders += g.state.team.toList()
        }
        assertEquals(listOf(listOf("t1", "t2", "t0", "t3"), listOf("t2", "t0", "t1", "t3"), listOf("t0", "t1", "t2", "t3")), orders)
        val h = game(team = 4, box = 0)
        repeat(3) { h.cycleMain(cardShowing = false) }
        assertEquals("the same order as three immediate cycles", orders.last(), h.state.team.toList())
    }

    @Test fun aPickAfterAQueuedCycleIsAPlainSwitch() {
        val g = game()
        g.cycleMain()
        g.requestMain("t2")
        assertFalse(g.state.pendingCycle)
        answer(g)
        assertEquals(listOf("t2", "t0", "t1"), g.state.team.toList())
    }

    // ---------------------------------------------------------------- a queue that went stale is dropped

    @Test fun aQueuedPokemonThatIsReleasedIsDropped() {
        val g = game()
        g.requestMain("t1")
        assertTrue(g.release("t1").ok)
        assertNull(g.state.pendingMain)
        val e = answer(g)
        assertEquals("t0", g.state.main!!.uid)
        assertTrue(e.none { it is GameEvent.MainChanged })
        assertTrue(noWildAttack(e))
    }

    @Test fun aQueuedPokemonThatGoesToTheBoxIsDropped() {
        val g = game()
        g.requestMain("t2")
        assertTrue(g.toBox("t2").ok)
        assertNull(g.state.pendingMain)
        assertTrue(answer(g).none { it is GameEvent.MainChanged })
    }

    @Test fun aQueuedPokemonThatBecomesTheMainByATeamEditIsNotSwitchedInAgain() {
        val g = game()
        g.requestMain("t1")
        assertTrue(g.moveInTeam("t1", -1).ok) // the Team edit makes it the main at once (the old rules)
        assertEquals("t1", g.state.main!!.uid)
        assertNull(g.state.pendingMain)
        assertTrue(answer(g).none { it is GameEvent.MainChanged })
    }

    @Test fun aStaleQueueFoundAtTheAnswerIsDroppedWithoutEvents() {
        val g = game()
        g.state.pendingMain = "gone" // e.g. an import replaced the Pokémon
        val e = answer(g)
        assertNull(g.state.pendingMain)
        assertTrue(e.none { it is GameEvent.MainChanged })
        assertEquals("t0", g.state.main!!.uid)
    }

    @Test fun queuedMainIgnoresAQueueThatIsNotAWaitingSwitch() {
        val g = game()
        g.state.pendingMain = "t0"
        assertNull(g.state.queuedMain) // the main itself
        g.state.pendingMain = "b3"
        assertNull(g.state.queuedMain) // not in the team
        g.state.pendingMain = "t2"
        assertEquals("t2", g.state.queuedMain!!.uid)
    }

    // ---------------------------------------------------------------- the small rules and words

    @Test fun fightOnNeedsAWildOneWithHpAndAMainThatCanFight() {
        val g = game()
        assertTrue(SwitchQueue.fightOn(g.state))
        g.state.battle!!.wild.hp = 0
        assertFalse(SwitchQueue.fightOn(g.state))
        g.state.battle!!.wild.hp = 5
        g.state.main!!.hp = 0
        assertFalse(SwitchQueue.fightOn(g.state))
        g.state.main!!.hp = 5
        g.state.battle = null
        assertFalse(SwitchQueue.fightOn(g.state))
    }

    @Test fun aQueuedSwitchKeepsItsToastWhileAnImmediateOneStaysQuiet() {
        val g = game()
        val queued = g.requestMain("t1").quietUnlessHit()
        assertTrue(queued.message.isNotEmpty())
        val cancelled = g.requestMain("t0").quietUnlessHit()
        assertTrue(cancelled.message.isNotEmpty())
        val free = game(fight = false).requestMain("t1").quietUnlessHit()
        assertEquals("", free.message) // visible on screen: no toast, as before
    }

    // ---------------------------------------------------------------- the save

    @Test fun theQueueIsSavedAndLoaded() {
        val g = game()
        g.requestMain("t2")
        val back = SaveCodec.decode(SaveCodec.encode(g.state))
        assertEquals("t2", back.pendingMain)
        assertEquals("t2", back.queuedMain!!.uid)
        val none = SaveCodec.decode(SaveCodec.encode(game().state))
        assertNull(none.pendingMain)
    }

    @Test fun aLoadedQueueIsAppliedByTheNextAnswer() {
        val g = game()
        g.requestMain("t1")
        val loaded = SaveCodec.decode(SaveCodec.encode(g.state))
        val g2 = Game(data, loaded, Random(3)) { now }
        val e = answer(g2)
        assertEquals("t1", g2.state.main!!.uid)
        assertEquals(1, e.of<GameEvent.MainChanged>().size)
        assertNull(g2.state.pendingMain)
    }

    @Test fun anOldSaveWithoutTheKeyLoadsWithNoQueue() {
        val g = game()
        g.requestMain("t1")
        val o = JSONObject(SaveCodec.encode(g.state))
        assertTrue(o.has("pendingMain"))
        o.remove("pendingMain")
        assertNull(SaveCodec.decode(o.toString()).pendingMain)
    }

    @Test fun aSavedQueueThatNoLongerFitsTheTeamLoadsAsNone() {
        val g = game()
        g.requestMain("t1")
        val o = JSONObject(SaveCodec.encode(g.state))
        o.put("pendingMain", "ghost")
        assertNull(SaveCodec.decode(o.toString()).pendingMain)
        o.put("pendingMain", "t0") // the main itself is nothing to wait for
        assertNull(SaveCodec.decode(o.toString()).pendingMain)
        o.put("pendingMain", "b3") // in the box
        assertNull(SaveCodec.decode(o.toString()).pendingMain)
    }

    @Test fun theQueueIsNotPartOfTheTeamOrTheBattleSave() {
        val g = game()
        g.requestMain("t1")
        val o = JSONObject(SaveCodec.encode(g.state))
        assertFalse(o.getJSONObject("battle").has("pendingMain"))
        assertEquals(listOf("t0", "t1", "t2"), (0 until o.getJSONArray("team").length()).map { o.getJSONArray("team").getString(it) })
    }
}
