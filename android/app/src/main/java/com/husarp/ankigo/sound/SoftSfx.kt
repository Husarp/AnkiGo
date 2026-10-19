package com.husarp.ankigo.sound

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The round-5 sounds (PLAN 39 + 84, pick 39A "soft"): one entry per recipe of docs/design/round5.html (S39.REC).
 * Names are the contract between the sound plumbing ([SoundId], [SoundRules]) and the synth below.
 */
enum class SoftSound {
    // battle
    MISS, STAT_UP, STAT_DOWN, CHARGE,
    // status inflicted and its tick
    BURN, BURN_TICK, POISON, POISON_TICK, PARALYSIS, SLEEP, FREEZE,
    // Poké Ball
    THROW, WOBBLE, BREAK_FREE,
    // moments
    SHINY, FLED, EVO_START, EVO_DONE, LEARNED, ITEM, CASH, BADGE, HEALED, SENT_OUT, GOAL, XP_ABSORB,
    // buttons (PLAN 84)
    SHOW_ANSWER, AGAIN, HARD, GOOD, EASY, TAB, MENU, BUY, BALL_BTN,
}

/**
 * Soft synthesized sounds (sine/triangle tones with a soft second layer, filtered noise, short fades), pure Kotlin,
 * deterministic, 22,050 Hz mono 16-bit like [Sfx]. A port of the "soft" renderer of the mockup (S39.mkP / S39.REC),
 * made of plain maths only: nothing is bundled and no game audio is copied.
 */
object SoftSfx {
    private const val RATE = Sfx.RATE
    private const val NYQ_LIMIT = 10_800.0 // partials above this are skipped (Nyquist is 11,025 Hz)
    private const val LP_SOFT = 5_500.0    // the fixed low-pass after band/high-pass noise
    private const val TRI_MAX_K = 51      // 1/51² is under -68 dB: harmonics beyond that are not worth the sin() calls
    private const val MAX_S = 0.7          // the longest sound is 0.6 s; the mixing buffer has room to spare

    /** Odd harmonics of a triangle of fundamental [f] that stay below [NYQ_LIMIT] (at least the fundamental; capped, 1/k² is tiny up there). */
    internal fun triangleHarmonics(f: Double): Int = max(1, min(TRI_MAX_K, ((NYQ_LIMIT / f).toInt() + 1) / 2 * 2 - 1))

    /**
     * Band-limited triangle at phase [ph] (radians) for fundamental [f] Hz: the sum of odd harmonics, 1/k² with alternating sign,
     * only those below [NYQ_LIMIT], so nothing folds back into the audible band. Peak 1, like the plain triangle it replaces.
     */
    internal fun triangle(ph: Double, f: Double): Double {
        val top = triangleHarmonics(f)
        var sum = 0.0
        var k = 1
        var sign = 1.0
        while (k <= top && k * f < NYQ_LIMIT) {
            sum += sign * sin(k * ph) / (k.toDouble() * k)
            sign = -sign
            k += 2
        }
        return sum * (8 / (PI * PI))
    }

    fun samples(s: SoftSound): ShortArray {
        val p = Pen()
        recipe(p, s)
        return p.finish(level(s), button(s))
    }

    /** The XP-D glass ping; [partial] = false leaves out the 2.76 x f partial (a test seam, so a test can prove the partial is really in the sound). */
    private fun xpAbsorb(p: Pen, partial: Boolean) {
        p.ping(0.0, 2093.0, 1.0, .003, .110, .32)
        if (partial) p.ping(0.0, 2093.0 * 2.76, 0.15, .002, .040, .15)
    }

    internal fun xpAbsorbSamples(partial: Boolean): ShortArray {
        val p = Pen()
        xpAbsorb(p, partial)
        return p.finish(level(SoftSound.XP_ABSORB), false)
    }

    private fun button(s: SoftSound) = s.ordinal >= SoftSound.SHOW_ANSWER.ordinal

