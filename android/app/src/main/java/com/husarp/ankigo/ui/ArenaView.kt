package com.husarp.ankigo.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.Keyframe
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.GradientDrawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.husarp.ankigo.R
import com.husarp.ankigo.game.Type
import com.husarp.ankigo.gfx.SpriteView
import kotlin.math.max
import kotlin.math.min

/** Style of the little number that floats up from a sprite (mockup `.dmg`, `.dmg.big`, `.dmg.heal`). */
enum class FloatStyle { DAMAGE, BIG, HEAL, MISS }

/**
 * The battle arena (mockup `.arena`): gradient sky, two platforms, the two bobbing sprites, the two info cards, the
 * catch tag and the catch-moment overlay (ball, stars, banner). It lays everything out by the mockup's dp values,
 * scaled to its own height (212 dp is the reference; sprite boxes and platforms shrink on a short window), and offers
 * the animation pieces the [EventPlayer] strings together. It draws what it is told; it never reads the game.
 */
class ArenaView(ctx: Context) : FrameLayout(ctx) {
    // ---- sprite boxes: holder (bobs) > sprite (lunge / shake / faint / enter) ----
    val enemySprite = SpriteView(ctx).apply { scaleDp = 2f }
    val playerSprite = SpriteView(ctx).apply { scaleDp = 2f }
    private val ball = ImageView(ctx).apply { visibility = GONE }
    private val enemyHolder = FrameLayout(ctx).apply { clipChildren = false; clipToPadding = false }
    private val playerHolder = FrameLayout(ctx).apply { clipChildren = false; clipToPadding = false }
    private val platEnemy = View(ctx).apply { background = oval(Ui.c(ctx, R.color.ag_plat2)) }
    private val platPlayer = View(ctx).apply { background = oval(Ui.c(ctx, R.color.ag_plat)) }

