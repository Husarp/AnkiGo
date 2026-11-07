package com.husarp.ankigo.ui

import com.husarp.ankigo.game.*
import org.junit.Assert.*
import org.junit.Test

class MoveEffectPlaybackTest {
    private val attack = GameEvent.Attack(true, "User", "Target", "Absorb", Type.GRASS, Category.SPECIAL,
        20, 1, 1.0, false, false, false, 80, 100, 70, 100)
    private val heal = GameEvent.Effect(true, "Healed", 70, 100, kind = EffectKind.DRAIN, amount = 10)

    @Test fun healingHpIsNotShownWithTheEarlierDamage() {
        for (player in listOf(true, false)) {
            val before = MoveEffectPlayback.beforeEffects(attack.copy(byPlayer = player), listOf(heal.copy(onPlayer = player)))
            assertEquals(60, before.userHp)
            assertEquals(80, before.targetHp)
        }
    }

    @Test fun damageThenBerryThenDrainUsesEachExactSnapshot() {
        val berry = heal.copy(onPlayer = false, kind = EffectKind.HEAL, hp = 90)
        val shown = MoveEffectPlayback.beforeEffects(attack.copy(targetHp = 90), listOf(berry, heal))
        assertEquals(80, shown.targetHp)
        assertEquals(60, shown.userHp)
    }

    @Test fun effectsFollowTheirMoveAndScaleWithoutOvertakingOneAnother() {
        val stat = heal.copy(kind = EffectKind.STAT_CHANGE, amount = 0, stat = "atk", stages = 1)
        for (factor in listOf(1.0, 0.5)) {
            val timed = MoveEffectPlayback.timeline(listOf(heal, stat), (460 * factor).toLong(),
                (990 * factor).toLong(), (700 * factor).toLong(), factor)
            assertEquals(listOf((990 * factor).toLong(), (1080 * factor).toLong()), timed.map { it.atMs })
            assertEquals(listOf(heal, stat), timed.map { it.effect })
        }
    }

    @Test fun hitEffectsAndRecoilRetainTheirOwnMoments() {
        val berry = heal.copy(onPlayer = false, kind = EffectKind.HEAL, hitIndex = 0)
        val recoil = heal.copy(kind = EffectKind.RECOIL, moveRecoil = true)
        val times = MoveEffectPlayback.timeline(listOf(berry, recoil, heal), 380, 880, 700, 1.0, listOf(380, 630, 880))
        assertEquals(listOf(380L, 700L, 880L), times.map { it.atMs })
    }

    @Test fun recoilThenHealingDoesNotJumpToTheFinalHpAtImpact() {
        val recoil = heal.copy(kind = EffectKind.RECOIL, hp = 40, amount = 20, moveRecoil = true)
        assertEquals(60, MoveEffectPlayback.beforeEffects(attack, listOf(recoil, heal)).userHp)
    }
}
