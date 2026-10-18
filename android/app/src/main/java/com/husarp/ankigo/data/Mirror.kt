package com.husarp.ankigo.data

import android.content.Context
import com.husarp.ankigo.Report
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.zip.CRC32
import java.util.zip.GZIPInputStream

// R8 contract (manager): the declarations below are fixed; R8-01 writes the bodies and adds private/internal helpers freely.

/** The two network calls the mirror makes; [Http] in the app, a fake in the tests. */
internal interface MirrorHost {
    /** Same contract as [Http.get]. */
    fun get(url: String, timeoutMs: Int, progress: ((Long, Long) -> Unit)?): ByteArray?
    /** Same contract as [Http.getRange]. */
    fun range(url: String, offset: Long, length: Int, timeoutMs: Int, progress: ((Long, Long) -> Unit)?): ByteArray?
}

private object HttpHost : MirrorHost {
    override fun get(url: String, timeoutMs: Int, progress: ((Long, Long) -> Unit)?) = Http.get(url, timeoutMs, progress)
    override fun range(url: String, offset: Long, length: Int, timeoutMs: Int, progress: ((Long, Long) -> Unit)?) = Http.getRange(url, offset, length, timeoutMs, progress)
}

/**
 * AnkiGo's own copy of every third-party file the app downloads (PLAN 117): the GitHub release [TAG] of Husarp/AnkiGo holds STORED
 * zips of the sprites, pictures, item icons, cries, game data and Showdown backgrounds, an index (`index.json.gz`), and Adam's own
 * backgrounds as plain files. The app asks the mirror first and the original source second. Never in the code tree or the APK.
 *
 * The index maps a key (see [keyFor]) to one byte range of one pack; a file is read with one HTTP Range request and counts only
 * when its length and crc32 match. Any trouble falls back to the original URL, so the mirror can only ever make things faster.
 */
object Mirror {
    const val TAG = "assets-1"
    const val BASE = "https://github.com/Husarp/AnkiGo/releases/download/$TAG/"

    private const val LOG = "MIRROR"
    private const val INDEX_FILE = "assets-1-index.json"
    private const val RETRY_MS = 10 * 60 * 1000L
    private const val PACK_FAILS_TO_TRIP = 3
    private const val PACK_COOLDOWN_MS = 10 * 60 * 1000L
    private const val INDEX_TIMEOUT_MS = 30_000
    private const val MAX_INDEX_BYTES = 32 * 1024 * 1024

    /** One file inside a pack: pack number, byte offset, length, crc32. */
    private class Entry(val pack: Int, val offset: Long, val length: Int, val crc: Long)
    private class Index(val packs: List<String>, val files: Map<String, Entry>)

    // Test seams (see MirrorTest): the network, the clock and the cache folder.
    @Volatile internal var host: MirrorHost = HttpHost
    @Volatile internal var clock: () -> Long = System::currentTimeMillis
    @Volatile internal var cacheDir: File? = null
    @Volatile internal var logSink: (String) -> Unit = { msg -> try { Report.log(LOG, msg) } catch (_: Throwable) { } }

    @Volatile private var appCtx: Context? = null
    private val loadLock = Any()
    @Volatile private var index: Index? = null
    private var failedAt: Long? = null
    private val fallbackLogged = java.util.concurrent.atomic.AtomicBoolean(false)
    // Pack backoff: consecutive transport failures of pack reads; at PACK_FAILS_TO_TRIP the mirror is skipped until packDownUntil.
    private val packFails = java.util.concurrent.atomic.AtomicInteger(0)
    @Volatile private var packDownUntil = 0L

    /** The release URL of one of AnkiGo's own files (Adam's backgrounds: "bg-night-alley.webp"). */
    fun ownUrl(file: String) = BASE + file

    /** Remembers where the index is cached (call once at app start; cheap and safe to call again). No I/O here. */
    fun init(ctx: Context) { appCtx = ctx.applicationContext }

    /**
     * One blocking GET with the same contract as [Http.get] (body, null = not found, IOException = trouble), mirror first: a URL the
     * mirror holds is read from its pack; on any mirror problem, or a URL it doesn't hold, the original [url] is asked.
     * Background threads only (the first call may download the index).
     */
    fun get(url: String, timeoutMs: Int = 20_000, progress: ((Long, Long) -> Unit)? = null): ByteArray? {
        val key = keyFor(url) ?: return host.get(url, timeoutMs, progress)
        if (clock() < packDownUntil) return host.get(url, timeoutMs, progress)
        val ix = loadIndex() ?: return host.get(url, timeoutMs, progress)
        val e = ix.files[key] ?: return host.get(url, timeoutMs, progress)
        val bytes = try {
            readEntry(ix, e, timeoutMs, progress)
        } catch (ex: IOException) {
            packFailed()
            fallback("${ex.javaClass.simpleName}: ${ex.message} for $key"); null
        }
        if (bytes != null) { packFails.set(0); return bytes }
        return host.get(url, timeoutMs, progress)
    }

    /** The mirror key of a third-party URL, or null when the mirror doesn't hold that kind of URL. Pure. */
    internal fun keyFor(url: String): String? {
        val raw = "https://raw.githubusercontent.com/"
        val jsd = "https://cdn.jsdelivr.net/gh/"
        val showdown = "https://play.pokemonshowdown.com/"
        return when {
            url.startsWith(raw) -> {
                val p = url.substring(raw.length).split('/', limit = 4)
                if (p.size == 4 && p.all { it.isNotEmpty() }) "gh/${p[0]}/${p[1]}@${p[2]}/${p[3]}" else null
            }
            url.startsWith(jsd) -> {
                val p = url.substring(jsd.length).split('/', limit = 3)
                val at = p.getOrNull(1)?.indexOf('@') ?: -1
                if (p.size == 3 && at > 0 && at < p[1].length - 1 && p[0].isNotEmpty() && p[2].isNotEmpty()) "gh/${p[0]}/${p[1]}/${p[2]}" else null
            }
            url.startsWith(showdown) -> url.substring(showdown.length).takeIf { it.isNotEmpty() }?.let { "showdown/$it" }
            else -> null
        }
    }

