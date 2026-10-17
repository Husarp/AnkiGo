package com.husarp.ankigo.game

import com.husarp.ankigo.game.GameEvent.Effect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** R3-10: the engine says WHAT happened in the Effect events (kind, stat, stages, amount); the log text is unchanged. */
class EffectKindsTest {
    private val extra = TestData.MOVES + listOf(
        Move("burnhit", "Burn Hit", Type.NORMAL, Category.PHYSICAL, 40, 100, secondary = Secondary(100, status = "brn")),
        Move("flamewheel", "Flame Wheel", Type.FIRE, Category.PHYSICAL, 60, 100),
        Move("bigdance", "Big Dance", Type.NORMAL, Category.STATUS, 0, null, target = "self", boosts = mapOf("atk" to 3, "spe" to 1, "accuracy" to 1)),
        Move("shine", "Shine", Type.NORMAL, Category.STATUS, 0, 100, target = "allAdjacentFoes", boosts = mapOf("evasion" to -1, "spd" to -2, "spa" to -3, "def" to -1)),
        Move("hit2", "Hit Two", Type.NORMAL, Category.PHYSICAL, 40, 100, secondary = Secondary(100, boosts = mapOf("def" to -1))),
    ).associateBy { it.id }
    private val data = TestData.data(TestData.world().species.values.toList(), extra)

    private fun mon(sp: Int, lv: Int, vararg moves: String) = TestData.mon(data, sp, lv, moves.toList())
    private fun maxHp(m: Mon) = StatCalc.maxHp(data, m)
    private fun round(main: Mon, b: Battle, acts: Boolean = true, seed: Int = 1, mult: Double = 1.0) =
        BattleEngine.playRound(data, b, main, mult, acts, Random(seed))
    private fun List<GameEvent>.fx(kind: EffectKind) = filterIsInstance<Effect>().filter { it.kind == kind }

    @Test fun existingConstructorCallsStillCompile() {
        val e = Effect(true, "x", 1, 2)
        assertEquals(EffectKind.OTHER, e.kind); assertNull(e.stat); assertEquals(0, e.stages); assertEquals(0, e.amount)
    }

    @Test fun statusMoveAppliesStatusAtTheTarget() {
        var seen = 0
        for (s in 1..40) {
            val b = Battle(mon(25, 20, "thunderwave"), Tier.NORMAL)
            val ev = round(mon(19, 20, "tackle"), b, seed = s)
            val e = ev.fx(EffectKind.STATUS_APPLIED).firstOrNull() ?: continue
            seen++
            assertTrue(e.onPlayer); assertEquals("par", e.status); assertEquals("Ratty was paralyzed!", e.text)
        }
        assertTrue(seen > 10)
        // sleep and toxic
        val hyp = (1..40).firstNotNullOf { s -> round(mon(19, 20, "tackle"), Battle(mon(150, 20, "hypnosis"), Tier.NORMAL), seed = s).fx(EffectKind.STATUS_APPLIED).firstOrNull() }
        assertEquals("slp", hyp.status)
    }

    @Test fun secondaryStatusIsApplied() {
        val b = Battle(mon(150, 60, "splash"), Tier.NORMAL)
        val ev = round(mon(19, 20, "burnhit"), b, acts = false)
        val e = ev.fx(EffectKind.STATUS_APPLIED).single()
        assertFalse(e.onPlayer); assertEquals("brn", e.status); assertEquals("brn", b.enemy.status)
    }

    @Test fun boostAndDropGiveSignedAppliedStages() {
        val b = Battle(mon(16, 20, "growl"), Tier.NORMAL)
        val g = round(mon(19, 20, "tackle"), b).fx(EffectKind.STAT_CHANGE).single()
        assertTrue(g.onPlayer); assertEquals("atk", g.stat); assertEquals(-1, g.stages); assertEquals("Ratty's Attack fell!", g.text)

        val b2 = Battle(mon(150, 100, "swordsdance"), Tier.NORMAL)
        val sd = round(mon(19, 5, "tackle"), b2).fx(EffectKind.STAT_CHANGE).single()
        assertFalse(sd.onPlayer); assertEquals("atk", sd.stat); assertEquals(2, sd.stages)
    }

    @Test fun threeStagesAndEveryStatKey() {
        val b = Battle(mon(150, 100, "bigdance"), Tier.NORMAL)
        val ch = round(mon(19, 5, "tackle"), b).fx(EffectKind.STAT_CHANGE)
        assertEquals(listOf("atk" to 3, "spe" to 1, "accuracy" to 1), ch.map { it.stat to it.stages })
        val b2 = Battle(mon(150, 100, "shine"), Tier.NORMAL)
        val ch2 = round(mon(19, 5, "tackle"), b2).fx(EffectKind.STAT_CHANGE)
        assertEquals(setOf("evasion" to -1, "spd" to -2, "spa" to -3, "def" to -1), ch2.map { it.stat to it.stages }.toSet())
    }

    @Test fun appliedStagesAreClampedAtTheLimitAndThenALimitEventFollows() {
        val b = Battle(mon(150, 100, "swordsdance"), Tier.NORMAL)
        b.enemy.boosts[0] = 5
        val one = round(mon(19, 5, "tackle"), b).fx(EffectKind.STAT_CHANGE).single()
        assertEquals(1, one.stages) // 5 -> 6
        val ev = round(mon(19, 5, "tackle"), b)
        assertTrue(ev.fx(EffectKind.STAT_CHANGE).isEmpty())
        val lim = ev.fx(EffectKind.STAT_LIMIT).single()
        assertEquals("atk", lim.stat); assertEquals(1, lim.stages); assertEquals("Mindlord's Attack won't go any higher!", lim.text)

        val b2 = Battle(mon(16, 20, "growl"), Tier.NORMAL); b2.player.boosts[0] = -6
        val low = round(mon(19, 20, "tackle"), b2).fx(EffectKind.STAT_LIMIT).single()
        assertEquals(-1, low.stages); assertEquals("atk", low.stat); assertTrue(low.onPlayer)
    }

