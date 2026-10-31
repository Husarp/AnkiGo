package com.husarp.ankigo.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R19-F (PLAN 274): the sharp battle background, pure parts. The Lanczos reduction ([BackdropSharp.resample]), the decision "the GPU draws
 * it with no smoothing, or a reduced copy is needed" ([BackdropSharp.hard], [BackdropDraw.sharp]) per source and panel size, that the
 * crop / stand points are the ones [StageFit] always gave, and that the copy covers the panel and the Earthquake jolt's room.
 */
class R19FBackdropSharpTest {
    private fun px(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    private fun red(p: Int) = (p shr 16) and 0xFF

    // ---- the reduction ---------------------------------------------------------------------------------------------------

    @Test fun aFlatPictureStaysFlatAtAnyScale() {
        val flat = IntArray(40 * 30) { px(200, 100, 50) }
        for ((ow, oh) in listOf(20 to 15, 13 to 7, 40 to 30, 3 to 2)) {
            val out = BackdropSharp.resample(flat, 40, 30, 0f, 0f, 40f, 30f, ow, oh)
            assertEquals(ow * oh, out.size)
            assertTrue("$ow x $oh", out.all { it == px(200, 100, 50) })
        }
    }

    @Test fun oneToOneAtWholePixelsIsACopy() {
        val src = IntArray(16 * 12) { px((it * 7) and 0xFF, (it * 13) and 0xFF, (it * 29) and 0xFF) }
        val out = BackdropSharp.resample(src, 16, 12, 0f, 0f, 16f, 12f, 16, 12)
        assertTrue(src.indices.all { src[it] == out[it] })
        // a window starting on a whole pixel keeps the pixels too
        val part = BackdropSharp.resample(src, 16, 12, 4f, 2f, 12f, 8f, 8, 6)
        for (y in 0 until 6) for (x in 0 until 8) assertEquals("($x,$y)", src[(y + 2) * 16 + x + 4], part[y * 8 + x])
    }

    @Test fun aOnePixelCheckerboardHalvedIsMidGrey() {
        val src = IntArray(32 * 32) { if (((it % 32) + (it / 32)) % 2 == 0) px(0, 0, 0) else px(255, 255, 255) }
        val out = BackdropSharp.resample(src, 32, 32, 0f, 0f, 32f, 32f, 16, 16)
        for (y in 4 until 12) for (x in 4 until 12) assertEquals("($x,$y)", 127.5f, red(out[y * 16 + x]).toFloat(), 3f)
    }

    @Test fun aHardEdgeStaysHardWhenReduced() {
        // black left half, white right half, 100 px wide, reduced to 40 px (2.5 x): the edge is crossed in at most two pixels
        val src = IntArray(100 * 4) { if (it % 100 < 50) px(0, 0, 0) else px(255, 255, 255) }
        val out = BackdropSharp.resample(src, 100, 4, 0f, 0f, 100f, 4f, 40, 4)
        val mixed = (0 until 40).count { red(out[it]) in 24..231 }
        assertTrue("$mixed mixed pixels", mixed <= 2)
        assertTrue(red(out[10]) <= 4); assertTrue(red(out[30]) >= 251)
        // and it stays opaque
        assertTrue(out.all { (it ushr 24) == 0xFF })
    }

    @Test fun onlyTheAskedWindowIsUsedAndTheEdgesRepeat() {
        val src = IntArray(20 * 10) { if (it % 20 < 10) px(0, 0, 0) else px(255, 255, 255) }
        val right = BackdropSharp.resample(src, 20, 10, 12f, 0f, 20f, 10f, 4, 5)
        assertTrue(right.all { red(it) >= 250 })
        // a window reaching past the picture repeats its end pixels (no exception, right size, still white at the far side)
        val past = BackdropSharp.resample(src, 20, 10, 10f, 0f, 40f, 10f, 15, 5)
        assertEquals(15 * 5, past.size)
        assertTrue(red(past[14]) >= 250)
    }

    @Test fun aGradientKeepsItsMeanAndOrder() {
        val w = 200
        val src = IntArray(w * 2) { px((it % w) * 255 / (w - 1), 0, 0) }
        val out = BackdropSharp.resample(src, w, 2, 0f, 0f, w.toFloat(), 2f, 70, 2)
        for (i in 1 until 70) assertTrue("monotone at $i", red(out[i]) >= red(out[i - 1]))
        assertEquals(127.5f, (0 until 70).map { red(out[it]) }.average().toFloat(), 2f)
    }

    // ---- the decision -------------------------------------------------------------------------------------------------------

    @Test fun aPictureAtOrAboveTheThresholdIsDrawnHardAndUnderItIsReduced() {
        assertTrue(BackdropSharp.hard(2.1f)); assertTrue(BackdropSharp.hard(1f)); assertTrue(BackdropSharp.hard(BackdropSharp.HARD_MIN))
        assertFalse(BackdropSharp.hard(0.84f)); assertFalse(BackdropSharp.hard(0.45f))
    }

    /** The layout of [bg] in an arena of [wDp] x [hDp] dp at [d] px per dp (the stage is worked out in dp, the picture drawn in px). */
    private fun scaled(bg: Backdrop, wDp: Float, hDp: Float, d: Float): BackdropDraw.Layout {
        val st = StageFit.fit(bg, wDp, hDp)!!
        return BackdropDraw.layout(bg.w, bg.h, BackdropDraw.Crop(st.cropX, st.cropY, st.cropW, st.cropH), 96, 64, wDp * d, hDp * d)
    }

    @Test fun theCopyIsOnlyMadeForAReduction() {
        val jungle = BackdropCatalog.byId("jungle")!! // a 1264 x 848 file
        // a 412 x 400 dp arena at 1 px per dp: the picture is cut down to well under one screen px per file px
        assertNotNull(BackdropDraw.sharp(scaled(jungle, 412f, 400f, 1f), 412f, 400f, 1f, 1f, 1264, 848, 1f))
        // the same arena on a 420 dpi phone: about 1 screen px per file px, so the GPU draws it with no smoothing
        val big = scaled(jungle, 412f, 400f, 2.625f)
        assertNull(BackdropDraw.sharp(big, 1081.5f, 1050f, 1f, 1f, 1264, 848, 1f))
        // the panel's own on-screen scale counts: the same virtual panel shown at half size needs the copy
        assertNotNull(BackdropDraw.sharp(big, 1081.5f, 1050f, 1f, 1f, 1264, 848, 0.5f))
    }

    @Test fun theCopyShowsTheLayersRectangleInScreenPixels() {
        val canyon = BackdropCatalog.byId("canyon")!!
        val d = 2.625f
        val w = 390f * d; val h = 180f * d // the picture-in-picture's virtual arena, drawn at 0.6157 on the screen
        val l = scaled(canyon, 390f, 180f, d)
        val s = BackdropDraw.sharp(l, w, h, 1f, 1f, 1375, 768, 0.6157f)!!
        val v = BackdropDraw.visible(l, w, h)
        assertEquals(v[0], s.dstL, 1e-3f); assertEquals(v[1], s.dstT, 1e-3f); assertEquals(v[2], s.dstR, 1e-3f); assertEquals(v[3], s.dstB, 1e-3f)
        assertEquals(Math.round((v[2] - v[0]) * 0.6157f), s.outW); assertEquals(Math.round((v[3] - v[1]) * 0.6157f), s.outH)
        // the source window is inside the file and in file pixels
        assertTrue(s.srcL >= 0f && s.srcT >= 0f && s.srcR <= 1375f && s.srcB <= 768f)
        assertEquals((v[2] - v[0]) / l.kx, s.srcR - s.srcL, 0.01f)
        // a file of half the catalog's size: the same part of the picture in half as many file pixels
        val half = BackdropDraw.sharp(l, w, h, 0.5f, 0.5f, 688, 384, 0.5f)!! // (a half-size file is twice as big per picture pixel: the screen scale is lowered to keep it a reduction)
        assertEquals((s.srcR - s.srcL) / 2f, half.srcR - half.srcL, 0.05f)
        // another screen scale only changes the copy's size, not what it shows
        val other = BackdropDraw.sharp(l, w, h, 1f, 1f, 1375, 768, 0.5f)!!
        assertTrue(s.sameView(other)); assertTrue(other.outW < s.outW)
    }

    // ---- the real arenas ------------------------------------------------------------------------------------------------------

    /** The three hosts as the preview measured them (virtual arena in dp, the panel's on-screen scale). */
    private class Host(val name: String, val wDp: Float, val hDp: Float, val screen: Float)

    private val hosts = listOf(
        Host("battle tab", 412f, 691.5f, 1f),
        Host("study panel", 651f, 209.5f, 0.633f),
        Host("picture in picture", 390f, 180f, 0.6157f),
    )
    private val density = 2.625f

    @Test fun theStandPointsStayInsideThePanelInEveryHostForEveryPicture() {
        for (h in hosts) for (bg in BackdropCatalog.ALL) {
            val st = StageFit.fit(bg, h.wDp, h.hDp)!!
            for ((x, y) in listOf(st.playerFeetX to st.playerFeetY, st.wildFeetX to st.wildFeetY)) {
                assertTrue("${bg.id} @ ${h.name}: feet ($x, $y) in ${h.wDp} x ${h.hDp}", x in 0f..h.wDp && y in 0f..h.hDp)
            }
            // the crop (and so the stage) is what StageFit gave: it holds both feet with room above, as before
            assertTrue("${bg.id} @ ${h.name}", st.cropW > 0f && st.cropH > 0f)
        }
    }

    @Test fun theCopyCoversThePanelAndTheJoltsRoomWhereThePictureReaches() {
        var made = 0
        for (h in hosts) for (bg in BackdropCatalog.ALL) {
            val wPx = h.wDp * density; val hPx = h.hDp * density
            val l = scaled(bg, h.wDp, h.hDp, density)
            val s = BackdropDraw.sharp(l, wPx, hPx, 1f, 1f, bg.w, bg.h, h.screen) ?: continue
            made++
            val tag = "${bg.id} @ ${h.name}"
            // the layer's rectangle (picture cut to the panel plus the overscan): Earthquake's jolt (at most JOLT_MAX of the height) never shows past it where the picture reaches
            assertTrue("$tag: jolt inside the overscan", BackdropDraw.JOLT_MAX < BackdropDraw.OVERSCAN)
            if (l.picT <= -BackdropDraw.JOLT_MAX * hPx) assertTrue("$tag top", s.dstT <= -BackdropDraw.JOLT_MAX * hPx + 0.01f)
            if (l.picB >= hPx * (1f + BackdropDraw.JOLT_MAX)) assertTrue("$tag bottom", s.dstB >= hPx * (1f + BackdropDraw.JOLT_MAX) - 0.01f)
            // the panel itself is covered wherever the picture reaches (a side that ends inside the panel fades into the blur)
            assertTrue("$tag left", s.dstL <= 0.01f || l.fadeLeft); assertTrue("$tag right", s.dstR >= wPx - 0.01f || l.fadeRight)
            assertTrue("$tag top", s.dstT <= 0.01f || l.fadeTop); assertTrue("$tag bottom", s.dstB >= hPx - 0.01f || l.fadeBottom)
            assertTrue("$tag: source inside the file", s.srcL >= 0f && s.srcT >= 0f && s.srcR <= bg.w && s.srcB <= bg.h && s.srcR > s.srcL && s.srcB > s.srcT)
            assertTrue(tag, s.outW >= 1 && s.outH >= 1 && s.outW <= wPx * (1f + 2f * BackdropDraw.OVERSCAN) * h.screen + 2 && s.outH <= hPx * (1f + 2f * BackdropDraw.OVERSCAN) * h.screen + 2)
        }
        assertTrue("some copies were checked ($made)", made > 20)
    }

    @Test fun onAPhoneTheBattleTabNeedsNoCopyButThePipDoes() {
        val battle = hosts[0]; val pip = hosts[2]
        val none = BackdropCatalog.ALL.count { BackdropDraw.sharp(scaled(it, battle.wDp, battle.hDp, density), battle.wDp * density, battle.hDp * density, 1f, 1f, it.w, it.h, battle.screen) == null }
        assertEquals("all backgrounds are drawn hard on the Battle tab", BackdropCatalog.ALL.size, none)
        val some = BackdropCatalog.ALL.count { BackdropDraw.sharp(scaled(it, pip.wDp, pip.hDp, density), pip.wDp * density, pip.hDp * density, 1f, 1f, it.w, it.h, pip.screen) != null }
        assertTrue("the picture-in-picture reduces most of them ($some)", some >= 20)
    }

    @Test fun everyCatalogPictureIsCrispExceptTheFactory() {
        assertTrue(BackdropCatalog.ALL.filter { it.id != "factory" }.all { it.crisp })
        assertFalse(BackdropCatalog.byId("factory")!!.crisp)
    }

    /** Showdown and the factory keep their original drawing; other own scenes use the sharp copy. */
    @Test fun showdownAndFactoryKeepTodaysDrawingOtherOwnScenesAreSharp() {
        val today = BackdropCatalog.ALL.filter { !it.sharp }
        assertEquals(12, today.size)
        assertTrue(today.filter { it.source == BackdropSource.SHOWDOWN }.size == 11 && today.any { it.id == "factory" })
        assertEquals(35, BackdropCatalog.ALL.count { it.sharp && it.source == BackdropSource.OWN })
        // today's Showdown = nearest neighbour, the factory = filtered
        assertTrue(today.filter { it.source == BackdropSource.SHOWDOWN }.all { it.crisp })
    }
}
