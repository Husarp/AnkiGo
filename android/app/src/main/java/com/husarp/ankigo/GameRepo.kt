package com.husarp.ankigo

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.husarp.ankigo.data.AnkimonFiles
import com.husarp.ankigo.data.DataStore
import com.husarp.ankigo.game.Achievements
import com.husarp.ankigo.game.Game
import com.husarp.ankigo.game.GameData
import com.husarp.ankigo.game.Looks
import com.husarp.ankigo.game.Wishlist
import com.husarp.ankigo.game.GameEvent
import com.husarp.ankigo.game.GameState
import com.husarp.ankigo.game.PendingFight
import com.husarp.ankigo.game.ankimon.AnkimonMerge
import com.husarp.ankigo.game.save.SaveCodec
import com.husarp.ankigo.game.save.SaveStore
import com.husarp.ankigo.ui.WidgetLogic
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

// TICKET T11 owns this file. Public signatures are fixed (all screens and widgets use them); add private helpers freely.

/**
 * The one place that holds the loaded game for the whole app process (activities + widgets).
 * All game calls happen on the main thread through [act]; saving is coalesced on a background thread
 * (encode on main, write on background). Saves live in filesDir/game (Android backup includes it).
 */
object GameRepo {
    enum class Status { LOADING, NEED_DATA, NEED_STARTER, READY, SAVE_ERROR }

    private const val TAG = "GAME"
    private const val SAVE_DELAY_MS = 300L
    private const val SAVE_WAIT_SECONDS = 10L

    @Volatile var status: Status = Status.LOADING
        private set
    /** Why the save couldn't be read (SAVE_ERROR) — shown to the user with restore options. */
    @Volatile var error: String? = null
        private set

    @Volatile private var loadedData: GameData? = null
    /** The loaded save (also while the game data is still missing); null in SAVE_ERROR. */
    @Volatile private var held: GameState? = null
    @Volatile private var current: Game? = null

    val data: GameData? get() = loadedData
    val state: GameState? get() = held
    val game: Game? get() = current

    /** The moves the wild Pokémon used in this fight (info window, PLAN 93); in memory only. */
    val wildMoves = com.husarp.ankigo.game.WildMoveLog()

    fun saveDir(ctx: Context): File = File(ctx.filesDir, "game")

    private var storeRef: SaveStore? = null

    @Synchronized
    fun store(ctx: Context): SaveStore {
        val dir = saveDir(ctx)
        return storeRef?.takeIf { it.dir == dir } ?: SaveStore(dir).also { storeRef = it }
    }

    private val handler by lazy { Handler(Looper.getMainLooper()) }
    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    // ---- loading ----

    private val lock = Any()
    private var loading = false
    private val waiters = ArrayList<(Status) -> Unit>()
    /** True once SaveStore.load ran in this process. It moves unreadable files aside, so it must never run twice. */
    private var saveRead = false
    private val loader = Executors.newSingleThreadExecutor { r -> Thread(r, "ankigo-load").apply { isDaemon = true } }

    private class Loaded(val data: GameData?, val save: SaveStore.Loaded?, val saveError: String?)

    /**
     * Loads data (DataStore.load) and the save (SaveStore.load) on a background thread, then calls [done] on the main
     * thread. No save at all -> a new GameState (NEED_STARTER). Unreadable saves -> SAVE_ERROR (never a silent new game).
     * Already loaded -> calls back at once. After NEED_DATA, calling again only re-checks the data (FirstRun downloads it).
     * A missing data file wins over a save problem: the data is needed for everything, the save error shows after it.
     */
    fun load(ctx: Context, done: (Status) -> Unit) {
        val app = ctx.applicationContext
        widgetApp = app
        Report.init(app)
        var callNow = false
        synchronized(lock) {
            when {
                loading -> waiters += done // share the load that is already running
                status == Status.READY || status == Status.NEED_STARTER || status == Status.SAVE_ERROR -> callNow = true
                else -> { // LOADING (first call) or NEED_DATA (re-check the data)
                    waiters += done
                    loading = true
                    loader.execute { loadInBackground(app) }
                }
            }
        }
        if (callNow) onMain { done(status) }
    }

