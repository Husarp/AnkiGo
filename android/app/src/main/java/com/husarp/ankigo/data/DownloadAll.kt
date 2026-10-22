package com.husarp.ankigo.data

import android.content.Context
import com.husarp.ankigo.game.GameData
import com.husarp.ankigo.game.Items
import com.husarp.ankigo.game.Species
import com.husarp.ankigo.sound.CryStore
import com.husarp.ankigo.sound.GameSound
import com.husarp.ankigo.ui.Backdrop
import com.husarp.ankigo.ui.BackdropCatalog
import com.husarp.ankigo.ui.BackdropSource
import com.husarp.ankigo.ui.ItemIconPlan
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.roundToInt

/**
 * PLAN 167: the one "Download everything" button of Settings -> Downloads. Everything the app would fetch one by one when it first
 * shows it, for every species of the enabled generations: the Pokédex picture and the FRONT / BACK battle animation, each in its
 * normal and shiny version, the cry; plus every item icon, and every battle background. The plan, what is missing, the size
 * estimate, the text, the worker loop and the real fetches are here; the Settings page only shows them.
 *
 * Pure parts (JVM-tested): [plan], [scan], [check], [run] and the text helpers. The real network and folders enter through
 * [Layout] and [fetcher]. The on-demand path stays as it was: this only fills the same folders earlier.
 */
internal object DownloadAll {
    enum class Kind { STATIC, FRONT, BACK, ITEM, BACKGROUND, CRY }

    // ---- size estimate: the average size of one file of each kind. Real sizes need the network (not provable here); the numbers: ----
    //  * animation (FRONT / BACK, normal or shiny): the old button said "about 80 MB" for the front + back animations of the default
    //    generations 1-8 (about 960 species with the regional forms, about 1 920 files) = about 40 KB a file, from gen-5 pixel animations
    //    (10-40 KB) mixed with the bigger 3D ones of gens 6-9 (up to 0.6 MB). A shiny animation has the same frames, so the same size.
    //  * Pokédex picture: Sprites.downloadStatics already estimates 1 500 B a picture ("Download pictures (about 1 MB)").
    //  * item icon: PokeAPI's 30 x 30 icons are under 1 KB.
    //  * background: Adam's own WebP files of the assets release average 177 KB (24 files, 79-375 KB, measured); Showdown's pixel
    //    PNGs are smaller (about 40 KB; the catalogue text says 8-400 KB overall).
    //  * cry: "~7 KB" (the Settings text of the cries switch).
    const val STATIC_BYTES = 1_500L
    const val ANIMATION_BYTES = 40_000L
    const val ITEM_BYTES = 800L
    const val SHOWDOWN_BG_BYTES = 40_000L
    const val OWN_BG_BYTES = 177_000L
    const val CRY_BYTES = 7_000L

    /** Parallel downloads (the animation job used 4 too). */
    const val WORKERS = 4

    /**
     * After this many NETWORK failures in a row (no connection, timeout; nothing downloaded, answered or "not found" in between) the run
     * stops: the internet isn't working. A server answer that is an error for one file ("HTTP 403 ...") doesn't count: the connection works.
     */
    const val STOP_AFTER_FAILURES = 30

    /** Is [e] a network-level failure (no route, timeout, reset) and not the server's error answer for one file ([Http.get]: "HTTP <code> for <url>")? */
    fun isNetworkFailure(e: Throwable): Boolean = e is IOException && e.message?.startsWith("HTTP ") != true

    /**
     * One file to get. [num] = species number (form id for regional forms) for pictures, animations and cries; [id] = item id or
     * background id; [bytes] = the estimate of its size. [species] / [bg] are what the real fetch needs.
     */
    class Job(
        val kind: Kind, val shiny: Boolean, val num: Int, val id: String, val bytes: Long,
        val species: Species? = null, val bg: Backdrop? = null,
    ) {
        override fun toString() = "$kind${if (shiny) " shiny" else ""} ${if (id.isNotEmpty()) id else num.toString()}"

        /** What tells this job from the others across two scans (the objects are made again each time). */
        val key: String get() = toString()
    }

    /**
     * PLAN 167: the order of a Resume. The jobs that failed in the previous run ([failedBefore], their [Job.key]s) go to the END of
     * [todo], so untried work runs first and one stubborn prefix can't stop every Resume at the same place. Stable otherwise.
     */
    fun resumeOrder(todo: List<Job>, failedBefore: Set<String>): List<Job> =
        if (failedBefore.isEmpty()) todo else todo.filter { it.key !in failedBefore } + todo.filter { it.key in failedBefore }

