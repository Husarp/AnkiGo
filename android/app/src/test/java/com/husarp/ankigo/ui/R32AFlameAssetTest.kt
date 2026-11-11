package com.husarp.ankigo.ui

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/** Original 512px Noto payload hashes; the released loop order and half-speed timing stay intact. */
class R32AFlameAssetTest {
    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    @Test fun everyFullResolutionFrameIsPreservedAtHalfSpeed() {
        val reference = javaClass.getResource("/streak-flame-r33/frames.txt")!!.readText().lines()
        val bytes = File("src/main/res/raw/streak_flame.webp").readBytes()
        fun uint(at: Int, count: Int) = (0 until count).sumOf { (bytes[at + it].toInt() and 255) shl (8 * it) }
        assertEquals("RIFF", String(bytes, 0, 4, Charsets.US_ASCII))
        assertEquals("WEBP", String(bytes, 8, 4, Charsets.US_ASCII))
        val frames = mutableListOf<String>()
        var offset = 12
        var duration = 0
        while (offset < bytes.size) {
            val kind = String(bytes, offset, 4, Charsets.US_ASCII)
            val size = uint(offset + 4, 4)
            if (kind == "VP8X") {
                assertEquals("Full-resolution canvas width", 512, uint(offset + 12, 3) + 1)
                assertEquals("Full-resolution canvas height", 512, uint(offset + 15, 3) + 1)
            }
            if (kind == "ANMF") {
                val ms = uint(offset + 20, 3)
                duration += ms
                val originalPayload = bytes.copyOfRange(offset + 8, offset + 8 + size)
                assertEquals("Exactly double each original duration", 60, ms)
                val originalMs = ms / 2
                for (i in 0..2) originalPayload[12 + i] = (originalMs shr (8 * i)).toByte()
                frames += "${frames.size} $originalMs ${sha(originalPayload)}"
            }
            if (kind == "ANIM") assertEquals("Loop forever", 0, uint(offset + 12, 2))
            offset += 8 + size + size % 2
        }
        assertEquals(reference.filter { it.isNotBlank() && !it.startsWith("#") }, frames)
        assertEquals(33, frames.size)
        assertEquals(1980, duration)
        assertTrue("Keep the original source encoding under 200 KB", bytes.size < 200_000)
        assertEquals("Each frame must actually move", 33, frames.map { it.substringAfterLast(' ') }.toSet().size)
    }
}
