package com.husarp.ankigo.gfx

import com.husarp.ankigo.data.Sprites
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Dev check against real GIFs (never kept in the project): the folder in env ANKIGO_REAL_GIFS, else
 * ~/.cache/ankigo-shots/gifs (e.g. Showdown ani/, ani-back/, gen5ani/ samples). Skipped when neither has .gif files.
 * Writes the frame count of each file to <tmp>/ankigo-realgif-report.txt.
 */
class RealGifCheck {
    @Test fun decodesEveryRealGif() {
        val dir = System.getenv("ANKIGO_REAL_GIFS")?.takeIf { it.isNotBlank() }
            ?: File(System.getProperty("user.home"), ".cache/ankigo-shots/gifs").path
        val files = File(dir).listFiles { f -> f.extension.equals("gif", true) }.orEmpty().sortedBy { it.name }
        assumeTrue("no .gif files in $dir", files.isNotEmpty())
        val report = StringBuilder()
        for (f in files) {
            val bytes = f.readBytes()
            val g = GifDecoder.decode(bytes)
            assertTrue("${f.name}: no frames", g.frames.isNotEmpty())
            assertTrue("${f.name}: width ${g.width}", g.width in 20..400)
            assertTrue("${f.name}: height ${g.height}", g.height in 20..400)
            assertTrue("${f.name}: all transparent", g.frames.all { fr -> fr.pixels.any { it != 0 } })
            assertTrue("${f.name}: no transparent pixel", g.frames[0].pixels.any { it == 0 })
            // the app's memory budget: fewer frames, same total time
            val kept = GifDecoder.decode(bytes, Sprites.DECODE_MAX_PIXELS)
            assertTrue("${f.name}: over budget", kept.frames.size == 1 ||
                kept.frames.size.toLong() * kept.width * kept.height <= Sprites.DECODE_MAX_PIXELS)
            assertEquals("${f.name}: total time", g.frames.sumOf { it.delayMs }, kept.frames.sumOf { it.delayMs })
            report.append("${f.name}: ${g.frames.size} frames (${kept.frames.size} kept), ${g.width}x${g.height}, delays ${g.frames.map { it.delayMs }.distinct()}\n")
        }
        File(System.getProperty("java.io.tmpdir"), "ankigo-realgif-report.txt").writeText(report.toString())
        println(report)
    }
}
