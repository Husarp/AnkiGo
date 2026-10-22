package com.husarp.ankigo.data

import com.husarp.ankigo.data.DownloadAll.Have
import com.husarp.ankigo.data.DownloadAll.Job
import com.husarp.ankigo.data.DownloadAll.Kind
import com.husarp.ankigo.game.Items
import com.husarp.ankigo.game.TestData
import com.husarp.ankigo.sound.CryStore
import com.husarp.ankigo.ui.BackdropCatalog
import com.husarp.ankigo.ui.ItemIconPlan
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger

/**
 * PLAN 167 ("Download everything"): the plan (species x kinds incl. shiny, items, backgrounds, cries), what is missing, the estimate, the
 * worker loop (cancel, resume, failures) and the texts. A small fake data set, fake files in temp folders; never the network.
 */
class DownloadAllTest {
    private lateinit var root: File
    private lateinit var sprites: File
    private lateinit var cries: File
    private lateinit var layout: DownloadAll.Layout

    private val mons = listOf(TestData.species(1), TestData.species(4), TestData.species(7))
    private val showdownBg = BackdropCatalog.byId("beach")!!        // Showdown's: bg/beach.png
    private val ownBg = BackdropCatalog.byId("dragon-peak")!!        // Adam's, revision 2: bg/dragon-peak-2.webp
    private val items = listOf("poke-ball", "potion")
    private val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4)
    private val gif = "GIF89a-fake".toByteArray()
    private val ogg = "OggS\u0000\u0002rest-of-a-fake-file".toByteArray(Charsets.ISO_8859_1)
    private val now = 10_000_000_000L

    @Before fun setUp() {
        root = Files.createTempDirectory("ankigo-all").toFile()
        sprites = File(root, "sprites"); cries = File(root, "cries")
        layout = DownloadAll.Layout(sprites, cries)
    }

    @After fun tearDown() { root.deleteRecursively() }

    private fun plan() = DownloadAll.plan(mons, items, listOf(showdownBg, ownBg))
    private fun job(kind: Kind, shiny: Boolean = false, num: Int = 0, id: String = "") = plan().first { it.kind == kind && it.shiny == shiny && it.num == num && it.id == id }

    private fun put(rel: String, bytes: ByteArray = png, base: File = sprites) = File(base, rel).also { it.parentFile.mkdirs(); it.writeBytes(bytes) }

    /** A "not found" note of [rel] that is [ageMs] old. */
    private fun note(rel: String, ageMs: Long, base: File = sprites) = put(rel, "1".toByteArray(), base).also { it.setLastModified(now - ageMs) }

    private fun check(j: Job) = DownloadAll.check(j, layout, now)

    private val MIN = 60_000L
    private val HOUR = 60 * MIN
    private val DAY = 24 * HOUR

    // ---- the plan ----

    @Test fun planHasEveryKindForEverySpecies() {
        val p = plan()
        fun n(kind: Kind, shiny: Boolean? = null) = p.count { it.kind == kind && (shiny == null || it.shiny == shiny) }
        assertEquals(3, n(Kind.STATIC, false)); assertEquals(3, n(Kind.STATIC, true))
        assertEquals(3, n(Kind.FRONT, false)); assertEquals(3, n(Kind.FRONT, true))
        assertEquals(3, n(Kind.BACK, false)); assertEquals(3, n(Kind.BACK, true))
        assertEquals(3, n(Kind.CRY)); assertEquals(2, n(Kind.ITEM)); assertEquals(2, n(Kind.BACKGROUND))
        assertEquals(6 + 6 + 6 + 3 + 2 + 2, p.size)
        // each species appears once per kind and shiny flag
        assertEquals(setOf(1, 4, 7), p.filter { it.kind == Kind.FRONT && it.shiny }.map { it.num }.toSet())
        assertEquals(listOf("poke-ball", "potion"), p.filter { it.kind == Kind.ITEM }.map { it.id })
    }

    @Test fun planPutsPicturesFirstAndAnimationsLast() {
        val kinds = plan().map { it.kind }
        assertEquals(Kind.STATIC, kinds.first())
        val firstAnimation = kinds.indexOfFirst { it == Kind.FRONT || it == Kind.BACK }
        val lastSmall = kinds.indexOfLast { it != Kind.FRONT && it != Kind.BACK }
        assertTrue("small files before animations", lastSmall < firstAnimation)
    }

    @Test fun planCarriesWhatTheRealFetchNeeds() {
        val p = plan()
        assertEquals(1, p.first { it.kind == Kind.FRONT }.species!!.num)
        assertEquals(ownBg.id, p.first { it.kind == Kind.BACKGROUND && it.id == "dragon-peak" }.bg!!.id)
    }

    @Test fun itemCatalogueIsTheGameCatalogueWithoutThePicturelessOnes() {
        val ids = DownloadAll.catalogueItems()
        assertEquals(ids.sorted(), ids)
        for (id in listOf("poke-ball", "master-ball", "thunder-stone", "lucky-egg", "helix-fossil", "potion", "poke-doll")) assertTrue(id, id in ids)
        for (id in ItemIconPlan.DRAWN) assertFalse("$id has no PokeAPI picture", id in ids)
        assertEquals(Items.ALL.keys.count { it !in ItemIconPlan.DRAWN }, ids.size)
    }

    @Test fun everyBackgroundOfTheCatalogueIsInThePlan() {
        val p = DownloadAll.plan(emptyList(), emptyList(), BackdropCatalog.ALL)
        assertEquals(BackdropCatalog.ALL.size, p.size)
        assertEquals(BackdropCatalog.ALL.map { it.id }, p.map { it.id })
        assertTrue(p.all { it.kind == Kind.BACKGROUND })
    }

    @Test fun planForUsesOnlyTheEnabledGenerations() {
        val data = TestData.data(listOf(TestData.species(1), TestData.species(152), TestData.species(252)))   // gens 1, 2, 3
        val gens12 = DownloadAll.planFor(data, setOf(1, 2))
        assertEquals(setOf(1, 152), gens12.filter { it.kind == Kind.CRY }.map { it.num }.toSet())
        assertEquals(2, gens12.count { it.kind == Kind.FRONT && !it.shiny })
        val gen3 = DownloadAll.planFor(data, setOf(3))
        assertEquals(setOf(252), gen3.filter { it.kind == Kind.STATIC }.map { it.num }.toSet())
        // items and backgrounds do not depend on the generations
        assertEquals(DownloadAll.catalogueItems().size, gen3.count { it.kind == Kind.ITEM })
        assertEquals(BackdropCatalog.ALL.size, gen3.count { it.kind == Kind.BACKGROUND })
    }

    // ---- what is missing, the estimate ----

    @Test fun withNothingOnThePhoneEverythingIsMissingAndTheEstimateIsTheSumOfTheKinds() {
        val s = DownloadAll.scan(plan(), layout, now)
        assertEquals(plan().size, s.todo.size)
        assertEquals(plan().size, s.total)
        assertEquals(0L, s.haveBytes)
        val expected = 6 * DownloadAll.STATIC_BYTES + 12 * DownloadAll.ANIMATION_BYTES + 3 * DownloadAll.CRY_BYTES +
            2 * DownloadAll.ITEM_BYTES + DownloadAll.SHOWDOWN_BG_BYTES + DownloadAll.OWN_BG_BYTES
        assertEquals(expected, s.todoBytes)
    }

    @Test fun filesOnThePhoneAreNotMissingAndAreCounted() {
        val files = listOf(
            put("static/1.png"), put("static/4s.png"), put("front/4.gif", gif), put("front/7s.png"), put("back/7s.gif", gif),
            put("item/potion.png"), put("bg/beach.png"), put("bg/dragon-peak-2.webp", png), put("1.ogg", ogg, cries),
        )
        val s = DownloadAll.scan(plan(), layout, now)
        assertEquals(plan().size - files.size, s.todo.size)
        assertEquals(files.sumOf { it.length() }, s.haveBytes)
        assertTrue(s.todo.none { it.kind == Kind.ITEM && it.id == "potion" })
        assertTrue(s.todo.any { it.kind == Kind.ITEM && it.id == "poke-ball" })
        // the estimate shrinks by exactly the missing kinds: 2 pictures, 3 animations, 1 cry, 1 item, 2 backgrounds are gone
        val all = DownloadAll.scan(plan(), DownloadAll.Layout(File(root, "nowhere"), File(root, "nowhere2")), now).todoBytes
        val gone = 2 * DownloadAll.STATIC_BYTES + 3 * DownloadAll.ANIMATION_BYTES + DownloadAll.CRY_BYTES + DownloadAll.ITEM_BYTES +
            DownloadAll.SHOWDOWN_BG_BYTES + DownloadAll.OWN_BG_BYTES
        assertEquals(all - gone, s.todoBytes)
    }

    @Test fun shinyAndNormalFilesAreSeparate() {
        put("front/1.gif", gif)
        assertEquals(Have.HAVE, check(job(Kind.FRONT, false, 1)).state)
        assertEquals(Have.NONE, check(job(Kind.FRONT, true, 1)).state)
        assertEquals(Have.NONE, check(job(Kind.BACK, false, 1)).state)
    }

    @Test fun aStillPictureCountsForABattleSprite() {
        put("front/4.png")
        assertEquals(Have.HAVE, check(job(Kind.FRONT, false, 4)).state)
    }

    @Test fun anEmptyCryFileIsNotACry() {
        put("1.ogg", ByteArray(0), cries)
        assertEquals(Have.NONE, check(job(Kind.CRY, false, 1)).state)
    }

    @Test fun aFreshNotFoundNoteCountsAsDoneAnOldOneDoesNot() {
        // the windows are the ones the fetching code uses: FRONT / Pokédex / background 1 hour, BACK / item / cry 7 days
        note("front/4.missing", 30 * MIN); assertEquals(Have.NOTE, check(job(Kind.FRONT, false, 4)).state)
        note("front/4.missing", 2 * HOUR); assertEquals(Have.NONE, check(job(Kind.FRONT, false, 4)).state)
        note("back/7.missing", 3 * DAY); assertEquals(Have.NOTE, check(job(Kind.BACK, false, 7)).state)
        note("back/7.missing", 8 * DAY); assertEquals(Have.NONE, check(job(Kind.BACK, false, 7)).state)
        note("static/1.missing", 30 * MIN); assertEquals(Have.NOTE, check(job(Kind.STATIC, false, 1)).state)
        note("static/1.missing", 2 * HOUR); assertEquals(Have.NONE, check(job(Kind.STATIC, false, 1)).state)
        note("1.missing", 6 * DAY, cries); assertEquals(Have.NOTE, check(job(Kind.CRY, false, 1)).state)
        note("1.missing", 8 * DAY, cries); assertEquals(Have.NONE, check(job(Kind.CRY, false, 1)).state)
        note("item/potion.missing", 5 * DAY); assertEquals(Have.NOTE, check(job(Kind.ITEM, false, 0, "potion")).state)
        note("bg/beach.missing", 30 * MIN); assertEquals(Have.NOTE, check(job(Kind.BACKGROUND, false, 0, "beach")).state)
        note("bg/dragon-peak-2.missing", 2 * HOUR); assertEquals(Have.NONE, check(job(Kind.BACKGROUND, false, 0, "dragon-peak")).state)
    }

    @Test fun aNoteCountsOnlyForItsOwnFile() {
        note("front/4.missing", MIN)
        assertEquals(Have.NONE, check(job(Kind.FRONT, true, 4)).state)   // the shiny one has its own note
        assertEquals(Have.NONE, check(job(Kind.BACK, false, 4)).state)
    }

    @Test fun notesTakeAJobOutOfTheScanWithoutCountingBytes() {
        note("back/7.missing", DAY)
        val s = DownloadAll.scan(plan(), layout, now)
        assertEquals(plan().size - 1, s.todo.size)
        assertEquals("a note is not a file", 0L, s.haveBytes)
    }

    // ---- names agree with the code that really writes the files ----

    @Test fun theFilesTheRealDownloadersWriteAreFoundAndTheirNotesToo() {
        val get: HttpGet = { _, _ -> png }
        val gifGet: HttpGet = { _, _ -> gif }
        val none: HttpGet = { _, _ -> null }
        val sp = mons[0]
        // written by Sprites.download / Backdrops.fetchFile / CryStore.fetch themselves
        Sprites.download(File(File(sprites, "static"), "1s"), SpriteUrls.staticChain(1, true), get = get)
        Sprites.download(File(File(sprites, "front"), "1s"), SpriteUrls.battle(sp, SpriteKind.FRONT, true), get = gifGet)
        Sprites.download(File(File(sprites, "back"), "1"), SpriteUrls.battle(sp, SpriteKind.BACK, false), get = gifGet)
        Sprites.download(File(File(sprites, "item"), "potion"), listOf(SpriteUrls.item("potion")), get = get)
        assertTrue(Backdrops.fetchFile(sprites, ownBg) { _, _ -> byteArrayOf('R'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), 'F'.code.toByte(), 0, 0, 0, 0, 'W'.code.toByte(), 'E'.code.toByte(), 'B'.code.toByte(), 'P'.code.toByte(), 1) } != null)
        assertTrue(Backdrops.fetchFile(sprites, showdownBg, get) != null)
        assertTrue(CryStore(cries).fetch(1) { ogg } != null)
        for (j in listOf(job(Kind.STATIC, true, 1), job(Kind.FRONT, true, 1), job(Kind.BACK, false, 1), job(Kind.ITEM, false, 0, "potion"),
            job(Kind.BACKGROUND, false, 0, "dragon-peak"), job(Kind.BACKGROUND, false, 0, "beach"), job(Kind.CRY, false, 1))) {
            val c = DownloadAll.check(j, layout, System.currentTimeMillis())
            assertEquals("$j", Have.HAVE, c.state)
            assertTrue("$j has a size", c.bytes > 0)
        }
        // and their "not found" notes
        Sprites.download(File(File(sprites, "static"), "4"), SpriteUrls.staticChain(4, false), get = none)
        Sprites.download(File(File(sprites, "front"), "4"), SpriteUrls.battle(mons[1], SpriteKind.FRONT, false), get = none)
        Sprites.download(File(File(sprites, "back"), "4s"), SpriteUrls.battle(mons[1], SpriteKind.BACK, true), get = none)
        Sprites.download(File(File(sprites, "item"), "poke-ball"), listOf(SpriteUrls.item("poke-ball")), get = none)
        Backdrops.fetchFile(sprites, BackdropCatalog.byId("city")!!, none)
        CryStore(cries).fetch(4) { null }
        val later = System.currentTimeMillis()
        for (j in listOf(job(Kind.STATIC, false, 4), job(Kind.FRONT, false, 4), job(Kind.BACK, true, 4), job(Kind.ITEM, false, 0, "poke-ball"), job(Kind.CRY, false, 4))) {
            assertEquals("$j", Have.NOTE, DownloadAll.check(j, layout, later).state)
        }
        val city = DownloadAll.plan(emptyList(), emptyList(), listOf(BackdropCatalog.byId("city")!!)).single()
        assertEquals(Have.NOTE, DownloadAll.check(city, layout, later).state)
    }

    // ---- the run ----

    /** What a fetch of [j] puts on the disk when it works (the names the real code uses). */
    private fun download(j: Job) {
        val b = if (j.shiny) "${j.num}s" else "${j.num}"
        when (j.kind) {
            Kind.STATIC -> put("static/$b.png")
            Kind.FRONT -> put("front/$b.gif", gif)
            Kind.BACK -> put("back/$b.gif", gif)
            Kind.ITEM -> put("item/${j.id}.png")
            Kind.BACKGROUND -> put("bg/${Backdrops.fileName(j.bg!!)}")
            Kind.CRY -> put("${j.num}.ogg", ogg, cries)
        }
    }

    private fun notFound(j: Job) {
        val b = if (j.shiny) "${j.num}s" else "${j.num}"
        when (j.kind) {
            Kind.BACK -> put("back/$b.missing", "1".toByteArray())
            else -> error("only BACK in these tests")
        }
    }

    private fun run(
        todo: List<Job>, workers: Int = 4, stopAfter: Int = 30, cancelled: () -> Boolean = { false },
        fetch: (Job) -> Unit = { download(it) }, progress: (Int, Int, Int, Long) -> Unit = { _, _, _, _ -> },
    ) = DownloadAll.run(todo, workers, stopAfter, cancelled, { DownloadAll.check(it, layout) }, fetch, progress)

    @Test fun aRunFetchesEveryJobAndReportsProgress() {
        val todo = DownloadAll.scan(plan(), layout, now).todo
        val last = AtomicInteger(); val calls = AtomicInteger(); val seenFailed = AtomicInteger(); val wrongTotal = AtomicInteger()
        val r = run(todo) { ok, failed, total, _ ->
            calls.incrementAndGet(); seenFailed.addAndGet(failed)
            if (total != todo.size) wrongTotal.incrementAndGet()
            last.accumulateAndGet(ok) { a, b -> maxOf(a, b) }
        }
        assertEquals(0, seenFailed.get()); assertEquals(0, wrongTotal.get())
        assertEquals(todo.size, r.ok)
        assertTrue(r.failed.isEmpty() && r.untouched.isEmpty() && !r.aborted)
        assertEquals(todo.size, calls.get())
        assertEquals(todo.size, last.get())
        assertTrue("bytes written: ${r.bytes}", r.bytes > 0)
        assertEquals(0L, r.leftBytes)
        assertTrue("nothing left to do", DownloadAll.scan(plan(), layout).todo.isEmpty())
    }

    @Test fun cancelKeepsWhatWasDownloadedAndTheRestIsTheResumeSize() {
        val todo = DownloadAll.scan(plan(), layout, now).todo
        val fetched = AtomicInteger()
        val r = run(todo, workers = 2, cancelled = { fetched.get() >= 8 }, fetch = { download(it); fetched.incrementAndGet() })
        assertTrue(r.untouched.isNotEmpty())
        assertEquals(todo.size, r.ok + r.failed.size + r.untouched.size)
        assertEquals(r.untouched.sumOf { it.bytes }, r.leftBytes)
        // resume: exactly the untouched ones are still missing, the downloaded ones are skipped
        val again = DownloadAll.scan(plan(), layout)
        assertEquals(r.untouched.map { it.toString() }.toSet(), again.todo.map { it.toString() }.toSet())
        val second = AtomicInteger()
        val r2 = run(again.todo, fetch = { download(it); second.incrementAndGet() })
        assertEquals(again.todo.size, second.get())
        assertEquals(again.todo.size, r2.ok)
        assertTrue(DownloadAll.scan(plan(), layout).todo.isEmpty())
    }

    @Test fun aFetchThatLeavesNothingBehindIsAFailureAndKeepsItsSizeInTheRest() {
        val todo = DownloadAll.scan(plan(), layout, now).todo
        val r = run(todo, fetch = { if (it.kind != Kind.ITEM) download(it) })   // the network fails for the two item icons
        assertEquals(todo.size - 2, r.ok)
        assertEquals(setOf("poke-ball", "potion"), r.failed.map { it.id }.toSet())
        assertEquals(2 * DownloadAll.ITEM_BYTES, r.leftBytes)
        assertEquals("a failed file is tried again by the next scan", 2, DownloadAll.scan(plan(), layout).todo.size)
    }

    @Test fun anAnswerOfNotFoundIsDoneNotAFailure() {
        val todo = DownloadAll.scan(plan(), layout, now).todo
        val r = run(todo, fetch = { if (it.kind == Kind.BACK && it.shiny) notFound(it) else download(it) })
        assertTrue(r.failed.isEmpty())
        assertEquals(todo.size, r.ok)
        assertTrue("the note is respected next time", DownloadAll.scan(plan(), layout).todo.isEmpty())
    }

    @Test fun aFetchThatThrowsIsAFailureAndTheRunGoesOn() {
        val todo = DownloadAll.scan(plan(), layout, now).todo
        val r = run(todo, fetch = { if (it.kind == Kind.CRY && it.num == 4) throw IOException("boom") else download(it) })
        assertEquals(listOf("CRY 4"), r.failed.map { it.toString() })
        assertEquals(todo.size - 1, r.ok)
    }

    @Test fun aJobThatIsAlreadyThereWhenItsTurnComesIsNotFetched() {
        val todo = DownloadAll.scan(plan(), layout, now).todo
        todo.filter { it.kind == Kind.ITEM }.forEach { download(it) }   // an on-demand fetch got them first
        val fetched = AtomicInteger()
        val r = run(todo, fetch = { fetched.incrementAndGet(); download(it) })
        assertEquals(todo.size - 2, fetched.get())
        assertEquals(todo.size, r.ok)
    }

    @Test fun manyFailuresInARowStopTheRun() {
        val todo = DownloadAll.scan(plan(), layout, now).todo
        val r = run(todo, workers = 1, stopAfter = 5, fetch = { throw IOException("Unable to resolve host") })   // the internet is gone
        assertTrue(r.aborted)
        assertEquals(5, r.failed.size)
        assertEquals(todo.size - 5, r.untouched.size)
        assertEquals(todo.sumOf { it.bytes }, r.leftBytes)
    }

    @Test fun fileErrorsAreNotAConnectionBreakdown() {
        val todo = DownloadAll.scan(plan(), layout, now).todo
        val r = run(todo, workers = 1, stopAfter = 3, fetch = { if (it.kind == Kind.STATIC) throw IOException("HTTP 403 for https://x/${it.num}") else download(it) })
        assertFalse("an HTTP error for one file isn't the network being gone", r.aborted)
        assertEquals(todo.size, r.ok + r.failed.size)
        val silent = run(todo, workers = 1, stopAfter = 3, fetch = { if (it.kind == Kind.STATIC) Unit else download(it) })
        assertFalse("a fetch that just leaves nothing isn't either", silent.aborted)
    }

    @Test fun networkFailuresAreTheIOExceptionsThatAreNotHttpAnswers() {
        assertTrue(DownloadAll.isNetworkFailure(java.net.SocketTimeoutException("timeout")))
        assertTrue(DownloadAll.isNetworkFailure(java.net.UnknownHostException("host")))
        assertFalse(DownloadAll.isNetworkFailure(IOException("HTTP 500 for https://x")))
        assertFalse(DownloadAll.isNetworkFailure(IllegalStateException("bug")))
    }

    @Test fun resumeOrderPutsLastRunsFailuresAtTheEndAndKeepsTheRestInOrder() {
        val todo = DownloadAll.scan(plan(), layout, now).todo
        val bad = todo.take(3).map { it.key }.toSet()
        val o = DownloadAll.resumeOrder(todo, bad)
        assertEquals(todo.size, o.size)
        assertEquals(todo.drop(3) + todo.take(3), o)
        assertEquals(todo, DownloadAll.resumeOrder(todo, emptySet()))
    }

    @Test fun aFailingPrefixDoesNotStarveAResume() {
        val todo = DownloadAll.scan(plan(), layout, now).todo
        val stubborn = todo.take(8).map { it.key }.toSet()   // the first 8 jobs fail on the network every time
        val fetch: (Job) -> Unit = { if (it.key in stubborn) throw IOException("timeout") else download(it) }
        val first = run(todo, workers = 1, stopAfter = 5, fetch = fetch)
        assertTrue(first.aborted)
        assertEquals("the whole prefix stops it at the 5th", 5, first.failed.size)
        // Resume: the scan gives the same order again; the failed ones go last
        val again = DownloadAll.scan(plan(), layout, now).todo
        val second = run(DownloadAll.resumeOrder(again, first.failed.map { it.key }.toSet()), workers = 1, stopAfter = 5, fetch = fetch)
        assertEquals("everything that can work did", again.size - 8, second.ok)
        assertEquals("only the stubborn ones are left", stubborn, second.failed.map { it.key }.toSet())
    }

    @Test fun aWorkingFileInBetweenResetsTheCount() {
        val todo = DownloadAll.scan(plan(), layout, now).todo
        var n = 0
        val r = run(todo, workers = 1, stopAfter = 3, fetch = { if (n++ % 3 == 2) download(it) })   // fail, fail, work, fail, fail, work, ...
        assertFalse(r.aborted)
        assertEquals(todo.size, r.ok + r.failed.size)
    }

    @Test fun anEmptyPlanRunsNothing() {
        val r = run(emptyList())
        assertEquals(0, r.ok)
        assertFalse(r.aborted)
    }

    // ---- texts ----

    @Test fun roundedSizesAreWholeMbBelowTenAndFiveMbStepsAbove() {
        assertEquals("under 1 MB", DownloadAll.roundedSize(400_000))
        assertEquals("1 MB", DownloadAll.roundedSize(1_200_000))
        assertEquals("7 MB", DownloadAll.roundedSize(7_400_000))
        assertEquals("10 MB", DownloadAll.roundedSize(9_600_000))
        assertEquals("10 MB", DownloadAll.roundedSize(12_400_000))
        assertEquals("15 MB", DownloadAll.roundedSize(12_600_000))
        assertEquals("140 MB", DownloadAll.roundedSize(138_000_000))
        assertEquals("145 MB", DownloadAll.roundedSize(142_600_000))
        assertEquals("1.0 GB", DownloadAll.roundedSize(999_000_000))
        assertEquals("1.4 GB", DownloadAll.roundedSize(1_400_000_000))
    }

    @Test fun aboutAndResumeTexts() {
        assertEquals("about 140 MB", DownloadAll.about(138_000_000))
        assertEquals("less than 1 MB", DownloadAll.about(300_000))
        assertEquals("Resume download (about 35 MB left)", DownloadAll.resumeButton(34_000_000))
        assertEquals("Resume download (less than 1 MB left)", DownloadAll.resumeButton(10_000))
        assertEquals("Download everything", DownloadAll.BUTTON)
    }

    @Test fun confirmMessageSaysTheSizeWhatIsThereAndWiFi() {
        val s = DownloadAll.Scan(10, listOf(Job(Kind.FRONT, false, 1, "", 138_000_000)), 31_000_000)
        assertEquals("About 140 MB (30 MB is already on this phone). Best on Wi-Fi.", DownloadAll.confirmMessage(s))
        val fresh = DownloadAll.Scan(10, listOf(Job(Kind.FRONT, false, 1, "", 138_000_000)), 0)
        assertEquals("About 140 MB. Best on Wi-Fi.", DownloadAll.confirmMessage(fresh))
        val tiny = DownloadAll.Scan(10, listOf(Job(Kind.ITEM, false, 0, "x", 300_000)), 500_000)
        assertEquals("Less than 1 MB. Best on Wi-Fi.", DownloadAll.confirmMessage(tiny))
        assertEquals("Download everything?", DownloadAll.confirmTitle(false))
        assertEquals("Resume download?", DownloadAll.confirmTitle(true))
    }

    @Test fun progressAndResultLabels() {
        assertEquals("Downloaded 1 234 / 5 678 · 45 MB", DownloadAll.progressLabel(1234, 5678, 45_300_000))
        assertEquals("Downloaded 12 / 900 · 850 KB", DownloadAll.progressLabel(12, 900, 850_000))
        assertEquals("Downloaded 3 / 9 · 3.2 MB", DownloadAll.progressLabel(3, 9, 3_200_000))
        assertEquals("Stopped at 40 / 100. What was downloaded is kept.", DownloadAll.stoppedLabel(40, 100))
        assertEquals("Done, 1 file could not be downloaded. Tap to try again.", DownloadAll.failedLabel(1))
        assertEquals("Done, 12 files could not be downloaded. Tap to try again.", DownloadAll.failedLabel(12))
        assertTrue(DownloadAll.OFFLINE_LABEL.contains("Tap to try again"))
        assertFalse((DownloadAll.OFFLINE_LABEL + DownloadAll.failedLabel(3) + DownloadAll.stoppedLabel(1, 2)).any { it == '→' || it == '⇄' })
    }
}
