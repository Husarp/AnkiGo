package com.husarp.ankigo.game

/** PLAN 491: fixed catalogues; saved per-kind markers start with this release, never inventory backfill. */
object ItemCollections {
    val kinds: Map<Metric, Set<String>> = linkedMapOf(
        Metric.PANTRY_KINDS to setOf("sweet-heart", "berry-juice", "fresh-water", "soda-pop", "energy-powder", "lemonade", "moomoo-milk", "energy-root"),
        Metric.REGULAR_BALL_KINDS to setOf("poke-ball", "great-ball", "ultra-ball"),
        Metric.SPECIAL_BALL_KINDS to setOf("premier-ball", "net-ball", "dive-ball", "dusk-ball", "heal-ball", "luxury-ball", "nest-ball", "quick-ball", "repeat-ball", "timer-ball"),
        Metric.BERRY_KINDS to setOf("oran-berry", "sitrus-berry", "figy-berry", "liechi-berry", "ganlon-berry", "petaya-berry", "apicot-berry", "salac-berry"),
        Metric.EQUIPMENT_KINDS to setOf("everstone", "amulet-coin", "luck-incense", "lucky-egg", "macho-brace", "soothe-bell", "power-weight", "power-bracer", "power-belt", "power-lens", "power-band", "power-anklet"),
        Metric.STONE_KINDS to setOf("fire-stone", "water-stone", "thunder-stone", "leaf-stone", "moon-stone", "sun-stone", "shiny-stone", "dusk-stone", "dawn-stone", "ice-stone"),
    )

    private fun key(metric: Metric, item: String) = "collection/${metric.name}/$item"

    fun record(state: GameState, metric: Metric, item: String) {
        if (item in kinds[metric].orEmpty()) state.counters[key(metric, item)] = 1L
    }

    fun value(state: GameState, metric: Metric): Long =
        kinds[metric].orEmpty().count { (state.counters[key(metric, it)] ?: 0L) > 0 }.toLong()
}