    private fun loadInBackground(app: Context) {
        val data = try {
            DataStore.load(app)
        } catch (e: Throwable) {
            Report.fail(TAG, "game data", e)
            null
        }
        var save: SaveStore.Loaded? = null
        var saveError: String? = null
        if (!saveRead) {
            try {
                save = store(app).load()
            } catch (e: Throwable) {
                Report.fail(TAG, "reading the save", e)
                saveError = "The save could not be read (${e.message ?: e.javaClass.simpleName})."
            }
            saveRead = true
        }
        // "Not supported yet" Pokémon whose species this version knows become normal Pokémon (PLAN 67). Before anything shares the state.
        val loaded = save?.state
        if (data != null && loaded != null && loaded.unsupported.isNotEmpty()) {
            try {
                val forms = DataStore.formBaseMap(app)
                AnkimonMerge.reviveUnsupported(data, loaded, { forms[it] }, System.currentTimeMillis()) // saved with the next save
            } catch (e: Throwable) {
                Report.fail(TAG, "turning Not supported yet Pokémon into Pokémon", e)
            }
        }
        val result = Loaded(data, save, saveError)
        handler.post { applyLoaded(result) }
    }

    /** Main thread: takes over what the loader found, then tells everybody who waited. */
    private fun applyLoaded(r: Loaded) {
        loadedData = r.data
        r.save?.let { s ->
            if (s.problems.isNotEmpty()) Report.fail(TAG, "save problems: " + s.problems.joinToString("; "))
            when {
                s.state != null -> { held = s.state; error = null }
                s.problems.isNotEmpty() -> { held = null; error = s.problems.joinToString("\n") }
                else -> { held = GameState(createdAt = System.currentTimeMillis()); error = null }
            }
        }
        r.saveError?.let { held = null; error = it }
        rebuild()
        widgetTeam = widgetSignature(widgetApp) // the widgets are drawn from this game: later changes are measured against it
        val callbacks = synchronized(lock) {
            loading = false
            ArrayList(waiters).also { waiters.clear() }
        }
        callbacks.forEach {
            try { it(status) } catch (e: Exception) { Report.fail(TAG, "load callback", e) }
        }
        notifyListeners()
    }

    /** Game + status from what is loaded: no data -> NEED_DATA, no state -> SAVE_ERROR, else the starter decides. */
    private fun rebuild() {
        val d = loadedData
        val st = held
        current = null
        pendingFight.clear() // a loaded save, a restored backup, an import, a new game: nothing waiting leads to it
        status = when {
            d == null -> Status.NEED_DATA
            st == null -> Status.SAVE_ERROR
            else -> {
                current = Game(d, st)
                if (st.starterChosen) {
                    // R4 fix A2: the first, quiet look at the achievements, before any screen reads this game or any action changes it
                    // (a loaded save, a restored backup, an import: they all come through here), so the tiers it earns are stored first.
                    try { Achievements.onReady(st, d, System.currentTimeMillis()) { Report.log("ACH", it) } } catch (e: Exception) { Report.fail(TAG, "first look at the achievements", e) }
                    try { Wishlist.prune(st) } catch (e: Exception) { Report.fail(TAG, "pruning the wishlist", e) }
                    try { Looks.sanitize(st, d) } catch (e: Exception) { Report.fail(TAG, "clearing uncaught looks", e) }
                    Status.READY
                } else Status.NEED_STARTER
            }
        }
    }

    // ---- using the game ----

    private fun isMain() = Looper.myLooper() == Looper.getMainLooper()

    private fun requireMain() = check(isMain()) { "GameRepo must be used on the main thread" }

    private fun onMain(f: () -> Unit) { if (isMain()) f() else handler.post { f() } }

