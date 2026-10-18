package com.husarp.ankigo.ui

import com.husarp.ankigo.gfx.SpriteView
import com.husarp.ankigo.ui.movefx.Joltable
import com.husarp.ankigo.ui.movefx.MoveFxStage

/**
 * The arena as one attack animation sees it (R7-05): the two sprite centres, the pixel unit, and the sprites' poses. It
 * poses the SpriteViews themselves (translation, scale about the bottom centre, colour matrix) on top of their rest pose, which is
 * always translation 0 / scale 1 when a move starts (the event before has finished or been cleared). [restore] puts that back.
 * [turning] = our sprite's swipe-turn squash runs: its scaleX belongs to that animator, so the stage leaves it alone.
 * A stage lives for one move: the first [restore] RELEASES it, and from then on [pose], [jolt] and [restore] do nothing, so a
 * late frame or a late end of the move can never reset a sprite that the next event (a faint, an entrance) is animating.
 */
class ArenaMoveStage(
    override val attackerX: Float,
    override val attackerY: Float,
    override val targetX: Float,
    override val targetY: Float,
    override val unit: Float,
    private val attacker: SpriteView,
    private val target: SpriteView,
    private val background: () -> Any?,
    private val turning: (SpriteView) -> Boolean = { false },
) : MoveFxStage {

    /** True after the first [restore]: the sprites belong to whatever animates them next. */
    var released = false
        private set

    override fun pose(attacker: Boolean, dx: Float, dy: Float, sx: Float, sy: Float, matrix: FloatArray?) {
        if (released) return
        val s = if (attacker) this.attacker else target
        s.translationX = dx
        s.translationY = dy
        s.pivotX = s.width / 2f
        s.pivotY = s.height.toFloat()
        if (!turning(s)) s.scaleX = sx
        s.scaleY = sy
        s.fxMatrix = matrix
    }

    override fun jolt(dy: Float) {
        if (released) return
        (background() as? Joltable)?.jolt = dy
    }

    override fun restore() {
        if (released) return
        released = true
        for (s in arrayOf(attacker, target)) {
            s.translationX = 0f
            s.translationY = 0f
            if (!turning(s)) s.scaleX = 1f
            s.scaleY = 1f
            s.fxMatrix = null
        }
        (background() as? Joltable)?.jolt = 0f
    }

    /** Pure geometry and timing of the wiring (JVM-tested). */
    object Geo {
        /** The mockup's stage distance between the attacker's centre (140, 190) and the target's (350, 105). */
        const val STAGE_DISTANCE = 226f
        /** The mockup's target sprite height, in stage units. */
        const val STAGE_SPRITE_H = 92f

        /**
         * Pixels per stage unit: the distance between the two centres over [STAGE_DISTANCE], kept within 0.7 to 1.4 times
         * (target drawn height / [STAGE_SPRITE_H]) so a very wide or tall arena makes neither huge nor tiny effects.
         */
        fun unit(ax: Float, ay: Float, tx: Float, ty: Float, targetDrawnH: Float): Float {
            val raw = kotlin.math.hypot(tx - ax, ty - ay) / STAGE_DISTANCE
            val ref = targetDrawnH / STAGE_SPRITE_H
            if (ref <= 0f) return raw.coerceAtLeast(0.01f)
            return raw.coerceIn(0.7f * ref, 1.4f * ref)
        }

        /**
         * Centre of the DRAWN image of a sprite view at ([holderLeft], [holderTop]) of the arena, [viewW] x [viewH] big, whose image
         * ([drawnH] high) SpriteView draws bottom-centred. [bottomInset] = how far the image reaches below the view's bottom when the
         * feet are anchored ([SpriteView.drawnBottomInset]; 0 otherwise). Returns (x, y); no bob or pose translation is counted.
         */
        fun centre(holderLeft: Float, holderTop: Float, viewW: Float, viewH: Float, drawnH: Float, bottomInset: Float = 0f): Pair<Float, Float> =
            Pair(holderLeft + viewW / 2f, holderTop + viewH + bottomInset - drawnH / 2f)

        /**
         * How long the event player waits after an animated attack: the look's own end, but at least [impactMs] plus 500 ms of the
         * pace [factor] (the floats and the HP bar need that long after the hit).
         */
        fun waitMs(doneMs: Long, impactMs: Long, factor: Double): Long =
            maxOf(doneMs, impactMs + maxOf(1L, (500 * factor).toLong()))
    }
}
