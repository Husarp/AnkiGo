package com.husarp.ankigo.study

import com.husarp.ankigo.CardHtml
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class CardStylesTest {
    private val css = """@import url('_vocabulary.css');
.card{color:#123456}
.nightMode{color:#abcdef;background:#303030}
.hint{background:#51472b;border-radius:12px;padding:16px}
.category{color:#287cdc}"""
    private val content = """<div class="category">BUSINESS</div><b>commitments</b><hr id=answer>
<div>zobowiązania (podjęte dobrowolnie)</div><div class="hint">Dobrowolne obietnice i deklaracje.<br><i>The bank made commitments to cut its emissions.</i></div>
<img src="example.png">\(x^2\) [sound:example.mp3]"""
    private fun page(dark: Boolean, side: String = "a") =
        CardHtml.build(content, css, 1, side, null, dark, tapShow = false) { null }

    @Test fun importsStartAnIndependentStylesheetAfterDefaults() {
        val html = page(false).html
        val sheets = Regex("<style[^>]*>(.*?)</style>", RegexOption.DOT_MATCHES_ALL)
            .findAll(html).map { it.groupValues[1].trim() }.toList()
        // CSS imports following a normal rule in the same sheet are ignored by WebView.
        assertEquals(css, sheets.single { it.startsWith("@import") })
        assertTrue(sheets.first().contains("body{"))
        assertTrue(html.indexOf("ag-note-style") > html.indexOf(".ag-placeholder svg"))
    }

    @Test fun authoredNightColoursAreNotOverriddenByMoreSpecificAppRules() {
        val html = page(true).html
        assertTrue(html.contains(css))
        assertTrue(html.contains("card card2 chrome nightMode night_mode"))
        assertFalse(html.contains("body.nightMode{"))
        assertTrue(html.contains("background:#202124")) // fallback stays available without note CSS
    }

    @Test fun bothSidesRetainMarkupOfflineMathAndMedia() {
        writeReproductionPages()
        for (dark in listOf(false, true)) for (side in listOf("q", "a")) {
            val page = page(dark, side)
            assertTrue(page.html.contains("<div class=\"hint\">"))
            assertTrue(page.html.contains("<img src=\"example.png\">"))
            assertTrue(page.html.contains("https://ankigo.media/mathjax/tex-mml-chtml.js"))
            assertEquals(listOf("example.mp3"), page.sounds)
            assertEquals(CardResourcePolicy.Route.MEDIA,
                CardResourcePolicy.route(false, "ankigo.media", "/_vocabulary.css"))
        }
    }

    private fun writeReproductionPages() {
        val out = File("build/cardhtml/r26c").apply { mkdirs() }
        for (dark in listOf(false, true)) {
            File(out, "${if (dark) "dark" else "light"}.html").writeText(page(dark).html)
        }
        File(out, "_vocabulary.css").writeText(".hint{border:2px solid #a88623}.category{font-weight:bold}")
    }
}