    /** Forgets the index, the failure note and the "logged once" flag, and puts the real network back (tests only). */
    internal fun resetForTest() {
        host = HttpHost; clock = System::currentTimeMillis; cacheDir = null; appCtx = null
        logSink = { msg -> try { Report.log(LOG, msg) } catch (_: Throwable) { } }
        synchronized(loadLock) { index = null; failedAt = null }
        fallbackLogged.set(false); packFails.set(0); packDownUntil = 0L
    }

    private fun fallback(why: String) { if (fallbackLogged.compareAndSet(false, true)) logSink("fallback to the original: $why") }

    /** Counts one transport failure of a pack read; the third in a row switches the mirror off for [PACK_COOLDOWN_MS]. */
    private fun packFailed() {
        if (packFails.incrementAndGet() >= PACK_FAILS_TO_TRIP) {
            packFails.set(0)
            val first = clock() >= packDownUntil
            packDownUntil = clock() + PACK_COOLDOWN_MS
            if (first) logSink("pack reads failed $PACK_FAILS_TO_TRIP times in a row, originals for 10 min")
        }
    }

    /** One Range read of the entry's bytes (progress streams while reading); null when missing or the length or crc32 is wrong. */
    private fun readEntry(ix: Index, e: Entry, timeoutMs: Int, progress: ((Long, Long) -> Unit)?): ByteArray? {
        val b = host.range(BASE + ix.packs[e.pack], e.offset, e.length, timeoutMs, progress)
        if (b == null) { packFailed(); fallback("pack ${ix.packs[e.pack]} not found"); return null }
        if (b.size != e.length) { fallback("short read ${b.size} of ${e.length} bytes"); return null }
        val c = CRC32(); c.update(b)
        if (c.value != e.crc) { fallback("crc32 mismatch"); return null }
        return b
    }

    /** The index, loaded on the first call (one load at a time, others wait); null while a failed load is remembered. */
    private fun loadIndex(): Index? {
        index?.let { return it }
        synchronized(loadLock) {
            index?.let { return it }
            failedAt?.let { if (clock() - it < RETRY_MS) return null }
            val loaded = try { readCache() ?: download() } catch (e: IOException) {
                failedAt = clock()
                logSink("index unreachable: ${e.javaClass.simpleName}: ${e.message}, originals for 10 min")
                return null
            }
            failedAt = null; index = loaded
            logSink("index $TAG: ${loaded.files.size} files")
            return loaded
        }
    }

    private fun cacheFile(): File? = (cacheDir ?: appCtx?.let { File(it.noBackupFilesDir, "mirror") })?.let { File(it, INDEX_FILE) }

    private fun readCache(): Index? {
        val f = cacheFile() ?: return null
        if (!f.isFile) return null
        return try { parse(f.readBytes()) } catch (_: Exception) { f.delete(); null }
    }

    private fun download(): Index {
        val body = host.get(BASE + "index.json.gz", INDEX_TIMEOUT_MS, null) ?: throw IOException("index.json.gz not found")
        val json = if (body.size > 2 && body[0] == 0x1f.toByte() && body[1] == 0x8b.toByte()) gunzip(body) else body
        val ix = try { parse(json) } catch (e: Exception) { throw IOException("bad index: ${e.message}") }
        cacheFile()?.let { f ->
            try {
                f.parentFile?.mkdirs()
                val tmp = File(f.parentFile, f.name + ".tmp")
                tmp.writeBytes(json)
                if (!tmp.renameTo(f)) tmp.delete()
            } catch (_: Exception) { /* no cache: the next start downloads it again */ }
        }
        return ix
    }

    private fun gunzip(b: ByteArray): ByteArray = try {
        GZIPInputStream(ByteArrayInputStream(b)).use { inp ->
            val out = ByteArrayOutputStream(b.size * 6)
            val buf = ByteArray(32 * 1024)
            while (true) {
                val n = inp.read(buf); if (n < 0) break
                out.write(buf, 0, n)
                if (out.size() > MAX_INDEX_BYTES) throw IOException("index too big")
            }
            out.toByteArray()
        }
    } catch (e: IOException) { throw e }

    /** Plain JSON in, [Index] out; throws when the format or tag isn't ours. */
    private fun parse(json: ByteArray): Index {
        val o = JSONObject(String(json, Charsets.UTF_8))
        require(o.optInt("format", -1) == 1) { "format ${o.opt("format")}" }
        require(o.optString("tag") == TAG) { "tag ${o.opt("tag")}" }
        val packsJson = o.getJSONArray("packs")
        val packs = List(packsJson.length()) { packsJson.getJSONObject(it).getString("name") }
        val filesJson = o.getJSONArray("files")
        val files = HashMap<String, Entry>(filesJson.length() * 2)
        for (i in 0 until filesJson.length()) {
            val a = filesJson.getJSONArray(i)
            val pack = a.getInt(1); val off = a.getLong(2); val len = a.getInt(3)
            if (pack !in packs.indices || off < 0 || len <= 0) continue
            files[a.getString(0)] = Entry(pack, off, len, a.getLong(4) and 0xFFFFFFFFL)
        }
        return Index(packs, files)
    }
}
