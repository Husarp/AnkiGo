package com.husarp.ankigo.game

import org.junit.Assert.*
import org.junit.Test

class R20MoveTextTest {
    private val extra = listOf(
        Move("stockpile", "Stockpile", Type.NORMAL, Category.STATUS, 0, null, target = "self"),
        Move("swallow", "Swallow", Type.NORMAL, Category.STATUS, 0, null, target = "self"),
        Move("spitup", "Spit Up", Type.NORMAL, Category.SPECIAL, 0, 100),
    )
    private val data = GameData(R17C8.data.species, R17C8.data.moves + extra.associateBy { it.id }, R17C8.data.growth, R17C8.data.itemPrices)
    @Test fun accuracyGuideExplainsSubtractionClampingAndOneFactor() {
        assertTrue(StageText.table.contains("Subtract the target's evasion stage from its accuracy stage"))
        assertTrue(StageText.table.contains("clamp the result to −6…+6"))
        assertTrue(StageText.table.contains("+2 accuracy against +1 evasion gives ×4/3"))
        assertFalse(StageText.table.contains("divided by"))
        assertEquals("4/3", StageText.factor((2 - 1).coerceIn(-6, 6), true))
        assertEquals("3", StageText.factor((6 - -6).coerceIn(-6, 6), true))
    }

    @Test fun everyMoveDescriptionUsesThePokemonAsItsSubject() {
        data.moves.values.forEach { move ->
            assertFalse(move.id + ": " + MoveText.describe(move), Regex("(?i)\\b(you|your|yours)\\b").containsMatchIn(MoveText.describe(move)))
        }
    }

    @Test fun stageFactorsMatchTheEngineForEveryStage() {
        for (n in -6..6) {
            fun value(s: String): Double = s.split('/').let { if (it.size == 2) it[0].toDouble() / it[1].toDouble() else it[0].toDouble() }
            assertEquals(BattleEngine.stageMult(n), value(StageText.factor(n)), 1e-12)
            assertEquals(BattleEngine.accStageMult(n), value(StageText.factor(n, true)), 1e-12)
        }
        assertTrue(StageText.table.contains("−6 ×1/4"))
        assertTrue(StageText.table.contains("−6 ×1/3"))
    }
}
