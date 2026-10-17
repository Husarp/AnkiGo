package com.husarp.ankigo.shots

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.husarp.ankigo.R
import com.husarp.ankigo.ui.ArenaFxView
import com.husarp.ankigo.ui.movefx.MoveFx
import com.husarp.ankigo.ui.movefx.MoveFxEffect
import com.husarp.ankigo.ui.movefx.MoveFxPlan
import com.husarp.ankigo.ui.movefx.MoveFxStage
import kotlin.math.hypot

/**
 * Shot kit for the move animation looks (R7-03, reused by R7-04 / R7-08): a fake arena (the real arena's colours and sprite boxes,
 * two generated placeholder creatures, no game art) with a [MoveFxStage] that poses those placeholders, and a look rendered
 * frozen at chosen mockup ms through [ArenaFxView.showStill]. Both directions, two frame sizes, light and dark come from the caller.
 *
 * ```
 * paparazzi.snapshot(MoveFxShotKit.sheet(ctx, "Tackle", MoveFxShotKit.Frame.WIDE, plan, listOf(160f, 330f, 500f)), "name")
 * ```
 * [sheet] gives two columns (our Pokemon attacks the wild one, and the other way round) x one row per moment; render it on a
 * device at least 830 dp wide (Size.TABLET_LAND).
 */
object MoveFxShotKit {
    /** Arena sizes in dp: the phone-wide arena and a picture-in-picture one. */
    enum class Frame(val wDp: Int, val hDp: Int) { WIDE(412, 212), PIP(240, 136) }

    /** The player's stand-in creature (back view) or the wild one's (front view, with eyes): a blob with ears and a tail. */
    class FakeSprite(ctx: Context, private val body: Int, private val front: Boolean) : View(ctx) {
        var matrix: FloatArray? = null
            set(v) { field = v; invalidate() }
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val path = Path()
        private val rect = RectF()

        override fun onDraw(c: Canvas) {
            paint.colorFilter = matrix?.let { ColorMatrixColorFilter(ColorMatrix(it)) }
            val w = width.toFloat()
            val drawnH = height * DRAWN
            val bodyW = minOf(w * .9f, drawnH * 1.05f)
            val cx = w / 2f
            val bottom = height.toFloat()
            val top = bottom - drawnH
            paint.style = Paint.Style.FILL
            paint.color = shade(body, .72f)
            // ears and tail
            for (side in intArrayOf(-1, 1)) {
                path.reset()
                path.moveTo(cx + side * bodyW * .18f, top + drawnH * .25f)
                path.lineTo(cx + side * bodyW * .40f, top - drawnH * .02f)
                path.lineTo(cx + side * bodyW * .46f, top + drawnH * .40f)
                path.close()
                c.drawPath(path, paint)
            }
            rect.set(cx + bodyW * .30f, bottom - drawnH * .32f, cx + bodyW * .68f, bottom - drawnH * .12f)
            c.drawOval(rect, paint)
            paint.color = body
            rect.set(cx - bodyW / 2f, top + drawnH * .12f, cx + bodyW / 2f, bottom)
            c.drawOval(rect, paint)
            paint.color = shade(body, 1.25f)
            rect.set(cx - bodyW * .28f, bottom - drawnH * .55f, cx + bodyW * .28f, bottom - drawnH * .05f)
            c.drawOval(rect, paint)
            if (front) {
                paint.color = 0xFF1E1E2E.toInt()
                for (side in intArrayOf(-1, 1)) c.drawCircle(cx + side * bodyW * .16f, top + drawnH * .42f, drawnH * .045f, paint)
            }
        }

        private fun shade(c: Int, f: Float): Int =
            Color.rgb((Color.red(c) * f).toInt().coerceIn(0, 255), (Color.green(c) * f).toInt().coerceIn(0, 255), (Color.blue(c) * f).toInt().coerceIn(0, 255))

        companion object { const val DRAWN = .8f }
    }

    /**
     * One arena frame: the arena gradient, two platforms, the sprite boxes of the real arena (the wild one top right, ours
     * bottom left), an [ArenaFxView] on top. [playerAttacks] = our creature is the attacker.
     */
    class ShotArena(ctx: Context, val frame: Frame, val playerAttacks: Boolean) : FrameLayout(ctx) {
        private val d = ctx.resources.displayMetrics.density
        private val s = frame.hDp / 212f
        private val wPx = frame.wDp * d
        private val hPx = frame.hDp * d
        val player = FakeSprite(ctx, 0xFFE0833A.toInt(), front = false)
        val wild = FakeSprite(ctx, 0xFF3FA6A0.toInt(), front = true)
        val fx = ArenaFxView(ctx)

        // sprite boxes in px, laid out like ArenaView: wild 110 x 100 dp top right (42 dp in, 12 dp down), ours 140 x 128 dp bottom left
        private val wildBox = RectF(wPx - (42 + 110) * s * d, 12 * s * d, wPx - 42 * s * d, (12 + 100) * s * d)
        private val playerBox = RectF(22 * s * d, hPx - (6 + 128) * s * d, (22 + 140) * s * d, hPx - 6 * s * d)

