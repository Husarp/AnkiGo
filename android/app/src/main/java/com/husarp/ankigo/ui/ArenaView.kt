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
import kotlin.math.abs
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
    val enemySprite = SpriteView(ctx).apply { scaleDp = 2f; idleMotion = true }   // PLAN 205: a still picture floats
    val playerSprite = SpriteView(ctx).apply { scaleDp = 2f; idleMotion = true }
    private val ball = ImageView(ctx).apply { visibility = GONE }
    private val enemyHolder = FrameLayout(ctx).apply { clipChildren = false; clipToPadding = false }
    private val playerHolder = FrameLayout(ctx).apply { clipChildren = false; clipToPadding = false }
    private val platEnemy = View(ctx).apply { background = oval(Ui.c(ctx, R.color.ag_plat2)) }
    private val platPlayer = View(ctx).apply { background = oval(Ui.c(ctx, R.color.ag_plat)) }
    /** The arena's background (R7-07): the plain gradient or a downloaded battle picture; it is a [com.husarp.ankigo.ui.movefx.Joltable] too. */
    private val backdrop = ArenaBackdrop(ctx)

    // ---- enemy info card (R9 D12: [buildCards] puts the parts together by [infoBoxStyle]) ----
    private val eName = FitName.make(ctx)
    private val eLevel = Ui.text(ctx, 12f, 800, R.color.ag_sub0).apply { maxLines = 1 }
    private val eTypes = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }   // the type chips (above style)
    private val eIcons = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }   // the type symbols (inside style, reduced boxes, a narrow card)
    private val eStatus = StatusRow(ctx)           // the status badge, in the name line (item 95, R9 D12)
    private val pStatus = StatusRow(ctx)
    // elevated above the stat cards (the study strip lifts them by 2 dp, and a higher z draws over any sibling order)
    private val bubble = StatusBubble(ctx).apply { visibility = GONE; elevation = Ui.dp(ctx, 8f); outlineProvider = null }
    private val eHpText = Ui.text(ctx, 11f, 800, R.color.ag_sub0)
    private val eHp = BarView(ctx).apply { ghostOnDrop = true }
    private val eHpBox = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }   // compact cards: HP bar + "61 / 61 HP" side by side
    private val enemyCard = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        background = Ui.rounded(translucentCard(ctx), 12, ctx)
    }
    // "NEW" sticker: red text, tilted like a sticker, on the wild card's top-left corner (no background). Shown while the wild species isn't caught yet.
    // A sibling of the card (not a child) so it can overhang the card's corner without being clipped; [placeNewTag] puts it there.
    // Round 10 G: when it would be cut (or cover the HP numbers) there, [updateNewTag] moves it to another corner of the card ([NewTagRule]).
    // PLAN 139 / NEW colour: its own strong red with a halo in the page colour, and drawn again over the battle frame ([onDrawForeground]).
    private val newTag = Ui.text(ctx, 8f, 900, R.color.ag_new_tag).apply {
        text = "NEW"
        setShadowLayer(Ui.dp(ctx, 1.5f), 0f, 0f, Ui.c(ctx, R.color.ag_base))
        maxLines = 1
        letterSpacing = 0.04f
        rotation = -20f
        contentDescription = "New: not caught yet"
    }
    private val enemyBlock = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }

    /**
     * PLAN 140: the two info boxes live in this layer, which (like every group inside the boxes, see [init]) clips nothing. The arena
     * clips EACH of its children at the child's own bounds (`clipChildren`, the sprites' faint / entrance animations rely on it), so a
     * box that was a direct child lost everything it draws outside its own rectangle: a drop shadow, a text outline, a bar ring. The
     * layer is as big as the arena (so the boxes keep the arena's coordinates) and is clipped by it only at the arena's own edge.
     */
    private val cardLayer = FrameLayout(ctx)

    /**
     * PLAN 207: the small side pop-ups (the team XP cards, the status note) live in this layer. The arena draws it AGAIN in
     * [onDrawForeground], after the battle frame, so the frame never covers a pop-up (like the NEW sticker, PLAN 139); the pop-ups are
     * not clipped at their own box either. It is the last child, as big as the arena.
     */
    private val popLayer = FrameLayout(ctx)

    // ---- player info card ----
    private val pName = FitName.make(ctx)
    private val pLevel = Ui.text(ctx, 12f, 800, R.color.ag_sub0).apply { maxLines = 1 }
    private val pHp = BarView(ctx).apply { ghostOnDrop = true }
    private val pHpText = Ui.text(ctx, 11f, 800, R.color.ag_sub0).apply { maxLines = 1 }
    private val pHpLabel = Ui.text(ctx, InfoBoxGeo.NUM, 800, R.color.ag_sub0).apply { maxLines = 1; text = "HP" }   // above style: "HP" before the number
    private val pXpText = Ui.text(ctx, 11f, 800, R.color.ag_sub0).apply { maxLines = 1 }
    private val pXp = BarView(ctx)
    private val pHpBox = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }   // compact cards: HP bar + "61 / 61 HP" side by side
    private val playerCard = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        background = Ui.rounded(translucentCard(ctx), 12, ctx)
    }

    private val eRow = NameRow(ctx, eName, eLevel)
    private val pRow = NameRow(ctx, pName, pLevel)
    private val eNumRow = ChipsNumberRow(ctx, eTypes, eIcons, eHpText)    // above style, wild: the type chips and the HP number
    private val pNumRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }   // above style, ours: "HP" and the HP number
    private val pXpRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }    // above style, ours: the XP bar and its number

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

    /**
     * Round 9 D12: the design of the info boxes ([AppPrefs.infoBoxStyle]; the battle panel sets it, so every host follows the
     * setting live). The compact cards (pref / small windows) keep their own shape in both.
     */
    var infoBoxStyle: InfoBoxStyle = InfoBoxStyle.DEFAULT
        set(v) { if (field != v) { field = v; updateCards() } }

    /**
     * Round 10 W8: the look of the info boxes' HP / XP bars ([AppPrefs.hpBarLook]; the battle panel sets it). In [HpBarLook.PIXEL] each
     * bar is laid out 2 outline units taller ([fitBars]), so the boxes grow like the mock and the box rule follows ([extraHeight]).
     */
    var hpBarLook: HpBarLook = HpBarLook.DEFAULT
        set(v) { if (field != v) { field = v; eHp.look = v; pHp.look = v; pXp.look = v; updateCards() } }

    /** Picture mode: the plan drew the boxes under the scale their optional parts (type chips, XP number) are readable at, so they are left out ([StagePlan.reduced]). */
    private var partsReduced = false

    /**
     * Round 10 W7: the look of the two info boxes ([AppPrefs.infoBoxLook]; the battle panel sets it, so every host follows the setting
     * live). The layout [infoBoxStyle] combines with every look. [InfoBoxLook.TODAY] is the boxes exactly as they were; what each other
     * look paints is [InfoBoxSkin]. The HUD also moves the boxes to two opposite corners while a picture shows ([StageGeo] corners mode).
     */
    var infoBoxLook: InfoBoxLook = InfoBoxLook.DEFAULT
        set(v) { if (field != v) { field = v; applyLook(); requestLayout() } }

    // the look's state (see "info box looks" below): the inside colours of a look that is the same in light and dark (null = the theme's),
    // what the colours are set on (levels, HP fractions, the wild types), the Pokémon's first type colours (the type look), the glass behind each box
    private var ink: InfoBoxSkin.Ink? = null
    private var skinKey = "${InfoBoxLook.DEFAULT.name}|false|0|0"
    private var baseElevation = 0f
    private var eLvl = 0
    private var pLvl = 0
    private var eHpFrac = -1f
    private var pHpFrac = -1f
    private var enemyTypes: List<Type> = emptyList()
    private var eTypeColor = 0
    private var pTypeColor = 0
    private var eGlass: ArenaGlass? = null
    private var pGlass: ArenaGlass? = null

    // what the cards say (kept so a new style or a new text floor can write it again)
    private var eHpStr = ""
    private var pHpStr = ""
    private var xpStr = ""
    private var builtShape = -1

    /** Each stat card under (or next to) its own Pokémon ([AppPrefs.cardsUnderOwn]); false = the classic layout (opposite sides). Plain mode only: a battle picture has the automatic box rule ([StageGeo]). */
    var cardsUnderOwn: Boolean = true
        set(v) { if (field != v) { field = v; requestLayout() } }

    /**
     * How far the stage rule may zoom the battle picture in (round 8): 1 = not at all, 0.72 = up to ~28 % (the floating window,
     * which is small and would rather show less of the picture; [StageFit.fit]). Picture mode only.
     */
    var stageZoom: Float = 1f
        set(v) { if (v != field) { field = v; requestLayout() } }

    /**
     * Round 9 wave 2: the panel's draw scale ([PanelFit.Fit.scale], 1 = unscaled). The info-box rule sizes the boxes from the arena's
     * on-screen height (virtual height x this). Set by [BattlePanelView.apply].
     */
    var panelScale: Float = 1f
        set(v) { if (v != field) { field = v; requestLayout() } }

    /** Round 9 wave 2: how far (virtual dp) the battle frame reaches into the arena on each side; the info boxes stay inside it. 0 = no frame. */
    var frameInsetDp: Float = 0f
        set(v) { if (v != field) { field = v; requestLayout() } }

    /**
     * PLAN 140: what the battle frame draws deeper than its band (virtual dp, [FrameSafe]): [frameTopDp] the top (the ice's icicles),
     * [frameCornerDp] the side of the square each corner pattern covers. With [frameInsetDp] they are the frame's safe area: the info
     * boxes stay inside it, so nothing of the frame is drawn over a box. 0 / 0 = just the band.
     */
    var frameTopDp: Float = 0f
        set(v) { if (v != field) { field = v; requestLayout() } }
    var frameCornerDp: Float = 0f
        set(v) { if (v != field) { field = v; requestLayout() } }

    /** The frame's safe area in virtual dp; null = no frame. */
    private fun frameSafe(): FrameSafe? =
        if (frameInsetDp <= 0f && frameTopDp <= 0f && frameCornerDp <= 0f) null else FrameSafe(frameInsetDp, max(frameInsetDp, frameTopDp), frameCornerDp)

    /**
     * Round 10 B20: the Pokémon size ([PokemonSize], [AppPrefs.pokemonSize] / 100; the battle panel sets it): both sprites are drawn this much
     * bigger or smaller around their feet in every layout, kept inside the room the layout has. 1 = the layout before the setting.
     */
    var pokemonSize: Float = 1f
        set(v) { if (abs(v - field) > 0.0001f) { field = v; requestLayout() } }

    /** The picture's stage and the layout made from it while a picture shows (null = plain mode); see [layoutStage]. */
    internal var stageNow: Stage? = null
        private set
    internal var stagePlanNow: StagePlan? = null
        private set

    /** True while the two stat cards sit in the middle column between the sprites (the corner layout would cover one; never in picture mode). */
    val middleLayout: Boolean get() = stagePlanNow == null && plan?.middle == true

    /** How much bigger than their normal size the two stat cards are drawn (1 in the study layouts). */
    val cardScale: Float get() = stagePlanNow?.k ?: if (compact || studyStrip) 1f else plan?.k ?: 1f

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
        // PLAN 140: nothing inside the info boxes (and the layers that hold the boxes and the pop-ups) cuts a child at its own bounds
        for (g in listOf<ViewGroup>(cardLayer, popLayer, enemyBlock, enemyCard, playerCard, eRow, pRow, eNumRow, pNumRow, pXpRow, eHpBox, pHpBox, eTypes, eIcons, eStatus, pStatus)) {
            g.clipChildren = false
            g.clipToPadding = false
        }
        background = backdrop   // the plain gradient, or a downloaded battle picture (R7-07)
        backdrop.onPictureChange = { shown -> setPlatformsShown(!shown); invalidateGlass(); requestLayout() }   // R8-04: another picture = other stand points
        backdrop.onSettled = { fireBackdropReady() }   // R9 C4: the next wild one waits for this
        enemySprite.onArtChange = { artChanged() }    // R8-FIX M2: a new image / placeholder can change what the cards must keep clear of
        playerSprite.onArtChange = { artChanged() }

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

        // the cards' parts are put together by [buildCards] (the style and the compact pref decide how)
        pXp.setTrackColor(Ui.c(ctx, R.color.ag_s0))
        enemyBlock.addView(enemyCard, LinearLayout.LayoutParams(-1, -2))
        buildCards()

        banner.addView(bannerTitle)

        addView(platEnemy, LayoutParams(0, 0))
        addView(platPlayer, LayoutParams(0, 0))
        addView(enemyHolder, LayoutParams(0, 0))
        addView(playerHolder, LayoutParams(0, 0))
        addView(moveFx, LayoutParams(-1, -1))
        cardLayer.addView(enemyBlock, LayoutParams(0, -2))
        cardLayer.addView(playerCard, LayoutParams(0, -2))
        addView(cardLayer, LayoutParams(-1, -1))
        addView(newTag, LayoutParams(-2, -2, Gravity.TOP or Gravity.LEFT))
        addView(stars, LayoutParams(-1, -1))
        addView(banner, LayoutParams(-2, -2, Gravity.TOP or Gravity.LEFT))   // placed by [placeBanner] (translation), never by margins
        popLayer.addView(bubble, LayoutParams(-2, -2, Gravity.TOP or Gravity.LEFT))
        addView(fx, LayoutParams(-1, -1))
        addView(popLayer, LayoutParams(-1, -1))
        bubble.setOnClickListener { hideStatusBubble() }
        eStatus.onTap = { showStatusBubble(it) }
        pStatus.onTap = { showStatusBubble(it) }

        // Nothing until the first encounter.
        setEnemyShown(false)
        setNewTag(false)
    }

    /**
     * PLAN 139: the battle frame is this view's foreground, drawn over every child; the NEW sticker goes over it (and over the cards, fx
     * and texts). PLAN 207: so do the side pop-ups ([popLayer]), after it: the layer is skipped in the normal child pass ([drawChild])
     * and drawn only here, so a pop-up is drawn once, above the frame.
     */
    override fun onDrawForeground(canvas: Canvas) {
        super.onDrawForeground(canvas)
        if (newTag.visibility == VISIBLE && newTag.alpha > 0f) drawChild(canvas, newTag, drawingTime)
        if (popLayer.visibility == VISIBLE && popLayer.childCount > 0) {
            popOnTop = true
            try { drawChild(canvas, popLayer, drawingTime) } finally { popOnTop = false }
        }
    }

    private var popOnTop = false

    override fun drawChild(canvas: Canvas, child: View, drawingTime: Long): Boolean =
        if (child === popLayer && !popOnTop) false else super.drawChild(canvas, child, drawingTime)

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
        if (!layoutStage(w, h)) { if (compact || studyStrip) layoutStudy(w, h) else layoutPlan(w, h) }
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
        // the real cards can be taller than the model's at a big system font: the plan is made for their real height (0 extra at the normal size)
        val c0 = ArenaGeo.cards(floorSp, cardsCompact, xpOn, infoBoxStyle)
        val c = ArenaGeo.Cards(c0.wildW, c0.wildH + extraHeight(enemyBlock, c0.wildW, d, c0.wildH),
            c0.playerW, c0.playerH + extraHeight(playerCard, c0.playerW, d, c0.playerH))
        val p0 = ArenaGeo.plan(w / d, h / d, c, cardsCompact, cardsUnderOwn, frameSafe())   // PLAN 140: the cards stay inside the frame's safe area
        val sized = PokemonSize.fitHolders(w / d, h / d, p0.wildSprite, p0.playerSprite, listOf(p0.wildCard, p0.playerCard), pokemonSize)
        val p = if (sized.changed) sizedPlan(p0, sized) else p0   // Round 10 B20: the same plan at 100 %
        setSpriteFactor(if (sized.changed) sized.f else 1f)
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

    /** [p] with its two sprite holders as [sized] says (Round 10 B20); the platforms follow their Pokémon (scaled around their middle, moved with it). */
    private fun sizedPlan(p: ArenaGeo.Plan, sized: PokemonSize.Sized): ArenaGeo.Plan {
        fun plat(b: ArenaGeo.Box, dx: Float) = ArenaGeo.Box(b.x + b.w / 2f - b.w * sized.f / 2f + dx, b.y + b.h / 2f - b.h * sized.f / 2f, b.w * sized.f, b.h * sized.f)
        return ArenaGeo.Plan(p.layout, p.fits, p.wildCard, p.playerCard, sized.wild, sized.player,
            plat(p.wildPlat, sized.wildDx), plat(p.playerPlat, sized.playerDx), p.a, p.k)
    }

    /**
     * Picture mode (round 8, "Pokémon belong to the picture"): while a battle picture shows, the Pokémon stand on its two stand
     * points. [StageFit] crops the picture to this arena (the backdrop gets that stage) and says where the feet are and how big the
     * frames are; [StageGeo] puts the two info boxes by the round 9 automatic rule (sized from the panel's on-screen height, on the
     * outer side of their own Pokémon, a gap from every real art box; [panelScale] and [frameInsetDp] feed it). The sprite holders
     * are the plan's square frames (bottom = the feet, so [SpriteView.anchorFeet] stands the visible feet on them), the cards are
     * laid out at their normal width and drawn [StagePlan.k] times as big from their corner (under 1 in small panels). The study
     * layouts use their own card widths and a roomy scale of 1; the plain ellipses are hidden. False = plain mode (no picture, or no
     * size yet): the caller lays out the plain way.
     */
    private fun layoutStage(w: Int, h: Int): Boolean {
        val bg = backdrop.shown
        val d = Ui.dp(context, 1f)
        val stage = if (bg != null && w > 0 && h > 0) fitStage(bg, w / d, h / d) else null
        backdrop.setStage(stage)
        stageNow = stage
        if (bg == null || stage == null) {
            stagePlanNow = null
            setPartsReduced(false)
            for (s in arrayOf(enemySprite, playerSprite)) { s.anchorFeet = false; s.byFrame = false }
            return false
        }
        val study = compact || studyStrip
        // The boxes' sizes are the model's ([InfoBoxGeo]: calibrated on the real views by the shots), with their optional parts and without.
        val c0 = ArenaGeo.cards(floorSp, cardsCompact, xpOn, infoBoxStyle)
        val cr0 = ArenaGeo.cards(floorSp, cardsCompact, xpOn, infoBoxStyle, reduced = true)
        val widths = studyCardWidths(w)
        val wildW = if (study) widths.first / d else c0.wildW
        val playerW = if (study) widths.second / d else c0.playerW
        // a bigger system font makes the real text taller than the model's: the boxes are as high as that (the same extra for both levels)
        val now = if (partsReduced) cr0 else c0
        val wildExtra = extraHeight(enemyBlock, wildW, d, now.wildH)
        val playerExtra = extraHeight(playerCard, playerW, d, now.playerH)
        val c = ArenaGeo.Cards(c0.wildW, c0.wildH + wildExtra, c0.playerW, c0.playerH + playerExtra)
        val cr = ArenaGeo.Cards(cr0.wildW, cr0.wildH + wildExtra, cr0.playerW, cr0.playerH + playerExtra)
        val k0 = if (study) 1f else min(w / d / 300f, h / d / 430f).coerceIn(1f, 1.4f)
        // The smallest text of a full and of a reduced box: no box is drawn so small that it falls under the readable size (R9 wave 3).
        val fullSp = if (cardsCompact) InfoBoxGeo.COMPACT_SP else InfoBoxGeo.smallestSp(infoBoxStyle, wantXpText && xpOn && wantXpBar, reduced = false)
        val minSp = if (cardsCompact) InfoBoxGeo.COMPACT_SP else InfoBoxGeo.smallestSp(infoBoxStyle, xpText = false, reduced = true)
        // The boxes keep clear of each sprite's REAL art box (the pixels it draws, R9 D1) at the planned frame, not of the frame or a flat 85 % of it.
        fun planAt(size: Float, atOne: Float) = StageGeo.plan(w / d, h / d, stage, wildW, c.wildH, playerW, c.playerH, k0,
            panelScale, floorSp, frameInsetDp, { side -> enemySprite.artShare(side * d) }, { side -> playerSprite.artShare(side * d) },
            cr.wildH, cr.playerH, minSp, fullSp, size, atOne, InfoBoxSkin.corners(infoBoxLook), frameSafe())   // Round 10 W7: the HUD look = the corners mode; PLAN 140: the frame's safe area
        // Round 10 B20: the plan at 100 % first (the layout before the setting); a Pokémon size draws both sprites that many times what a
        // frame 1 / size as big would ([SpriteView.sizeFactor]) and is planned with its sprites like that. A bigger size is used only while it
        // stays clear and its rule step is not lower than the 100 % plan's (so it is never a smaller Pokémon than 100 %).
        setSpriteFactor(1f)
        var p = planAt(1f, 0f)
        if (abs(pokemonSize - 1f) > 0.0001f) {
            setSpriteFactor(pokemonSize)
            val sized = planAt(pokemonSize, p.spriteScale)
            if (StageGeo.keepSized(pokemonSize, p, sized)) p = sized else setSpriteFactor(1f)
        }
        stagePlanNow = p
        setPartsReduced(p.reduced)
        artOf(stage, p, d, plannedArt)
        for (s in arrayOf(enemySprite, playerSprite)) {
            s.anchorFeet = true
            s.byFrame = true    // R8-FIX M1: a small species is small in its frame, a big one big (frame / 96 px per sprite pixel)
            if (s.scaleDp != BIG_SPRITE_DP) s.scaleDp = BIG_SPRITE_DP   // the holder decides the size
        }
        putBox(enemyHolder, p.wildSprite, d)
        putBox(playerHolder, p.playerSprite, d)
        putBox(enemyBlock, p.wildCard, d, wrapHeight = true, k = p.k)
        putBox(playerCard, p.playerCard, d, wrapHeight = true, k = p.k)
        return true
    }

    /**
     * How much taller (virtual dp) [card], laid out [widthDp] wide, really is than the model's [modelH]: 0 at the normal font size; with
     * the system font enlarged the texts grow and the model (made at the normal size) is short.
     */
    private fun extraHeight(card: View, widthDp: Float, d: Float, modelH: Float): Float {
        card.measure(MeasureSpec.makeMeasureSpec(Math.round(widthDp * d), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
        return max(0f, card.measuredHeight / d - modelH - 1f)   // 1 dp of slack: the model is a hair taller than the real card at the normal size
    }

    /** Drops (or brings back) the boxes' optional parts as the plan says ([StagePlan.reduced]); a no-op while it is the same. */
    private fun setPartsReduced(v: Boolean) {
        if (partsReduced == v) return
        partsReduced = v
        updateCards()
    }

    /**
     * Fills [out] with what the two sprites draw (the [SpriteView.artShare] art box: left, top, width, height shares, wild then ours)
     * at the full frame and at the planned one: the numbers [StageGeo.plan] decided the boxes by. No share yet (nothing shown) = the
     * plan's default.
     */
    private fun artOf(stage: Stage, p: StagePlan, d: Float, out: FloatArray) {
        fun put(i: Int, spr: SpriteView, frameDp: Float) {
            val a = spr.artShare(frameDp * d) ?: ArtShare.DEFAULT
            out[i] = a.x; out[i + 1] = a.y; out[i + 2] = a.w; out[i + 3] = a.h
        }
        put(0, enemySprite, stage.wildFrame); put(4, enemySprite, p.wildSprite.w)
        put(8, playerSprite, stage.playerFrame); put(12, playerSprite, p.playerSprite.w)
    }
    private val plannedArt = FloatArray(16)
    private val artNow = FloatArray(16)

    /**
     * A sprite's image or placeholder changed: in picture mode, when that changes what the cards were planned around, lay out again
     * (so the cards move onto the freed room, or off the bigger Pokémon). The same numbers again = nothing; the layout itself never
     * changes a sprite's image, so this cannot loop.
     */
    private fun artChanged() {
        val stage = stageNow ?: return
        val p = stagePlanNow ?: return
        artOf(stage, p, Ui.dp(context, 1f), artNow)
        if (!java.util.Arrays.equals(artNow, plannedArt)) requestLayout()
    }

    /** The stage for [bg] in a panel of [w] x [h] dp; the same object while nothing it depends on changed (the backdrop re-lays-out on a new one). */
    private fun fitStage(bg: Backdrop, w: Float, h: Float): Stage? {
        val k = fitKey
        val old = fitted
        if (old != null && fitBg === bg && k[0] == w && k[1] == h && k[2] == stageZoom) return old
        val st = StageFit.fit(bg, w, h, stageZoom)
        fitBg = bg; k[0] = w; k[1] = h; k[2] = stageZoom
        fitted = st
        invalidateGlass()   // another crop of the picture: the glass boxes line up with it again
        return st
    }
    private var fitBg: Backdrop? = null
    private val fitKey = FloatArray(3)
    private var fitted: Stage? = null

    /** The study layouts' card widths in px (wild, ours): the same numbers [layoutStudy] uses. */
    private fun studyCardWidths(w: Int): Pair<Int, Int> {
        val max = ((w - dpi(30)) / 2).coerceAtLeast(dpi(if (studyStrip) 96 else 100))
        return min(dpi(InfoBoxGeo.W.toInt()), max) to min(dpi(InfoBoxGeo.W.toInt()), max)
    }

    /**
     * Places [v] on the box [b] (virtual dp, [d] px per dp). A sprite holder has its edges rounded to the pixel, so its bottom-centre is
     * its feet; a card ([wrapHeight]) is laid out at its normal size ([k] times smaller than the box: the box is as high as the card's
     * text, [InfoBoxGeo]) and drawn [k] times as big from its corner. Its height is given exactly, not wrapped: under k = 1 the
     * room under its top-left corner is smaller than the card's own height (a box on the panel's bottom edge), and a wrapped
     * card would be measured into it and cut off.
     */
    private fun putBox(v: View, b: ArenaGeo.Box, d: Float, wrapHeight: Boolean = false, k: Float = 1f) {
        val x0 = Math.round(b.x * d)
        val y0 = Math.round(b.y * d)
        val w = if (wrapHeight) Math.round(b.w / k * d) else Math.round(b.right * d) - x0
        place(v, w, if (wrapHeight) Math.round(b.h / k * d) else Math.round(b.bottom * d) - y0, Gravity.TOP or Gravity.LEFT, x0, y0, 0, 0)
        scaleFrom(v, k)
    }

    /**
     * The NEW sticker sits on the wild card's top-left corner, overhanging it a little, and scales with the card (any k; under 1 it stops at the size its text is readable at).
     * A card that touches the panel's edge (round 9 boxes) would push it out of the arena: it stays inside, over the card's corner.
     */
    private fun placeNewTag() {
        val card = enemyBlock.layoutParams as LayoutParams
        val k = enemyBlock.scaleX
        val lp = newTag.layoutParams as LayoutParams
        val edge = if (stagePlanNow != null) (frameInsetDp * Ui.dp(context, 1f)).toInt() else 0
        lp.setMargins(max(edge, card.leftMargin - (dpi(4) * k).toInt()), max(edge, card.topMargin - (dpi(3) * k).toInt()), 0, 0)
        // from its own top-left, so it stays on the card's corner at every k; its 8 sp text does not shrink under the readable size with a small box
        scaleFrom(newTag, max(k, StageGeo.textScale(8f, panelScale, floorSp)))
    }

    /**
     * Round 10 G (after layout): the sticker stays on today's corner when it fits there, else goes to the first other corner of the wild box
     * where the whole turned sticker is inside the panel and clear of the HP numbers ([NewTagRule]). A translation and a tilt on the same
     * view, so a fresh layout (the box moved, rescaled, the panel resized) always starts from today's margins and picks again.
     */
    private fun updateNewTag() {
        newTag.rotation = -NewTagRule.TILT; newTag.translationX = 0f; newTag.translationY = 0f
        if (newTag.visibility != VISIBLE || newTag.width <= 0 || enemyBlock.width <= 0 || width <= 0 || height <= 0) return
        val d = Ui.dp(context, 1f)
        val k = enemyBlock.scaleX
        val box = NewTagRule.Box(enemyBlock.left.toFloat(), enemyBlock.top.toFloat(), enemyBlock.left + enemyBlock.width * k, enemyBlock.top + enemyBlock.height * k)
        val edge = if (stagePlanNow != null) frameInsetDp * d else 0f
        val s = NewTagRule.place(box, width.toFloat(), height.toFloat(), edge, 4f * d * k, 3f * d * k,
            newTag.width.toFloat(), newTag.height.toFloat(), newTag.scaleX, hpNumbersRect(k, d))
        if (NewTagRule.keepsTodaysSpot(s)) return   // today's spot, exactly as placeNewTag left it (a pushed-in top-left one is applied below)
        newTag.rotation = s.angle
        newTag.translationX = s.x - newTag.left
        newTag.translationY = s.y - newTag.top
    }

    /** Where the wild card draws its HP numbers (the bar itself when the number is inside it), a dp of room round it, in the arena's px; null = not laid out. */
    private fun hpNumbersRect(k: Float, d: Float): NewTagRule.Box? {
        val v: View = if (eHpText.parent != null && eHpText.visibility != GONE) eHpText else eHp
        if (v.width <= 0 || v.height <= 0 || v.visibility == GONE) return null
        var x = 0f; var y = 0f
        var p: View = v
        while (p !== enemyBlock) {
            x += p.left; y += p.top
            p = (p.parent as? View) ?: return null
        }
        return NewTagRule.Box(enemyBlock.left + x * k, enemyBlock.top + y * k, enemyBlock.left + (x + v.width) * k, enemyBlock.top + (y + v.height) * k).grow(d)
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
        place(platEnemy, px(150f), px(30f), Gravity.TOP or Gravity.RIGHT, 0, px(92f) + down, px(22f), 0)
        place(platPlayer, px(180f), px(40f), Gravity.BOTTOM or Gravity.LEFT, px(4f), 0, 0, -px(8f))
        place(enemyHolder, px(110f), px(100f), Gravity.TOP or Gravity.RIGHT, 0, px(12f) + down, px(42f), 0)
        place(playerHolder, px(140f), px(128f), Gravity.BOTTOM or Gravity.LEFT, px(22f), 0, 0, px(6f))
        val infoMax = ((w - dpi(30)) / 2).coerceAtLeast(dpi(if (compact) 100 else 120))
        val edge = if (compact) 6 else 10
        place(enemyBlock, min(dpi(InfoBoxGeo.W.toInt()), infoMax), -2, Gravity.TOP or Gravity.LEFT, dpi(edge), dpi(edge), 0, 0)
        place(playerCard, min(dpi(InfoBoxGeo.W.toInt()), infoMax), -2, Gravity.BOTTOM or Gravity.RIGHT, 0, 0, dpi(edge), dpi(if (compact) 6 else 12))
        if (studyStrip) {
            // Approved 146 dp strip geometry, scaled only when landscape supplies extra arena height.
            val scale = (h / Ui.dp(context, 146f)).coerceIn(.6f, 1.5f)
            fun sp(v: Int) = (dpi(v) * scale).toInt()
            place(platEnemy, sp(120), sp(24), Gravity.TOP or Gravity.RIGHT, 0, sp(70), sp(16), 0)
            place(platPlayer, sp(150), sp(34), Gravity.BOTTOM or Gravity.LEFT, 0, 0, 0, -sp(10))
            place(enemyHolder, sp(96), sp(80), Gravity.TOP or Gravity.RIGHT, 0, sp(4), sp(26), 0)
            place(playerHolder, sp(116), sp(96), Gravity.BOTTOM or Gravity.LEFT, sp(14), 0, 0, sp(2))
            // a narrow pane: the cards may go down to 96 dp (the name shortens) so ours covers no more of our sprite's box than before the HP number moved up
            val stripMax = ((w - dpi(30)) / 2).coerceAtLeast(dpi(96))
            place(enemyBlock, min(dpi(InfoBoxGeo.W.toInt()), stripMax), -2, Gravity.TOP or Gravity.LEFT, dpi(8), dpi(8), 0, 0)
            place(playerCard, min(dpi(InfoBoxGeo.W.toInt()), stripMax), -2, Gravity.BOTTOM or Gravity.RIGHT, 0, 0, dpi(8), dpi(8))
        }
        clearStudyCards(w, h)
        setSpriteFactor(studySize(w, h))
    }

    /**
     * PLAN 140: the study layouts put the cards at fixed margins from the corners; with a battle frame they move inside its band and
     * off its corner squares ([FrameSafe.clearInto], in this arena's px). No frame = nothing moves.
     */
    private fun clearStudyCards(w: Int, h: Int) {
        val s = frameSafe() ?: return
        val d = Ui.dp(context, 1f)
        val px = FrameSafe(s.edge * d, s.top * d, s.corner * d)
        val ec = enemyBlock.layoutParams as LayoutParams       // TOP | LEFT
        val pc = playerCard.layoutParams as LayoutParams       // BOTTOM | RIGHT
        val ech = cardHeightPx(enemyBlock, ec.width)
        val pch = cardHeightPx(playerCard, pc.width)
        val wild = floatArrayOf(ec.leftMargin.toFloat(), ec.topMargin.toFloat(), ec.width.toFloat(), ech.toFloat())
        px.clearInto(wild, w.toFloat(), h.toFloat())
        ec.leftMargin = Math.round(wild[0]); ec.topMargin = Math.round(wild[1])
        val ours = floatArrayOf((w - pc.rightMargin - pc.width).toFloat(), (h - pc.bottomMargin - pch).toFloat(), pc.width.toFloat(), pch.toFloat())
        px.clearInto(ours, w.toFloat(), h.toFloat())
        pc.rightMargin = Math.round(w - ours[0] - pc.width); pc.bottomMargin = Math.round(h - ours[1] - pch)
    }

    /** Round 10 B20: both sprites draw [f] times what they would in a holder 1 / [f] as big (1 = as before the setting; see [SpriteView.sizeFactor]). */
    private fun setSpriteFactor(f: Float) {
        enemySprite.sizeFactor = f
        playerSprite.sizeFactor = f
    }

    /**
     * Round 10 B20 for the study layouts (px): the two holders, laid out as above, are scaled around their feet by the Pokémon size, kept to
     * the room ([PokemonSize.fitHolders]: art inside the arena, clear of both cards). Their platforms follow. Returns the factor used (1 = nothing changed).
     */
    private fun studySize(w: Int, h: Int): Float {
        if (abs(pokemonSize - 1f) < 0.0001f) return 1f
        val wl = enemyHolder.layoutParams as LayoutParams      // TOP | RIGHT
        val pl = playerHolder.layoutParams as LayoutParams     // BOTTOM | LEFT
        val wild = ArenaGeo.Box((w - wl.rightMargin - wl.width).toFloat(), wl.topMargin.toFloat(), wl.width.toFloat(), wl.height.toFloat())
        val player = ArenaGeo.Box(pl.leftMargin.toFloat(), (h - pl.bottomMargin - pl.height).toFloat(), pl.width.toFloat(), pl.height.toFloat())
        val ec = enemyBlock.layoutParams as LayoutParams       // TOP | LEFT
        val pc = playerCard.layoutParams as LayoutParams       // BOTTOM | RIGHT
        val ech = cardHeightPx(enemyBlock, ec.width)
        val pch = cardHeightPx(playerCard, pc.width)
        val cards = listOf(
            ArenaGeo.Box(ec.leftMargin.toFloat(), ec.topMargin.toFloat(), ec.width.toFloat(), ech.toFloat()),
            ArenaGeo.Box((w - pc.rightMargin - pc.width).toFloat(), (h - pc.bottomMargin - pch).toFloat(), pc.width.toFloat(), pch.toFloat()),
        )
        val sized = PokemonSize.fitHolders(w.toFloat(), h.toFloat(), wild, player, cards, pokemonSize)
        if (!sized.changed) return 1f
        place(enemyHolder, Math.round(sized.wild.w), Math.round(sized.wild.h), Gravity.TOP or Gravity.RIGHT, 0, Math.round(sized.wild.y), Math.round(w - sized.wild.right), 0)
        place(playerHolder, Math.round(sized.player.w), Math.round(sized.player.h), Gravity.BOTTOM or Gravity.LEFT, Math.round(sized.player.x), 0, 0, Math.round(h - sized.player.bottom))
        // the platforms: scaled around their middle, moved with their Pokémon
        val we = platEnemy.layoutParams as LayoutParams        // TOP | RIGHT
        val pp = platPlayer.layoutParams as LayoutParams       // BOTTOM | LEFT (its bottom margin is negative: it hangs below the arena)
        val ew = we.width * sized.f; val eh = we.height * sized.f
        val ex = w - we.rightMargin - we.width / 2f - ew / 2f + sized.wildDx
        place(platEnemy, Math.round(ew), Math.round(eh), Gravity.TOP or Gravity.RIGHT, 0, Math.round(we.topMargin + (we.height - eh) / 2f), Math.round(w - ex - ew), 0)
        val pw = pp.width * sized.f; val ph = pp.height * sized.f
        val px = pp.leftMargin + pp.width / 2f - pw / 2f + sized.playerDx
        place(platPlayer, Math.round(pw), Math.round(ph), Gravity.BOTTOM or Gravity.LEFT, Math.round(px), 0, 0, Math.round(pp.bottomMargin - (ph - pp.height) / 2f))
        return sized.f
    }

    /** The height (px) a card laid out [widthPx] wide wants (the study layouts give it no height of its own). */
    private fun cardHeightPx(card: View, widthPx: Int): Int {
        card.measure(MeasureSpec.makeMeasureSpec(widthPx, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
        return card.measuredHeight
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
     * The battle background for a wild Pokémon with [types] ([BackdropCatalog.pick]): one of its type's pictures, [seed] (from the
     * wild one's uid, [BackdropCatalog.seed]) fixing which for the whole fight; the plain gradient until it is there. Plain too
     * while pictures or downloads are off in the settings ([Backdrops.allowed]).
     */
    fun setBackdrop(types: List<Type>, seed: Int, legendary: Boolean = false) {
        lastBackdrop = Triple(types, seed, legendary)
        val download = Backdrops.allowed(Sprites.enabled(context), AppPrefs.backgroundPictures(context))
        if (!download) { backdrop.plain(); return }
        backdrop.request(BackdropCatalog.pick(types, seed, legendary), download)
    }

    /** The plain gradient with the two platform ellipses. */
    fun setPlainBackdrop() = backdrop.plain()

    private var lastBackdrop: Triple<List<Type>, Int, Boolean>? = null

    /**
     * Round 10 B19: "Background pictures" was switched in the battle's view settings (or Settings) while this arena is open: asks again for the
     * picture of the wild Pokémon last shown, which is the plain gradient when pictures are off now. Nothing before the first encounter.
     */
    fun reapplyBackdrop() {
        val last = lastBackdrop ?: return
        setBackdrop(last.first, last.second, last.third)
    }

    /**
     * R9 C4: runs [then] once the background asked for last by [setBackdrop] is settled (applied and the stand points asked to
     * re-plan, or plain, or its load failed): at once when nothing is loading, else at the latest [capMs] from now (the wild one then
     * comes in anyway and may move once, as it did before). Only one waiter at a time: a newer call drops an older one unrun.
     */
    fun whenBackdropReady(capMs: Long, then: () -> Unit) {
        capHandler.removeCallbacks(backdropCap)
        backdropWaiter = null
        if (!backdrop.loading) { then(); return }
        backdropWaiter = then
        capHandler.postDelayed(backdropCap, capMs)   // the main looper's, not the view's: the cap runs whether or not the view is attached
    }

    private var backdropWaiter: (() -> Unit)? = null
    private val capHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val backdropCap = Runnable { fireBackdropReady() }

    private fun fireBackdropReady() {
        capHandler.removeCallbacks(backdropCap)
        val w = backdropWaiter ?: return
        backdropWaiter = null
        w()
    }

    /** R9 C4: true while the background asked for by [setBackdrop] is still loading (the stand points will change when it comes). */
    val backdropLoading: Boolean get() = backdrop.loading

    /** R9 C4: how long the background just applied still cross-fades (ms; 0 = none). The next wild one comes in after it. */
    fun backdropFadeMs(): Long = backdrop.fadeRemainingMs()

    /** R9 C4: starts loading the background of a queued encounter (its wild one's types / uid are known) so it is ready when the wild one comes. */
    fun prefetchBackdrop(types: List<Type>, seed: Int, legendary: Boolean = false) {
        val download = Backdrops.allowed(Sprites.enabled(context), AppPrefs.backgroundPictures(context))
        if (download) backdrop.prefetch(BackdropCatalog.pick(types, seed, legendary), download)
    }

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
                it.setSpan(ForegroundColorSpan(ink?.yellow ?: Ui.c(context, R.color.ag_yellow)), at, it.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                it.setSpan(RelativeSizeSpan(12f / 14f), at, it.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        } else name
        eLvl = level
        eLevel.text = "Lv $level"; eLevel.setTextColor(levelColor(level))
        enemyTypes = types
        eTypeColor = types.firstOrNull()?.let { Ui.typeColor(it) } ?: 0
        buildEnemyTypes()
        if (InfoBoxSkin.needsType(infoBoxLook)) applyBackgrounds()   // the type look: this Pokémon's colour
    }

    /** The wild one's type chips and symbols, painted in the look's palette (the dark one for a look that is the same in light and dark). */
    private fun buildEnemyTypes() {
        eTypes.removeAllViews()
        eIcons.removeAllViews()
        for (t in enemyTypes) {
            eTypes.addView(TypeChips.chip(context, t, dark = insideDark()).also { it.maxLines = 1; sz(it, InfoBoxGeo.CHIP) }, LinearLayout.LayoutParams(-2, -2).also { it.marginEnd = dpi(3) })
            eIcons.addView(typeSquare(t), LinearLayout.LayoutParams(-2, -2).also { it.marginEnd = dpi(2) })
        }
        fitSquares()
    }

    /** A type as a small coloured square with its symbol in it (D12 `typeIcons`): the inside style's, and the swap for chips that do not fit. */
    private fun typeSquare(t: Type) = ImageView(context).apply {
        val dk = insideDark()
        setImageDrawable(TypeChips.tinted(context, t, TypeInk.ink(Ui.typeColor(t), dk)))
        background = Ui.rounded(TypeInk.fill(Ui.typeColor(t), dk), 4, context)
        scaleType = ImageView.ScaleType.FIT_CENTER
        contentDescription = t.label
    }

    /** The symbols' size follows the raised text like the bars ([InfoBoxGeo.SQUARE], the symbol 11 of the 15). */
    private fun fitSquares() {
        val side = dpf(InfoBoxGeo.SQUARE * TextMath.bar(floorSp))
        val pad = (side * 2f / 15f + 0.5f).toInt()
        for (i in 0 until eIcons.childCount) {
            val v = eIcons.getChildAt(i)
            val lp = v.layoutParams
            if (lp.width != side || lp.height != side) { lp.width = side; lp.height = side; v.layoutParams = lp }
            v.setPadding(pad, pad, pad, pad)
        }
    }

    fun setEnemyHp(hp: Int, max: Int, animate: Boolean) {
        val f = if (max > 0) hp.toFloat() / max else 0f
        eHpFrac = f
        eHp.set(f, hpColorOf(f), animate)
        eHpStr = DisplayNumbers.hp(hp, max)
        showHpText(eHpText, eHp, eHpStr)
    }

    /** The red "NEW" sticker on the wild card's corner: shown while the wild species isn't caught yet. */
    fun setNewTag(isNew: Boolean) {
        wildIsNew = isNew
        showNewTag()
    }

    /** The sticker follows the wild card: shown only while the card is (and the species is new). */
    private fun showNewTag() { newTag.visibility = if (wildIsNew && cardShown) VISIBLE else INVISIBLE; updateNewTag() }

    /**
     * The study strips' compact look (narrow split-screen strip): the info cards keep name, level and HP (no type chips, XP number or
     * XP bar). Picture-in-picture and the floating battle do not use it any more: they show the normal arena, scaled by the
     * panel ([configure]).
     */
    fun setCompact(on: Boolean) {
        if (compact == on) return
        compact = on
        wantTypes = !on
        wantXpText = !on
        wantXpBar = !on
        sz(bannerTitle, if (on) 19f else 26f)
        updateCards()
    }

    /** Restores the type and XP rows hidden by [setCompact]: the study strip's cards are the normal boxes (R9 D12), scaled by the plan or the strip's own geometry. */
    fun setStudyStrip(on: Boolean) {
        studyStrip = on
        wantTypes = true
        wantXpText = true
        wantXpBar = true
        baseElevation = Ui.dp(context, 2f)
        applyBackgrounds(force = true)   // the card colour (or the look's box) with the strip's corner radius, and the strip's 2 dp lift
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

    /** Which parts of the box show: the type chips / symbols, the XP bar, the XP number (not in a reduced box, not when the panel stepped the XP numbers out). */
    private fun xpTextShown() = wantXpText && xpOn && !partsReduced && !cardsCompact && wantXpBar

    /** The inside style: the HP / XP numbers are written in their bars (compact cards never). */
    private val inside get() = infoBoxStyle == InfoBoxStyle.INSIDE && !cardsCompact

    /**
     * Puts the card parts together for the current shape (above / inside / compact): the name line (name, [wild: symbols,] badge,
     * Lv), then the numbers and the bars. Does nothing while the shape is the one already built; the views are the same ones, so
     * their texts and bars stay.
     */
    private fun buildCards() {
        val shape = if (cardsCompact) 2 else infoBoxStyle.ordinal
        if (shape == builtShape) return
        builtShape = shape
        for (v in listOf<ViewGroup>(enemyCard, playerCard, eRow, pRow, eNumRow, pNumRow, pXpRow, eHpBox, pHpBox)) v.removeAllViews()
        fun lp(w: Int, h: Int, top: Int = 0, start: Int = 0) = LinearLayout.LayoutParams(w, h).also { it.topMargin = dpi(top); it.marginStart = dpi(start) }
        val badge = { LinearLayout.LayoutParams(-2, -2).also { it.marginStart = dpi(1) } }
        fun nameRow(row: NameRow, name: View, status: View, level: View, symbols: View? = null) {
            row.addView(name, LinearLayout.LayoutParams(0, -2, 1f))
            if (symbols != null) row.addView(symbols, lp(-2, -2, start = 4))
            row.addView(status, badge())
            row.addView(level, lp(-2, -2, start = 4))
            row.dropForName = symbols
        }
        when (shape) {
            0 -> {   // number above the bar
                for (c in listOf(enemyCard, playerCard)) c.setPadding(dpi(9), dpi(6), dpi(9), dpi(7))
                nameRow(eRow, eName, eStatus, eLevel)
                nameRow(pRow, pName, pStatus, pLevel)
                eNumRow.addView(eTypes, lp(-2, -2)); eNumRow.addView(eIcons, lp(-2, -2)); eNumRow.addView(View(context), lp(0, 1).also { it.weight = 1f }); eNumRow.addView(eHpText, lp(-2, -2, start = 6))
                pNumRow.addView(pHpLabel, lp(-2, -2)); pNumRow.addView(View(context), lp(0, 1).also { it.weight = 1f }); pNumRow.addView(pHpText, lp(-2, -2, start = 6))
                pXpRow.addView(pXp, lp(0, dpi(3), start = 0).also { it.weight = 1f }); pXpRow.addView(pXpText, lp(-2, -2, start = 5))
                enemyCard.addView(eRow, lp(-1, -2)); enemyCard.addView(eNumRow, lp(-1, -2, top = 3)); enemyCard.addView(eHp, lp(-1, dpi(7), top = 3))
                playerCard.addView(pRow, lp(-1, -2)); playerCard.addView(pNumRow, lp(-1, -2, top = 3)); playerCard.addView(pHp, lp(-1, dpi(7), top = 3)); playerCard.addView(pXpRow, lp(-1, -2, top = 3))
            }
            1 -> {   // number inside the bar
                for (c in listOf(enemyCard, playerCard)) c.setPadding(dpi(8), dpi(6), dpi(8), dpi(7))
                nameRow(eRow, eName, eStatus, eLevel, symbols = eIcons)
                nameRow(pRow, pName, pStatus, pLevel)
                enemyCard.addView(eRow, lp(-1, -2)); enemyCard.addView(eHp, lp(-1, dpi(14), top = 5))
                playerCard.addView(pRow, lp(-1, -2)); playerCard.addView(pHp, lp(-1, dpi(14), top = 5)); playerCard.addView(pXp, lp(-1, dpi(10), top = 3))
            }
            else -> {   // compact: name + a thin bar with the small HP number beside it
                for (c in listOf(enemyCard, playerCard)) c.setPadding(dpi(10), dpi(5), dpi(10), dpi(6))
                nameRow(eRow, eName, eStatus, eLevel)
                nameRow(pRow, pName, pStatus, pLevel)
                eHpBox.addView(eHp, lp(0, dpi(5), start = 0).also { it.weight = 1f }); eHpBox.addView(eHpText, lp(-2, -2, start = 6))
                pHpBox.addView(pHp, lp(0, dpi(5), start = 0).also { it.weight = 1f }); pHpBox.addView(pHpText, lp(-2, -2, start = 6))
                enemyCard.addView(eRow, lp(-1, -2)); enemyCard.addView(eHpBox, lp(-1, -2, top = 3))
                playerCard.addView(pRow, lp(-1, -2)); playerCard.addView(pHpBox, lp(-1, -2, top = 3))
            }
        }
    }

    /** Applies the card flags: the shape, what shows, the bars' heights, the text sizes and the text floor. Cheap; called whenever a flag changes. */
    private fun updateCards() {
        buildCards()
        val full = !cardsCompact
        fun vis(on: Boolean) = if (on) VISIBLE else GONE
        val typesOn = wantTypes && full
        // fonts (design sizes; [TextFloor] raises them to the panel's floor)
        for (t in listOf(eName, pName)) sz(t, InfoBoxGeo.NAME)
        for (t in listOf(eLevel, pLevel)) sz(t, InfoBoxGeo.LV)
        val numSp = if (full) InfoBoxGeo.NUM else InfoBoxGeo.COMPACT_SP
        for (t in listOf(eHpText, pHpText)) sz(t, numSp)
        sz(pHpLabel, InfoBoxGeo.NUM)
        sz(pXpText, InfoBoxGeo.XP)
        for (i in 0 until eTypes.childCount) (eTypes.getChildAt(i) as? TextView)?.let { sz(it, InfoBoxGeo.CHIP) }
        eHpText.setTextColor(if (full) inkText() else inkSub())
        pHpText.setTextColor(if (full) inkText() else inkSub())
        // what shows
        eNumRow.visibility = vis(full && !inside)
        pNumRow.visibility = vis(full && !inside)
        eNumRow.typesShown = typesOn
        eNumRow.iconsOnly = partsReduced
        eRow.dropAllowed = typesOn
        if (inside) eIcons.visibility = vis(typesOn)
        pXpRow.visibility = vis(wantXpBar && full && !inside)
        pXp.visibility = vis(wantXpBar && full)
        pXpText.visibility = vis(xpTextShown())
        eRow.tight = cardsCompact
        pRow.tight = cardsCompact
        statusRow(eStatus, enemyStatus)
        statusRow(pStatus, playerStatus)
        fitBars()
        fitSquares()
        refreshTexts()
        hideStatusBubble()
        TextFloor.apply(this, floorSp)
        requestLayout()
    }

    /**
     * The bars' heights: with the raised text they grow like the text ([TextMath.bar]). A bar with its number inside is at least as
     * tall as that number really is ([BarView.labelNeedPx]: the label follows the system font size, the bar does not), so a big
     * font never cuts the text or its glow; the card's measured height then carries it into the box rule ([extraHeight]).
     */
    private fun fitBars() {
        val b = TextMath.bar(floorSp)
        fun h(bar: BarView, dp: Float, labelSp: Float = 0f) {
            val lp = bar.layoutParams as LinearLayout.LayoutParams
            val inner = max(dpf(dp * b), if (labelSp > 0f) bar.labelNeedPx(labelSp) else 0)
            val px = if (bar.look == HpBarLook.PIXEL) PixelBar.outerPx(inner, resources.displayMetrics.density) else inner   // pixel look: + an outline unit on each side
            if (lp.height != px) { lp.height = px; bar.layoutParams = lp }
        }
        when {
            cardsCompact -> { h(eHp, 5f); h(pHp, 5f) }
            infoBoxStyle == InfoBoxStyle.INSIDE -> {
                val hp = TextMath.at(InfoBoxGeo.IN_HP, floorSp)
                h(eHp, 14f, hp); h(pHp, 14f, hp)
                if (xpTextShown()) h(pXp, 10f, TextMath.at(InfoBoxGeo.IN_XP, floorSp)) else h(pXp, 4f)
            }
            else -> { h(eHp, 7f); h(pHp, 7f); h(pXp, 3f) }
        }
    }

    /** Writes the HP / XP numbers where the shape wants them: beside the bar (compact, with "HP"), above it, or inside it. */
    private fun refreshTexts() {
        showHpText(eHpText, eHp, eHpStr)
        showHpText(pHpText, pHp, pHpStr)
        showXp(xpStr)
    }

    private fun showHpText(tv: TextView, bar: BarView, plain: String) {
        tv.text = if (cardsCompact && plain.isNotEmpty()) "$plain HP" else plain
        bar.setLabel(if (inside) plain else null, TextMath.at(InfoBoxGeo.IN_HP, floorSp))
        bar.contentDescription = if (inside && plain.isNotEmpty()) "HP $plain" else null   // a number drawn in the bar is no text for a screen reader
    }

    /**
     * The status badge of a card, in its name line: [StatusRow] takes no room (GONE) without a status and is a little taller than the
     * badge with one, the name line being taller than both, so a status coming or going moves nothing (and never touches the bar).
     */
    private fun statusRow(row: StatusRow, status: String?) {
        row.set(status)
        row.fit(dpf(InfoBoxGeo.BADGE * TextMath.bar(floorSp)) + dpi(2))
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

    private val statusTouch = StatusTouch.Delegate(this)

    /**
     * Gives each shown status badge a touch area of at least 48 x 48 dp on screen ([StatusTouch]): the badge is a small square in the
     * name line and its card may be drawn smaller than normal. Worked out again after every layout (a card that moved or changed scale,
     * a status that came or went) and removed while there is no status. Touches the arena's other children take stay theirs.
     */
    private fun updateStatusTouch() {
        val r = android.graphics.Rect()
        var any = false
        for ((slot, row) in listOf(eStatus, pStatus).withIndex()) {
            val badge = row.badge
            var visible = badge != null && badge.width > 0 && row.status != null
            var v: View? = row
            while (visible && v != null && v !== this) { if (v.visibility != VISIBLE) visible = false; v = v.parent as? View }
            if (!visible || width <= 0 || height <= 0) { statusTouch.set(slot, null, null); continue }
            r.set(0, 0, badge!!.width, badge.height)
            offsetDescendantRectToMyCoords(badge, r)
            val a = StatusTouch.area(r.left, r.top, r.right, r.bottom, dpi(StatusTouch.MIN_DP), width, height)
            statusTouch.set(slot, android.graphics.Rect(a[0], a[1], a[2], a[3]), row)
            any = true
        }
        touchDelegate = if (any) statusTouch else null
    }

    /** The touch area (arena px) of a card's status badge, null = none (tests). */
    internal fun statusTouchArea(wild: Boolean): android.graphics.Rect? = statusTouch.areaOf(if (wild) 0 else 1)

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
        invalidateGlass()
    }

    // ---------------------------------------------------------------- player side

    fun setPlayerInfo(uid: String?, name: String, level: Int) {
        playerUid = uid
        playerLevel = level
        pName.text = name
        pLvl = level
        pLevel.text = "Lv $level"; pLevel.setTextColor(levelColor(level))
    }

    fun setPlayerLevel(level: Int) {
        xpTrace?.invoke("level $level")
        playerLevel = level
        pLvl = level
        pLevel.text = "Lv $level"; pLevel.setTextColor(levelColor(level))
    }

    fun setPlayerName(name: String) { pName.text = name }

    /** Round 10 W7: our Pokémon's types; the type look washes our box with the first one's colour (nothing else shows them). */
    fun setPlayerTypes(types: List<Type>) {
        val c = types.firstOrNull()?.let { Ui.typeColor(it) } ?: 0
        if (c == pTypeColor) return
        pTypeColor = c
        if (InfoBoxSkin.needsType(infoBoxLook)) applyBackgrounds()
    }

    /** HP bar eases 450 ms; the "58 / 71" counts along. */
    fun setPlayerHp(hp: Int, max: Int, animate: Boolean) {
        val f = if (max > 0) hp.toFloat() / max else 0f
        pHpFrac = f
        pHp.set(f, hpColorOf(f), animate)
        hpCount?.cancel()
        val from = pHpShown
        pHpShown = hp
        if (!animate || from == hp || !isAttachedToWindow) { showPlayerHp(hp, max); return }
        hpCount = ValueAnimator.ofInt(from, hp).setDuration(450).also { a ->
            a.addUpdateListener { showPlayerHp(it.animatedValue as Int, max) }
            a.addListener(object : AnimatorListenerAdapter() { override fun onAnimationEnd(an: Animator) { showPlayerHp(hp, max) } })
            a.start()
        }
    }

    /** Writes our HP number where the shape wants it (beside, above or inside the bar). */
    private fun showPlayerHp(hp: Int, max: Int) {
        pHpStr = DisplayNumbers.hp(hp, max)
        showHpText(pHpText, pHp, pHpStr)
    }

    /** XP bar (3 dp, sky) and the "XP 640 / 1000" label. */
    fun setPlayerXp(current: Int, needed: Int, animate: Boolean, durationMs: Long = 450) {
        val f = (if (needed > 0) current.toFloat() / needed else 1f).coerceIn(0f, 1f)
        pXpShown = f
        xpTrace?.invoke("bar ${"%.2f".format(java.util.Locale.US, f)}${if (animate) " slide" else ""}")
        setXpBar(f, animate, durationMs)
        showXp(DisplayNumbers.xp(current, needed))
    }

    /** Writes the "XP a / b" number beside the XP bar, or inside it (the inside style); a box that has no room for it ([xpTextShown]) says nothing. */
    private fun showXp(text: String) {
        xpStr = text
        pXpText.text = text
        pXp.setLabel(if (inside && xpTextShown()) text else null, TextMath.at(InfoBoxGeo.IN_XP, floorSp))
        pXp.contentDescription = if (inside && text.isNotEmpty()) text else null
    }

    val xpShown: Float get() = pXpShown

    // ---------------------------------------------------------------- info box looks (Round 10 W7)

    /** The palette the inside of the boxes is painted in: the dark one for a look that is the same in light and dark, else the theme's. */
    private fun insideDark() = ink != null || TypeChips.isDark(context)
    private fun inkText() = ink?.text ?: Ui.c(context, R.color.ag_text)
    private fun inkSub() = ink?.sub ?: Ui.c(context, R.color.ag_sub0)
    private fun levelColor(level: Int) = ink?.tier(level) ?: LevelTier.color(context, level)
    private fun hpColorOf(f: Float) = ink?.hp(f) ?: Ui.hpColor(context, f)

    /** The XP bar's fill at [f]: the look's sky, or the meaning colour of the chosen bar style. */
    private fun setXpBar(f: Float, animate: Boolean, durationMs: Long = 450) {
        val i = ink
        if (i == null) pXp.setMeaning(BarMeaning.XP, f, animate, durationMs) else { pXp.clearGradient(); pXp.set(f, i.xp, animate, durationMs) }
    }

    /** Test-only (PLAN 138): hears what the XP bar and the level number are told, in order ("bar 1.00", "bar 0.00", "bar 0.40 slide", "level 11"). */
    internal var xpTrace: ((String) -> Unit)? = null

    /** The glass boxes draw their picture again (the picture, its crop or the arena changed under them). */
    private fun invalidateGlass() {
        if (InfoBoxSkin.glass(infoBoxLook)) { enemyCard.invalidate(); playerCard.invalidate() }
    }

    /** Dresses the two boxes in [infoBoxLook]: their backgrounds, the colours inside, the text shadows and bar rings. */
    private fun applyLook() {
        ink = InfoBoxSkin.ink(infoBoxLook)
        applyBackgrounds()
        applyInk()
        invalidateGlass()
    }

    /**
     * The boxes' backgrounds: today's translucent card, or the look's layers ([InfoBoxSkin]); the strip's corner radius and lift; the
     * glass look's soft shadow. Built again only when the look, the strip flag or (type look) a Pokémon's type colour changed.
     */
    private fun applyBackgrounds(force: Boolean = false) {
        val look = infoBoxLook
        val typed = InfoBoxSkin.needsType(look)
        val key = "${look.name}|$studyStrip|${if (typed) eTypeColor else 0}|${if (typed) pTypeColor else 0}"
        if (!force && key == skinKey) return
        skinKey = key
        val d = resources.displayMetrics.density
        val baseRadius = if (studyStrip) 10f else 12f
        val radius = InfoBoxSkin.radiusDp(look, baseRadius)
        val card = Ui.c(context, R.color.ag_card)
        val dark = TypeChips.isDark(context)
        val lift = max(baseElevation, Ui.dp(context, InfoBoxSkin.elevationDp(look)))
        val glass = InfoBoxSkin.glass(look)
        fun dress(v: View, typeColor: Int, glassOf: ArenaGlass?) {
            v.background =
                if (look == InfoBoxLook.TODAY) Ui.rounded(translucentCard(context), baseRadius, context)   // exactly as the boxes always were
                else InfoBoxDrawable(d, InfoBoxSkin.layers(look, card, dark, typeColor.takeIf { it != 0 }), radius, if (glass) glassOf else null)
            v.elevation = lift
            cardLayer.elevation = lift   // PLAN 140: the layer keeps the boxes' place in the arena's depth order (over fx / texts, under the attacker and the status note)
        }
        dress(enemyCard, eTypeColor, eGlass ?: ArenaGlass(context, this, enemyCard, { backdrop.shown }, { stageNow }).also { eGlass = it })
        dress(playerCard, pTypeColor, pGlass ?: ArenaGlass(context, this, playerCard, { backdrop.shown }, { stageNow }).also { pGlass = it })
    }

    /**
     * Everything inside the boxes that a look recolours: texts, level tiers, HP / XP bars and their tracks, the status badges, the wild
     * one's type chips; the shadows of the texts and the rings of the bars of the looks that have no plate behind them.
     */
    private fun applyInk() {
        val look = infoBoxLook
        val text = inkText()
        val sub = inkSub()
        eName.setTextColor(text); pName.setTextColor(text)
        pHpLabel.setTextColor(sub); pXpText.setTextColor(sub)
        val full = !cardsCompact
        eHpText.setTextColor(if (full) text else sub); pHpText.setTextColor(if (full) text else sub)
        eLevel.setTextColor(if (eLvl > 0) levelColor(eLvl) else sub)
        pLevel.setTextColor(if (pLvl > 0) levelColor(pLvl) else sub)
        val track = ink?.track ?: Ui.c(context, R.color.ag_s0)
        eHp.setTrackColor(track); pHp.setTrackColor(track); pXp.setTrackColor(track)
        if (eHpFrac >= 0f) eHp.set(eHpFrac, hpColorOf(eHpFrac), false)
        if (pHpFrac >= 0f) pHp.set(pHpFrac, hpColorOf(pHpFrac), false)
        setXpBar(pXpShown, false)
        eStatus.dark = ink != null
        pStatus.dark = ink != null
        if (enemyTypes.isNotEmpty()) buildEnemyTypes()
        val sh = InfoBoxSkin.textShadow(look)
        for (t in listOf(eName, pName, eLevel, pLevel, eHpText, pHpText, pHpLabel, pXpText)) {
            if (sh == null) t.setShadowLayer(0f, 0f, 0f, 0)
            else t.setShadowLayer(Ui.dp(context, sh.radiusDp), Ui.dp(context, sh.dxDp), Ui.dp(context, sh.dyDp), sh.color)
        }
        // the HUD's ring round the bars is drawn outside the bars' bounds (like the texts' shadows): no group inside a box clips its children ([init])
        val ring = InfoBoxSkin.barRing(look)
        val d = resources.displayMetrics.density
        for (b in listOf(eHp, pHp, pXp)) b.foreground = if (ring == null) null else BarRingDrawable(b, ring, d)
        // the pixel bars' outline: the fixed dark palette's text colour on the looks with a fixed dark plate, else the theme's own
        for (b in listOf(eHp, pHp, pXp)) b.outlineColor = ink?.text
        updateCards()
    }

    // ---------------------------------------------------------------- hit animations

    /** The user steps toward the target and back (mockup lunge: +14/-10 dp for us, -14/+10 for the wild one). */
    fun lunge(byPlayer: Boolean, ms: Long) {
        val spr = if (byPlayer) playerSprite else enemySprite
        val dx = Ui.dp(context, if (byPlayer) 14f else -14f)
        val dy = Ui.dp(context, if (byPlayer) -10f else 10f)
        val x = PropertyValuesHolder.ofKeyframe(View.TRANSLATION_X, Keyframe.ofFloat(0f, 0f), Keyframe.ofFloat(0.5f, dx), Keyframe.ofFloat(1f, 0f))
        val y = PropertyValuesHolder.ofKeyframe(View.TRANSLATION_Y, Keyframe.ofFloat(0f, 0f), Keyframe.ofFloat(0.5f, dy), Keyframe.ofFloat(1f, 0f))
        val holder = if (byPlayer) playerHolder else enemyHolder
        val token = liftAttacker(holder, raise = true, particles = false)
        launch(ObjectAnimator.ofPropertyValuesHolder(spr, x, y).setDuration(ms).also { it.interpolator = DecelerateInterpolator() }) {
            spr.translationX = 0f; spr.translationY = 0f
            dropAttacker(token)
        }
    }

    /**
     * R9-B5: while an attack plays (a move look or the plain [lunge]) nothing cuts the attacker off: the arena stops clipping its
     * children (the arena's `clipChildren` used to cut a holder, with the sprite it carries, at its OWN box edge the moment a pose
     * carried the sprite out of it: the "invisible border"). Only an attacker that TRAVELS onto the target ([raise]: the plain lunge
     * and the looks in [travelsOnto]) is also drawn ABOVE the target's sprite and the two info cards: its holder gets a translation Z
     * over the cards' elevation (it has no background, hence no shadow), and with [particles] (a travelling move look) the move's
     * particle layer one dp more, because the burst sits on the attacker. Every other look (the attacker stays home, or only bobs or
     * recoils) keeps the R7 order: translation Z 0 for the holder and the particles, so a screen tint or a dark burst dims the cards
     * as it did before and they stay readable under it. The plain lunge is tiny and has no particles. The faint / entrance
     * animations that rely on the clip run after the attack, when the clip is back.
     *
     * Returns the token of this attack: [dropAttacker] restores only while the token still owns the lift, so an older attack that ends
     * late (a lunge's end action, a released stage) can never undo a newer attack of the same side.
     */
    private fun liftAttacker(holder: View, raise: Boolean, particles: Boolean): Int {
        lifted?.translationZ = 0f   // an older attack that never let go (a lunge cancelled by a move): back to rest first
        lifted = holder
        liftToken++
        val z = Ui.dp(context, ATTACK_Z_DP)
        holder.translationZ = if (raise) z else 0f
        moveFx.translationZ = if (particles) z + Ui.dp(context, 1f) else 0f
        clipChildren = false
        return liftToken
    }

    /** The end of [liftAttacker] for the attack that got [token]: normal order and clip again. Does nothing once another attack has taken the lift over. */
    private fun dropAttacker(token: Int) {
        if (lifted == null || token != liftToken) return
        forceDrop()
    }

    /** Puts the order and the clip back whoever owns the lift (a reset or a cancel of the side's animations). */
    private fun forceDrop() {
        lifted?.translationZ = 0f
        lifted = null
        moveFx.translationZ = 0f
        clipChildren = true
    }

    /** The holder lifted by [liftAttacker] (null = all at rest) and the token of the attack that owns the lift. */
    private var lifted: View? = null
    private var liftToken = 0

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
        val top = if (drawn > 0) max(holder.top - dpi(8), holder.bottom + spr.drawnBottomInset() - drawn - dpi(8)) else holder.top + dpi(6)
        val left = holder.left + (holder.width - tvW) / 2
        val mine = flying.filter { it.onPlayer == ft.onPlayer }   // texts of this side wait for each other
        // Not laid out yet (no size): the old free-floating start. Otherwise keep the whole rise inside the arena.
        val spot = if (width > 0 && height > 0) FloatSpot.placeClear(left, top, width, height, tvW, tvH, dpi(34), flying.map { it.rect }, cardBoxes(dpi(2)), dpi(2))
        else FloatSpot.Spot(left, top, dpi(34))
        val lp = LayoutParams(-2, -2, Gravity.TOP or Gravity.LEFT)
        lp.leftMargin = spot.left
        lp.topMargin = spot.top
        if (lifted != null) tv.translationZ = Ui.dp(context, ATTACK_Z_DP + 2f)   // the number pops at the hit: over the attacker and its particles (R9-B5)
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
        val x = v.left + (v.width - aw) / 2; val y = v.top + (v.height - ah) + spr.drawnBottomInset()   // anchored feet: the image reaches below the holder
        return FloatSpot.Rect(x - m, y - m, x + aw + m, min(y + ah, v.bottom) + m)
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
        popLayer.addView(v, LayoutParams(v.measuredWidth, v.measuredHeight, Gravity.TOP or Gravity.LEFT).also { it.leftMargin = spot.x; it.topMargin = spot.y })   // PLAN 207: above the frame
        val shown = XpShown(v, spot.x, spot.y)
        xpActive += shown
        val finish = { popLayer.removeView(v); xpActive -= shown; pumpXp() }
        val hold = if (v.card.levelUp) maxOf(2500L, v.playMs + 900L) else 1800L   // PLAN 182: a loop of several levels needs longer
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
     * R9-C8: with a [fill] the card's XP bar and label are driven by the effect's own clock, growing a step with every landing
     * and ending exactly on the target with the last one; [xpLastLandingMs] then says when that is. R9-C3: [onLand] gets
     * (pitch, last) for the landings that get a soft pling.
     */
    fun xpAbsorb(xp: Int, factor: Float, fill: XpFill? = null, onLand: ((Float, Boolean) -> Unit)? = null, fromBall: Boolean = false): Long {
        xpLastLandingMs = 0L
        xpPlan = null
        if (xp <= 0 || reduceMotion() || !xpOnScreen()) return 0L
        val plan = XpParticles.plan(kotlin.random.Random.nextLong(), factor, burst = fromBall)
        val e = xpEffect(plan, xp, fill != null, onLand, fromBall) ?: return 0L
        stopXp()
        xpFx = e
        xpFillSpec = fill
        xpLastLandingMs = plan.lastLanding.toLong()
        xpPlan = plan
        fx.add(e)
        return plan.firstLanding.toLong()
    }

    // tests only: no window in the renderer
    internal var xpOnScreen: () -> Boolean = { isAttachedToWindow }

    /** The plan of the XP animation [xpAbsorb] last started (null = the last call started none); R9-W6 CX-A reads the ball fade / banner times from it. */
    var xpPlan: XpParticles.Plan? = null
        private set

    /** When the last orb of the running XP animation lands (ms after it started; 0 = none running). */
    var xpLastLandingMs = 0L
        private set

    private var xpFillSpec: XpFill? = null

    /** One step of the bar fill ([fill] 0..1); the finished fill lets go of its spec. */
    private fun applyXpFill(fill: Float) {
        val spec = xpFillSpec ?: return
        val f = spec.frac(fill)
        pXpShown = f
        if (xpTrace != null && fill >= 1f) xpTrace?.invoke("bar ${"%.2f".format(java.util.Locale.US, f)}")
        setXpBar(f, false)
        showXp(DisplayNumbers.xp(spec.current(fill), spec.needed))
        if (fill >= 1f) xpFillSpec = null
    }

    private fun xpBox(v: View) = XpParticles.Box(v.left.toFloat(), v.top.toFloat(), v.width.toFloat(), v.height.toFloat())

    private fun xpEffect(plan: XpParticles.Plan, xp: Int, driveBar: Boolean = false, onLand: ((Float, Boolean) -> Unit)? = null, fromBall: Boolean = false): XpAbsorbFx? {
        if (width <= 0 || height <= 0 || enemyHolder.width <= 0 || enemyHolder.height <= 0 || playerHolder.width <= 0 || playerHolder.height <= 0) return null
        // CX-A: from the catch ball (it sits in the wild holder, bottom centre: its layout params say where, whatever the layout state), not from the empty wild spot
        val from = if (fromBall) {
            if (!ballOnField) return null
            val lp = ball.layoutParams as LayoutParams
            XpParticles.Box(enemyHolder.left + (enemyHolder.width - lp.width) / 2f, enemyHolder.top + (enemyHolder.height - lp.bottomMargin - lp.height).toFloat(), lp.width.toFloat(), lp.height.toFloat())
        } else xpBox(enemyHolder)
        return XpAbsorbFx(context, plan, from, xpBox(playerHolder), width.toFloat(), xp, floorSp, { playerSprite.flash = it },
            if (driveBar) { f -> applyXpFill(f) } else null, onLand, fromBall)
    }

    /** Ends the XP animation; one cut short by the next event still leaves the bar at its target (a cancel does not: [cancelAnimations] / [resetFx] drop the fill, the save is redrawn). */
    private fun stopXp() { if (xpFillSpec != null) applyXpFill(1f); xpFx?.let { fx.stop(it) }; xpFx = null }

    /** Test-only: the XP animation (mockup seed) frozen at [atMs], our sprite flashed as it is then. */
    internal fun xpAbsorbStill(xp: Int, atMs: Float) {
        val plan = XpParticles.plan(XpParticles.MOCK_SEED)
        val e = xpEffect(plan, xp) ?: return
        playerSprite.flash = XpParticles.flash(plan, atMs)
        fx.showStill(e, atMs)
    }

    /** Test-only (R9-W6 CX-A): the burst out of the catch ball (mockup seed) frozen at [atMs] after the click; the ball must be on the field and laid out. */
    internal fun xpBurstStill(xp: Int, atMs: Float) {
        val plan = XpParticles.plan(XpParticles.MOCK_SEED, burst = true)
        val e = xpEffect(plan, xp, false, null, true) ?: return
        playerSprite.flash = XpParticles.flash(plan, atMs)
        ball.alpha = XpParticles.ballAlpha(plan, atMs)
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
        val token = IntArray(1)   // the stage's release needs the token of the lift it was created for
        val stage = moveStage(byPlayer) { token[0] } ?: return null
        val holder = if (byPlayer) playerHolder else enemyHolder
        val travels = travelsOnto(plan.look)
        token[0] = liftAttacker(holder, raise = travels, particles = travels)   // the stage drops it again when the move lets go of the sprites
        val effect = moveMaker(stage, plan, react, speed)
        if (effect == null) { dropAttacker(token[0]); return null }
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
    private fun moveStage(byPlayer: Boolean, token: () -> Int): ArenaMoveStage? {
        if (width <= 0 || height <= 0 || enemyHolder.visibility != VISIBLE) return null
        val attacker = if (byPlayer) playerSprite else enemySprite
        val target = if (byPlayer) enemySprite else playerSprite
        val aBox = if (byPlayer) playerHolder else enemyHolder
        val tBox = if (byPlayer) enemyHolder else playerHolder
        for ((spr, box) in listOf(attacker to aBox, target to tBox)) {
            if (box.width <= 0 || box.height <= 0 || spr.width <= 0 || spr.height <= 0 || spr.drawnWidth() <= 0 || spr.drawnHeight() <= 0) return null
        }
        val a = ArenaMoveStage.Geo.centre(aBox.left.toFloat(), aBox.top.toFloat(), attacker.width.toFloat(), attacker.height.toFloat(), attacker.drawnHeight().toFloat(), attacker.drawnBottomInset().toFloat())
        val t = ArenaMoveStage.Geo.centre(tBox.left.toFloat(), tBox.top.toFloat(), target.width.toFloat(), target.height.toFloat(), target.drawnHeight().toFloat(), target.drawnBottomInset().toFloat())
        val unit = ArenaMoveStage.Geo.unit(a.first, a.second, t.first, t.second, target.drawnHeight().toFloat())
        return ArenaMoveStage(a.first, a.second, t.first, t.second, unit, attacker, target, { background }, { it === playerSprite && turnAnim != null }, onRestore = { dropAttacker(token()) })
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
        if (lifted === (if (player) playerHolder else enemyHolder)) forceDrop()   // this side's animations are cancelled: whatever lifted it is over
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
        ball.alpha = 1f
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
        ball.translationY = 0f; ball.rotation = 0f; ball.alpha = 1f
        ballOnField = false
    }

    /** R9-W6 CX-A: the ball fades out over [ms] (the XP orbs have left it); [clearBall] takes it away and puts its opacity back. */
    fun fadeBall(ms: Long) { ball.animate().alpha(0f).setDuration(ms).start() }

    /** R9-W6 CX-A: the "Gotcha!" banner fades out over [ms] (the last orb has landed); [hideBanner] takes it away. */
    fun fadeBanner(ms: Long) { if (banner.visibility == VISIBLE) banner.animate().alpha(0f).setDuration(ms).start() }

    fun showStars(on: Boolean) {
        stars.visibility = if (on) VISIBLE else GONE
        stars.running = on
    }

    /**
     * "Gotcha!" banner in the comic text style (pick 8A); [line1] / [line2] only go to its description (the result tiles and the log
     * say them). It sits above the Poké Ball, or centred in the arena when there is no room there (pick 8D), never over a Pokémon
     * or a stat card ([BannerSpot], PLAN 35).
     */
    fun showBanner(title: String, line1: String, line2: String?, numberAbove: Boolean = false) {
        bannerRoom = if (numberAbove) dpi(NUMBER_ROOM_DP) else 0
        bannerTitle.text = title.uppercase(java.util.Locale.ROOT)
        banner.contentDescription = listOfNotNull(title, line1, line2).joinToString(". ")
        banner.visibility = VISIBLE
        banner.animate().cancel()
        if (!placeBanner()) { banner.visibility = GONE; return }   // no clear spot even small: the tiles / log already say it
        banner.scaleX = bannerFit * 0.97f; banner.scaleY = bannerFit * 0.97f; banner.alpha = 0.6f
        banner.animate().scaleX(bannerFit).scaleY(bannerFit).alpha(1f).setDuration(300).start()
    }

    private var bannerFit = 1f
    /** R9-W6 CX-A: the "+N XP" number rides over the ball ([XpParticles.BALL_NUMBER_LIFT]); the banner sits this much higher (px) so they don't touch. */
    private var bannerRoom = 0

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
        val ballBox = if (ballOnField && ball.width > 0) FloatSpot.Rect(enemyHolder.left + ball.left, enemyHolder.top + ball.top - bannerRoom, enemyHolder.left + ball.right, enemyHolder.top + ball.bottom) else null
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
        updateNewTag()
        updateStatusTouch()
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
        xpFillSpec = null
        xpQueue.clear()
        xpActive.toList().forEach { it.view.animate().cancel(); popLayer.removeView(it.view) }
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
        xpFillSpec = null   // the save is redrawn next: a late fill step must not undo it
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
        /** R9-W6 CX-A: room the banner leaves over the ball for the "+N XP" number (dp). */
        private const val NUMBER_ROOM_DP = 28
        /** R9-B5: translation Z of the attacker's holder while it attacks: over the cards' 2 dp, under the status bubble's 8 dp. */
        private const val ATTACK_Z_DP = 4f

        /**
         * The looks in which the attacker itself travels onto the target (Tackle's lunge, and the physical template that reuses it): only
         * they need the attacker, and then its particles, above the cards. Checked in the look files in `ui/movefx` and `FxScript`: Ember and Water Jet
         * only recoil a few units, Earthquake bounces, Psychic and the status template float, Vine Whip / Ice Beam / Thunderbolt / Shadow
         * Ball and the special template pose nothing that reaches the target.
         */
        internal fun travelsOnto(look: com.husarp.ankigo.ui.movefx.MoveLook): Boolean =
            look == com.husarp.ankigo.ui.movefx.MoveLook.TACKLE || look == com.husarp.ankigo.ui.movefx.MoveLook.HIT_TEMPLATE
        /** dp per sprite pixel in the normal arena: the holder is the limit (the study layouts keep 2). */
        private const val BIG_SPRITE_DP = 8f
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
 * The name line of an info card: name, (wild: type symbols), (status badge), "Lv". The name takes what the others leave and ends in
 * "…" before the badge or the Lv would go. On compact cards [tight] keeps "Lv" only when the name still fits at its full letter
 * width next to it; otherwise "Lv" is left out rather than shrinking the name. [dropForName]: the type symbols of the inside style
 * go first when the name would get under [MIN_NAME_DP] (a narrow card).
 */
private class NameRow(ctx: Context, private val name: FitName, private val level: TextView) : LinearLayout(ctx) {
    var tight = false
        set(v) { field = v; if (!v) level.visibility = VISIBLE; requestLayout() }

    /** The part that makes room for the name; null = none. [dropAllowed] false: it is not shown at all (types off). */
    var dropForName: View? = null
    var dropAllowed = true
        set(v) { if (field != v) { field = v; requestLayout() } }

    init { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val un = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        val avail = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
        val drop = dropForName
        if (drop != null) {
            drop.visibility = if (dropAllowed) VISIBLE else GONE
            if (dropAllowed) {
                var others = 0
                for (i in 0 until childCount) {
                    val c = getChildAt(i)
                    if (c === name || c.visibility == GONE) continue
                    c.measure(un, un)
                    others += c.measuredWidth + (c.layoutParams as MarginLayoutParams).marginStart
                }
                if (avail - others < Ui.dp(context, MIN_NAME_DP)) drop.visibility = GONE
            }
        }
        if (tight && name.text.isNotEmpty()) {
            var others = 0
            for (i in 0 until childCount) {
                val c = getChildAt(i)
                if (c === name || (c !== level && c.visibility == GONE)) continue
                c.measure(un, un)
                others += c.measuredWidth + (c.layoutParams as MarginLayoutParams).marginStart
            }
            level.visibility = if (name.naturalWidth() + others <= avail) VISIBLE else GONE
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    private companion object { const val MIN_NAME_DP = 40 }
}

/**
 * Line 2 of the wild card (above style): the type chips at the left, the HP number at the right. When the two do not fit on the
 * line (two long type names in a narrow card) the chips become the type symbols ([icons]); [iconsOnly] makes that so in a reduced
 * box; [typesShown] false shows neither. The row is as high as the symbols, so the swap moves nothing.
 */
private class ChipsNumberRow(ctx: Context, private val chips: View, private val icons: View, private val number: View) : LinearLayout(ctx) {
    var typesShown = true
        set(v) { if (field != v) { field = v; requestLayout() } }
    var iconsOnly = false
        set(v) { if (field != v) { field = v; requestLayout() } }

    init { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val un = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        number.measure(un, un)
        chips.visibility = VISIBLE
        chips.measure(un, un)
        val avail = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
        val symbols = iconsOnly || chips.measuredWidth + number.measuredWidth + Ui.dp(context, 6) > avail
        chips.visibility = if (typesShown && !symbols) VISIBLE else GONE
        icons.visibility = if (typesShown && symbols) VISIBLE else GONE
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
