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

// PLAN item 60: Pokédex text + category per species, fetched on demand the first time its Info is opened (PokeAPI
// pokemon-species/{baseNum}) and kept in filesDir/dextext/{baseNum}.json (a few hundred bytes each). Offline or any error: null,
// nothing is cached, so the next open tries again. Parsing is game/data/DexTextParser.kt. No bulk download.

object DexText {
    private const val TAG = "DEXTEXT"
    private const val URL = "https://pokeapi.co/api/v2/pokemon-species/"
    private const val TIMEOUT_MS = 8_000

    private val main by lazy { Handler(Looper.getMainLooper()) }
    private val pool by lazy { Executors.newSingleThreadExecutor { r -> Thread(r, "dextext").apply { isDaemon = true } } }

    private fun file(ctx: Context, baseNum: Int) = File(File(ctx.filesDir, "dextext"), "$baseNum.json")

    /** Blocking (background thread): the cached entry of species [baseNum], or null when it was never fetched. */
    fun cached(ctx: Context, baseNum: Int): DexEntry? {
        val f = file(ctx, baseNum)
        if (!f.isFile) return null
        return try { DexTextParser.decode(f.readText()) } catch (e: IOException) { null }
    }

    /** Blocking (background thread): the cached entry, else one request (8 s timeouts); null when offline or the answer is unusable. */
    fun fetch(ctx: Context, baseNum: Int): DexEntry? {
        if (baseNum <= 0) return null
        Report.init(ctx)
        cached(ctx, baseNum)?.let { return it }
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
