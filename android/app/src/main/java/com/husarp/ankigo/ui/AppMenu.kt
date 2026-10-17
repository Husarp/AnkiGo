package com.husarp.ankigo.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.PorterDuff
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import com.husarp.ankigo.R

/**
 * The app's own ⋯ menu: a rounded `ag_card` popup with Nunito rows and tinted icons, anchored under the tapped view
 * (right-aligned; [MenuPlacement] flips it above, slides it sideways and scrolls it so it never leaves the screen). Works in an Activity and in an overlay window
 * (floating battle / cards), because a PopupWindow hangs on the anchor's window.
 * Use: `AppMenu(anchor).header("Title").item(1, "Team", R.drawable.ic_team).show { id -> … }`.
 */
class AppMenu(private val anchor: View) {
    private class Row(val id: Int, val label: String, @DrawableRes val icon: Int, val enabled: Boolean, val sub: String?, val danger: Boolean, val dividerAfter: Boolean = false, @ColorRes val tone: Int = 0)

    private val rows = ArrayList<Row>()
    private var title: String? = null
    private var titleSub: String? = null
    private var window: PopupWindow? = null

    /** Optional title (and a small line under it) above the rows. */
    fun header(title: String, sub: String? = null): AppMenu { this.title = title; titleSub = sub; return this }

    /** One row. A disabled row is dimmed and not clickable (its [sub] line stays readable); [danger] tints it red; [tone] (a colour res) tints only the icon. */
    fun item(id: Int, label: String, @DrawableRes icon: Int, enabled: Boolean = true, sub: String? = null, danger: Boolean = false, dividerAfter: Boolean = false, @ColorRes tone: Int = 0): AppMenu {
        rows.add(Row(id, label, icon, enabled, sub, danger, dividerAfter, tone))
        return this
    }

    val isShowing: Boolean get() = window?.isShowing == true

    fun dismiss() { window?.dismiss(); window = null }

