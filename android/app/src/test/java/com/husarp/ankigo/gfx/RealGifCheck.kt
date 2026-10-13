package com.husarp.ankigo.gfx

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Dev check against real GIFs (never kept in the project): run with env ANKIGO_REAL_GIFS=<dir containing .gif files>.
 * Skipped otherwise. Prints the frame count of each file.
 */
class RealGifCheck {
    @Test fun decodesEveryRealGif() {
        val dir = System.getenv("ANKIGO_REAL_GIFS")
        assumeTrue("ANKIGO_REAL_GIFS not set", !dir.isNullOrBlank())
        val files = File(dir!!).listFiles { f -> f.extension.equals("gif", true) }.orEmpty().sortedBy { it.name }
        assumeTrue("no .gif files in $dir", files.isNotEmpty())
        val report = StringBuilder()
        for (f in files) {
            val g = GifDecoder.decode(f.readBytes())
            report.append("${f.name}: ${g.frames.size} frames, ${g.width}x${g.height}, delays ${g.frames.map { it.delayMs }.distinct()}\n")
            assertTrue("${f.name}: no frames", g.frames.isNotEmpty())
            assertTrue("${f.name}: width ${g.width}", g.width in 20..200)
            assertTrue("${f.name}: height ${g.height}", g.height in 20..200)
            assertTrue("${f.name}: all transparent", g.frames.all { fr -> fr.pixels.any { it != 0 } })
        }
        File(System.getProperty("java.io.tmpdir"), "ankigo-realgif-report.txt").writeText(report.toString())
        println(report)
    }
}