    // ---- enemy info card + catch tag (one block so the tag always sits under the card) ----
    private val eName = Ui.text(ctx, 14f, 900, R.color.ag_text).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
    private val eLevel = Ui.text(ctx, 12f, 800, R.color.ag_sub0).apply { maxLines = 1 }
    private val eTypes = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
    private val eHpText = Ui.text(ctx, 11f, 800, R.color.ag_sub0)
    private val eHp = BarView(ctx)
    private val enemyCard = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        background = Ui.rounded(translucentCard(ctx), 12, ctx)
        setPadding(dpi(10), dpi(7), dpi(10), dpi(8))
    }
    private val catchTag = Ui.text(ctx, 10f, 900, R.color.ag_sub0).apply {
        maxLines = 1
        // 10 sp / 900 on ag_card at 85 % opacity. The visible pill is inset 6 dp from the view edge, so the touch area
        // is bigger than what you see; the padding must be set after the background (an inset drawable resets it).
        background = android.graphics.drawable.InsetDrawable(Ui.rounded(Ui.c(ctx, R.color.ag_card), 8, ctx), dpi(6))
        setPadding(dpi(13), dpi(9), dpi(13), dpi(9))
        alpha = 0.85f
    }
    private val enemyBlock = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }

    // ---- player info card ----
    private val pName = Ui.text(ctx, 14f, 900, R.color.ag_text).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
    private val pLevel = Ui.text(ctx, 12f, 800, R.color.ag_sub0).apply { maxLines = 1 }
    private val pHp = BarView(ctx)
    private val pHpText = Ui.text(ctx, 11f, 800, R.color.ag_sub0).apply { maxLines = 1 }
    private val pXpText = Ui.text(ctx, 11f, 800, R.color.ag_sub0).apply { maxLines = 1 }
    private val pXp = BarView(ctx)
    private val pNums = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
    private val playerCard = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        background = Ui.rounded(translucentCard(ctx), 12, ctx)
        setPadding(dpi(10), dpi(7), dpi(10), dpi(8))
    }

    // ---- catch moment ----
    private val stars = PanelStars(ctx).apply { visibility = GONE }
    private val bannerTitle = Ui.text(ctx, 20f, 900, R.color.ag_mauve).apply { letterSpacing = -0.01f }
    private val bannerLine1 = Ui.text(ctx, 12f, 700, R.color.ag_sub0)
    private val bannerLine2 = Ui.text(ctx, 12f, 800, R.color.ag_mauve)
    private val banner = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        background = Ui.rounded(Ui.c(ctx, R.color.ag_card), 16, ctx)
        setPadding(dpi(16), dpi(10), dpi(16), dpi(10))
        minimumWidth = dpi(170)
        elevation = Ui.dp(ctx, 6f)
        visibility = GONE
    }

    // ---- state shown ----
    /** Name shown on the wild card ("" while no wild Pokémon is on stage). */
    var enemyName: String = ""
        private set
    var playerUid: String? = null
        private set
    var playerLevel: Int = 0
        private set
    private var pHpShown = 0
    private var pXpShown = 0f
    private var ballOnField = false
    var onCatchTagTap: () -> Unit = {}

    private var compact = false
    private var studyStrip = false
    private val live = LinkedHashSet<Animator>()
    private val floats = ArrayList<View>()
    private val bobs = ArrayList<ObjectAnimator>()
    private var hpCount: ValueAnimator? = null

    init {
        clipChildren = true
        background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Ui.c(ctx, R.color.ag_arena_top), Ui.c(ctx, R.color.ag_arena_bot)))

        enemyHolder.addView(enemySprite, FrameLayout.LayoutParams(-1, -1))
        enemyHolder.addView(ball, FrameLayout.LayoutParams(dpi(48), dpi(48), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).also { it.bottomMargin = dpi(6) })
        playerHolder.addView(playerSprite, FrameLayout.LayoutParams(-1, -1))

        // enemy card: name + Lv, type chips, HP bar (no numbers)
        val eRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        eRow.addView(eName, LinearLayout.LayoutParams(0, -2, 1f))
        eRow.addView(eLevel, LinearLayout.LayoutParams(-2, -2).also { it.marginStart = dpi(5) })
        enemyCard.addView(eRow, LinearLayout.LayoutParams(-1, -2))
        enemyCard.addView(eTypes, LinearLayout.LayoutParams(-2, -2).also { it.topMargin = dpi(4) })
        enemyCard.addView(eHp, LinearLayout.LayoutParams(-1, dpi(7)).also { it.topMargin = dpi(6) })
        enemyCard.addView(eHpText)
        enemyBlock.addView(enemyCard, LinearLayout.LayoutParams(-1, -2))
        enemyBlock.addView(catchTag, LinearLayout.LayoutParams(-2, -2).also { it.marginStart = -dpi(6) })
        catchTag.setOnClickListener { onCatchTagTap() }

        // player card: name + Lv, HP bar, one row "58 / 71" left + "XP 640 / 1000" right, XP bar
        val pRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        pRow.addView(pName, LinearLayout.LayoutParams(0, -2, 1f))
        pRow.addView(pLevel, LinearLayout.LayoutParams(-2, -2).also { it.marginStart = dpi(5) })
        playerCard.addView(pRow, LinearLayout.LayoutParams(-1, -2))
        playerCard.addView(pHp, LinearLayout.LayoutParams(-1, dpi(7)).also { it.topMargin = dpi(6) })
        pNums.addView(pHpText, LinearLayout.LayoutParams(0, -2, 1f))
        pNums.addView(pXpText, LinearLayout.LayoutParams(-2, -2).also { it.marginStart = dpi(6) })
        playerCard.addView(pNums, LinearLayout.LayoutParams(-1, -2).also { it.topMargin = dpi(3) })
        pXp.setTrackColor(Ui.c(ctx, R.color.ag_s0))
        playerCard.addView(pXp, LinearLayout.LayoutParams(-1, dpi(3)).also { it.topMargin = dpi(4) })

        banner.addView(bannerTitle)
        banner.addView(bannerLine1)
        banner.addView(bannerLine2)

        addView(platEnemy, LayoutParams(0, 0))
        addView(platPlayer, LayoutParams(0, 0))
        addView(enemyHolder, LayoutParams(0, 0))
        addView(playerHolder, LayoutParams(0, 0))
        addView(enemyBlock, LayoutParams(0, -2))
        addView(playerCard, LayoutParams(0, -2))
        addView(stars, LayoutParams(-1, -1))
        addView(banner, LayoutParams(-2, -2, Gravity.TOP or Gravity.LEFT).also { it.leftMargin = dpi(12); it.topMargin = dpi(14) })

        // Nothing until the first encounter.
        setEnemyShown(false)
        setCatchTag(false, false, visible = false)
    }

    // ---------------------------------------------------------------- layout

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val ref = Ui.dp(context, 212f)
        val minH = Ui.dp(context, if (compact) 70f else 140f).toInt()
        val hMode = MeasureSpec.getMode(heightMeasureSpec)
        val hSize = MeasureSpec.getSize(heightMeasureSpec)
        val h = when (hMode) {
            MeasureSpec.EXACTLY -> hSize
            MeasureSpec.AT_MOST -> min(hSize, minH)
            else -> minH
        }
        val w = MeasureSpec.getSize(widthMeasureSpec)
        // Sprite boxes and platforms follow the arena height; a tall window pushes the wild one a bit lower so the
        // two sides don't sit in opposite corners.
        val s = (h / ref).coerceIn(if (compact) 0.45f else 0.66f, 1.5f)
        val extra = max(0f, h - ref * s)
        val down = (extra * 0.75f).toInt()
        fun px(v: Float) = (Ui.dp(context, v) * s).toInt()
        place(platEnemy, px(150f), px(30f), Gravity.TOP or Gravity.RIGHT, 0, px(92f) + down, px(22f), 0)
        place(platPlayer, px(180f), px(40f), Gravity.BOTTOM or Gravity.LEFT, px(4f), 0, 0, -px(8f))
        place(enemyHolder, px(110f), px(100f), Gravity.TOP or Gravity.RIGHT, 0, px(12f) + down, px(42f), 0)
        place(playerHolder, px(140f), px(128f), Gravity.BOTTOM or Gravity.LEFT, px(22f), 0, 0, px(6f))
        val infoMax = ((w - dpi(30)) / 2).coerceAtLeast(dpi(if (compact) 100 else 120))
        val edge = if (compact) 6 else 10
        place(enemyBlock, min(dpi(if (compact) 130 else 166), infoMax), -2, Gravity.TOP or Gravity.LEFT, dpi(edge), dpi(edge), 0, 0)
        place(playerCard, min(dpi(if (compact) 130 else 170), infoMax), -2, Gravity.BOTTOM or Gravity.RIGHT, 0, 0, dpi(edge), dpi(if (compact) 6 else 12))
        if (studyStrip) {
            // Approved 146 dp strip geometry, scaled only when landscape supplies extra arena height.
            val scale = (h / Ui.dp(context, 146f)).coerceIn(.6f, 1.5f)
            fun sp(v: Int) = (dpi(v) * scale).toInt()
            place(platEnemy, sp(120), sp(24), Gravity.TOP or Gravity.RIGHT, 0, sp(70), sp(16), 0)
            place(platPlayer, sp(150), sp(34), Gravity.BOTTOM or Gravity.LEFT, 0, 0, 0, -sp(10))
            place(enemyHolder, sp(96), sp(80), Gravity.TOP or Gravity.RIGHT, 0, sp(4), sp(26), 0)
            place(playerHolder, sp(116), sp(96), Gravity.BOTTOM or Gravity.LEFT, sp(14), 0, 0, sp(2))
            place(enemyBlock, min(dpi(138), infoMax), -2, Gravity.TOP or Gravity.LEFT, dpi(8), dpi(8), 0, 0)
            place(playerCard, min(dpi(142), infoMax), -2, Gravity.BOTTOM or Gravity.RIGHT, 0, 0, dpi(8), dpi(8))
        }
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY))
    }

    private fun place(v: View, w: Int, h: Int, gravity: Int, left: Int, top: Int, right: Int, bottom: Int) {
        val lp = v.layoutParams as LayoutParams
        lp.width = w; lp.height = h; lp.gravity = gravity
        lp.setMargins(left, top, right, bottom)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!ValueAnimator.areAnimatorsEnabled() || context.getSharedPreferences("study", 0).getBoolean("reduceMotion", false)) return
        if (bobs.isEmpty()) {
            bobs += bob(enemyHolder, 1600)
            bobs += bob(playerHolder, 1900)
        }
        bobs.forEach { if (!it.isStarted) it.start() }
    }

    /** The idle bob must not keep the screen redrawing while the app is in the background. */
    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        bobs.forEach {
            if (visibility == VISIBLE && ValueAnimator.areAnimatorsEnabled() && !context.getSharedPreferences("study", 0).getBoolean("reduceMotion", false)) { if (it.isPaused) it.resume() else if (!it.isStarted) it.start() }
            else if (it.isStarted && !it.isPaused) it.pause()
        }
    }

    override fun onDetachedFromWindow() {
        bobs.forEach { it.cancel() }
        bobs.clear()
        cancelAnimations()
        super.onDetachedFromWindow()
    }

    /** Idle bob: translationY 0 -> -3 dp, alternating (mockup `bob`). */
    private fun bob(v: View, ms: Long): ObjectAnimator =
        ObjectAnimator.ofFloat(v, View.TRANSLATION_Y, 0f, -Ui.dp(context, 3f)).apply {
            duration = ms
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
        }

    // ---------------------------------------------------------------- enemy side

    /** Shows or hides the whole wild side (card, tag, sprite, platform stays). */
    fun setEnemyShown(shown: Boolean) {
        val vis = if (shown) VISIBLE else INVISIBLE
        enemyBlock.visibility = vis
        enemyHolder.visibility = vis
        if (!shown) enemyName = ""
    }

    fun setEnemyInfo(name: String, level: Int, shiny: Boolean, types: List<Type>) {
        enemyName = name
        eName.text = if (shiny) {
            SpannableStringBuilder(name).also {
                val at = it.length
                it.append("  ★")
                it.setSpan(ForegroundColorSpan(Ui.c(context, R.color.ag_yellow)), at, it.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                it.setSpan(RelativeSizeSpan(12f / 14f), at, it.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        } else name
        eLevel.text = "Lv $level"
        eTypes.removeAllViews()
        for (t in types) eTypes.addView(Ui.typeChip(context, t), LinearLayout.LayoutParams(-2, -2).also { it.marginEnd = dpi(3) })
    }

    fun setEnemyHp(hp: Int, max: Int, animate: Boolean) {
        val f = if (max > 0) hp.toFloat() / max else 0f
        eHp.set(f, Ui.hpColor(context, f), animate)
        eHpText.text = DisplayNumbers.hp(hp, max)
    }

    /**
     * "NEW · will catch" (mauve), "will catch" (mauve), "NEW · will defeat" and "caught before · XP" (grey): what the
     * auto rule will do when the wild one faints. Tapping it flips the choice (the activity handles the tap).
     */
    fun setCatchTag(willCatch: Boolean, isNew: Boolean, visible: Boolean = true) {
        catchTag.visibility = if (compact) GONE else if (visible) VISIBLE else INVISIBLE
        val (text, mauve) = when {
            willCatch && isNew -> "NEW · will catch" to true
            willCatch -> "will catch" to true
            isNew -> "NEW · will defeat" to false
            else -> "caught before · XP" to false
        }
        catchTag.text = text
        catchTag.setTextColor(Ui.c(context, if (mauve) R.color.ag_mauve else R.color.ag_sub0))
    }

    /**
     * Small-window look (picture-in-picture, floating battle): the info cards keep name, level, HP bar and HP numbers
     * (no type chips, catch tag, XP text or XP bar), so the two cards never collide in a ~240x130 dp window.
     */
    fun setCompact(on: Boolean) {
        if (compact == on) return
        compact = on
        val small = if (on) GONE else VISIBLE
        eTypes.visibility = small
        pXpText.visibility = small
        pXp.visibility = small
        if (on) catchTag.visibility = GONE
        val name = if (on) 12f else 14f
        val lvl = if (on) 11f else 12f
        for (t in listOf(eName, pName)) t.setTextSize(TypedValue.COMPLEX_UNIT_SP, name)
        for (t in listOf(eLevel, pLevel)) t.setTextSize(TypedValue.COMPLEX_UNIT_SP, lvl)
        val padV = if (on) 5 else 7
        for (c in listOf(enemyCard, playerCard)) c.setPadding(dpi(10), dpi(padV), dpi(10), dpi(if (on) 6 else 8))
        bannerTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, if (on) 15f else 20f)
        banner.minimumWidth = dpi(if (on) 110 else 170)
        requestLayout()
    }

    /** Restores the type and numeric rows hidden by PiP compact mode, with the study mockup's metrics. */
    fun setStudyStrip(on: Boolean) {
        studyStrip = on
        eTypes.visibility = VISIBLE
        pNums.visibility = VISIBLE
        pXpText.visibility = VISIBLE
        pXp.visibility = if (on) GONE else VISIBLE
        for (t in listOf(eName, pName)) t.textSize = if (on) 12.5f else 14f
        for (t in listOf(eLevel, pLevel)) t.textSize = if (on) 11f else 12f
        for (t in listOf(eHpText, pHpText, pXpText)) t.textSize = if (on) 10f else 11f
        for (c in listOf(enemyCard, playerCard)) {
            c.elevation = Ui.dp(context, 2f)
            c.background = Ui.rounded(translucentCard(context), if (on) 10 else 12, context)
            c.setPadding(dpi(if (on) 8 else 10), dpi(if (on) 5 else 7), dpi(if (on) 8 else 10), dpi(if (on) 6 else 8))
        }
        for (bar in listOf(eHp, pHp)) (bar.layoutParams as LinearLayout.LayoutParams).apply {
            height = dpi(if (on) 6 else 7); topMargin = dpi(if (on) 4 else 6)
        }
        requestLayout()
    }

    // ---------------------------------------------------------------- player side

    fun setPlayerInfo(uid: String?, name: String, level: Int) {
        playerUid = uid
        playerLevel = level
        pName.text = name
        pLevel.text = "Lv $level"
    }

    fun setPlayerLevel(level: Int) {
        playerLevel = level
        pLevel.text = "Lv $level"
    }

    fun setPlayerName(name: String) { pName.text = name }

    /** HP bar eases 450 ms; the "58 / 71" counts along. */
    fun setPlayerHp(hp: Int, max: Int, animate: Boolean) {
        val f = if (max > 0) hp.toFloat() / max else 0f
        pHp.set(f, Ui.hpColor(context, f), animate)
        hpCount?.cancel()
        val from = pHpShown
        pHpShown = hp
        if (!animate || from == hp || !isAttachedToWindow) { pHpText.text = "$hp / $max"; return }
        hpCount = ValueAnimator.ofInt(from, hp).setDuration(450).also { a ->
            a.addUpdateListener { pHpText.text = "${it.animatedValue as Int} / $max" }
            a.addListener(object : AnimatorListenerAdapter() { override fun onAnimationEnd(an: Animator) { pHpText.text = "$hp / $max" } })
            a.start()
        }
    }

    /** XP bar (3 dp, sky) and the "XP 640 / 1000" label. */
    fun setPlayerXp(current: Int, needed: Int, animate: Boolean) {
        val f = (if (needed > 0) current.toFloat() / needed else 1f).coerceIn(0f, 1f)
        pXpShown = f
        pXp.set(f, Ui.c(context, R.color.ag_sky), animate)
        pXpText.text = DisplayNumbers.xp(current, needed)
    }

    val xpShown: Float get() = pXpShown

    // ---------------------------------------------------------------- hit animations

    /** The user steps toward the target and back (mockup lunge: +14/-10 dp for us, -14/+10 for the wild one). */
    fun lunge(byPlayer: Boolean, ms: Long) {
        val spr = if (byPlayer) playerSprite else enemySprite
        val dx = Ui.dp(context, if (byPlayer) 14f else -14f)
        val dy = Ui.dp(context, if (byPlayer) -10f else 10f)
        val x = PropertyValuesHolder.ofKeyframe(View.TRANSLATION_X, Keyframe.ofFloat(0f, 0f), Keyframe.ofFloat(0.5f, dx), Keyframe.ofFloat(1f, 0f))
        val y = PropertyValuesHolder.ofKeyframe(View.TRANSLATION_Y, Keyframe.ofFloat(0f, 0f), Keyframe.ofFloat(0.5f, dy), Keyframe.ofFloat(1f, 0f))
        launch(ObjectAnimator.ofPropertyValuesHolder(spr, x, y).setDuration(ms).also { it.interpolator = DecelerateInterpolator() }) {
            spr.translationX = 0f; spr.translationY = 0f
        }
    }

    /** Shake + two white flashes on the target (mockup `.hit`, 380 ms). */
    fun hit(onPlayer: Boolean, ms: Long) {
        val spr = if (onPlayer) playerSprite else enemySprite
        val d = Ui.dp(context, 1f)
        val shake = PropertyValuesHolder.ofKeyframe(View.TRANSLATION_X,
            Keyframe.ofFloat(0f, 0f), Keyframe.ofFloat(0.2f, -6 * d), Keyframe.ofFloat(0.4f, 6 * d),
            Keyframe.ofFloat(0.6f, -4 * d), Keyframe.ofFloat(0.8f, 3 * d), Keyframe.ofFloat(1f, 0f))
        launch(ObjectAnimator.ofPropertyValuesHolder(spr, shake).setDuration(ms).also { it.interpolator = LinearInterpolator() }) { spr.translationX = 0f }
        flash(spr, ms, 2)
    }

    /** [times] white flashes over [ms]. */
    fun flash(spr: SpriteView, ms: Long, times: Int) {
        val values = FloatArray(times * 2 + 1) { if (it % 2 == 1) 1f else 0f }
        val a = ValueAnimator.ofFloat(*values).setDuration(ms)
        a.interpolator = LinearInterpolator()
        a.addUpdateListener { spr.flash = it.animatedValue as Float }
        launch(a) { spr.flash = 0f }
    }

    fun flashPlayer(ms: Long, times: Int) = flash(playerSprite, ms, times)

    /** A number floats up from the sprite and fades (mockup `.dmg`, 1 s). */
    fun floatNumber(onPlayer: Boolean, text: String, style: FloatStyle) {
        val holder = if (onPlayer) playerHolder else enemyHolder
        val spr = if (onPlayer) playerSprite else enemySprite
        val (size, colorRes) = when (style) {
            FloatStyle.DAMAGE -> 18f to R.color.ag_red
            FloatStyle.BIG -> 22f to R.color.ag_peach
            FloatStyle.HEAL -> 18f to R.color.ag_green
            FloatStyle.MISS -> 13f to R.color.ag_sub0
        }
        val tv = Ui.text(context, size, 900, colorRes)
        tv.text = text
        tv.setShadowLayer(Ui.dp(context, 6f), 0f, Ui.dp(context, 1f), Ui.c(context, R.color.ag_base))
        tv.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED)
        val drawn = spr.drawnHeight()
        val top = if (drawn > 0) max(holder.top - dpi(8), holder.bottom - drawn - dpi(8)) else holder.top + dpi(6)
        val lp = LayoutParams(-2, -2, Gravity.TOP or Gravity.LEFT)
        lp.leftMargin = holder.left + (holder.width - tv.measuredWidth) / 2
        lp.topMargin = top
        addView(tv, lp)
        floats += tv
        tv.animate().translationY(-Ui.dp(context, 34f)).alpha(0f).setDuration(1000).setInterpolator(DecelerateInterpolator())
            .withEndAction { removeView(tv); floats -= tv }.start()
    }

    /** Sprite sinks 40 dp and fades (mockup `.faint`, 500 ms). */
    fun faint(player: Boolean, ms: Long) {
        val spr = if (player) playerSprite else enemySprite
        spr.animate().translationY(Ui.dp(context, 40f)).alpha(0f).setDuration(ms).setInterpolator(AccelerateInterpolator()).start()
    }

    /** Slides in from the right (mockup `.enter`, 450 ms). */
    fun enter(player: Boolean, ms: Long) {
        val spr = if (player) playerSprite else enemySprite
        spr.animate().cancel()
        spr.translationX = Ui.dp(context, 60f); spr.translationY = 0f; spr.alpha = 0f; spr.scaleX = 1f; spr.scaleY = 1f; spr.flash = 0f
        spr.animate().translationX(0f).alpha(1f).setDuration(ms).setInterpolator(DecelerateInterpolator()).start()
    }

    /** The wild one runs off to the right and fades. */
    fun enemyLeave(ms: Long) {
        enemySprite.animate().translationX(Ui.dp(context, 70f)).alpha(0f).setDuration(ms).setInterpolator(AccelerateInterpolator()).start()
    }

    /** Back to the idle look: no leftover offset, fade, scale or flash on a sprite. */
    fun reset(player: Boolean) {
        val spr = if (player) playerSprite else enemySprite
        spr.animate().cancel()
        spr.translationX = 0f; spr.translationY = 0f; spr.alpha = 1f; spr.scaleX = 1f; spr.scaleY = 1f; spr.flash = 0f
        spr.visibility = VISIBLE
    }

    // ---------------------------------------------------------------- catch moment

    val ballShown: Boolean get() = ballOnField

    /** Wild sprite shrinks into the ball (mockup `.gone`, 350 ms, bright flash). */
    fun enemyIntoBall(ms: Long) {
        val spr = enemySprite
        spr.pivotX = spr.width / 2f; spr.pivotY = spr.height.toFloat()
        spr.animate().scaleX(0f).scaleY(0f).alpha(0f).setDuration(ms).setInterpolator(AccelerateInterpolator()).start()
        val a = ValueAnimator.ofFloat(0f, 1f).setDuration(ms)
        a.addUpdateListener { spr.flash = it.animatedValue as Float }
        launch(a) { spr.flash = 0f }
    }

    /** The wild one pops back out of a ball that broke. */
    fun enemyOutOfBall(ms: Long) {
        val spr = enemySprite
        spr.pivotX = spr.width / 2f; spr.pivotY = spr.height.toFloat()
        spr.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(ms).setInterpolator(DecelerateInterpolator()).start()
    }

    /** The ball drops onto the enemy platform with a bounce (mockup `drop`, 450 ms). [item] is e.g. "ultra-ball". */
    fun dropBall(item: String, ms: Long) {
        ballOnField = true
        Ui.itemIcon(context, item, ball)
        ball.visibility = VISIBLE
        ball.rotation = 0f
        ball.pivotX = ball.layoutParams.width / 2f; ball.pivotY = ball.layoutParams.height * 0.9f
        ball.translationY = -Ui.dp(context, 70f)
        ball.animate().translationY(0f).setDuration(ms).setInterpolator(OvershootInterpolator(1.4f)).start()
    }

    /** Wobbles three times (mockup `wobble`, 1.8 s). */
    fun wobbleBall(ms: Long) {
        val r = PropertyValuesHolder.ofKeyframe(View.ROTATION,
            Keyframe.ofFloat(0f, 0f), Keyframe.ofFloat(0.08f, -22f), Keyframe.ofFloat(0.22f, 0f), Keyframe.ofFloat(0.30f, 22f),
            Keyframe.ofFloat(0.44f, 0f), Keyframe.ofFloat(0.52f, -22f), Keyframe.ofFloat(0.66f, 0f), Keyframe.ofFloat(1f, 0f))
        launch(ObjectAnimator.ofPropertyValuesHolder(ball, r).setDuration(ms).also { it.interpolator = AccelerateDecelerateInterpolator() }) { ball.rotation = 0f }
    }

    fun clearBall() {
        ball.animate().cancel()
        ball.visibility = GONE
        ball.translationY = 0f; ball.rotation = 0f
        ballOnField = false
    }

    fun showStars(on: Boolean) {
        stars.visibility = if (on) VISIBLE else GONE
        stars.running = on
    }

    /** "Gotcha!" banner top-left (mockup `.banner`); [line2] is the optional mauve line ("New in your Pokédex"). */
    fun showBanner(title: String, line1: String, line2: String?) {
        bannerTitle.text = title
        bannerLine1.text = line1
        bannerLine2.text = line2 ?: ""
        bannerLine2.visibility = if (line2 == null) GONE else VISIBLE
        banner.visibility = VISIBLE
        banner.animate().cancel()
        banner.scaleX = 0.97f; banner.scaleY = 0.97f; banner.alpha = 0.6f
        banner.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(300).start()
    }

    fun hideBanner() { banner.animate().cancel(); banner.visibility = GONE }

    // ---------------------------------------------------------------- housekeeping

    /** Stops every running animation and removes leftovers (floating numbers too); sprites keep whatever they show. */
    fun cancelAnimations() {
        stopAnimators()
        floats.toList().forEach { it.animate().cancel(); removeView(it) }
        floats.clear()
    }

    private fun stopAnimators() {
        live.toList().forEach { it.cancel() }
        live.clear()
        hpCount?.cancel()
        enemySprite.animate().cancel(); playerSprite.animate().cancel(); ball.animate().cancel(); banner.animate().cancel()
    }

    /** Idle look: no catch overlay, both sprites at rest (numbers still floating are left to finish). */
    fun resetFx() {
        stopAnimators()
        clearBall()
        showStars(false)
        hideBanner()
        reset(false)
        reset(true)
    }

    /** Only the wild side back to rest (a new encounter is about to slide in). */
    fun resetEnemySide() {
        enemySprite.animate().cancel()
        clearBall()
        showStars(false)
        hideBanner()
        reset(false)
    }

    /** The wild card + tag alone (hidden while the ball is on the field). */
    fun setEnemyCardShown(shown: Boolean) { enemyBlock.visibility = if (shown) VISIBLE else INVISIBLE }

    val playerHpShown: Int get() = pHpShown

    private fun launch(a: Animator, onEnd: () -> Unit) {
        live += a
        a.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(an: Animator) { live -= a; onEnd() }
            override fun onAnimationCancel(an: Animator) { live -= a }
        })
        a.start()
    }

    private fun dpi(v: Int) = Ui.dp(context, v)

    companion object {
        private fun translucentCard(ctx: Context): Int = (Ui.c(ctx, R.color.ag_card) and 0x00FFFFFF) or (0xEB shl 24)
        private fun oval(color: Int) = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) }
    }
}

