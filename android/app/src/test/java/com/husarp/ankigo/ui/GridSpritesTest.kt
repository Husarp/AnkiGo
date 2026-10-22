package com.husarp.ankigo.ui

import com.husarp.ankigo.gfx.GifDecoder
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PLAN 156 (R15-D): the pure parts of the animated Pokédex / Box grids: the frame for the elapsed time, the adaptive tick rule of the
 * shared clock, the memory budget (a full screen of animations fits it, a big picture is shrunk to its tile) and the rule for which cells animate. The views are in
 * R15DGridAnimShots.
 */
class GridSpritesTest {
    // ---- the frame for the elapsed time ----

    @Test fun framesEndAtTheirCumulativeTimeWithTheTwentyMillisecondFloor() {
        assertEquals(listOf(100L, 150L, 250L), GridFrames.ends(intArrayOf(100, 50, 100), 3).toList())
        assertEquals(listOf(20L, 40L), GridFrames.ends(intArrayOf(0, 5), 2).toList())      // never under 20 ms
        assertEquals(listOf(100L, 200L), GridFrames.ends(intArrayOf(100), 2).toList())     // a missing delay counts 100 ms
    }

    @Test fun theFrameFollowsTheRealElapsedTimeAndLoops() {
        val ends = longArrayOf(100, 150, 250)
        assertEquals(0, GridFrames.frameAt(ends, 0))
        assertEquals(0, GridFrames.frameAt(ends, 99))
        assertEquals(1, GridFrames.frameAt(ends, 100))
        assertEquals(2, GridFrames.frameAt(ends, 150))
        assertEquals(2, GridFrames.frameAt(ends, 249))
        assertEquals(0, GridFrames.frameAt(ends, 250))      // the loop starts again
        assertEquals(1, GridFrames.frameAt(ends, 250 * 40 + 120))
        assertEquals(0, GridFrames.frameAt(ends, -5))       // a tick stamped before the start
    }

    @Test fun aSkippedTickDoesNotSlowTheAnimation() {
        // a clock that ticks only every 4th vsync (67 ms) still shows the frame of the real time, not "the next frame"
        val ends = GridFrames.ends(IntArray(10) { 40 }, 10)
        assertEquals(4, GridFrames.frameAt(ends, 167))
        assertEquals(6, GridFrames.frameAt(ends, 250))
    }

    // ---- the adaptive tick rule ----

    private val vsync = 16_666_667L
    private fun TickRate.feed(times: Int, ms: Double) { repeat(times) { onTick((ms * 1_000_000).toLong(), vsync) } }

    @Test fun aSmoothPhoneStaysAtEveryVsync() {
        val r = TickRate()
        r.feed(300, 16.7)
        assertEquals(1, r.step)
    }

    @Test fun droppedFramesRaiseTheIntervalOneStepAtATimeUpToFour() {
        val r = TickRate()
        r.feed(29, 45.0)
        assertEquals("the window is not full yet", 1, r.step)
        r.feed(1, 45.0)
        assertEquals(2, r.step)
        r.feed(30, 80.0)   // over 1.5 x the 33 ms budget of 2 vsyncs
        assertEquals(3, r.step)
        r.feed(30, 120.0)  // over 1.5 x 50 ms
        assertEquals(4, r.step)
        r.feed(300, 400.0)
        assertEquals("never past 4", 4, r.step)
    }

    @Test fun oneSlowTickDoesNotCount() {
        val r = TickRate()
        repeat(10) { r.feed(29, 16.7); r.feed(1, 100.0) }   // an average of ~19.5 ms against a 25 ms line
        assertEquals(1, r.step)
    }

    @Test fun aSmoothSecondLowersTheIntervalAgain() {
        val r = TickRate()
        r.feed(30, 45.0)
        assertEquals(2, r.step)
        r.feed(29, 34.0)
        assertEquals("under a second since the raise", 2, r.step)
        r.feed(1, 34.0)
        assertEquals("a smooth second", 1, r.step)
    }

    @Test fun aPhoneThatCannotDoSixtyDoesNotFlipEverySecond() {
        val r = TickRate()
        val ups = ArrayList<Long>() // simulated seconds of each change
        var clock = 0.0
        var last = r.step
        repeat(3000) {
            // the phone manages 30 frames a second: ticks every vsync arrive 45 ms apart, ticks every 2nd vsync 33 ms apart
            val ms = if (r.step == 1) 45.0 else 34.0 * r.step / 2
            r.feed(1, ms)
            clock += ms
            if (r.step != last) { ups += clock.toLong(); last = r.step }
        }
        val changes = ups.size
        // without the back-off it would change every ~1.5 s (about 40 times in 60 s); with it the waits double up to 16 s
        assertTrue("changes: $changes", changes in 4..24)
        val gaps = ups.zipWithNext { a, b -> b - a }
        assertTrue("the waits grow: $gaps", gaps.maxOrNull()!! >= 8000)
    }

