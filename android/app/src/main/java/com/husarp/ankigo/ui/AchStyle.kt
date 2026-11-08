package com.husarp.ankigo.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.view.View
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import com.husarp.ankigo.R

// R4-V2 (PLAN 79, picks 2A / 2C): how achievements LOOK: the icon of every table key, the five tier shapes and colours, and the
// round medal. Used by the page, the detail sheet and the popup.

object AchStyle {
    /** The drawable of every `icon` key in AchievementTable (a test checks that every key of the table is here). */
    val ICONS: Map<String, Int> = mapOf(
        // Study
        "cards" to R.drawable.ic_stats, "flame" to R.drawable.ic_flame, "combo" to R.drawable.ic_game, "calendar" to R.drawable.ic_calendar,
        "days" to R.drawable.ic_book, "clock" to R.drawable.ic_clock, "new-card" to R.drawable.ic_ach_cards, "good" to R.drawable.ic_check,
        "easy" to R.drawable.ic_ach_easy, "target" to R.drawable.ic_ach_target,
        // Battle: the app's own swords
        "crossed-swords" to R.drawable.ic_swords, "sword" to R.drawable.ic_swords, "heart-broken" to R.drawable.ic_ach_heart_broken,
        "shield" to R.drawable.ic_ach_shield, "miss" to R.drawable.ic_ach_miss, "bolt" to R.drawable.ic_bolt,
        "heart" to R.drawable.ic_heart,
        // Collection: the app's ball
        "ball" to R.drawable.ic_ball, "dex-caught" to R.drawable.ic_dex, "dex" to R.drawable.ic_ach_eye, "sparkle" to R.drawable.ic_ach_sparkle,
        "legendary" to R.drawable.ic_ach_crown, "types" to R.drawable.ic_palette, "evolve" to R.drawable.ic_ach_evolve,
        "trophy" to R.drawable.ic_ach_trophy, "level" to R.drawable.ic_ach_levels, "xp" to R.drawable.ic_star, "ball-throw" to R.drawable.ic_ball,
        "ball-catch" to R.drawable.ic_ach_ballcheck,
        // Economy
        "coin" to R.drawable.ic_ach_money, "bag" to R.drawable.ic_shop, "potion" to R.drawable.ic_ach_potion, "tm" to R.drawable.ic_ach_tm, "gift" to R.drawable.ic_gift,
        "secret-one-shot" to R.drawable.ic_ach_secret_one_shot,
        "secret-last-breath" to R.drawable.ic_ach_secret_last_breath,
        "secret-combo-20" to R.drawable.ic_ach_secret_combo_20,
        "secret-revolving-door" to R.drawable.ic_ach_secret_revolving_door,
        "secret-perfect-throw" to R.drawable.ic_ach_secret_perfect_throw,
        "secret-david-goliath" to R.drawable.ic_ach_secret_david_goliath,
        "secret-night-owl" to R.drawable.ic_ach_secret_night_owl,
        "secret-easy-street" to R.drawable.ic_ach_secret_easy_street,
        "secret-deja-vu" to R.drawable.ic_ach_secret_deja_vu,
        "secret-bully" to R.drawable.ic_ach_secret_bully,
        "secret-glow-up" to R.drawable.ic_ach_secret_glow_up,
        // Special (Ankimon badges)
        "badge-ball" to R.drawable.ic_ball, "badge-normal" to R.drawable.ic_dex, "badge-baby" to R.drawable.ic_heart_outline,
        "badge-ultra" to R.drawable.ic_bolt, "badge-legendary" to R.drawable.ic_ach_crown, "badge-mythical" to R.drawable.ic_ach_sparkle,
        "badge-level" to R.drawable.ic_up, "badge-item" to R.drawable.ic_gift, "badge-evolve" to R.drawable.ic_ach_evolve,
        "badge-fossil" to R.drawable.ic_ach_fossil, "badge-heal" to R.drawable.ic_ach_potion,
    )

    @DrawableRes fun icon(key: String): Int = ICONS[key] ?: R.drawable.ic_star

    /**
     * R6-07 (item 21): icons whose drawing is not centred in its 24 x 24 viewport, as the shift
     * (x, y in viewport units) that centres them in a medal. Measured from the paths' bounding boxes (centre of the box vs. 12, 12).
     */
    private val NUDGE: Map<Int, Pair<Float, Float>> = mapOf(
        R.drawable.ic_ach_fossil to (-1.8f to 0.6f), R.drawable.ic_bolt to (1f to 0f),
        R.drawable.ic_shop to (0.75f to 0.1f), R.drawable.ic_ach_cards to (0f to -1f), R.drawable.ic_ach_crown to (0f to -0.5f),
        R.drawable.ic_ach_easy to (0f to -0.8f), R.drawable.ic_ach_heart_broken to (0f to -0.68f), R.drawable.ic_gift to (0f to -0.76f),
    )

