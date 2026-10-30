package com.husarp.ankigo.ui

import android.app.Activity
import android.app.Application
import android.content.Context
import android.graphics.PorterDuff
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.lang.ref.WeakReference
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.R
import com.husarp.ankigo.game.AskChoice
import com.husarp.ankigo.game.AskMe
import com.husarp.ankigo.game.GameData
import com.husarp.ankigo.game.GameEvent
import com.husarp.ankigo.game.GameState
import com.husarp.ankigo.game.Mon
import com.husarp.ankigo.game.PendingAsk
import com.husarp.ankigo.game.lookNum

/**
 * R19-P (PLAN 281, pick 1C): the catch option "Ask me" asks in an Ankimon-style pop-up when the fight ends: the wild Pokémon and your
 * best copy side by side, three buttons: Catch (keep both) / Catch and replace / Don't catch. Back or a tap outside = Don't catch.
 *
 * WHERE it shows (the question itself lives in the saved game, [GameState.asks], so it survives a restart and is answered exactly once):
 *  - Study, the Battle tab, the Team page's battle card: as soon as the fight's rewards have played ([EventPlayer] queues [offer] behind
 *    them, so it comes before the next card is shown: the result holds until Continue), and again whenever such a screen comes back
 *    to the front ([offer] in onResume), if something still waits.
 *  - Picture-in-picture, the floating window, studying in AnkiDroid with nothing of AnkiGo in front: NO pop-up (it would block the
 *    studying or sit on a postage stamp). The fight is already paid as a defeat (same XP and coins), so nothing is lost or stuck: the
 *    result row says "Your call", the question waits, and it opens the next time a full AnkiGo screen is in front. It never answers
 *    itself, and never catches or releases anything unasked.
 * One question at a time; the next one (also "Release the old one?" after "Catch and replace") follows the answer.
 */
object CatchAskPopup {
    /** True when [events] (a fight's batch) end with a question waiting: the screens queue [offer] behind them. */
    fun waiting(events: List<GameEvent>): Boolean = events.any { (it is GameEvent.Defeated && it.ask) || (it is GameEvent.Duplicate && it.ask) }

    /** Tests only: told at every [offer]. */
    internal var offerSpy: ((Context) -> Unit)? = null

    /** What is open now (one at a time, process wide). R19-FIX (CX-4): it is freed when its screen stops or goes to picture-in-picture, too. */
    internal val slot = AskSlot()

    /** The screen that came to the front last while a question was open (the next host to offer it to when its owner stops). */
    private var resumedHost: WeakReference<Activity>? = null

    /**
     * Shows the first waiting question over [ctx]'s screen, if [ctx] belongs to a full AnkiGo screen that is in front (an Activity that is
     * showing, not in picture-in-picture) and nothing is open yet. Safe to call as often as you like: it only ever shows what the saved
     * game says is waiting, and the answer removes it.
     */
    fun offer(ctx: Context) {
        offerSpy?.invoke(ctx)
        if (slot.isOpen) return
        val act = Ui.findActivity(ctx) ?: return                    // a floating window or a widget: it waits for a full screen
        if (act.isFinishing || act.isDestroyed || inPip(act)) return
        val decor = act.window?.decorView ?: return
        if (!decor.isAttachedToWindow || decor.windowVisibility != View.VISIBLE) return
        val data = GameRepo.data ?: return
        val state = GameRepo.state ?: return
        if (GameRepo.game == null) return
        val ask = AskMe.next(state) ?: return
        if (ask.kind == PendingAsk.Kind.REPLACE) ReplaceWindow.show(act, data, state, ask) else showCatch(act, data, state, ask)
    }

    private fun inPip(act: Activity) = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && act.isInPictureInPictureMode

    private fun showCatch(act: Activity, data: GameData, state: GameState, ask: PendingAsk) {
        val model = CatchAskPlan.of(data, state, ask)
        if (model == null) { // damaged: a question without its Pokémon can not be shown; drop it (a catch question holds no collection change)
            GameRepo.run(act) { it.answerCatch(ask.uid, AskChoice.DONT) }
            return
        }
        var answered = false
        lateinit var dlg: AppDialog
        fun answer(choice: AskChoice) {
            if (answered) return
            answered = true
            dlg.dismiss()
            val r = GameRepo.run(act) { it.answerCatch(ask.uid, choice) }
            if (r.ok) Ui.toast(act, r.message)
            act.window?.decorView?.post { offer(act) } // the next question, e.g. "Release the old one?" after "Catch and replace"
        }
        dlg = AppDialog.Builder(act).setTitle(model.title).setView(content(act, data, model) { answer(it) })
            .setOnCancelListener { answer(AskChoice.DONT) }.create()
        present(act, dlg)
    }

