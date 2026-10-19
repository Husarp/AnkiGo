package com.husarp.ankigo.sound

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The sounds. [file] is the cache file name; bump the "-v1" when a sound is redesigned. [group] = the settings group that switches it.
 * The first six are the original battle sounds ([Sfx]); the rest carry the names of [SoftSound] (made by [SoftSfx]).
 */
enum class SoundId(val file: String, val group: SoundGroup) {
    HIT("hit-v1.wav", SoundGroup.BATTLE), SUPER("super-v1.wav", SoundGroup.BATTLE), WEAK("weak-v1.wav", SoundGroup.BATTLE),
    FAINT("faint-v1.wav", SoundGroup.BATTLE), CATCH("catch-v1.wav", SoundGroup.BATTLE), LEVEL_UP("levelup-v1.wav", SoundGroup.BATTLE),

    // battle
    MISS("miss-v1.wav", SoundGroup.BATTLE), CHARGE("charge-v1.wav", SoundGroup.BATTLE),
    THROW("throw-v1.wav", SoundGroup.BATTLE), WOBBLE("wobble-v1.wav", SoundGroup.BATTLE), BREAK_FREE("break_free-v1.wav", SoundGroup.BATTLE),
    FLED("fled-v1.wav", SoundGroup.BATTLE), SENT_OUT("sent_out-v1.wav", SoundGroup.BATTLE),
    // moments
    STAT_UP("stat_up-v1.wav", SoundGroup.MOMENTS), STAT_DOWN("stat_down-v1.wav", SoundGroup.MOMENTS),
    BURN("burn-v1.wav", SoundGroup.MOMENTS), BURN_TICK("burn_tick-v1.wav", SoundGroup.MOMENTS),
    POISON("poison-v1.wav", SoundGroup.MOMENTS), POISON_TICK("poison_tick-v1.wav", SoundGroup.MOMENTS),
    PARALYSIS("paralysis-v1.wav", SoundGroup.MOMENTS), SLEEP("sleep-v1.wav", SoundGroup.MOMENTS), FREEZE("freeze-v1.wav", SoundGroup.MOMENTS),
    SHINY("shiny-v1.wav", SoundGroup.MOMENTS), EVO_START("evo_start-v1.wav", SoundGroup.MOMENTS), EVO_DONE("evo_done-v1.wav", SoundGroup.MOMENTS),
    LEARNED("learned-v1.wav", SoundGroup.MOMENTS), ITEM("item-v1.wav", SoundGroup.MOMENTS), CASH("cash-v1.wav", SoundGroup.MOMENTS),
    BADGE("badge-v1.wav", SoundGroup.MOMENTS), HEALED("healed-v1.wav", SoundGroup.MOMENTS), GOAL("goal-v1.wav", SoundGroup.MOMENTS),
    // R9-C3: one orb of the XP animation landing (a reward sound, like the cash one)
    XP_ABSORB("xp_absorb-v2.wav", SoundGroup.MOMENTS),
    // buttons (PLAN 84)
    AGAIN("again-v2.wav", SoundGroup.ANSWER_BUTTONS), HARD("hard-v2.wav", SoundGroup.ANSWER_BUTTONS),
    GOOD("good-v2.wav", SoundGroup.ANSWER_BUTTONS), EASY("easy-v2.wav", SoundGroup.ANSWER_BUTTONS),
    SHOW_ANSWER("show_answer-v2.wav", SoundGroup.SHOW_ANSWER),
    TAB("tab-v2.wav", SoundGroup.MENUS), MENU("menu-v2.wav", SoundGroup.MENUS), BALL_BTN("ball_btn-v2.wav", SoundGroup.MENUS),
    BUY("buy-v2.wav", SoundGroup.SHOP);

    /** The soft recipe that makes this sound, or null for the six original battle sounds. */
    val soft: SoftSound? get() = SoftSound.entries.firstOrNull { it.name == name }
}

