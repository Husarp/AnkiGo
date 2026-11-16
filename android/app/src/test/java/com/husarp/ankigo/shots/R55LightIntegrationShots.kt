package com.husarp.ankigo.shots

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import com.husarp.ankigo.data.BackdropPic
import com.husarp.ankigo.data.Backdrops
import com.husarp.ankigo.game.Category
import com.husarp.ankigo.game.Type
import com.husarp.ankigo.ui.*
import com.husarp.ankigo.ui.movefx.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class R55LightIntegrationShots {
    @get:Rule val paparazzi = Shots.rule()
    @After fun cleanup() { Backdrops.forget(); BattleShotKit.reset() }

    private fun field(arena: ArenaView, name: String): Any =
        ArenaView::class.java.getDeclaredField(name).apply { isAccessible = true }.get(arena)!!

    private fun arena(): ArenaView {
        val ctx = Shots.files(paparazzi.context)
        return ArenaView(ctx).apply {
            measure(View.MeasureSpec.makeMeasureSpec(480, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(270, View.MeasureSpec.EXACTLY))
            layout(0, 0, 480, 270)
            for (i in 0 until childCount) getChildAt(i).visibility = View.INVISIBLE
            background = null
            foreground = null
        }
    }

    private fun energy(arena: ArenaView, layer: View): Long {
        for (i in 0 until arena.childCount) {
            val child = arena.getChildAt(i)
            child.visibility = if (child === layer) View.VISIBLE else View.INVISIBLE
        }
        val bitmap = Bitmap.createBitmap(480, 270, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.BLACK)
        arena.draw(Canvas(bitmap))
        val pixels = IntArray(480 * 270)
        bitmap.getPixels(pixels, 0, 480, 0, 0, 480, 270)
        val result = pixels.sumOf { (Color.red(it) + Color.green(it) + Color.blue(it)).toLong() }
        bitmap.recycle()
        return result
    }

    private fun assertDimmed(arena: ArenaView, layer: ArenaFxView, label: String) {
        layer.visibility = View.VISIBLE
        layer.layout(0, 0, 480, 270)
        val backdrop = field(arena, "backdrop") as ArenaBackdrop
        backdrop.reduceMotion = { true }
        backdrop.plain()
        val bright = energy(arena, layer)
        assertTrue("$label must draw visible pixels", bright > 1000)
        val dark = BackdropCatalog.byId("dark-forest")!!
        val pic = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        Backdrops.finish(Backdrops.currentGeneration(), dark.id, BackdropPic(pic, pic))
        backdrop.request(dark, false)
        val ratio = energy(arena, layer).toDouble() / bright
        assertEquals("$label follows the backdrop's RGB light", (1f - BackdropLight.forScene(dark.id)).toDouble(), ratio, .025)
        backdrop.plain()
        assertEquals("$label restores its original light on plain backgrounds", bright, energy(arena, layer))
        layer.clear()
        layer.visibility = View.INVISIBLE
    }

    @Test fun allTenAiAttackLooksFollowTheSceneLight() {
        val arena = arena()
        val art = FxArt.of(arena.context)
        art.warmUp(); check(art.awaitWarm(15000))
        val layer = field(arena, "moveFx") as ArenaFxView
        val stage = object : MoveFxStage {
            override val attackerX = 140f
            override val attackerY = 190f
            override val targetX = 350f
            override val targetY = 105f
            override val unit = 1f
            override fun pose(attacker: Boolean, dx: Float, dy: Float, sx: Float, sy: Float, matrix: FloatArray?) {}
            override fun jolt(dy: Float) {}
            override fun restore() {}
        }
        val moves = listOf(
            Triple("flamethrower", Type.FIRE, 470f), Triple("watergun", Type.WATER, 480f),
            Triple("thunderbolt", Type.ELECTRIC, 440f), Triple("shadowball", Type.GHOST, 600f),
            Triple("earthquake", Type.GROUND, 700f), Triple("psychic", Type.PSYCHIC, 700f),
            Triple("icebeam", Type.ICE, 650f), Triple("vinewhip", Type.GRASS, 760f),
            Triple("tackle", Type.NORMAL, 390f), Triple("gigadrain", Type.GRASS, 900f))
        for ((id, type, time) in moves) {
            val category = if (id in listOf("earthquake", "vinewhip", "tackle")) Category.PHYSICAL else Category.SPECIAL
            val effect = MoveFx.create(arena.context, stage, MoveFxCatalog.plan(id, type, category, 60), true, 1f, 7L)!!
            layer.showStill(effect, time)
            assertDimmed(arena, layer, id)
        }
    }

    @Test fun battleMomentsDimBothInFrontOfAndBehindThePokemon() {
        val arena = arena()
        for (kind in BattleMomentFx.Kind.entries) {
            val time = when (kind) {
                BattleMomentFx.Kind.EVOLVE -> 900f
                BattleMomentFx.Kind.LEVEL -> 500f
                BattleMomentFx.Kind.UP, BattleMomentFx.Kind.DOWN -> 540f
                else -> 230f
            }
            val fronts = if (kind == BattleMomentFx.Kind.UP || kind == BattleMomentFx.Kind.DOWN) listOf(false, true) else listOf(true)
            for (front in fronts) {
                val layer = if (front) arena.fx else field(arena, "momentBack") as ArenaFxView
                layer.showStill(BattleMomentFx(arena.context, kind, 240f, 135f, 1f, front = front), time)
                assertDimmed(arena, layer, "$kind front=$front")
            }
        }
    }

    @Test fun sceneLightLeavesTheStatCardsReadable() {
        val arena = arena()
        val cards = field(arena, "cardLayer") as View
        cards.visibility = View.VISIBLE
        cards.layout(0, 0, 480, 270)
        cards.setBackgroundColor(Color.rgb(210, 180, 140))
        val bright = energy(arena, cards)
        val backdrop = field(arena, "backdrop") as ArenaBackdrop
        backdrop.reduceMotion = { true }
        val dark = BackdropCatalog.byId("dark-forest")!!
        val pic = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        Backdrops.finish(Backdrops.currentGeneration(), dark.id, BackdropPic(pic, pic))
        backdrop.request(dark, false)
        assertTrue(backdrop.lightDim > .15f)
        assertEquals(bright, energy(arena, cards))
    }
}
