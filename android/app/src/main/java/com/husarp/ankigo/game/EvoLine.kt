package com.husarp.ankigo.game

// PLAN item 60: the whole evolution line of a species for the Info part of the details: root, branches, methods. Pure Kotlin.

/** One species in a line. [via] is the evolution that leads to it from its parent (null for the root). */
data class EvoNode(val species: Species, val via: Evolution?, val children: List<EvoNode>)

/** The line [root] ... with [current] marked. Follows `prevo` up to the base form, then every branch down. */
class EvoLine(val root: EvoNode, val current: Species) {
    /** Every node, parents before children. */
    val nodes: List<EvoNode> get() = ArrayList<EvoNode>().also { out -> fun walk(n: EvoNode) { out += n; n.children.forEach(::walk) }; walk(root) }

    /** Nodes by depth: stage 0 is the root. */
    val stages: List<List<EvoNode>> get() {
        val out = ArrayList<List<EvoNode>>()
        var level = listOf(root)
        while (level.isNotEmpty()) { out += level; level = level.flatMap { it.children } }
        return out
    }

    /** The last species of every branch (leaves), in line order. */
    val finals: List<EvoNode> get() = nodes.filter { it.children.isEmpty() }

    /** True when some species in the line has more than one evolution. */
    val branched: Boolean get() = nodes.any { it.children.size > 1 }

    /** True when the species neither evolves nor evolves from anything. */
    val standalone: Boolean get() = root.children.isEmpty()

    /** Root down to [current] (just [current] when it is not found). */
    val pathToCurrent: List<EvoNode> get() {
        fun find(n: EvoNode): List<EvoNode>? {
            if (n.species.num == current.num) return listOf(n)
            for (c in n.children) find(c)?.let { return listOf(n) + it }
            return null
        }
        return find(root) ?: listOf(EvoNode(current, null, emptyList()))
    }

    companion object {
        /** True when [sp] (or its base form) is in [seen] (the player's seen + caught species); the same check as the hidden sprite. */
        fun isSeen(sp: Species, seen: Set<Int>): Boolean = sp.num in seen || sp.baseNum in seen

        /** The name an evolution chain writes for [sp]: the real name once seen, else "???" (like its silhouette). */
        fun shownName(sp: Species, seen: Set<Int>): String = if (isSeen(sp, seen)) sp.name else "???"

        /** The line of [species]; a regional line stays regional (Perrserker's line starts at the regional pre-form). */
        fun of(data: GameData, species: Species): EvoLine {
            var top = species
            val seen = HashSet<Int>().also { it += species.num }
            while (true) {
                val up = top.prevo?.let { data.species[it] } ?: break
                if (!seen.add(up.num)) break
                top = up
            }
            return EvoLine(build(data, top, null, HashSet()), species)
        }

        private fun build(data: GameData, sp: Species, via: Evolution?, path: MutableSet<Int>): EvoNode {
            if (!path.add(sp.num)) return EvoNode(sp, via, emptyList())
            val kids = sp.evolutions.filter { data.species.containsKey(it.to) }.distinctBy { it.to }
                .map { build(data, data.species.getValue(it.to), it, path) }
            path.remove(sp.num)
            return EvoNode(sp, via, kids)
        }

        fun explanation(data: GameData, e: Evolution): String = buildList {
            add("Reach Level ${Bond.requiredLevel(e)} Bond (${Bond.thresholds[Bond.requiredLevel(e)]} points). Earn +1 point per card while fighting and +3 per win.")
            when (e.kind) {
                EvoKind.LEVEL -> add("Reach level ${e.level ?: 1}.")
                EvoKind.FRIENDSHIP -> add("Build your Bond by fighting together. A held Soothe Bell multiplies Bond points by 1.5.")
                EvoKind.LEVEL_MOVE -> add("Know ${e.move?.let { data.move(it)?.name ?: it } ?: "the required move"}.")
                else -> add("Have ${Items.name(Progression.evolutionItem(e)!!)} in the Bag. Choosing Evolve uses one up.")
            }
            if (e.time == "day") add("Day is 06:00–18:00 on your phone clock.")
            if (e.time == "night") add("Night is 18:00–06:00 on your phone clock.")
            e.moveType?.let { add("Know a ${it.label}-type move.") }
            e.gender?.let { add("Requires a ${if (it == 'M') "male" else "female"} Pokémon.") }
            e.condition?.replace("during the day", "")?.replace("at night", "")?.trim()?.takeIf { it.isNotEmpty() && e.kind != EvoKind.OTHER }?.let {
                val checked = it in setOf("with an Atk stat > its Def stat", "with an Atk stat < its Def stat",
                    "with an Atk stat equal to its Def stat", "with a Dark-type in the party")
                add(it.replaceFirstChar { c -> c.uppercase() } + if (checked) "." else " (this extra condition is not checked in AnkiGo).")
            }
            add("When ready, choose Evolve in your Pokémon's Evolution section. An Everstone blocks evolution.")
        }.joinToString("\n\n")

        /** "Lv 16", "Thunder Stone", "Linking Cord", "Friendship", "knows Mimic" (+ day/night, other conditions). */
        fun method(data: GameData, e: Evolution): String {
            val base = when (e.kind) {
                EvoKind.LEVEL -> if (e.level != null) "Lv ${e.level}" else "Level up"
                EvoKind.FRIENDSHIP -> "Level ${Bond.requiredLevel(e)} Bond"
                EvoKind.ITEM -> e.item?.let { Items.name(it) } ?: "an item"
                EvoKind.TRADE -> Items.name(e.item ?: "linking-cord")
                EvoKind.LEVEL_MOVE -> "knows ${e.move?.let { data.move(it)?.name ?: it } ?: "a move"}"
                EvoKind.OTHER -> "Linking Cord"
            }
            val extra = listOfNotNull(
                e.time,
                e.moveType?.let { "${it.label}-type move" },
                e.condition?.takeIf { e.kind != EvoKind.OTHER }?.replace("during the day", "")?.replace("at night", "")?.trim()?.takeIf { it.isNotEmpty() },
            )
            return if (extra.isEmpty()) base else "$base, ${extra.joinToString(", ")}"
        }
    }
}
