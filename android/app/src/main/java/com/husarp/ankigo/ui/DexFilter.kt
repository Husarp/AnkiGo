package com.husarp.ankigo.ui

import com.husarp.ankigo.game.Mon
import com.husarp.ankigo.game.Species
import com.husarp.ankigo.game.Type

enum class DexOwned { ALL, OWNED, NOT_OWNED }

enum class DexSort(val label: String) { NUMBER("Number"), NAME("Name"), LAST_CAUGHT("Last caught") }

/**
 * The Pokédex filters and sort, pure Kotlin. All parts combine with AND.
 * - [owned]: Owned = at least one of that species in the team or box now; Not owned = the rest (seen-only and never met).
 * - [gens]: multi-select, empty = every enabled gen. [types]: any of the species' types. [wishlistOnly]: only the wishlist.
 * - [query]: name (contains) or number (prefix of "25" or of the padded "025"; a leading "#" is ignored), case-insensitive.
 */
data class DexFilter(
    val owned: DexOwned = DexOwned.ALL,
    val gens: Set<Int> = emptySet(),
    val types: Set<Type> = emptySet(),
    val wishlistOnly: Boolean = false,
    val query: String = "",
    val sort: DexSort = DexSort.NUMBER,
) {
    /**
     * @param all every species to consider (usually the enabled gens)
     * @param lastCaught species number -> newest caughtAt among the mons you own now (absent = not owned), see [lastCaught]
     */
    fun apply(all: List<Species>, lastCaught: Map<Int, Long>, wishlist: Set<Int>, enabledGens: Set<Int>): List<Species> {
        val q = query.trim().removePrefix("#").lowercase()
        val digits = q.isNotEmpty() && q.all { it.isDigit() }
        val out = all.filter { sp ->
            sp.gen in enabledGens &&
                (gens.isEmpty() || sp.gen in gens) &&
                (!wishlistOnly || sp.num in wishlist) &&
                (types.isEmpty() || sp.types.any { it in types }) &&
                when (owned) {
                    DexOwned.ALL -> true
                    DexOwned.OWNED -> sp.num in lastCaught
                    DexOwned.NOT_OWNED -> sp.num !in lastCaught
                } &&
                (q.isEmpty() || sp.name.lowercase().contains(q) || (digits && numMatches(sp.num, q)))
        }
        return when (sort) {
            DexSort.NUMBER -> out.sortedBy { it.num }
            DexSort.NAME -> out.sortedWith(compareBy({ it.name.lowercase() }, { it.num }))
            // owned first, newest first; the rest after, by number
            DexSort.LAST_CAUGHT -> out.sortedWith(compareBy<Species>({ if (it.num in lastCaught) 0 else 1 }, { -(lastCaught[it.num] ?: 0L) }, { it.num }))
        }
    }

    // "25" = numbers starting with 25; "025" = the padded form (#025...), so a zero-padded query pins one number
    private fun numMatches(num: Int, q: String) =
        if (q.startsWith("0")) "%03d".format(num).startsWith(q) else num.toString().startsWith(q)

    /** Anything beyond the defaults (used for the "No Pokémon match" line and the sort pill). */
    val active get() = owned != DexOwned.ALL || gens.isNotEmpty() || types.isNotEmpty() || wishlistOnly || query.isNotBlank()

    companion object {
        /** Species number -> newest caughtAt among the mons you own now. */
        fun lastCaught(mons: List<Mon>): Map<Int, Long> {
            val m = HashMap<Int, Long>()
            for (mon in mons) m[mon.species] = maxOf(m[mon.species] ?: Long.MIN_VALUE, mon.caughtAt)
            return m
        }
    }
}