    /**
     * Shows [dlg] as THE open question of [owner]. It closes WITHOUT answering (the question stays saved) when the screen is destroyed,
     * stops (another screen came in front, the app went to the background) or goes to picture-in-picture, and it frees the slot whenever it
     * ends (R19-FIX CX-4: a screen kept alive behind another one used to hold the slot, so the front screen could not offer its question).
     * When the owner stops, the screen that came to the front meanwhile is offered the question.
     */
    internal fun present(owner: Activity, dlg: AppDialog) {
        fun end(event: OwnerEvent): Boolean = slot.onOwner(dlg, event, inPip(owner)).also { ended ->
            if (ended) resumedHost?.get()?.takeIf { it !== owner }?.let { h -> h.window?.decorView?.post { offer(h) } }
        }
        val cb = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityDestroyed(a: Activity) { if (a === owner) { if (!end(OwnerEvent.DESTROYED)) dlg.dismiss() } }
            override fun onActivityStopped(a: Activity) { if (a === owner) end(OwnerEvent.STOPPED) }
            override fun onActivityPaused(a: Activity) {
                // picture-in-picture: the mode may be set a moment after onPause, so look again once the pause is through
                if (a === owner && !end(OwnerEvent.PAUSED)) a.window?.decorView?.post { end(OwnerEvent.PAUSED) }
            }
            override fun onActivityResumed(a: Activity) { resumedHost = WeakReference(a) }
            override fun onActivityCreated(a: Activity, s: Bundle?) {}
            override fun onActivityStarted(a: Activity) {}
            override fun onActivitySaveInstanceState(a: Activity, s: Bundle) {}
        }
        slot.take(dlg) { dlg.dismiss() }
        owner.application.registerActivityLifecycleCallbacks(cb)
        // the slot is freed in the dialog's own dismiss listener; AppDialog keeps one, so wrap it through a tiny holder
        dlg.setOnDismissListener {
            owner.application.unregisterActivityLifecycleCallbacks(cb)
            slot.free(dlg)
        }
        Pickers.show(dlg)
    }

    // ---------------------------------------------------------------- the look (mockup 1C)

    /** The pop-up's inside, without the title (the dialog card has it): the two cards, "vs", three stacked buttons. */
    internal fun content(ctx: Context, data: GameData, m: CatchAskPlan.Model, onPick: (AskChoice) -> Unit): View {
        val cfg = ctx.resources.configuration
        val short = cfg.screenHeightDp < 520 // a split-screen half: the three buttons sit side by side (the mockup's small form), so all of it fits
        val small = short || cfg.screenWidthDp < 340 || cfg.screenHeightDp < 600
        val spriteDp = if (short) 48 else if (small) 60 else 96
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(ctx, 24), Ui.dp(ctx, 4), Ui.dp(ctx, 24), Ui.dp(ctx, 8))
        }
        val cmp = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        cmp.addView(sideCard(ctx, data, "Wild", m.wild, m.levelUp, m.ivsUp, spriteDp), LinearLayout.LayoutParams(0, -2, 1f))
        cmp.addView(Ui.text(ctx, 11f, 900, R.color.ag_sub0).apply { text = "vs"; gravity = Gravity.CENTER },
            LinearLayout.LayoutParams(-2, -2).also { it.marginStart = Ui.dp(ctx, 6); it.marginEnd = Ui.dp(ctx, 6) })
        cmp.addView(sideCard(ctx, data, "Yours", m.yours, false, false, spriteDp), LinearLayout.LayoutParams(0, -2, 1f))
        col.addView(cmp, LinearLayout.LayoutParams(-1, -2))
        val gap = Ui.dp(ctx, 6)
        val b1 = button(ctx, "Catch", "keep both", Style.PRIMARY, true, stacked = short) { onPick(AskChoice.CATCH) }
        val b2 = button(ctx, "Catch and replace", m.replaceSub, Style.SOFT, m.canReplace, stacked = short) { onPick(AskChoice.REPLACE) }
        val b3 = button(ctx, "Don't catch", "move on", Style.GREY, true, stacked = short) { onPick(AskChoice.DONT) }
        col.addView(b1, LinearLayout.LayoutParams(-1, -2).also { it.topMargin = Ui.dp(ctx, 12) })
        col.addView(b2, LinearLayout.LayoutParams(-1, -2).also { it.topMargin = gap })
        col.addView(b3, LinearLayout.LayoutParams(-1, -2).also { it.topMargin = gap })
        return col
    }

    /** One of the two cards: label, picture, name, level (green arrow when better), IV total (same). */
    private fun sideCard(ctx: Context, data: GameData, label: String, mon: Mon?, levelUp: Boolean, ivsUp: Boolean, spriteDp: Int): View {
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = Ui.rounded(Ui.c(ctx, R.color.ag_mantle), 14, ctx)
            setPadding(Ui.dp(ctx, 6), Ui.dp(ctx, 8), Ui.dp(ctx, 6), Ui.dp(ctx, 8))
            minimumHeight = Ui.dp(ctx, spriteDp + 70)
        }
        card.addView(Ui.text(ctx, 10.5f, 900, R.color.ag_sub0).apply { text = label.uppercase(); letterSpacing = 0.06f })
        if (mon == null) {
            card.addView(Ui.text(ctx, 13f, 800, R.color.ag_sub0).apply { text = "none now"; gravity = Gravity.CENTER },
                LinearLayout.LayoutParams(-1, 0, 1f).also { it.topMargin = Ui.dp(ctx, 6) })
            return card
        }
        card.contentDescription = describe(label, mon, levelUp, ivsUp)
        val img = ImageView(ctx).apply { scaleType = ImageView.ScaleType.FIT_CENTER; importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }
        MonPreview.show(ctx, mon.lookNum(data), mon.shiny, img)
        card.addView(img, LinearLayout.LayoutParams(Ui.dp(ctx, spriteDp), Ui.dp(ctx, spriteDp)).also { it.topMargin = Ui.dp(ctx, 2) })
        card.addView(Ui.text(ctx, 14f, 900, R.color.ag_text).apply {
            text = (if (mon.shiny) "★ " else "") + mon.name; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END; gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, -2).also { it.topMargin = Ui.dp(ctx, 2) })
        card.addView(valueRow(ctx, CatchAskPlan.levelText(mon), 15f, 900, levelUp), LinearLayout.LayoutParams(-2, -2).also { it.topMargin = Ui.dp(ctx, 3) })
        card.addView(valueRow(ctx, CatchAskPlan.ivText(mon), 12f, 700, ivsUp), LinearLayout.LayoutParams(-2, -2).also { it.topMargin = Ui.dp(ctx, 1) })
        return card
    }

    private fun valueRow(ctx: Context, text: String, sp: Float, weight: Int, up: Boolean): View = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(Ui.text(ctx, sp, weight, if (up) R.color.ag_green else if (weight >= 900) R.color.ag_text else R.color.ag_sub1).apply { this.text = text })
        if (up) addView(ImageView(ctx).apply {
            setImageResource(R.drawable.ic_up); setColorFilter(Ui.c(ctx, R.color.ag_green), PorterDuff.Mode.SRC_IN)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(Ui.dp(ctx, 12), Ui.dp(ctx, 12)).also { it.marginStart = Ui.dp(ctx, 2) })
    }

    private fun describe(label: String, mon: Mon, levelUp: Boolean, ivsUp: Boolean): String =
        "$label: ${mon.name}, level ${mon.level}" + (if (levelUp) ", higher" else "") + ", IV total ${mon.ivs.total} of 186" + (if (ivsUp) ", higher" else "")

    // ---------------------------------------------------------------- buttons (shared with the release window)

    internal enum class Style { PRIMARY, SOFT, GREY }

    /**
     * A 46 dp button: [big] bold, [small] beside it (or under it, [stacked], for the side-by-side form of a short window).
     * [enabled] false = faded and dead (the small text says why).
     */
    internal fun button(ctx: Context, big: String, small: String?, style: Style, enabled: Boolean, stacked: Boolean = false, onClick: () -> Unit): View {
        val mauve = Ui.c(ctx, R.color.ag_mauve)
        val s0 = Ui.c(ctx, R.color.ag_s0)
        val fill = when (style) {
            Style.PRIMARY -> mauve
            Style.SOFT -> Bits.blend(mauve, s0, 0.22f)
            Style.GREY -> s0
        }
        val ink = when (style) {
            Style.PRIMARY -> AppDialogRules.onFill(mauve, Ui.c(ctx, R.color.ag_crust), TypeInk::contrast)
            Style.SOFT -> mauve
            Style.GREY -> Ui.c(ctx, R.color.ag_sub1)
        }
        val ripple = RippleDrawable(ColorStateList.valueOf((ink and 0xFFFFFF) or (0x33 shl 24)), Ui.rounded(fill, 12, ctx), Ui.rounded(0xFF000000.toInt(), 12, ctx))
        return LinearLayout(ctx).apply {
            orientation = if (stacked) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            minimumHeight = Ui.dp(ctx, if (stacked) 48 else 46)
            setPadding(Ui.dp(ctx, if (stacked) 4 else 10), Ui.dp(ctx, 4), Ui.dp(ctx, if (stacked) 4 else 10), Ui.dp(ctx, 4))
            background = ripple
            isEnabled = enabled
            alpha = if (enabled) 1f else 0.45f
            isClickable = enabled
            contentDescription = if (small != null) "$big, $small" else big
            if (enabled) setOnClickListener { onClick() }
            addView(TextView(ctx).apply {
                typeface = Ui.font(ctx, 900); setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, if (stacked) 12.5f else 14f); setTextColor(ink)
                includeFontPadding = false; text = big; maxLines = if (stacked) 2 else 1; gravity = Gravity.CENTER
            })
            if (small != null) addView(TextView(ctx).apply {
                typeface = Ui.font(ctx, 800); setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, if (stacked) 10f else 11.5f); setTextColor(ink)
                includeFontPadding = false; text = small; maxLines = if (stacked) 2 else 1; alpha = 0.85f; gravity = Gravity.CENTER
                ellipsize = android.text.TextUtils.TruncateAt.END
            }, LinearLayout.LayoutParams(-2, -2).also { if (stacked) it.topMargin = Ui.dp(ctx, 1) else it.marginStart = Ui.dp(ctx, 8) })
        }
    }
}

