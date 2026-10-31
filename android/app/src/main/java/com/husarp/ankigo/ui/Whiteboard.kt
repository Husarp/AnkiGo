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
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import com.husarp.ankigo.R
import kotlin.math.abs

/** Transparent card-only drawing surface. Erasing is replayed on a separate canvas layer. */
internal class Whiteboard(ctx: Context) : View(ctx) {
    init { contentDescription = "Whiteboard drawing area" }
    private data class Stroke(val path: Path, val paint: Paint)
    private val strokes = mutableListOf<Stroke>()
    private var path: Path? = null
    private var activePaint: Paint? = null
    private var x = 0f
    private var y = 0f
    var ink = Ui.c(ctx, R.color.ag_text)
    var erasing = false
    internal val strokeCount get() = strokes.size
    private fun pen() = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ink; style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND
        strokeWidth = Ui.dp(context, if (erasing) 22 else 3).toFloat()
        if (erasing) xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }
    fun clear() { strokes.clear(); cancelStroke(); invalidate() }
    fun undo() { cancelStroke(); if (strokes.isNotEmpty()) strokes.removeAt(strokes.lastIndex); invalidate() }
    private fun cancelStroke() { path = null; activePaint = null }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val layer = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
        strokes.forEach { canvas.drawPath(it.path, it.paint) }
        path?.let { canvas.drawPath(it, activePaint!!) }
        canvas.restoreToCount(layer)
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.pointerCount > 1 || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            cancelStroke(); invalidate(); return true
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
                path?.let { it.lineTo(event.x, event.y); strokes.add(Stroke(it, activePaint!!)) }
                cancelStroke(); performClick()
            }
        }
        invalidate(); return true
    }
    override fun performClick(): Boolean { super.performClick(); return true }
}

/** Always reachable hide toggle; tools scroll on narrow phones without covering the card or answers. */
internal class WhiteboardTools(ctx: Context, val board: Whiteboard) : LinearLayout(ctx) {
    companion object { const val PREF = "whiteboardOn" }
    private val prefs = ctx.getSharedPreferences("study", 0)
    private val tools = LinearLayout(ctx)
    private val toggle = button("Write") { setBoardOn(board.visibility != View.VISIBLE) }
    init {
        setBackgroundColor(Ui.c(ctx, R.color.ag_base))
        addView(toggle)
        addView(HorizontalScrollView(ctx).apply {
            isHorizontalScrollBarEnabled = false; addView(tools)
        }, LayoutParams(0, -2, 1f))
        tools.addView(button("Pen") { board.erasing = false })
        listOf("Ink" to R.color.ag_text, "Red" to R.color.ag_red, "Blue" to R.color.ag_blue, "Green" to R.color.ag_green).forEach { (name, color) ->
            tools.addView(button(name) { board.ink = Ui.c(ctx, color); board.erasing = false }.apply { setTextColor(Ui.c(ctx, color)) })
        }
        tools.addView(button("Eraser") { board.erasing = true })
        tools.addView(button("Undo") { board.undo() })
        tools.addView(button("Clear") { board.clear() })
        setBoardOn(prefs.getBoolean(PREF, false))
    }
    private fun button(label: String, action: () -> Unit) = StudyUi.label(context, label, 13, R.color.ag_text, 700).apply {
        gravity = android.view.Gravity.CENTER
        minWidth = Ui.dp(context, 48); minHeight = Ui.dp(context, 48)
        setPadding(Ui.dp(context, 10), 0, Ui.dp(context, 10), 0)
        contentDescription = "Whiteboard $label"
        setOnClickListener { action() }
    }
    internal fun setBoardOn(on: Boolean) {
        board.visibility = if (on) View.VISIBLE else View.GONE
        tools.visibility = if (on) View.VISIBLE else View.GONE
        toggle.text = if (on) "Hide writing" else "Write"
        toggle.contentDescription = if (on) "Hide whiteboard" else "Show whiteboard"
        prefs.edit().putBoolean(PREF, on).apply()
    }
}