    /** Peak as a fraction of full scale. Battle/moment sounds sit near today's HIT/CATCH; buttons are short and
     *  get another 40 % volume slider on top, so they are a bit higher; tab and menu taps are the softest (R7-X1: the tab tick is
     *  about 10 dB under its old level of 0.42, the menu tick 6 dB under 0.40, both under the answer clicks). */
    private fun level(s: SoftSound) = when (s) {
        SoftSound.TAB, SoftSound.SHOW_ANSWER, SoftSound.AGAIN, SoftSound.HARD, SoftSound.GOOD, SoftSound.EASY -> 0.13 // R9-W4-A: the answer buttons + Show answer are the tab tick
        SoftSound.MENU -> 0.20
        SoftSound.BUY -> 0.50
        SoftSound.XP_ABSORB -> 0.095  // R9-W4-A: the XP-D glass ping at 10 dB under the page's peak 0.30 (0.30 x 10^-0.5); well under the battle hits (HIT 0.70)
        SoftSound.BALL_BTN -> 0.42
        SoftSound.BURN_TICK, SoftSound.POISON_TICK -> 0.50
        else -> 0.60
    }

    /** Midi note number -> Hz. */
    private fun m(n: Int) = 440.0 * 2.0.pow((n - 69) / 12.0)

    // ------------------------------------------------------------------ recipes (S39.REC, soft only)

