package com.husarp.ankigo.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import com.husarp.ankigo.gfx.GifDecoder
import com.husarp.ankigo.game.Species
import java.io.File
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

// TICKET T03 owns this file. Public signatures are fixed; add private helpers freely.

enum class SpriteKind { FRONT, BACK }

/** A decoded sprite: one frame for PNGs, several for animated GIFs. Bitmaps are ARGB_8888 at native pixel size. */
class SpriteImage(val frames: List<Bitmap>, val delays: IntArray) {
    val width get() = frames[0].width
    val height get() = frames[0].height
    val animated get() = frames.size > 1
}

/**
 * Sprite download + cache (noBackupFilesDir/sprites). Battle sprites are fetched on demand with a fallback chain
 * (first that exists wins; a "not found" result is remembered for 7 days):
 *  FRONT:  PokeAPI BW animated (#1-649) -> Showdown gen5ani -> Showdown gen5 static -> PokeAPI static
 *  BACK:   PokeAPI BW animated back (#1-649) -> Showdown gen5ani-back -> Showdown gen5-back -> (none: caller mirrors FRONT)
 *  shiny variants of each (PokeAPI .../shiny/, Showdown gen5ani-shiny, gen5ani-back-shiny, gen5-shiny, gen5-back-shiny).
 * Static 96x96 PNGs (Pokédex, team, widgets) come from PokeAPI sprites/pokemon/{num}.png (shiny/{num}.png);
 * item icons from PokeAPI sprites/items/{id}.png. PokeAPI URLs are pinned to DataStore.SPRITES_COMMIT.
 */
/** Where sprites come from (pure functions, unit-tested). */
internal object SpriteUrls {
    private const val SHOWDOWN = "https://play.pokemonshowdown.com/sprites/"
    private fun pokeApi() = "https://raw.githubusercontent.com/PokeAPI/sprites/${DataStore.SPRITES_COMMIT}/sprites/"

    /** URLs to try in order; the first that exists wins. Empty when the chain ends with "none" (BACK without any hit). */
    fun battle(sp: Species, kind: SpriteKind, shiny: Boolean): List<String> {
        val api = pokeApi()
        val out = ArrayList<String>()
        val bw = sp.num <= 649
        if (kind == SpriteKind.FRONT) {
            val s = if (shiny) "shiny/" else ""
            val suf = if (shiny) "-shiny" else ""
            if (bw) out += "${api}pokemon/versions/generation-v/black-white/animated/$s${sp.num}.gif"
            out += "${SHOWDOWN}gen5ani$suf/${sp.id}.gif"
            out += "${SHOWDOWN}gen5$suf/${sp.id}.png"
            out += "${api}pokemon/$s${sp.num}.png"
        } else {
            val s = if (shiny) "shiny/" else ""
            val suf = if (shiny) "-shiny" else ""
            if (bw) out += "${api}pokemon/versions/generation-v/black-white/animated/back/$s${sp.num}.gif"
            out += "${SHOWDOWN}gen5ani-back$suf/${sp.id}.gif"
            out += "${SHOWDOWN}gen5-back$suf/${sp.id}.png"
        }
        return out
    }

