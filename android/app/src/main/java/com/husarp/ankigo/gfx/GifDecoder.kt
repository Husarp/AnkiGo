package com.husarp.ankigo.gfx

// TICKET T03 owns this package (except where noted). Pure Kotlin: unit-tested on the JVM with GIF bytes built in the test.

/** GIF87a/89a decoder producing fully composed ARGB frames (disposal, transparency, interlace, local palettes). */
object GifDecoder {
    /** One composed frame: width*height ARGB pixels (0 = transparent), shown for delayMs (min 20; 0/10 -> 100 like browsers). */
    class Frame(val pixels: IntArray, val delayMs: Int)
    class Gif(val width: Int, val height: Int, val frames: List<Frame>)

    /** Thrown internally when the data ends in the middle of a block. */
    private class Truncated : Exception()

    /**
     * Throws IllegalArgumentException on data that isn't a GIF, or that is cut off before the first frame is complete.
     * A file that is cut off later returns the frames decoded so far.
     */
    fun decode(bytes: ByteArray): Gif {
        require(bytes.size >= 13) { "not a GIF (too short)" }
        require(bytes[0] == 'G'.code.toByte() && bytes[1] == 'I'.code.toByte() && bytes[2] == 'F'.code.toByte() &&
            bytes[3] == '8'.code.toByte() && (bytes[4] == '7'.code.toByte() || bytes[4] == '9'.code.toByte()) &&
            bytes[5] == 'a'.code.toByte()) { "not a GIF (bad signature)" }
        val r = Reader(bytes)
        r.pos = 6
        val width: Int
        val height: Int
        var globalTable: IntArray? = null
        val frames = ArrayList<Frame>()
        try {
            width = r.u16(); height = r.u16()
            val packed = r.u8()
            r.u8(); r.u8() // background colour index + aspect ratio: the background is treated as transparent
            require(width > 0 && height > 0) { "GIF without size" }
            if (packed and 0x80 != 0) globalTable = readTable(r, 1 shl ((packed and 7) + 1))
            return decodeFrames(r, width, height, globalTable, frames)
        } catch (e: Truncated) {
            throw IllegalArgumentException("GIF is cut off before the first frame")
        }
    }

    private fun decodeFrames(r: Reader, width: Int, height: Int, globalTable: IntArray?, frames: ArrayList<Frame>): Gif {
        val canvas = IntArray(width * height)
        var previous: IntArray? = null // canvas before the last frame was drawn (disposal 3)
        var lastDisposal = 0
        var lastRect = intArrayOf(0, 0, 0, 0)
        var delayMs = 100
        var transparent = -1
        var disposal = 0
        try {
            while (true) {
                when (r.u8()) {
                    0x3B -> break
                    0x21 -> {
                        val label = r.u8()
                        if (label == 0xF9) {
                            val size = r.u8()
                            val start = r.pos
                            val flags = r.u8()
                            var cs = r.u16()
                            val tIndex = r.u8()
                            r.pos = start + size
                            disposal = (flags shr 2) and 7
                            transparent = if (flags and 1 != 0) tIndex else -1
                            if (cs * 10 < 20) cs = 10
                            delayMs = cs * 10
                            r.skipSubBlocks()
                        } else {
                            r.skipSubBlocks()
                        }
                    }
                    0x2C -> {
                        val left = r.u16(); val top = r.u16()
                        val w = r.u16(); val h = r.u16()
                        val packed = r.u8()
                        val interlaced = packed and 0x40 != 0
                        val table = if (packed and 0x80 != 0) readTable(r, 1 shl ((packed and 7) + 1)) else globalTable
                        val minCode = r.u8()
                        val data = r.readSubBlocks()
                        // Only now is the frame known to be complete: apply the previous frame's disposal.
                        when (lastDisposal) {
                            2 -> clearRect(canvas, width, height, lastRect)
                            3 -> previous?.let { System.arraycopy(it, 0, canvas, 0, canvas.size) }
                        }
                        previous = if (disposal == 3) canvas.copyOf() else null
                        val indices = lzwDecode(data, minCode, w * h)
                        draw(canvas, width, height, left, top, w, h, interlaced, indices, table, transparent)
                        frames.add(Frame(canvas.copyOf(), delayMs))
                        lastDisposal = disposal
                        lastRect = intArrayOf(left, top, w, h)
                        // The control block only applies to the next image.
                        delayMs = 100; transparent = -1; disposal = 0
                    }
                    else -> break // garbage: stop with what we have
                }
            }
        } catch (e: Truncated) {
            if (frames.isEmpty()) throw e
        }
        if (frames.isEmpty()) throw IllegalArgumentException("GIF has no frames")
        return Gif(width, height, frames)
    }

