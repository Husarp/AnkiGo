package com.husarp.ankigo.ui

import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.husarp.ankigo.R

/**
 * The app's own dialog (replaces the Android-looking AlertDialog): a rounded `ag_card` with Nunito title / message,
 * 48 dp list rows and pill buttons, light and dark. Built with [Builder], which has the subset of `AlertDialog.Builder`
 * the app uses (same listener shapes, so a call site only changes the class name), and shown with [Pickers.show].
 *
 * Layout: title, then a scrolling part (message, custom view, list rows; a custom view that scrolls by itself, such as a ListView or a
 * ScrollView, goes straight into the card with the remaining height, the message above it staying put), then the buttons in one row at the end (they wrap
 * to a second row on a narrow screen). A custom view ([Builder.setView]) gets the full card width, like in AlertDialog:
 * give it its own side padding (24 dp lines it up with the title). A button tap runs its listener, then closes the dialog;
 * so does a tap on a list row.
 */
class AppDialog internal constructor(val context: Context) : DialogInterface {
    private var title: CharSequence? = null
    private var message: CharSequence? = null
    private var customView: View? = null
    private var items: Array<out CharSequence>? = null
    private var itemListener: DialogInterface.OnClickListener? = null
    private var cancelable = true
    private var dismissListener: DialogInterface.OnDismissListener? = null
    private var cancelListener: DialogInterface.OnCancelListener? = null
    private class Btn(val text: CharSequence, val listener: DialogInterface.OnClickListener?)
    private val buttons = LinkedHashMap<Int, Btn>()
    private val buttonViews = HashMap<Int, TextView>()
    private var built: View? = null

    /** The platform dialog, made on first use (the content alone can be built and shot without a window). */
    private var made: Dialog? = null
    private val dlg: Dialog get() = made ?: makeDialog().also { made = it }

