package com.husarp.ankigo.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.core.graphics.ColorUtils
import com.husarp.ankigo.R
import kotlin.math.abs
import kotlin.math.max

/*
 * R19-H (PLAN 257, pick A "Card toast", from the LEFT): the old whole-bar lines (Played fast, New card OK, Redo after undo, Undo in
 * AnkiDroid, Caught up, Daily gift, Item found, Badge, Trainer level, New day, Daily goal / Info lines, Switching after this card) are
 * small cards at the top-right of the arena, above your Pokémon: they slide in from the left edge, stay about 2.5 s, slide out; a tap
 * closes one. The action bar keeps its segments. Everything here that is not a View is pure (unit-tested): [ToastKind], [ToastText],
 * [ToastQueue], [ToastLook], [ToastSpot]. The panel raises one with [BattlePanelView.toast] (or [BattlePanelView.setLog], which maps
 * its kind / pill to a [ToastKind]).
 */

/** What a toast is about; it picks the icon and the tone. Other code raises one with `panel.toast(ToastKind.X, "text")`. */
enum class ToastKind(@DrawableRes val icon: Int, val neutral: Boolean = false) {
    /** Anything else about the app (an info line, a hint). */
    NEWS(R.drawable.ic_info),
    BOND(R.drawable.ic_heart),
    /** A neutral note (nothing happened in the battle: a redo that does not count ...). */
    NOTE(R.drawable.ic_info, neutral = true),
    /** Answers played fast / the summary of a skipped pile of events. */
    FAST(R.drawable.ic_bolt),
    /** "New card: OK. No attacks this time." */
    NEW_CARD_OK(R.drawable.ic_check, neutral = true),
    /** "Redo after undo" / "Undo in AnkiDroid". */
    UNDO(R.drawable.ic_undo, neutral = true),
    /** Answers made while AnkiGo was closed were counted. */
    CAUGHT_UP(R.drawable.ic_clock),
    /** The daily goal's gift. */
    GIFT(R.drawable.ic_gift),
    /** A found item. */
    ITEM(R.drawable.ic_gift),
    /** A badge or an achievement. */
    BADGE(R.drawable.ic_ach_trophy),
    /** A new trainer level. */
    TRAINER(R.drawable.ic_trainer_rank),
    /** The day changed: the daily goal starts again. */
    NEW_DAY(R.drawable.ic_calendar),
    /** The daily goal was reached. */
    GOAL(R.drawable.ic_ach_target),
    /** A switch: queued, cancelled, or "Switched! ... gets a free hit". */
    SWITCH(R.drawable.ic_swap);

    companion object {
        /** The kind for a [BattlePanelView.setLog] line: its pill word first ("FAST", "ITEM" ...), the log kind and the text for the rest. */
        fun of(kind: LogKind, pill: String, text: String = ""): ToastKind = when (pill.trim().uppercase()) {
            "BOND ♥" -> BOND
            "FAST" -> FAST
            "OK" -> NEW_CARD_OK
            "UNDO" -> UNDO
            "CAUGHT UP" -> CAUGHT_UP
            "ITEM" -> ITEM
            "BADGE", "ACHIEVE" -> BADGE
            "TRAINER" -> TRAINER
            "NEW DAY" -> NEW_DAY
            "SWITCH" -> SWITCH
            "GOAL" -> if (text.startsWith("Daily gift")) GIFT else GOAL
            else -> if (kind == LogKind.NEUTRAL) NOTE else NEWS
        }
    }
}

/** How a line is cut into the toast's main text and its small second line. */
object ToastText {
    /**
     * "Caught up 5 answers made while AnkiGo was closed: 1 won, 0 caught, +25 coins" is the one long line with a second part: the part after
     * the colon becomes the small second line (as in the design page). Every other line is one text.
     */
    fun split(kind: ToastKind, text: String): Pair<String, String?> {
        if (kind == ToastKind.BOND) {
            val level = Regex("reached ♥([0-5])").find(text)?.groupValues?.get(1)
            if (level != null) return "Reached Bond level $level" to null
        }
        if (kind != ToastKind.CAUGHT_UP) return text to null
        val i = text.indexOf(": ")
        return if (i > 0 && i + 2 < text.length) text.substring(0, i) to text.substring(i + 2) else text to null
    }

