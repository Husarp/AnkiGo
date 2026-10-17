package com.husarp.ankigo.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import java.io.File
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * Round 7 battle backgrounds: the 14 Pokémon Showdown arena pictures, downloaded on demand (never in the repo or the APK) into
 * `Sprites.dir(ctx)/bg/<name>.png`, so the Downloads size and "clear pictures" include them. Only while "Download pictures as
 * needed" and the background-pictures setting are both on ([allowed]); a missing picture is the plain gradient.
 * The pure parts (names, URL, file, gate) are unit-tested; [load] decodes off the main thread and keeps at most [KEEP] bitmaps.
 */
object Backdrops {
    /** The pictures used (Showdown's `fx/bg-<name>.png`); [com.husarp.ankigo.ui.BackdropRules] decides which Pokémon gets which. */
    val NAMES = listOf(
        "beach", "beachshore", "city", "dampcave", "deepsea", "desert", "earthycave",
        "forest", "icecave", "meadow", "mountain", "river", "route", "volcanocave",
    )

    private const val BASE = "https://play.pokemonshowdown.com/fx/"

    /** Memory kept: the picture on screen and one more (a bitmap is about 1.5 MB). */
    internal const val KEEP = 2

    /** Like the sprites that must exist: a "not found" note is tried again within the hour (a hiccup, not a missing file). */
    private const val RETRY_MS = 3600L * 1000

    fun url(name: String) = "${BASE}bg-$name.png"

    /** Where [name] is (or will be) saved under the sprite folder [spritesDir]. */
    fun file(spritesDir: File, name: String) = File(File(spritesDir, "bg"), "$name.png")

    /** Pictures show and download only with sprite downloads on AND the background setting on. */
    fun allowed(spritesOn: Boolean, picturesOn: Boolean) = spritesOn && picturesOn

    /** Runs the loads off the main thread; a var only so the design screenshots can load in place. */
    internal var pool: Executor = Executor { r -> realPool.execute(r) }
    private val realPool by lazy { Executors.newSingleThreadExecutor { r -> Thread(r, "backdrops").apply { isDaemon = true } } }

    /** The HTTP GET; a var only so tests and the design screenshots never touch the network. */
    internal var get: HttpGet = { url, progress -> Http.get(url, progress = progress) }

    private val main by lazy { Handler(Looper.getMainLooper()) }
    private val memory = object : LinkedHashMap<String, Bitmap>(4, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>) = size > KEEP
    }
    private val inflight = HashMap<String, MutableList<(Bitmap?) -> Unit>>()

    /** The decoded picture if it is in memory right now (main thread), else null. */
    fun inMemory(name: String): Bitmap? = synchronized(memory) { memory[name] }

    /** Bumped by [forget]: a load that started in an older generation neither stores its picture nor calls back. */
    private var generation = 0

    /** Forgets the pictures kept in memory (the files stay) and drops the loads still running. */
    fun forget() {
        synchronized(memory) { generation++; memory.clear() }
        synchronized(inflight) { inflight.clear() }
    }

    /** Stores [bmp] and hands back the waiting callbacks, unless [gen] is no longer current (then null: drop everything). */
    internal fun finish(gen: Int, name: String, bmp: Bitmap?): List<(Bitmap?) -> Unit>? {
        synchronized(memory) {
            if (gen != generation) return null
            if (bmp != null) memory[name] = bmp
        }
        return synchronized(inflight) { inflight.remove(name) }.orEmpty()
    }

    internal fun currentGeneration() = synchronized(memory) { generation }

    /**
     * Calls back on the main thread with the decoded picture, or null (not downloaded and [download] off, not found, network
     * trouble, unreadable file). The file comes from the cache, else from the network when [download] says so.
     */
    fun load(ctx: Context, name: String, download: Boolean, cb: (Bitmap?) -> Unit) {
        inMemory(name)?.let { main.post { cb(it) }; return }
        val first: Boolean
        synchronized(inflight) {
            val waiting = inflight[name]
            if (waiting != null) { waiting.add(cb); first = false } else { inflight[name] = mutableListOf(cb); first = true }
        }
        if (!first) return
        val dir = Sprites.dir(ctx)
        val gen = currentGeneration()
        pool.execute {
            val bmp = try { fetchBlocking(dir, name, download, get) } catch (_: Throwable) { null }
            val callbacks = finish(gen, name, bmp) ?: return@execute
            main.post { callbacks.forEach { it(bmp) } }
        }
    }

    /** The cached file, else (when [download]) the downloaded one, decoded; null if there is none. Background threads only. */
    internal fun fetchBlocking(spritesDir: File, name: String, download: Boolean, get: HttpGet): Bitmap? {
        val cached = file(spritesDir, name)
        val f = cached.takeIf { it.isFile } ?: if (download) fetchFile(spritesDir, name, get) else null
        f ?: return null
        val bmp = decode(f)
        if (bmp == null) f.delete() // a damaged file: gone, so the next try downloads it again
        return bmp
    }

    /** Downloads [name] through the sprite downloader (PNG check, size limit, "not found" note); the file or null. */
    internal fun fetchFile(spritesDir: File, name: String, get: HttpGet): File? =
        Sprites.download(File(File(spritesDir, "bg"), name), listOf(url(name)), get = get, retryMissingMs = RETRY_MS)

    private fun decode(f: File): Bitmap? = try {
        val o = BitmapFactory.Options().apply { inScaled = false; inPreferredConfig = Bitmap.Config.ARGB_8888 }
        val bytes = f.readBytes()
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
    } catch (_: Exception) { null }
}
