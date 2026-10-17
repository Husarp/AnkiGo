package com.husarp.ankigo.stats

import com.husarp.ankigo.game.DayLog
import com.husarp.ankigo.game.StudyStats
import com.husarp.ankigo.game.Tuning
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

// TICKET R3-04 (PLAN 104) + R3-14: the statistics chart as data. One metric, one period, one deck filter -> bars + numbers.
// Pure Kotlin (no android.*), tested with made-up data. The screen only draws a [ChartSeries] and prints what it holds.

/** The three kinds of chart: bars over days/weeks/months, bars with labels instead of days, and the streak calendar grid. */
enum class ChartKind { TIME, CATEGORY, HEATMAP }

/** The groups of the metric list (the "Show" sheet): Study, Game, Collection. */
enum class ChartGroup(val label: String) { STUDY("Study"), GAME("Game"), COLLECTION("Collection") }

/** How much time one bar covers: a day, a calendar week (Monday first) or a calendar month. [label] is the filter text. */
enum class ChartGrain(val label: String) { DAY("Per day"), WEEK("Per week"), MONTH("Per month") }

/**
 * What the chart can show. [label] and [group] are the metric list's text and group, [icon] a plain icon id the screen maps
 * to a drawable. [kind] says how the chart is drawn. [unit] and [noun] name the numbers ("cards", "reviews"); [seriesNames]
 * name the values of a bar (more than one = stacked; for a CATEGORY chart one value per bar).
 */
enum class ChartMetric(
    val label: String, val group: ChartGroup, val icon: String, val kind: ChartKind,
    val unit: String, val noun: String, val seriesNames: List<String>, val fromAnkiDroid: Boolean,
) {
    // Study
    REVIEWS("Reviews", ChartGroup.STUDY, "bars", ChartKind.TIME, "cards", "reviews", listOf("Cards"), true),
    TIME("Time studied", ChartGroup.STUDY, "clock", ChartKind.TIME, "min", "min", listOf("In AnkiGo", "Live"), false),
    RETENTION("Retention", ChartGroup.STUDY, "check", ChartKind.TIME, "%", "% kept", listOf("Retention"), false),
    NEW_CARDS("New cards", ChartGroup.STUDY, "plus", ChartKind.TIME, "cards", "new cards", listOf("New cards"), true),
    BUTTONS("Buttons pressed", ChartGroup.STUDY, "gamepad", ChartKind.TIME, "answers", "answers", listOf("Again", "Hard", "Good", "Easy"), false),
    /** AnkiGo's own answer count; not in the metric list (Reviews already falls back to it when AnkiDroid isn't there). */
    ANSWERS("Answers", ChartGroup.STUDY, "bars", ChartKind.TIME, "answers", "answers", listOf("Answers"), false),
    CARD_STATES("Card states", ChartGroup.STUDY, "book", ChartKind.CATEGORY, "cards", "cards", listOf("Cards"), true),
    DUE("Due forecast", ChartGroup.STUDY, "calendar", ChartKind.TIME, "cards", "cards due", listOf("Due"), true),
    STREAK_CALENDAR("Streak calendar", ChartGroup.STUDY, "flame", ChartKind.HEATMAP, "cards", "reviews", listOf("Cards"), true),

    // Game
    MONEY("Money earned", ChartGroup.GAME, "coin", ChartKind.TIME, "₽", "₽ earned", listOf("Earned"), false),
    CATCHES("Catches", ChartGroup.GAME, "ball", ChartKind.TIME, "catches", "catches", listOf("Catches"), false),
    WINS("Battles won", ChartGroup.GAME, "swords", ChartKind.TIME, "battles", "battles won", listOf("Battles won"), false),
    XP("XP earned", ChartGroup.GAME, "up", ChartKind.TIME, "XP", "XP", listOf("XP"), false),
    HEALING("Healing used", ChartGroup.GAME, "heal", ChartKind.TIME, "items", "healing items", listOf("Healing items"), false),
    MONEY_SPENT("Money spent", ChartGroup.GAME, "cart", ChartKind.TIME, "₽", "₽ spent", listOf("Spent"), false),
    // The battle numbers (R4-V11): counted since 0.13, so earlier days read 0 (see ChartData.BATTLE_SOURCE)
    DMG_DEALT("Damage dealt", ChartGroup.GAME, "bolt", ChartKind.TIME, "HP", "HP dealt", listOf("Dealt"), false),
    DMG_TAKEN("Damage taken", ChartGroup.GAME, "heartbreak", ChartKind.TIME, "HP", "HP taken", listOf("Taken"), false),
    DMG_BLOCKED("Damage blocked", ChartGroup.GAME, "shield", ChartKind.TIME, "HP", "HP blocked", listOf("Blocked"), false),
    MISSES("Misses", ChartGroup.GAME, "miss", ChartKind.TIME, "misses", "misses", listOf("Misses"), false),

    // Collection
    DEX_PROGRESS("Pokédex progress", ChartGroup.COLLECTION, "dex", ChartKind.CATEGORY, "%", "caught", listOf("Caught"), false),
    PER_TYPE("Pokémon per type", ChartGroup.COLLECTION, "types", ChartKind.CATEGORY, "species", "caught", listOf("Caught"), false),
    SHINIES_RARE("Shinies & rare", ChartGroup.COLLECTION, "star", ChartKind.CATEGORY, "Pokémon", "Pokémon", listOf("Owned"), false);

    /** In the metric list? Only [ANSWERS] isn't. */
    val listed: Boolean get() = this != ANSWERS

    /** A period applies (CATEGORY charts are a picture of now). */
    val usesPeriod: Boolean get() = kind != ChartKind.CATEGORY

    companion object {
        /** The metric list as the mockup groups it: every [ChartGroup] with its listed metrics, in display order. */
        val menu: List<Pair<ChartGroup, List<ChartMetric>>> =
            ChartGroup.values().map { g -> g to values().filter { it.group == g && it.listed } }
    }
}

