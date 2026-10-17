package com.husarp.ankigo.game.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Made-up PokeAPI pokemon-species answers (not real Pokédex text). */
class DexTextParserTest {
    private fun flavor(text: String, lang: String, versionId: Int?) =
        "{\"flavor_text\":\"${text.replace("\n", "\\n").replace("\u000C", "\\f")}\",\"language\":{\"name\":\"$lang\",\"url\":\"x\"}," +
            "\"version\":{\"name\":\"v$versionId\"" + (if (versionId != null) ",\"url\":\"https://example.test/api/v2/version/$versionId/\"" else "") + "}}"

    private fun genus(g: String, lang: String) = "{\"genus\":\"$g\",\"language\":{\"name\":\"$lang\",\"url\":\"x\"}}"

    private val sample = "{\"id\":9001,\"name\":\"madeupmon\"," +
        "\"genera\":[${genus("Ersatz Wesen", "de")},${genus("Test Monster", "en")},${genus("Monstre", "fr")}]," +
        "\"flavor_text_entries\":[" +
        listOf(
            flavor("Old text\nwith a break.", "en", 3),
            flavor("Neueste deutsche Zeile.", "de", 30),
            flavor("Newest english\u000Ctext that\nwraps  twice.", "en", 26),
            flavor("Middle text.", "en", 12),
        ).joinToString(",") + "]}"

    @Test fun picksTheEnglishGenusAndTheNewestEnglishText() {
        val e = DexTextParser.parse(sample)!!
        assertEquals("Test Monster", e.genus)
        assertEquals("Newest english text that wraps twice.", e.text) // version 26 beats 12 and 3; German 30 ignored; \f and \n cleaned
    }

    @Test fun withoutVersionIdsTheLaterEntryWins() {
        val json = "{\"genera\":[],\"flavor_text_entries\":[${flavor("First.", "en", null)},${flavor("Second.", "en", null)}]}"
        val e = DexTextParser.parse(json)!!
        assertNull(e.genus)
        assertEquals("Second.", e.text)
    }

    @Test fun noEnglishAtAllGivesAnEmptyEntry() {
        val json = "{\"genera\":[${genus("Nur Deutsch", "de")}],\"flavor_text_entries\":[${flavor("Nur Deutsch.", "de", 5)}]}"
        val e = DexTextParser.parse(json)!!
        assertTrue(e.empty)
    }

    @Test fun notASpeciesAnswerIsNull() {
        assertNull(DexTextParser.parse("not json"))
        assertNull(DexTextParser.parse("{\"detail\":\"Not found.\"}"))
        assertNull(DexTextParser.parse(""))
    }

    @Test fun cleanFoldsFormFeedsNewlinesAndBlanks() {
        assertEquals("a b c d", DexTextParser.clean(" a\u000Cb\nc \u00A0 d\r\n"))
        assertEquals("co-operate", DexTextParser.clean("co\u00AD-operate"))
    }

    @Test fun theCacheFileRoundTrips() {
        val e = DexEntry("Test Monster", "Some text.")
        assertEquals(e, DexTextParser.decode(DexTextParser.encode(e)))
        val genusOnly = DexEntry("Only Genus", null)
        assertEquals(genusOnly, DexTextParser.decode(DexTextParser.encode(genusOnly)))
        assertNull(DexTextParser.decode("{broken"))
        assertFalse(DexTextParser.decode("{}")!!.let { !it.empty })
    }
}
