package com.husarp.ankigo.ui

/** Stable keys: discard stale/duplicate entries and append any newly introduced sections. */
object DetailOrder {
    val default: List<DetailSection> = DetailSection.entries.toList()
    fun resolve(keys: List<String>): List<DetailSection> =
        (keys.mapNotNull { k -> default.firstOrNull { it.key == k } } + default).distinct()

    fun move(order: List<DetailSection>, section: DetailSection, to: Int): List<DetailSection> = order.toMutableList().apply {
        if (remove(section)) add(to.coerceIn(0, size), section)
    }

    fun label(section: DetailSection): String = if (section == DetailSection.EVOLUTION) "Holding & Evolution" else section.title
}
