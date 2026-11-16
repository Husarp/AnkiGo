package com.husarp.ankigo.stats

import com.husarp.ankigo.game.StudyDay
import java.time.LocalDate
import java.time.ZoneId

/** Exact, half-open review-log windows; relative Anki searches use AnkiDroid's own rollover instead. */
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
}
