package com.husarp.ankigo.ui

/**
 * The cross-fade between two battle backgrounds (round 8, pure, JVM-tested; [ArenaBackdrop] reads the clock and draws). The new
 * picture's opacity rises linearly over [DURATION_MS]; with reduced motion there is no fade, the new picture is simply there.
 */
internal object BackdropFade {
    const val DURATION_MS = 400L

    /** How long the fade lasts: 0 with reduced motion. */
    fun duration(reducedMotion: Boolean): Long = if (reducedMotion) 0L else DURATION_MS

    /** The new picture's opacity [elapsedMs] after the fade began: 0 at the start, 1 at the end (and always 1 with reduced motion). */
    fun alpha(elapsedMs: Long, reducedMotion: Boolean): Float {
        val d = duration(reducedMotion)
        if (d <= 0L || elapsedMs >= d) return 1f
        if (elapsedMs <= 0L) return 0f
        return elapsedMs.toFloat() / d
    }

    /** True while frames must still be drawn for the fade. */
    fun running(elapsedMs: Long, reducedMotion: Boolean): Boolean = alpha(elapsedMs, reducedMotion) < 1f

    /** [alpha] as the 0..255 a canvas layer takes. */
    fun alpha255(elapsedMs: Long, reducedMotion: Boolean): Int = (alpha(elapsedMs, reducedMotion) * 255f + 0.5f).toInt()

    /** What the outgoing side of a new fade is when a picture is applied while another fade is [fading]. */
    enum class Outgoing {
        /** The fade has not drawn a frame yet (or is still at opacity 0): the outgoing picture (or gradient) simply stays. */
        KEEP,

        /** The fade is part way: the blend on screen now is frozen into one bitmap and fades out. */
        SNAPSHOT,

        /** The fade is over (its last frame not drawn yet): the incoming picture is the outgoing one. */
        INCOMING,
    }

    /** Which [Outgoing] a picture applied during a fade gets; [started] = the fade has drawn its first frame, [elapsedMs] since then. */
    fun outgoingAt(started: Boolean, elapsedMs: Long): Outgoing {
        if (!started) return Outgoing.KEEP
        val a = alpha255(elapsedMs, false)
        return if (a <= 0) Outgoing.KEEP else if (a >= 255) Outgoing.INCOMING else Outgoing.SNAPSHOT
    }
}
