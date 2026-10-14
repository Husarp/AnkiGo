package com.husarp.ankigo.study
import com.husarp.ankigo.CardHtml
import com.husarp.ankigo.CardMedia
import org.junit.Assert.*
import org.junit.Test

class IoScriptTest {
    private val template = "<div style=\"display: none\"><div class=\"cloze\" data-ordinal=\"1\" data-shape=\"rect\" data-left=\".2\" data-top=\".3\" data-width=\".1\" data-height=\".05\" ></div></div>" +
        "<div id=\"image-occlusion-container\"><img src=\"p.png\"><canvas id=\"image-occlusion-canvas\"></canvas></div>" +
        "<script>try { anki.imageOcclusion.setup(); } catch (exc) {}</script>"

    private fun page(content: String, css: String = ".card{color:red}") = CardHtml.build(content, css, 0, "q", null) { null }.html

    @Test fun recognisesImageOcclusionCards() {
        assertTrue(IoScript.needed(template))
        assertTrue(IoScript.needed("<script>anki.imageOcclusion.setup()</script>"))
        assertTrue(IoScript.needed("<div id=\"image-occlusion-container\"></div>"))
        assertFalse(IoScript.needed("ordinary card with {{c1::cloze}} and an image-occlusion mention"))
        assertFalse(IoScript.needed("<img src=\"a.png\">"))
    }

    @Test fun cssComesBeforeNoteCssAndScriptBeforeBody() {
        val html = page(template)
        val io = html.indexOf("#image-occlusion-container{position:relative")
        val note = html.indexOf(".card{color:red}")
        val script = html.indexOf("window.anki.imageOcclusion=")
        assertTrue(io in 0 until note)
        assertTrue(script in 0 until html.indexOf("<body"))
        assertTrue(html.indexOf("<style>") < io)
        assertEquals(1, Regex("window\\.anki\\.imageOcclusion=").findAll(html).count())
        assertTrue(html.contains("max-height:calc(95vh - 40px)"))
        assertTrue(html.contains("--active-shape-color:#ff8e8e"))
        assertTrue(html.contains("<div id=\"image-occlusion-container\">"))
    }

    @Test fun answerSidePageGetsItToo() {
        val html = CardHtml.build(template + "<div><button id=\"toggle\">Toggle Masks</button></div>", "", 1, "a", null) { null }.html
        assertTrue(html.contains("window.anki.imageOcclusion="))
        assertFalse(html.contains("addEventListener('click',function(e){if(!e.target.closest('input,a,button,textarea,select'))AnkiGo.flip()"))
    }

    @Test fun normalCardsGetNothing() {
        val html = page("<b>Front</b> {{c1::x}}")
        assertFalse(html.contains("image-occlusion"))
        assertFalse(html.contains("anki.imageOcclusion"))
        assertFalse(html.contains("#toggle"))
    }

    @Test fun imageOcclusionIsNoLongerFlaggedUnsupported() {
        assertFalse(CardMedia.unsupported(template))
        assertFalse(CardMedia.unsupported("<script>anki.imageOcclusion.setup()</script>"))
        assertTrue(CardHtml.build(template, "", 0, "q", null) { null }.notes.none { it.contains("OCCLUSION", true) })
    }

    @Test fun scriptHasNoStrayTemplateMarkers() {
        assertFalse(IoScript.JS.contains("\${"))
        assertTrue(IoScript.JS.contains("exec(v||'')") && IoScript.JS.contains("\\s*$/"))
        assertFalse(IoScript.JS.contains("</script"))
        assertEquals(IoScript.JS.count { it == '(' }, IoScript.JS.count { it == ')' })
        assertEquals(IoScript.JS.count { it == '{' }, IoScript.JS.count { it == '}' })
    }
}