/** The chart periods: the last [days] days (DUE: the next [days] days). [defaultGrain] is the bar size used unless the user picks another. */
enum class ChartPeriod(val days: Int, val defaultGrain: ChartGrain) {
    D7(7, ChartGrain.DAY), D30(30, ChartGrain.DAY), D90(90, ChartGrain.WEEK), D365(365, ChartGrain.MONTH);

    /** The bar sizes that make sense for this period (the first and last bar of a week or month grain can be partial). */
    val grains: List<ChartGrain>
        get() = when (this) {
            D7 -> listOf(ChartGrain.DAY)
            D30 -> listOf(ChartGrain.DAY, ChartGrain.WEEK)
            D90 -> listOf(ChartGrain.DAY, ChartGrain.WEEK, ChartGrain.MONTH)
            D365 -> listOf(ChartGrain.WEEK, ChartGrain.MONTH)
        }
}

/**
 * One bar: [startDay]..[endDay] (game dates, both inclusive, clipped to the period; empty for a CATEGORY bar) and one value
 * per [ChartMetric.seriesNames]. [detail] is what a tap on the bar shows, one line each, already formatted: a day, week or
 * month bar says which period and how much, a stacked bar adds each part with its value and percent.
 */
data class Bar(
    val label: String, val startDay: String, val endDay: String, val values: List<Double>, val hasData: Boolean = true,
    val detail: List<String> = emptyList(),
)

/**
 * Everything the chart screen needs. [period] is the period really used (DUE only knows 7 and 30), [grain] the bar size
 * really used ([ChartData.effectiveGrain]; DAY for a CATEGORY or HEATMAP chart, which have no bar size). [total], [perDayAvg]
 * and [best] (a single day, on [bestDay]) are in [unit]; for RETENTION [total] is the period's percent, [perDayAvg] the mean
 * of the days that had answers. A CATEGORY chart has no days: [total] is the sum of the bars (DEX_PROGRESS: the overall caught
 * percent, PER_TYPE: the caught species, SHINIES_RARE: the Pokémon owned: one Pokémon can sit in several bars), [perDayAvg]
 * is 0, [best] the biggest bar and [bestLabel] its label. [deckApplies] false = the numbers cover all decks whatever [deck]
 * says. [loading] = the AnkiDroid read for this deck is still running (bars are empty then). [source] says where the numbers
 * come from. [heatmap] is the calendar grid of a HEATMAP chart (its [bars] are the grid's days, one per cell).
 */
data class ChartSeries(
    val metric: ChartMetric,
    val period: ChartPeriod,
    val deck: String?,
    val bars: List<Bar>,
    val total: Double,
    val perDayAvg: Double,
    val best: Double,
    val bestDay: String?,
    val unit: String,
    val source: String,
    val deckApplies: Boolean,
    val empty: Boolean,
    val loading: Boolean = false,
    val seriesNames: List<String> = metric.seriesNames,
    val kind: ChartKind = metric.kind,
    val grain: ChartGrain = ChartGrain.DAY,
    val bestLabel: String? = null,
    val heatmap: StudyStats.Heatmap? = null,
) {
    /** The tap text of the bar that holds game date [day] (a HEATMAP cell's day, or any day of a time chart); empty when none does. */
    fun detailFor(day: String): List<String> = bars.firstOrNull { day >= it.startDay && day <= it.endDay }?.detail.orEmpty()
}

