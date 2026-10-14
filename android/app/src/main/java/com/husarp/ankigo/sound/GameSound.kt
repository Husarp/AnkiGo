package com.husarp.ankigo.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.Report
import com.husarp.ankigo.data.Http
import com.husarp.ankigo.data.Sprites
import com.husarp.ankigo.game.GameEvent
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Plays the battle sounds and cries. Everything here is best effort: a failure is logged with [Report.fail] and the
 * battle goes on. Files are made and loaded on a background thread; a sound asked for before it is ready is skipped.
 * The phone decides the loudness: USAGE_GAME (media volume), no audio focus requests, silent when ringer is not normal.
 */
object GameSound {
    private const val TAG = "SOUND"
    private const val MAX_CRIES = 6
    private const val CRY_WAIT_MS = 1500L

    private val main by lazy { Handler(Looper.getMainLooper()) }
    private val io by lazy { Executors.newSingleThreadExecutor { r -> Thread(r, "game-sound").apply { isDaemon = true } } }

    @Volatile private var pool: SoundPool? = null
    private val effects = ConcurrentHashMap<SoundId, Int>()          // sound -> SoundPool id
    private val ready = ConcurrentHashMap.newKeySet<Int>()           // SoundPool ids that finished loading
    private val waiting = ConcurrentHashMap<Int, Pair<Float, Long>>() // cry id -> (volume, play before this uptime)
    private val cries = LinkedHashMap<Int, Int>()                    // species -> SoundPool id, oldest first
    private val lastAt = ConcurrentHashMap<Any, Long>()
    private val fetching = ConcurrentHashMap.newKeySet<Int>()
    @Volatile private var warming = false

    private fun app(ctx: Context) = ctx.applicationContext ?: ctx

    private fun cryStore(ctx: Context) = CryStore(File(app(ctx).noBackupFilesDir, "cries"))

    /** Makes the sound files and loads them, in the background; does nothing when both kinds are switched off or already done. */
    fun warm(ctx: Context) {
        if (warming || (pool != null && effects.size == SoundId.entries.size)) return
        val c = app(ctx)
        val sfx = SoundPrefs.battleSounds(c)
        if (!sfx && !SoundPrefs.cries(c)) return
        if (!sfx && pool != null) return
        warming = true
        io.execute {
            // Cries need the pool even when the battle sounds are off.
            try { if (sfx) start(c) else ensurePool() } catch (t: Throwable) { Report.fail(TAG, "preparing the sounds", t) } finally { warming = false }
        }
    }

    private fun start(c: Context) {
        val p = ensurePool()
        val dir = File(c.cacheDir, "sfx").apply { mkdirs() }
        for (id in SoundId.entries) {
            if (effects.containsKey(id)) continue
            val f = File(dir, id.file)
            if (!f.isFile || f.length() < 100) {
                val tmp = File(dir, "${id.file}.${System.nanoTime()}.tmp")
                tmp.writeBytes(Sfx.wav(id))
                if (!tmp.renameTo(f)) { tmp.delete(); continue }
            }
            effects[id] = p.load(f.path, 1)
        }
    }

    @Synchronized
    private fun ensurePool(): SoundPool {
        pool?.let { return it }
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val p = SoundPool.Builder().setMaxStreams(4).setAudioAttributes(attrs).build()
        p.setOnLoadCompleteListener { sp, id, status ->
            if (status != 0) return@setOnLoadCompleteListener
            ready.add(id)
            val want = waiting.remove(id)
            if (want != null && SystemClock.uptimeMillis() <= want.second) sp.play(id, want.first, want.first, 1, 0, 1f)
        }
        pool = p
        return p
    }

    // ---------------------------------------------------------------- events

    /**
     * The one hook of the event player: makes the sound of [e], if it has one and the rules allow it.
     * [muted] = the study screen says the card has audio. Never throws.
     */
    fun onEvent(ctx: Context, e: GameEvent, muted: Boolean, ballShown: Boolean) {
        try {
            val c = app(ctx)
            val cue = SoundRules.cueFor(e, ballShown)
            if (e is GameEvent.Encounter) prefetchCries(c, e.species)
            if (!SoundRules.shouldPlay(cue, context(c, muted))) return
            warm(c)
            play(c, cue!!)
        } catch (t: Throwable) {
            Report.fail(TAG, "sound for ${e.javaClass.simpleName}", t)
        }
    }

