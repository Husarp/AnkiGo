package com.husarp.ankigo.ui

import org.junit.Assert.*
import org.junit.Test

class R29BeachPlacementTest {
    private val hosts = listOf(412f to 869f, 412f to 640f, 412f to 400f, 869f to 253f,
        240f to 135f, 200f to 200f, 90f to 600f, 360f to 200f, 412f to 230f) +
        listOf(.5f, .6f, 1f, 1.25f, 1.4f, 2.2f, 2.6f, 3.5f).map { it * 400f to 400f }

    @Test fun bothFeetStayInsideMeasuredDrySandAtEveryTestShape() {
        for (id in listOf("tropical-cove", "coral-lagoon", "sandbar-isle", "reef-bay")) {
            val bg = BackdropCatalog.byId(id)!!
            assertTrue(bg.groundZoom >= 1f)
            for ((w, h) in hosts) for (zoom in listOf(.72f, 1f, 1.5f)) {
                val stage = StageFit.fit(bg, w, h, zoom)!!
                val crop = BackdropDraw.stageCrop(bg, stage)
                val message = "$id $w x $h zoom $zoom"
                assertTrue(message, crop.h <= bg.h * .4f + .01f)
                assertEquals(message, w / h, crop.w / crop.h, .001f)
                assertFalse(message, BackdropDraw.layout(bg.w, bg.h, crop, 96, 64, w, h).anyFade)
                // Feet are fixed by StageFit; map them back through ArenaBackdrop's actual artwork crop.
                // This rectangle was measured on the four real PNGs; the offline Python check also samples sand pixels.
                for ((fx, fy) in listOf(stage.playerFeetX to stage.playerFeetY, stage.wildFeetX to stage.wildFeetY)) {
                    val x = (crop.x + fx / w * crop.w) / bg.w
                    val y = (crop.y + fy / h * crop.h) / bg.h
                    assertTrue("$message x=$x", x >= .34f && x <= .52f)
                    assertTrue("$message y=$y", y >= .79f && y <= .95f)
                }
            }
        }
    }

    @Test fun normalBattleWindowsKeepTheMeasuredZoomWithoutTallHostMagnification() {
        for (id in listOf("tropical-cove", "coral-lagoon", "sandbar-isle", "reef-bay")) {
            val bg = BackdropCatalog.byId(id)!!
            for ((w, h) in listOf(360f to 200f, 412f to 230f)) {
                val stage = StageFit.fit(bg, w, h)!!
                val crop = BackdropDraw.stageCrop(bg, stage)
                assertEquals(stage.cropW / 3f, crop.w, .01f)
                assertEquals(stage.cropH / 3f, crop.h, .01f)
            }
        }
    }
}
