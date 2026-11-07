package com.husarp.ankigo.ui

import com.husarp.ankigo.game.EffectKind
import com.husarp.ankigo.game.GameEvent

/** Timing and HP snapshots for effects owned by a move; residuals keep their own queue steps. */
internal object MoveEffectPlayback {
    data class Timed(val effect: GameEvent.Effect, val atMs: Long)

    fun beforeEffects(attack: GameEvent.Attack, effects: List<GameEvent.Effect>): GameEvent.Attack {
        fun before(onPlayer: Boolean, fallback: Int): Int {
            val effect = effects.firstOrNull { it.onPlayer == onPlayer && it.maxHp > 0 } ?: return fallback
            val delta = when (effect.kind) {
                EffectKind.DRAIN, EffectKind.HEAL -> -effect.amount
                EffectKind.RECOIL, EffectKind.RESIDUAL, EffectKind.STATUS_DAMAGE -> effect.amount
                else -> 0
            }
            return (effect.hp + delta).coerceIn(0, effect.maxHp)
        }
        return attack.copy(userHp = before(attack.byPlayer, attack.userHp),
            targetHp = if (attack.multiHit) attack.targetHp else before(!attack.byPlayer, attack.targetHp))
    }

    fun timeline(effects: List<GameEvent.Effect>, impactMs: Long, healMs: Long, recoilMs: Long,
                 factor: Double, hitTimes: List<Long> = emptyList()): List<Timed> {
        var next = impactMs
        return effects.map { effect ->
            val wanted = when {
                effect.hitIndex in hitTimes.indices -> hitTimes[effect.hitIndex]
                effect.moveRecoil -> recoilMs
                effect.kind == EffectKind.DRAIN || effect.kind == EffectKind.HEAL -> healMs
                else -> hitTimes.lastOrNull() ?: impactMs
            }
            val at = maxOf(next, wanted)
            next = at + maxOf(1L, (90 * factor).toLong())
            Timed(effect, at)
        }
    }
}
