package com.husarp.ankigo.debug

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class R26LogInitTest {
    @Test fun startupDoesNotOpenOrTrimFilesBeforeTheWorkerUsesTheLog() {
        val dir = Files.createTempDirectory("r26-log").toFile()
        val caller = Thread.currentThread()
        var opened = false
        val writer = LogWriter(openFiles = {
            assertNotSame("log initialization must run on the worker", caller, Thread.currentThread())
            opened = true
            RotatingLog(dir, "report", 100)
        })
        assertFalse(opened)
        val worker = Executors.newSingleThreadExecutor()
        try {
            worker.submit { writer.write("background line\n", true) }.get(5, TimeUnit.SECONDS)
            assertTrue(opened)
            assertEquals("background line\n", worker.submit<String> { writer.readAll() }.get(5, TimeUnit.SECONDS))
            worker.submit { writer.files.close() }.get(5, TimeUnit.SECONDS)
        } finally { worker.shutdownNow(); dir.deleteRecursively() }
    }
}
