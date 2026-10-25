package com.husarp.ankigo.ui

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.graphics.PorterDuff
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.R
import com.husarp.ankigo.game.AskMe
import com.husarp.ankigo.game.GameData
import com.husarp.ankigo.game.GameState
import com.husarp.ankigo.game.Mon
import com.husarp.ankigo.game.PendingAsk
import com.husarp.ankigo.game.lookNum
import com.husarp.ankigo.game.shownName

/**
 * R19-P (PLAN 281, pick 2B): "Release the old one?". Shown when a better copy was caught and the copy it beat holds a place: a Team
 * slot, the favourite heart or the Exp. Share ("Catch and replace" in the Ask me pop-up, or the "better copies" catch option). Old and new
 * side by side; while "Keep its place (Team, favourite, Exp. Share)" is ticked (it is, to begin with) the marks of the old one hop across
 * to the new one, and the new one takes the place when the old one goes. "Release old one" lets the old one go (its held item goes to the
 * Bag); "Keep both", Back or a tap outside keeps both and releases nothing.
 */
object ReplaceWindow {
    /**
     * Opens the window for the release question [ask] over [act]; a stale question (a Pokémon is gone or changed, see [ReplacePlan.of]) is
     * closed as "keep both" with one short line ([AskMe.STALE]; R19-FIX CX-1) and the next question in line is offered.
     */
    internal fun show(act: Activity, data: GameData, state: GameState, ask: PendingAsk) {
        val model = ReplacePlan.of(data, state, ask)
        if (model == null) {
            GameRepo.run(act) { it.answerReplace(ask.uid, release = false, keepPlace = false) }
            Ui.toast(act, AskMe.STALE)
            act.window?.decorView?.post { CatchAskPopup.offer(act) }
            return
        }
        var answered = false
        var keep = true
        lateinit var dlg: AppDialog
        fun answer(release: Boolean) {
            if (answered) return
            answered = true
            dlg.dismiss()
            val r = GameRepo.run(act) { it.answerReplace(ask.uid, release, keep) }
            if (r.ok) Ui.toast(act, r.message) else if (release) Ui.toast(act, r.message)
            act.window?.decorView?.post { CatchAskPopup.offer(act) } // the next question in line, if any
        }
        val body = content(act, data, model, onKeepChanged = { keep = it }, onPick = { answer(it) })
        dlg = AppDialog.Builder(act).setTitle("Release the old one?").setView(body).setOnCancelListener { answer(false) }.create()
        CatchAskPopup.present(act, dlg)
    }

