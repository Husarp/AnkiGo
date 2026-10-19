package com.husarp.ankigo.ui

import com.husarp.ankigo.game.EvoLine
import com.husarp.ankigo.game.GameData
import com.husarp.ankigo.game.GameState
import com.husarp.ankigo.game.Evolution
import com.husarp.ankigo.game.Mon
import com.husarp.ankigo.game.Species
import com.husarp.ankigo.game.Stat
import com.husarp.ankigo.game.StatCalc
import com.husarp.ankigo.game.Stats
import com.husarp.ankigo.game.Type
import com.husarp.ankigo.game.TypeMatchups
import com.husarp.ankigo.game.shownName

// PLAN item 9 (U-10): the data behind the Compare dialog. Pure Kotlin (no android.*): unit-tested on the JVM.

/** One move in a Compare cell: [type] is null for a move the data doesn't know; [power] is "90" or "—"; [id] is the move's id (R10-F: a tap opens its popup). */
data class MoveCell(val name: String, val type: Type?, val power: String, val id: String = "")

/** One matchup entry (now in game/TypeMatchups.kt, R4-04): attacking type, multiplier 4, 2, 0.5, 0.25 or 0. */
typealias Matchup = com.husarp.ankigo.game.Matchup

/** What one table cell shows. Only [Text] can be the best value of its row. */
sealed class Cell {
    /** A value; [sub] are small grey lines under it. "—" (no value) is a Text with [empty]. [value] is the raw number (null = none / not numeric). */
    data class Text(val text: String, val best: Boolean = false, val sub: List<String> = emptyList(), val level: Int? = null, val value: Int? = null) : Cell() {
        val empty: Boolean get() = text == DASH
    }
    data class TypeChips(val types: List<Type>) : Cell()
    data class Moves(val moves: List<MoveCell>) : Cell()
    data class Matchups(val entries: List<Matchup>) : Cell()
    /** The evolution line, [mark] = index of the picked Pokémon in [names]; [note] explains a branch. */
    data class Evo(val names: List<String>, val mark: Int, val note: String? = null) : Cell() {
        val line: String get() = names.joinToString(" → ")
    }
}

/**
 * One column. [shown] is the species the numbers come from (the picked one, or its last evolution in "Fully evolved");
 * [picked] is always the species that was picked. [mon] is null for a Pokédex entry. [note] is the small line under the name.
 */
data class CompareColumn(
    val pick: ComparePick,
    val mon: Mon?,
    val shown: Species,
    val picked: Species,
    val name: String,
    val shiny: Boolean,
    val note: String?,
    /** "Fully evolved" shows a final form the player hasn't seen: its sprite is a silhouette (PLAN 128). */
    val hidden: Boolean = false,
)

/**
 * A labelled row; [section] (when set) starts a new group with that heading. [numeric] rows carry a [Cell.Text.value] in
 * their cells and a higher value is better (R3-09: lets Compare modes diff, rank and sort by them).
 */
data class CompareRow(val key: String, val label: String, val cells: List<Cell>, val section: String? = null, val numeric: Boolean = false)

data class CompareData(val columns: List<CompareColumn>, val rows: List<CompareRow>) {
    fun row(key: String): CompareRow = rows.first { it.key == key }
    fun rowOrNull(key: String): CompareRow? = rows.firstOrNull { it.key == key }
}

const val DASH = "—"

object CompareTable {
    private val STAT_LABELS = listOf("HP", "Atk", "Def", "SpA", "SpD", "Spe")

    /** The last species of [sp]'s line (first branch), how many branches the first branching step had (1 = none). */
    data class FinalForm(val species: Species, val branches: Int)

    private fun nextOf(data: GameData, sp: Species): List<Species> =
        sp.evolutions.mapNotNull { data.species[it.to] }.distinctBy { it.num }

    fun finalForm(data: GameData, sp: Species): FinalForm {
        var cur = sp
        var branches = 1
        val seen = HashSet<Int>().also { it += sp.num }
        while (true) {
            val next = nextOf(data, cur)
            if (next.isEmpty()) break
            if (branches == 1 && next.size > 1) branches = next.size
            val n = next.first()
            if (!seen.add(n.num)) break
            cur = n
        }
        return FinalForm(cur, branches)
    }

