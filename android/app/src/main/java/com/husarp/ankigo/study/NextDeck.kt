package com.husarp.ankigo.study

/**
 * PLAN 147: the deck the finished screen's "Next deck" offers. Nearest relative first: a sibling (same parent) and its subdecks, then
 * one level up, and so on to any deck. Never the finished deck, its subdecks or an ancestor (studying the parent mixes every
 * subdeck's cards). On one level a deck without subdecks comes before one with them, then tree order from the finished deck on, wrapping.
 */
object NextDeck {
    data class D(val id: Long, val name: String, val total: Int)

    fun pick(decks: List<D>, currentId: Long, currentName: String): D? {
        val inTree = decks.sortedWith { a, b -> compareParts(a.name, b.name) }
        fun under(name: String, root: String) = name.startsWith("$root::")
        val ok = inTree.filter {
            it.total > 0 && it.id != currentId && it.name != currentName && !under(it.name, currentName) && !under(currentName, it.name)
        }
        if (ok.isEmpty()) return null
        val after = { d: D -> if (compareParts(d.name, currentName) > 0) 0 else 1 }   // from the finished deck on, then wrap round
        val leaf = { d: D -> if (inTree.any { under(it.name, d.name) }) 1 else 0 }
        var level = currentName.substringBeforeLast("::", "")
        while (true) {
            val here = if (level.isEmpty()) ok else ok.filter { under(it.name, level) }
            here.minWithOrNull(compareBy(leaf, after).thenComparator { a, b -> compareParts(a.name, b.name) })?.let { return it }
            if (level.isEmpty()) return null
            level = level.substringBeforeLast("::", "")
        }
    }

    /** Tree order: "A::B" right after "A", and part by part, ignoring case. */
    private fun compareParts(a: String, b: String): Int {
        val x = a.split("::"); val y = b.split("::")
        for (i in 0 until minOf(x.size, y.size)) {
            val c = x[i].compareTo(y[i], ignoreCase = true)
            if (c != 0) return c
        }
        return x.size - y.size
    }

    /** PLAN 152: the card's own deck below the opened one, e.g. "Kanji › N5"; null when it is the opened deck (or not below it). */
    fun subPath(opened: String, cardDeck: String?): String? =
        cardDeck?.takeIf { it.startsWith("$opened::") }?.removePrefix("$opened::")?.replace("::", " › ")
}
