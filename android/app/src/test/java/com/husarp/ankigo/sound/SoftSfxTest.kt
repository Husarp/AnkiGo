package com.husarp.ankigo.sound

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs

class SoftSfxTest {
    private val rate = Sfx.RATE
    private val buttons = SoftSound.entries.filter { it.ordinal >= SoftSound.SHOW_ANSWER.ordinal }

    private fun peak(s: ShortArray) = s.maxOf { abs(it.toInt()) } / 32767.0

    /** Seconds until the last sample louder than about -46 dB. */
    private fun audibleEnd(s: ShortArray): Double {
        val last = s.indexOfLast { abs(it.toInt()) > 0.005 * 32767 }
        return (last + 1).toDouble() / rate
    }

    private fun zeroCrossRate(s: ShortArray): Double {
        var z = 0
        for (i in 1 until s.size) if ((s[i - 1] < 0) != (s[i] < 0)) z++
        return z / (s.size.toDouble() / rate)
    }

    @Test fun thereAre34Sounds() = assertEquals(34, SoftSound.entries.size)

    @Test fun everySoundRendersWithinLimits() {
        for (id in SoftSound.entries) {
            val s = SoftSfx.samples(id)
            assertTrue("$id empty", s.isNotEmpty())
            val p = peak(s)
            val low = when (id) { SoftSound.TAB -> 0.10..0.16; SoftSound.MENU -> 0.17..0.24; else -> 0.38..0.66 }
            assertTrue("$id peak $p", p in low)
            val len = audibleEnd(s)
            val limit = if (id in buttons) 0.08 else 0.6
            assertTrue("$id audible length $len > $limit", len <= limit)
            assertTrue("$id length ${s.size}", s.size.toDouble() / rate <= 0.62)
        }
    }

    @Test fun edgesAreSilentSoThereAreNoClicks() {
        for (id in SoftSound.entries) {
            val s = SoftSfx.samples(id)
            assertEquals("$id first", 0, s.first().toInt())
            assertTrue("$id last ${s.last()}", abs(s.last().toInt()) <= 3)
        }
    }

    @Test fun deterministic() {
        for (id in SoftSound.entries) assertArrayEquals("$id", SoftSfx.samples(id), SoftSfx.samples(id))
    }

    @Test fun noSoundIsSilentOrFlat() {
        for (id in SoftSound.entries) {
            val s = SoftSfx.samples(id)
            // a real waveform changes sign, and uses a good part of the range
            assertTrue("$id never crosses zero", zeroCrossRate(s) > 20)
            assertFalse("$id all equal", s.all { it == s[0] })
        }
    }

    @Test fun allSoundsAreDifferent() {
        val seen = HashMap<List<Short>, SoftSound>()
        for (id in SoftSound.entries) {
            val key = SoftSfx.samples(id).toList()
            val other = seen.put(key, id)
            assertTrue("$id equals $other", other == null)
        }
    }

    @Test fun answerButtonsRiseInPitch() {
        val r = listOf(SoftSound.AGAIN, SoftSound.HARD, SoftSound.GOOD, SoftSound.EASY).map { zeroCrossRate(SoftSfx.samples(it)) }
        assertTrue("again < hard < good < easy: $r", r[0] < r[1] && r[1] < r[2] && r[2] < r[3])
    }

    /** Share of the signal's energy above [hz], by a first-order high-pass of the samples (rough, but enough to tell a tick from a beep). */
    private fun highShare(s: ShortArray, hz: Double): Double {
        val a = Math.exp(-2 * Math.PI * hz / rate)
        var y = 0.0; var xp = 0.0; var hi = 0.0; var all = 0.0
        for (v in s) { val x = v / 32767.0; y = a * (y + x - xp); xp = x; hi += y * y; all += x * x }
        return hi / all
    }

