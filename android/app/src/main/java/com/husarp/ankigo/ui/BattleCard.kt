package com.husarp.ankigo.ui

import android.content.Context
import android.graphics.PorterDuff
import android.graphics.Outline
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.R
import com.husarp.ankigo.game.GameEvent
import com.husarp.ankigo.live.LiveSession

/**
 * The battle card at the top of the Pokémon tab's Team section (R4-V1, Adam's pick 1): a small "BATTLE" header with a
 * "Pop out" pill and an expand icon, then the live battle panel in its card look ([BattlePanelView.setCardLook]: both
 * Pokémon with names, Lv and HP, the last log line and the daily goal bar). A tap on the card or on the expand icon opens the
 * full-screen battle (a tap on the wild Pokémon opens its info window instead) ([Host.expand]); Pop out is [Host.popOut]. It is live only while it is on screen: [start] holds
 * [LiveSession] and attaches a sink (answers made in AnkiDroid play on the card), [stop] lets both go. The card also shows in a
 * short window (a split-screen half): its panel is then smaller ([panelDp]) and [PanelFit] scales the battle to it. Pure decisions
 * ([wanted], [panelDp]) are tested on the JVM.
 */
internal class BattleCard(private val ctx: Context, private val host: Host, private val session: Live = RealLive) {
    /** What the card needs of the live session: [LiveSession] itself; a test passes its own to count who holds it. */
    interface Live {
        val paused: Boolean
        fun hold(ctx: Context, owner: Any)
        fun release(ctx: Context, owner: Any)
        fun addSink(s: LiveSession.Sink)
        fun removeSink(s: LiveSession.Sink)
        fun addPauseListener(l: (Boolean) -> Unit)
        fun removePauseListener(l: (Boolean) -> Unit)
        /** The AnkiDroid permission was granted: detection starts again. */
        fun restart(ctx: Context) {}
    }

    object RealLive : Live {
        override val paused: Boolean get() = LiveSession.paused
        override fun hold(ctx: Context, owner: Any) = LiveSession.hold(ctx, owner)
        override fun release(ctx: Context, owner: Any) = LiveSession.release(ctx, owner)
        override fun addSink(s: LiveSession.Sink) = LiveSession.addSink(s)
        override fun removeSink(s: LiveSession.Sink) = LiveSession.removeSink(s)
        override fun addPauseListener(l: (Boolean) -> Unit) = LiveSession.addPauseListener(l)
        override fun removePauseListener(l: (Boolean) -> Unit) = LiveSession.removePauseListener(l)
        override fun restart(ctx: Context) = LiveSession.restart(ctx)
    }

    interface Host {
        /** Tap on the card / the expand icon: the full-screen battle (Back returns to the Pokémon tab). */
        fun expand()
        /** The Pop out pill: the battle as a floating window or picture-in-picture (needs the home screen). */
        fun popOut()
        /** The panel's "Grant" button (live status: AnkiDroid permission missing): ask for the permission through the activity; the answer goes to [accessAnswered]. */
        fun grantAccess() {}
    }

    private val panel = BattlePanelView(ctx).apply { setCardLook(true) }
    private val player = EventPlayer(panel)
    private var live = false
    private val pauseListener: (Boolean) -> Unit = { panel.setPaused(it) }

    /** The panel as a [LiveSession] sink; attached only between [start] and [stop], so a hidden card queues no animations. */
    private val sink = object : LiveSession.Sink {
        override fun onGameEvents(events: List<GameEvent>) { player.enqueue(events) }
        override fun onLogLine(kind: LogKind, pill: String, text: String, redraw: Boolean) {
            player.enqueueAction { if (redraw) panel.renderAll(); panel.setLog(kind, pill, text) }
        }
        override fun onStatus(ok: Boolean, text: String, canGrant: Boolean) { panel.setStatus(ok, text, canGrant) }
    }

    /** True between [start] and [stop]: the card holds the live session. */
    val isLive: Boolean get() = live

    /** The card: header + panel, 16 dp corners. Its height follows the window ([panelDp]). */
    val view: View = build()

