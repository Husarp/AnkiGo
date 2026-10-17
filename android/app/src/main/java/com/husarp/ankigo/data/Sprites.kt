package com.husarp.ankigo.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import com.husarp.ankigo.Report
import com.husarp.ankigo.gfx.GifDecoder
import com.husarp.ankigo.game.Species
import java.io.File
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

// TICKET T03 owns this file. Public signatures are fixed; add private helpers freely.

enum class SpriteKind { FRONT, BACK }

/** One HTTP GET: the body, null for "not found", IOException for trouble. The real one is [Http.get]; tests pass a fake. */
internal typealias HttpGet = (url: String, progress: ((Long, Long) -> Unit)?) -> ByteArray?

private val realGet: HttpGet = { url, progress -> Http.get(url, progress = progress) }

/** A decoded sprite: one frame for PNGs, several for animated GIFs. Bitmaps are ARGB_8888 at native pixel size. */
class SpriteImage(val frames: List<Bitmap>, val delays: IntArray) {
    val width get() = frames[0].width
    val height get() = frames[0].height
    val animated get() = frames.size > 1
}

/**
 * Sprite download + cache (noBackupFilesDir/sprites). Battle sprites are fetched on demand with a fallback chain
 * (first that exists wins; a "not found" result is remembered for 7 days):
 *  FRONT:  PokeAPI BW animated (#1-649) -> Showdown gen5ani -> [3D] Showdown ani -> PokeAPI other/showdown
 *          -> Showdown gen5 static -> PokeAPI static
 *  BACK:   PokeAPI BW animated back (#1-649) -> Showdown gen5ani-back -> [3D] Showdown ani-back -> PokeAPI
 *          other/showdown/back -> Showdown gen5-back -> (none: caller mirrors FRONT)
 *  shiny variants of each (PokeAPI .../shiny/, Showdown gen5ani-shiny, ani-shiny, gen5ani-back-shiny, ani-back-shiny, ...).
 * The Gen-5 pixel animations come first so a Pokémon that has one keeps that look; the [3D] model animations
 * (Showdown's, mirrored by PokeAPI under the national number / form id) fill the rest of gens 6-9 and the regional
 * forms. They are never used for a base species #1-649, which always has the pixel ones.
 * Static 96x96 PNGs (Pokédex, team, widgets) come from PokeAPI sprites/pokemon/{num}.png (shiny/{num}.png);
 * item icons from PokeAPI sprites/items/{id}.png. PokeAPI URLs are pinned to DataStore.SPRITES_COMMIT.
 */
/** Where sprites come from (pure functions, unit-tested). */
internal object SpriteUrls {
    private const val SHOWDOWN = "https://play.pokemonshowdown.com/sprites/"
    private fun pokeApi() = "https://raw.githubusercontent.com/PokeAPI/sprites/${DataStore.SPRITES_COMMIT}/sprites/"
    /** The same repo files at the same commit through jsDelivr (identical bytes, checked), for when raw.githubusercontent.com refuses us. */
    private fun jsDelivr() = "https://cdn.jsdelivr.net/gh/PokeAPI/sprites@${DataStore.SPRITES_COMMIT}/sprites/"

    /**
     * URLs to try in order; the first that exists wins. Empty when the chain ends with "none" (BACK without any hit).
     * Showdown file names are [Species.spriteId] ("raticate-alola", "tauros-paldeacombat"), PokeAPI ones the number
     * (regional forms: pokemon id > 10000, no BW gif; PokeAPI's other/showdown copy uses the same id).
     */
    fun battle(sp: Species, kind: SpriteKind, shiny: Boolean): List<String> {
        val api = pokeApi()
        val out = ArrayList<String>()
        val bw = sp.num <= 649
        val s = if (shiny) "shiny/" else ""
        val suf = if (shiny) "-shiny" else ""
        val back = if (kind == SpriteKind.BACK) "back/" else ""
        val dash = if (kind == SpriteKind.BACK) "-back" else ""
        if (bw) out += "${api}pokemon/versions/generation-v/black-white/animated/$back$s${sp.num}.gif"
        out += "${SHOWDOWN}gen5ani$dash$suf/${sp.spriteId}.gif"
        if (!bw) {
            out += "${SHOWDOWN}ani$dash$suf/${sp.spriteId}.gif"
            out += "${api}pokemon/other/showdown/$back$s${sp.num}.gif"
        }
        out += "${SHOWDOWN}gen5$dash$suf/${sp.spriteId}.png"
        if (kind == SpriteKind.FRONT) out += "${api}pokemon/$s${sp.num}.png"
        return out
    }

