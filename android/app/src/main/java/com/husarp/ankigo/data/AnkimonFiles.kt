package com.husarp.ankigo.data

import android.content.Context
import com.husarp.ankigo.Report
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream

// TICKET T53 owns this file: the kept copy of the last imported Ankimon save (what the export is built on) and its helpers.

/**
 * `filesDir/ankimon/last-import.db` is the Ankimon file of the last import, kept so the export can write a new Ankimon
 * save on top of it. Only [keep] ever writes it (atomically: tmp file, sync, rename); the export only reads it.
 * `replaced-<millis>.db` next to it are the newest 3 files an export overwrote at its destination.
 */
object AnkimonFiles {
    private const val TAG = "ANKIMON"
    private const val KEPT = "last-import.db"
    private const val REPLACED_KEEP = 3

    fun dir(ctx: Context): File = File(ctx.filesDir, "ankimon")

    /** The kept Ankimon file, or null when there is none (or it is empty). */
    fun kept(ctx: Context): File? = keptIn(dir(ctx))

    /** When the kept file was written (its modification time), or null. */
    fun keptAt(ctx: Context): Long? = kept(ctx)?.lastModified()

    /**
     * Keeps a copy of [copy] as the Ankimon file to export onto. False (and logged) when it couldn't be written; the
     * previous kept file is then exactly as it was. [copy] itself is left alone.
     */
    @Synchronized
    fun keep(ctx: Context, copy: File): Boolean = try {
        keepInto(dir(ctx), copy); true
    } catch (e: Exception) {
        Report.fail(TAG, "keeping the imported Ankimon file", e); false
    }

    /** Saves what an export is about to overwrite as `replaced-<now>.db` (null when [old] is empty) and prunes to the newest 3. */
    fun saveReplaced(ctx: Context, old: InputStream, now: Long): File? = saveReplacedIn(dir(ctx), old, now)

    /** `ankimon-save-<trainer>-<day>.db`; the trainer reduced to letters, digits, - and _ like Ankimon's own export name. */
    fun exportName(trainer: String, day: String): String {
        val safe = trainer.filter { it.isLetterOrDigit() || it == '-' || it == '_' }.ifEmpty { "trainer" }
        return "ankimon-save-$safe-$day.db"
    }

    // ---- file-level parts (take the folder, so the tests need no Context) ----

    internal fun keptIn(dir: File): File? = File(dir, KEPT).takeIf { it.isFile && it.length() > 0 }

    internal fun keepInto(dir: File, copy: File) {
        if (!copy.isFile || copy.length() == 0L) throw IOException("nothing to keep")
        dir.mkdirs()
        val tmp = File(dir, "$KEPT.tmp")
        try {
            FileInputStream(copy).use { i -> writeSynced(tmp, i) }
            if (!tmp.renameTo(File(dir, KEPT))) throw IOException("rename failed")
        } finally {
            tmp.delete() // gone after a rename; removes the leftover after a failure
        }
    }

    internal fun saveReplacedIn(dir: File, old: InputStream, now: Long): File? {
        dir.mkdirs()
        val tmp = File(dir, "replaced-$now.db.tmp")
        try {
            val n = writeSynced(tmp, old)
            if (n == 0L) return null
            val out = File(dir, "replaced-$now.db")
            if (!tmp.renameTo(out)) throw IOException("rename failed")
            pruneReplaced(dir)
            return out
        } finally {
            tmp.delete()
        }
    }

    internal fun pruneReplaced(dir: File, keep: Int = REPLACED_KEEP) {
        val all = dir.listFiles { f -> f.name.startsWith("replaced-") && f.name.endsWith(".db") } ?: return
        all.sortedByDescending { it.name.removePrefix("replaced-").removeSuffix(".db").toLongOrNull() ?: 0L }
            .drop(keep).forEach { it.delete() }
    }

    /** Copies [input] to [file], flushes and syncs it to disk; returns the byte count. */
    private fun writeSynced(file: File, input: InputStream): Long {
        var size = 0L
        FileOutputStream(file).use { o ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                o.write(buf, 0, n)
                size += n
            }
            o.flush()
            o.fd.sync()
        }
        return size
    }
}