    @Test fun aRelapseLongAfterTheLowerStartsFromASecondAgain() {
        val r = TickRate()
        r.feed(30, 45.0); assertEquals(2, r.step)
        r.feed(30, 34.0); assertEquals(1, r.step)
        r.feed(30, 45.0); assertEquals("flipped back soon after: waits 2 s now", 2, r.step)
        r.feed(30, 34.0); assertEquals("1 s is not enough any more", 2, r.step)
        r.feed(30, 34.0); assertEquals(1, r.step)
        r.feed(600, 16.7)   // ten smooth seconds
        r.feed(30, 45.0); assertEquals(2, r.step)
        r.feed(30, 34.0); assertEquals("not recent: a second is enough again", 1, r.step)
    }

    // ---- memory ----

    private val MB = 1024L * 1024

    @Test fun theBudgetIsThirtyTwoMegabytesOrAnEighthOfASmallHeap() {
        assertEquals(32 * MB, GridSprites.budgetBytes(512 * MB))
        assertEquals(32 * MB, GridSprites.budgetBytes(256 * MB))
        assertEquals(16 * MB, GridSprites.budgetBytes(128 * MB))
        assertEquals(8 * MB, GridSprites.budgetBytes(64 * MB))
    }

    /** A 4-column phone 1080 x 2340 px at 2.75 density, laid out like the Pokedex grid (6 dp gaps, 12 dp sides, 17 dp top, 16 dp strip, 2 dp inset). */
    private val phone = GridShape.layout(1080, 2340, 4, 1.25f, gapPx = 16, sidePx = 33, topPx = 47, stripPx = 44, insetPx = 6)

    @Test fun aGridCountsTheCellsItShowsAtOnceAndTheRoomOfATile() {
        assertEquals("4 columns x (7 rows that fit + 1)", 32, phone.cells)
        assertEquals("the sprite view of a tile: the cell less the 2 dp insets", 229, phone.roomW)
        assertEquals("the cell (301 px) less the top row and the name strip", 210, phone.roomH)
        // a tablet with six columns shows more cells, a phone with three fewer columns each
        val tablet = GridShape.layout(1920, 1200, 6, 1.25f, gapPx = 16, sidePx = 33, topPx = 47, stripPx = 44, insetPx = 6)
        assertEquals("6 columns x (3 rows that fit + 1)", 6 * 4, tablet.cells)
        // sizes it cannot work out give the default shape, never a division by zero
        assertSame(GridShape.DEFAULT, GridShape.layout(0, 2340, 4, 1.25f, 16, 33, 47, 44, 6))
        assertSame(GridShape.DEFAULT, GridShape.layout(1080, 0, 4, 1.25f, 16, 33, 47, 44, 6))
        assertEquals(GridSprites.DEFAULT_CELLS, GridShape.DEFAULT.cells)
    }

    @Test fun theShareOfOneCellIsTheBudgetOverTheCellsOfTheGrid() {
        val budget = GridSprites.budgetBytes(512 * MB)
        assertEquals(233_016L, GridSprites.maxPixels(budget, 36)) // 32 MB / 4 bytes / 36 cells
        assertEquals(262_144L, GridSprites.maxPixels(budget, phone.cells))
        assertTrue(GridSprites.maxPixels(budget, 36) * 4 * 36 <= budget)
        assertEquals("never zero", 1L, GridSprites.maxPixels(0, 36))
    }

    // ---- a tiny GIF writer (every pixel is its own LZW code, so the file is valid and trivial) ----

