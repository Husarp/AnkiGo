package com.husarp.ankigo.ui

import com.husarp.ankigo.game.Type
import org.junit.Assert.*
import org.junit.Test

class R28OBackdropTest {
    @Test fun everySceneCoversEveryHostWithoutBlurBands() {
        for (bg in BackdropCatalog.ALL) for ((w,h) in listOf(412f to 869f, 412f to 640f, 412f to 400f, 869f to 253f, 240f to 135f, 200f to 200f, 90f to 600f)) for (zoom in listOf(.72f, 1f, 1.5f)) {
            val stage = StageFit.fit(bg,w,h,zoom)!!
            val crop = BackdropDraw.stageCrop(bg,stage)
            val layout = BackdropDraw.layout(bg.w,bg.h,crop,96,64,w,h)
            assertFalse("${bg.id} $w x $h zoom $zoom",layout.anyFade)
            assertEquals(w/h,crop.w/crop.h,.001f)
        }
    }
    @Test fun beachesAreWaterOnlyWithFixedWildFeetAndLosslessArt() {
        for(id in listOf("tropical-cove","coral-lagoon","sandbar-isle","reef-bay")) {
            val bg=BackdropCatalog.byId(id)!!
            assertEquals(listOf(Type.WATER),Type.entries.filter { bg in BackdropCatalog.options(it) })
            assertEquals(.74f,bg.wild.x,0f);assertEquals(.54f,bg.wild.y,0f)
            assertEquals("png",bg.ext);assertTrue(bg.groundZoom>=1f)
        }
    }
}
