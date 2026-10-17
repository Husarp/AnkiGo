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
import android.view.ViewConfiguration
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
import com.husarp.ankigo.data.Backdrops
import com.husarp.ankigo.data.Sprites
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
    /** The arena's background (R7-07): the plain gradient or a downloaded battle picture; it is a [com.husarp.ankigo.ui.movefx.Joltable] too. */
    private val backdrop = ArenaBackdrop(ctx)

    // ---- enemy info card ----
    private val eName = FitName.make(ctx)
    private val eLevel = Ui.text(ctx, 12f, 800, R.color.ag_sub0).apply { maxLines = 1 }
    private val eTypes = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
    private val eTypeRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private val eStatus = StatusRow(ctx)           // status badge row directly under the HP bar (item 95)
    private val pStatus = StatusRow(ctx)
    // elevated above the stat cards (the study strip lifts them by 2 dp, and a higher z draws over any sibling order)
    private val bubble = StatusBubble(ctx).apply { visibility = GONE; elevation = Ui.dp(ctx, 8f); outlineProvider = null }
    private val eHpText = Ui.text(ctx, 11f, 800, R.color.ag_sub0)
    private val eHp = BarView(ctx).apply { ghostOnDrop = true }
    private val eHpBox = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }   // HP bar + "61 / 61 HP" side by side
    private val enemyCard = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        background = Ui.rounded(translucentCard(ctx), 12, ctx)
        setPadding(dpi(10), dpi(7), dpi(10), dpi(8))
    }
    // "NEW" sticker: red text, tilted like a sticker, on the wild card's top-left corner (no background). Shown while the wild species isn't caught yet.
    // A sibling of the card (not a child) so it can overhang the card's corner without being clipped; [placeNewTag] puts it there.
    private val newTag = Ui.text(ctx, 8f, 900, R.color.ag_red).apply {
        text = "NEW"
        maxLines = 1
        letterSpacing = 0.04f
        rotation = -20f
        contentDescription = "New: not caught yet"
    }
    private val enemyBlock = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }

    // ---- player info card ----
    private val pName = FitName.make(ctx)
    private val pLevel = Ui.text(ctx, 12f, 800, R.color.ag_sub0).apply { maxLines = 1 }
    private val pHp = BarView(ctx).apply { ghostOnDrop = true }
    private val pHpText = Ui.text(ctx, 11f, 800, R.color.ag_sub0).apply { maxLines = 1 }
    private val pXpText = Ui.text(ctx, 11f, 800, R.color.ag_sub0).apply { maxLines = 1 }
    private val pXp = BarView(ctx)
    private val pHpBox = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }   // HP bar + "61 / 61 HP" side by side
    private val playerCard = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        background = Ui.rounded(translucentCard(ctx), 12, ctx)
        setPadding(dpi(10), dpi(7), dpi(10), dpi(8))
    }

    private val eRow = NameRow(ctx, eName, eLevel)
    private val pRow = NameRow(ctx, pName, pLevel)

    // ---- catch moment ----
    private val stars = PanelStars(ctx).apply { visibility = GONE }
    /** Canvas effects (the shiny star burst, round 5), on top of everything laid out here; draws only while one runs. */
    val fx = ArenaFxView(ctx)
    /** The attack animation's own canvas layer (round 7), under the stat cards so they stay readable over dims and flashes. */
    private val moveFx = ArenaFxView(ctx)
    /** "GOTCHA!" in the comic style of the floating battle texts (Round 3 pick 9C): heavy italic capitals, yellow, dark outline. */
    private val bannerTitle = OutlineTextView(ctx, Ui.dp(ctx, 2.5f), FloatTextView.Look.OUTLINE).apply {
        typeface = Ui.font(ctx, 900)
        setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 26f)
        setTextColor(FloatTextView.Look.fill(FloatKind.CRIT))
        includeFontPadding = false
        paint.textSkewX = -0.22f
        letterSpacing = 0.02f
        setShadowLayer(Ui.dp(ctx, 3f), 0f, Ui.dp(ctx, 1.2f), 0x99000000.toInt())
        setPadding(Ui.dp(ctx, 6), Ui.dp(ctx, 2), Ui.dp(ctx, 8), Ui.dp(ctx, 2))
    }
    // no card: the comic text stands on the arena; the padding leaves room for its slight tilt (BannerSpot places the padded box)
    private val banner = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(dpi(8), dpi(8), dpi(8), dpi(8))
        rotation = -5f
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

    /** Tap on our Pokémon's sprite (PLAN 86): the screen decides whether it may open the details now. */
    var onPlayerTap: () -> Unit = {}

    /** What a screen reader says about our Pokémon's sprite (the Study view's card opens the battle instead of the details). */
    fun setPlayerTapHint(description: String) { playerHolder.contentDescription = description }

    /** Whether a swipe may turn our Pokémon now (the screen says no while a battle animation plays or in PiP) and what to show (PLAN 34). */
    var canTurn: () -> Boolean = { true }
    var turnSource: PlayerTurnSource? = null
    private var turnAnim: Animator? = null
    internal var instantTurn: Boolean? = null   // tests only: the renderer has no animation clock
    private val swipe = SwipeTracker(ViewConfiguration.get(ctx).scaledTouchSlop.toFloat())

    /** Tap on the wild Pokémon's sprite (PLAN 93): the screen decides whether it may open the info now. */
    var onWildTap: () -> Unit = {}

    /** What a screen reader says about the wild Pokémon's sprite. */
    fun setWildTapHint(description: String) { enemyHolder.contentDescription = description }

    private var wildIsNew = false
    private var cardShown = false
    private var compact = false
    private var studyStrip = false

    // The panel's decisions ([configure]) and what the study layouts want ([setCompact], [setStudyStrip]).
    private var floorSp = 0f
    private var cardsCompact = false
    private var xpOn = true
    private var wantTypes = true
    private var wantXpText = true
    private var wantXpBar = true
    private var plan: ArenaGeo.Plan? = null

    /** Each stat card under (or next to) its own Pokémon ([AppPrefs.cardsUnderOwn]); false = the classic layout (opposite sides). */
    var cardsUnderOwn: Boolean = true
        set(v) { if (field != v) { field = v; requestLayout() } }

    /** Our Pokémon's sprite box is as big as the wild one's ([AppPrefs.playerSameSize]) instead of the enlarged one. */
    var playerSameSize: Boolean = false
        set(v) { if (field != v) { field = v; requestLayout() } }

    /** True while the two stat cards sit in the middle column between the sprites (the corner layout would cover one). */
    val middleLayout: Boolean get() = plan?.middle == true

    /** How much bigger than their normal size the two stat cards are drawn (1 in the study layouts). */
    val cardScale: Float get() = if (compact || studyStrip) 1f else plan?.k ?: 1f

    private val live = LinkedHashSet<Animator>()
    private val floats = ArrayList<View>()
    private class Flying(val onPlayer: Boolean, val rect: FloatSpot.Rect)
    private val flying = ArrayList<Flying>()       // floating texts on screen (or waiting to start): their start boxes
    private class XpShown(val view: XpCardView, val x: Int, val top: Int)
    private val xpQueue = ArrayDeque<Pair<XpCard, (SpriteView) -> Unit>>()
    private val xpActive = ArrayList<XpShown>()
    private val bobs = ArrayList<ObjectAnimator>()
    private var hpCount: ValueAnimator? = null

    init {
        clipChildren = true
        background = backdrop   // the plain gradient, or a downloaded battle picture (R7-07)
        backdrop.onPictureChange = { shown -> setPlatformsShown(!shown) }

        enemyHolder.addView(enemySprite, FrameLayout.LayoutParams(-1, -1))
        enemyHolder.addView(ball, FrameLayout.LayoutParams(dpi(48), dpi(48), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).also { it.bottomMargin = dpi(6) })
        playerHolder.addView(playerSprite, FrameLayout.LayoutParams(-1, -1))
        playerHolder.contentDescription = "Your Pokémon. Tap for its details"
        playerHolder.setOnClickListener { onPlayerTap() }
        playerHolder.setOnTouchListener { v, e -> // PLAN 34: a horizontal swipe turns the Pokémon; a tap still opens the details
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { swipe.down(e.x, e.y); false }
                MotionEvent.ACTION_MOVE -> when {
                    swipe.swiped -> true
                    swipe.move(e.x, e.y) -> { cancelPress(v, e); v.parent?.requestDisallowInterceptTouchEvent(true); swipeTurn(); true }
                    else -> {
                        // clearly sideways already: keep a vertical scroll parent from taking the swipe before it counts
                        if (swipe.claims(e.x, e.y)) v.parent?.requestDisallowInterceptTouchEvent(true)
                        false
                    }
                }
                MotionEvent.ACTION_UP -> swipe.end()   // a swipe's UP is swallowed so it does not click
                MotionEvent.ACTION_CANCEL -> { swipe.end(); false }
                else -> false
            }
        }
        enemyHolder.contentDescription = "Wild Pokémon. Tap for its info"
        enemyHolder.setOnClickListener { onWildTap() }

        // enemy card: name + Lv, type chips, HP bar (no numbers)
        eRow.addView(eName, LinearLayout.LayoutParams(0, -2, 1f))
        eRow.addView(eLevel, LinearLayout.LayoutParams(-2, -2).also { it.marginStart = dpi(5) })
        enemyCard.addView(eRow, LinearLayout.LayoutParams(-1, -2))
        eTypeRow.addView(eTypes, LinearLayout.LayoutParams(-2, -2))
        enemyCard.addView(eTypeRow, LinearLayout.LayoutParams(-2, -2).also { it.topMargin = dpi(4) })
        eHpBox.addView(eHp, LinearLayout.LayoutParams(0, dpi(7), 1f))
        eHpBox.addView(eHpText, LinearLayout.LayoutParams(-2, -2))
        enemyCard.addView(eHpBox, LinearLayout.LayoutParams(-1, -2).also { it.topMargin = dpi(6) })
        enemyCard.addView(eStatus, LinearLayout.LayoutParams(-1, dpi(STATUS_ROW_DP)))
        enemyBlock.addView(enemyCard, LinearLayout.LayoutParams(-1, -2))

        // player card: name + Lv, HP bar with "58 / 71 HP" beside it, status row, "XP 640 / 1000", XP bar
        pRow.addView(pName, LinearLayout.LayoutParams(0, -2, 1f))
        pRow.addView(pLevel, LinearLayout.LayoutParams(-2, -2).also { it.marginStart = dpi(5) })
        playerCard.addView(pRow, LinearLayout.LayoutParams(-1, -2))
        pHpBox.addView(pHp, LinearLayout.LayoutParams(0, dpi(7), 1f))
        pHpBox.addView(pHpText, LinearLayout.LayoutParams(-2, -2))
        playerCard.addView(pHpBox, LinearLayout.LayoutParams(-1, -2).also { it.topMargin = dpi(6) })
        playerCard.addView(pStatus, LinearLayout.LayoutParams(-1, dpi(STATUS_ROW_DP)))
        playerCard.addView(pXpText, LinearLayout.LayoutParams(-2, -2))
        pXp.setTrackColor(Ui.c(ctx, R.color.ag_s0))
        playerCard.addView(pXp, LinearLayout.LayoutParams(-1, dpi(3)).also { it.topMargin = dpi(4) })

        banner.addView(bannerTitle)

        addView(platEnemy, LayoutParams(0, 0))
        addView(platPlayer, LayoutParams(0, 0))
        addView(enemyHolder, LayoutParams(0, 0))
        addView(playerHolder, LayoutParams(0, 0))
        addView(moveFx, LayoutParams(-1, -1))
        addView(enemyBlock, LayoutParams(0, -2))
        addView(playerCard, LayoutParams(0, -2))
        addView(newTag, LayoutParams(-2, -2, Gravity.TOP or Gravity.LEFT))
        addView(stars, LayoutParams(-1, -1))
        addView(banner, LayoutParams(-2, -2, Gravity.TOP or Gravity.LEFT))   // placed by [placeBanner] (translation), never by margins
        addView(bubble, LayoutParams(-2, -2, Gravity.TOP or Gravity.LEFT))
        addView(fx, LayoutParams(-1, -1))
        bubble.setOnClickListener { hideStatusBubble() }
        eStatus.onTap = { showStatusBubble(it) }
        pStatus.onTap = { showStatusBubble(it) }

        // Nothing until the first encounter.
        setEnemyShown(false)
        setNewTag(false)
    }

    // ---------------------------------------------------------------- layout

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val minH = Ui.dp(context, if (compact) 70f else 140f).toInt()
        val hMode = MeasureSpec.getMode(heightMeasureSpec)
        val hSize = MeasureSpec.getSize(heightMeasureSpec)
        val h = when (hMode) {
            MeasureSpec.EXACTLY -> hSize
            MeasureSpec.AT_MOST -> min(hSize, minH)
            else -> minH
        }
        val w = MeasureSpec.getSize(widthMeasureSpec)
        if (compact || studyStrip) layoutStudy(w, h) else layoutPlan(w, h)
        placeNewTag()
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY))
    }

    /**
     * The normal arena: sprites, platforms and cards from [ArenaGeo], which decides under / beside / corners / middle and
     * how much the sprites and cards grow. The sprites are drawn as big as their holder allows (whole device pixels, so
     * pixel art stays sharp); a card is laid out at its normal width and drawn [ArenaGeo.Plan.k] times bigger from its corner.
     */
    private fun layoutPlan(w: Int, h: Int) {
        val d = Ui.dp(context, 1f)
        val p = ArenaGeo.plan(w / d, h / d, ArenaGeo.cards(floorSp, cardsCompact, xpOn), cardsCompact, cardsUnderOwn, playerSameSize)
        plan = p
        for (s in listOf(enemySprite, playerSprite)) if (s.scaleDp != BIG_SPRITE_DP) s.scaleDp = BIG_SPRITE_DP
        fun put(v: View, b: ArenaGeo.Box, wrapHeight: Boolean = false, k: Float = 1f) {
            place(v, (b.w / k * d).toInt(), if (wrapHeight) -2 else (b.h * d).toInt(), Gravity.TOP or Gravity.LEFT, (b.x * d).toInt(), (b.y * d).toInt(), 0, 0)
            scaleFrom(v, k)
        }
        put(platEnemy, p.wildPlat)
        put(platPlayer, p.playerPlat)
        put(enemyHolder, p.wildSprite)
        put(playerHolder, p.playerSprite)
        put(enemyBlock, p.wildCard, wrapHeight = true, k = p.k)
        put(playerCard, p.playerCard, wrapHeight = true, k = p.k)
    }

    /** The NEW sticker sits on the wild card's top-left corner, overhanging it a little, and grows with the card. */
    private fun placeNewTag() {
        val card = enemyBlock.layoutParams as LayoutParams
        val k = enemyBlock.scaleX
        val lp = newTag.layoutParams as LayoutParams
        lp.setMargins(card.leftMargin - (dpi(4) * k).toInt(), card.topMargin - (dpi(3) * k).toInt(), 0, 0)
        newTag.scaleX = k; newTag.scaleY = k
    }

    /** Draws [v] [k] times bigger around its top-left corner (touches still land on the right view). */
    private fun scaleFrom(v: View, k: Float) {
        v.pivotX = 0f; v.pivotY = 0f
        v.scaleX = k; v.scaleY = k
    }

    /** The study strips' own geometry (the approved mockup values), unchanged. */
    private fun layoutStudy(w: Int, h: Int) {
        for (s in listOf(enemySprite, playerSprite)) if (s.scaleDp != 2f) s.scaleDp = 2f
        scaleFrom(enemyBlock, 1f)
        scaleFrom(playerCard, 1f)
        val ref = Ui.dp(context, 212f)
        // Sprite boxes and platforms follow the arena height; a tall window pushes the wild one a bit lower so the
        // two sides don't sit in opposite corners.
        val s = (h / ref).coerceIn(if (compact) 0.45f else 0.66f, 1.5f)
        val extra = max(0f, h - ref * s)
        val down = (extra * 0.75f).toInt()
        fun px(v: Float) = (Ui.dp(context, v) * s).toInt()
        // The cards now always carry the status row, so they are one row taller than the mockup's: the sprite boxes give that
        // height back (a smaller sprite, not a card over its feet).
        val g = dpf(ArenaGeo.statusRow(floorSp, cardsCompact))
        place(platEnemy, px(150f), px(30f), Gravity.TOP or Gravity.RIGHT, 0, px(92f) + down, px(22f), 0)
        place(platPlayer, px(180f), px(40f), Gravity.BOTTOM or Gravity.LEFT, px(4f), 0, 0, -px(8f))
        place(enemyHolder, px(110f), px(100f) - g, Gravity.TOP or Gravity.RIGHT, 0, px(12f) + down, px(42f), 0)
        if (playerSameSize) place(playerHolder, px(110f), px(100f) - g, Gravity.BOTTOM or Gravity.LEFT, px(22f), 0, 0, px(6f))
        else place(playerHolder, px(140f), px(128f) - g, Gravity.BOTTOM or Gravity.LEFT, px(22f), 0, 0, px(6f))
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
            place(enemyHolder, sp(96), sp(80) - g, Gravity.TOP or Gravity.RIGHT, 0, sp(4), sp(26), 0)
            if (playerSameSize) place(playerHolder, sp(96), sp(80) - g, Gravity.BOTTOM or Gravity.LEFT, sp(14), 0, 0, sp(2))
            else place(playerHolder, sp(116), sp(96) - g, Gravity.BOTTOM or Gravity.LEFT, sp(14), 0, 0, sp(2))
            // a narrow pane: the cards may go down to 96 dp (the name shortens) so ours covers no more of our sprite's box than before the HP number moved up
            val stripMax = ((w - dpi(30)) / 2).coerceAtLeast(dpi(96))
            place(enemyBlock, min(dpi(138), stripMax), -2, Gravity.TOP or Gravity.LEFT, dpi(8), dpi(8), 0, 0)
            place(playerCard, min(dpi(142), stripMax), -2, Gravity.BOTTOM or Gravity.RIGHT, 0, 0, dpi(8), dpi(8))
        }
    }

    private fun place(v: View, w: Int, h: Int, gravity: Int, left: Int, top: Int, right: Int, bottom: Int) {
        val lp = v.layoutParams as LayoutParams
        lp.width = w; lp.height = h; lp.gravity = gravity
        lp.setMargins(left, top, right, bottom)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // the attack animations' textures are decoded now, on a background thread (once per process), not inside the first frame
        if (!reduceMotion() && AppPrefs.attackAnimations(context)) com.husarp.ankigo.ui.movefx.FxArt.of(context).warmUp()
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
        if (visibility != VISIBLE) moveFx.clear()   // nothing keeps drawing (or leaves a sprite posed) off screen
        bobs.forEach {
            if (visibility == VISIBLE && ValueAnimator.areAnimatorsEnabled() && !context.getSharedPreferences("study", 0).getBoolean("reduceMotion", false)) { if (it.isPaused) it.resume() else if (!it.isStarted) it.start() }
            else if (it.isStarted && !it.isPaused) it.pause()
        }
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility != VISIBLE) moveFx.clear()
    }

    /** A swipe across our Pokémon: it turns to the other view (back <-> front) and stays turned; the facing is saved ([PlayerTurnSource]). */
    private fun swipeTurn() {
        val src = turnSource ?: return
        stopTurnAnim()   // a swipe during a squash: its picture swap (and the saved facing) happens first
        val toFront = !src.facingFront()
        if (!SwipeTurn.mayTurn(canTurn(), toFront) { src.hasFront() }) return
        val before = spriteTag()
        squash(instant = false) {
            // the picture only over the one the swipe saw: a battle event may have loaded another one during the squash
            src.turnTo(toFront, spriteTag() === before)
        }
    }

    private fun spriteTag(): Any = playerSprite.getTag(R.id.ag_tag_sprite) ?: NO_TAG

    /**
     * scaleX 1 -> 0, [swap], 0 -> 1 on the turn's own animator, so it never cancels a battle event's sprite animation (those
     * stop it instead: [stopTurnAnim]). Stopped or not, [swap] runs once and the width ends at 1. Instant with reduced motion.
     */
    private fun squash(instant: Boolean, swap: () -> Unit) {
        stopTurnAnim()
        if (instant || (instantTurn ?: reduceMotion())) { swap(); return }
        var swapped = false
        val a = ObjectAnimator.ofFloat(playerSprite, View.SCALE_X, 1f, 0f, 1f).setDuration(SwipeTurn.SQUASH_MS * 2)
        a.interpolator = LinearInterpolator()
        a.addUpdateListener { if (!swapped && it.animatedFraction >= 0.5f) { swapped = true; swap() } }
        a.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(an: Animator) {
                if (turnAnim === a) turnAnim = null
                if (!swapped) { swapped = true; swap() }
                playerSprite.scaleX = 1f
            }
        })
        turnAnim = a
        a.start()
    }

    /** Ends a running turn squash at full width (its picture swap still happens). */
    private fun stopTurnAnim() { turnAnim?.cancel(); turnAnim = null; playerSprite.scaleX = 1f }

    /** The swipe owns the touch now: let the holder's own handling see a CANCEL (no pressed look, no pending tap or long press). */
    private fun cancelPress(v: View, e: MotionEvent) {
        val c = MotionEvent.obtain(e)
        c.action = MotionEvent.ACTION_CANCEL
        v.onTouchEvent(c)
        c.recycle()
    }

    override fun onDetachedFromWindow() {
        stopTurnAnim()
        hideStatusBubble()
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

    // ---------------------------------------------------------------- background (R7-07)

    /**
     * The battle background for a wild Pokémon of [species] with [types] ([BackdropRules]): the downloaded Showdown picture once
     * it is there, else the plain gradient. Plain too while pictures or downloads are off in the settings ([Backdrops.allowed]).
     */
    fun setBackdrop(types: List<Type>, species: Int) {
        val download = Backdrops.allowed(Sprites.enabled(context), AppPrefs.backgroundPictures(context))
        if (!download) { backdrop.plain(); return }
        backdrop.request(BackdropRules.pick(types, species), download)
    }

    /** The plain gradient with the two platform ellipses. */
    fun setPlainBackdrop() = backdrop.plain()

    /** The two plain-mode ellipses show only while no picture does (the picture has its own ground spots). */
    private fun setPlatformsShown(shown: Boolean) {
        val vis = if (shown) VISIBLE else GONE
        platEnemy.visibility = vis
        platPlayer.visibility = vis
    }

    // ---------------------------------------------------------------- enemy side

    /** Shows or hides the whole wild side (card, tag, sprite, platform stays). */
    fun setEnemyShown(shown: Boolean) {
        val vis = if (shown) VISIBLE else INVISIBLE
        enemyBlock.visibility = vis
        enemyHolder.visibility = vis
        cardShown = shown
        showNewTag()
        if (!shown) { enemyName = ""; if (bubbleFor === eStatus) hideStatusBubble() }
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
        eLevel.text = "Lv $level"; eLevel.setTextColor(LevelTier.color(context, level))
        eTypes.removeAllViews()
        for (t in types) eTypes.addView(Ui.typeChip(context, t).also { it.maxLines = 1; TextFloor.apply(it, floorSp) }, LinearLayout.LayoutParams(-2, -2).also { it.marginEnd = dpi(3) })
    }

    fun setEnemyHp(hp: Int, max: Int, animate: Boolean) {
        val f = if (max > 0) hp.toFloat() / max else 0f
        eHp.set(f, Ui.hpColor(context, f), animate)
        eHpText.text = DisplayNumbers.hpLabel(hp, max)
    }

    /** The red "NEW" sticker on the wild card's corner: shown while the wild species isn't caught yet. */
    fun setNewTag(isNew: Boolean) {
        wildIsNew = isNew
        showNewTag()
    }

    /** The sticker follows the wild card: shown only while the card is (and the species is new). */
    private fun showNewTag() { newTag.visibility = if (wildIsNew && cardShown) VISIBLE else INVISIBLE }

    /**
     * The study strips' compact look (narrow split-screen strip): the info cards keep name, level, HP bar and HP numbers
     * (no type chips, XP text or XP bar). Picture-in-picture and the floating battle do not use it any more:
     * they show the normal arena, scaled by the panel ([configure]).
     */
    fun setCompact(on: Boolean) {
        if (compact == on) return
        compact = on
        wantTypes = !on
        wantXpText = !on
        wantXpBar = !on
        val name = if (on) 12f else 14f
        val lvl = if (on) 11f else 12f
        for (t in listOf(eName, pName)) sz(t, name)
        for (t in listOf(eLevel, pLevel)) sz(t, lvl)
        val padV = if (on) 5 else 7
        for (c in listOf(enemyCard, playerCard)) c.setPadding(dpi(10), dpi(padV), dpi(10), dpi(if (on) 6 else 8))
        sz(bannerTitle, if (on) 19f else 26f)
        updateCards()
    }

    /** Restores the type and numeric rows hidden by [setCompact], with the study mockup's metrics. */
    fun setStudyStrip(on: Boolean) {
        studyStrip = on
        wantTypes = true
        wantXpText = true
        wantXpBar = !on
        for (t in listOf(eName, pName)) sz(t, if (on) 12.5f else 14f)
        for (t in listOf(eLevel, pLevel)) sz(t, if (on) 11f else 12f)
        for (c in listOf(enemyCard, playerCard)) {
            c.elevation = Ui.dp(context, 2f)
            c.background = Ui.rounded(translucentCard(context), if (on) 10 else 12, context)
            c.setPadding(dpi(if (on) 8 else 10), dpi(if (on) 5 else 7), dpi(if (on) 8 else 10), dpi(if (on) 6 else 8))
        }
        updateCards()
    }

    /**
     * What the panel decided for this window ([PanelFit.Fit]): the text floor in sp, compact stat cards (the pref: name +
     * a thin HP bar with the HP number beside it), and whether the XP numbers show.
     */
    fun configure(floor: Float, compactCards: Boolean, xp: Boolean) {
        if (floor == floorSp && compactCards == cardsCompact && xp == xpOn) return
        floorSp = floor
        cardsCompact = compactCards
        xpOn = xp
        updateCards()
    }

    /** Applies the card flags: what shows, the HP rows' shape, the text floor. Cheap; called whenever a flag changes. */
    private fun updateCards() {
        val full = !cardsCompact
        fun vis(on: Boolean) = if (on) VISIBLE else GONE
        eTypes.visibility = vis(wantTypes && full)
        eTypeRow.visibility = vis(wantTypes && full)
        pXpText.visibility = vis(wantXpText && full && xpOn)
        pXp.visibility = vis(wantXpBar && full)
        val hpSp = if (cardsCompact || studyStrip) 10f else 11f
        for (t in listOf(eHpText, pHpText, pXpText)) sz(t, hpSp)
        statusRow(eStatus, enemyStatus)
        statusRow(pStatus, playerStatus)
        hpRow(eHpBox, eHp)
        hpRow(pHpBox, pHp)
        (pXp.layoutParams as LinearLayout.LayoutParams).height = dpf(3f * TextMath.bar(floorSp))
        eRow.tight = cardsCompact
        pRow.tight = cardsCompact
        hideStatusBubble()
        TextFloor.apply(this, floorSp)
        requestLayout()
    }

    /**
     * The status badge row of a card, under the HP bar and its number: a fixed [ArenaGeo.statusRow] dp tall whether or not a
     * badge shows, so nothing moves when an effect comes or goes. This holds in the study strips too (their fixed geometry is
     * not planned by [ArenaGeo], the row is part of the card's own height).
     */
    private fun statusRow(row: StatusRow, status: String?) {
        row.set(status)
        row.visibility = VISIBLE
        row.fit(dpf(ArenaGeo.statusRow(floorSp, cardsCompact)))
    }

    /** The HP bar with its "61 / 61 HP" number right beside it (bar thinner on compact cards). */
    private fun hpRow(box: LinearLayout, bar: BarView) {
        val k = TextMath.bar(floorSp)
        val barLp = bar.layoutParams as LinearLayout.LayoutParams
        val boxLp = box.layoutParams as LinearLayout.LayoutParams
        barLp.marginEnd = dpi(6)
        if (cardsCompact) {
            barLp.height = dpf(5f * k); boxLp.topMargin = 0
        } else {
            barLp.height = dpf((if (studyStrip) 6f else 7f) * k); boxLp.topMargin = dpi(if (studyStrip) 4 else 6)
        }
        bar.layoutParams = barLp
        box.layoutParams = boxLp
    }

    private fun sz(tv: TextView, sp: Float) = TextFloor.set(tv, sp, floorSp)

    // ---------------------------------------------------------------- status badges

    private var enemyStatus: String? = null
    private var playerStatus: String? = null

    /** Status badge (poison, burn, paralysis, sleep, freeze) on the wild card; null clears it. Raw engine ids ("tox" shows as poison). */
    fun setEnemyStatus(status: String?) { if (status != enemyStatus) { enemyStatus = status; statusChanged(eStatus) } }

    /** Status badge on our card; null clears it. */
    fun setPlayerStatus(status: String?) { if (status != playerStatus) { playerStatus = status; statusChanged(pStatus) } }

    private fun statusChanged(row: StatusRow) {
        if (bubbleFor === row) hideStatusBubble()
        updateCards()
    }

    private var bubbleFor: StatusRow? = null
    private val hideBubble = Runnable { hideStatusBubble() }

    /**
     * Tap on a badge row: the one-line explanation from [StatusInfo] in a bubble above the badge (below it when there is no
     * room above), kept inside the arena. It goes away after about 3 s, on the next tap on a badge or on the bubble.
     */
    private fun showStatusBubble(row: StatusRow) {
        val showing = bubble.visibility == VISIBLE && bubbleFor === row
        hideStatusBubble()
        val info = StatusInfo.of(row.status)
        val badge = row.badge
        if (showing || info == null || badge == null || width <= 0 || height <= 0) return
        val d = Ui.dp(context, 1f)
        val margin = (6 * d).toInt()
        val maxW = min(width - 2 * margin, (230 * d).toInt())
        if (maxW <= 0) return
        bubble.text = StatusBubble.text(context, info.line)
        TextFloor.set(bubble, 11f, floorSp)
        bubble.maxWidth = maxW
        bubble.prepare(false, 0f)
        bubble.measure(MeasureSpec.makeMeasureSpec(maxW, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
        val bw = bubble.measuredWidth
        val bh = bubble.measuredHeight
        val r = android.graphics.Rect(0, 0, badge.width, badge.height)
        offsetDescendantRectToMyCoords(badge, r)
        val cx = (r.left + r.right) / 2
        val x = (cx - bw / 2).coerceIn(margin.coerceAtMost(width - bw).coerceAtLeast(0), (width - margin - bw).coerceAtLeast(0))
        val up = r.top - bh - d < 2 * d
        val y = (if (up) r.bottom + d else r.top - bh - d).toInt().coerceIn(0, (height - bh).coerceAtLeast(0))
        bubble.prepare(up, (cx - x).toFloat())
        (bubble.layoutParams as LayoutParams).also { it.width = bw; it.height = bh; it.gravity = Gravity.TOP or Gravity.LEFT; it.setMargins(x, y, 0, 0) }
        bubbleFor = row
        bubble.visibility = VISIBLE
        bubble.bringToFront()
        requestLayout()
        postDelayed(hideBubble, 3000)
    }

    private fun hideStatusBubble() {
        removeCallbacks(hideBubble)
        bubble.visibility = GONE
        bubbleFor = null
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (bubbleFor != null) hideStatusBubble()
    }

    // ---------------------------------------------------------------- player side

    fun setPlayerInfo(uid: String?, name: String, level: Int) {
        playerUid = uid
        playerLevel = level
        pName.text = name
        pLevel.text = "Lv $level"; pLevel.setTextColor(LevelTier.color(context, level))
    }

    fun setPlayerLevel(level: Int) {
        playerLevel = level
        pLevel.text = "Lv $level"; pLevel.setTextColor(LevelTier.color(context, level))
    }

    fun setPlayerName(name: String) { pName.text = name }

    /** HP bar eases 450 ms; the "58 / 71" counts along. */
    fun setPlayerHp(hp: Int, max: Int, animate: Boolean) {
        val f = if (max > 0) hp.toFloat() / max else 0f
        pHp.set(f, Ui.hpColor(context, f), animate)
        hpCount?.cancel()
        val from = pHpShown
        pHpShown = hp
        if (!animate || from == hp || !isAttachedToWindow) { pHpText.text = DisplayNumbers.hpLabel(hp, max); return }
        hpCount = ValueAnimator.ofInt(from, hp).setDuration(450).also { a ->
            a.addUpdateListener { pHpText.text = DisplayNumbers.hpLabel(it.animatedValue as Int, max) }
            a.addListener(object : AnimatorListenerAdapter() { override fun onAnimationEnd(an: Animator) { pHpText.text = DisplayNumbers.hpLabel(hp, max) } })
            a.start()
        }
    }

    /** XP bar (3 dp, sky) and the "XP 640 / 1000" label. */
    fun setPlayerXp(current: Int, needed: Int, animate: Boolean) {
        val f = (if (needed > 0) current.toFloat() / needed else 1f).coerceIn(0f, 1f)
        pXpShown = f
        pXp.setMeaning(BarMeaning.XP, f, animate)
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

    /** The old four number styles, now drawn by [floatText]. */
    fun floatNumber(onPlayer: Boolean, text: String, style: FloatStyle) {
        val kind = when (style) {
            FloatStyle.DAMAGE -> FloatKind.DAMAGE
            FloatStyle.BIG -> FloatKind.BIG_DAMAGE
            FloatStyle.HEAL -> FloatKind.HEAL
            FloatStyle.MISS -> FloatKind.MISS
        }
        floatText(FloatText(onPlayer, text, kind))
    }

    private fun reduceMotion() = !ValueAnimator.areAnimatorsEnabled() || context.getSharedPreferences("study", 0).getBoolean("reduceMotion", false)

    /** The two stat cards as boxes (they are drawn scaled from their top-left corner). */
    private fun cardBoxes(pad: Int, shownOnly: Boolean = false): List<FloatSpot.Rect> =
        listOf(enemyBlock, playerCard).filter { !shownOnly || (it.visibility == VISIBLE && it.width > 0) }.map { v -> FloatSpot.Rect(v.left - pad, v.top - pad, v.left + (v.width * v.scaleX).toInt() + pad, v.top + (v.height * v.scaleY).toInt() + pad) }

    /**
     * A floating battle text in the comic style ([FloatTextView]) pops in over the Pokémon it belongs to, holds a moment, then
     * rises and fades (about 1.1 s). Kept inside the arena and off the stat cards ([FloatSpot.placeClear]); texts of one
     * round start 0.23 s apart and sit one under the other, so they never print over each other.
     */
    fun floatText(ft: FloatText) {
        val holder = if (ft.onPlayer) playerHolder else enemyHolder
        val spr = if (ft.onPlayer) playerSprite else enemySprite
        val tv = FloatTextView(context, ft, floorSp)
        tv.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED)
        val tvW = tv.measuredWidth
        val tvH = tv.measuredHeight
        val drawn = spr.drawnHeight()
        val top = if (drawn > 0) max(holder.top - dpi(8), holder.bottom - drawn - dpi(8)) else holder.top + dpi(6)
        val left = holder.left + (holder.width - tvW) / 2
        val mine = flying.filter { it.onPlayer == ft.onPlayer }   // texts of this side wait for each other
        // Not laid out yet (no size): the old free-floating start. Otherwise keep the whole rise inside the arena.
        val spot = if (width > 0 && height > 0) FloatSpot.placeClear(left, top, width, height, tvW, tvH, dpi(34), flying.map { it.rect }, cardBoxes(dpi(2)), dpi(2))
        else FloatSpot.Spot(left, top, dpi(34))
        val lp = LayoutParams(-2, -2, Gravity.TOP or Gravity.LEFT)
        lp.leftMargin = spot.left
        lp.topMargin = spot.top
        addView(tv, lp)
        floats += tv
        val rec = Flying(ft.onPlayer, FloatSpot.Rect(spot.left, spot.top, spot.left + tvW, spot.top + tvH))
        flying += rec
        val done = { removeView(tv); floats -= tv; flying -= rec }
        if (reduceMotion()) {   // no pop, no rise: just show, then fade out
            tv.animate().alpha(0f).setStartDelay(900).setDuration(500).withEndAction(done).start()
            return
        }
        val delay = min(3, mine.size) * FLOAT_STAGGER_MS
        tv.alpha = 0f; tv.scaleX = 0.6f; tv.scaleY = 0.6f
        tv.animate().alpha(1f).scaleX(1f).scaleY(1f).setStartDelay(delay).setDuration(150).setInterpolator(OvershootInterpolator()).withEndAction {
            if (tv.parent == null) return@withEndAction
            tv.animate().translationY(-spot.rise.toFloat()).alpha(0f).setStartDelay(520).setDuration(480)
                .setInterpolator(DecelerateInterpolator()).withEndAction(done).start()
        }.start()
    }

    // ---------------------------------------------------------------- team XP cards

    /**
     * Queues the XP card of a team member (pick 9A): it slides in from the left edge, between the two Pokémon, stays a moment
     * and slides away. [loadSprite] puts the mini sprite in. Several stack in the free gap or play one after another; with no
     * room even for the slim card (tiny window) it is skipped. It sits on no sprite and no stat card, and takes no touches.
     */
    fun showXpCard(card: XpCard, loadSprite: (SpriteView) -> Unit) {
        if (xpQueue.size >= 8) return
        xpQueue.addLast(card to loadSprite)
        pumpXp()
    }

    /** What a card must keep off: both stat cards (a little more room round the wild one for its NEW sticker) and both sprites' pictures. */
    private fun xpObstacles(): List<FloatSpot.Rect> {
        val m = dpi(3)
        return cardBoxes(m * 2) + artBox(enemyHolder, enemySprite, m) + artBox(playerHolder, playerSprite, m)
    }

    /** The picture a sprite really draws (bottom-centred in its holder), [m] px of room round it. */
    private fun artBox(v: View, spr: SpriteView, m: Int): FloatSpot.Rect {
        val drawn = spr.drawnHeight()
        val dw = spr.drawnWidth()
        val aw = if (dw > 0) min(dw, v.width) else (v.width * 0.85f).toInt(); val ah = if (drawn > 0) min(drawn, v.height) else (v.height * 0.85f).toInt()
        val x = v.left + (v.width - aw) / 2; val y = v.top + (v.height - ah)
        return FloatSpot.Rect(x - m, y - m, x + aw + m, y + ah + m)
    }

    private fun pumpXp() {
        while (xpQueue.isNotEmpty()) {
            if (width <= 0 || height <= 0) { xpQueue.clear(); return }
            val (card, load) = xpQueue.first()
            val slimOnly = compact || studyStrip || height < dpi(190)
            val obstacles = xpObstacles()
            var made: XpCardView? = null
            var spots: List<XpCardSpot.Pos> = emptyList()
            for (slim in if (slimOnly) listOf(true) else listOf(false, true)) {
                val v = XpCardView(context, card, slim, floorSp)
                val w = min(dpi(if (slim) 160 else 188), (width * (if (slim) 0.72f else 0.55f)).toInt())
                v.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.UNSPECIFIED)
                val t = XpCardSpot.slots(height, dpi(XP_EDGE_DP), w, v.measuredHeight, dpi(6), dpi(4), obstacles)
                if (t.isNotEmpty()) { made = v; spots = t.map { XpCardSpot.Pos(dpi(XP_EDGE_DP), it) }; break }
            }
            if (made == null) {
                // Small window: the slim one-line card in any free spot (along the top or bottom edge, or beside a stat card),
                // a bit narrower if it must be, and last the micro card (no name) in the narrowest gap.
                search@ for (micro in booleanArrayOf(false, true)) {
                    val v = XpCardView(context, card, true, floorSp, micro)
                    for (wDp in if (micro) intArrayOf(88, 80) else intArrayOf(160, 132)) {
                        val w = min(dpi(wDp), (width * 0.72f).toInt())
                        v.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.UNSPECIFIED)
                        val t = XpCardSpot.anywhere(width, height, w, v.measuredHeight, dpi(if (micro) 2 else 4), dpi(2), obstacles)
                        if (t.isNotEmpty()) { made = v; spots = t; break@search }
                    }
                }
            }
            if (made == null) { xpQueue.clear(); return }   // no room: skipped
            val v: XpCardView = made
            val busy = xpActive.map { FloatSpot.Rect(it.x, it.top, it.x + it.view.measuredWidth, it.top + it.view.measuredHeight) }
            val spot = spots.firstOrNull { p -> val r = FloatSpot.Rect(p.x, p.y, p.x + v.measuredWidth, p.y + v.measuredHeight); busy.none { b -> b.hits(r) } }
                ?: return   // all places busy: the next one leaves
            xpQueue.removeFirst()
            slideXp(v, spot, load)
        }
    }

    private fun slideXp(v: XpCardView, spot: XpCardSpot.Pos, load: (SpriteView) -> Unit) {
        load(v.sprite)
        addView(v, LayoutParams(v.measuredWidth, v.measuredHeight, Gravity.TOP or Gravity.LEFT).also { it.leftMargin = spot.x; it.topMargin = spot.y })
        val shown = XpShown(v, spot.x, spot.y)
        xpActive += shown
        val finish = { removeView(v); xpActive -= shown; pumpXp() }
        val hold = if (v.card.levelUp) 2500L else 1800L
        if (reduceMotion()) {   // no slide: there, and gone after a moment
            v.play(true)
            v.postDelayed(finish, hold - 300)
            return
        }
        // Slides in from the nearer side edge.
        val fromRight = spot.x + v.measuredWidth / 2 > width / 2
        val off = if (fromRight) (width - spot.x).toFloat() else -(spot.x + v.measuredWidth).toFloat()
        v.translationX = off
        v.play(false)
        v.animate().translationX(0f).setDuration(260).setInterpolator(DecelerateInterpolator()).withEndAction {
            if (v.parent == null) return@withEndAction
            v.animate().translationX(off).alpha(0f).setStartDelay(hold).setDuration(240).setInterpolator(AccelerateInterpolator()).withEndAction(finish).start()
        }.start()
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
        if (player) stopTurnAnim()
        spr.translationX = Ui.dp(context, 60f); spr.translationY = 0f; spr.alpha = 0f; spr.scaleX = 1f; spr.scaleY = 1f; spr.flash = 0f
        spr.animate().translationX(0f).alpha(1f).setDuration(ms).setInterpolator(DecelerateInterpolator()).start()
    }

    /**
     * The shiny star burst around the wild ([player] = false) or your sprite, starting [delayMs] from now (the end of its entrance).
     * Centred on the sprite box, scaled by it; nothing with reduced motion or before the box has a size.
     */
    fun shinyBurst(player: Boolean, delayMs: Long) {
        if (reduceMotion()) return
        fx.addAfter(delayMs) { if (isAttachedToWindow) burstFx(player) else null }
    }

    private fun burstFx(player: Boolean): FxEffect? {
        val box = if (player) playerHolder else enemyHolder
        if (box.width <= 0 || box.height <= 0) return null
        return ShinyBurstFx(box.left + box.width / 2f, ShinyBurst.centreY(box.top.toFloat(), box.height.toFloat()), ShinyBurst.scale(box.width.toFloat(), box.height.toFloat()))
    }

    /** Test-only: the burst frozen at [atMs] (a still frame for the screenshot tests). */
    internal fun shinyBurstStill(player: Boolean, atMs: Float) { burstFx(player)?.let { fx.showStill(it, atMs) } }

    private var xpFx: XpAbsorbFx? = null

    /**
     * The XP animation (PLAN 38) for [xp] gained by our Pokémon: sparks fly from the defeated wild one's box into ours, our sprite
     * flashes white at each landing and a "+N XP" number counts up over the defeated one's spot. [factor] is the player's pace
     * (all its times scale with it). Returns how long until the first spark lands, which is when the card's XP bar starts; 0 = no
     * animation started (reduced motion, not on screen, no size yet).
     */
    fun xpAbsorb(xp: Int, factor: Float): Long {
        if (xp <= 0 || reduceMotion() || !isAttachedToWindow) return 0L
        val plan = XpParticles.plan(kotlin.random.Random.nextLong(), factor)
        val e = xpEffect(plan, xp) ?: return 0L
        stopXp()
        xpFx = e
        fx.add(e)
        return plan.firstLanding.toLong()
    }

    private fun xpBox(v: View) = XpParticles.Box(v.left.toFloat(), v.top.toFloat(), v.width.toFloat(), v.height.toFloat())

    private fun xpEffect(plan: XpParticles.Plan, xp: Int): XpAbsorbFx? {
        if (width <= 0 || height <= 0 || enemyHolder.width <= 0 || enemyHolder.height <= 0 || playerHolder.width <= 0 || playerHolder.height <= 0) return null
        return XpAbsorbFx(context, plan, xpBox(enemyHolder), xpBox(playerHolder), width.toFloat(), xp, floorSp) { playerSprite.flash = it }
    }

    private fun stopXp() { xpFx?.let { fx.stop(it) }; xpFx = null }

    /** Test-only: the XP animation (mockup seed) frozen at [atMs], our sprite flashed as it is then. */
    internal fun xpAbsorbStill(xp: Int, atMs: Float) {
        val plan = XpParticles.plan(XpParticles.MOCK_SEED)
        val e = xpEffect(plan, xp) ?: return
        playerSprite.flash = XpParticles.flash(plan, atMs)
        fx.showStill(e, atMs)
    }

    /**
     * The attack animation of [plan] (round 7) between the two sprites, [byPlayer] = ours attacks. [react] = false: the target
     * shows no reaction. [speed] 2 = the battle's fast pace. Any move animation still running ends first. Null (the caller plays
     * the plain lunge and hit): reduced motion, not on screen, no size yet, the wild side not shown, a sprite with no picture,
     * or no look to play.
     */
    fun playMove(plan: com.husarp.ankigo.ui.movefx.MoveFxPlan, byPlayer: Boolean, react: Boolean, speed: Float): com.husarp.ankigo.ui.movefx.MoveFxEffect? {
        if (reduceMotion() || !moveOnScreen()) return null
        moveFx.clear()
        val e = moveEffect(plan, byPlayer, react, speed) ?: return null
        moveFx.add(e)
        return e
    }

    private fun moveEffect(plan: com.husarp.ankigo.ui.movefx.MoveFxPlan, byPlayer: Boolean, react: Boolean, speed: Float): com.husarp.ankigo.ui.movefx.MoveFxEffect? {
        val stage = moveStage(byPlayer) ?: return null
        val effect = moveMaker(stage, plan, react, speed) ?: return null
        moveLive = stage
        return effect
    }

    /** The stage of the latest move animation: [releaseMove] lets go of the sprites it posed. */
    private var moveLive: ArenaMoveStage? = null

    /**
     * The event player calls this right before it starts the next queued item after an attack animation: the move's sprite poses end
     * for good (the sprites are back at rest and later frames or the end of the effect do not touch them again), while its particles
     * may still fade. Does nothing when no move ran or it has let go already.
     */
    fun releaseMove() {
        moveLive?.restore()
        moveLive = null
    }

    // tests only: no window in the renderer, and a fake player instead of the real one
    internal var moveOnScreen: () -> Boolean = { isAttachedToWindow && windowVisibility == VISIBLE && isShown }
    internal var moveMaker: (com.husarp.ankigo.ui.movefx.MoveFxStage, com.husarp.ankigo.ui.movefx.MoveFxPlan, Boolean, Float) -> com.husarp.ankigo.ui.movefx.MoveFxEffect? =
        { stage, plan, react, speed -> com.husarp.ankigo.ui.movefx.MoveFx.create(context, stage, plan, react, speed, kotlin.random.Random.nextLong()) }

    /** The stage of one attack, or null while a sprite box has no size or picture or the wild side is hidden. */
    private fun moveStage(byPlayer: Boolean): ArenaMoveStage? {
        if (width <= 0 || height <= 0 || enemyHolder.visibility != VISIBLE) return null
        val attacker = if (byPlayer) playerSprite else enemySprite
        val target = if (byPlayer) enemySprite else playerSprite
        val aBox = if (byPlayer) playerHolder else enemyHolder
        val tBox = if (byPlayer) enemyHolder else playerHolder
        for ((spr, box) in listOf(attacker to aBox, target to tBox)) {
            if (box.width <= 0 || box.height <= 0 || spr.width <= 0 || spr.height <= 0 || spr.drawnWidth() <= 0 || spr.drawnHeight() <= 0) return null
        }
        val a = ArenaMoveStage.Geo.centre(aBox.left.toFloat(), aBox.top.toFloat(), attacker.width.toFloat(), attacker.height.toFloat(), attacker.drawnHeight().toFloat())
        val t = ArenaMoveStage.Geo.centre(tBox.left.toFloat(), tBox.top.toFloat(), target.width.toFloat(), target.height.toFloat(), target.drawnHeight().toFloat())
        val unit = ArenaMoveStage.Geo.unit(a.first, a.second, t.first, t.second, target.drawnHeight().toFloat())
        return ArenaMoveStage(a.first, a.second, t.first, t.second, unit, attacker, target, { background }, { it === playerSprite && turnAnim != null })
    }

    /** Test-only: the move animation of [plan] frozen at [atMs] (a still frame for the screenshot tests; no clock, no window checks). */
    internal fun moveStill(plan: com.husarp.ankigo.ui.movefx.MoveFxPlan, byPlayer: Boolean, atMs: Float) {
        moveFx.clear()
        com.husarp.ankigo.ui.movefx.FxArt.of(context).awaitWarm(10_000)   // the art warm-up of onAttachedToWindow, if it started
        moveEffect(plan, byPlayer, true, 1f)?.let { moveFx.showStill(it, atMs) }
    }

    /** The wild one runs off to the right and fades. */
    fun enemyLeave(ms: Long) {
        enemySprite.animate().translationX(Ui.dp(context, 70f)).alpha(0f).setDuration(ms).setInterpolator(AccelerateInterpolator()).start()
    }

    /** Back to the idle look: no leftover offset, fade, scale or flash on a sprite. */
    fun reset(player: Boolean) {
        val spr = if (player) playerSprite else enemySprite
        spr.animate().cancel()
        if (player) stopTurnAnim()
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

    /**
     * "Gotcha!" banner in the comic text style (pick 8A); [line1] / [line2] only go to its description (the result tiles and the log
     * say them). It sits above the Poké Ball, or centred in the arena when there is no room there (pick 8D), never over a Pokémon
     * or a stat card ([BannerSpot], PLAN 35).
     */
    fun showBanner(title: String, line1: String, line2: String?) {
        bannerTitle.text = title.uppercase(java.util.Locale.ROOT)
        banner.contentDescription = listOfNotNull(title, line1, line2).joinToString(". ")
        banner.visibility = VISIBLE
        banner.animate().cancel()
        if (!placeBanner()) { banner.visibility = GONE; return }   // no clear spot even small: the tiles / log already say it
        banner.scaleX = bannerFit * 0.97f; banner.scaleY = bannerFit * 0.97f; banner.alpha = 0.6f
        banner.animate().scaleX(bannerFit).scaleY(bannerFit).alpha(1f).setDuration(300).start()
    }

    private var bannerFit = 1f

    /**
     * Puts the banner at its [BannerSpot] (translation from the layout origin) and shrinks it if the arena is smaller than it.
     * False = no spot clear of the Pokémon and the cards at any size ([BannerSpot.Placed.shown]): the banner must not show.
     */
    private fun placeBanner(): Boolean {
        if (width <= 0 || height <= 0) return true   // not laid out yet: onLayout calls this again
        banner.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST))
        val bw = banner.measuredWidth; val bh = banner.measuredHeight
        val edge = dpi(6)
        val m = dpi(3)
        val obstacles = cardBoxes(dpi(4), shownOnly = true) + artBox(playerHolder, playerSprite, m) +
            (if (ballOnField) emptyList() else listOf(artBox(enemyHolder, enemySprite, m)))   // the wild one is inside the ball
        val ballBox = if (ballOnField && ball.width > 0) FloatSpot.Rect(enemyHolder.left + ball.left, enemyHolder.top + ball.top, enemyHolder.left + ball.right, enemyHolder.top + ball.bottom) else null
        val placed = BannerSpot.placeFitting(width, height, bw, bh, ballBox, obstacles, edge, dpi(4))
        if (!placed.shown) return false
        val fit = placed.scale; val spot = placed.spot
        val vw = (bw * fit).toInt(); val vh = (bh * fit).toInt()
        bannerFit = fit
        banner.pivotX = bw / 2f; banner.pivotY = bh / 2f
        banner.translationX = spot.left - (bw - vw) / 2f
        banner.translationY = spot.top - (bh - vh) / 2f
        return true
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        backdrop.setFeet(
            (enemyHolder.left + enemyHolder.right) / 2f, enemyHolder.bottom.toFloat(),
            (playerHolder.left + playerHolder.right) / 2f, playerHolder.bottom.toFloat(),
        )
        if (banner.visibility == VISIBLE) {
            val was = bannerFit
            if (!placeBanner()) { banner.animate().cancel(); banner.visibility = GONE; return }   // the arena shrank: no clear spot any more
            if (bannerFit != was) { banner.scaleX = bannerFit; banner.scaleY = bannerFit }   // the arena changed size under the banner
        }
    }

    fun hideBanner() { banner.animate().cancel(); banner.visibility = GONE }

    // ---------------------------------------------------------------- housekeeping

    /** Stops every running animation and removes leftovers (floating numbers too); sprites keep whatever they show. */
    fun cancelAnimations() {
        stopAnimators()
        fx.clear()
        moveFx.clear()
        xpFx = null
        xpQueue.clear()
        xpActive.toList().forEach { it.view.animate().cancel(); removeView(it.view) }
        xpActive.clear()
        floats.toList().forEach { it.animate().cancel(); removeView(it) }
        floats.clear()
        flying.clear()
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
        fx.clear()
        moveFx.clear()
        clearBall()
        showStars(false)
        hideBanner()
        reset(false)
        reset(true)
    }

    /** Only the wild side back to rest (a new encounter is about to slide in). */
    fun resetEnemySide() {
        stopXp()   // the XP number stood over this spot
        moveFx.clear()
        enemySprite.animate().cancel()
        clearBall()
        showStars(false)
        hideBanner()
        reset(false)
    }

    /** The wild card + tag alone (hidden while the ball is on the field). */
    fun setEnemyCardShown(shown: Boolean) { enemyBlock.visibility = if (shown) VISIBLE else INVISIBLE; cardShown = shown; showNewTag() }

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
    private fun dpf(v: Float) = (v * resources.displayMetrics.density + 0.5f).toInt()

    companion object {
        private const val FLOAT_STAGGER_MS = 230L
        private const val XP_EDGE_DP = 8
        /** dp per sprite pixel in the normal arena: the holder is the limit (the study layouts keep 2). */
        private const val BIG_SPRITE_DP = 8f
        /** Initial height of a card's status row (dp); [ArenaGeo.statusRow] sets the real one. */
        private const val STATUS_ROW_DP = 14
        /** [spriteTag] before any PanelSprites load. */
        private val NO_TAG = Any()
        private fun translucentCard(ctx: Context): Int = (Ui.c(ctx, R.color.ag_card) and 0x00FFFFFF) or (0xEB shl 24)
        private fun oval(color: Int) = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) }
    }
}

