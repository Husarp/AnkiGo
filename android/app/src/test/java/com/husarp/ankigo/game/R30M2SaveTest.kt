package com.husarp.ankigo.game

import com.husarp.ankigo.game.save.SaveCodec
import org.junit.Assert.*
import org.junit.Test

class R30M2SaveTest {
    @Test fun mergedSaveRetainsProtectionPurchasesHeldItemsKnowledgeAndNatureCarries() {
        val data = TestData.world()
        val state = TestData.state(data, 25, 20)
        state.day = "2026-11-07"
        state.lastGoalDay = "2026-11-05"
        state.streakThroughDay = "2026-11-06"
        state.streakDays = 17
        state.bag[StreakFreeze.ITEM] = 2
        state.battleFreezeDay = state.day
        state.shopPurchases += "2026-11-07:everstone"
        state.defeatedSpecies += 54
        state.main!!.apply { heldItem = "soothe-bell"; natureXpCarry = 137; natureBondCarry = 79 }
        state.achLadder = 4
        state.achTiers["types"] = mutableListOf(11L, 22L, 33L)
        state.achInferred += "types/2"
        val restored = SaveCodec.decode(SaveCodec.encode(state))
        assertTrue(Achievements.migrate(restored))
        assertEquals(listOf(22L, 33L), restored.achTiers["types"])
        assertTrue("types/1" in restored.achInferred)
        assertEquals(17, restored.streakDays)
        assertEquals(state.lastGoalDay, restored.lastGoalDay)
        assertEquals(state.streakThroughDay, restored.streakThroughDay)
        assertEquals(2, restored.count(StreakFreeze.ITEM))
        assertEquals(state.day, restored.battleFreezeDay)
        assertTrue(Shop.soldOut(restored, "everstone"))
        assertTrue(SpeciesKnowledge.known(data, restored, 54))
        assertEquals("soothe-bell", restored.main!!.heldItem)
        assertEquals(137, restored.main!!.natureXpCarry)
        assertEquals(79, restored.main!!.natureBondCarry)
        assertTrue(StreakFreeze.rollover(restored, state.day).isEmpty())
        assertFalse(Achievements.migrate(restored))
    }
}