    fun static(num: Int, shiny: Boolean) = "${pokeApi()}pokemon/${if (shiny) "shiny/" else ""}$num.png"

    /** Static picture URLs to try in order: GitHub raw, then the same file from jsDelivr (raw may throttle or refuse a burst of ~900 requests). */
    fun staticChain(num: Int, shiny: Boolean) =
        listOf(static(num, shiny), "${jsDelivr()}pokemon/${if (shiny) "shiny/" else ""}$num.png")

    fun item(id: String) = "${pokeApi()}items/$id.png"

    fun isGif(b: ByteArray) = b.size > 6 && b[0] == 'G'.code.toByte() && b[1] == 'I'.code.toByte() &&
        b[2] == 'F'.code.toByte() && b[3] == '8'.code.toByte()

    private val PNG = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    fun isPng(b: ByteArray) = b.size > 8 && PNG.indices.all { b[it] == PNG[it] }

    /** True when the bytes are what the URL's extension promises (an HTML error page must never be saved as a sprite). */
    fun validFor(url: String, b: ByteArray) = if (url.endsWith(".gif")) isGif(b) else isPng(b)
}

/** Memory cache bounded by bytes, least recently used out first. */
internal class ByteLru<V : Any>(private val maxBytes: Long, private val sizeOf: (V) -> Long) {
    private val map = LinkedHashMap<String, V>(16, 0.75f, true)
    private var bytes = 0L

    @Synchronized fun get(key: String): V? = map[key]

    @Synchronized fun put(key: String, v: V) {
        val size = sizeOf(v)
        if (size > maxBytes) return
        map.put(key, v)?.let { bytes -= sizeOf(it) }
        bytes += size
        val iter = map.entries.iterator()
        while (bytes > maxBytes && iter.hasNext()) {
            val e = iter.next()
            if (e.value === v) continue
            bytes -= sizeOf(e.value)
            iter.remove()
        }
    }

    @Synchronized fun clear() { map.clear(); bytes = 0 }
}

/**
 * Sprite download + cache (noBackupFilesDir/sprites). Battle sprites are fetched on demand with a fallback chain
 * (first that exists wins; a "not found" result is remembered for 7 days):
 *  FRONT:  PokeAPI BW animated (#1-649) -> Showdown gen5ani -> [3D] Showdown ani -> PokeAPI other/showdown
 *          -> Showdown gen5 static -> PokeAPI static
 *  BACK:   PokeAPI BW animated back (#1-649) -> Showdown gen5ani-back -> [3D] Showdown ani-back -> PokeAPI
 *          other/showdown/back -> Showdown gen5-back -> (none: caller mirrors FRONT)
 *  shiny variants of each (PokeAPI .../shiny/, Showdown gen5ani-shiny, ani-shiny, gen5ani-back-shiny, ani-back-shiny, ...).
 * The Gen-5 pixel animations come first so a Pokémon that has one keeps that look; the [3D] model animations
 * (Showdown's, mirrored by PokeAPI under the national number / form id) fill the rest of gens 6-9 and the regional
 * forms. They are never used for a base species #1-649, which always has the pixel ones.
 * Static 96x96 PNGs (Pokédex, team, widgets) come from PokeAPI sprites/pokemon/{num}.png (shiny/{num}.png);
 * item icons from PokeAPI sprites/items/{id}.png. PokeAPI URLs are pinned to DataStore.SPRITES_COMMIT.
 */
object Sprites {
    fun enabled(ctx: Context) = !ctx.getSharedPreferences("firstrun", 0).getBoolean("skipSprites", false)
    fun setEnabled(ctx: Context, enabled: Boolean) = ctx.getSharedPreferences("firstrun", 0).edit().putBoolean("skipSprites", !enabled).apply()

    private const val RETRY_MISSING_MS = 7L * 24 * 3600 * 1000
    /**
     * How long a "not found" is remembered for a picture that must exist (every Pokédex picture, every FRONT sprite: the chain
     * ends with PokeAPI's still). An answer like that is a hiccup (CDN, throttle), so it is tried again within the hour.
     */
    private const val RETRY_EXPECTED_MS = 3600L * 1000
    private const val IMAGE_CACHE_BYTES = 24L * 1024 * 1024
    private const val BITMAP_CACHE_BYTES = 16L * 1024 * 1024
    /** A sprite file bigger than this is skipped like a missing one (the largest 3D animation is ~0.6 MB). */
    internal const val MAX_SPRITE_BYTES = 2_000_000
    /** Decoded frames kept per sprite (pixels; x4 bytes): long 3D animations keep every 2nd/3rd frame instead. */
    internal const val DECODE_MAX_PIXELS = 2_000_000L

