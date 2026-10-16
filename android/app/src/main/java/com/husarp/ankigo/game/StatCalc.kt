package com.husarp.ankigo.game

import kotlin.math.floor

/** Ankimon's stat formulas (pokemon_obj.py): standard Gen 3+ with IVs, EVs and nature. */
object StatCalc {
    fun hp(base: Int, iv: Int, ev: Int, level: Int): Int =
        10 + level + floor((2 * base + iv + ev / 4) * level / 100.0).toInt()

    fun other(base: Int, iv: Int, ev: Int, level: Int, natureMult: Double): Int =
        floor((5 + floor((2 * base + iv + ev / 4) * level / 100.0).toInt()) * natureMult).toInt()

    fun stats(sp: Species, mon: Mon): Stats = Stats(
        hp(sp.base.hp, mon.ivs.hp, mon.evs.hp, mon.level),
        other(sp.base.atk, mon.ivs.atk, mon.evs.atk, mon.level, mon.nature.mult(Stat.ATK)),
        other(sp.base.def, mon.ivs.def, mon.evs.def, mon.level, mon.nature.mult(Stat.DEF)),
        other(sp.base.spa, mon.ivs.spa, mon.evs.spa, mon.level, mon.nature.mult(Stat.SPA)),
        other(sp.base.spd, mon.ivs.spd, mon.evs.spd, mon.level, mon.nature.mult(Stat.SPD)),
        other(sp.base.spe, mon.ivs.spe, mon.evs.spe, mon.level, mon.nature.mult(Stat.SPE)),
    )

    /**
     * Lowest and highest value a species can have in [stat] at [level] (Dex details): min = IV 0, EV 0, hindering nature;
     * max = IV 31, EV 252, helping nature. HP has no nature.
     */
    fun range(base: Int, stat: Stat, level: Int = 100): IntRange =
        if (stat == Stat.HP) hp(base, 0, 0, level)..hp(base, 31, 252, level)
        else other(base, 0, 0, level, 0.9)..other(base, 31, 252, level, 1.1)

    fun stats(data: GameData, mon: Mon): Stats = stats(data.sp(mon.species), mon)
    fun maxHp(data: GameData, mon: Mon): Int = stats(data, mon).hp
}
