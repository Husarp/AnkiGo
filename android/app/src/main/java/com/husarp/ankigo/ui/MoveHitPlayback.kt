package com.husarp.ankigo.ui

import com.husarp.ankigo.game.GameEvent

/** Presentation slices of a real attack, never a guessed division of its total damage. */
internal object MoveHitPlayback {
    /** The attack's final HP may already include healing that followed recoil. */
    fun beforeRecoil(e: GameEvent.Attack, recoil: GameEvent.Effect?): GameEvent.Attack =
        if (recoil == null) e else e.copy(userHp = recoil.hp + recoil.amount)

    fun hits(e: GameEvent.Attack): List<GameEvent.Attack> {
        if (e.missed || e.failed || e.hitDamages.size != e.hits || e.hits <= 1 ||
            e.hitDamages.any { it < 0 } || e.hitDamages.sum() != e.damage) return emptyList()
        if (e.hitTargetHps.isNotEmpty() && (e.hitTargetHps.size != e.hits ||
                e.hitTargetHps.any { it !in 0..e.targetMaxHp })) return emptyList()
        var hp = e.targetHp + e.damage
        return e.hitDamages.mapIndexed { i, damage ->
            hp -= damage
            e.copy(damage = damage, hits = 1, targetHp = e.hitTargetHps.getOrNull(i) ?: hp,
                crit = e.crit && i == e.hitDamages.lastIndex,
                effectiveness = if (i == e.hitDamages.lastIndex) e.effectiveness else 1.0,
                hitDamages = emptyList(), hitTargetHps = emptyList())
        }
    }
}
