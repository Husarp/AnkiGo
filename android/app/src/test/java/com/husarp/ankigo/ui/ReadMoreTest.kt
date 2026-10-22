package com.husarp.ankigo.ui

import com.husarp.ankigo.game.Species
import com.husarp.ankigo.game.TestData
import org.junit.Assert.assertEquals
import org.junit.Test

/** PLAN 183: the Bulbapedia page of a species (pure). */
class ReadMoreTest {
    private val b = "https://bulbapedia.bulbagarden.net/wiki/"
    private val pk = "_(Pok%C3%A9mon)"

    private fun url(name: String) = ReadMore.url(name)

    @Test fun plainSpecies() {
        assertEquals("${b}Squirtle$pk", url("Squirtle"))
    }

    @Test fun nidoransTakeTheirGenderSigns() {
        assertEquals("${b}Nidoran%E2%99%80$pk", url("Nidoran-F"))
        assertEquals("${b}Nidoran%E2%99%82$pk", url("Nidoran-M"))
        assertEquals("${b}Nidoran%E2%99%80$pk", url("Nidoran♀"))
    }

    @Test fun apostropheNamesUseTheStraightOne() {
        assertEquals("${b}Farfetch'd$pk", url("Farfetch’d"))
        assertEquals("${b}Farfetch'd$pk", url("Farfetch'd"))
        assertEquals("${b}Sirfetch'd$pk", url("Sirfetch’d"))
    }

    @Test fun namesWithDotsColonsAndSpaces() {
        assertEquals("${b}Mr._Mime$pk", url("Mr. Mime"))
        assertEquals("${b}Mime_Jr.$pk", url("Mime Jr."))
        assertEquals("${b}Mr._Rime$pk", url("Mr. Rime"))
        assertEquals("${b}Type:_Null$pk", url("Type: Null"))
        assertEquals("${b}Tapu_Koko$pk", url("Tapu Koko"))
        assertEquals("${b}Tapu_Bulu$pk", url("Tapu Bulu"))
    }

    @Test fun accentsAreEncoded() {
        assertEquals("${b}Flab%C3%A9b%C3%A9$pk", url("Flabébé"))
    }

    @Test fun hyphenNamesStayAsTheyAre() {
        for (n in listOf("Ho-Oh", "Porygon-Z", "Jangmo-o", "Hakamo-o", "Kommo-o", "Wo-Chien", "Chien-Pao", "Ting-Lu", "Chi-Yu"))
            assertEquals("$b$n$pk", url(n))
    }

    @Test fun paradoxNames() {
        assertEquals("${b}Great_Tusk$pk", url("Great Tusk"))
        assertEquals("${b}Iron_Treads$pk", url("Iron Treads"))
        assertEquals("${b}Roaring_Moon$pk", url("Roaring Moon"))
        assertEquals("${b}Walking_Wake$pk", url("Walking Wake"))
    }

    @Test fun aRegionalFormOpensItsBaseSpeciesPage() {
        val rattata = TestData.species(19, name = "Rattata")
        val alolan = TestData.species(10091, name = "Alolan Rattata").copy(form = "Alola", baseNum = 19)
        val data = TestData.data(listOf(rattata, alolan))
        assertEquals("${b}Rattata$pk", ReadMore.url(data, alolan))
        assertEquals("${b}Rattata$pk", ReadMore.url(data, rattata))
    }

    @Test fun aNicknameNeverDecidesThePage() {
        val sp: Species = TestData.species(7, name = "Squirtle")
        val data = TestData.data(listOf(sp))
        assertEquals("${b}Squirtle$pk", ReadMore.url(data, sp))
    }
}
