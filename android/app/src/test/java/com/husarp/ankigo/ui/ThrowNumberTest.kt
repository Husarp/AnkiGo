package com.husarp.ankigo.ui

import com.husarp.ankigo.game.Battle
import com.husarp.ankigo.game.Catching
import com.husarp.ankigo.game.Economy
import com.husarp.ankigo.game.Game
import com.husarp.ankigo.game.GameEvent
import com.husarp.ankigo.game.TestData
import com.husarp.ankigo.game.Tier
import com.husarp.ankigo.game.save.SaveCodec
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import kotlin.random.Random

/**
 * R4 fix B8: the throw number of a catch comes from the fight in the game state, not from the screen that watched it. PLAN 171: a miss
 * ends the fight now (the wild one runs away), so a new fight's throw is always its first; a fight saved before the update keeps its count.
 */
class ThrowNumberTest {
    private val data = TestData.world()
    private val now = 1_791_892_800_000L

    /** A seed whose first draws say [outcomes] for the Poké Ball (true = it catches). */
    private fun seed(vararg outcomes: Boolean): Int =
        (0..200_000).first { s -> Random(s).let { r -> outcomes.all { want -> (r.nextInt(100) < Catching.ballChance("poke-ball")) == want } } }

    private fun fight(outcomes: BooleanArray): Game {
        val st = TestData.state(data, 25, 20, listOf("tackle"))
        val g = Game(data, st, Random(seed(*outcomes))) { now }
        Economy.give(st, "poke-ball", 6)
        st.battle = Battle(TestData.mon(data, 54, 15, listOf("tackle"), uid = "wild").also { it.hp = 20 }, Tier.NORMAL, startedAt = now)
        return g
    }

    private fun throws(g: Game, n: Int) = (1..n).map { g.throwBall("poke-ball") }

    @Test fun aMissEndsTheFightSoEveryThrowIsItsFightsFirst() {
        val g = fight(booleanArrayOf(false, false, true))
        val batches = throws(g, 3)
        assertEquals(listOf(1, 1, 1), batches.map { b -> b.filterIsInstance<GameEvent.BallThrow>().single().number })
        assertEquals(2, batches.take(2).count { b -> b.any { it is GameEvent.Fled } })
    }

    @Test fun theCatchSaysThrowThreeEvenForAScreenThatOnlySawTheLastThrow() {
        val g = fight(booleanArrayOf(true))
        g.state.battle!!.failedThrows = 2   // a fight saved before PLAN 171 with two misses in it
        val batches = listOf(g.throwBall("poke-ball"))
        assertEquals(3, batches[0].filterIsInstance<GameEvent.BallThrow>().single().number)
        // A panel that was not open for the first two throws has only the last batch: it still reports throw 3.
        val last = CatchSummary.of(batches[0], data = data)!!
        assertEquals(3, last.throwInfo!!.number)
        val tile = CatchTilesPlan.tiles(last) { it }.single { it.kind == CatchTilesPlan.Kind.THROW }
        assertEquals("Throw 3", tile.value)
        assertEquals("Throw 3 · ${last.throwInfo!!.chance} %", CatchTilesPlan.rowTiles(last).single { it.kind == CatchTilesPlan.Kind.THROW }.value)
    }

    @Test fun aFirstThrowCatchIsThrowOne() {
        val g = fight(booleanArrayOf(true))
        assertEquals(1, CatchSummary.of(g.throwBall("poke-ball"), data = data)!!.throwInfo!!.number)
    }

    @Test fun aNewFightStartsCountingAgain() {
        val g = fight(booleanArrayOf(false, true))
        throws(g, 2)
        assertNotNull(g.state.battle) // the catch put the next wild one in
        assertEquals(0, g.state.battle!!.failedThrows)
    }

    @Test fun theCountIsSavedWithTheFight() {
        val g = fight(booleanArrayOf(true))
        g.state.battle!!.failedThrows = 2   // a fight saved before PLAN 171 with two misses in it
        val back = SaveCodec.decode(SaveCodec.encode(g.state))
        assertEquals(2, back.battle!!.failedThrows)
        val g2 = Game(data, back, Random(seed(true))) { now } // the app was restarted between the throws
        assertEquals(3, g2.throwBall("poke-ball").filterIsInstance<GameEvent.BallThrow>().single().number)
    }

    @Test fun anOldSaveWithoutTheKeyStartsAtZero() {
        val g = fight(booleanArrayOf(false, true))
        val o = JSONObject(SaveCodec.encode(g.state))
        o.getJSONObject("battle").remove("failedThrows")
        assertEquals(0, SaveCodec.decode(o.toString()).battle!!.failedThrows)
    }

    @Test fun anEventWithoutANumberFallsBackToTheEarlierCount() {
        val old = listOf(GameEvent.BallThrow("ball-a", 12, true, 2), GameEvent.Caught("w1", 147, "Wildling", 12, false, "ball-a", true, true))
        assertEquals(1, CatchSummary.of(old)!!.throwInfo!!.number)
        assertEquals(3, CatchSummary.of(old, earlierThrows = 2)!!.throwInfo!!.number)
        val numbered = listOf(GameEvent.BallThrow("ball-a", 12, true, 2, 4), GameEvent.Caught("w1", 147, "Wildling", 12, false, "ball-a", true, true))
        assertEquals(4, CatchSummary.of(numbered, earlierThrows = 2)!!.throwInfo!!.number) // the fight's own count wins
    }
}
