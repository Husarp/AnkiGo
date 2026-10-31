package com.husarp.ankigo.ui

import com.husarp.ankigo.game.*
import org.junit.Assert.*
import org.junit.Test

class R23ZbHitPlaybackTest {
    private fun attack(player: Boolean = true) = GameEvent.Attack(player, "User", "Target", "Fury Swipes", Type.NORMAL,
        Category.PHYSICAL, 19, 3, 2.0, true, false, false, 0, 100, 80, 100,
        hitDamages = listOf(7, 9, 3), multiHit = true)

    @Test fun numbersUseActualUnequalDamageAndProgressiveHpOnEitherSide() {
        for (player in listOf(false, true)) {
            val hits = MoveHitPlayback.hits(attack(player))
            assertEquals(listOf(7, 9, 3), hits.map { it.damage })
            assertEquals(listOf(12, 3, 0), hits.map { it.targetHp })
            assertEquals(listOf("−7 HP", "−9 HP", "−3 HP"), hits.map { FloatTexts.of(it).first().text })
            assertTrue(hits.all { FloatTexts.of(it).first().onPlayer == !player })
            assertEquals(1, hits.count { it.crit })
            assertEquals(1, hits.count { it.effectiveness == 2.0 })
        }
    }
    @Test fun healingBetweenHitsUsesSnapshotsInsteadOfReconstructingHpFromTheTotal() {
        val e = attack().copy(damage = 60, hits = 2, targetHp = 65,
            hitDamages = listOf(30, 30), hitTargetHps = listOf(70, 65))
        val hits = MoveHitPlayback.hits(e)
        assertEquals(listOf(30, 30), hits.map { it.damage })
        assertEquals(listOf(70, 65), hits.map { it.targetHp })
        assertTrue(hits.all { it.hitTargetHps.isEmpty() })
        assertTrue(MoveHitPlayback.hits(e.copy(hitTargetHps = listOf(70))).isEmpty())
    }

    @Test fun legacyAndInvalidEventsNeverInventHitAmounts() {
        for (e in listOf(attack().copy(hitDamages = emptyList()), attack().copy(hitDamages = listOf(6, 6, 6)),
            attack().copy(missed = true), attack().copy(failed = true))) assertTrue(MoveHitPlayback.hits(e).isEmpty())
    }
    @Test fun substituteHitsKeepTheirTimingWithoutFakeDamageNumbers() {
        val hits = MoveHitPlayback.hits(attack().copy(damage = 3, hitDamages = listOf(0, 0, 3)))
        assertEquals(3, hits.size)
        assertTrue(FloatTexts.of(hits.first()).isEmpty())
    }
    @Test fun recoilBarUsesItsOwnEventBeforeLaterHealing() {
        val attack = attack().copy(userHp = 100)
        val recoil = GameEvent.Effect(true, "Recoil", 70, 100, kind = EffectKind.RECOIL, amount = 10, moveRecoil = true)
        assertEquals(80, MoveHitPlayback.beforeRecoil(attack, recoil).userHp)
        assertEquals(100, MoveHitPlayback.beforeRecoil(attack, null).userHp)
    }

}
