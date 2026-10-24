package com.husarp.ankigo.ui

import java.util.Locale

/** One matching setting for the Settings search box: [info] plus the line "Game · Battle" saying where it lives. */
data class SettingHit(val info: SettingInfo, val where: String)

/** One group of rows inside a Settings section: its title ("" = none) and the indices of its rows, in the order built. */
data class SettingGroup(val title: String, val rows: List<Int>)

/** Pure helpers for the Settings tab (search box, grouping, where a setting lives). No Android. */
object SettingsSearch {
    /** Lower case, without accents, so "pokemon" finds "Pokémon". */
    private fun norm(s: String): String =
        java.text.Normalizer.normalize(s.lowercase(Locale.ROOT), java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

    /**
     * The settings whose title, explanation, group or section words contain every word of [query] (a word may be the start of
     * a longer one or sit inside it). Blank query = nothing. Title matches come first (starts-with before contains), then
     * explanation/group/section matches, each keeping the catalog order.
     */
    fun match(query: String, entries: List<SettingInfo> = SettingsCatalog.all): List<SettingHit> {
        val words = norm(query).split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()
        val ranked = ArrayList<Pair<Int, SettingInfo>>()
        for (e in entries) {
            val title = norm(e.title)
            val rest = norm("${e.explain} ${e.sub} ${sectionOf(e).title} ${e.section.title}")
            if (!words.all { it in title || it in rest }) continue
            val rank = when {
                words.all { it in title } && title.startsWith(words[0]) -> 0
                words.all { it in title } -> 1
                else -> 2
            }
            ranked += rank to e
        }
        return ranked.sortedBy { it.first }.map { SettingHit(it.second, whereOf(it.second)) }
    }

    /** "Game · Battle", or just "Look" when the setting has no group. */
    fun whereOf(e: SettingInfo): String = if (e.sub.isEmpty()) sectionOf(e).title else "${sectionOf(e).title} · ${e.sub}"

    /** The Settings home section that shows [e] (the catalog's "Data & import" is split over Downloads / Ankimon / Save). */
    fun sectionOf(e: SettingInfo): SettingsSection = when (e.section) {
        CatalogSection.LOOK -> SettingsSection.LOOK
        CatalogSection.SOUNDS -> SettingsSection.SOUND
        CatalogSection.BATTLE -> SettingsSection.BATTLE
        CatalogSection.STUDY -> SettingsSection.STUDY
        CatalogSection.WIDGETS -> SettingsSection.WIDGETS
        CatalogSection.REMINDERS -> SettingsSection.REMINDERS
        CatalogSection.GAME -> SettingsSection.GAME
        CatalogSection.DATA -> when (e.sub) {
            "Ankimon PC sync" -> SettingsSection.ANKIMON
            "Save & backups" -> SettingsSection.SAVE
            else -> SettingsSection.DOWNLOADS
        }
        CatalogSection.UPDATES -> SettingsSection.UPDATES
        CatalogSection.DIAGNOSTICS -> SettingsSection.DIAGNOSTICS
        CatalogSection.ABOUT -> SettingsSection.ABOUT
    }

    /** The catalog entries shown inside [s] (what the explanation lines are looked up in). */
    fun entriesOf(s: SettingsSection, entries: List<SettingInfo> = SettingsCatalog.all): List<SettingInfo> =
        entries.filter { sectionOf(it) == s }

    /**
     * Groups rows by their catalog group title: [titles] has one entry per row (its group, "" for none), the result lists the
     * groups in the order of [first] (the group titles named there that exist), then the rest in the order their first row
     * appears; each group has its row indices in the order given.
     */
    fun groups(titles: List<String>, first: List<String> = emptyList()): List<SettingGroup> {
        val order = LinkedHashMap<String, MutableList<Int>>()
        for (t in first) if (t in titles) order[t] = ArrayList()
        titles.forEachIndexed { i, t -> order.getOrPut(t) { ArrayList() } += i }
        return order.map { SettingGroup(it.key, it.value) }
    }
}