    /** The text without the `{{name}}` marks (what a screen reader says). */
    fun plain(s: String) = s.replace("{{", "").replace("}}", "")
}

/**
 * The toasts in a row (pure; times are ms on the caller's clock). One shows at a time. A new one that equals the one showing, a waiting one,
 * or one that started within [DUP_MS] merges (nothing happens); otherwise it waits behind the showing one; it replaces a waiting one of the
 * same kind (the newest news of a kind wins); at most [MAX_PENDING] wait (the oldest goes). While others wait the showing one is shortened.
 */
class ToastQueue(private val maxPending: Int = MAX_PENDING) {
    /** [pop] false = it comes and goes without sliding. */
    data class Item(val kind: ToastKind, val text: String, val sub: String? = null, val pop: Boolean = true) {
        val key: String get() = kind.name + "|" + text
    }

    enum class Offer { SHOW, QUEUED, REPLACED, MERGED }

    var showing: Item? = null
        private set
    private val waiting = ArrayDeque<Item>()
    private val started = HashMap<String, Long>()

    val pendingCount: Int get() = waiting.size
    fun pending(): List<Item> = waiting.toList()

    /** Takes [item] in at [now]. [Offer.SHOW] = it shows right now (the caller presents it). */
    fun offer(item: Item, now: Long): Offer {
        val key = item.key
        if (showing?.key == key || waiting.any { it.key == key } || started[key]?.let { now - it < DUP_MS } == true) return Offer.MERGED
        if (showing == null) { start(item, now); return Offer.SHOW }
        val i = waiting.indexOfFirst { it.kind == item.kind }
        if (i >= 0) { waiting[i] = item; return Offer.REPLACED }
        waiting.addLast(item)
        if (waiting.size > maxPending) waiting.removeFirst()
        return Offer.QUEUED
    }

    /** The showing one is over: the next waiting one starts (returned), or null. */
    fun finish(now: Long): Item? {
        showing = null
        val next = waiting.removeFirstOrNull() ?: return null
        start(next, now)
        return next
    }

    fun clear() { showing = null; waiting.clear(); started.clear() }

    private fun start(item: Item, now: Long) {
        showing = item
        started[item.key] = now
        if (started.size > 24) started.entries.removeAll { now - it.value >= DUP_MS }
    }

    /** How long [item] stays, ms after it came in: about 2.5 s, a little longer for a long text, shorter while others wait. */
    fun holdMs(item: Item): Long = holdMs(item, waiting.isNotEmpty())

    companion object {
        const val DUP_MS = 4000L
        const val MAX_PENDING = 3
        const val BASE_MS = 2500L
        const val MAX_MS = 4500L
        const val BUSY_MIN_MS = 1500L
        private const val FREE_CHARS = 40
        private const val MS_PER_CHAR = 30L

        fun holdMs(item: Item, busy: Boolean): Long {
            val chars = ToastText.plain(item.text).length + (item.sub?.length ?: 0)
            val base = (BASE_MS + max(0, chars - FREE_CHARS) * MS_PER_CHAR).coerceAtMost(MAX_MS)
            return if (busy) (base * 2 / 3).coerceAtLeast(BUSY_MIN_MS) else base
        }
    }
}

/**
 * How big a toast is in a host (pure): by the arena's size ON SCREEN (dp). The Study panel and the Battle tab get the full card; a short
 * arena (the Study strip, a floating window, picture-in-picture) a smaller one-line card; a tiny arena the smallest, without the second
 * line. Texts never go under [PanelFit.MIN_SP] on screen: the card raises them with the panel's floor like every other text of the panel.
 */
object ToastLook {
    /** Sizes in design dp / sp (the panel scale is applied by the panel; [mainLines] = 1 cuts a long text with "…"). */
    data class Look(
        val tier: Tier, val textSp: Float, val subSp: Float, val iconDp: Float, val padStartDp: Float, val padEndDp: Float, val padVDp: Float,
        val gapDp: Float, val radiusDp: Float, val mainLines: Int, val showSub: Boolean, val maxWidthShare: Float, val marginDp: Float,
    )