    /** The first branch out of [sp] with its evolution rule (the same step [finalForm] takes), or null at the end of the line. */
    private fun firstStep(data: GameData, sp: Species): Pair<Evolution, Species>? =
        sp.evolutions.mapNotNull { e -> data.species[e.to]?.let { e to it } }.distinctBy { it.second.num }.firstOrNull()

    /**
     * R9-B10: the level [mon] has once it reaches the final form of [picked]: its current level, raised to every evolution level
     * along the first-branch path picked → final (a step without a level, like a stone or friendship, adds nothing). Equals
     * [Mon.level] when [picked] is already final or the Pokémon is already past those levels.
     */
    fun evolvedLevel(data: GameData, picked: Species, mon: Mon): Int {
        var level = mon.level
        var cur = picked
        val seen = HashSet<Int>().also { it += picked.num }
        while (true) {
            val (evo, next) = firstStep(data, cur) ?: break
            if (!seen.add(next.num)) break
            evo.level?.let { if (it > level) level = it }
            cur = next
        }
        return level
    }

    /** The line "root → ... → picked → ... → final" (first branch after the picked one), mark index, branch count after it. */
    fun evolutionLine(data: GameData, sp: Species, seen: Set<Int>? = null): Cell.Evo {
        fun label(s: Species) = if (seen == null || s.num == sp.num) s.name else EvoLine.shownName(s, seen)
        val before = ArrayList<Species>()
        val seen = HashSet<Int>().also { it += sp.num }
        var up = sp.prevo?.let { data.species[it] }
        while (up != null && seen.add(up.num)) {
            before.add(0, up)
            up = up.prevo?.let { data.species[it] }
        }
        val names = before.map { label(it) } + sp.name
        val fin = finalForm(data, sp)
        // species after the picked one, following the first branch
        val after = ArrayList<Species>()
        var cur = sp
        val s2 = HashSet<Int>().also { it += sp.num }
        while (true) {
            val n = nextOf(data, cur).firstOrNull() ?: break
            if (!s2.add(n.num)) break
            after += n; cur = n
        }
        val note = if (fin.branches > 1) "first of ${fin.branches} branches" else null
        return Cell.Evo(names + after.map { label(it) }, before.size, note)
    }

    /**
     * Indexes of the best value(s): the highest of the non-null [values] (ties: all of them), or none when fewer than two
     * Pokémon have a value (nothing to compare).
     */
    fun bestIndexes(values: List<Int?>): Set<Int> {
        if (values.count { it != null } < 2) return emptySet()
        val top = values.filterNotNull().max()
        return values.indices.filter { values[it] == top }.toSet()
    }

    private fun numeric(key: String, label: String, values: List<Int?>, section: String? = null, fmt: (Int) -> String = { it.toString() }): CompareRow {
        val best = bestIndexes(values)
        return CompareRow(key, label, values.mapIndexed { i, v -> Cell.Text(v?.let(fmt) ?: DASH, best = i in best, level = if (key == "level") v else null, value = v) }, section, numeric = true)
    }

    fun matchups(types: List<Type>): Triple<List<Matchup>, List<Matchup>, List<Matchup>> {
        val m = TypeMatchups.of(types)
        return Triple(m.weakTo, m.resists, m.immune)
    }

    private fun natureCell(m: Mon): Cell.Text {
        val n = m.nature
        val eff = if (n.plus == null || n.minus == null) "neutral" else "+${STAT_LABELS[n.plus.ordinal]} −${STAT_LABELS[n.minus.ordinal]}"
        return Cell.Text(n.label, sub = listOf(eff))
    }

