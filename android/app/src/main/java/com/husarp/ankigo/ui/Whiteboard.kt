// SPDX-License-Identifier: GPL-3.0-or-later
// Stroke smoothing adapted from AnkiDroid Whiteboard.kt:
// https://github.com/ankidroid/Anki-Android/blob/main/AnkiDroid/src/main/java/com/ichi2/anki/Whiteboard.kt
// Copyright (c) 2009 Andrew <andrewdubya@gmail.com>
// Copyright (c) 2009 Nicolas Raoul <nicolas.raoul@gmail.com>
// Copyright (c) 2009 Edu Zamora <edu.zasu@gmail.com>
// Copyright (c) 2021 Nicolai Weitkemper <kontakt@nicolaiweitkemper.de>
package com.husarp.ankigo.ui

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import com.husarp.ankigo.R
import kotlin.math.abs

/** Transparent card-only drawing surface. Erasing is replayed on a separate canvas layer. */
internal class Whiteboard(ctx: Context) : View(ctx) {
    init { contentDescription = "Whiteboard drawing area" }
    private data class Stroke(val path: Path, val paint: Paint)
    private val strokes = mutableListOf<Stroke>()
    private val history = mutableListOf<List<Stroke>>()
    val inkBottom: Int get() {
        if (strokes.isEmpty() || width == 0 || height == 0) return 0
        // A small alpha mask accounts for erased ink, without allocating a full document bitmap.
        val scale = minOf(1f, 256f / width)
        val mask = Bitmap.createBitmap((width * scale).toInt().coerceAtLeast(1),
            kotlin.math.ceil(height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(mask); canvas.scale(scale, scale); onDraw(canvas)
            val row = IntArray(mask.width)
            for (y in mask.height - 1 downTo 0) {
                mask.getPixels(row, 0, row.size, 0, y, row.size, 1)
                if (row.any { Color.alpha(it) != 0 }) return ((y + 1) / scale).toInt()
            }
            return 0
        } finally { mask.recycle() }
    }
    private var path: Path? = null
    private var activePaint: Paint? = null
    private var x = 0f
    private var y = 0f
    var ink = Ui.c(ctx, R.color.ag_text)
    var erasing = false
    var drawingEnabled = true
        set(value) {
            field = value
            if (!value) { cancelStroke(); parent?.requestDisallowInterceptTouchEvent(false); invalidate() }
        }
    internal val strokeCount get() = strokes.size
    private fun pen() = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ink; style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND
        strokeWidth = Ui.dp(context, if (erasing) 22 else 3).toFloat()
        if (erasing) xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }
    fun reset() { strokes.clear(); history.clear(); cancelStroke(); invalidate() }
    fun clear() { if (strokes.isNotEmpty()) history.add(strokes.toList()); strokes.clear(); cancelStroke(); invalidate() }
    fun undo() { cancelStroke(); if (history.isNotEmpty()) { strokes.clear(); strokes.addAll(history.removeAt(history.lastIndex)) }; invalidate() }
    private fun cancelStroke() { path = null; activePaint = null }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val layer = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
        strokes.forEach { canvas.drawPath(it.path, it.paint) }
        path?.let { canvas.drawPath(it, activePaint!!) }
        canvas.restoreToCount(layer)
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!drawingEnabled) return false
        if (event.pointerCount > 1 || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            cancelStroke(); parent?.requestDisallowInterceptTouchEvent(false); invalidate(); return true
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                x = event.x; y = event.y; activePaint = pen()
                path = Path().apply { moveTo(x, y); lineTo(x + .01f, y) }
            }
            MotionEvent.ACTION_MOVE -> path?.let {
                if (abs(event.x - x) >= 2 || abs(event.y - y) >= 2) {
                    it.quadTo(x, y, (x + event.x) / 2, (y + event.y) / 2)
                    x = event.x; y = event.y
                }
            }
            MotionEvent.ACTION_UP -> {
                path?.let { it.lineTo(event.x, event.y); history.add(strokes.toList()); strokes.add(Stroke(it, activePaint!!)) }
                cancelStroke(); performClick()
            }
        }
        invalidate(); return true
    }
    override fun performClick(): Boolean { super.performClick(); return true }
}

