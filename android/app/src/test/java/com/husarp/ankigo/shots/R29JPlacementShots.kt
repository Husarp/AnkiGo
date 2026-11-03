package com.husarp.ankigo.shots

import android.graphics.BitmapFactory
import android.view.View
import com.husarp.ankigo.gfx.SpriteView
import com.android.resources.Density
import com.android.resources.ScreenOrientation
import com.husarp.ankigo.data.*
import com.husarp.ankigo.ui.*
import com.husarp.ankigo.game.Type
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Production ArenaView, including StageGeo, real padded sprites and actual backdrop files.
 * 1080 x 550 matches the short phone battle region; 1080 x 700 exercises a taller host.
 * The full OnePlus 6 display is 1080 x 2280 at 420 dpi; system bars/AnkiDroid are outside this view.
 */
class R29JPlacementShots {
    @get:Rule val paparazzi = Shots.rule(useDeviceResolution = true)
    @After fun cleanup() { Backdrops.forget(); BackdropSharp.runner = BackdropSharp.InPlace }

    @Test fun realBattlePlacement() {
        val changed = setOf("tropical-cove", "coral-lagoon", "sandbar-isle", "reef-bay", "beach", "city", "desert",
            "deep-sea-cave", "dragon-peak", "flower-field", "forest-tech-b", "windy-hilltop", "countryside-route",
            "coral-shallows", "lava-field", "starry-plateau", "night-rooftops")
        val controls = setOf("meadow", "night-forest", "factory", "snowy-pines", "old-graveyard")
        for (height in listOf(550, 700)) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.PHONE, Theme.LIGHT).copy(
                screenWidth = 1080, screenHeight = height, density = Density.create(420), xdpi = 420, ydpi = 420,
                orientation = ScreenOrientation.LANDSCAPE))
            for (bg in BackdropCatalog.ALL.filter { it.id in changed || it.id in controls }) {
                for (species in listOf("eevee", "snorlax")) {
                    val ctx = Shots.files(paparazzi.context)
                    FakeGame.install(ctx)
                    Backdrops.forget()
                    BackdropSharp.runner = BackdropSharp.InPlace
                    val stream = runCatching { ctx.assets.open("backdrops/${Backdrops.fileName(bg)}") }.getOrNull()
                        ?: checkNotNull(javaClass.getResourceAsStream("/r26e/backgrounds/${bg.id}.webp")) { bg.id }
                    Backdrops.file(Sprites.dir(ctx), bg).also { it.parentFile!!.mkdirs(); it.writeBytes(stream.use { s -> s.readBytes() }) }
                    val pic = Backdrops.fetchBlocking(Sprites.dir(ctx), bg, false) { _, _ -> null }!!
                    Backdrops.finish(Backdrops.currentGeneration(), bg.id, pic)
                    val arena = ArenaView(ctx).apply { setEnemyShown(true) }
                    val type = Type.entries.first { t -> bg in BackdropCatalog.options(t) }
                    arena.setBackdrop(listOf(type), BackdropCatalog.options(type).indexOf(bg))
                    assertEquals(bg.id, arena.backdropScenery)
                    fun image(name: String): SpriteImage {
                        val path = if (name == "eevee-back") "/r28o/eevee-back.png" else "/r26e/$name.png"
                        val bytes = javaClass.getResourceAsStream(path)!!.use { it.readBytes() }
                        return SpriteImage(listOf(BitmapFactory.decodeByteArray(bytes, 0, bytes.size)!!), intArrayOf(100))
                    }
                    val playerImage = image("eevee-back")
                    val wildImage = image(species)
                    arena.playerSprite.setImage(playerImage)
                    arena.enemySprite.setImage(wildImage)
                    arena.playerSprite.idleMotion = false
                    arena.enemySprite.idleMotion = false
                    arena.setPlayerInfo("j-player", "Eevee", 25)
                    arena.setPlayerTypes(listOf(Type.NORMAL))
                    arena.setEnemyInfo(species.replaceFirstChar { it.titlecase() }, 25, false, listOf(Type.NORMAL))
                    val w = View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY)
                    val h = View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
                    arena.measure(w, h); arena.layout(0, 0, 1080, height)
                    assertTrue(arena.playerSprite.anchorFeet)
                    assertTrue(arena.enemySprite.anchorFeet)
                    val stage = checkNotNull(arena.stageNow)
                    val crop = BackdropDraw.stageCrop(bg, stage)
                    assertFalse(bg.id, BackdropDraw.layout(bg.w, bg.h, crop, 96, 64, 1080f, height.toFloat()).anyFade)
                    paparazzi.snapshot(arena, "j-placement-${bg.id}-${height}-$species", 0)
                    assertTrue((arena.enemySprite.parent as View).visibility == View.VISIBLE)
                    assertEquals(1080, arena.width)
                    assertEquals(height, arena.height)
                    assertFeet(bg, arena.playerSprite, playerImage, stage.playerFeetY, crop, true, height)
                    assertFeet(bg, arena.enemySprite, wildImage, stage.wildFeetY, crop, false, height)
                    // Save production geometry for the evidence audit; never a substitute for the render.
                    java.io.File(Shots.outDir, "j-placement-${bg.id}-${height}-$species.txt").writeText(
                        "density=${ctx.resources.displayMetrics.density} crop=${crop.x},${crop.y},${crop.w},${crop.h} " +
                        "feet=${stage.playerFeetX},${stage.playerFeetY};${stage.wildFeetX},${stage.wildFeetY} " +
                        "insets=${arena.playerSprite.drawnBottomInset()},${arena.enemySprite.drawnBottomInset()}")
                }
            }
        }
    }

    /** Conservative interior rectangles measured on the actual art, not the nominal sprite frames.
     * Left/top/right/bottom in source-image fractions; separate pads stay separate.
     */
    private fun ground(id: String, player: Boolean): FloatArray? {
        val pair = when (id) {
            "tropical-cove", "coral-lagoon", "sandbar-isle", "reef-bay" ->
                floatArrayOf(.31f, .87f, .44f, .96f) to floatArrayOf(.46f, .76f, .54f, .84f)
            "beach" -> floatArrayOf(.12f, .64f, .48f, .88f) to floatArrayOf(.55f, .39f, .89f, .53f)
            "city" -> floatArrayOf(.18f, .72f, .45f, .90f) to floatArrayOf(.55f, .51f, .80f, .68f)
            "desert" -> floatArrayOf(.18f, .70f, .44f, .85f) to floatArrayOf(.55f, .47f, .71f, .61f)
            "deep-sea-cave" -> floatArrayOf(.16f, .77f, .50f, .85f) to floatArrayOf(.71f, .57f, .93f, .64f)
            "dragon-peak" -> floatArrayOf(.16f, .76f, .49f, .86f) to floatArrayOf(.69f, .56f, .91f, .64f)
            "flower-field" -> floatArrayOf(.22f, .70f, .38f, .79f) to floatArrayOf(.67f, .43f, .81f, .51f)
            "forest-tech-b" -> floatArrayOf(.19f, .68f, .40f, .76f) to floatArrayOf(.67f, .47f, .90f, .55f)
            "windy-hilltop" -> floatArrayOf(.18f, .74f, .44f, .83f) to floatArrayOf(.71f, .64f, .84f, .68f)
            "countryside-route" -> floatArrayOf(.15f, .74f, .44f, .85f) to floatArrayOf(.66f, .56f, .86f, .61f)
            "coral-shallows" -> floatArrayOf(.15f, .77f, .44f, .88f) to floatArrayOf(.68f, .40f, .89f, .48f)
            "lava-field" -> floatArrayOf(.17f, .69f, .46f, .82f) to floatArrayOf(.70f, .44f, .87f, .51f)
            "starry-plateau" -> floatArrayOf(.15f, .74f, .45f, .83f) to floatArrayOf(.64f, .58f, .87f, .65f)
            "night-rooftops" -> floatArrayOf(.17f, .75f, .42f, .85f) to floatArrayOf(.71f, .46f, .87f, .52f)
            else -> return null // Approved controls: compare the actual PNGs byte for byte.
        }
        return if (player) pair.first else pair.second
    }

    private fun assertFeet(bg: Backdrop, sprite: SpriteView, image: SpriteImage, anchorY: Float,
                           crop: BackdropDraw.Crop, player: Boolean, panelHeight: Int) {
        val holder = sprite.parent as View
        val bmp = image.frames.single()
        val row = IntArray(bmp.width)
        val last = (0 until bmp.height).last { y ->
            bmp.getPixels(row, 0, bmp.width, 0, y, bmp.width, 1)
            row.any { (it ushr 24) > 16 }
        }
        bmp.getPixels(row, 0, bmp.width, 0, last, bmp.width, 1)
        val scale = sprite.drawnHeight().toFloat() / bmp.height
        val left = holder.left + sprite.left + (sprite.width - sprite.drawnWidth()) / 2
        val bottom = holder.top + sprite.top + sprite.height - sprite.drawnHeight() +
            sprite.drawnBottomInset() + (last + 1) * scale
        assertEquals("${bg.id} visible feet must meet the StageFit anchor", anchorY * sprite.resources.displayMetrics.density, bottom, 1.1f)
        val bounds = ground(bg.id, player) ?: return
        val sourceY = (crop.y + bottom / panelHeight * crop.h) / bg.h
        assertTrue("${bg.id} player=$player foot y=$sourceY", sourceY in bounds[1]..bounds[3])
        for (x in row.indices.filter { (row[it] ushr 24) > 16 }) {
            val screenX = left + (x + .5f) * scale
            val sourceX = (crop.x + screenX / 1080f * crop.w) / bg.w
            assertTrue("${bg.id} player=$player foot x=$sourceX", sourceX in bounds[0]..bounds[2])
        }
    }

}
