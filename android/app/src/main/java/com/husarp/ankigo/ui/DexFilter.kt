package com.husarp.ankigo.ui

import com.husarp.ankigo.game.GameData
import com.husarp.ankigo.game.GameState
import com.husarp.ankigo.game.Mon
import com.husarp.ankigo.game.Species
import com.husarp.ankigo.game.Stat
import com.husarp.ankigo.game.Type

enum class DexOwned { ALL, OWNED, NOT_OWNED }

/**
 * The Pokédex sorts. [LAST_CAUGHT] needs the owned-copies map; the stat sorts read [DexFilter.basis]/[DexFilter.stat]/
 * [DexFilter.chosenStats]; [LEVEL] (highest owned level) and [FIRST_SEEN] need the [DexSortInfo] given to [DexFilter.apply].
 * [naturalDescending]: the direction a sort starts in (newest / biggest first; number and name go up).
 */
enum class DexSort(val label: String, val naturalDescending: Boolean = false) {
    NUMBER("Number"), NAME("Name"), LAST_CAUGHT("Last caught", true),
    LEVEL("Level", true), TOTAL("Total stats", true), STAT("One stat", true), CHOSEN_TOTAL("Chosen stats", true), FIRST_SEEN("First seen", true);

    /** The [SortKey] that does the same job. */
    val key: SortKey get() = when (this) {
        NUMBER -> SortKey.DEX_NUMBER; NAME -> SortKey.NAME; LAST_CAUGHT -> SortKey.CAUGHT_DATE; LEVEL -> SortKey.LEVEL
        TOTAL -> SortKey.TOTAL; STAT -> SortKey.STAT; CHOSEN_TOTAL -> SortKey.CHOSEN_TOTAL; FIRST_SEEN -> SortKey.FIRST_SEEN
    }

    companion object {
        /** The Dex sort for a Box/Team sort key; keys a species can't have (attacks, caught date) give NUMBER. */
        fun fromKey(k: SortKey): DexSort = when (k) {
            SortKey.DEX_NUMBER -> NUMBER; SortKey.NAME -> NAME; SortKey.LEVEL -> LEVEL; SortKey.TOTAL -> TOTAL; SortKey.STAT -> STAT
            SortKey.CHOSEN_TOTAL -> CHOSEN_TOTAL; SortKey.FIRST_SEEN -> FIRST_SEEN; SortKey.CAUGHT_DATE -> LAST_CAUGHT
            SortKey.STRONGEST_ATTACK, SortKey.VS_WILD -> NUMBER
        }
    }
}

/** What the Dex sorts that look at your collection need: the owned copies and first-seen dates live in [state]. */
class DexSortInfo(val data: GameData, val state: GameState)

/**
 * The Pokédex filters and sort, pure Kotlin. All parts combine with AND.
 * - [owned]: Owned = at least one of that species in the team or box now; Not owned = the rest (seen-only and never met).
 * - [gens]: multi-select, empty = every enabled gen. [types]: any of the species' types. [wishlistOnly]: only the wishlist.
 * - [query]: name (contains) or number (prefix of "25" or of the padded "025"; a leading "#" is ignored), case-insensitive.
 *   The number is the national one ([Species.baseNum]): "19" also finds Alolan Rattata. Sorted by number, a form follows its base species.
 */
data class DexFilter(
    val owned: DexOwned = DexOwned.ALL,
    val gens: Set<Int> = emptySet(),
    val types: Set<Type> = emptySet(),
    val wishlistOnly: Boolean = false,
    val query: String = "",
    val sort: DexSort = DexSort.NUMBER,
    /** Sort direction; null = the sort's own ([DexSort.naturalDescending]). Missing values (not owned, never seen) always go last. */
    val descending: Boolean? = null,
    /** For [DexSort.STAT] (null = HP). */
    val stat: Stat? = null,
    /** For [DexSort.CHOSEN_TOTAL]: the stats that are added up. */
    val chosenStats: Set<Stat> = emptySet(),
    /** Which stats the stat sorts read (Pokédex species: base, or IV 31 / EV 0 / neutral at Lv 100 / [level]; CURRENT = best owned copy). */
    val basis: StatBasis = StatBasis.BASE,
    val level: Int = 100,
) {
    /**
     * @param all every species to consider (usually the enabled gens)
     * @param lastCaught species number -> newest caughtAt among the mons you own now (absent = not owned), see [lastCaught]
     * @param info the collection, for the LEVEL, FIRST_SEEN and CURRENT-basis sorts (null: those have no values, so the list stays in number order)
     */
    fun apply(
        all: List<Species>, lastCaught: Map<Int, Long>, wishlist: Set<Int>, enabledGens: Set<Int>, info: DexSortInfo? = null,
    ): List<Species> {
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
                (q.isEmpty() || sp.name.lowercase().contains(q) || (digits && numMatches(sp.baseNum, q)))
        }
        val desc = descending ?: sort.naturalDescending
        val tie = compareBy<Species>({ it.baseNum }, { it.num })
        return when (sort) {
            DexSort.NUMBER -> MonSort.ordered(out, desc, { it.baseNum.toDouble() }, null, tie)
            DexSort.NAME -> MonSort.ordered(out, desc, null, { it.name.lowercase() }, tie)
            // owned first, newest first; the rest after, by number
            DexSort.LAST_CAUGHT -> MonSort.ordered(out, desc, { lastCaught[it.num]?.toDouble() }, null, tie)
            else -> MonSort.sortSpecies(info?.data, info?.state, out, spec(desc))
        }
    }

    /** The [SortSpec] of this filter's sort (for the Box-style sorts and for remembering it). */
    fun spec(desc: Boolean = descending ?: sort.naturalDescending) =
        SortSpec(key = sort.key, stat = stat, chosenStats = chosenStats, basis = basis, level = level, descending = desc)

    /** This filter with the sort taken from a remembered [SortSpec] (see [SortPrefs]). */
    fun withSort(s: SortSpec): DexFilter =
        copy(sort = DexSort.fromKey(s.key), descending = s.descending, stat = s.stat, chosenStats = s.chosenStats, basis = s.basis, level = s.level)

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
