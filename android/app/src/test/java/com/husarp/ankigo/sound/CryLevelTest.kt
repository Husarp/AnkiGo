package com.husarp.ankigo.sound

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

class CryLevelTest {
    /** A synthetic "cry": a sine of [amp] (fraction of full scale) with [silence] samples of nothing before and after. */
    private fun tone(amp: Double, n: Int = 20_000, silence: Int = 0): ShortArray {
        val out = ShortArray(n + 2 * silence)
        for (i in 0 until n) out[silence + i] = (amp * 32767 * sin(2 * PI * 440 * i / 22_050.0)).roundToInt().toShort()
        return out
    }

    private fun levelled(s: ShortArray): Pair<Double, Double> { // RMS and peak after the gain, as fractions of full scale
        val st = CryLevel.Stats().also { it.add(s) }
        val g = CryLevel.gainFor(st.peakLin, st.rmsLin)
        return st.rmsLin * g to st.peakLin * g
    }

    @Test fun aLoudCryIsBroughtDownToTheTarget() {
        val (rms, peak) = levelled(tone(0.95))
        assertEquals(CryLevel.TARGET_RMS_DB, CryLevel.db(rms), 0.1)
        assertTrue(peak <= CryLevel.lin(CryLevel.PEAK_CEILING_DB) + 1e-6)
    }

    @Test fun cryLoudnessesLandWithinOneDecibel() {
        val levels = listOf(0.95, 0.7, 0.5, 0.35).map { CryLevel.db(levelled(tone(it)).first) }
        assertTrue("$levels", levels.max() - levels.min() < 1.0)
    }

    @Test fun aQuietCryIsNeverBoostedPastFullVolume() {
        val st = CryLevel.Stats().also { it.add(tone(0.02)) }
        assertEquals(CryLevel.MAX_GAIN, CryLevel.gainFor(st.peakLin, st.rmsLin), 0f)
    }

    @Test fun aSpikyCryIsHeldByThePeakCeiling() {
        // a low hum with one near-full-scale click: the RMS says "raise it", the peak says no
        val s = tone(0.05)
        s[100] = 32000
        val st = CryLevel.Stats().also { it.add(s) }
        val g = CryLevel.gainFor(st.peakLin, st.rmsLin)
        assertEquals(CryLevel.lin(CryLevel.PEAK_CEILING_DB), st.peakLin * g, 1e-4)
        assertTrue(CryLevel.db(st.rmsLin * g) < CryLevel.TARGET_RMS_DB)
    }

    @Test fun aClippedFullScaleCryGetsTheSameLevelAsAnUnclippedOne() {
        val clipped = ShortArray(20_000) { i -> (if (sin(2 * PI * 300 * i / 22_050.0) >= 0) 32767 else -32768).toShort() } // a square wave
        val (rms, peak) = levelled(clipped)
        assertEquals(CryLevel.TARGET_RMS_DB, CryLevel.db(rms), 0.5)
        assertTrue(peak < 1.0)
    }

    @Test fun silenceAroundTheCryDoesNotChangeItsGain() {
        val bare = CryLevel.Stats().also { it.add(tone(0.9)) }
        val padded = CryLevel.Stats().also { it.add(tone(0.9, silence = 40_000)) }
        assertEquals(bare.gain(), padded.gain(), 0.002f)
    }

    @Test fun nothingMeasuredGivesTheDefault() {
        assertEquals(CryLevel.DEFAULT_GAIN, CryLevel.Stats().gain(), 0f)
        assertEquals(CryLevel.DEFAULT_GAIN, CryLevel.Stats().also { it.add(ShortArray(500)) }.gain(), 0f)
        assertEquals(CryLevel.DEFAULT_GAIN, CryLevel.gainFor(Double.NaN, 0.1), 0f)
        assertEquals(CryLevel.DEFAULT_GAIN, CryLevel.gainFor(0.5, 0.0), 0f)
    }

    @Test fun theGainStaysInsideItsLimits() {
        for ((p, r) in listOf(1.0 to 0.9, 0.5 to 0.001, 1.0 to 0.0005, 0.9 to 0.5)) {
            val g = CryLevel.gainFor(p, r)
            assertTrue("$p $r -> $g", g in CryLevel.MIN_GAIN..CryLevel.MAX_GAIN)
        }
    }

    @Test fun volumeIsChannelTimesGainTimesEntranceAndZeroStaysSilent() {
        assertEquals(0.7f * 0.4f * CryLevel.ENTRANCE_FACTOR, CryLevel.volume(0.7f, 0.4f), 1e-6f)
        assertEquals(0f, CryLevel.volume(0f, 0.9f), 0f)
        assertTrue(CryLevel.volume(1f, 1f) <= 1f)
    }

    @Test fun anEntranceCryIsNoLouderThanTheBattleSounds() {
        // Same slider settings for both: a levelled cry (target RMS, then the entrance factor) against the RMS of the battle sounds.
        val cry = CryLevel.TARGET_RMS_DB + CryLevel.db(CryLevel.ENTRANCE_FACTOR.toDouble())
        val battle = SoundId.entries.filter { it.group == SoundGroup.BATTLE }.associateWith { CryLevel.db(CryLevel.rms(Sfx.samples(it))) }
        val sorted = battle.values.sorted()
        val median = sorted[sorted.size / 2]
        assertTrue("cry $cry vs median $median", cry <= median)
        for (id in listOf(SoundId.HIT, SoundId.SUPER, SoundId.WEAK, SoundId.CATCH)) assertTrue("$id ${battle[id]} vs cry $cry", cry <= battle.getValue(id))
    }
}
