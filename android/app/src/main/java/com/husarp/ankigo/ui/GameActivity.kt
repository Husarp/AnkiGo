package com.husarp.ankigo.ui

import android.app.Activity
import android.app.AlertDialog
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.util.Rational
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
import com.husarp.ankigo.live.LiveSession
import com.husarp.ankigo.overlay.FloatBattleService
import com.husarp.ankigo.overlay.FloatPage
import com.husarp.ankigo.overlay.FloatScreens
import com.husarp.ankigo.overlay.Overlay
import com.husarp.ankigo.update.UpdateBanner
import com.husarp.ankigo.update.Updates
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The AnkiGo battle panel (launcher): meant for the bottom half of a split screen next to AnkiDroid, also fine full screen.
 * Nothing needs a tap while you study: [LiveSession] sees each answer in AnkiDroid, [Game] turns it into battle
 * events and [EventPlayer] shows them on the [BattlePanelView]. Loading, first-run and save-error screens live here too.
 * Leaving the app with LeaveMode.PIP puts the compact battle into picture-in-picture (Open and Pause/Resume buttons);
 * the activity is only paused there, so live detection keeps running.
 */
class GameActivity : Activity() {
    private lateinit var content: FrameLayout
    private lateinit var nav: View
    private lateinit var bannerHolder: FrameLayout   // holds the update banner so PiP can hide it whatever the banner decides

    private var panel: BattlePanelView? = null
    private var player: EventPlayer? = null
    private var bound = false               // the panel is showing a loaded game
    private var started = false             // between onStart and onStop
    private var loading = false
    private var firstRunCanceled = false
    private var destroyed = false
    private var inPip = false
    private var internalNav = false          // we are opening one of our own screens: that is not "leaving" (no PiP)
    private var pipReceiver: BroadcastReceiver? = null
    private val pauseListener: (Boolean) -> Unit = { p -> panel?.setPaused(p); updatePipParams() }

    /** The panel as a [LiveSession] sink: attached while the activity is started, so a stopped one queues no animations. */
    private val liveSink = object : LiveSession.Sink {
        override fun onGameEvents(events: List<GameEvent>) { player?.enqueue(events) }
        override fun onLogLine(kind: LogKind, pill: String, text: String, redraw: Boolean) {
            player?.enqueueAction { if (redraw) panel?.renderAll(); panel?.setLog(kind, pill, text) }
        }
        override fun onStatus(ok: Boolean, text: String, canGrant: Boolean) { panel?.setStatus(ok, text, canGrant) }
    }

    // ---------------------------------------------------------------- lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Report.init(this)
        Anki.load(this)
        if (savedInstanceState == null) Updates.onAppStart(this)

