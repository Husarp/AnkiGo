package com.husarp.ankigo.ui

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupWindow
import com.husarp.ankigo.R
import com.husarp.ankigo.game.Move
import com.husarp.ankigo.game.MoveText

/**
 * R10-F (B10): the small transient card a tap on a move in Compare opens next to it (app style: the card of [AppMenu], not a toast or a
 * dialog). Name, type chip, category with its kind icon, Power / Accuracy (only what the move has) and the plain-words effect. It closes
 * after [SHOW_MS], on a tap anywhere (also on the card) and when the table scrolls; only one is open at a time.
 */
object CompareMovePopup {
    const val SHOW_MS = 4000L
    private const val MAX_DP = 270
    private const val PAD_DP = 10

    private var window: PopupWindow? = null
    private var shown = false

    /** What the open popup shows (tests read it; null when none). */
    internal var content: View? = null
        private set

    /** Tests set this to take the popup instead of opening a window (a renderer has no window for it). Null in the app. */
    internal var intercept: ((View) -> Unit)? = null

    /** The timer behind the auto-close; tests replace it to fire the close by hand. */
    internal interface Timer { fun after(ms: Long, r: Runnable); fun cancel(r: Runnable) }
    private object MainTimer : Timer {
        private val handler by lazy { Handler(Looper.getMainLooper()) }
        override fun after(ms: Long, r: Runnable) { handler.postDelayed(r, ms) }
        override fun cancel(r: Runnable) { handler.removeCallbacks(r) }
    }
    internal var timer: Timer = MainTimer

    private val autoClose = Runnable { dismiss() }

    /** R10-FIX (Codex 3): the anchor the open popup hangs on and the listener that closes it when that screen's view goes away. */
    private var anchorView: View? = null
    internal var detachListener: View.OnAttachStateChangeListener? = null
        private set

    val isShowing: Boolean get() = shown

    fun dismiss() {
        timer.cancel(autoClose)
        anchorView?.let { a -> detachListener?.let { a.removeOnAttachStateChangeListener(it) } }
        anchorView = null
        detachListener = null
        val w = window
        window = null
        shown = false
        content = null
        w?.dismiss()
    }

