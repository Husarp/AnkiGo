package com.husarp.ankigo.data

import android.content.Context
import com.husarp.ankigo.Report
import com.husarp.ankigo.game.GameData
import com.husarp.ankigo.game.data.Parsers
import java.io.File
import java.io.IOException

// TICKET T02 owns this file. Public signatures are fixed; add private helpers freely.

/**
 * Downloads the game data once (about 4.3 MB) into noBackupFilesDir/data, builds GameData with Parsers and keeps a
 * compact cache (gamedata.json) that loads at every start. Nothing Pokémon is in the APK or the repo.
 * Every URL is here, PokeAPI pinned to a commit.
 */
object DataStore {
    const val POKEAPI_COMMIT = "81d20919845b6a445969b9265aab5cec6b04cece"
    const val SPRITES_COMMIT = "0dc105d5b12416932f29b594078282ab4be045f5"
    const val USER_AGENT = "AnkiGo (personal Android app; github.com/Husarp/AnkiGo)"

    private const val SHOWDOWN = "https://play.pokemonshowdown.com/data/"
    private const val POKEAPI = "https://raw.githubusercontent.com/PokeAPI/pokeapi/$POKEAPI_COMMIT/data/v2/csv/"

    /** file name -> URL. */
    val SOURCES: Map<String, String> = linkedMapOf(
        "pokedex.json" to SHOWDOWN + "pokedex.json",
        "moves.json" to SHOWDOWN + "moves.json",
        "learnsets.json" to SHOWDOWN + "learnsets.json",
        "pokemon_species.csv" to POKEAPI + "pokemon_species.csv",
        "pokemon.csv" to POKEAPI + "pokemon.csv",
        "pokemon_stats.csv" to POKEAPI + "pokemon_stats.csv",
        "pokemon_evolution.csv" to POKEAPI + "pokemon_evolution.csv",
        "experience.csv" to POKEAPI + "experience.csv",
        "items.csv" to POKEAPI + "items.csv",
    )

    /** Rough size of each file (progress estimate) and the smallest size we accept (a cut-off download is an error). */
    private val EXPECTED = mapOf(
        "pokedex.json" to 524_000L, "moves.json" to 261_000L, "learnsets.json" to 3_192_000L, "pokemon_species.csv" to 57_000L,
        "pokemon.csv" to 47_000L, "pokemon_stats.csv" to 94_000L, "pokemon_evolution.csv" to 38_000L, "experience.csv" to 7_000L,
        "items.csv" to 60_000L,
    )
    private val MIN_SIZE = mapOf("pokedex.json" to 300_000L, "moves.json" to 150_000L, "learnsets.json" to 2_000_000L)
    private const val MIN_CSV_SIZE = 5_000L
    private const val TRIES = 3

    private const val TAG = "DATA"
    private const val CACHE_FILE = "gamedata.json"

    @Volatile private var memory: GameData? = null

    fun dir(ctx: Context): File = File(ctx.noBackupFilesDir, "data")

    private fun cacheFile(ctx: Context) = File(dir(ctx), CACHE_FILE)
    private fun rawDir(ctx: Context) = File(dir(ctx), "raw")

    /** True when the built cache exists. */
    fun hasData(ctx: Context): Boolean = memory != null || cacheFile(ctx).isFile

    /**
     * Blocking (background thread): downloads every source (retries, size sanity check, temp file then rename),
     * builds GameData, writes the cache, keeps it in memory. progress(doneBytes, totalBytesEstimate, label).
     */
    @Synchronized
    fun download(ctx: Context, progress: (Long, Long, String) -> Unit): Result<GameData> {
        Report.init(ctx)
        val raw = rawDir(ctx)
        raw.mkdirs()
        val total = EXPECTED.values.sum()
        var done = 0L
        for ((name, url) in SOURCES) {
            progress(done, total, name)
            val bytes = fetch(name, url) { partial, _ -> progress(done + partial, maxOf(total, done + partial), name) }
                ?: return Result.failure(IOException("Couldn't download $name \u2014 check the internet connection"))
            val tmp = File(raw, "$name.tmp")
            try {
                tmp.writeBytes(bytes)
                if (!tmp.renameTo(File(raw, name))) throw IOException("rename failed")
            } catch (e: IOException) {
                tmp.delete()
                Report.fail(TAG, "saving $name", e)
                return Result.failure(IOException("Couldn't save $name \u2014 is the phone storage full?"))
            }
            done += bytes.size
            progress(done, maxOf(total, done), name)
        }
        progress(done, maxOf(total, done), "Building")
        val data = try {
            buildFromRaw(ctx)
        } catch (e: IllegalStateException) {
            Report.fail(TAG, "build", e)
            return Result.failure(IOException("The downloaded game data has an unexpected format (${e.message}). Try again later."))
        } catch (e: IOException) {
            Report.fail(TAG, "build", e)
            return Result.failure(IOException("Couldn't read the downloaded game data (${e.message})"))
        }
        try {
            writeCache(ctx, data)
        } catch (e: IOException) {
            Report.fail(TAG, "cache", e)
            return Result.failure(IOException("Couldn't save the game data \u2014 is the phone storage full?"))
        }
        memory = data
        Report.ok(TAG, "downloaded: ${data.species.size} species, ${data.moves.size} moves")
        return Result.success(data)
    }

