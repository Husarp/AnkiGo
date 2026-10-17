package com.husarp.ankigo.ui

import com.husarp.ankigo.game.CashKind
import com.husarp.ankigo.game.GameData
import com.husarp.ankigo.game.GameEvent

/**
 * The data of the catch result card (PLAN 40). Pure; the card itself is drawn by the look ticket.
 * [pokedex] and [throwInfo] are null after a defeat; [throwInfo] is also null for a catch by the auto rule (no ball thrown).
 */
data class CatchSummary(
    val caught: Boolean,
    val name: String,
    val pokedex: PokedexLine?,
    val throwInfo: ThrowLine?,
    val xp: List<XpLine>,
    val coins: Int, // answer + defeat money of the batch; the once-a-day goal bonus is not part of the fight
    val next: String,
) {
    /** [isNew] = first of its species; [number] = national Pokédex number. */
    data class PokedexLine(val isNew: Boolean, val number: Int) {
        val text get() = if (isNew) "New · #$number" else "Caught before"
    }

    /** [number] = which throw of this fight caught it (1 = first); [chance] = that ball's real catch chance in %. */
    data class ThrowLine(val ball: String, val number: Int, val chance: Int)

    /** XP one Pokémon gained in this batch (all its XpGained summed); [level] = its level after, [leveledUp] = a level came with it. */
    data class XpLine(val uid: String, val name: String, val xp: Int, val level: Int, val leveledUp: Boolean)

    companion object {
        const val NEXT_TEXT = "Continue starts the next battle"

        /**
         * [events] = the batch of the answer (or ball throw) that ended a fight; null when it ended none. The throw number is the
         * [GameEvent.BallThrow.number] the game counted with the fight itself (so it is the same on every screen); [earlierThrows]
         * (failed throws earlier in this fight, see [failedThrows]) only stands in for an event that has no number. [data] gives
         * the national number of a regional form.
         */
        fun of(events: List<GameEvent>, earlierThrows: Int = 0, data: GameData? = null): CatchSummary? {
            val caught = events.filterIsInstance<GameEvent.Caught>().lastOrNull()
            val defeated = events.filterIsInstance<GameEvent.Defeated>().lastOrNull()
            if (caught == null && defeated == null) return null
            val throws = events.filterIsInstance<GameEvent.BallThrow>()
            val pokedex = caught?.let {
                PokedexLine(it.newSpecies, data?.species?.get(it.species)?.baseNum ?: it.species)
            }
            val throwLine = if (caught != null) throws.lastOrNull { it.success }?.let {
                ThrowLine(it.ball, if (it.number > 0) it.number else earlierThrows + throws.size, it.chance)
            } else null
            val levelled = events.filterIsInstance<GameEvent.LevelUp>().map { it.uid }.toSet()
            val xp = events.filterIsInstance<GameEvent.XpGained>().groupBy { it.uid }.map { (uid, list) ->
                XpLine(uid, list.last().name, list.sumOf { it.xp }, list.last().level, uid in levelled)
            }.filter { it.xp > 0 }
            val coins = events.filterIsInstance<GameEvent.Cash>().filter { it.kind != CashKind.DAILY_BONUS }.sumOf { it.amount }
            return CatchSummary(caught != null, caught?.name ?: defeated!!.name, pokedex, throwLine, xp, coins, NEXT_TEXT)
        }

        /** Failed throws in [events] (the events since the fight began): what to pass as `earlierThrows` for the batch that ends it. */
        fun failedThrows(events: List<GameEvent>): Int = events.count { it is GameEvent.BallThrow && !it.success }
    }
}
