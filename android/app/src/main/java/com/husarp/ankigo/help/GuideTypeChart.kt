package com.husarp.ankigo.help

import com.husarp.ankigo.ui.MotionScrollView
import com.husarp.ankigo.ui.MotionHorizontalScrollView

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.HorizontalScrollView
import android.widget.ScrollView
import com.husarp.ankigo.R
import com.husarp.ankigo.game.Type
import com.husarp.ankigo.game.TypeChart
import com.husarp.ankigo.ui.*

internal object GuideTypeChart {
    fun targets(type: Type, multiplier: Double, defend: Boolean = false) = Type.entries.filter {
        (if (defend) TypeChart.single(it, type) else TypeChart.single(type, it)) == multiplier
    }

    fun picker(ctx: Context): View = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        addView(HelpViews.rich(ctx, "Move type → defending type: ×2 strong, ×½ weak, ×0 no effect."))
        val types = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val cells = ArrayList<View>()
        val result = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        var selected = Type.FIRE
        var defending = false
        fun select(t: Type) {
            selected = t
            result.removeAllViews()
            result.addView(LinearLayout(ctx).apply {
                gravity = Gravity.CENTER
                addView(android.widget.ImageView(ctx).apply {
                    setImageDrawable(TypeChips.tinted(ctx, t, TypeChips.ink(ctx, t)))
                }, LinearLayout.LayoutParams(Ui.dp(ctx, 36), Ui.dp(ctx, 36)))
                addView(Ui.text(ctx, 18f, 900, R.color.ag_text).apply { text = " ${t.label}" })
            }, HelpViews.lp(ctx, 10))
            result.addView(HelpViews.rich(ctx, if (defending) "Moves hitting this Pokémon type" else "This move type against Pokémon"))
            for ((label, mult) in listOf((if (defending) "Hit hard by ×2" else "Strong against ×2") to 2.0, (if (defending) "Resists ×½" else "Weak against ×½") to 0.5, (if (defending) "Immune to ×0" else "No effect ×0") to 0.0)) {
                result.addView(HelpViews.rich(ctx, label), HelpViews.lp(ctx, 8))
                val flow = LeftFlow(ctx, Ui.dp(ctx, 5))
                targets(t, mult, defending).forEach { flow.addView(Ui.typeChip(ctx, it)) }
                if (flow.childCount == 0) result.addView(HelpViews.rich(ctx, "None")) else result.addView(flow)
            }
            for (i in cells.indices) {
                val type = Type.entries[i]
                cells[i].apply {
                    isSelected = type == t
                    background = Ui.rounded(if (type == t) TypeChips.fill(ctx, type) else android.graphics.Color.TRANSPARENT, 10, ctx).apply {
                        if (type == t) setStroke(Ui.dp(ctx, 2), TypeChips.ink(ctx, type))
                    }
                }
            }
        }
        Type.entries.chunked(4).forEach { rowTypes ->
            val row = LinearLayout(ctx).apply { gravity = Gravity.CENTER }
            rowTypes.forEach { t ->
                val cell = LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    minimumHeight = Ui.dp(ctx, 56)
                    addView(android.widget.ImageView(ctx).apply {
                        setImageDrawable(TypeChips.tinted(ctx, t, TypeChips.ink(ctx, t)))
                    }, LinearLayout.LayoutParams(Ui.dp(ctx, 32), Ui.dp(ctx, 32)))
                    addView(Ui.text(ctx, 10f, 800, R.color.ag_text).apply { text = t.label; gravity = Gravity.CENTER })
                    contentDescription = "Show ${t.name.lowercase()} matchups"
                    setOnClickListener { select(t) }
                }
                cells += cell
                row.addView(cell, LinearLayout.LayoutParams(0, -2, 1f))
            }
            repeat(4 - rowTypes.size) { row.addView(View(ctx), LinearLayout.LayoutParams(0, 1, 1f)) }
            types.addView(row, HelpViews.lp(ctx, 6))
        }
        val modes = LookChoosers.segmented(ctx, listOf("Attack", "Defend"), 0) { i ->
            defending = i == 1; select(selected)
        }
        addView(modes, HelpViews.lp(ctx, 12))
        addView(types, HelpViews.lp(ctx, 12)); addView(result, HelpViews.lp(ctx, 10))
        addView(Form.secondary(ctx, "Full chart", onPage = true) { full(ctx) }, HelpViews.lp(ctx, 12))
        select(Type.FIRE)
    }

    fun grid(ctx: Context): View {
        val table = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = android.widget.FrameLayout.LayoutParams(Ui.dp(ctx, maxOf(304, ctx.resources.configuration.screenWidthDp - 80)), -2)
        }
        fun typeHeader(t: Type): View = android.widget.ImageView(ctx).apply {
            setImageDrawable(TypeChips.tinted(ctx, t, TypeChips.ink(ctx, t)))
            contentDescription = t.label
            setPadding(Ui.dp(ctx, 2), Ui.dp(ctx, 2), Ui.dp(ctx, 2), Ui.dp(ctx, 2))
            layoutParams = LinearLayout.LayoutParams(0, Ui.dp(ctx, 18), 1f)
            setOnClickListener { Ui.toast(ctx, t.label) }
        }
        fun cell(text: String, colour: Int = R.color.ag_text, desc: String = text): View = Ui.text(ctx, 9f, 800, colour).apply {
            this.text = text; gravity = Gravity.CENTER; contentDescription = desc
            layoutParams = LinearLayout.LayoutParams(0, Ui.dp(ctx, 18), 1f)
        }
        val top = LinearLayout(ctx)
        top.addView(cell("↘"))
        Type.entries.forEach { top.addView(typeHeader(it)) }
        table.addView(top)
        Type.entries.forEach { atk ->
            val row = LinearLayout(ctx)
            row.addView(typeHeader(atk))
            Type.entries.forEach { def ->
                val m = TypeChart.single(atk, def)
                row.addView(cell(when (m) { 2.0 -> "2"; 0.5 -> "½"; 0.0 -> "0"; else -> "" },
                    when (m) { 2.0 -> R.color.ag_blue; 0.5 -> R.color.ag_peach; 0.0 -> R.color.ag_sub0; else -> R.color.ag_text },
                    "${atk.name} against ${def.name}: ×$m").apply {
                    layoutParams = LinearLayout.LayoutParams(0, Ui.dp(ctx, 18), 1f)
                    if (m != 1.0) background = Ui.rounded(Bits.blend(Ui.c(ctx, when(m) { 2.0 -> R.color.ag_blue; 0.5 -> R.color.ag_peach; else -> R.color.ag_sub0 }), Ui.c(ctx, R.color.ag_card), .2f), 3, ctx)
                })
            }
            table.addView(row)
        }
        return MotionHorizontalScrollView(ctx).apply { addView(table) }
    }

    private fun full(ctx: Context) {
        val body = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            addView(HelpViews.rich(ctx, "Row = attacker; column = Pokémon hit. Blank = ×1. Blue = ×2; orange = ×½; grey = ×0."), HelpViews.lp(ctx, 12))
            addView(grid(ctx), HelpViews.lp(ctx, 12))
        }
        AppDialog.Builder(ctx).setTitle("Type chart").setView(MotionScrollView(ctx).apply { addView(body) })
            .setPositiveButton("Back", null).create().also { Pickers.show(it) }
    }
}
