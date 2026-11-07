package com.husarp.ankigo.ui.movefx

import com.husarp.ankigo.game.Category
import com.husarp.ankigo.game.Type
import org.junit.Assert.*
import org.junit.Test

class R21BehaviourTest {
    @Test fun pickedMovesAndApprovedFamiliesGetBehaviour() {
        val cases = mapOf("absorb" to MoveLook.DRAIN, "recover" to MoveLook.HEAL, "furyswipes" to MoveLook.MULTI_HIT, "doubleedge" to MoveLook.RECOIL)
        cases.forEach { (id, look) -> assertEquals(look, MoveFxCatalog.plan(id, Type.NORMAL, Category.SPECIAL, 80).look) }
        assertEquals(MoveLook.AURA_TEMPLATE, MoveFxCatalog.plan("growl", Type.NORMAL, Category.STATUS, 0).look)
        assertEquals(MoveLook.DRAIN, MoveFxCatalog.plan("gigadrain", Type.GRASS, Category.SPECIAL, 75).look)
    }

    @Test fun everyNewLookRestoresAndHasNoLatePosesInBothDirections() {
        for (look in listOf(MoveLook.DRAIN, MoveLook.HEAL, MoveLook.MULTI_HIT, MoveLook.RECOIL)) for (reverse in listOf(false, true)) {
            val st = RecStage(2f, reverse)
            val script = MoveFx.script(st, plan(look), true, 7)!!
            val t = MoveFx.build(script, look)
            var ms = 0f
            while (ms < t.done) { script.advance(ms); ms += 10f }
            script.advance(t.done)
            assertEquals("$look dx", 0f, st.last(true)?.dx ?: 0f, .01f)
            script.releaseStage()
            val count = st.poses.size
            script.advance(t.total)
            assertEquals(count, st.poses.size)
        }
    }

    @Test fun selfHealingAndStatusAurasNeverMoveTheUserTowardTheTarget() {
        for (look in listOf(MoveLook.HEAL, MoveLook.AURA_TEMPLATE)) {
            val st = RecStage()
            val s = MoveFx.script(st, plan(look), true, 1)!!
            MoveFx.build(s, look)
            for (ms in 0..2000 step 10) s.advance(ms.toFloat())
            assertTrue(st.poses.filter { it.attacker }.all { it.dx == 0f && it.dy == 0f })
        }
    }

    @Test fun drainReturnsLightToTheUserAndSwipesHaveFourLayeredImpacts() {
        val st = RecStage()
        val s = MoveFx.script(st, plan(MoveLook.DRAIN), true, 3)!!
        MoveFx.build(s, MoveLook.DRAIN)
        s.advance(540f)
        assertTrue(s.procs.any { it.s == 540f && it.e == 980f })
        s.advance(1050f)
        assertTrue(s.particles.any { it.img == "circle_02" && it.x0 == st.attackerX })
        val multi = MoveFx.script(st, plan(MoveLook.MULTI_HIT), true, 3)!!
        MoveFx.build(multi, MoveLook.MULTI_HIT)
        var slashes = 0
        for (ms in listOf(380f, 630f, 880f, 1130f)) {
            multi.advance(ms)
            slashes += multi.particles.count { it.img == "slash_03" && it.t0 == ms }
        }
        assertEquals(12, slashes) // three layers for each of four real impacts
    }

    @Test fun allNineMissingTypesHaveTheirOwnTintedTextures() {
        for (t in listOf(Type.FIGHTING, Type.POISON, Type.FLYING, Type.BUG, Type.ROCK, Type.DRAGON, Type.DARK, Type.STEEL, Type.FAIRY)) {
            val p = MoveFxCatalog.plan("", t, Category.PHYSICAL, 40)
            assertEquals(t.name, p.kit.name)
            val s = MoveFx.script(RecStage(), p, true, 4)!!
            MoveFx.build(s, p.look); s.advance(310f)
            assertTrue(t.name, s.particles.isNotEmpty())
            assertTrue(t.name, s.particles.all { it.tint in listOf(p.palette.glow, p.palette.spark, p.palette.deep, p.palette.smoke) })
        }
    }
}
