package com.husarp.ankigo.stats

import android.content.Context
import android.content.SharedPreferences

/**
 * R18 item 232: the Stats chart remembers its last choice (metric, period, deck, bar size) across visits and app restarts.
 * Stored in the `ankigo_ui` file as enum names. Every rule below is pure and takes plain strings, so it is unit-tested without Android.
 */
object ChartPrefs {
    private const val FILE = "ankigo_ui"
    private const val K_METRIC = "stats.chart.metric"
    private const val K_PERIOD = "stats.chart.period"
    private const val K_DECK = "stats.chart.deck"
    private const val K_GRAIN = "stats.chart.grain"

    /** What the chart shows. [grain] null = the period's default bar size; [deck] null = all decks. */
    data class Choice(
        val metric: ChartMetric = ChartMetric.REVIEWS,
        val period: ChartPeriod = ChartPeriod.D30,
        val deck: String? = null,
        val grain: ChartGrain? = null,
    )

    /** The periods a metric offers (Due forecast looks ahead 7 or 30 days only). */
    fun periodsFor(metric: ChartMetric): List<ChartPeriod> =
        if (metric == ChartMetric.DUE) listOf(ChartPeriod.D7, ChartPeriod.D30) else ChartPeriod.values().toList()

    /**
     * The choice from the saved strings. An unknown, removed or unlisted metric = Reviews; an unknown period, or one the metric
     * doesn't offer = 30 days; a grain that is unknown or not offered for the period = null (the period's default).
     */
    fun decode(metric: String?, period: String?, deck: String?, grain: String?): Choice {
        val m = ChartMetric.values().firstOrNull { it.name == metric && it.listed } ?: ChartMetric.REVIEWS
        val p = ChartPeriod.values().firstOrNull { it.name == period }?.takeIf { it in periodsFor(m) } ?: ChartPeriod.D30
        val g = ChartGrain.values().firstOrNull { it.name == grain }?.takeIf { m.kind == ChartKind.TIME && it in ChartData.effectivePeriod(m, p).grains }
        return Choice(m, p, deck?.takeIf { it.isNotEmpty() }, g)
    }

    /** Once the deck list is loaded (not empty): a saved deck that is gone = all decks. An empty list (not loaded / no access) keeps it. */
    fun withKnownDeck(c: Choice, decks: List<String>): Choice =
        if (c.deck != null && decks.isNotEmpty() && c.deck !in decks) c.copy(deck = null) else c

    fun load(p: SharedPreferences): Choice =
        decode(p.getString(K_METRIC, null), p.getString(K_PERIOD, null), p.getString(K_DECK, null), p.getString(K_GRAIN, null))

    fun save(p: SharedPreferences, c: Choice) {
        p.edit().putString(K_METRIC, c.metric.name).putString(K_PERIOD, c.period.name)
            .putString(K_DECK, c.deck).putString(K_GRAIN, c.grain?.name).apply()
    }

    private fun prefs(ctx: Context): SharedPreferences = ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun load(ctx: Context): Choice = try { load(prefs(ctx)) } catch (e: Exception) { Choice() }
    fun save(ctx: Context, c: Choice) { try { save(prefs(ctx), c) } catch (e: Exception) { /* a choice that isn't remembered is no harm */ } }
}
