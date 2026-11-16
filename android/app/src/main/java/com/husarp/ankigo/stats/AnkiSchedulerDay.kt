package com.husarp.ankigo.stats

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

/** Modern Anki collection-day numbers are calendar days since creation in its original UTC offset. */
internal data class AnkiSchedulerDay(val createdDay: LocalDate, val rollover: Int) {
    fun dueDays(gameDay: String, gameHour: Int, zone: ZoneId = ZoneId.systemDefault()): List<Long> {
        val date = LocalDate.parse(gameDay)
        val start = com.husarp.ankigo.game.StudyDay.start(gameDay, gameHour, zone)
        val end = com.husarp.ankigo.game.StudyDay.start(date.plusDays(1).toString(), gameHour, zone)
        return listOf(date, date.plusDays(1)).filter { day ->
            com.husarp.ankigo.game.StudyDay.start(day.toString(), rollover, zone) in start until end
        }.map { ChronoUnit.DAYS.between(createdDay, it) }
    }

    companion object {
        const val QUERY = "SELECT crt,(SELECT CAST(val AS TEXT) FROM config WHERE key='creationOffset'),(SELECT CAST(val AS TEXT) FROM config WHERE key='rollover'),(SELECT CAST(val AS TEXT) FROM config WHERE key='localOffset'),0,0,0,0,0,0,0 FROM col LIMIT 1"

        fun read(row: Map<String, String?>, now: Long, zone: ZoneId): AnkiSchedulerDay {
            val created = requireNotNull(row["_id"]?.toLongOrNull()).also { require(it > 0) }
            val west = requireNotNull(row["guid"]?.toIntOrNull()).also { require(it in -1380..1380) }
            val rollover = requireNotNull(row["mid"]?.toIntOrNull()).also { require(it in 0..23) }
            // A collection explicitly using a different clock cannot be mapped by the phone's local rollover rules.
            row["mod"]?.toIntOrNull()?.let { local ->
                require(local in -1380..1380 && local * 60L == -zone.rules.getOffset(Instant.ofEpochMilli(now)).totalSeconds.toLong())
            }
            val date = Instant.ofEpochSecond(created - west * 60L).atOffset(ZoneOffset.UTC).toLocalDate()
            return AnkiSchedulerDay(date, rollover)
        }
    }
}
