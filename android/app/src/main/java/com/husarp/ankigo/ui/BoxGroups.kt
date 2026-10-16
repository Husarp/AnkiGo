package com.husarp.ankigo.ui

import com.husarp.ankigo.game.GameData
import com.husarp.ankigo.game.Mon

/**
 * Box grouping ("×N" tiles): copies of one species collapse into one group. Pure logic, no views.
 * [shinySeparate]: shinies form their own group; [formSeparate]: regional forms (Species.num > 10000) group by their
 * own number instead of with the base species.
 */
object BoxGroups {
    class Group(
        /** Stable key: species number, plus "s" for a separate shiny group, e.g. "25", "25s", "10100". */
        val key: String,
        /** The species number the group sorts by (base number when forms are merged). */
        val num: Int,
        /** Highest level first; tie: shiny, then latest caught. */
        val members: List<Mon>,
    ) {
        val count: Int get() = members.size
        /** The copy whose sprite the tile shows. */
        val best: Mon get() = members.first()
        /** A group of one is a plain tile (no badge). */
        val isSingle: Boolean get() = members.size == 1
    }

    /** Groups [mons] in today's Box order (species, then level); groups sort by species number, shiny group after the normal one. */
    fun group(data: GameData, mons: List<Mon>, shinySeparate: Boolean, formSeparate: Boolean): List<Group> {
        val byKey = LinkedHashMap<String, MutableList<Mon>>()
        val numOf = HashMap<String, Int>()
        val shinyOf = HashMap<String, Boolean>()
        for (m in mons) {
            val num = if (formSeparate) m.species else data.species[m.species]?.baseNum ?: m.species
            val sh = shinySeparate && m.shiny
            val key = if (sh) "${num}s" else "$num"
            byKey.getOrPut(key) { ArrayList() }.add(m)
            numOf[key] = num; shinyOf[key] = sh
        }
        val bestFirst = compareBy<Mon>({ -it.level }, { if (it.shiny) 0 else 1 }, { -it.caughtAt })
        return byKey.map { (key, list) -> Group(key, numOf.getValue(key), list.sortedWith(bestFirst)) }
            .sortedWith(compareBy<Group>({ it.num }, { shinyOf.getValue(it.key) }))
    }
}