    /** The two folders everything lives in: the sprite folder (`static/ front/ back/ item/ bg/`) and the cry folder. */
    class Layout(val sprites: File, val cries: File)

    enum class Have { HAVE, NOTE, NONE }

    /** [state]: the file is there ([bytes] = its size), a fresh "not found" note says it isn't upstream, or neither. */
    class Check(val state: Have, val bytes: Long = 0)

    /** The jobs still to do ([todo]), how many there are in all ([total]) and the size of what is already there. */
    class Scan(val total: Int, val todo: List<Job>, val haveBytes: Long) {
        val todoBytes: Long get() = todo.sumOf { it.bytes }
    }

    // ---- the plan ----

    /** The item icons the game can show: the catalogue except the five items PokeAPI has no picture for ([ItemIconPlan.DRAWN]; TM discs are drawn too). */
    fun catalogueItems(): List<String> = Items.ALL.keys.filter { it !in ItemIconPlan.DRAWN }.sorted()

    /**
     * Every job: pictures first (small and seen everywhere), then item icons, backgrounds and cries, the animations last (the big part;
     * a Cancel keeps the useful small ones). Per species: normal and shiny Pokédex picture, FRONT and BACK animation in both.
     */
    fun plan(species: Collection<Species>, items: List<String>, backdrops: List<Backdrop>): List<Job> {
        val sps = species.sortedBy { it.num }
        val out = ArrayList<Job>(sps.size * 7 + items.size + backdrops.size)
        for (sp in sps) for (shiny in listOf(false, true)) out += Job(Kind.STATIC, shiny, sp.num, "", STATIC_BYTES)
        for (id in items) out += Job(Kind.ITEM, false, 0, id, ITEM_BYTES)
        for (bg in backdrops) out += Job(Kind.BACKGROUND, false, 0, bg.id, if (bg.source == BackdropSource.OWN) OWN_BG_BYTES else SHOWDOWN_BG_BYTES, bg = bg)
        for (sp in sps) out += Job(Kind.CRY, false, sp.num, "", CRY_BYTES)
        for (sp in sps) for (shiny in listOf(false, true)) {
            out += Job(Kind.FRONT, shiny, sp.num, "", ANIMATION_BYTES, species = sp)
            out += Job(Kind.BACK, shiny, sp.num, "", ANIMATION_BYTES, species = sp)
        }
        return out
    }

    /** [plan] for the species of [gens] in [data], the item catalogue and all the backgrounds. */
    fun planFor(data: GameData, gens: Set<Int>): List<Job> =
        plan(data.species.values.filter { it.gen in gens }, catalogueItems(), BackdropCatalog.ALL)

    // ---- what is on the phone ----

    private fun baseName(num: Int, shiny: Boolean) = if (shiny) "${num}s" else "$num"

    /** The file names (without folder) that mean "this job is done", and the base name of its "not found" note: `<base>.missing`. */
    private fun place(job: Job, l: Layout): Pair<List<File>, File> {
        fun sprite(dir: String) = File(l.sprites, dir)
        return when (job.kind) {
            Kind.STATIC -> {
                val b = baseName(job.num, job.shiny)
                listOf(File(sprite("static"), "$b.png")) to File(sprite("static"), b)
            }
            Kind.FRONT, Kind.BACK -> {
                val d = sprite(if (job.kind == Kind.FRONT) "front" else "back")
                val b = baseName(job.num, job.shiny)
                listOf(File(d, "$b.gif"), File(d, "$b.png")) to File(d, b)
            }
            Kind.ITEM -> listOf(File(sprite("item"), "${job.id}.png")) to File(sprite("item"), job.id)
            Kind.BACKGROUND -> {
                val bg = job.bg!!
                listOf(Backdrops.file(l.sprites, bg)) to File(sprite("bg"), Backdrops.baseName(bg))
            }
            Kind.CRY -> listOf(File(l.cries, "${job.num}.ogg")) to File(l.cries, "${job.num}")
        }
    }

    /** How long a "not found" note counts as an answer, the same as the code that fetches this kind uses (an older note is asked again). */
    private fun noteMs(kind: Kind): Long = when (kind) {
        Kind.STATIC, Kind.FRONT -> Sprites.NOTE_EXPECTED_MS
        Kind.BACK, Kind.ITEM -> Sprites.NOTE_RARE_MS
        Kind.BACKGROUND -> Backdrops.NOTE_MS
        Kind.CRY -> CryStore.RETRY_MISSING_MS
    }

