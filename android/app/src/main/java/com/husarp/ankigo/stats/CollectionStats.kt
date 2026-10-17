package com.husarp.ankigo.stats

import com.husarp.ankigo.game.Encounters
import com.husarp.ankigo.game.GameData
import com.husarp.ankigo.game.GameState
import com.husarp.ankigo.game.Mon
import com.husarp.ankigo.game.Species
import com.husarp.ankigo.game.Tier
import com.husarp.ankigo.game.Type

// TICKET R3-05 (PLAN 66): the numbers of the Stats screen's "Collection" section (pure Kotlin, tested; no screen here).

/** Seen / caught out of [total] base species (regional forms don't count), with whole-percent helpers. */
data class DexProgress(val seen: Int, val caught: Int, val total: Int) {
    val seenPercent: Int get() = CollectionStats.percent(seen, total)
    val caughtPercent: Int get() = CollectionStats.percent(caught, total)
}

/** A Pokémon picked out for a "highest level" / "longest held" / "rarest" line; [name] is the species name. */
data class MonRef(
    val uid: String, val species: Int, val name: String, val nickname: String?,
    val level: Int, val shiny: Boolean, val tier: Tier, val caughtAt: Long,
)

data class CollectionStats(
    /** Every owned Pokémon (team + box); [unsupported] ones are not counted here. */
    val owned: Int,
    val distinctSpecies: Int,
    /** [owned] minus [distinctSpecies]: extra copies. */
    val duplicates: Int,
    val shinies: Int,
    val legendaries: Int,
    val mythicals: Int,
    /** Owned Pokémon that are Ultra Beasts (Encounters.ULTRA_BEASTS). */
    val ultraBeasts: Int = 0,
    /** Owned Pokémon whose species is a regional form (Alolan, Galarian, ...). */
    val regionalForms: Int,
    /** Ankimon Pokémon kept as "Not supported yet". */
    val unsupported: Int,
    val dex: DexProgress,
    /** Only generations that have species in the data, ascending. */
    val dexByGen: Map<Int, DexProgress>,
    /** Every type that has species in the data (a dual type counts for both types). */
    val dexByType: Map<Type, DexProgress>,
    val highest: MonRef?,
    /** Average level of the team, 1 decimal; 0.0 with no team. */
    val averageTeamLevel: Double,
    /** Wild catches per ball id ("auto" = the auto rule's catch), most first; ties by id. */
    val ballCatches: List<Pair<String, Int>>,
    val totalCatches: Int,
    val totalDefeats: Int,
    val totalEvolutions: Int,
    /** Epoch ms of the oldest / newest known catch date among owned Pokémon; null when none is known. */
    val firstCatch: Long?,
    val lastCatch: Long?,
    /** The owned Pokémon with the oldest known catch date. */
    val longestHeld: MonRef?,
    /** The owned Pokémon of the rarest tier; ties: shiny first, then highest level. */
    val rarest: MonRef?,
) {
    companion object {
        /** Whole percent, rounded; 0 when [total] is 0 (never divides by zero). */
        fun percent(part: Int, total: Int): Int =
            if (total <= 0) 0 else Math.round(part * 100.0 / total).toInt().coerceIn(0, 100)

        fun percentText(part: Int, total: Int): String = "${percent(part, total)} %"

        /** Rarity rank of a tier: MYTHICAL > LEGENDARY > ULTRA > BABY > STARTER > NORMAL (Mega/G-Max fall back to NORMAL). */
        fun rarity(tier: Tier): Int = when (tier) {
            Tier.MYTHICAL -> 5
            Tier.LEGENDARY -> 4
            Tier.ULTRA -> 3
            Tier.BABY -> 2
            Tier.STARTER -> 1
            else -> 0
        }

        fun of(data: GameData, state: GameState, now: Long): CollectionStats {
            // [now] is part of the API for the screen (date text); nothing here depends on the clock yet.
            val owned = state.mons.filter { data.species.containsKey(it.species) }
            val ownedAll = state.mons.size
            val distinct = state.mons.map { it.species }.toSet().size

            fun ref(m: Mon) = MonRef(
                m.uid, m.species, data.species[m.species]?.name ?: m.name, m.nickname, m.level, m.shiny, m.tier, m.caughtAt,
            )

            val base = data.species.values.filter { it.form == null }
            fun progress(list: List<Species>) = DexProgress(
                seen = list.count { it.num in state.seen }, caught = list.count { it.num in state.caught }, total = list.size,
            )

            val dated = owned.filter { it.caughtAt > 0 }
            val team = state.teamMons()
            val avg = if (team.isEmpty()) 0.0 else Math.round(team.sumOf { it.level } * 10.0 / team.size) / 10.0

            return CollectionStats(
                owned = ownedAll,
                distinctSpecies = distinct,
                duplicates = ownedAll - distinct,
                shinies = state.mons.count { it.shiny },
                legendaries = owned.count { data.species.getValue(it.species).isLegendary },
                mythicals = owned.count { data.species.getValue(it.species).isMythical },
                ultraBeasts = owned.count { data.species.getValue(it.species).baseNum in Encounters.ULTRA_BEASTS },
                regionalForms = owned.count { data.species.getValue(it.species).form != null },
                unsupported = state.unsupported.size,
                dex = progress(base),
                dexByGen = base.groupBy { it.gen }.toSortedMap().mapValues { progress(it.value) },
                dexByType = Type.values().mapNotNull { t ->
                    base.filter { t in it.types }.takeIf { it.isNotEmpty() }?.let { t to progress(it) }
                }.toMap(),
                highest = owned.maxByOrNull { it.level }?.let(::ref),
                averageTeamLevel = avg,
                ballCatches = state.ballCatches.filter { it.value > 0 }.toList()
                    .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first }),
                totalCatches = state.catches,
                totalDefeats = state.defeats,
                totalEvolutions = state.evolutions,
                firstCatch = dated.minOfOrNull { it.caughtAt },
                lastCatch = dated.maxOfOrNull { it.caughtAt },
                longestHeld = dated.minByOrNull { it.caughtAt }?.let(::ref),
                rarest = owned.maxWithOrNull(
                    compareBy<Mon> { rarity(it.tier) }.thenBy { it.shiny }.thenBy { it.level },
                )?.let(::ref),
            )
        }
    }
}
