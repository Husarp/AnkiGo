package com.husarp.ankigo.shots

import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.CardHtml
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.CRC32
import java.util.zip.Deflater
import org.junit.Rule
import org.junit.Test

/**
 * Writes Image Occlusion card pages (real CardHtml.build output + the backend's exact cloze divs) and a generated
 * picture to build/cardhtml/t45, for rendering in headless Chrome. Not an assertion test.
 */
class IoPages {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    private val out = File(System.getProperty("user.dir"), "build/cardhtml/t45").apply { mkdirs() }
    private val w = 800
    private val h = 500

    /** A made-up 800x500 diagram: grey grid every 100 px and coloured targets at the exact pixel boxes the masks should cover. */
    private fun diagram(): ByteArray {
        val px = IntArray(w * h) { i -> val x = i % w; val y = i / w; if (x % 100 == 0 || y % 100 == 0) 0xFF9AA5B1.toInt() else 0xFFE9EEF4.toInt() }
        fun box(l: Int, t: Int, wd: Int, ht: Int, c: Int) { for (y in t until t + ht) for (x in l until l + wd) px[y * w + x] = c }
        box(160, 150, 80, 25, 0xFF2E9E5B.toInt())                       // rect  .2/.3/.1/.05
        for (y in 100 until 180) for (x in 440 until 600) {              // ellipse bbox 440,100 160x80
            val dx = (x - 520) / 80.0; val dy = (y - 140) / 40.0
            if (dx * dx + dy * dy <= 1) px[y * w + x] = 0xFFC2185B.toInt()
        }
        for (y in 350 until 450) for (x in 80 until 240) {               // triangle (80,350)(240,350)(160,450)
            val half = 80 - (y - 350) * 0.8
            if (kotlin.math.abs(x - 160) <= half) px[y * w + x] = 0xFF1565C0.toInt()
        }
        box(480, 300, 120, 50, 0xFFEF6C00.toInt())                       // unrotated twin of the rotated rect (.6/.6/.15/.1)
        box(60, 40, 12, 12, 0xFF000000.toInt()); box(728, 448, 12, 12, 0xFF000000.toInt()) // corner marks
        return png(px)
    }

    private fun png(px: IntArray): ByteArray {
        val raw = ByteArrayOutputStream()
        for (y in 0 until h) { raw.write(0); for (x in 0 until w) { val c = px[y * w + x]; raw.write(c shr 16 and 255); raw.write(c shr 8 and 255); raw.write(c and 255) } }
        val z = Deflater().apply { setInput(raw.toByteArray()); finish() }
        val data = ByteArrayOutputStream(); val buf = ByteArray(8192)
        while (!z.finished()) data.write(buf, 0, z.deflate(buf))
        val o = ByteArrayOutputStream()
        o.write(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
        fun chunk(type: String, d: ByteArray) {
            fun i32(v: Int) = byteArrayOf((v ushr 24).toByte(), (v ushr 16).toByte(), (v ushr 8).toByte(), v.toByte())
            o.write(i32(d.size)); val t = type.toByteArray(); o.write(t); o.write(d)
            val crc = CRC32().apply { update(t); update(d) }; o.write(i32(crc.value.toInt()))
        }
        chunk("IHDR", byteArrayOf(0, 0, (w shr 8).toByte(), w.toByte(), 0, 0, (h shr 8).toByte(), h.toByte(), 8, 2, 0, 0, 0))
        chunk("IDAT", data.toByteArray()); chunk("IEND", ByteArray(0))
        return o.toByteArray()
    }

    /** (ordinals, backend attribute text) of each occlusion in the Occlusion field. */
    private val shapes = listOf(
        "1" to """data-shape="rect" data-left=".2" data-top=".3" data-width=".1" data-height=".05" data-occludeInactive="1" """,
        "2" to """data-shape="ellipse" data-left=".55" data-top=".2" data-rx=".1" data-ry=".08" data-occludeInactive="1" """,
        "1" to """data-shape="polygon" data-left=".1" data-top=".7" data-points=".1,.7 .3,.7 .2,.9" """,
        "2" to """data-shape="rect" data-left=".6" data-top=".6" data-width=".15" data-height=".1" data-angle="833" data-occludeInactive="0" """,
        "2" to """data-shape="rect" data-left=".05" data-top=".55" data-width=".1" data-height=".08" data-fill="#00aa00" data-occludeInactive="1" """,
        "0" to """data-shape="text" data-left=".4" data-top=".05" data-text="Label" data-scale="1.5" data-font-size=".05" """,
    )

    /** What AnkiDroid's provider returns for the card: the template with `{{cloze:Occlusion}}` expanded for [ord] on [side]. */
    private fun content(ord: Int, side: String, image: String = "diagram.png", toggle: Boolean = side == "a"): String {
        val divs = shapes.joinToString("") { (o, attrs) ->
            val cls = when {
                o == "0" || (side == "q" && o == "${ord + 1}") -> "cloze"
                o != "${ord + 1}" -> "cloze-inactive"
                else -> "cloze-highlight"
            }
            """<div class="$cls" data-ordinal="$o" $attrs></div>"""
        }
        return """<div>Header text</div><div style="display: none">$divs</div><div id="err"></div>""" +
            """<div id="image-occlusion-container"><img src="$image"><canvas id="image-occlusion-canvas"></canvas></div>""" +
            """<script>try { anki.imageOcclusion.setup(); } catch (exc) { document.getElementById("err").innerHTML = `Error: ${'$'}{exc}`; }</script>""" +
            (if (toggle) """<div><button id="toggle">Toggle Masks</button></div>Extra back text""" else "")
    }

    private val noteCss = ".card { font-family: arial; font-size: 20px; text-align: center; color: black; background-color: white; }\n" +
        "#image-occlusion-canvas { --inactive-shape-color: #ffeba2; --active-shape-color: #ff8e8e; --inactive-shape-border: 1px #212121; --active-shape-border: 1px #212121; --highlight-shape-color: #ff8e8e00; --highlight-shape-border: 1px #ff8e8e; }"
    private val customCss = noteCss + "\n#image-occlusion-canvas { --active-shape-color: #8ec5ff; --inactive-shape-color: #b9f6ca; --active-shape-border: 3px #000000; }"

    private val stub = "<script>window.AnkiGo={flip:function(){document.title='FLIPPED'},actions:function(){},play:function(){},mathjax:function(){},jsError:function(m){document.title='ERR '+m}};</script>"
    private val click = "<script>window.addEventListener('load',function(){setTimeout(function(){var b=document.getElementById('toggle');if(b)b.click()},400)})</script>"

    private fun write(name: String, ord: Int, side: String, dark: Boolean, css: String = noteCss, image: String = "diagram.png", clickToggle: Boolean = false) {
        var html = CardHtml.build(content(ord, side, image), css, ord, side, null, dark) { null }.html
        html = html.replace("<meta charset=\"utf-8\">", "<meta charset=\"utf-8\">$stub")
        if (clickToggle) html = html.replace("</body>", "$click</body>")
        File(out, "$name.html").writeText(html)
    }

    @Test fun pages() {
        File(out, "diagram.png").writeBytes(diagram())
        for (dark in listOf(false, true)) {
            val t = if (dark) "dark" else "light"
            write("q1-$t", 0, "q", dark); write("q2-$t", 1, "q", dark)
            write("a1-$t", 0, "a", dark); write("a2-$t", 1, "a", dark)
            write("a1-toggled-$t", 0, "a", dark, clickToggle = true)
        }
        write("custom-q1-light", 0, "q", false, customCss)
        write("missing-q1-light", 0, "q", false, image = "nope.png")
    }
}