    enum class Tier { FULL, COMPACT, TINY }

    /** The arena's on-screen height from which the full card shows, and the one from which the compact one does. */
    const val FULL_H = 200f
    const val COMPACT_H = 110f

    /** [arenaShown] false = no arena (the Study "mini" bar): the smallest card over the panel's top-right. */
    fun of(onScreenWdp: Float, onScreenHdp: Float, arenaShown: Boolean = true): Look {
        val tier = when {
            !arenaShown -> Tier.TINY
            onScreenHdp >= FULL_H && onScreenWdp >= 300f -> Tier.FULL
            onScreenHdp >= COMPACT_H && onScreenWdp >= 200f -> Tier.COMPACT
            else -> Tier.TINY
        }
        return when (tier) {
            Tier.FULL -> Look(tier, 12.5f, 11f, 16f, 10f, 12f, 7f, 7f, 12f, 2, true, 0.85f, 10f)
            Tier.COMPACT -> Look(tier, 11.5f, 10f, 14f, 8f, 10f, 5f, 6f, 10f, 3, true, 0.72f, 6f)
            Tier.TINY -> Look(tier, 10f, 9f, 12f, 6f, 8f, 3f, 5f, 8f, 1, false, 0.9f, 3f)
        }
    }
}

/** What a toast keeps clear of, in the arena's px; [safe] = the battle frame's safe area; [extra] = anything else (the "Paused" badge). */
internal class KeepOff(
    val ourCard: FloatSpot.Rect, val wildCard: FloatSpot.Rect, val ourArt: FloatSpot.Rect, val wildArt: FloatSpot.Rect,
    val safe: FrameSafe? = null, val extra: List<FloatSpot.Rect> = emptyList(),
)

/**
 * Where a toast sits (pure, px of the arena). First choice: the top-right corner, inside the battle frame's safe area, a margin in: above
 * your Pokémon, which the layouts leave free (the wild one stands top-right; your info box is under or beside your Pokémon). When
 * something is in the way (a layout with a card at the top-right) the toast takes the free place nearest to that corner; nothing free: the
 * place that covers the least. What it covers counts by weight: your info box most, then the wild one's, then the pictures (their
 * transparent edges do not count: only the inner part of a picture's box); a little over a picture's top is better than a spot far from the corner (the left side is the place, not the right).
 */
internal object ToastSpot {
    /**
     * [covered] = what the card covers, as weighted shares of its own area (0 = nothing; your info box counts 6 times, the wild box 3, a
     * picture 0.4 / 0.3); [cost] = that and the way from the corner, to compare two cards of different sizes.
     */
    class Spot(val left: Int, val top: Int, val coversOurCard: Boolean, val covered: Double, val cost: Double)

    private const val W_OUR_CARD = 6.0
    private const val W_EXTRA = 6.0
    private const val W_WILD_CARD = 3.0
    private const val W_OUR_ART = 0.4
    private const val W_WILD_ART = 0.3

    /** A picture's box is the drawn image's, with the empty edges of the sprite: only this inner share of each side counts as covered. */
    private const val ART_CORE = 0.10f

    /** One share of covering costs this many card heights of distance from the corner; sideways counts [SIDEWAYS] times as much as down (the right side is the place). */
    private const val COVER_COST = 100.0
    private const val SIDEWAYS = 8.0

