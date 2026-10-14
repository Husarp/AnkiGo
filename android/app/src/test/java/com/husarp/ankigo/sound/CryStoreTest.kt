package com.husarp.ankigo.sound

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException

class CryStoreTest {
    private lateinit var dir: File
    private var clock = 1_000_000_000L
    private val ogg = "OggS\u0000\u0002rest-of-a-fake-file".toByteArray(Charsets.ISO_8859_1)

    @Before fun setUp() { dir = File(System.getProperty("java.io.tmpdir"), "cries-test-${System.nanoTime()}") }
    @After fun tearDown() { dir.deleteRecursively() }

    private fun store() = CryStore(dir) { clock }

    // ---- URLs ----

    @Test fun pinnedUrls() {
        assertEquals(
            listOf(
                "https://raw.githubusercontent.com/PokeAPI/cries/b3a9044bb839b5e3152aaa536c7b463a0fcf0140/cries/pokemon/latest/25.ogg",
                "https://cdn.jsdelivr.net/gh/PokeAPI/cries@b3a9044bb839b5e3152aaa536c7b463a0fcf0140/cries/pokemon/latest/25.ogg",
            ), CryUrls.chain(25))
    }

    @Test fun formIdUrl() {
        assertTrue(CryUrls.chain(10091).all { it.endsWith("/cries/pokemon/latest/10091.ogg") })
        assertTrue(CryUrls.chain(1025).first().endsWith("/1025.ogg"))
    }

    // ---- download and cache ----

    @Test fun downloadsAndCaches() {
        var calls = 0
        val f = store().fetch(25) { calls++; ogg }
        assertNotNull(f)
        assertEquals("25.ogg", f!!.name)
        assertEquals(ogg.toList(), f.readBytes().toList())
        assertEquals(1, calls)
        assertEquals(f, store().cached(25))
        store().fetch(25) { calls++; ogg }       // cached: no second download
        assertEquals(1, calls)
        assertTrue(dir.listFiles()!!.none { it.name.endsWith(".tmp") })
    }

    @Test fun fallsBackToTheSecondUrl() {
        val asked = ArrayList<String>()
        val f = store().fetch(7) { url -> asked += url; if (asked.size == 1) throw IOException("refused") else ogg }
        assertNotNull(f)
        assertEquals(2, asked.size)
        assertTrue(asked[1].startsWith("https://cdn.jsdelivr.net/"))
    }

    @Test fun refusesNonOggBytes() {
        val html = "<html>Rate limited</html>".toByteArray()
        assertNull(store().fetch(25) { html })
        assertNull(store().cached(25))
        assertFalse("an error page must not be remembered as 'not found'", store().knownMissing(25))
    }

    @Test fun notFoundIsRememberedForSevenDays() {
        var calls = 0
        assertNull(store().fetch(999) { calls++; null })
        assertEquals(2, calls)
        assertTrue(store().knownMissing(999))
        assertNull(store().fetch(999) { calls++; ogg })   // not asked again
        assertEquals(2, calls)
        clock += CryStore.RETRY_MISSING_MS - 1000
        assertTrue(store().knownMissing(999))
        clock += 2000
        assertFalse(store().knownMissing(999))
        assertNotNull(store().fetch(999) { calls++; ogg })
        assertFalse(File(dir, "999.missing").exists())
    }

    @Test fun networkTroubleLeavesNoNote() {
        assertNull(store().fetch(5) { throw IOException("offline") })
        assertFalse(store().knownMissing(5))
        assertNotNull(store().fetch(5) { ogg })
    }

    @Test fun oggDetection() {
        assertTrue(CryStore.isOgg(ogg))
        assertFalse(CryStore.isOgg(byteArrayOf()))
        assertFalse(CryStore.isOgg("RIFFxxxxxx".toByteArray()))
    }
}
