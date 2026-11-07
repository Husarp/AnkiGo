package com.husarp.ankigo.ui.movefx

import com.husarp.ankigo.ui.ArenaMoveStage
import org.junit.Assert.*
import org.junit.Test

class R30eTimelineTest {
    private fun times(look: MoveLook): FxTimes {
        val script = MoveFx.script(RecStage(), plan(look), true, 7)!!
        return MoveFx.build(script, look)
    }

    @Test fun typicalTurnDurationsCoverRecoveryAndOverlapFadingParticles() {
        val tackle = times(MoveLook.TACKLE)
        val drain = times(MoveLook.DRAIN)
        val heal = times(MoveLook.HEAL)
        val stat = times(MoveLook.AURA_TEMPLATE)
        val g = ArenaMoveStage.Geo
        val reply = g.waitMs(tackle.done.toLong(), tackle.impact.toLong(), 1.0)
        assertEquals(690L, reply)
        assertEquals(1630L, 250 + reply * 2)
        assertEquals(2530L, 250 + g.waitMs(drain.done.toLong(), BehaviourFx.DRAIN_HEAL_AT.toLong(), 1.0) + reply)
        assertEquals(1690L, 250 + g.waitMs(heal.done.toLong(), heal.impact.toLong(), 1.0) + reply)
        assertEquals(1840L, 250 + g.waitMs(stat.done.toLong(), stat.impact.toLong(), 1.0) + reply)
        assertEquals(460f, drain.impact, .01f)
        assertEquals(150f, heal.impact, .01f)
        assertTrue(drain.total > drain.done)
        assertTrue(heal.total > heal.done)
    }

    @Test fun healingGlowReleasesAtRestAtBothPaces() {
        for (look in listOf(MoveLook.HEAL, MoveLook.DRAIN)) for (speed in listOf(1f, 2f)) {
            val stage = RecStage()
            val script = MoveFx.script(stage, plan(look), true, 7)!!
            val times = MoveFx.build(script, look)
            val player = MoveFxPlayer(stage, script, times, speed) { error("no canvas") }
            player.step(player.doneMs * speed)
            assertEquals(1, stage.restores)
            assertNull(stage.last(true)!!.matrix)
            assertEquals(times.impact / speed, player.impactMs.toFloat(), .5f)
        }
    }
}
