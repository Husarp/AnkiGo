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
    /** A sound waiting for its file to load: its volume, the uptime it is too late after, and the check to pass right before it starts. */
    private class Waiting(val volume: Float, val deadline: Long, val allowed: () -> Boolean)
    private val waiting = ConcurrentHashMap<Int, Waiting>()          // SoundPool id -> sound waiting for the load
    private val cries = LinkedHashMap<Int, Int>()                    // species -> SoundPool id, oldest first
    private val lastAt = ConcurrentHashMap<Any, Long>()
    private val fetching = ConcurrentHashMap.newKeySet<Int>()
    @Volatile private var warming = false

    /** Set by the study screen while it is resumed: true when the card on screen (or the one just answered) has audio and the player wants quiet. */
    @Volatile private var studyQuiet: (() -> Boolean)? = null
    @Volatile private var studyOwner: Any? = null

    fun setStudyQuiet(owner: Any, quiet: () -> Boolean) { studyOwner = owner; studyQuiet = quiet }
    /** Clears the provider, but only when [owner] still holds it (a newer screen keeps its own). */
    fun clearStudyQuiet(owner: Any) { if (studyOwner === owner) { studyQuiet = null; studyOwner = null } }
    private fun quietNow(muted: Boolean): Boolean = muted || (try { studyQuiet?.invoke() } catch (_: Throwable) { false }) == true

    private fun app(ctx: Context) = ctx.applicationContext ?: ctx

    private fun cryStore(ctx: Context) = CryStore(File(app(ctx).noBackupFilesDir, "cries"))

    /** True when any sound that is not a cry is switched on (a button sound too). */
    private fun anyEffectGroupOn(c: Context) = SoundGroup.entries.any { it != SoundGroup.CRIES && SoundPrefs.groupOn(c, it) }

    /** Makes the sound files and loads them, in the background; does nothing when every group is off or it is already done. */
    fun warm(ctx: Context) {
        if (warming || (pool != null && effects.size == SoundId.entries.size)) return
        val c = app(ctx)
        val sfx = anyEffectGroupOn(c)
        if (!sfx && !SoundPrefs.cries(c)) return
        if (!sfx && pool != null) return
        warming = true
        io.execute {
            // Cries need the pool even when all the effect sounds are off.
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
        val p = SoundPool.Builder().setMaxStreams(6).setAudioAttributes(attrs).build()
        p.setOnLoadCompleteListener { sp, id, status ->
            if (status != 0) return@setOnLoadCompleteListener
            ready.add(id)
            val want = waiting.remove(id)
            if (want != null && SystemClock.uptimeMillis() <= want.deadline && want.allowed()) sp.play(id, want.volume, want.volume, 1, 0, 1f)
        }
        pool = p
        return p
    }

    // ---------------------------------------------------------------- events

    /**
     * The one hook of the event player: makes the sound of [e], if it has one and the rules allow it.
     * [muted] = the study screen says the card has audio. [pace] = the event player's current pace factor (0.5 = the visuals run
     * twice as fast): every cue delay is scaled by it once. [delayMs] (real ms) replaces every cue delay: an animated attack sounds when its hit lands. Never throws.
     */
    fun onEvent(ctx: Context, e: GameEvent, muted: Boolean, ballShown: Boolean, pace: Double = 1.0, delayMs: Long? = null) {
        try {
            val c = app(ctx)
            val shiny = e is GameEvent.MainChanged && GameRepo.state?.mon(e.uid)?.shiny == true
            val cues = SoundRules.cuesFor(e, ballShown, shiny)
            if (e is GameEvent.Encounter) prefetchCries(c, e.species)
            if (cues.isEmpty()) return
            val sc = context(c, quietNow(muted))
            val heard = cues.filter { SoundRules.shouldPlay(it, sc) }
            if (heard.isEmpty()) return
            warm(c)
            for (cue in heard) play(c, cue, delayMs ?: SoundRules.scaledDelay(cue.delayMs, pace))
        } catch (t: Throwable) {
            Report.fail(TAG, "sound for ${e.javaClass.simpleName}", t)
        }
    }

    /**
     * A button click ([SoundId.AGAIN], [SoundId.TAB], ...): plays when its group is on, its channel volume is above 0, the phone's
     * ringer is normal and [muted] is false. The first click after a start may stay silent while the files load. Never throws, never blocks.
     */
    fun ui(ctx: Context, id: SoundId, muted: Boolean = false) {
        try {
            val c = app(ctx)
            val cue = Cue.Effect(id)
            if (!SoundRules.shouldPlay(cue, context(c, quietNow(muted)))) return
            warm(c)
            playNow(c, cue)
        } catch (t: Throwable) {
            Report.fail(TAG, "click sound $id", t)
        }
    }

    private fun context(c: Context, muted: Boolean) = SoundContext(
        groupsOff = SoundGroup.entries.filterTo(HashSet()) { !SoundPrefs.groupOn(c, it) },
        volumes = SoundChannel.entries.associateWith { SoundPrefs.volume(c, it) },
        ringerNormal = ringerNormal(c), muted = muted,
    )

    private fun ringerNormal(c: Context): Boolean =
        (c.getSystemService(Context.AUDIO_SERVICE) as? AudioManager)?.ringerMode?.let { it == AudioManager.RINGER_MODE_NORMAL } ?: true

    /** True when [cue] may start right now: its group is on, its volume above 0, the ringer normal and the card quiet rule not active. */
    private fun allowedNow(c: Context, cue: Cue) = try {
        SoundRules.shouldPlay(cue, context(c, quietNow(false)))
    } catch (t: Throwable) { Report.fail(TAG, "checking $cue", t); false }

    private fun play(c: Context, cue: Cue, delayMs: Long) {
        if (delayMs > 0) main.postDelayed({ if (allowedNow(c, cue)) playNow(c, cue) }, delayMs) else playNow(c, cue)
    }

    private fun playNow(c: Context, cue: Cue) {
        try {
            val p = pool ?: return
            val now = SystemClock.elapsedRealtime()
            val key: Any = when (cue) { is Cue.Effect -> cue.id; is Cue.Cry -> "cry${cue.species}" }
            if (!SoundRules.gapOk(lastAt[key], now)) return
            lastAt[key] = now
            val channel = SoundRules.groupOf(cue).channel
            val v = SoundRules.volume(SoundPrefs.volume(c, channel))
            when (cue) {
                is Cue.Effect -> effects[cue.id]?.takeIf { it in ready }?.let { p.play(it, v, v, 1, 0, 1f) }
                is Cue.Cry -> playCry(c, p, cue.species, v, cue) // v = the channel volume; the cry's gain and the entrance factor come on top
            }
        } catch (t: Throwable) {
            Report.fail(TAG, "playing $cue", t)
        }
    }

    // ---------------------------------------------------------------- cries

    private fun playCry(c: Context, p: SoundPool, species: Int, channelVolume: Float, cue: Cue) {
        val file = cryStore(c).cached(species) ?: return
        // Each cry is levelled to the same loudness (CryLevel); one that was not measured yet plays at the safe default and is measured now.
        val gain = CryDecoder.stored(c, species) ?: CryLevel.DEFAULT_GAIN.also { measure(c, species) }
        val volume = CryLevel.volume(channelVolume, gain)
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
        else waiting[id] = Waiting(volume, SystemClock.uptimeMillis() + CRY_WAIT_MS) { allowedNow(c, cue) }
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
                store.fetch(species) { url -> Http.get(url) }?.let { f -> if (CryDecoder.stored(c, species) == null) CryDecoder.measureAndStore(c, species, f) }
            } catch (t: Throwable) {
                Report.fail(TAG, "cry $species", t)
            } finally {
                fetching.remove(species)
            }
        }
    }

    private val measuring = ConcurrentHashMap.newKeySet<Int>()
    private val unmeasurable = ConcurrentHashMap.newKeySet<Int>()   // cries that failed to decode: not tried again until the app restarts

    /** Measures an already cached cry in the background (once at a time per species), so its next play is levelled. */
    private fun measure(c: Context, species: Int) {
        if (species in unmeasurable || !measuring.add(species)) return
        io.execute {
            try {
                if (cryStore(c).cached(species)?.let { CryDecoder.measureAndStore(c, species, it) } == null) unmeasurable.add(species)
            } catch (t: Throwable) {
                Report.fail(TAG, "measuring cry $species", t)
            } finally {
                measuring.remove(species)
            }
        }
    }

    // ---------------------------------------------------------------- Settings

    /** The "Play a test sound" row: plays the level-up sound at the battle volume. Returns why nothing could be heard, else null. */
    fun test(ctx: Context): String? = preview(ctx, SoundId.LEVEL_UP)

    /**
     * The ▶ on a settings row: plays [id] at the volume of its channel, also when its group is switched off. Returns why nothing
     * can be heard (volume 0, phone silent), else null.
     */
    fun preview(ctx: Context, id: SoundId): String? {
        val c = app(ctx)
        val channel = id.group.channel
        if (SoundPrefs.volume(c, channel) <= 0) return "Volume is 0"
        if (!ringerNormal(c)) return "Your phone is on silent or vibrate"
        io.execute {
            try {
                start(c) // makes and loads the files if that has not happened yet (also when the group is off)
                main.postDelayed({ previewNow(c, id) }, 150)
            } catch (t: Throwable) {
                Report.fail(TAG, "preview of $id", t)
            }
        }
        return null
    }

    private fun previewNow(c: Context, id: SoundId) {
        try {
            val p = pool ?: return
            val sp = effects[id] ?: return
            val v = SoundRules.volume(SoundPrefs.volume(c, id.group.channel))
            if (sp in ready) p.play(sp, v, v, 1, 0, 1f)
            else waiting[sp] = Waiting(v, SystemClock.uptimeMillis() + CRY_WAIT_MS) { ringerNormal(c) && SoundPrefs.volume(c, id.group.channel) > 0 } // played by the load listener; a preview ignores the group switch
        } catch (t: Throwable) {
            Report.fail(TAG, "preview of $id", t)
        }
    }
}
