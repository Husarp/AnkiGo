package com.husarp.ankigo.ui

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.OvalShape
import android.view.Gravity
import android.view.View
import android.widget.AbsListView
import android.widget.FrameLayout
import android.widget.ListView
import com.husarp.ankigo.R

/** R19 250: when the back-to-top arrow shows. Pure, unit-tested. All lengths in the same unit (px). */
object BackToTopRules {
    /** The arrow fades in once the list is scrolled down about one screen ... */
    const val SHOW_AT = 1.0f

    /** ... and fades out again when it is back within this part of a screen from the top. */
    const val HIDE_AT = 0.25f

    /** The room the list keeps below its last row so the arrow never covers a cell (dp). */
    const val PAD_DP = 48

    /** How far the list is scrolled ([firstTop] = the first visible row's top, <= 0 when it is partly above the list). */
    fun offset(firstPosition: Int, firstTop: Int, firstHeight: Int, headerHeight: Int): Int =
        if (firstPosition <= 0) -firstTop else headerHeight + (firstPosition - 1) * firstHeight - firstTop

    /** Whether the arrow shows now: [wasShown] keeps it steady between [HIDE_AT] and [SHOW_AT] of a screen ([viewport] = the list's height). */
    fun show(wasShown: Boolean, offset: Int, viewport: Int): Boolean =
        if (viewport <= 0) false else if (wasShown) offset > viewport * HIDE_AT else offset >= viewport * SHOW_AT
}

/**
 * R19 250: the back-to-top arrow of a long list (Pokédex and Box). [wrap] puts a ListView and the arrow in one FrameLayout; the arrow is
 * bare (no tile behind it): an up-arrow in the accent colour with a thin halo in the page colour so it reads over cells, a 48 dp touch
 * area and a round ripple. It fades in after about one screen of scrolling, scrolls the list smoothly to its top on a tap and fades out
 * at the top. Reduced motion (animators off in the system or "Reduce motion" in the app): no fade, the list jumps.
 */
object BackToTop {
    /** The list's bottom padding (px) should include this much room ([BackToTopRules.PAD_DP]) so the last row stays clear of the arrow. */
    fun room(ctx: Context): Int = Ui.dp(ctx, BackToTopRules.PAD_DP)

    private const val FADE_MS = 150L
    private const val SCROLL_MS = 350

    private fun still(ctx: Context) =
        AppToastRules.still(ValueAnimator.areAnimatorsEnabled(), ctx.getSharedPreferences("study", 0).getBoolean("reduceMotion", false))

    /** [list] and the arrow in one frame; the caller puts the frame where the list was (and shows / hides the frame, not the list). */
    fun wrap(list: ListView): FrameLayout {
        val ctx = list.context
        val arrow = Arrow(ctx).apply {
            contentDescription = "Back to top"
            alpha = 0f
            visibility = View.GONE
            background = RippleDrawable(
                ColorStateList.valueOf(Ui.c(ctx, R.color.ag_mauve) and 0x00FFFFFF or 0x33000000), null,
                ShapeDrawable(OvalShape()).apply { paint.color = 0xFF000000.toInt() })
            setOnClickListener {
                if (still(ctx)) list.setSelection(0) else list.smoothScrollToPositionFromTop(0, 0, SCROLL_MS)
            }
        }
        var shown = false
        var headerH = -1
        fun setShown(on: Boolean) {
            if (on == shown) return
            shown = on
            arrow.animate().cancel()
            if (still(ctx)) {
                arrow.alpha = if (on) 1f else 0f
                arrow.visibility = if (on) View.VISIBLE else View.GONE
            } else if (on) {
                arrow.visibility = View.VISIBLE
                arrow.animate().alpha(1f).setDuration(FADE_MS).start()
            } else {
                arrow.animate().alpha(0f).setDuration(FADE_MS).withEndAction { if (!shown) arrow.visibility = View.GONE }.start()
            }
        }
        list.setOnScrollListener(object : AbsListView.OnScrollListener {
            override fun onScrollStateChanged(view: AbsListView?, scrollState: Int) = Unit
            override fun onScroll(view: AbsListView?, firstVisibleItem: Int, visibleItemCount: Int, totalItemCount: Int) {
                val first = list.getChildAt(0) ?: return setShown(false)
                if (firstVisibleItem == 0) headerH = first.height
                val vp = list.height
                // the header's height is unknown until it was seen once (a list restored mid-way): count it as one screen
                val off = BackToTopRules.offset(firstVisibleItem, first.top, first.height, if (headerH >= 0) headerH else vp)
                setShown(BackToTopRules.show(shown, off, vp))
            }
        })
        return FrameLayout(ctx).apply {
            addView(list, FrameLayout.LayoutParams(-1, -1))
            addView(arrow, FrameLayout.LayoutParams(Ui.dp(ctx, 48), Ui.dp(ctx, 48), Gravity.END or Gravity.BOTTOM).also {
                it.rightMargin = Ui.dp(ctx, 8); it.bottomMargin = Ui.dp(ctx, 2)
            })
        }
    }

    /** The arrow: drawn in code so the halo can be thicker than the line. 28 dp inside the 48 dp touch area. */
    private class Arrow(ctx: Context) : View(ctx) {
        private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
            color = Ui.c(ctx, R.color.ag_mauve); strokeWidth = Ui.dp(ctx, 2.6f).toFloat()
        }
        private val halo = Paint(line).apply { color = Ui.c(ctx, R.color.ag_base); strokeWidth = line.strokeWidth + Ui.dp(ctx, 4f) }
        private val path = Path()

        override fun onDraw(canvas: Canvas) {
            val size = Ui.dp(context, 28).toFloat()
            val s = size / 24f
            val ox = (width - size) / 2f
            val oy = (height - size) / 2f
            path.reset() // the page's up icon (ic_up): a stem and the head, in its 24 x 24 box
            path.moveTo(ox + 12 * s, oy + 19 * s); path.lineTo(ox + 12 * s, oy + 5 * s)
            path.moveTo(ox + 6 * s, oy + 11 * s); path.lineTo(ox + 12 * s, oy + 5 * s); path.lineTo(ox + 18 * s, oy + 11 * s)
            canvas.drawPath(path, halo)
            canvas.drawPath(path, line)
        }
    }
}