    private val main by lazy { Handler(Looper.getMainLooper()) }
    private val pool by lazy {
        Executors.newFixedThreadPool(4) { r -> Thread(r, "sprites").apply { isDaemon = true } }
    }
    private val inflight = HashMap<String, MutableList<(Any?) -> Unit>>()

    private val images = ByteLru<SpriteImage>(IMAGE_CACHE_BYTES) { img -> img.frames.sumOf { it.byteCount.toLong() } }
    private val bitmaps = ByteLru<Bitmap>(BITMAP_CACHE_BYTES) { it.byteCount.toLong() }

    fun dir(ctx: Context): File = File(ctx.noBackupFilesDir, "sprites").also { if (!upgraded) upgradeBattleCache(it) }

    @Volatile private var upgraded = false

    /**
     * Once per sprite folder: drops the still battle pictures and "not found" notes that older versions saved for
     * Pokémon without a Gen-5 animation (gens 6-9, regional forms), so they are fetched again and get the 3D animation.
     * GIFs, the static pictures and item icons stay.
     */
    @Synchronized internal fun upgradeBattleCache(dir: File) {
        upgraded = true
        val mark = File(dir, ".battle-v2")
        if (!mark.isFile) {
            for (sub in listOf("front", "back")) {
                File(dir, sub).listFiles()?.forEach { if (it.name.endsWith(".png") || it.name.endsWith(".missing")) it.delete() }
            }
            try { dir.mkdirs(); mark.writeText("1") } catch (_: IOException) { }
        }
        // Every Pokédex picture exists, so a "not found" note in static/ is always a wrong one (version 0.2.0 saved one for an
        // error page; a note blocked the picture for 7 days). Dropped once; the pictures themselves stay.
        val staticMark = File(dir, ".static-v2")
        if (!staticMark.isFile) {
            File(dir, "static").listFiles()?.forEach { if (it.name.endsWith(".missing")) it.delete() }
            try { dir.mkdirs(); staticMark.writeText("1") } catch (_: IOException) { }
        }
    }

    private fun baseName(num: Int, shiny: Boolean) = if (shiny) "${num}s" else "$num"
    private fun folder(ctx: Context, kind: SpriteKind) = File(dir(ctx), if (kind == SpriteKind.FRONT) "front" else "back")

    /** Cached battle sprite file (gif or png) or null. */
    fun cached(ctx: Context, sp: Species, kind: SpriteKind, shiny: Boolean): File? {
        val f = folder(ctx, kind)
        val n = baseName(sp.num, shiny)
        return File(f, "$n.gif").takeIf { it.isFile } ?: File(f, "$n.png").takeIf { it.isFile }
    }

    /** Fetches (background pool, fallback chain) and calls back on the main thread with the file or null. */
    fun fetch(ctx: Context, sp: Species, kind: SpriteKind, shiny: Boolean, cb: (File?) -> Unit) {
        val done = cached(ctx, sp, kind, shiny)
        if (done != null) { main.post { cb(done) }; return }
        if (!enabled(ctx)) { main.post { cb(null) }; return }
        val key = File(folder(ctx, kind), baseName(sp.num, shiny)).path
        enqueue(key, { fetchBlocking(ctx, sp, kind, shiny) }) { cb(it as File?) }
    }

    /** Blocking variant (background threads only). */
    fun fetchBlocking(ctx: Context, sp: Species, kind: SpriteKind, shiny: Boolean): File? {
        cached(ctx, sp, kind, shiny)?.let { return it }
        if (!enabled(ctx)) return null
        val base = File(folder(ctx, kind), baseName(sp.num, shiny))
        return download(base, SpriteUrls.battle(sp, kind, shiny), retryMissingMs = if (kind == SpriteKind.FRONT) RETRY_EXPECTED_MS else RETRY_MISSING_MS)
    }

    /** Starts front+back downloads in the background (next encounter, new catch). */
    fun prefetch(ctx: Context, sp: Species, shiny: Boolean) {
        fetch(ctx, sp, SpriteKind.FRONT, shiny) { }
        fetch(ctx, sp, SpriteKind.BACK, shiny) { }
    }

