package com.husarp.ankigo.ui.movefx

import com.husarp.ankigo.game.Category
import com.husarp.ankigo.game.Type
import org.junit.Assert.*
import org.junit.Test

class R53AiAttackTest {
    @Test fun approvedMovesAndExistingFamiliesUseTheNewRenderer() {
        val cases = listOf(
            Triple("flamethrower", Type.FIRE, MoveLook.EMBER), Triple("watergun", Type.WATER, MoveLook.WATER_JET),
            Triple("thunderbolt", Type.ELECTRIC, MoveLook.THUNDERBOLT), Triple("shadowball", Type.GHOST, MoveLook.SHADOW_BALL),
            Triple("psychic", Type.PSYCHIC, MoveLook.PSYCHIC), Triple("icebeam", Type.ICE, MoveLook.ICE_BEAM),
            Triple("earthquake", Type.GROUND, MoveLook.EARTHQUAKE), Triple("vinewhip", Type.GRASS, MoveLook.VINE_WHIP),
            Triple("tackle", Type.NORMAL, MoveLook.TACKLE), Triple("gigadrain", Type.GRASS, MoveLook.DRAIN))
        for ((id, type, look) in cases) {
            val category = if (look in setOf(MoveLook.TACKLE, MoveLook.VINE_WHIP, MoveLook.EARTHQUAKE)) Category.PHYSICAL else Category.SPECIAL
            assertEquals(id, look, MoveFxCatalog.plan(id, type, category, 90).look)
            assertTrue(look in AiAttackFx.looks)
            if (look != MoveLook.DRAIN) assertEquals(look, MoveFxCatalog.plan("samefamily", type, category, 65).look)
        }
        for (id in MoveFxCatalog.drains) assertEquals(id, MoveLook.DRAIN, MoveFxCatalog.plan(id, Type.NORMAL, Category.PHYSICAL, 60).look)
        assertEquals(MoveLook.MULTI_HIT, MoveFxCatalog.plan("bulletseed", Type.GRASS, Category.PHYSICAL, 25, multiHit = true).look)
        assertEquals(MoveLook.RECOIL, MoveFxCatalog.plan("flareblitz", Type.FIRE, Category.PHYSICAL, 120, recoil = true).look)
    }

    @Test fun approvedImpactsMatchTheDesignAndReleaseBothDirectionsAtBothPaces() {
        val impacts = mapOf(MoveLook.TACKLE to 300f, MoveLook.THUNDERBOLT to 200f, MoveLook.SHADOW_BALL to 700f,
            MoveLook.PSYCHIC to 380f, MoveLook.ICE_BEAM to 230f, MoveLook.VINE_WHIP to 340f, MoveLook.EARTHQUAKE to 80f, MoveLook.DRAIN to 460f)
        for (look in AiAttackFx.looks) for (reverse in listOf(false, true)) for (react in listOf(false, true)) for (speed in listOf(1f, 2f)) {
            val stage = RecStage(1.5f, reverse)
            val script = MoveFx.script(stage, plan(look), react, 53L)!!
            val times = MoveFx.build(script, look)
            impacts[look]?.let { assertEquals(look.name, it, times.impact, .001f) }
            assertTrue(times.impact <= times.done && times.done < times.total)
            val player = MoveFxPlayer(stage, script, times, speed) { error("JVM test") }
            player.step(0f)
            player.step(times.done + 1f)
            val count = stage.poses.size
            assertEquals(1, stage.restores)
            player.step(times.total - 1f)
            assertEquals(count, stage.poses.size)
            player.end()
            assertEquals(1, stage.restores)
            if (!react) assertEquals(0, stage.count(false))
            assertEquals(times.impact / speed, player.impactMs.toFloat(), .51f)
        }
    }

    @Test fun tintFramesArePreparedBeforePlaybackAndReusedByEveryPose() {
        for (look in AiAttackFx.looks) {
            val rec = RecStage()
            val prepared = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<FloatArray, Boolean>())
            val stage = object : MoveFxStage by rec {
                override fun prepareFilter(attacker: Boolean, matrix: FloatArray) { prepared.add(matrix) }
                override fun pose(attacker: Boolean, dx: Float, dy: Float, sx: Float, sy: Float, matrix: FloatArray?) {
                    if (matrix != null) assertTrue("$look filter was prepared", prepared.contains(matrix))
                    rec.pose(attacker, dx, dy, sx, sy, matrix)
                }
            }
            val script = MoveFx.script(stage, plan(look), true, 53)!!
            val times = MoveFx.build(script, look)
            val count = prepared.size
            var ms = 0f
            while (ms <= times.done) { script.advance(ms); ms += 17f }
            assertEquals(count, prepared.size)
            if (look != MoveLook.EARTHQUAKE) assertTrue("$look has a tint", count > 0)
        }
    }

    @Test fun cubeUsesVisibleBodyPlus41PercentAndTheApprovedAspectAndClamps() {
        assertEquals(98.7f, IceCubeFit.width(70f, 70f), .001f)
        assertEquals(98.7f, IceCubeFit.height(70f, 70f), .001f)
        assertEquals(141f, IceCubeFit.width(100f, 20f), .001f)
        assertEquals(112.8f, IceCubeFit.height(100f, 20f), .001f)
        assertEquals(112.8f, IceCubeFit.width(20f, 100f), .001f)
        assertEquals(141f, IceCubeFit.height(20f, 100f), .001f)
        for ((w, h) in listOf(0f to 0f, 1f to 1000f, 1000f to 1f, 500f to 500f, 34f to 88f)) {
            val cw = IceCubeFit.width(w, h); val ch = IceCubeFit.height(w, h)
            assertTrue(cw in 50f..190f && ch in 50f..190f)
            assertTrue(cw / ch in .7999f..1.2501f)
        }
        // Device scale changes the final pixels, never the fit in design coordinates.
        val body = FxBody(20f, 40f, 70f * 2f, 90f * 2f)
        assertEquals(IceCubeFit.width(70f, 90f) * 2f, IceCubeFit.width(body.w / 2f, body.h / 2f) * 2f, 0f)
    }

    @Test fun frozenHoldsCracksShattersAndNeverRestartsAfterCureOrDeparture() {
        val state = ConditionLookState()
        state.set("frz", 10)
        assertEquals(ConditionLookState.FrozenPhase.HOLD, state.frozenPhase(1_000_000))
        state.set(null, 1000)
        assertEquals(ConditionLookState.FrozenPhase.CRACK, state.frozenPhase(1439))
        assertEquals(ConditionLookState.FrozenPhase.SHATTER, state.frozenPhase(1440))
        assertEquals(ConditionLookState.FrozenPhase.HIDDEN, state.frozenPhase(2000))
        state.advance(2000)
        assertFalse(state.visible)
        state.set("frz", 3000)
        state.set(null, 3100, immediate = true)
        assertEquals(ConditionLookState.FrozenPhase.HIDDEN, state.frozenPhase(3200))
    }

    @Test fun onlyApprovedEarthAndWaterImpactFramesArePackaged() {
        assertNull(AiSheet.EQ_FX_A.frames[0]); assertNull(AiSheet.EQ_FX_A.frames[1])
        assertNotNull(AiSheet.EQ_FX_A.frames[2]); assertNotNull(AiSheet.EQ_FX_A.frames[3])
        assertEquals(1, AiSheet.WATER_IMP3.frames.count { it != null })
        assertEquals(10, AiSheet.WATER_SPL5.frames.count { it != null })
    }
}
