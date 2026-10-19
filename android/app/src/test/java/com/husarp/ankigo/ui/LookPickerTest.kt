package com.husarp.ankigo.ui

import com.husarp.ankigo.game.Looks
import com.husarp.ankigo.game.TestData
import com.husarp.ankigo.game.lookNum
import com.husarp.ankigo.game.lookSpecies
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** R3-V8 (PLAN 102, pick 8B): when "Change look" exists, what the cards say, and which picture an owned Pokémon shows. Made-up species only. */
class LookPickerTest {
    private val data = TestData.world()
    private fun mon(num: Int) = TestData.mon(data, num, 20, listOf("tackle"), "m$num")

    @Test fun entryOnlyWhenThereIsAChoice() {
        assertTrue(Looks.hasChoice(data, mon(3), data.species.keys))     // Leafking <- Leafbud <- Leafling
        assertTrue(Looks.hasChoice(data, mon(2), data.species.keys))
        assertFalse(Looks.hasChoice(data, mon(1), data.species.keys))    // base form, nothing to pick
        assertFalse(Looks.hasChoice(data, mon(16), data.species.keys))   // no evolution line at all
    }

    @Test fun captionsAndPicks() {
        assertEquals("Stage 1", LookPicker.caption(0, 1, 3))
        assertEquals("Stage 2", LookPicker.caption(1, 2, 3))
        assertEquals("Current", LookPicker.caption(2, 3, 3))
        assertNull(LookPicker.pickOf(3, 3))           // its own species = no look chosen
        assertEquals(1, LookPicker.pickOf(1, 3))
    }

    @Test fun ownedPictureFollowsTheLook() {
        val m = mon(3)
        assertEquals(3, m.lookNum(data))
        m.look = 1
        assertEquals(1, m.lookNum(data))
        assertEquals("Leafling", m.lookSpecies(data).name)
        m.look = 25                                    // not in its line (stale save): its own species
        assertEquals(3, m.lookNum(data))
        m.look = 1
        assertEquals(3, m.lookNum(null))              // data not loaded: its own species
    }
}