/** Six twinkling 4-point stars over the arena (mockup `.stars`, 1.6 s loop, staggered by 0.25 s). */
/**
 * The one-line name of an info card: narrows its letters (down to 80 %) before it has to end in "…", so long names like
 * "Galarian Darmanitan" stay readable. The scale is worked out from the text at full width, so a second pass keeps it.
 */
private class FitName(ctx: Context) : TextView(ctx) {
    /** The width of the name at full letter width. */
    fun naturalWidth(): Int = kotlin.math.ceil(paint.measureText(text, 0, text.length) / textScaleX.toDouble()).toInt() + compoundPaddingLeft + compoundPaddingRight

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val t = text
        val avail = measuredWidth - compoundPaddingLeft - compoundPaddingRight
        if (avail <= 0 || t.isNullOrEmpty()) return
        val full = paint.measureText(t, 0, t.length) / textScaleX
        val want = if (full <= avail) 1f else max(MIN_SCALE, avail / full)
        if (kotlin.math.abs(want - textScaleX) > 0.005f) {
            textScaleX = want
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }

    companion object {
        private const val MIN_SCALE = 0.8f

        fun make(ctx: Context) = FitName(ctx).apply {
            typeface = Ui.font(ctx, 900)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(Ui.c(ctx, R.color.ag_text))
            includeFontPadding = false
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
    }
}

/**
 * The name line of an info card: name, (status pill), "Lv". On compact cards [tight] keeps "Lv" only when the name still
 * fits at its full letter width next to it; otherwise "Lv" is left out rather than shrinking the name.
 */
private class NameRow(ctx: Context, private val name: FitName, private val level: TextView) : LinearLayout(ctx) {
    var tight = false
        set(v) { field = v; if (!v) level.visibility = VISIBLE; requestLayout() }

    init { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (tight && name.text.isNotEmpty()) {
            val un = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
            var others = 0
            for (i in 0 until childCount) {
                val c = getChildAt(i)
                if (c === name || (c !== level && c.visibility == GONE)) continue
                c.measure(un, un)
                others += c.measuredWidth + (c.layoutParams as MarginLayoutParams).marginStart
            }
            val avail = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
            level.visibility = if (name.naturalWidth() + others <= avail) VISIBLE else GONE
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
}

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
