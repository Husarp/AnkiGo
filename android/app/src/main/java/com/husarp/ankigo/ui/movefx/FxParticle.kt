package com.husarp.ankigo.ui.movefx

import kotlin.math.atan2

/** Draws a particle by hand (the mockup's `fn`): [size] in px, [life] 0..1, drawn at the origin of [c] (already moved and rotated). */
fun interface FxParticleDraw {
    fun draw(c: FxCanvas, size: Float, life: Float, ms: Float)
}

/** Draws a proc: [p] = progress 0..1 of the proc's own time span, [ms] = mockup ms since the animation began. */
fun interface FxDraw {
    fun draw(c: FxCanvas, p: Float, ms: Float)
}

/** A drawing function active from [s] to [e] mockup ms; layer 0 draws under the particles, layer 1 over them. */
class FxProc(@JvmField val s: Float, @JvmField val e: Float, @JvmField val layer: Int, @JvmField val draw: FxDraw)

/**
 * One particle (the mockup's `P()`), pure maths: everything is in pixels and seconds of mockup time, filled in by
 * [FxScript.particle] (which takes stage units). Position = start + v t + a t^2 / 2; size and alpha fade linearly over [life].
 */
class FxParticle(
    /** Art key ([FxArt]: a Kenney texture name, "ps:<name>", "drop", ...) or null when [pix] / [draw] is used. */
    @JvmField val img: String?,
    /** Tint (RGB multiplied into the texture), 0 = none. */
    @JvmField val tint: Int,
    @JvmField val t0: Float, @JvmField val life: Float,
    @JvmField val x0: Float, @JvmField val y0: Float,
    @JvmField val vx: Float, @JvmField val vy: Float, @JvmField val ax: Float, @JvmField val ay: Float,
    @JvmField val s0: Float, @JvmField val s1: Float,
    @JvmField val a0: Float, @JvmField val a1: Float,
    @JvmField val rot: Float, @JvmField val vrot: Float,
    /** Rotate along the velocity instead of spinning. */
    @JvmField val follow: Boolean,
    /** Texture drawn [stretch] times longer and 1/sqrt(stretch) as wide (droplets). */
    @JvmField val stretch: Float,
    @JvmField val add: Boolean,
    /** A square pixel bit cycling through these colours; null = not a pixel bit. */
    @JvmField val pix: IntArray?,
    @JvmField val draw: FxParticleDraw?,
) {
    /** The renderer's cached bitmap for [img] (so a frame does no lookup). */
    @JvmField var cache: Any? = null

    fun alive(ms: Float): Boolean = ms - t0 < life

    /** 0..1 over the particle's life. */
    fun progress(ms: Float): Float = (ms - t0) / life

    private fun secs(ms: Float) = (ms - t0) / 1000f

    fun x(ms: Float): Float { val t = secs(ms); return x0 + vx * t + .5f * ax * t * t }
    fun y(ms: Float): Float { val t = secs(ms); return y0 + vy * t + .5f * ay * t * t }
    fun size(ms: Float): Float = FxMath.lerp(s0, s1, progress(ms))
    fun alpha(ms: Float): Float = FxMath.clamp01(FxMath.lerp(a0, a1, progress(ms)))

    /** Rotation in radians: along the velocity for [follow], else the start angle plus the spin. */
    fun angle(ms: Float): Float {
        val t = secs(ms)
        return if (follow) atan2(vy + ay * t, vx + ax * t) else rot + vrot * t
    }
}