object ChartData {
    /** The caption of the four battle charts: the day log only started counting them with 0.13, earlier days show 0. */
    const val BATTLE_SOURCE = "AnkiGo's own count, counted since 0.13 (earlier days show 0)"

    /** DUE looks ahead 7 or 30 days only; everything else keeps the asked period. */
    fun effectivePeriod(metric: ChartMetric, period: ChartPeriod): ChartPeriod =
        if (metric == ChartMetric.DUE && period.days > 30) ChartPeriod.D30 else period

    /** The bar sizes the "Per day" filter offers for [metric] and [period]; empty for a CATEGORY or HEATMAP chart (no bar size). */
    fun grains(metric: ChartMetric, period: ChartPeriod): List<ChartGrain> =
        if (metric.kind != ChartKind.TIME) emptyList() else effectivePeriod(metric, period).grains

    /** The bar size really used: [grain] when it is one of [grains], else the period's default (e.g. MONTH for 7 days -> DAY). */
    fun effectiveGrain(metric: ChartMetric, period: ChartPeriod, grain: ChartGrain?): ChartGrain {
        val p = effectivePeriod(metric, period)
        return if (metric.kind == ChartKind.TIME && grain != null && grain in p.grains) grain else p.defaultGrain
    }

    /** How many weeks the streak calendar shows for [period] (always whole weeks, Monday first, ending with the current week). */
    fun heatmapWeeks(period: ChartPeriod): Int = when (period) {
        ChartPeriod.D7 -> 4
        ChartPeriod.D30 -> 6
        ChartPeriod.D90 -> 14
        ChartPeriod.D365 -> 52
    }

    /**
     * The AnkiDroid reads [build] needs and the snapshot doesn't hold yet by itself (give them to
     * `AnkiStatsStore.requestSeries`): per-deck REVIEWS / DUE / CARD_STATES, NEW_CARDS for any deck, the 7 and 365 day retention
     * windows (the 30 day one is part of every AnkiDroid refresh). Empty for the game's own metrics.
     */
    fun seriesKeys(metric: ChartMetric, period: ChartPeriod, deck: String?, today: String): List<SeriesKey> {
        val days = effectivePeriod(metric, period).days
        val d = deck?.takeIf { it.isNotBlank() }
        return when (metric) {
            ChartMetric.REVIEWS, ChartMetric.DUE -> if (d == null) emptyList() else listOf(SeriesKey(d, metric, days, today))
            ChartMetric.CARD_STATES -> if (d == null) emptyList() else listOf(SeriesKey(d, metric, 0, today))
            ChartMetric.NEW_CARDS -> listOf(SeriesKey(d, metric, days, today))
            ChartMetric.RETENTION -> if (days == 7 || days == 365) listOf(SeriesKey(null, metric, days, today)) else emptyList()
            else -> emptyList()
        }
    }

    /** How one metric reads its numbers: per game day, [comps] values (the components of a bar). */
    private class Plan(val comps: Int, val value: (String) -> DoubleArray, val source: String, val deckApplies: Boolean, val loading: Boolean = false)