    private fun context(c: Context, muted: Boolean) = SoundContext(
        battleSounds = SoundPrefs.battleSounds(c), cries = SoundPrefs.cries(c), volumePct = SoundPrefs.volume(c),
        ringerNormal = ringerNormal(c), muted = muted,
    )

    private fun ringerNormal(c: Context): Boolean =
        (c.getSystemService(Context.AUDIO_SERVICE) as? AudioManager)?.ringerMode?.let { it == AudioManager.RINGER_MODE_NORMAL } ?: true

    private fun play(c: Context, cue: Cue) {
        if (cue.delayMs > 0) main.postDelayed({ playNow(c, cue) }, cue.delayMs) else playNow(c, cue)
    }

    private fun playNow(c: Context, cue: Cue) {
        try {
            val p = pool ?: return
            val now = SystemClock.elapsedRealtime()
            val key: Any = when (cue) { is Cue.Effect -> cue.id; is Cue.Cry -> "cry${cue.species}" }
            if (!SoundRules.gapOk(lastAt[key], now)) return
            lastAt[key] = now
            val v = SoundRules.volume(SoundPrefs.volume(c)) * (if (cue is Cue.Cry) 0.9f else 1f)
            when (cue) {
                is Cue.Effect -> effects[cue.id]?.takeIf { it in ready }?.let { p.play(it, v, v, 1, 0, 1f) }
                is Cue.Cry -> playCry(c, p, cue.species, v)
            }
        } catch (t: Throwable) {
            Report.fail(TAG, "playing $cue", t)
        }
    }

    // ---------------------------------------------------------------- cries

    private fun playCry(c: Context, p: SoundPool, species: Int, volume: Float) {
        val file = cryStore(c).cached(species) ?: return
        val id: Int
        synchronized(cries) {
            val known = cries.remove(species)
            id = known ?: p.load(file.path, 1)
            cries[species] = id // most recently used last
            while (cries.size > MAX_CRIES) {
                val oldest = cries.entries.first()
                cries.remove(oldest.key)
                ready.remove(oldest.value)
                p.unload(oldest.value)
            }
        }
        if (id in ready) p.play(id, volume, volume, 1, 0, 1f)
        else waiting[id] = volume to SystemClock.uptimeMillis() + CRY_WAIT_MS
    }

    /** Downloads the cry of [species] and of the next encounter in the background (once each; only when downloads are allowed). */
    private fun prefetchCries(c: Context, species: Int) {
        if (!SoundPrefs.cries(c) || !Sprites.enabled(c)) return
        fetch(c, species)
        GameRepo.state?.nextSpecies?.let { fetch(c, it) }
    }

    private fun fetch(c: Context, species: Int) {
        val store = cryStore(c)
        if (store.cached(species) != null || store.knownMissing(species) || !fetching.add(species)) return
        io.execute {
            try {
                store.fetch(species) { url -> Http.get(url) }
            } catch (t: Throwable) {
                Report.fail(TAG, "cry $species", t)
            } finally {
                fetching.remove(species)
            }
        }
    }

    // ---------------------------------------------------------------- Settings

    /** The "Play a test sound" row: plays the level-up sound at the chosen volume. Returns why nothing could be heard, else null. */
    fun test(ctx: Context): String? {
        val c = app(ctx)
        if (SoundPrefs.volume(c) <= 0) return "Volume is 0"
        if (!ringerNormal(c)) return "Your phone is on silent or vibrate"
        io.execute {
            try {
                start(c) // makes and loads the files if that has not happened yet (also when battle sounds are off)
                val p = pool ?: return@execute
                val id = effects[SoundId.LEVEL_UP] ?: return@execute
                val v = SoundRules.volume(SoundPrefs.volume(c))
                fun go() { if (id in ready) p.play(id, v, v, 1, 0, 1f) }
                main.postDelayed({ go() }, 150)
            } catch (t: Throwable) {
                Report.fail(TAG, "test sound", t)
            }
        }
        return null
    }
}
