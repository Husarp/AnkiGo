package com.husarp.ankigo.study

import com.husarp.ankigo.CardHtml
import org.junit.Assert.*
import org.junit.Test

class R30FCardNightTest {
    @Test fun aPlainUnstyledCardUsesNightColorsOnBothSides() {
        for (side in listOf("q", "a")) {
            val html = CardHtml.build("plain question", "", 0, side, null, dark = true) { null }.html
            assertTrue(html.contains("background:#202124!important;color:#e8eaed!important"))
            assertTrue(html.contains("nightMode night_mode"))
            assertTrue(html.contains(">plain question</div>"))
        }
    }
    @Test fun defaultWhiteDeckCssCannotOverrideTheNightFrame() {
        val css = ".card{background:white;color:black} .example{color:red;border:1px solid blue}"
        val html = CardHtml.build("<div class='example'>example</div>", css, 0, "a", null, dark = true) { null }.html
        assertTrue(html.contains(css))
        assertTrue(html.indexOf("background:#202124!important") > html.indexOf(css))
        val light = CardHtml.build("plain", css, 0, "q", null, dark = false) { null }.html
        assertFalse(light.contains("!important"))
        assertTrue(light.contains(css))
    }
}
