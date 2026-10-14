package com.husarp.ankigo.gfx

import java.io.File
import org.junit.Test

class TmpDumpCheck {
    @Test fun dump() {
        val out = File("/tmp/laned-dump").apply { mkdirs() }
        File(System.getProperty("user.home"), ".cache/ankigo-shots/gifs").listFiles()!!.filter { it.extension == "gif" }.forEach { f ->
            val g = GifDecoder.decode(f.readBytes())
            val bb = java.nio.ByteBuffer.allocate(8 + g.frames.size * g.width * g.height * 4)
            bb.putInt(g.width); bb.putInt(g.height)
            g.frames.forEach { fr -> fr.pixels.forEach { bb.putInt(it) } }
            File(out, f.name + ".bin").writeBytes(bb.array())
        }
    }
}
