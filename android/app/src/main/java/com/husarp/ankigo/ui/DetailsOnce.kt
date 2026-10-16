package com.husarp.ankigo.ui

/**
 * "Open this Pokémon's details once" (PLAN 86): a tap on your Pokémon in a battle asks the Team screen to open its
 * details after its first render. [take] hands the uid out exactly once; a Pokémon that no longer exists (released
 * meanwhile) is dropped, not retried.
 */
class DetailsOnce {
    private var uid: String? = null

    /** Remember [u] until the next [take]. */
    fun ask(u: String?) { uid = u }

    /** The uid to open now, or null (nothing asked, already taken, or no longer [known]). Clears the request. */
    fun take(known: (String) -> Boolean): String? {
        val u = uid ?: return null
        uid = null
        return if (known(u)) u else null
    }
}
