package com.husarp.ankigo.shots

import android.graphics.Canvas
import android.os.SystemClock
import android.widget.FrameLayout
import com.husarp.ankigo.game.Category
import com.husarp.ankigo.game.Type
import com.husarp.ankigo.ui.AppPrefs
import com.husarp.ankigo.ui.BattlePanelView
import com.husarp.ankigo.ui.movefx.FxArt
import com.husarp.ankigo.ui.movefx.MoveFxCatalog
import org.junit.Assert.*
import org.junit.After
import org.junit.Rule
import org.junit.Test

class R53AiAttackShots {
    @get:Rule val paparazzi = Shots.rule()
    @After fun cleanup() = BattleShotKit.reset()

    private fun move(id: String, type: Type, category: Category, power: Int, ms: Float) {
        paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.TABLET_LAND, Theme.LIGHT))
        val ctx = Shots.files(paparazzi.context)
        val art = FxArt.of(ctx); art.warmUp(); check(art.awaitWarm(15000))
        paparazzi.snapshot(MoveFxShotKit.sheet(ctx, id, MoveFxShotKit.Frame.WIDE,
            MoveFxCatalog.plan(id, type, category, power), listOf(ms)), "r53-$id", 0)
    }

    @Test fun flamethrower() = move("flamethrower", Type.FIRE, Category.SPECIAL, 90, 470f)
    @Test fun waterGun() = move("watergun", Type.WATER, Category.SPECIAL, 40, 480f)
    @Test fun thunderbolt() = move("thunderbolt", Type.ELECTRIC, Category.SPECIAL, 90, 440f)
    @Test fun shadowBall() = move("shadowball", Type.GHOST, Category.SPECIAL, 80, 600f)
    @Test fun earthquake() = move("earthquake", Type.GROUND, Category.PHYSICAL, 100, 700f)
    @Test fun psychic() = move("psychic", Type.PSYCHIC, Category.SPECIAL, 90, 700f)
    @Test fun iceBeam() = move("icebeam", Type.ICE, Category.SPECIAL, 90, 650f)
    @Test fun vineWhip() = move("vinewhip", Type.GRASS, Category.PHYSICAL, 45, 760f)
    @Test fun tackle() = move("tackle", Type.NORMAL, Category.PHYSICAL, 40, 390f)
    @Test fun drain() = move("gigadrain", Type.GRASS, Category.SPECIAL, 75, 900f)

    @Test fun visibleBoundsIgnoreTransparentPaddingCacheAndFollowMirroring() {
        val ctx = Shots.files(paparazzi.context)
        val bitmap = android.graphics.Bitmap.createBitmap(96, 96, android.graphics.Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawRect(12f, 18f, 44f, 70f, android.graphics.Paint().apply { color = -1 })
        val sprite = com.husarp.ankigo.gfx.SpriteView(ctx).apply {
            scaleDp = 1f; byFrame = true; anchorFeet = true
            setImage(com.husarp.ankigo.data.SpriteImage(listOf(bitmap), intArrayOf(100)))
            layout(0, 0, 192, 192)
        }
        val box = android.graphics.RectF()
        sprite.visibleBody(box)
        assertEquals(64f, box.width(), .01f); assertEquals(104f, box.height(), .01f)
        val centre = box.centerX()
        // Changing pixels after measurement does not trigger another scan while the image stays the same.
        bitmap.eraseColor(-1)
        sprite.visibleBody(box)
        assertEquals(64f, box.width(), .01f)
        sprite.mirror = true
        sprite.visibleBody(box)
        assertEquals(192f - centre, box.centerX(), .01f)
        val backdrop = object : com.husarp.ankigo.ui.movefx.Joltable {
            override var jolt = 0f
            override var joltX = 0f
        }
        val stage = com.husarp.ankigo.ui.ArenaMoveStage(20f, 40f, 300f, 140f, 2f,
            sprite, sprite, { backdrop })
        assertEquals(64f, stage.targetBody.w, .01f)
        assertEquals(104f, stage.targetBody.h, .01f)
        assertEquals(300f - 96f + box.centerX(), stage.targetBody.cx, .01f)
        stage.sceneJolt(4f, 2f); stage.rotateTarget(7f)
        assertEquals(4f, backdrop.joltX, 0f); assertEquals(7f, sprite.rotation, 0f)
        stage.restore()
        assertEquals(0f, backdrop.joltX, 0f); assertEquals(0f, backdrop.jolt, 0f)
        assertEquals(0f, sprite.rotation, 0f)
        stage.sceneJolt(9f, 9f); stage.rotateTarget(12f)
        assertEquals(0f, backdrop.joltX, 0f); assertEquals(0f, sprite.rotation, 0f)
    }

    @Test fun frozenAndThawUseTheSameGlassOnBothSides() {
        for (exit in listOf<Long?>(null, 300L, 650L)) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.PHONE, Theme.LIGHT))
            val ctx = Shots.files(paparazzi.context)
            FakeGame.install(ctx)
            ctx.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", false).commit()
            AppPrefs.setAttackAnimations(ctx, true)
            val panel = BattlePanelView(ctx).also { it.renderAll() }
            panel.arena.setPlayerStatus("frz"); panel.arena.setEnemyStatus("frz")
            val art = FxArt.of(ctx); art.warmUp(); check(art.awaitWarm(15000))
            val host = object : FrameLayout(ctx) {
                override fun dispatchDraw(c: Canvas) {
                    for (sprite in arrayOf(panel.arena.playerSprite, panel.arena.enemySprite)) {
                        val now = SystemClock.uptimeMillis()
                        sprite.conditionLook!!.state.set("frz", now - 1700L, immediate = true)
                        if (exit != null) sprite.conditionLook!!.state.set(null, now - exit)
                    }
                    super.dispatchDraw(c)
                }
            }.apply { addView(panel) }
            paparazzi.snapshot(host, "r53-frozen-${exit ?: "hold"}", 0)
        }
    }
}
