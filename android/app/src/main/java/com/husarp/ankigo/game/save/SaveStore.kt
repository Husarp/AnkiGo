package com.husarp.ankigo.game.save

import com.husarp.ankigo.game.GameState
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.FileSystemException
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

// Pure Kotlin + java.io/java.nio (no android.*): unit-tested on the JVM.

/**
 * Crash-safe save files in [dir] (the app's filesDir/game, included in Android backup):
 * save.json (current), save.bak (previous), save.json.tmp (being written), snapshots/save-YYYY-MM-DD.json (last 7 days),
 * save.corrupt-<millis>.json (unreadable files moved out of the way).
 * Losing a save is the worst bug: never overwrite a good save with a broken or empty one.
 */
class SaveStore(val dir: File) {
    data class Loaded(val state: GameState?, val source: String?, val problems: List<String>)

    private val saveFile get() = File(dir, "save.json")
    private val bakFile get() = File(dir, "save.bak")
    private val tmpFile get() = File(dir, "save.json.tmp")
    private val snapDir get() = File(dir, "snapshots")

    /** True once save.json is known to be readable (checked by load, or written by us), so write may rotate it into save.bak. */
    private var currentChecked = false

    /**
     * Loads the newest valid save: candidates save.json, save.json.tmp, save.bak, then snapshots (newest first);
     * each is decoded, the valid one with the highest seq wins (ties: the earlier candidate). An unreadable save.json
     * or tmp is moved aside as save.corrupt-<time>.json (so the next write can't turn it into save.bak over a good
     * backup) and listed in problems; bak and snapshots are only listed. A save from a newer app is listed but never
     * moved. If the winner isn't save.json it is written back as save.json so the next start is clean.
     * state == null and problems empty = no save yet (new player); state == null with problems = saves exist but none
     * is readable (caller must NOT start a new game silently).
     */
    fun load(): Loaded = synchronized(LOCK) {
        val problems = mutableListOf<String>()
        val candidates = buildList {
            add(Candidate(saveFile, "save.json", true))
            add(Candidate(tmpFile, "save.json.tmp", true))
            add(Candidate(bakFile, "save.bak", false))
            snapshots().forEach { add(Candidate(it, "snapshots/${it.name}", false)) }
        }
        var best: Candidate? = null
        var bestState: GameState? = null
        var bestText = ""
        for (c in candidates) {
            if (!c.file.isFile) continue
            val text = try {
                c.file.readText(Charsets.UTF_8)
            } catch (e: IOException) { // can't read it now: say so, but don't move a maybe-good file
                problems += "${c.name} could not be read (${e.message})"
                continue
            }
            val st = try {
                SaveCodec.decode(text)
            } catch (e: SaveFormatException) {
                if (e.newerSchema) {
                    problems += "${c.name} is a save from a newer AnkiGo"
                } else {
                    problems += "${c.name} is unreadable (${e.message})" + if (c.moveAside) moveAside(c.file) else ""
                }
                continue
            }
            if (bestState == null || st.seq > bestState.seq) {
                best = c; bestState = st; bestText = text
            }
        }
        currentChecked = true // save.json is now either good, absent or moved aside
        if (best == null || bestState == null) return@synchronized Loaded(null, null, problems)
        if (best.file != saveFile) {
            try {
                writeLocked(bestText, null, tmpReady = best.file == tmpFile)
            } catch (e: IOException) {
                problems += "could not restore ${best.name} as save.json (${e.message})"
            }
        }
        Loaded(bestState, best.name, problems)
    }

    /** state.seq++, state.savedAt = now, returns SaveCodec.encode(state). Call on the thread that owns the state (main). */
    fun prepare(state: GameState, now: Long = System.currentTimeMillis()): String {
        state.seq++
        state.savedAt = now
        return SaveCodec.encode(state)
    }

    /**
     * Writes save.json.tmp, fsyncs, renames save.json -> save.bak, renames tmp -> save.json. Once per [day] also writes
     * snapshots/save-<day>.json and keeps the newest 7. Any thread; synchronized; never throws away the old save on failure.
     */
    fun write(json: String, day: String) { synchronized(LOCK) { writeLocked(json, day, tmpReady = false) } }

    /** prepare + write in one go (tests, onStop). */
    fun save(state: GameState, now: Long = System.currentTimeMillis()) = write(prepare(state, now), state.day)

    /** save.bak, then the snapshots, newest first (for "Restore backup"). */
    fun backups(): List<File> = synchronized(LOCK) { listOfNotNull(bakFile.takeIf { it.isFile }) + snapshots() }

    /** Decodes a backup/export without touching the current save; null if invalid. */
    fun read(file: File): GameState? = try {
        SaveCodec.decode(file.readText(Charsets.UTF_8))
    } catch (e: Exception) {
        null
    }

