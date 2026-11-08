package com.husarp.ankigo.ui

import org.junit.Assert.*
import org.junit.Test

class R30U1ConditionTest {
    @Test fun combinedRowIsUsedAtTheExactFitAndEffectsWrapOnlyWhenNeeded() {
        val widths = floatArrayOf(40f, 40f, 70f, 60f)
        assertEquals(listOf(listOf(0, 1, 2, 3)), ConditionFlow.rows(widths, 2, 225f, 5f))
        assertEquals(listOf(listOf(0, 1), listOf(2, 3)), ConditionFlow.rows(widths, 2, 224f, 5f))
        assertEquals(listOf(listOf(0, 1), listOf(2), listOf(3)), ConditionFlow.rows(widths, 2, 100f, 5f))
    }

    @Test fun crowdedStatsKeepEveryStageAndNeverAddAnOverflowChip() {
        val widths = FloatArray(10) { 42f }
        val rows = ConditionFlow.rows(widths, 7, 320f, 4f)
        assertEquals((0..9).toList(), rows.flatten())
        assertEquals((0..6).toList(), rows.first())
        assertEquals((7..9).toList(), rows.last())
    }

    @Test fun bothOwnersAndTheFieldFitWithoutCoveringCardsOrEachOther() {
        val wild = ConditionSpot.R(10f, 20f, 150f, 70f)
        val ours = ConditionSpot.R(190f, 200f, 350f, 270f)
        val scene = ConditionSpot.Scene(360f, 500f, null, listOf(wild, ours), 4f)
        val taken = mutableListOf<ConditionSpot.R>()
        for ((card, below) in listOf(wild to true, ours to false, null to true)) {
            val rects = ConditionFlow.place(scene, card, below, floatArrayOf(40f, 40f, 110f, 100f), 2, 5f, 5f, 20f, taken)
            assertEquals(4, rects.size)
            for (r in rects) {
                assertTrue(r.inside(scene.area))
                assertFalse((scene.blocks + taken).any { it.hits(r) })
            }
            taken += rects
        }
    }

    @Test fun embargoPerishSongAndHappyHourFollowTheSavedFightAndSwitchRules() {
        val data = com.husarp.ankigo.game.TestData.world()
        val battle = com.husarp.ankigo.game.Battle(com.husarp.ankigo.game.TestData.mon(data, 54, 25, listOf("tackle"), "wild"), com.husarp.ankigo.game.Tier.NORMAL)
        battle.player.volatiles["embargo"] = 4
        battle.player.volatiles["perishsong"] = 2
        battle.field.player["happyhour"] = 1
        val board = ConditionBoard()
        assertTrue(board.syncFrom(battle))
        val marks = board.of(ConditionMarks.Slot.OURS).associateBy { it.key }
        assertEquals(setOf("embargo", "perishsong", "happyhour"), marks.keys)
        assertEquals("4 turns left", marks.getValue("embargo").detail)
        assertEquals("2 turns left", marks.getValue("perishsong").detail)
        assertNull(marks.getValue("happyhour").number)
        assertEquals("", marks.getValue("happyhour").detail)
        board.dropOurVolatiles()
        assertEquals(listOf("happyhour"), board.of(ConditionMarks.Slot.OURS).map { it.key })
        board.syncFrom(null)
        assertEquals(0, board.size)
    }
}