    /** Opens the popup of [move] next to [anchor] (replacing the one that is open). */
    fun show(anchor: View, move: Move) {
        dismiss()
        val ctx = anchor.context
        val card = content(ctx, move)
        shown = true
        content = card
        timer.after(SHOW_MS, autoClose)
        // the screen (rotation, back) takes the popup with it: no timer and no static reference to a destroyed screen outlives it
        val l = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {}
            override fun onViewDetachedFromWindow(v: View) { dismiss() }
        }
        anchor.addOnAttachStateChangeListener(l)
        anchorView = anchor
        detachListener = l
        intercept?.let { it(card); return }
        val frame = android.graphics.Rect().also { anchor.getWindowVisibleDisplayFrame(it) }
        val onScreen = IntArray(2).also { anchor.getLocationOnScreen(it) }
        val inWindow = IntArray(2).also { anchor.getLocationInWindow(it) }
        val dx = onScreen[0] - inWindow[0]
        val dy = onScreen[1] - inWindow[1]
        val bounds = MenuPlacement.Box(frame.left - dx, frame.top - dy, frame.right - dx, frame.bottom - dy)
        val pad = Ui.dp(ctx, PAD_DP)
        card.measure(View.MeasureSpec.makeMeasureSpec(minOf(Ui.dp(ctx, MAX_DP + 2 * PAD_DP), bounds.width), View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        val spot = MenuPlacement.place(
            MenuPlacement.Box(inWindow[0], inWindow[1], inWindow[0] + anchor.width, inWindow[1] + anchor.height),
            card.measuredWidth, card.measuredHeight, bounds, pad, Ui.dp(ctx, 100),
        )
        val pw = PopupWindow(card, spot.width, spot.height, true) // focusable: a tap anywhere outside closes it (and does nothing else)
        pw.setBackgroundDrawable(ColorDrawable(0))
        pw.isOutsideTouchable = true
        pw.setIsClippedToScreen(true)
        pw.setOnDismissListener { if (window === pw) dismiss() }
        window = pw
        if (!still(ctx)) { card.alpha = 0f; card.animate().alpha(1f).setDuration(AppToastRules.FADE_MS).start() } // reduced motion: no fade
        pw.showAtLocation(anchor, Gravity.TOP or Gravity.START, spot.x, spot.y)
    }

    private fun still(ctx: Context): Boolean = AppToastRules.still(
        android.animation.ValueAnimator.areAnimatorsEnabled(), ctx.getSharedPreferences("study", 0).getBoolean("reduceMotion", false))

    /** What TalkBack (and a tap on the move) says: the whole card in words. */
    internal fun said(m: Move): String {
        val power = MoveText.showsPower(m)
        return listOfNotNull(m.name, m.type.label, DetailSections.kindName(m.category), if (power) "power ${m.power}" else null,
            m.accuracy?.let { "accuracy $it" }).joinToString(", ")
    }

    /** The popup's card inside a transparent frame that leaves room for its shadow. Screenshots render this. */
    internal fun content(ctx: Context, m: Move): View {
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Ui.c(ctx, R.color.ag_card))
                cornerRadius = Ui.dp(ctx, 14f)
                setStroke(Ui.dp(ctx, 1), Ui.c(ctx, R.color.ag_s0))
            }
            elevation = Ui.dp(ctx, 8f)
            setPadding(Ui.dp(ctx, 14), Ui.dp(ctx, 12), Ui.dp(ctx, 14), Ui.dp(ctx, 12))
        }
        card.addView(Ui.text(ctx, 15f, 900, R.color.ag_text).apply { text = m.name; maxLines = 1; ellipsize = TextUtils.TruncateAt.END })
        card.addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(Ui.typeChip(ctx, m.type), LinearLayout.LayoutParams(-2, -2))
            addView(DetailSections.kindTile(ctx, m.category, 20), LinearLayout.LayoutParams(Ui.dp(ctx, 20), Ui.dp(ctx, 20)).also { it.marginStart = Ui.dp(ctx, 8) })
            addView(Ui.text(ctx, 11.5f, 800, R.color.ag_sub1).apply { text = DetailSections.kindName(m.category); maxLines = 1 },
                LinearLayout.LayoutParams(-2, -2).also { it.marginStart = Ui.dp(ctx, 5) })
        }, LinearLayout.LayoutParams(-2, -2).also { it.topMargin = Ui.dp(ctx, 5) })
        fun num(v: String, label: String) = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            addView(Ui.text(ctx, 14f, 900, R.color.ag_text).apply { text = v })
            addView(Ui.text(ctx, 10f, 700, R.color.ag_sub0).apply { text = label })
        }
        val power = MoveText.showsPower(m)
        if (power || m.accuracy != null) {
            card.addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                if (power) addView(num(m.power.toString(), "Power"), LinearLayout.LayoutParams(-2, -2).also { it.marginEnd = Ui.dp(ctx, 22) })
                m.accuracy?.let { addView(num(it.toString(), "Accuracy"), LinearLayout.LayoutParams(-2, -2)) }
            }, LinearLayout.LayoutParams(-2, -2).also { it.topMargin = Ui.dp(ctx, 8) })
        }
        val effect = MoveText.describe(m)
        if (effect.isNotBlank()) {
            card.addView(Ui.text(ctx, 12f, 700, R.color.ag_sub1).apply {
                text = effect
                maxLines = 3
                ellipsize = TextUtils.TruncateAt.END
            }, LinearLayout.LayoutParams(-2, -2).also { it.topMargin = Ui.dp(ctx, 8) })
        }
        val pad = Ui.dp(ctx, PAD_DP)
        val cap = Ui.dp(ctx, MAX_DP + 2 * PAD_DP)
        return object : FrameLayout(ctx) {
            override fun onMeasure(w: Int, h: Int) {
                val size = MeasureSpec.getSize(w)
                super.onMeasure(if (MeasureSpec.getMode(w) == MeasureSpec.UNSPECIFIED || size > cap) MeasureSpec.makeMeasureSpec(cap, MeasureSpec.AT_MOST) else w, h)
            }
        }.apply {
            clipChildren = false; clipToPadding = false
            setPadding(pad, pad, pad, pad)
            addView(card, FrameLayout.LayoutParams(-2, -2))
            contentDescription = said(m) + ". " + effect
            isClickable = true
            setOnClickListener { dismiss() }
        }
    }
}
