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
    private val menuButton: ImageView
    private val pausedPill: TextView
    private var paused = false

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
        // "Paused" pill (tap = resume), only while paused
        pausedPill = Ui.pill(app, "Paused", R.color.ag_peach).apply {
            visibility = android.view.View.GONE
            contentDescription = "Paused. Tap to resume watching AnkiDroid"
            setOnClickListener { onTogglePause() }
        }
        window.addHeaderView(pausedPill)
        menuButton = window.addHeaderButton(R.drawable.ic_menu, "Menu", { showMenu() })
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
        } else refreshPicture()
        attach()
        return true
    }

    /**
     * R9-C6 / R9-W5-A: the window stayed up while the game changed under it (the floating Team / Pokédex card on top sent the main to
     * the box, changed a look, ...): the main's card and picture follow. Not while an animation plays; no reload when nothing changed.
     */
    internal fun refreshPicture() {
        if (player?.idle != false) panel?.refreshMain()
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

    /** The header "Paused" pill and the menu's Pause/Resume row follow [LiveSession.paused]. */
    fun setPaused(paused: Boolean) {
        this.paused = paused
        pausedPill.visibility = if (paused) android.view.View.VISIBLE else android.view.View.GONE
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
            p.arena.stageZoom = 0.72f // the picture may zoom in up to ~28 % in this small window (R8-04)
            p.setCornerClearance(Ui.dp(window.context, 26)) // keeps the log clear of the resize grip
            p.onGrant = { onOpenGame() } // the permission dialog needs an activity
            p.arena.onPlayerTap = { openMainDetails() }
            p.arena.canTurn = { player?.idle != false }
            p.arena.onWildTap = { openWildInfo() }
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

    /** Tap on the wild Pokémon (PLAN 93): its info window on top of the floating battle; not while an animation plays. */
    private fun openWildInfo() {
        if (player?.idle == false) return
        try { com.husarp.ankigo.ui.BattleDetails.showWild(window.context) }
        catch (e: Exception) { Ui.toast(app, "Turn on “Display over other apps” for AnkiGo to open this here") }
    }

    /** The ⋯ menu: [AppMenu] hung on the header button's window (the overlay), same entries as the battle screen's. */
    private fun showMenu() {
        val m = AppMenu(menuButton)
        fillMenu(m)
        menu = m
        m.show { id ->
            menu = null
            if (id == 8) { onOpenGame(); return@show }
            if (id == 9) { onTogglePause(); return@show }
            if (id == 7) { // Help: its own task too
                com.husarp.ankigo.help.HelpActivity.open(app)
                return@show
            }
            if (id == 10) { // R10 B19: the view settings sheet, an overlay window of its own
                try { com.husarp.ankigo.ui.ViewSettingsSheet.open(window.context) }
                catch (e: Exception) { Ui.toast(app, "Turn on “Display over other apps” for AnkiGo to open this here") }
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

    /** The entries (also read by the screenshots). */
    internal fun fillMenu(m: AppMenu) {
        m.item(8, "Open AnkiGo", R.drawable.ic_open)
        if (paused) m.item(9, "Resume watching", R.drawable.ic_play)
        else m.item(9, "Pause watching AnkiDroid", R.drawable.ic_pause)
        m.item(1, "Pokémon", R.drawable.ic_team)
        m.item(2, "Pokédex", R.drawable.ic_dex)
        m.item(3, "Shop", R.drawable.ic_shop)
        m.item(4, "Settings", R.drawable.ic_settings)
        m.item(10, "View settings", R.drawable.ic_palette)
        m.item(7, "Help", R.drawable.ic_help)
    }
}