    @Test fun tabTickIsTenDbUnderItsOldLevelAndUnderTheAnswerClicks() {
        val tab = peak(SoftSfx.samples(SoftSound.TAB))
        assertTrue("tab $tab", tab <= 0.42 * 0.32) // the old level 0.42, minus 10 dB
        for (b in listOf(SoftSound.AGAIN, SoftSound.HARD, SoftSound.GOOD, SoftSound.EASY, SoftSound.SHOW_ANSWER)) {
            assertTrue("tab $tab vs $b", tab < peak(SoftSfx.samples(b)) / 3)
        }
        assertTrue(peak(SoftSfx.samples(SoftSound.MENU)) < peak(SoftSfx.samples(SoftSound.AGAIN)) / 2)
    }

    @Test fun tabAndMenuTicksAreLowAndHaveNoHarshPartials() {
        for (id in listOf(SoftSound.TAB, SoftSound.MENU)) {
            val s = SoftSfx.samples(id)
            assertTrue("$id energy above 3 kHz: ${highShare(s, 3000.0)}", highShare(s, 3000.0) < 0.02)
            assertTrue("$id zero crossings ${zeroCrossRate(s)}", zeroCrossRate(s) < 2 * 700) // a fundamental under ~700 Hz
            val s2 = s.size.toDouble() / rate
            assertTrue("$id length $s2", s2 <= 0.06)
        }
    }

    @Test fun buttonsAreShort() {
        for (id in buttons) assertTrue("$id", SoftSfx.samples(id).size.toDouble() / rate <= 0.09)
    }

    /** Writes every sound as a WAV so it can be listened to (not an assertion). */
    @Test fun writeWavsForListening() {
        val dir = File("build/sfx-r5").also { it.mkdirs() }
        for (id in SoftSound.entries) File(dir, "${id.name.lowercase()}.wav").writeBytes(Sfx.wav(SoftSfx.samples(id)))
        assertEquals(34, dir.listFiles { f -> f.extension == "wav" }!!.size)
    }

    // ---- R5-FIX 6: band-limited triangle ----

    @Test fun triangleUsesFewerHarmonicsAsPitchRises() {
        assertEquals(51, SoftSfx.triangleHarmonics(100.0)) // capped
        assertEquals(5, SoftSfx.triangleHarmonics(2000.0)) // 1, 3, 5 below 10.8 kHz
        assertEquals(1, SoftSfx.triangleHarmonics(8000.0))
        assertTrue(SoftSfx.triangleHarmonics(500.0) > SoftSfx.triangleHarmonics(1000.0))
    }

    @Test fun triangleKeepsThePeakOfTheOldOne() {
        assertEquals(1.0, SoftSfx.triangle(Math.PI / 2, 110.0), 0.01)
        assertEquals(-1.0, SoftSfx.triangle(-Math.PI / 2, 110.0), 0.01)
        assertEquals(0.0, SoftSfx.triangle(0.0, 110.0), 1e-9)
    }

    @Test fun triangleOf2kHzHasNoFoldedHarmonics() {
        // 2 kHz at 22,050 Hz: the naive triangle's 7th harmonic (14 kHz) would fold to 8,050 Hz
        val n = 22_050 // one second, 2000 whole periods
        val x = DoubleArray(n) { SoftSfx.triangle(2 * Math.PI * 2000.0 * it / rate, 2000.0) }
        fun mag(hz: Double): Double {
            var re = 0.0; var im = 0.0
            for (i in x.indices) { val a = 2 * Math.PI * hz * i / rate; re += x[i] * Math.cos(a); im += x[i] * Math.sin(a) }
            return 2 * Math.hypot(re, im) / n
        }
        assertTrue(mag(2000.0) > 0.7)
        assertTrue("folded 7th harmonic: ${mag(8050.0)}", mag(8050.0) < 1e-3)
        assertTrue("9th harmonic folded: ${mag(4050.0)}", mag(4050.0) < 1e-3)
    }
}
