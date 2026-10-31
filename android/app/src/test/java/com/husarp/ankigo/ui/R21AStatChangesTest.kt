package com.husarp.ankigo.ui

import com.husarp.ankigo.game.Battle
import com.husarp.ankigo.game.EffectKind
import com.husarp.ankigo.game.GameEvent
import com.husarp.ankigo.game.TestData
import com.husarp.ankigo.game.Tier
import org.junit.Assert.*
import org.junit.Test

class R21AStatChangesTest {
    private fun battle(): Battle {
        val data = TestData.world()
        return Battle(TestData.mon(data, 54, 25, listOf("tackle"), "wild"), Tier.NORMAL)
    }
    @Test fun stagesComeFirstAndKeepTheRealStageCount() {
        val b = battle()
        b.player.boosts[0] = 6; b.player.boosts[2] = -2
        b.player.volatiles["confusion"] = 3
        val board = ConditionBoard(); board.syncFrom(b)
        val marks = board.of(ConditionMarks.Slot.OURS)
        assertEquals(listOf("stat.atk", "stat.spa", "confusion"), marks.map { it.key })
        assertEquals("Atk +6", marks[0].chipText)
        assertEquals("SpA −2", marks[1].chipText)
        assertTrue(marks[0].detail.contains("×4.00"))
        assertTrue(marks[1].detail.contains("×0.50"))
    }
    @Test fun signedChipsCoverEveryStageAndHideZero() {
        val b = battle()
        val board = ConditionBoard()
        for (stage in -6..6) {
            b.player.boosts[0] = stage
            board.syncFrom(b)
            val marks = board.of(ConditionMarks.Slot.OURS)
            if (stage == 0) assertTrue(marks.isEmpty())
            else assertEquals("Atk ${if (stage > 0) "+" else "−"}${kotlin.math.abs(stage)}", marks.single().chipText)
        }
    }
    @Test fun switchAndNewFightRemoveStages() {
        val b = battle(); b.player.boosts[0] = 2; b.enemy.boosts[4] = -1
        val board = ConditionBoard(); board.syncFrom(b)
        board.dropOurVolatiles()
        assertTrue(board.of(ConditionMarks.Slot.OURS).isEmpty())
        assertEquals(1, board.of(ConditionMarks.Slot.WILD).size)
        board.syncFrom(null); assertEquals(0, board.size)
    }
    @Test fun playedStatChangeAndResetFollowSavedFight() {
        val b = battle(); val board = ConditionBoard()
        b.enemy.boosts[5] = -3
        val e = GameEvent.Effect(false, "Accuracy fell", 10, 20, null, EffectKind.STAT_CHANGE, "accuracy", -3)
        assertTrue(board.applyEffect(e, b))
        assertTrue(board.of(ConditionMarks.Slot.WILD).single().detail.contains("×0.50"))
        b.enemy.boosts[5] = 0
        assertTrue(board.applyEffect(e, b)); assertEquals(0, board.size)
    }
    @Test fun retiredIceChoiceFallsBackWithoutChangingOtherSavedNames() {
        assertEquals(BattleFrameStyle.NONE, BattleFrameStyle.parse("ICE"))
        assertEquals(BattleFrameStyle.AUTOMATIC, BattleFrameStyle.parse("AUTOMATIC"))
        assertEquals(BattleFrameStyle.SEA, BattleFrameStyle.parse("SEA"))
    }
}
