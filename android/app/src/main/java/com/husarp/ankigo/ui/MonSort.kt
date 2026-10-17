package com.husarp.ankigo.ui

import com.husarp.ankigo.game.Category
import com.husarp.ankigo.game.GameData
import com.husarp.ankigo.game.GameState
import com.husarp.ankigo.game.Mon
import com.husarp.ankigo.game.Move
import com.husarp.ankigo.game.Nature
import com.husarp.ankigo.game.Species
import com.husarp.ankigo.game.Stat
import com.husarp.ankigo.game.StatCalc
import com.husarp.ankigo.game.Stats
import com.husarp.ankigo.game.Tuning
import com.husarp.ankigo.game.Type
import com.husarp.ankigo.game.TypeChart
import com.husarp.ankigo.game.shownName
import java.util.IdentityHashMap

// PLAN items 48 + 55 (R3-08): sort models for Box, the Team picker and the Pokédex. Pure Kotlin (no android.*), unit-tested
// on the JVM. The chips/sheets that choose a [SortSpec] come later; this file is the maths and the remembered string.

/** What to sort by. Which keys fit a Pokédex species (no moves, no catch date) is in [MonSort.dexKeys]. */
enum class SortKey(
    val label: String,
    /** The direction a player expects first: biggest / newest first for numbers and dates, A-Z and 1-9 for name and number. */
    val naturalDescending: Boolean,
) {
    LEVEL("Level", true),
    TOTAL("Total stats", true),
    STAT("One stat", true),
    CHOSEN_TOTAL("Chosen stats", true),
    STRONGEST_ATTACK("Strongest attack", true),
    VS_WILD("Best vs the wild one", true),
    NAME("Name", false),
    DEX_NUMBER("Pokédex number", false),
    CAUGHT_DATE("Caught date", true),
    FIRST_SEEN("First seen", true),
}

/** Which stats a stat-based sort uses: species base stats, the Pokémon at Lv 100, as it is now, or at [SortSpec.level]. */
enum class StatBasis(val label: String) {
    BASE("Base stats"), MAX_LEVEL("At Lv 100"), CURRENT("Current"), AT_LEVEL("At level…"),
}

/**
 * One sort choice. [descending] is literal: true = biggest value / newest date / Z first (use [SortKey.naturalDescending]
 * for the usual start). [stat] is for [SortKey.STAT] (null = HP); [chosenStats] for [SortKey.CHOSEN_TOTAL] (empty = 0, all tie);
 * [basis] + [level] (1..100, only for [StatBasis.AT_LEVEL]) say which stats the stat-based keys read.
 */
data class SortSpec(
    val key: SortKey = SortKey.DEX_NUMBER,
    val stat: Stat? = null,
    val chosenStats: Set<Stat> = emptySet(),
    val basis: StatBasis = StatBasis.CURRENT,
    val level: Int = 100,
    val descending: Boolean = key.naturalDescending,
) {
    /** The string kept in SharedPreferences, e.g. `key=TOTAL;stat=;chosen=ATK,DEF;basis=CURRENT;level=100;desc=1`. */
    fun encode(): String =
        "key=${key.name};stat=${stat?.name.orEmpty()};chosen=${chosenStats.sortedBy { it.ordinal }.joinToString(",") { it.name }};" +
            "basis=${basis.name};level=${level.coerceIn(1, 100)};desc=${if (descending) 1 else 0}"

    /** The same choice with the opposite direction (the direction toggle). */
    fun flipped(): SortSpec = copy(descending = !descending)

    companion object {
        val DEFAULT = SortSpec()

        /** The spec for [key] with its natural direction. */
        fun of(key: SortKey) = SortSpec(key = key)

        /** Reads [encode]'s string. Null, empty, garbage or an unknown key gives [DEFAULT]; a bad single field gets its default. */
        fun decode(s: String?): SortSpec {
            if (s.isNullOrBlank()) return DEFAULT
            val m = HashMap<String, String>()
            for (part in s.split(';')) {
                val i = part.indexOf('=')
                if (i > 0) m[part.substring(0, i).trim()] = part.substring(i + 1).trim()
            }
            val key = SortKey.entries.firstOrNull { it.name == m["key"] } ?: return DEFAULT
            return SortSpec(
                key = key,
                stat = Stat.entries.firstOrNull { it.name == m["stat"] },
                chosenStats = m["chosen"].orEmpty().split(',').mapNotNull { n -> Stat.entries.firstOrNull { it.name == n.trim() } }.toSet(),
                basis = StatBasis.entries.firstOrNull { it.name == m["basis"] } ?: StatBasis.CURRENT,
                level = (m["level"]?.toIntOrNull() ?: 100).coerceIn(1, 100),
                descending = when (m["desc"]) { "1" -> true; "0" -> false; else -> key.naturalDescending },
            )
        }
    }
}

