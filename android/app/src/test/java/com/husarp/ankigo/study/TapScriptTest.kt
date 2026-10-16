package com.husarp.ankigo.study
import com.husarp.ankigo.CardHtml
import org.junit.Assert.*
import org.junit.Test

/** Settings -> Study "Tap the card to show the answer" / "Tap again to hide the answer": what the card page listens for. */
class TapScriptTest {
    private fun page(side: String, tapShow: Boolean, tapHide: Boolean) =
        CardHtml.build("<b>x</b>", "", 0, side, null, tapShow = tapShow, tapHide = tapHide) { null }.html

    @Test fun questionSideFollowsTapShow() {
        assertTrue(page("q", true, false).contains("AnkiGo.flip()"))
        assertTrue(page("q", true, true).contains("AnkiGo.flip()"))
        assertFalse(page("q", false, false).contains("AnkiGo.flip()"))
        assertFalse(page("q", false, true).contains("AnkiGo.flip()"))
    }

    @Test fun questionHintOnlyWhileTappingWorks() {
        assertTrue(page("q", true, false).contains("class=\"ag-hint\""))
        assertFalse(page("q", false, false).contains("class=\"ag-hint\""))
    }

    @Test fun answerSideOnlyUnflipsWhenBothAreOn() {
        assertTrue(page("a", true, true).contains("AnkiGo.unflip()"))
        assertFalse(page("a", true, false).contains("AnkiGo.unflip()"))
        assertFalse(page("a", false, true).contains("AnkiGo.unflip()"))   // tapHide means nothing without tapShow
        assertFalse(page("a", false, false).contains("AnkiGo.unflip()"))
    }

    @Test fun answerSideNeverFlipsAndQuestionSideNeverUnflips() {
        for (show in listOf(true, false)) for (hide in listOf(true, false)) {
            assertFalse(page("a", show, hide).contains("AnkiGo.flip()"))
            assertFalse(page("q", show, hide).contains("AnkiGo.unflip()"))
        }
    }

    @Test fun defaultsAreTodaysBehaviour() {
        val html = CardHtml.build("<b>x</b>", "", 0, "q", null) { null }.html
        assertTrue(html.contains("AnkiGo.flip()"))
        assertFalse(CardHtml.build("<b>x</b>", "", 0, "a", null) { null }.html.contains("AnkiGo.unflip()"))
    }

    @Test fun taps_onControlsAreIgnored() {
        for (s in listOf(CardHtml.tapScript("q", true, false), CardHtml.tapScript("a", true, true))) {
            assertTrue(s.contains("closest('input,a,button,textarea,select')"))
            assertEquals(s.count { it == '(' }, s.count { it == ')' })
            assertEquals(s.count { it == '{' }, s.count { it == '}' })
        }
        assertEquals("", CardHtml.tapScript("a", true, false))
        assertEquals("", CardHtml.tapScript("q", false, true))
    }
}
