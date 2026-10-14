package com.husarp.ankigo.gfx

import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** GIF bytes are built here (tiny encoder), so no real image is needed. */
class GifDecoderTest {
    private val RED = 0xFFFF0000.toInt()
    private val GREEN = 0xFF00FF00.toInt()
    private val BLUE = 0xFF0000FF.toInt()
    private val WHITE = 0xFFFFFFFF.toInt()

    // ---- tiny GIF writer ----

    private class Img(
        val left: Int, val top: Int, val w: Int, val h: Int, val indices: IntArray,
        val delayCs: Int = 10, val disposal: Int = 0, val transparent: Int = -1,
        val localTable: IntArray? = null, val interlaced: Boolean = false, val properLzw: Boolean = false,
        val minCode: Int = 2,
    )

    private fun ByteArrayOutputStream.u16(v: Int) { write(v and 0xFF); write(v shr 8 and 0xFF) }
    private fun ByteArrayOutputStream.table(t: IntArray, size: Int) {
        for (i in 0 until size) {
            val c = if (i < t.size) t[i] else 0
            write(c shr 16 and 0xFF); write(c shr 8 and 0xFF); write(c and 0xFF)
        }
    }

    private fun tableBits(n: Int): Int { var b = 1; while ((1 shl b) < n) b++; return b }

    private fun gif(w: Int, h: Int, global: IntArray?, vararg imgs: Img, trailer: Boolean = true): ByteArray {
        val o = ByteArrayOutputStream()
        o.write("GIF89a".toByteArray())
        o.u16(w); o.u16(h)
        if (global != null) { o.write(0x80 or (tableBits(global.size) - 1)); } else o.write(0)
        o.write(0); o.write(0)
        if (global != null) o.table(global, 1 shl tableBits(global.size))
        // a harmless application extension that must be skipped
        o.write(0x21); o.write(0xFF); o.write(11); o.write("NETSCAPE2.0".toByteArray()); o.write(3); o.write(1); o.u16(0); o.write(0)
        for (img in imgs) {
            o.write(0x21); o.write(0xF9); o.write(4)
            o.write((img.disposal shl 2) or (if (img.transparent >= 0) 1 else 0))
            o.u16(img.delayCs); o.write(maxOf(img.transparent, 0)); o.write(0)
            o.write(0x2C); o.u16(img.left); o.u16(img.top); o.u16(img.w); o.u16(img.h)
            var packed = if (img.interlaced) 0x40 else 0
            if (img.localTable != null) packed = packed or 0x80 or (tableBits(img.localTable.size) - 1)
            o.write(packed)
            if (img.localTable != null) o.table(img.localTable, 1 shl tableBits(img.localTable.size))
            o.write(img.minCode)
            val data = if (img.properLzw) lzwEncode(img.indices, img.minCode) else fixedEncode(img.indices, img.minCode)
            var p = 0
            while (p < data.size) { val n = minOf(255, data.size - p); o.write(n); o.write(data, p, n); p += n }
            o.write(0)
        }
        if (trailer) o.write(0x3B)
        return o.toByteArray()
    }

    private class BitWriter {
        val out = ByteArrayOutputStream()
        private var cur = 0
        private var n = 0
        fun put(code: Int, size: Int) {
            cur = cur or (code shl n); n += size
            while (n >= 8) { out.write(cur and 0xFF); cur = cur ushr 8; n -= 8 }
        }
        fun finish(): ByteArray { if (n > 0) out.write(cur and 0xFF); return out.toByteArray() }
    }

    /** Valid LZW whose code size never grows: a CLEAR code before every index. */
    private fun fixedEncode(indices: IntArray, minCode: Int): ByteArray {
        val bw = BitWriter()
        val clear = 1 shl minCode
        for (i in indices) { bw.put(clear, minCode + 1); bw.put(i, minCode + 1) }
        bw.put(clear + 1, minCode + 1)
        return bw.finish()
    }

    /** A real dictionary LZW encoder (code size grows, table reset when full). */
    private fun lzwEncode(indices: IntArray, minCode: Int): ByteArray {
        val bw = BitWriter()
        val clear = 1 shl minCode
        val eoi = clear + 1
        var codeSize = minCode + 1
        var next = clear + 2
        var dict = HashMap<Long, Int>()
        bw.put(clear, codeSize)
        var cur = indices[0]
        for (k in 1 until indices.size) {
            val c = indices[k]
            val key = (cur.toLong() shl 12) or c.toLong()
            val hit = dict[key]
            if (hit != null) { cur = hit; continue }
            bw.put(cur, codeSize)
            dict[key] = next++
            if (next > (1 shl codeSize) && codeSize < 12) codeSize++
            if (next == 4096) {
                bw.put(clear, codeSize)
                dict = HashMap(); next = clear + 2; codeSize = minCode + 1
            }
            cur = c
        }
        bw.put(cur, codeSize)
        // the decoder adds one more entry after this code, which can bump its code size for the EOI
        next++
        if (next > (1 shl codeSize) && codeSize < 12) codeSize++
        bw.put(eoi, codeSize)
        return bw.finish()
    }

