package com.husarp.ankigo.ui

import com.husarp.ankigo.game.Answer
import com.husarp.ankigo.game.Battle
import com.husarp.ankigo.game.Economy
import com.husarp.ankigo.game.Game
import com.husarp.ankigo.game.GameEvent
import com.husarp.ankigo.game.MoveChoice
import com.husarp.ankigo.game.StatCalc
import com.husarp.ankigo.game.SwitchHitText
import com.husarp.ankigo.game.SwitchQueue
import com.husarp.ankigo.game.TestData
import com.husarp.ankigo.game.Tier
import com.husarp.ankigo.game.Tuning
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
 * R18-B (PLAN 227 a): a switch queued on a card, played in event order through what the Study screen holds: the action bar's model
 * ([ActionBarModel]) and the dots ([StudyCharge]). Real [Game] events (made-up species), both cases: the first card of a round and
 * the card that ends the round. The arena (HP of the incoming Pokémon) takes its numbers from the same events: the checks here are
 * on those numbers. An item used in a fight keeps the dots as they are.
 */
class R18BSwitchFlowTest {
    private val data = TestData.world()
    private val now = ZonedDateTime.of(2026, 10, 24, 12, 0, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli()
    private lateinit var savedZone: TimeZone

    @Before fun utcZone() {
        savedZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After fun restoreZone() = TimeZone.setDefault(savedZone)

    /** Main t0 and t1, t2 on the bench in a fight against Ratty with one round played (so a switch costs the attack). */
    private fun game(): Game {
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
        s.battle = Battle(wild, Tier.NORMAL, startedAt = now, rounds = 1)
        Economy.give(s, "potion", 3)
        return Game(data, s, Random(7)) { now }
    }

    /** What the Study screen shows after [events] played in order. */
    private class Screen {
        val bar = ActionBarModel()
        val dots = StudyCharge()
        fun play(events: List<GameEvent>) = events.forEach { bar.observe(it); dots.observe(it, Tuning.CARDS_PER_ROUND) }
    }

    @Test fun aSwitchQueuedOnTheFirstCardOfARoundShowsInOrder() {
        val g = game()
        val t1 = g.state.mon("t1")!!
        val before = t1.hp
        val q = g.requestMain("t1")
        assertEquals("Switching to ${t1.name} on your next move.", q.message)
        val screen = Screen()
        screen.play(q.events)   // the queue itself changes neither the bar nor the dots
        assertEquals(HalfState.Empty, screen.bar.mine)
        assertEquals(HalfState.Empty, screen.bar.theirs)
        assertEquals(emptyList<Int>(), screen.dots.filled)

        val e = g.onAnswer(Answer(1, 3, now))
        screen.play(e)
        // the answer counted as normal, then the switch came in and the wild one attacked once
        assertEquals("t1", g.state.main!!.uid)
        assertTrue(e.none { it is GameEvent.RoundStart })
        assertEquals("your half says what happened", HalfState.Note("Switched to ${t1.name}"), screen.bar.mine)
        val wildMove = screen.bar.theirs as HalfState.Moved
        assertTrue(wildMove.move.who.startsWith("Wild"))
        assertTrue(t1.hp < before)
        assertEquals("the hit is the one the marker announced", before - wildMove.move.damage, t1.hp)
        // the dot of the answer is drawn in the switch colour; the round is not over
        assertEquals(listOf(StudyCharge.SWITCH), screen.dots.filled)
        assertEquals(2, screen.dots.goal)
    }

    @Test fun aSwitchQueuedOnTheRoundEndingCardKeepsTheFullDotsWithTheLastOneSwitch() {
        val g = game()
        g.state.battle!!.answers = 1; g.state.battle!!.tally = Tuning.POINTS[3]; g.state.battle!!.buttons += 3
        val screen = Screen()
        // the first answer of the round was played already
        screen.dots.observe(GameEvent.Counted(3, 10, 1, 1, false), 2)
        screen.dots.observe(GameEvent.Charge(10, 1, 2), 2)
        val t1 = g.state.mon("t1")!!
        g.requestMain("t1")
        val e = g.onAnswer(Answer(2, 4, now))
        screen.play(e)
        assertEquals("t1", g.state.main!!.uid)
        assertTrue("the round was played", e.any { it is GameEvent.RoundStart })
        assertEquals("both dots stay until the next answer, the last one is the switch's", listOf(3, StudyCharge.SWITCH), screen.dots.filled)
        assertEquals(HalfState.Note("Switched to ${t1.name}"), screen.bar.mine)
        assertTrue(screen.bar.theirs is HalfState.Moved)
        // the next answer starts a new round: ordinary dots again
        screen.dots.observe(GameEvent.Counted(3, 10, 2, 2, false), 2)
        assertEquals(listOf(3), screen.dots.filled)
    }

    @Test fun anImmediateSwitchWithNoCardNeverRecoloursADot() {
        val g = game()
        val screen = Screen()
        screen.dots.observe(GameEvent.Counted(2, 5, 1, 1, false), 2)
        screen.dots.observe(GameEvent.Charge(5, 1, 2), 2)
        g.state.battle!!.answers = 1
        val r = g.requestMain("t1", cardShowing = false)   // a result holds: the switch is made at once
        screen.play(r.events)
        assertTrue(r.events.any { it is GameEvent.MainChanged && !it.queued })
        assertEquals(listOf(2), screen.dots.filled)
    }

    @Test fun theNeutralOkOfANewCardDoesNotMarkThePreviousDot() {
        val screen = Screen()
        screen.dots.observe(GameEvent.Counted(3, 10, 1, 1, false), 2)
        screen.dots.observe(GameEvent.Counted(3, 0, 1, 1, false, neutral = true), 2)
        screen.dots.observe(GameEvent.MainChanged("t1", "X", queued = true), 2)
        assertEquals(listOf(3), screen.dots.filled)
    }

    @Test fun anItemOnTheMainWorksAtOnceAndLeavesTheDotsAlone() {
        val g = game()
        val main = g.state.main!!
        main.hp = main.hp / 2
        val screen = Screen()
        screen.dots.observe(GameEvent.Counted(3, 10, 1, 1, false), 2)
        screen.dots.observe(GameEvent.Charge(10, 1, 2), 2)
        val r = g.useItem("potion", main.uid)
        screen.play(r.events)
        assertEquals(HalfState.Note("Used Potion"), screen.bar.mine)
        assertTrue(screen.bar.theirs is HalfState.Moved)
        assertEquals(listOf(3), screen.dots.filled)
        assertEquals("answers waiting in the round stay", 0, g.state.battle!!.answers)
    }

    // ---------------------------------------------------------------- the words (264)

    @Test fun theWordsSayTheRealRuleNeverAFreeHit() {
        assertEquals("Switching to Pika on your next move.", SwitchQueue.queuedText("Pika"))
        assertEquals("Pika stays in.", SwitchQueue.cancelledText("Pika"))
        assertEquals("On your next move · the wild Pokémon attacks once", SwitchQueue.MENU_SUB)
        assertEquals("On your next move", SwitchQueue.ROW_MARK)
        val g = game()
        val q = g.requestMain("t1")
        val e = g.onAnswer(Answer(1, 3, now))
        val hit = e.dropWhile { it !is GameEvent.SwitchHit }
        val toast = SwitchHitText.withHit("X is your main now.", hit, named = true)
        for (m in listOf(q.message, toast, SwitchQueue.MENU_SUB)) assertFalse(m, m.contains("free hit", true) || m.contains("one turn", true))
        assertNull(g.state.pendingMain)
    }
}
