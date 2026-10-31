package com.husarp.ankigo.ui.movefx

import com.husarp.ankigo.game.Category
import com.husarp.ankigo.game.Type
import org.junit.Assert.*
import org.junit.Test

class R23FamiliesTest {
    @Test fun entireApprovedFamiliesUseFixedSizeAndTypePalette() {
        assertEquals(13, MoveFxCatalog.drains.size)
        assertEquals(17, MoveFxCatalog.heals.size)
        for ((ids, look) in listOf(MoveFxCatalog.drains to MoveLook.DRAIN, MoveFxCatalog.heals to MoveLook.HEAL)) {
            for (id in ids) for (type in Type.values()) {
                val p = MoveFxCatalog.plan(id, type, Category.STATUS, 150)
                assertEquals(id, look, p.look)
                assertEquals(1f, p.k, 0f)
                assertEquals(FxPalettes.of(type), p.palette)
            }
        }
    }

    @Test fun excludedFamiliesAndSacrificesKeepTheirNormalEffects() {
        assertEquals(MoveLook.HIT_TEMPLATE, MoveFxCatalog.plan("doublekick", Type.FIGHTING, Category.PHYSICAL, 30).look)
        assertEquals(MoveLook.HIT_TEMPLATE, MoveFxCatalog.plan("bravebird", Type.FLYING, Category.PHYSICAL, 120).look)
        for (id in listOf("healingwish", "lunardance", "memento"))
            assertEquals(MoveLook.AURA_TEMPLATE, MoveFxCatalog.plan(id, Type.NORMAL, Category.STATUS, 0).look)
        assertEquals(MoveLook.TACKLE, MoveFxCatalog.plan("finalgambit", Type.NORMAL, Category.PHYSICAL, 0).look)
        for (id in listOf("explosion", "selfdestruct", "mistyexplosion"))
            assertEquals(MoveLook.BLAST, MoveFxCatalog.plan(id, Type.FAIRY, Category.PHYSICAL, 250).look)
    }

    @Test fun preparationStaysStillAndDoesNotHitInEitherDirection() {
        for (reverse in listOf(false, true)) for (type in Type.values()) {
            val st = RecStage(reverse = reverse)
            val p = MoveFxCatalog.preparation(type)
            val s = MoveFx.script(st, p, true, 2)!!
            val times = MoveFx.build(s, p.look)
            for (ms in 0..400 step 10) s.advance(ms.toFloat())
            assertTrue(st.poses.isEmpty())
            assertTrue(s.particles.any { it.tint == p.palette.glow })
            assertEquals(900f, times.done, 0f)
            s.releaseStage()
            assertEquals(1, st.restores)
        }
    }

    @Test fun blastIsStationaryAndRespectsNoTargetReaction() {
        val st = RecStage()
        val s = MoveFx.script(st, plan(MoveLook.BLAST), false, 3)!!
        val times = MoveFx.build(s, MoveLook.BLAST)
        s.advance(180f)
        assertTrue(s.flashAlpha > 0f)
        assertEquals(2, s.particles.size)
        s.advance(times.done)
        assertTrue(st.poses.isEmpty())
        s.releaseStage()
        s.advance(times.total)
        assertEquals(1, st.restores)
    }
}
