package com.husarp.ankigo.game

/** A permanent Bag tool. The level cooldown starts when the post-skip lock lifts. */
object PokeDoll {
    const val ITEM = "poke-doll"
    const val LOCKED = "Defeat or catch a Pokémon to skip again"
    private const val MINUTE = 60_000L
    private const val HOUR = 60 * MINUTE
    private val thresholds = listOf(1, 5, 15, 30, 50)
    private val cooldowns = listOf(60, 50, 40, 35, 30)

    fun level(found: Int): Int = thresholds.count { found >= it }
    fun nextLevel(found: Int): Int? = thresholds.firstOrNull { found < it }
    fun cooldownMinutes(found: Int): Int = cooldowns[(level(found) - 1).coerceAtLeast(0)]
    fun rewardPercent(found: Int): Int = when (level(found)) { 0, 1 -> 0; 2, 3 -> 10; else -> 20 }
    fun wishlistWeight(found: Int): Int = Tuning.WISHLIST_WEIGHT * if (level(found) == 5) 2 else 1

    /** Old Bag copies become found dolls once; the Bag keeps one permanent tool. */
    fun migrate(state: GameState) {
        if (state.dollsFound == 0) state.dollsFound = state.count(ITEM).coerceAtLeast(0)
        if (state.dollsFound > 0) state.bag[ITEM] = 1
    }

    fun found(state: GameState, count: Int) {
        migrate(state)
        state.dollsFound = (state.dollsFound.toLong() + count.coerceAtLeast(0)).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        migrate(state)
    }

    private fun unlockAt(state: GameState, at: Long) {
        state.dollLockedAt = null
        state.dollReadyAt = at + cooldownMinutes(state.dollsFound) * MINUTE
    }

    private fun expireLock(state: GameState, now: Long) {
        state.dollLockedAt?.let { if (now >= it + HOUR) unlockAt(state, it + HOUR) }
    }

    fun won(state: GameState, now: Long) {
        expireLock(state, now)
        if (state.dollLockedAt != null) unlockAt(state, now)
    }

    fun blocker(state: GameState, now: Long): String? {
        expireLock(state, now)
        if (state.dollLockedAt != null) return LOCKED
        val left = state.dollReadyAt - now
        return if (left > 0) "Poké Doll: ${(left + MINUTE - 1) / MINUTE} min left" else null
    }

    fun used(state: GameState, now: Long) { state.dollLockedAt = now }

    fun facts(state: GameState, now: Long): List<ItemFact> {
        val found = state.dollsFound
        val currentLevel = level(found)
        val progress = nextLevel(found)?.let { "$found / $it dolls found for Level ${currentLevel + 1}" } ?: "$found dolls found · max level"
        val perks = "${cooldownMinutes(found)} min cooldown · ${rewardPercent(found)}% of defeat XP and coins" +
            if (currentLevel == 5) " · wishlist weight ×2 (4 total)" else ""
        val next = nextLevel(found)?.let {
            "${cooldownMinutes(it)} min cooldown · ${rewardPercent(it)}% rewards" + if (level(it) == 5) " · wishlist weight ×2" else ""
        }
        return listOfNotNull(
            ItemFact("Level $currentLevel", progress),
            ItemFact("Current perks", perks),
            next?.let { ItemFact("Next level", it) },
            ItemFact("Availability", blocker(state, now) ?: "Ready to skip"),
        )
    }
}
