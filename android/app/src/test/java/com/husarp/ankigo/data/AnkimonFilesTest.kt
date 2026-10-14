package com.husarp.ankigo.data

import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

// TICKET T53: the kept Ankimon file and the files an export replaced.
class AnkimonFilesTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun file(name: String, bytes: ByteArray) = File(tmp.root, name).also { it.writeBytes(bytes) }

    @Test fun keepWritesAnExactCopyAndLeavesNoTmp() {
        val dir = File(tmp.root, "ankimon")
        val src = file("src.db", byteArrayOf(1, 2, 3, 4))
        assertNull(AnkimonFiles.keptIn(dir))
        AnkimonFiles.keepInto(dir, src)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), AnkimonFiles.keptIn(dir)!!.readBytes())
        assertEquals(listOf("last-import.db"), dir.list()!!.toList())
        assertTrue(src.isFile) // the cache copy is the caller's to delete
    }

    @Test fun keepReplacesThePreviousOneOnlyOnSuccess() {
        val dir = File(tmp.root, "ankimon")
        AnkimonFiles.keepInto(dir, file("a.db", byteArrayOf(9, 9)))
        // a missing or empty source fails and the previous kept file stays exactly as it was
        for (bad in listOf(File(tmp.root, "missing.db"), file("empty.db", ByteArray(0)))) {
            try { AnkimonFiles.keepInto(dir, bad); throw AssertionError("should fail") } catch (_: IOException) {}
            assertArrayEquals(byteArrayOf(9, 9), AnkimonFiles.keptIn(dir)!!.readBytes())
            assertEquals(listOf("last-import.db"), dir.list()!!.toList())
        }
        AnkimonFiles.keepInto(dir, file("b.db", byteArrayOf(7)))
        assertArrayEquals(byteArrayOf(7), AnkimonFiles.keptIn(dir)!!.readBytes())
    }

    @Test fun emptyKeptFileCountsAsNone() {
        val dir = tmp.newFolder("ankimon")
        File(dir, "last-import.db").writeBytes(ByteArray(0))
        assertNull(AnkimonFiles.keptIn(dir))
    }

    @Test fun replacedFilesKeepTheNewestThree() {
        val dir = File(tmp.root, "ankimon")
        assertNull(AnkimonFiles.saveReplacedIn(dir, ByteArrayInputStream(ByteArray(0)), 5)) // nothing to save
        assertEquals(emptyList<String>(), dir.list()!!.toList())
        for (t in listOf(900L, 1000L, 20000L, 3000L, 40000L)) {
            val f = AnkimonFiles.saveReplacedIn(dir, ByteArrayInputStream(byteArrayOf(t.toByte())), t)
            assertNotNull(f)
        }
        // numeric order (20000 > 3000 > 1000 as numbers; as text "20000" < "3000")
        assertEquals(setOf("replaced-40000.db", "replaced-20000.db", "replaced-3000.db"), dir.list()!!.toSet())
        assertFalse(File(dir, "replaced-1000.db").exists())
    }

    @Test fun exportName() {
        assertEquals("ankimon-save-AshK_-2026-10-14.db", AnkimonFiles.exportName("Ash K_!", "2026-10-14"))
        assertEquals("ankimon-save-trainer-2026-10-14.db", AnkimonFiles.exportName("", "2026-10-14"))
        assertEquals("ankimon-save-trainer-2026-10-14.db", AnkimonFiles.exportName("../ /.", "2026-10-14"))
        assertEquals("ankimon-save-Łukasz-1-2026-10-14.db", AnkimonFiles.exportName("Łukasz-1", "2026-10-14"))
    }
}
