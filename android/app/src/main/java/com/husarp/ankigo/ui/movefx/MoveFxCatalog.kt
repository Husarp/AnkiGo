package com.husarp.ankigo.ui.movefx

import com.husarp.ankigo.game.Category
import com.husarp.ankigo.game.Type
import kotlin.math.sqrt

/** Which look a move gets (the page's fallback rules 1-4). Pure Kotlin. */
object MoveFxCatalog {
    /** A picked look: its move id, type, category, base power and the kit its type uses in the templates. */
    private class Pick(val id: String, val look: MoveLook, val type: Type, val category: Category, val power: Int, val kit: FxKit)

    private val picks = listOf(
        Pick("tackle", MoveLook.TACKLE, Type.NORMAL, Category.PHYSICAL, 40, FxKit.NORMAL),
        Pick("ember", MoveLook.EMBER, Type.FIRE, Category.SPECIAL, 40, FxKit.FIRE),
        Pick("watergun", MoveLook.WATER_JET, Type.WATER, Category.SPECIAL, 40, FxKit.WATER),
        Pick("thunderbolt", MoveLook.THUNDERBOLT, Type.ELECTRIC, Category.SPECIAL, 90, FxKit.ELECTRIC),
        Pick("vinewhip", MoveLook.VINE_WHIP, Type.GRASS, Category.PHYSICAL, 45, FxKit.GRASS),
        Pick("icebeam", MoveLook.ICE_BEAM, Type.ICE, Category.SPECIAL, 90, FxKit.ICE),
        Pick("psychic", MoveLook.PSYCHIC, Type.PSYCHIC, Category.SPECIAL, 90, FxKit.PSYCHIC),
        Pick("shadowball", MoveLook.SHADOW_BALL, Type.GHOST, Category.SPECIAL, 80, FxKit.GHOST),
        Pick("earthquake", MoveLook.EARTHQUAKE, Type.GROUND, Category.PHYSICAL, 100, FxKit.GROUND),
    )

    /** Types with an original picked move retain their kit; the other nine use the tinted C texture kits. */
    private val kits: Map<Type, FxKit> = picks.associate { it.type to it.kit }

    /**
     * The look for one move. [moveId] = Showdown id ("ember", "" = not known), [type] null = typeless (Struggle: plays as
     * Normal), [power] = base power (0 = none: k = 1).
     */
    fun plan(moveId: String, type: Type?, category: Category, power: Int,
        multiHit: Boolean = false, recoil: Boolean = false, hits: Int = 4, showRecoil: Boolean = true): MoveFxPlan {
        val t = type ?: Type.NORMAL
        val palette = FxPalettes.of(t)
        // Behaviour groups come from the move data / battle event, never a hand-maintained move list.
        val behaviour = when {
            multiHit -> MoveLook.MULTI_HIT
            recoil -> MoveLook.RECOIL
            moveId in drains -> MoveLook.DRAIN
            moveId in heals -> MoveLook.HEAL
            moveId in blasts -> MoveLook.BLAST
            moveId == "furyswipes" -> MoveLook.MULTI_HIT
            moveId == "doubleedge" -> MoveLook.RECOIL
            else -> null
        }
        if (behaviour != null) return MoveFxPlan(behaviour, 1f, kits[t] ?: FxKit.valueOf(t.name), palette, hits.coerceAtLeast(0), showRecoil)
        // The picked moves themselves.
        picks.firstOrNull { it.id == moveId }?.let { return MoveFxPlan(it.look, 1f, it.kit, FxPalettes.of(it.type)) }
        // Rule 1: same type and category as a pick.
        picks.firstOrNull { it.type == t && it.category == category }?.let {
            return MoveFxPlan(it.look, k(power, it.power), it.kit, palette)
        }
        // Category templates retain the original kits or use the new type-tinted C kits.
        val kit = kits[t] ?: FxKit.valueOf(t.name)
        return when (category) {
            Category.PHYSICAL -> MoveFxPlan(MoveLook.HIT_TEMPLATE, k(power, 40), kit, palette)
            Category.SPECIAL -> MoveFxPlan(MoveLook.ORB_TEMPLATE, k(power, 80), kit, palette)
            Category.STATUS -> MoveFxPlan(MoveLook.AURA_TEMPLATE, 1f, kit, palette)
        }
    }

    // Explicit design-page families, including callback-based heals absent from the heal fraction field.
    internal val drains = "absorb bitterblade bouncybubble drainingkiss drainpunch dreameater gigadrain hornleech leechlife matchagotcha megadrain oblivionwing paraboliccharge".split(" ").toSet()
    internal val heals = "healorder milkdrink recover roost slackoff softboiled moonlight morningsun shoreup synthesis rest swallow strengthsap purify junglehealing lunarblessing lifedew".split(" ").toSet()
    private val blasts = setOf("explosion", "selfdestruct", "mistyexplosion")

    fun preparation(type: Type): MoveFxPlan = MoveFxPlan(MoveLook.CHARGE, 1f, kits[type] ?: FxKit.valueOf(type.name), FxPalettes.of(type))

    /** Rule 4: sqrt(power / base) kept in 0.8..1.6; 1 when the move has no power. */
    private fun k(power: Int, base: Int): Float =
        if (power <= 0) 1f else sqrt(power.toDouble() / base).toFloat().coerceIn(0.8f, 1.6f)
}
