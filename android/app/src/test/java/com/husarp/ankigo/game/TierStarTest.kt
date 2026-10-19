package com.husarp.ankigo.game

import com.husarp.ankigo.ui.AchStyle
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.hypot

/** R9 D6-C2: the Mythical star has 16 points on the three radii of the mockup. */
class TierStarTest {
    @Test fun sixteenPointsOnTheRadii() {
        val pts = AchStyle.starPoints()
        assertEquals(16, pts.size)
        pts.forEachIndexed { i, (x, y) ->
            val want = if (i % 2 == 1) 2.9 else if (i % 4 == 0) 7.4 else 5.9
            assertEquals("point $i", want, hypot((x - 8f).toDouble(), (y - 8f).toDouble()), 0.001)
        }
        // first point straight up
        assertEquals(8f, pts[0].first, 0.001f); assertEquals(0.6f, pts[0].second, 0.001f)
    }
}
