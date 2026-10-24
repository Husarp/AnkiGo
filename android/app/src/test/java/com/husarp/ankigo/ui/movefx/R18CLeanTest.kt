package com.husarp.ankigo.ui.movefx

import com.husarp.ankigo.game.Category
import com.husarp.ankigo.game.Type
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** R18-C (PLAN 228): the user of a special or status move leans a little toward the target and back; physical moves are untouched. Pure JVM. */
class R18CLeanTest {
    private val unit = 2f
    private val eps = 1e-3f

    private fun run(look: MoveLook, reverse: Boolean = false): Pair<RecStage, FxScript> {
        val stage = RecStage(unit, reverse)
        val s = MoveFx.script(stage, plan(look), true, 7L)!!
        MoveFx.build(s, look)
        return stage to s
    }

    /** The attacker's dx after each frame up to [to] ms. */
    private fun dxs(look: MoveLook, to: Float, reverse: Boolean = false, step: Float = 5f): List<Float> {
        val (stage, s) = run(look, reverse)
        val out = ArrayList<Float>()
        var ms = 0f
        while (ms <= to) { s.advance(ms); stage.last(true)?.let { out += it.dx }; ms += step }
        return out
    }

    @Test fun specialAndStatusLooksLeanPhysicalOnesDoNot() {
        val lean = listOf(MoveLook.EMBER, MoveLook.WATER_JET, MoveLook.THUNDERBOLT, MoveLook.ICE_BEAM, MoveLook.PSYCHIC, MoveLook.SHADOW_BALL, MoveLook.ORB_TEMPLATE, MoveLook.AURA_TEMPLATE)
        val still = listOf(MoveLook.TACKLE, MoveLook.VINE_WHIP, MoveLook.EARTHQUAKE, MoveLook.HIT_TEMPLATE)
        for (l in lean) assertTrue("$l", FxLean.leans(l))
        for (l in still) assertFalse("$l", FxLean.leans(l))
        assertEquals(MoveLook.entries.toSet(), (lean + still).toSet())
    }

    @Test fun theCatalogGivesSpecialAndStatusMovesALeaningLookAndPhysicalOnesNot() {
        for (t in Type.entries) for (power in listOf(0, 40, 90)) {
            assertFalse("$t physical", FxLean.leans(MoveFxCatalog.plan("", t, Category.PHYSICAL, power).look))
            assertTrue("$t special", FxLean.leans(MoveFxCatalog.plan("", t, Category.SPECIAL, power).look))
            assertTrue("$t status", FxLean.leans(MoveFxCatalog.plan("", t, Category.STATUS, 0).look))
        }
        for ((id, cat) in listOf("tackle" to Category.PHYSICAL, "vinewhip" to Category.PHYSICAL, "earthquake" to Category.PHYSICAL, "ember" to Category.SPECIAL, "psychic" to Category.SPECIAL, "shadowball" to Category.SPECIAL)) {
            assertEquals(id, cat != Category.PHYSICAL, FxLean.leans(MoveFxCatalog.plan(id, null, cat, 40).look))
        }
        // the plain fallback ("Attack animations" off) follows the category
        assertFalse(FxLean.leansCategory(Category.PHYSICAL))
        assertTrue(FxLean.leansCategory(Category.SPECIAL)); assertTrue(FxLean.leansCategory(Category.STATUS))
    }

