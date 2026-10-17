package com.husarp.ankigo.ui.movefx

import com.husarp.ankigo.game.Category
import com.husarp.ankigo.game.Category.PHYSICAL
import com.husarp.ankigo.game.Category.SPECIAL
import com.husarp.ankigo.game.Category.STATUS
import com.husarp.ankigo.game.Type
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import kotlin.math.sqrt

class MoveFxCatalogTest {
    private fun plan(id: String, t: Type?, c: Category, p: Int) = MoveFxCatalog.plan(id, t, c, p)

    @Test fun pickedMovesPlayTheirOwnLookWithK1() {
        val cases = listOf(
            Triple("tackle", MoveLook.TACKLE, Type.NORMAL), Triple("ember", MoveLook.EMBER, Type.FIRE),
            Triple("watergun", MoveLook.WATER_JET, Type.WATER), Triple("thunderbolt", MoveLook.THUNDERBOLT, Type.ELECTRIC),
            Triple("vinewhip", MoveLook.VINE_WHIP, Type.GRASS), Triple("icebeam", MoveLook.ICE_BEAM, Type.ICE),
            Triple("psychic", MoveLook.PSYCHIC, Type.PSYCHIC), Triple("shadowball", MoveLook.SHADOW_BALL, Type.GHOST),
            Triple("earthquake", MoveLook.EARTHQUAKE, Type.GROUND),
        )
        for ((id, look, type) in cases) {
            val p = plan(id, type, SPECIAL, 999)
            assertEquals(id, look, p.look)
            assertEquals(id, 1f, p.k, 0f)
        }
    }

    @Test fun rule1SameTypeAndCategory() {
        val fl = plan("flamethrower", Type.FIRE, SPECIAL, 90)
        assertEquals(MoveLook.EMBER, fl.look); assertEquals(1.5f, fl.k, 0.001f)
        val hp = plan("hydropump", Type.WATER, SPECIAL, 110)
        assertEquals(MoveLook.WATER_JET, hp.look); assertEquals(1.6f, hp.k, 0f)
        val th = plan("thunder", Type.ELECTRIC, SPECIAL, 110)
        assertEquals(MoveLook.THUNDERBOLT, th.look); assertEquals(1.105f, th.k, 0.001f)
        val rl = plan("razorleaf", Type.GRASS, PHYSICAL, 55)
        assertEquals(MoveLook.VINE_WHIP, rl.look); assertEquals(1.105f, rl.k, 0.001f)
        assertEquals(MoveLook.ICE_BEAM, plan("blizzard", Type.ICE, SPECIAL, 110).look)
        assertEquals(MoveLook.PSYCHIC, plan("psyshock", Type.PSYCHIC, SPECIAL, 80).look)
        assertEquals(MoveLook.SHADOW_BALL, plan("shadowpunch", Type.GHOST, SPECIAL, 80).look)
        assertEquals(MoveLook.EARTHQUAKE, plan("bulldoze", Type.GROUND, PHYSICAL, 60).look)
        assertEquals(MoveLook.TACKLE, plan("bodyslam", Type.NORMAL, PHYSICAL, 85).look)
    }

    @Test fun kIsClamped() {
        assertEquals(0.8f, plan("x", Type.FIRE, SPECIAL, 5).k, 0f)
        assertEquals(1.6f, plan("x", Type.FIRE, SPECIAL, 400).k, 0f)
        assertEquals(sqrt(60f / 40f), plan("x", Type.FIRE, SPECIAL, 60).k, 0.001f)
    }

    @Test fun noPowerMeansK1() {
        assertEquals(1f, plan("x", Type.FIRE, SPECIAL, 0).k, 0f)
        assertEquals(1f, plan("x", Type.FIRE, SPECIAL, -3).k, 0f)
        assertEquals(1f, plan("x", Type.BUG, PHYSICAL, 0).k, 0f)
    }

    @Test fun rule2OtherCategoryGetsTheTypesKit() {
        val fp = plan("firepunch", Type.FIRE, PHYSICAL, 75)
        assertEquals(MoveLook.HIT_TEMPLATE, fp.look); assertEquals(FxKit.FIRE, fp.kit); assertEquals(sqrt(75f / 40f), fp.k, 0.001f)
        assertEquals(1.37f, fp.k, 0.01f)
        val growl = plan("growl", Type.NORMAL, STATUS, 0)
        assertEquals(MoveLook.AURA_TEMPLATE, growl.look); assertEquals(FxKit.NORMAL, growl.kit); assertEquals(1f, growl.k, 0f)
        val hv = plan("hypervoice", Type.NORMAL, SPECIAL, 90)
        assertEquals(MoveLook.ORB_TEMPLATE, hv.look); assertEquals(FxKit.NORMAL, hv.kit); assertEquals(sqrt(90f / 80f), hv.k, 0.001f)
        assertEquals(FxKit.WATER, plan("x", Type.WATER, PHYSICAL, 40).kit)
        assertEquals(FxKit.ELECTRIC, plan("x", Type.ELECTRIC, PHYSICAL, 40).kit)
        assertEquals(FxKit.GRASS, plan("x", Type.GRASS, SPECIAL, 80).kit)
        assertEquals(FxKit.ICE, plan("x", Type.ICE, PHYSICAL, 40).kit)
        assertEquals(FxKit.PSYCHIC, plan("x", Type.PSYCHIC, PHYSICAL, 40).kit)
        assertEquals(FxKit.GHOST, plan("x", Type.GHOST, PHYSICAL, 40).kit)
        assertEquals(FxKit.GROUND, plan("x", Type.GROUND, SPECIAL, 80).kit)
        assertEquals(MoveLook.AURA_TEMPLATE, plan("x", Type.FIRE, STATUS, 0).look)
        assertEquals(MoveLook.ORB_TEMPLATE, plan("x", Type.GRASS, SPECIAL, 80).look)
    }

