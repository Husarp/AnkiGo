package com.husarp.ankigo.ui

import com.husarp.ankigo.game.Category
import com.husarp.ankigo.game.EffectKind
import com.husarp.ankigo.game.GameEvent
import com.husarp.ankigo.game.Type
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** R19-C (PLAN 125 X1): the stat-change pill: short names, stage clamp, up / down, and the chips the move bubble gets. */
class R19CStatPillTest {
    private fun attack(byPlayer: Boolean) =
        GameEvent.Attack(byPlayer, "Sparkmouse", "Target", "Swords Dance", Type.NORMAL, Category.STATUS, 0, 1, 1.0, false, false, false, 1, 10, 10, 10)

    private fun effect(stat: String, stages: Int) = GameEvent.Effect(true, "x", 5, 10, null, EffectKind.STAT_CHANGE, stat, stages)

    @Test fun shortNamesOfTheSevenStats() {
        val keys = listOf("atk", "def", "spa", "spd", "spe", "accuracy", "evasion")
        assertEquals(listOf("Atk", "Def", "SpA", "SpD", "Spe", "Acc", "Eva"), keys.map { StatPill.shortName(it) })
        assertNull(StatPill.shortName("hp")); assertNull(StatPill.shortName(null))
    }

    @Test fun stagesRetainAllSixArrows() {
        assertEquals(listOf(1, 1, 2, 3, 4, 6), listOf(0, 1, 2, 3, 4, 6).map { StatPill.count(it) })
        assertEquals(listOf(1, 2, 3, 6), listOf(-1, -2, -3, -6).map { StatPill.count(it) })
    }

    @Test fun spokenSaysDirectionAndStages() {
        assertEquals("Attack up 6", StatPill.spoken("atk", 6))
        assertEquals("Attack down 6", StatPill.spoken("atk", -6))
        assertEquals("Attack up 2", StatPill.spoken("atk", 2))
        assertEquals("Defense down 1", StatPill.spoken("def", -1))
        assertEquals("Special Attack up 5", StatPill.spoken("spa", 5))
        assertEquals("Accuracy down 3", StatPill.spoken("accuracy", -3))
        assertFalse(StatPill.spoken("spe", 1).any { it in "↑↓" })
    }

    @Test fun tintIsStrongerInTheDarkTheme() {
        assertTrue(StatPill.tint(true, dark = true) > StatPill.tint(true, dark = false))
        assertEquals(0.22f, StatPill.tint(false, dark = true), 0f)
        assertEquals(0.13f, StatPill.tint(true, dark = false), 0f)
        assertTrue(StatPill.UP != StatPill.DOWN)
    }

    @Test fun aSecondRowDistinguishesFourThroughSix() {
        for (n in 4..6) {
            assertEquals(21f, StatPill.boxHeight(n), 0f)
            assertEquals(36f, StatPill.boxWidth(n), 0f)
        }
        assertEquals(11f, StatPill.boxHeight(1), 0f)
        assertEquals(11f, StatPill.boxHeight(2), 0f)
        assertEquals(11f, StatPill.boxHeight(3), 0f)
    }

    @Test fun theBubbleGetsAPillChipPerShift() {
        val m = ActionBarModel()
        m.observe(attack(true))
        m.observe(effect("atk", 2)); m.observe(effect("def", -1)); m.observe(effect("spe", 1)); m.observe(effect("accuracy", -3))
        val c = ActionBarModel.bubble((m.half(BarSide.MINE) as HalfState.Moved).move)
        assertEquals(listOf("Attack up 2", "Defense down 1", "Speed up 1", "Accuracy down 3"), c.chips.map { it.label })
        assertEquals(listOf(BubbleTone.STAT_UP, BubbleTone.STAT_DOWN, BubbleTone.STAT_UP, BubbleTone.STAT_DOWN), c.chips.map { it.tone })
        assertEquals(listOf("atk" to 2, "def" to -1, "spe" to 1, "accuracy" to -3), c.chips.map { it.stat to it.stages })
        assertTrue(c.chips.none { chip -> chip.label.any { it in "↑↓" } })
        assertEquals("Swords Dance", c.move)
        assertTrue(c.spoken().endsWith("Attack up 2, Defense down 1, Speed up 1, Accuracy down 3"))
    }
}
