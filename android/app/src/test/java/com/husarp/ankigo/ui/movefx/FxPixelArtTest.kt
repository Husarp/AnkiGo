package com.husarp.ankigo.ui.movefx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The ported pixel rows are intact: right sizes, only palette characters. */
class FxPixelArtTest {
    @Test fun flameFramesAre22By16() {
        assertEquals(6, FxPixelArt.FLAME_BIG.size)
        assertEquals(6, FxPixelArt.FLAME_SMALL.size)
        for (f in FxPixelArt.FLAME_BIG + FxPixelArt.FLAME_SMALL) {
            val rows = FxPixelArt.rows(f)
            assertEquals(16, rows.size)
            for (r in rows) {
                assertEquals(22, r.length)
                assertTrue(r.all { it == '.' || it in FxPixelArt.FLAME_PALETTE })
            }
        }
    }

    @Test fun flameFramesHaveAHeadAndDifferFromEachOther() {
        assertEquals(6, FxPixelArt.FLAME_BIG.toSet().size)
        assertEquals(6, FxPixelArt.FLAME_SMALL.toSet().size)
        // the head (art pixel 16, 9.5) sits on lit pixels of every frame
        for (f in FxPixelArt.FLAME_BIG) assertTrue(FxPixelArt.rows(f)[9][16] != '.' || FxPixelArt.rows(f)[10][16] != '.')
    }

    @Test fun leafIs9By6() {
        assertEquals(6, FxPixelArt.LEAF_ROWS.size)
        for (r in FxPixelArt.LEAF_ROWS) {
            assertEquals(9, r.length)
            assertTrue(r.all { it == '.' || it in FxPixelArt.LEAF_PALETTE })
        }
    }

    @Test fun emberColoursAreOpaque() {
        for (c in FxPixelArt.EMBER_COLORS + FxPixelArt.DIRT_COLORS) assertEquals(0xFF, c ushr 24)
    }
}