    /**
     * The chart for [metric]. [grain] is the user's bar size (null or not offered for the period = the period's default),
     * [goal] the daily goal the streak calendar's heat levels are measured against, [collection] what the three COLLECTION
     * metrics read (null = no collection yet: those charts come out empty).
     */
    fun build(
        metric: ChartMetric, period: ChartPeriod, deck: String?, snap: StatsSnapshot, history: Map<String, DayLog>, today: String,
        grain: ChartGrain? = null, goal: Int = Tuning.DAILY_AVERAGE, collection: CollectionStats? = null, goalFor: ((String) -> Int)? = null,
    ): ChartSeries {
        val p = effectivePeriod(metric, period)
        val d = deck?.takeIf { it.isNotBlank() }
        val t = runCatching { LocalDate.parse(today) }.getOrNull()
            ?: return ChartSeries(metric, p, d, emptyList(), 0.0, 0.0, 0.0, null, metric.unit, "", false, true)
        when (metric.kind) {
            ChartKind.CATEGORY -> return categories(metric, p, d, snap, today, collection)
            ChartKind.HEATMAP -> return heatmap(metric, p, d, snap, history, today, t, goal, goalFor)
            ChartKind.TIME -> Unit
        }
        val g = effectiveGrain(metric, p, grain)
        val plan = plan(metric, p, d, snap, history, today)
        val forward = metric == ChartMetric.DUE
        val days = if (forward) (1..p.days).map { t.plusDays(it.toLong()) } else (p.days - 1 downTo 0).map { t.minusDays(it.toLong()) }
        val perDay = days.map { it to plan.value(it.toString()) }

        val bars = buckets(days, g, p).map { grp ->
            val sum = DoubleArray(plan.comps)
            for (date in grp.dates) { val v = plan.value(date.toString()); for (i in v.indices) sum[i] += v[i] }
            val values = finish(metric, sum)
            val has = hasData(metric, sum)
            Bar(grp.label, grp.dates.first().toString(), grp.dates.last().toString(), values, hasData = has,
                detail = if (plan.loading) emptyList() else barDetail(metric, grp.dates, sum, values, has, t))
        }

        val daily = perDay.map { (date, v) -> date to dayNumber(metric, v) }
        val withData = perDay.filter { hasData(metric, it.second) }
        val retention = metric == ChartMetric.RETENTION
        val gameTotal = if (retention) pct(perDay.sumOf { it.second[0] }, perDay.sumOf { it.second[1] }) else null
        val ankiRetention = if (retention) ankiRetention(p, snap, today) else null
        val total = when {
            retention -> (ankiRetention ?: gameTotal ?: 0).toDouble()
            else -> daily.sumOf { it.second }
        }
        val avg = when {
            retention -> if (withData.isEmpty()) 0.0 else withData.sumOf { dayNumber(metric, it.second) } / withData.size
            else -> total / p.days
        }
        // best single day: for RETENTION among the days that had answers, else any day above 0
        val bestPair = (if (retention) daily.filterIndexed { i, _ -> hasData(metric, perDay[i].second) } else daily.filter { it.second > 0.0 })
            .maxByOrNull { it.second }
        val empty = if (retention) withData.isEmpty() && ankiRetention == null else daily.all { it.second <= 0.0 }
        val source = if (retention) retentionSource(ankiRetention != null, withData.isNotEmpty()) else plan.source
        return ChartSeries(
            metric, p, d, bars, total, avg, bestPair?.second ?: 0.0, bestPair?.first?.toString(), metric.unit, source,
            plan.deckApplies, empty, plan.loading, grain = g,
        )
    }

    // ---------------------------------------------------------------- the numbers per metric

    private fun plan(metric: ChartMetric, p: ChartPeriod, deck: String?, snap: StatsSnapshot, history: Map<String, DayLog>, today: String): Plan {
        fun game(source: String, pick: (DayLog) -> Int) =
            Plan(1, { day -> doubleArrayOf((history[day]?.let(pick) ?: 0).toDouble()) }, source, false)
        return when (metric) {
            ChartMetric.REVIEWS -> when {
                deck != null -> fromSeries(metric, p, deck, snap, today)
                snap.stats != null -> {
                    val st = snap.stats
                    val note = if (p.days > AnkiStatsReader.QUICK_DAYS && st.historyCheckedAt == 0L) "AnkiDroid · history still loading" else "AnkiDroid"
                    Plan(1, { day -> doubleArrayOf(st.studied(day).toDouble()) }, note, true)
                }
                else -> game("AnkiGo's own count (AnkiDroid isn't available)") { it.reviews }
            }
            ChartMetric.NEW_CARDS -> fromSeries(metric, p, deck, snap, today)
            ChartMetric.DUE -> when {
                deck != null -> fromSeries(metric, p, deck, snap, today)
                snap.stats != null -> {
                    val st = snap.stats
                    val t = LocalDate.parse(today)
                    val byDay = st.forecast.withIndex().associate { (i, n) -> t.plusDays(i + 1L).toString() to n }
                    Plan(1, { day -> doubleArrayOf((byDay[day] ?: 0).toDouble()) }, "AnkiDroid", true)
                }
                else -> Plan(1, { doubleArrayOf(0.0) }, StatsText.reason(snap.access, snap.message).ifBlank { "Reading AnkiDroid…" }, true, loading = snap.access == null)
            }
            ChartMetric.TIME -> Plan(
                2, { day -> doubleArrayOf((snap.timeByDay[day] ?: 0L) / 60_000.0, (snap.liveTimeByDay[day] ?: 0L) / 60_000.0) },
                "AnkiGo's own timer: study screen + live detection (AnkiDroid doesn't share its time)", false,
            )
            ChartMetric.RETENTION -> Plan(
                2, { day ->
                    val b = history[day]?.buttons
                    // button 0 = an answer whose button is unknown (catch-up): counted, and not an Again
                    val answers = if (b == null) 0 else (0..4).sumOf { b[it] }
                    doubleArrayOf((answers - (b?.get(1) ?: 0)).toDouble(), answers.toDouble())
                }, "", false,
            )
            ChartMetric.BUTTONS -> Plan(
                4, { day -> DoubleArray(4) { i -> (history[day]?.buttons?.get(i + 1) ?: 0).toDouble() } },
                "AnkiGo's own count (buttons pressed on answers it counted)", false,
            )
            ChartMetric.ANSWERS -> game("AnkiGo's own count") { it.reviews }
            ChartMetric.XP -> game("AnkiGo") { it.xp }
            ChartMetric.CATCHES -> game("AnkiGo") { it.caught }
            ChartMetric.WINS -> game("AnkiGo") { it.won }
            ChartMetric.MONEY -> game("AnkiGo") { it.cash }
            ChartMetric.MONEY_SPENT -> game("AnkiGo") { it.spent }
            ChartMetric.HEALING -> game("AnkiGo") { it.healItems }
            ChartMetric.DMG_DEALT -> game(BATTLE_SOURCE) { it.dmgDealt }
            ChartMetric.DMG_TAKEN -> game(BATTLE_SOURCE) { it.dmgTaken }
            ChartMetric.DMG_BLOCKED -> game(BATTLE_SOURCE) { it.dmgBlocked }
            ChartMetric.MISSES -> game(BATTLE_SOURCE) { it.misses }
            // not time metrics: [build] hands these to categories() / heatmap() before it asks for a plan
            ChartMetric.CARD_STATES, ChartMetric.STREAK_CALENDAR, ChartMetric.DEX_PROGRESS, ChartMetric.PER_TYPE, ChartMetric.SHINIES_RARE ->
                Plan(1, { doubleArrayOf(0.0) }, "", false)
        }
    }

