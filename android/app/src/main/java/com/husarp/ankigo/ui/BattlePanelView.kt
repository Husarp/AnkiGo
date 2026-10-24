package com.husarp.ankigo.ui

import com.husarp.ankigo.game.lookSpecies
import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.R
import com.husarp.ankigo.data.SpriteKind
import com.husarp.ankigo.game.DailyGoal
import com.husarp.ankigo.game.GameEvent
import com.husarp.ankigo.game.GameData
import com.husarp.ankigo.game.GameState
import com.husarp.ankigo.game.ItemKind
import com.husarp.ankigo.game.Items
import com.husarp.ankigo.game.Mon
import com.husarp.ankigo.game.PendingFight
import com.husarp.ankigo.game.Progression
import com.husarp.ankigo.game.shownName
import com.husarp.ankigo.game.StatCalc
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Colour family of a log line: the border and the pill (mockup `.log.again` ... `.log.sys`). */
enum class LogKind(@ColorRes val tone: Int, @ColorRes val border: Int) {
    AGAIN(R.color.ag_red, R.color.ag_red),
    HARD(R.color.ag_peach, R.color.ag_peach),
    GOOD(R.color.ag_green, R.color.ag_green),
    EASY(R.color.ag_blue, R.color.ag_blue),
    SYS(R.color.ag_mauve, R.color.ag_mauve),
    NEUTRAL(0, R.color.ag_s1),
}

/**
 * The AnkiGo battle panel (mockup frames A/B below the AnkiDroid half): status strip, [arena], one log card and the
 * footer (goal bar, tool buttons, menu, chips). It only draws; [EventPlayer] tells it what happened and [renderAll]
 * redraws everything from the saved game state.
 *
 * Whatever the window size, the panel is laid out at a virtual size and drawn scaled by one factor ([PanelFit]): all
 * parts shrink together, texts keep an 8 sp floor, and only in a window too small even for that do the stats bar, the
 * log line and the XP numbers step out, in that order. Nothing scrolls.
 */
class BattlePanelView(ctx: Context) : FrameLayout(ctx) {
    val arena = ArenaView(ctx)

    var onGrant: () -> Unit = {}
    var onMenu: (View) -> Unit = {}
    var onBalls: () -> Unit = {}
    /** True while the button click sounds must stay quiet (the study screen: the card plays audio). */
    var clickMuted: () -> Boolean = { false }

