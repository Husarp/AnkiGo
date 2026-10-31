package com.husarp.ankigo.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.husarp.ankigo.Report
import com.husarp.ankigo.game.data.DexEntry
import com.husarp.ankigo.game.data.DexTextParser
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors
import java.util.zip.GZIPInputStream
import org.json.JSONObject

// English text ships in assets/dextext.json.gz. Missing bundled entries use the existing
// filesDir/dextext cache, then PokeAPI as a last resort.

object DexText {
    private const val TAG = "DEXTEXT"
    private const val URL = "https://pokeapi.co/api/v2/pokemon-species/"
    private const val TIMEOUT_MS = 8_000

    private val main by lazy { Handler(Looper.getMainLooper()) }
    private val pool by lazy { Executors.newSingleThreadExecutor { r -> Thread(r, "dextext").apply { isDaemon = true } } }

    private fun file(ctx: Context, baseNum: Int) = File(File(ctx.filesDir, "dextext"), "$baseNum.json")

    @Volatile private var bundle: JSONObject? = null

    private fun bundled(ctx: Context, baseNum: Int): DexEntry? {
        val entries = bundle ?: synchronized(this) {
            bundle ?: try {
                ctx.assets.open("dextext.json.gz").use { input ->
                    GZIPInputStream(input).bufferedReader(Charsets.UTF_8).use { JSONObject(it.readText()) }
                }.also { bundle = it }
            } catch (_: IOException) { return null }
              catch (_: org.json.JSONException) { return null }
        }
        return bundledEntry(entries, baseNum)
    }

    internal fun bundledEntry(entries: JSONObject, baseNum: Int): DexEntry? =
        entries.optJSONObject(baseNum.toString())?.let { DexTextParser.decode(it.toString()) }?.takeUnless { it.empty }

    /** Shared lookup order; fallbacks are lazy so bundled entries never need disk cache or network. */
    internal fun resolve(bundled: () -> DexEntry?, cached: () -> DexEntry?, online: () -> DexEntry?): DexEntry? =
        bundled() ?: cached() ?: online()

    /** Blocking (background thread): the cached entry of species [baseNum], or null when it was never fetched. */
    fun cached(ctx: Context, baseNum: Int): DexEntry? {
        val f = file(ctx, baseNum)
        if (!f.isFile) return null
        return try { DexTextParser.decode(f.readText()) } catch (e: IOException) { null }
    }

    /** Blocking (background thread): bundled, cached, then one request (8 s timeouts). */
    fun fetch(ctx: Context, baseNum: Int): DexEntry? {
        if (baseNum <= 0) return null
        return resolve({ bundled(ctx, baseNum) }, { cached(ctx, baseNum) }, { download(ctx, baseNum) })
    }

    private fun download(ctx: Context, baseNum: Int): DexEntry? {
        Report.init(ctx)
        val entry = try {
            val bytes = Http.get("$URL$baseNum/", TIMEOUT_MS) ?: return null
            DexTextParser.parse(String(bytes, Charsets.UTF_8))
        } catch (e: IOException) {
            Report.log(TAG, "species $baseNum: ${e.message}")
            return null
        } catch (e: RuntimeException) {
            Report.fail(TAG, "species $baseNum", e)
            return null
        }
        if (entry == null || entry.empty) return null
        try {
            val f = file(ctx, baseNum)
            f.parentFile?.mkdirs()
            val tmp = File(f.parentFile, "${f.name}.tmp")
            tmp.writeText(DexTextParser.encode(entry))
            if (!tmp.renameTo(f)) tmp.delete()
        } catch (e: IOException) {
            Report.fail(TAG, "saving species $baseNum", e) // still shown this time
        }
        return entry
    }

    /** Background thread: [onDone] runs on the main thread with the entry, or null ("not downloaded"). */
    fun load(ctx: Context, baseNum: Int, onDone: (DexEntry?) -> Unit) {
        val app = ctx.applicationContext
        pool.execute {
            val e = try { fetch(app, baseNum) } catch (t: Throwable) { Report.fail(TAG, "load $baseNum", t); null }
            main.post { onDone(e) }
        }
    }
}