    /** A per-deck (or NEW_CARDS) read from the snapshot's series cache; not there yet = loading, or the reason AnkiDroid can't be read. */
    private fun fromSeries(metric: ChartMetric, p: ChartPeriod, deck: String?, snap: StatsSnapshot, today: String): Plan {
        val data = snap.series[SeriesKey(deck, metric, p.days, today)]
        val failed = snap.access != null && snap.access != AnkiAccess.OK
        return if (data != null) Plan(1, { day -> doubleArrayOf((data.perDay[day] ?: 0).toDouble()) }, "AnkiDroid", true)
        else Plan(1, { doubleArrayOf(0.0) }, if (failed) StatsText.reason(snap.access, snap.message) else "Reading AnkiDroid…", true, loading = !failed)
    }

    /** Percent kept for the period from AnkiDroid's retention search; only for 7, 30 and 365 days (the others are the game's). */
    private fun ankiRetention(p: ChartPeriod, snap: StatsSnapshot, today: String): Int? = when (p.days) {
        30 -> snap.stats?.retention30
        7, 365 -> snap.series[SeriesKey(null, ChartMetric.RETENTION, p.days, today)]?.perDay?.let {
            AnkiStats.retentionPercent(it[AnkiSeriesReader.ANSWERED] ?: 0, it[AnkiSeriesReader.AGAIN] ?: 0)
        }
        else -> null
    }

    private fun retentionSource(anki: Boolean, game: Boolean): String = when {
        anki && game -> "Total: AnkiDroid (cards not new in the period). Bars: AnkiGo's counted answers"
        anki -> "AnkiDroid (cards not new in the period); AnkiGo counted no answers here"
        game -> "AnkiGo's own count: answers that weren't Again"
        else -> "AnkiGo's own count"
    }

    /** Whole percent of [answers] that were passed; null without answers. */
    private fun pct(passed: Double, answers: Double): Int? = if (answers <= 0.0) null else Math.round(100.0 * passed / answers).toInt()

    /** The bar values from the summed components: RETENTION turns (passed, answers) into one rounded percent. */
    private fun finish(metric: ChartMetric, sum: DoubleArray): List<Double> =
        if (metric == ChartMetric.RETENTION) listOf((pct(sum[0], sum[1]) ?: 0).toDouble()) else sum.toList()

    private fun hasData(metric: ChartMetric, sum: DoubleArray): Boolean =
        if (metric == ChartMetric.RETENTION) sum[1] > 0.0 else true