    private val pal = intArrayOf(0xFF000000.toInt(), RED, GREEN, BLUE) // index 0 black, 1 red, 2 green, 3 blue

    // ---- tests ----

    @Test fun twoFramesWithDelays() {
        val bytes = gif(2, 2, pal,
            Img(0, 0, 2, 2, intArrayOf(1, 1, 1, 1), delayCs = 5),
            Img(0, 0, 2, 2, intArrayOf(2, 2, 3, 3), delayCs = 30))
        val g = GifDecoder.decode(bytes)
        assertEquals(2, g.width); assertEquals(2, g.height)
        assertEquals(2, g.frames.size)
        assertEquals(listOf(RED, RED, RED, RED), g.frames[0].pixels.toList())
        assertEquals(listOf(GREEN, GREEN, BLUE, BLUE), g.frames[1].pixels.toList())
        assertEquals(listOf(50, 300), g.frames.map { it.delayMs })
    }

    @Test fun transparencyKeepsEarlierPixels() {
        val g = GifDecoder.decode(gif(2, 1, pal,
            Img(0, 0, 2, 1, intArrayOf(1, 1)),
            Img(0, 0, 2, 1, intArrayOf(3, 0), transparent = 0)))
        assertEquals(listOf(BLUE, RED), g.frames[1].pixels.toList())
    }

    @Test fun firstFrameTransparentIsZero() {
        val g = GifDecoder.decode(gif(2, 1, pal, Img(0, 0, 2, 1, intArrayOf(0, 2), transparent = 0)))
        assertEquals(listOf(0, GREEN), g.frames[0].pixels.toList())
    }

    @Test fun disposalTwoClearsTheFrameRect() {
        val g = GifDecoder.decode(gif(3, 1, pal,
            Img(0, 0, 3, 1, intArrayOf(1, 1, 1)),
            Img(1, 0, 1, 1, intArrayOf(3), disposal = 2),
            Img(2, 0, 1, 1, intArrayOf(2))))
        assertEquals(listOf(RED, BLUE, RED), g.frames[1].pixels.toList())
        // frame 2 started by clearing frame 1's rectangle (x=1) to transparent
        assertEquals(listOf(RED, 0, GREEN), g.frames[2].pixels.toList())
    }

    @Test fun disposalOneKeepsPixels() {
        val g = GifDecoder.decode(gif(2, 1, pal,
            Img(0, 0, 2, 1, intArrayOf(1, 1)),
            Img(1, 0, 1, 1, intArrayOf(3), disposal = 1),
            Img(0, 0, 1, 1, intArrayOf(2))))
        assertEquals(listOf(GREEN, BLUE), g.frames[2].pixels.toList())
    }

    @Test fun disposalThreeRestoresThePreviousCanvas() {
        val g = GifDecoder.decode(gif(2, 1, pal,
            Img(0, 0, 2, 1, intArrayOf(1, 1)),
            Img(0, 0, 2, 1, intArrayOf(3, 3), disposal = 3),
            Img(0, 0, 1, 1, intArrayOf(2))))
        assertEquals(listOf(BLUE, BLUE), g.frames[1].pixels.toList())
        assertEquals(listOf(GREEN, RED), g.frames[2].pixels.toList()) // red came back from before frame 1
    }

    @Test fun localPaletteOverridesGlobal() {
        val local = intArrayOf(WHITE, 0xFFFFFF00.toInt())
        val g = GifDecoder.decode(gif(2, 1, pal,
            Img(0, 0, 2, 1, intArrayOf(1, 0), localTable = local, minCode = 2)))
        assertEquals(listOf(0xFFFFFF00.toInt(), WHITE), g.frames[0].pixels.toList())
    }

    @Test fun worksWithoutGlobalPalette() {
        val g = GifDecoder.decode(gif(1, 1, null, Img(0, 0, 1, 1, intArrayOf(1), localTable = pal)))
        assertEquals(RED, g.frames[0].pixels[0])
    }

