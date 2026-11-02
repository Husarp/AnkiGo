package com.husarp.ankigo.shots

import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import android.widget.LinearLayout
import com.husarp.ankigo.data.Backdrops
import com.husarp.ankigo.data.Sprites
import com.husarp.ankigo.ui.ArenaBackdrop
import com.husarp.ankigo.ui.BackdropSharp
import org.junit.After
import com.husarp.ankigo.data.SpriteImage
import com.husarp.ankigo.gfx.SpriteView
import com.husarp.ankigo.ui.BackdropDraw
import com.husarp.ankigo.ui.BackdropCatalog
import com.husarp.ankigo.ui.StageFit
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Offline visual audit: real scene art and real padded Gen-5 sprites, through production feet anchoring. */
class R26EPlacementShots {
    @get:Rule val paparazzi = Shots.rule()

    @After fun cleanup() { Backdrops.forget(); BackdropSharp.runner = BackdropSharp.InPlace }

    @Test fun everySceneWithSmallAndLargeWildPokemon() {
        val ctx = Shots.files(paparazzi.context)
        BackdropSharp.runner = BackdropSharp.InPlace
        for (bg in BackdropCatalog.ALL) {
            val column = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
            for (species in listOf("eevee", "snorlax")) {
                val spriteBytes = javaClass.getResourceAsStream("/r26e/$species.png")!!.use { it.readBytes() }
                val image = BitmapFactory.decodeByteArray(spriteBytes, 0, spriteBytes.size)!!
                val sprite = SpriteView(ctx).apply {
                    anchorFeet = true
                    byFrame = true
                    setImage(SpriteImage(listOf(image), intArrayOf(100)))
                }
                val pixels = IntArray(image.width * image.height)
                image.getPixels(pixels, 0, image.width, 0, 0, image.width, image.height)
                val lastRow = (0 until image.height).last { y ->
                    (0 until image.width).any { x -> (pixels[y * image.width + x] ushr 24) > 16 }
                }
                sprite.layout(0, 0, 192, 192)
                assertEquals("$species transparent rows must land below the anchored feet",
                    (image.height - lastRow - 1) * 2f, sprite.drawnBottomInset().toFloat(), 1f)
                // Use shipped art or an offline copy of the actual download (settings thumbnails
                // for older scenes can show different artwork and cannot prove placement).
                val stream = runCatching { ctx.assets.open("backdrops/${Backdrops.fileName(bg)}") }.getOrNull()
                    ?: checkNotNull(javaClass.getResourceAsStream("/r26e/backgrounds/${bg.id}.webp")) { bg.id }
                val bytes = stream.use { it.readBytes() }
                val file = Backdrops.file(Sprites.dir(ctx), bg)
                file.parentFile!!.mkdirs()
                file.writeBytes(bytes)
                val pic = Backdrops.fetchBlocking(Sprites.dir(ctx), bg, false) { _, _ -> null }!!
                Backdrops.finish(Backdrops.currentGeneration(), bg.id, pic)
                val back = ArenaBackdrop(ctx).apply { reduceMotion = { true } }
                back.request(bg, false)
                val panel = object : View(ctx) {
                    override fun onDraw(canvas: Canvas) {
                        val stage = StageFit.fit(bg, width.toFloat(), height.toFloat())!!
                        back.setStage(stage)
                        back.setBounds(0, 0, width, height)
                        back.draw(canvas)
                        val side = stage.wildFrame.toInt()
                        sprite.layout(0, 0, side, side)
                        canvas.save()
                        canvas.translate(stage.wildFeetX - side / 2f, stage.wildFeetY - side)
                        sprite.draw(canvas)
                        canvas.restore()
                        canvas.drawCircle(stage.playerFeetX, stage.playerFeetY, 4f, Paint().apply { color = 0xff00ff00.toInt() })
                        canvas.drawText("${bg.id} / $species", 8f, 22f, Paint().apply { color = -1; textSize = 18f })
                    }
                }
                column.addView(panel, LinearLayout.LayoutParams(824, 520))
            }
            paparazzi.snapshot(column, "r26e-${bg.id}")
        }
    }

    @Test fun correctedSpotsStayOnTheNearSurfaceInEveryPanelShape() {
        for ((id, minY, maxY) in listOf(Triple("windy-hilltop", .64f, .68f),
            Triple("coral-shallows", .43f, .48f), Triple("old-graveyard", .65f, .71f),
            Triple("desert-oasis", .59f, .63f))) {
            val bg = BackdropCatalog.byId(id)!!
            assertTrue(id, bg.wildGround.y in minY..maxY)
        }
        for (bg in BackdropCatalog.ALL) {
            val id = bg.id
            for ((w, h) in listOf(412f to 869f, 412f to 400f, 869f to 300f, 240f to 135f, 200f to 200f)) {
                val stage = StageFit.fit(bg, w, h)!!
                val crop = BackdropDraw.stageCrop(bg, stage)
                assertEquals(id, w / h, crop.w / crop.h, .001f)
                // R28: cover takes priority where measured-ground alignment would expose an edge.
                val layout = BackdropDraw.layout(bg.w, bg.h, crop, 96, 64, w, h)
                assertFalse("$id $w x $h must cover the panel", layout.anyFade)
                assertTrue(id, crop.x >= -.001f && crop.y >= -.001f)
                assertTrue(id, crop.x + crop.w <= bg.w + .001f && crop.y + crop.h <= bg.h + .001f)
                assertTrue(id, stage.wildFeetY in 0f..h)
            }
        }
    }
}
