package com.husarp.ankigo.data

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import com.husarp.ankigo.ui.picturesMostlyThere
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The picture download (Settings "Download pictures" and the first run) with a fake network. T31: it must survive the main
 * host refusing us (raw.githubusercontent.com throttles bursts) and must not remember a bad answer as "not found".
 */
class StaticDownloadTest {
    private lateinit var dir: File
    private val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4)
    private val html = "<html><body>Too many requests</body></html>".toByteArray()
    private val raw = "https://raw.githubusercontent.com/"
    private val cdn = "https://cdn.jsdelivr.net/"

    @Before fun setUp() { dir = Files.createTempDirectory("ankigo-static").toFile() }
    @After fun tearDown() { dir.deleteRecursively() }

    private fun run(nums: List<Int>, enabled: () -> Boolean = { true }, get: HttpGet): Int =
        Sprites.downloadStatics(dir, enabled, nums, null, { _, _ -> }, get)

    @Test fun staticChainIsTheSameFileOnTwoHosts() {
        val c = SpriteUrls.staticChain(25, false)
        assertEquals(SpriteUrls.static(25, false), c[0])
        assertEquals("${cdn}gh/PokeAPI/sprites@${DataStore.SPRITES_COMMIT}/sprites/pokemon/25.png", c[1])
        assertEquals("${cdn}gh/PokeAPI/sprites@${DataStore.SPRITES_COMMIT}/sprites/pokemon/shiny/25.png", SpriteUrls.staticChain(25, true)[1])
    }

    @Test fun allPicturesDownloadWhenTheNetworkIsFine() {
        val failed = run((1..40).toList()) { _, _ -> png }
        assertEquals(0, failed)
        assertTrue((1..40).all { File(dir, "$it.png").isFile })
    }

    @Test fun mainHostRefusingEverythingFallsBackToTheCdn() {
        val rawHits = AtomicInteger()
        val failed = run((1..60).toList()) { url, _ ->
            if (url.startsWith(raw)) { rawHits.incrementAndGet(); throw IOException("HTTP 429 for $url") }
            png
        }
        assertEquals("every picture should come from the second host", 0, failed)
        assertTrue((1..60).all { File(dir, "$it.png").isFile })
        // once the second host works it is tried first, so the refusing host is not hammered for all 60 pictures
        assertTrue("raw hits: ${rawHits.get()}", rawHits.get() < 60)
    }

    @Test fun mainHostAnsweringNotFoundFallsBackToTheCdn() {
        val failed = run((1..10).toList()) { url, _ -> if (url.startsWith(raw)) null else png }
        assertEquals(0, failed)
        assertTrue((1..10).all { File(dir, "$it.png").isFile })
    }

    @Test fun anExplicitDownloadIgnoresOldNotFoundMarkers() {
        dir.mkdirs()
        File(dir, "25.missing").writeText(System.currentTimeMillis().toString()) // left by an earlier run
        val failed = run(listOf(25)) { _, _ -> png }
        assertEquals(0, failed)
        assertTrue(File(dir, "25.png").isFile)
        assertFalse(File(dir, "25.missing").exists())
    }

    @Test fun anErrorPageIsNotRememberedAsNotFound() {
        // a throttling or captive-portal page answered 200: nothing may be saved, and no 7-day "missing" note either
        val base = File(dir, "7")
        assertNull(Sprites.download(base, SpriteUrls.staticChain(7, false), get = { _, _ -> html }))
        assertFalse(File(dir, "7.missing").exists())
        assertFalse(File(dir, "7.png").exists())
        // so the next try, with a working network, just works
        assertNotNull(Sprites.download(base, SpriteUrls.staticChain(7, false), get = { _, _ -> png }))
        assertTrue(File(dir, "7.png").isFile)
    }

    @Test fun aRealNotFoundOnEveryHostIsRemembered() {
        val base = File(dir, "9")
        assertNull(Sprites.download(base, SpriteUrls.staticChain(9, false), get = { _, _ -> null }))
        assertTrue(File(dir, "9.missing").isFile)
    }

    @Test fun networkTroubleOnEveryHostLeavesNoNote() {
        val failed = run((1..8).toList()) { url, _ -> throw IOException("offline: $url") }
        assertEquals(8, failed)
        assertTrue(dir.listFiles().orEmpty().none { it.name.endsWith(".missing") })
    }

    @Test fun switchingPicturesOffStopsTheWorkers() {
        val hits = AtomicInteger()
        val failed = run((1..50).toList(), enabled = { false }, get = { _, _ -> hits.incrementAndGet(); png })
        assertEquals(0, failed)
        assertEquals(0, hits.get())
    }

    @Test fun fewMissingPicturesCountAsDoneButManyDoNot() {
        // one rule for "done": the download result and the first-run step check must agree
        assertTrue(picturesMostlyThere(missing = 0, total = 905))
        assertTrue(picturesMostlyThere(missing = 45, total = 905))   // 5 %
        assertFalse(picturesMostlyThere(missing = 46, total = 905))
        assertFalse(picturesMostlyThere(missing = 70, total = 905))  // 7.7 %: used to count as "done" yet bounced back to step 2
    }
}
