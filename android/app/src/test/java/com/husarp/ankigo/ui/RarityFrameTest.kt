package com.husarp.ankigo.ui

import com.husarp.ankigo.game.PokemonRarity
import org.junit.Assert.*
import org.junit.Test

class RarityFrameTest {
    @Test fun onlySpecialTiersHaveFramesInBothThemes() {
        for (dark in listOf(false, true)) {
            val effects = PokemonRarity.entries.associateWith { RarityFrame.effect(it, dark) }
            assertEquals(setOf(PokemonRarity.ULTRA, PokemonRarity.LEGENDARY, PokemonRarity.MYTHICAL), effects.filterValues { it != null }.keys)
            assertNull(RarityFrame.effect(null, dark))
            assertEquals(listOf(3, 5, 7), effects.values.filterNotNull().map { it.stars })
            assertEquals(listOf(1f, 1.3f, 1.65f), effects.values.filterNotNull().map { it.intensity })
            assertEquals(if (dark) 0xffc191fa.toInt() else 0xff842ef4.toInt(), effects[PokemonRarity.ULTRA]!!.color)
            assertEquals(if (dark) 0xffffa770.toInt() else 0xffff6004.toInt(), effects[PokemonRarity.LEGENDARY]!!.color)
            assertEquals(if (dark) 0xffff5b59.toInt() else 0xffd20934.toInt(), effects[PokemonRarity.MYTHICAL]!!.color)
        }
    }

    @Test fun burstsHaveIrregularFourToFiveSecondAverageAndOneToThreeGlints() {
        assertEquals(0f, RarityFrame.envelope(0), .001f)
        assertEquals(1f, RarityFrame.envelope(784), .001f)
        assertEquals(.5f, RarityFrame.envelope(392), .001f)
        assertEquals(.5f, RarityFrame.envelope(1204), .001f)
        assertEquals(0f, RarityFrame.envelope(1624), .001f)
        val counts = mutableSetOf<Int>()
        val schedules = mutableSetOf<List<Long>>()
        for (seed in 0..30) {
            val schedule = RarityFrame.schedule(seed)
            schedules += schedule.toList()
            val gaps = (schedule.toList() + RarityFrame.CYCLE_MS).zipWithNext { a, b -> b - a }
            assertEquals(4500.0, gaps.average(), 0.0)
            assertEquals(1000L, gaps.min())
            assertEquals(7500L, gaps.max())
            for (start in schedule) {
                val lit = (0..6).count { RarityFrame.glintAlpha(start + 784, it, seed, schedule) > 0 }
                assertTrue(lit in 1..3)
                counts += lit
            }
        }
        assertEquals(setOf(1, 2, 3), counts)
        assertTrue("each tile has its own random gaps", schedules.size > 25)
    }
}
