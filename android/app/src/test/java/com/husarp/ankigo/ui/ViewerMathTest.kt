package com.husarp.ankigo.ui

import com.husarp.ankigo.ui.ViewerMath.Layout
import com.husarp.ankigo.ui.ViewerMath.Pose
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** TICKET R10-B: zoom / pan maths of the full-screen sprite viewer. */
class ViewerMathTest {
    // a 96 px picture at 8 x in a 1000 x 2000 box: 768 x 768 at zoom 1
    private val l = Layout(768f, 768f, 1000f, 2000f)
    private val sq = Layout(768f, 768f, 1000f, 1000f)
    private val eps = 0.001f

    @Test fun zoomIsClamped() {
        assertEquals(1f, ViewerMath.clampZoom(0.2f), eps)
        assertEquals(5f, ViewerMath.clampZoom(9f), eps)
        assertEquals(3f, ViewerMath.clampZoom(3f), eps)
        assertEquals(1f, ViewerMath.clampZoom(Float.NaN), eps)
        assertEquals(5f, ViewerMath.pinch(Pose.FIT, 100f, 0f, 0f, l).zoom, eps)
        assertEquals(1f, ViewerMath.pinch(Pose.FIT, 0.01f, 0f, 0f, l).zoom, eps)
    }

    @Test fun fitScalePortraitAndLandscapeBoxes() {
        // pixel art: the largest whole number that fits
        assertEquals(10f, ViewerMath.fitScale(96, 96, 1000, 1500), eps)   // portrait: width limits (10.4)
        assertEquals(4f, ViewerMath.fitScale(96, 96, 1500, 400), eps)     // landscape: height limits (4.16)
        assertEquals(1f, ViewerMath.fitScale(96, 96, 96, 96), eps)
        // a whole number that would waste more than 15 % of the room gives way to the exact fit
        assertEquals(380f / 96f, ViewerMath.fitScale(96, 96, 380, 1000), eps)  // 3.96 x, not 3
        assertEquals(1.9f, ViewerMath.fitScale(100, 100, 190, 1000), eps)      // 1.9 x, not 1
        // fractional only when even 1 does not fit
        assertEquals(0.5f, ViewerMath.fitScale(96, 96, 48, 48), eps)
        // PLAN 213: a bigger (3D) picture follows the same rule: 3.33 -> 3 (3 >= 2.83)
        assertEquals(3f, ViewerMath.fitScale(300, 300, 1000, 1500), eps)
        // a non-square picture
        assertEquals(5f, ViewerMath.fitScale(40, 80, 1000, 420), eps)
        assertEquals(0f, ViewerMath.fitScale(0, 96, 1000, 1000), eps)
        assertEquals(0f, ViewerMath.fitScale(96, 96, 0, 1000), eps)
    }

    @Test fun panIsZeroWhileThePictureFitsTheBox() {
        val p = ViewerMath.clamp(Pose(1f, 300f, -200f), l)
        assertEquals(0f, p.tx, eps); assertEquals(0f, p.ty, eps)
        // 1.2 x: 921.6 wide still fits 1000 wide, but 921.6 high fits 2000 too
        val q = ViewerMath.clamp(Pose(1.2f, 99f, 99f), l)
        assertEquals(0f, q.tx, eps); assertEquals(0f, q.ty, eps)
    }

    @Test fun panBoundsKeepTheEdgeAtOrBeyondTheScreenEdge() {
        for (z in listOf(1.5f, 2f, 2.5f, 3f, 4f, 5f)) {
            for (t in listOf(-9999f, -500f, 0f, 500f, 9999f)) {
                val p = ViewerMath.clamp(Pose(z, t, t), l)
                val r = ViewerMath.spriteRect(p, l)
                if (l.baseW * z > l.boxW) { assertTrue("left edge z=$z t=$t", r[0] <= eps); assertTrue("right edge z=$z t=$t", r[2] >= l.boxW - eps) }
                else assertEquals(l.boxW / 2f, (r[0] + r[2]) / 2f, eps)
                if (l.baseH * z > l.boxH) { assertTrue("top edge z=$z t=$t", r[1] <= eps); assertTrue("bottom edge z=$z t=$t", r[3] >= l.boxH - eps) }
                else assertEquals(l.boxH / 2f, (r[1] + r[3]) / 2f, eps)
            }
        }
        // 4 x: 3072 wide in 1000 -> the centre may move by 1036
        assertEquals(1036f, ViewerMath.clamp(Pose(4f, 5000f, 0f), l).tx, eps)
        assertEquals(-1036f, ViewerMath.clamp(Pose(4f, -5000f, 0f), l).tx, eps)
    }

