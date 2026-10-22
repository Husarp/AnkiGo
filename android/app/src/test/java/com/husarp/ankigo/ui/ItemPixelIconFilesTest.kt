package com.husarp.ankigo.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer

/** PLAN 166A (R14-B2-C): the five drawn items have a 16x16 PNG in res/drawable-nodpi (read from the file header). */
class ItemPixelIconFilesTest {
    private val dir = File(System.getProperty("user.dir"), "src/main/res/drawable-nodpi")

    @Test fun eachDrawnItemHasA16x16Png() {
        for (id in ItemIconPlan.DRAWN) {
            val f = File(dir, "item_${id.replace('-', '_')}.png")
            assertTrue(f.path, f.isFile)
            val b = f.readBytes()
            assertTrue("${f.name} is a PNG", b.size > 33 && b[0] == 0x89.toByte() && b[1] == 'P'.code.toByte() && b[2] == 'N'.code.toByte() && b[3] == 'G'.code.toByte())
            val ihdr = ByteBuffer.wrap(b, 16, 8)
            assertEquals("${f.name} width", 16, ihdr.int)
            assertEquals("${f.name} height", 16, ihdr.int)
        }
    }
}