    fun place(w: Int, h: Int, aw: Int, ah: Int, margin: Int, gap: Int, keep: KeepOff?): Spot {
        val safe = keep?.safe?.takeIf { !it.none }
        val x0 = max(0, aw - w - (safe?.edge ?: 0f).toInt() - margin)
        val y0 = (safe?.top ?: 0f).toInt() + margin
        fun fit(x: Int, y: Int): IntArray {
            if (safe != null) {
                val a = floatArrayOf(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat())
                safe.clearInto(a, aw.toFloat(), ah.toFloat())
                return intArrayOf(Math.round(a[0]), Math.round(a[1]))
            }
            return intArrayOf(x.coerceIn(0, max(0, aw - w - margin)), y.coerceIn(0, max(0, ah - h - margin)))
        }
        val first = fit(x0, y0)
        if (keep == null) return Spot(first[0], first[1], false, 0.0, 0.0)
        val weighted = listOf(keep.ourCard to W_OUR_CARD, keep.wildCard to W_WILD_CARD, core(keep.ourArt) to W_OUR_ART, core(keep.wildArt) to W_WILD_ART) +
            keep.extra.map { it to W_EXTRA }
        val own = max(1, w * h).toDouble()
        fun covered(x: Int, y: Int): Double {
            val r = FloatSpot.Rect(x, y, x + w, y + h)
            return weighted.sumOf { (k, wt) -> wt * area(r, k) / own }
        }
        fun cost(p: IntArray): Double {
            val r = FloatSpot.Rect(p[0], p[1], p[0] + w, p[1] + h)
            val chipPenalty = if (keep.extra.any { it.hits(r) }) 1_000_000_000.0 else 0.0
            return chipPenalty + COVER_COST * covered(p[0], p[1]) +
                (SIDEWAYS * abs(p[0] - first[0]) + 2.0 * abs(p[1] - first[1])) / max(1, h)
        }
        // a margin is kept on every side of the arena (a card never touches its edge)
        val xs = (listOf(x0) + weighted.flatMap { listOf(it.first.r + gap, it.first.l - gap - w) }).filter { it >= margin && it + w + margin <= aw }.distinct()
        val ys = (listOf(y0) + weighted.flatMap { listOf(it.first.b + gap, it.first.t - gap - h) }).filter { it >= margin && it + h + margin <= ah }.distinct()
        var best = first
        var bestCost = cost(first)
        for (x in xs) for (y in ys) {
            val p = fit(x, y)
            val c = cost(p)
            if (c < bestCost) { bestCost = c; best = p }
        }
        val r = FloatSpot.Rect(best[0], best[1], best[0] + w, best[1] + h)
        return Spot(best[0], best[1], area(r, keep.ourCard) > 0L, covered(best[0], best[1]), bestCost)
    }

    private fun core(r: FloatSpot.Rect): FloatSpot.Rect {
        val dx = ((r.r - r.l) * ART_CORE).toInt(); val dy = ((r.b - r.t) * ART_CORE).toInt()
        return FloatSpot.Rect(r.l + dx, r.t + dy, r.r - dx, r.b - dy)
    }

    private fun area(a: FloatSpot.Rect, b: FloatSpot.Rect): Long {
        val x = minOf(a.r, b.r) - maxOf(a.l, b.l)
        val y = minOf(a.b, b.b) - maxOf(a.t, b.t)
        return if (x > 0 && y > 0) x.toLong() * y else 0L
    }
}

/** One card: the kind's icon and the text (and a small second line). Looks like the design page's 257A. */
internal class ToastCard(ctx: Context, val item: ToastQueue.Item, look: ToastLook.Look, floor: Float) : LinearLayout(ctx) {
    val main: TextView
    val sub: TextView?

