package com.husarp.ankigo.shots

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.R
import com.husarp.ankigo.data.Backdrops
import com.husarp.ankigo.data.Sprites
import com.husarp.ankigo.game.*
import com.husarp.ankigo.ui.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

class R49XFieldEffectsShots {
    @get:Rule val paparazzi = Shots.rule(useDeviceResolution = true)

    private fun context(theme: Theme = Theme.LIGHT, size: Size = Size.SMALL): Context {
        paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(size, theme))
        return Shots.files(paparazzi.context).also {
            FakeGame.install(it)
            AppPrefs.setAttackAnimations(it, true)
            it.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", false).commit()
            val source = File("/home/coder/code/projects/AnkiGo/docs/design/plan-kit/sprites")
            for (side in listOf("front", "back", "static")) for (n in listOf(25, 54)) {
                val art = File(source, "${if (side == "static") "front" else side}/$n.png")
                if (art.isFile) File(Sprites.dir(it), "$side/$n.png").also { target ->
                    target.parentFile!!.mkdirs(); art.copyTo(target, overwrite = true)
                }
            }
        }
    }

    private fun set(key: String?) {
        GameRepo.state!!.battle!!.field.let {
            it.weather = if (key == "hail") key else null
            it.weatherTurns = 3
            it.terrain = key?.takeUnless { k -> k == "hail" }
            it.terrainTurns = 3
        }
    }

    private fun picture(ctx: Context, panel: BattlePanelView, id: String) {
        val bg = BackdropCatalog.byId(id)!!
        Backdrops.installBundled(ctx, Sprites.dir(ctx), bg)
        val cached = Backdrops.file(Sprites.dir(ctx), bg)
        if (!cached.isFile) {
            val art = System.getenv("ANKIGO_R50_ASSETS")?.let { File(it, "bg-${Backdrops.fileName(bg)}") }
            assumeTrue("Downloadable design art requires ANKIGO_R50_ASSETS", art?.isFile == true)
            cached.parentFile!!.mkdirs(); art!!.copyTo(cached, overwrite = true)
        }
        val pic = Backdrops.fetchBlocking(Sprites.dir(ctx), bg, false) { _, _ -> null }!!
        Backdrops.finish(Backdrops.currentGeneration(), bg.id, pic)
        val back = ArenaView::class.java.getDeclaredField("backdrop").let { it.isAccessible = true; it.get(panel.arena) as ArenaBackdrop }
        back.reduceMotion = { true }
        back.plain() // discard the panel’s earlier queued request before installing this fixture
        back.request(bg, false)
        assertEquals(id, panel.arena.backdropScenery)
    }

    private fun pixels(fx: FieldEffectsView, ms: Float, ground: Boolean = true): IntArray {
        fx.stillMs = ms
        val bmp = Bitmap.createBitmap(fx.width, fx.height, Bitmap.Config.ARGB_8888)
        if (ground) fx.drawGround(Canvas(bmp)) else fx.draw(Canvas(bmp))
        return IntArray(fx.width * fx.height).also { bmp.getPixels(it, 0, fx.width, 0, 0, fx.width, fx.height); bmp.recycle() }
    }

    private fun flashMs(fx: FieldEffectsView): Float = (0..1200).maxBy {
        FieldEffectsState.electricAlpha(it / 100f + fx.terrainOffsetSeconds)
    } * 10f

    private fun still(key: String) {
        for (theme in Theme.entries) {
            val ctx = context(theme); set(key)
            val panel = BattlePanelView(ctx).also { it.renderAll() }
            val fx = panel.arena.fieldEffects
            fx.stillMs = if (key == "electric") flashMs(fx) else 1700f
            paparazzi.snapshot(panel, "x-$key-${theme.name.lowercase()}", 0)
            assertTrue(fx.state.active)
            assertFalse(fx.running)
            if (key == "electric") {
                val darkMs = (0..600).first { FieldEffectsState.electricAlpha(it / 100f + fx.terrainOffsetSeconds) == 0f } * 10f
                assertTrue(pixels(fx, darkMs).all { it == 0 })
                fx.stillMs = darkMs
                paparazzi.snapshot(panel, "x-electric-between-flashes-${theme.name.lowercase()}", 0)
            }
        }
    }

    @Test fun hail() = still("hail")
    @Test fun electric() = still("electric")
    @Test fun grassy() = still("grassy")
    @Test fun misty() = still("misty")

    @Test fun terrainStaysOnGroundAndGrassDoesNotMoveAtEveryBackground() {
        val ctx = context()
        val examples = setOf("sandbar-isle", "countryside-route", "power-plant", "normal-meadow", "desert-oasis", "legendary-shrine", "night-forest-2")
        for (bg in BackdropCatalog.ALL) {
            set("grassy")
            val panel = BattlePanelView(ctx).also { it.renderAll() }
            picture(ctx, panel, bg.id)
            panel.measure(View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1280, View.MeasureSpec.EXACTLY))
            panel.layout(0, 0, 720, 1280)
            val fx = panel.arena.fieldEffects
            for (key in listOf("electric", "grassy", "misty")) {
                set(key); panel.arena.syncConditions(GameRepo.state!!.battle)
                val ms = if (key == "electric") flashMs(fx) else 1700f
                val first = pixels(fx, ms)
                if (first.all { it == 0 }) paparazzi.snapshot(panel, "x-missing-${bg.id}-$key", 0)
                assertTrue("${bg.id} $key paints: ${fx.width}x${fx.height}, top=${fx.terrainTop}, time=$ms, alpha=${FieldEffectsState.electricAlpha(ms / 1000f + fx.terrainOffsetSeconds)}, state=${fx.state.terrain}", first.any { it != 0 })
                val rows = (fx.terrainTop * fx.height).toInt()
                assertTrue("${bg.id} $key touches sky", first.take(rows * fx.width).all { it == 0 })
                if (key == "grassy") assertArrayEquals(bg.id, first, pixels(fx, 7100f))
                if (key == "misty") assertFalse(first.contentEquals(pixels(fx, 7100f)))
                panel.arena.clearConditions()
                assertTrue(pixels(fx, ms).all { it == 0 })
            }
            if (bg.id in examples) {
                set("grassy"); panel.arena.syncConditions(GameRepo.state!!.battle); fx.stillMs = 1700f
                paparazzi.snapshot(panel, "x-grassy-${bg.id}", 0)
            }
        }
    }

    @Test fun hailFallsAndItsArtClearsOnExpiration() {
        val ctx = context(); set("hail")
        val panel = BattlePanelView(ctx).also { it.renderAll() }
        val fx = panel.arena.fieldEffects
        fx.stillMs = 0f
        paparazzi.snapshot(panel, "x-hail-start", 0)
        val first = pixels(fx, 0f, ground = false)
        assertTrue(first.any { it != 0 })
        assertFalse(first.contentEquals(pixels(fx, 70f, ground = false)))
        set(null); panel.arena.syncConditions(GameRepo.state!!.battle)
        assertFalse(fx.state.active)
        assertTrue(pixels(fx, 70f, ground = false).all { it == 0 })
    }

    @Test fun newConditionsStartReplaceEndAndSuppressFromPlayback() {
        val ctx = context(); val panel = BattlePanelView(ctx).also { it.renderAll() }; val arena = panel.arena
        for (key in listOf("hail", "electric", "grassy", "misty")) {
            set(key)
            arena.conditionEffect(GameEvent.Effect(true, "Start", 1, 1, kind = EffectKind.FIELD, stat = key, stages = 1), GameRepo.state!!.battle)
            assertEquals(key, if (key == "hail") arena.fieldEffects.state.weather else arena.fieldEffects.state.terrain)
            arena.conditionEffect(GameEvent.Effect(true, "End", 1, 1, kind = EffectKind.FIELD, stat = key, stages = -1), GameRepo.state!!.battle)
            assertFalse(arena.fieldEffects.state.active)
        }
        set("grassy"); arena.syncConditions(GameRepo.state!!.battle)
        set("misty"); arena.conditionEffect(GameEvent.Effect(true, "Replace", 1, 1, kind = EffectKind.FIELD, stat = "misty", stages = 1), GameRepo.state!!.battle)
        assertEquals("misty", arena.fieldEffects.state.terrain)
        GameRepo.state!!.battle!!.field.let { it.weather = "hail"; it.weatherTurns = 3 }
        for (ability in listOf("cloudnine", "airlock")) {
            GameRepo.state!!.main!!.ability = ability; arena.conditionsSwitched()
            assertNull(arena.fieldEffects.state.weather)
            assertEquals("misty", arena.fieldEffects.state.terrain)
        }
        arena.syncConditions(null)
        assertFalse(arena.fieldEffects.state.active)
    }

    @Test fun tickerSettingsVisibilityAndStaticGrass() {
        val ctx = context(); set("hail")
        val panel = BattlePanelView(ctx).also { it.renderAll() }; val fx = panel.arena.fieldEffects
        var checked = false
        val wrapper = object : FrameLayout(ctx) {
            override fun dispatchDraw(canvas: Canvas) {
                if (!checked) {
                    for (key in listOf("hail", "electric", "grassy", "misty")) {
                        set(key); panel.arena.syncConditions(GameRepo.state!!.battle)
                        assertEquals(key, key != "grassy", fx.running)
                        ctx.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", true).commit(); fx.refresh()
                        assertFalse(fx.running)
                        assertTrue(fx.state.active)
                        val bmp = Bitmap.createBitmap(fx.width, fx.height, Bitmap.Config.ARGB_8888)
                        val c = Canvas(bmp); fx.drawGround(c); fx.draw(c)
                        val px = IntArray(fx.width * fx.height); bmp.getPixels(px, 0, fx.width, 0, 0, fx.width, fx.height)
                        assertTrue("$key reduced-motion art", px.any { it != 0 }); bmp.recycle()
                        ctx.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", false).commit()
                        AppPrefs.setAttackAnimations(ctx, false); fx.refresh(); assertFalse(fx.running)
                        AppPrefs.setAttackAnimations(ctx, true); fx.refresh(); assertEquals(key != "grassy", fx.running)
                        panel.visibility = View.INVISIBLE; assertFalse(fx.running)
                        panel.visibility = View.VISIBLE; assertEquals(key != "grassy", fx.running)
                    }
                    panel.arena.clearConditions(); assertFalse(fx.running); checked = true
                }
                super.dispatchDraw(canvas)
            }
        }.apply { addView(panel, FrameLayout.LayoutParams(-1, -1)) }
        paparazzi.snapshot(wrapper, "x-settings-lifecycle", 0)
        assertTrue(checked); assertFalse(fx.running)
    }

    @Test fun terrainFitsCompactAndWideWindows() {
        for (size in listOf(Size.PIP, Size.SPLIT, Size.LAND)) {
            val ctx = context(Theme.DARK, size); set("misty")
            val panel = BattlePanelView(ctx).also { it.renderAll(); if (size == Size.PIP) it.setCompact(true) }
            picture(ctx, panel, "sandbar-isle")
            panel.arena.fieldEffects.stillMs = 1700f
            paparazzi.snapshot(panel, "x-misty-${size.name.lowercase()}", 0)
            val fx = panel.arena.fieldEffects
            assertTrue(pixels(fx, 1700f).any { it != 0 })
        }
    }

    @Test fun freezeUsesNearestNeighbourDrawableAtEveryUsedSize() {
        for (theme in Theme.entries) {
            val ctx = context(theme)
            val column = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Ui.c(ctx, R.color.ag_card)) }
            for (sizes in listOf(24, 38, 48, 56, 64, 72, 80, 84, 96).chunked(3)) {
                val row = LinearLayout(ctx)
                for (dp in sizes) {
                    val view = ShopViews.itemArt(ctx, StreakFreeze.ITEM, dp)
                    row.addView(LinearLayout(ctx).apply {
                        orientation = LinearLayout.VERTICAL
                        addView(Ui.text(ctx, 12f, 700, R.color.ag_text).apply { text = "${dp}dp" })
                        addView(view)
                    }, LinearLayout.LayoutParams(Ui.dp(ctx, 110), -2))
                    val art = (view as android.widget.ImageView).drawable as BitmapDrawable
                    assertFalse(art.paint.isFilterBitmap)
                    assertEquals(24, art.bitmap.width)
                    assertEquals(24, art.bitmap.height)
                }
                column.addView(row)
            }
            paparazzi.snapshot(column, "x-freeze-sizes-${theme.name.lowercase()}", 0)
        }
    }
}
