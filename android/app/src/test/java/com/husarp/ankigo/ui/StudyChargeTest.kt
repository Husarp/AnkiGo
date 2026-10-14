package com.husarp.ankigo.ui

import com.husarp.ankigo.game.GameEvent
import com.husarp.ankigo.game.Tier
import org.junit.Assert.assertEquals
import org.junit.Test

class StudyChargeTest {
    private fun counted(button: Int) = GameEvent.Counted(button, 10, 1, 1, false)
    private fun encounter() = GameEvent.Encounter(25, "Wild Pokémon", 10, false, Tier.NORMAL, 20, 20, true, true)

    @Test fun `first answer stays charged when the encounter follows counted`() {
        val charge = StudyCharge()
        charge.observe(counted(3), 2)
        charge.observe(encounter(), 2)
        charge.observe(GameEvent.Charge(10, 1, 2), 2)
        assertEquals(listOf(3), charge.filled)
    }

    @Test fun `completed round shows both answers until the next answer begins`() {
        val charge = StudyCharge()
        charge.observe(counted(3), 2)
        charge.observe(GameEvent.Charge(10, 1, 2), 2)
        charge.observe(counted(4), 2)
        charge.observe(GameEvent.RoundStart(1.5, 30, false), 2)
        assertEquals(listOf(3, 4), charge.filled)
        charge.observe(counted(2), 2)
        assertEquals(listOf(2), charge.filled)
    }

    @Test fun `next encounter clears the finished battle without filling another pip`() {
        val charge = StudyCharge()
        charge.observe(counted(1), 1)
        charge.observe(GameEvent.RoundStart(0.0, 0, true), 1)
        charge.observe(encounter(), 1)
        assertEquals(emptyList<Int>(), charge.filled)
    }

    @Test fun `redo after undo does not fill a pip`() {
        val charge = StudyCharge()
        charge.observe(counted(3), 2)
        charge.observe(GameEvent.Charge(10, 1, 2), 2)
        charge.observe(GameEvent.Ignored("Redo"), 2)
        assertEquals(listOf(3), charge.filled)
    }

    @Test fun `restored tally shows an exact single answer and neutral unknown combinations`() {
        val charge = StudyCharge()
        for ((points, button) in listOf(0 to 1, 5 to 2, 10 to 3, 20 to 4)) {
            charge.restore(1, points, 2)
            assertEquals(listOf(button), charge.filled)
        }
        charge.restore(2, 20, 3) // Could be Again+Easy or Good+Good: do not invent either.
        assertEquals(listOf(0, 0), charge.filled)
    }

    @Test fun `custom round length stays consistent and fleeing empties it`() {
        val charge = StudyCharge()
        repeat(3) {
            charge.observe(counted(3), 4)
            charge.observe(GameEvent.Charge((it + 1) * 10, it + 1, 4), 4)
        }
        assertEquals(4, charge.goal)
        assertEquals(listOf(3, 3, 3), charge.filled)
        charge.observe(GameEvent.Fled("Wild Pokémon"), 4)
        assertEquals(emptyList<Int>(), charge.filled)
    }
}