object MonSort {
    /** The keys that make sense for a Pokédex species (no moves, no catch date). */
    val dexKeys: List<SortKey> = listOf(
        SortKey.DEX_NUMBER, SortKey.NAME, SortKey.LEVEL, SortKey.TOTAL, SortKey.STAT, SortKey.CHOSEN_TOTAL, SortKey.FIRST_SEEN,
    )

    /** Whether [SortKey.VS_WILD] has a wild Pokémon to compare with (the UI greys the option when not). */
    fun vsWildAvailable(state: GameState): Boolean = state.battle != null

    /** The key really used: [SortKey.VS_WILD] without a battle falls back to [SortKey.STRONGEST_ATTACK]. */
    fun effectiveKey(spec: SortSpec, state: GameState): SortKey =
        if (spec.key == SortKey.VS_WILD && !vsWildAvailable(state)) SortKey.STRONGEST_ATTACK else spec.key

    // ---- stats ----

    /** Stats from raw parts at [level]: the stat formulas of the game (StatCalc). */
    fun statsAt(base: Stats, ivs: Stats, evs: Stats, nature: Nature, level: Int): Stats = Stats(
        StatCalc.hp(base.hp, ivs.hp, evs.hp, level),
        StatCalc.other(base.atk, ivs.atk, evs.atk, level, nature.mult(Stat.ATK)),
        StatCalc.other(base.def, ivs.def, evs.def, level, nature.mult(Stat.DEF)),
        StatCalc.other(base.spa, ivs.spa, evs.spa, level, nature.mult(Stat.SPA)),
        StatCalc.other(base.spd, ivs.spd, evs.spd, level, nature.mult(Stat.SPD)),
        StatCalc.other(base.spe, ivs.spe, evs.spe, level, nature.mult(Stat.SPE)),
    )

    private val PERFECT = Stats(31, 31, 31, 31, 31, 31)

    /** The stats an owned Pokémon is sorted by under [spec]'s basis (its own IVs, EVs and nature); null if its species is unknown. */
    fun statsFor(data: GameData, mon: Mon, spec: SortSpec): Stats? {
        val sp = data.species[mon.species] ?: return null
        return when (spec.basis) {
            StatBasis.BASE -> sp.base
            StatBasis.CURRENT -> StatCalc.stats(sp, mon)
            StatBasis.MAX_LEVEL -> statsAt(sp.base, mon.ivs, mon.evs, mon.nature, 100)
            StatBasis.AT_LEVEL -> statsAt(sp.base, mon.ivs, mon.evs, mon.nature, spec.level.coerceIn(1, 100))
        }
    }

    /** The stats of a species that isn't a copy: base, or IV 31 / EV 0 / neutral nature at Lv 100 or [SortSpec.level]. Null for [StatBasis.CURRENT]. */
    fun speciesStats(sp: Species, spec: SortSpec): Stats? = when (spec.basis) {
        StatBasis.BASE -> sp.base
        StatBasis.CURRENT -> null
        StatBasis.MAX_LEVEL -> statsAt(sp.base, PERFECT, Stats.ZERO, Nature.HARDY, 100)
        StatBasis.AT_LEVEL -> statsAt(sp.base, PERFECT, Stats.ZERO, Nature.HARDY, spec.level.coerceIn(1, 100))
    }

