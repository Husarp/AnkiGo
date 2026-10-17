package com.husarp.ankigo.ui.movefx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** R7-FIX 1: once a look is done it hands the sprites back ONCE and never touches them again. Pure JVM (the renderer is never made). */
class MoveFxPlayerTest {
    private class Run(val stage: RecStage, val script: FxScript, val times: FxTimes, val player: MoveFxPlayer)

    private fun run(look: MoveLook = MoveLook.TACKLE, speed: Float = 1f, react: Boolean = true): Run {
        val stage = RecStage(2f)
        val p = plan(look)
        val s = MoveFx.script(stage, p, react, 5L)!!
        val t = MoveFx.build(s, look)
        return Run(stage, s, t, MoveFxPlayer(stage, s, t, speed) { error("no canvas in a JVM test") })
    }

    @Test fun theStageIsRestoredOnceAtDoneAndNeverPosedAgain() {
        val r = run()
        var ms = 0f
        while (ms < r.times.done - 1f) { assertTrue(r.player.step(ms)); assertEquals(0, r.stage.restores); ms += 16f }
        assertTrue(r.player.step(r.times.done))
        assertEquals("handed back at done", 1, r.stage.restores)
        val posesThen = r.stage.poses.size
        while (ms < r.times.total) { assertTrue(r.player.step(ms)); ms += 16f }
        assertEquals("not a single pose after the hand-back", posesThen, r.stage.poses.size)
        assertEquals(posesThen, r.stage.posesAtRestore)
        assertEquals(1, r.stage.restores)
    }

    @Test fun aLateFrameThatJumpsOverDoneDoesNotRunOverduePoses() {
        val r = run()
        r.player.step(100f)
        val before = r.stage.poses.size
        assertTrue(r.player.step(r.times.total - 1f))                    // one frame, from 100 ms to the end of the particles
        assertEquals(1, r.stage.restores)
        // the poses of the jump all happened before the restore (up to done), none after it
        assertEquals(r.stage.poses.size, r.stage.posesAtRestore)
        assertTrue(r.stage.poses.size > before)
        // the last pose before the hand-back is at rest: tweens end at p = 1 at done
        val a = r.stage.last(true)!!
        assertEquals(0f, a.dx, 0.5f); assertEquals(0f, a.dy, 0.5f)
    }

    @Test fun endAfterDoneRestoresNothingMore() {
        val r = run()
        r.player.step(r.times.done + 5f)
        assertEquals(1, r.stage.restores)
        val poses = r.stage.poses.size
        r.player.end(); r.player.end()
        assertEquals("end() after done does not reset the sprites again", 1, r.stage.restores)
        assertEquals(poses, r.stage.poses.size)
    }

    @Test fun endBeforeDoneRestoresOnceAndLaterStepsDoNothing() {
        val r = run()
        r.player.step(100f)
        r.player.end()
        assertEquals(1, r.stage.restores)
        val poses = r.stage.poses.size
        r.player.step(r.times.done + 10f)                                // a frame that still came after the end (it cannot pose)
        assertEquals(poses, r.stage.poses.size)
        assertEquals(1, r.stage.restores)
    }

    @Test fun aSpriteAnotherAnimationMovedIsLeftAlone() {
        // a stage with real state: restore() zeroes the offset, like the arena's; then a "faint" moves the sprite
        class Sprite : MoveFxStage {
            val rec = RecStage(2f)
            override val attackerX get() = rec.attackerX; override val attackerY get() = rec.attackerY
            override val targetX get() = rec.targetX; override val targetY get() = rec.targetY
            override val unit get() = rec.unit
            var ty = 0f
            override fun pose(attacker: Boolean, dx: Float, dy: Float, sx: Float, sy: Float, matrix: FloatArray?) { if (!attacker) ty = dy }
            override fun jolt(dy: Float) = rec.jolt(dy)
            override fun restore() { ty = 0f; rec.restore() }
        }
        val stage = Sprite()
        val s = MoveFx.script(stage, plan(MoveLook.TACKLE), true, 5L)!!
        val t = MoveFx.build(s, MoveLook.TACKLE)
        val player = MoveFxPlayer(stage, s, t, 1f) { error("no canvas") }
        player.step(t.done + 1f)
        assertEquals(1, stage.rec.restores)
        stage.ty = 40f                                                   // the next event: the faint sinks the sprite
        player.step(t.done + 100f)
        player.step(t.total - 1f)
        player.end()
        assertEquals("the faint keeps its offset", 40f, stage.ty, 0f)
    }

    @Test fun anEarthquakeNeverJoltsAfterTheHandBack() {
        val r = run(MoveLook.EARTHQUAKE)
        var ms = 0f
        while (ms < r.times.total) { r.player.step(ms); ms += 8f }
        assertTrue("it did jolt", r.stage.jolts > 0)
        assertEquals(r.stage.jolts, r.stage.joltsAtRestore)
        assertEquals(1, r.stage.restores)
    }

    @Test fun everyLookReleasesOnceAndPosesNothingAfter() {
        for (look in MoveLook.entries) for (react in listOf(true, false)) {
            val r = run(look, react = react)
            var ms = 0f
            while (r.player.step(ms)) ms += 37f                           // coarse, jumpy frames
            assertEquals("$look", 1, r.stage.restores)
            assertEquals("$look", r.stage.poses.size, r.stage.posesAtRestore)
            r.player.end()
            assertEquals("$look", 1, r.stage.restores)
        }
    }

    @Test fun stepEndsAtTotalAndTheScriptReportsRelease() {
        val r = run()
        assertFalse(r.script.stageReleased)
        assertTrue(r.player.step(r.times.total - 1f))
        assertTrue(r.script.stageReleased)
        assertFalse(r.player.step(r.times.total))
    }
}
