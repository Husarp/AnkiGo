package com.husarp.ankigo.game

import kotlin.math.floor

/** Ankimon's stat formulas (pokemon_obj.py): standard Gen 3+ with IVs, EVs and nature. */
object StatCalc {
    /** R17C-FIX1: the mainline Shedinja rule: a species with base HP 1 has 1 HP at any level, IV and EV (so its Wonder Guard can be beaten). */
    const val SHEDINJA_BASE_HP = 1

    fun hp(base: Int, iv: Int, ev: Int, level: Int): Int =
        if (base == SHEDINJA_BASE_HP) 1 else 10 + level + floor((2 * base + iv + ev / 4) * level / 100.0).toInt()

    fun other(base: Int, iv: Int, ev: Int, level: Int, natureMult: Double): Int =
        floor((5 + floor((2 * base + iv + ev / 4) * level / 100.0).toInt()) * natureMult).toInt()

    fun stats(sp: Species, mon: Mon): Stats = stats(sp, mon, mon.level)

    /** [mon]'s IVs, EVs and nature on [sp]'s base stats at [level] (Compare's "Fully evolved" uses the level the final form is reached). */
    fun stats(sp: Species, mon: Mon, level: Int): Stats = Stats(
        hp(sp.base.hp, mon.ivs.hp, mon.evs.hp, level),
        other(sp.base.atk, mon.ivs.atk, mon.evs.atk, level, mon.nature.mult(Stat.ATK)),
        other(sp.base.def, mon.ivs.def, mon.evs.def, level, mon.nature.mult(Stat.DEF)),
        other(sp.base.spa, mon.ivs.spa, mon.evs.spa, level, mon.nature.mult(Stat.SPA)),
        other(sp.base.spd, mon.ivs.spd, mon.evs.spd, level, mon.nature.mult(Stat.SPD)),
        other(sp.base.spe, mon.ivs.spe, mon.evs.spe, level, mon.nature.mult(Stat.SPE)),
    )

    /**
     * Lowest and highest value a species can have in [stat] at [level] (Dex details): min = IV 0, EV 0, hindering nature;
     * max = IV 31, EV 252, helping nature. HP has no nature; a base HP of 1 (Shedinja) gives 1..1.
     */
    fun range(base: Int, stat: Stat, level: Int = 100): IntRange =
        if (stat == Stat.HP) hp(base, 0, 0, level)..hp(base, 31, 252, level)
        else other(base, 0, 0, level, 0.9)..other(base, 31, 252, level, 1.1)

    fun stats(data: GameData, mon: Mon): Stats = stats(data.sp(mon.species), mon)
    fun maxHp(data: GameData, mon: Mon): Int = stats(data, mon).hp
}