        val root = buildContent()
        setContentView(root)
        showMessage("Loading…")
        registerPipReceiver()
    }

    /** The screen's View tree (banner, content holder, bottom bar). Split out of onCreate so screenshots can build it. */
    internal fun buildContent(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.c(this@GameActivity, R.color.ag_base))
        }
        bannerHolder = FrameLayout(this).apply { addView(UpdateBanner(this@GameActivity), FrameLayout.LayoutParams(-1, -2)) }
        root.addView(bannerHolder, LinearLayout.LayoutParams(-1, -2))
        content = FrameLayout(this)
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        nav = BottomNav.create(this, BottomNav.Tab.BATTLE).also { it.visibility = View.GONE }
        root.addView(nav, LinearLayout.LayoutParams(-1, -2))
        // The bottom bar only fits a tall window; in a split-screen half the ⋯ menu is the way out.
        root.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            val tall = v.height / resources.displayMetrics.density >= 560f
            val want = if (tall && !inPip) View.VISIBLE else View.GONE
            if (nav.visibility != want) nav.visibility = want
        }
        return root
    }

    override fun onStart() {
        super.onStart()
        started = true
        BattleScreen.setVisible(this, true)
        LiveSession.addPauseListener(pauseListener)
        panel?.setPaused(LiveSession.paused)
        if (bound) {
            panel?.renderAll()
            LiveSession.addSink(liveSink)
            LiveSession.hold(this, this)
        }
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent) }

    override fun onResume() {
        super.onResume()
        Updates.onResume(this)
        val reviewDeck = intent.getLongExtra("reviewDeck", 0)
        if (reviewDeck != 0L) {
            intent.removeExtra("reviewDeck")
            window.decorView.post { runCatching { startActivity(Anki.reviewIntent(reviewDeck)) }.onFailure { Ui.toast(this, "Open AnkiDroid once first") } }
        }
        internalNav = false
        updatePipParams()
        FloatBattleService.startIfWanted(this)
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
        BattleScreen.setVisible(this, false)
        LiveSession.removePauseListener(pauseListener)
        LiveSession.removeSink(liveSink)
        if (bound) {
            LiveSession.saveSnapshot()
            LiveSession.release(this, this)
            player?.clear()
            GameRepo.saveNow(this)
            Widgets.refreshAll(this)
        }
        super.onStop()
    }

    override fun onDestroy() {
        destroyed = true
        BattleScreen.setVisible(this, false)
        pipReceiver?.let { try { unregisterReceiver(it) } catch (e: IllegalArgumentException) { } }
        pipReceiver = null
        LiveSession.removePauseListener(pauseListener)
        LiveSession.removeSink(liveSink)
        LiveSession.release(this, this) // in case onStop didn't run
        player?.clear()
        super.onDestroy()
    }

    // ---------------------------------------------------------------- picture-in-picture

    /** PiP applies only for LeaveMode.PIP, with a loaded game, on a device that has it, and not while opening our own screens. */
    private fun pipWanted(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && bound && !internalNav &&
            AppPrefs.leaveMode(this) == LeaveMode.PIP &&
            packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    private fun pipActions(): List<RemoteAction> {
        fun action(icon: Int, title: String, pi: PendingIntent) =
            RemoteAction(Icon.createWithResource(this, icon), title, title, pi)
        val open = PendingIntent.getActivity(this, 0,
            Intent(this, GameActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val toggle = if (LiveSession.paused) {
            action(R.drawable.ic_play, "Resume", pipBroadcast(ACTION_RESUME, 2))
        } else {
            action(R.drawable.ic_pause, "Pause", pipBroadcast(ACTION_PAUSE, 1))
        }
        return listOf(action(R.drawable.ic_open, "Open", open), toggle)
    }

    private fun pipBroadcast(action: String, code: Int): PendingIntent =
        PendingIntent.getBroadcast(this, code, Intent(action).setPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private fun pipParams(): PictureInPictureParams {
        val b = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(3, 2))
            .setActions(pipActions())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) b.setAutoEnterEnabled(pipWanted())
        return b.build()
    }

    /** Keeps the PiP params (auto-enter, buttons) current. Call when the game binds, on resume and when pause changes. */
    private fun updatePipParams() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || destroyed) return
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) return
        try {
            setPictureInPictureParams(pipParams())
        } catch (t: Throwable) {
            Report.fail("PANEL", "pip params", t)
        }
    }

    /** API 26-30: leaving by Home/Recents enters PiP by hand (API 31+ does it through auto-enter). */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S || !pipWanted() || inPip) return
        try {
            enterPictureInPictureMode(pipParams())
        } catch (t: Throwable) {
            Report.fail("PANEL", "enter pip", t)
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip = isInPictureInPictureMode
        panel?.setCompact(inPip)
        bannerHolder.visibility = if (inPip) View.GONE else View.VISIBLE
        // the layout listener re-decides the bottom bar by height; force it now so it doesn't flash in PiP
        if (inPip) nav.visibility = View.GONE else content.requestLayout()
    }

    /** The PiP buttons Pause and Resume come back as broadcasts (the Open button starts this activity directly). */
    private fun registerPipReceiver() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val r = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    ACTION_PAUSE -> LiveSession.setPaused(this@GameActivity, true)
                    ACTION_RESUME -> LiveSession.setPaused(this@GameActivity, false)
                }
            }
        }
        val f = IntentFilter().apply { addAction(ACTION_PAUSE); addAction(ACTION_RESUME) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) registerReceiver(r, f, Context.RECEIVER_NOT_EXPORTED)
        else registerReceiver(r, f)
        pipReceiver = r
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
        if (bound && started) LiveSession.restart(this)
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
                if (getSharedPreferences("firstrun", 0).getBoolean("studyOnly", false)) {
                    startActivity(Intent(this, StudyDecksActivity::class.java)); finish(); return
                }
                if (firstRunCanceled) {
                    showCard("Setup isn't finished",
                        "AnkiGo needs the game data and your first Pokémon before it can start.",
                        "Continue setup" to { firstRunCanceled = false; onStatus(GameRepo.status) })
                } else {
                    startActivityForResult(Intent(this, FirstRunActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NO_USER_ACTION), REQ_FIRST_RUN)
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
        p.onGrant = { internalNav = true; updatePipParams(); requestPermissions(arrayOf(Anki.permission), REQ_PERMISSION) }
        p.onMenu = { showMenu(it) }
        p.onBalls = { showBallDialog() }
        p.arena.onCatchTagTap = { toggleCatch() }
        p.setPaused(LiveSession.paused)
        if (inPip) p.setCompact(true)
        panel = p
        player = EventPlayer(p)
        content.removeAllViews()
        content.addView(p, FrameLayout.LayoutParams(-1, -1))
        bound = true
        p.renderAll()
        handleEvents(act { it.ensureEncounter() })
        if (started) {
            LiveSession.addSink(liveSink)
            LiveSession.hold(this, this)
        }
        updatePipParams()
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
                    .setNegativeButton("Cancel", null).showCard()
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
                        try { GameRepo.replaceState(this, st) }
                        catch (e: Exception) {
                            Report.fail("GAME", "backing up before restore", e)
                            Ui.toast(this, "Couldn't back up the current game. Nothing was changed.")
                            return@runOnUiThread
                        }
                        startLoad()
                    }
                }.start()
            }
            .setNegativeButton("Cancel", null).showCard()
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
                    .setNegativeButton("Cancel", null).showCard()
            }
            .setNegativeButton("Cancel", null).showCard()
    }

    // ---------------------------------------------------------------- game actions

    /** Runs a game action; a failure is logged (it must never crash a study session) and gives no events. */
    private fun act(f: (Game) -> List<GameEvent>): List<GameEvent> = try {
        GameRepo.act(this, f)
    } catch (t: Throwable) {
        Report.fail("PANEL", "game action", t)
        emptyList()
    }

    /** Hands the events of this screen's own actions to the player; some of them change what the widgets show. */
    private fun handleEvents(events: List<GameEvent>) {
        if (events.isEmpty()) return
        player?.enqueue(events)
        LiveSession.refreshWidgetsFor(this, events)
    }

    private fun showMenu(anchor: View) {
        val m = PopupMenu(this, anchor)
        m.menu.add(0, 1, 0, "Team")
        m.menu.add(0, 2, 1, "Pokédex")
        m.menu.add(0, 3, 2, "Shop")
        m.menu.add(0, 4, 3, "Settings")
        m.menu.add(0, 5, 4, "Switch main")
        m.menu.add(0, 6, 5, "Stats")
        m.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> openScreen(FloatPage.TEAM, TeamActivity::class.java)
                2 -> openScreen(FloatPage.DEX, DexActivity::class.java)
                3 -> openScreen(FloatPage.SHOP, ShopActivity::class.java)
                4 -> openScreen(FloatPage.SETTINGS, SettingsActivity::class.java)
                5 -> switchMain()
                6 -> { // always its own Activity, also in split screen
                    internalNav = true
                    updatePipParams()
                    startActivity(Intent(this, StatsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NO_USER_ACTION))
                }
            }
            true
        }
        m.show()
    }

    /**
     * In split screen (with "floating screens" on) the screen opens as a floating card over AnkiDroid so the battle
     * stays visible; if the overlay permission is missing it is explained once and the screen comes back after it is
     * granted. Full screen, or anything else: the normal Activity.
     */
    private fun openScreen(page: FloatPage, cls: Class<*>) {
        if (isInMultiWindowMode && AppPrefs.floatScreens(this)) {
            if (Overlay.canDraw(this)) {
                if (FloatScreens.open(this, page)) return
            } else if (Overlay.askOnce(this, "Team, Pokédex, Shop and Settings can open in a small window over AnkiDroid, so the battle stays visible.")) {
                return
            }
        }
        internalNav = true
        updatePipParams()
        startActivity(Intent(this, cls).addFlags(Intent.FLAG_ACTIVITY_NO_USER_ACTION))
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
            .setNegativeButton("Cancel", null).showCard()
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

    private companion object {
        const val REQ_FIRST_RUN = 1
        const val REQ_PERMISSION = 2
        const val ACTION_PAUSE = "com.husarp.ankigo.PIP_PAUSE"
        const val ACTION_RESUME = "com.husarp.ankigo.PIP_RESUME"
    }
}
