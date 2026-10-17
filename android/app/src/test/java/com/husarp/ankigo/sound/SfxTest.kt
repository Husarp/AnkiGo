package com.husarp.ankigo.sound

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

class SfxTest {
    /** The six original battle sounds; the soft ones ([SoftSound]) are checked by the tests of [SoftSfx]. */
    private val LEGACY = SoundId.entries.filter { it.soft == null }

    private fun peak(s: ShortArray) = s.maxOf { abs(it.toInt()) } / 32767.0

    @Test fun everySoundIsShortAndSafe() {
        for (id in LEGACY) {
            val s = Sfx.samples(id)
            val seconds = s.size.toDouble() / Sfx.RATE
            assertTrue("$id length $seconds", seconds in 0.1..0.7)
            assertTrue("$id peak ${peak(s)}", peak(s) <= 0.8)
            assertTrue("$id is too quiet: ${peak(s)}", peak(s) >= 0.3)
        }
    }

    @Test fun edgesAreSilentSoThereAreNoClicks() {
        for (id in LEGACY) {
            val s = Sfx.samples(id)
            assertEquals("$id first", 0, s.first().toInt())
            assertTrue("$id last ${s.last()}", abs(s.last().toInt()) <= 3)
            // the first 5 ms ramp up and the last 5 ms ramp down: no sample there is louder than the ramp allows
            val n = Sfx.RATE / 200
            assertTrue("$id start ramp", s.take(n).all { abs(it.toInt()) < 0.8 * 32767 * 0.7 })
            assertTrue("$id end ramp", s.takeLast(n).all { abs(it.toInt()) < 0.8 * 32767 * 0.7 })
        }
    }

    @Test fun weakHitIsSofterThanHit() {
        assertTrue(peak(Sfx.samples(SoundId.WEAK)) < peak(Sfx.samples(SoundId.HIT)))
    }

    @Test fun originalSixAreUnchanged() {
        // fingerprints of the six original sounds from before the soft ones were added
        val got = LEGACY.joinToString { "${it.name}=${java.util.Arrays.hashCode(Sfx.samples(it))}" }
        assertEquals("HIT=-2078988362, SUPER=1879347882, WEAK=424984795, FAINT=848167230, CATCH=618402792, LEVEL_UP=-363492495", got)
    }

    @Test fun deterministic() {
        for (id in SoundId.entries) assertArrayEquals("$id", Sfx.wav(id), Sfx.wav(id))
    }

    @Test fun soundsDiffer() {
        val all = SoundId.entries.map { Sfx.samples(it).toList() }
        assertEquals(all.size, all.toSet().size)
    }

    @Test fun wavHeader() {
        for (id in SoundId.entries) {
            val pcm = Sfx.samples(id)
            val b = Sfx.wav(id)
            assertEquals(44 + pcm.size * 2, b.size)
            val bb = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN)
            fun tag(at: Int) = String(b, at, 4, Charsets.US_ASCII)
            assertEquals("RIFF", tag(0)); assertEquals(b.size - 8, bb.getInt(4)); assertEquals("WAVE", tag(8))
            assertEquals("fmt ", tag(12)); assertEquals(16, bb.getInt(16))
            assertEquals(1, bb.getShort(20).toInt())          // PCM
            assertEquals(1, bb.getShort(22).toInt())          // mono
            assertEquals(22_050, bb.getInt(24))
            assertEquals(44_100, bb.getInt(28))               // byte rate
            assertEquals(2, bb.getShort(32).toInt())          // block align
            assertEquals(16, bb.getShort(34).toInt())         // bits
            assertEquals("data", tag(36)); assertEquals(pcm.size * 2, bb.getInt(40))
            for (i in pcm.indices step 97) assertEquals(pcm[i], bb.getShort(44 + i * 2))
        }
    }

    @Test fun fileNamesAreVersioned() {
        // redesigned sounds (R7-X1: the quiet tab/menu/ball/shop ticks) carry -v2 so a cached old file is never played
        val v2 = setOf(SoundId.TAB, SoundId.MENU, SoundId.BALL_BTN, SoundId.BUY)
        assertTrue(SoundId.entries.all { it.file.endsWith(if (it in v2) "-v2.wav" else "-v1.wav") })
        assertEquals(SoundId.entries.size, SoundId.entries.map { it.file }.toSet().size)
    }
}