    /**
     * The window's inside: the sentence, old -> new with the marks, the tick box, the two buttons. [onKeepChanged] gets the tick box;
     * [onPick] gets true for "Release old one". The tick box starts ticked.
     */
    internal fun content(ctx: Context, data: GameData, m: ReplacePlan.Model, keepStart: Boolean = true,
                         onKeepChanged: (Boolean) -> Unit = {}, onPick: (Boolean) -> Unit): View {
        val cfg = ctx.resources.configuration
        val short = cfg.screenHeightDp < 520 // a split-screen half: the two buttons sit side by side and the pictures shrink
        val small = short || cfg.screenWidthDp < 340 || cfg.screenHeightDp < 600
        val spriteDp = if (short) 34 else if (small) 52 else 80
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(ctx, 24), Ui.dp(ctx, 0), Ui.dp(ctx, 24), Ui.dp(ctx, 8))
        }
        col.addView(Ui.text(ctx, 13.5f, 800, R.color.ag_text).apply { text = m.sentence; setLineSpacing(0f, 1.15f) },
            LinearLayout.LayoutParams(-1, -2).also { it.bottomMargin = Ui.dp(ctx, 10) })

        val oldMarks = marks(ctx, m)
        val newMarks = marks(ctx, m)
        val oldCaption = Ui.text(ctx, 11f, 700, R.color.ag_sub0).apply { gravity = Gravity.CENTER }
        val newCaption = Ui.text(ctx, 11f, 700, R.color.ag_sub0).apply { gravity = Gravity.CENTER }
        val newCard = card(ctx, data, m.new, spriteDp, newMarks, newCaption, outlined = true)
        fun render(keep: Boolean, animate: Boolean) {
            // ticked: the old one's marks fade (they leave with it) and a dashed frame on the new one shows where they land
            oldMarks.alpha = if (keep) 0.45f else 1f
            oldCaption.text = if (keep) "goes" else "goes, with its marks"
            newMarks.visibility = if (keep) View.VISIBLE else View.INVISIBLE
            newMarks.background = if (keep) dashed(ctx) else null
            newCaption.text = if (keep) "takes its place" else "stays as it is"
            if (keep && animate && ValueAnimator.areAnimatorsEnabled()) { // the marks hop across: from the old card's side, 220 ms
                newMarks.translationX = -Ui.dp(ctx, 28).toFloat(); newMarks.alpha = 0f
                newMarks.animate().translationX(0f).alpha(1f).setDuration(220).start()
            } else { newMarks.translationX = 0f; newMarks.alpha = 1f }
        }
        val swap = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        swap.addView(card(ctx, data, m.old, spriteDp, oldMarks, oldCaption, outlined = false), LinearLayout.LayoutParams(0, -2, 1f))
        swap.addView(ImageView(ctx).apply {
            setImageResource(R.drawable.ic_chevron); setColorFilter(Ui.c(ctx, R.color.ag_mauve), PorterDuff.Mode.SRC_IN)
            contentDescription = "becomes"
        }, LinearLayout.LayoutParams(Ui.dp(ctx, 28), Ui.dp(ctx, 28)).also { it.marginStart = Ui.dp(ctx, 2); it.marginEnd = Ui.dp(ctx, 2) })
        swap.addView(newCard, LinearLayout.LayoutParams(0, -2, 1f))
        col.addView(swap, LinearLayout.LayoutParams(-1, -2))

        var keep = keepStart
        render(keep, animate = false)
        col.addView(checkRow(ctx, keep) { on -> keep = on; render(on, animate = true); onKeepChanged(on) },
            LinearLayout.LayoutParams(-1, -2).also { it.topMargin = Ui.dp(ctx, 6) })
        val release = CatchAskPopup.button(ctx, "Release old one", null, CatchAskPopup.Style.PRIMARY, true) { onPick(true) }
        val both = CatchAskPopup.button(ctx, "Keep both", null, CatchAskPopup.Style.GREY, true) { onPick(false) }
        if (short) {
            col.addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(release, LinearLayout.LayoutParams(0, -1, 1f))
                addView(both, LinearLayout.LayoutParams(0, -1, 1f).also { it.marginStart = Ui.dp(ctx, 6) })
            }, LinearLayout.LayoutParams(-1, -2).also { it.topMargin = Ui.dp(ctx, 2) })
        } else {
            col.addView(release, LinearLayout.LayoutParams(-1, -2).also { it.topMargin = Ui.dp(ctx, 6) })
            col.addView(both, LinearLayout.LayoutParams(-1, -2).also { it.topMargin = Ui.dp(ctx, 6) })
        }
        onKeepChanged(keep)
        return col
    }

    private fun dashed(ctx: Context): GradientDrawable = GradientDrawable().apply {
        setColor(0)
        cornerRadius = Ui.dp(ctx, 9f)
        setStroke(Ui.dp(ctx, 1), Ui.c(ctx, R.color.ag_mauve), Ui.dp(ctx, 3).toFloat(), Ui.dp(ctx, 2).toFloat())
    }

    /** One Pokémon: picture, level, the marks row, a caption. The new one has a mauve outline. */
    private fun card(ctx: Context, data: GameData, mon: Mon, spriteDp: Int, marks: View, caption: View, outlined: Boolean): View =
        LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = Ui.rounded(Ui.c(ctx, R.color.ag_mantle), 14, ctx).apply { if (outlined) setStroke(Ui.dp(ctx, 2), Ui.c(ctx, R.color.ag_mauve)) }
            setPadding(Ui.dp(ctx, 4), Ui.dp(ctx, 8), Ui.dp(ctx, 4), Ui.dp(ctx, 8))
            contentDescription = "${mon.shownName}, level ${mon.level}"
            val img = ImageView(ctx).apply { scaleType = ImageView.ScaleType.FIT_CENTER; importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }
            MonPreview.show(ctx, mon.lookNum(data), mon.shiny, img)
            addView(img, LinearLayout.LayoutParams(Ui.dp(ctx, spriteDp), Ui.dp(ctx, spriteDp)))
            addView(Ui.text(ctx, 14f, 900, R.color.ag_text).apply { text = "Lv ${mon.level}"; gravity = Gravity.CENTER },
                LinearLayout.LayoutParams(-1, -2).also { it.topMargin = Ui.dp(ctx, 2) })
            addView(marks, LinearLayout.LayoutParams(-2, Ui.dp(ctx, 22)).also { it.topMargin = Ui.dp(ctx, 4) })
            addView(caption, LinearLayout.LayoutParams(-1, -2).also { it.topMargin = Ui.dp(ctx, 2) })
        }

    /** The old one's marks as small coloured icons: the Team (blue), the favourite heart (red), the Exp. Share (teal). */
    private fun marks(ctx: Context, m: ReplacePlan.Model): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        setPadding(Ui.dp(ctx, 6), Ui.dp(ctx, 2), Ui.dp(ctx, 6), Ui.dp(ctx, 2))
        fun add(icon: Int, tone: Int, desc: String) = addView(ImageView(ctx).apply {
            setImageResource(icon); setColorFilter(Ui.c(ctx, tone), PorterDuff.Mode.SRC_IN); contentDescription = desc
        }, LinearLayout.LayoutParams(Ui.dp(ctx, 16), Ui.dp(ctx, 16)).also { it.marginStart = Ui.dp(ctx, 2); it.marginEnd = Ui.dp(ctx, 2) })
        if (m.place.inTeam) add(R.drawable.ic_team, R.color.ag_blue, if (m.place.teamSlot == 0) "In your team, the main" else "In your team, place ${m.place.teamSlot + 1}")
        if (m.place.favourite) add(R.drawable.ic_heart, R.color.ag_red, "A favourite")
        if (m.place.expShare) add(R.drawable.ic_exp_share, R.color.ag_teal, "Holds the Exp. Share")
    }

    /** The tick box row (48 dp touch height): the box on the left, the words beside it. */
    private fun checkRow(ctx: Context, startOn: Boolean, onChange: (Boolean) -> Unit): View {
        var on = startOn
        val box = ImageView(ctx)
        fun draw() {
            box.setImageResource(if (on) R.drawable.ic_checkbox_on else R.drawable.ic_checkbox_off)
            box.setColorFilter(Ui.c(ctx, if (on) R.color.ag_mauve else R.color.ag_ov), PorterDuff.Mode.SRC_IN)
        }
        draw()
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = Ui.dp(ctx, 48)
            isClickable = true
            contentDescription = "Keep its place (Team, favourite, Exp. Share)"
            addView(box, LinearLayout.LayoutParams(Ui.dp(ctx, 24), Ui.dp(ctx, 24)))
            addView(Ui.text(ctx, 13.5f, 800, R.color.ag_text).apply { text = "Keep its place (Team, favourite, Exp. Share)"; setLineSpacing(0f, 1.1f) },
                LinearLayout.LayoutParams(0, -2, 1f).also { it.marginStart = Ui.dp(ctx, 10) })
            setOnClickListener { on = !on; draw(); onChange(on) }
        }
    }
}

/** What the release window shows (pure: no views): the better copy, the old one with the place it holds, the sentence. */
object ReplacePlan {
    class Model(val new: Mon, val old: Mon, val place: AskMe.Place, val sentence: String)

    /**
     * Null when the question is stale: one of the two Pokémon is gone, or they are not the pair that was asked about any more (the old one
     * may not go: shiny / protected kind; evolved to another species; the new one is not better than it any more: [AskMe.stillReplaces],
     * R19-FIX CX-1). The caller then closes it as "keep both". [AskMe.Place] is read now, from the saved game.
     */
    fun of(data: GameData, state: GameState, ask: PendingAsk): Model? {
        val new = state.mon(ask.uid) ?: return null
        val old = ask.oldUid?.let { state.mon(it) } ?: return null
        if (!AskMe.stillReplaces(data, state, new, old)) return null
        return Model(new, old, AskMe.placeOf(state, old), sentence(new, old))
    }

    /** "You caught a better Bulbasaur (Lv 34). Release the old one (Lv 18)?" */
    fun sentence(new: Mon, old: Mon): String = "You caught a better ${new.name} (Lv ${new.level}). Release the old one (Lv ${old.level})?"
}
