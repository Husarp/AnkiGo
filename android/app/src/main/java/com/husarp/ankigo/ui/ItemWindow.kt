package com.husarp.ankigo.ui

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import com.husarp.ankigo.R
import com.husarp.ankigo.game.Items

/**
 * PLAN 210 (R15-A): the window a Shop or Bag item opens (it replaced the card that used to open in place and push the list down). It has the
 * look of the app's bottom sheets ([SheetKit]: handle, drag down to close, tap outside or Back closes, a light dim), takes at most
 * [MAX_SHARE] of the screen height, and holds the item's card in three parts ([ItemCard]): the header (icon, name, one line, a ✕), the body
 * that scrolls, and the footer with the "You own" line and the buttons that always stays in view.
 *
 * The page owns at most one window and keeps it up to date with [update] on every render (Buy and Trade finish inside it and show the new
 * counts); [dismiss] is the page closing it, a close by the user (✕, drag, outside, Back) calls [onClosed] once. A window over the floating
 * card has no Activity, so it becomes an overlay window then.
 */
internal class ItemWindow(private val page: GamePage, private val onClosed: () -> Unit) {
    private val ctx: Context get() = page
    private var dialog: Dialog? = null
    private var shown = false
    private var done = false // closed, by the user or by [dismiss]: nothing more happens
    private var title = "" // the item's name: what a screen reader says when the window opens

    private val headBox = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
    private val bodyBox = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(0, Ui.dp(ctx, 10), 0, Ui.dp(ctx, 6)) }
    private val footBox = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(0, Ui.dp(ctx, 4), 0, 0) }
    private val scroll = ScrollView(ctx).apply {
        isVerticalScrollBarEnabled = false
        isVerticalFadingEdgeEnabled = true // a cut-off line at the bottom says the body goes on
        setFadingEdgeLength(Ui.dp(ctx, 18))
        addView(bodyBox, FrameLayout.LayoutParams(-1, -2))
    }

    /** The window's content: the sheet with its drag (a real Dialog puts it in as its content; the shots lay it out themselves). */
    val view: View = SheetKit.host(ctx, buildSheet(), { cancelByUser() }, { dim -> dialog?.window?.setDimAmount(dim) })

    val isShowing: Boolean get() = shown && !done

    private fun buildSheet(): View {
        val max = (ctx.resources.displayMetrics.heightPixels * MAX_SHARE).toInt()
        val sheet = CappedSheet(ctx, max).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Ui.c(ctx, R.color.ag_card))
                val r = Ui.dp(ctx, 22f); cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
            }
            setPadding(Ui.dp(ctx, 16), 0, Ui.dp(ctx, 16), Ui.dp(ctx, 12))
        }
        sheet.addView(SheetKit.newHandle(ctx), LinearLayout.LayoutParams(Ui.dp(ctx, 36), Ui.dp(ctx, 4)).also {
            it.gravity = Gravity.CENTER_HORIZONTAL; it.topMargin = Ui.dp(ctx, 8); it.bottomMargin = Ui.dp(ctx, 6)
        })
        sheet.addView(headBox, LinearLayout.LayoutParams(-1, -2))
        sheet.addView(View(ctx).apply { setBackgroundColor(Ui.c(ctx, R.color.ag_s0)) }, LinearLayout.LayoutParams(-1, Ui.dp(ctx, 1)).also { it.topMargin = Ui.dp(ctx, 4) })
        sheet.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f)) // the only part that gives way when the sheet reaches its cap
        sheet.addView(footBox, LinearLayout.LayoutParams(-1, -2))
        return sheet
    }

    /** The sheet, never taller than [maxPx]; the body scroll (weight 1) shrinks first, the header and the footer keep their size. */
    private class CappedSheet(ctx: Context, private val maxPx: Int) : LinearLayout(ctx) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val cap = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED) maxPx else minOf(maxPx, MeasureSpec.getSize(heightMeasureSpec))
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(cap, MeasureSpec.AT_MOST))
        }
    }

    /** Draws [spec] again in place (new counts, a new price, the trade confirm); the scroll position stays. */
    fun update(spec: ItemCard.Spec) {
        if (done) return
        headBox.removeAllViews(); headBox.addView(ItemCard.header(page, spec, ::cancelByUser), LinearLayout.LayoutParams(-1, -2))
        bodyBox.removeAllViews(); bodyBox.addView(ItemCard.body(page, spec), LinearLayout.LayoutParams(-1, -2))
        footBox.removeAllViews(); footBox.addView(ItemCard.footer(page, spec), LinearLayout.LayoutParams(-1, -2))
        title = if (spec.item == null) Items.tmName(page.data, spec.move.orEmpty()) else Items.name(spec.item)
        dialog?.window?.setTitle(title)
    }

    /** Puts the window on the screen (once; later calls do nothing). */
    fun show() {
        if (done || shown) return
        shown = true
        interceptShow?.let { it(this); return }
        val d = Dialog(ctx).apply { requestWindowFeature(Window.FEATURE_NO_TITLE) }
        dialog = d
        d.setContentView(view)
        d.setCanceledOnTouchOutside(true)
        d.setOnCancelListener { userClosed() }
        d.setOnDismissListener { userClosed() }
        d.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(SheetDrag.DIM); setGravity(Gravity.BOTTOM)
            setTitle(title)
            if (Ui.findActivity(ctx) == null) setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        }
        d.show()
        d.window?.setLayout(minOf(ctx.resources.displayMetrics.widthPixels, Ui.dp(ctx, 600)), -2)
    }

    /** The page closes the window (another tab, the item is gone, the page is left): no [onClosed]. */
    fun dismiss() {
        if (done) return
        done = true
        shown = false
        closeDialog()
    }

    /** ✕ and the drag: the same as a tap outside or Back. */
    private fun cancelByUser() {
        userClosed()
        closeDialog()
    }

    private fun userClosed() {
        if (done) return
        done = true
        shown = false
        onClosed()
    }

    private fun closeDialog() {
        val d = dialog ?: return
        dialog = null
        d.window?.setWindowAnimations(0)
        try { d.dismiss() } catch (_: IllegalArgumentException) { /* its Activity is already gone, and the window with it */ }
    }

    companion object {
        /** The window takes at most this share of the screen height. */
        const val MAX_SHARE = 0.85f

        /** Tests: where the window would be shown (a JVM test has no window manager); the window is then not put on the screen. */
        internal var interceptShow: ((ItemWindow) -> Unit)? = null
    }
}