/** Compact tools with independent drawing, colour and visibility controls. */
internal class WhiteboardTools(ctx: Context, val board: Whiteboard) : LinearLayout(ctx) {
    companion object { const val PREF = "whiteboardOn" }
    private val prefs = ctx.getSharedPreferences("study", 0)
    var onChanged: ((Boolean) -> Unit)? = null
    val isOn get() = board.visibility == View.VISIBLE
    private val colour = button("Colour") { palette() }.apply {
        text = "●"; textSize = 22f; translationX = -Ui.dp(ctx, 6).toFloat()
        // The dot hugs Pen's upper corner; the full 40 x 48 dp cell is independently tappable.
        gravity = android.view.Gravity.START or android.view.Gravity.TOP
        setPadding(0, Ui.dp(ctx, 1), 0, 0)
    }
    private val pen = button("Pen") {
        board.drawingEnabled = !board.drawingEnabled || board.erasing
        board.erasing = false; refresh()
    }
    private val eraser = button("Eraser") {
        val selected = board.drawingEnabled && board.erasing
        board.drawingEnabled = !selected; board.erasing = !selected; refresh()
    }
    init {
        setBackgroundColor(Ui.c(ctx, R.color.ag_base))
        gravity = android.view.Gravity.CENTER_VERTICAL
        addView(pen.apply { toolIcon(this, R.drawable.ic_pencil, 18, true) }, LayoutParams(Ui.dp(ctx, 56), Ui.dp(ctx, 48)))
        addView(colour, LayoutParams(Ui.dp(ctx, 40), Ui.dp(ctx, 48)))
        addView(eraser.apply { toolIcon(this, R.drawable.ic_whiteboard_eraser, 18, true) }, LayoutParams(Ui.dp(ctx, 64), Ui.dp(ctx, 48)))
        addView(button("Undo") { board.undo() }.apply { toolIcon(this, R.drawable.ic_undo, 22) }, LayoutParams(Ui.dp(ctx, 48), Ui.dp(ctx, 48)))
        addView(button("Clear") { board.clear() }.apply { toolIcon(this, R.drawable.ic_close, 22) }, LayoutParams(Ui.dp(ctx, 48), Ui.dp(ctx, 48)))
        addView(View(ctx), LayoutParams(0, 1, 1f))
        addView(button("Off") { setBoardOn(false) }.apply { toolIcon(this, R.drawable.ic_whiteboard_off, 22) }, LayoutParams(Ui.dp(ctx, 48), Ui.dp(ctx, 48)))
        setBoardOn(prefs.getBoolean(PREF, true)); refresh()
    }
    internal fun colours(): List<Pair<String, Int>> {
        val dark = (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        return listOf("Text" to Ui.c(context, R.color.ag_text), "Red" to Ui.c(context, R.color.ag_red), "Blue" to Ui.c(context, R.color.ag_blue),
            "Green" to Ui.c(context, R.color.ag_green), "Orange" to Ui.c(context, R.color.ag_peach),
            (if (dark) "White" else "Black") to (if (dark) Color.WHITE else Color.BLACK))
    }
    private fun toolIcon(view: android.widget.TextView, icon: Int, size: Int, labelled: Boolean = false) {
        val drawable = context.getDrawable(icon)!!.mutate()
        drawable.setTint(Ui.c(context, R.color.ag_text))
        drawable.setBounds(0, 0, Ui.dp(context, size), Ui.dp(context, size))
        if (labelled) {
            view.setCompoundDrawables(null, drawable, null, null)
            view.compoundDrawablePadding = Ui.dp(context, 1)
            view.setPadding(0, Ui.dp(context, 5), 0, 0)
        } else {
            view.text = ""
            view.background = null
            view.setCompoundDrawables(null, drawable, null, null)
            view.setPadding(0, Ui.dp(context, 13), 0, 0)
        }
    }
    internal fun paletteContent(onPick: () -> Unit = {}): View = android.widget.GridLayout(context).apply {
        columnCount = 3
        setPadding(Ui.dp(context, 7), Ui.dp(context, 7), Ui.dp(context, 7), Ui.dp(context, 7))
        background = android.graphics.drawable.GradientDrawable().apply {
            setColor(Ui.c(context, R.color.ag_card)); cornerRadius = Ui.dp(context, 14).toFloat()
            setStroke(Ui.dp(context, 1), Ui.c(context, R.color.ag_s0))
        }
        colours().forEach { (name, value) ->
            addView(android.widget.FrameLayout(context).apply {
                contentDescription = "Whiteboard colour: $name"
                addView(View(context).apply {
                    background = android.graphics.drawable.GradientDrawable().apply {
                        shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(value)
                        if (value == board.ink) setStroke(Ui.dp(context, 2), Ui.c(context, R.color.ag_text))
                    }
                }, android.widget.FrameLayout.LayoutParams(Ui.dp(context, 32), Ui.dp(context, 32), android.view.Gravity.CENTER))
                setOnClickListener { board.ink = value; board.erasing = false; board.drawingEnabled = true; refresh(); onPick() }
            }, android.widget.GridLayout.LayoutParams().apply { width = Ui.dp(context, 48); height = Ui.dp(context, 48) })
        }
    }
    internal fun paletteWindow(popup: android.widget.PopupWindow = android.widget.PopupWindow()): android.widget.PopupWindow {
        popup.contentView = paletteContent { popup.dismiss() }
        popup.width = Ui.dp(context, 158); popup.height = Ui.dp(context, 110)
        popup.isFocusable = true
        popup.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        popup.elevation = Ui.dp(context, 6).toFloat()
        AwayWindows.track(popup) { it.dismiss() }
        return popup
    }
    private fun palette() {
        paletteWindow().showAsDropDown(colour, 0, 0, android.view.Gravity.END)
    }
    private fun refresh() {
        colour.setTextColor(board.ink)
        colour.contentDescription = "Whiteboard colour: " + (colours().firstOrNull { it.second == board.ink }?.first ?: "Text")
        listOf(pen to (board.drawingEnabled && !board.erasing), eraser to (board.drawingEnabled && board.erasing)).forEach { (view, selected) ->
            view.isSelected = selected
            view.contentDescription = "${view.text}, " + if (selected) "selected" else "off"
            view.background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Ui.c(context, if (selected) R.color.ag_s0 else R.color.ag_base))
                cornerRadius = Ui.dp(context, 10).toFloat()
            }
        }
    }
    private fun button(label: String, action: () -> Unit) = StudyUi.label(context, label, 11, R.color.ag_text, 700).apply {
        gravity = android.view.Gravity.CENTER
        minHeight = Ui.dp(context, 48)
        contentDescription = "Whiteboard $label"
        isFocusable = true
        setOnClickListener { action() }
    }
    internal fun setBoardOn(on: Boolean) {
        board.visibility = if (on) View.VISIBLE else View.GONE
        visibility = if (on) View.VISIBLE else View.GONE
        prefs.edit().putBoolean(PREF, on).apply()
        onChanged?.invoke(on)
    }
}