    private fun clearRect(canvas: IntArray, width: Int, height: Int, rect: IntArray) {
        val x0 = rect[0].coerceIn(0, width); val x1 = (rect[0] + rect[2]).coerceIn(0, width)
        val y0 = rect[1].coerceIn(0, height); val y1 = (rect[1] + rect[3]).coerceIn(0, height)
        for (y in y0 until y1) java.util.Arrays.fill(canvas, y * width + x0, y * width + x1, 0)
    }

    private fun draw(
        canvas: IntArray, width: Int, height: Int, left: Int, top: Int, w: Int, h: Int,
        interlaced: Boolean, indices: ByteArray, table: IntArray?, transparent: Int,
    ) {
        for (row in 0 until h) {
            val y = top + if (interlaced) interlacedRow(row, h) else row
            if (y < 0 || y >= height) continue
            for (col in 0 until w) {
                val x = left + col
                if (x < 0 || x >= width) continue
                val idx = indices[row * w + col].toInt() and 0xFF
                if (idx == transparent) continue
                if (table == null || idx >= table.size) continue // missing colour: leave the pixel as it was
                canvas[y * width + x] = table[idx]
            }
        }
    }

    /** Which picture row the [n]th stored row of an interlaced image belongs to (passes: 0/8, 4/8, 2/4, 1/2). */
    private fun interlacedRow(n: Int, h: Int): Int {
        var left = n
        val pass1 = (h + 7) / 8
        if (left < pass1) return left * 8
        left -= pass1
        val pass2 = (h + 3) / 8
        if (left < pass2) return 4 + left * 8
        left -= pass2
        val pass3 = (h + 1) / 4
        if (left < pass3) return 2 + left * 4
        left -= pass3
        return 1 + left * 2
    }

    private fun readTable(r: Reader, n: Int): IntArray {
        val t = IntArray(n)
        for (i in 0 until n) {
            val red = r.u8(); val green = r.u8(); val blue = r.u8()
            t[i] = (0xFF shl 24) or (red shl 16) or (green shl 8) or blue
        }
        return t
    }

    /** LZW (variable code size up to 12 bits, clear/end codes, deferred clear). Missing data leaves index 0. */
    private fun lzwDecode(data: ByteArray, minCodeSize: Int, pixelCount: Int): ByteArray {
        val out = ByteArray(pixelCount)
        if (minCodeSize < 1 || minCodeSize > 11) return out
        val clear = 1 shl minCodeSize
        val eoi = clear + 1
        val prefix = ShortArray(4096)
        val suffix = ByteArray(4096)
        val stack = ByteArray(4097)
        for (i in 0 until clear) suffix[i] = i.toByte()
        var avail = clear + 2
        var codeSize = minCodeSize + 1
        var mask = (1 shl codeSize) - 1
        var oldCode = -1
        var first = 0
        var datum = 0
        var bits = 0
        var pos = 0
        var outPos = 0
        while (outPos < pixelCount) {
            while (bits < codeSize) {
                if (pos >= data.size) return out
                datum = datum or ((data[pos++].toInt() and 0xFF) shl bits)
                bits += 8
            }
            var code = datum and mask
            datum = datum ushr codeSize
            bits -= codeSize
            if (code == clear) {
                avail = clear + 2; codeSize = minCodeSize + 1; mask = (1 shl codeSize) - 1; oldCode = -1
                continue
            }
            if (code == eoi) break
            if (oldCode == -1) {
                if (code >= clear) break // corrupt
                out[outPos++] = suffix[code]
                oldCode = code; first = code
                continue
            }
            val inCode = code
            var sp = 0
            if (code >= avail) {
                if (code > avail) break // corrupt
                stack[sp++] = first.toByte()
                code = oldCode
            }
            while (code >= clear) {
                stack[sp++] = suffix[code]
                code = prefix[code].toInt()
            }
            first = suffix[code].toInt() and 0xFF
            stack[sp++] = first.toByte()
            if (avail < 4096) {
                prefix[avail] = oldCode.toShort()
                suffix[avail] = first.toByte()
                avail++
                if (avail and mask == 0 && avail < 4096) { codeSize++; mask = (1 shl codeSize) - 1 }
            }
            oldCode = inCode
            while (sp > 0 && outPos < pixelCount) out[outPos++] = stack[--sp]
        }
        return out
    }

    private class Reader(val b: ByteArray) {
        var pos = 0
        fun u8(): Int {
            if (pos >= b.size) throw Truncated()
            return b[pos++].toInt() and 0xFF
        }
        fun u16(): Int { val lo = u8(); return lo or (u8() shl 8) }
        fun skipSubBlocks() {
            while (true) {
                val n = u8()
                if (n == 0) return
                if (pos + n > b.size) throw Truncated()
                pos += n
            }
        }
        fun readSubBlocks(): ByteArray {
            val out = java.io.ByteArrayOutputStream()
            while (true) {
                val n = u8()
                if (n == 0) return out.toByteArray()
                if (pos + n > b.size) throw Truncated()
                out.write(b, pos, n)
                pos += n
            }
        }
    }
}
