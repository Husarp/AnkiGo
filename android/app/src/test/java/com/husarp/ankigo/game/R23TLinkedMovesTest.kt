package com.husarp.ankigo.game

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class R23TLinkedMovesTest {
    private fun move(id: String, type: Type = Type.NORMAL, damage: Boolean = false) =
        Move(id, id, type, if (damage) Category.PHYSICAL else Category.STATUS, if (damage) 40 else 0, 100)
    private val moves = listOf(move("defensecurl"), move("rollout", damage = true), move("iceball", damage = true),
        move("charge"), move("spark", Type.ELECTRIC, true), move("stockpile"), move("swallow"), move("spitup"),
        move("tackle", damage = true), move("scratch", damage = true), move("growl"))
    private fun data(learn: List<String>): GameData {
        val sp = TestData.species(1, levelMoves = learn.map { LevelMove(1, it) })
        return GameData(mapOf(1 to sp), moves.associateBy { it.id }, emptyMap(), emptyMap())
    }
    @Test fun generatedPairsEveryEmpowererAndKeepsFourDistinctMoves() {
        val d = data(moves.map { it.id })
        for ((source, partner) in listOf("defensecurl" to "rollout", "charge" to "spark", "stockpile" to "swallow")) {
            val result = LinkedMoves.generated(d, d.sp(1), listOf(source, "tackle", "scratch", "growl"))
            assertTrue(source in result); assertTrue(partner in result)
            assertEquals(4, result.size); assertEquals(4, result.distinct().size)
        }
    }
    @Test fun unavailablePartnerReplacesEmpowerer() {
        val d = data(listOf("charge", "tackle", "growl"))
        assertEquals(listOf("growl", "tackle"), LinkedMoves.generated(d, d.sp(1), listOf("charge", "tackle")))
    }
    @Test fun generatedIntegrationAndExistingListUntouched() {
        val d = data(listOf("defensecurl", "tackle", "growl", "scratch", "rollout"))
        val saved = listOf("defensecurl", "tackle")
        LinkedMoves.generated(d, d.sp(1), saved)
        assertEquals(listOf("defensecurl", "tackle"), saved)
        repeat(30) {
            val mon = Progression.newMon(d, 1, 5, Random(it), 0)
            if ("defensecurl" in mon.moves) assertTrue("rollout" in mon.moves)
        }
    }
    @Test fun allowsSpeciesLearnablePartnersBeyondCurrentLevelAndFromTm() {
        val d0 = data(listOf("charge", "tackle"))
        for (sp in listOf(
            d0.sp(1).copy(levelMoves = d0.sp(1).levelMoves + LevelMove(70, "spark")),
            d0.sp(1).copy(tmMoves = setOf("spark")))) {
            val d = GameData(mapOf(1 to sp), d0.moves, emptyMap(), emptyMap())
            assertEquals(listOf("charge", "tackle", "spark"), LinkedMoves.generated(d, sp, listOf("charge", "tackle")))
        }
    }
    @Test fun caughtPokemonKeepsGeneratedPair() {
        val d = data(listOf("defensecurl", "tackle", "rollout"))
        val wild = Progression.newMon(d, 1, 5, Random(7), 0)
        val state = GameState()
        val caught = Catching.addToCollection(d, state, wild, Tier.NORMAL, null, 1, mutableListOf())
        assertEquals(wild.moves, caught.moves)
        assertNotSame(wild.moves, caught.moves)
        assertTrue("rollout" in caught.moves)
    }
    @Test fun preservesSeveralPairs() {
        val d = data(moves.map { it.id })
        val result = LinkedMoves.generated(d, d.sp(1), listOf("defensecurl", "charge", "stockpile", "tackle"))
        assertTrue(result.size <= 4)
        for (s in result.filter { it in LinkedMoves.empowering }) assertTrue(result.any { LinkedMoves.empowers(s, d.move(it)!!) })
    }
    @Test fun namesOnlyKnownLinkedMoves() {
        for ((source, yes, no) in listOf(Triple("defensecurl", "rollout", "Ice Ball"), Triple("stockpile", "swallow", "Spit Up"))) {
            val text = MoveText.describe(moves.first { it.id == source }, moves.filter { it.id == source || it.id == yes })
            assertTrue(text.contains(yes)); assertFalse(text.contains(no))
        }
        assertTrue(MoveText.describe(moves.first { it.id == "charge" }, moves.filter { it.id in setOf("charge", "spark") }).contains("spark"))
    }
    @Test fun warnsInBothDirectionsAndNamesOtherMove() {
        val d = data(moves.map { it.id })
        assertTrue(LinkedMoves.replacementWarning(d, listOf("defensecurl", "rollout"), 1, "tackle")!!.contains("defensecurl becomes useless"))
        assertTrue(LinkedMoves.replacementWarning(d, listOf("defensecurl", "rollout"), 0, "tackle")!!.contains("rollout becomes useless"))
    }
    @Test fun noWarningWithRemainingPartnerOrReplacementPartner() {
        val d = data(moves.map { it.id })
        assertNull(LinkedMoves.replacementWarning(d, listOf("defensecurl", "rollout", "iceball"), 1, "tackle"))
        assertNull(LinkedMoves.replacementWarning(d, listOf("defensecurl", "rollout"), 1, "iceball"))
        assertNull(LinkedMoves.replacementWarning(d, listOf("tackle", "scratch"), 1, "growl"))
    }
}