    /** True when the text is cut with "…" at the size it was measured at (a card must not be narrowed into that). */
    val truncated: Boolean get() = main.layout?.let { l -> (0 until l.lineCount).any { l.getEllipsisCount(it) > 0 } } == true

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        // under the panel's text floor the text is raised: the icon, the paddings and the corners grow with it
        val k = TextMath.at(look.textSp, floor) / look.textSp
        fun px(v: Float) = Ui.dp(ctx, v * k).toInt()
        setPadding(px(look.padStartDp), px(if (item.kind == ToastKind.BOND) 8f else look.padVDp), px(look.padEndDp), px(if (item.kind == ToastKind.BOND) 8f else look.padVDp))
        background = GradientDrawable().apply {
            setColor(ColorUtils.setAlphaComponent(Ui.c(ctx, R.color.ag_card), 0xF7))
            cornerRadius = Ui.dp(ctx, look.radiusDp * k)
            setStroke(Ui.dp(ctx, 1f).toInt().coerceAtLeast(1), ColorUtils.setAlphaComponent(Ui.c(ctx, R.color.ag_text), 0x1A))
        }
        elevation = Ui.dp(ctx, 3f)
        addView(ImageView(ctx).apply {
            if (item.kind == ToastKind.BOND) {
                com.husarp.ankigo.GameRepo.state?.main?.let { Ui.staticSprite(ctx, it.species, it.shiny, this) }
            } else setImageResource(item.kind.icon)
            if (item.kind != ToastKind.BOND) setColorFilter(Ui.c(ctx, if (item.kind.neutral) R.color.ag_sub1 else R.color.ag_mauve), android.graphics.PorterDuff.Mode.SRC_IN)
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LayoutParams(px(if (item.kind == ToastKind.BOND) 40f else look.iconDp), px(if (item.kind == ToastKind.BOND) 40f else look.iconDp)).also { it.marginEnd = px(look.gapDp) })
        val column = LinearLayout(ctx).apply { orientation = VERTICAL }
        main = Ui.text(ctx, if (item.kind == ToastKind.BOND) 13f else look.textSp, 800, R.color.ag_text).apply {
            TextFloor.set(this, if (item.kind == ToastKind.BOND) 13f else look.textSp, floor)
            maxLines = if (item.kind == ToastKind.BOND) 2 else look.mainLines
            ellipsize = TextUtils.TruncateAt.END
            setLineSpacing(0f, 1.2f)
            text = PanelText.rich(ctx, item.text)
        }
        column.addView(main, LayoutParams(-2, -2))
        sub = if (look.showSub && !item.sub.isNullOrBlank()) Ui.text(ctx, look.subSp, 700, R.color.ag_sub0).apply {
            TextFloor.set(this, look.subSp, floor)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            text = PanelText.rich(ctx, item.sub!!)
        } else null
        sub?.let { column.addView(it, LayoutParams(-2, -2).also { lp -> lp.topMargin = px(1f) }) }
        addView(column, LayoutParams(-2, -2))
        isClickable = true
        contentDescription = ToastText.plain(item.text) + (item.sub?.let { ". " + ToastText.plain(it) } ?: "")
    }
}

/**
 * The toast layer of the battle panel: a full-size layer over the panel (the arena's top-right is its place), above the arena's frame.
 * It takes no touches except a card's own (a tap closes it). One card at a time ([ToastQueue]); it slides in from the right edge, holds,
 * slides out; reduced motion (or `pop` false) = it comes and goes without sliding. Main thread only.
 */
internal class ArenaToastLayer(ctx: Context) : FrameLayout(ctx) {
    /** The panel's text floor and scale (virtual px on screen = px x [panelScale]). */
    var floorSp = 0f
    var panelScale = 1f

    /** The arena in this layer's px (null / empty = no arena on screen: the toast sits over the panel's top-right, smallest form). */
    var arenaRect: () -> Rect? = { null }

    /** What the toast keeps clear of, in the ARENA's px (null = nothing known). */
    var keepOff: () -> KeepOff? = { null }

    /** Told once with the plain text of a card that comes in (TalkBack, when "Read battle log" is on). */
    var announce: (String) -> Unit = {}

    internal var clock: () -> Long = { SystemClock.uptimeMillis() }

    private val queue = ToastQueue()
    private var card: ToastCard? = null
    private var cardLeft = 0
    private var shownAt = 0L
    private var slideMs = 0L
    private var hideAt = 0L
    private var leaving = false
    private var placedKey: List<Int> = emptyList()
    private var waitingForLayout = false
    private var waitingSince = 0L
    private val hideRun = Runnable { slideOut() }

    /** What the tests look at. */
    internal val shown: ToastQueue.Item? get() = if (card != null) queue.showing else null
    internal val shownCard: ToastCard? get() = card
    internal val pendingCount: Int get() = queue.pendingCount
    internal fun pending(): List<ToastQueue.Item> = queue.pending()

