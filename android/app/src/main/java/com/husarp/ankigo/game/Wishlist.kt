package com.husarp.ankigo.game

/**
 * Wishlist rule (R6-14): by hand, only a species you have SEEN but not caught yet can be added (not an unseen one,
 * not one you own or have caught). Removing is always allowed. A species that becomes caught (catch, ball, fossil revive,
 * evolution, starter, Ankimon import) leaves the list on its own ([onCaught]); an old save is cleaned once at load ([prune]).
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

    /** [num] was just caught/owned: it leaves the wishlist (no star, no encounter boost). */
    fun onCaught(state: GameState, num: Int) {
        state.settings.wishlist.remove(num)
    }

    /** Drops every wishlisted species that is already caught (old saves, bulk imports). Returns true when the list changed. */
    fun prune(state: GameState): Boolean = state.settings.wishlist.removeAll { it in state.caught }

    /** Removes [num] if listed (always allowed); else adds it when [canAdd]. Returns true when the list changed. */
    fun toggle(state: GameState, num: Int): Boolean {
        val w = state.settings.wishlist
        if (w.remove(num)) return true
        return if (canAdd(state, num)) w.add(num) else false
    }
}
