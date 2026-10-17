package com.husarp.ankigo.ui

import kotlin.math.abs

/**
 * PLAN 34 (changed in 0.15): a swipe across our own Pokémon in battle turns it between its back and its front view; it stays
 * turned (the facing is saved, [com.husarp.ankigo.ui.AppPrefs.playerFront]) until the next swipe. The decisions, pure.
 */
internal object SwipeTurn {
    /** One half of the "turn" squash (scaleX 1 -> 0, then 0 -> 1). */
    const val SQUASH_MS = 90L

    /** A drag counts as a swipe once it is clearly horizontal: at least three touch slops wide and 1.5 times wider than tall. */
    fun isSwipe(dx: Float, dy: Float, slopPx: Float): Boolean = abs(dx) >= slopPx * 3f && abs(dx) >= abs(dy) * 1.5f

    /** A drag already this sideways (one touch slop, 1.5 times wider than tall) is kept from a vertical scroll parent. */
    fun isClaim(dx: Float, dy: Float, slopPx: Float): Boolean = abs(dx) >= slopPx && abs(dx) >= abs(dy) * 1.5f

    /**
     * Whether a detected swipe turns the Pokémon: not while an animation plays (or in PiP), and a turn to the front needs a
     * front picture (turning back always works). [hasFront] (file checks) is asked only when it matters.
     */
    fun mayTurn(allowed: Boolean, toFront: Boolean, hasFront: () -> Boolean): Boolean = allowed && (!toFront || hasFront())
}

/** Follows one finger from DOWN: [move] says true once, the moment the drag becomes a swipe; after that the touch is not a tap. */
internal class SwipeTracker(private val slopPx: Float) {
    private var x0 = 0f
    private var y0 = 0f
    var swiped = false
        private set

    fun down(x: Float, y: Float) { x0 = x; y0 = y; swiped = false }

    fun move(x: Float, y: Float): Boolean {
        if (swiped || !SwipeTurn.isSwipe(x - x0, y - y0, slopPx)) return false
        swiped = true
        return true
    }

    /** Whether the drag so far is clearly sideways ([SwipeTurn.isClaim]), before it counts as a swipe. */
    fun claims(x: Float, y: Float): Boolean = !swiped && SwipeTurn.isClaim(x - x0, y - y0, slopPx)

    /** End of the touch; true when it was a swipe (the matching UP must not click). */
    fun end(): Boolean { val s = swiped; swiped = false; return s }
}

/** What the arena asks of the panel to swap our Pokémon's picture (the arena never reads the game). */
interface PlayerTurnSource {
    /** The saved facing: true = shown from the front (turned to face the wild one). */
    fun facingFront(): Boolean
    /** True when a front picture is there to show (animated or static); false = a swipe to the front does nothing. */
    fun hasFront(): Boolean
    /** Saves the facing; with [show] also loads the matching picture into the player's sprite. */
    fun turnTo(front: Boolean, show: Boolean)
}
