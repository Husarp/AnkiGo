package com.husarp.ankigo.ui

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.widget.TextView
import com.husarp.ankigo.R
import com.husarp.ankigo.study.SwipeRules
import com.husarp.ankigo.study.SwipeRules.Dir
import kotlin.math.abs
import kotlin.math.max

/**
 * Wraps the card's WebView for opt-in swipe-to-answer (answer side only): takes over a drag only when the card can't scroll
 * that way, shows a pill on the edge being swiped to, and a small hint at the bottom. [onSwipe] gets the ease; the caller
 * answers through its normal guarded path. The rules live in [SwipeRules].
 */
internal class SwipeFrame(ctx: Context, private val content: View) : FrameLayout(ctx) {
    var onSwipe: (Int) -> Unit = {}
    /** Called once each time the finger crosses the commit distance. */
    var onTick: () -> Unit = { performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
    private var armed = false
    private var buttons = 4
    private val slop = ViewConfiguration.get(ctx).scaledTouchSlop
    private val threshold = Ui.dp(ctx, SwipeRules.THRESHOLD_DP).toFloat()
    private val dark = ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    private val hint = Ui.text(ctx, 11f, 800, R.color.ag_sub1).apply {
        gravity = Gravity.CENTER; visibility = View.GONE
        setPadding(Ui.dp(ctx, 10), Ui.dp(ctx, 5), Ui.dp(ctx, 10), Ui.dp(ctx, 5))
        background = Ui.rounded((Ui.c(ctx, R.color.ag_mantle) and 0x00ffffff) or 0xE6000000.toInt(), 10, ctx)
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private val pill = Ui.text(ctx, 14f, 900, R.color.ag_text).apply {
        gravity = Gravity.CENTER; visibility = View.GONE
        setTextColor(if (dark) Ui.c(ctx, R.color.ag_crust) else Color.WHITE)
        setPadding(Ui.dp(ctx, 14), Ui.dp(ctx, 8), Ui.dp(ctx, 14), Ui.dp(ctx, 8))
    }
    // the gesture in progress
    private var downX = 0f; private var downY = 0f
    private var tracking = false; private var claimed = false; private var dead = false; private var full = false

    init {
        addView(content, LayoutParams(-1, -1))
        addView(hint, LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).also { val m = Ui.dp(ctx, 10); it.setMargins(m, m, m, m) })
        addView(pill, LayoutParams(-2, -2))
    }

    /** [enabled]: a swipe may answer now; [showHint]: the hint shows (the answer side with the setting on). */
    fun setState(enabled: Boolean, buttons: Int, showHint: Boolean) {
        this.buttons = buttons; armed = enabled
        hint.visibility = if (showHint) View.VISIBLE else View.GONE
        if (showHint) {
            hint.text = coloured(buttons); hint.contentDescription = SwipeRules.description(buttons)
        }
        if (!enabled) reset()
    }

    private fun tone(dir: Dir) = when (dir) { Dir.LEFT -> R.color.ag_red; Dir.DOWN -> R.color.ag_peach; Dir.RIGHT -> R.color.ag_green; Dir.UP -> R.color.ag_blue }

    private fun coloured(buttons: Int): CharSequence = SpannableStringBuilder().apply {
        SwipeRules.dirs(buttons).forEachIndexed { i, dir ->
            if (i > 0) append(" · ")
            val start = length
            append(dir.arrow)
            setSpan(ForegroundColorSpan(Ui.c(context, tone(dir))), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            append("\u00a0${SwipeRules.label(dir)}")   // no break between an arrow and its word
        }
    }

    /** Live feedback: the pill for [dir] at [progress] (0..1); null hides it. Also the screenshot hook. */
    internal fun preview(dir: Dir?, progress: Float) {
        if (dir == null || progress <= 0f) { pill.visibility = View.GONE; hint.alpha = 1f; return }
        val m = Ui.dp(context, 12)
        pill.layoutParams = LayoutParams(-2, -2, when (dir) {
            Dir.LEFT -> Gravity.START or Gravity.CENTER_VERTICAL; Dir.RIGHT -> Gravity.END or Gravity.CENTER_VERTICAL
            Dir.UP -> Gravity.TOP or Gravity.CENTER_HORIZONTAL; Dir.DOWN -> Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        }).also { it.setMargins(m, m, m, m) }
        pill.text = SwipeRules.pill(dir)
        pill.background = Ui.rounded(Ui.c(context, tone(dir)), 12, context)
        pill.visibility = View.VISIBLE; pill.alpha = .3f + .7f * progress
        pill.scaleX = .8f + .2f * progress; pill.scaleY = pill.scaleX
        hint.alpha = 1f - progress
    }

    private fun reset() { tracking = false; claimed = false; dead = false; full = false; preview(null, 0f) }

    private fun canScroll(horizontal: Boolean, direction: Int) = if (horizontal) content.canScrollHorizontally(direction) else content.canScrollVertically(direction)

    override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
        if (!armed) return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { reset(); downX = e.x; downY = e.y; tracking = true }
            MotionEvent.ACTION_POINTER_DOWN -> tracking = false
            MotionEvent.ACTION_MOVE -> if (tracking) {
                val dx = e.x - downX; val dy = e.y - downY
                val far = max(abs(dx), abs(dy))
                if (far >= slop && SwipeRules.dominant(dx, dy)) {
                    tracking = false   // decided once per drag: a card that scrolls this way keeps its scroll
                    if (SwipeRules.claims(dx, dy, buttons, ::canScroll)) { claimed = true; parent?.requestDisallowInterceptTouchEvent(true); return true }
                } else if (far >= 3 * slop) tracking = false
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> tracking = false
        }
        return false
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (!claimed) return super.onTouchEvent(e)
        if (SwipeRules.cancelledBy(e.pointerCount)) dead = true
        val dx = e.x - downX; val dy = e.y - downY
        when (e.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                val dir = SwipeRules.axis(dx, dy)
                val p = if (dead || dir == null) 0f else SwipeRules.progress(dir, dx, dy, threshold)
                preview(dir, p)
                if (p >= 1f && !full) { full = true; onTick() } else if (p < 1f) full = false
            }
            MotionEvent.ACTION_UP -> {
                val dir = if (dead) null else SwipeRules.commit(dx, dy, threshold, buttons)
                reset()
                dir?.let { d -> SwipeRules.ease(d, buttons)?.let(onSwipe) }
            }
            MotionEvent.ACTION_CANCEL -> reset()
        }
        return true
    }
}