    /**
     * Like [read], for restoring a backup: also lifts seq to at least the highest seq on disk. Without that, the restored
     * game (seq of the old backup) would lose to the newer files in the next [load] and the restore would undo itself.
     */
    fun restore(file: File): GameState? = synchronized(LOCK) {
        val st = read(file) ?: return@synchronized null
        val highest = (listOf(saveFile, tmpFile, bakFile) + snapshots()).filter { it.isFile }
            .mapNotNull { read(it)?.seq }.maxOrNull() ?: 0L
        if (st.seq < highest) st.seq = highest
        st
    }

    // ---- internals (all under LOCK) ----

    private class Candidate(val file: File, val name: String, val moveAside: Boolean)

    private fun writeLocked(json: String, day: String?, tmpReady: Boolean) {
        require(json.isNotBlank()) { "refusing to save an empty document" }
        if (!dir.isDirectory && !dir.mkdirs() && !dir.isDirectory) throw IOException("cannot create $dir")
        if (!tmpReady) {
            val bytes = json.toByteArray(Charsets.UTF_8)
            try {
                FileOutputStream(tmpFile).use { out ->
                    out.write(bytes)
                    out.flush()
                    out.fd.sync()
                }
                if (tmpFile.length() != bytes.size.toLong()) throw IOException("short write to ${tmpFile.name}")
            } catch (e: IOException) {
                tmpFile.delete() // only a half-written copy of what we were about to save
                throw e
            }
        }
        if (saveFile.exists()) {
            if (!currentChecked) checkCurrent() // an unreadable save.json is moved aside here instead of becoming save.bak
            if (saveFile.exists()) moveReplace(saveFile, bakFile)
        }
        currentChecked = false
        try {
            moveReplace(tmpFile, saveFile)
        } catch (e: IOException) {
            // save.json already went to save.bak: put a copy back so the next start finds it where it belongs.
            if (!saveFile.exists() && bakFile.exists()) {
                try { Files.copy(bakFile.toPath(), saveFile.toPath(), REPLACE_EXISTING) } catch (e2: IOException) { /* load() still finds save.bak */ }
            }
            throw e
        }
        currentChecked = true
        if (day != null && SAFE_DAY.matches(day)) {
            try { writeSnapshot(json, day) } catch (e: IOException) { /* best effort: the save itself is already safe */ }
        }
    }

    /** Called before the first rotation of a store that never loaded: an unreadable save.json must not become save.bak. */
    private fun checkCurrent() {
        val ok = try {
            saveFile.length() > 0 && run { SaveCodec.decode(saveFile.readText(Charsets.UTF_8)); true }
        } catch (e: SaveFormatException) {
            e.newerSchema // a newer app's save is valid: keep it as the backup
        } catch (e: IOException) {
            true // can't tell: keep it
        }
        if (!ok) moveAside(saveFile)
        currentChecked = true
    }

    private fun writeSnapshot(json: String, day: String) {
        val snap = File(snapDir, "save-$day.json")
        if (snap.exists()) return
        if (!snapDir.isDirectory && !snapDir.mkdirs() && !snapDir.isDirectory) throw IOException("cannot create $snapDir")
        val tmp = File(snapDir, "save-$day.json.tmp")
        try {
            FileOutputStream(tmp).use { out ->
                out.write(json.toByteArray(Charsets.UTF_8))
                out.flush()
                out.fd.sync()
            }
            moveReplace(tmp, snap)
        } catch (e: IOException) {
            tmp.delete()
            throw e
        }
        snapshots().drop(KEEP_SNAPSHOTS).forEach { it.delete() }
    }

    /** Snapshot files, newest first (the file name carries the day). */
    private fun snapshots(): List<File> =
        snapDir.listFiles { f -> f.isFile && SNAPSHOT.matches(f.name) }.orEmpty().sortedByDescending { it.name }

    /** Renames [f] to save.corrupt-<millis>.json. Returns text for the problem list. */
    private fun moveAside(f: File): String {
        val now = System.currentTimeMillis()
        var target = File(dir, "save.corrupt-$now.json")
        var n = 1
        while (target.exists()) target = File(dir, "save.corrupt-$now-${n++}.json")
        return try {
            Files.move(f.toPath(), target.toPath())
            "; moved to ${target.name}"
        } catch (e: IOException) {
            "; could not be moved aside (${e.message})"
        }
    }

    /** Atomic rename that replaces [to]; falls back to a plain replacing move where atomic isn't supported. */
    private fun moveReplace(from: File, to: File) {
        try {
            Files.move(from.toPath(), to.toPath(), ATOMIC_MOVE)
        } catch (e: FileSystemException) {
            Files.move(from.toPath(), to.toPath(), REPLACE_EXISTING)
        }
    }

    private companion object {
        /** One lock for every store: two instances on one directory (e.g. a restarted service) must not interleave. */
        val LOCK = Any()
        const val KEEP_SNAPSHOTS = 7
        val SNAPSHOT = Regex("save-[0-9A-Za-z_-]{1,40}\\.json")
        val SAFE_DAY = Regex("[0-9A-Za-z_-]{1,40}")
    }
}
