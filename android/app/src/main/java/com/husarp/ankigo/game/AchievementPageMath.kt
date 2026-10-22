package com.husarp.ankigo.game

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// R4-V2 (PLAN 79): the pure parts of the Achievements page, its detail sheet and the "earned" popup. No android.* in here.

/** The page's filter (the "Show" menu, R10 W2: it applies inside the picked tab). */
enum class AchFilter(val label: String) { ALL("All"), CLOSE("Close to next"), RECENT("Recently reached") }

/** Where a row's count comes from (the footnote of the detail sheet). */
enum class AchSource {
    SAVE,          // derived from what the save already holds: earlier progress is included
    HISTORY,       // counted live, started from the save's daily history (kept 400 days) after the update
    SINCE_UPDATE,  // counted live from the update on; starts at 0
    EXTERNAL,      // measured outside the save (AnkiGo's timer files, AnkiDroid's history)
    BADGE,         // an Ankimon badge (a one-off)
}

/**
 * One tier on the detail sheet's ladder. [state]: reached, the one being worked on, or locked. [lockedText] = "next after Epic" / "locked until Legendary".
 * [reward] = the item id this tier puts in the Bag (R14-B2-D: the Master Ball for the top tier of "Best streak"), or null.
 */
class AchStep(val tier: Int, val name: String, val threshold: Long, val state: State, val date: Long?, val lockedText: String?, val inferred: Boolean = false, val reward: String? = null) {
    enum class State { REACHED, CURRENT, LOCKED }
}

/** An achievement (or tier) just earned, for the popup. [tier] -1 = a one-off; [threshold] null for a one-off. */
class AchCelebration(val rowId: String, val title: String, val tier: Int, val tierName: String, val threshold: Long?) {
    /** The big word of the popup ("Epic!"). */
    val word: String get() = if (tier < 0) "Achievement!" else "$tierName!"
}

/** One group of the page: all its rows ([all]) and those the filter lets through ([shown]). A group with nothing shown is left out of the "All" tab; its own tab stays and says so. */
class AchGroup(val name: String, val all: List<AchView>, val shown: List<AchView>) {
    val empty get() = shown.isEmpty()
}

object AchPageMath {
    /** "Close to next": at least this share of the way to the next tier. */
    const val CLOSE_FRACTION = 0.75f

    /** "Recently reached": the last tier was reached within this many days. */
    const val RECENT_DAYS = 14

    private val MONTHS = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

    // ---- filters ----

    /** True when [v] has a next tier and is at least [CLOSE_FRACTION] of the way there. One-offs have no "next". */
    fun close(v: AchView): Boolean = !v.oneOff && v.next != null && v.progress >= CLOSE_FRACTION

    /**
     * True when the last tier of [v] was reached in the last [RECENT_DAYS] days before [now]. A tier whose date is only inferred
     * ([AchView.inferred]: found by a silent look, a backfill or a first external feed, however long after it really happened) and
     * one dated [achSince] (a save from before the flag) were already there when tracking began: they are not "recent".
     */
    fun recent(v: AchView, now: Long, achSince: Long): Boolean {
        if (v.tier < 0 || v.tier in v.inferred) return false
        val at = v.dates.getOrNull(v.tier) ?: return false
        return at > 0L && at != achSince && now - at in 0..RECENT_DAYS * DAY_MS
    }

    fun matches(f: AchFilter, v: AchView, now: Long, achSince: Long): Boolean = when (f) {
        AchFilter.ALL -> true
        AchFilter.CLOSE -> close(v)
        AchFilter.RECENT -> recent(v, now, achSince)
    }

    // ---- groups ----

    /** The page's groups in [AchievementTable.GROUPS] order (a group with no row is left out), each with the rows [f] lets through. */
    fun groups(views: List<AchView>, f: AchFilter, now: Long, achSince: Long): List<AchGroup> =
        (AchievementTable.GROUPS + views.map { it.group }).distinct().mapNotNull { g ->
            val rows = views.filter { it.group == g }
            if (rows.isEmpty()) null else AchGroup(g, rows, rows.filter { matches(f, it, now, achSince) })
        }

    /** The note at the right of a group header: "7 of 35 tiers", or "3 of 11" for a group of one-offs only (the Badges). */
    fun groupCount(rows: List<AchView>): String =
        if (rows.all { it.oneOff }) "${rows.count { it.tier >= 0 }} of ${rows.size}"
        else "${rows.filter { !it.oneOff }.sumOf { it.tier + 1 }} of ${rows.filter { !it.oneOff }.sumOf { it.tiers.size }} tiers"

    // ---- tabs (R10 W2, D3-A): All · one tab per group; a tab shows only its group ----

    /** The first tab: every group, each under its header. */
    const val ALL_TAB = "All"

    /** The tabs in order: "All", then each group of [groups] (a group with no row at all has no tab). */
    fun tabs(groups: List<AchGroup>): List<String> = listOf(ALL_TAB) + groups.map { it.name }

