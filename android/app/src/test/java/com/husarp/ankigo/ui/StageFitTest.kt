package com.husarp.ankigo.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min

// ROUND 8 R8-02: the stage rule's crop, feet and frame sizes (port of docs/design/backgrounds-stage-r8.html), golden values + properties.
class StageFitTest {
    private fun bg(id: String) = BackdropCatalog.byId(id)!!

    private val shapes = listOf(0.5f, 0.6f, 1f, 1.25f, 1.4f, 2.2f, 2.6f, 3.5f)

    private fun stage(id: String, w: Float, h: Float, zoom: Float = 1f) = StageFit.fit(bg(id), w, h, zoom)!!

    // the generic safe box, recomputed here (picture pixels): l, t, r, b
    private fun safe(b: Backdrop): FloatArray {
        val iw = b.w.toFloat()
        val ih = b.h.toFloat()
        var l = Float.MAX_VALUE; var t = Float.MAX_VALUE; var r = -Float.MAX_VALUE; var bt = -Float.MAX_VALUE
        for (s in listOf(b.player, b.wild)) {
            val f = s.s * ih
            l = min(l, s.x * iw - f * 0.5f); r = max(r, s.x * iw + f * 0.5f)
            t = min(t, s.y * ih - f * 0.82f); bt = max(bt, s.y * ih)
        }
        val pad = 0.025f * ih
        return floatArrayOf(max(0f, l - pad), max(0f, t - pad), min(iw, r + pad), min(ih, bt + pad))
    }

    // ---------------------------------------------------------------- golden values (the page's JS with the round 8 clamp change)

    private fun golden(
        s: Stage, scale: Float, cx: Float, cy: Float, cw: Float, ch: Float, shrunk: Boolean,
        px: Float, py: Float, pf: Float, wx: Float, wy: Float, wf: Float,
    ) {
        val d = 0.05f
        assertEquals("cropX", cx * scale, s.cropX, d * scale + 0.01f)
        assertEquals("cropY", cy * scale, s.cropY, d * scale + 0.01f)
        assertEquals("cropW", cw * scale, s.cropW, d * scale + 0.01f)
        assertEquals("cropH", ch * scale, s.cropH, d * scale + 0.01f)
        assertEquals("shrunk", shrunk, s.shrunk)
        assertEquals("playerFeetX", px, s.playerFeetX, d)
        assertEquals("playerFeetY", py, s.playerFeetY, d)
        assertEquals("playerFrame", pf, s.playerFrame, d)
        assertEquals("wildFeetX", wx, s.wildFeetX, d)
        assertEquals("wildFeetY", wy, s.wildFeetY, d)
        assertEquals("wildFrame", wf, s.wildFrame, d)
    }

    @Test fun goldenForest() =
        golden(stage("forest", 1400f, 1000f), 1f, 26.50f, 0f, 700f, 500f, false, 398.8f, 780.0f, 440.0f, 971.1f, 500.0f, 320.0f)

    // round 9 D10: the catalog lost its 316 x 212 pixel-grid pictures and the wide set; these goldens come from a port of the page's maths (checked
    // against goldenForest and the old night-alley zoomed-window golden before the drop), so they pin StageFit for the pictures that are left.
    @Test fun goldenForestWideShrunk() =
        golden(stage("forest", 2600f, 1000f), 1f, -8.56f, 106.30f, 770.12f, 296.20f, true, 791.56f, 957.80f, 742.74f, 1757.60f, 485.15f, 540.18f)

    @Test fun goldenJungleTallShrunk() =
        golden(stage("jungle", 600f, 1000f), 1f, 171.44f, -459.20f, 1059.84f, 1766.40f, true, 117.62f, 658.42f, 211.23f, 511.19f, 560.01f, 153.62f)

    @Test fun goldenNightForestZoomedWindow() =
        golden(stage("night-forest", 1250f, 1000f, 0.72f), 1f, 234.64f, 161.92f, 857.60f, 686.08f, false, 302.82f, 728.08f, 543.84f, 1021.34f, 493.24f, 395.52f)

