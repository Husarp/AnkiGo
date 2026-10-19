package com.husarp.ankigo.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** R10-B18 / B4: the generation text of the details and the learn-level label of a species' move. */
class DetailGenerationTest {
    @Test fun generationNamesTheRegion() {
        val regions = listOf("Kanto", "Johto", "Hoenn", "Sinnoh", "Unova", "Kalos", "Alola", "Galar", "Paldea")
        regions.forEachIndexed { i, r -> assertEquals("Gen ${i + 1} · $r", DetailFormat.generation(i + 1)) }
        assertEquals("Gen 3 · Hoenn", DetailFormat.generation(3))
    }

    @Test fun unknownGenerationIsJustTheNumber() {
        assertEquals("Gen 10", DetailFormat.generation(10))
        assertEquals("Gen 0", DetailFormat.generation(0))
        assertEquals("Gen -1", DetailFormat.generation(-1))
    }

    @Test fun learnLevelLabels() {
        assertEquals("Evo", DetailFormat.learnLevel(0))
        assertEquals("Start", DetailFormat.learnLevel(1))
        assertEquals("Lv 2", DetailFormat.learnLevel(2))
        assertEquals("Lv 100", DetailFormat.learnLevel(100))
    }
}