    /** Decodes a GIF (GifDecoder) or PNG file; small LRU cache keyed by path. Null if unreadable. */
    fun decode(file: File): SpriteImage? {
        val key = "${file.path}|${file.lastModified()}"
        images.get(key)?.let { return it }
        val img = try {
            val bytes = file.readBytes()
            if (SpriteUrls.isGif(bytes)) {
                val gif = GifDecoder.decode(bytes, DECODE_MAX_PIXELS)
                SpriteImage(
                    gif.frames.map { Bitmap.createBitmap(it.pixels, gif.width, gif.height, Bitmap.Config.ARGB_8888) },
                    IntArray(gif.frames.size) { gif.frames[it].delayMs },
                )
            } else {
                val bmp = decodePng(bytes) ?: return null
                SpriteImage(listOf(bmp), intArrayOf(0))
            }
        } catch (_: Exception) {
            return null
        }
        images.put(key, img)
        return img
    }

    private fun decodePng(bytes: ByteArray): Bitmap? {
        val o = BitmapFactory.Options().apply { inScaled = false; inPreferredConfig = Bitmap.Config.ARGB_8888 }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
    }

    /** The static PNG file path (may not exist yet). */
    fun staticFile(ctx: Context, num: Int, shiny: Boolean = false): File = staticFileIn(File(dir(ctx), "static"), num, shiny)

    private fun staticFileIn(staticDir: File, num: Int, shiny: Boolean = false): File = File(staticDir, baseName(num, shiny) + ".png")

    /** Decoded static PNG from disk (LRU cached), or null if not downloaded. Never touches the network. */
    fun staticBitmap(ctx: Context, num: Int, shiny: Boolean = false): Bitmap? = diskBitmap(staticFile(ctx, num, shiny))

    /** Downloads a missing static PNG in the background, then calls back on the main thread. */
    fun fetchStatic(ctx: Context, num: Int, shiny: Boolean, cb: (Bitmap?) -> Unit) {
        val file = staticFile(ctx, num, shiny)
        diskBitmap(file)?.let { main.post { cb(it) }; return }
        if (!enabled(ctx)) { main.post { cb(null) }; return }
        enqueue(file.path, {
            download(File(file.parentFile, baseName(num, shiny)), SpriteUrls.staticChain(num, shiny), retryMissingMs = RETRY_EXPECTED_MS)
            diskBitmap(file)
        }) { cb(it as Bitmap?) }
    }

    /** Blocking, parallel (6 threads): downloads every missing static PNG of [nums]. progress(done, total). Returns failures. */
    fun downloadStatics(ctx: Context, nums: List<Int>, bytesProgress: ((Long, Long) -> Unit)? = null, stop: () -> Boolean = { false }, progress: (Int, Int) -> Unit): Int {
        Report.init(ctx)
        val firstError = java.util.concurrent.atomic.AtomicReference<String?>(null)
        val get: HttpGet = { url, p ->
            try { realGet(url, p) } catch (e: IOException) { firstError.compareAndSet(null, "${e.javaClass.simpleName}: ${e.message}"); throw e }
        }
        val failed = downloadStatics(File(dir(ctx), "static"), { enabled(ctx) && !stop() }, nums, bytesProgress, progress, get)
        // shows in Settings -> Diagnostics, so a failing phone says why
        Report.log("PICS", "static pictures: $failed of ${nums.size} failed" + (firstError.get()?.let { ", first network error $it" } ?: ""))
        return failed
    }