    // ---- strip ----
    private val stripText = Ui.text(ctx, 12f, 800, R.color.ag_red).apply { maxLines = 2; ellipsize = TextUtils.TruncateAt.END }
    private val grant = Ui.text(ctx, 11f, 900, R.color.ag_crust).apply {
        text = "Grant"
        setPadding(dpi(10), dpi(5), dpi(10), dpi(5))
        background = Ui.rounded(Ui.c(ctx, R.color.ag_mauve), 9, ctx)
        setOnClickListener { onGrant() }
    }
    private val strip = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setBackgroundColor(Ui.c(ctx, R.color.ag_mantle))
        setPadding(dpi(10), dpi(5), dpi(8), dpi(5))
        visibility = GONE
    }

    // ---- action bar (round 9 D3): the log slot under the arena: my last move | dots | their last move, details in a bubble ----
    private val logBox = ActionBarView(ctx)

    // ---- catch result tiles (PLAN 40): they take the log card's place after a catch or a defeat ----
    private val tiles = CatchTilesView(ctx).apply { visibility = GONE }
    private var tilesOn = false
    private var tilesSummary: CatchSummary? = null
    private var enemyHeld = false
    private var tilesTimed = false   // the result runs on the small-window timeout ([CatchTilesPlan.Mode.TIMED]); a HOLD result becomes one when the window shrinks
    private val hideTilesRun = Runnable { hideCatchTiles() }
    /**
     * The held next wild one was let go because the window shrank under a result ([CatchResultShift]), not by the next answer: whoever
     * parked its Encounter ([EventPlayer]) plays it now. Without a listener the panel draws the wild one from the saved game itself.
     */
    var onHeldReleased: (() -> Unit)? = null
    /**
     * A tap on "Continue" under the held result tiles (R6-09: the screens whose answers come from AnkiDroid, [EventPlayer] sets it).
     * Null = the tiles show no Continue (the Study screen has its own button in the answer row).
     */
    var onContinue: (() -> Unit)? = null
    /** Tells the screen when a result starts or stops holding the next wild one back ([holdsNextFight]); always on the main looper, never inside a layout pass. */
    var onHoldChanged: ((Boolean) -> Unit)? = null
    private var holdTold = false

    // ---- modes ----
    /** Picture-in-picture or floating battle ([setCompact] outside the study screens): the normal panel, scaled. */
    private var small = false
    /** The study strip ([setStudyLayout]): the old stripped-down look (one log line, no footer), never scaled. */
    private var legacy = false
    private var flatBar = false   // the study strip's bar runs edge to edge (no side margins, no rounded line card)
    private var cornerClearance = 0
    private var study = false
    private var statsBarSmall = true
    private var cardLook = false     // the Pokémon tab's battle card ([setCardLook])
    private var compactCards = false
    private var cardsOwn = true
    private var boxStyle = InfoBoxStyle.DEFAULT    // the info boxes' design ([refreshPrefs]); the heights of the boxes depend on it
    private var applied: PanelFit.Fit? = null
    private var fit = PanelFit.Fit(1f, 0f, 0f, 0f, stats = true, log = true, xp = true, tools = true, logLines = 2, compactCards = false, middle = false, ok = true)
    private val studyCharge = StudyCharge()

    private var paused = false
    private var statusOk = true

    // ---- layout parts: the column is laid out at the virtual size and drawn scaled inside [scaler] ----
    private val scaler = FrameLayout(ctx)
    private val column = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
    private val footer = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dpi(10), dpi(9), dpi(10), dpi(9))
    }
    private val logLp = LinearLayout.LayoutParams(-1, dpi(52)).also { it.setMargins(dpi(10), dpi(8), dpi(10), 0) }

    private fun newPausedPill() = Ui.text(context, 10f, 900, R.color.ag_crust).apply {
        text = "Paused"
        textSize = 9.5f
        setPadding(dpi(7), dpi(1), dpi(7), dpi(1))
        background = Ui.rounded(Ui.c(context, R.color.ag_peach), 7, context)
        visibility = GONE
    }

    /** The "Paused" pill over the arena's top-left corner while paused and the log line has stepped out (the smallest windows). */
    private val pausedBadge = newPausedPill()

    // ---- footer ----
    private val goalBar = BarView(ctx)
    private val goalText = Ui.text(ctx, 12f, 900, R.color.ag_text).apply { maxLines = 1 }
    private val goalBarLp = LinearLayout.LayoutParams(0, dpi(8), 1f).also { it.marginEnd = dpi(8) }
    private val goalRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    /** The tool buttons and the menu button, which show and hide together ([setFooterTools], and never in small windows). */
    private val tools = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private var toolsWanted = true
    private val menuButton = iconButton(R.drawable.ic_menu, "Menu: Team, Pokédex, Shop, Settings") { onMenu(it) }
    private val comboChip = Ui.chip(ctx, R.drawable.ic_bolt, R.color.ag_sub0, plain = true)
    private val streakFlame = StreakFlame(ctx)
    private val streakChip = Ui.chip(ctx, streakFlame, R.color.ag_red)
    private val coinChip = Ui.chip(ctx, R.drawable.ic_money, R.color.ag_yellow, plain = true)
    private val itemChip = Ui.chip(ctx, R.drawable.ic_gift, R.color.ag_teal, plain = true)
    private val ballChip = Ui.chip(ctx, R.drawable.ic_ball, R.color.ag_sub1, plain = true)
    private val chips = PanelChipRow(ctx)
    private val overlay = FrameLayout(ctx).apply { clipChildren = false; clipToPadding = false }
    private var shownCombo = 0
    private var shownBalls = -1
    private var shownCoins = -1
    private var enemyKey: String? = null
    private var playerKey: String? = null

    /** PLAN 34: the swipe-to-turn facing for our main (saved in [AppPrefs.playerFront], so every battle view agrees). */
    private val turnSource = object : PlayerTurnSource {
        private fun look(): Pair<com.husarp.ankigo.game.Species, Boolean>? {
            val data = GameRepo.data ?: return null
            val m = GameRepo.state?.main ?: return null
            return m.lookSpecies(data) to m.shiny
        }
        override fun facingFront(): Boolean = AppPrefs.playerFront(context)
        override fun hasFront(): Boolean {
            val (sp, shiny) = look() ?: return false
            if (com.husarp.ankigo.data.Sprites.cached(context, sp, SpriteKind.FRONT, shiny) != null) return true
            if (com.husarp.ankigo.data.Sprites.staticFile(context, sp.num, shiny).isFile) return true
            com.husarp.ankigo.data.Sprites.fetch(context, sp, SpriteKind.FRONT, shiny) { }   // ready for the next swipe
            return false
        }
        override fun turnTo(front: Boolean, show: Boolean) {
            AppPrefs.setPlayerFront(context, front)
            // the Pokémon on screen (the game may already hold the next main while an event still shows this one fainting)
            val (sp, shiny) = (playerLook ?: look()) ?: return
            if (!show) return   // playerKey no longer matches the facing: the next render loads the picture
            PanelSprites.showMain(context, arena.playerSprite, sp, shiny)
            playerKey = playerKeyOf(sp.num, shiny)
        }
    }

    /** What [playerKey] says: the picture follows the Pokémon, its shininess and the saved facing (so another view's swipe shows here too). */
    private fun playerKeyOf(species: Int, shiny: Boolean) = "$species:$shiny:${AppPrefs.playerFront(context)}"

    /** The Pokémon whose picture the player's sprite shows now (null: unknown, the game's main). */
    private var playerLook: Pair<com.husarp.ankigo.game.Species, Boolean>? = null

    /** A swipe in another battle view changed the facing: load the picture again here. */
    private val facingWatch = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "playerFront") post { if (isAttachedToWindow) refreshPlayer() }
        if (key == BattleFrame.KEY) post { if (isAttachedToWindow) refreshPrefs() }   // Settings picked another frame: every open panel (Study, float window...) follows
        if (key == DotsPlace.KEY) post { if (isAttachedToWindow) refreshPrefs() }     // ... and another place for the turn dots
        if (key == InfoBoxStyle.KEY) post { if (isAttachedToWindow) refreshPrefs() }  // ... and another design for the info boxes
        if (key == InfoBoxLook.KEY) post { if (isAttachedToWindow) refreshPrefs() }   // ... and another look for them (Round 10 W7)
        if (key == HpBarLook.KEY) post { if (isAttachedToWindow) refreshPrefs() }     // ... and another look for their HP / XP bars (Round 10 W8)
        // Round 10 B19: the battle's "View settings" sheet changes these from any host; every open panel follows live
        if (key == PokemonSize.KEY || key == "cardsUnderOwn" || key == "compactCards") post { if (isAttachedToWindow) refreshPrefs() }
        if (key == "backgroundPictures") post { if (isAttachedToWindow) arena.reapplyBackdrop() }
    }

    /** Round 9 (D2): the battle frame drawn over the arena. [frameStyle] is the saved choice; [updateFrame] fits it to the panel scale. */
    private var frameStyle = BattleFrameStyle.NONE
    private val frameDrawable = BattleFrameDrawable()

    private fun updateFrame(scale: Float) {
        val d = resources.displayMetrics.density
        val u = BattleFrame.unitPx(d, scale)
        frameDrawable.set(frameStyle, u)
        val on = frameStyle != BattleFrameStyle.NONE
        if (on && arena.foreground !== frameDrawable) arena.foreground = frameDrawable
        else if (!on && arena.foreground != null) arena.foreground = null
        arena.frameInsetDp = if (on) frameStyle.t * u / d else 0f
        // PLAN 140: and what the frame draws deeper than its band (the icicles, the corner squares): the info boxes keep clear of it too
        val safe = BattleFrame.safe(frameStyle, u / d)
        arena.frameTopDp = safe.top
        arena.frameCornerDp = safe.corner
    }

    /**
     * R9-W5-A: whether the host's [EventPlayer] has nothing playing or waiting ([EventPlayer] sets it). While it plays, its events draw
     * the new numbers; a panel without a player (a screenshot) counts as idle.
     */
    internal var hostIdle: () -> Boolean = { true }

    /**
     * R9-W5-A: the saved game changed while this panel is on screen (the Pokémon tab next to it in split screen, a floating Team
     * card over it, a change made in another window): the player side follows by itself, in every host (Study, Battle tab, PiP,
     * floating window, the Pokémon tab's card). Only a visible panel with an idle host; [refreshMain] does nothing when the main is
     * unchanged, so the changes that come with every answer cost one comparison.
     */
    private val gameWatch: () -> Unit = {
        if (isAttachedToWindow && isShown && windowVisibility == VISIBLE && hostIdle() && !onPendingFight()) refreshMain()
    }

    /**
     * PLAN 191: the host takes the fight events that were made on another screen and plays them ([EventPlayer.playPending]); true
     * when it did (then they draw the player side, not [refreshMain]). A panel without a player has none.
     */
    internal var onPendingFight: () -> Boolean = { false }

    /** PLAN 191: the panel came on screen (attached, or its window became visible): a host that is idle takes the waiting fight events. */
    private val takePending = Runnable {
        if (isAttachedToWindow && isShown && windowVisibility == VISIBLE && hostIdle()) onPendingFight()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE) post(takePending)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        AppPrefs.watchPlayerFront(context, facingWatch)
        GameRepo.addListener(gameWatch)
        post(takePending)
    }

    override fun onDetachedFromWindow() {
        GameRepo.removeListener(gameWatch)
        AppPrefs.unwatchPlayerFront(context, facingWatch)
        super.onDetachedFromWindow()
    }

    init {
        arena.turnSource = turnSource
        // strip
        strip.addView(stripText, LinearLayout.LayoutParams(0, -2, 1f))
        strip.addView(grant, LinearLayout.LayoutParams(-2, -2).also { it.marginStart = dpi(8) })

        // log / action bar: the bubble opens in the overlay, inside the scaled layer
        logBox.attachBubbleLayer(overlay)

        // footer: goal bar, goal text, tool buttons, menu; then the chips
        goalRow.addView(goalBar, goalBarLp)
        goalRow.addView(goalText, LinearLayout.LayoutParams(-2, -2).also { it.marginEnd = dpi(4) })
        tools.addView(menuButton, buttonLp())
        goalRow.addView(tools, LinearLayout.LayoutParams(-2, -2))

        chips.addView(comboChip); chips.addView(streakChip); chips.addView(coinChip); chips.addView(itemChip); chips.addView(ballChip)
        chips.hideOrder = listOf(itemChip, streakChip) // least important first (only when even the scaled row is too wide)
        ballChip.setOnClickListener { com.husarp.ankigo.sound.GameSound.ui(ctx, com.husarp.ankigo.sound.SoundId.BALL_BTN, clickMuted()); onBalls() }
        ballChip.contentDescription = "Poké Balls"
        // R18-D (123): bare chip, a ripple on touch; the 48 dp touch area is added by [PanelChipRow]
        ballChip.foreground = android.util.TypedValue().let { tv ->
            ctx.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, tv, true)
            androidx.core.content.ContextCompat.getDrawable(ctx, tv.resourceId)
        }

        footer.addView(goalRow, LinearLayout.LayoutParams(-1, -2))
        footer.addView(chips, LinearLayout.LayoutParams(-1, -2).also { it.topMargin = dpi(4) })

        column.addView(strip, LinearLayout.LayoutParams(-1, -2))
        column.addView(arena, LinearLayout.LayoutParams(-1, 0, 1f))
        column.addView(logBox, logLp)
        column.addView(tiles, LinearLayout.LayoutParams(-1, -2))
        column.addView(footer, LinearLayout.LayoutParams(-1, -2))

        scaler.addView(column, LayoutParams(-1, -1))
        scaler.addView(overlay, LayoutParams(-1, -1))
        scaler.addView(pausedBadge, LayoutParams(-2, -2, Gravity.TOP or Gravity.START).also { it.setMargins(dpi(6), dpi(6), 0, 0) })
        addView(scaler, LayoutParams(-1, -1))
        refreshPrefs()
    }

    // ---------------------------------------------------------------- scaling

    /**
     * Lays the column out at the window's size divided by the scale and draws it scaled (pivot at the corner), so
     * touches still land on the right view. [PanelFit] decides scale, parts and card layout from the window.
     */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val d = resources.displayMetrics.density
        val w = if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED) dpi(PanelFit.REF_W_STATS.toInt()) else MeasureSpec.getSize(widthMeasureSpec)
        val h = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED) dpi(340) else MeasureSpec.getSize(heightMeasureSpec)
        val f = if (legacy) PanelFit.Fit(1f, w / d, h / d, 0f, stats = false, log = true, xp = true, tools = false, logLines = 1, compactCards = compactCards, middle = false, ok = true)
        else decide(w / d, h / d)
        apply(f)
        scaler.measure(MeasureSpec.makeMeasureSpec((w / f.scale).roundToInt(), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec((h / f.scale).roundToInt(), MeasureSpec.EXACTLY))
        scaler.pivotX = 0f; scaler.pivotY = 0f
        scaler.scaleX = f.scale; scaler.scaleY = f.scale
        setMeasuredDimension(w, h)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        scaler.layout(0, 0, scaler.measuredWidth, scaler.measuredHeight)
    }

    private data class Asked(val w: Float, val h: Float, val small: Boolean, val statsBar: Boolean, val compact: Boolean, val strip: Boolean, val corner: Float, val tools: Boolean, val own: Boolean, val style: InfoBoxStyle)
    private var asked: Asked? = null
    private var answer: PanelFit.Fit? = null

    /** [PanelFit.decide] for this window and these prefs; the last answer is kept while nothing changes (measure runs often). */
    private fun decide(w: Float, h: Float): PanelFit.Fit {
        val a = Asked(w, h, small, statsBarSmall, compactCards, !statusOk, if (small) cornerClearance / resources.displayMetrics.density else 0f, toolsWanted, cardsOwn, boxStyle)
        answer?.let { if (a == asked) return it }
        return PanelFit.decide(a.w, a.h, a.small, a.statsBar, a.compact, a.strip, a.corner, a.tools, a.own, a.style).also { asked = a; answer = it }
    }

    /** Applies a decision: which parts show, the text floor, the log's height. Does nothing when it did not change. */
    private fun apply(f: PanelFit.Fit) {
        fit = f
        if (f == applied) return
        applied = f
        arena.configure(f.floorSp, f.compactCards, f.xp)
        arena.panelScale = f.scale
        updateFrame(f.scale)
        strip.visibility = if (statusOk || legacy) GONE else VISIBLE
        stripText.maxLines = if (small) 1 else 2
        footer.visibility = if (f.stats) VISIBLE else GONE
        logBox.visibility = if (f.log && !tilesOn) VISIBLE else GONE
        tiles.visibility = if (f.log && tilesOn) VISIBLE else GONE
        tools.visibility = if (f.tools && toolsWanted) VISIBLE else GONE
        val k = TextMath.bar(f.floorSp)
        goalBarLp.height = dpf(8f * k)
        goalBar.layoutParams = goalBarLp
        // clearance for a resize grip over the bottom-right corner: the footer's end, or the log's when no footer shows
        val clear = if (small) (cornerClearance / f.scale).roundToInt() else 0
        footer.setPadding(dpi(10), dpi(9), dpi(10) + if (f.stats) clear else 0, dpi(9))
        if (!legacy) {
            logLp.height = dpf(TextMath.logBox(f.logLines, f.floorSp))
            logLp.setMargins(dpi(10), dpi(8), dpi(10) + if (f.stats) 0 else clear, 0)
            logBox.layoutParams = logLp
        }
        logBox.floorSp = f.floorSp
        logBox.closeBubble()   // the window changed under it
        for (v in listOf(strip, logBox, footer, pausedBadge)) TextFloor.apply(v, f.floorSp)
        updatePausedPill()
        if (tilesOn) { layoutTiles(); resultWindowChanged() } else if (enemyHeld) earlyHoldWindowChanged()
        requestLayout()
    }

    private fun dpf(v: Float) = (v * resources.displayMetrics.density + 0.5f).toInt()

    /** The window's decision, for tests: scale, parts, card layout. */
    internal val currentFit: PanelFit.Fit get() = fit

    /** Re-reads the battle panel prefs (stats bar in small windows, compact stat cards, cards under their own Pokémon) and lays out again. */
    fun refreshPrefs() {
        statsBarSmall = cardLook || AppPrefs.statsBarSmall(context)
        compactCards = AppPrefs.compactCards(context)
        cardsOwn = AppPrefs.cardsUnderOwn(context)
        arena.cardsUnderOwn = cardsOwn
        boxStyle = AppPrefs.infoBoxStyle(context)
        arena.infoBoxStyle = boxStyle
        arena.infoBoxLook = AppPrefs.infoBoxLook(context)
        arena.hpBarLook = AppPrefs.hpBarLook(context)
        arena.pokemonSize = PokemonSize.factor(AppPrefs.pokemonSize(context))
        frameStyle = AppPrefs.battleFrame(context)
        logBox.dotsPlace = AppPrefs.turnDots(context)
        applied = null
        requestLayout()
    }

    // ---------------------------------------------------------------- footer tool buttons

    /** A small rounded icon button like the menu button: a 48 dp touch height around the 26 dp look. */
    private fun iconButton(@DrawableRes icon: Int, description: String, onClick: (View) -> Unit): View {
        val box = FrameLayout(context)
        box.addView(ImageView(context).apply {
            setImageResource(icon)
            setColorFilter(Ui.c(context, R.color.ag_sub0), PorterDuff.Mode.SRC_IN)
            setPadding(dpi(5), dpi(5), dpi(5), dpi(5))
        }, LayoutParams(dpi(26), dpi(26), Gravity.CENTER))
        // R18-D (226): no tile behind the glyph; a round ripple on touch
        box.foreground = android.graphics.drawable.RippleDrawable(
            android.content.res.ColorStateList.valueOf((Ui.c(context, R.color.ag_ov) and 0x00ffffff) or 0x33000000), null, null)
        box.contentDescription = description
        box.setOnClickListener { onClick(box) }
        return box
    }

    private fun buttonLp() = LinearLayout.LayoutParams(dpi(40), dpi(48)).also { it.setMargins(0, -dpi(9), 0, -dpi(9)) }

    /**
     * Adds a small icon button to the goal row, before the menu button (it looks like the menu button). Returns the
     * button. Tool buttons show and hide with the menu button ([setFooterTools]) and are never shown in small windows.
     */
    fun addToolButton(@DrawableRes icon: Int, description: String, onClick: (View) -> Unit): View {
        val b = iconButton(icon, description, onClick)
        tools.addView(b, tools.indexOfChild(menuButton), buttonLp())
        return b
    }

    /** Shows or hides the tool buttons and the menu button together; the goal bar, goal text and chips stay. */
    fun setFooterTools(visible: Boolean) {
        toolsWanted = visible
        applied = null
        requestLayout()
    }

    // ---------------------------------------------------------------- strip

    /** Red strip above the arena while detection has a problem; [canGrant] adds the "Grant" button. */
    fun setStatus(ok: Boolean, text: String, canGrant: Boolean) {
        statusOk = ok
        strip.visibility = if (ok || legacy) GONE else VISIBLE
        stripText.text = text
        grant.visibility = if (canGrant) VISIBLE else GONE
        requestLayout()
    }

    /**
     * Small-window mode for picture-in-picture and the floating battle window: the same parts as the normal panel,
     * scaled to the window ([PanelFit]); no menu or tool buttons, one log line, the stats bar only with
     * [AppPrefs.statsBarSmall]. False = the normal panel. On the study screens it is the old stripped-down strip.
     */
    fun setCompact(on: Boolean) {
        if (study) setLegacy(on) else setSmall(on)
    }

    /**
     * The look of the battle card in the Pokémon tab (R4-V1): the small-window panel (one log line, compact cards, no menu or
     * tool buttons) with the goal bar always shown (whatever [AppPrefs.statsBarSmall] says) and without the chips row.
     */
    fun setCardLook(on: Boolean) {
        cardLook = on
        chips.visibility = if (on) GONE else VISIBLE
        setCompact(on)
        refreshPrefs()
    }

    private fun setSmall(on: Boolean) {
        if (small == on) return
        small = on
        applyLogLook()
        updatePausedPill()
        applied = null
        requestLayout()
    }

    /** The plain log line's paddings, text size and pill for the current mode (the segments follow the bar's own size). */
    private fun applyLogLook() {
        logBox.setLook(small, legacy, flatBar)
        logBox.applyLiveRegion()
    }

    /** The study strip: the old compact look (one 26 dp log line, no footer, no strip), at scale 1. */
    private fun setLegacy(on: Boolean) {
        if (legacy == on) return
        legacy = on
        arena.setCompact(on)
        strip.visibility = if (on || statusOk) GONE else VISIBLE
        logLp.height = if (on) dpi(26) else dpi(52)
        if (on) logLp.setMargins(dpi(6), dpi(3), dpi(6) + cornerClearance, dpi(3)) else logLp.setMargins(dpi(10), dpi(8), dpi(10), 0)
        applyLogLook()
        logBox.layoutParams = logLp
        updatePausedPill()
        applied = null
        requestLayout()
    }

    /**
     * Extra space [px] kept free at the right end of the bottom row (the stats bar, or the log line when there is none),
     * for a resize grip drawn over the bottom-right corner (the floating battle window). 0 = none (picture-in-picture).
     */
    fun setCornerClearance(px: Int) {
        cornerClearance = px
        if (legacy) { logLp.setMargins(dpi(6), dpi(3), dpi(6) + px, dpi(3)); logBox.layoutParams = logLp }
        applied = null
        requestLayout()
    }

    /** Study-only presentation; PiP and floating windows scale the normal panel instead. */
    fun setStudyLayout(small: Boolean, mini: Boolean) {
        study = true
        setCompact(small)
        arena.setStudyStrip(small)
        arena.visibility = if (mini) GONE else VISIBLE
        logBox.dotsOn = true
        logLp.height = dpi(if (small) 34 else 52)
        if (small) logLp.setMargins(0, 0, 0, 0)
        flatBar = small   // the short strip runs edge to edge
        applyLogLook()
        logBox.layoutParams = logLp
        renderCharge()
    }

    /**
     * Every played event comes through here, in the order it plays: the action bar's halves follow the moves (round 9 D3), and on the
     * Study screen the charge colours follow the same queue as attacks, so quick answers cannot jump the visuals ahead.
     */
    fun observeStudyEvent(event: GameEvent) {
        logBox.observe(event)
        if (!study) return
        studyCharge.observe(event, com.husarp.ankigo.game.Tuning.CARDS_PER_ROUND)
        renderCharge()
    }

    /** One pending answer can be reconstructed exactly from the saved tally; multiple choices stay neutral. */
    fun restoreStudyCharge() {
        val battle = GameRepo.state?.battle
        studyCharge.restore(battle?.answers ?: 0, battle?.tally ?: 0, com.husarp.ankigo.game.Tuning.CARDS_PER_ROUND)
        renderCharge()
    }

    private fun renderCharge() = logBox.setCharge(studyCharge.goal, studyCharge.filled)

    /** Shows a small "Paused" pill in the bar while paused (small windows and the study strip; the normal panel has the strip). */
    fun setPaused(p: Boolean) {
        paused = p
        updatePausedPill()
    }

    private fun updatePausedPill() {
        logBox.setPaused((small || legacy) && paused)
        pausedBadge.visibility = if (small && paused && !fit.log) VISIBLE else GONE
    }

    // ---------------------------------------------------------------- log

    /**
     * Shows a line about the whole battle across the full bar (the app's own news, hints, errors): coloured pill + text, until the
     * next move arrives. `{{...}}` in [text] turns mauve. [pop] plays the short scale/fade-in (mockup `.pop`, 250 ms). Lines
     * about a move or one side are not shown here (the halves and the arena show them): they go to [sayBattle].
     */
    fun setLog(kind: LogKind, pill: String, text: String, pop: Boolean = true) {
        logBox.showLine(kind, pill, text, pop)
    }

    /** A battle line the bar does not show: only spoken (TalkBack, when "Read battle log" is on). */
    fun sayBattle(text: String) = logBox.announce(text)

    /** A touch that starts anywhere in the panel closes an open move bubble, unless it is on the bubble or a half of the bar. */
    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        if (ev.actionMasked == android.view.MotionEvent.ACTION_DOWN && logBox.bubbleOpen) logBox.onPanelDown(this, ev)
        return super.dispatchTouchEvent(ev)
    }

    // ---------------------------------------------------------------- catch result tiles

    /** True while the catch result tiles replace the log card (tests). */
    internal val tilesShown: Boolean get() = tilesOn

    /**
     * True while a fight's result holds the next wild one back (it appears with the next answer's events or a Continue). Since round 9
     * C5 this starts when the winning batch arrives ([holdEarly]), before the result tiles show, so Continue is there at once.
     */
    val holdsNextFight: Boolean get() = enemyHeld

    /** What [CatchTilesPlan.mode] says about a fight result in this window right now (no answer waiting); [NextFight.holdsEarly] asks it. */
    internal fun resultMode(): CatchTilesPlan.Mode = CatchTilesPlan.mode(!small && !legacy, fit.log, false)

    /** R9 C5: a won or caught fight with a next one behind it: hold the next wild one back from now on, so Continue shows while the rewards still play. */
    fun holdEarly() {
        if (enemyHeld) return
        enemyHeld = true
        holdChanged()
    }

    /** The early hold goes (the result will not hold after all, or it was not shown): nothing else changes; the result tiles, if they are up, keep their own hold. */
    fun releaseEarlyHold() {
        if (!enemyHeld || tilesOn) return
        enemyHeld = false
        holdChanged()
    }

    /** The window became one that does not hold results (strip, mini, floating, no log) before the tiles came: the early hold goes. */
    private fun earlyHoldWindowChanged() {
        if (resultMode() != CatchTilesPlan.Mode.HOLD) releaseEarlyHold()
    }

    /**
     * Shows the result [s] of a fight in the log card's place: the big tiles in a tall panel, one line in a small window or a
     * short panel. Normal panel: stays until [hideCatchTiles] (the next answer) and holds the next wild one back; small windows:
     * [CatchTilesPlan.SMALL_MS], then the log returns. [answerWaiting] = an answer is already queued (fast play): nothing shows.
     * Returns the mode that was used.
     */
    fun showCatchTiles(s: CatchSummary, answerWaiting: Boolean): CatchTilesPlan.Mode {
        val full = !small && !legacy
        val mode = CatchTilesPlan.mode(full, fit.log, answerWaiting)
        if (mode == CatchTilesPlan.Mode.NONE) { releaseEarlyHold(); return mode }   // an answer is waiting behind it: no tiles, so no hold either
        removeCallbacks(hideTilesRun)
        tilesSummary = s
        tilesOn = true
        enemyHeld = mode == CatchTilesPlan.Mode.HOLD
        tilesTimed = mode == CatchTilesPlan.Mode.TIMED
        layoutTiles()
        logBox.visibility = GONE
        tiles.visibility = VISIBLE
        if (mode == CatchTilesPlan.Mode.TIMED) postDelayed(hideTilesRun, CatchTilesPlan.SMALL_MS)
        requestLayout()
        holdChanged()
        return mode
    }

    /**
     * Tells [onHoldChanged] when [holdsNextFight] changed. Posted: it can be called from a measure pass. One notice at a time: it reads the
     * state when it runs, so a hold that came and went (or went and came back) within one call tells nothing / tells once.
     */
    private fun holdChanged() {
        if (holdPosted || holdsNextFight == holdTold) return
        holdPosted = true
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            holdPosted = false
            val now = holdsNextFight
            if (now != holdTold) { holdTold = now; onHoldChanged?.invoke(now) }
        }
    }
    private var holdPosted = false

    /** Back to the log card (and the held wild one, if there is one, is drawn by the next event or [renderAll]). */
    fun hideCatchTiles() {
        removeCallbacks(hideTilesRun)
        if (!tilesOn && !enemyHeld) return
        tilesOn = false
        enemyHeld = false
        tilesTimed = false
        tilesSummary = null
        tiles.visibility = GONE
        logBox.visibility = if (fit.log) VISIBLE else GONE
        requestLayout()
        holdChanged()
    }

    /**
     * The window changed under a result that is showing (PiP or a smaller floating window, the log stepping out): what [CatchResultShift]
     * says. A HOLD result in a small window gets the timeout and lets the parked next wild one appear; no room for it: it goes at once.
     */
    private fun resultWindowChanged() {
        val held = enemyHeld
        when (CatchResultShift.after(tilesTimed, !small && !legacy, fit.log)) {
            CatchResultShift.Do.KEEP -> {}
            CatchResultShift.Do.TIMEOUT -> {
                tilesTimed = true
                enemyHeld = false
                removeCallbacks(hideTilesRun)
                postDelayed(hideTilesRun, CatchTilesPlan.SMALL_MS)
                releaseHeld(held)
                holdChanged()
            }
            CatchResultShift.Do.HIDE -> { hideCatchTiles(); releaseHeld(held) }
        }
    }

    /** The next wild one was held back and is let go now: its owner plays it, else it is drawn from the saved game. */
    private fun releaseHeld(wasHeld: Boolean) {
        if (!wasHeld) return
        // posted on the main looper (not from inside a measure pass; also when the view is detached)
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            val listener = onHeldReleased
            if (listener != null) listener()
            else {
                val st = GameRepo.state ?: return@post
                val data = GameRepo.data ?: return@post
                renderEnemy(st, data)
            }
        }
    }

    /** Binds the tiles for the current window: layout (big or one line), height and margins like the log card's. */
    private fun layoutTiles() {
        val s = tilesSummary ?: return
        val full = !small && !legacy
        val grid = CatchTilesPlan.grid(full, fit.virtualH)
        val lp = tiles.layoutParams as LinearLayout.LayoutParams
        val cont: (() -> Unit)? = if (enemyHeld) onContinue?.let { c -> { c() } } else null   // only a held result waits for a tap
        if (grid) {
            tiles.bindGrid(s, next = true, cont = cont)
            lp.height = -2
            lp.setMargins(dpi(10), dpi(8), dpi(10), 0)
        } else {
            tiles.bindRow(s, next = full, rowH = logLp.height, radiusDp = if (legacy && logBox.flat) 0f else 12f, cont = cont)
            lp.height = logLp.height
            lp.setMargins(logLp.leftMargin, logLp.topMargin, logLp.rightMargin, logLp.bottomMargin)
        }
        tiles.layoutParams = lp
        TextFloor.apply(tiles, fit.floorSp)
    }

    // ---------------------------------------------------------------- footer

    /** Goal bar + "84 / 100 today" (or "Goal done · 112"). */
    fun setGoal(today: Int, goal: Int, animate: Boolean) {
        val g = max(goal, 1)
        goalBar.setMeaning(BarMeaning.GOAL, min(1f, today.toFloat() / g), animate)
        goalBar.setGoalSparks(GoalTone.flameLevel(today.toFloat() / g))
        val sub = Ui.c(context, R.color.ag_sub0)
        val sb = SpannableStringBuilder()
        if (today >= g) {
            sb.append("Goal done")
            sb.append(" · $today")
            sb.setSpan(ForegroundColorSpan(sub), "Goal done".length, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        } else {
            sb.append(today.toString())
            val at = sb.length
            sb.append(" / $g today")
            sb.setSpan(ForegroundColorSpan(sub), at, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        goalText.text = sb
    }

    /** Combo chip: "Combo ×4" in peach, "Combo 0" in grey. [bump] pulses it when the number grew. */
    fun setCombo(n: Int, bump: Boolean) {
        comboChip.setText(if (n > 0) "Combo ×$n" else "Combo 0")
        comboChip.setTone(if (n > 0) R.color.ag_peach else R.color.ag_sub0)
        if (bump && n > shownCombo) comboChip.bump()
        shownCombo = n
    }

    fun setStreak(days: Int) {
        streakChip.setText(if (days == 1) "1 day" else "$days days")
        streakFlame.alive = days > 0
        streakChip.setTone(if (days > 0) R.color.ag_red else R.color.ag_sub0)
    }

    fun setCoins(total: Int, bump: Boolean) {
        coinChip.setText(String.format(Locale.US, "%,d", total))
        if (bump && total != shownCoins) coinChip.bump()
        shownCoins = total
    }

    /** "Item in 7": reviews until the next random item (hidden until the game rolled it). */
    fun setItemCountdown(n: Int) {
        itemChip.visibility = if (n > 0) VISIBLE else GONE
        itemChip.setText("Item in $n")
    }

    fun setBalls(n: Int, bump: Boolean) {
        ballChip.setText(n.toString())
        if (bump && shownBalls >= 0 && n != shownBalls) ballChip.bump()
        shownBalls = n
    }

    /** "+5" with the coin icon floats up from the coin chip. */
    fun floatAtCoins(text: String) {
        val tv = Ui.text(context, 13f, 900, R.color.ag_yellow)
        TextFloor.set(tv, 13f, fit.floorSp)
        tv.text = CoinText.of(tv, text)
        tv.setShadowLayer(Ui.dp(context, 6f), 0f, Ui.dp(context, 1f), Ui.c(context, R.color.ag_base))
        // The overlay sits inside the scaled container: its position is the chip's layout offset in that container's own dp,
        // not a screen coordinate (those are scaled).
        var x = 0; var y = 0
        var c: View? = coinChip
        while (c != null && c !== scaler) { x += c.left; y += c.top; c = c.parent as? View }
        val lp = LayoutParams(-2, -2, Gravity.TOP or Gravity.LEFT)
        lp.leftMargin = x + dpi(6)
        lp.topMargin = y - dpi(14)
        overlay.addView(tv, lp)
        tv.animate().translationY(-Ui.dp(context, 34f)).alpha(0f).setDuration(1000)
            .withEndAction { overlay.removeView(tv) }.start()
    }

    // ---------------------------------------------------------------- drawing from the saved game

    /** Redraws everything from the saved state, no animation. Call when nothing is being played. */
    fun renderAll() {
        val st = GameRepo.state ?: return
        val data = GameRepo.data ?: return
        arena.resetFx()
        renderEnemy(st, data)
        renderPlayer(st, data)
        renderFooter(st)
        prefetch(st, data)
    }

    private fun renderEnemy(st: GameState, data: GameData) {
        val b = st.battle
        if (b == null || enemyHeld) {   // held: the next wild one waits for the next answer (the catch result tiles show)
            arena.setEnemyShown(false)
            arena.setEnemyStatus(null)
            enemyKey = null
            return
        }
        val w = b.wild
        val sp = data.species[w.species]
        cancelEnemyReveal()
        arena.setBackdrop(sp?.types.orEmpty(), BackdropCatalog.seed(w.uid), BackdropCatalog.isLegendary(sp))   // R8-03: a restart / PiP / floating window shows the same picture
        // R9 C4: a picture that is still loading changes the stand points when it comes: the wild one is drawn after it (at the latest after the cap)
        val wait = arena.backdropLoading
        arena.setEnemyShown(!wait)
        arena.setEnemyInfo(w.name, w.level, w.shiny, sp?.types.orEmpty())
        arena.setEnemyStatus(b.enemy.status)
        arena.setEnemyHp(w.hp, if (sp != null) b.maxWildHp(data) else max(w.hp, 1), false)
        arena.setNewTag(w.species !in st.caught)
        if (sp != null) {
            val key = "${w.species}:${w.shiny}"
            if (key != enemyKey) { enemyKey = key; PanelSprites.show(context, arena.enemySprite, sp, SpriteKind.FRONT, w.shiny, false) }
        }
        if (wait) {
            val gen = revealGen
            arena.whenBackdropReady(NextFight.PICTURE_WAIT_MS) { if (gen == revealGen && !enemyHeld && GameRepo.state?.battle != null) arena.setEnemyShown(true) }
        }
    }

    /** R9 C4: a wild one drawn from the saved game waits for its picture ([renderEnemy]); this voids that wait (the event player plays an entrance instead, or the draw is redone). */
    private var revealGen = 0
    fun cancelEnemyReveal() { revealGen++ }

    private fun renderPlayer(st: GameState, data: GameData, m: Mon? = st.main) {
        if (m == null) {
            arena.setPlayerInfo(null, "", 0)
            arena.setPlayerStatus(null)
            playerKey = null
            playerLook = null
            playerSeen = null
            return
        }
        val sp = data.species[m.species]
        playerSeen = playerSeenOf(m)
        arena.setPlayerInfo(m.uid, m.shownName, m.level)
        arena.setPlayerTypes(sp?.types.orEmpty())   // the type look washes our box with the first one's colour (Round 10 W7)
        val pre = preHit(m)   // PLAN 191: fight events from another screen still wait to play: the Pokémon is drawn as it is before the wild one's hit
        arena.setPlayerStatus(if (pre?.fresh == true) null else st.battle?.player?.status)
        arena.setPlayerHp(pre?.hp ?: m.hp, pre?.maxHp ?: if (sp != null) StatCalc.maxHp(data, m) else max(m.hp, 1), false)
        val need = Progression.xpToNext(data, m)
        arena.setPlayerXp(m.xp, need, false)
        if (sp != null) showPlayerPicture(m, data)
    }

    /** The main's picture follows the chosen look (purely visual); nothing is reloaded while the picture is already the right one. */
    private fun showPlayerPicture(m: Mon, data: GameData) {
        val look = m.lookSpecies(data)
        val key = playerKeyOf(look.num, m.shiny)
        if (key != playerKey) { playerKey = key; playerLook = look to m.shiny; PanelSprites.showMain(context, arena.playerSprite, look, m.shiny) }
    }

    /**
     * What [renderPlayer] last drew of the main: who it is (uid, species), the picture it shows (look, shiny), its name, level, HP
     * and XP. A host redraws the player side when the saved game says something else while nothing plays ([refreshMain]).
     */
    private var playerSeen: String? = null

    private fun playerSeenOf(m: Mon) = "${m.uid}:${m.species}:${m.look}:${m.shiny}:${m.shownName}:${m.level}:${preHit(m)?.hp ?: m.hp}:${m.xp}"

    /**
     * PLAN 191: what the player side shows while fight events made on another screen wait to play: [hp] / [maxHp] before the wild
     * one's hit (the saved game already has the HP after it), and a [fresh] status when the Pokémon came in (a switch starts clean).
     * Null: nothing waits for this Pokémon, the saved game is drawn.
     */
    private class PreHit(val hp: Int, val maxHp: Int, val fresh: Boolean)

    private fun preHit(m: Mon): PreHit? {
        val waiting = GameRepo.pendingFightEvents()
        if (waiting.isEmpty()) return null
        val hit = PendingFight.hpBeforeHit(waiting, m.name) ?: return null
        return PreHit(hit.hp, hit.maxHp, waiting.any { it is GameEvent.MainChanged })
    }

    /**
     * R9-W5-A: the saved game changed under this panel without an event for it (the Pokémon tab sent the main to the box, a look
     * or a name was changed, an item healed it, an evolution stone, a release, an import; in another window, another screen, or a
     * screen that waited in the back stack): draws the player side again when the main, its picture, name, level, HP or XP differ
     * from what is shown. Nothing changed = nothing happens (no reload). Hosts call it only when their [EventPlayer] is idle: while
     * events play, those events draw the new numbers (and the player redraws from the game when it ends).
     */
    fun refreshMain() {
        val st = GameRepo.state ?: return
        val data = GameRepo.data ?: return
        val m = st.main
        if (m == null) { if (playerSeen != null) renderPlayer(st, data); return }
        if (playerSeenOf(m) != playerSeen) renderPlayer(st, data)
    }

    fun renderFooter(st: GameState) {
        setGoal(st.reviewsToday, DailyGoal.today(st), false)
        setCombo(st.cardStreak, false)
        val g = GameRepo.game
        val now = System.currentTimeMillis()
        val live = st.lastGoalDay != null && g != null &&
            (st.lastGoalDay == g.dayKey(now) || st.lastGoalDay == g.dayKey(now - 86_400_000L))
        setStreak(if (live) st.streakDays else 0)
        setCoins(st.cash, false)
        setItemCountdown(st.itemCountdown)
        setBalls(ballCount(st), false)
    }

    /**
     * Redraws the player card (and sprite if the species changed) from the saved state, e.g. after a team switch. [uid]: draw that
     * team member instead of the main (a switch event playing while the save is already further on: several switches made on another
     * screen play one by one, each Pokémon drawn as itself; [refreshMain] draws the main again once nothing plays).
     */
    fun refreshPlayer(uid: String? = null) {
        val st = GameRepo.state ?: return
        val data = GameRepo.data ?: return
        renderPlayer(st, data, uid?.let { st.mon(it) } ?: st.main)
    }

    /** Forgets which sprite is shown so the next [renderAll] / event loads it again (after a species change). */
    fun forgetSprites() { enemyKey = null; playerKey = null; playerLook = null }

    /** Marks the main's sprite as already loaded for a species (set by the evolution animation). */
    fun noteEnemySprite(species: Int, shiny: Boolean) { enemyKey = "$species:$shiny" }
    fun notePlayerSprite(species: Int, shiny: Boolean) { playerKey = playerKeyOf(species, shiny); playerLook = null }

    /** Downloads the next encounter and the main ahead of time so the next fight appears at once. */
    /** R9 C4: the queued next encounter's background starts loading now, so it is in memory (usually) when its wild one comes. */
    fun prefetchBackdrop(species: Int, wildUid: String) {
        val sp = GameRepo.data?.species?.get(species)
        arena.prefetchBackdrop(sp?.types.orEmpty(), BackdropCatalog.seed(wildUid), BackdropCatalog.isLegendary(sp))
    }

    fun prefetch(st: GameState, data: GameData) {
        st.nextSpecies?.let { n -> data.species[n]?.let { PanelSprites.prefetch(context, it, st.nextShiny, false) } }
        st.main?.let { m -> PanelSprites.prefetch(context, m.lookSpecies(data), m.shiny, true) }
        // "Next team member as main" brings the second one next: have its sprite ready.
        st.team.getOrNull(1)?.let { st.mon(it) }?.let { m -> PanelSprites.prefetch(context, m.lookSpecies(data), m.shiny, true) }
    }

    fun cancelAnimations() {
        arena.cancelAnimations()
        logBox.cancelAnimations()
        logBox.closeBubble()
        overlay.removeAllViews()
        listOf(comboChip, coinChip, ballChip, itemChip, streakChip).forEach { it.animate().cancel(); it.scaleX = 1f; it.scaleY = 1f }
    }

    private fun dpi(v: Int) = Ui.dp(context, v)

    companion object {
        /** Number of Poké Balls (all kinds) in the bag. */
        fun ballCount(st: GameState): Int =
            st.bag.entries.sumOf { (id, n) -> if (n > 0 && Items.def(id)?.kind == ItemKind.BALL) n else 0 }
    }
}

/** The log's tiny markup: `{{name}}` is shown in mauve (the mockup's `<em>`), for Pokémon and move names. */
internal object PanelText {
    fun em(s: String) = "{{$s}}"

    fun rich(ctx: Context, s: String): CharSequence {
        if (!s.contains("{{")) return s
        val mauve = Ui.c(ctx, R.color.ag_mauve)
        val out = SpannableStringBuilder()
        var i = 0
        while (i < s.length) {
            val a = s.indexOf("{{", i)
            val b = if (a >= 0) s.indexOf("}}", a + 2) else -1
            if (a < 0 || b < 0) { out.append(s, i, s.length); break }
            out.append(s, i, a)
            val start = out.length
            out.append(s, a + 2, b)
            out.setSpan(ForegroundColorSpan(mauve), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            i = b + 2
        }
        return out
    }
}

/**
 * The log card's background: a rounded `ag_card` with a coloured strip down the left edge, like the CSS
 * `border-left: 4px solid` on a card with 12 px corners (the inner edge curves a little less).
 */
internal class PanelAccentCard(private val card: Int, accent: Int, private val radius: Float, private val bar: Float) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val rect = RectF()

    var cornerRadius: Float = radius
        set(v) { field = v; invalidateSelf() }

    var accent: Int = accent
        set(v) { field = v; invalidateSelf() }

    override fun draw(canvas: Canvas) {
        val b = bounds
        rect.set(b)
        paint.color = accent
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)
        val radius = cornerRadius
        val inner = max(radius - bar, 0f)
        rect.set(b.left + bar, b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat())
        path.rewind()
        path.addRoundRect(rect, floatArrayOf(inner, radius, radius, radius, radius, radius, inner, radius), Path.Direction.CW)
        paint.color = card
        canvas.drawPath(path, paint)
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

/**
 * A row of chips that never wraps: when they don't fit, [hideOrder] says which go first (item countdown, then the
 * day streak), so the more useful ones stay.
 */
private class PanelChipRow(ctx: Context) : ViewGroup(ctx) {
    var hideOrder: List<View> = emptyList()
    private val gap = Ui.dp(ctx, 5)
    private val hidden = HashSet<View>()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val avail = MeasureSpec.getSize(widthMeasureSpec)
        val kids = (0 until childCount).map { getChildAt(it) }.filter { it.visibility != GONE }
        var total = 0
        var h = 0
        for (k in kids) {
            k.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
            total += k.measuredWidth
            h = max(h, k.measuredHeight)
        }
        total += gap * max(0, kids.size - 1)
        hidden.clear()
        for (v in hideOrder) {
            if (total <= avail) break
            if (v in kids) { hidden += v; total -= v.measuredWidth + gap }
        }
        setMeasuredDimension(avail, h)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        // R18-D (123): the chips are spread over the whole line with even gaps (space-evenly), no boxes behind them
        val shown = (0 until childCount).map { getChildAt(it) }.filter { it.visibility != GONE && it !in hidden }
        val free = (r - l) - shown.sumOf { it.measuredWidth }
        val space = max(gap, free / (shown.size + 1))
        var x = if (free / (shown.size + 1) >= gap) space else 0
        for (i in 0 until childCount) {
            val k = getChildAt(i)
            if (k.visibility == GONE) continue
            if (k in hidden) { k.layout(0, 0, 0, 0); continue }
            k.layout(x, 0, x + k.measuredWidth, k.measuredHeight)
            x += k.measuredWidth + space
        }
        // a clickable chip keeps a 48 dp touch area although the row is only as tall as its chips (R18 RV-8): a view only gets touches
        // inside its own bounds, so the delegate sits on the parent (the footer) with the rects in the parent's coordinates
        val host = parent as? View ?: return
        val min = Ui.dp(context, 48)
        val tc = TouchDelegateGroup(host, left, top)
        for (k in shown) if (k.isClickable) tc.add(delegateRect(k.left, k.top, k.right, k.bottom, min).also { it.offset(left, top) }, k)
        if (tc.isEmpty()) { if (host.touchDelegate is TouchDelegateGroup) host.touchDelegate = null } else host.touchDelegate = tc
    }

    companion object {
        /** The touch rect of a chip at [l],[t],[r],[b]: grown to at least [min] px wide and tall, around the chip (pure, tested). */
        fun delegateRect(l: Int, t: Int, r: Int, b: Int, min: Int): android.graphics.Rect {
            val padX = max(0, (min - (r - l)) / 2); val padY = max(0, (min - (b - t)) / 2)
            return android.graphics.Rect(l - padX, t - padY, r + padX, b + padY)
        }
    }
}

/**
 * R18-D: one touch delegate for several children (a view has only one). The rects are in the host's coordinates; the children sit in a
 * row at ([dx], [dy]) inside the host, so the event is moved by that and by the child's own position.
 */
private class TouchDelegateGroup(host: View, private val dx: Int, private val dy: Int) : android.view.TouchDelegate(android.graphics.Rect(), host) {
    private val items = ArrayList<Pair<android.graphics.Rect, View>>()
    private var active: View? = null
    fun add(r: android.graphics.Rect, v: View) { items += r to v }
    fun isEmpty() = items.isEmpty()
    override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
        val x = event.x.toInt(); val y = event.y.toInt()
        if (event.actionMasked == android.view.MotionEvent.ACTION_DOWN) active = items.firstOrNull { it.first.contains(x, y) }?.second
        val v = active ?: return false
        val e = android.view.MotionEvent.obtain(event)
        e.offsetLocation(-(dx + v.left).toFloat(), -(dy + v.top).toFloat())
        val ok = v.dispatchTouchEvent(e)
        e.recycle()
        if (event.actionMasked == android.view.MotionEvent.ACTION_UP || event.actionMasked == android.view.MotionEvent.ACTION_CANCEL) active = null
        return ok
    }
}
