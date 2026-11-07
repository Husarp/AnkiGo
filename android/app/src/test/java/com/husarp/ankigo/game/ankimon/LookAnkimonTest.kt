package com.husarp.ankigo.game.ankimon

import com.husarp.ankigo.game.GameData
import com.husarp.ankigo.game.GameState
import com.husarp.ankigo.game.TestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The chosen look (R3-03) and Ankimon: kept on re-import. */
class LookAnkimonTest {
    private val NOW = 1_800_000_000_000L
    private val data: GameData = TestData.world()

    private fun am(level: Int) = AnkimonMon(
        individualId = "look-1", isMain = true, id = 25, name = "Sparkmouse", nickname = null, level = level, xp = 0,
        iv = emptyMap(), ev = emptyMap(), nature = null, gender = null, ability = null, attacks = listOf("tackle"), shiny = false,
        heldItem = null, everstone = false, friendship = null, capturedDate = null, defeated = null, tier = null,
    )

    private fun sv(level: Int) = AnkimonSave(listOf(am(level)), emptyList(), emptyMap(), emptyList(), null, emptySet(), emptySet(), emptyList())

    private fun merge(state: GameState, level: Int) = AnkimonMerge.merge(data, state, sv(level), { null }, false, NOW)

    @Test fun reImportKeepsTheChosenLook() {
        val state = GameState()
        merge(state, 10)
        val mon = state.mons.single()
        mon.look = 172
        val r = merge(state, 25) // the file is further on: the Mon is upgraded in place
        assertEquals(1, r.upgraded)
        assertTrue(state.mons.single() === mon)
        assertEquals(172, mon.look)
        merge(state, 25)
        assertEquals(172, state.mons.single().look)
    }

}