    private fun gif(w: Int, h: Int, frames: Int, delayCs: Int = 5): ByteArray {
        val o = ByteArrayOutputStream()
        fun u16(v: Int) { o.write(v and 0xFF); o.write(v shr 8 and 0xFF) }
        o.write("GIF89a".toByteArray()); u16(w); u16(h); o.write(0x81); o.write(0); o.write(0)
        for (c in intArrayOf(0x000000, 0xFF0000, 0x00FF00, 0x0000FF)) { o.write(c shr 16 and 0xFF); o.write(c shr 8 and 0xFF); o.write(c and 0xFF) }
        for (f in 0 until frames) {
            o.write(0x21); o.write(0xF9); o.write(4); o.write(0); u16(delayCs); o.write(0); o.write(0)
            o.write(0x2C); u16(0); u16(0); u16(w); u16(h); o.write(0)
            o.write(2) // minimum code size
            val bits = ByteArrayOutputStream()
            var cur = 0; var n = 0
            fun put(code: Int) { cur = cur or (code shl n); n += 3; while (n >= 8) { bits.write(cur and 0xFF); cur = cur ushr 8; n -= 8 } }
            for (i in 0 until w * h) { put(4); put((i + f) % 4) } // CLEAR before every index
            put(5)
            if (n > 0) bits.write(cur and 0xFF)
            val data = bits.toByteArray()
            var p = 0
            while (p < data.size) { val k = minOf(255, data.size - p); o.write(k); o.write(data, p, k); p += k }
            o.write(0)
        }
        o.write(0x3B)
        return o.toByteArray()
    }

    /** What a grid of [shape] keeps of a [w] x [h] GIF of [frames] frames at [share]: (frames kept, frame width, frame height). */
    private fun kept(w: Int, h: Int, frames: Int, shape: GridShape, share: Long): Triple<Int, Int, Int> {
        val plan = GridSprites.plan(w, h, shape, share)
        val g = GifDecoder.decode(gif(w, h, frames), plan.decoderCap)
        return Triple(g.frames.size, plan.outW, plan.outH)
    }

    private fun bytesOf(k: Triple<Int, Int, Int>) = GridSprites.frameBytes(k.first, k.second, k.third)

    @Test fun aNinetySixPixelAnimationOfSixtyFramesKeepsTwentyOnAFourColumnPhone() {
        val share = GridSprites.maxPixels(GridSprites.budgetBytes(512 * MB), phone.cells)
        val k = kept(96, 96, 60, phone, share)
        assertTrue("frames kept: ${k.first}", k.first >= 20)
        assertEquals("a small picture is not scaled", 96 to 96, k.second to k.third)
        assertTrue(bytesOf(k) <= share * 4)
        // the old 16 MB / 48 cells budget kept 8 of the 60 (the choppy case)
        val old = kept(96, 96, 60, GridShape(48, Int.MAX_VALUE, Int.MAX_VALUE), GridSprites.maxPixels(16 * MB, 48))
        assertTrue("old budget: ${old.first}", old.first < 20)
    }

    @Test fun theTypicalAnimationsAreKeptWhole() {
        val share = GridSprites.maxPixels(GridSprites.budgetBytes(512 * MB), phone.cells)
        assertEquals(24, kept(96, 96, 24, phone, share).first)
        assertEquals("a longer 96 x 96 one keeps every 2nd frame (same speed)", 15, kept(96, 96, 30, phone, share).first)
        assertEquals(40, kept(48, 48, 40, phone, share).first)
        assertEquals(60, kept(40, 40, 60, phone, share).first)
    }

    @Test fun aBigAnimationIsShrunkToTheRoomOfItsTileAndStillAnimates() {
        val share = GridSprites.maxPixels(GridSprites.budgetBytes(512 * MB), phone.cells)
        val k = kept(300, 300, 12, phone, share)
        assertTrue("a 300 x 300 GIF animates: ${k.first} frames", k.first >= 2)
        assertTrue("shrunk into the ${phone.roomW} x ${phone.roomH} room: ${k.second} x ${k.third}", k.second <= phone.roomW && k.third <= phone.roomH)
        assertEquals("a square stays square", k.second, k.third)
        assertTrue("and fills the room's short side", k.second >= phone.roomH - 1)
        assertTrue(bytesOf(k) <= share * 4)
    }

    @Test fun anyCachedAnimationAnimatesWhateverItsSizeOrTheHeap() {
        for (heap in listOf(512 * MB, 128 * MB, 64 * MB, 32 * MB, 16 * MB)) {
            val share = GridSprites.maxPixels(GridSprites.budgetBytes(heap), phone.cells)
            for ((w, h) in listOf(96 to 96, 200 to 120, 300 to 300, 480 to 320)) {
                val k = kept(w, h, 6, phone, share)
                assertTrue("${w}x$h on a ${heap / MB} MB heap: ${k.first} frames", k.first >= 2)
                assertTrue("${w}x$h on a ${heap / MB} MB heap: ${bytesOf(k)} bytes of ${share * 4}", bytesOf(k) <= share * 4)
            }
        }
        // without a grid around it (no room limit) a 230 x 230 picture is kept whole and still animates
        val k = kept(230, 230, 4, GridShape.DEFAULT, GridSprites.maxPixels(GridSprites.budgetBytes(512 * MB), GridShape.DEFAULT.cells))
        assertEquals(230 to 230, k.second to k.third)
        assertTrue(k.first >= 2)
    }

