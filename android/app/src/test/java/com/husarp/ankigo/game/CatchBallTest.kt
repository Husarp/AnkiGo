package com.husarp.ankigo.game

import org.junit.Assert.assertEquals
import org.junit.Test

/** PLAN 205 (R14 B2-E): which ball an auto-catch animation shows, by the wild Pokémon's rarity. */
class CatchBallTest {
    private fun total(t: Int) = TestData.stats(t / 6, t / 6, t / 6, t / 6, t / 6, t - 5 * (t / 6))

    @Test fun mythicalIsAMasterBall() = assertEquals("master-ball", CatchBall.forAuto(TestData.species(151, isMythical = true)))

    @Test fun legendaryIsAnUltraBall() = assertEquals("ultra-ball", CatchBall.forAuto(TestData.species(150, isLegendary = true)))

    @Test fun legendaryBeatsBigBaseTotal() =
        assertEquals("ultra-ball", CatchBall.forAuto(TestData.species(150, base = total(680), isLegendary = true)))

    @Test fun starterIsAGreatBallEvenWithSmallStats() = assertEquals("great-ball", CatchBall.forAuto(TestData.species(1, base = total(318))))

    @Test fun ultraBeastIsAGreatBall() = assertEquals("great-ball", CatchBall.forAuto(TestData.species(793, base = total(300))))

    @Test fun babyIsAGreatBall() = assertEquals("great-ball", CatchBall.forAuto(TestData.species(172, base = total(205), isBaby = true)))

    @Test fun normalOnTheFiveHundredEdge() {
        assertEquals("poke-ball", CatchBall.forAuto(TestData.species(300, base = total(499))))
        assertEquals("great-ball", CatchBall.forAuto(TestData.species(300, base = total(500))))
        assertEquals("great-ball", CatchBall.forAuto(TestData.species(300, base = total(600))))
    }

    @Test fun anOrdinarySmallPokemonIsAPokeBall() = assertEquals("poke-ball", CatchBall.forAuto(TestData.species(19, base = total(253))))

    @Test fun noSpeciesDataIsAPokeBall() = assertEquals("poke-ball", CatchBall.forAuto(null))

    @Test fun aRegionalFormFollowsItsBaseNumber() {
        // a form of a legendary / starter line has baseNum of the base species
        val form = TestData.species(10_100, isLegendary = true).copy(form = "Alola", baseNum = 150)
        assertEquals("ultra-ball", CatchBall.forAuto(form))
    }
}