    /** [tab] when it exists among [groups], else "All" (a tab that vanished falls back). */
    fun tabOrAll(groups: List<AchGroup>, tab: String): String = if (tab in tabs(groups)) tab else ALL_TAB

    /**
     * The groups a tab SHOWS: "All" = every group the filter leaves something in (each gets its header); a group's tab = that group alone,
     * even when the filter leaves it empty (its tab stays and shows [emptyText]). An unknown tab shows nothing.
     */
    fun tabGroups(groups: List<AchGroup>, tab: String): List<AchGroup> =
        if (tab == ALL_TAB) groups.filter { !it.empty } else groups.filter { it.name == tab }

    /** The tab a row lives in (its group), or null for an unknown id. */
    fun tabOf(rowId: String): String? = AchievementTable.row(rowId)?.group

    /** "Battle · 12 of 50 tiers" for a tab; "All · 47 of 166 tiers" for All. Not changed by the filter (it counts what is reached, not what is shown). */
    fun countLine(groups: List<AchGroup>, tab: String): String {
        val rows = if (tab == ALL_TAB) groups.flatMap { it.all } else groups.firstOrNull { it.name == tab }?.all.orEmpty()
        return "$tab · ${groupCount(rows)}"
    }

    /** The text on the "Show" button. */
    fun showLabel(f: AchFilter): String = "Show: ${f.label}"

    /** What a tab says when the filter leaves nothing in it ("" when it has rows, or when the filter is All). */
    fun emptyText(f: AchFilter, tab: String): String = when {
        f == AchFilter.ALL -> ""
        tab == ALL_TAB -> if (f == AchFilter.CLOSE) "Nothing is close to its next tier yet." else "Nothing reached in the last $RECENT_DAYS days."
        else -> if (f == AchFilter.CLOSE) "Nothing close to the next tier in $tab" else "Nothing reached in $tab in the last $RECENT_DAYS days"
    }

    /** True when [rowId] is among the rows the groups SHOW (the filter lets it through), so a "See it" can keep the filter. */
    fun shows(groups: List<AchGroup>, rowId: String): Boolean = groups.any { g -> g.shown.any { it.id == rowId } }

    // ---- header numbers ----

    /** Per tier 0..4: how many tiered rows have reached it (a row at Epic counts for Common, Rare and Epic). One-offs are not counted. */
    fun tierCounts(views: List<AchView>): IntArray {
        val out = IntArray(AchievementTable.TIER_NAMES.size)
        for (v in views) if (!v.oneOff) for (t in 0..v.tier.coerceAtMost(out.size - 1)) if (t >= 0) out[t]++
        return out
    }

    /** All tiers of all tiered rows ("of 175 tiers reached"). */
    fun tiersTotal(views: List<AchView>): Int = views.filter { !it.oneOff }.sumOf { it.tiers.size }

    fun tiersReached(views: List<AchView>): Int = tierCounts(views).sum()

    /** The one-offs that are earned. */
    fun oneOffsEarned(views: List<AchView>): Int = views.count { it.oneOff && it.tier >= 0 }

    // ---- the detail sheet ----

    /** Every tier of [v] with its state, in order. */
    fun ladder(v: AchView): List<AchStep> = v.tiers.mapIndexed { t, threshold ->
        val state = when { t <= v.tier -> AchStep.State.REACHED; t == v.tier + 1 -> AchStep.State.CURRENT; else -> AchStep.State.LOCKED }
        val lockedText = when {
            state != AchStep.State.LOCKED -> null
            t == v.tier + 2 -> "next after ${AchievementTable.tierName(v.tier + 1)}"
            else -> "locked until ${AchievementTable.tierName(t - 1)}"
        }
        AchStep(t, AchievementTable.tierName(t), threshold, state, v.dates.getOrNull(t), lockedText, t in v.inferred, rewardOf(v.id, t))
    }

    // ---- rewards (R14-B2-D) ----

    /** The item id tier [tier] of row [rowId] puts in the Bag, or null. */
    fun rewardOf(rowId: String, tier: Int): String? = AchievementTable.row(rowId)?.rewards?.get(tier)

    /** The detail sheet's line on a tier with a reward: "Reward: Master Ball". */
    fun rewardText(item: String): String = "Reward: ${Items.name(item)}"

    /** The popup's line when the tier just reached has a reward: "Reward: a Master Ball, in your Bag". */
    fun rewardPopupText(item: String): String = "Reward: ${DailyGift.withArticle(Items.name(item))}, in your Bag"

    /**
     * "Reached 4 Jun 2026", or "Reached before 4 Jun 2026" for a tier whose date is only inferred ([inferred]: first seen by a silent look,
     * backfill or external feed) or was there when tracking began ([achSince]); "Reached" without a date.
     */
    fun reachedText(at: Long?, achSince: Long, zone: ZoneId, inferred: Boolean = false): String = when {
        at == null || at <= 0L -> "Reached"
        inferred || at == achSince -> "Reached before ${date(at, zone)}"
        else -> "Reached ${date(at, zone)}"
    }