/**
 * What the catch pop-up shows, from the saved game (pure: no views). [yours] = the copy it is compared with, which is also the one
 * "Catch and replace" would release ([AskMe.better]); null = no copy of the species is owned any more.
 */
object CatchAskPlan {
    class Model(
        val title: String,
        val wild: Mon,
        val yours: Mon?,
        val levelUp: Boolean,
        val ivsUp: Boolean,
        /** "Catch and replace" works: the wild one is still better than [yours], and [yours] may be released (not shiny, not a protected kind). */
        val canReplace: Boolean,
        /** The small words on the "Catch and replace" button; with [canReplace] false they say why. */
        val replaceSub: String,
    )

    /** Null for a question without its wild Pokémon (a damaged save). */
    fun of(data: GameData, state: GameState, ask: PendingAsk): Model? {
        val wild = ask.wild ?: return null
        val better = AskMe.better(state, wild)
        val yours = better?.yours ?: state.mons.filter { it.species == wild.species }.maxWithOrNull(compareBy<Mon>({ it.level }, { it.ivs.total }))
        val levelUp = yours != null && wild.level > yours.level
        val ivsUp = yours != null && wild.ivs.total > yours.ivs.total
        val allowed = better != null && yours != null && AskMe.canReplace(data, state, yours)
        val sub = when {
            yours == null -> "nothing to replace"
            better == null -> "yours is better now"
            yours.shiny -> "your shiny stays"
            !allowed -> "a protected one stays"
            else -> "release your Lv ${yours.level} one"
        }
        return Model("A better ${wild.name}!", wild, yours, levelUp, ivsUp, allowed, sub)
    }

