package com.husarp.ankigo

import java.io.File
import java.nio.file.Files
import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalCountTest {
    @Test fun countsOnlyTodaysLinesAndMissingFileIsZero() {
        val dir = Files.createTempDirectory("loc").toFile()
        val file = File(dir, "reviews.csv")
        assertEquals(0, Local.countToday(file))
        val now = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 19, 12, 0, 0) }.timeInMillis
        val dayMs = 86_400_000L
        file.writeText(listOf(now - 3600_000, now, now + 3600_000, now - dayMs, now + dayMs).joinToString("\n") { "$it,1,0,3,500,app" } + "\nbad line\n")
        assertEquals(3, Local.countToday(file, now))
        dir.deleteRecursively()
    }
}