/** Full-width document, with controls above (never inside) its ink input layer. */
internal class WhiteboardPaper(ctx: Context, private val card: View, private val board: Whiteboard) : android.widget.FrameLayout(ctx) {
    private val document = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
    private val paper = android.widget.FrameLayout(ctx)
    private val scroll = MotionScrollView(ctx)
    private var pages = 0
    private var enabled = true
    private val more = StudyUi.label(ctx, "+ More room", 14, R.color.ag_text, 700).apply {
        gravity = android.view.Gravity.CENTER; minHeight = Ui.dp(ctx, 48)
        setBackgroundColor(Ui.c(ctx, R.color.ag_base))
        setOnClickListener { if (pages < 20) { pages++; resize() } }
    }
    private val jumps = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
    init {
        paper.addView(card, LayoutParams(-1, -1)); paper.addView(board, LayoutParams(-1, -1))
        document.addView(paper); document.addView(more, LinearLayout.LayoutParams(-1, Ui.dp(ctx, 56)))
        scroll.isFillViewport = true; scroll.addView(document); addView(scroll, LayoutParams(-1, -1))
        listOf(R.drawable.ic_whiteboard_up to "Jump to top", R.drawable.ic_whiteboard_down to "Jump to last ink or card end").forEachIndexed { index, (icon, label) ->
            jumps.addView(android.widget.ImageView(ctx).apply {
                setImageResource(icon); imageTintList = android.content.res.ColorStateList.valueOf(Ui.c(ctx, R.color.ag_text))
                setPadding(Ui.dp(ctx, 12), Ui.dp(ctx, 12), Ui.dp(ctx, 12), Ui.dp(ctx, 12))
                contentDescription = label
                setBackgroundColor(Ui.c(ctx, R.color.ag_base))
                setOnClickListener {
                    val bottom = if (index == 0) 0 else board.inkBottom
                    scroll.smoothScrollTo(0, if (index == 0) 0 else if (bottom > 0)
                        (bottom - scroll.height / 2).coerceAtLeast(0) else paper.height - scroll.height)
                }
            }, LinearLayout.LayoutParams(Ui.dp(ctx, 48), Ui.dp(ctx, 48)).apply { topMargin = Ui.dp(ctx, 4) })
        }
        addView(jumps, LayoutParams(-2, -2, android.view.Gravity.BOTTOM or android.view.Gravity.END).apply { rightMargin = Ui.dp(ctx, 8) })
        paper.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> positionJumps() }
        scroll.setOnScrollChangeListener { _, _, _, _, _ -> positionJumps() }
    }
    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        resize(MeasureSpec.getSize(heightSpec))
        super.onMeasure(widthSpec, heightSpec)
    }
    fun setBoardOn(on: Boolean) { enabled = on; resize() }
    fun reset() { pages = 0; board.reset(); scroll.scrollTo(0, 0); resize() }
    private fun positionJumps() {
        jumps.translationY = -((scroll.height - (paper.height - scroll.scrollY)).coerceAtLeast(0) + Ui.dp(context, 8)).toFloat()
    }
    private fun resize(available: Int = height) {
        if (available == 0) return
        val cardHeight = (available - if (enabled) Ui.dp(context, 56) else 0).coerceAtLeast(1)
        if (card.layoutParams.height != cardHeight) card.layoutParams = (card.layoutParams as LayoutParams).apply { height = cardHeight }
        val wanted = cardHeight + available * (if (enabled) pages else 0)
        if (paper.layoutParams?.height != wanted) paper.layoutParams = LinearLayout.LayoutParams(-1, wanted)
        more.visibility = if (enabled) View.VISIBLE else View.GONE
        val label = if (pages == 20) "Room limit reached" else "+ More room"
        if (more.text.toString() != label) more.text = label
        more.isEnabled = pages < 20
        jumps.visibility = if (enabled && pages > 0) View.VISIBLE else View.GONE
        positionJumps()
    }
}
