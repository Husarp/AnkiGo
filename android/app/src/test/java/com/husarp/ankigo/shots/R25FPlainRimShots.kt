package com.husarp.ankigo.shots

import android.graphics.Bitmap
import android.graphics.Canvas
import com.husarp.ankigo.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class R25FPlainRimShots {
    @get:Rule val paparazzi = Shots.rule()

    @org.junit.After fun reset() { BattleShotKit.reset() }

    @Test fun automaticBattleHosts() {
        paparazzi.shootWith("r25f-automatic-battle", listOf(Size.PHONE, Size.PIP)) { ctx ->
            AppPrefs.setBattleFrame(ctx, BattleFrameStyle.AUTOMATIC)
            BattleShotKit.bound(ctx).root
        }
    }

    @Test fun allSceneriesHavePlainCrispRimsAtEveryDensity() {
        assertEquals(BackdropCatalog.ALL.map { it.id }.toSet(), SceneryRims.groups.keys)
        for (dark in listOf(false, true)) for (scene in BackdropCatalog.ALL) {
            val colors = SceneryRims.colors(scene.id, dark)
            for (unit in listOf(1, 2, 3, 4, 5, 7)) {
                val w = 180 * unit + 1; val h = 150 * unit + 3
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                val dr = BattleFrameDrawable(BattleFrameStyle.AUTOMATIC, unit).apply {
                    scenery = scene.id; this.dark = dark; setBounds(0, 0, w, h)
                }
                dr.draw(Canvas(bmp))
                val pixels = IntArray(w * h)
                bmp.getPixels(pixels, 0, w, 0, 0, w, h)
                for (y in 0 until h) for (x in 0 until w) {
                    val depth = minOf(x, y, w - 1 - x, h - 1 - y) / unit
                    val expected = if (depth < 3) colors[depth] else 0
                    if (pixels[y * w + x] != expected) fail("${scene.id} dark=$dark unit=$unit at $x,$y")
                }
                if (unit == 2) {
                    // Transparent centre intentionally exposes any accidental decorative pixels.
                    FakeSprites.write(FakeSprites.Img(w, h).also { pixels.copyInto(it.px) }, File(Shots.outDir, "r25f-${scene.id}-${if (dark) "dark" else "light"}.png"))
                }
                bmp.recycle()
            }
        }
        assertEquals(3, BattleFrameStyle.AUTOMATIC.t)
        assertEquals(0, BattleFrameStyle.AUTOMATIC.cornerUnits)
        assertArrayEquals(intArrayOf(0xFF56738A.toInt(), 0xFFBED9DF.toInt(), 0xFF304D65.toInt()), SceneryRims.colors("icecave", false))
        assertArrayEquals(intArrayOf(0xFF486174.toInt(), 0xFFA0B6BB.toInt(), 0xFF284155.toInt()), SceneryRims.colors("icecave", true))
    }
}
