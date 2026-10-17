package com.husarp.ankigo.ui

import com.husarp.ankigo.game.lookSpecies
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
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
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.R
import com.husarp.ankigo.data.SpriteKind
import com.husarp.ankigo.game.GameEvent
import com.husarp.ankigo.game.GameData
import com.husarp.ankigo.game.GameState
import com.husarp.ankigo.game.ItemKind
import com.husarp.ankigo.game.Items
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

    // ---- log ----
    private val logBg = PanelAccentCard(Ui.c(ctx, R.color.ag_card), Ui.c(ctx, R.color.ag_s1), Ui.dp(ctx, 12f), Ui.dp(ctx, 4f))
    private val logText = Ui.text(ctx, 13f, 700, R.color.ag_text).apply {
        maxLines = 2
        ellipsize = TextUtils.TruncateAt.END
        setLineSpacing(0f, 1.2f)
    }
    private var logPill: TextView? = null
    private val logBox = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = logBg
        // 4 dp border + the mockup's 10 dp padding on the left
        setPadding(dpi(14), dpi(8), dpi(10), dpi(8))
    }
    private var accentAnim: ValueAnimator? = null
    private var logSize = 13f

    // ---- modes ----
    /** Picture-in-picture or floating battle ([setCompact] outside the study screens): the normal panel, scaled. */
    private var small = false
    /** The study strip ([setStudyLayout]): the old stripped-down look (one log line, no footer), never scaled. */
    private var legacy = false
    private var cornerClearance = 0
    private var study = false
    private var statsBarSmall = true
    private var compactCards = false
    private var cardsOwn = true
    private var playerFront = false
    private var applied: PanelFit.Fit? = null
    private var fit = PanelFit.Fit(1f, 0f, 0f, 0f, stats = true, log = true, xp = true, tools = true, logLines = 2, compactCards = false, middle = false, ok = true)
    private val chargePips = StudyUi.row(ctx).apply { visibility = GONE }
    private val studyCharge = StudyCharge()
    private val pips = List(4) { View(ctx) }

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

    /** "Paused" pill at the start of the one-line log (small windows and the study strip; it replaces the log pill). */
    private val pausedPill = newPausedPill()

    /** The same pill over the arena's top-left corner while paused and the log line has stepped out (the smallest windows). */
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
    private val comboChip = Ui.chip(ctx, R.drawable.ic_bolt, R.color.ag_sub0)
    private val streakChip = Ui.chip(ctx, R.drawable.ic_flame, R.color.ag_red)
    private val coinChip = Ui.chip(ctx, R.drawable.ic_coin, R.color.ag_yellow)
    private val itemChip = Ui.chip(ctx, R.drawable.ic_gift, R.color.ag_teal)
    private val ballChip = Ui.chip(ctx, R.drawable.ic_ball, R.color.ag_sub1)
    private val chips = PanelChipRow(ctx)
    private val overlay = FrameLayout(ctx).apply { clipChildren = false; clipToPadding = false }
    private var shownCombo = 0
    private var shownBalls = -1
    private var shownCoins = -1
    private var enemyKey: String? = null
    private var playerKey: String? = null

    init {
        // strip
        strip.addView(stripText, LinearLayout.LayoutParams(0, -2, 1f))
        strip.addView(grant, LinearLayout.LayoutParams(-2, -2).also { it.marginStart = dpi(8) })

        // log
        logBox.addView(pausedPill, LinearLayout.LayoutParams(-2, -2).also { it.marginEnd = dpi(6) })
        logBox.addView(logText, LinearLayout.LayoutParams(0, -2, 1f))
        pips.forEachIndexed { i, pip -> chargePips.addView(pip, LinearLayout.LayoutParams(dpi(10), dpi(10)).also { if (i > 0) it.marginStart = dpi(4) }) }
        logBox.addView(chargePips, LinearLayout.LayoutParams(-2, -2).also { it.marginStart = dpi(7) })

        setLog(LogKind.NEUTRAL, "READY", "Waiting for your first answer in AnkiDroid.", pop = false)

        // footer: goal bar, goal text, tool buttons, menu; then the chips
        goalRow.addView(goalBar, goalBarLp)
        goalRow.addView(goalText, LinearLayout.LayoutParams(-2, -2).also { it.marginEnd = dpi(4) })
        tools.addView(menuButton, buttonLp())
        goalRow.addView(tools, LinearLayout.LayoutParams(-2, -2))

        chips.addView(comboChip); chips.addView(streakChip); chips.addView(coinChip); chips.addView(itemChip); chips.addView(ballChip)
        chips.hideOrder = listOf(itemChip, streakChip) // least important first (only when even the scaled row is too wide)
        ballChip.setOnClickListener { onBalls() }
        ballChip.contentDescription = "Poké Balls"

        footer.addView(goalRow, LinearLayout.LayoutParams(-1, -2))
        footer.addView(chips, LinearLayout.LayoutParams(-1, -2).also { it.topMargin = dpi(4) })

        column.addView(strip, LinearLayout.LayoutParams(-1, -2))
        column.addView(arena, LinearLayout.LayoutParams(-1, 0, 1f))
        column.addView(logBox, logLp)
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

    private data class Asked(val w: Float, val h: Float, val small: Boolean, val statsBar: Boolean, val compact: Boolean, val strip: Boolean, val corner: Float, val tools: Boolean, val own: Boolean)
    private var asked: Asked? = null
    private var answer: PanelFit.Fit? = null

    /** [PanelFit.decide] for this window and these prefs; the last answer is kept while nothing changes (measure runs often). */
    private fun decide(w: Float, h: Float): PanelFit.Fit {
        val a = Asked(w, h, small, statsBarSmall, compactCards, !statusOk, if (small) cornerClearance / resources.displayMetrics.density else 0f, toolsWanted, cardsOwn)
        answer?.let { if (a == asked) return it }
        return PanelFit.decide(a.w, a.h, a.small, a.statsBar, a.compact, a.strip, a.corner, a.tools, a.own).also { asked = a; answer = it }
    }

    /** Applies a decision: which parts show, the text floor, the log's height. Does nothing when it did not change. */
    private fun apply(f: PanelFit.Fit) {
        fit = f
        if (f == applied) return
        applied = f
        arena.configure(f.floorSp, f.compactCards, f.xp)
        strip.visibility = if (statusOk || legacy) GONE else VISIBLE
        stripText.maxLines = if (small) 1 else 2
        footer.visibility = if (f.stats) VISIBLE else GONE
        logBox.visibility = if (f.log) VISIBLE else GONE
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
            logText.maxLines = f.logLines
        }
        for (v in listOf(strip, logBox, footer, pausedBadge)) TextFloor.apply(v, f.floorSp)
        updatePausedPill()
        requestLayout()
    }

    private fun dpf(v: Float) = (v * resources.displayMetrics.density + 0.5f).toInt()

    /** The window's decision, for tests: scale, parts, card layout. */
    internal val currentFit: PanelFit.Fit get() = fit

    /** Re-reads the battle panel prefs (stats bar in small windows, compact stat cards, cards under their own Pokémon) and lays out again. */
    fun refreshPrefs() {
        statsBarSmall = AppPrefs.statsBarSmall(context)
        compactCards = AppPrefs.compactCards(context)
        cardsOwn = AppPrefs.cardsUnderOwn(context)
        arena.cardsUnderOwn = cardsOwn
        arena.playerSameSize = AppPrefs.playerSameSize(context)
        AppPrefs.playerFront(context).let { if (it != playerFront) { playerFront = it; playerKey = null } }   // the main's picture loads again
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
            background = Ui.rounded(Ui.c(context, R.color.ag_mantle), 8, context)
        }, LayoutParams(dpi(26), dpi(26), Gravity.CENTER))
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

    private fun setSmall(on: Boolean) {
        if (small == on) return
        small = on
        applyLogLook()
        updatePausedPill()
        applied = null
        requestLayout()
    }

    /** Log card paddings, line count and pill for the current mode. */
    private fun applyLogLook() {
        val one = small || legacy
        logText.accessibilityLiveRegion = if (context.getSharedPreferences("study", 0).getBoolean("speakBattle", true)) View.ACCESSIBILITY_LIVE_REGION_POLITE else View.ACCESSIBILITY_LIVE_REGION_NONE
        logText.maxLines = if (one) 1 else 2
        if (!legacy) logBox.setPadding(dpi(14), dpi(if (small) 4 else 8), dpi(10), dpi(if (small) 4 else 8))
        logPill?.visibility = if (small && paused) GONE else VISIBLE
        TextFloor.set(logText, if (legacy) 12f else logSize, fit.floorSp)
    }

    /** The study strip: the old compact look (one 26 dp log line, no footer, no strip), at scale 1. */
    private fun setLegacy(on: Boolean) {
        if (legacy == on) return
        legacy = on
        arena.setCompact(on)
        strip.visibility = if (on || statusOk) GONE else VISIBLE
        logLp.height = if (on) dpi(26) else dpi(52)
        if (on) logLp.setMargins(dpi(6), dpi(3), dpi(6) + cornerClearance, dpi(3)) else logLp.setMargins(dpi(10), dpi(8), dpi(10), 0)
        logBox.setPadding(if (on) dpi(10) else dpi(14), if (on) dpi(2) else dpi(8), dpi(8), if (on) dpi(2) else dpi(8))
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
        chargePips.visibility = VISIBLE
        logLp.height = dpi(if (small) 34 else 52)
        if (small) {
            logLp.setMargins(0, 0, 0, 0)
            logBox.setPadding(dpi(12), dpi(5), dpi(10), dpi(5))
            TextFloor.set(logText, 12f, 0f)
            logBg.cornerRadius = 0f
        } else logBg.cornerRadius = Ui.dp(context, 12f)
        logPill?.let { styleStudyPill(it) }
        logBox.layoutParams = logLp
        renderCharge()
    }

    /** Charge colours follow the same event queue as attacks, so quick answers cannot jump the visuals ahead. */
    fun observeStudyEvent(event: GameEvent) {
        if (!study) return
        studyCharge.observe(event, GameRepo.state?.settings?.cardsPerRound ?: 2)
        renderCharge()
    }

    /** One pending answer can be reconstructed exactly from the saved tally; multiple choices stay neutral. */
    fun restoreStudyCharge() {
        val battle = GameRepo.state?.battle
        studyCharge.restore(battle?.answers ?: 0, battle?.tally ?: 0, GameRepo.state?.settings?.cardsPerRound ?: 2)
        renderCharge()
    }

    private fun renderCharge() {
        val filled = studyCharge.filled
        pips.forEachIndexed { i, v ->
            v.visibility = if (i < studyCharge.goal) VISIBLE else GONE
            val button = filled.getOrNull(i)
            val tone = when (button) { 1 -> R.color.ag_red; 2 -> R.color.ag_peach; 3 -> R.color.ag_green; 4 -> R.color.ag_blue; 0 -> R.color.ag_ov; else -> R.color.ag_s0 }
            v.background = Ui.rounded(Ui.c(context, tone), 5, context).apply {
                if (button == null) setStroke(Ui.dp(context, 1.5f).toInt(), Ui.c(context, R.color.ag_s1))
            }
        }
        chargePips.contentDescription = "Battle charge ${filled.size} of ${studyCharge.goal}"
    }

    private fun styleStudyPill(p: TextView) {
        if (!study) return
        p.visibility = VISIBLE
        if (legacy) {
            (p.background as? android.graphics.drawable.GradientDrawable)?.cornerRadius = Ui.dp(context, 6f)
            TextFloor.set(p, 9.5f, 0f)
            p.setPadding(dpi(6), dpi(2), dpi(6), dpi(2))
            (p.layoutParams as LinearLayout.LayoutParams).marginEnd = dpi(7)
        }
    }

    /** Shows a small "Paused" pill in the log while paused (small windows and the study strip; the normal panel has the strip). */
    fun setPaused(p: Boolean) {
        paused = p
        updatePausedPill()
        logPill?.visibility = if (small && paused) GONE else VISIBLE
    }

    private fun updatePausedPill() {
        pausedPill.visibility = if ((small || legacy) && paused) VISIBLE else GONE
        pausedBadge.visibility = if (small && paused && !fit.log) VISIBLE else GONE
    }

    // ---------------------------------------------------------------- log

    /**
     * Replaces the log card's content: coloured pill + text. `{{...}}` in [text] turns mauve. [pop] plays the short
     * scale/fade-in (mockup `.pop`, 250 ms).
     */
    fun setLog(kind: LogKind, pill: String, text: String, pop: Boolean = true) {
        logPill?.let { logBox.removeView(it) }
        val p = Ui.pill(context, pill, kind.tone)
        logBox.addView(p, 0, LinearLayout.LayoutParams(-2, -2).also { it.marginEnd = dpi(8) })
        p.visibility = if (small && paused) GONE else VISIBLE
        logPill = p
        TextFloor.apply(p, fit.floorSp)
        styleStudyPill(p)
        logSize = if (text.length > 64) 12f else 13f
        TextFloor.set(logText, if (legacy) 12f else logSize, fit.floorSp)
        logText.text = PanelText.rich(context, text)
        val to = Ui.c(context, kind.border)
        accentAnim?.cancel()
        if (pop && isAttachedToWindow) {
            val from = logBg.accent
            accentAnim = ValueAnimator.ofObject(ArgbEvaluator(), from, to).setDuration(200).also { a ->
                a.addUpdateListener { logBg.accent = it.animatedValue as Int }
                a.start()
            }
            logBox.animate().cancel()
            logBox.scaleX = 0.97f; logBox.scaleY = 0.97f; logBox.alpha = 0.6f
            logBox.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(250).start()
        } else {
            logBg.accent = to
        }
    }

    // ---------------------------------------------------------------- footer

    /** Goal bar + "84 / 100 today" (or "Goal done · 112"). */
    fun setGoal(today: Int, goal: Int, animate: Boolean) {
        val g = max(goal, 1)
        goalBar.setMeaning(BarMeaning.GOAL, min(1f, today.toFloat() / g), animate)
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

    fun setStreak(days: Int) { streakChip.setText(if (days == 1) "1 day" else "$days days") }

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

    /** "+5 ₽" floats up from the coin chip. */
    fun floatAtCoins(text: String) {
        val tv = Ui.text(context, 13f, 900, R.color.ag_yellow)
        TextFloor.set(tv, 13f, fit.floorSp)
        tv.text = text
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
        if (b == null) {
            arena.setEnemyShown(false)
            arena.setEnemyStatus(null)
            enemyKey = null
            return
        }
        val w = b.wild
        val sp = data.species[w.species]
        arena.setEnemyShown(true)
        arena.setEnemyInfo(w.name, w.level, w.shiny, sp?.types.orEmpty())
        arena.setEnemyStatus(b.enemy.status)
        arena.setEnemyHp(w.hp, if (sp != null) b.maxWildHp(data) else max(w.hp, 1), false)
        arena.setNewTag(w.species !in st.caught)
        if (sp != null) {
            val key = "${w.species}:${w.shiny}"
            if (key != enemyKey) { enemyKey = key; PanelSprites.show(context, arena.enemySprite, sp, SpriteKind.FRONT, w.shiny, false) }
        }
    }

    private fun renderPlayer(st: GameState, data: GameData) {
        val m = st.main
        if (m == null) {
            arena.setPlayerInfo(null, "", 0)
            arena.setPlayerStatus(null)
            playerKey = null
            return
        }
        val sp = data.species[m.species]
        arena.setPlayerInfo(m.uid, m.shownName, m.level)
        arena.setPlayerStatus(st.battle?.player?.status)
        arena.setPlayerHp(m.hp, if (sp != null) StatCalc.maxHp(data, m) else max(m.hp, 1), false)
        val need = Progression.xpToNext(data, m)
        arena.setPlayerXp(m.xp, need, false)
        if (sp != null) {
            val look = m.lookSpecies(data)   // the picture follows the chosen look (purely visual)
            val key = "${look.num}:${m.shiny}"
            if (key != playerKey) { playerKey = key; PanelSprites.showMain(context, arena.playerSprite, look, m.shiny) }
        }
    }

    fun renderFooter(st: GameState) {
        setGoal(st.reviewsToday, st.settings.dailyAverage, false)
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

    /** Redraws the player card (and sprite if the species changed) from the saved state, e.g. after a team switch. */
    fun refreshPlayer() {
        val st = GameRepo.state ?: return
        val data = GameRepo.data ?: return
        renderPlayer(st, data)
    }

    /** Forgets which sprite is shown so the next [renderAll] / event loads it again (after a species change). */
    fun forgetSprites() { enemyKey = null; playerKey = null }

    /** Marks the main's sprite as already loaded for a species (set by the evolution animation). */
    fun noteEnemySprite(species: Int, shiny: Boolean) { enemyKey = "$species:$shiny" }
    fun notePlayerSprite(species: Int, shiny: Boolean) { playerKey = "$species:$shiny" }

    /** Downloads the next encounter and the main ahead of time so the next fight appears at once. */
    fun prefetch(st: GameState, data: GameData) {
        st.nextSpecies?.let { n -> data.species[n]?.let { PanelSprites.prefetch(context, it, st.nextShiny, false) } }
        st.main?.let { m -> PanelSprites.prefetch(context, m.lookSpecies(data), m.shiny, true) }
        // "Next team member as main" brings the second one next: have its sprite ready.
        st.team.getOrNull(1)?.let { st.mon(it) }?.let { m -> PanelSprites.prefetch(context, m.lookSpecies(data), m.shiny, true) }
    }

    fun cancelAnimations() {
        arena.cancelAnimations()
        accentAnim?.cancel()
        logBox.animate().cancel()
        logBox.scaleX = 1f; logBox.scaleY = 1f; logBox.alpha = 1f
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
private class PanelAccentCard(private val card: Int, accent: Int, private val radius: Float, private val bar: Float) : Drawable() {
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
        var x = 0
        for (i in 0 until childCount) {
            val k = getChildAt(i)
            if (k.visibility == GONE) continue
            if (k in hidden) { k.layout(0, 0, 0, 0); continue }
            k.layout(x, 0, x + k.measuredWidth, k.measuredHeight)
            x += k.measuredWidth + gap
        }
    }
}
