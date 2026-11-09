package com.husarp.ankigo.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import com.husarp.ankigo.R

/** PLAN 571: current app colour in one row, with a live, wrapping palette in the app dialog. */
internal object AccentPicker {
    fun row(ctx: Context, open: () -> Unit): View {
        val current = AppPrefs.accent(ctx)
        val choice = LinearLayout(ctx).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(View(ctx).apply { background = Swatch(ctx, current, false) },
                LinearLayout.LayoutParams(Ui.dp(ctx, 24), Ui.dp(ctx, 24)))
            addView(Ui.text(ctx, 13f, 700, R.color.ag_text).apply { text = current.label },
                LinearLayout.LayoutParams(-2, -2).also { it.marginStart = Ui.dp(ctx, 6) })
            addView(Form.secondary(ctx, "Pick", onClick = open),
                LinearLayout.LayoutParams(-2, -2).also { it.marginStart = Ui.dp(ctx, 8) })
        }
        return Form.row(ctx, "Accent", null, choice, open)
    }

    fun dialog(ctx: Context, changed: () -> Unit): AppDialog {
        val grid = FlowRow(ctx, Ui.dp(ctx, 4), lineGapPx = Ui.dp(ctx, 8)).apply {
            setPadding(Ui.dp(ctx, 12), Ui.dp(ctx, 4), Ui.dp(ctx, 12), Ui.dp(ctx, 12))
        }
        val marks = linkedMapOf<Accent, View>()
        val cells = linkedMapOf<Accent, View>()
        fun update() {
            val current = AppPrefs.accent(ctx)
            for ((accent, mark) in marks) {
                val selected = current == accent
                mark.background = Swatch(ctx, accent, selected)
                cells.getValue(accent).apply {
                    isSelected = selected
                    contentDescription = accent.label + if (selected) ", selected" else ""
                }
            }
        }
        val palette = listOf(Accent.GREEN, Accent.MULTICOLOR, Accent.RED, Accent.CORAL, Accent.PEACH, Accent.ORANGE,
            Accent.AMBER, Accent.YELLOW, Accent.LIME, Accent.OLIVE, Accent.JADE, Accent.TEAL, Accent.CYAN,
            Accent.BLUE, Accent.INDIGO, Accent.MAUVE, Accent.PURPLE, Accent.PINK, Accent.ROSE, Accent.FUCHSIA,
            Accent.BROWN, Accent.SLATE)
        for (accent in palette) {
            val mark = View(ctx)
            marks[accent] = mark
            val cell = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                minimumWidth = Ui.dp(ctx, 76)
                setPadding(0, Ui.dp(ctx, 4), 0, Ui.dp(ctx, 4))
                addView(mark, LinearLayout.LayoutParams(Ui.dp(ctx, 48), Ui.dp(ctx, 48)))
                addView(Ui.text(ctx, 11f, 700, R.color.ag_text).apply {
                    text = accent.label
                    gravity = Gravity.CENTER
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }, LinearLayout.LayoutParams(-1, -2))
                isFocusable = true
                setOnClickListener {
                    if (AppPrefs.accent(ctx) != accent) {
                        AppLook.setAccent(ctx, accent)
                        changed()
                    }
                    update()
                }
            }
            cells[accent] = cell
            grid.addView(cell, ViewGroup.MarginLayoutParams(Ui.dp(ctx, 76), -2))
        }
        update()
        return AppDialog.Builder(ctx).setTitle("App colour")
            .setMessage("Choose a colour to preview it.")
            .setView(grid).setNegativeButton("Close", null).create()
    }

    /** The swatch itself is the picture: no tile, border or coloured box behind it. */
    private class Swatch(val ctx: Context, val accent: Accent, val selected: Boolean) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun draw(canvas: Canvas) {
            val cx = bounds.exactCenterX()
            val cy = bounds.exactCenterY()
            val radius = bounds.width().coerceAtMost(bounds.height()) / 2f
            val r = radius * if (selected) .70f else .82f
            val color = ctx.getColor(accent.res)
            paint.style = Paint.Style.FILL
            if (accent == Accent.MULTICOLOR) {
                val tones = Multicolor.Tone.entries
                val oval = RectF(cx - r, cy - r, cx + r, cy + r)
                tones.forEachIndexed { i, tone ->
                    paint.color = if (TypeChips.isDark(ctx)) tone.dark else tone.light
                    canvas.drawArc(oval, -90f + i * 45f, 45f, true, paint)
                }
            } else {
                paint.color = color
                canvas.drawCircle(cx, cy, r, paint)
            }
            if (selected) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = Ui.dp(ctx, 2).toFloat()
                paint.color = Ui.c(ctx, R.color.ag_text)
                canvas.drawCircle(cx, cy, radius * .90f, paint)
                paint.color = if (accent == Accent.MULTICOLOR) android.graphics.Color.BLACK else Multicolor.onFill(color)
                paint.strokeCap = Paint.Cap.ROUND
                paint.strokeJoin = Paint.Join.ROUND
                canvas.drawPath(Path().apply {
                    moveTo(cx - r * .40f, cy)
                    lineTo(cx - r * .10f, cy + r * .30f)
                    lineTo(cx + r * .44f, cy - r * .30f)
                }, paint)
            }
        }
        override fun setAlpha(alpha: Int) = Unit
        override fun setColorFilter(colorFilter: ColorFilter?) = Unit
        @Deprecated("Deprecated in Java")
        override fun getOpacity() = PixelFormat.TRANSLUCENT
    }
}