    @Test fun secondaryBoostIsAStatChange() {
        val b = Battle(mon(150, 60, "splash"), Tier.NORMAL)
        val e = round(mon(19, 20, "hit2"), b, acts = false).fx(EffectKind.STAT_CHANGE).single()
        assertFalse(e.onPlayer); assertEquals("def", e.stat); assertEquals(-1, e.stages)
    }

    @Test fun cantMoveKinds() {
        var par = 0; var slp = 0; var frz = 0
        for (s in 1..300) {
            for (st in listOf("par", "slp", "frz")) {
                val b = Battle(mon(150, 50, "splash"), Tier.NORMAL); b.player.status = st
                val ev = round(mon(19, 20, "tackle"), b, acts = false, seed = s)
                val c = ev.fx(EffectKind.CANT_MOVE).firstOrNull() ?: continue
                assertTrue(c.onPlayer); assertEquals(st, c.status)
                when (st) {
                    "par" -> { par++; assertEquals("Ratty is fully paralyzed!", c.text) }
                    "slp" -> { slp++; assertEquals("Ratty is fast asleep.", c.text) }
                    else -> { frz++; assertEquals("Ratty is frozen solid!", c.text) }
                }
            }
        }
        assertTrue(par > 0 && slp > 0 && frz > 0)
    }

    @Test fun wakeAndThawAreCuredEvents() {
        var woke = 0; var thawed = 0
        for (s in 1..300) {
            for (st in listOf("slp", "frz")) {
                val b = Battle(mon(150, 50, "splash"), Tier.NORMAL); b.player.status = st
                val c = round(mon(19, 20, "tackle"), b, acts = false, seed = s).fx(EffectKind.STATUS_CURED).firstOrNull() ?: continue
                assertTrue(c.onPlayer); assertNull(c.status); assertEquals(st, c.stat)
                if (st == "slp") { woke++; assertEquals("Ratty woke up!", c.text) } else { thawed++; assertEquals("Ratty thawed out!", c.text) }
            }
        }
        assertTrue(woke > 0 && thawed > 0)
    }

    @Test fun thawByAFireHitIsACuredEvent() {
        val b = Battle(mon(150, 100, "splash"), Tier.NORMAL); b.enemy.status = "frz"
        val c = round(mon(19, 20, "flamewheel"), b, acts = false).fx(EffectKind.STATUS_CURED).single()
        assertFalse(c.onPlayer); assertEquals("frz", c.stat)
    }

    @Test fun drainRecoilAndHealCarryTheRealHpChange() {
        val main = mon(1, 20, "absorb"); main.hp = maxHp(main) - 10
        val start = main.hp
        val d = round(main, Battle(mon(150, 60, "splash"), Tier.NORMAL), acts = false).fx(EffectKind.DRAIN).single()
        assertTrue(d.onPlayer); assertEquals(main.hp - start, d.amount); assertTrue(d.amount in 1..10)
        // a full-HP user gains nothing: the amount is what really came back
        val full = mon(1, 20, "absorb")
        assertEquals(0, round(full, Battle(mon(150, 60, "splash"), Tier.NORMAL), acts = false).fx(EffectKind.DRAIN).single().amount)

        val rat = mon(19, 20, "doubleedge"); val h0 = rat.hp
        val r = round(rat, Battle(mon(150, 60, "splash"), Tier.NORMAL), acts = false).fx(EffectKind.RECOIL).single()
        assertEquals(h0 - rat.hp, r.amount); assertTrue(r.amount > 0)

        val wild = mon(150, 50, "recover"); wild.hp = 10
        val h = round(mon(150, 50, "splash"), Battle(wild, Tier.NORMAL)).fx(EffectKind.HEAL).single()
        assertFalse(h.onPlayer); assertEquals(maxHp(wild) / 2, h.amount)
    }

    @Test fun struggleRecoilIsRecoil() {
        val m = mon(19, 20, "growl")
        val ev = round(m, Battle(mon(150, 60, "splash"), Tier.NORMAL), acts = false)
        assertEquals(1, ev.fx(EffectKind.RECOIL).size)
    }

    @Test fun burnAndPoisonTicksAreStatusDamage() {
        for ((st, div) in listOf("brn" to 16, "psn" to 8, "tox" to 16)) {
            val m = mon(19, 40, "tackle"); val b = Battle(mon(150, 60, "splash"), Tier.NORMAL); b.player.status = st
            val h0 = m.hp
            val e = round(m, b, acts = false).fx(EffectKind.STATUS_DAMAGE).single()
            assertTrue(e.onPlayer); assertEquals(st, e.status)
            assertEquals(h0 - m.hp, e.amount); assertEquals(maxOf(1, maxHp(m) / div), e.amount)
        }
        val w = mon(150, 60, "splash"); val wb = Battle(w, Tier.NORMAL); wb.enemy.status = "brn"
        val e = round(mon(19, 5, "tackle"), wb, acts = false).fx(EffectKind.STATUS_DAMAGE).single()
        assertFalse(e.onPlayer)
    }

    @Test fun otherEffectsStayOther() {
        val ev = round(mon(19, 5, "tackle"), Battle(mon(150, 100, "splash"), Tier.NORMAL))
        val nothing = ev.filterIsInstance<Effect>().single { it.text == "But nothing happened." }
        assertEquals(EffectKind.OTHER, nothing.kind)
    }
}
