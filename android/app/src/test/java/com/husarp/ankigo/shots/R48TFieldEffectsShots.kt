package com.husarp.ankigo.shots

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.FrameLayout
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.data.Sprites
import com.husarp.ankigo.game.*
import com.husarp.ankigo.ui.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class R48TFieldEffectsShots {
    @get:Rule val paparazzi = Shots.rule(useDeviceResolution = true)
    @After fun reset() = BattleShotKit.reset()

    private fun context(theme: Theme = Theme.LIGHT, size: Size = Size.SMALL): Context {
        paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(size, theme))
        return Shots.files(paparazzi.context).also { ctx ->
            FakeGame.install(ctx)
            ctx.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", false).commit()
            AppPrefs.setAttackAnimations(ctx, true)
            val source = File("/home/coder/code/projects/AnkiGo/docs/design/plan-kit/sprites")
            for (side in listOf("front", "back", "static")) for (n in listOf(25, 54)) {
                val art = File(source, "${if (side == "static") "front" else side}/$n.png")
                if (art.isFile) File(Sprites.dir(ctx), "$side/$n.png").also { target ->
                    target.parentFile!!.mkdirs(); art.copyTo(target, overwrite = true)
                }
            }
        }
    }

    private fun set(key: String) {
        val f = GameRepo.state!!.battle!!.field
        when (key) {
            "psychic" -> { f.terrain = key; f.terrainTurns = 3 }
            "watersport" -> f.rooms[key] = 3
            else -> { f.weather = key; f.weatherTurns = 3 }
        }
    }

    private fun still(key: String, ms: Float = 1700f) {
        for (theme in Theme.entries) {
            val ctx = context(theme)
            set(key)
            val panel = BattlePanelView(ctx).also { it.renderAll() }
            panel.arena.fieldEffects.stillMs = ms
            paparazzi.snapshot(panel, "t-$key-${theme.name.lowercase()}", 0)
            assertTrue(panel.arena.fieldEffects.state.active)
            assertFalse(panel.arena.fieldEffects.running)
        }
    }
    @Test fun rain() = still("rain")
    @Test fun sun() = still("sun")
    @Test fun snow() = still("snow")
    @Test fun psychic() = still("psychic")
    @Test fun waterSport() = still("watersport")
    @Test fun sand() {
        for (theme in Theme.entries) {
            val ctx = context(theme); set("sand")
            val panel = BattlePanelView(ctx).also { it.renderAll() }
            val fx = panel.arena.fieldEffects
            val peak = (0..600).maxBy { FieldEffectsState.sandDensity(it / 10f + fx.sandOffsetSeconds) } / 10f
            fx.stillMs = peak * 1000f
            paparazzi.snapshot(panel, "t-sand-${theme.name.lowercase()}", 0)
            assertFalse(fx.running)
            if (theme == Theme.LIGHT) {
                val lull = (0..600).minBy { FieldEffectsState.sandDensity(it / 10f + fx.sandOffsetSeconds) } / 10f
                fx.stillMs = lull * 1000f
                paparazzi.snapshot(panel, "t-sand-lull", 0)
            }
        }
    }

    @Test fun playbackStartsReplacesAndStopsEachSelectedCondition() {
        val ctx = context()
        val panel = BattlePanelView(ctx).also { it.renderAll() }
        val arena = panel.arena
        for (key in listOf("rain", "sun", "sand", "snow", "psychic", "watersport")) {
            set(key)
            arena.conditionEffect(GameEvent.Effect(true, "Start", 1, 1, kind = EffectKind.FIELD, stat = key, stages = 1), GameRepo.state!!.battle)
            val state = arena.fieldEffects.state
            when (key) {
                "psychic" -> assertTrue(state.psychic)
                "watersport" -> assertTrue(state.waterSport)
                else -> assertEquals(key, state.weather)
            }
            // The stop line wins even if the saved battle is still holding the condition.
            arena.conditionEffect(GameEvent.Effect(true, "End", 1, 1, kind = EffectKind.FIELD, stat = key, stages = -1), GameRepo.state!!.battle)
            when (key) {
                "psychic" -> assertFalse(state.psychic)
                "watersport" -> assertFalse(state.waterSport)
                else -> assertNull(state.weather)
            }
        }
        set("rain"); arena.syncConditions(GameRepo.state!!.battle)
        set("sun"); arena.conditionEffect(GameEvent.Effect(true, "Replace", 1, 1, kind = EffectKind.FIELD, stat = "sun", stages = 1), GameRepo.state!!.battle)
        assertEquals("sun", arena.fieldEffects.state.weather)
        arena.clearConditions()
        assertFalse(arena.fieldEffects.state.active)
        arena.syncConditions(null)
        assertFalse(arena.fieldEffects.state.active)
        // A condition already expired in the saved battle must never briefly start on playback.
        GameRepo.state!!.battle!!.field.weather = null
        arena.conditionEffect(GameEvent.Effect(true, "Expired", 1, 1, kind = EffectKind.FIELD, stat = "rain", stages = 1), GameRepo.state!!.battle)
        assertFalse(arena.fieldEffects.state.active)
    }

    @Test fun effectiveWeatherSuppressionAndExpirationFollowSavedBattle() {
        val ctx = context(); set("rain"); set("psychic"); set("watersport")
        val panel = BattlePanelView(ctx).also { it.renderAll() }
        val arena = panel.arena
        for (ability in listOf("cloudnine", "airlock")) {
            GameRepo.state!!.main!!.ability = ability
            arena.conditionsSwitched()
            assertNull(arena.fieldEffects.state.weather)
            assertTrue(arena.fieldEffects.state.psychic)
            assertTrue(arena.fieldEffects.state.waterSport)
        }
        GameRepo.state!!.main!!.ability = "static"
        arena.syncConditions(GameRepo.state!!.battle)
        assertEquals("rain", arena.fieldEffects.state.weather)
        GameRepo.state!!.battle!!.field.let { it.weather = null; it.terrain = null; it.rooms.clear() }
        arena.syncConditions(GameRepo.state!!.battle)
        assertFalse(arena.fieldEffects.state.active)
    }

    @Test fun oneTickerStopsForSettingsHiddenWindowAndDetach() {
        val ctx = context(); set("rain"); set("psychic"); set("watersport")
        val panel = BattlePanelView(ctx).also { it.renderAll() }
        val fx = panel.arena.fieldEffects
        var checked = false
        val wrapper = object : FrameLayout(ctx) {
            override fun dispatchDraw(canvas: Canvas) {
                if (!checked) {
                    assertTrue(fx.running)
                    ctx.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", true).commit()
                    fx.refresh() // the renderer's memory preferences have no listener delivery
                    assertFalse(fx.running)
                    assertTrue(fx.state.active)
                    ctx.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", false).commit()
                    AppPrefs.setAttackAnimations(ctx, false); fx.refresh()
                    assertFalse(fx.running)
                    AppPrefs.setAttackAnimations(ctx, true); fx.refresh()
                    assertTrue(fx.running)
                    panel.visibility = View.INVISIBLE
                    assertFalse(fx.running)
                    panel.visibility = View.VISIBLE
                    assertTrue(fx.running)
                    panel.arena.clearConditions()
                    assertFalse(fx.running)
                    checked = true
                }
                super.dispatchDraw(canvas)
            }
        }.apply { addView(panel, FrameLayout.LayoutParams(-1, -1)) }
        paparazzi.snapshot(wrapper, "t-stopped", 0)
        assertTrue(checked)
        assertFalse(fx.running)
        wrapper.removeView(panel)
        set("rain"); panel.arena.syncConditions(GameRepo.state!!.battle)
        paparazzi.snapshot(panel, "t-detached", 0)
        assertFalse(fx.running)
    }

    @Test fun eachSelectedEffectPaintsMovesAndClears() {
        val ctx = context(); set("psychic")
        val panel = BattlePanelView(ctx).also { it.renderAll() }
        val arena = panel.arena
        val fx = arena.fieldEffects
        fx.stillMs = 0f
        paparazzi.snapshot(panel, "t-render-baseline", 0)
        fun pixels(ms: Float): IntArray {
            fx.stillMs = ms
            val bmp = Bitmap.createBitmap(fx.width, fx.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            fx.drawGround(canvas); fx.draw(canvas)
            return IntArray(fx.width * fx.height).also { bmp.getPixels(it, 0, fx.width, 0, 0, fx.width, fx.height); bmp.recycle() }
        }
        for (key in listOf("rain", "sun", "sand", "snow", "psychic", "watersport")) {
            GameRepo.state!!.battle!!.field.let { it.weather = null; it.terrain = null; it.rooms.clear() }
            set(key); arena.syncConditions(GameRepo.state!!.battle)
            val first = pixels(0f)
            assertTrue("$key paints", first.any { it != 0 })
            assertFalse("$key moves", first.contentEquals(pixels(700f)))
            arena.clearConditions()
            assertTrue("$key clears", pixels(700f).all { it == 0 })
        }
    }

    @Test fun reducedMotionIsStillVisibleAndDoesNotTick() {
        val ctx = context(); set("rain"); set("psychic"); set("watersport")
        ctx.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", true).commit()
        val panel = BattlePanelView(ctx).also { it.renderAll() }
        paparazzi.snapshot(panel, "t-reduced-motion", 0)
        assertTrue(panel.arena.fieldEffects.state.active)
        assertFalse(panel.arena.fieldEffects.running)
        val fx = panel.arena.fieldEffects
        fun pixels(): IntArray {
            val bmp = Bitmap.createBitmap(fx.width, fx.height, Bitmap.Config.ARGB_8888)
            fx.draw(Canvas(bmp))
            return IntArray(fx.width * fx.height).also { bmp.getPixels(it, 0, fx.width, 0, 0, fx.width, fx.height); bmp.recycle() }
        }
        val first = pixels()
        assertTrue(first.any { it != 0 })
        assertArrayEquals(first, pixels())
        panel.arena.clearConditions()
        assertTrue(pixels().all { it == 0 })
    }

    @Test fun terrainAlsoDrawsOverPictureGroundWithHiddenPlatforms() {
        val ctx = context(); set("psychic")
        val panel = BattlePanelView(ctx).also { it.renderAll() }
        val back = ArenaView::class.java.getDeclaredField("backdrop").let { it.isAccessible = true; it.get(panel.arena) as ArenaBackdrop }
        back.reduceMotion = { true }
        back.request(BackdropCatalog.ALL.first { it.id == "countryside-route" }, false)
        panel.arena.fieldEffects.stillMs = 1700f
        paparazzi.snapshot(panel, "t-psychic-picture", 0)
        assertEquals("countryside-route", panel.arena.backdropScenery)
        assertTrue(panel.arena.fieldEffects.wildRing.visible && panel.arena.fieldEffects.playerRing.visible)
    }

    @Test fun ringsStayClearOfCardsAndFollowSmallAndLargeStages() {
        for (size in listOf(Size.SMALL, Size.SPLIT, Size.PIP, Size.LAND)) {
            val ctx = context(Theme.LIGHT, size); set("psychic"); set("watersport"); set("rain")
            val panel = BattlePanelView(ctx).also { it.renderAll(); if (size == Size.PIP) it.setCompact(true) }
            val arena = panel.arena
            arena.fieldEffects.stillMs = 2100f
            paparazzi.snapshot(panel, "t-combined-${size.name.lowercase()}", 0)
            assertEquals(arena.width, arena.fieldEffects.width)
            assertEquals(arena.height, arena.fieldEffects.height)
            val cards = listOf("enemyBlock", "playerCard").map { name ->
                ArenaView::class.java.getDeclaredField(name).let { it.isAccessible = true; it.get(arena) as View }
            }
            for (ring in listOf(arena.fieldEffects.wildRing, arena.fieldEffects.playerRing)) {
                assertTrue(ring.visible && ring.rx > 0f && ring.ry > 0f)
                assertTrue(ring.x - ring.rx >= 0f && ring.x + ring.rx <= arena.width)
                assertTrue(ring.y - ring.ry >= 0f && ring.y + ring.ry <= arena.height)
                for (card in cards) assertTrue("$size: ring touches card", ring.x + ring.rx <= card.left || ring.x - ring.rx >= card.left + card.width * card.scaleX ||
                    ring.y + ring.ry <= card.top || ring.y - ring.ry >= card.top + card.height * card.scaleY)
            }
        }
    }
}
