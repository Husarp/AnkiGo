package com.husarp.ankigo.game

import com.husarp.ankigo.game.save.SaveCodec
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class R20SwitchTest {
    private val data = TestData.world()
    private fun game(started: Boolean = true): Game {
        val s = TestData.state(data, 25, 20, listOf("tackle"))
        for (uid in listOf("b", "c", "box")) s.mons += TestData.mon(data, 16, 20, listOf("tackle"), uid = uid)
        s.team += listOf("b", "c")
        s.battle = Battle(TestData.mon(data, 19, 80, listOf("tackle"), uid = "wild"), Tier.NORMAL, rounds = if (started) 1 else 0)
        return Game(data, s, Random(42))
    }

    @Test fun everyMainChangingTeamEditWaitsAndSurvivesASave() {
        val edits: List<(Game) -> ActionResult> = listOf(
            { it.swapIntoTeam("box", it.state.main!!.uid) },
            { it.moveInTeam("b", -1) },
            { it.moveInTeam(it.state.main!!.uid, 1) },
            { it.toBox(it.state.main!!.uid) },
        )
        edits.forEach { edit ->
            val g = game(); val old = g.state.main!!.uid
            assertTrue(edit(g).ok)
            assertEquals(old, g.state.main!!.uid)
            assertNotNull(g.state.pendingMain)
            val restored = SaveCodec.decode(SaveCodec.encode(g.state))
            assertEquals(g.state.pendingTeam, restored.pendingTeam)
            val next = restored.pendingMain
            val events = Game(data, restored, Random(4)).onAnswer(Answer(700, 3, System.currentTimeMillis()))
            assertEquals(next, restored.main!!.uid)
            assertEquals(1, events.filterIsInstance<GameEvent.SwitchHit>().size)
            assertNull(restored.pendingTeam)
        }
    }

    @Test fun queuedBoxReplacementSurvivesUnrelatedReleaseAndStillSwitches() {
        val g = game()
        g.state.mons += TestData.mon(data, 16, 20, listOf("tackle"), uid = "unrelated")
        val old = g.state.main!!.uid
        assertTrue(g.swapIntoTeam("box", old).ok)
        val queued = g.state.pendingTeam
        assertTrue(g.release("unrelated").ok)
        assertEquals("box", g.state.pendingMain)
        assertEquals(queued, g.state.pendingTeam)
        assertEquals(old, g.state.main!!.uid)
        val events = g.onAnswer(Answer(700, 3, System.currentTimeMillis()))
        assertEquals("box", g.state.main!!.uid)
        assertEquals(1, events.filterIsInstance<GameEvent.SwitchHit>().size)
        assertNull(g.state.pendingMain)
        assertNull(g.state.pendingTeam)
        assertFalse(g.state.pendingCycle)
    }

    @Test fun releasingAnyQueuedTeamMemberClearsTheWholePendingSwitch() {
        for (uid in listOf("box", "b")) {
            val g = game()
            assertTrue(g.swapIntoTeam("box", g.state.main!!.uid).ok)
            assertTrue(g.release(uid).ok)
            assertNull(g.state.pendingMain)
            assertNull(g.state.pendingTeam)
            assertFalse(g.state.pendingCycle)
        }
    }

    @Test fun firstCardSwitchesAndCyclesAreImmediateAndFreeButLaterOnesWait() {
        val g = game(false)
        assertFalse(SwitchQueue.waitsForAnswer(g.state))
        assertTrue(g.requestMain("b").events.any { it is GameEvent.MainChanged })
        assertEquals("b", g.state.main!!.uid)
        assertNull(g.state.pendingMain)
        val before = g.state.team.toList()
        assertTrue(g.cycleMain().events.none { it is GameEvent.SwitchHit })
        assertEquals(listOf(before[1], before[2], before[0]), g.state.team)
        g.state.battle!!.answers = 1
        assertTrue(SwitchQueue.waitsForAnswer(g.state))
        g.requestMain("b")
        assertEquals("b", g.state.pendingMain)
    }

    @Test fun firstCardBoxReplacementIsFreeAndCancellingALaterReplacementDoesNotLoseAnyone() {
        val early = game(false)
        val r = early.swapIntoTeam("box", early.state.main!!.uid)
        assertEquals("box", early.state.main!!.uid)
        assertTrue(r.events.none { it is GameEvent.SwitchHit })
        val later = game()
        val team = later.state.team.toList()
        later.swapIntoTeam("box", later.state.main!!.uid)
        later.requestMain(later.state.main!!.uid)
        assertNull(later.state.pendingTeam)
        assertNull(later.state.pendingMain)
        assertEquals(team, later.state.team)
        assertEquals(4, later.state.mons.size)
    }
}