    @Test fun pictureNeverLostOffScreenInALandscapeBoxToo() {
        val land = Layout(384f, 384f, 2000f, 600f)
        val p = ViewerMath.clamp(Pose(5f, 7000f, 7000f), land)
        val r = ViewerMath.spriteRect(p, land)
        assertEquals(1000f, (r[0] + r[2]) / 2f, eps)
        assertTrue(r[1] <= eps && r[3] >= 600f - eps)
        assertEquals(0f, p.tx, eps) // 1920 wide in 2000: centred
    }

    @Test fun focalZoomKeepsThePointUnderTheFingerStill() {
        // a point 200 px right and 100 px below the box centre, zooming in from fit to 2.5 x
        val fx = 200f; val fy = 100f
        val p = ViewerMath.zoomAt(Pose.FIT, 2.5f, fx, fy, sq)
        assertEquals(2.5f, p.zoom, eps)
        // the picture point that was under the finger (at fit, offset (200, 100) from the picture centre) is still there:
        // screen offset = pan + picture offset * zoom
        assertEquals(fx, p.tx + fx * 2.5f, eps)
        assertEquals(fy, p.ty + fy * 2.5f, eps)
    }

    @Test fun focalZoomIsClampedAtTheEdges() {
        // a tap in the far corner of a picture that is only a bit bigger than the box must not pull it off screen
        val small = Layout(900f, 900f, 1000f, 1000f)
        val p = ViewerMath.zoomAt(Pose.FIT, 2.5f, 480f, 480f, small)
        val r = ViewerMath.spriteRect(p, small)
        assertTrue(r[0] <= eps && r[2] >= 1000f - eps && r[1] <= eps && r[3] >= 1000f - eps)
    }

    @Test fun doubleTapZoomsInAroundThePointThenOut() {
        val inn = ViewerMath.doubleTap(Pose.FIT, -150f, 80f, sq)
        assertEquals(ViewerMath.DOUBLE_TAP_ZOOM, inn.zoom, eps)
        assertEquals(-150f, inn.tx + -150f * 2.5f, eps)
        assertEquals(80f, inn.ty + 80f * 2.5f, eps)
        assertEquals(Pose.FIT, ViewerMath.doubleTap(inn, 10f, 10f, sq))
        // pinched to 3 x: a double-tap also goes back to fit
        assertEquals(Pose.FIT, ViewerMath.doubleTap(Pose(3f, 20f, 0f), 0f, 0f, l))
    }

    @Test fun dragMovesOnlyWhenZoomedAndStaysInBounds() {
        assertEquals(Pose.FIT, ViewerMath.drag(Pose.FIT, 80f, 80f, l))
        val z = Pose(3f, 0f, 0f)
        val a = ViewerMath.drag(z, 60f, -40f, l)
        assertEquals(60f, a.tx, eps); assertEquals(-40f, a.ty, eps)
        val far = ViewerMath.drag(z, 99999f, -99999f, l)
        assertEquals((768f * 3f - 1000f) / 2f, far.tx, eps)
        assertEquals(-152f, far.ty, eps) // 2304 high in 2000 high: the centre may move 152
    }

    @Test fun tapOutsideClosesOnlyWhenNotZoomed() {
        // the picture sits at x 116..884, y 616..1384 in the box
        assertFalse(ViewerMath.closesOnTap(Pose.FIT, 500f, 1000f, l))   // on the picture
        assertTrue(ViewerMath.closesOnTap(Pose.FIT, 50f, 1000f, l))     // beside it
        assertTrue(ViewerMath.closesOnTap(Pose.FIT, 500f, 100f, l))     // above it
        assertFalse(ViewerMath.closesOnTap(Pose(2f, 0f, 0f), 20f, 20f, l)) // zoomed: never
        assertFalse(ViewerMath.hits(Pose.FIT, 100f, 1000f, l))
        assertTrue(ViewerMath.hits(Pose.FIT, 120f, 1000f, l))
    }

    @Test fun spriteRectFollowsZoomAndPan() {
        val r = ViewerMath.spriteRect(Pose(2f, 100f, -50f), l)
        assertEquals(500f + 100f - 768f, r[0], eps)
        assertEquals(500f + 100f + 768f, r[2], eps)
        assertEquals(1000f - 50f - 768f, r[1], eps)
        assertEquals(1000f - 50f + 768f, r[3], eps)
    }

    @Test fun reclampAfterASizeChange() {
        // rotated: the box is now wide and short; a pose that was fine before is pulled back inside
        val before = Pose(3f, 800f, 0f)
        val wide = Layout(768f, 768f, 2000f, 600f)
        val p = ViewerMath.clamp(before, wide)
        assertEquals(152f, p.tx, eps) // 2304 wide in 2000: limit 152, so 800 clamps to 152
        assertEquals(0f, p.ty, eps)
    }
}
