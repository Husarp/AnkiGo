package com.husarp.ankigo.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.TextView
import com.husarp.ankigo.Anki
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.R
import com.husarp.ankigo.Report
import com.husarp.ankigo.Widgets
import com.husarp.ankigo.game.Catching
import com.husarp.ankigo.game.Game
import com.husarp.ankigo.game.GameEvent
import com.husarp.ankigo.game.ItemKind
import com.husarp.ankigo.game.Items
import com.husarp.ankigo.game.Outcome
import com.husarp.ankigo.game.Tuning
import com.husarp.ankigo.live.LiveDetector
import com.husarp.ankigo.update.UpdateBanner
import com.husarp.ankigo.update.Updates
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.husarp.ankigo.game.Answer as GameAnswer

/**
 * The AnkiGo battle panel (launcher): meant for the bottom half of a split screen next to AnkiDroid, also fine full screen.
 * Nothing needs a tap while you study: [LiveDetector] sees each answer in AnkiDroid, [Game] turns it into battle
 * events and [EventPlayer] shows them on the [BattlePanelView]. Loading, first-run and save-error screens live here too.
 */
class GameActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var content: FrameLayout
    private lateinit var nav: View
    private lateinit var detector: LiveDetector

    private var panel: BattlePanelView? = null
    private var player: EventPlayer? = null
    private var bound = false               // the panel is showing a loaded game
    private var started = false             // between onStart and onStop
    private var loading = false
    private var firstRunCanceled = false
    private var destroyed = false
    private var detectorUsed = false        // detector.start was called, so its snapshot means something

    private val pendingCatchUp = ArrayList<GameAnswer>()
    private var flushPosted = false
    private var lastWidgets = 0L
    private var widgetsPosted = false

    // ---------------------------------------------------------------- lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Report.init(this)
        Anki.load(this)
        if (savedInstanceState == null) Updates.onAppStart(this)
        detector = LiveDetector(this) { onLive(it) }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.c(this@GameActivity, R.color.ag_base))
        }
        root.addView(UpdateBanner(this), LinearLayout.LayoutParams(-1, -2))
        content = FrameLayout(this)
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        nav = BottomNav.create(this, BottomNav.Tab.BATTLE).also { it.visibility = View.GONE }
        root.addView(nav, LinearLayout.LayoutParams(-1, -2))
        // The bottom bar only fits a tall window; in a split-screen half the ⋯ menu is the way out.
        root.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            val tall = v.height / resources.displayMetrics.density >= 560f
            val want = if (tall) View.VISIBLE else View.GONE
            if (nav.visibility != want) nav.visibility = want
        }
        setContentView(root)
        showMessage("Loading…")
    }

    override fun onStart() {
        super.onStart()
        started = true
        if (bound) {
            panel?.renderAll()
            startDetection()
        }
    }

    override fun onResume() {
        super.onResume()
        Updates.onResume(this)
        if (bound) {
            val events = act { it.tick() }
            if (events.isEmpty()) { if (player?.idle != false) panel?.renderAll() } else handleEvents(events)
        } else {
            startLoad()
        }
    }

    override fun onPause() {
        Updates.onPause(this)
        super.onPause()
    }

    override fun onStop() {
        started = false
        if (bound) {
            detector.stop()
            flushCatchUp()
            player?.clear()
            saveSnapshot()
            GameRepo.saveNow(this)
            Widgets.refreshAll(this)
        }
        super.onStop()
    }

    override fun onDestroy() {
        destroyed = true
        detector.stop()
        player?.clear()
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_FIRST_RUN) {
            firstRunCanceled = resultCode != RESULT_OK
            startLoad()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQ_PERMISSION) return
        val ok = grantResults.firstOrNull() == 0
        Report.log("PANEL", "permission ${permissions.firstOrNull()}: ${if (ok) "granted" else "denied"}")
        if (bound && started) { detector.stop(); startDetection() }
    }

    // ---------------------------------------------------------------- loading the game

    private fun startLoad() {
        if (loading || bound) return
        loading = true
        showMessage("Loading…")
        GameRepo.load(this) { status ->
            loading = false
            if (!destroyed) onStatus(status)
        }
    }

    private fun onStatus(status: GameRepo.Status) {
        when (status) {
            GameRepo.Status.LOADING -> showMessage("Loading…")
            GameRepo.Status.NEED_DATA, GameRepo.Status.NEED_STARTER -> {
                if (firstRunCanceled) {
                    showCard("Setup isn't finished",
                        "AnkiGo needs the game data and your first Pokémon before it can start.",
                        "Continue setup" to { firstRunCanceled = false; onStatus(GameRepo.status) })
                } else {
                    startActivityForResult(Intent(this, FirstRunActivity::class.java), REQ_FIRST_RUN)
                }
            }
            GameRepo.Status.SAVE_ERROR -> showSaveError()
            GameRepo.Status.READY -> bind()
        }
    }

    private fun bind() {
        if (bound) return
        if (GameRepo.game == null) { showMessage("Loading…"); return }
        player?.clear()
        val p = BattlePanelView(this)
        p.onGrant = { requestPermissions(arrayOf(Anki.permission), REQ_PERMISSION) }
        p.onMenu = { showMenu(it) }
        p.onBalls = { showBallDialog() }
        p.arena.onCatchTagTap = { toggleCatch() }
        panel = p
        player = EventPlayer(p)
        content.removeAllViews()
        content.addView(p, FrameLayout.LayoutParams(-1, -1))
        bound = true
        p.renderAll()
        handleEvents(act { it.ensureEncounter() })
        if (started) startDetection()
    }

    // ---------------------------------------------------------------- simple screens (loading, errors)

    private fun showMessage(text: String) {
        content.removeAllViews()
        content.addView(Ui.text(this, 16f, 800, R.color.ag_sub0).apply { this.text = text; gravity = Gravity.CENTER },
            FrameLayout.LayoutParams(-1, -1))
    }

    private fun button(label: String, tone: Int, onClick: () -> Unit): TextView =
        Ui.text(this, 13f, 900, R.color.ag_crust).apply {
            text = label
            gravity = Gravity.CENTER
            setPadding(Ui.dp(this@GameActivity, 14), Ui.dp(this@GameActivity, 11), Ui.dp(this@GameActivity, 14), Ui.dp(this@GameActivity, 11))
            background = Ui.rounded(Ui.c(this@GameActivity, tone), 12, this@GameActivity)
            setOnClickListener { onClick() }
        }

    /** A card with a title, a text and buttons (mauve; those named in [danger] red). */
    private fun showCard(title: String, message: String, vararg buttons: Pair<String, () -> Unit>, danger: Set<String> = emptySet()) {
        val card = Ui.card(this).apply {
            val p = Ui.dp(this@GameActivity, 16)
            setPadding(p, p, p, p)
        }
        card.addView(Ui.text(this, 18f, 900, R.color.ag_text).apply { text = title })
        card.addView(Ui.text(this, 13f, 700, R.color.ag_sub1).apply { text = message; setLineSpacing(0f, 1.15f) },
            LinearLayout.LayoutParams(-1, -2).also { it.topMargin = Ui.dp(this, 8) })
        for ((label, action) in buttons) {
            card.addView(button(label, if (label in danger) R.color.ag_red else R.color.ag_mauve, action),
                LinearLayout.LayoutParams(-1, -2).also { it.topMargin = Ui.dp(this, 10) })
        }
        content.removeAllViews()
        content.addView(ScrollView(this).apply {
            val m = Ui.dp(this@GameActivity, 14)
            setPadding(m, m, m, m)
            clipToPadding = false
            addView(card, FrameLayout.LayoutParams(-1, -2))
        }, FrameLayout.LayoutParams(-1, -1))
    }

    private fun showSaveError() {
        showCard("Your save couldn't be read",
            "Your save couldn't be read: ${GameRepo.error ?: "unknown error"}\n\nNothing was deleted. Restore a backup, or start over only if you really want to.",
            "Restore a backup" to { chooseBackup() },
            "Start a new game" to { confirmNewGame() },
            danger = setOf("Start a new game"))
    }

    private fun chooseBackup() {
        val store = GameRepo.store(this)
        Thread {
            val found = store.backups().mapNotNull { f -> store.read(f)?.let { f to it } }
            runOnUiThread {
                if (destroyed) return@runOnUiThread
                if (found.isEmpty()) { Ui.toast(this, "No readable backup found"); return@runOnUiThread }
                val fmt = SimpleDateFormat("d MMM yyyy HH:mm", Locale.getDefault())
                val labels = found.map { (f, st) -> "${fmt.format(Date(f.lastModified()))}  ·  save #${st.seq}  ·  ${st.mons.size} Pokémon" }
                AlertDialog.Builder(this).setTitle("Restore a backup")
                    .setItems(labels.toTypedArray()) { _, i -> confirmRestore(found[i].first, labels[i]) }
                    .setNegativeButton("Cancel", null).show()
            }
        }.start()
    }

    private fun confirmRestore(file: File, label: String) {
        AlertDialog.Builder(this).setTitle("Restore this backup?")
            .setMessage("Your game goes back to:\n$label\n\nThe current files stay on disk.")
            .setPositiveButton("Restore") { _, _ ->
                Thread {
                    val st = GameRepo.store(this).restore(file)
                    runOnUiThread {
                        if (destroyed) return@runOnUiThread
                        if (st == null) { Ui.toast(this, "That backup couldn't be read"); return@runOnUiThread }
                        GameRepo.replaceState(this, st)
                        startLoad()
                    }
                }.start()
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun confirmNewGame() {
        AlertDialog.Builder(this).setTitle("Start a new game?")
            .setMessage("You begin with nothing. Your old save files stay on disk, but the game won't use them.")
            .setPositiveButton("Continue") { _, _ ->
                AlertDialog.Builder(this).setTitle("Really start over?")
                    .setMessage("Your Pokémon, items and progress are gone from the game. Are you sure?")
                    .setPositiveButton("Start over") { _, _ ->
                        GameRepo.startNewGame(this)
                        startLoad()
                    }
                    .setNegativeButton("Cancel", null).show()
            }
            .setNegativeButton("Cancel", null).show()
    }

    // ---------------------------------------------------------------- game actions

    /** Runs a game action; a failure is logged (it must never crash a study session) and gives no events. */
    private fun act(f: (Game) -> List<GameEvent>): List<GameEvent> = try {
        GameRepo.act(this, f)
    } catch (t: Throwable) {
        Report.fail("PANEL", "game action", t)
        emptyList()
    }

    /** Hands events to the player; some of them change what the widgets show. */
    private fun handleEvents(events: List<GameEvent>) {
        if (events.isEmpty()) return
        player?.enqueue(events)
        if (changesWidgets(events)) refreshWidgetsSoon()
    }

    private fun changesWidgets(events: List<GameEvent>) = events.any {
        it is GameEvent.Caught || it is GameEvent.Defeated || it is GameEvent.MainHealed || it is GameEvent.Evolved || it is GameEvent.DayChanged
    }

    /** Widgets refresh at most once per 20 s; a refresh that is too early is postponed, not dropped. */
    private fun refreshWidgetsSoon() {
        val wait = WIDGET_EVERY_MS - (SystemClock.elapsedRealtime() - lastWidgets)
        if (wait <= 0) {
            lastWidgets = SystemClock.elapsedRealtime()
            Widgets.refreshAll(this)
        } else if (!widgetsPosted) {
            widgetsPosted = true
            handler.postDelayed({
                widgetsPosted = false
                lastWidgets = SystemClock.elapsedRealtime()
                if (!destroyed) Widgets.refreshAll(this)
            }, wait)
        }
    }

    private fun showMenu(anchor: View) {
        val m = PopupMenu(this, anchor)
        m.menu.add(0, 1, 0, "Team")
        m.menu.add(0, 2, 1, "Pokédex")
        m.menu.add(0, 3, 2, "Shop")
        m.menu.add(0, 4, 3, "Settings")
        m.menu.add(0, 5, 4, "Switch main")
        m.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> startActivity(Intent(this, TeamActivity::class.java))
                2 -> startActivity(Intent(this, DexActivity::class.java))
                3 -> startActivity(Intent(this, ShopActivity::class.java))
                4 -> startActivity(Intent(this, SettingsActivity::class.java))
                5 -> switchMain()
            }
            true
        }
        m.show()
    }

    private fun switchMain() {
        val r = try { GameRepo.run(this) { it.cycleMain() } } catch (t: Throwable) { Report.fail("PANEL", "cycleMain", t); return }
        if (r.message.isNotEmpty()) Ui.toast(this, r.message)
        handleEvents(r.events)
        player?.enqueueAction { panel?.refreshPlayer() }
    }

    /** Optional: pick a ball to throw at the wild Pokémon, with each ball's chance. */
    private fun showBallDialog() {
        val st = GameRepo.state ?: return
        val battle = st.battle
        if (battle == null) { Ui.toast(this, "No wild Pokémon right now"); return }
        val types = GameRepo.data?.species?.get(battle.wild.species)?.types.orEmpty()
        val balls = st.bag.filter { (id, n) -> n > 0 && Items.def(id)?.kind == ItemKind.BALL }.keys
            .sortedBy { Tuning.BALL_CHANCE[it] ?: 0 }
        if (balls.isEmpty()) { Ui.toast(this, "No Poké Balls. Buy some in the shop."); return }
        val labels = balls.map { "${Items.name(it)}  ×${st.count(it)}  ·  ${Catching.ballChance(it, types)}%" }
        AlertDialog.Builder(this).setTitle("Throw a ball")
            .setItems(labels.toTypedArray()) { _, i -> handleEvents(act { it.throwBall(balls[i]) }) }
            .setNegativeButton("Cancel", null).show()
    }

    /** Optional: tap the catch tag to flip catch <-> defeat for this fight. */
    private fun toggleCatch() {
        val st = GameRepo.state ?: return
        val battle = st.battle ?: return
        val g = GameRepo.game ?: return
        val before = try { g.willCatch() } catch (t: Throwable) { return }
        try {
            GameRepo.run(this) { it.setOverride(if (before) Outcome.DEFEAT else Outcome.CATCH) }
        } catch (t: Throwable) {
            Report.fail("PANEL", "setOverride", t)
            return
        }
        val after = g.willCatch()
        panel?.arena?.setCatchTag(after, battle.wild.species !in st.caught)
        Ui.toast(this, when {
            after != before && after -> "Will catch it"
            after != before -> "Will defeat it instead (XP)"
            after -> "This one is always caught"
            else -> "No change"
        })
    }

    // ---------------------------------------------------------------- live detection

    private fun startDetection() {
        val st = GameRepo.state ?: return
        val g = GameRepo.game ?: return
        if (detector.running) return
        detectorUsed = true
        detector.start(st.liveSnapshot, st.liveDay, g.dayKey())
    }

    /** Copies the detector's card map into the state, so the save and the game agree on what was counted. */
    private fun saveSnapshot() {
        if (!detectorUsed) return
        val st = GameRepo.state ?: return
        st.liveSnapshot = detector.snapshot()
        st.liveDay = GameRepo.game?.dayKey()
    }

    private fun saveSnapshotInto(g: Game) {
        if (!detectorUsed) return
        g.state.liveSnapshot = detector.snapshot()
        g.state.liveDay = g.dayKey()
    }

    private fun onLive(e: LiveDetector.Event) {
        if (destroyed || !bound) return
        when (e) {
            is LiveDetector.Event.Status ->
                panel?.setStatus(e.ok, e.text, !e.ok && e.text.contains("permission", ignoreCase = true))
            is LiveDetector.Event.Answer -> onAnswer(e)
            is LiveDetector.Event.Undo -> {
                val done = try {
                    GameRepo.run(this) { g -> saveSnapshotInto(g); g.onUndo(e.cardId, e.count) }
                    true
                } catch (t: Throwable) {
                    Report.fail("PANEL", "undo", t)
                    false
                }
                if (done) player?.enqueueAction { panel?.setLog(LogKind.NEUTRAL, "UNDO", "Undo in AnkiDroid: the redo won't count twice") }
            }
        }
    }

    private fun onAnswer(e: LiveDetector.Event.Answer) {
        val a = GameAnswer(e.cardId, e.button, System.currentTimeMillis(), e.catchUp)
        if (e.catchUp) {
            // Answers made while AnkiGo was closed arrive in one burst: no animation, one summary line.
            pendingCatchUp += a
            if (!flushPosted) { flushPosted = true; handler.post { flushPosted = false; flushCatchUp() } }
            return
        }
        flushCatchUp()
        handleEvents(act { g -> saveSnapshotInto(g); g.tick() + g.onAnswer(a) })
        // The detector refreshes its map a moment after delivering; save the fresh one too.
        handler.removeCallbacks(snapshotLater)
        handler.postDelayed(snapshotLater, 1500)
    }

    private val snapshotLater = Runnable {
        if (destroyed || !bound) return@Runnable
        saveSnapshot()
        GameRepo.scheduleSave(this)
    }

    private fun flushCatchUp() {
        if (pendingCatchUp.isEmpty()) return
        val batch = pendingCatchUp.toList()
        pendingCatchUp.clear()
        val events = act { g -> saveSnapshotInto(g); g.tick() + batch.flatMap { g.onAnswer(it) } }
        var won = 0; var caught = 0; var cash = 0
        for (ev in events) when (ev) {
            is GameEvent.Defeated -> won++
            is GameEvent.Caught -> caught++
            is GameEvent.Cash -> cash += ev.amount
            else -> {}
        }
        val n = batch.size
        val text = "Caught up $n answer${if (n == 1) "" else "s"} made while AnkiGo was closed: $won won, $caught caught, +$cash ₽"
        player?.enqueueAction { panel?.renderAll(); panel?.setLog(LogKind.SYS, "CAUGHT UP", text) }
        if (changesWidgets(events)) refreshWidgetsSoon()
    }

    private companion object {
        const val REQ_FIRST_RUN = 1
        const val REQ_PERMISSION = 2
        const val WIDGET_EVERY_MS = 20_000L
    }
}