    internal fun downloadStatics(
        staticDir: File, enabled: () -> Boolean, nums: List<Int>, bytesProgress: ((Long, Long) -> Unit)? = null,
        progress: (Int, Int) -> Unit, get: HttpGet = realGet,
    ): Int {
        val total = nums.size
        val todo = nums.filter { !staticFileIn(staticDir, it).isFile }
        val transferred = java.util.concurrent.atomic.AtomicLong(0)
        val estimate = todo.size * 1500L
        val done = AtomicInteger(total - todo.size)
        val failures = AtomicInteger(0)
        if (todo.isEmpty()) { progress(total, total); return 0 }
        progress(done.get(), total)
        val queue = java.util.concurrent.ConcurrentLinkedQueue(todo)
        val preferred = AtomicInteger(0) // the host that worked last goes first, so a throttled one isn't asked for every picture
        val workers = minOf(6, todo.size)
        val latch = CountDownLatch(workers)
        repeat(workers) {
            Thread {
                try {
                    while (true) {
                        if (!enabled()) break
                        val num = queue.poll() ?: break
                        val base = File(staticDir, baseName(num, false))
                        // one retry for network trouble; every picture of the list exists, so an old "not found" note doesn't count
                        var seen = 0L
                        val ok = download(base, SpriteUrls.staticChain(num, false), retries = 1, progress = { n, _ ->
                            val delta = (n - seen).coerceAtLeast(0); seen = n
                            val bytes = transferred.addAndGet(delta)
                            bytesProgress?.invoke(bytes, maxOf(estimate, bytes))
                        }, get = get, ignoreMissing = true, preferred = preferred) != null
                        if (!ok) failures.incrementAndGet()
                        progress(done.incrementAndGet(), total)
                    }
                } finally {
                    latch.countDown()
                }
            }.apply { isDaemon = true; name = "sprites-static" }.start()
        }
        latch.await()
        return failures.get()
    }

    /** One owned Pokémon (or a shiny one) whose Pokédex picture is not usable: [noteAgeMs] is the age of a "not found" note, [broken] a file that doesn't decode. */
    internal class PictureGap(val num: Int, val shiny: Boolean, val noteAgeMs: Long?, val broken: Boolean)

    /** The pictures of [keys] (species number, shiny) that are missing from [staticDir] or can't be decoded ([decodes]). */
    internal fun pictureGaps(
        staticDir: File, keys: Collection<Pair<Int, Boolean>>, now: Long = System.currentTimeMillis(), decodes: (File) -> Boolean = { true },
    ): List<PictureGap> = keys.distinct().sortedBy { it.first }.mapNotNull { (num, shiny) ->
        val f = staticFileIn(staticDir, num, shiny)
        val broken = f.isFile && !decodes(f)
        if (f.isFile && !broken) return@mapNotNull null
        val marker = missingMarker(File(staticDir, baseName(num, shiny)))
        PictureGap(num, shiny, if (marker.isFile) (now - marker.lastModified()).coerceAtLeast(0) else null, broken)
    }

    /** Gaps of the Pokémon in [keys] on this phone (Diagnostics). */
    internal fun pictureGaps(ctx: Context, keys: Collection<Pair<Int, Boolean>>): List<PictureGap> =
        pictureGaps(File(dir(ctx), "static"), keys, decodes = { diskBitmap(it) != null })

    /** One Diagnostics line per gap: "#1018 Archaludon: no picture, 'not found' note from 3 days ago". */
    internal fun describeGaps(gaps: List<PictureGap>, nameOf: (Int) -> String): List<String> = gaps.map { g ->
        val what = if (g.broken) "picture file can't be read" else "no picture on the phone"
        val note = g.noteAgeMs?.let { ", a 'not found' note from ${ageText(it)} blocks the download" } ?: ""
        "#${g.num} ${nameOf(g.num)}${if (g.shiny) " (shiny)" else ""}: $what$note"
    }

    private fun ageText(ms: Long): String = when {
        ms < 3600_000L -> "${ms / 60_000} min ago"
        ms < 86_400_000L -> "${ms / 3600_000} h ago"
        else -> "${ms / 86_400_000L} days ago"
    }

    /**
     * Tries the picture again right now, ignoring any "not found" note (blocking, background thread only), and says what happened:
     * "downloaded", "network problem: ...", "not found on both hosts" or "downloaded but can't be read".
     */
    fun retryStatic(ctx: Context, num: Int, shiny: Boolean): String {
        var error: String? = null
        val get: HttpGet = { url, p -> try { realGet(url, p) } catch (e: IOException) { error = "${e.javaClass.simpleName}: ${e.message}"; throw e } }
        val file = staticFile(ctx, num, shiny)
        file.delete()
        val got = download(File(file.parentFile, baseName(num, shiny)), SpriteUrls.staticChain(num, shiny), retries = 1, get = get, ignoreMissing = true)
        return when {
            got != null && diskBitmap(file) != null -> "downloaded"
            got != null -> { file.delete(); "downloaded but can't be read" }
            error != null -> "network problem: $error"
            else -> "not found on both hosts"
        }
    }

    /** Item icon from disk (LRU), or null. */
    fun itemBitmap(ctx: Context, item: String): Bitmap? = diskBitmap(itemFile(ctx, item))

