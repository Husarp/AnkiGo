package com.husarp.ankigo.stats

import com.husarp.ankigo.game.StudyDay
import java.time.LocalDate
import java.time.ZoneId

/** Exact game-day history and forecast windows; relative Anki searches use its own rollover instead. */
internal object StudyDayQueries {
    fun cards(day: String, hour: Int, zone: ZoneId, deckIds: List<Long>? = null, introduced: Boolean = false): String {
        val start = StudyDay.start(day, hour, zone)
        val end = StudyDay.start(LocalDate.parse(day).plusDays(1).toString(), hour, zone)
        val deck = when {
            deckIds == null -> ""
            deckIds.isEmpty() -> " AND 0"
            else -> " AND (c.did IN (${deckIds.joinToString(",")}) OR c.odid IN (${deckIds.joinToString(",")}))"
        }
        val first = if (introduced) " AND r.type = 0 AND NOT EXISTS (SELECT 1 FROM revlog old WHERE old.cid = r.cid AND old.type = 0 AND old.id < r.id)" else ""
        return "SELECT COUNT(DISTINCT r.cid),0,0,0,0,0,0,0,0,0,0 FROM revlog r JOIN cards c ON c.id = r.cid WHERE r.id >= $start AND r.id < $end AND r.ease BETWEEN 1 AND 4 AND r.type BETWEEN 0 AND 3$deck$first"
    }
    fun due(day: String, hour: Int, zone: ZoneId, timing: AnkiSchedulerDay, deckIds: List<Long>? = null): String {
        val start = StudyDay.start(day, hour, zone) / 1000
        val end = StudyDay.start(LocalDate.parse(day).plusDays(1).toString(), hour, zone) / 1000
        val dueDays = timing.dueDays(day, hour, zone)
        val deck = when {
            deckIds == null -> ""
            deckIds.isEmpty() -> " AND 0"
            else -> " AND (c.did IN (${deckIds.joinToString(",")}) OR c.odid IN (${deckIds.joinToString(",")}))"
        }
        val due = "(CASE WHEN c.odue != 0 THEN c.odue ELSE c.due END)"
        val review = if (dueDays.isEmpty()) "0" else "(c.queue IN (2,3) AND $due IN (${dueDays.joinToString(",")}))"
        return "SELECT COUNT(*),0,0,0,0,0,0,0,0,0,0 FROM cards c WHERE ($review OR (c.queue IN (1,4) AND $due >= $start AND $due < $end))$deck"
    }

    /** The rolling API card windows use the same game-day bounds as their daily charts. */
    fun window(query: String, today: String, hour: Int, zone: ZoneId): String? {
        val introduced = Regex("""introduced:(\d+)""").matchEntire(query)
        val rated = Regex("""rated:(\d+)(?::([1-4]))?(?: -introduced:(\d+))?""").matchEntire(query)
        val days = (introduced?.groupValues?.get(1) ?: rated?.groupValues?.get(1))?.toIntOrNull()?.takeIf { it in 1..1000 } ?: return null
        val t = LocalDate.parse(today)
        val start = StudyDay.start(t.minusDays(days - 1L).toString(), hour, zone)
        val end = StudyDay.start(t.plusDays(1).toString(), hour, zone)
        val button = rated?.groupValues?.get(2)?.takeIf { it.isNotEmpty() }?.let { " AND r.ease = $it" }.orEmpty()
        val first = if (introduced != null) " AND r.type = 0 AND NOT EXISTS (SELECT 1 FROM revlog old WHERE old.cid = r.cid AND old.type = 0 AND old.id < r.id)" else ""
        val excluded = rated?.groupValues?.get(3)?.takeIf { it.isNotEmpty() }?.toIntOrNull()
        val exclude = if (excluded != null) {
            require(excluded in 1..1000)
            val introStart = StudyDay.start(t.minusDays(excluded - 1L).toString(), hour, zone)
            " AND NOT EXISTS (SELECT 1 FROM revlog fresh WHERE fresh.cid = r.cid AND fresh.type = 0 AND fresh.id >= $introStart AND fresh.id < $end AND NOT EXISTS (SELECT 1 FROM revlog earlier WHERE earlier.cid = fresh.cid AND earlier.type = 0 AND earlier.id < fresh.id))"
        } else ""
        return "SELECT COUNT(DISTINCT r.cid),0,0,0,0,0,0,0,0,0,0 FROM revlog r JOIN cards c ON c.id = r.cid WHERE r.id >= $start AND r.id < $end AND r.ease BETWEEN 1 AND 4 AND r.type BETWEEN 0 AND 3$button$first$exclude"
    }

}
