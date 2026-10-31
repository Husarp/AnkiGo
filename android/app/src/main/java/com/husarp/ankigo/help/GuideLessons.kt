package com.husarp.ankigo.help

/** The brief lesson stays visible; the existing exact rules remain available in named folds. */
internal object GuideLessons {
    fun shortPoints(ch: GuideChapter): List<String> = when (ch.id) {
        GameGuide.BATTLES -> listOf(
            ch.points[0],
            "Good and Easy power your Main's attack; Again and Hard power the wild Pokémon's attack.",
            "Both can attack in the same round. Only Good and Easy answers keep you safe.",
            "Win to earn XP. An Exp. Share holder shares it; Lucky Egg boosts it.",
        )
        else -> ch.points.take(4).filter { it.length <= 180 }
    }

    fun details(ch: GuideChapter): List<Pair<String, List<String>>> {
        if (ch.id == GameGuide.BATTLES) return listOf(
            "Answer power and OK" to ch.points.slice(1..6),
            "Switching and items · a switch costs one hit" to ch.points.slice(7..9),
            "Move choice · Smart is the default" to ch.points.slice(10..11),
            "XP and held items" to ch.points.drop(12),
        )
        val remaining = ch.points.filter { it !in shortPoints(ch) }
        return remaining.map { p ->
            val title = p.substringBefore(':').takeIf { it.length <= 40 }
                ?: p.substringBefore('.').split(' ').take(5).joinToString(" ")
            title to listOf(p)
        }
    }
}