    @Test fun theLeanCurveIsSmallEasedAndEndsAtRest() {
        assertEquals(0f, FxLean.at(0f), eps)
        assertEquals(FxLean.UNITS, FxLean.at(FxLean.OUT_MS), eps)
        assertEquals(0f, FxLean.at(FxLean.OUT_MS + FxLean.BACK_MS), eps)
        assertEquals(0f, FxLean.at(5000f), 0f)
        var prev = 0f
        var ms = 0f
        while (ms <= FxLean.OUT_MS) { val v = FxLean.at(ms); assertTrue(v >= prev - eps); assertTrue(v <= FxLean.UNITS + eps); prev = v; ms += 2f }
        while (ms <= FxLean.OUT_MS + FxLean.BACK_MS) { val v = FxLean.at(ms); assertTrue(v <= prev + eps); assertTrue(v >= -eps); prev = v; ms += 2f }
        assertTrue("about 250 ms", FxLean.OUT_MS + FxLean.BACK_MS in 200f..300f)
        assertTrue("much smaller than the 14 dp lunge", FxLean.UNITS <= 6f && FxLean.DP <= 6f)
    }

    @Test fun theOrbTemplateUserLeansTowardTheTargetAndIsBackAtRest() {
        val xs = dxs(MoveLook.ORB_TEMPLATE, 900f)
        assertTrue(xs.max() > FxLean.UNITS * unit * 0.9f)                  // it really moves
        for (x in xs) { assertTrue(x <= FxLean.UNITS * unit + eps); assertTrue(x >= -eps) }   // never more than the constant, never away
        val (stage, s) = run(MoveLook.ORB_TEMPLATE)
        s.advance(FxLean.OUT_MS + FxLean.BACK_MS + 10f)
        assertEquals(0f, stage.last(true)!!.dx, eps)
        s.advance(900f)
        assertEquals(0f, stage.last(true)!!.dx, eps)
        assertEquals(0f, stage.last(true)!!.dy, eps)
    }

    @Test fun theWildOneLeansTheOtherWay() {
        val xs = dxs(MoveLook.AURA_TEMPLATE, 600f, reverse = true)
        assertTrue(xs.min() < -FxLean.UNITS * unit * 0.9f)
        for (x in xs) { assertTrue(x >= -FxLean.UNITS * unit - eps); assertTrue(x <= eps) }
    }

    @Test fun theLeanIsAddedToALooksOwnPoseAndNeverLiftsTheSprite() {
        // Ember recoils by -10 sin(..): with the lean the pose is the look's own plus the lean at that time
        val with = run(MoveLook.EMBER)
        val bare = RecStage(unit).let { st -> MoveFx.script(st, plan(MoveLook.EMBER), true, 7L)!!.also { EmberFx.build(it) }.let { st to it } }
        var ms = 0f
        while (ms <= 700f) {
            with.second.advance(ms); bare.second.advance(ms)
            val a = with.first.last(true)!!
            val b = bare.first.last(true)
            assertEquals("dx at $ms", (b?.dx ?: 0f) + FxLean.at(ms) * unit, a.dx, 0.05f)
            assertEquals(0f, a.dy, eps)
            ms += 10f
        }
    }

    @Test fun aPhysicalLookIsExactlyAsBefore() {
        for (look in listOf(MoveLook.HIT_TEMPLATE, MoveLook.TACKLE)) {
            val a = RecStage(unit).let { st -> MoveFx.script(st, plan(look), true, 3L)!!.also { MoveFx.build(it, look) }.let { st to it } }
            val b = RecStage(unit).let { st -> MoveFx.script(st, plan(look), true, 3L)!!.also { if (look == MoveLook.TACKLE) TackleFx.build(it) else HitTemplateFx.build(it) }.let { st to it } }
            var ms = 0f
            while (ms <= 700f) { a.second.advance(ms); b.second.advance(ms); ms += 10f }
            assertEquals(b.first.poses.size, a.first.poses.size)
            for (i in a.first.poses.indices) assertEquals(b.first.poses[i].dx, a.first.poses[i].dx, 0f)
        }
    }

    @Test fun releasingTheStageEndsTheLeanForGood() {
        val (stage, s) = run(MoveLook.ORB_TEMPLATE)
        s.advance(60f)
        s.releaseStage()
        val n = stage.poses.size
        s.advance(120f); s.advance(400f)
        assertEquals(n, stage.poses.size)   // a late frame never poses a sprite that was handed back
    }
}
