package com.husarp.ankigo.debug

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Test

class DelayedFlushTest {
    private class Fake {
        val tasks = ArrayList<() -> Unit>()
        val delays = ArrayList<Long>()
        fun schedule(ms: Long, t: () -> Unit) { delays += ms; tasks += t }
        fun runDue() { val t = tasks.toList(); tasks.clear(); t.forEach { it() } }
    }

    @Test fun manyRequestsInOneWindowRunOnce() {
        val f = Fake(); var runs = 0
        val d = DelayedFlush(2000, f::schedule) { runs++ }
        repeat(10) { d.request() }
        assertEquals(1, f.tasks.size); assertEquals(listOf(2000L), f.delays)
        f.runDue()
        assertEquals(1, runs)
    }

    @Test fun aLaterRequestSchedulesAgain() {
        val f = Fake(); var runs = 0
        val d = DelayedFlush(2000, f::schedule) { runs++ }
        d.request(); f.runDue(); d.request(); f.runDue()
        assertEquals(2, runs)
    }

    @Test fun rollingLogCoalescesAndKeepsAllLines() {
        val dir = Files.createTempDirectory("rgl").toFile()
        val file = File(dir, "e.log")
        val f = Fake()
        lateinit var log: RollingGameLog
        val d = DelayedFlush(2000, f::schedule) { log.persist() }
        log = RollingGameLog(file, 500, d)
        repeat(5) { log.append(listOf("line $it")) }
        assertEquals(false, file.exists())
        assertEquals(5, log.all().lines().size)
        f.runDue()
        assertEquals(5, RollingGameLog(file).all().lines().size)
        log.append(listOf("later")); f.runDue()
        assertEquals(6, RollingGameLog(file).all().lines().size)
        log.clear()
        assertEquals("", file.readText())
        dir.deleteRecursively()
    }
}
