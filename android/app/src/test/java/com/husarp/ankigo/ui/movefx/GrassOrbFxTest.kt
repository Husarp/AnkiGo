package com.husarp.ankigo.ui.movefx

import com.husarp.ankigo.game.Type
import org.junit.Assert.*
import org.junit.Test

class GrassOrbFxTest {
    private val grass = MoveFxPlan(MoveLook.ORB_TEMPLATE, 1.6f, FxKit.GRASS, FxPalettes.of(Type.GRASS))

    @Test fun tailStartsAtAttackerAndNeverRunsBehindItOrAheadOfOrb() {
        for (unit in listOf(.4f, 1f, 3f)) for (reverse in listOf(false, true)) for (flipped in listOf(false, true)) {
            val stage = RecStage(unit, reverse)
            val ax = if (flipped) 480f * unit - stage.attackerX else stage.attackerX
            val tx = if (flipped) 480f * unit - stage.targetX else stage.targetX
            val space = FxSpace(ax, stage.attackerY, tx, stage.targetY, unit)
            val out = FloatArray(2)
            val dx = tx - ax; val dy = stage.targetY - stage.attackerY
            for (i in 0..18) {
                assertFalse(GrassOrbFx.trailPoint(space, i * .028f - .001f, i, out))
                assertTrue(GrassOrbFx.trailPoint(space, i * .028f, i, out))
                assertEquals(ax, out[0], .001f); assertEquals(stage.attackerY, out[1], .001f)
                for (frame in 0..100) {
                    val p = frame / 100f
                    if (!GrassOrbFx.trailPoint(space, p, i, out)) continue
                    val along = ((out[0] - ax) * dx + (out[1] - stage.attackerY) * dy) / (dx * dx + dy * dy)
                    assertTrue("tail behind attacker: $along", along >= -.00001f)
                    assertTrue("tail ahead of orb: $along", along <= p * p + .00001f)
                }
            }
            GrassOrbFx.trailPoint(space, 1f, 0, out)
            assertEquals(tx, out[0], .001f); assertEquals(stage.targetY, out[1], .001f)
        }
    }

    @Test fun horizontalFlipMirrorsTheWholeTrail() {
        val normal = FxSpace(100f, 200f, 340f, 80f, 1f)
        val flipped = FxSpace(380f, 200f, 140f, 80f, 1f)
        val a = FloatArray(2); val b = FloatArray(2)
        for (i in 0..18) for (frame in 0..100) {
            val p = frame / 100f
            if (!GrassOrbFx.trailPoint(normal, p, i, a)) continue
            GrassOrbFx.trailPoint(flipped, p, i, b)
            assertEquals(480f - a[0], b[0], .001f); assertEquals(a[1], b[1], .001f)
        }
    }

    @Test fun grassRoutingHasAnImpactRingAndAuraAndCleansUpWithinBudget() {
        for (reverse in listOf(false, true)) for (react in listOf(false, true)) {
            val stage = RecStage(reverse = reverse)
            val s = MoveFx.script(stage, grass, react, 7L)!!
            val t = MoveFx.build(s, grass.look)
            assertEquals(GrassOrbFx.IMPACT, t.impact, 0f)
            var peak = 0
            for (ms in 0..1300 step 10) {
                s.advance(ms.toFloat())
                peak = maxOf(peak, s.particles.size)
                if (ms < 680) assertTrue(s.particles.isEmpty())
                if (ms == 680) {
                    val ring = s.particles.single { it.img == "circle_02" }
                    assertEquals(stage.targetX, ring.x0, 0f); assertEquals(stage.targetY, ring.y0, 0f)
                    assertTrue(ring.s1 > ring.s0)
                    assertTrue(s.procs.any { it.s == t.impact && it.e == t.impact + 580f })
                }
            }
            assertEquals(11, peak)
            assertTrue(s.particles.isEmpty()); assertTrue(s.procs.isEmpty())
            assertEquals(0f, s.dim, 0f); assertEquals(0f, s.flashAlpha, 0f)
            if (!react) assertEquals(0, stage.count(false))
            else { assertTrue(stage.count(false) > 0); assertEquals(0f, stage.last(false)!!.dx, .001f) }
        }
    }

    @Test fun grassSpecialMovesReachStormWhileOtherGrassFamiliesKeepTheirLooks() {
        for (id in "appleacid energyball frenzyplant grassknot grasspledge hiddenpowergrass leafstorm leaftornado magicalleaf petaldance seedflare solarbeam syrupbomb".split(" ")) {
            val p = MoveFxCatalog.plan(id, Type.GRASS, com.husarp.ankigo.game.Category.SPECIAL, 90)
            assertEquals(id, MoveLook.ORB_TEMPLATE, p.look)
            val s = MoveFx.script(RecStage(), p, true, 7L)!!
            assertEquals(id, GrassOrbFx.IMPACT, MoveFx.build(s, p.look).impact, 0f)
        }
        for (id in listOf("absorb", "megadrain", "gigadrain", "matchagotcha"))
            assertEquals(MoveLook.DRAIN, MoveFxCatalog.plan(id, Type.GRASS, com.husarp.ankigo.game.Category.SPECIAL, 60).look)
        assertEquals(MoveLook.RECOIL, MoveFxCatalog.plan("chloroblast", Type.GRASS, com.husarp.ankigo.game.Category.SPECIAL, 150, recoil = true).look)
        assertEquals(MoveLook.VINE_WHIP, MoveFxCatalog.plan("razorleaf", Type.GRASS, com.husarp.ankigo.game.Category.PHYSICAL, 55).look)
        assertEquals(MoveLook.AURA_TEMPLATE, MoveFxCatalog.plan("growth", Type.GRASS, com.husarp.ankigo.game.Category.STATUS, 0).look)
    }

    @Test fun otherOrbsKeepTheirExistingLook() {
        for (kit in FxKit.entries.filter { it != FxKit.GRASS }) {
            val p = grass.copy(kit = kit)
            val s = MoveFx.script(RecStage(), p, true, 7L)!!
            assertEquals(ShadowBallFx.IMPACT, MoveFx.build(s, p.look).impact, 0f)
        }
    }
}
