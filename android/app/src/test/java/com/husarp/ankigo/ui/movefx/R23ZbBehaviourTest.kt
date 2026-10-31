package com.husarp.ankigo.ui.movefx

import com.husarp.ankigo.game.Category
import com.husarp.ankigo.game.Type
import org.junit.Assert.*
import org.junit.Test

class R23ZbBehaviourTest {
    @Test fun metadataSelectsBothGroupsInEveryType() {
        for (type in Type.entries) {
            val multi = MoveFxCatalog.plan("any-data-defined-move", type, Category.SPECIAL, 20, multiHit = true, hits = 7)
            assertEquals(MoveLook.MULTI_HIT, multi.look)
            assertEquals(7, multi.hitCount)
            assertEquals(FxPalettes.of(type), multi.palette)
            val recoil = MoveFxCatalog.plan("any-data-defined-move", type, Category.PHYSICAL, 100, recoil = true)
            assertEquals(MoveLook.RECOIL, recoil.look)
            assertEquals(FxPalettes.of(type), recoil.palette)
        }
    }

    @Test fun oneLayeredSlashPerActualHitIncludingEarlyKnockoutAndTenHits() {
        for (count in listOf(0, 1, 2, 3, 5, 10)) for (reverse in listOf(false, true)) {
            val stage = RecStage(reverse = reverse)
            val p = plan(MoveLook.MULTI_HIT).copy(hitCount = count)
            val script = MoveFx.script(stage, p, true, 7)!!
            val times = MoveFx.build(script, p.look)
            for (i in 0 until count) {
                val at = BehaviourFx.hitTime(i)
                script.advance(at)
                assertEquals(3, script.particles.count { it.img == "slash_03" && it.t0 == at })
            }
            if (count == 0) {
                script.advance(BehaviourFx.FIRST_HIT)
                assertTrue(script.particles.none { it.img == "slash_03" })
            }
            script.advance(times.done)
            assertTrue(times.done > BehaviourFx.hitTime(count - 1))
            assertEquals(0f, stage.last(true)!!.dx, .001f)
        }
    }

    @Test fun preventedRecoilHasNoRedFlashOrUserShakeAndActualRecoilReturnsToRest() {
        for (enabled in listOf(false, true)) for (reverse in listOf(false, true)) {
            val stage = RecStage(reverse = reverse)
            val p = plan(MoveLook.RECOIL).copy(showRecoil = enabled)
            val script = MoveFx.script(stage, p, true, 2)!!
            val times = MoveFx.build(script, p.look)
            script.advance(690f)
            val before = stage.poses.size
            script.advance(740f)
            assertEquals(enabled, stage.poses.drop(before).any { it.attacker && it.matrix != null })
            assertEquals(enabled, script.particles.any { it.tint == 0xFFFF6A5A.toInt() })
            script.advance(times.done)
            assertEquals(0f, stage.last(true)!!.dx, .001f)
            assertNull(stage.last(true)!!.matrix)
        }
    }
}