    @Test fun aFullScreenOfAnimationsStaysInsideTheBudget() {
        for (heap in listOf(512 * MB, 64 * MB)) {
            val budget = GridSprites.budgetBytes(heap)
            val share = GridSprites.maxPixels(budget, phone.cells)
            for ((w, h) in listOf(96 to 96, 80 to 60, 120 to 120, 150 to 150, 300 to 300, 40 to 40)) {
                for (frames in listOf(2, 8, 40, 120)) {
                    val k = kept(w, h, frames, phone, share)
                    assertTrue("${w}x$h, $frames frames, ${heap / MB} MB heap: ${bytesOf(k) * phone.cells} of $budget", bytesOf(k) * phone.cells <= budget)
                }
            }
        }
    }

    @Test fun aLongAnimationKeepsEveryFewFramesButPlaysJustAsLong() {
        val share = GridSprites.maxPixels(GridSprites.budgetBytes(512 * MB), phone.cells)
        val plan = GridSprites.plan(96, 96, phone, share)
        val g = GifDecoder.decode(gif(96, 96, 60), plan.decoderCap)
        assertTrue("fewer frames than the file: ${g.frames.size}", g.frames.size in 2..59)
        assertEquals("same total time", 60 * 50, g.frames.sumOf { it.delayMs })
    }

    @Test fun theDecoderNeverHoldsMoreThanTheTransientBoundOrFewerThanTwoFullFrames() {
        val share = GridSprites.maxPixels(GridSprites.budgetBytes(512 * MB), phone.cells)
        for ((w, h) in listOf(96 to 96, 300 to 300, 480 to 320, 700 to 700)) {
            val plan = GridSprites.plan(w, h, phone, share)
            val full = w.toLong() * h
            assertTrue("${w}x$h: ${plan.decoderCap}", plan.decoderCap >= 2 * full)
            assertTrue("${w}x$h: ${plan.decoderCap}", plan.decoderCap <= maxOf(GridSprites.TRANSIENT_MAX_PIXELS, 2 * full))
        }
    }

    // ---- shrinking ----

    @Test fun shrinkingPicksThePixelAtTheCentreOfEachTargetPixelWithoutBlending() {
        // 4 x 4 -> 2 x 2: each target pixel is a 2 x 2 block, sampled at its centre (the block's lower right of the middle)
        val src = IntArray(16) { it + 1 }
        assertEquals(listOf(6, 8, 14, 16), GridSprites.shrink(src, 4, 4, 2, 2).toList())
        assertEquals("same size: unchanged", src.toList(), GridSprites.shrink(src, 4, 4, 4, 4).toList())
        // every output pixel is one of the source pixels (no new colours), transparent stays transparent
        val argb = IntArray(300 * 300) { if (it % 7 == 0) 0 else (0xFF shl 24) or (it % 5 * 40) }
        val out = GridSprites.shrink(argb, 300, 300, 211, 167)
        assertEquals(211 * 167, out.size)
        val set = argb.toSet()
        assertTrue(out.all { it in set })
        assertTrue(out.any { it == 0 })
    }

    @Test fun theGifHeaderGivesTheFrameSize() {
        assertEquals(96 to 80, GridSprites.gifSize(gif(96, 80, 2).copyOf(10)))
        assertEquals(null, GridSprites.gifSize(ByteArray(10)))
        assertEquals(null, GridSprites.gifSize(byteArrayOf(71, 73, 70)))
    }

    // ---- which cells animate ----

    @Test fun onlyACaughtCellWithACachedAnimationMovesAndOnlyWithTheSettingOn() {
        assertTrue(GridSprites.animates(true, true, false, true, "25.gif"))
        assertTrue(GridSprites.animates(true, true, false, true, "25s.GIF"))
        assertFalse("setting off", GridSprites.animates(false, true, false, true, "25.gif"))
        assertFalse("seen only / unseen / silhouette", GridSprites.animates(true, false, false, true, "25.gif"))
        assertFalse("Remove battle animations", GridSprites.animates(true, true, true, true, "25.gif"))
        assertFalse("Android's animators are off", GridSprites.animates(true, true, false, false, "25.gif"))
    }

    @Test fun nothingMovesWithoutADownloadedAnimation() {
        assertFalse("a still picture is cached", GridSprites.animates(true, true, false, true, "700.png"))
        assertFalse("nothing is cached (and nothing is fetched)", GridSprites.animates(true, true, false, true, null))
    }
}