    /** Is [job] done: the file is there (HAVE), or a note younger than [noteMs] says upstream has none (NOTE: counts as done), or neither (NONE). */
    fun check(job: Job, l: Layout, now: Long = System.currentTimeMillis()): Check {
        val (files, base) = place(job, l)
        for (f in files) if (f.isFile && (job.kind != Kind.CRY || f.length() > 0)) return Check(Have.HAVE, f.length())
        val note = File(base.path + ".missing")
        if (note.isFile && now - note.lastModified() < noteMs(job.kind)) return Check(Have.NOTE)
        return Check(Have.NONE)
    }

    /** Which jobs of [plan] are not done yet, and how many bytes of the rest are on the phone. */
    fun scan(plan: List<Job>, l: Layout, now: Long = System.currentTimeMillis()): Scan {
        val todo = ArrayList<Job>()
        var have = 0L
        for (j in plan) {
            val c = check(j, l, now)
            if (c.state == Have.NONE) todo += j else have += c.bytes
        }
        return Scan(plan.size, todo, have)
    }

    // ---- the run ----

    /**
     * What a run did: [ok] jobs done (downloaded, or upstream has none), the [failed] ones (network trouble: no file, no note), the
     * [untouched] ones left in the queue by a Cancel or [aborted], and [bytes] written on disk by this run.
     */
    class Result(val ok: Int, val failed: List<Job>, val untouched: List<Job>, val bytes: Long, val aborted: Boolean) {
        /** The estimate of what is still missing after this run. */
        val leftBytes: Long get() = failed.sumOf { it.bytes } + untouched.sumOf { it.bytes }
    }

    /**
     * Does [todo] with [workers] threads, in order. A job already there when its turn comes (an on-demand fetch got it first) is skipped.
     * [fetch] gets the file or leaves a note; afterwards [check] says what became of the job. [cancelled] stops taking new jobs; a run of
     * [stopAfter] NETWORK failures in a row (a [fetch] that throws an IOException for which [isNetworkFailure] holds) stops it too
     * ([Result.aborted]). Any other failure (an HTTP error for that file, a fetch that returns with nothing) is listed in [Result.failed]
     * but the connection is working then: it resets that count and the run goes on. [progress] (ok, failed, total, bytes) after every job, from any worker thread.
     */
    fun run(
        todo: List<Job>, workers: Int = WORKERS, stopAfter: Int = STOP_AFTER_FAILURES,
        cancelled: () -> Boolean, check: (Job) -> Check, fetch: (Job) -> Unit,
        progress: (ok: Int, failed: Int, total: Int, bytes: Long) -> Unit,
    ): Result {
        val queue = ConcurrentLinkedQueue(todo)
        val total = todo.size
        val ok = AtomicInteger()
        val failed = ConcurrentLinkedQueue<Job>()
        val bytes = AtomicLong()
        val straight = AtomicInteger()
        val aborted = AtomicBoolean(false)
        if (total == 0) return Result(0, emptyList(), emptyList(), 0, false)
        val n = workers.coerceIn(1, total)
        val latch = CountDownLatch(n)
        repeat(n) {
            Thread {
                try {
                    while (!cancelled() && !aborted.get()) {
                        val job = queue.poll() ?: break
                        val before = check(job)
                        if (before.state != Have.NONE) {
                            ok.incrementAndGet(); straight.set(0)
                        } else {
                            var network = false
                            try { fetch(job) } catch (e: Exception) { network = isNetworkFailure(e) /* counted below: nothing on disk */ }
                            val after = check(job)
                            if (after.state == Have.NONE) {
                                failed += job
                                if (!network) straight.set(0)
                                else if (straight.incrementAndGet() >= stopAfter) aborted.set(true)
                            } else {
                                ok.incrementAndGet(); straight.set(0)
                                if (after.state == Have.HAVE) bytes.addAndGet(after.bytes)
                            }
                        }
                        progress(ok.get(), failed.size, total, bytes.get())
                    }
                } finally { latch.countDown() }
            }.apply { isDaemon = true; name = "download-all" }.start()
        }
        latch.await()
        return Result(ok.get(), failed.toList(), queue.toList(), bytes.get(), aborted.get())
    }

