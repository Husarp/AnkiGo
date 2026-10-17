package com.husarp.ankigo.ui

import com.husarp.ankigo.game.lookNum
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import com.husarp.ankigo.R
import com.husarp.ankigo.game.Mon

/**
 * The copies sheet of a Box group tile (PLAN 45, "4B"): a bottom sheet listing every copy of the species (sprite, level,
 * ★ shiny pill, nature · IV total, tags), scrollable, with a small sort toggle: "Level" (high first, default) or "Recent"
 * (latest caught first). Tap a row = [onOpen] (the sheet closes first), hold = [onMenu] (actions; the sheet closes once an
 * entry was picked, the copies may have changed).
 */
internal class BoxCopiesSheet(
    private val page: GamePage,
    private val tile: BoxTile,
    private val onOpen: (Mon) -> Unit,
    private val onMenu: (Mon, View, () -> Unit) -> Unit,
) {
    var sort = CopySort.LEVEL
        private set
    private var dismiss: () -> Unit = {}
    private lateinit var list: LinearLayout
    private lateinit var sortBox: LinearLayout

    private val ctx: Context get() = page

    /** The sheet's content (rounded top corners, grab handle, title + sort toggle, the scrolling list); [maxListPx] caps the list height. */
    fun build(maxListPx: Int): View {
        val sheet = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Ui.c(ctx, R.color.ag_card))
                val r = Ui.dp(ctx, 22f); cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
            }
            setPadding(Ui.dp(ctx, 16), Ui.dp(ctx, 6), Ui.dp(ctx, 16), Ui.dp(ctx, 10))
        }
        sheet.addView(View(ctx).apply { background = Ui.rounded(Ui.c(ctx, R.color.ag_ov), 2, ctx) },
            LinearLayout.LayoutParams(Ui.dp(ctx, 40), Ui.dp(ctx, 4)).also {
                it.gravity = Gravity.CENTER_HORIZONTAL; it.topMargin = Ui.dp(ctx, 4); it.bottomMargin = Ui.dp(ctx, 6)
            })

        val head = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val titles = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        titles.addView(Ui.text(ctx, 17f, 900, R.color.ag_text).apply {
            text = "${page.data.sp(tile.best.species).name} ×${tile.count}"
            maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
        })
        head.addView(titles, LinearLayout.LayoutParams(0, -2, 1f))
        sortBox = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        head.addView(sortBox, LinearLayout.LayoutParams(-2, -2).also { it.marginStart = Ui.dp(ctx, 8) })
        sheet.addView(head, LinearLayout.LayoutParams(-1, -2))

        list = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        sheet.addView(MaxHeightScroll(ctx, maxListPx).apply {
            isVerticalScrollBarEnabled = false
            addView(list, android.widget.FrameLayout.LayoutParams(-1, -2))
        }, LinearLayout.LayoutParams(-1, -2).also { it.topMargin = Ui.dp(ctx, 2) })
        fill()
        return sheet
    }

    private fun setSort(s: CopySort) {
        if (s == sort) return
        sort = s
        fill()
    }

    private fun fill() {
        sortBox.removeAllViews()
        for (s in CopySort.entries) {
            sortBox.addView(BoxViews.chip(ctx, s.label, s == sort) { setSort(s) },
                LinearLayout.LayoutParams(-2, -2).also { if (s.ordinal > 0) it.marginStart = Ui.dp(ctx, 6) })
        }
        list.removeAllViews()
        for (mon in BoxTiles.order(tile.members, sort)) list.addView(row(mon), LinearLayout.LayoutParams(-1, -2))
    }

    private fun row(mon: Mon): View {
        val data = page.data
        val state = page.state
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = Ui.dp(ctx, 56)
            setPadding(Ui.dp(ctx, 4), Ui.dp(ctx, 4), Ui.dp(ctx, 4), Ui.dp(ctx, 4))
            background = RippleDrawable(ColorStateList.valueOf(Ui.c(ctx, R.color.ag_mantle)), Ui.rounded(Color.TRANSPARENT, 12, ctx), null)
            alpha = if (mon.hp <= 0) 0.55f else 1f
            setOnClickListener { dismiss(); onOpen(mon) }
            setOnLongClickListener { onMenu(mon, it) { dismiss() }; true }
        }
        val img = ImageView(ctx)
        Ui.staticSprite(ctx, mon.lookNum(page.data), mon.shiny, img)
        row.addView(img, LinearLayout.LayoutParams(Ui.dp(ctx, 44), Ui.dp(ctx, 44)))

        val body = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val r1 = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        r1.addView(Ui.text(ctx, 15f, 900, R.color.ag_text).apply { LevelTier.style(this, mon.level); maxLines = 1 })
        if (mon.favourite) r1.addView(BoxViews.heartMark(ctx), LinearLayout.LayoutParams(Ui.dp(ctx, 13), Ui.dp(ctx, 13)).also { it.marginStart = Ui.dp(ctx, 6) })
        // the shiny pill first, then the other tags as many as fit
        val tags = MonTags.of(data, state, mon).sortedBy { if (it.kind == TagKind.SHINY) 0 else 1 }.take(3)
        val fit = TeamPage.FitRow(ctx).apply {
            tags.forEach { t ->
                addView(MonTags.pill(ctx, t), LinearLayout.LayoutParams(-2, -2).also { lp -> lp.marginStart = Ui.dp(ctx, 5) })
            }
        }
        r1.addView(fit, LinearLayout.LayoutParams(0, -2, 1f))
        body.addView(r1)
        body.addView(Ui.text(ctx, 11f, 700, R.color.ag_sub0).apply { text = BoxViews.copyNote(mon); maxLines = 1 },
            LinearLayout.LayoutParams(-2, -2).also { it.topMargin = Ui.dp(ctx, 2) })
        row.addView(body, LinearLayout.LayoutParams(0, -2, 1f).also { it.marginStart = Ui.dp(ctx, 8) })

        if (mon.uid in state.team) row.addView(BoxViews.teamMark(ctx), LinearLayout.LayoutParams(Ui.dp(ctx, 16), Ui.dp(ctx, 16)).also { it.marginStart = Ui.dp(ctx, 6) })
        row.addView(ImageView(ctx).apply {
            setImageResource(R.drawable.ic_chevron)
            setColorFilter(Ui.c(ctx, R.color.ag_sub0), android.graphics.PorterDuff.Mode.SRC_IN)
        }, LinearLayout.LayoutParams(Ui.dp(ctx, 22), Ui.dp(ctx, 22)).also { it.marginStart = Ui.dp(ctx, 2) })
        return row
    }

    /** A ScrollView that never grows past [maxPx]: the sheet grows with its copies, then scrolls. */
    private class MaxHeightScroll(ctx: Context, private val maxPx: Int) : ScrollView(ctx) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val cap = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED) maxPx else minOf(maxPx, MeasureSpec.getSize(heightMeasureSpec))
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(cap, MeasureSpec.AT_MOST))
        }
    }

    /** Shows the sheet at the bottom of the screen (an overlay window when the page lives in the floating card). */
    fun show() {
        val dialog = Dialog(ctx).apply { requestWindowFeature(Window.FEATURE_NO_TITLE) }
        dismiss = { dialog.dismiss() }
        val maxList = (ctx.resources.displayMetrics.heightPixels * LIST_SHARE).toInt()
        dialog.setContentView(build(maxList))
        dialog.setCanceledOnTouchOutside(true)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(.48f); setGravity(Gravity.BOTTOM)
            if (Ui.findActivity(ctx) == null) setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        }
        dialog.show()
        dialog.window?.setLayout(minOf(ctx.resources.displayMetrics.widthPixels, Ui.dp(ctx, 600)), -2)
    }

    companion object {
        /** The list takes at most this share of the screen height. */
        const val LIST_SHARE = 0.5f

        fun show(page: GamePage, tile: BoxTile, onOpen: (Mon) -> Unit, onMenu: (Mon, View, () -> Unit) -> Unit) =
            BoxCopiesSheet(page, tile, onOpen, onMenu).show()
    }
}
