package com.husarp.ankigo.game

import com.husarp.ankigo.nextStudyDayAt
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class StudyDayTimeTest {
    private fun ms(s: String) = Instant.parse(s).toEpochMilli()
    @Test fun schedulesConfiguredRolloverAndDoesNotRescheduleTheSameBoundary() {
        val zone = ZoneId.of("UTC")
        assertEquals(ms("2026-11-10T04:00:00Z"), nextStudyDayAt(ms("2026-11-10T03:59:59Z"), 4, zone))
        assertEquals(ms("2026-11-11T04:00:00Z"), nextStudyDayAt(ms("2026-11-10T04:00:00Z"), 4, zone))
        assertEquals(ms("2026-11-11T00:00:00Z"), nextStudyDayAt(ms("2026-11-10T10:00:00Z"), 0, zone))
    }
    @Test fun daylightSavingsBoundariesAgreeWithGameDayKey() {
        val zone = ZoneId.of("Europe/Warsaw")
        for (now in listOf(ms("2026-03-29T00:00:00Z"), ms("2026-10-25T00:00:00Z"))) {
            val at = nextStudyDayAt(now, 4, zone)
            fun key(t: Long) = Instant.ofEpochMilli(t).atZone(zone).minusHours(4).toLocalDate()
            assertTrue(at > now)
            assertEquals(key(now), key(at - 1))
            assertNotEquals(key(now), key(at))
            assertTrue(nextStudyDayAt(at, 4, zone) > at)
        }
    }
}