    /** The number a stat-based key reads out of [s] (null for the other keys). */
    private fun statValue(s: Stats, spec: SortSpec): Double? = when (spec.key) {
        SortKey.TOTAL -> s.total.toDouble()
        SortKey.STAT -> s[spec.stat ?: Stat.HP].toDouble()
        SortKey.CHOSEN_TOTAL -> spec.chosenStats.sumOf { s[it] }.toDouble()
        else -> null
    }

    // ---- attacks ----

    private val FALLBACK = Move(Tuning.FALLBACK_MOVE, "Tackle", Type.NORMAL, Category.PHYSICAL, 40, 100)

    /**
     * Damage score of the best damaging move of [mon]: power x STAB (1.5 when the move's type is one of its types) x accuracy/100
     * (never-miss = 1) x attacking stat (Atk for physical, SpA for special, at [spec]'s basis); with [vsWild] also x the type
     * effectiveness against the wild Pokémon's types. Power 0 on a damaging move counts as Tuning.UNKNOWN_POWER, as in battle.
     * 0.0 when it knows no damaging move.
     */
    fun attackScore(data: GameData, mon: Mon, spec: SortSpec, vsWild: Mon? = null): Double {
        val sp = data.species[mon.species] ?: return 0.0
        val stats = statsFor(data, mon, spec) ?: return 0.0
        val wildTypes = vsWild?.let { data.species[it.species]?.types }
        var best = 0.0
        // as in battle: move ids the data doesn't know are skipped, and with none left the fallback move (Tackle) is used
        val known = mon.moves.mapNotNull { data.move(it) }.ifEmpty { listOf(data.move(Tuning.FALLBACK_MOVE) ?: FALLBACK) }
        for (m in known) {
            if (!m.damaging) continue
            val power = if (m.power > 0) m.power else Tuning.UNKNOWN_POWER
            val stab = if (m.type in sp.types) Tuning.STAB else 1.0
            val acc = (m.accuracy ?: 100) / 100.0
            val atk = (if (m.category == Category.PHYSICAL) stats.atk else stats.spa).toDouble()
            val eff = wildTypes?.let { TypeChart.eff(m.type, it) } ?: 1.0
            best = maxOf(best, power * stab * acc * atk * eff)
        }
        return best
    }

    // ---- values ----

    /**
     * The number [spec] sorts the owned Pokémon [mon] by; null = no value (always sorted last). [SortKey.NAME] has no number
     * (see [nameOf]). Dates: caught date and first seen are epoch ms; an unknown date (<= 0 / never seen) is null.
     */
    fun value(data: GameData, state: GameState, mon: Mon, spec: SortSpec): Double? = when (val k = effectiveKey(spec, state)) {
        SortKey.LEVEL -> mon.level.toDouble()
        SortKey.TOTAL, SortKey.STAT, SortKey.CHOSEN_TOTAL -> statsFor(data, mon, spec)?.let { statValue(it, spec.copy(key = k)) }
        SortKey.STRONGEST_ATTACK -> attackScore(data, mon, spec)
        SortKey.VS_WILD -> attackScore(data, mon, spec, state.battle?.wild)
        SortKey.NAME -> null
        SortKey.DEX_NUMBER -> (data.species[mon.species]?.baseNum ?: mon.species).toDouble()
        SortKey.CAUGHT_DATE -> mon.caughtAt.takeIf { it > 0 }?.toDouble()
        SortKey.FIRST_SEEN -> state.firstSeen(mon.species)?.takeIf { it > 0 }?.toDouble()
    }

    /** What [SortKey.NAME] compares: the nickname first, else the species name, case-insensitive. */
    fun nameOf(mon: Mon): String = mon.shownName.lowercase()

