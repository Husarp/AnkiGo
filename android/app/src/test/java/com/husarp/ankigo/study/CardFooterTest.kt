package com.husarp.ankigo.study
import com.husarp.ankigo.CardHtml
import org.junit.Assert.*
import org.junit.Test

class CardFooterTest {
    private val css = ".footer{position:fixed;bottom:0;left:0;right:0;background:#eee} .head{position:sticky;top:0}"
    private val long = "<div class=\"head\">Deck</div>" + "<p>line</p>".repeat(80) + "<div class=\"footer\">Source: book</div>"

    private fun page(side: String, content: String = long) = CardHtml.build(content, css, 0, side, null) { null }.html

    @Test fun fixedAndStickyNoteCssIsKeptAndUnfixScriptRunsAfterTheCard() {
        for (side in listOf("q", "a")) {
            val html = page(side)
            assertTrue(html.contains(css))                                  // the note's own CSS is untouched
            val qa = html.indexOf("<div id=\"qa\">")
            val footer = html.indexOf("Source: book")
            val script = html.indexOf("<script>${CardHtml.UNFIX_JS}</script>")
            assertTrue(qa in 0 until footer)
            assertTrue("script after the card body", footer in 0 until script)
            assertEquals(1, Regex("getComputedStyle\\(el\\)\\.position").findAll(html).count())
        }
    }

    @Test fun scriptPutsFixedAndStickyBackInFlowAndPushesBottomOnesDown() {
        val js = CardHtml.UNFIX_JS
        assertTrue(js.contains("p!=='fixed'&&p!=='sticky'"))
        assertTrue(js.contains("setProperty('position','static','important')"))
        assertTrue(js.contains("margin-top','auto'"))      // a footer sits at the bottom of a short card
        assertTrue(js.contains("body.insertBefore(el,anchor)")) // and after the text of a long one
        assertEquals(js.count { it == '{' }, js.count { it == '}' })
        assertEquals(js.count { it == '(' }, js.count { it == ')' })
    }

    @Test fun otherNotesStillGetTheSamePageStructure() {
        val html = page("q", "<b>plain</b>")
        assertTrue(html.contains("<div id=\"qa\"><b>plain</b></div>"))
        assertTrue(html.contains("class=\"ag-hint\""))
        assertFalse(html.substringAfter("<body").contains("position:fixed")) // the script only inspects, nothing is hard-coded
    }
}