    /** Shows the menu; [onPick] gets the id of the tapped row (the menu closes first). Tests set [intercept] to take it instead. */
    fun show(onPick: (Int) -> Unit) {
        picked = onPick
        intercept?.let { it(this); return }
        val ctx = anchor.context
        val card = content(ctx)
        // Everything in the anchor window's coordinates; the visible display frame (screen minus system bars, or the split / PiP
        // window) is the box the menu must stay inside.
        val frame = android.graphics.Rect().also { anchor.getWindowVisibleDisplayFrame(it) }
        val onScreen = IntArray(2).also { anchor.getLocationOnScreen(it) }
        val inWindow = IntArray(2).also { anchor.getLocationInWindow(it) }
        val dx = onScreen[0] - inWindow[0]
        val dy = onScreen[1] - inWindow[1]
        val bounds = MenuPlacement.Box(frame.left - dx, frame.top - dy, frame.right - dx, frame.bottom - dy)
        val maxW = minOf(Ui.dp(ctx, MAX_DP + 2 * PAD_DP), bounds.width)
        card.measure(View.MeasureSpec.makeMeasureSpec(maxW, View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        val pad = Ui.dp(ctx, PAD_DP)
        val spot = MenuPlacement.place(
            MenuPlacement.Box(inWindow[0], inWindow[1], inWindow[0] + anchor.width, inWindow[1] + anchor.height),
            card.measuredWidth, card.measuredHeight, bounds, pad, Ui.dp(ctx, MIN_ROOM_DP),
        )
        val pw = PopupWindow(card, spot.width, spot.height, true)
        pw.setBackgroundDrawable(ColorDrawable(0))
        pw.isOutsideTouchable = true
        pw.setIsClippedToScreen(true)
        pw.setOnDismissListener { if (window === pw) window = null }
        window = pw
        pw.showAtLocation(anchor, Gravity.TOP or Gravity.START, spot.x, spot.y)
    }

    private var picked: (Int) -> Unit = {}

    /** Called by a row: closes the menu, then reports the id. Tests call it through the intercepted menu. */
    internal fun pick(id: Int) { dismiss(); picked(id) }

    /** The rows as shown (tests read labels, enabled state and subs from here). */
    internal fun describe(): List<Triple<Int, String, Boolean>> = rows.map { Triple(it.id, it.label, it.enabled) }
    internal fun subOf(id: Int): String? = rows.firstOrNull { it.id == id }?.sub

    /** The menu's view: the card inside a transparent frame that leaves room for its shadow. Screenshots render this. */
    internal fun content(ctx: Context): View {
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            minimumWidth = Ui.dp(ctx, MIN_DP)
            background = GradientDrawable().apply {
                setColor(Ui.c(ctx, R.color.ag_card))
                cornerRadius = Ui.dp(ctx, 14f)
                setStroke(Ui.dp(ctx, 1), Ui.c(ctx, R.color.ag_s0))
            }
            elevation = Ui.dp(ctx, 8f)
            setPadding(0, Ui.dp(ctx, 6), 0, Ui.dp(ctx, 6))
        }
        title?.let { t ->
            card.addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(Ui.dp(ctx, 16), Ui.dp(ctx, 8), Ui.dp(ctx, 16), Ui.dp(ctx, 8))
                addView(Ui.text(ctx, 14f, 900, R.color.ag_text).apply { text = t; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
                titleSub?.let { s -> addView(Ui.text(ctx, 11f, 700, R.color.ag_sub0).apply { text = s }) }
            })
            card.addView(View(ctx).apply { setBackgroundColor(Ui.c(ctx, R.color.ag_s0)) }, LinearLayout.LayoutParams(-1, Ui.dp(ctx, 1)).also { it.topMargin = Ui.dp(ctx, 2); it.bottomMargin = Ui.dp(ctx, 4) })
        }
        for (r in rows) {
            card.addView(rowView(ctx, r), LinearLayout.LayoutParams(-1, -2))
            if (r.dividerAfter) card.addView(View(ctx).apply { setBackgroundColor(Ui.c(ctx, R.color.ag_s0)) }, LinearLayout.LayoutParams(-1, Ui.dp(ctx, 1)).also { it.topMargin = Ui.dp(ctx, 2); it.bottomMargin = Ui.dp(ctx, 4) })
        }
        val scroll = ScrollView(ctx).apply { isVerticalScrollBarEnabled = false; addView(card, FrameLayout.LayoutParams(-2, -2)) }
        val pad = Ui.dp(ctx, PAD_DP)
        // card shadow needs space around it in the frame
        val cap = Ui.dp(ctx, MAX_DP + 2 * PAD_DP)
        return object : FrameLayout(ctx) {
            /** The card is at most 300 dp wide (long labels wrap), whoever measures it. */
            override fun onMeasure(w: Int, h: Int) {
                val size = MeasureSpec.getSize(w)
                super.onMeasure(if (MeasureSpec.getMode(w) == MeasureSpec.UNSPECIFIED || size > cap) MeasureSpec.makeMeasureSpec(cap, MeasureSpec.AT_MOST) else w, h)
            }
        }.apply {
            clipChildren = false; clipToPadding = false
            setPadding(pad, pad, pad, pad)
            addView(scroll, FrameLayout.LayoutParams(-2, -2))
        }.also { scroll.clipChildren = false; scroll.clipToPadding = false }
    }

    private fun rowView(ctx: Context, r: Row): View {
        val tint = Ui.c(ctx, if (r.danger) R.color.ag_red else if (r.tone != 0) r.tone else R.color.ag_sub1)
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = Ui.dp(ctx, 48)
            setPadding(Ui.dp(ctx, 16), Ui.dp(ctx, 6), Ui.dp(ctx, 16), Ui.dp(ctx, 6))
            background = RippleDrawable(ColorStateList.valueOf(Ui.c(ctx, R.color.ag_s1) and 0x00FFFFFF or 0x55000000), null, ColorDrawable(0xFF000000.toInt()))
            addView(ImageView(ctx).apply {
                setImageResource(r.icon)
                setColorFilter(tint, PorterDuff.Mode.SRC_IN)
            }, LinearLayout.LayoutParams(Ui.dp(ctx, 20), Ui.dp(ctx, 20)).also { it.marginEnd = Ui.dp(ctx, 12) })
            addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                addView(Ui.text(ctx, 14f, 800, if (r.danger) R.color.ag_red else R.color.ag_text).apply { text = r.label })
                r.sub?.let { s -> addView(Ui.text(ctx, 11f, 700, R.color.ag_sub0).apply { text = s }) }
            }, LinearLayout.LayoutParams(0, -2, 1f))
            if (r.enabled) {
                setOnClickListener { pick(r.id) }
            } else {
                isClickable = false
                // dim icon and label, not the reason line
                getChildAt(0).alpha = 0.4f
                ((getChildAt(1) as LinearLayout).getChildAt(0) as TextView).alpha = 0.4f
            }
        }
    }

    companion object {
        private const val MIN_DP = 200
        private const val MAX_DP = 300
        private const val PAD_DP = 10
        private const val MIN_ROOM_DP = 140

        /** Screenshot / unit tests set this to take a menu instead of showing it (a renderer has no window for it). Null in the app. */
        internal var intercept: ((AppMenu) -> Unit)? = null
    }
}