    private fun makeDialog(): Dialog =
        Dialog(context).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(content())
            window?.apply {
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                setDimAmount(0.48f)
                val wDp = AppDialogRules.widthDp(this@AppDialog.context.resources.configuration.screenWidthDp)
                setLayout(Ui.dp(this@AppDialog.context, wDp), ViewGroup.LayoutParams.WRAP_CONTENT)
            }
            setCancelable(cancelable)
            setCanceledOnTouchOutside(cancelable)
            setOnDismissListener { dismissListener?.onDismiss(this@AppDialog) }
            setOnCancelListener { cancelListener?.onCancel(this@AppDialog) }
        }

    val window: Window? get() = dlg.window
    val isShowing: Boolean get() = made?.isShowing == true

    fun show() = dlg.show()
    override fun dismiss() { made?.dismiss() }
    override fun cancel() { made?.cancel() }

    fun setOnDismissListener(l: DialogInterface.OnDismissListener?) { dismissListener = l }
    fun setOnCancelListener(l: DialogInterface.OnCancelListener?) { cancelListener = l }

    /** The button view of [which] (`BUTTON_POSITIVE` / `BUTTON_NEGATIVE` / `BUTTON_NEUTRAL`), or null when the dialog has none; for enabling / disabling it. */
    fun button(which: Int): TextView? { content(); return buttonViews[which] }

    /** The card with all its parts; built once. Screenshot tests put it in a scene; [show] puts it in the window. */
    internal fun content(): View = built ?: build().also { built = it }

    // ---------- building the card ----------

    private fun build(): View {
        val ctx = context
        val card = Card(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = Ui.rounded(Ui.c(ctx, R.color.ag_card), 20, ctx)
            maxHeightPx = ctx.resources.displayMetrics.heightPixels - Ui.dp(ctx, 48)
        }
        val hasBody = message != null || customView != null || items != null
        title?.let { t ->
            card.addView(Ui.text(ctx, 18f, 900, R.color.ag_text).apply {
                text = t
                setPadding(Ui.dp(ctx, 24), Ui.dp(ctx, 24), Ui.dp(ctx, 24), Ui.dp(ctx, if (hasBody) 12 else 4))
            }, LinearLayout.LayoutParams(-1, -2))
        }
        if (hasBody) {
            val own = customView?.takeIf { items == null && scrollsItself(it) }
            val body = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
            message?.let { m ->
                body.addView(Ui.text(ctx, 14f, 700, R.color.ag_sub1).apply {
                    text = m
                    setLineSpacing(0f, 1.15f)
                    setPadding(Ui.dp(ctx, 24), Ui.dp(ctx, if (title == null) 24 else 0), Ui.dp(ctx, 24), Ui.dp(ctx, 8))
                }, LinearLayout.LayoutParams(-1, -2))
            }
            customView?.let { v ->
                (v.parent as? ViewGroup)?.removeView(v)
                if (v === own) {
                    // a list / scroller of its own gets the card's remaining height directly (inside our ScrollView it would measure about one row
                    // tall); the message above stays put
                    if (message != null) card.addView(body, LinearLayout.LayoutParams(-1, -2))
                    card.addView(v, LinearLayout.LayoutParams(-1, 0, 1f).also { if (title == null && message == null) it.topMargin = Ui.dp(ctx, 16) })
                } else {
                    body.addView(v, LinearLayout.LayoutParams(-1, -2).also { if (title == null && message == null) it.topMargin = Ui.dp(ctx, 16) })
                }
            }
            items?.forEachIndexed { i, label ->
                body.addView(itemRow(label, i), LinearLayout.LayoutParams(-1, -2).also { if (i == 0 && title == null && message == null) it.topMargin = Ui.dp(ctx, 8) })
            }
            if (own == null) {
                card.addView(ScrollView(ctx).apply {
                    overScrollMode = View.OVER_SCROLL_NEVER
                    addView(body, ViewGroup.LayoutParams(-1, -2))
                }, LinearLayout.LayoutParams(-1, 0, 1f))
            }
        }
        if (buttons.isNotEmpty()) {
            val row = ButtonRow(ctx).apply { setPadding(Ui.dp(ctx, 12), Ui.dp(ctx, 8), Ui.dp(ctx, 12), Ui.dp(ctx, 10)) }
            for (which in intArrayOf(DialogInterface.BUTTON_NEUTRAL, DialogInterface.BUTTON_NEGATIVE, DialogInterface.BUTTON_POSITIVE)) {
                val b = buttons[which] ?: continue
                row.addView(makeButton(ctx, which, b).also { buttonViews[which] = it })
            }
            card.addView(row, LinearLayout.LayoutParams(-1, -2))
        } else if (hasBody) {
            card.addView(View(ctx), LinearLayout.LayoutParams(-1, Ui.dp(ctx, 16)))
        }
        return card
    }

    /** True for a custom view that scrolls by itself (a list, a ScrollView, a RecyclerView): it must not sit inside the dialog's own ScrollView. */
    private fun scrollsItself(v: View): Boolean {
        if (v is android.widget.AbsListView || v is ScrollView) return true
        var c: Class<*>? = v.javaClass
        while (c != null) { // RecyclerView / NestedScrollView by name: the app has no such dependency yet
            if (c.simpleName == "RecyclerView" || c.simpleName == "NestedScrollView") return true
            c = c.superclass
        }
        return false
    }

    private fun ink(alpha: Int, base: Int) = ColorStateList.valueOf((base and 0xFFFFFF) or (alpha shl 24))

    private fun itemRow(label: CharSequence, index: Int): TextView = Ui.text(context, 15f, 700, R.color.ag_text).apply {
        text = label
        minHeight = Ui.dp(context, 48)
        gravity = Gravity.CENTER_VERTICAL
        setPadding(Ui.dp(context, 24), Ui.dp(context, 8), Ui.dp(context, 24), Ui.dp(context, 8))
        background = RippleDrawable(ink(0x2A, Ui.c(context, R.color.ag_text)), null, ColorDrawable(Color.BLACK))
        setOnClickListener { itemListener?.onClick(this@AppDialog, index); dismiss() }
    }

    private fun makeButton(ctx: Context, which: Int, b: Btn): TextView {
        val positive = which == DialogInterface.BUTTON_POSITIVE
        val fillColor = Ui.c(ctx, R.color.ag_mauve)
        val textColor = if (positive) AppDialogRules.onFill(fillColor, Ui.c(ctx, R.color.ag_crust), TypeInk::contrast) else Ui.c(ctx, R.color.ag_sub1)
        val shape: Drawable = Ui.rounded(if (positive) fillColor else Color.TRANSPARENT, 22, ctx)
        val ripple = RippleDrawable(ink(if (positive) 0x44 else 0x22, if (positive) Color.WHITE else Ui.c(ctx, R.color.ag_text)), shape, Ui.rounded(Color.BLACK, 22, ctx))
        return AppButton(ctx).apply {
            typeface = Ui.font(ctx, 900)
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14f)
            isAllCaps = false
            includeFontPadding = false
            setTextColor(textColor)
            text = b.text
            Ui.touchPill(this, ripple, 44, if (positive) 22 else 16)
            setOnClickListener { b.listener?.onClick(this@AppDialog, which); dismiss() }
        }
    }

    /** A button that fades when disabled (callers switch OK on and off). */
    private class AppButton(ctx: Context) : TextView(ctx) {
        override fun setEnabled(enabled: Boolean) { super.setEnabled(enabled); alpha = if (enabled) 1f else 0.4f }
    }

    /** Vertical LinearLayout that never grows past [maxHeightPx] (the scrolling part shrinks instead). */
    private class Card(ctx: Context) : LinearLayout(ctx) {
        var maxHeightPx = Int.MAX_VALUE
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val size = MeasureSpec.getSize(heightMeasureSpec)
            val capped = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED || size > maxHeightPx) {
                MeasureSpec.makeMeasureSpec(maxHeightPx, MeasureSpec.AT_MOST)
            } else heightMeasureSpec
            super.onMeasure(widthMeasureSpec, capped)
        }
    }

    /** The buttons, right-aligned; [AppDialogRules.rows] moves the ones that do not fit to a second row. */
    private class ButtonRow(ctx: Context) : ViewGroup(ctx) {
        private var rows: List<List<Int>> = emptyList()
        private val gap = Ui.dp(ctx, 4)
        private val kids get() = (0 until childCount).map { getChildAt(it) }.filter { it.visibility != GONE }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val w = MeasureSpec.getSize(widthMeasureSpec)
            val avail = w - paddingLeft - paddingRight
            val ks = kids
            ks.forEach { it.measure(MeasureSpec.makeMeasureSpec(avail, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)) }
            rows = AppDialogRules.rows(ks.map { it.measuredWidth }, avail, gap)
            val h = rows.sumOf { r -> r.maxOf { ks[it].measuredHeight } } + paddingTop + paddingBottom
            setMeasuredDimension(w, h)
        }

        override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
            val ks = kids
            var y = paddingTop
            for (row in rows) {
                val rowH = row.maxOf { ks[it].measuredHeight }
                var x = r - l - paddingRight
                for (i in row.asReversed()) {
                    val k = ks[i]
                    k.layout(x - k.measuredWidth, y, x, y + k.measuredHeight)
                    x -= k.measuredWidth + gap
                }
                y += rowH
            }
        }
    }

    // ---------- the builder ----------

    /** The subset of `AlertDialog.Builder` the app uses; every setter returns the builder. */
    class Builder(private val ctx: Context) {
        private val d = AppDialog(ctx)

        fun setTitle(t: CharSequence?) = apply { d.title = t }
        fun setMessage(m: CharSequence?) = apply { d.message = m }
        fun setView(v: View?) = apply { d.customView = v }
        fun setItems(labels: Array<out CharSequence>, l: DialogInterface.OnClickListener?) = apply { d.items = labels; d.itemListener = l }
        fun setPositiveButton(text: CharSequence, l: DialogInterface.OnClickListener?) = apply { d.buttons[DialogInterface.BUTTON_POSITIVE] = Btn(text, l) }
        fun setNegativeButton(text: CharSequence, l: DialogInterface.OnClickListener?) = apply { d.buttons[DialogInterface.BUTTON_NEGATIVE] = Btn(text, l) }
        fun setNeutralButton(text: CharSequence, l: DialogInterface.OnClickListener?) = apply { d.buttons[DialogInterface.BUTTON_NEUTRAL] = Btn(text, l) }
        fun setCancelable(c: Boolean) = apply { d.cancelable = c }
        fun setOnDismissListener(l: DialogInterface.OnDismissListener?) = apply { d.dismissListener = l }
        fun setOnCancelListener(l: DialogInterface.OnCancelListener?) = apply { d.cancelListener = l }
        fun create(): AppDialog = d
    }

    companion object {
        const val BUTTON_POSITIVE = DialogInterface.BUTTON_POSITIVE
        const val BUTTON_NEGATIVE = DialogInterface.BUTTON_NEGATIVE
        const val BUTTON_NEUTRAL = DialogInterface.BUTTON_NEUTRAL
    }
}

/** Builds and shows the dialog through [Pickers.show] (the app's dialog look). */
fun AppDialog.Builder.showCard() = Pickers.show(create())
