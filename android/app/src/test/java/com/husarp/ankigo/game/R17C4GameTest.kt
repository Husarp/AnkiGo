package com.husarp.ankigo.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.random.Random

/** R17C-4 through [Game]: the team reaches Beat Up, so the Main hits once for each team member with HP. */
class R17C4GameTest {
    private val data = R17C4Fixture.data()

    @Test fun beatUpHitsOncePerTeamMemberThroughTheGame() {
        var now = ZonedDateTime.of(2026, 10, 13, 12, 0, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli()
        val state = TestData.state(data, 25, 20, listOf("beatup"))
        state.mons += TestData.mon(data, 63, 10, listOf("tackle"), uid = "t2"); state.team += "t2"
        state.mons += TestData.mon(data, 16, 10, listOf("tackle"), uid = "t3"); state.team += "t3"
        state.mons += TestData.mon(data, 19, 10, listOf("tackle"), uid = "t4").also { it.hp = 0 }; state.team += "t4"
        val g = Game(data, state, Random(1)) { now }
        val wild = TestData.mon(data, 19, 15, listOf("splash"), uid = "wild").also { it.hp = 5000 }
        state.battle = Battle(wild, Tier.NORMAL, startedAt = now).also { it.wildMaxHp = 5000 }
        // two Easy answers are one round: the Main attacks, the wild one does nothing
        now += 4_000; g.onAnswer(Answer(1, 4, now, false))
        now += 4_000; val ev = g.onAnswer(Answer(2, 4, now, false))
        val attack = ev.filterIsInstance<GameEvent.Attack>().first { it.byPlayer }
        assertEquals("beatup", attack.moveId)
        assertEquals(3, attack.hits) // the Main, t2 and t3; t4 has no HP
        assertTrue(attack.damage > 0)
    }
}