    /**
     * The real fetches for [run]: each job goes to the same code the on-demand path uses (so the same hosts, the same mirror, the same
     * "not found" notes). Network errors are remembered in [firstError] for the one summary line in the report.
     */
    fun fetcher(ctx: Context, firstError: AtomicReference<String?>): (Job) -> Unit {
        val app = ctx.applicationContext
        val sprites = Sprites.dir(app)
        val cries = GameSound.cryStoreOf(app)
        val preferred = AtomicInteger(0)
        val netError = ThreadLocal<IOException?>() // the last network-level failure of this worker's current job
        val get: HttpGet = { url, p ->
            try { Mirror.get(url, progress = p) } catch (e: IOException) {
                firstError.compareAndSet(null, "${e.javaClass.simpleName}: ${e.message}")
                if (isNetworkFailure(e)) netError.set(e)
                throw e
            }
        }
        return { job ->
            netError.set(null)
            fetchOne(app, sprites, cries, job, get, preferred)
            // the fetch code swallows its IOException and leaves nothing; say it was the network so [run] counts it toward the stop
            val e = netError.get()
            if (e != null && check(job, Layout(sprites, cries.folder())).state == Have.NONE) throw e
        }
    }

    private fun fetchOne(app: Context, sprites: File, cries: CryStore, job: Job, get: HttpGet, preferred: AtomicInteger) {
        when (job.kind) {
            Kind.STATIC -> Sprites.fetchStaticBlocking(app, job.num, job.shiny, get, preferred)
            Kind.FRONT -> Sprites.fetchBattleQuiet(app, job.species!!, SpriteKind.FRONT, job.shiny, get)
            Kind.BACK -> Sprites.fetchBattleQuiet(app, job.species!!, SpriteKind.BACK, job.shiny, get)
            Kind.ITEM -> Sprites.fetchItemBlocking(app, job.id, get)
            Kind.BACKGROUND -> Backdrops.fetchFile(sprites, job.bg!!, get)
            Kind.CRY -> cries.fetch(job.num) { url -> get(url, null) }
        }
    }

    /** The folders of this phone. */
    fun layout(ctx: Context): Layout = Layout(Sprites.dir(ctx), GameSound.cryStoreOf(ctx).folder())

    // ---- texts (plain English) ----

    /** 1234 -> "1 234" (a no-break space, so a number never wraps). */
    fun grouped(n: Int): String = String.format(Locale.US, "%,d", n).replace(',', ' ')

    /** "140 MB", "7 MB", "1.2 GB", "under 1 MB": whole MB below 10, steps of 5 MB from 10 up. */
    fun roundedSize(bytes: Long): String {
        val mb = bytes / 1_000_000.0
        if (mb < 1) return "under 1 MB"
        val r = if (mb < 10) mb.roundToInt() else (mb / 5).roundToInt() * 5
        return if (r >= 1000) String.format(Locale.US, "%.1f GB", mb / 1000) else "$r MB"
    }

    /** "about 140 MB" ([roundedSize] with "about"; "less than 1 MB" for a tiny rest). */
    fun about(bytes: Long): String = roundedSize(bytes).let { if (it.startsWith("under")) "less than 1 MB" else "about $it" }

    /** The size of what a run has written so far: "45 MB", "3.2 MB", "850 KB". */
    fun written(bytes: Long): String = when {
        bytes >= 10_000_000 -> "${(bytes / 1_000_000.0).roundToInt()} MB"
        bytes >= 1_000_000 -> String.format(Locale.US, "%.1f MB", bytes / 1_000_000.0)
        else -> "${bytes / 1000} KB"
    }

    fun progressLabel(ok: Int, total: Int, bytes: Long) = "Downloaded ${grouped(ok)} / ${grouped(total)} · ${written(bytes)}"

    fun stoppedLabel(ok: Int, total: Int) = "Stopped at ${grouped(ok)} / ${grouped(total)}. What was downloaded is kept."

    fun failedLabel(n: Int) = "Done, ${grouped(n)} file${if (n == 1) "" else "s"} could not be downloaded. Tap to try again."

    const val OFFLINE_LABEL = "Stopped: the internet isn't answering. What was downloaded is kept. Tap to try again."

    const val BUTTON = "Download everything"

    fun resumeButton(leftBytes: Long) = "Resume download (${about(leftBytes)} left)"

    fun confirmTitle(resume: Boolean) = if (resume) "Resume download?" else "Download everything?"

    /** "About 140 MB (30 MB is already on this phone). Best on Wi-Fi." */
    fun confirmMessage(s: Scan): String {
        val size = about(s.todoBytes).replaceFirstChar { it.uppercase() }
        val have = if (s.haveBytes >= 1_000_000) " (${roundedSize(s.haveBytes)} is already on this phone)" else ""
        return "$size$have. Best on Wi-Fi."
    }
}