    /** The shift (x, y) in viewport units that centres [res] in a medal; (0, 0) for an icon that is centred already. */
    fun nudge(@DrawableRes res: Int): Pair<Float, Float> = NUDGE[res] ?: (0f to 0f)

    /** The colour of tier 0..4 (light and dark come from the colour resources). */
    @ColorRes fun tierRes(tier: Int): Int = when (tier.coerceIn(0, 4)) {
        0 -> R.color.ag_ach_0; 1 -> R.color.ag_ach_1; 2 -> R.color.ag_ach_2; 3 -> R.color.ag_ach_3; else -> R.color.ag_ach_4
    }

    fun tierColor(ctx: Context, tier: Int): Int = ctx.getColor(tierRes(tier))

    fun badgeColor(ctx: Context, key: String): Int = Ui.c(ctx, when (key) {
        "badge-ultra", "badge-legendary", "badge-level" -> R.color.ag_yellow
        "badge-baby", "badge-ball", "badge-heal" -> R.color.ag_red
        "badge-mythical", "badge-item" -> R.color.ag_mauve
        "badge-evolve" -> R.color.ag_green
        "badge-fossil" -> R.color.ag_peach
        else -> R.color.ag_sub0
    })

    /** What colour a medal uses: a tier's, a reached one-off's (teal), or the grey of a locked one. */
    fun medalColor(ctx: Context, tier: Int, special: Boolean = false): Int =
        if (special) ctx.getColor(R.color.ag_teal) else if (tier < 0) ctx.getColor(R.color.ag_ov) else tierColor(ctx, tier)

    /** The 16 points of the Mythical star in the 16 x 16 box: radius 7.4 / 5.9 / 2.9 (every 4th / other even / odd), from straight up. */
    fun starPoints(): List<Pair<Float, Float>> = List(16) { i ->
        val a = Math.toRadians(-90.0 + i * 22.5)
        val r = if (i % 2 == 1) 2.9 else if (i % 4 == 0) 7.4 else 5.9
        (8 + Math.cos(a) * r).toFloat() to (8 + Math.sin(a) * r).toFloat()
    }

    /** The shape of a tier in a 16 x 16 box (the mockup's own drawings): dot, diamond, hexagon, 4-point sparkle (Legendary), straight 8-point star (Mythical). */
    fun shapePath(tier: Int): Path = Path().apply {
        when (tier.coerceIn(0, 4)) {
            0 -> addCircle(8f, 8f, 5.2f, Path.Direction.CW)
            1 -> { moveTo(8f, 1.6f); lineTo(14.4f, 8f); lineTo(8f, 14.4f); lineTo(1.6f, 8f); close() }
            2 -> { moveTo(8f, 1.4f); lineTo(13.7f, 4.7f); lineTo(13.7f, 11.3f); lineTo(8f, 14.6f); lineTo(2.3f, 11.3f); lineTo(2.3f, 4.7f); close() }
            3 -> {
                moveTo(8f, 0.8f); cubicTo(8.6f, 5.4f, 10.6f, 7.4f, 15.2f, 8f); cubicTo(10.6f, 8.6f, 8.6f, 10.6f, 8f, 15.2f)
                cubicTo(7.4f, 10.6f, 5.4f, 8.6f, 0.8f, 8f); cubicTo(5.4f, 7.4f, 7.4f, 5.4f, 8f, 0.8f); close()
            }
            else -> {
                val pts = starPoints()
                moveTo(pts[0].first, pts[0].second)
                for (i in 1 until pts.size) lineTo(pts[i].first, pts[i].second)
                close()
            }
        }
    }

    /** A tier shape as a drawable of any size (for an ImageView). */
    fun shape(tier: Int, color: Int): Drawable = ShapeDrawable(tier, color)

    private class ShapeDrawable(tier: Int, color: Int) : Drawable() {
        private val src = shapePath(tier)
        private val path = Path()
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        override fun onBoundsChange(b: android.graphics.Rect) {
            val m = Matrix().apply { setScale(b.width() / 16f, b.height() / 16f); postTranslate(b.left.toFloat(), b.top.toFloat()) }
            src.transform(m, path)
        }
        override fun draw(canvas: Canvas) = canvas.drawPath(path, paint)
        override fun setAlpha(alpha: Int) { paint.alpha = alpha }
        override fun setColorFilter(cf: ColorFilter?) { paint.colorFilter = cf }
        @Deprecated("Deprecated in Java") override fun getOpacity() = PixelFormat.TRANSLUCENT
    }