    private fun recipe(p: Pen, s: SoftSound) {
        when (s) {
            SoftSound.MISS -> { p.x(0.0, .22, .5, 'b', 1800.0, 500.0, 1.2); p.s(.02, 420.0, 260.0, .12, .3) }
            SoftSound.STAT_UP -> { p.n(0.0, m(72), .14, .6); p.n(.09, m(79), .24, .6) }
            SoftSound.STAT_DOWN -> { p.n(0.0, m(67), .14, .6); p.s(.09, m(62), m(57), .26, .6) }
            SoftSound.CHARGE -> { p.s(0.0, 200.0, 900.0, .5, .55, rise = true); p.x(0.0, .5, .3, 'b', 300.0, 2200.0, 2.0, rise = true) }
            SoftSound.BURN -> {
                p.x(0.0, .35, .4, 'l', 2600.0, 500.0); p.s(0.0, 150.0, 340.0, .3, .3)
                for (t in doubleArrayOf(.02, .07, .13, .2, .27)) p.x(t, .02, .35, 'b', 3000.0, 3000.0, 3.0)
            }
            SoftSound.BURN_TICK -> { p.x(0.0, .06, .4, 'b', 2500.0, 2500.0, 3.0); p.n(0.0, 180.0, .08, .3) }
            SoftSound.POISON -> { p.s(0.0, 260.0, 520.0, .12, .5); p.s(.13, 300.0, 600.0, .14, .5); p.s(.28, 220.0, 440.0, .17, .4) }
            SoftSound.POISON_TICK -> p.s(0.0, 300.0, 520.0, .09, .45)
            SoftSound.PARALYSIS -> {
                doubleArrayOf(1500.0, 900.0, 1800.0, 700.0, 2000.0, 800.0, 1600.0, 1000.0)
                    .forEachIndexed { i, f -> p.n(i * .03, f, .04, .35, tri = true) }
                p.x(0.0, .26, .22, 'b', 3500.0, 3500.0, 2.0)
            }
            SoftSound.SLEEP -> { p.n(0.0, m(76), .2, .45); p.n(.17, m(72), .2, .45); p.n(.34, m(69), .26, .45) }
            SoftSound.FREEZE -> {
                p.n(0.0, 1568.0, .26, .35, bell = true); p.n(.04, 2093.0, .26, .3, bell = true); p.n(.08, 2637.0, .3, .25, bell = true)
                p.x(0.0, .15, .18, 'h', 6000.0, 6000.0); p.s(.13, 300.0, 120.0, .12, .35)
            }
            SoftSound.THROW -> { p.x(0.0, .28, .45, 'b', 500.0, 2000.0, 1.5, rise = true); p.s(0.0, 300.0, 700.0, .2, .2, rise = true) }
            SoftSound.WOBBLE -> { p.s(0.0, 700.0, 420.0, .07, .5); p.x(0.0, .02, .2, 'b', 1200.0, 1200.0, 2.0) }
            SoftSound.BREAK_FREE -> { p.x(0.0, .05, .5, 'b', 900.0, 900.0, 1.0); p.s(.05, 200.0, 800.0, .18, .5); p.s(.21, 800.0, 300.0, .2, .4) }
            SoftSound.SHINY ->
                doubleArrayOf(1568.0, 2093.0, 2637.0, 3136.0, 2637.0, 3136.0)
                    .forEachIndexed { i, f -> p.n(i * .05, f, if (i == 5) .3 else .22, .3, bell = true) }
            SoftSound.FLED -> { p.x(0.0, .35, .3, 'b', 2500.0, 300.0, 1.2); p.s(0.0, 700.0, 300.0, .3, .4) }
            SoftSound.EVO_START -> { p.s(0.0, 300.0, 1200.0, .55, .4, rise = true); p.s(0.0, 303.0, 1212.0, .55, .4, rise = true) }
            SoftSound.EVO_DONE -> {
                intArrayOf(72, 76, 79).forEachIndexed { i, n -> p.n(i * .08, m(n), .15, .5) }
                for (n in intArrayOf(72, 76, 79, 84)) p.n(.28, m(n), .3, .2)
            }
            SoftSound.LEARNED -> { p.n(0.0, m(79), .12, .5); p.n(.09, m(84), .12, .5); p.n(.18, m(88), .24, .5) }
            SoftSound.ITEM -> { p.n(0.0, m(88), .12, .45); p.n(.1, m(95), .22, .45) }
            SoftSound.CASH -> { p.x(0.0, .02, .3, 'h', 5000.0, 5000.0); p.n(.02, m(83), .1, .5, bell = true); p.n(.08, m(88), .22, .5, bell = true) }
            SoftSound.BADGE -> {
                p.n(0.0, m(67), .12, .5); p.n(.1, m(72), .12, .5); p.n(.2, m(76), .12, .5); p.n(.3, m(79), .28, .5); p.n(.3, m(55), .28, .3)
            }
            SoftSound.HEALED -> { p.n(0.0, m(77), .18, .4); p.n(.1, m(81), .18, .4); p.n(.2, m(84), .3, .4) }
            SoftSound.SENT_OUT -> { p.x(0.0, .09, .45, 'l', 1500.0, 400.0); p.s(.03, 350.0, 800.0, .14, .45) }
            SoftSound.GOAL -> {
                intArrayOf(72, 79, 84, 88).forEachIndexed { i, n -> p.n(i * .08, m(n), .1, .5) }
                for (n in intArrayOf(79, 84, 88)) p.n(.34, m(n), .25, .25)
            }
            // R9-W4-A: XP-D "glass ping" (sounds.html XP-D row): sine 2093 Hz, 3 ms attack, tau 110 ms, 0.32 s; plus a plain sine partial at 2.76 x f, relative 0.15, 2 ms attack, tau 40 ms, 0.15 s
            SoftSound.XP_ABSORB -> xpAbsorb(p, true)
            // R9-W4-A: the answer buttons and Show answer sound exactly like the tab tick (own files and groups, so their volumes and switches stay)
            SoftSound.SHOW_ANSWER, SoftSound.AGAIN, SoftSound.HARD, SoftSound.GOOD, SoftSound.EASY -> p.tick(0.0, 480.0, .035, 1.0, 0.006)
            SoftSound.TAB -> p.tick(0.0, 480.0, .035, 1.0, 0.006) // a low, soft tick: pure sine, no octave layer, no sharp attack
            SoftSound.MENU -> p.tick(0.0, 620.0, .035, 1.0, 0.006)
            SoftSound.BUY -> { p.tick(0.0, 880.0, .04, .8, 0.004); p.tick(.04, 1320.0, .04, .8, 0.004) }
            SoftSound.BALL_BTN -> { p.x(0.0, .05, .12, 'b', 800.0, 1600.0, 0.8); p.tick(0.0, 560.0, .05, .9, 0.005) }
        }
    }

