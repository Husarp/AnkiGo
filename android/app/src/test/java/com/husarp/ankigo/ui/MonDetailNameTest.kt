package com.husarp.ankigo.ui

import com.husarp.ankigo.game.TestData
import org.junit.Assert.assertEquals
import org.junit.Test

/** R3-X2 item 2: the gender sign leads the name line of the details (♂/♀ before the name, none for a genderless one). */
class MonDetailNameTest {
    private val data = TestData.world()
    private fun mon(gender: Char, shiny: Boolean = false, nick: String? = null) =
        TestData.mon(data, 25, 10).also { it.gender = gender; it.shiny = shiny; it.nickname = nick }

    @Test fun maleAndFemaleSignsComeFirst() {
        assertEquals("♂ Sparkmouse", MonDetail.nameLine(mon('M')))
        assertEquals("♀ Sparkmouse", MonDetail.nameLine(mon('F')))
    }

    @Test fun aGenderlessOneHasNoSign() = assertEquals("Sparkmouse", MonDetail.nameLine(mon('N')))

    @Test fun theShinyStarAndTheNicknameStayInThePlace() {
        assertEquals("♀ ★ Sparkmouse", MonDetail.nameLine(mon('F', shiny = true)))
        assertEquals("♂ Zappy", MonDetail.nameLine(mon('M', nick = "Zappy")))
        assertEquals("★ Zappy", MonDetail.nameLine(mon('N', shiny = true, nick = "Zappy")))
    }
}
