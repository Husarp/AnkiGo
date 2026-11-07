package com.husarp.ankigo.game

import com.husarp.ankigo.game.save.SaveCodec
import org.junit.Assert.*
import org.junit.Test

class R30TTypesTest {
    private val data = TestData.data(Type.entries.mapIndexed { i, t -> TestData.species(100 + i, types = listOf(t)) })
    private fun state(types: Int) = GameState(achLadder = 4).also { s -> repeat(types) { s.caught += 100 + it } }

    @Test fun versionFourReachedTiersKeepDatesAndInferredFlags() {
        for ((count, dates, expected) in listOf(
            Triple(6, listOf(11L), emptyList()), Triple(10, listOf(11L, 22L), listOf(22L)),
            Triple(18, listOf(11L, 22L, 33L), listOf(22L, 33L)))) {
            val s = state(count); s.achTiers["types"] = dates.toMutableList(); s.achInferred += listOf("types/1", "types/2", "answers/0")
            assertTrue(Achievements.migrate(s))
            assertEquals(expected, s.achTiers["types"])
            assertEquals(expected.indices.map { "types/$it" }.toSet(), s.achInferred.filter { it.startsWith("types/") }.toSet())
            assertTrue("answers/0" in s.achInferred)
            assertFalse(Achievements.migrate(s))
            assertEquals(expected, SaveCodec.decode(SaveCodec.encode(s)).achTiers["types"])
        }
    }

    @Test fun newThresholdsAndLabelsAreNineAndEighteenOnly() {
        for ((count, tier) in listOf(8 to -1, 9 to 0, 10 to 0, 17 to 0, 18 to 1)) {
            val s = state(count); Achievements.onReady(s, data, 123)
            val v = Achievements.view(s, data).single { it.id == "types" }
            assertEquals(tier, v.tier)
            assertEquals(listOf("Common", "Rare"), AchPageMath.ladder(v).map { it.name })
        }
    }
}