    @Test fun interlacedRowsAreReordered() {
        // 1 pixel wide, 10 rows. Stored order of rows: 0,8, 4, 2,6, 1,3,5,7,9. Value stored = true row number + 1 (index 0 is black).
        val h = 10
        val stored = intArrayOf(0, 8, 4, 2, 6, 1, 3, 5, 7, 9).map { it % 3 + 1 }.toIntArray()
        val colours = intArrayOf(0, RED, GREEN, BLUE)
        val g = GifDecoder.decode(gif(1, h, pal, Img(0, 0, 1, h, stored, interlaced = true)))
        for (row in 0 until h) assertEquals("row $row", colours[row % 3 + 1], g.frames[0].pixels[row])
    }

    @Test fun delayZeroBecomesHundred() {
        val g = GifDecoder.decode(gif(1, 1, pal,
            Img(0, 0, 1, 1, intArrayOf(1), delayCs = 0),
            Img(0, 0, 1, 1, intArrayOf(1), delayCs = 1),
            Img(0, 0, 1, 1, intArrayOf(1), delayCs = 2),
            Img(0, 0, 1, 1, intArrayOf(1), delayCs = 7)))
        assertEquals(listOf(100, 100, 20, 70), g.frames.map { it.delayMs })
    }

    @Test fun truncatedAfterFirstFrameKeepsIt() {
        val two = gif(2, 1, pal, Img(0, 0, 2, 1, intArrayOf(1, 2)), Img(0, 0, 2, 1, intArrayOf(3, 3)), trailer = false)
        val oneLen = gif(2, 1, pal, Img(0, 0, 2, 1, intArrayOf(1, 2)), trailer = false).size
        val cut = two.copyOf(oneLen + 8) // into the second frame's blocks
        val g = GifDecoder.decode(cut)
        assertEquals(1, g.frames.size)
        assertEquals(listOf(RED, GREEN), g.frames[0].pixels.toList())
    }

    @Test fun missingTrailerIsFine() {
        val g = GifDecoder.decode(gif(1, 1, pal, Img(0, 0, 1, 1, intArrayOf(2)), trailer = false))
        assertEquals(1, g.frames.size)
    }

    @Test fun truncatedInsideFirstFrameThrows() {
        val full = gif(2, 1, pal, Img(0, 0, 2, 1, intArrayOf(1, 2)))
        expectIae(full.copyOf(full.size - 6))
        expectIae(full.copyOf(10))
    }

