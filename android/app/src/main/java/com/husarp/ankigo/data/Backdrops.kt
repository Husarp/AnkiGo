package com.husarp.ankigo.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import com.husarp.ankigo.ui.Backdrop
import com.husarp.ankigo.ui.BackdropBlur
import com.husarp.ankigo.ui.BackdropSource
import java.io.File
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * Battle backgrounds (round 9: the 35 pictures of [com.husarp.ankigo.ui.BackdropCatalog]), downloaded on demand (never in the repo
 * or the APK) into `Sprites.dir(ctx)/bg/<fileName>`, so the Downloads size and "clear pictures" include them. Showdown's come from
 * `fx/bg-<id>.png` (the AnkiGo mirror first, [Mirror.get]), Adam's own from the `assets-1` release ([Mirror.ownUrl]). Only while
 * "Download pictures as needed" and the background-pictures setting are both on ([allowed]); a missing picture is the plain gradient.
 * The pure parts (URL, file, gate, blur maths in [BackdropBlur]) are unit-tested; [load] decodes off the main thread, makes the
 * blurred copy there too, and keeps at most [KEEP] pictures (each with its blur) in memory.
 */
object Backdrops {
    /** Memory kept: the picture on screen and one more (a bitmap is about 1.5 MB; the blur is a few KB). */
    internal const val KEEP = 2

    /** Like the sprites that must exist: a "not found" note is tried again within the hour (a hiccup, not a missing file). */
    private const val RETRY_MS = 3600L * 1000

    private const val SHOWDOWN_BASE = "https://play.pokemonshowdown.com/fx/"

    /** Showdown's URL of its picture called [name] (`fx/bg-<name>.png`). */
    fun url(name: String) = "${SHOWDOWN_BASE}bg-$name.png"

    /** Where [bg] is downloaded from: Showdown's `fx/bg-<id>.png`, or AnkiGo's own release file `bg-<id>.<ext>`. */
    fun url(bg: Backdrop): String = when (bg.source) {
        BackdropSource.SHOWDOWN -> url(bg.id)
        BackdropSource.OWN -> Mirror.ownUrl("bg-" + fileName(bg))
    }

    /** The picture's revision: Showdown's pictures are always the first (their names never change). */
    private fun revOf(bg: Backdrop) = if (bg.source == BackdropSource.OWN) bg.rev else 1

    /** The picture's name without the extension: `<id>` for revision 1, `<id>-<rev>` after a retouch. */
    internal fun baseName(bg: Backdrop) = if (revOf(bg) > 1) "${bg.id}-${revOf(bg)}" else bg.id

    /** The picture's file name: `<id>.<ext>` for revision 1, `<id>-<rev>.<ext>` after a retouch. */
    fun fileName(bg: Backdrop) = "${baseName(bg)}.${bg.ext}"

    /** Where [bg] is (or will be) saved under the sprite folder [spritesDir]: `bg/<fileName>` (round 7's Showdown files are the same names). */
    fun file(spritesDir: File, bg: Backdrop) = File(File(spritesDir, "bg"), fileName(bg))

    /** Pictures show and download only with sprite downloads on AND the background setting on. */
    fun allowed(spritesOn: Boolean, picturesOn: Boolean) = spritesOn && picturesOn

    /** Runs the loads off the main thread; a var only so the design screenshots can load in place. */
    internal var pool: Executor = Executor { r -> realPool.execute(r) }
    private val realPool by lazy { Executors.newSingleThreadExecutor { r -> Thread(r, "backdrops").apply { isDaemon = true } } }

    /** The HTTP GET (the mirror first, the original second); a var only so tests and the design screenshots never touch the network. */
    internal var get: HttpGet = { url, progress -> Mirror.get(url, progress = progress) }

