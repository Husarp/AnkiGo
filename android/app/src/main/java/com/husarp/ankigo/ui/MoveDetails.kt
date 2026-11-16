package com.husarp.ankigo.ui

import android.content.Context
import android.graphics.Paint
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import com.husarp.ankigo.R
import com.husarp.ankigo.game.*
import java.util.Locale
import kotlin.math.abs

internal object MoveAccuracy {
    fun effective(move: Move, battle: BattleView?, byPlayer: Boolean): Double? {
        val base = move.accuracy ?: return null
        if (battle == null || base >= 100 || move.ohko || move.target in setOf("self", "adjacentAllyOrSelf", "allies", "allySide")) return null
        val hit = BattleEngine.newHit(battle, byPlayer, battle.mon(byPlayer), battle.side(byPlayer),
            battle.mon(!byPlayer), battle.side(!byPlayer), move)
        val stage = BattleEngine.accuracyStage(hit)
        if (stage == 0) return null
        val accuracy = rulesChain<Double?>(base.toDouble()) { rule, acc -> rule.accuracy(hit, acc) }
        val effective = accuracy?.let { (it * BattleEngine.accStageMult(stage)).coerceIn(0.0, 100.0) } ?: 100.0
        return effective.takeIf { abs(it - base) > 0.01 }
    }

    fun percent(value: Double): String = (if (abs(value - value.toInt()) < 0.01) value.toInt().toString()
        else String.format(Locale.ROOT, "%.1f", value)) + "%"

    fun value(ctx: Context, base: Int?, effective: Double?, large: Boolean = false): View = LinearLayout(ctx).apply {
        gravity = Gravity.BOTTOM
        isBaselineAligned = false
        val original = Ui.text(ctx, if (large) 26f else 13f, 900, R.color.ag_text).apply {
            text = base?.let { "$it%" } ?: "—"
            if (effective != null) {
                paintFlags = paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                setTextColor(Ui.c(ctx, R.color.ag_sub0))
            }
        }
        addView(original)
        if (effective != null) addView(Ui.text(ctx, if (large) 13f else 9f, 900, R.color.ag_text).apply {
            text = percent(effective)
            setTextColor(StatPill.ink(effective > (base ?: 0), StatPill.isDark(ctx)))
        }, LinearLayout.LayoutParams(-2, -2).also { it.marginStart = Ui.dp(ctx, 2); it.bottomMargin = Ui.dp(ctx, if (large) 19 else 9) })
        contentDescription = if (effective == null) original.text else "Original accuracy $base%, effective accuracy ${percent(effective)}"
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        descendantFocusability = android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
    }
}

internal object MoveDetails {
    const val POWER_HELP = "Damage grows roughly in proportion to Power: a move with 80 Power hits about twice as hard as one with 40, with other factors equal. Type matchups, a matching Pokémon type, Attack or Special Attack, the target's Defense or Special Defense, and stat stages also affect damage. Power is not a fixed amount of HP lost."
    const val ACCURACY_HELP = "Accuracy is the percentage chance to hit: 90% means about 9 hits out of 10 attempts. Accuracy stages raise or lower this chance, and the target's evasion stages change it in the opposite direction. Subtract evasion from accuracy, cap the stage at −6 to +6, then apply its multiplier; the chance cannot exceed 100%. Some moves and battle effects skip the accuracy check."

    fun show(ctx: Context, move: Move, known: List<Move>?, effective: Double?) {
        AppDialog.Builder(ctx).setTitle(move.name).setView(content(ctx, move, known, effective))
            .setPositiveButton("Done", null).showCard()
    }