/**
 * Battle sounds made of plain maths (sine sweeps, filtered noise): nothing is bundled, no game audio is copied.
 * Pure Kotlin, deterministic: the same sound comes out byte for byte every time. 22,050 Hz, mono, 16-bit.
 */
object Sfx {
    const val RATE = 22_050
    private const val FADE_IN_S = 0.008
    private const val FADE_OUT_S = 0.010

    /** Peak of each sound as a fraction of full scale (all <= 0.8). The weak hit is the softest. */
    private fun level(id: SoundId) = when (id) {
        SoundId.HIT -> 0.70; SoundId.SUPER -> 0.75; SoundId.WEAK -> 0.42
        SoundId.FAINT -> 0.60; SoundId.CATCH -> 0.58; SoundId.LEVEL_UP -> 0.55
        else -> 0.5 // the soft sounds are leveled by SoftSfx itself
    }

    fun samples(id: SoundId): ShortArray {
        id.soft?.let { return SoftSfx.samples(it) }
        val b = Buf(when (id) {
            SoundId.HIT -> 0.24; SoundId.SUPER -> 0.40; SoundId.WEAK -> 0.16
            SoundId.FAINT -> 0.50; SoundId.CATCH -> 0.67; SoundId.LEVEL_UP -> 0.55
            else -> 0.1
        })
        when (id) {
            SoundId.HIT -> hit(b, 0.0, 1.0)
            SoundId.SUPER -> { hit(b, 0.0, 1.0); chirp(b, 0.10, 0.12, 520.0, 0.5); chirp(b, 0.19, 0.19, 780.0, 0.55) }
            SoundId.WEAK -> { thump(b, 0.0, 0.14, 110.0, 70.0, 0.9, 28.0); noise(b, 0.0, 0.03, 0.12, 90.0, 0.35) }
            SoundId.FAINT -> glide(b)
            SoundId.CATCH -> {
                for (k in 0 until 3) click(b, k * 0.10)
                chirp(b, 0.32, 0.12, 1318.5, 0.5) // E6
                chirp(b, 0.42, 0.25, 1760.0, 0.5) // A6
            }
            SoundId.LEVEL_UP -> {
                val notes = doubleArrayOf(523.25, 659.25, 783.99, 1046.5) // C5 E5 G5 C6
                notes.forEachIndexed { i, f -> chirp(b, i * 0.09, if (i == 3) 0.25 else 0.14, f, 0.5) }
            }
            else -> {}
        }
        return b.finish(level(id))
    }

    /** A complete WAV file (44-byte header + PCM). */
    fun wav(id: SoundId): ByteArray = wav(samples(id))

    fun wav(pcm: ShortArray): ByteArray {
        val data = pcm.size * 2
        val out = java.io.ByteArrayOutputStream(44 + data)
        fun str(s: String) = out.write(s.toByteArray(Charsets.US_ASCII))
        fun i32(v: Int) { out.write(v and 255); out.write(v shr 8 and 255); out.write(v shr 16 and 255); out.write(v shr 24 and 255) }
        fun i16(v: Int) { out.write(v and 255); out.write(v shr 8 and 255) }
        str("RIFF"); i32(36 + data); str("WAVE")
        str("fmt "); i32(16); i16(1); i16(1); i32(RATE); i32(RATE * 2); i16(2); i16(16)
        str("data"); i32(data)
        for (s in pcm) i16(s.toInt())
        return out.toByteArray()
    }

    // ------------------------------------------------------------------ the sounds

    /** Noise burst (80 ms) + thump sweep 140 -> 60 Hz. */
    private fun hit(b: Buf, at: Double, gain: Double) {
        noise(b, at, 0.08, 0.5 * gain, 45.0, 0.5)
        thump(b, at, 0.2, 140.0, 60.0, 0.9 * gain, 16.0)
    }

