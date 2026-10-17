package com.husarp.ankigo.ui.movefx

import android.graphics.Canvas
import kotlin.math.roundToLong

/**
 * One running move animation: the [FxScript] a look filled, drawn by an [FxRenderer], on the arena's effect layer.
 * [draw] gets real ms; the animation runs in mockup ms = real ms x [speed]. Poses go to the [stage] only; [end] (also when the
 * animation finishes by itself) puts both sprites and the background back. They are put back ONCE, at [doneMs]: from then on the
 * look never touches the sprites or the background again (later poses, filters and jolts are dropped), so the event that starts
 * next may animate them while the particles still fade; [end] after that restores nothing.
 */
internal class MoveFxPlayer(
    private val stage: MoveFxStage,
    private val script: FxScript,
    private val times: FxTimes,
    private val speed: Float,
    makeRenderer: () -> FxRenderer,
) : MoveFxEffect {
    private val renderer by lazy(LazyThreadSafetyMode.NONE, makeRenderer)
    private var ended = false

    override val impactMs: Long = (times.impact / speed).roundToLong()
    override val doneMs: Long = (times.done / speed).roundToLong()

    /** Real ms after which nothing is left to draw. */
    val totalMs: Long get() = (times.total / speed).roundToLong()

    override fun draw(canvas: Canvas, t: Float): Boolean {
        if (ended) return false
        if (!step(t * speed)) { finish(); return false }
        renderer.draw(canvas, script)
        return true
    }

    /**
     * Runs the script up to [ms] mockup ms (no canvas needed). At [FxTimes.done] the stage is released: a frame that jumps over it
     * first runs the script exactly up to it (the last poses land at rest), releases, and only then runs on, so no overdue pose
     * callback reaches a sprite another animation owns by now. False when the animation is over.
     */
    internal fun step(ms: Float): Boolean {
        if (ms >= times.total) return false
        if (!script.stageReleased && ms >= times.done) {
            script.advance(times.done)
            script.releaseStage()
        }
        script.advance(ms)
        return true
    }

    override fun end() = finish()

    private fun finish() {
        if (ended) return
        ended = true
        script.releaseStage()
    }
}
