package com.husarp.ankigo.game

/**
 * Wishlist rule (R6-14): by hand, only a species you have SEEN but not caught yet can be added (not an unseen one,
 * not one you own or have caught). Removing is always allowed. Old entries stay as they are.
 */
object Wishlist {
    /** True when the player may add [num] by hand. */
    fun canAdd(state: GameState, num: Int): Boolean =
        num in state.seen && num !in state.caught && state.mons.none { it.species == num }

    /** Why [num] can't be added: "unseen", "owned", or null when it can (or is already listed). */
    fun blocker(state: GameState, num: Int): String? = when {
        num in state.settings.wishlist -> null
        num in state.caught || state.mons.any { it.species == num } -> "owned"
        num !in state.seen -> "unseen"
        else -> null
    }

    /** Removes [num] if listed (always allowed); else adds it when [canAdd]. Returns true when the list changed. */
    fun toggle(state: GameState, num: Int): Boolean {
        val w = state.settings.wishlist
        if (w.remove(num)) return true
        return if (canAdd(state, num)) w.add(num) else false
    }
}