    // ------------------------------------------------------------------ the renderer (S39.mkP, style 'soft')

    private class Pen {
        private val buf = DoubleArray((MAX_S * RATE).toInt())
        private var end = 0
        private var burst = 0

        /** A tone gliding f0 -> f1 over [dur] seconds starting at [t]. [tri] = triangle instead of sine + octave. */
        fun s(t: Double, f0: Double, f1: Double, dur: Double, amp: Double, rise: Boolean = false, tri: Boolean = false, bell: Boolean = false, atk: Double = 0.004) {
            osc(t, f0, f1, dur, amp, rise, atk, tri, 1.0)
            if (!tri) osc(t, f0 * 2, f1 * 2, dur, amp * 0.25, rise, atk, false, 1.0) // the soft second layer
            if (bell) s(t, f0 * 2.76, f1 * 2.76, dur * 0.5, amp * 0.18) // a bell partial: plain sine, half as long
        }

        fun n(t: Double, f: Double, dur: Double, amp: Double, tri: Boolean = false, bell: Boolean = false, atk: Double = 0.004) =
            s(t, f, f, dur, amp, tri = tri, bell = bell, atk = atk)

        /** A short click at pitch [f] (default 45 ms). */
        fun ui(t: Double, f: Double, amp: Double, dur: Double = 0.045) = n(t, f, dur, amp * 0.8, atk = 0.002)

        /** A soft tick: one plain sine (no octave layer, so no high partials) with a gentle [atk]-second attack. */
        fun tick(t: Double, f: Double, dur: Double, amp: Double, atk: Double) = osc(t, f, f, dur, amp, false, atk, false, 1.0)

        /** R9-W4-A: a plain sine of [f] Hz with a linear [atk]-second attack, then an exponential decay exp(-(t-atk)/[tau]), for [dur] seconds. */
        fun ping(t: Double, f: Double, amp: Double, atk: Double, tau: Double, dur: Double) {
            if (f >= NYQ_LIMIT) return
            val start = (t * RATE).roundToInt()
            val n = (dur * RATE).roundToInt()
            var ph = 0.0
            for (i in 0 until n) {
                val idx = start + i
                if (idx >= buf.size) break
                val tt = i.toDouble() / RATE
                ph += 2 * PI * f / RATE
                val env = if (tt < atk) tt / atk else kotlin.math.exp(-(tt - atk) / tau)
                buf[idx] += sin(ph) * amp * env
                end = max(end, idx + 1)
            }
        }

        private fun osc(t: Double, f0: Double, f1: Double, dur: Double, amp: Double, rise: Boolean, atk: Double, tri: Boolean, scale: Double) {
            if (max(f0, f1) >= NYQ_LIMIT) return // above Nyquist: skip rather than alias
            val start = (t * RATE).roundToInt()
            val n = (dur * RATE).roundToInt()
            val a = amp * scale
            var ph = 0.0
            val ratio = f1 / f0
            for (i in 0 until n) {
                val idx = start + i
                if (idx >= buf.size) break
                val tt = i.toDouble() / RATE
                val f = if (f0 == f1) f0 else f0 * ratio.pow(tt / dur)
                ph += 2 * PI * f / RATE
                val w = if (tri) triangle(ph, f) else sin(ph)
                buf[idx] += w * envelope(tt, dur, a, rise, atk)
                end = max(end, idx + 1)
            }
        }

