package com.husarp.ankigo.overlay

import android.content.Context
import android.view.Gravity
import android.widget.ImageView
import android.widget.TextView
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.R
import com.husarp.ankigo.game.GameEvent
import com.husarp.ankigo.live.LiveSession
import com.husarp.ankigo.ui.AppMenu
import com.husarp.ankigo.ui.BattlePanelView
import com.husarp.ankigo.ui.EventPlayer
import com.husarp.ankigo.ui.StatsActivity
import com.husarp.ankigo.ui.LogKind
import com.husarp.ankigo.ui.Ui

/**
 * The floating battle window's contents: a [FloatWindow] with the compact [BattlePanelView] and its own [EventPlayer],
 * fed by [LiveSession] while the window is shown. When the game isn't READY it shows a short text instead of the panel.
 * [FloatBattleService] decides when to [show] and [hide] it. Main thread only.
 */
internal class FloatBattleView(
    private val app: Context,
    /** The user closed the window (X or Back): the service ends. */
    private val onClose: () -> Unit,
    private val onTogglePause: () -> Unit,
    private val onOpenGame: () -> Unit,
) {
    private val window = FloatWindow(app, "battle", 240, 190, 170, 130) { closed() }
    private val pauseButton: ImageView
    private val menuButton: ImageView

    private var panel: BattlePanelView? = null
    private var player: EventPlayer? = null
    private var message: TextView? = null
    private var menu: AppMenu? = null
    private var attached = false

    /** The panel as a [LiveSession] sink; same wiring as GameActivity's. */
    private val sink = object : LiveSession.Sink {
        override fun onGameEvents(events: List<GameEvent>) { player?.enqueue(events) }
        override fun onLogLine(kind: LogKind, pill: String, text: String, redraw: Boolean) {
            player?.enqueueAction { if (redraw) panel?.renderAll(); panel?.setLog(kind, pill, text) }
        }
        override fun onStatus(ok: Boolean, text: String, canGrant: Boolean) { panel?.setStatus(ok, text, canGrant) }
    }

    init {
        window.compactHeader = true
        window.setTitle("AnkiGo")
        pauseButton = window.addHeaderButton(R.drawable.ic_pause, "Pause", { onTogglePause() })
        menuButton = window.addHeaderButton(R.drawable.ic_menu, "Menu: Team, Pokédex, Shop, Settings, Stats", { showMenu() })
        window.addHeaderButton(R.drawable.ic_open, "Open AnkiGo", { onOpenGame() })
    }

    val isShowing: Boolean get() = window.isShowing

    /**
     * Shows the window (already showing = just refreshes what it shows) and attaches the live sink.
     * False when Android refused the window (overlay permission gone).
     */
    fun show(): Boolean {
        bind()
        if (!window.isShowing) {
            panel?.renderAll() // everything that happened while it was hidden
            if (!window.show()) return false
        }
        attach()
        return true
    }

    /** Hides the window and detaches the sink; the object can [show] again. */
    fun hide() {
        if (!window.isShowing && !attached && menu == null) return // called on every game change while hidden
        menu?.dismiss()
        menu = null
        detach()
        player?.clear()
        window.hide()
    }

    /** Hides for good (service ends). */
    fun destroy() = hide()

    /** Pause/resume button follows [LiveSession.paused]. */
    fun setPaused(paused: Boolean) {
        pauseButton.setImageResource(if (paused) R.drawable.ic_play else R.drawable.ic_pause)
        pauseButton.contentDescription = if (paused) "Resume" else "Pause"
    }

    // ---------------------------------------------------------------- internals

    private fun closed() {
        detach()
        player?.clear()
        onClose()
    }

    private fun attach() {
        if (attached || panel == null) return
        attached = true
        LiveSession.addSink(sink)
    }

    private fun detach() {
        if (!attached) return
        attached = false
        LiveSession.removeSink(sink)
    }

    /** Panel while the game is READY, a short text otherwise (loading, setup unfinished, save problem). */
    private fun bind() {
        val ready = GameRepo.status == GameRepo.Status.READY && GameRepo.game != null && GameRepo.data != null
        if (ready) {
            if (panel != null) return
            val p = BattlePanelView(window.context)
            p.setCompact(true)
            p.setCornerClearance(Ui.dp(window.context, 26)) // keeps the log clear of the resize grip
            p.onGrant = { onOpenGame() } // the permission dialog needs an activity
            p.arena.onPlayerTap = { openMainDetails() }
            panel = p
            player = EventPlayer(p)
            message = null
            window.setContent(p)
            if (window.isShowing) p.renderAll()
        } else {
            if (panel != null) {
                detach()
                player?.clear()
                panel = null
                player = null
            }
            val m = message ?: Ui.text(window.context, 13f, 800, R.color.ag_sub0).apply {
                gravity = Gravity.CENTER
                val pad = Ui.dp(window.context, 12)
                setPadding(pad, pad, pad, pad)
                setOnClickListener { onOpenGame() }
            }.also { message = it; window.setContent(it) }
            m.text = when (GameRepo.status) {
                GameRepo.Status.LOADING -> "Loading…"
                GameRepo.Status.SAVE_ERROR -> "Open AnkiGo: your save needs attention."
                else -> "Open AnkiGo to finish setting up."
            }
        }
    }

    /** Tap on our Pokémon (PLAN 86): its Details window on top of the floating battle; not while an animation plays or without a main. */
    private fun openMainDetails() {
        val uid = GameRepo.state?.main?.uid ?: return
        if (player?.idle == false) return
        try { com.husarp.ankigo.ui.BattleDetails.show(window.context, uid) }
        catch (e: Exception) { Ui.toast(app, "Turn on “Display over other apps” for AnkiGo to open this here") }
    }

    /** The ⋯ menu: [AppMenu] hung on the header button's window (the overlay), same entries as the battle screen's. */
    private fun showMenu() {
        val m = AppMenu(menuButton)
        fillMenu(m)
        menu = m
        m.show { id ->
            menu = null
            if (id == 5 || id == 6) { // no Activity here: its own task
                app.startActivity(android.content.Intent(app, if (id == 5) StatsActivity::class.java else com.husarp.ankigo.ui.BattleLogActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                return@show
            }
            if (id == 7) { // Help: its own task too
                com.husarp.ankigo.help.HelpActivity.open(app)
                return@show
            }
            val page = when (id) {
                1 -> FloatPage.TEAM
                2 -> FloatPage.DEX
                3 -> FloatPage.SHOP
                else -> FloatPage.SETTINGS
            }
            if (!FloatScreens.open(app, page)) Ui.toast(app, "Turn on “Display over other apps” for AnkiGo to open this here")
        }
    }

    /** The five entries (also read by the screenshots). */
    internal fun fillMenu(m: AppMenu) {
        m.item(1, "Team", R.drawable.ic_team)
        m.item(2, "Pokédex", R.drawable.ic_book)
        m.item(3, "Shop", R.drawable.ic_shop)
        m.item(4, "Settings", R.drawable.ic_settings)
        m.item(5, "Stats", R.drawable.ic_stats)
        m.item(6, "Battle log", R.drawable.ic_swords)
        m.item(7, "Help", R.drawable.ic_help)
    }
}
