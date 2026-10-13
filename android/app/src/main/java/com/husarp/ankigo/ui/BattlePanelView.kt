package com.husarp.ankigo.ui

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
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.annotation.ColorRes
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.R
import com.husarp.ankigo.data.SpriteKind
import com.husarp.ankigo.game.GameData
import com.husarp.ankigo.game.GameState
import com.husarp.ankigo.game.ItemKind
import com.husarp.ankigo.game.Items
import com.husarp.ankigo.game.Progression
import com.husarp.ankigo.game.StatCalc
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

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
 * footer (goal bar, menu, chips). It only draws; [EventPlayer] tells it what happened and [renderAll] redraws
 * everything from the saved game state.
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

    // ---- footer ----
    private val goalBar = BarView(ctx).apply {
        setGradient(Ui.c(ctx, R.color.ag_mauve), Ui.c(ctx, R.color.ag_pink))
    }
    private val goalText = Ui.text(ctx, 12f, 900, R.color.ag_text).apply { maxLines = 1 }
    private val menuButton = FrameLayout(ctx)
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
        logBox.addView(logText, LinearLayout.LayoutParams(0, -2, 1f))
        setLog(LogKind.NEUTRAL, "READY", "Waiting for your first answer in AnkiDroid.", pop = false)

        // footer
        val menuIcon = ImageView(ctx).apply {
            setImageResource(R.drawable.ic_menu)
            setColorFilter(Ui.c(ctx, R.color.ag_sub0), PorterDuff.Mode.SRC_IN)
            setPadding(dpi(5), dpi(5), dpi(5), dpi(5))
            background = Ui.rounded(Ui.c(ctx, R.color.ag_mantle), 8, ctx)
        }
        menuButton.addView(menuIcon, LayoutParams(dpi(26), dpi(26), Gravity.CENTER))
        menuButton.contentDescription = "Menu: Team, Pokédex, Shop, Settings"
        menuButton.setOnClickListener { onMenu(menuButton) }
        val goalRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        goalRow.addView(goalBar, LinearLayout.LayoutParams(0, dpi(8), 1f).also { it.marginEnd = dpi(8) })
        goalRow.addView(goalText, LinearLayout.LayoutParams(-2, -2).also { it.marginEnd = dpi(4) })
        goalRow.addView(menuButton, LinearLayout.LayoutParams(dpi(34), dpi(30)))

        chips.addView(comboChip); chips.addView(streakChip); chips.addView(coinChip); chips.addView(itemChip); chips.addView(ballChip)
        chips.hideOrder = listOf(itemChip, streakChip) // least important first
        ballChip.setOnClickListener { onBalls() }
        ballChip.contentDescription = "Poké Balls"

        val footer = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpi(10), dpi(8), dpi(10), dpi(8))
        }
        footer.addView(goalRow, LinearLayout.LayoutParams(-1, -2))
        footer.addView(chips, LinearLayout.LayoutParams(-1, -2).also { it.topMargin = dpi(4) })

        val column = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        column.addView(strip, LinearLayout.LayoutParams(-1, -2))
        column.addView(arena, LinearLayout.LayoutParams(-1, 0, 1f))
        column.addView(logBox, LinearLayout.LayoutParams(-1, dpi(52)).also { it.setMargins(dpi(10), dpi(8), dpi(10), 0) })
        column.addView(footer, LinearLayout.LayoutParams(-1, -2))

        // A very short window scrolls instead of squeezing the arena below ~140 dp.
        val scroll = ScrollView(ctx).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            overScrollMode = OVER_SCROLL_NEVER
            addView(column, ViewGroup.LayoutParams(-1, -2))
        }
        addView(scroll, LayoutParams(-1, -1))
        addView(overlay, LayoutParams(-1, -1))
    }

    // ---------------------------------------------------------------- strip

    /** Red strip above the arena while detection has a problem; [canGrant] adds the "Grant" button. */
    fun setStatus(ok: Boolean, text: String, canGrant: Boolean) {
        strip.visibility = if (ok) GONE else VISIBLE
        stripText.text = text
        grant.visibility = if (canGrant) VISIBLE else GONE
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
        logPill = p
        logText.setTextSize(TypedValue.COMPLEX_UNIT_SP, if (text.length > 64) 12f else 13f)
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
        goalBar.set(min(1f, today.toFloat() / g), Ui.c(context, R.color.ag_mauve), animate)
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

    /** "+40 ₽" floats up from the coin chip. */
    fun floatAtCoins(text: String) {
        val tv = Ui.text(context, 13f, 900, R.color.ag_yellow)
        tv.text = text
        tv.setShadowLayer(Ui.dp(context, 6f), 0f, Ui.dp(context, 1f), Ui.c(context, R.color.ag_base))
        val a = IntArray(2); val b = IntArray(2)
        coinChip.getLocationOnScreen(a); overlay.getLocationOnScreen(b)
        val lp = LayoutParams(-2, -2, Gravity.TOP or Gravity.LEFT)
        lp.leftMargin = a[0] - b[0] + dpi(6)
        lp.topMargin = a[1] - b[1] - dpi(14)
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
            enemyKey = null
            return
        }
        val w = b.wild
        val sp = data.species[w.species]
        arena.setEnemyShown(true)
        arena.setEnemyInfo(w.name, w.level, w.shiny, sp?.types.orEmpty())
        arena.setEnemyHp(w.hp, if (sp != null) StatCalc.maxHp(data, w) else max(w.hp, 1), false)
        arena.setCatchTag(willCatchNow(), w.species !in st.caught)
        if (sp != null) {
            val key = "${w.species}:${w.shiny}"
            if (key != enemyKey) { enemyKey = key; PanelSprites.show(context, arena.enemySprite, sp, SpriteKind.FRONT, w.shiny, false) }
        }
    }

    private fun renderPlayer(st: GameState, data: GameData) {
        val m = st.main
        if (m == null) {
            arena.setPlayerInfo(null, "", 0)
            playerKey = null
            return
        }
        val sp = data.species[m.species]
        arena.setPlayerInfo(m.uid, m.name, m.level)
        arena.setPlayerHp(m.hp, if (sp != null) StatCalc.maxHp(data, m) else max(m.hp, 1), false)
        val need = Progression.xpToNext(data, m)
        arena.setPlayerXp(if (need > 0) m.xp.toFloat() / need else 1f, false)
        if (sp != null) {
            val key = "${m.species}:${m.shiny}"
            if (key != playerKey) { playerKey = key; PanelSprites.show(context, arena.playerSprite, sp, SpriteKind.BACK, m.shiny, true) }
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
        st.main?.let { m -> data.species[m.species]?.let { PanelSprites.prefetch(context, it, m.shiny, true) } }
    }

    private fun willCatchNow(): Boolean = try { GameRepo.game?.willCatch() ?: false } catch (e: Exception) { false }

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

    var accent: Int = accent
        set(v) { field = v; invalidateSelf() }

    override fun draw(canvas: Canvas) {
        val b = bounds
        rect.set(b)
        paint.color = accent
        canvas.drawRoundRect(rect, radius, radius, paint)
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
