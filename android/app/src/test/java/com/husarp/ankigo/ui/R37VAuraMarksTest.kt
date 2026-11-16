package com.husarp.ankigo.ui

import com.husarp.ankigo.game.*
import org.junit.Assert.*
import org.junit.Test

class R37VAuraMarksTest {
    private val data = TestData.world()
    private fun battle(tier: Tier = Tier.LEGENDARY) = Battle(TestData.mon(data, 150, 80), tier).apply {
        enemy.boosts.fill(1, 0, 5)
    }
    private fun effect() = GameEvent.Effect(false, "Attack fell!", 10, 20, null, EffectKind.STAT_CHANGE, "atk", -1)

    @Test fun rareTiersCollapseOnlyTheirFiveStatsAndKeepOtherConditions() {
        for (tier in listOf(Tier.LEGENDARY, Tier.MYTHICAL)) {
            val b = battle(tier)
            b.enemy.volatiles["confusion"] = 3
            b.enemy.boosts[5] = -1
            b.player.boosts.fill(1, 0, 5)
            val board = ConditionBoard().apply { syncFrom(b) }
            assertEquals(setOf("legendaryaura", "confusion", "stat.accuracy"), board.of(ConditionMarks.Slot.WILD).map { it.key }.toSet())
            assertEquals(5, board.of(ConditionMarks.Slot.OURS).size)
            val aura = board.of(ConditionMarks.Slot.WILD).single { it.key == "legendaryaura" }
            assertEquals("Aura", aura.chipText)
            assertNull(aura.number)
            assertEquals(ConditionMarks.Group.AURA, aura.def.group)
            assertEquals("Legendary aura: every stat ×1.5 for this fight", ConditionExplanation.effect(aura))
            assertNotNull(ConditionIcons.of(aura.key))
        }
    }

    @Test fun otherTiersAlwaysShowTheirActualStages() {
        for (tier in Tier.entries.filter { it != Tier.LEGENDARY && it != Tier.MYTHICAL }) {
            val board = ConditionBoard().apply { syncFrom(battle(tier)) }
            assertEquals(5, board.size)
            assertTrue(board.all().all { it.key.startsWith("stat.") })
        }
    }

    @Test fun changingAnyCoreStatShowsEveryRealStage() {
        for (i in 0 until 5) for (value in listOf(-6, 0, 2, 6)) {
            val b = battle()
            val board = ConditionBoard().apply { syncFrom(b) }
            b.enemy.boosts[i] = value
            assertTrue(board.syncFrom(b))
            assertFalse(board.all().any { it.key == "legendaryaura" })
            assertEquals(b.enemy.boosts.filter { it != 0 }.size, board.size)
            assertEquals(b.enemy.boosts.toList().filter { it != 0 }.sorted(), board.all().map { it.value }.sorted())
        }
    }

    @Test fun stageEventsReplaceAuraImmediatelyAndCollapseAgainWhenExactlyOne() {
        val b = battle()
        val board = ConditionBoard().apply { syncFrom(b) }
        b.enemy.boosts[0] = 0
        assertTrue(board.applyEffect(effect(), b))
        assertEquals(setOf("stat.def", "stat.spa", "stat.spd", "stat.spe"), board.all().map { it.key }.toSet())
        b.enemy.boosts[0] = 1
        assertTrue(board.applyEffect(effect(), b))
        assertEquals("legendaryaura", board.all().single().key)
    }

    @Test fun resettingStagesAndEndingTheBattleRemoveAura() {
        val b = battle()
        val board = ConditionBoard().apply { syncFrom(b) }
        b.enemy.boosts.fill(0)
        assertTrue(board.syncFrom(b))
        assertEquals(0, board.size)
        b.enemy.boosts.fill(1, 0, 5)
        board.syncFrom(b)
        assertTrue(board.syncFrom(null))
        assertEquals(0, board.size)
    }
}
