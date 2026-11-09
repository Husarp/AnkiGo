package com.husarp.ankigo.help

/** The brief lesson stays visible; the existing exact rules remain available in named folds. */
internal object GuideLessons {
    fun shortPoints(ch: GuideChapter): List<String> = when (ch.id) {
        GameGuide.BATTLES -> listOf(
            ch.points[0],
            "Good and Easy power your Main's attack; Again and Hard power the wild Pokémon's attack.",
            "Both can attack in the same round. With only Good and Easy, the wild Pokémon gets no move.",
            "Defeat or catch a wild Pokémon to earn XP. Exp. Share helps another Pokémon grow; Lucky Egg boosts XP.",
        )
        else -> ch.points.take(4).filter { it.length <= 180 }
    }

    data class Section(val title: String, val icon: Int, val lines: List<String>)

    fun sections(ch: GuideChapter): List<Section> = when (ch.id) {
        GameGuide.BATTLES -> listOf(
            Section("Answer to attack", com.husarp.ankigo.R.drawable.ic_swords, shortPoints(ch).take(3)),
            Section("Win XP together", com.husarp.ankigo.R.drawable.ic_ach_levels, shortPoints(ch).drop(3)),
        )
        GameGuide.MOVES -> listOf(
            Section("Rounds and timing", com.husarp.ankigo.R.drawable.ic_clock, listOf(ch.points[1])),
            Section("Stages change stats", com.husarp.ankigo.R.drawable.ic_stats, listOf(ch.points[0].substringBefore("Factors below").trim())),
            Section("A faster move can interrupt", com.husarp.ankigo.R.drawable.ic_bolt, listOf(ch.points[3])),
        )
        else -> listOf(Section("The essentials", HelpViews.chapterIcon(ch.id), shortPoints(ch).ifEmpty { ch.points.take(1) }))
    }

    fun details(ch: GuideChapter): List<Pair<String, List<String>>> {
        if (ch.id == GameGuide.BATTLES) return listOf(
            "Answer power and OK" to ch.points.slice(1..6),
            "Switching and items · when the wild Pokémon gets a free move" to ch.points.slice(7..9),
            "Move choice · Smart is the default" to ch.points.slice(10..11),
            "XP and held items" to ch.points.drop(12),
        )
        val giftPoints = if (ch.id == GameGuide.MONEY) ch.points.slice(4..6) else emptyList()
        val remaining = ch.points.filter { it !in shortPoints(ch) && it !in giftPoints }
        val giftSection = if (giftPoints.isEmpty()) emptyList() else listOf("Daily gift and goal streak" to giftPoints)
        return giftSection + remaining.map { p ->
            val title = p.substringBefore(':').takeIf { it.length <= 40 }
                ?: p.substringBefore('.').split(' ').take(5).joinToString(" ")
            title to listOf(p)
        }
    }
}
