package com.husarp.ankigo.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.husarp.ankigo.R
import com.husarp.ankigo.game.GameEvent
import kotlin.math.max

/**
 * The bar under the battle picture (Round 9 D3-E): separate rounded segments `[ my last move | dots | their last move ]` (or
 * `[ my move | their move | dots ]`), the halves like the Pokémon-details move row. Tapping a half pops [MoveBubble] over the arena
 * (the bar never changes height). The bar keeps the log slot's size in every host; a line about the whole battle ([showLine])
 * replaces the segments across the full width until the next move. [ActionBarModel] holds what the halves show.
 *
 * It is the `logBox` of [BattlePanelView]; the panel lays it out at the size the old log card had.
 */
internal class ActionBarView(ctx: Context) : FrameLayout(ctx) {
    val model = ActionBarModel()

    private val mineHalf = HalfView(ctx, BarSide.MINE)
    private val theirsHalf = HalfView(ctx, BarSide.THEIRS)
    private val dots = DotsSegment(ctx)
    private val row = SegmentRow(ctx, mineHalf, theirsHalf, dots)
    private val line = LineBox(ctx)

    private var wholeLine = false
    private var pausedLine = false

    /** The panel's text floor ([PanelFit]); texts made later start at it. */
    var floorSp = 0f

    /** The charge dots show (the Study screen); they sit by [dotsPlace]. */
    var dotsOn = false
        set(v) { field = v; row.dotsOn = v; line.pips.visibility = if (v) VISIBLE else GONE }

    var dotsPlace = DotsPlace.MIDDLE
        set(v) { field = v; row.dotsPlace = v }

    /** True while the bar sits edge to edge (the Study strip): the segments get an inset of their own. */
    val flat: Boolean get() = line.flat

    init {
        addView(row, LayoutParams(-1, -1))
        addView(line, LayoutParams(-1, -1))
        line.visibility = GONE
        line.pips.visibility = GONE
        mineHalf.setOnClickListener { toggleBubble(BarSide.MINE) }
        theirsHalf.setOnClickListener { toggleBubble(BarSide.THEIRS) }
        mineHalf.bind(model.mine, floorSp); theirsHalf.bind(model.theirs, floorSp)
    }

    // ---------------------------------------------------------------- what is shown

    /** Takes one played event in: the halves follow the moves of the fight. */
    fun observe(e: GameEvent) {
        val changed = model.observe(e)
        if (changed.isEmpty()) return
        if (wholeLine) { wholeLine = false; sync() }
        for (s in changed) {
            if (bubbleSide == s) closeBubble()
            val h = half(s)
            val fresh = h.bind(model.half(s), floorSp)
            if (fresh && model.half(s) !is HalfState.Empty) h.pop(still())
        }
    }

    private fun half(s: BarSide) = if (s == BarSide.MINE) mineHalf else theirsHalf

    /** Shows a line about the whole battle across the full bar, until the next move arrives. */
    fun showLine(kind: LogKind, pill: String, text: String, pop: Boolean) {
        line.show(kind, pill, text, pop && isAttachedToWindow && !still(), floorSp)
        wholeLine = true
        sync()
    }

    /** The "Paused" pill replaces the segments with the line while paused in a small window or the Study strip. */
    fun setPaused(on: Boolean) {
        pausedLine = on
        line.setPaused(on)
        sync()
    }

    private fun sync() {
        val lineOn = wholeLine || pausedLine
        line.visibility = if (lineOn) VISIBLE else GONE
        row.visibility = if (lineOn) INVISIBLE else VISIBLE
        if (lineOn) closeBubble()
    }

    /** The line card's look for the host: [small] = one short line, [legacy] = the study strip, [flat] = edge to edge (Study, short). */
    fun setLook(small: Boolean, legacy: Boolean, flat: Boolean) {
        line.setLook(small, legacy, flat, floorSp)
        row.flat = flat
    }

    /** The dots: [goal] pips, [buttons] = the answer button of each filled one (0 = counted, button unknown). */
    fun setCharge(goal: Int, buttons: List<Int>) {
        dots.pips.render(goal, buttons)
        line.pips.render(goal, buttons)
        dots.contentDescription = "Battle charge ${buttons.size} of $goal"
        line.pips.contentDescription = dots.contentDescription
    }

