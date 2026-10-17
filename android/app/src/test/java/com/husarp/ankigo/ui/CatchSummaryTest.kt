package com.husarp.ankigo.ui

import com.husarp.ankigo.game.CashKind
import com.husarp.ankigo.game.GameEvent
import com.husarp.ankigo.game.Tier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CatchSummaryTest {
    private fun caught(newSpecies: Boolean, ball: String? = "ball-a", species: Int = 147) =
        GameEvent.Caught("w1", species, "Wildling", 12, false, ball, newSpecies, true)
    private fun xp(uid: String, name: String, n: Int, level: Int) = GameEvent.XpGained(uid, name, n, level, 0, 100)

    @Test fun newSpeciesCatchWithOneFailedAndOneGoodThrow() {
        val s = CatchSummary.of(
            listOf(
                GameEvent.BallThrow("ball-a", 12, false, 4), GameEvent.Info("broke free"),
                GameEvent.BallThrow("ball-a", 12, true, 3), caught(true),
                xp("main", "Mainy", 30, 11), GameEvent.Cash(5, 105, CashKind.ANSWER),
            ),
        )!!
        assertTrue(s.caught)
        assertEquals(CatchSummary.PokedexLine(true, 147), s.pokedex)
        assertEquals("New · #147", s.pokedex!!.text)
        assertEquals(CatchSummary.ThrowLine("ball-a", 2, 12), s.throwInfo)
        assertEquals(listOf(CatchSummary.XpLine("main", "Mainy", 30, 11, false)), s.xp)
        assertEquals(5, s.coins)
        assertEquals("Next answer starts a new battle", s.next)
    }

    @Test fun throwNumberCountsFailedThrowsOfEarlierBatches() {
        val batch = listOf(GameEvent.BallThrow("ball-a", 12, true, 2), caught(false))
        assertEquals(1, CatchSummary.of(batch)!!.throwInfo!!.number)
        assertEquals(3, CatchSummary.of(batch, earlierThrows = 2)!!.throwInfo!!.number)
        val fight = listOf(GameEvent.BallThrow("ball-a", 12, false, 5), GameEvent.BallThrow("ball-a", 12, false, 4), GameEvent.Info("x"))
        assertEquals(2, CatchSummary.failedThrows(fight))
    }

    @Test fun reCatchSaysCaughtBefore() {
        val s = CatchSummary.of(listOf(GameEvent.BallThrow("ball-a", 8, true, 1), caught(false)))!!
        assertEquals("Caught before", s.pokedex!!.text)
    }

    @Test fun autoCatchHasPokedexButNoThrow() {
        val s = CatchSummary.of(listOf(caught(true, ball = null)))!!
        assertNotNull(s.pokedex)
        assertNull(s.throwInfo)
    }

    @Test fun defeatHasNoPokedexOrThrowButKeepsXpAndCoins() {
        val s = CatchSummary.of(
            listOf(
                GameEvent.Defeated(147, "Wildling", 40), xp("main", "Mainy", 20, 12), xp("main", "Mainy", 5, 13),
                GameEvent.LevelUp("main", "Mainy", 13, 50, 50), xp("bench", "Benchy", 10, 7),
                GameEvent.Cash(5, 5, CashKind.ANSWER), GameEvent.Cash(10, 15, CashKind.DEFEAT), GameEvent.Cash(75, 90, CashKind.DAILY_BONUS),
            ),
        )!!
        assertTrue(!s.caught)
        assertEquals("Wildling", s.name)
        assertNull(s.pokedex); assertNull(s.throwInfo)
        assertEquals(CatchSummary.XpLine("main", "Mainy", 25, 13, true), s.xp[0])
        assertEquals(CatchSummary.XpLine("bench", "Benchy", 10, 7, false), s.xp[1])
        assertEquals(15, s.coins)
    }

    @Test fun aBatchThatEndedNoFightGivesNull() {
        assertNull(CatchSummary.of(listOf(GameEvent.BallThrow("ball-a", 12, false, 4), GameEvent.Info("broke free"))))
        assertNull(CatchSummary.of(listOf(xp("main", "Mainy", 5, 3), GameEvent.Cash(5, 5))))
        assertNull(CatchSummary.of(emptyList()))
    }

    @Test fun regionalFormUsesTheNationalNumber() {
        val d = com.husarp.ankigo.game.TestData.data(
            listOf(com.husarp.ankigo.game.TestData.species(10100, "Formling").copy(baseNum = 19)),
        )
        val s = CatchSummary.of(listOf(caught(true, species = 10100)), data = d)!!
        assertEquals(19, s.pokedex!!.number)
    }
}
