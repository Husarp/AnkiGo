package com.husarp.ankigo.ui

import com.husarp.ankigo.game.CashKind
import com.husarp.ankigo.game.GameEvent
import com.husarp.ankigo.ui.CatchTilesPlan.Kind
import com.husarp.ankigo.ui.CatchTilesPlan.Mode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CatchTilesPlanTest {
    private fun caught(newSpecies: Boolean, ball: String? = "ball-a") = GameEvent.Caught("w1", 147, "Wildling", 12, false, ball, newSpecies, true)
    private fun xp(uid: String, name: String, n: Int, level: Int = 10) = GameEvent.XpGained(uid, name, n, level, 0, 100)
    private val ballName = { id: String -> "Ball " + id.last().uppercase() }

    private fun summary(vararg e: GameEvent, earlier: Int = 0) = CatchSummary.of(e.toList(), earlier)!!

    @Test fun newCatchWithTwoThrowsHasFourTiles() {
        val s = summary(
            GameEvent.BallThrow("ball-a", 12, true, 3), caught(true), xp("m", "Mainy", 120, 11), GameEvent.Cash(10, 110, CashKind.DEFEAT),
            earlier = 1,
        )
        val t = CatchTilesPlan.tiles(s, ballName)
        assertEquals(listOf(Kind.DEX, Kind.THROW, Kind.XP, Kind.COINS), t.map { it.kind })
        assertEquals("New · #147", t[0].value)
        assertEquals("Ball A", t[1].label); assertEquals("Throw 2", t[1].value); assertEquals("· 12 %", t[1].extra); assertEquals("ball-a", t[1].ball)
        assertEquals("Mainy", t[2].label); assertEquals("+120 XP", t[2].value)
        assertEquals("+10", t[3].value)
    }

    @Test fun reCatchAndAutoCatch() {
        val re = CatchTilesPlan.tiles(summary(GameEvent.BallThrow("ball-a", 8, true, 1), caught(false)), ballName)
        assertEquals("Caught before", re[0].value)
        val auto = CatchTilesPlan.tiles(summary(caught(true, ball = null)), ballName)
        assertEquals(listOf(Kind.DEX, Kind.AUTO), auto.map { it.kind })
        assertEquals("Caught automatically", auto[1].value)
        assertNull(auto[1].ball)
    }

    @Test fun levelUpAndNoCoinsNoXp() {
        val s = summary(caught(false, null), xp("m", "Mainy", 30, 11), GameEvent.LevelUp("m", "Mainy", 11, 50, 50))
        val t = CatchTilesPlan.tiles(s, ballName)
        assertEquals("· Lv 11", t.first { it.kind == Kind.XP }.extra)
        assertTrue(t.none { it.kind == Kind.COINS })
        val bare = CatchTilesPlan.tiles(summary(caught(false, null)), ballName)
        assertTrue(bare.none { it.kind == Kind.XP || it.kind == Kind.COINS })
    }

    @Test fun defeatKeepsOnlyWhatApplies() {
        val s = summary(GameEvent.Defeated(147, "Wildling", 40), xp("m", "Mainy", 84), GameEvent.Cash(10, 20, CashKind.DEFEAT))
        val t = CatchTilesPlan.tiles(s, ballName)
        assertEquals(listOf(Kind.DEFEATED, Kind.XP, Kind.COINS), t.map { it.kind })
        assertEquals("Wildling", t[0].value)
        assertEquals(listOf(Kind.DEFEATED, Kind.XP, Kind.COINS), CatchTilesPlan.rowTiles(s).map { it.kind })
    }

    @Test fun manyXpPokemonShareOneTile() {
        val s = summary(caught(false, null), xp("a", "A", 10), xp("b", "B", 20), xp("c", "C", 30))
        val t = CatchTilesPlan.tiles(s, ballName).filter { it.kind == Kind.XP }
        assertEquals(1, t.size)
        assertEquals("3 Pokémon", t[0].label); assertEquals("+60 XP", t[0].value)
        val two = CatchTilesPlan.tiles(summary(caught(false, null), xp("a", "A", 10), xp("b", "B", 20)), ballName).filter { it.kind == Kind.XP }
        assertEquals(2, two.size)
    }

    @Test fun rowTilesAreShortAndMergeXp() {
        val s = summary(
            GameEvent.BallThrow("ball-a", 12, true, 3), caught(true), xp("a", "A", 10), xp("b", "B", 20), GameEvent.Cash(10, 110, CashKind.DEFEAT),
        )
        val r = CatchTilesPlan.rowTiles(s)
        assertEquals(listOf("New #147", "Throw 1 · 12 %", "+30 XP", "+10"), r.map { it.value })
        assertEquals("ball-a", r[1].ball)
    }

    @Test fun modeRule() {
        assertEquals(Mode.HOLD, CatchTilesPlan.mode(full = true, logShown = true, answerWaiting = false))
        assertEquals(Mode.TIMED, CatchTilesPlan.mode(full = false, logShown = true, answerWaiting = false))
        assertEquals(Mode.NONE, CatchTilesPlan.mode(full = true, logShown = true, answerWaiting = true))
        assertEquals(Mode.NONE, CatchTilesPlan.mode(full = false, logShown = false, answerWaiting = false))
        assertEquals(Mode.NONE, CatchTilesPlan.mode(full = true, logShown = false, answerWaiting = false))
    }

    @Test fun gridOnlyInTallFullPanels() {
        assertTrue(CatchTilesPlan.grid(true, 700f))
        assertTrue(!CatchTilesPlan.grid(true, 500f))
        assertTrue(!CatchTilesPlan.grid(false, 900f))
    }

    @Test fun theThrowNumberComesWithTheEventWhicheverBatchesThePanelSaw() {
        // R4 fix B8: the game counts the fight's throws (BallThrow.number); a panel needs no counter of its own.
        val s = CatchSummary.of(listOf(GameEvent.BallThrow("ball-a", 12, true, 2, 3), caught(false)))!!
        assertEquals(3, s.throwInfo!!.number)
        assertEquals("Throw 3", CatchTilesPlan.tiles(s, ballName).single { it.kind == Kind.THROW }.value)
        // the next fight starts again at 1
        assertEquals(1, CatchSummary.of(listOf(GameEvent.BallThrow("ball-a", 12, true, 1, 1), caught(false)))!!.throwInfo!!.number)
    }

    @Test fun resultGoesBeforeTheNextEncounter() {
        val enc = GameEvent.Encounter(1, "N", 5, false, com.husarp.ankigo.game.Tier.NORMAL, 10, 10, false, false)
        val withEnc = listOf(GameEvent.Defeated(1, "W", 5), xp("m", "M", 5), GameEvent.Cash(1, 1, CashKind.DEFEAT), enc)
        assertEquals(3, CatchTilesPlan.resultIndex(withEnc))
        val without = listOf(GameEvent.BallThrow("ball-a", 12, true, 1), caught(false))
        assertEquals(2, CatchTilesPlan.resultIndex(without))
    }
}
