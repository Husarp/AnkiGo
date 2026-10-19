package com.husarp.ankigo.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

// ROUND 9 wave 3 (R9-W3-D): the three new pictures (legendary-shrine, ruins-courtyard, dark-town) in every arena host shape of
// docs/design/r9/arena.js (R9_SHAPES: on-screen size in dp, the panel's draw scale s, the floating window's 0.72 zoom): both Pokémon's
// feet inside the arena, their frames (feet row up to the 0.82 art height, half a frame to each side) not cut off.
class R9NewBackdropStageTest {
    private val ids = listOf("legendary-shrine", "ruins-courtyard", "dark-town")

    // name, on-screen width, height (dp), draw scale s, zoom
    private val shapes = listOf(
        Shape("battle", 412f, 640f, 1f, 1f), Shape("study-tall", 412f, 265f, 1f, 1f), Shape("study-short", 412f, 134f, .634f, 1f),
        Shape("strip", 412f, 146f, 1f, 1f), Shape("land-study", 365f, 266f, 1f, 1f), Shape("land-battle", 860f, 253f, 1f, 1f),
        Shape("split", 206f, 136f, 1f, 1f), Shape("float", 240f, 130f, .61f, .72f), Shape("pip", 246f, 138f, .65f, 1f),
    )

    private class Shape(val name: String, val w: Float, val h: Float, val s: Float, val zoom: Float)

    @Test fun feetAndFramesStayInsideTheArenaInEveryHostShape() {
        for (id in ids) for (sh in shapes) {
            val b = BackdropCatalog.byId(id)!!
            val vw = sh.w / sh.s; val vh = sh.h / sh.s
            val st = StageFit.fit(b, vw, vh, sh.zoom)
            assertNotNull("$id ${sh.name}", st)
            st!!
            val tag = "$id ${sh.name}"
            val e = 0.5f
            for ((who, x, y, f) in listOf(
                Foot("ours", st.playerFeetX, st.playerFeetY, st.playerFrame), Foot("wild", st.wildFeetX, st.wildFeetY, st.wildFrame),
            )) {
                assertTrue("$tag $who feet x", x >= -e && x <= vw + e)
                assertTrue("$tag $who feet y", y >= -e && y <= vh + e)
                assertTrue("$tag $who frame left/right", x - f / 2 >= -e && x + f / 2 <= vw + e)
                assertTrue("$tag $who art top", y - f * .82f >= -e)
            }
            assertTrue("$tag wild stands higher", st.wildFeetY < st.playerFeetY)
            assertTrue("$tag wild stands right of ours", st.wildFeetX > st.playerFeetX)
            assertEquals("$tag crop shape", vw / vh, st.cropW / st.cropH, 0.001f)
        }
    }

    private data class Foot(val who: String, val x: Float, val y: Float, val f: Float)
}
