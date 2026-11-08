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

    @Test fun burstsAreTwiceAsSlowAndOneThirdAsFrequent() {
        assertEquals(5600L, RarityFrame.BURST_MS)
        assertEquals(16800L, RarityFrame.CYCLE_MS)
        assertEquals(0f, RarityFrame.envelope(0), .001f)
        assertEquals(1f, RarityFrame.envelope(784), .001f)
        assertEquals(.5f, RarityFrame.envelope(392), .001f)
        assertEquals(.5f, RarityFrame.envelope(1204), .001f)
        assertEquals(0f, RarityFrame.envelope(1624), .001f)
        assertEquals(0f, RarityFrame.envelope(5600), .001f)
        assertEquals(0f, RarityFrame.envelope(16799), .001f)
        assertEquals(RarityFrame.envelope(784), RarityFrame.envelope(16800 + 784), .001f)
        val occupied = (0 until 16800).count { RarityFrame.envelope(it.toLong()) > 0 }
        assertEquals(.29 / 3, occupied / 16800.0, .001)
    }
}