    @Test fun rule3TypesWithoutAPickUsePlainTemplates() {
        val fc = plan("flashcannon", Type.STEEL, SPECIAL, 80)
        assertEquals(MoveLook.ORB_TEMPLATE, fc.look); assertEquals(FxKit.PLAIN, fc.kit); assertEquals(1f, fc.k, 0f)
        assertEquals(FxPalettes.of(Type.STEEL), fc.palette)
        val noPick = listOf(Type.FIGHTING, Type.POISON, Type.FLYING, Type.BUG, Type.ROCK, Type.DRAGON, Type.DARK, Type.STEEL, Type.FAIRY)
        for (t in noPick) {
            val hit = plan("x", t, PHYSICAL, 80)
            assertEquals(MoveLook.HIT_TEMPLATE, hit.look); assertEquals(FxKit.PLAIN, hit.kit)
            assertEquals(sqrt(2f), hit.k, 0.001f)
            assertEquals(FxPalettes.of(t), hit.palette)
            assertEquals(MoveLook.ORB_TEMPLATE, plan("x", t, SPECIAL, 80).look)
            val st = plan("x", t, STATUS, 0)
            assertEquals(MoveLook.AURA_TEMPLATE, st.look); assertEquals(1f, st.k, 0f)
        }
    }

    @Test fun typelessPlaysAsNormal() {
        val s = plan("struggle", null, PHYSICAL, 50)
        assertEquals(MoveLook.TACKLE, s.look)
        assertEquals(sqrt(50f / 40f), s.k, 0.001f)
        assertEquals(FxPalettes.of(Type.NORMAL), s.palette)
        assertEquals(MoveLook.ORB_TEMPLATE, plan("", null, SPECIAL, 80).look)
    }

    @Test fun unknownIdUsesTypeAndCategory() {
        assertEquals(MoveLook.EMBER, plan("", Type.FIRE, SPECIAL, 40).look)
    }

    @Test fun everyTypeHasAnOpaquePalette() {
        assertEquals(18, Type.entries.size)
        for (t in Type.entries) {
            val p = FxPalettes.of(t)
            for (c in listOf(p.deep, p.glow, p.smoke, p.spark)) assertEquals(t.name, 0xFF, (c ushr 24) and 0xFF)
            assertNotEquals(t.name, p.deep, p.glow)
        }
    }

    @Test fun ghostAndSteelAreThePagesTints() {
        val g = FxPalettes.of(Type.GHOST)
        assertEquals(0xFF3A1458.toInt(), g.deep); assertEquals(0xFFB06BFF.toInt(), g.glow)
        assertEquals(0xFF52207A.toInt(), g.smoke); assertEquals(0xFFE1B8FF.toInt(), g.spark)
        val s = FxPalettes.of(Type.STEEL)
        assertEquals(0xFF3F4757.toInt(), s.deep); assertEquals(0xFF9EABC2.toInt(), s.glow)
        assertEquals(0xFF6F7889.toInt(), s.smoke); assertEquals(0xFFFFFFFF.toInt(), s.spark)
    }

    @Test fun derivedPaletteOrderingDeepSmokeGlowSpark() {
        fun lum(c: Int) = ((c shr 16) and 0xFF) + ((c shr 8) and 0xFF) + (c and 0xFF)
        for (t in Type.entries) {
            if (t == Type.GHOST || t == Type.STEEL) continue
            val p = FxPalettes.of(t)
            assert(lum(p.deep) < lum(p.smoke)) { t.name }
            assert(lum(p.smoke) < lum(p.glow)) { t.name }
            assert(lum(p.glow) < lum(p.spark)) { t.name }
        }
        // Fire: deep = 45 % of #ee8130, spark = 80 % toward white.
        val f = FxPalettes.of(Type.FIRE)
        assertEquals(0xFF6B3A15.toInt(), f.deep)
        assertEquals(0xFFFBE5D5.toInt(), f.spark)
    }
}