    /** Raises a toast; returns what the queue did with it. */
    fun show(kind: ToastKind, text: String, sub: String? = null, pop: Boolean = true): ToastQueue.Offer {
        val item = ToastQueue.Item(kind, text, sub, pop)
        val offer = queue.offer(item, clock())
        when (offer) {
            ToastQueue.Offer.SHOW -> present(item, resume = false)
            ToastQueue.Offer.QUEUED, ToastQueue.Offer.REPLACED -> shorten()
            ToastQueue.Offer.MERGED -> {}
        }
        return offer
    }

    /** Ends the slide of the showing card at once (the panel skips its animations); the card stays until its time is over. */
    fun settle() {
        val c = card ?: return
        c.animate().cancel()
        if (leaving) { finish(); return }   // it was on its way out
        c.translationX = 0f; c.alpha = 1f
    }

    /** Forgets everything (the panel left the screen). */
    fun clear() {
        removeCallbacks(hideRun)
        card?.let { it.animate().cancel(); removeView(it) }
        card = null
        leaving = false
        waitingForLayout = false
        queue.clear()
        onChanged()
    }

    /** Closes the showing card now (a tap): it slides out like at the end of its time. */
    internal fun dismiss() { removeCallbacks(hideRun); slideOut() }

    /** The showing card is over at once, no sliding (tests, a skipped batch); the next one starts. */
    internal fun finishNow() { removeCallbacks(hideRun); finish() }

    private fun still(item: ToastQueue.Item) =
        !item.pop || !ValueAnimator.areAnimatorsEnabled() || context.getSharedPreferences("study", 0).getBoolean("reduceMotion", false)

    internal var blocked: () -> Boolean = { false }
    internal val hasMessages: Boolean get() = queue.showing != null

    internal fun resumeBlocked() {
        if (card == null) queue.showing?.let { present(it, resume = false) }
    }