    /**
     * Runs a game action on the main thread, then schedules a save and notifies listeners. Returns its events.
     * Returns an empty list (and does nothing) while no game is loaded: call [load] first. An exception inside the action
     * is logged to the Report and swallowed, so a bug can't crash the app in the middle of studying.
     */
    fun act(ctx: Context, f: (Game) -> List<GameEvent>): List<GameEvent> {
        requireMain()
        val g = current ?: return emptyList()
        try {
            return f(g).also { GameLog.events(it); wildMoves.observe(it, held?.battle); com.husarp.ankigo.ui.AchievementPopup.offer(it); forgetPendingFight(it) }
        } catch (e: Exception) {
            // A bug in one action must not take the whole app down while studying; the state stays as it is.
            Report.fail(TAG, "action failed", e)
            return emptyList()
        } finally {
            afterChange(ctx)
        }
    }

    /** Same for actions that return an ActionResult. */
    fun <T> run(ctx: Context, f: (Game) -> T): T = runIn(ctx, host = true, f)

    private fun <T> runIn(ctx: Context, host: Boolean, f: (Game) -> T): T {
        requireMain()
        val g = current ?: error("The game is not loaded")
        val settingsBefore = com.husarp.ankigo.debug.SettingsText.of(g.state.settings) // R13 Part E: a changed game setting goes to the log
        try {
            return f(g).also { result ->
                com.husarp.ankigo.debug.SettingsText.diff(settingsBefore, com.husarp.ankigo.debug.SettingsText.of(g.state.settings)).forEach { Report.log("SETTINGS", "game $it") }
                when (result) {
                    is com.husarp.ankigo.game.ActionResult -> { GameLog.events(result.events); wildMoves.observe(result.events, held?.battle); com.husarp.ankigo.ui.AchievementPopup.offer(result.events); if (host) forgetPendingFight(result.events) }
                    is List<*> -> result.filterIsInstance<GameEvent>().let { GameLog.events(it); wildMoves.observe(it, held?.battle); com.husarp.ankigo.ui.AchievementPopup.offer(it); if (host) forgetPendingFight(it) }
                }
            }
        } catch (e: Exception) {
            GameLog.error("GAME", e)
            throw e
        } finally {
            afterChange(ctx)
        }
    }

    // ---- fight events made on a screen that is not a battle (PLAN 191) ----

    private val pendingFight = PendingFight()

    /** The id of the fight on now (the wild Pokémon's uid), or null without a fight. */
    fun fightId(): String? = held?.battle?.wild?.uid

    /**
     * PLAN 191: [run] for the pages that are not a battle host (Team, Box, Compare, the Bag, the details sheets: [com.husarp.ankigo.ui.GamePage.act]).
     * What the action did to the fight on (an item on the main, a team edit that changed it: the new Pokémon comes in and the wild one
     * attacks once) happens in the saved game at once, but no battle shows it. Those fight events are kept as pending, with the id of the
     * fight, for the battle host that is on screen next ([takePendingFightEvents]); a battle host that is on screen now takes them
     * as soon as the listeners are told. Only a fight that goes on keeps them ([PendingFight.fightPart]).
     */
    fun runOffscreen(ctx: Context, f: (Game) -> com.husarp.ankigo.game.ActionResult): com.husarp.ankigo.game.ActionResult {
        val before = fightId()
        val result = runIn(ctx, host = false, f)
        keepPendingFight(before, result.events)
        return result
    }

    /** [runOffscreen]'s hand-off: the fight events of [events], made in fight [before] and still the fight on now. */
    internal fun keepPendingFight(before: String?, events: List<GameEvent>) {
        if (before == null || before != fightId()) return
        PendingFight.fightPart(events)?.let { pendingFight.add(before, it) }
    }

    /**
     * The fight events waiting for a battle host, handed over ONCE: returns them and forgets them. [fightId] = the fight the host
     * shows now; events of another fight (the wild one was replaced meanwhile) or of no fight are dropped and none come back.
     */
    fun takePendingFightEvents(fightId: String? = fightId()): List<GameEvent> {
        val had = !pendingFight.isEmpty
        val events = pendingFight.take(fightId)
        if (had) notifyListeners() // the other panels drew the Pokémon before the hit while they waited: they follow the saved game now (or the host that took them plays)
        return events
    }