    internal fun effect(ctx: Context, sentence: String): CharSequence {
        val out = SpannableStringBuilder(sentence)
        val verbs = Regex("(?i)\\b(raises?|lowers?)\\b").findAll(sentence).toList()
        for ((i, verb) in verbs.withIndex()) {
            val color = StatPill.ink(verb.value.startsWith("rais", true), StatPill.isDark(ctx))
            out.setSpan(ForegroundColorSpan(color), verb.range.first, verb.range.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            val end = verbs.getOrNull(i + 1)?.range?.first ?: sentence.length
            Regex("\\b[1-6] stages?(?: \\(×[^)]+\\))?").findAll(sentence.substring(verb.range.last + 1, end)).forEach { amount ->
                val start = verb.range.last + 1 + amount.range.first
                out.setSpan(ForegroundColorSpan(color), start, start + amount.value.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        return StatProse.format(ctx, out)
    }

    fun content(ctx: Context, move: Move, known: List<Move>? = null, effective: Double? = null): View = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(Ui.dp(ctx, 24), Ui.dp(ctx, 4), Ui.dp(ctx, 24), Ui.dp(ctx, 16))
        addView(LinearLayout(ctx).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(Ui.typeChip(ctx, move.type))
            addView(DetailSections.kindTile(ctx, move.category, 28), LinearLayout.LayoutParams(Ui.dp(ctx, 28), Ui.dp(ctx, 28)).also { it.marginStart = Ui.dp(ctx, 8) })
            addView(Ui.text(ctx, 12f, 800, R.color.ag_sub0).apply { text = DetailSections.kindName(move.category) })
        })
        val powerHelp = Ui.text(ctx, 13f, 700, R.color.ag_text).apply {
            text = StatProse.format(ctx, (if (MoveText.showsPower(move)) "" else if (move.category == Category.STATUS)
                "This status move deals no direct damage, so Power does not apply. " else "This move uses a special damage rule, so ordinary Power does not apply. ") + POWER_HELP); visibility = View.GONE; tag = "move-help:Power"
            setPadding(0, Ui.dp(ctx, 12), 0, 0)
        }
        val accuracyHelp = Ui.text(ctx, 13f, 700, R.color.ag_text).apply {
            text = StatProse.format(ctx, (if (move.accuracy == null) "This move has no accuracy check. " else "") + ACCURACY_HELP); visibility = View.GONE; tag = "move-help:Accuracy"
            setPadding(0, Ui.dp(ctx, 12), 0, 0)
        }
        fun metric(label: String, value: View, help: View): View = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            minimumHeight = Ui.dp(ctx, 64)
            setPadding(Ui.dp(ctx, 12), Ui.dp(ctx, 8), Ui.dp(ctx, 6), Ui.dp(ctx, 8))
            addView(Ui.text(ctx, 12f, 800, R.color.ag_sub0).apply { text = label })
            addView(value)
            tag = "move-metric:$label"
            contentDescription = "$label. ${value.contentDescription ?: (value as? android.widget.TextView)?.text}. Tap for explanation"
            background = DetailParts.rowRipple(ctx)
            setOnClickListener {
                help.visibility = if (help.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                contentDescription = "$label. Tap to ${if (help.visibility == View.VISIBLE) "collapse" else "expand"} explanation"
            }
        }
        addView(LinearLayout(ctx).apply {
            background = Ui.rounded(Ui.c(ctx, R.color.ag_mantle), 12, ctx)
            addView(metric("Power", Ui.text(ctx, 26f, 900, R.color.ag_text).apply { text = if (MoveText.showsPower(move)) move.power.toString() else "—" }, powerHelp), LinearLayout.LayoutParams(0, -2, 1f))
            addView(metric("Accuracy", MoveAccuracy.value(ctx, move.accuracy, effective, large = true), accuracyHelp), LinearLayout.LayoutParams(0, -2, 1f))
        }, LinearLayout.LayoutParams(-1, -2).also { it.topMargin = Ui.dp(ctx, 14) })
        addView(powerHelp)
        addView(accuracyHelp)
        for (sentence in MoveText.describe(move, known).split(Regex("(?<=[.!?])\\s+(?=[A-Z])"))) {
            val verb = Regex("(?i)\\b(raises?|lowers?)\\b").find(sentence)
            val line = Ui.text(ctx, 14f, 700, R.color.ag_text).apply {
                text = effect(ctx, sentence)
                setLineSpacing(0f, 1.18f)
            }
            addView(LinearLayout(ctx).apply {
                gravity = Gravity.CENTER_VERTICAL
                if (verb != null) addView(View(ctx).apply { setBackgroundColor(StatPill.ink(verb.value.startsWith("rais", true), StatPill.isDark(ctx))) },
                    LinearLayout.LayoutParams(Ui.dp(ctx, 3), -1).also { it.marginEnd = Ui.dp(ctx, 10) })
                addView(line, LinearLayout.LayoutParams(0, -2, 1f))
            }, LinearLayout.LayoutParams(-1, -2).also { it.topMargin = Ui.dp(ctx, 16) })
        }
    }
}