    /** The one number of a day (for total / average / best): all components added, RETENTION the day's percent. */
    private fun dayNumber(metric: ChartMetric, v: DoubleArray): Double =
        if (metric == ChartMetric.RETENTION) (pct(v[0], v[1]) ?: 0).toDouble() else v.sum()

    // ---------------------------------------------------------------- CATEGORY charts (bars with labels instead of days)

    private fun categories(metric: ChartMetric, p: ChartPeriod, deck: String?, snap: StatsSnapshot, today: String, c: CollectionStats?): ChartSeries =
        when (metric) {
            ChartMetric.CARD_STATES -> cardStates(metric, p, deck, snap, today)
            ChartMetric.DEX_PROGRESS -> collectionBars(
                metric, p, c, "AnkiGo: caught species out of all species, per generation",
                c?.dexByGen?.map { (gen, dp) -> Cat("Gen $gen", dp.caughtPercent.toDouble(), dexDetail("Gen $gen", dp)) }.orEmpty(),
            ) { it.dex.caughtPercent.toDouble() }
            ChartMetric.PER_TYPE -> collectionBars(
                metric, p, c, "AnkiGo: caught species per type (a dual type counts for both)",
                c?.dexByType?.map { (type, dp) -> Cat(type.label, dp.caught.toDouble(), dexDetail(type.label, dp)) }.orEmpty(),
            ) { it.dex.caught.toDouble() }
            else -> collectionBars(
                metric, p, c, "AnkiGo: Pokémon you own (one Pokémon can count in several bars)",
                if (c == null) emptyList() else listOf(
                    "Shinies" to c.shinies, "Legendaries" to c.legendaries, "Mythicals" to c.mythicals,
                    "Ultra Beasts" to c.ultraBeasts, "Regional forms" to c.regionalForms,
                ).map { (name, n) ->
                    Cat(name, n.toDouble(), listOfNotNull("$name · ${num(n.toDouble())}",
                        if (c.owned > 0 && n > 0) "${CollectionStats.percent(n, c.owned)} % of your ${num(c.owned.toDouble())} Pokémon" else null))
                },
            ) { it.owned.toDouble() }
        }

    /** One bar of a CATEGORY chart: its label, value and the tap text. */
    private class Cat(val label: String, val value: Double, val detail: List<String>)

    private fun categorySeries(
        metric: ChartMetric, p: ChartPeriod, deck: String?, cats: List<Cat>, total: Double, source: String, deckApplies: Boolean,
        loading: Boolean = false,
    ): ChartSeries {
        val best = cats.filter { it.value > 0.0 }.maxByOrNull { it.value }
        return ChartSeries(
            metric, p, deck, cats.map { Bar(it.label, "", "", listOf(it.value), true, if (loading) emptyList() else it.detail) },
            total, 0.0, best?.value ?: 0.0, null, metric.unit, source, deckApplies, cats.all { it.value <= 0.0 }, loading, bestLabel = best?.label,
        )
    }

    private fun collectionBars(
        metric: ChartMetric, p: ChartPeriod, c: CollectionStats?, source: String, cats: List<Cat>, total: (CollectionStats) -> Double,
    ): ChartSeries = categorySeries(
        metric, p, null, cats, if (c == null) 0.0 else total(c), if (c == null) "No collection yet" else source, false,
    )

    /** "Gen 3 · 40 of 135 caught (30 %)" and "Seen 80 (59 %)". */
    private fun dexDetail(label: String, dp: DexProgress): List<String> = listOf(
        "$label · ${num(dp.caught.toDouble())} of ${num(dp.total.toDouble())} caught (${dp.caughtPercent} %)",
        "Seen ${num(dp.seen.toDouble())} (${dp.seenPercent} %)",
    )

    /** The card states: all decks from the normal refresh's counts, one deck from the series cache (like the per-deck REVIEWS). */
    private fun cardStates(metric: ChartMetric, p: ChartPeriod, deck: String?, snap: StatsSnapshot, today: String): ChartSeries {
        val failed = snap.access != null && snap.access != AnkiAccess.OK
        val counts: Map<CardState, Int>?
        val source: String
        when {
            deck != null -> {
                counts = snap.series[SeriesKey(deck, metric, 0, today)]?.perDay?.let { m -> CardState.values().associateWith { m[it.name] ?: 0 } }
                source = "AnkiDroid"
            }
            snap.stats != null -> { counts = CardState.values().associateWith { it.of(snap.stats.counts) }; source = "AnkiDroid" }
            else -> { counts = null; source = "" }
        }
        if (counts == null) {
            val why = if (failed) StatsText.reason(snap.access, snap.message) else "Reading AnkiDroid…"
            return categorySeries(metric, p, deck, CardState.values().map { Cat(it.label, 0.0, emptyList()) }, 0.0, why, true, loading = !failed)
        }
        val sum = counts.values.sum()
        val cats = CardState.values().map { st ->
            val n = counts.getValue(st)
            Cat(st.label, n.toDouble(), listOfNotNull(
                "${st.label} · ${num(n.toDouble())} ${if (n == 1) "card" else "cards"}",
                if (sum > 0 && n > 0) "${CollectionStats.percent(n, sum)} % of the ${num(sum.toDouble())} cards counted" else null,
            ))
        }
        return categorySeries(metric, p, deck, cats, sum.toDouble(), source, true)
    }

