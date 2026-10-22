package com.husarp.ankigo.ui

import android.app.Activity
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
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.TextView
import com.husarp.ankigo.Anki
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.R
import com.husarp.ankigo.Report
import com.husarp.ankigo.Widgets
import com.husarp.ankigo.data.AnkimonFiles
import com.husarp.ankigo.game.Game
import com.husarp.ankigo.game.GameEvent
import com.husarp.ankigo.game.Outcome
import com.husarp.ankigo.live.LiveSession
import com.husarp.ankigo.overlay.FloatBattleService
import com.husarp.ankigo.overlay.FloatPage
import com.husarp.ankigo.overlay.FloatScreens
import com.husarp.ankigo.overlay.Overlay
import com.husarp.ankigo.update.Updates
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The AnkiGo home screen (launcher, PLAN 88 + 108). It has two views ([HomeView], decided by [HomeLayout]):
 * Study (the deck list, [StudyHome], with the bottom bar; the small battle card lives in the Pokémon tab) and the full-screen
 * Battle (back arrow, Pop out, ⋯, no bottom bar; Back returns to where it was opened from). In a short window
 * (the bottom half of a split screen next to AnkiDroid, a landscape phone) and in picture-in-picture it shows the battle only,
 * as before the merge; a short window with no playable battle (study only, setup canceled, save error) keeps the Study layout. The panel is on screen, and animated by [LiveSession], only in those battle layouts.
 * Nothing needs a tap while you study: [LiveSession] sees each answer in AnkiDroid, [Game] turns it into battle
 * events and [EventPlayer] shows them on the [BattlePanelView]. Loading, first-run and save-error screens live here too.
 * Leaving the app with LeaveMode.PIP puts the compact battle into picture-in-picture (Open and Pause/Resume buttons);
 * the activity is only paused there, so live detection keeps running.
 */
class GameActivity : AgActivity() {
    private lateinit var content: FrameLayout   // the battle's area: the panel, or a loading message; in the Study view the status slot (setup row / card), else gone
    private var capped: CappedFrame? = null     // [content] as the capped frame (the status slot of a short window's Study layout shares the area with the deck list)
    private lateinit var nav: View
    private lateinit var studyBar: View       // Study view: "Study" app bar with the AnkiDroid pill, Sync and New deck
    private lateinit var topBar: View        // slim "Battle" bar with Back, Pop out and ⋯; only in the Battle view of a tall window
    private lateinit var continueSlot: FrameLayout // "Continue studying" bar under the top bar (PLAN 92); empty/gone unless a deck is due
    private lateinit var deckPane: View       // Study view: the deck list with pull-to-refresh
    private lateinit var homeRoot: View
    private var continueDeck: Anki.Deck? = null
    private var topContinue: View? = null      // PLAN 204: small green Continue button in the top bar (before Pop out)
    private var topContinueDeck: Anki.Deck? = null // its deck: the card rule (no "hide today"), set by loadContinue
    private var homeView = HomeView.STUDY
    private var parts: HomeLayout.Parts? = null  // what the last layout decision showed ([relayout])
    private var studyOnly = false             // "Study without the game" was chosen at first run
    private var battleOrigin: Dest? = null    // the screen that opened the full-screen battle (the Pokémon tab's card): Back returns there
    private var popOutPending = false         // the Pokémon tab's Pop out asked for the pop-out; it runs once the game is bound and we are on screen
    private var holding = false               // LiveSession is held (started, with a loaded game)
    private var sinkOn = false                // the panel is a LiveSession sink (it is on screen)
    private var slotKey: Any? = null          // what [fillContent] last drew, so a layout pass that changes nothing redraws nothing

    private var panel: BattlePanelView? = null
    private var player: EventPlayer? = null
    private var bound = false               // the panel is showing a loaded game
    private var started = false             // between onStart and onStop
    private var loading = false
    private var firstRunCanceled = false
    private var destroyed = false
    private var inPip = false
    private var internalNav = false          // we are opening one of our own screens: that is not "leaving" (no PiP)
    private val studyReturn = StudyReturn()  // "Study in AnkiDroid" round trip: back lands on the Study tab
    private var pipReceiver: BroadcastReceiver? = null
    private val pauseListener: (Boolean) -> Unit = { p -> panel?.setPaused(p); updatePipParams() }

    /** What the Study view's deck list asks of the home screen. */
    private val homeHost = object : StudyHome.Host {
        override fun studyInAnkiDroid(deckId: Long) = reviewInAnkiDroid(deckId)
        override fun askAnkiAccess() { internalNav = true; updatePipParams(); requestPermissions(arrayOf(Anki.permission), REQ_PERMISSION) }
    }
    internal val home by lazy { StudyHome(this, homeHost) }