    fun applyLiveRegion() {
        line.text.accessibilityLiveRegion =
            if (context.getSharedPreferences("study", 0).getBoolean("speakBattle", true)) ACCESSIBILITY_LIVE_REGION_POLITE else ACCESSIBILITY_LIVE_REGION_NONE
    }

    /** A battle line the bar does not show (a move, or something about one side): spoken only, when Settings → Read battle log says so. */
    fun announce(text: String) {
        if (!context.getSharedPreferences("study", 0).getBoolean("speakBattle", true) || !isAttachedToWindow) return
        announceForAccessibility(text.replace("{{", "").replace("}}", ""))
    }

    fun cancelAnimations() {
        line.cancelAnimations()
        mineHalf.animate().cancel(); theirsHalf.animate().cancel()
        mineHalf.scaleX = 1f; mineHalf.scaleY = 1f; mineHalf.alpha = 1f
        theirsHalf.scaleX = 1f; theirsHalf.scaleY = 1f; theirsHalf.alpha = 1f
    }

    private fun still() = !ValueAnimator.areAnimatorsEnabled() || context.getSharedPreferences("study", 0).getBoolean("reduceMotion", false)

    // ---------------------------------------------------------------- size

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // a short bar (one-line hosts, under ~40 dp) shows one line of text in the line card
        val h = MeasureSpec.getSize(heightMeasureSpec) / resources.displayMetrics.density
        val lines = if (h < 40f) 1 else 2
        if (line.text.maxLines != lines) line.text.maxLines = lines   // setMaxLines asks for a layout even when unchanged
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        closeBubble()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility != VISIBLE) closeBubble()   // the bar or something above it went away (only told while attached)
    }

    override fun setVisibility(visibility: Int) {
        super.setVisibility(visibility)
        if (visibility != VISIBLE) closeBubble()
    }

    override fun onDetachedFromWindow() {
        closeBubble()
        super.onDetachedFromWindow()
    }

    // ---------------------------------------------------------------- the bubble

    private var layer: FrameLayout? = null
    private var bubble: MoveBubble? = null
    private var bubbleSide: BarSide? = null

    /** The layer the bubble is added to: a full-size child of the panel's scaled container, above everything else in it. */
    fun attachBubbleLayer(l: FrameLayout) { layer = l }

    val bubbleOpen: Boolean get() = bubbleSide != null

    /** The bubble's look ([MoveBubbleLook]); one value to change when another mockup look is wanted. */
    internal var bubbleVariant = MoveBubbleLook.CURRENT

    // what the tests look at
    internal fun openBubble(side: BarSide) { if (bubbleSide != side) toggleBubble(side) }
    internal fun halfView(side: BarSide): View = half(side)
    internal fun bubbleViews(): Int = (0 until (layer?.childCount ?: 0)).count { layer?.getChildAt(it) is MoveBubble }
    internal fun lineShown(): Boolean = line.visibility == VISIBLE
    internal fun segmentOrder(): List<String> = (0 until row.childCount).map { when (row.getChildAt(it)) { mineHalf -> "mine"; theirsHalf -> "theirs"; else -> "dots" } }

    private fun toggleBubble(side: BarSide) {
        if (bubbleSide == side) { closeBubble(); return }
        val move = (model.half(side) as? HalfState.Moved)?.move ?: return
        val layer = layer ?: return
        val root = layer.parent as? View ?: return
        closeBubble()
        val b = MoveBubble(context, MoveBubbleLook.create(context, bubbleVariant))
        b.bind(ActionBarModel.bubble(move))
        TextFloor.apply(b, floorSp)
        val d = resources.displayMetrics.density
        val width = if (layer.width > 0) layer.width else root.width
        b.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
        val h = half(side)
        val at = offsetIn(root, h)
        val barTop = offsetIn(root, this)[1]
        if (barTop < (40f * d).toInt()) return   // the bar is (nearly) all there is of the panel (the Study "mini" height): no room for a bubble above it
        val centre = at[0] + h.width / 2f
        val left = (centre - b.measuredWidth / 2f).coerceIn(0f, max(0f, (width - b.measuredWidth).toFloat())).toInt()
        // the body ends 9 dp above the bar; its 7 dp tail hangs into that gap (the view ends 2 dp above the bar)
        val top = max(0, barTop - b.measuredHeight - (2f * d).toInt())
        b.tailX = centre - left
        layer.addView(b, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START).also { it.leftMargin = left; it.topMargin = top })
        b.popIn(still())
        h.setOpen(true)
        bubble = b; bubbleSide = side
    }

    fun closeBubble() {
        val b = bubble ?: return
        b.animate().cancel()
        (b.parent as? ViewGroup)?.removeView(b)
        bubble = null
        bubbleSide?.let { half(it).setOpen(false) }
        bubbleSide = null
    }

    /** A touch started somewhere in the panel ([panel] is its root): a tap outside the bubble and the two halves closes the bubble. */
    fun onPanelDown(panel: View, ev: MotionEvent) {
        val b = bubble ?: return
        if (hits(panel, b, ev.x, ev.y) || hits(panel, mineHalf, ev.x, ev.y) || hits(panel, theirsHalf, ev.x, ev.y)) return
        closeBubble()
    }

    private fun offsetIn(root: View, v: View): IntArray {
        var x = 0; var y = 0
        var c: View? = v
        while (c != null && c !== root) { x += c.left; y += c.top; c = c.parent as? View }
        return intArrayOf(x, y)
    }

    /** True when the point ([x], [y] in [root]'s space) is inside [target], through the scale of the panel's container. */
    private fun hits(root: View, target: View, x: Float, y: Float): Boolean {
        val chain = ArrayList<View>()
        var c: View? = target
        while (c != null && c !== root) { chain += c; c = c.parent as? View }
        if (c !== root || target.visibility != VISIBLE) return false
        val pt = floatArrayOf(x, y)
        val inv = Matrix()
        for (i in chain.indices.reversed()) {
            val v = chain[i]
            val p = v.parent as View
            pt[0] += p.scrollX - v.left; pt[1] += p.scrollY - v.top
            if (!v.matrix.isIdentity) { v.matrix.invert(inv); inv.mapPoints(pt) }
        }
        return pt[0] >= 0f && pt[1] >= 0f && pt[0] < target.width && pt[1] < target.height
    }

    // ================================================================ parts

    /** The two halves and the dots, in a row with 4 dp gaps (the dots at the setting's place). */
    private class SegmentRow(ctx: Context, private val mine: HalfView, private val theirs: HalfView, private val dots: DotsSegment) : ViewGroup(ctx) {
        private val gap = Ui.dp(ctx, 4)
        var dotsOn = false
            set(v) { field = v; order() }
        var dotsPlace = DotsPlace.MIDDLE
            set(v) { field = v; order() }
        var flat = false
            set(v) { field = v; setPadding(Ui.dp(context, if (v) 6 else 0), Ui.dp(context, if (v) 3 else 0), Ui.dp(context, if (v) 6 else 0), Ui.dp(context, if (v) 3 else 0)) }

        init { order() }

        private fun order() {
            removeAllViews()
            val kids = when {
                !dotsOn -> listOf(mine, theirs)
                dotsPlace == DotsPlace.MIDDLE -> listOf(mine, dots, theirs)
                else -> listOf(mine, theirs, dots)
            }
            kids.forEach { addView(it) }
            requestLayout()
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val w = MeasureSpec.getSize(widthMeasureSpec)
            val h = MeasureSpec.getSize(heightMeasureSpec)
            val innerW = w - paddingLeft - paddingRight
            val innerH = h - paddingTop - paddingBottom
            var used = 0
            var n = 0
            for (i in 0 until childCount) {
                val k = getChildAt(i)
                if (k === dots) {
                    k.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), MeasureSpec.makeMeasureSpec(innerH, MeasureSpec.EXACTLY))
                    used += k.measuredWidth
                }
                n++
            }
            val halfW = max(0, (innerW - used - gap * (n - 1)) / 2)
            for (i in 0 until childCount) {
                val k = getChildAt(i)
                if (k !== dots) k.measure(MeasureSpec.makeMeasureSpec(halfW, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(innerH, MeasureSpec.EXACTLY))
            }
            setMeasuredDimension(w, h)
        }

        override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
            var x = paddingLeft
            for (i in 0 until childCount) {
                val k = getChildAt(i)
                k.layout(x, paddingTop, x + k.measuredWidth, paddingTop + k.measuredHeight)
                x += k.measuredWidth + gap
            }
        }
    }

    /** One move: kind tile, name, type chip and kind word, like a row of the Pokémon details' move list; mine at the left edge, theirs at the right. */
    private class HalfView(ctx: Context, private val side: BarSide) : LinearLayout(ctx) {
        private var state: HalfState = HalfState.Empty
        private var floor = 0f
        private var oneLine = false
        private var tileDp = 0
        private var tile: View? = null
        private var kind: View? = null
        private var key = ""
        private val bg = GradientDrawable().apply { cornerRadius = Ui.dp(ctx, 12f); setColor(Ui.c(ctx, R.color.ag_card)) }

        init {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = bg
            isClickable = false
            clipChildren = false
        }

        /** Shows [s]; true when it looks different from before (the caller pops it). */
        fun bind(s: HalfState, floorSp: Float): Boolean {
            val k = keyOf(s)
            val changed = k != key
            state = s; floor = floorSp; key = k
            build()
            return changed
        }

        private fun keyOf(s: HalfState) = when (s) {
            is HalfState.Empty -> "-"
            is HalfState.Note -> "n:" + s.text
            is HalfState.Moved -> "m:${s.move.move}:${s.move.damage}:${s.move.missed}:${s.move.type}:${s.move.category}"
        }

        fun setOpen(open: Boolean) {
            bg.setStroke(if (open) Ui.dp(context, 1.5f).toInt() else 0, Ui.c(context, R.color.ag_s1))
        }

        fun pop(still: Boolean) {
            animate().cancel()
            if (still) { scaleX = 1f; scaleY = 1f; alpha = 1f; return }
            scaleX = 0.94f; scaleY = 0.94f; alpha = 0.6f
            animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(200).start()
        }

        private fun build() {
            removeAllViews()
            tile = null; kind = null
            val s = state
            val mine = side == BarSide.MINE
            val padV = if (oneLine) 3 else 6
            val side = Ui.dp(context, if (oneLine) 8 else 10)
            setPadding(if (paddingLeft > 0) paddingLeft else side, Ui.dp(context, padV), if (paddingRight > 0) paddingRight else side, Ui.dp(context, padV))
            isClickable = s is HalfState.Moved
            when (s) {
                is HalfState.Moved -> {
                    val m = s.move
                    val t = DetailSections.kindTile(context, m.category, tileDp).also { tile = it }
                    val txt = LinearLayout(context).apply {
                        orientation = VERTICAL
                        gravity = if (mine) Gravity.START else Gravity.END
                        addView(Ui.text(context, 13.5f, 800, if (m.missed) R.color.ag_sub0 else R.color.ag_text).apply {
                            text = m.move
                            maxLines = 1
                            ellipsize = TextUtils.TruncateAt.END
                            if (m.missed) paintFlags = paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                        }, LayoutParams(-2, -2))
                        if (!oneLine) addView(subRow(m), LayoutParams(-2, -2).also { it.topMargin = Ui.dp(context, 3) })
                    }
                    val gap = Ui.dp(context, 8)
                    if (mine) {
                        addView(t, LayoutParams(Ui.dp(context, tileDp), Ui.dp(context, tileDp)).also { it.marginEnd = gap })
                        addView(txt, LayoutParams(0, -2, 1f))
                    } else {
                        addView(txt, LayoutParams(0, -2, 1f))
                        addView(t, LayoutParams(Ui.dp(context, tileDp), Ui.dp(context, tileDp)).also { it.marginStart = gap })
                    }
                    contentDescription = "${if (mine) "Your" else "Wild"} last move: ${m.move}" +
                        (if (m.missed) ", missed" else "") + ". ${DetailSections.kindName(m.category)}" +
                        (m.type?.let { ", ${it.label} type" } ?: "") + ". Tap for details"
                }
                else -> {
                    val text = if (s is HalfState.Note) s.text else "No move yet"
                    addView(Ui.text(context, 12f, 700, R.color.ag_sub0).apply {
                        this.text = text
                        maxLines = 2
                        ellipsize = TextUtils.TruncateAt.END
                        gravity = if (mine) Gravity.START or Gravity.CENTER_VERTICAL else Gravity.END or Gravity.CENTER_VERTICAL
                    }, LayoutParams(0, -2, 1f))
                    contentDescription = "${if (mine) "Your" else "Wild"} Pokémon: $text"
                }
            }
            TextFloor.apply(this, floor)
        }

        /** The type chip and the kind word; theirs is mirrored (the chip stays at the outer edge). */
        private fun subRow(m: BarMove): LinearLayout = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val chip = m.type?.let { Ui.typeChip(context, it) }
            val word = Ui.text(context, 10f, 800, R.color.ag_sub0).apply { text = DetailSections.kindName(m.category); maxLines = 1 }.also { kind = it }
            val gap = Ui.dp(context, 5)
            if (side == BarSide.MINE) {
                chip?.let { addView(it, LayoutParams(-2, -2)) }
                addView(word, LayoutParams(-2, -2).also { if (chip != null) it.marginStart = gap })
            } else {
                addView(word, LayoutParams(-2, -2).also { if (chip != null) it.marginEnd = gap })
                chip?.let { addView(it, LayoutParams(-2, -2)) }
            }
        }

        /** The narrow-half rules (like the mockup's container queries): under 150 dp the kind word goes, under 118 dp the tile goes too. */
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val d = resources.displayMetrics.density
            val hDp = MeasureSpec.getSize(heightMeasureSpec) / d
            val one = hDp < 40f
            val size = if (one) (hDp - 8f).toInt().coerceIn(16, 22) else 26
            if (one != oneLine || size != tileDp) { oneLine = one; tileDp = size; build() }
            val wDp = MeasureSpec.getSize(widthMeasureSpec) / d
            val side = Ui.dp(context, if (wDp < 118f) 6 else if (one) 8 else 10)   // a narrow half gives its name more room
            if (paddingLeft != side) setPadding(side, paddingTop, side, paddingBottom)
            kind?.visibility = if (wDp < 150f) GONE else VISIBLE
            tile?.visibility = if (wDp < 118f) GONE else VISIBLE
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }

    /** The charge dots on a segment of their own: 4 dp gaps, at least 44 dp wide. */
    private class DotsSegment(ctx: Context) : LinearLayout(ctx) {
        val pips = PipRow(ctx)

        init {
            gravity = Gravity.CENTER
            minimumWidth = Ui.dp(ctx, 44)
            setPadding(Ui.dp(ctx, 8), 0, Ui.dp(ctx, 8), 0)
            background = Ui.rounded(Ui.c(ctx, R.color.ag_card), 12, ctx)   // the same card colour as the two move halves (mantle read as black in dark)
            addView(pips, LayoutParams(-2, -2))
        }
    }

    /** The turn's charge pips, 10 dp each; the colour is the answer button (red again, peach hard, green good, blue easy). */
    private class PipRow(ctx: Context) : LinearLayout(ctx) {
        private val pips = List(4) { View(ctx) }

        init {
            gravity = Gravity.CENTER_VERTICAL
            pips.forEachIndexed { i, p -> addView(p, LayoutParams(Ui.dp(ctx, 10), Ui.dp(ctx, 10)).also { if (i > 0) it.marginStart = Ui.dp(ctx, 4) }) }
        }

        fun render(goal: Int, buttons: List<Int>) {
            pips.forEachIndexed { i, v ->
                v.visibility = if (i < goal) VISIBLE else GONE
                val button = buttons.getOrNull(i)
                val tone = when (button) { 1 -> R.color.ag_red; 2 -> R.color.ag_peach; 3 -> R.color.ag_green; 4 -> R.color.ag_blue; 0 -> R.color.ag_ov; else -> R.color.ag_s0 }
                v.background = Ui.rounded(Ui.c(context, tone), 5, context).apply {
                    if (button == null) setStroke(Ui.dp(context, 1.5f).toInt(), Ui.c(context, R.color.ag_s1))
                }
            }
        }
    }

    /** Today's plain one-card line (coloured pill + text, the accent strip at its left): for lines about the whole battle. */
    private class LineBox(ctx: Context) : LinearLayout(ctx) {
        private val bg = PanelAccentCard(Ui.c(ctx, R.color.ag_card), Ui.c(ctx, R.color.ag_s1), Ui.dp(ctx, 12f), Ui.dp(ctx, 4f))
        val text = Ui.text(ctx, 13f, 700, R.color.ag_text).apply {
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            setLineSpacing(0f, 1.2f)
        }
        val pips = PipRow(ctx)
        private val pausedPill = Ui.text(ctx, 10f, 900, R.color.ag_crust).apply {
            this.text = "Paused"
            textSize = 9.5f
            setPadding(Ui.dp(ctx, 7), Ui.dp(ctx, 1), Ui.dp(ctx, 7), Ui.dp(ctx, 1))
            background = Ui.rounded(Ui.c(ctx, R.color.ag_peach), 7, ctx)
            visibility = GONE
        }
        private var pill: TextView? = null
        private var accentAnim: ValueAnimator? = null
        private var small = false
        private var legacy = false
        var flat = false
            private set
        private var size = 13f
        private var paused = false

        init {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = bg
            setPadding(Ui.dp(ctx, 14), Ui.dp(ctx, 8), Ui.dp(ctx, 10), Ui.dp(ctx, 8))   // 4 dp strip + the mockup's 10 dp
            addView(pausedPill, LayoutParams(-2, -2).also { it.marginEnd = Ui.dp(ctx, 6) })
            addView(text, LayoutParams(0, -2, 1f))
            addView(pips, LayoutParams(-2, -2).also { it.marginStart = Ui.dp(ctx, 7) })
        }

        fun setLook(small: Boolean, legacy: Boolean, flat: Boolean, floor: Float) {
            this.small = small; this.legacy = legacy; this.flat = flat
            when {
                flat -> setPadding(Ui.dp(context, 12), Ui.dp(context, 5), Ui.dp(context, 10), Ui.dp(context, 5))
                legacy -> setPadding(Ui.dp(context, 10), Ui.dp(context, 2), Ui.dp(context, 8), Ui.dp(context, 2))
                else -> setPadding(Ui.dp(context, 14), Ui.dp(context, if (small) 4 else 8), Ui.dp(context, 10), Ui.dp(context, if (small) 4 else 8))
            }
            bg.cornerRadius = if (flat) 0f else Ui.dp(context, 12f)
            TextFloor.set(text, if (legacy) 12f else size, floor)
            pill?.let { stylePill(it, floor) }
        }

        fun setPaused(on: Boolean) {
            paused = on
            pausedPill.visibility = if (on) VISIBLE else GONE
            pill?.visibility = if (on) GONE else VISIBLE
        }

        private fun stylePill(p: TextView, floor: Float) {
            TextFloor.apply(p, floor)
            if (legacy) {
                (p.background as? GradientDrawable)?.cornerRadius = Ui.dp(context, 6f)
                TextFloor.set(p, 9.5f, 0f)
                p.setPadding(Ui.dp(context, 6), Ui.dp(context, 2), Ui.dp(context, 6), Ui.dp(context, 2))
                (p.layoutParams as? LayoutParams)?.marginEnd = Ui.dp(context, 7)
            }
        }

        fun show(kind: LogKind, pillText: String, line: String, pop: Boolean, floor: Float) {
            pill?.let { removeView(it) }
            val p = Ui.pill(context, pillText, kind.tone)
            addView(p, 1, LayoutParams(-2, -2).also { it.marginEnd = Ui.dp(context, 8) })   // after the "Paused" pill
            p.visibility = if (paused) GONE else VISIBLE
            pill = p
            stylePill(p, floor)
            size = if (line.length > 64) 12f else 13f
            TextFloor.set(text, if (legacy) 12f else size, floor)
            text.text = PanelText.rich(context, line)
            val to = Ui.c(context, kind.border)
            accentAnim?.cancel()
            if (pop) {
                val from = bg.accent
                accentAnim = ValueAnimator.ofObject(android.animation.ArgbEvaluator(), from, to).setDuration(200).also { a ->
                    a.addUpdateListener { bg.accent = it.animatedValue as Int }
                    a.start()
                }
                animate().cancel()
                scaleX = 0.97f; scaleY = 0.97f; alpha = 0.6f
                animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(250).start()
            } else bg.accent = to
        }

        fun cancelAnimations() {
            accentAnim?.cancel()
            animate().cancel()
            scaleX = 1f; scaleY = 1f; alpha = 1f
        }
    }
}