    @Test fun goldenCanyon() =
        golden(stage("canyon", 2200f, 1000f), 1f, 0.00f, 133.94f, 1375.00f, 625.00f, false, 726.00f, 695.01f, 565.25f, 1606.00f, 363.23f, 405.50f)

    // ---------------------------------------------------------------- edge cases

    @Test fun noSizeNoStage() {
        val b = bg("forest")
        assertNull(StageFit.fit(b, 0f, 100f))
        assertNull(StageFit.fit(b, 100f, 0f))
        assertNull(StageFit.fit(b, -5f, 100f))
        assertNull(StageFit.fit(b, Float.NaN, 100f))
        val zero = Backdrop("x", BackdropSource.OWN, "png", 0, 100, b.player, b.wild, crisp = true, dark = false)
        assertNull(StageFit.fit(zero, 100f, 100f))
        val zeroH = Backdrop("x", BackdropSource.OWN, "png", 100, 0, b.player, b.wild, crisp = true, dark = false)
        assertNull(StageFit.fit(zeroH, 100f, 100f))
    }

    @Test fun theUnitDoesNotMatter() {
        // the same shape in px, in dp or tiny: crop is in picture pixels, positions scale with the panel
        val a = stage("forest", 1400f, 1000f)
        val b = stage("forest", 14f, 10f)
        assertEquals(a.cropX, b.cropX, 0.001f)
        assertEquals(a.cropW, b.cropW, 0.001f)
        assertEquals(a.playerFeetX / 100f, b.playerFeetX, 0.001f)
        assertEquals(a.wildFrame / 100f, b.wildFrame, 0.001f)
    }

    // ---------------------------------------------------------------- properties over the whole catalog

    private fun allStages(block: (Backdrop, Float, Float, Stage) -> Unit) {
        for (b in BackdropCatalog.ALL) for (a in shapes) for (zoom in listOf(1f, 0.72f)) {
            val h = 1000f
            val w = a * h
            val s = StageFit.fit(b, w, h, zoom)
            assertNotNull("${b.id} $a $zoom", s)
            block(b, w, h, s!!)
        }
    }

    @Test fun cropShapeAndSafeBoxAlwaysInTheCrop() = allStages { b, w, h, s ->
        val tag = "${b.id} ${w / h}"
        assertEquals(tag, w / h, s.cropW / s.cropH, 0.001f)
        val sf = safe(b)
        val e = 0.01f
        assertTrue("$tag left", sf[0] >= s.cropX - e)
        assertTrue("$tag top", sf[1] >= s.cropY - e)
        assertTrue("$tag right", sf[2] <= s.cropX + s.cropW + e)
        assertTrue("$tag bottom", sf[3] <= s.cropY + s.cropH + e)
    }

    @Test fun notShrunkCropStaysInsideThePicture() = allStages { b, w, h, s ->
        val tag = "${b.id} ${w / h}"
        if (!s.shrunk && s.cropW <= b.w + 0.01f && s.cropH <= b.h + 0.01f) {
            assertTrue("$tag x", s.cropX >= -0.01f && s.cropX + s.cropW <= b.w + 0.01f)
            assertTrue("$tag y", s.cropY >= -0.01f && s.cropY + s.cropH <= b.h + 0.01f)
        }
    }

    @Test fun shrunkMeansBiggerThanThePictureAndCentred() = allStages { b, w, h, s ->
        val tag = "${b.id} ${w / h}"
        if (s.shrunk) {
            assertTrue(tag, s.cropW > b.w - 0.01f || s.cropH > b.h - 0.01f)
        }
        // an axis where the crop is bigger than the picture is centred
        if (s.cropW >= b.w) assertEquals("$tag cx", (b.w - s.cropW) / 2f, s.cropX, 0.01f)
        if (s.cropH >= b.h) assertEquals("$tag cy", (b.h - s.cropH) / 2f, s.cropY, 0.01f)
    }

