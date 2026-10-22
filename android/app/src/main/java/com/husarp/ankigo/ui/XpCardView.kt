package com.husarp.ankigo.ui

import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import com.husarp.ankigo.R
import com.husarp.ankigo.gfx.SpriteView

/**
 * The little card of a team member that gained XP (Round 3 pick 9A): mini sprite, name, "+30 XP" and an XP bar that fills
 * from [XpCard.barFrom] to [XpCard.barTo]; on a level-up the bar fills to the top, restarts, and the text becomes
 * "Level up! Lv 16" with a sparkle. [slim] is the one-line version for small windows; [micro] (slim too) drops the name and
 * says just "Lv 16!", for a window with only a narrow free spot. The arena slides it in and out;
 * it never takes touches.
 */
internal class XpCardView(ctx: Context, val card: XpCard, val slim: Boolean, floorSp: Float, val micro: Boolean = false) : LinearLayout(ctx) {
    val sprite = SpriteView(ctx).apply { scaleDp = 1f }
    private val name = Ui.text(ctx, if (slim) 10f else 11f, 800, R.color.ag_text).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END; text = card.name }
    private val note = Ui.text(ctx, if (slim) 9.5f else 10f, 900, R.color.ag_sky).apply { maxLines = 1; text = "+${card.xp} XP" }
    private val sparkle = Sparkle(ctx).apply { visibility = GONE }
    private val bar = BarView(ctx).apply { setTrackColor(Ui.c(ctx, R.color.ag_s0)) }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = Ui.rounded((Ui.c(ctx, R.color.ag_card) and 0x00FFFFFF) or (0xF2 shl 24), if (slim) 8 else 12, ctx)
        elevation = Ui.dp(ctx, 3f)
        isClickable = false
        val pad = if (micro) 3 else if (slim) 4 else 6
        val padY = if (micro) 2 else pad
        setPadding(Ui.dp(ctx, pad + 2), Ui.dp(ctx, padY), Ui.dp(ctx, pad + 2), Ui.dp(ctx, padY))
        val icon = Ui.dp(ctx, if (micro) 14 else if (slim) 18 else 28)
        addView(sprite, LayoutParams(icon, icon).also { it.marginEnd = Ui.dp(ctx, 6) })
        val col = LinearLayout(ctx).apply { orientation = VERTICAL }
        val row = LinearLayout(ctx).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(name, LayoutParams(0, -2, 1f))
        row.addView(sparkle, LayoutParams(Ui.dp(ctx, 10), Ui.dp(ctx, 10)).also { it.marginStart = Ui.dp(ctx, 4); it.marginEnd = Ui.dp(ctx, 2) })
        row.addView(note, LayoutParams(-2, -2).also { it.marginStart = Ui.dp(ctx, 4) })
        col.addView(row, LayoutParams(-1, -2))
        col.addView(bar, LayoutParams(-1, Ui.dp(ctx, if (slim) 3 else 4)).also { it.topMargin = Ui.dp(ctx, if (micro) 1 else if (slim) 2 else 3) })
        addView(col, LayoutParams(0, -2, 1f))
        if (micro) name.visibility = GONE
        TextFloor.set(name, if (slim) 10f else 11f, floorSp)
        TextFloor.set(note, if (slim) 9.5f else 10f, floorSp)
        contentDescription = "${card.name} gained ${card.xp} XP" + if (card.levelUp) ", level ${card.levelTo}" else ""
        bar.setMeaning(BarMeaning.XP, card.barFrom, false)
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean = false

    /** The bar's runs: one for a plain gain; per level crossed the loop of [XpLoop] (fill to the end, hold, empty, on). */
    private val segs = XpLoop.segments(card.levelFrom, card.barFrom, card.levelTo, card.barTo)

    /** How long [play] takes in ms (the arena keeps the card up for it). */
    val playMs: Long = START_MS + if (!card.levelUp) PLAIN_MS else segs.withIndex().sumOf { (i, s) -> (if (i > 0) XpLoop.HOLD_MS else 0L) + XpLoop.runMs(s.length) }

    /** Starts the bar (and the level-up text); [reduce]: no animation, the end state right away. */
    fun play(reduce: Boolean) {
        if (reduce) {
            bar.setMeaning(BarMeaning.XP, card.barTo, false)
            if (card.levelUp) levelUpText(card.levelTo)
            return
        }
        if (!card.levelUp) {
            postDelayed({ bar.setMeaning(BarMeaning.XP, card.barTo, true) }, START_MS)
            return
        }
        // PLAN 182: from the XP it had, to the end; then per level: hold, "Level up! Lv N", empty, on to the next end
        var t = START_MS
        for ((i, s) in segs.withIndex()) {
            val ms = XpLoop.runMs(s.length)
            if (i > 0) t += XpLoop.HOLD_MS
            val at = t
            postDelayed({
                if (i > 0) { levelUpText(s.level); bar.setMeaning(BarMeaning.XP, 0f, false) }
                bar.setMeaning(BarMeaning.XP, s.to, ms > 0L, maxOf(ms, 1L))
            }, at)
            t += ms
        }
    }

    private fun levelUpText(level: Int) {
        note.text = if (micro) "Lv $level!" else "Level up! Lv $level"
        note.setTextColor(Ui.c(context, R.color.ag_peach))
        if (!micro) { sparkle.visibility = VISIBLE; sparkle.twinkle() }
    }

    private companion object {
        const val START_MS = 260L   // the card's slide-in
        const val PLAIN_MS = 450L   // a plain gain's slide
    }

    /** A small four-point star that pulses. */
    private class Sparkle(ctx: Context) : View(ctx) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFB81C.toInt() }
        private val path = Path()
        private var anim: ObjectAnimator? = null
        fun twinkle() {
            if (!android.animation.ValueAnimator.areAnimatorsEnabled()) return
            anim?.cancel()
            anim = ObjectAnimator.ofFloat(this, SCALE_X, 0.6f, 1.15f).apply { duration = 420; repeatMode = android.animation.ValueAnimator.REVERSE; repeatCount = 3; start() }
            ObjectAnimator.ofFloat(this, SCALE_Y, 0.6f, 1.15f).apply { duration = 420; repeatMode = android.animation.ValueAnimator.REVERSE; repeatCount = 3; start() }
        }
        override fun onDetachedFromWindow() { anim?.cancel(); super.onDetachedFromWindow() }
        override fun onTouchEvent(event: MotionEvent?): Boolean = false
        override fun onDraw(canvas: Canvas) {
            val c = width / 2f
            val r = width / 2f
            val i = r * 0.28f
            path.reset()
            path.moveTo(c, c - r); path.lineTo(c + i, c - i); path.lineTo(c + r, c); path.lineTo(c + i, c + i)
            path.lineTo(c, c + r); path.lineTo(c - i, c + i); path.lineTo(c - r, c); path.lineTo(c - i, c - i)
            path.close()
            canvas.drawPath(path, paint)
        }
    }
}