    private fun present(item: ToastQueue.Item, resume: Boolean) {
        if (blocked()) return
        removeCallbacks(hideRun)
        if (waitingForLayout && clock() - waitingSince > 5000L) { clear(); return }
        if (width <= 0 || height <= 0) {
            if (!waitingForLayout) waitingSince = clock()
            waitingForLayout = true
            return
        }   // the first layout presents it (onLayout)
        waitingForLayout = false
        val d = resources.displayMetrics.density
        val arena = arenaRect()?.takeIf { it.width() > 0 && it.height() > 0 }
        val area = arena ?: Rect(0, 0, width, height)
        val look = ToastLook.of(area.width() * panelScale / d, area.height() * panelScale / d, arena != null)
        val margin = Ui.dp(context, look.marginDp).toInt()
        val maxW = max(1, ((area.width() - 2 * margin) * look.maxWidthShare).toInt())
        val keep = if (arena != null) keepOff() else null
        placedKey = geoKey(arena, keep)
        val gap = Ui.dp(context, 4f).toInt()
        fun made(atMost: Int) = ToastCard(context, item, look, floorSp).also {
            it.measure(MeasureSpec.makeMeasureSpec(atMost, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
        }
        fun spotOf(c: ToastCard) = ToastSpot.place(c.measuredWidth, c.measuredHeight, area.width(), area.height(), margin, gap, keep)
        var c = made(maxW)
        var spot = spotOf(c)
        if (look.mainLines > 1 && spot.covered > 0.0) {
            // something is in the way: a narrower card (its text on more lines, never cut) may fit where the wide one does not
            val natural = c.measuredWidth
            for (share in floatArrayOf(0.85f, 0.7f, 0.55f)) {
                val narrow = made(max(1, (natural * share).toInt()))
                if (narrow.measuredWidth >= c.measuredWidth || (narrow.truncated && !c.truncated)) continue
                val s2 = spotOf(narrow)
                if (s2.cost < spot.cost - 1.0) { c = narrow; spot = s2 }
            }
        }
        // If no free place exists in a tiny window, wait for a layout with room for the chips.
        val chipRect = FloatSpot.Rect(spot.left, spot.top, spot.left + c.measuredWidth, spot.top + c.measuredHeight)
        if (keep?.extra?.any { it.hits(chipRect) } == true) {
            card?.let { it.animate().cancel(); removeView(it) }; card = null
            waitingForLayout = true
            waitingSince = clock()
            return
        }
        card?.let { it.animate().cancel(); removeView(it) }
        cardLeft = area.left + spot.left
        val top = area.top + spot.top
        addView(c, LayoutParams(c.measuredWidth, c.measuredHeight, Gravity.TOP or Gravity.START).also { it.leftMargin = cardLeft; it.topMargin = top })
        c.layout(cardLeft, top, cardLeft + c.measuredWidth, top + c.measuredHeight)   // we may be inside a layout pass: this card would wait for the next one
        c.setOnClickListener { dismiss() }
        card = c
        onChanged()
        leaving = false
        val now = clock()
        if (!resume) {
            val slide = !still(item)
            shownAt = now; slideMs = if (slide) IN_MS else 0L
            hideAt = now + slideMs + queue.holdMs(item)
            if (slide) {
                c.translationX = (width - cardLeft).toFloat()
                c.animate().translationX(0f).setDuration(IN_MS).setInterpolator(DecelerateInterpolator()).start()
            }
        }
        postDelayed(hideRun, max(MIN_LEFT_MS, hideAt - now))
        if (!resume) announce(contentOf(item))
    }

    /** What the place depends on: the arena, the cards, the pictures, the frame, the panel's scale and floor (a layout pass that changes any of it places the card again). */
    private fun geoKey(arena: Rect?, keep: KeepOff?): List<Int> {
        if (arena == null) return listOf(width, height, panelScale.toBits(), floorSp.toBits())
        val k = ArrayList<Int>(24)
        k += listOf(arena.left, arena.top, arena.right, arena.bottom, panelScale.toBits(), floorSp.toBits())
        keep?.let { for (r in listOf(it.ourCard, it.wildCard, it.ourArt, it.wildArt) + it.extra) { k += r.l; k += r.t; k += r.r; k += r.b } }
        keep?.safe?.let { k += listOf(it.edge.toBits(), it.top.toBits(), it.corner.toBits()) }
        return k
    }

    private fun contentOf(item: ToastQueue.Item) = ToastText.plain(item.text) + (item.sub?.let { ". " + ToastText.plain(it) } ?: "")

    /** Others wait: the showing card goes sooner. */
    private fun shorten() {
        val item = queue.showing ?: return
        if (card == null) return
        if (leaving) return
        val now = clock()
        val sooner = max(shownAt + slideMs + ToastQueue.holdMs(item, true), now + MIN_LEFT_MS)
        if (sooner < hideAt) { hideAt = sooner; removeCallbacks(hideRun); postDelayed(hideRun, hideAt - now) }
    }

    private fun slideOut() {
        val c = card ?: return
        val item = queue.showing
        if (leaving) return
        leaving = true
        c.isClickable = false
        if (item == null || still(item) || !c.isAttachedToWindow) { finish(); return }
        c.animate().translationX((width - cardLeft).toFloat()).alpha(0f).setDuration(OUT_MS).setInterpolator(AccelerateInterpolator())
            .withEndAction { finish() }.start()
    }

    var onChanged: () -> Unit = {}

    private fun finish() {
        card?.let { it.animate().cancel(); removeView(it) }
        card = null
        leaving = false
        queue.finish(clock())?.let { present(it, resume = false) }
        onChanged()
    }

    internal fun reposition() {
        val item = queue.showing ?: return
        if (card == null || leaving) return
        val arena = arenaRect()?.takeIf { it.width() > 0 && it.height() > 0 }
        if (geoKey(arena, if (arena != null) keepOff() else null) != placedKey) present(item, resume = true)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        val item = queue.showing ?: return
        if (waitingForLayout) { present(item, resume = false); return }
        if (card == null || leaving) return
        // the panel changed size, or the arena finished its own layout after the card came (a first pass): place it again for the picture as it is
        val arena = arenaRect()?.takeIf { it.width() > 0 && it.height() > 0 }
        if (geoKey(arena, if (arena != null) keepOff() else null) != placedKey) present(item, resume = true)
    }

    override fun onDetachedFromWindow() {
        clear()
        super.onDetachedFromWindow()
    }

    companion object {
        const val IN_MS = 260L
        const val OUT_MS = 240L
        private const val MIN_LEFT_MS = 400L
    }
}