    private val main by lazy { Handler(Looper.getMainLooper()) }
    private val memory = object : LinkedHashMap<String, BackdropPic>(4, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, BackdropPic>) = size > KEEP
    }
    private val inflight = HashMap<String, MutableList<(BackdropPic?) -> Unit>>()

    /** The decoded picture (with its blur) if it is in memory right now, else null. */
    fun inMemory(bg: Backdrop): BackdropPic? = synchronized(memory) { memory[bg.id] }

    /** Bumped by [forget]: a load that started in an older generation neither stores its picture nor calls back. */
    private var generation = 0

    /** Forgets the pictures kept in memory (the files stay) and drops the loads still running. */
    fun forget() {
        synchronized(memory) { generation++; memory.clear() }
        synchronized(inflight) { inflight.clear() }
    }

    /** Stores [pic] and hands back the waiting callbacks, unless [gen] is no longer current (then null: drop everything). */
    internal fun finish(gen: Int, id: String, pic: BackdropPic?): List<(BackdropPic?) -> Unit>? {
        synchronized(memory) {
            if (gen != generation) return null
            if (pic != null) memory[id] = pic
        }
        return synchronized(inflight) { inflight.remove(id) }.orEmpty()
    }

    internal fun currentGeneration() = synchronized(memory) { generation }

    /**
     * Calls back on the main thread with the decoded picture and its blurred copy, or null (not downloaded and [download] off, not
     * found, network trouble, unreadable file). The file comes from the cache, else from the network when [download] says so.
     */
    fun load(ctx: Context, bg: Backdrop, download: Boolean, cb: (BackdropPic?) -> Unit) {
        inMemory(bg)?.let { main.post { cb(it) }; return }
        val first: Boolean
        synchronized(inflight) {
            val waiting = inflight[bg.id]
            if (waiting != null) { waiting.add(cb); first = false } else { inflight[bg.id] = mutableListOf(cb); first = true }
        }
        if (!first) return
        val dir = Sprites.dir(ctx)
        val gen = currentGeneration()
        pool.execute {
            val pic = try { fetchBlocking(dir, bg, download, get) } catch (_: Throwable) { null }
            val callbacks = finish(gen, bg.id, pic) ?: return@execute
            main.post { callbacks.forEach { it(pic) } }
        }
    }

    /** The cached file, else (when [download]) the downloaded one, decoded and blurred; null if there is none. Background threads only. */
    internal fun fetchBlocking(spritesDir: File, bg: Backdrop, download: Boolean, get: HttpGet): BackdropPic? {
        val cached = file(spritesDir, bg)
        val f = cached.takeIf { it.isFile } ?: if (download) fetchFile(spritesDir, bg, get) else null
        f ?: return null
        val bmp = decode(f)
        if (bmp == null) { f.delete(); return null } // a damaged file: gone, so the next try downloads it again
        return try { BackdropPic(bmp, makeBlur(bmp)) } catch (_: Throwable) { null }
    }

    /** Downloads [bg] through the sprite downloader (PNG / WebP check, size limit, "not found" note); the file or null. */
    internal fun fetchFile(spritesDir: File, bg: Backdrop, get: HttpGet): File? {
        val f = Sprites.download(File(File(spritesDir, "bg"), baseName(bg)), listOf(url(bg)), get = get, retryMissingMs = RETRY_MS)
        if (f != null) deleteStale(spritesDir, bg)
        return f
    }

    /** After a retouched picture (rev > 1) arrived: removes the older files of the same id (`<id>.<ext>`, `<id>-<n>.<ext>`). Best effort. */
    internal fun deleteStale(spritesDir: File, bg: Backdrop) {
        try {
            val rev = revOf(bg)
            if (rev <= 1) return
            val dir = File(spritesDir, "bg")
            for (n in 1 until rev) {
                val base = if (n == 1) bg.id else "${bg.id}-$n"
                File(dir, "$base.${bg.ext}").delete()
            }
        } catch (_: Throwable) {}
    }

    private fun decode(f: File): Bitmap? = try {
        val o = BitmapFactory.Options().apply { inScaled = false; inPreferredConfig = Bitmap.Config.ARGB_8888 }
        val bytes = f.readBytes()
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
    } catch (_: Exception) { null }

    /** The blurred, darkened little copy ([BackdropBlur]): scaled down (filtered), blurred on the pixels, back into a bitmap. */
    private fun makeBlur(bmp: Bitmap): Bitmap {
        val (w, h) = BackdropBlur.smallSize(bmp.width, bmp.height)
        val small = if (w == bmp.width && h == bmp.height) bmp else Bitmap.createScaledBitmap(bmp, w, h, true)
        val px = IntArray(w * h)
        small.getPixels(px, 0, w, 0, 0, w, h)
        if (small !== bmp) small.recycle()
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(BackdropBlur.process(px, w, h), 0, w, 0, 0, w, h)
        return out
    }

    /** PLAN 167: how long a "not found" note keeps a picture from being asked for again ([DownloadAll] counts a fresh note as done). */
    internal val NOTE_MS: Long get() = RETRY_MS
}

/** A decoded background and its blurred, darkened copy ([BackdropBlur]; a few KB), kept and dropped together. */
class BackdropPic(val bitmap: Bitmap, val blur: Bitmap)
