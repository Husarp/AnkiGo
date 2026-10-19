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

    @Test fun thereAre35Sounds() = assertEquals(35, SoftSound.entries.size)

    @Test fun everySoundRendersWithinLimits() {
        for (id in SoftSound.entries) {
            val s = SoftSfx.samples(id)
            assertTrue("$id empty", s.isNotEmpty())
            val p = peak(s)
            val low = when (id) { SoftSound.TAB, in tabLike -> 0.10..0.16; SoftSound.MENU -> 0.17..0.24; SoftSound.XP_ABSORB -> 0.085..0.105; else -> 0.38..0.66 }
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

    /** R9-W4-A: the answer buttons and Show answer are the tab tick, sample for sample. */
    private val tabLike = listOf(SoftSound.SHOW_ANSWER, SoftSound.AGAIN, SoftSound.HARD, SoftSound.GOOD, SoftSound.EASY)

    @Test fun allSoundsAreDifferent() {
        val seen = HashMap<List<Short>, SoftSound>()
        for (id in SoftSound.entries - tabLike.toSet()) {
            val key = SoftSfx.samples(id).toList()
            val other = seen.put(key, id)
            assertTrue("$id equals $other", other == null)
        }
    }

    @Test fun answerButtonsAndShowAnswerPlayExactlyTheTabTick() {
        val tab = SoftSfx.samples(SoftSound.TAB)
        for (b in tabLike) assertArrayEquals("$b", tab, SoftSfx.samples(b))
    }

    /** Share of the signal's energy above [hz], by a first-order high-pass of the samples (rough, but enough to tell a tick from a beep). */
    private fun highShare(s: ShortArray, hz: Double): Double {
        val a = Math.exp(-2 * Math.PI * hz / rate)
        var y = 0.0; var xp = 0.0; var hi = 0.0; var all = 0.0
        for (v in s) { val x = v / 32767.0; y = a * (y + x - xp); xp = x; hi += y * y; all += x * x }
        return hi / all
    }

    @Test fun tabTickIsTenDbUnderItsOldLevelAndTheAnswerButtonsShareIt() {
        val tab = peak(SoftSfx.samples(SoftSound.TAB))
        assertTrue("tab $tab", tab <= 0.42 * 0.32) // the old level 0.42, minus 10 dB
        for (b in tabLike) assertEquals("tab $tab vs $b", tab, peak(SoftSfx.samples(b)), 0.0)
        assertTrue(peak(SoftSfx.samples(SoftSound.MENU)) > tab)
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

    // ---- R9-C3: the XP orb "absorb" pling ----

    /** Magnitude of the signal's component at [hz] (single-bin DFT, normalised by the length). */
    private fun mag(s: ShortArray, hz: Double): Double {
        var re = 0.0; var im = 0.0
        for (i in s.indices) { val a = 2 * Math.PI * hz * i / rate; re += s[i] * Math.cos(a); im += s[i] * Math.sin(a) }
        return 2 * Math.hypot(re, im) / s.size
    }

    @Test fun xpAbsorbIsTheGlassPingTenDbUnderThePage() {
        val s = SoftSfx.samples(SoftSound.XP_ABSORB)
        assertEquals("peak", 0.095, peak(s), 0.002)          // the page's XP-D peak 0.30, minus 10 dB
        assertEquals("length", 0.32, s.size.toDouble() / rate, 0.01)
        assertTrue("quieter than the hit", peak(s) < peak(Sfx.samples(SoundId.HIT)) / 4)
        // energy near 2093 Hz dominates; the 2.76 x partial (5777 Hz) is weaker; nothing at an octave below or above
        val main = mag(s, 2093.0)
        assertTrue("main $main vs bell ${mag(s, 2093.0 * 2.76)}", main > 2 * mag(s, 2093.0 * 2.76))
        assertTrue("octave up ${mag(s, 4186.0)}", main > 10 * mag(s, 4186.0))
        assertTrue("octave down ${mag(s, 1046.5)}", main > 10 * mag(s, 1046.5))
    }

    @Test fun theGlassPartialIsReallyInTheXpPing() {
        // the 2.76 x f partial (5777 Hz): relative 0.15, tau 40 ms. Compare the sound with and without it, so a fundamental's leakage cannot pass for it.
        val withP = SoftSfx.xpAbsorbSamples(true)
        val without = SoftSfx.xpAbsorbSamples(false)
        val f = 2093.0 * 2.76
        fun win(s: ShortArray, from: Double, to: Double) = s.copyOfRange((from * rate).toInt(), (to * rate).toInt())
        val head = win(withP, 0.0, 0.02); val headNo = win(without, 0.0, 0.02)
        val ratio = mag(head, f) / mag(head, 2093.0)
        val ratioNo = mag(headNo, f) / mag(headNo, 2093.0)
        assertTrue("partial/main in the first 20 ms $ratio (without: $ratioNo)", ratio in 0.08..0.2)
        assertTrue("with $ratio vs without $ratioNo", ratio > 5 * ratioNo)
        // it decays (tau 40 ms) and is over by 0.15 s
        val mid = win(withP, 0.06, 0.08)
        assertTrue("decay ${mag(mid, f)} vs ${mag(head, f)}", mag(mid, f) < 0.45 * mag(head, f))
        val tail = win(withP, 0.2, 0.3)
        assertTrue("no partial in the tail ${mag(tail, f)}", mag(tail, f) < 0.02 * mag(head, 2093.0))
    }

    @Test fun buttonsAreShort() {
        for (id in buttons) assertTrue("$id", SoftSfx.samples(id).size.toDouble() / rate <= 0.09)
    }

    /** Writes every sound as a WAV so it can be listened to (not an assertion). */
    @Test fun writeWavsForListening() {
        val dir = File("build/sfx-r5").also { it.mkdirs() }
        for (id in SoftSound.entries) File(dir, "${id.name.lowercase()}.wav").writeBytes(Sfx.wav(SoftSfx.samples(id)))
        assertEquals(35, dir.listFiles { f -> f.extension == "wav" }!!.size)
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