    @Test fun nonGifThrows() {
        expectIae("<html>not a gif at all</html>".toByteArray())
        expectIae(ByteArray(0))
        expectIae(byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10, 0, 0, 0, 13, 0))
    }

    private fun expectIae(bytes: ByteArray) {
        try { GifDecoder.decode(bytes); fail("expected IllegalArgumentException") } catch (_: IllegalArgumentException) { }
    }

    @Test fun realLzwWithGrowingCodeSizeRoundTrips() {
        val w = 40; val h = 40
        val rnd = java.util.Random(7)
        val palette = IntArray(16) { 0xFF000000.toInt() or (it * 16 shl 16) or (255 - it * 16 shl 8) or (it * 7) }
        // blocks of colour with noise: has repeats (long dictionary entries) and fresh patterns
        val idx = IntArray(w * h) { i -> if (rnd.nextInt(4) == 0) rnd.nextInt(16) else (i / 50 + (i % w) / 10) % 16 }
        val g = GifDecoder.decode(gif(w, h, palette, Img(0, 0, w, h, idx, properLzw = true, minCode = 4)))
        assertEquals(1, g.frames.size)
        for (i in idx.indices) assertEquals("pixel $i", palette[idx[i]], g.frames[0].pixels[i])
    }

    @Test fun realLzwThatFillsTheTableAndResets() {
        val w = 120; val h = 120
        val rnd = java.util.Random(99)
        val palette = IntArray(32) { 0xFF000000.toInt() or (it * 8 shl 16) or (it * 3 shl 8) or it }
        val idx = IntArray(w * h) { rnd.nextInt(32) }
        val g = GifDecoder.decode(gif(w, h, palette, Img(0, 0, w, h, idx, properLzw = true, minCode = 5)))
        for (i in idx.indices) assertEquals("pixel $i", palette[idx[i]], g.frames[0].pixels[i])
    }

    @Test fun realLzwHighlyRepetitiveData() {
        val w = 64; val h = 64
        val palette = intArrayOf(0xFF000000.toInt(), WHITE)
        val idx = IntArray(w * h) { if (it % 7 == 0) 1 else 0 }
        val g = GifDecoder.decode(gif(w, h, palette, Img(0, 0, w, h, idx, properLzw = true, minCode = 2)))
        for (i in idx.indices) assertEquals("pixel $i", palette[idx[i]], g.frames[0].pixels[i])
        assertTrue(g.frames[0].pixels.any { it == WHITE })
    }

    @Test fun frameOutsideTheScreenIsClipped() {
        val g = GifDecoder.decode(gif(2, 2, pal, Img(1, 1, 3, 3, IntArray(9) { 1 })))
        assertEquals(listOf(0, 0, 0, RED), g.frames[0].pixels.toList())
    }
    // ---- 3D model GIFs (Showdown ani/): many frames, every one disposal 2 or 3, partial rects, transparency ----

    @Test fun overTheBudgetEveryOtherFrameIsKeptAndPlaysAsLong() {
        val frames = (0 until 5).map { Img(0, 0, 2, 2, IntArray(4) { _ -> 1 + it % 3 }, delayCs = 4) }.toTypedArray()
        val all = GifDecoder.decode(gif(2, 2, pal, *frames))
        assertEquals(5, all.frames.size)
        val cut = GifDecoder.decode(gif(2, 2, pal, *frames), maxPixels = 12) // 5 x 4 px = 20 > 12: step 2
        assertEquals(3, cut.frames.size)
        assertEquals(listOf(RED, BLUE, GREEN), cut.frames.map { it.pixels[0] }) // frames 0, 2, 4
        assertEquals(listOf(80, 80, 40), cut.frames.map { it.delayMs })
        assertEquals(all.frames.sumOf { it.delayMs }, cut.frames.sumOf { it.delayMs })
        // a budget smaller than one frame keeps just the first one
        assertEquals(1, GifDecoder.decode(gif(2, 2, pal, *frames), maxPixels = 1).frames.size)
    }

    @Test fun skippedFramesStillComposeTheCanvas() {
        // frame 1 (skipped) paints the right pixel; frame 2 (kept) only the left one, so the kept picture has both
        val bytes = gif(2, 1, pal,
            Img(0, 0, 2, 1, intArrayOf(1, 1), disposal = 1),
            Img(1, 0, 1, 1, intArrayOf(2), disposal = 1),
            Img(0, 0, 1, 1, intArrayOf(3), disposal = 1))
        val g = GifDecoder.decode(bytes, maxPixels = 4)
        assertEquals(2, g.frames.size)
        assertEquals(listOf(BLUE, GREEN), g.frames[1].pixels.toList())
    }

    @Test fun hugeCanvasIsRefused() {
        val bytes = gif(2000, 2000, pal, Img(0, 0, 1, 1, intArrayOf(1)))
        try { GifDecoder.decode(bytes); fail("expected a refusal") } catch (e: IllegalArgumentException) {
            assertTrue(e.message, e.message!!.contains("too big"))
        }
    }

    @Test fun manyRestorePreviousFramesWithTransparentPartialRects() {
        // like the 3D GIFs: every frame disposal 3 (restore to the blank canvas), a moving 1x1 dot, index 0 transparent
        val n = 40
        val frames = (0 until n).map { k -> Img(k % 4, 0, 1, 1, intArrayOf(1 + k % 3), disposal = 3, transparent = 0) }.toTypedArray()
        val g = GifDecoder.decode(gif(4, 1, pal, *frames))
        assertEquals(n, g.frames.size)
        for (k in 0 until n) {
            val expect = IntArray(4).also { it[k % 4] = pal[1 + k % 3] }
            assertEquals("frame $k", expect.toList(), g.frames[k].pixels.toList())
        }
    }

    @Test fun manyClearToBackgroundFrames() {
        // disposal 2 on a partial rect clears only that rect; the pixels outside stay from frame 0
        val bytes = gif(3, 1, pal,
            Img(0, 0, 3, 1, intArrayOf(1, 1, 1), disposal = 1),
            Img(1, 0, 1, 1, intArrayOf(2), disposal = 2),
            Img(2, 0, 1, 1, intArrayOf(3), disposal = 2),
            Img(0, 0, 1, 1, intArrayOf(0), disposal = 2, transparent = 0))
        val g = GifDecoder.decode(bytes)
        assertEquals(listOf(RED, GREEN, RED), g.frames[1].pixels.toList())
        assertEquals(listOf(RED, 0, BLUE), g.frames[2].pixels.toList())
        assertEquals(listOf(RED, 0, 0), g.frames[3].pixels.toList())
    }
}