    /** The fight events waiting for a battle host (nothing is taken): a panel draws the main at its HP before the hit until they play. */
    fun pendingFightEvents(): List<GameEvent> = pendingFight.peek(fightId())

    /** An action of a battle host (or an answer from AnkiDroid) moved the fight on by itself: what waited no longer leads to the saved game, it is dropped. */
    private fun forgetPendingFight(events: List<GameEvent>) {
        if (!pendingFight.isEmpty && PendingFight.movesFightOn(events)) pendingFight.clear()
    }

    private fun afterChange(ctx: Context) {
        if (status == Status.NEED_STARTER && held?.starterChosen == true) status = Status.READY
        scheduleSave(ctx)
        notifyListeners()
        widgetsFollowTheTeam(ctx)
    }

    // ---- widgets ----

    /** What the home-screen widgets last showed of the team ([WidgetLogic.teamSignature]); null until a game is loaded. */
    private var widgetTeam: String? = null

    private var widgetApp: Context? = null

    /** Which Pokémon the placed widgets show by pick (a test swaps it); the default reads the Buddy widgets' prefs. */
    internal var widgetPicked: (Context) -> Set<String> = { com.husarp.ankigo.ui.BuddyWidgetPrefs.pickedUids(it) }

    /** The team signature plus the Pokémon the widgets pick, so a picked boxed Pokémon's look / name / level / release redraws them too. */
    private fun widgetSignature(ctx: Context?): String? {
        val s = held ?: return null
        val picked = if (ctx == null) emptySet() else try { widgetPicked(ctx) } catch (_: Throwable) { emptySet() }
        return WidgetLogic.teamSignature(s, picked)
    }

    /** How the widgets are told to draw again (a test swaps it). The study widgets keep their card. */
    internal var widgetRefresh: (Context) -> Unit = { Widgets.refreshAll(it, study = false) }

    /**
     * R9-W5-A: the team, the main or one of their pictures / names changed (the Pokémon tab, Switch Pokémon, a look, an evolution, a
     * release, an import...): the widgets show them, so they draw again now, not with the next answer. One shared place for every
     * source of such a change. Answers (HP, XP, cash) do not change the signature and send nothing.
     */
    private fun widgetsFollowTheTeam(ctx: Context) {
        val sig = widgetSignature(ctx.applicationContext)
        val was = widgetTeam
        widgetTeam = sig
        if (was == null || sig == null || was == sig) return
        try { widgetRefresh(ctx.applicationContext) } catch (e: Throwable) { Report.fail(TAG, "refreshing the widgets", e) } // never reaches the caller: a missed nudge only leaves a widget a little late
    }

    // ---- saving ----

    private var appContext: Context? = null
    private var saveQueued = false
    private val writer = Executors.newSingleThreadExecutor { r -> Thread(r, "ankigo-save").apply { isDaemon = true } }
    private val saveRunnable = Runnable {
        saveQueued = false
        appContext?.let { submitSave(it) }
    }

    /**
     * Coalesced background save (call after any change made outside act/run). The first change starts a ~300 ms timer,
     * more changes inside it ride along, so a steady stream of answers still saves every 300 ms.
     */
    fun scheduleSave(ctx: Context) {
        val app = ctx.applicationContext
        if (!isMain()) { handler.post { scheduleSave(app) }; return }
        appContext = app
        if (saveQueued) return
        saveQueued = true
        handler.postDelayed(saveRunnable, SAVE_DELAY_MS)
    }

    /**
     * Main thread: bumps seq and encodes the state, then hands the JSON to the single writer thread (so writes can't
     * overtake each other). Null when there is nothing to save (SAVE_ERROR: never write over unreadable saves).
     */
    private fun submitSave(app: Context): Future<*>? {
        val st = held ?: return null
        val store = store(app)
        val day = st.day
        val json = try {
            store.prepare(st)
        } catch (e: Exception) {
            Report.fail(TAG, "encoding the save", e)
            return null
        }
        return writer.submit(Runnable {
            try {
                store.write(json, day)
            } catch (e: Exception) {
                Report.fail(TAG, "writing the save", e) // the game keeps running; the next change tries again
            }
        })
    }