    /**
     * The number [spec] sorts the Pokédex entry [sp] by (null = no value, sorted last whatever the direction). Under
     * [StatBasis.CURRENT] and for LEVEL the best owned copy counts (highest value); a species you don't own has none.
     * Keys that need moves or a catch date ([SortKey.STRONGEST_ATTACK], [SortKey.VS_WILD], [SortKey.CAUGHT_DATE]) have no value here.
     */
    fun speciesValue(data: GameData?, state: GameState?, sp: Species, spec: SortSpec): Double? = when (spec.key) {
        SortKey.DEX_NUMBER -> sp.baseNum.toDouble()
        SortKey.NAME, SortKey.STRONGEST_ATTACK, SortKey.VS_WILD, SortKey.CAUGHT_DATE -> null
        SortKey.FIRST_SEEN -> state?.firstSeen(sp.num)?.takeIf { it > 0 }?.toDouble()
        SortKey.LEVEL -> state?.mons?.filter { it.species == sp.num }?.maxOfOrNull { it.level }?.toDouble()
        SortKey.TOTAL, SortKey.STAT, SortKey.CHOSEN_TOTAL ->
            if (spec.basis == StatBasis.CURRENT) {
                if (data == null || state == null) null
                else state.mons.filter { it.species == sp.num }.mapNotNull { m -> statsFor(data, m, spec)?.let { statValue(it, spec) } }.maxOrNull()
            } else speciesStats(sp, spec)?.let { statValue(it, spec) }
    }

    // ---- ordering ----

    /**
     * Orders [items]: by the number from [num] (or the text from [text]) in [descending] direction; items without a number go
     * last in either direction; ties keep the fixed [tie] order, so equal values never jump around.
     */
    internal fun <T> ordered(
        items: List<T>, descending: Boolean, num: ((T) -> Double?)?, text: ((T) -> String)?, tie: Comparator<T>,
    ): List<T> {
        val nums = IdentityHashMap<T, Double?>()
        val texts = IdentityHashMap<T, String>()
        for (x in items) { if (num != null) nums[x] = num(x); if (text != null) texts[x] = text(x) }
        val cmp = Comparator<T> { a, b ->
            val c = when {
                text != null -> texts.getValue(a).compareTo(texts.getValue(b)).let { if (descending) -it else it }
                else -> {
                    val x = nums[a]; val y = nums[b]
                    when {
                        x == null && y == null -> 0
                        x == null -> 1
                        y == null -> -1
                        else -> x.compareTo(y).let { if (descending) -it else it }
                    }
                }
            }
            if (c != 0) c else tie.compare(a, b)
        }
        return items.sortedWith(cmp)
    }

    /** Species number, then level (high first), then uid: the order equal values keep. */
    private val monTie: Comparator<Mon> = compareBy<Mon>({ it.species }, { -it.level }, { it.uid })

    /** [mons] (the Box, the Team picker list) in [spec]'s order. */
    fun sort(data: GameData, state: GameState, mons: List<Mon>, spec: SortSpec): List<Mon> {
        val named = spec.key == SortKey.NAME
        return ordered(mons, spec.descending, if (named) null else { m -> value(data, state, m, spec) }, if (named) ::nameOf else null, monTie)
    }

    /**
     * Pokédex entries in [spec]'s order. [state] supplies owned copies and first-seen dates (null = none known, so LEVEL and
     * CURRENT give nobody a value). Equal values keep national number order.
     */
    fun sortSpecies(data: GameData?, state: GameState?, species: List<Species>, spec: SortSpec): List<Species> {
        val named = spec.key == SortKey.NAME
        return ordered(
            species, spec.descending,
            if (named) null else { sp -> speciesValue(data, state, sp, spec) },
            if (named) { sp -> sp.name.lowercase() } else null,
            compareBy({ it.baseNum }, { it.num }),
        )
    }

    /**
     * Groups of copies (Box tiles) ordered by their best member: each group is sorted with [spec] and its first member
     * stands for it. The members' own order is not touched.
     */
    fun <G> sortGroups(data: GameData, state: GameState, groups: List<G>, members: (G) -> List<Mon>, spec: SortSpec): List<G> {
        val byRep = IdentityHashMap<Mon, G>()
        val reps = ArrayList<Mon>()
        for (g in groups) {
            val rep = sort(data, state, members(g), spec).firstOrNull() ?: continue
            byRep[rep] = g; reps += rep
        }
        return sort(data, state, reps, spec).map { byRep.getValue(it) }
    }
}