    fun date(ms: Long, zone: ZoneId): String = MONTHS.format(Instant.ofEpochMilli(ms).atZone(zone))

    /** "188 to go" for the tier being worked on. */
    fun toGo(v: AchView): String = v.next?.let { fmt(metricOf(v.id), (it - v.value).coerceAtLeast(0)) + " to go" } ?: ""

    /** Where the row's count comes from. */
    fun source(row: AchRow): AchSource {
        if (row.ankimonBadge != null) return AchSource.BADGE
        val m = row.metric ?: return AchSource.BADGE
        return when {
            m.external -> AchSource.EXTERNAL
            !m.counted -> AchSource.SAVE
            m in BACKFILLED -> AchSource.HISTORY
            else -> AchSource.SINCE_UPDATE
        }
    }

    /** The counted metrics [Achievements.backfill] fills from the day history of an old save. */
    private val BACKFILLED = setOf(
        Metric.MONEY_EARNED, Metric.MONEY_SPENT, Metric.TOTAL_XP, Metric.ITEMS_USED, Metric.EASY_PRESSES, Metric.GOOD_PRESSES,
        Metric.SHINIES, Metric.DAYS_STUDIED, Metric.GOALS_REACHED, Metric.BALLS_THROWN,
    )

    /** R13 Part F: "Total levels" counts each level gained from that update on (the starter's earlier ones were worked out once). */
    const val LEVELS_NOTE = "Counted level by level since the October 2026 update (your starter's earlier levels are included); the levels a Pokémon came with never count."

    /** The footnote under the ladder. */
    fun sourceNote(row: AchRow, since: String): String = if (row.metric == Metric.TOTAL_LEVELS) LEVELS_NOTE else when (source(row)) {
        AchSource.SAVE -> "Counted from your save, so your earlier progress is included."
        AchSource.HISTORY -> "Started from your save's daily history (up to 400 days), then counted live."
        AchSource.SINCE_UPDATE -> "Counted since the update ($since); earlier progress is not included."
        AchSource.EXTERNAL -> "Measured outside the game: AnkiGo's own timer and AnkiDroid's history."
        AchSource.BADGE -> "One of Ankimon's badges: it is earned once."
    }

    // ---- numbers as text ----

    private fun metricOf(id: String): Metric? = AchievementTable.row(id)?.metric

    /** "31,240"; the study time (minutes) as "41 h" / "30 min". */
    fun fmt(metric: Metric?, v: Long): String {
        if (metric == Metric.STUDY_MINUTES) return if (v >= 60) String.format(Locale.US, "%,d h", v / 60) else "$v min"
        return String.format(Locale.US, "%,d", v)
    }

    fun fmtValue(v: AchView): String = fmt(metricOf(v.id), v.value)
    fun fmtNext(v: AchView): String? = v.next?.let { fmt(metricOf(v.id), it) }

    // ---- badges and the popup ----

    /** The table row (and tier index) an Ankimon badge stands for: a one-off row (tier 0) or a tier of a row whose tiers are badges. Null for a badge with no row (3 = 300 cards). */
    fun forBadge(badgeId: Int): Pair<AchRow, Int>? {
        AchievementTable.ROWS.firstOrNull { it.ankimonBadge == badgeId }?.let { return it to 0 }
        for (row in AchievementTable.ROWS) {
            val t = row.tierBadges.indexOf(badgeId)
            if (t >= 0) return row to t
        }
        return null
    }

    /**
     * What to celebrate for the [events] of one action, in order and without doubles: every AchievementReached, and every
     * BadgeEarned that stands for a row ([forBadge]; BadgeEarned of a badge-backed tier arrives without an AchievementReached).
     */
    fun celebrations(events: List<GameEvent>): List<AchCelebration> {
        val out = ArrayList<AchCelebration>()
        fun add(c: AchCelebration) { if (out.none { it.rowId == c.rowId && it.tier == c.tier }) out += c }
        for (e in events) when (e) {
            is GameEvent.AchievementReached -> add(AchCelebration(e.id, e.title, e.tier, e.tierName, AchievementTable.row(e.id)?.tiers?.getOrNull(e.tier)))
            is GameEvent.BadgeEarned -> forBadge(e.id)?.let { (row, t) ->
                if (row.oneOff) add(AchCelebration(row.id, row.title, -1, "Badge", null))
                else add(AchCelebration(row.id, row.title, t, AchievementTable.tierName(t), row.tiers.getOrNull(t)))
            }
            else -> {}
        }
        return out
    }

    /** The badges in [events] that no row stands for: they still get the old toast. */
    fun unmappedBadges(events: List<GameEvent>): List<GameEvent.BadgeEarned> =
        events.filterIsInstance<GameEvent.BadgeEarned>().filter { forBadge(it.id) == null }

    private const val DAY_MS = 24L * 60 * 60 * 1000
}
