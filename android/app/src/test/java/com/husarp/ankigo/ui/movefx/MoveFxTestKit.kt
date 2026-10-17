package com.husarp.ankigo.ui.movefx

/** A stage that records what the animation did to it (pure, no views). Mockup layout at [unit] px per unit; [reverse] = the wild one attacks. */
class RecStage(unitPx: Float = 2f, reverse: Boolean = false) : MoveFxStage {
    class Pose(val attacker: Boolean, val dx: Float, val dy: Float, val sx: Float, val sy: Float, val matrix: FloatArray?)

    override val unit: Float = unitPx
    override val attackerX = (if (reverse) FxSpace.DX else FxSpace.AX) * unitPx
    override val attackerY = (if (reverse) FxSpace.DY else FxSpace.AY) * unitPx
    override val targetX = (if (reverse) FxSpace.AX else FxSpace.DX) * unitPx
    override val targetY = (if (reverse) FxSpace.AY else FxSpace.DY) * unitPx

    val poses = ArrayList<Pose>()
    var jolts = 0
    var restores = 0
    /** How many poses / jolts had been made when [restore] ran (-1 = not yet): anything after it touched sprites that were handed back. */
    var posesAtRestore = -1
    var joltsAtRestore = -1

    override fun pose(attacker: Boolean, dx: Float, dy: Float, sx: Float, sy: Float, matrix: FloatArray?) { poses += Pose(attacker, dx, dy, sx, sy, matrix) }
    override fun jolt(dy: Float) { jolts++ }
    override fun restore() { if (restores++ == 0) { posesAtRestore = poses.size; joltsAtRestore = jolts } }

    fun last(attacker: Boolean): Pose? = poses.lastOrNull { it.attacker == attacker }
    fun count(attacker: Boolean): Int = poses.count { it.attacker == attacker }
}

val TEST_PALETTE = FxPalette(0xFF6D6B4E.toInt(), 0xFFE8E4C8.toInt(), 0xFFA8A77A.toInt(), 0xFFFFFFFF.toInt())

fun plan(look: MoveLook = MoveLook.TACKLE, kit: FxKit = FxKit.NORMAL, k: Float = 1f) = MoveFxPlan(look, k, kit, TEST_PALETTE)