    // ---------------------------------------------------------------- HEATMAP (the streak calendar)

    /** The calendar of the period's weeks: cards answered per day (AnkiDroid's count, else AnkiGo's own), heat measured against [goal]. */
    private fun heatmap(
        metric: ChartMetric, p: ChartPeriod, deck: String?, snap: StatsSnapshot, history: Map<String, DayLog>, today: String, t: LocalDate, goal: Int, goalFor: ((String) -> Int)?,
    ): ChartSeries {
        val weeks = heatmapWeeks(p)
        // the REVIEWS numbers of all decks; D30 so the "history still loading" note shows (the calendar reaches past 7 days)
        val plan = plan(ChartMetric.REVIEWS, ChartPeriod.D30, null, snap, history, today)
        val gridFirst = t.with(DayOfWeek.MONDAY).minusWeeks((weeks - 1).toLong())
        // the grid is whole weeks, but only the requested period counts (its first days before the period are empty cells)
        val periodFirst = t.minusDays((p.days - 1).toLong())
        val first = if (periodFirst.isAfter(gridFirst)) periodFirst else gridFirst
        val days = generateSequence(first) { it.plusDays(1) }.takeWhile { !it.isAfter(t) }.toList()
        val counts = days.associate { it.toString() to plan.value(it.toString())[0].toInt() }
        val grid = StudyStats.heatmapFromCounts(counts, goal, today, weeks, first.toString(), goalFor)
        val bars = days.map { date ->
            val n = counts.getValue(date.toString())
            Bar(date.dayOfMonth.toString(), date.toString(), date.toString(), listOf(n.toDouble()), true, heatDetail(date, n, goalFor?.invoke(date.toString()) ?: goal, t))
        }
        val total = counts.values.sumOf { it.toDouble() }
        val best = bars.filter { it.values[0] > 0.0 }.maxByOrNull { it.values[0] }
        return ChartSeries(
            metric, p, deck, bars, total, total / days.size, best?.values?.get(0) ?: 0.0, best?.startDay, metric.unit, plan.source,
            false, total <= 0.0, heatmap = grid,
        )
    }

    /** "Tue 14 Oct · 43 reviews" and how far the day got: "43 % of the daily goal (100)" or "Daily goal reached (100)". */
    private fun heatDetail(date: LocalDate, n: Int, goal: Int, today: LocalDate): List<String> {
        val head = "${dayText(date, today)} · ${valueText(ChartMetric.STREAK_CALENDAR, n.toDouble())}"
        val g = goal.coerceAtLeast(1)
        return when {
            n <= 0 -> listOf(head)
            n >= g -> listOf(head, "Daily goal reached (${num(g.toDouble())})")
            else -> listOf(head, "${CollectionStats.percent(n, g)} % of the daily goal (${num(g.toDouble())})")
        }
    }

    // ---------------------------------------------------------------- what a tap shows (text, built here so the screen only prints it)

    /**
     * The lines of a time bar: "Tue 14 Oct · 43 reviews"; a stacked bar then lists every part with its value and percent
     * ("Again · 3 · 12 %"); a week or month bar ends with the average per day. RETENTION says how many answers it is made of.
     */
    private fun barDetail(metric: ChartMetric, dates: List<LocalDate>, sum: DoubleArray, values: List<Double>, hasData: Boolean, today: LocalDate): List<String> {
        val head = periodText(dates.first(), dates.last(), today)
        if (metric == ChartMetric.RETENTION) {
            return if (!hasData) listOf("$head · no answers counted")
            else listOf("$head · ${values[0].toInt()} % kept", "${num(sum[0])} of ${num(sum[1])} answers weren't Again")
        }
        val total = values.sum()
        val lines = arrayListOf("$head · ${valueText(metric, total)}")
        if (values.size > 1 && total > 0.0) values.forEachIndexed { i, v ->
            lines += "${metric.seriesNames[i]} · ${partText(metric, v)} · ${Math.round(100.0 * v / total)} %"
        }
        if (dates.size > 1 && total > 0.0) lines += "Average ${valueText(metric, total / dates.size)} a day"
        return lines
    }

