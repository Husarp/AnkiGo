package com.husarp.ankigo.sound

import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Even cries (round 6, item 30): the cry files come at very different loudness and some are near full scale, which made the
 * entrance of a wild Pokémon pierce through the soft battle sounds. Every cry is measured once (peak + gated RMS of its decoded
 * 16-bit samples) and gets a gain so that it plays at [TARGET_RMS_DB] with the peak under [PEAK_CEILING_DB]. Pure maths; the
 * decoding lives in [CryDecoder]. SoundPool can only play at volume 1.0 or less, so a cry never gets louder than its file.
 */
object CryLevel {
    /** Bump when the maths or the targets change: the stored gains are then measured again. */
    const val VERSION = 1

    /**
     * Gain used until a cry is measured. The files are hot (RMS about -8 to -14 dBFS, peaks at full scale), so reaching the
     * target takes a gain of about 0.25 to 0.5; this sits a little above that, still far under the old plain 1.0.
     */
    const val DEFAULT_GAIN = 0.3f

    /**
     * The loudness every cry is brought to, as dBFS of the RMS of its non-silent part. The soft battle sounds sit at -9 to
     * -20 dBFS (median -16.8; the weakest hit -19.5), but a cry is broadband and sounds louder at the same RMS, so it goes at the
     * quiet end: -20 dBFS, and with [ENTRANCE_FACTOR] -20.9 dBFS, under the weakest battle hit. CryLevelTest pins this.
     */
    const val TARGET_RMS_DB = -20.0

    /** No cry peaks above this, whatever its RMS (a spiky cry gets a lower gain). */
    const val PEAK_CEILING_DB = -6.0

    /** The entrance factor: the cry plays at this share of the cry channel volume (it was 0.9 before the levelling too). */
    const val ENTRANCE_FACTOR = 0.9f

    /** Samples under this (1 % of full scale, -40 dBFS) are silence between the sounds and do not count for the RMS. */
    private const val GATE = 328

    /** Gains outside this range mean a broken measurement: fall back to the default. */
    const val MIN_GAIN = 0.03f
    const val MAX_GAIN = 1.0f

    fun db(x: Double) = 20 * log10(x)
    fun lin(db: Double) = 10.0.pow(db / 20)

    /** Running peak and RMS of a stream of 16-bit samples (full scale = 32768). */
    class Stats {
        private var peak = 0
        private var sumSq = 0.0
        private var loud = 0L

        fun add(s: ShortArray, count: Int = s.size) {
            for (i in 0 until min(count, s.size)) {
                val a = abs(s[i].toInt())
                if (a > peak) peak = a
                if (a >= GATE) { sumSq += a.toDouble() * a; loud++ }
            }
        }

        /** Peak as a fraction of full scale. */
        val peakLin get() = peak / 32768.0

        /** RMS of the samples over the gate as a fraction of full scale; 0 when there are none. */
        val rmsLin get() = if (loud == 0L) 0.0 else sqrt(sumSq / loud) / 32768.0

        fun gain() = gainFor(peakLin, rmsLin)
    }

    /** RMS of the non-silent part of [s], fraction of full scale (the same measure the cries use). */
    fun rms(s: ShortArray) = Stats().also { it.add(s) }.rmsLin

    /** The gain that brings a cry with [peakLin] and [rmsLin] (fractions of full scale) to the target; [DEFAULT_GAIN] when nothing was measured. */
    fun gainFor(peakLin: Double, rmsLin: Double): Float {
        if (peakLin <= 0.0 || rmsLin <= 0.0 || peakLin.isNaN() || rmsLin.isNaN()) return DEFAULT_GAIN
        val byRms = lin(TARGET_RMS_DB) / rmsLin
        val byPeak = lin(PEAK_CEILING_DB) / peakLin
        return min(min(byRms, byPeak), MAX_GAIN.toDouble()).toFloat().let { max(it, MIN_GAIN) }
    }

    /** The volume a cry plays at: the cry channel volume, the cry's own gain and the entrance factor. */
    fun volume(channelVolume: Float, gain: Float) = (channelVolume * gain * ENTRANCE_FACTOR).coerceIn(0f, 1f)
}