/** Six twinkling 4-point stars over the arena (mockup `.stars`, 1.6 s loop, staggered by 0.25 s). */
private class PanelStars(ctx: Context) : View(ctx) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Ui.c(ctx, R.color.ag_yellow) }
    private val path = Path()
    private val spots = arrayOf(58f to 18f, 78f to 30f, 66f to 52f, 88f to 12f, 50f to 40f, 92f to 44f)
    private var clock: ValueAnimator? = null
    private var t0 = 0L

    var running: Boolean = false
        set(v) {
            field = v
            clock?.cancel(); clock = null
            if (v && isAttachedToWindow) start()
        }

    private fun start() {
        t0 = System.nanoTime()
        clock = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1600
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { invalidate() }
            start()
        }
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); if (running) start() }
    override fun onDetachedFromWindow() { clock?.cancel(); clock = null; super.onDetachedFromWindow() }

    override fun onTouchEvent(event: MotionEvent?): Boolean = false

    override fun onDraw(canvas: Canvas) {
        val size = Ui.dp(context, 9f)
        val now = (System.nanoTime() - t0) / 1_000_000_000f
        spots.forEachIndexed { i, (px, py) ->
            // 0 -> 1 -> 0 over 1.6 s, each star 0.25 s later than the one before.
            val ph = (((now - i * 0.25f) % 1.6f) + 1.6f) % 1.6f / 1.6f
            val k = if (ph < 0.5f) ph * 2f else (1f - ph) * 2f
            val e = k * k * (3f - 2f * k)
            val scale = 0.4f + (1.3f - 0.4f) * e
            paint.alpha = ((0.3f + 0.7f * e) * 255).toInt()
            val cx = width * px / 100f
            val cy = height * py / 100f
            val r = size / 2f * scale
            path.rewind()
            // 4-point star (mockup clip-path polygon)
            path.moveTo(cx, cy - r)
            path.lineTo(cx + r * 0.22f, cy - r * 0.22f)
            path.lineTo(cx + r, cy)
            path.lineTo(cx + r * 0.22f, cy + r * 0.22f)
            path.lineTo(cx, cy + r)
            path.lineTo(cx - r * 0.22f, cy + r * 0.22f)
            path.lineTo(cx - r, cy)
            path.lineTo(cx - r * 0.22f, cy - r * 0.22f)
            path.close()
            canvas.drawPath(path, paint)
        }
    }
}
