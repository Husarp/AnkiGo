package com.husarp.ankigo.ui.movefx

import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The geometry of one move animation: the mockup's 480 x 270 stage mapped onto the real arena (pure, no android.*).
 *
 * Looks think in **mockup stage units**, exactly like docs/design/attack-anims-v2/v2-fx.js, where the attacker is on the left
 * and the target up on the right. This class turns that into pixels of the effect layer, for either side attacking:
 *  - A point "relative to the attacker" is the attacker's centre plus (dx x [dir] x [unit], dy x [unit]); "relative to the target"
 *    likewise from the target's centre ([fromAttacker], [fromTarget]); a point on the way is between the two centres plus an
 *    offset ([between]). JS `[AX + 55, AY - 28]` is `fromAttacker(55f, -28f)`, `[DX - 12, DY + 4]` is `fromTarget(-12f, 4f)`,
 *    `lerp(AX, DX, f)` is `between(f, 0f, 0f)`.
 *  - [dir] is +1 when the target is right of the attacker, -1 when it is left (the wild one attacks to the left). Only
 *    left/right mirrors: "up" stays up (gravity, bolts from the sky, dirt flying up), so y offsets are never mirrored.
 *  - Lengths, sizes, speeds and accelerations are stage units: multiply with [unit] for pixels ([len]). A velocity or offset that
 *    points "toward the target" is positive x in the mockup frame; [vx] / [x] flip it with [dir].
 */
class FxSpace(
    val attackerX: Float, val attackerY: Float,
    val targetX: Float, val targetY: Float,
    /** Pixels per mockup stage unit. */
    val unit: Float,
) {
    /** +1 when the target is right of the attacker (or level), -1 when it is left. */
    val dir: Float = if (targetX >= attackerX) 1f else -1f

    /** Centre-to-centre vector toward the target in mockup frame units: x positive toward the target (the mockup's `DX - AX` = 210), y down (`DY - AY` = -85). */
    val towardX: Float get() = (targetX - attackerX) * dir / unit
    val towardY: Float get() = (targetY - attackerY) / unit

    /** Angle (radians, screen axes) of the line attacker -> target: for bitmaps that point right and must point along the path. */
    val pathAngle: Float get() = atan2(targetY - attackerY, targetX - attackerX)

    /** Distance attacker -> target in stage units. */
    val pathLength: Float get() = hypot(targetX - attackerX, targetY - attackerY) / unit

    /** An x offset of [v] stage units in the mockup frame (toward the target = positive) as pixels on screen. */
    fun x(v: Float): Float = dir * v * unit
    /** A y offset of [v] stage units (down = positive) as pixels. */
    fun y(v: Float): Float = v * unit
    /** A length / size / speed of [v] stage units as pixels (no mirroring). */
    fun len(v: Float): Float = v * unit

    /** The attacker's centre plus (dx, dy) stage units, dx in the mockup frame. */
    fun fromAttacker(dx: Float, dy: Float) = FxPt(attackerX + x(dx), attackerY + y(dy))
    /** The target's centre plus (dx, dy) stage units, dx in the mockup frame. */
    fun fromTarget(dx: Float, dy: Float) = FxPt(targetX + x(dx), targetY + y(dy))
    /** The point [f] of the way (0 = attacker's centre, 1 = target's) plus (dx, dy) stage units. */
    fun between(f: Float, dx: Float, dy: Float) = FxPt(betweenX(f, dx), betweenY(f, dy))
    fun betweenX(f: Float, dx: Float): Float = attackerX + (targetX - attackerX) * f + x(dx)
    fun betweenY(f: Float, dy: Float): Float = attackerY + (targetY - attackerY) * f + y(dy)

    /**
     * One art pixel of pixel-art bitmaps in whole device pixels (at least 1), for a size factor [k]: the mockup's `artPx`
     * (1.5 stage units x k; the mockup keeps it at 2 or more, but in a small arena that makes the art far too big). Draw pixel
     * art at a multiple of this with filtering off so its pixels stay square.
     */
    fun artPixels(k: Float = 1f): Int = max(1, (FxPixelArt.ART_PX * unit * k).roundToInt())

    companion object {
        /** The mockup's stage and the two sprite centres. */
        const val STAGE_W = 480f
        const val STAGE_H = 270f
        const val AX = 140f
        const val AY = 190f
        const val DX = 350f
        const val DY = 105f
    }
}
