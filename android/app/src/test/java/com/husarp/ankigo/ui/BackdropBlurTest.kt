package com.husarp.ankigo.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** R8-03: the blurred, darkened copy of a battle background (pure maths on ARGB ints). */
class BackdropBlurTest {
    private fun argb(a: Int, r: Int, g: Int, b: Int) = (a shl 24) or (r shl 16) or (g shl 8) or b
    private fun r(p: Int) = (p shr 16) and 0xFF
    private fun g(p: Int) = (p shr 8) and 0xFF
    private fun b(p: Int) = p and 0xFF

    @Test fun theCopyHasALongSideOfAtMost96AndKeepsTheShape() {
        assertEquals(96 to 64, BackdropBlur.smallSize(1264, 848))
        assertEquals(96 to 41, BackdropBlur.smallSize(753, 325))
        val (w, h) = BackdropBlur.smallSize(316, 212)
        assertEquals(96, w); assertTrue(h in 63..65)
        for ((pw, ph) in listOf(700 to 500, 753 to 500, 1264 to 848)) {
            val s = BackdropBlur.smallSize(pw, ph)
            assertEquals(96, maxOf(s.first, s.second))
            assertEquals(pw.toFloat() / ph, s.first.toFloat() / s.second, 0.03f)
        }
    }

    @Test fun aSmallPictureIsNotEnlargedAndNothingIsEverZero() {
        assertEquals(40 to 30, BackdropBlur.smallSize(40, 30))
        assertEquals(96 to 1, BackdropBlur.smallSize(3000, 4))
        assertEquals(1 to 1, BackdropBlur.smallSize(0, 10))
    }

    @Test fun aFlatPictureStaysFlat() {
        val px = IntArray(30 * 20) { argb(255, 180, 90, 40) }
        val out = BackdropBlur.blur(px, 30, 20)
        assertTrue(out.all { it == px[0] })
    }

    @Test fun aFlatGreyIsDarkenedByTheBrightnessAndStaysFlatAndGrey() {
        val px = IntArray(24 * 16) { argb(255, 200, 200, 200) }
        val out = BackdropBlur.process(px, 24, 16)
        assertEquals(24 * 16, out.size)
        val want = (200 * BackdropBlur.BRIGHTNESS).toInt() // 110
        for (p in out) {
            assertEquals(255, p ushr 24)
            assertTrue(Math.abs(want - r(p)) <= 1 && Math.abs(want - g(p)) <= 1 && Math.abs(want - b(p)) <= 1)
        }
        assertTrue(out.all { it == out[0] })
    }

    @Test fun saturationPushesAColourAwayFromItsGrey() {
        val out = BackdropBlur.process(IntArray(8 * 8) { argb(255, 200, 100, 50) }, 8, 8)[0]
        val dr = 200 * BackdropBlur.BRIGHTNESS; val db = 50 * BackdropBlur.BRIGHTNESS
        // the spread between red and blue grew by about the saturation factor
        assertEquals((dr - db) * BackdropBlur.SATURATION, (r(out) - b(out)).toFloat(), 2f)
        assertTrue(r(out) > dr && b(out) < db)
    }

    @Test fun aSharpEdgeSpreadsOverAboutTheWindowAndKeepsItsEnds() {
        val w = 40; val h = 6
        val px = IntArray(w * h) { if (it % w < w / 2) argb(255, 0, 0, 0) else argb(255, 255, 255, 255) }
        val out = BackdropBlur.blur(px, w, h)
        val row = IntArray(w) { r(out[it]) } // first row
        for (y in 0 until h) for (x in 0 until w) assertEquals(row[x], r(out[y * w + x])) // rows stay equal (the edge is vertical)
        assertEquals(0, row[0]); assertEquals(255, row[w - 1])           // far from the edge: unchanged
        assertTrue("the edge is soft", row[w / 2 - 1] in 1..254 && row[w / 2] in 1..254)
        for (x in 1 until w) assertTrue("rises monotonically", row[x] >= row[x - 1])
        // three passes of a window of 5 reach 3 * 2 = 6 pixels each way: nothing changes further than that
        assertEquals(0, row[w / 2 - 7]); assertEquals(255, row[w / 2 + 6])
    }

    @Test fun theBlurKeepsTheAverageBrightness() {
        val w = 32; val h = 24
        val px = IntArray(w * h) { val v = ((it * 37) % 256); argb(255, v, 255 - v, (v * 3) % 256) }
        val out = BackdropBlur.blur(px, w, h)
        fun mean(a: IntArray, f: (Int) -> Int) = a.map(f).average()
        // the edges repeat their end pixel, so the mean moves a little, not much
        assertEquals(mean(px, ::r), mean(out, ::r), 12.0)
        assertEquals(mean(px, ::g), mean(out, ::g), 12.0)
    }

    @Test fun theInputIsNotChanged() {
        val px = IntArray(10 * 10) { argb(255, it, 255 - it, it * 2 % 256) }
        val copy = px.copyOf()
        BackdropBlur.process(px, 10, 10)
        assertEquals(copy.toList(), px.toList())
    }

    @Test fun aSinglePixelAndAThinStripAreFine() {
        assertEquals(1, BackdropBlur.process(intArrayOf(argb(255, 10, 20, 30)), 1, 1).size)
        val strip = IntArray(50) { argb(255, if (it < 25) 0 else 200, 0, 0) }
        val out = BackdropBlur.blur(strip, 50, 1)
        assertEquals(50, out.size)
        assertTrue(r(out[24]) in 1..199)
    }

    @Test fun alphaIsKept() {
        val out = BackdropBlur.process(IntArray(6 * 6) { argb(128, 100, 100, 100) }, 6, 6)
        assertTrue(out.all { (it ushr 24) == 128 })
    }

    @Test fun gradingNeverLeavesTheByteRange() {
        val out = BackdropBlur.grade(intArrayOf(argb(255, 255, 0, 0), argb(255, 0, 255, 0), argb(255, 255, 255, 255), argb(255, 0, 0, 0)), 1.6f, 3f)
        for (p in out) { assertTrue(r(p) in 0..255 && g(p) in 0..255 && b(p) in 0..255) }
        assertEquals(argb(255, 0, 0, 0), out[3])
    }
}
