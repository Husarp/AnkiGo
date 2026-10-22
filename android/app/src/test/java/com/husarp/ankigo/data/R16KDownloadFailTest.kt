package com.husarp.ankigo.data

import com.husarp.ankigo.data.DownloadAll.Have
import com.husarp.ankigo.data.DownloadAll.Job
import com.husarp.ankigo.data.DownloadAll.Kind
import com.husarp.ankigo.game.TestData
import com.husarp.ankigo.sound.CryStore
import com.husarp.ankigo.ui.BackdropCatalog
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

/**
 * PLAN 219 ("Download everything": 3 files always failed and the log said nothing). Every kind of job goes through the REAL fetch code
 * ([DownloadAll.fetching] on temp folders) with a fake network that answers 404, 403, a timeout, rubbish or a good file, and afterwards
 * the job must be HAVE or NOTE, or have a reason in the record. The real cause: PokeAPI's cries of Pikachu (25), Meltan (808) and
 * Melmetal (809) are MP3 data inside .ogg files; [CryStore] threw them away as "not an Ogg file" without an error, without a note.
 */
class R16KDownloadFailTest {
    private lateinit var root: File
    private lateinit var layout: DownloadAll.Layout
    private lateinit var store: CryStore

    // a base species of gen 1 (BW animation chain), one of gen 6 and one of gen 9 (Showdown chain), a regional form (id above 10000)
    private val mons = listOf(
        TestData.species(25),
        TestData.species(700),
        TestData.species(1000),
        TestData.species(10091).copy(form = "Alola", baseNum = 26, spriteId = "raichu-alola", gen = 7),
    )
    private val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4)
    private val gif = "GIF89a-fake".toByteArray()
    private val webp = "RIFF\u0000\u0000\u0000\u0000WEBPVP8 x".toByteArray(Charsets.ISO_8859_1)
    private val ogg = "OggS\u0000\u0002rest-of-a-fake-file".toByteArray(Charsets.ISO_8859_1)

    /** The first bytes of PokeAPI's real 25.ogg / 808.ogg (MPEG 1 layer III, 128 kbps, 48 kHz, mono) and 809.ogg (44.1 kHz), padded. */
    private fun mp3(vararg head: Int) = ByteArray(2000).also { b -> head.forEachIndexed { i, v -> b[i] = v.toByte() } }
    private val pikachuCry = mp3(0xFF, 0xFB, 0x94, 0xC4)
    private val melmetalCry = mp3(0xFF, 0xFB, 0x90, 0xC4)

    private fun plan(): List<Job> = DownloadAll.plan(mons, DownloadAll.catalogueItems(), BackdropCatalog.ALL)

    @Before fun setUp() {
        root = Files.createTempDirectory("ankigo-r16k").toFile()
        layout = DownloadAll.Layout(File(root, "sprites"), File(root, "cries"))
        store = CryStore(layout.cries)
    }

    @After fun tearDown() { root.deleteRecursively() }

    /** [DownloadAll.fetching] on the temp folders over the fake network [net]; returns (fetch, reasons, firstError). */
    private fun fetcher(net: HttpGet): Triple<(Job) -> Unit, ConcurrentHashMap<String, String>, AtomicReference<String?>> {
        val reasons = ConcurrentHashMap<String, String>()
        val first = AtomicReference<String?>(null)
        return Triple(DownloadAll.fetching(layout, store, first, reasons, net = net), reasons, first)
    }

    /** A good file for whatever [url] is. */
    private val good: HttpGet = { url, _ -> goodFor(url) }
    private fun goodFor(url: String) = when {
        url.endsWith(".gif") -> gif
        url.endsWith(".webp") -> webp
        url.endsWith(".ogg") -> ogg
        else -> png
    }

    /** Runs [fetch] for [job], returns the exception it threw (a network failure) or null. */
    private fun tryFetch(fetch: (Job) -> Unit, job: Job): Exception? = try { fetch(job); null } catch (e: Exception) { e }

    // ---- the cause ----

    @Test fun theMp3CriesOfPikachuMeltanAndMelmetalAreDownloaded() {
        val (fetch, reasons, _) = fetcher { url, _ -> if (url.contains("/25.ogg")) pikachuCry else if (url.contains("/809.ogg")) melmetalCry else null }
        for (num in listOf(25, 809)) {
            val job = Job(Kind.CRY, false, num, "", DownloadAll.CRY_BYTES)
            assertNull(tryFetch(fetch, job))
            assertEquals("cry $num: ${reasons[job.key]}", Have.HAVE, DownloadAll.check(job, layout).state)
            assertTrue(reasons.isEmpty())
        }
        assertEquals(2000L, File(layout.cries, "25.ogg").length())
    }

    // ---- every kind, every answer ----

    @Test fun aCleanNotFoundLeavesANoteForEveryKind() {
        val (fetch, reasons, first) = fetcher { _, _ -> null }
        val jobs = plan()
        assertEquals(setOf(Kind.STATIC, Kind.FRONT, Kind.BACK, Kind.ITEM, Kind.BACKGROUND, Kind.CRY), jobs.map { it.kind }.toSet())
        for (job in jobs) {
            assertNull("$job", tryFetch(fetch, job))
            assertEquals("$job: a 404 is 'not available', a note", Have.NOTE, DownloadAll.check(job, layout).state)
        }
        assertTrue("nothing failed: $reasons", reasons.isEmpty())
        assertNull(first.get())
        // and a Resume has nothing to ask again
        assertTrue(DownloadAll.scan(jobs, layout).todo.isEmpty())
    }

    @Test fun aGoodAnswerLeavesTheFileForEveryKind() {
        val (fetch, reasons, _) = fetcher(good)
        for (job in plan()) {
            assertNull("$job", tryFetch(fetch, job))
            assertEquals("$job", Have.HAVE, DownloadAll.check(job, layout).state)
        }
        assertTrue(reasons.isEmpty())
    }

    @Test fun anHttpErrorFailsTheJobWithItsCodeAndTheUrl() {
        val (fetch, reasons, first) = fetcher { url, _ -> throw IOException("HTTP 403 for $url") }
        for (job in plan()) {
            assertNull("$job: an HTTP answer is not a broken connection, nothing is thrown", tryFetch(fetch, job))
            assertEquals("$job", Have.NONE, DownloadAll.check(job, layout).state)
            val why = reasons[job.key].orEmpty()
            assertTrue("$job: '$why'", why.startsWith("last asked https://") && why.endsWith(": HTTP 403"))
        }
        assertTrue(first.get()!!.startsWith("IOException: HTTP 403"))
    }

    @Test fun aTimeoutFailsTheJobWithItsReasonAndIsRethrownAsANetworkFailure() {
        val (fetch, reasons, _) = fetcher { _, _ -> throw SocketTimeoutException("timeout") }
        for (job in plan()) {
            val e = tryFetch(fetch, job)
            assertTrue("$job: the run counts it toward the stop", e is SocketTimeoutException)
            assertEquals("$job", Have.NONE, DownloadAll.check(job, layout).state)
            assertTrue("$job: ${reasons[job.key]}", reasons[job.key]!!.endsWith(": SocketTimeoutException: timeout"))
        }
    }

    @Test fun rubbishInsteadOfAFileFailsWithTheSizeItWasNotWithNoReasonMissing() {
        val html = "<html>Rate limited</html>".toByteArray()
        val (fetch, reasons, first) = fetcher { _, _ -> html }
        for (job in plan()) {
            assertNull("$job", tryFetch(fetch, job))
            assertEquals("$job", Have.NONE, DownloadAll.check(job, layout).state)
            assertTrue("$job: ${reasons[job.key]}", reasons[job.key]!!.endsWith("answered with ${html.size} bytes, but nothing was saved from them"))
        }
        assertNull("no exception came through the network layer", first.get())
    }

    @Test fun everyJobAfterAnyAnswerIsHaveNoteOrHasAReason() {
        val answers = listOf<HttpGet>(
            { _, _ -> null },
            { url, _ -> goodFor(url) },
            { url, _ -> throw IOException("HTTP 500 for $url") },
            { _, _ -> throw SocketTimeoutException("timeout") },
            { _, _ -> "nope".toByteArray() },
            { url, _ -> if (url.contains("jsdelivr") || url.contains("play.pokemonshowdown")) null else "nope".toByteArray() },   // one host 404s, the other sends rubbish
            { url, _ -> if (url.hashCode() % 2 == 0) null else goodFor(url) },
        )
        for ((i, net) in answers.withIndex()) {
            val dir = File(root, "answer$i")
            val l = DownloadAll.Layout(File(dir, "sprites"), File(dir, "cries"))
            val reasons = ConcurrentHashMap<String, String>()
            val fetch = DownloadAll.fetching(l, CryStore(l.cries), AtomicReference<String?>(null), reasons, net = net)
            for (job in plan()) {
                tryFetch(fetch, job)
                val s = DownloadAll.check(job, l).state
                assertTrue("answer $i, $job: $s and no reason", s != Have.NONE || reasons.containsKey(job.key))
            }
        }
    }

    @Test fun theSwitchForPicturesIsAReasonToo() {
        val reasons = ConcurrentHashMap<String, String>()
        val asked = java.util.concurrent.atomic.AtomicInteger()
        val fetch = DownloadAll.fetching(layout, store, AtomicReference<String?>(null), reasons, picturesOn = { false }) { _, _ -> asked.incrementAndGet(); null }
        val item = plan().first { it.kind == Kind.ITEM }
        val cry = plan().first { it.kind == Kind.CRY }
        assertNull(tryFetch(fetch, item)); assertNull(tryFetch(fetch, cry))
        assertEquals(Have.NONE, DownloadAll.check(item, layout).state)
        assertTrue(reasons[item.key]!!.contains("Download pictures as needed"))
        assertEquals("a cry has its own rules, the picture switch doesn't stop it", Have.NOTE, DownloadAll.check(cry, layout).state)
    }

    // ---- the record and the log lines ----

    @Test fun theReasonNamesTheLastUrlAndTheRequestThatWasTheProblem() {
        val t = DownloadAll.Trail()
        assertEquals("nothing was asked, no file and no answer", t.describe())
        t.got("https://a/1.ogg", null)
        assertEquals("last asked https://a/1.ogg: no file and no answer", t.describe())
        t.got("https://b/1.ogg", ByteArray(7080))
        t.got("https://c/1.ogg", null)
        assertEquals("last asked https://c/1.ogg: https://b/1.ogg answered with 7080 bytes, but nothing was saved from them", t.describe())
        t.failed("https://d/1.ogg", IOException("HTTP 403 for https://d/1.ogg"))
        assertEquals("last asked https://d/1.ogg: HTTP 403", t.describe())
        t.failed("https://e/1.ogg", SocketTimeoutException("timeout"))
        assertEquals("last asked https://e/1.ogg: SocketTimeoutException: timeout", t.describe())
        assertEquals("HTTP 302", DownloadAll.Trail.answerOf(IOException("HTTP 302 without Location for https://x")))
    }

    @Test fun theLogGetsOneLinePerFailedFileUpToFiftyThenAnAndMore() {
        val failed = (1..53).map { Job(Kind.CRY, false, it, "", DownloadAll.CRY_BYTES) }
        val lines = DownloadAll.failureLines(failed, mapOf("CRY 1" to "last asked https://x/1.ogg: HTTP 403"))
        assertEquals(51, lines.size)
        assertEquals("download everything: failed CRY 1, last asked https://x/1.ogg: HTTP 403", lines[0])
        assertEquals("download everything: failed CRY 2, no file and no answer", lines[1])
        assertEquals("download everything: ... and 3 more failed", lines.last())
        assertEquals(2, DownloadAll.failureLines(failed.take(2), emptyMap()).size)
        assertTrue(DownloadAll.failureLines(emptyList(), emptyMap()).isEmpty())
        val shiny = Job(Kind.FRONT, true, 25, "", DownloadAll.ANIMATION_BYTES, species = TestData.species(25))
        val item = Job(Kind.ITEM, false, 0, "potion", DownloadAll.ITEM_BYTES)
        assertEquals(listOf("download everything: failed FRONT shiny 25, why", "download everything: failed ITEM potion, why"),
            DownloadAll.failureLines(listOf(shiny, item), mapOf(shiny.key to "why", item.key to "why")))
    }

    @Test fun aRunCountsTheNotAvailableOnesAsDoneAndSeparately() {
        val (fetch, reasons, _) = fetcher { url, _ -> if (url.contains("PokeAPI/cries")) null else goodFor(url) }   // upstream has no cries
        val jobs = plan()
        val r = DownloadAll.run(jobs, cancelled = { false }, check = { DownloadAll.check(it, layout) }, fetch = fetch, progress = { _, _, _, _ -> })
        val cries = jobs.count { it.kind == Kind.CRY }
        assertEquals(jobs.size, r.ok)
        assertTrue(r.failed.isEmpty())
        assertEquals(cries, r.notAvailable)
        assertEquals(0L, r.leftBytes)
        assertTrue(reasons.isEmpty())
        assertTrue(r.bytes > 0)
        // a second run finds nothing to do, and the ones with a note still count as not available
        assertTrue(DownloadAll.scan(jobs, layout).todo.isEmpty())
        val again = DownloadAll.run(jobs, cancelled = { false }, check = { DownloadAll.check(it, layout) }, fetch = { fail("asked again: $it") }, progress = { _, _, _, _ -> })
        assertEquals(cries, again.notAvailable)
        assertFalse(again.failed.isNotEmpty())
    }
}