    /** The panel as a [LiveSession] sink: attached while the activity is started and the panel is on screen, so nothing hidden queues animations. */
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
        studyReturn.restore(savedInstanceState?.getIntArray("studyReturn"))
        // the view survives a recreation; else the Intent says (the launcher start has no extra: Study)
        homeView = HomeView.of(savedInstanceState?.getString(HomeView.EXTRA_VIEW))
            ?: HomeNav.viewFromIntent(intent?.getStringExtra(HomeView.EXTRA_VIEW), null)
        battleOrigin = if (savedInstanceState != null) Dest.entries.firstOrNull { it.name == savedInstanceState.getString(STATE_ORIGIN) }
            else HomeNav.originAfter(intent?.getStringExtra(HomeView.EXTRA_VIEW), intent?.getStringExtra(HomeView.EXTRA_FROM), null)
        popOutPending = savedInstanceState == null && intent?.getBooleanExtra(HomeView.EXTRA_POP_OUT, false) == true
        studyOnly = getSharedPreferences("firstrun", 0).getBoolean("studyOnly", false)

        val root = buildContent()
        setContentView(root)
        registerPipReceiver()
        home.resumeFlows()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putIntArray("studyReturn", studyReturn.toInts())
        outState.putString(HomeView.EXTRA_VIEW, homeView.key)
        outState.putString(STATE_ORIGIN, battleOrigin?.name)
    }

    /** The screen's View tree (app bars, content holder, deck list, bottom bar). Split out of onCreate so screenshots can build it. */
    internal fun buildContent(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.c(this@GameActivity, R.color.ag_base))
        }
        homeRoot = root
        studyBar = home.buildAppBar().also { it.visibility = View.GONE }
        root.addView(studyBar, LinearLayout.LayoutParams(-1, -2))
        topBar = buildTopBar().also { it.visibility = View.GONE }
        root.addView(topBar, LinearLayout.LayoutParams(-1, -2))
        continueSlot = FrameLayout(this).also { it.visibility = View.GONE }
        root.addView(continueSlot, LinearLayout.LayoutParams(-1, -2))
        // the battle's area (or the Study view's status slot) and the deck list share the area between the bars; [relayout] sizes them for the view
        content = CappedFrame(this).also { capped = it }
        deckPane = home.buildBody().also { it.visibility = View.GONE }
        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
            addView(deckPane, LinearLayout.LayoutParams(-1, 0, 1f))
        }, LinearLayout.LayoutParams(-1, 0, 1f))
        nav = BottomNav.create(this, BottomNav.tabOf(Dest.STUDY)).also { it.visibility = View.GONE }
        root.addView(nav, LinearLayout.LayoutParams(-1, -2))
        // Which bars and views fit depends on the window's height ([HomeLayout]); in a split-screen half the ⋯ menu is the way out.
        root.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ -> relayout(v.height / resources.displayMetrics.density) }
        return root
    }

    /**
     * Applies [HomeLayout.decide] for a window [heightDp] dp tall (every layout pass, a new view, a new game status, PiP):
     * which bars and parts show, what the battle's area holds, and the panel's own tool buttons. Does nothing when the
     * answer has not changed. Height 0 (not laid out yet) is skipped, except in PiP.
     */
    internal fun relayout(heightDp: Float = homeRoot.height / resources.displayMetrics.density) {
        if (heightDp <= 0f && !inPip) return
        val p = HomeLayout.decide(heightDp, inPip, homeView, GameRepo.status)
        if (p == parts) return
        val deckListWasShown = parts?.deckList == true
        parts = p
        studyBar.visibility = if (p.studyBar) View.VISIBLE else View.GONE
        topBar.visibility = if (p.battleBar) View.VISIBLE else View.GONE
        deckPane.visibility = if (p.deckList) View.VISIBLE else View.GONE
        nav.visibility = if (p.bottomBar) View.VISIBLE else View.GONE
        content.visibility = if (p.card == HomeLayout.Card.NONE) View.GONE else View.VISIBLE
        val lp = content.layoutParams as LinearLayout.LayoutParams
        if (p.card == HomeLayout.Card.FULL) { lp.height = 0; lp.weight = 1f } else { lp.height = -2; lp.weight = 0f } // SLOT: as tall as what it holds
        capped?.cap = if (p.card == HomeLayout.Card.SLOT && !p.tall) SHORT_SLOT_SHARE else 0f // a short window: a tall card scrolls, the deck list keeps its half
        content.layoutParams = lp
        fillContent(force = true)
        applyToPanel()
        syncLive()
        updateContinueVisibility()
        if (p.deckList && !deckListWasShown) home.refresh()
    }

    /** What the last layout decision means for the panel: its tool buttons (the Battle view's top bar has them). */
    private fun applyToPanel() {
        panel?.setFooterTools(parts?.footerTools ?: true)
    }

    /** The panel is in the battle's area and that area is on screen (the Battle view, a short window, picture-in-picture). */
    private fun panelShown(): Boolean = bound && panel?.parent === content && parts?.card == HomeLayout.Card.FULL

    /**
     * Fills the battle's area for the last layout decision: the panel when the game plays and the area is the battle's;
     * else what [HomeLayout.slot] says (the "Loading…" message where the battle would be, the "Set up the game" row, the
     * setup card, the save-error card; nothing while the Study view loads). [force]: draw again even when nothing changed.
     */
    private fun fillContent(force: Boolean = false) {
        val p = parts ?: return
        val slot = HomeLayout.slot(GameRepo.status, studyOnly, firstRunCanceled)
        val showPanel = bound && p.card == HomeLayout.Card.FULL
        val key = Triple(showPanel, p.card, slot)
        if (!force && key == slotKey) return
        slotKey = key
        val pn = panel
        when {
            showPanel && pn != null -> {
                if (pn.parent !== content) {
                    content.removeAllViews()
                    content.addView(pn, FrameLayout.LayoutParams(-1, -1))
                    pn.renderAll()
                }
            }
            slot == HomeLayout.Slot.SETUP_ROW -> showSetupRow()
            slot == HomeLayout.Slot.SETUP_CARD -> showCard("Setup isn't finished",
                "AnkiGo needs the game data and your first Pokémon before it can start.",
                "Continue setup" to { firstRunCanceled = false; onStatus(GameRepo.status) })
            slot == HomeLayout.Slot.SAVE_ERROR -> showSaveError()
            slot == HomeLayout.Slot.LOADING && p.card == HomeLayout.Card.FULL -> showMessage("Loading…")
            else -> content.removeAllViews()
        }
    }

    /** Shows [v] (the Study view or the full-screen battle). Study reloads its deck list when it comes back. */
    internal fun setHomeView(v: HomeView) {
        if (homeView == v) return
        homeView = v
        if (v != HomeView.BATTLE) battleOrigin = null // the way back belongs to the battle that was opened
        relayout()
    }

    /** The slim top bar of a tall window: Back, title "Battle", Pop out and ⋯ (Stats, Battle log, Switch… are in the menu; a short window has Pop out, Stats and ⋯ in the panel footer). */
    private fun buildTopBar(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setBackgroundColor(Ui.c(this@GameActivity, R.color.ag_base))
        setPadding(Ui.dp(this@GameActivity, 4), Ui.dp(this@GameActivity, 4), Ui.dp(this@GameActivity, 8), Ui.dp(this@GameActivity, 4))
        addView(topButton(R.drawable.ic_back, "Back", R.color.ag_text) { onBackPressed() })
        addView(Ui.text(this@GameActivity, 18f, 900, R.color.ag_text).apply { text = "Battle"; maxLines = 1; letterSpacing = -0.014f }, LinearLayout.LayoutParams(0, -2, 1f))
        addView(ContinueBar.button(this@GameActivity).also { topContinue = it })
        addView(topButton(R.drawable.ic_pip, "Pop out: small battle window") { popOut() })
        addView(topButton(R.drawable.ic_more_vert, "Menu") { showMenu(it) })
    }

    private fun topButton(icon: Int, description: String, tone: Int = R.color.ag_sub1, onClick: (View) -> Unit): View = ImageView(this).apply {
        setImageResource(icon)
        setColorFilter(Ui.c(this@GameActivity, tone), android.graphics.PorterDuff.Mode.SRC_IN)
        contentDescription = description
        val p = Ui.dp(this@GameActivity, 12)
        setPadding(p, p, p, p)
        layoutParams = LinearLayout.LayoutParams(Ui.dp(this@GameActivity, 48), Ui.dp(this@GameActivity, 48))
        setOnClickListener(onClick)
    }

    override fun onStart() {
        super.onStart()
        started = true
        BattleScreen.setVisible(this, true)
        LiveSession.addPauseListener(pauseListener)
        panel?.setPaused(LiveSession.paused)
        syncLive()
    }

    /**
     * Keeps LiveSession in step with what is on screen: held while the activity is started with a loaded game (answers count
     * wherever you are), the panel attached as a sink only while it is on screen (it draws itself from the game when it comes
     * back, so nothing hidden queues animations).
     */
    private fun syncLive() {
        val hold = started && bound
        if (hold && !holding) { holding = true; LiveSession.hold(this, this) }
        val sink = hold && panelShown()
        if (sink && !sinkOn) {
            sinkOn = true
            panel?.renderAll()
            LiveSession.addSink(liveSink)
        } else if (!sink && sinkOn) {
            sinkOn = false
            LiveSession.removeSink(liveSink)
            player?.clear()
        }
        if (!hold && holding) { holding = false; LiveSession.release(this, this) }
    }

    /** The Continue bar shows only under the top bar (tall window), never in PiP, and only while a deck is due. */
    private fun updateContinueVisibility() {
        val want = if (continueDeck != null && topBar.visibility == View.VISIBLE && !inPip) View.VISIBLE else View.GONE
        if (continueSlot.visibility != want) continueSlot.visibility = want
        val b = topContinue ?: return
        val shown = ContinueBar.topButtonShown(topContinueDeck, topBar.visibility == View.VISIBLE, inPip)
        ContinueBar.bindButton(b, topContinueDeck.takeIf { shown }) { openContinue(it) }
    }

    /** PLAN 204: the top bar's Continue button deck (null hides it). */
    internal fun setTopContinueDeck(deck: Anki.Deck?) {
        topContinueDeck = deck
        updateContinueVisibility()
    }

    /** Shows the Continue bar for [deck] (null removes it). */
    internal fun setContinueDeck(deck: Anki.Deck?) {
        continueDeck = deck
        continueSlot.removeAllViews()
        if (deck != null) continueSlot.addView(ContinueBar.build(this, deck, { openContinue(deck) }, { ContinueBar.hideToday(this); continueGen++; setContinueDeck(null) }))
        updateContinueVisibility()
    }

    /** Which Continue load may still apply its result: bumped by every new load and by the × (so a late result is dropped). */
    private var continueGen = 0

    /** Reads AnkiDroid's deck list off the main thread; no permission or no AnkiDroid means no bar. Never blocks the battle. */
    private fun loadContinue() {
        val gen = ++continueGen
        if (!Anki.hasPermission(this) || Anki.installed(this).none { it.pkg == Anki.pkg }) { setContinueDeck(null); setTopContinueDeck(null); return }
        Thread {
            val decks = runCatching { Anki.decks(this) }.getOrNull()
            val deck = decks?.let { ContinueBar.pick(this, it) }
            val topDeck = decks?.let { ContinueBar.forCard(this, it) }
            runOnUiThread {
                if (destroyed || gen != continueGen) return@runOnUiThread
                setTopContinueDeck(topDeck)
                // the × may have been tapped while the thread ran: the hidden-day rule is checked again here
                setContinueDeck(deck?.takeIf { ContinueBar.hiddenDay(this) != ContinueBar.today() })
            }
        }.start()
    }

    /** Like the Study tab's CONTINUE card: "Tapping a deck opens AnkiDroid" is honoured. */
    private fun openContinue(deck: Anki.Deck) {
        if (getSharedPreferences("study", 0).getBoolean("inAnkiDroid", false)) {
            // we come back to the battle (that is where the bar is): an older Study hand-over must not send the next resume or Back to the Study tab
            studyReturn.ownScreenOpened()
            Thread {
                val ok = runCatching { Anki.selectDeck(this, deck.id) }.isSuccess
                runOnUiThread {
                    if (destroyed) return@runOnUiThread
                    if (ok) runCatching { startActivity(Anki.reviewIntent(deck.id)) }.onFailure { Ui.toast(this, "Open AnkiDroid once first") }
                    else Ui.toast(this, "Couldn't select this deck")
                }
            }.start()
            return
        }
        studyReturn.ownScreenOpened()
        internalNav = true
        updatePipParams()
        startActivity(Intent(this, com.husarp.ankigo.StudyActivity::class.java).putExtra("deckId", deck.id).putExtra("deckName", deck.name))
    }

    /** A start of the home screen that found it open: the Intent says which view to show (PiP "Open", the floating window, a widget, a tab). */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        HomeNav.onIntent(intent.getStringExtra(HomeView.EXTRA_VIEW), studyReturn) // an explicit destination beats a pending "Study in AnkiDroid" return (onResume)
        battleOrigin = HomeNav.originAfter(intent.getStringExtra(HomeView.EXTRA_VIEW), intent.getStringExtra(HomeView.EXTRA_FROM), battleOrigin)
        setHomeView(HomeNav.viewFromIntent(intent.getStringExtra(HomeView.EXTRA_VIEW), homeView))
        if (intent.getBooleanExtra(HomeView.EXTRA_POP_OUT, false)) popOutPending = true
    }

    /** The Pokémon tab's Pop out: once the game is bound and the activity is started, the same pop-out as the top bar's button. */
    private fun runPendingPopOut() {
        if (!popOutPending || !bound || !started) return
        popOutPending = false
        intent?.removeExtra(HomeView.EXTRA_POP_OUT)
        window.decorView.post { popOut() }
    }

    /** "Study in AnkiDroid" for [deckId] (AnkiDroid has selected it): starts its reviewer and tracks the way back to the Study view ([StudyReturn]). */
    internal fun reviewInAnkiDroid(deckId: Long) {
        if (GameRepo.game == null) { // no game running: nothing to keep alive, nothing to come back to
            runCatching { startActivity(Anki.reviewIntent(deckId)) }.onFailure { Ui.toast(this, "Open AnkiDroid once first") }
            return
        }
        studyReturn.requested()
        window.decorView.post {
            studyReturn.ankiStarting()
            runCatching { startActivity(Anki.reviewIntent(deckId)) }.onFailure { studyReturn.ankiFailed(); Ui.toast(this, "Open AnkiDroid once first") }
        }
    }

    override fun onResume() {
        super.onResume()
        val reviewDeck = intent.getLongExtra("reviewDeck", 0)
        if (reviewDeck != 0L) {
            // handed over by the in-app study screen (StudyActivity "Study in AnkiDroid")
            intent.removeExtra("reviewDeck")
            reviewInAnkiDroid(reviewDeck)
        } else if (studyReturn.resumed(inPip)) {
            setHomeView(HomeView.STUDY) // back from AnkiDroid (not through the PiP window): the Study view
        }
        internalNav = false
        updatePipParams()
        FloatBattleService.startIfWanted(this)
        loadContinue()
        if (parts?.deckList == true) home.refresh() // the Study view is showing; else it loads when it first shows
        if (bound) {
            panel?.refreshPrefs()   // Settings may have changed the card layout / compact cards while we were away
            val events = act { it.tick() }
            if (events.isEmpty()) { if (panelShown() && player?.idle != false) panel?.renderAll() } else handleEvents(events)
            if (panelShown()) player?.playPending() // PLAN 191: fight events made on another screen (an item on the main from the Bag, a team edit) play now, once
            runPendingPopOut()
        } else {
            startLoad()
        }
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onStop() {
        started = false
        BattleScreen.setVisible(this, false)
        LiveSession.removePauseListener(pauseListener)
        if (bound) LiveSession.saveSnapshot()
        syncLive() // the sink goes, then the last hold is released (which saves)
        if (bound) {
            player?.clear()
            GameRepo.saveNow(this)
            Widgets.refreshAll(this)
        }
        super.onStop()
    }

    override fun onDestroy() {
        destroyed = true
        home.onDestroy()
        BattleScreen.setVisible(this, false)
        pipReceiver?.let { try { unregisterReceiver(it) } catch (e: IllegalArgumentException) { } }
        pipReceiver = null
        LiveSession.removePauseListener(pauseListener)
        LiveSession.removeSink(liveSink)
        LiveSession.release(this, this) // in case onStop didn't run
        holding = false; sinkOn = false
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
            Nav.intent(this, Dest.BATTLE).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP),
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

    private fun pipAvailable(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    private fun enterPip() {
        try {
            enterPictureInPictureMode(pipParams())
        } catch (t: Throwable) {
            Report.fail("PANEL", "pop out pip", t)
            Ui.toast(this, "Could not open picture-in-picture")
        }
    }

    /**
     * Pop out (PLAN 24, R4-X5): the battle as a floating window or as picture-in-picture, as chosen in Settings
     * (the floating window anyway when the device has no PiP). Works with any leave mode, is not "leaving" (internalNav / studyReturn untouched),
     * does nothing when already in PiP. The floating window needs the overlay permission (explained once; if it was
     * explained before and is still missing, PiP is used when the device has it).
     */
    fun popOut() {
        if (inPip || !bound || destroyed) return
        val pip = pipAvailable()
        if (PopOutPlan.useFloat(AppPrefs.popOutMode(this), pip)) {
            if (!Overlay.canDraw(this)) {
                if (Overlay.askOnce(this, "The floating battle window shows your battle over AnkiDroid while you study.")) return
                if (pip) { enterPip(); return }
                Ui.toast(this, "Allow “Display over other apps” in Settings to pop out")
                return
            }
            FloatBattleService.startPopOut(this)
            moveTaskToBack(true)
        } else {
            enterPip()
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip = isInPictureInPictureMode
        if (inPip) studyReturn.pipEntered()
        // expanded from PiP during a "Study in AnkiDroid" hand-over: show the battle (Back from it goes to the Study view)
        else if (studyReturn.fromStudy) { homeView = HomeView.BATTLE; battleOrigin = null }
        panel?.setCompact(inPip)
        // the layout listener re-decides by height; decide now so the bars don't flash in PiP
        relayout()
        if (!inPip) content.requestLayout()
        updateContinueVisibility()
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
        home.refresh()
        val ok = grantResults.firstOrNull() == 0
        Report.log("PANEL", "permission ${permissions.firstOrNull()}: ${if (ok) "granted" else "denied"}")
        if (bound && started) LiveSession.restart(this)
    }

    // ---------------------------------------------------------------- loading the game

    private fun startLoad() {
        if (loading || bound) return
        loading = true
        GameRepo.load(this) { status ->
            loading = false
            if (!destroyed) onStatus(status)
        }
    }

    private fun onStatus(status: GameRepo.Status) {
        studyOnly = getSharedPreferences("firstrun", 0).getBoolean("studyOnly", false)
        when (status) {
            GameRepo.Status.LOADING, GameRepo.Status.SAVE_ERROR -> {}
            GameRepo.Status.NEED_DATA, GameRepo.Status.NEED_STARTER ->
                // studying without the game: the Study view's slot offers "Set up the game"; a canceled first run: "Continue setup"
                if (!studyOnly && !firstRunCanceled) {
                    startActivityForResult(Intent(this, FirstRunActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NO_USER_ACTION), REQ_FIRST_RUN)
                }
            GameRepo.Status.READY -> bind()
        }
        relayout() // the layout depends on the status (a game that cannot be played shows the Study view)
        fillContent(force = true)
        syncLive()
    }

    private fun bind() {
        if (bound) return
        if (GameRepo.game == null) return // the area keeps showing what its status says
        player?.clear()
        val p = BattlePanelView(this)
        p.onGrant = { internalNav = true; updatePipParams(); requestPermissions(arrayOf(Anki.permission), REQ_PERMISSION) }
        p.onMenu = { showMenu(it) }
        p.addToolButton(R.drawable.ic_pip, "Pop out: small battle window") { popOut() }
        p.addToolButton(R.drawable.ic_stats, "Stats") { openStats() }
        p.onBalls = { showBallDialog() }
        p.arena.onPlayerTap = { openMainDetails() }
        p.arena.canTurn = { !inPip && player?.idle != false }
        p.arena.onWildTap = { openWildInfo() }
        p.setPaused(LiveSession.paused)
        if (inPip) p.setCompact(true)
        panel = p
        player = EventPlayer(p)
        bound = true
        fillContent(force = true) // puts the panel in the battle's area (and draws it) when that is on screen
        applyToPanel()
        handleEvents(act { it.ensureEncounter() })
        syncLive()
        runPendingPopOut()
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

    /** "Study without the game": a slim row in the Study view's status slot; its button leaves that mode and starts the first run (as Settings' Downloads does). */
    private fun showSetupRow() {
        val m = Ui.dp(this, 12)
        val row = StudyUi.row(this).apply {
            background = Ui.rounded(Ui.c(context, R.color.ag_card), 14, context)
            setPadding(Ui.dp(context, 12), Ui.dp(context, 10), Ui.dp(context, 10), Ui.dp(context, 10))
        }
        val text = StudyUi.column(this)
        text.addView(StudyUi.label(this, "Studying without the game", 14, R.color.ag_text, 800))
        text.addView(StudyUi.label(this, "Set it up to battle and catch Pokémon as you study.", 12, R.color.ag_sub0), StudyUi.spaced(this, 2))
        row.addView(text, StudyUi.lp(0, -2, 1f))
        row.addView(StudyUi.smallButton(this, "Set up the game", primary = true) { setUpGame() }, StudyUi.lp(-2, -2).also { it.marginStart = Ui.dp(this, 10) })
        content.removeAllViews()
        content.addView(row, FrameLayout.LayoutParams(-1, -2).also { it.setMargins(m, Ui.dp(this, 4), m, Ui.dp(this, 4)) })
    }

    private fun setUpGame() {
        getSharedPreferences("firstrun", 0).edit().putBoolean("studyOnly", false).apply()
        firstRunCanceled = false
        onStatus(GameRepo.status)
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
                AppDialog.Builder(this).setTitle("Restore a backup")
                    .setItems(labels.toTypedArray()) { _, i -> confirmRestore(found[i].first, labels[i]) }
                    .setNegativeButton("Cancel", null).showCard()
            }
        }.start()
    }

    private fun confirmRestore(file: File, label: String) {
        AppDialog.Builder(this).setTitle("Restore this backup?")
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
        AppDialog.Builder(this).setTitle("Start a new game?")
            .setMessage("You begin with nothing. Your old save files stay on disk, but the game won't use them.")
            .setPositiveButton("Continue") { _, _ ->
                Pickers.show(AnkimonExportFlow.startOverDialog(this, AnkimonFiles.keptAt(this)) { forget ->
                    GameRepo.startNewGame(this, forgetAnkimon = forget)
                    startLoad()
                })
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
        if (panelShown()) player?.enqueue(events) // a panel that is not on screen draws the final state when it comes back
        LiveSession.refreshWidgetsFor(this, events)
    }

    /** The ⋯ menu entries. "Switch Pokémon" is greyed with the reason under it when it can't work. */
    internal fun fillMenu(m: AppMenu) {
        m.item(8, "Pop out", R.drawable.ic_pip, sub = "Small battle window over other apps", dividerAfter = true)
        m.item(1, "Pokémon", R.drawable.ic_team)
        m.item(2, "Pokédex", R.drawable.ic_dex)
        m.item(3, "Shop", R.drawable.ic_shop)
        m.item(4, "Settings", R.drawable.ic_settings)
        val blocker = try { GameRepo.game?.cycleMainBlocker() } catch (t: Throwable) { null }
        m.item(5, "Switch Pokémon", R.drawable.ic_swap, enabled = blocker == null, sub = blocker)
        m.item(6, "Stats", R.drawable.ic_stats)
        m.item(7, "Battle log", R.drawable.ic_swords)
        m.item(11, "View settings", R.drawable.ic_palette)   // R10 B19: Pokémon size, facing, info boxes, frame... in one sheet
        catchMenuLabel()?.let { m.item(9, it, R.drawable.ic_ball) }
        m.item(10, "Help", R.drawable.ic_help)
    }

    private fun showMenu(anchor: View) {
        val m = AppMenu(anchor)
        fillMenu(m)
        m.show { id ->
            when (id) {
                1 -> openScreen(FloatPage.TEAM, Dest.TEAM)
                2 -> openScreen(FloatPage.DEX, Dest.DEX)
                3 -> openScreen(FloatPage.SHOP, Dest.SHOP)
                4 -> openScreen(FloatPage.SETTINGS, Dest.SETTINGS)
                5 -> openSwitchPopup()
                6 -> openStats()
                7 -> openStats(BattleLogActivity::class.java)
                11 -> ViewSettingsSheet.open(this)
                9 -> toggleCatch()
                10 -> openStats(com.husarp.ankigo.help.HelpActivity::class.java)
                8 -> popOut()
            }
        }
    }

    /**
     * Back from the full-screen battle returns to where it was opened from: the Pokémon tab when its battle card opened it,
     * else the Study view (also for the battle expanded from PiP after "Study in AnkiDroid"); from the Study view, a short
     * window and PiP it is the normal Back ([HomeNav.back]).
     */
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        studyReturn.back() // whichever way Back goes, the hand-over is over
        val p = parts
        when (if (p != null) HomeNav.back(p, battleOrigin) else HomeNav.Back.SYSTEM) {
            HomeNav.Back.TO_STUDY -> setHomeView(HomeView.STUDY)
            HomeNav.Back.TO_ORIGIN -> {
                val origin = battleOrigin
                setHomeView(HomeView.STUDY) // the home screen waits on Study, the origin comes to the front
                if (origin != null) Nav.open(this, origin)
            }
            HomeNav.Back.SYSTEM -> {
                @Suppress("DEPRECATION")
                super.onBackPressed()
            }
        }
    }

    /**
     * Our own screens (the Pokémon, Stats and Settings tabs, a deck's study page, the first run…) are not "leaving": no
     * picture-in-picture, and the system is told so. Other apps (AnkiDroid) are leaving.
     */
    override fun startActivity(intent: Intent, options: Bundle?) {
        if (intent.component?.packageName == packageName) {
            internalNav = true
            updatePipParams()
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_USER_ACTION)
        }
        super.startActivity(intent, options)
    }

    /** Stats are always their own Activity, also in split screen. [screen] = another own screen (Battle log, Help); the default is the Stats tab. */
    private fun openStats(screen: Class<*>? = null) {
        studyReturn.ownScreenOpened()
        internalNav = true
        updatePipParams()
        val i = if (screen == null) Nav.intent(this, Dest.STATS) else Intent(this, screen)
        startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NO_USER_ACTION))
    }

    /**
     * In split screen (with "floating screens" on) the screen opens as a floating card over AnkiDroid so the battle
     * stays visible; if the overlay permission is missing it is explained once and the screen comes back after it is
     * granted. Full screen, or anything else: the normal Activity.
     */
    private fun openScreen(page: FloatPage, dest: Dest, showUid: String? = null) {
        if (isInMultiWindowMode && AppPrefs.floatScreens(this)) {
            if (Overlay.canDraw(this)) {
                if (FloatScreens.open(this, page, showUid)) return
            } else if (Overlay.askOnce(this, "Team, Pokédex, Shop and Settings can open in a small window over AnkiDroid, so the battle stays visible.")) {
                return
            }
        }
        studyReturn.ownScreenOpened()
        internalNav = true
        updatePipParams()
        startActivity(Nav.intent(this, dest).addFlags(Intent.FLAG_ACTIVITY_NO_USER_ACTION).also { if (showUid != null) it.putExtra(TeamActivity.EXTRA_SHOW_UID, showUid) })
    }

    /** Tap on our Pokémon (PLAN 86): its Details window on top of the battle; not while an animation plays or without a main. */
    private fun openMainDetails() {
        if (inPip || player?.idle == false) return
        val uid = GameRepo.state?.main?.uid ?: return
        BattleDetails.show(this, uid)
    }

    /** Tap on the wild Pokémon (PLAN 93): its info window on top of the battle; not in the small PiP window or while an animation plays. */
    private fun openWildInfo() {
        if (inPip || player?.idle == false) return
        BattleDetails.showWild(this)
    }

    /** "Switch Pokémon" (PLAN 99): the team as a grid popup; the picked one becomes the main. */
    private fun openSwitchPopup() {
        val data = GameRepo.data ?: return
        val st = GameRepo.state ?: return
        SwitchPopup.show(this, data, st) { switchMain(it) }
    }

    private fun switchMain(uid: String) {
        if (GameRepo.state?.main?.uid == uid && GameRepo.state?.pendingMain == null) return
        // The animations still waiting end first (the saved game has their numbers), so the switch starts from the fight as it stands:
        // then the short "Go, X!" plays, and the wild one's free hit on the new main (R10 W10) with its floating damage text after it.
        player?.finishNow()
        // PLAN 206: AnkiDroid shows a card unless a result holds the next fight: then the pick is queued, to come in right after that
        // card is answered (the bar says "Switching after this card…"); picking the main again cancels it.
        val cardShowing = player?.holding != true
        val r = try { GameRepo.run(this) { it.requestMain(uid, cardShowing) } } catch (t: Throwable) { Report.fail("PANEL", "requestMain", t); return }
        handleEvents(r.events)
    }

    /** Optional: pick a ball to throw at the wild Pokémon, with each ball's chance. */
    private fun showBallDialog() {
        val st = GameRepo.state ?: return
        val battle = st.battle
        if (battle == null) { Ui.toast(this, "No wild Pokémon right now"); return }
        BallThrowUi.show(this, st) { ball -> handleEvents(act { it.throwBall(ball) }) }   // PLAN 171: each chance, and what a miss costs
    }

    /** The ⋯ entry that flips catch <-> defeat for this fight: its label says what a tap will do; null when no wild Pokémon is on stage. */
    private fun catchMenuLabel(): String? {
        if (GameRepo.state?.battle == null || player?.holding == true) return null   // a held result: the next wild one is not on stage yet
        val will = try { GameRepo.game?.willCatch() } catch (t: Throwable) { null } ?: return null
        return CatchChoiceText.menuLabel(will)
    }

    /** One-off: flip catch <-> defeat for this fight (the ⋯ menu entry). */
    private fun toggleCatch() {
        if (GameRepo.state?.battle == null || player?.holding == true) return
        val g = GameRepo.game ?: return
        val before = try { g.willCatch() } catch (t: Throwable) { return }
        try {
            GameRepo.run(this) { it.setOverride(if (before) Outcome.DEFEAT else Outcome.CATCH) }
        } catch (t: Throwable) {
            Report.fail("PANEL", "setOverride", t)
            return
        }
        val after = g.willCatch()
        Ui.toast(this, CatchChoiceText.toast(before, after))
    }

    /** The battle's area; with [cap] > 0 and a height that is not fixed it takes at most that share of the room it is offered. */
    private class CappedFrame(ctx: Context) : FrameLayout(ctx) {
        var cap = 0f
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val limited = cap > 0f && MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.EXACTLY
            super.onMeasure(widthMeasureSpec,
                if (limited) MeasureSpec.makeMeasureSpec((MeasureSpec.getSize(heightMeasureSpec) * cap).toInt(), MeasureSpec.AT_MOST) else heightMeasureSpec)
        }
    }

    private companion object {
        const val SHORT_SLOT_SHARE = 0.5f
        const val STATE_ORIGIN = "battleOrigin"
        const val REQ_FIRST_RUN = 1
        const val REQ_PERMISSION = 2
        const val ACTION_PAUSE = "com.husarp.ankigo.PIP_PAUSE"
        const val ACTION_RESUME = "com.husarp.ankigo.PIP_RESUME"
    }
}
