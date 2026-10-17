package com.husarp.ankigo.ui.movefx

import com.husarp.ankigo.ui.FxEffect

/*
 * Round 7 attack animations: the shared contract (written by the manager before the tickets started, so the tickets can be
 * built in parallel). The approved design is docs/design/attack-anims-v2.html + docs/design/attack-anims-v2/v2-fx.js.
 * R7-02 fills MoveFxCatalog (MoveFxCatalog.kt), R7-03 / R7-04 / R7-08 the player (MoveFx.kt and the looks), R7-05 wires it
 * into ArenaView and EventPlayer. Change these declarations only through the manager.
 */

/** The approved move looks, and the three category templates of the page's fallback rules 2 and 3. */
enum class MoveLook {
    TACKLE, EMBER, WATER_JET, THUNDERBOLT, VINE_WHIP, ICE_BEAM, PSYCHIC, SHADOW_BALL, EARTHQUAKE,
    /** Physical template: Tackle's lunge, then an impact burst at the target made of the kit's particles. */
    HIT_TEMPLATE,
    /** Special template: Shadow Ball's orb flies and bursts (C's dark burst), in the palette's colours (and the kit's particles). */
    ORB_TEMPLATE,
    /** Status template: Psychic's aura around the user, in the palette's colours (and the kit's particles). */
    AURA_TEMPLATE,
}

/**
 * Whose particles a template uses. Rule 2 (the type has a picked move, the category differs): that type's own particles
 * (FIRE = Ember's pixel flames and embers, WATER = droplets, ELECTRIC = sparks, GRASS = pixel leaves, ICE = ice shards and
 * frost stars, PSYCHIC = rings and twirls, GHOST = purple wisps, GROUND = dirt, NORMAL = Tackle's stars and sparks).
 * Rule 3 (the type has no pick): PLAIN, the template's own particles in the palette's colours.
 */
enum class FxKit { NORMAL, FIRE, WATER, ELECTRIC, GRASS, ICE, PSYCHIC, GHOST, GROUND, PLAIN }

/**
 * Colours (ARGB) a template draws with, like the page's Shadow Ball tint: [deep] = the orb / the dark ring, [glow] = the light
 * around it, [smoke] = the cloud, [spark] = the small stars.
 */
data class FxPalette(val deep: Int, val glow: Int, val smoke: Int, val spark: Int)

/**
 * What to play for one move. [k] = size factor, sqrt(power / the base look's power) kept in 0.8..1.6 (1 when the move has no
 * power, e.g. status moves); multi-shot looks fire more shots with it (Ember: round(10 * k)), streams last longer (Water Gun).
 */
data class MoveFxPlan(val look: MoveLook, val k: Float, val kit: FxKit, val palette: FxPalette)

/**
 * What a running move animation may touch on the arena (ArenaView implements it, R7-05). Positions are pixels of the layer the
 * effect draws on. The mockup's stage is 480 x 270 units with the attacker's centre at (140, 190) and the target's at
 * (350, 105); the looks keep "up" as up and mirror only the horizontal direction when the target is left of the attacker.
 */
interface MoveFxStage {
    /** Centre of the attacker's sprite box. */
    val attackerX: Float
    val attackerY: Float
    /** Centre of the target's sprite box. */
    val targetX: Float
    val targetY: Float
    /** Pixels per mockup stage unit. */
    val unit: Float

    /**
     * Sets a sprite's pose on top of its rest pose: offsets [dx], [dy] in px (screen axes), scale [sx], [sy] about its
     * bottom centre, [matrix] a 4 x 5 colour matrix (20 floats, android.graphics.ColorMatrix order) or null for none.
     */
    fun pose(attacker: Boolean, dx: Float = 0f, dy: Float = 0f, sx: Float = 1f, sy: Float = 1f, matrix: FloatArray? = null)

    /** Vertical jolt of the background in px (Earthquake); 0 = at rest. */
    fun jolt(dy: Float)

    /** Puts both sprites and the background back to rest. */
    fun restore()
}

/** A running move animation on the arena's effect layer. [FxEffect.end] must call [MoveFxStage.restore]. */
interface MoveFxEffect : FxEffect {
    /** Milliseconds from the start (already divided by the speed) when the first hit lands: the HP bar and damage number start then. */
    val impactMs: Long

    /**
     * Milliseconds from the start (already divided by the speed) after which the sprites and the background no longer move:
     * the next battle event may start then. Particles may still fade out after it.
     */
    val doneMs: Long
}

/**
 * Something that can jolt up and down by [jolt] px (Earthquake shakes the background). R7-07's arena background drawable
 * (set as ArenaView's background) implements it; R7-05's stage finds it as `arena.background as? Joltable`.
 */
interface Joltable {
    var jolt: Float
}
