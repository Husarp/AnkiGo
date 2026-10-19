package com.husarp.ankigo.ui

import com.husarp.ankigo.game.DuplicateResult
import com.husarp.ankigo.game.EffectKind
import com.husarp.ankigo.game.GameEvent
import com.husarp.ankigo.game.Category
import com.husarp.ankigo.game.Tuning

/** What a floating battle text means (the look is chosen per kind by the panel). */
enum class FloatKind { DAMAGE, BIG_DAMAGE, MISS, NO_EFFECT, NOT_VERY, SUPER, CRIT, STATUS, STAT_UP, STAT_DOWN, HEAL, HESITATE, INFO }

/**
 * One floating text: at the player's Pokémon ([onPlayer]) or the wild one. A stat change also carries its short label
 * ([stat], "ATK") and how many stages ([stages], 1..3, the direction is the kind), which the view draws as an arrow badge.
 */
data class FloatText(val onPlayer: Boolean, val text: String, val kind: FloatKind, val stat: String? = null, val stages: Int = 0)

/** [crits] = whether "Critical!" is shown (only when the engine has crits on). */
data class FloatContext(val crits: Boolean = Tuning.CRITS)

/**
 * Turns one game event into the floating texts it should spawn (pure, no views). Attack -> at the target, Effect -> at
 * the affected side, a counted Again answer -> "Hesitated" at the player's Pokémon. Order = the order they should play.
 */
object FloatTexts {
    private const val AGAIN = 1 // button index of Again (Tuning.POINTS[1] = 0)

    /** R9-W6-6: HP floats carry their unit: "+3 HP" and "−12 HP" (real minus sign, like CompareModes). */
    fun heal(n: Int) = "+$n HP"
    fun loss(n: Int) = "\u2212$n HP"

    /** What a screen reader says for a float: "3 HP healed" / "12 HP damage"; any other text is read as it is. */
    fun spoken(text: String, kind: FloatKind): String {
        if (kind == FloatKind.HEAL && text.startsWith("+") && text.endsWith(" HP")) return "${text.substring(1, text.length - 3)} HP healed"
        if ((kind == FloatKind.DAMAGE || kind == FloatKind.BIG_DAMAGE) && text.startsWith("\u2212") && text.endsWith(" HP")) return "${text.substring(1, text.length - 3)} HP damage"
        return text
    }

    fun of(e: GameEvent, ctx: FloatContext = FloatContext()): List<FloatText> = when (e) {
        is GameEvent.Counted -> if (e.button == AGAIN) listOf(FloatText(true, "Hesitated", FloatKind.HESITATE)) else emptyList()
        is GameEvent.Attack -> attack(e, ctx)
        is GameEvent.Effect -> effect(e)
        is GameEvent.Healed -> if (e.amount > 0) listOf(FloatText(true, heal(e.amount), FloatKind.HEAL)) else emptyList() // only the main's heals emit it
        is GameEvent.Duplicate -> if (e.result == DuplicateResult.SWAPPED) listOf(FloatText(false, "Swapped for Lv ${e.level}", FloatKind.INFO)) else emptyList()
        else -> emptyList()
    }

    private fun attack(e: GameEvent.Attack, ctx: FloatContext): List<FloatText> {
        val onPlayer = !e.byPlayer // the target's side
        if (e.missed) return listOf(FloatText(onPlayer, "Miss", FloatKind.MISS))
        if (e.failed) return emptyList()
        if (e.category != Category.STATUS && e.effectiveness == 0.0) return listOf(FloatText(onPlayer, "No effect", FloatKind.NO_EFFECT))
        if (e.damage <= 0) return emptyList()
        val out = ArrayList<FloatText>(3)
        val big = e.crit || e.effectiveness > 1.0
        out += FloatText(onPlayer, loss(e.damage), if (big) FloatKind.BIG_DAMAGE else FloatKind.DAMAGE)
        if (e.effectiveness > 1.0) out += FloatText(onPlayer, "Super effective!", FloatKind.SUPER)
        else if (e.effectiveness < 1.0) out += FloatText(onPlayer, "Not very effective", FloatKind.NOT_VERY)
        if (e.crit && ctx.crits) out += FloatText(onPlayer, "Critical!", FloatKind.CRIT)
        return out
    }

    private fun effect(e: GameEvent.Effect): List<FloatText> {
        val p = e.onPlayer
        return when (e.kind) {
            EffectKind.STATUS_APPLIED -> statusName(e.status)?.let { listOf(FloatText(p, it, FloatKind.STATUS)) } ?: emptyList()
            EffectKind.STATUS_CURED -> {
                val woke = e.stat == "slp" || (e.stat == null && "woke" in e.text)
                listOf(FloatText(p, if (woke) "Woke up" else "Thawed", FloatKind.STATUS))
            }
            EffectKind.CANT_MOVE -> listOf(FloatText(p, "Can't move", FloatKind.STATUS))
            EffectKind.STAT_CHANGE -> {
                val label = statLabel(e.stat) ?: return emptyList()
                if (e.stages == 0) emptyList()
                else {
                    val n = minOf(3, kotlin.math.abs(e.stages))
                    if (e.stages > 0) listOf(FloatText(p, "$label ${"↑".repeat(n)}", FloatKind.STAT_UP, label, n))
                    else listOf(FloatText(p, "$label ${"↓".repeat(n)}", FloatKind.STAT_DOWN, label, n))
                }
            }
            EffectKind.STAT_LIMIT -> statLabel(e.stat)?.let { listOf(FloatText(p, "$it ${if (e.stages > 0) "max" else "min"}", FloatKind.INFO)) } ?: emptyList()
            EffectKind.DRAIN, EffectKind.HEAL -> if (e.amount > 0) listOf(FloatText(p, heal(e.amount), FloatKind.HEAL)) else emptyList()
            EffectKind.RECOIL, EffectKind.STATUS_DAMAGE -> if (e.amount > 0) listOf(FloatText(p, loss(e.amount), FloatKind.DAMAGE)) else emptyList()
            EffectKind.OTHER, EffectKind.CHARGE -> emptyList()
        }
    }

    private fun statusName(status: String?): String? = when (status) {
        "brn" -> "Burned"
        "par" -> "Paralyzed"
        "psn" -> "Poisoned"
        "tox" -> "Badly poisoned"
        "slp" -> "Fell asleep"
        "frz" -> "Frozen"
        else -> null
    }

    private fun statLabel(stat: String?): String? = when (stat) {
        "atk" -> "ATK"
        "def" -> "DEF"
        "spa" -> "SPA"
        "spd" -> "SPD"
        "spe" -> "SPE"
        "accuracy" -> "ACC"
        "evasion" -> "EVA"
        else -> null
    }
}
