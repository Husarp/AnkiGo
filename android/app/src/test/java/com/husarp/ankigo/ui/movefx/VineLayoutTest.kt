package com.husarp.ankigo.ui.movefx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** R7-FIX 3: the Vine Whip pixel buffer is sized from the vines, so it never clips, at any unit (a study strip to a tablet). Pure JVM. */
class VineLayoutTest {
    private fun space(unit: Float, reverse: Boolean) =
        if (reverse) FxSpace(FxSpace.DX * unit, FxSpace.DY * unit, FxSpace.AX * unit, FxSpace.AY * unit, unit)
        else FxSpace(FxSpace.AX * unit, FxSpace.AY * unit, FxSpace.DX * unit, FxSpace.DY * unit, unit)

    private fun touchesEdge(r: VineRaster): Boolean {
        for (x in 0 until r.bw) if (r.pix[x] != 0 || r.pix[(r.bh - 1) * r.bw + x] != 0) return true
        for (y in 0 until r.bh) if (r.pix[y * r.bw] != 0 || r.pix[y * r.bw + r.bw - 1] != 0) return true
        return false
    }

    @Test fun noVinePixelEverTouchesTheBufferEdge() {
        for (unit in floatArrayOf(.3f, .6f, 1f, 2.5f)) for (reverse in listOf(false, true)) for (k in floatArrayOf(.8f, 1f, 1.34f, 1.35f, 1.6f)) {
            val sp = space(unit, reverse)
            val dev = sp.artPixels(k)
            val lay = VineWhipFx.layout(sp, k, dev)
            val vines = VineWhipFx.vinesFor(k)
            val r = VineRaster(lay.bw, lay.bh, k)
            var drawn = 0
            var ms = 0f
            while (ms <= VineWhipFx.TOT + 3 * 210f) {
                r.clear()
                for (i in vines.indices) {
                    val v = vines[i]
                    if (ms >= v.t0 && ms < v.t0 + VineWhipFx.TOT) r.vine(v.len, lay.baseX[i], lay.baseY[i], lay.sdir[i], ms - v.t0)
                }
                if (r.pix.any { it != 0 }) drawn++
                assertTrue("unit $unit reverse $reverse k $k at $ms ms: a vine pixel on the edge of a ${lay.bw} x ${lay.bh} buffer", !touchesEdge(r))
                ms += 10f
            }
            assertTrue("unit $unit k $k: the vines were drawn at all", drawn > 20)
        }
    }

    @Test fun theBufferIsSmallerOnASmallArenaAndNeverHuge() {
        val small = VineWhipFx.layout(space(.6f, false), 1f, space(.6f, false).artPixels(1f))
        val big = VineWhipFx.layout(space(2.5f, false), 1f, space(2.5f, false).artPixels(1f))
        assertTrue("${small.bw} x ${small.bh}", small.bw * small.bh < 20_000)
        assertTrue("${big.bw} x ${big.bh}", big.bw * big.bh < 60_000)
        assertTrue(small.bw >= 1 && small.bh >= 1)
    }

    @Test fun theRootsSitInsideTheBufferWithRoomForTheVineAndTheirSidesFollowTheDirection() {
        for (reverse in listOf(false, true)) {
            val sp = space(1f, reverse)
            val dev = sp.artPixels(1f)
            val lay = VineWhipFx.layout(sp, 1f, dev)
            val vines = VineWhipFx.vinesFor(1f)
            for (i in vines.indices) {
                // 28 art pixels long (the longest) and a margin, in every direction up from the root
                assertTrue(lay.baseY[i] >= vines[i].len)
                assertTrue(lay.baseX[i] >= vines[i].len && lay.baseX[i] <= lay.bw - vines[i].len)
                assertEquals(vines[i].dir * sp.dir, lay.sdir[i], 0f)
                // the root really is where fromTarget puts it, in buffer pixels
                val g = sp.fromTarget(vines[i].gx, 42f + vines[i].gy)
                assertEquals(g.x, lay.left + lay.baseX[i] * dev, .01f)
                assertEquals(g.y, lay.top + lay.baseY[i] * dev, .01f)
            }
        }
    }
}