    /** [v] with its word: "43 reviews", "1h 05m" (TIME, [v] in minutes), "67 %" (RETENTION). */
    fun valueText(metric: ChartMetric, v: Double): String = when (metric) {
        ChartMetric.TIME -> StatsText.duration(Math.round(v * 60_000.0))
        ChartMetric.RETENTION -> "${Math.round(v)} %"
        else -> "${num(v)} ${nounFor(metric, v)}"
    }

    /** [ChartMetric.noun] for [v]; exactly 1 gets the singular ("1 review"). */
    private fun nounFor(metric: ChartMetric, v: Double): String = if (v != 1.0) metric.noun else when (metric) {
        ChartMetric.REVIEWS, ChartMetric.STREAK_CALENDAR -> "review"
        ChartMetric.NEW_CARDS -> "new card"
        ChartMetric.BUTTONS, ChartMetric.ANSWERS -> "answer"
        ChartMetric.DUE -> "card due"
        ChartMetric.CATCHES -> "catch"
        ChartMetric.WINS -> "battle won"
        ChartMetric.HEALING -> "healing item"
        ChartMetric.MISSES -> "miss"
        else -> metric.noun
    }

    /** A part of a stacked bar without the word (the part's name stands next to it). */
    private fun partText(metric: ChartMetric, v: Double): String =
        if (metric == ChartMetric.TIME) StatsText.duration(Math.round(v * 60_000.0)) else num(v)

    /** Whole numbers with thousands commas, anything else with one decimal: "1,240", "6.1". */
    private fun num(v: Double): String =
        if (Math.abs(v - Math.rint(v)) < 0.05) String.format(Locale.US, "%,d", Math.rint(v).toLong()) else String.format(Locale.US, "%,.1f", v)

    /**
     * "Tue 14 Oct", "12 – 18 Oct", "28 Sep – 4 Oct"; a whole calendar month: "October 2026". The year is added when it isn't
     * [today]'s year ("15 – 31 Oct 2025") and both years are named when the range crosses a New Year.
     */
    private fun periodText(a: LocalDate, b: LocalDate, today: LocalDate): String = when {
        a == b -> dayText(a, today)
        a.dayOfMonth == 1 && b == YearMonth.from(a).atEndOfMonth() ->
            "${a.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)} ${a.year}"
        a.year != b.year -> "${a.dayOfMonth} ${monthShort(a)} ${a.year} – ${b.dayOfMonth} ${monthShort(b)} ${b.year}"
        a.month != b.month -> "${a.dayOfMonth} ${monthShort(a)} – ${b.dayOfMonth} ${monthShort(b)}" + yearIfNot(b, today)
        else -> "${a.dayOfMonth} – ${b.dayOfMonth} ${monthShort(b)}" + yearIfNot(b, today)
    }

    private fun dayText(d: LocalDate, today: LocalDate) =
        "${d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)} ${d.dayOfMonth} ${monthShort(d)}" + yearIfNot(d, today)

    private fun yearIfNot(d: LocalDate, today: LocalDate) = if (d.year == today.year) "" else " ${d.year}"

    // ---------------------------------------------------------------- buckets

    private class Group(val label: String, val dates: List<LocalDate>)

    /** [days] (in order) grouped by [grain]; a week or month that the period cuts only holds the days inside the period. */
    private fun buckets(days: List<LocalDate>, grain: ChartGrain, p: ChartPeriod): List<Group> = when (grain) {
        ChartGrain.DAY -> days.map { Group(dayLabel(it, p), listOf(it)) }
        ChartGrain.WEEK -> days.groupBy { it.with(DayOfWeek.MONDAY) }.map { (monday, ds) -> Group(monthShort(monday) + " " + monday.dayOfMonth, ds) }
        ChartGrain.MONTH -> days.groupBy { YearMonth.from(it) }.map { (m, ds) -> Group(m.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH), ds) }
    }

    /** A week is labelled with its Monday ("Oct 5"), a 7 day chart with the weekday, a longer day chart with the day of the month. */
    private fun dayLabel(d: LocalDate, p: ChartPeriod): String =
        if (p == ChartPeriod.D7) d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH) else d.dayOfMonth.toString()

    private fun monthShort(d: LocalDate) = d.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
}
