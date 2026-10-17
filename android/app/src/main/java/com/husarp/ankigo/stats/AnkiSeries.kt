package com.husarp.ankigo.stats

import java.time.LocalDate

// TICKET R3-04 (PLAN 104): per-day AnkiDroid counts for ONE deck (or all decks) over a chart period, read on request and
// cached. Pure Kotlin: every AnkiDroid call is the `count` function the caller passes in (like AnkiStatsReader).

/** What one on-demand read is about: the deck (null = all decks), the metric, the number of days and AnkiGo's game day. */
data class SeriesKey(val deck: String?, val metric: ChartMetric, val days: Int, val today: String)

/**
 * What a read found. For REVIEWS / NEW_CARDS / DUE: game date "yyyy-MM-dd" -> cards (DUE: the days after today). For the
 * RETENTION window read: just [AnkiSeriesReader.ANSWERED] and [AnkiSeriesReader.AGAIN]. For CARD_STATES: [CardState] name ->
 * cards (a key's `days` is 0 then: it is a picture of now, not a period).
 */
class SeriesData(val perDay: Map<String, Int>, val fetchedAt: Long)

/** The card states of the CARD_STATES chart, in AnkiDroid's order, each with the search that counts it (same as [Counts]). */
enum class CardState(val label: String, val query: String) {
    NEW("New", AnkiQueries.NEW), LEARNING("Learning", AnkiQueries.LEARNING), RELEARNING("Relearning", AnkiQueries.RELEARNING),
    YOUNG("Young", AnkiQueries.YOUNG), MATURE("Mature", AnkiQueries.MATURE),
    SUSPENDED("Suspended", AnkiQueries.SUSPENDED), BURIED("Buried", AnkiQueries.BURIED);

    /** This state's number in the all-decks [Counts] of a normal refresh. */
    fun of(c: Counts): Int = when (this) {
        NEW -> c.new; LEARNING -> c.learning; RELEARNING -> c.relearning; YOUNG -> c.young
        MATURE -> c.mature; SUSPENDED -> c.suspended; BURIED -> c.buried
    }
}

object AnkiSeriesQueries {
    /** Anki search escaping inside quotes: a backslash before `\`, `"`, `*` and `_`. */
    fun escape(s: String): String = buildString { for (c in s) { if (c == '\\' || c == '"' || c == '*' || c == '_') append('\\'); append(c) } }

    /** [q] limited to [deck] and its subdecks (`deck:"Name"`); [q] alone for all decks. */
    fun inDeck(deck: String?, q: String): String =
        if (deck.isNullOrBlank()) q else "deck:\"${escape(deck)}\" $q"

    fun reviewsOn(deck: String?, daysAgo: Int) = inDeck(deck, AnkiQueries.ratedOn(daysAgo))
    fun introducedIn(deck: String?, days: Int) = inDeck(deck, AnkiQueries.introduced(days))
    fun dueIn(deck: String?, days: Int) = inDeck(deck, AnkiQueries.due(days))
    fun inState(deck: String?, state: CardState) = inDeck(deck, state.query)

    /** The same two searches as the 30-day retention estimate, for any window. */
    fun retentionAnswered(days: Int) = "rated:$days -introduced:$days"
    fun retentionAgain(days: Int) = "rated:$days:1 -introduced:$days"
}

class AnkiSeriesReader(private val count: (String) -> Int) {
    /** Reads [key]; exceptions from [count] go to the caller. Metrics that don't come from AnkiDroid give an empty map. */
    fun read(key: SeriesKey, fetchedAt: Long): SeriesData = SeriesData(
        when (key.metric) {
            ChartMetric.REVIEWS -> reviews(key)
            ChartMetric.NEW_CARDS -> newCards(key)
            ChartMetric.DUE -> due(key)
            ChartMetric.CARD_STATES -> CardState.values().associate { it.name to count(AnkiSeriesQueries.inState(key.deck, it)) }
            ChartMetric.RETENTION -> mapOf(
                ANSWERED to count(AnkiSeriesQueries.retentionAnswered(key.days)),
                AGAIN to count(AnkiSeriesQueries.retentionAgain(key.days)),
            )
            else -> emptyMap()
        },
        fetchedAt,
    )

    private fun reviews(key: SeriesKey): Map<String, Int> {
        val t = LocalDate.parse(key.today)
        return (0 until key.days).associate { k -> t.minusDays(k.toLong()).toString() to count(AnkiSeriesQueries.reviewsOn(key.deck, k)) }
    }

    /** Cards introduced on day k ago = introduced:k+1 minus introduced:k (the cumulative counts); never below 0. */
    private fun newCards(key: SeriesKey): Map<String, Int> {
        val t = LocalDate.parse(key.today)
        val cumulative = IntArray(key.days + 1)
        for (j in 1..key.days) cumulative[j] = count(AnkiSeriesQueries.introducedIn(key.deck, j))
        return (0 until key.days).associate { k -> t.minusDays(k.toLong()).toString() to (cumulative[k + 1] - cumulative[k]).coerceAtLeast(0) }
    }

    private fun due(key: SeriesKey): Map<String, Int> {
        val t = LocalDate.parse(key.today)
        return (1..key.days).associate { k -> t.plusDays(k.toLong()).toString() to count(AnkiSeriesQueries.dueIn(key.deck, k)) }
    }

    companion object {
        const val ANSWERED = "answered"
        const val AGAIN = "again"
    }
}

/** The per-(deck, metric, period, day) cache kept in the snapshot: reuse a read for [MAX_AGE_MS], drop other days' reads. */
object AnkiSeriesCache {
    /** Today's numbers keep changing while you study, so a cached read is reused only this long. */
    const val MAX_AGE_MS = 60_000L

    fun fresh(cache: Map<SeriesKey, SeriesData>, key: SeriesKey, now: Long): Boolean =
        cache[key]?.let { now - it.fetchedAt in 0..MAX_AGE_MS } == true

    /** [cache] with [data] stored for [key]; reads made for another game day are dropped. */
    fun put(cache: Map<SeriesKey, SeriesData>, key: SeriesKey, data: SeriesData): Map<SeriesKey, SeriesData> =
        cache.filterKeys { it.today == key.today } + (key to data)
}