    /** Faint: a descending glide 600 -> 150 Hz over about 450 ms. */
    private fun glide(b: Buf) {
        val n = (0.45 * RATE).toInt()
        var phase = 0.0
        for (i in 0 until n) {
            val t = i.toDouble() / n
            val f = 600.0 * Math.pow(150.0 / 600.0, t)
            phase += 2 * PI * f / RATE
            val env = (1.0 - t * t) * min(1.0, i / (0.006 * RATE))
            b.add(i, (sin(phase) + 0.3 * sin(3 * phase)) * 0.55 * env)
        }
    }

    /** Sine sweep from [f0] to [f1] Hz with an exponential decay [k] (1/s). */
    private fun thump(b: Buf, at: Double, dur: Double, f0: Double, f1: Double, amp: Double, k: Double) {
        val start = (at * RATE).toInt()
        val n = (dur * RATE).toInt()
        var phase = 0.0
        for (i in 0 until n) {
            val t = i.toDouble() / n
            phase += 2 * PI * (f0 + (f1 - f0) * t) / RATE
            b.add(start + i, sin(phase) * amp * exp(-k * i / RATE) * min(1.0, i / (0.003 * RATE)))
        }
    }

    /** Soft tone with a second harmonic and a decaying tail (arpeggio notes, dings). */
    private fun chirp(b: Buf, at: Double, dur: Double, freq: Double, amp: Double) {
        val start = (at * RATE).toInt()
        val n = (dur * RATE).toInt()
        for (i in 0 until n) {
            val t = i.toDouble() / RATE
            val env = min(1.0, i / (0.004 * RATE)) * exp(-9.0 * t / dur)
            b.add(start + i, (sin(2 * PI * freq * t) + 0.25 * sin(4 * PI * freq * t)) * amp * env * 0.8)
        }
    }

    /** One wobble click of the ball: a very short 900 Hz blip with a little noise. */
    private fun click(b: Buf, at: Double) {
        val start = (at * RATE).toInt()
        val n = (0.022 * RATE).toInt()
        for (i in 0 until n) {
            val t = i.toDouble() / RATE
            val env = min(1.0, i / (0.002 * RATE)) * exp(-170.0 * t)
            b.add(start + i, sin(2 * PI * 900.0 * t) * 0.6 * env)
        }
        noise(b, at, 0.012, 0.1, 200.0, 0.4)
    }

    /** Low-passed noise (one-pole, [smooth] 0..1 = how much of the previous value stays) with a decay of [k] (1/s). Deterministic. */
    private fun noise(b: Buf, at: Double, dur: Double, amp: Double, k: Double, smooth: Double) {
        val start = (at * RATE).toInt()
        val n = (dur * RATE).toInt()
        var seed = 0x2545F491L + (at * 1000).toLong()
        var prev = 0.0
        for (i in 0 until n) {
            seed = (seed * 6364136223846793005L + 1442695040888963407L)
            val white = ((seed ushr 33).toInt() / 1073741824.0) - 1.0
            prev = prev * smooth + white * (1 - smooth)
            b.add(start + i, prev * 2.2 * amp * exp(-k * i / RATE) * min(1.0, i / (0.002 * RATE)))
        }
    }

    // ------------------------------------------------------------------ mixing

    private class Buf(seconds: Double) {
        private val v = DoubleArray((seconds * RATE).toInt())

        fun add(i: Int, x: Double) { if (i in v.indices) v[i] += x }

        /** Scales so the peak is [peak] of full scale, fades the edges (no clicks), rounds to 16 bit. */
        fun finish(peak: Double): ShortArray {
            var top = 0.0
            for (x in v) top = max(top, abs(x))
            val scale = if (top > 0) peak / top else 0.0
            val fin = (FADE_IN_S * RATE).toInt()
            val fout = (FADE_OUT_S * RATE).toInt()
            return ShortArray(v.size) { i ->
                var g = scale
                if (i < fin) g *= i.toDouble() / fin
                val left = v.size - 1 - i
                if (left < fout) g *= left.toDouble() / fout
                (v[i] * g * 32767.0).toInt().coerceIn(-32767, 32767).toShort()
            }
        }
    }
}