    fun static(num: Int, shiny: Boolean) = "${pokeApi()}pokemon/${if (shiny) "shiny/" else ""}$num.png"
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
 *  FRONT:  PokeAPI BW animated (#1-649) -> Showdown gen5ani -> Showdown gen5 static -> PokeAPI static
 *  BACK:   PokeAPI BW animated back (#1-649) -> Showdown gen5ani-back -> Showdown gen5-back -> (none: caller mirrors FRONT)
 *  shiny variants of each (PokeAPI .../shiny/, Showdown gen5ani-shiny, gen5ani-back-shiny, gen5-shiny, gen5-back-shiny).
 * Static 96x96 PNGs (Pokédex, team, widgets) come from PokeAPI sprites/pokemon/{num}.png (shiny/{num}.png);
 * item icons from PokeAPI sprites/items/{id}.png. PokeAPI URLs are pinned to DataStore.SPRITES_COMMIT.
 */
object Sprites {
    private const val RETRY_MISSING_MS = 7L * 24 * 3600 * 1000
    private const val IMAGE_CACHE_BYTES = 24L * 1024 * 1024
    private const val BITMAP_CACHE_BYTES = 16L * 1024 * 1024

    private val main by lazy { Handler(Looper.getMainLooper()) }
    private val pool by lazy {
        Executors.newFixedThreadPool(4) { r -> Thread(r, "sprites").apply { isDaemon = true } }
    }
    private val inflight = HashMap<String, MutableList<(Any?) -> Unit>>()

    private val images = ByteLru<SpriteImage>(IMAGE_CACHE_BYTES) { img -> img.frames.sumOf { it.byteCount.toLong() } }
    private val bitmaps = ByteLru<Bitmap>(BITMAP_CACHE_BYTES) { it.byteCount.toLong() }

    fun dir(ctx: Context): File = File(ctx.noBackupFilesDir, "sprites")

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
        val key = File(folder(ctx, kind), baseName(sp.num, shiny)).path
        enqueue(key, { fetchBlocking(ctx, sp, kind, shiny) }) { cb(it as File?) }
    }

    /** Blocking variant (background threads only). */
    fun fetchBlocking(ctx: Context, sp: Species, kind: SpriteKind, shiny: Boolean): File? {
        cached(ctx, sp, kind, shiny)?.let { return it }
        val base = File(folder(ctx, kind), baseName(sp.num, shiny))
        return download(base, SpriteUrls.battle(sp, kind, shiny))
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
                val gif = GifDecoder.decode(bytes)
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
    fun staticFile(ctx: Context, num: Int, shiny: Boolean = false): File =
        File(File(dir(ctx), "static"), baseName(num, shiny) + ".png")

    /** Decoded static PNG from disk (LRU cached), or null if not downloaded. Never touches the network. */
    fun staticBitmap(ctx: Context, num: Int, shiny: Boolean = false): Bitmap? = diskBitmap(staticFile(ctx, num, shiny))

    /** Downloads a missing static PNG in the background, then calls back on the main thread. */
    fun fetchStatic(ctx: Context, num: Int, shiny: Boolean, cb: (Bitmap?) -> Unit) {
        val file = staticFile(ctx, num, shiny)
        diskBitmap(file)?.let { main.post { cb(it) }; return }
        enqueue(file.path, {
            download(File(file.parentFile, baseName(num, shiny)), listOf(SpriteUrls.static(num, shiny)))
            diskBitmap(file)
        }) { cb(it as Bitmap?) }
    }

    /** Blocking, parallel (6 threads): downloads every missing static PNG of [nums]. progress(done, total). Returns failures. */
    fun downloadStatics(ctx: Context, nums: List<Int>, progress: (Int, Int) -> Unit): Int {
        val total = nums.size
        val todo = nums.filter { !staticFile(ctx, it).isFile }
        val done = AtomicInteger(total - todo.size)
        val failures = AtomicInteger(0)
        if (todo.isEmpty()) { progress(total, total); return 0 }
        progress(done.get(), total)
        val queue = java.util.concurrent.ConcurrentLinkedQueue(todo)
        val workers = minOf(6, todo.size)
        val latch = CountDownLatch(workers)
        repeat(workers) {
            Thread {
                try {
                    while (true) {
                        val num = queue.poll() ?: break
                        val file = staticFile(ctx, num)
                        val base = File(file.parentFile, baseName(num, false))
                        // one retry for network trouble; a "not found" is final
                        val ok = download(base, listOf(SpriteUrls.static(num, false)), retries = 1) != null
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

    /** Item icon from disk (LRU), or null. */
    fun itemBitmap(ctx: Context, item: String): Bitmap? = diskBitmap(itemFile(ctx, item))

    /** Downloads a missing item icon in the background, then calls back on the main thread (null if PokeAPI has none). */
    fun fetchItem(ctx: Context, item: String, cb: (Bitmap?) -> Unit) {
        val file = itemFile(ctx, item)
        diskBitmap(file)?.let { main.post { cb(it) }; return }
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
     * `.missing` marker is written (no retry for 7 days); network errors leave no marker so a later call tries again.
     */
    private fun download(base: File, urls: List<String>, retries: Int = 0): File? {
        val marker = missingMarker(base)
        if (marker.isFile && System.currentTimeMillis() - marker.lastModified() < RETRY_MISSING_MS) return null
        base.parentFile?.mkdirs()
        var networkError = false
        for (url in urls) {
            var bytes: ByteArray? = null
            var attempt = 0
            while (true) {
                try { bytes = Http.get(url); break } catch (_: IOException) {
                    if (attempt++ >= retries) { networkError = true; break }
                }
            }
            if (bytes == null || !SpriteUrls.validFor(url, bytes)) continue
            val ext = if (url.endsWith(".gif")) "gif" else "png"
            val target = File(base.parentFile, "${base.name}.$ext")
            val tmp = File(base.parentFile, "${base.name}.$ext.${System.nanoTime()}.tmp")
            try {
                tmp.writeBytes(bytes)
                if (!tmp.renameTo(target)) { tmp.delete(); continue }
            } catch (_: IOException) {
                tmp.delete(); networkError = true; continue
            }
            marker.delete()
            return target
        }
        if (!networkError) {
            try { marker.writeText(System.currentTimeMillis().toString()) } catch (_: IOException) { }
        }
        return null
    }
}
