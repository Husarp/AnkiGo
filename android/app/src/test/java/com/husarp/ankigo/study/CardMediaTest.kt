package com.husarp.ankigo.study
import com.husarp.ankigo.CardMedia
import org.junit.Assert.*
import org.junit.Test
class CardMediaTest {
    @Test fun findsMediaIncludingCssAndSound() {
        assertEquals(listOf("p.png", "a.mp3", "font.woff"), CardMedia.files("<img src='p.png'>[sound:a.mp3]@font-face{src:url('font.woff')}", listOf("p.png")))
        assertEquals(emptyList<String>(), CardMedia.files("<img src='https://example.org/p.png'><img src='data:abc'>", emptyList()))
        assertTrue(CardMedia.isSound("a.MP3")); assertFalse(CardMedia.isSound("p.png"))
    }
    @Test fun selectsOnlyTheActiveClozeWithoutHint() {
        assertEquals("Paris", CardMedia.clozeExpected("{{c1::Paris::city}} {{c2::France}}", 0))
        assertEquals("France", CardMedia.clozeExpected("{{c1::Paris}} {{c2::France}}", 1))
    }
    @Test fun insertionDoesNotMakeFollowingLettersWrong() {
        val html = CardMedia.comparison("abd", "abcd") { it }
        assertTrue(html.contains("color:#999\">c</span>"))
        assertTrue(html.contains("color:#16803c\">d</span>"))
        assertFalse(html.contains("#c53030"))
    }
    @Test fun comparisonAndUnsupportedCards() {
        val html = CardMedia.comparison("abx", "abcd") { it }
        assertTrue(html.contains("#16803c")); assertTrue(html.contains("#c53030")); assertTrue(html.contains("#999"))
        assertTrue(CardMedia.unsupported("[anki:play:q:0]")); assertFalse(CardMedia.unsupported("ordinary card"))
    }
}