    private fun build(): View {
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = Ui.rounded(Ui.c(ctx, R.color.ag_card), 16, ctx)
            clipToOutline = true // the arena runs to the card's edges: round them with the card
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(v: View, o: Outline) = o.setRoundRect(0, 0, v.width, v.height, Ui.dp(ctx, 16).toFloat())
            }
            contentDescription = "Battle. Tap to open it"
            setOnClickListener { host.expand() }
        }
        card.addView(header(), LinearLayout.LayoutParams(-1, -2))
        card.addView(panel, LinearLayout.LayoutParams(-1, Ui.dp(ctx, panelDp(ctx.resources.configuration.screenHeightDp.toFloat()))))
        // taps on the panel's arena / your Pokémon open the battle too (not the details they open in the full battle);
        // R6-06: a tap on the wild Pokémon opens its info window on top of this tab instead (not while an animation plays)
        panel.arena.setOnClickListener { host.expand() }
        panel.arena.onPlayerTap = { host.expand() }
        panel.arena.onWildTap = { if (player.idle) BattleDetails.showWild(ctx) }
        panel.arena.canTurn = { player.idle } // PLAN 34: swipe to turn, not while an animation plays
        panel.arena.setPlayerTapHint("Your Pokémon. Tap to open the battle")
        panel.arena.setWildTapHint("Wild Pokémon. Tap for its details")
        panel.setOnClickListener { host.expand() }
        panel.onGrant = { host.grantAccess() }
        return card
    }

    /** "BATTLE" label, the Pop out pill and the expand icon (48 dp touch areas around the small looks). */
    private fun header(): View = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(Ui.dp(ctx, 14), 0, Ui.dp(ctx, 4), 0)
        addView(Ui.text(ctx, 11f, 900, R.color.ag_sub0).apply { text = "BATTLE"; letterSpacing = 0.058f; maxLines = 1 }, LinearLayout.LayoutParams(0, -2, 1f))
        addView(popOutPill())
        addView(ImageView(ctx).apply {
            setImageResource(R.drawable.ic_fullscreen)
            setColorFilter(Ui.c(ctx, R.color.ag_sub1), PorterDuff.Mode.SRC_IN)
            contentDescription = "Open the full battle"
            val p = Ui.dp(ctx, 13)
            setPadding(p, p, p, p)
            setOnClickListener { host.expand() }
        }, LinearLayout.LayoutParams(Ui.dp(ctx, 48), Ui.dp(ctx, 48)))
    }

    /** The accent "Pop out" pill (icon + text, 26 dp look in a 48 dp touch area). */
    private fun popOutPill(): View {
        val accent = Ui.c(ctx, R.color.ag_mauve)
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = Ui.rounded((accent and 0x00FFFFFF) or 0x33000000, 10, ctx)
            setPadding(Ui.dp(ctx, 9), Ui.dp(ctx, 5), Ui.dp(ctx, 10), Ui.dp(ctx, 5))
            addView(ImageView(ctx).apply {
                setImageResource(R.drawable.ic_pip)
                setColorFilter(accent, PorterDuff.Mode.SRC_IN)
            }, LinearLayout.LayoutParams(Ui.dp(ctx, 14), Ui.dp(ctx, 14)).also { it.marginEnd = Ui.dp(ctx, 5) })
            addView(Ui.text(ctx, 12f, 900, R.color.ag_mauve).apply { text = "Pop out"; maxLines = 1 })
        }
        return FrameLayout(ctx).apply {
            minimumHeight = Ui.dp(ctx, 48)
            contentDescription = "Pop out: small battle window"
            setOnClickListener { host.popOut() }
            addView(row, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        }.also { it.layoutParams = LinearLayout.LayoutParams(-2, Ui.dp(ctx, 48)) }
    }

    // ---------------------------------------------------------------- live

    /** The card is on screen: draw the battle from the game and follow it live. Does nothing without a loaded game, or when already live. */
    fun start() {
        if (live || GameRepo.game == null || GameRepo.status != GameRepo.Status.READY) return
        live = true
        panel.refreshPrefs()
        panel.setPaused(session.paused)
        panel.renderAll()
        session.addPauseListener(pauseListener)
        session.addSink(sink)
        session.hold(ctx, this)
    }

    /** The card left the screen (another section, the page paused or closed): stop following, let the session go. */
    fun stop() {
        if (!live) return
        live = false
        session.removePauseListener(pauseListener)
        session.removeSink(sink)
        player.clear()
        session.release(ctx, this)
    }

    /** The AnkiDroid permission dialog was answered: when granted and the card is live, detection starts again (like the other panel hosts). */
    fun accessAnswered(granted: Boolean) {
        if (granted && live) session.restart(ctx)
    }

    /** The game changed by something other than a live answer (a main switched in the team list…): draw it again unless an animation is playing. */
    fun refresh() {
        if (live && player.idle) panel.renderAll()
    }

    companion object {
        /**
         * Whether the Team section shows the card: a game that is READY and the page lives in an Activity (floating cards have
         * none). Also in a short window: the Team section has no battle of its own there, so without the card it would have neither
         * the battle nor its LiveSession hold.
         */
        fun wanted(ready: Boolean, inActivity: Boolean): Boolean = ready && inActivity

        /** Windows shorter than this (dp) are "short" (split-screen half, landscape phone): the same line as [HomeLayout.TALL_DP]. */
        private const val SHORT_DP = HomeLayout.TALL_DP

        /**
         * Height (dp) of the panel under the header: about a quarter of the window, 170 to 210 dp ([PanelFit] scales the battle to
         * it). In a short window (0 = unknown counts as tall) it is smaller, 110 to 150 dp (a third of the window), so the team
         * list under it keeps some room.
         */
        fun panelDp(windowHeightDp: Float): Int =
            if (windowHeightDp > 0f && windowHeightDp < SHORT_DP) (windowHeightDp * 0.33f).coerceIn(110f, 150f).toInt()
            else (windowHeightDp * 0.24f).coerceIn(170f, 210f).toInt()
    }
}