        /** Noise burst through a biquad; [kind] 'l' lowpass, 'b' bandpass, 'h' highpass; the cutoff glides f -> f1. */
        fun x(t: Double, dur: Double, amp: Double, kind: Char, f: Double, f1: Double = f, q: Double = 0.8, rise: Boolean = false) {
            val start = (t * RATE).roundToInt()
            val n = (dur * RATE).roundToInt()
            val qq = min(q, 1.1)
            val rnd = Lcg(0x2545F491 + 7919 * (burst++))
            val b1 = Biquad(); val b2 = Biquad()
            val post = kind != 'l'
            val ratio = f1 / f
            for (i in 0 until n) {
                val idx = start + i
                if (idx >= buf.size) break
                val tt = i.toDouble() / RATE
                if (i % 8 == 0) {
                    val fc = if (f == f1) f else f * ratio.pow(tt / dur)
                    b1.set(kind, fc, qq)
                    if (post && i == 0) b2.set('l', LP_SOFT, 1.0)
                }
                var v = b1.run(rnd.next())
                if (post) v = b2.run(v)
                buf[idx] += v * envelope(tt, dur, amp, rise, 0.004)
                end = max(end, idx + 1)
            }
        }

        private fun envelope(tt: Double, dur: Double, a: Double, rise: Boolean, atk: Double): Double {
            if (rise) {
                val peak = dur * 0.85
                return if (tt < peak) a * tt / peak else a * max(0.0, (dur - tt) / (dur - peak))
            }
            if (tt < atk) return a * tt / atk
            return a * 1e-4.pow((tt - atk) / max(1e-6, dur - atk))
        }

        fun finish(peak: Double, isButton: Boolean): ShortArray {
            val v = buf.copyOf(max(1, end))
            var top = 0.0
            for (x in v) top = max(top, abs(x))
            val sc = if (top > 0) peak / top else 0.0
            val fin = (0.002 * RATE).toInt()
            val fout = ((if (isButton) 0.004 else 0.010) * RATE).toInt()
            return ShortArray(v.size) { i ->
                var g = sc
                if (i < fin) g *= i.toDouble() / fin
                val left = v.size - 1 - i
                if (left < fout) g *= left.toDouble() / fout
                (v[i] * g * 32767).roundToInt().coerceIn(-32767, 32767).toShort()
            }
        }
    }

    /** White noise in -1..1 from a seeded LCG (deterministic). */
    private class Lcg(seed: Int) {
        private var s = seed
        fun next(): Double { s = s * 1664525 + 1013904223; return (s ushr 1) / 1073741824.0 - 1.0 }
    }

    /** RBJ cookbook biquad (Web Audio flavour: band-pass Q linear, low/high-pass Q in dB). */
    private class Biquad {
        private var b0 = 0.0; private var b1 = 0.0; private var b2 = 0.0; private var a1 = 0.0; private var a2 = 0.0
        private var x1 = 0.0; private var x2 = 0.0; private var y1 = 0.0; private var y2 = 0.0

        fun set(kind: Char, freq: Double, q: Double) {
            val fc = freq.coerceIn(20.0, RATE * 0.45)
            val w0 = 2 * PI * fc / RATE
            val c = cos(w0); val sn = sin(w0)
            val alpha = if (kind == 'b') sn / (2 * q) else sn / (2 * 10.0.pow(q / 20))
            val a0 = 1 + alpha
            when (kind) {
                'l' -> { b0 = (1 - c) / 2; b1 = 1 - c; b2 = (1 - c) / 2 }
                'h' -> { b0 = (1 + c) / 2; b1 = -(1 + c); b2 = (1 + c) / 2 }
                else -> { b0 = alpha; b1 = 0.0; b2 = -alpha }
            }
            b0 /= a0; b1 /= a0; b2 /= a0
            a1 = -2 * c / a0; a2 = (1 - alpha) / a0
        }

        fun run(x: Double): Double {
            val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1; x1 = x; y2 = y1; y1 = y
            return y
        }
    }
}