    fun levelText(mon: Mon): String = "Lv ${mon.level}"

    /** "IVs 160 / 186": the six IVs added up. */
    fun ivText(mon: Mon): String = "IVs ${DetailFormat.ivTotal(mon.ivs)}"
}

/** What happened to the screen that owns an open question (R19-FIX CX-4). */
internal enum class OwnerEvent { PAUSED, STOPPED, DESTROYED }

/**
 * R19-FIX (CX-4): the process-wide "one question at a time" slot of [CatchAskPopup], without any Android class so a test can drive it.
 * [take] fills it with the [token] of the open dialog and the way to [close] that dialog; [free] empties it when that dialog ended on its own;
 * [onOwner] ends the question when its screen goes away: it stops or is destroyed, or it is paused INTO picture-in-picture (a plain pause,
 * e.g. a system dialog in front, keeps it). Ending closes the dialog without answering; the question stays saved and is offered again.
 */
internal class AskSlot {
    private var token: Any? = null
    private var close: (() -> Unit)? = null

    val isOpen: Boolean get() = token != null

    fun take(token: Any, close: () -> Unit) { this.token = token; this.close = close }

    /** The dialog [token] ended (answered, cancelled, dismissed): the slot is free, unless another dialog took it meanwhile. */
    fun free(token: Any) { if (this.token === token) { this.token = null; close = null } }

    /** The owner of [token] had [event] ([inPip]: it is in picture-in-picture mode). True when that ended the open question. */
    fun onOwner(token: Any, event: OwnerEvent, inPip: Boolean): Boolean {
        if (this.token !== token) return false
        if (event == OwnerEvent.PAUSED && !inPip) return false
        val c = close; this.token = null; close = null
        c?.invoke()
        return true
    }
}
