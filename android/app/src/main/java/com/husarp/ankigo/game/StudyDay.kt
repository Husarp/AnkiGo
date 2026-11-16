package com.husarp.ankigo.game

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** A study day starts at the configured local clock hour, including across daylight-saving changes. */
object StudyDay {
    fun start(day: String, hour: Int, zone: ZoneId = ZoneId.systemDefault()): Long =
        LocalDate.parse(day).atTime(hour.coerceIn(0, 23), 0).atZone(zone).toInstant().toEpochMilli()

    fun key(epochMs: Long, hour: Int, zone: ZoneId = ZoneId.systemDefault()): String {
        val local = Instant.ofEpochMilli(epochMs).atZone(zone)
        val date = local.toLocalDate()
        return (if (epochMs < start(date.toString(), hour, zone)) date.minusDays(1) else date).toString()
    }
}