        init {
            layoutParams = LayoutParams(wPx.toInt(), hPx.toInt())
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(ContextCompat.getColor(ctx, R.color.ag_arena_top), ContextCompat.getColor(ctx, R.color.ag_arena_bot)),
            )
            platform(ctx, wildBox, ContextCompat.getColor(ctx, R.color.ag_plat2))
            platform(ctx, playerBox, ContextCompat.getColor(ctx, R.color.ag_plat))
            place(wild, wildBox)
            place(player, playerBox)
            addView(fx, LayoutParams(-1, -1))
        }

        private fun platform(ctx: Context, box: RectF, color: Int) {
            val v = View(ctx).apply { background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) } }
            val w = box.width() * 1.15f
            val h = box.height() * .26f
            addView(v, LayoutParams(w.toInt(), h.toInt(), Gravity.TOP or Gravity.START).also {
                it.leftMargin = (box.centerX() - w / 2f).toInt(); it.topMargin = (box.bottom - h * .6f).toInt()
            })
        }

        private fun place(v: FakeSprite, box: RectF) {
            addView(v, LayoutParams(box.width().toInt(), box.height().toInt(), Gravity.TOP or Gravity.START).also {
                it.leftMargin = box.left.toInt(); it.topMargin = box.top.toInt()
            })
            v.pivotX = box.width() / 2f
            v.pivotY = box.height()
        }

        private fun centre(box: RectF) = Pair(box.centerX(), box.bottom - box.height() * FakeSprite.DRAWN / 2f)

        private val attackerCentre get() = centre(if (playerAttacks) playerBox else wildBox)
        private val targetCentre get() = centre(if (playerAttacks) wildBox else playerBox)

        /** The stage a look sees: the two creature centres, pixels per stage unit as the arena wiring computes it (centre distance / 226). */
        val stage: MoveFxStage = object : MoveFxStage {
            override val attackerX get() = attackerCentre.first
            override val attackerY get() = attackerCentre.second
            override val targetX get() = targetCentre.first
            override val targetY get() = targetCentre.second
            override val unit get() = hypot(targetX - attackerX, targetY - attackerY) / 226f

            override fun pose(attacker: Boolean, dx: Float, dy: Float, sx: Float, sy: Float, matrix: FloatArray?) {
                val v = if (attacker == playerAttacks) player else wild
                v.translationX = dx; v.translationY = dy; v.scaleX = sx; v.scaleY = sy; v.matrix = matrix
            }

            override fun jolt(dy: Float) = Unit   // the placeholder arena has no background layer to jolt (Earthquake, R7-08)

            override fun restore() {
                for (v in listOf(player, wild)) { v.translationX = 0f; v.translationY = 0f; v.scaleX = 1f; v.scaleY = 1f; v.matrix = null }
            }
        }

        /** Builds the look of [plan] and freezes it at [atMs] mockup ms (speed 1). Poses are applied first, since the effect view draws after the sprites. */
        fun showLook(plan: MoveFxPlan, atMs: Float, react: Boolean = true, seed: Long = 7L): MoveFxEffect? {
            val e = MoveFx.create(context, stage, plan, react, 1f, seed) ?: return null
            showEffect(e, atMs)
            return e
        }

        /** Freezes any effect built on [stage] at [atMs] real ms (applies its poses first). */
        fun showEffect(e: MoveFxEffect, atMs: Float) {
            e.draw(Canvas(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)), atMs)
            fx.showStill(e, atMs)
        }
    }

    private fun caption(ctx: Context, text: String): TextView = TextView(ctx).apply {
        this.text = text
        textSize = 11f
        setTextColor(ContextCompat.getColor(ctx, R.color.ag_text))
        setPadding(0, (2 * resources.displayMetrics.density).toInt(), 0, (2 * resources.displayMetrics.density).toInt())
    }

    /**
     * The contact sheet of one look: left column = our creature attacks the wild one, right column = the wild one attacks ours;
     * one row per moment of [momentsMs] (mockup ms).
     */
    fun sheet(ctx: Context, label: String, frame: Frame, plan: MoveFxPlan, momentsMs: List<Float>, react: Boolean = true): View =
        sheetOf(ctx, label, frame, momentsMs) { arena, ms -> arena.showLook(plan, ms, react) }

    /** [sheet] for any effect: [show] freezes the look on each arena at each moment (e.g. `arena.showEffect(myEffect(arena.stage), ms)`). */
    fun sheetOf(ctx: Context, label: String, frame: Frame, momentsMs: List<Float>, show: (ShotArena, Float) -> Unit): View {
        val d = ctx.resources.displayMetrics.density
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(ContextCompat.getColor(ctx, R.color.ag_base))
            val p = (8 * d).toInt(); setPadding(p, p, p, p)
        }
        root.addView(caption(ctx, "$label  -  ${frame.name.lowercase()}  -  " + momentsMs.joinToString(" / ") { "${it.toInt()} ms" }))
        val cols = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        for (playerAttacks in listOf(true, false)) {
            val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
            col.addView(caption(ctx, if (playerAttacks) "ours attacks the wild one" else "the wild one attacks ours"))
            for (ms in momentsMs) {
                val a = ShotArena(ctx, frame, playerAttacks)
                show(a, ms)
                col.addView(a, LinearLayout.LayoutParams(a.layoutParams.width, a.layoutParams.height).also { it.bottomMargin = (6 * d).toInt() })
            }
            cols.addView(col, LinearLayout.LayoutParams(-2, -2).also { it.rightMargin = (10 * d).toInt() })
        }
        root.addView(cols)
        return root
    }
}