    @Test fun zoomOnlyChangesWhenItIsMoreInThanTheSafeBoxAllows() {
        for (b in BackdropCatalog.ALL) for (a in shapes) {
            val one = StageFit.fit(b, a * 1000f, 1000f, 1f)!!
            val z = StageFit.fit(b, a * 1000f, 1000f, 0.72f)!!
            assertTrue("${b.id} $a", z.cropW <= one.cropW + 0.01f && z.cropH <= one.cropH + 0.01f)
            if (one.shrunk) assertEquals("${b.id} $a", one.cropW, z.cropW, 0.01f)
        }
    }

    @Test fun feetInsideThePanel() = allStages { b, w, h, s ->
        val tag = "${b.id} ${w / h}"
        val e = 0.01f
        assertTrue("$tag px", s.playerFeetX >= -e && s.playerFeetX <= w + e)
        assertTrue("$tag py", s.playerFeetY >= -e && s.playerFeetY <= h + e)
        assertTrue("$tag wx", s.wildFeetX >= -e && s.wildFeetX <= w + e)
        assertTrue("$tag wy", s.wildFeetY >= -e && s.wildFeetY <= h + e)
        // the wild one stands higher and further right, as on the picture
        assertTrue(tag, s.wildFeetY < s.playerFeetY)
        assertTrue(tag, s.wildFeetX > s.playerFeetX)
    }

    @Test fun framesWithinTheScaledClamps() = allStages { b, w, h, s ->
        val tag = "${b.id} ${w / h}"
        val ps = min(1f, b.h / s.cropH)
        val e = 0.0005f
        assertTrue("$tag player lo", s.playerFrame / h >= 0.30f * ps - e)
        assertTrue("$tag player hi", s.playerFrame / h <= 0.80f * ps + e)
        assertTrue("$tag wild lo", s.wildFrame / h >= 0.20f * ps - e)
        assertTrue("$tag wild hi", s.wildFrame / h <= 0.62f * ps + e)
    }

    @Test fun tallPanelsShowingABandDoNotBlowTheSpritesUp() {
        // jungle at 600x1000 is a band: the frame stays the picture's own share (clamp scales with ih / cropH)
        val s = stage("jungle", 600f, 1000f)
        assertTrue(s.shrunk)
        assertTrue(s.playerFrame / 1000f < 0.30f)
        assertEquals(0.44f * 848f / s.cropH * 1000f, s.playerFrame, 0.05f)
    }

    @Test fun bottomFirstSkyIsCroppedBeforeAnythingElse() = allStages { b, w, h, s ->
        val tag = "${b.id} ${w / h}"
        if (s.cropH < b.h) {
            val top = safe(b)[1]
            // the crop sits on the picture's bottom unless the safe box's top needs it higher; never higher than needed
            assertEquals(tag, min(top, b.h - s.cropH), s.cropY, 0.01f)
        }
    }

    @Test fun landscapeCropOfAWidePictureIsCentredAcross() {
        // a panel narrower than the picture's shape crops the sides, centred, unless the safe box needs it moved
        val b = bg("canyon")
        val s = StageFit.fit(b, 1000f, 1000f)!!
        val sf = safe(b)
        val centred = (b.w - s.cropW) / 2f
        val expected = max(0f, min(b.w - s.cropW, max(sf[2] - s.cropW, min(centred, sf[0]))))
        assertEquals(expected, s.cropX, 0.01f)
    }

    @Test fun sameRatioSameCrop() {
        // 600 x 1000 and 6 x 10: identical crop; shapes decide, not sizes
        assertFalse(stage("forest", 600f, 1000f).cropW == 0f)
        val a = stage("mine", 600f, 1000f)
        val b = stage("mine", 6f, 10f)
        assertEquals(a.cropX, b.cropX, 0.001f)
        assertEquals(a.cropY, b.cropY, 0.001f)
        assertEquals(a.cropH, b.cropH, 0.001f)
    }
}