    fun build(data: GameData, state: GameState, picks: List<ComparePick>, evolved: Boolean): CompareData {
        val columns = picks.mapNotNull { p ->
            when (p) {
                is ComparePick.Owned -> state.mon(p.uid)?.let { m -> data.species[m.species]?.let { sp -> Triple(p, m, sp) } }
                is ComparePick.Dex -> data.species[p.species]?.let { sp -> Triple(p, null, sp) }
            }
        }.map { (pick, mon, sp) ->
            val fin = finalForm(data, sp)
            val shown = if (evolved) fin.species else sp
            val changed = evolved && fin.species.num != sp.num
            val note = when {
                !evolved -> null
                !changed -> "already final"
                fin.branches > 1 -> "from ${sp.name} · first of ${fin.branches}"
                else -> "from ${sp.name}"
            }
            val hidden = changed && !EvoLine.isSeen(shown, state.seen + state.caught)
            val name = if (mon != null && !mon.nickname.isNullOrBlank()) mon.shownName else if (hidden) "???" else shown.name // an unseen final form stays "???" (PLAN 128)
            CompareColumn(pick, mon, shown, sp, name, mon?.shiny == true, note, hidden)
        }

        val owned = columns.map { it.mon }
        val anyOwned = owned.any { it != null }
        // "Fully evolved": an owned column whose form changed shows the level its final form is first reached at (R9-B10)
        val levels: List<Int?> = columns.map { c ->
            c.mon?.let { m -> if (evolved && c.shown.num != c.picked.num) evolvedLevel(data, c.picked, m) else m.level }
        }
        val stats: List<Stats?> = columns.mapIndexed { i, c -> c.mon?.let { StatCalc.stats(c.shown, it, levels[i] ?: it.level) } }
        val rows = ArrayList<CompareRow>()

        if (anyOwned) {
            val row = numeric("level", "Level", levels) { "Lv $it" }
            // the small grey line "evolves" marks a level raised by the evolution (a sub line, so a narrow column never wraps the "Lv")
            rows += row.copy(cells = row.cells.mapIndexed { i, c ->
                val raised = owned[i]?.let { m -> (levels[i] ?: m.level) > m.level } == true
                if (raised && c is Cell.Text) c.copy(sub = c.sub + "evolves") else c
            })
        }
        rows += CompareRow("types", "Types", columns.map { Cell.TypeChips(it.shown.types) })

        if (anyOwned) {
            Stat.entries.forEachIndexed { i, s ->
                rows += numeric(s.name.lowercase(), STAT_LABELS[i], stats.map { it?.get(s) }, section = if (i == 0) "Stats" else null)
            }
            rows += numeric("total", "Total", stats.map { it?.total })
        }

        Stat.entries.forEachIndexed { i, s ->
            rows += numeric("base_" + s.name.lowercase(), STAT_LABELS[i], columns.map { it.shown.base[s] }, section = if (i == 0) "Base stats" else null)
        }
        rows += numeric("base_total", "Total", columns.map { it.shown.base.total })

        if (anyOwned) {
            rows += CompareRow("nature", "Nature", owned.map { m -> m?.let(::natureCell) ?: Cell.Text(DASH) }, section = "Build")
            val ivSums = owned.map { it?.ivs?.total }
            val best = bestIndexes(ivSums)
            rows += CompareRow("ivs", "IVs", owned.mapIndexed { i, m ->
                if (m == null) Cell.Text(DASH) else {
                    val iv = m.ivs.toList()
                    Cell.Text("${m.ivs.total} / 186", best = i in best,
                        sub = listOf(iv.take(3).joinToString(" · "), iv.drop(3).joinToString(" · ")), value = m.ivs.total)
                }
            }, numeric = true)
            rows += CompareRow("moves", "Moves", owned.map { m ->
                if (m == null) Cell.Text(DASH) else Cell.Moves(m.moves.map { id ->
                    val mv = data.move(id)
                    if (mv == null) MoveCell(id, null, DASH, id)
                    else MoveCell(mv.name, mv.type, if (mv.damaging && mv.power > 0) mv.power.toString() else DASH, id)
                })
            }, section = "Moves")
        }

        val m = columns.map { matchups(it.shown.types) }
        rows += CompareRow("weak", "Weak against", m.map { Cell.Matchups(it.first) }, section = "Matchups")
        rows += CompareRow("resist", "Resists", m.map { Cell.Matchups(it.second) })
        rows += CompareRow("immune", "Immune to", m.map { Cell.Matchups(it.third) })

        val lines = columns.map { evolutionLine(data, it.picked, state.seen + state.caught) }
        rows += CompareRow("evo", "Line", lines, section = "Evolution")
        rows += CompareRow("becomes", "Becomes", columns.map { c ->
            val fin = finalForm(data, c.picked)
            if (fin.species.num == c.picked.num) Cell.Text("already final")
            else Cell.Text(EvoLine.shownName(fin.species, state.seen + state.caught), sub = if (fin.branches > 1) listOf("first of ${fin.branches}") else emptyList())
        })

        return CompareData(columns, rows)
    }
}