    /** Blocking: the in-memory GameData, else the cache file, else null. */
    @Synchronized
    fun load(ctx: Context): GameData? {
        memory?.let { return it }
        Report.init(ctx)
        val f = cacheFile(ctx)
        if (f.isFile) {
            try {
                return Parsers.decodeCache(f.readText()).also { memory = it }
            } catch (e: Exception) {
                Report.fail(TAG, "cache unusable, rebuilding from the downloaded files", e)
            }
        }
        // Cache missing, damaged or from an older format: rebuild from the raw files when they are all still there.
        if (SOURCES.keys.all { File(rawDir(ctx), it).isFile }) {
            try {
                val data = buildFromRaw(ctx)
                writeCache(ctx, data)
                memory = data
                return data
            } catch (e: Exception) {
                Report.fail(TAG, "rebuild", e)
            }
        }
        return null
    }

    /**
     * Blocking (background thread): PokeAPI form id -> base species number, for the forms numbered above 10000
     * (from the downloaded pokemon.csv: id, identifier, species_id, ...). Empty when the file is missing or unreadable.
     */
    fun formBaseMap(ctx: Context): Map<Int, Int> {
        val f = File(rawDir(ctx), "pokemon.csv")
        if (!f.isFile) return emptyMap()
        return try {
            val out = HashMap<Int, Int>()
            f.useLines { lines ->
                for (line in lines.drop(1)) { // header row
                    val cols = line.split(',')
                    val id = cols.getOrNull(0)?.toIntOrNull() ?: continue
                    val base = cols.getOrNull(2)?.toIntOrNull() ?: continue
                    if (id > 10000) out[id] = base
                }
            }
            out
        } catch (e: IOException) {
            Report.fail(TAG, "reading pokemon.csv for form names", e)
            emptyMap()
        }
    }

    /** Blocking (background thread): the text of a downloaded raw file (e.g. "items.csv"), or null when missing or unreadable. */
    fun rawText(ctx: Context, name: String): String? {
        if (name.contains('/') || name.contains('\\')) return null
        val f = File(rawDir(ctx), name)
        if (!f.isFile) return null
        return try { f.readText() } catch (e: IOException) { Report.fail(TAG, "reading $name", e); null }
    }

    /** Deletes the downloaded data (Settings -> re-download). */
    @Synchronized
    fun clear(ctx: Context) {
        memory = null
        dir(ctx).deleteRecursively()
    }

    /** Bytes used by the data folder. */
    fun size(ctx: Context): Long = dir(ctx).walkTopDown().filter { it.isFile }.sumOf { it.length() }

    /** One file with up to 3 tries; null when every try failed or the file looked cut off. */
    private fun fetch(name: String, url: String, progress: (Long, Long) -> Unit): ByteArray? {
        val min = MIN_SIZE[name] ?: MIN_CSV_SIZE
        repeat(TRIES) { attempt ->
            try {
                val b = Http.get(url, 30_000, progress)
                if (b == null) Report.log(TAG, "$name: not found (try ${attempt + 1})")
                else if (b.size < min) Report.log(TAG, "$name: only ${b.size} bytes, expected more than $min (try ${attempt + 1})")
                else return b
            } catch (e: IOException) {
                Report.fail(TAG, "$name try ${attempt + 1}", e)
            }
            if (attempt < TRIES - 1) try { Thread.sleep(1000L * (attempt + 1)) } catch (_: InterruptedException) { return null }
        }
        return null
    }

    private fun buildFromRaw(ctx: Context): GameData {
        fun text(name: String) = File(rawDir(ctx), name).readText()
        return Parsers.build(Parsers.Sources(
            pokedexJson = text("pokedex.json"), movesJson = text("moves.json"), learnsetsJson = text("learnsets.json"),
            speciesCsv = text("pokemon_species.csv"), pokemonCsv = text("pokemon.csv"), statsCsv = text("pokemon_stats.csv"),
            evolutionCsv = text("pokemon_evolution.csv"), experienceCsv = text("experience.csv"), itemsCsv = text("items.csv"),
        ))
    }

    private fun writeCache(ctx: Context, data: GameData) {
        val f = cacheFile(ctx)
        f.parentFile?.mkdirs()
        val tmp = File(f.parentFile, "$CACHE_FILE.tmp")
        tmp.writeText(Parsers.encodeCache(data))
        if (!tmp.renameTo(f)) throw IOException("rename failed")
    }
}

/** Small blocking HTTP GET helper shared by DataStore and Sprites (User-Agent, timeouts, follows redirects). */
object Http {
    /** Returns the body bytes, or null on 404; throws IOException on other failures. */
    fun get(url: String, timeoutMs: Int = 20_000, progress: ((Long, Long) -> Unit)? = null): ByteArray? {
        var u = url
        repeat(5) {
            val c = java.net.URL(u).openConnection() as java.net.HttpURLConnection
            try {
                c.connectTimeout = timeoutMs; c.readTimeout = timeoutMs
                c.instanceFollowRedirects = true
                c.setRequestProperty("User-Agent", DataStore.USER_AGENT)
                val code = c.responseCode
                when {
                    code == 404 || code == 410 -> return null
                    code in 300..399 -> { u = c.getHeaderField("Location") ?: throw java.io.IOException("HTTP $code without Location for $u") }
                    code !in 200..299 -> throw java.io.IOException("HTTP $code for $u")
                    else -> return c.inputStream.use { inp ->
                        val out = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(32 * 1024)
                        var bytes = 0L
                        while (true) {
                            val n = inp.read(buffer); if (n < 0) break
                            out.write(buffer, 0, n); bytes += n
                            progress?.invoke(bytes, c.contentLengthLong)
                        }
                        out.toByteArray()
                    }
                }
            } finally {
                c.disconnect()
            }
        }
        throw java.io.IOException("too many redirects for $url")
    }
}
