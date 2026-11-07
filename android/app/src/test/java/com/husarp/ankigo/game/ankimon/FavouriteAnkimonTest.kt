package com.husarp.ankigo.game.ankimon

import com.husarp.ankigo.game.GameData
import com.husarp.ankigo.game.GameState
import com.husarp.ankigo.game.Mon
import com.husarp.ankigo.game.TestData
import com.husarp.ankigo.game.save.SaveCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Favourites and Ankimon (R2-02): kept on re-import. */
class FavouriteAnkimonTest {
    private val NOW = 1_800_000_000_000L
    private val data: GameData = TestData.world()

    private fun am(level: Int) = AnkimonMon(
        individualId = "fav-1", isMain = true, id = 25, name = "Sparkmouse", nickname = null, level = level, xp = 0,
        iv = emptyMap(), ev = emptyMap(), nature = null, gender = null, ability = null, attacks = listOf("tackle"), shiny = false,
        heldItem = null, everstone = false, friendship = null, capturedDate = null, defeated = null, tier = null,
    )

    private fun sv(level: Int) = AnkimonSave(listOf(am(level)), emptyList(), emptyMap(), emptyList(), null, emptySet(), emptySet(), emptyList())

    private fun merge(state: GameState, level: Int) = AnkimonMerge.merge(data, state, sv(level), { null }, false, NOW)

    @Test fun reImportKeepsTheFavouriteFlagAndUpdatesTheMonInPlace() {
        val state = GameState()
        merge(state, 10)
        val mon = state.mons.single()
        mon.favourite = true
        val r = merge(state, 25) // the file is further on: the Mon is upgraded in place
        assertEquals(1, r.upgraded)
        assertTrue(state.mons.single() === mon)
        assertEquals(25, mon.level)
        assertTrue(mon.favourite)
        merge(state, 25) // same file again
        assertTrue(state.mons.single().favourite)
    }

    @Test fun favouriteSurvivesABackupRestoreOfTheWholeState() {
        val state = GameState()
        merge(state, 10)
        state.mons.single().favourite = true
        assertTrue(SaveCodec.decode(SaveCodec.encode(state)).mons.single().favourite)
    }
}