    /** Synchronous save (onStop, before the process may die). */
    fun saveNow(ctx: Context) {
        if (!isMain()) { scheduleSave(ctx); return }
        handler.removeCallbacks(saveRunnable)
        saveQueued = false
        val f = submitSave(ctx.applicationContext) ?: return
        try {
            f.get(SAVE_WAIT_SECONDS, TimeUnit.SECONDS)
        } catch (e: Exception) {
            Report.fail(TAG, "save did not finish", e)
        }
    }

    /**
     * Replaces the current state (restore backup / import), keeping the old one as a backup first
     * (dir/save.before-<backupTag>-<millis>.json; "restore" by default, "ankimon" for the Ankimon import).
     * Get [newState] from SaveStore.restore so its seq is above every file on disk.
     */
    fun replaceState(ctx: Context, newState: GameState, backupTag: String = "restore") {
        requireMain()
        val app = ctx.applicationContext
        saveNow(app)
        held?.let { old ->
            try {
                store(app).backupBeforeReplace(old, backupTag)
            } catch (e: IOException) {
                Report.fail(TAG, "could not keep the old save before restoring", e)
                throw IOException("Could not back up the current game; nothing was replaced", e)
            }
            if (newState.seq < old.seq) newState.seq = old.seq // prepare() bumps it, so the restored game wins on disk
        }
        GameLog.imported(backupTag, newState.mons.size, newState.seq)
        held = newState
        error = null
        rebuild()
        saveNow(app)
        notifyListeners()
        widgetsFollowTheTeam(app)
    }

    /**
     * Starts over after SAVE_ERROR only when the user explicitly asked. The old files stay on disk, renamed
     * (save.abandoned-<millis>.json for save.json, ...-bak.json, ...-tmp.json; the snapshots folder keeps its snapshots as
     * snapshots.abandoned-<millis>) because SaveStore.load would otherwise bring an older save back to life.
     */
    fun startNewGame(ctx: Context, forgetAnkimon: Boolean = false) {
        requireMain()
        val app = ctx.applicationContext
        saveNow(app) // a running game is saved first, so what gets set aside is its latest state
        val dir = saveDir(app)
        val stamp = System.currentTimeMillis()
        moveAside(File(dir, "save.json"), File(dir, "save.abandoned-$stamp.json"))
        moveAside(File(dir, "save.bak"), File(dir, "save.abandoned-$stamp-bak.json"))
        moveAside(File(dir, "save.json.tmp"), File(dir, "save.abandoned-$stamp-tmp.json"))
        moveAside(File(dir, "snapshots"), File(dir, "snapshots.abandoned-$stamp"))
        if (forgetAnkimon) AnkimonFiles.forget(app) // else a later export still builds on the old import (its warning says so)
        held = GameState(createdAt = stamp)
        error = null
        rebuild()
        notifyListeners()
        widgetsFollowTheTeam(app)
    }

    private fun moveAside(from: File, to: File) {
        if (!from.exists()) return
        try {
            Files.move(from.toPath(), to.toPath())
        } catch (e: IOException) {
            Report.fail(TAG, "could not move ${from.name} aside", e)
        }
    }

    // ---- listeners ----

    private var notifyQueued = false
    private val notifyRunnable = Runnable {
        notifyQueued = false
        for (l in listeners) {
            try { l() } catch (e: Exception) { Report.fail(TAG, "listener", e) }
        }
    }

    /**
     * Listeners are called on the main thread, shortly after a change (not inside [act]): changes that follow each other
     * give one call, so a flood of caught-up answers doesn't redraw the screen a hundred times, and a listener that
     * changes the game itself can't loop. Add and remove from any thread.
     */
    fun addListener(l: () -> Unit) { listeners.addIfAbsent(l) }
    fun removeListener(l: () -> Unit) { listeners.remove(l) }

    private fun notifyListeners() {
        if (!isMain()) { handler.post { notifyListeners() }; return }
        if (notifyQueued) return
        notifyQueued = true
        handler.post(notifyRunnable)
    }
}
