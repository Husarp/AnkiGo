package com.husarp.ankigo.ui.movefx

import android.content.Context

/**
 * The attack animation player (docs/design/attack-anims-v2/v2-fx.js ported). [create] builds the animation of a plan:
 *  1. [FxSpace] maps the mockup's stage onto the arena's [MoveFxStage],
 *  2. the plan's look fills an [FxScript] (timeline, particles, procs; random parts from the seed),
 *  3. a [MoveFxPlayer] runs it on the arena's effect layer ([com.husarp.ankigo.ui.ArenaFxView]).
 * To add a look: write `(FxScript) -> FxTimes` (see [TackleFx] and [FxScript]'s KDoc) and put it in the `when` below.
 */
object MoveFx {
    /**
     * Builds the animation of [plan] on [stage]. [react] = false: the target shows no reaction (no damage). [speed] 1 = normal,
     * 2 = the battle's fast pace (every time halved). [seed] drives the random parts. Null = nothing to play (no size yet).
     */
    fun create(ctx: Context, stage: MoveFxStage, plan: MoveFxPlan, react: Boolean, speed: Float, seed: Long): MoveFxEffect? {
        val script = script(stage, plan, react, seed) ?: return null
        val times = build(script, plan.look)
        return MoveFxPlayer(stage, script, times, if (speed > 0f) speed else 1f) { FxRenderer(ctx, script.space.unit) }
    }

    /** The script for [plan] on [stage] (no Android needed), null when the stage has no size yet. */
    internal fun script(stage: MoveFxStage, plan: MoveFxPlan, react: Boolean, seed: Long): FxScript? {
        if (stage.unit <= 0f) return null
        val space = FxSpace(stage.attackerX, stage.attackerY, stage.targetX, stage.targetY, stage.unit)
        return FxScript(space, plan, stage, react, seed)
    }

    /** Runs [look]'s builder on [script]; the registry of the looks. */
    internal fun build(script: FxScript, look: MoveLook): FxTimes = when (look) {
        MoveLook.TACKLE -> TackleFx.build(script)
        MoveLook.HIT_TEMPLATE -> HitTemplateFx.build(script)
        MoveLook.EMBER -> EmberFx.build(script) // R7-04
        MoveLook.WATER_JET -> WaterJetFx.build(script) // R7-04
        MoveLook.VINE_WHIP -> VineWhipFx.build(script) // R7-04
        MoveLook.ICE_BEAM -> IceBeamFx.build(script) // R7-04
        MoveLook.THUNDERBOLT -> ThunderboltFx.build(script) // R7-08
        MoveLook.PSYCHIC -> PsychicFx.build(script) // R7-08
        MoveLook.SHADOW_BALL -> ShadowBallFx.build(script, orb = false) // R7-08
        MoveLook.EARTHQUAKE -> EarthquakeFx.build(script) // R7-08
        MoveLook.ORB_TEMPLATE -> OrbFx.build(script) // R7-08
        MoveLook.AURA_TEMPLATE -> AuraFx.build(script) // R7-08
    }
}