    /** Downloads a missing item icon in the background, then calls back on the main thread (null if PokeAPI has none). */
    fun fetchItem(ctx: Context, item: String, cb: (Bitmap?) -> Unit) {
        val file = itemFile(ctx, item)
        diskBitmap(file)?.let { main.post { cb(it) }; return }
        if (!enabled(ctx)) { main.post { cb(null) }; return }
        enqueue(file.path, {
            download(File(file.parentFile, item), listOf(SpriteUrls.item(item)))
            diskBitmap(file)
        }) { cb(it as Bitmap?) }
    }

    /** Bytes used by the sprite folder. */
    fun size(ctx: Context): Long = dir(ctx).walkTopDown().filter { it.isFile }.sumOf { it.length() }

    /** Deletes all cached sprites. */
    fun clear(ctx: Context) {
        dir(ctx).deleteRecursively()
        images.clear()
        bitmaps.clear()
        Backdrops.forget()   // the battle background pictures live in the same folder (bg/)
    }

    // ---- internals ----

    private fun itemFile(ctx: Context, item: String) = File(File(dir(ctx), "item"), "$item.png")

    private fun diskBitmap(file: File): Bitmap? {
        if (!file.isFile) return null
        val key = "${file.path}|${file.lastModified()}"
        bitmaps.get(key)?.let { return it }
        val bmp = try { decodePng(file.readBytes()) } catch (_: Exception) { null } ?: return null
        bitmaps.put(key, bmp)
        return bmp
    }

    /**
     * Runs [work] once on the pool per [key]; every caller waiting for the same key gets the result on the main thread.
     */
    private fun enqueue(key: String, work: () -> Any?, cb: (Any?) -> Unit) {
        val start: Boolean
        synchronized(inflight) {
            val waiting = inflight[key]
            if (waiting != null) { waiting.add(cb); start = false }
            else { inflight[key] = mutableListOf(cb); start = true }
        }
        if (!start) return
        pool.execute {
            val result = try { work() } catch (_: Throwable) { null }
            val callbacks = synchronized(inflight) { inflight.remove(key) }.orEmpty()
            main.post { callbacks.forEach { it(result) } }
        }
    }

    private fun missingMarker(base: File) = File(base.path + ".missing")

    /**
     * Tries [urls] in order and saves the first valid hit as `<base>.gif|png`. If every URL answered "not found" a
     * `.missing` marker is written (no retry for 7 days, unless [ignoreMissing]); network errors and answers that
     * aren't a picture (an error page) leave no marker so a later call tries again. [preferred] (shared by the callers
     * of one batch) holds the index of the URL that worked last; that one is tried first next time.
     */
    internal fun download(
        base: File, urls: List<String>, retries: Int = 0, progress: ((Long, Long) -> Unit)? = null, get: HttpGet = realGet,
        ignoreMissing: Boolean = false, preferred: AtomicInteger? = null, retryMissingMs: Long = RETRY_MISSING_MS,
    ): File? {
        val marker = missingMarker(base)
        if (!ignoreMissing && marker.isFile && System.currentTimeMillis() - marker.lastModified() < retryMissingMs) return null
        base.parentFile?.mkdirs()
        var networkError = false
        val first = preferred?.get() ?: 0
        for (url in urls.indices.map { urls[(first + it) % urls.size] }) {
            var bytes: ByteArray? = null
            var attempt = 0
            while (true) {
                try { bytes = get(url, progress); break } catch (_: IOException) {
                    if (attempt++ >= retries) { networkError = true; break }
                }
            }
            if (bytes == null) continue
            if (!SpriteUrls.validFor(url, bytes)) { networkError = true; continue } // an error page, not "not found"
            if (bytes.size > MAX_SPRITE_BYTES) continue // too big to keep: counts as not there
            val ext = if (url.endsWith(".gif")) "gif" else "png"
            val target = File(base.parentFile, "${base.name}.$ext")
            val tmp = File(base.parentFile, "${base.name}.$ext.${System.nanoTime()}.tmp")
            try {
                tmp.writeBytes(bytes)
                if (!tmp.renameTo(target)) { tmp.delete(); networkError = true; continue } // a disk problem is not "not found" either
            } catch (_: IOException) {
                tmp.delete(); networkError = true; continue
            }
            marker.delete()
            preferred?.set(urls.indexOf(url))
            return target
        }
        if (!networkError) {
            try { marker.writeText(System.currentTimeMillis().toString()) } catch (_: IOException) { }
        }
        return null
    }
}