    /** [a] over [b] by [t] (0..1), opaque. */
    fun mix(a: Int, b: Int, t: Float): Int = Bits.blend(a, b, t)

    /** [c] with its opacity set to [a] (0..1). */
    fun alpha(c: Int, a: Float): Int = (c and 0x00FFFFFF) or ((a * 255).toInt().coerceIn(0, 255) shl 24)
}

/**
 * The round medal (mockup `.med`): a disc tinted with the colour, a ring in the colour, the icon, and the tier's shape in a corner.
 * [set] with tier -1 and not special = locked (grey dashed ring). [special] = an earned one-off (colour alone, no corner mark).
 * [ground] is the colour behind the medal (the card), so the corner mark is cut out of the ring.
 */
class AchMedal(ctx: Context, private val sizeDp: Int) : View(ctx) {
    private var icon: Drawable? = null
    private var nudge = 0f to 0f
    private var tier = -1
    private var special = false
    private var ground = Ui.c(ctx, R.color.ag_card)
    private val disc = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val cut = Paint(Paint.ANTI_ALIAS_FLAG)
    private var badgeInk: Int? = null
    private var bare = false
    private var secret = false
    private val shapeDrawables = HashMap<Int, Drawable>()

    fun set(@DrawableRes iconRes: Int, tier: Int, special: Boolean = false, ground: Int = Ui.c(context, R.color.ag_card), bareIcon: Boolean = false) {
        badgeInk = if (special) AchStyle.ICONS.entries.firstOrNull { it.key.startsWith("badge-") && it.value == iconRes }?.let { AchStyle.badgeColor(context, it.key) } else null
        secret = AchStyle.ICONS.any { it.key.startsWith("secret-") && it.value == iconRes }
        bare = bareIcon || iconRes == R.drawable.ic_ach_lock || secret
        this.tier = tier; this.special = special; this.ground = ground
        icon = context.getDrawable(iconRes)?.mutate()
        nudge = AchStyle.nudge(iconRes)
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val s = Ui.dp(context, sizeDp)
        setMeasuredDimension(s, s)
    }

    override fun onDraw(canvas: Canvas) {
        val s = width.toFloat()
        val color = if (secret) Ui.c(context, R.color.ag_teal) else badgeInk ?: AchStyle.medalColor(context, tier, special)
        val locked = tier < 0 && !special
        val stroke = (s * 0.045f).coerceAtLeast(Ui.dp(context, 1.5f))
        val r = s * 0.5f - stroke / 2 - s * 0.04f // leaves room for the corner mark
        val cx = s * 0.5f - s * 0.02f; val cy = cx
        disc.color = AchStyle.mix(color, ground, if (locked) 0.06f else 0.14f)
        if (!bare) canvas.drawCircle(cx, cy, r, disc)
        ring.strokeWidth = stroke
        ring.color = color
        ring.pathEffect = if (locked) DashPathEffect(floatArrayOf(stroke * 2.2f, stroke * 1.6f), 0f) else null
        if (!bare) canvas.drawCircle(cx, cy, r, ring)
        icon?.let {
            val h = s * if (bare) 0.45f else 0.27f
            val ox = nudge.first * h / 12f; val oy = nudge.second * h / 12f // the icon box is 2h wide for 24 viewport units
            it.setBounds((cx - h + ox).toInt(), (cy - h + oy).toInt(), (cx + h + ox).toInt(), (cy + h + oy).toInt())
            it.setTint(if (locked) AchStyle.mix(color, ground, 0.8f) else color)
            it.draw(canvas)
        }
        if (locked || special || bare) return
        // Tier shapes remain on tiered medals; one-offs use colour alone.
        val m = s * 0.15f
        val mx = s * 0.78f; val my = s * 0.78f
        cut.color = ground
        canvas.drawCircle(mx, my, m + stroke * 0.8f, cut)
        val d = shapeDrawables.getOrPut(tier) { AchStyle.shape(tier, color) }
        val h = (m * 1.15f).toInt()
        d.setBounds((mx - h).toInt(), (my - h).toInt(), (mx + h).toInt(), (my + h).toInt())
        d.draw(canvas)
    }
}
