package com.husarp.ankigo.game.data

import com.husarp.ankigo.game.EvoKind
import com.husarp.ankigo.game.GameData
import com.husarp.ankigo.game.LevelMove
import com.husarp.ankigo.game.Stats
import com.husarp.ankigo.game.Type
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Regional forms in Parsers (T37): hand-written, made-up Showdown/PokeAPI snippets with REAL form ids (the ids are the
 * contract: 10091 = the Alola form of species 19 ...). No real Pokémon data.
 */
class RegionalFormsParserTest {

    // ---- Fixture ---------------------------------------------------------------------------------------------

    private fun entry(key: String, num: Int, name: String, extra: String = "", types: String = "\"Normal\"") =
        "\"$key\":{\"num\":$num,\"name\":\"$name\",\"types\":[$types],\"baseStats\":{\"hp\":50,\"atk\":60,\"def\":70,\"spa\":80,\"spd\":90,\"spe\":100}" +
            (if (extra.isEmpty()) "" else ",$extra") + "}"

    private fun form(key: String, num: Int, name: String, baseSpecies: String, forme: String, extra: String = "", types: String = "\"Dark\",\"Normal\"") =
        entry(key, num, name, "\"baseSpecies\":\"$baseSpecies\",\"forme\":\"$forme\"" + (if (extra.isEmpty()) "" else ",$extra"), types)

    private fun pokedex(): String {
        val special = listOf(
            // Alola line: the base evolves into the base only; the form evolves into the form (night, level 20)
            entry("squeaker", 19, "Squeaker", "\"evos\":[\"Squeakmaster\",\"Squeakmaster-Alola\"]"),
            entry("squeakmaster", 20, "Squeakmaster", "\"prevo\":\"Squeaker\",\"evoLevel\":20"),
            form("squeakeralola", 19, "Squeaker-Alola", "Squeaker", "Alola", "\"evos\":[\"Squeakmaster-Alola\"]"),
            form("squeakmasteralola", 20, "Squeakmaster-Alola", "Squeakmaster", "Alola",
                "\"prevo\":\"Squeaker-Alola\",\"evoLevel\":20,\"evoCondition\":\"at night\""),
            // a form whose prevo is a BASE species (the base does not evolve into the form)
            entry("zapper", 25, "Zapper", "\"evos\":[\"Zapmaster\",\"Zapmaster-Alola\"]"),
            entry("zapmaster", 26, "Zapmaster", "\"prevo\":\"Zapper\",\"evoType\":\"useItem\",\"evoItem\":\"Thunder Stone\""),
            form("zapmasteralola", 26, "Zapmaster-Alola", "Zapmaster", "Alola",
                "\"prevo\":\"Zapper\",\"evoType\":\"useItem\",\"evoItem\":\"Thunder Stone\"", "\"Electric\",\"Psychic\""),
            // Galar line + a base species whose prevo is the form; friendship needs the BASE species' happiness
            entry("pawcat", 52, "Pawcat", "\"evos\":[\"Pouncer\"]"),
            entry("pouncer", 53, "Pouncer", "\"prevo\":\"Pawcat\",\"evoType\":\"levelFriendship\""),
            entry("ironpaw", 863, "Ironpaw", "\"prevo\":\"Pawcat-Galar\",\"evoLevel\":28", "\"Steel\""),
            form("pawcatgalar", 52, "Pawcat-Galar", "Pawcat", "Galar", "\"evos\":[\"Ironpaw\"]", "\"Normal\""),
            form("pawcatalola", 52, "Pawcat-Alola", "Pawcat", "Alola", "\"evos\":[\"Pouncer-Alola\"]", "\"Dark\""),
            form("pounceralola", 53, "Pouncer-Alola", "Pouncer", "Alola", "\"prevo\":\"Pawcat-Alola\",\"evoType\":\"levelFriendship\"", "\"Dark\""),
            // Paldea: three forms of one species (same name, told apart by type), male only, own gender kept
            entry("hornbull", 128, "Hornbull", "\"gender\":\"M\""),
            form("hornbullpaldeacombat", 128, "Hornbull-Paldea-Combat", "Hornbull", "Paldea-Combat", "\"gender\":\"M\"", "\"Fighting\""),
            form("hornbullpaldeablaze", 128, "Hornbull-Paldea-Blaze", "Hornbull", "Paldea-Blaze", "\"gender\":\"M\"", "\"Fighting\",\"Fire\""),
            form("hornbullpaldeaaqua", 128, "Hornbull-Paldea-Aqua", "Hornbull", "Paldea-Aqua", "\"gender\":\"M\"", "\"Fighting\",\"Water\""),
            // Hisui: an old species name with punctuation; genderless base (the form entry says nothing, so it inherits)
            entry("frostbird", 144, "Frostbird", "\"gender\":\"N\"", "\"Ice\",\"Flying\""),
            form("frostbirdgalar", 144, "Frostbird-Galar", "Frostbird", "Galar", "", "\"Psychic\",\"Flying\""),
            entry("mrmimic", 122, "Mr. Mimic", "\"genderRatio\":{\"M\":0.25,\"F\":0.75}"),
            form("mrmimicgalar", 122, "Mr. Mimic-Galar", "Mr. Mimic", "Galar", "", "\"Ice\",\"Psychic\""),
            entry("fowld", 83, "Fowl’d"),
            form("fowldgalar", 83, "Fowl’d-Galar", "Fowl’d", "Galar", "", "\"Fighting\""),
            // "-standard": the identifier suffix is dropped
            entry("blazeape", 555, "Blazeape", "\"evos\":[]", "\"Fire\""),
            form("blazeapegalar", 555, "Blazeape-Galar", "Blazeape", "Galar", "", "\"Ice\""),
            // Hisui starter line + a base-species look-alike without a forme (10163): must be skipped
            entry("flamelo", 157, "Flamelo", "", "\"Fire\""),
            form("flamelohisui", 157, "Flamelo-Hisui", "Flamelo", "Hisui", "\"evoLevel\":36", "\"Fire\",\"Ghost\""),
            entry("ponyx", 77, "Ponyx"),
            entry("ponyxgalar", 77, "Ponyx-Galar", "\"baseSpecies\":\"Ponyx\""), // no "forme" -> not a form
        )
        val used = setOf(19, 20, 25, 26, 52, 53, 863, 128, 144, 122, 83, 555, 157, 77)
        val fillers = (1..1025).filter { it !in used }.map { entry("filler$it", it, "Filler $it", types = "\"Water\"") }
        return "{" + (special + fillers).joinToString(",") + "}"
    }

    private fun move(id: String, name: String) =
        "\"$id\":{\"name\":\"$name\",\"type\":\"Normal\",\"category\":\"Physical\",\"basePower\":40,\"accuracy\":100,\"priority\":0,\"target\":\"normal\"}"

    private fun moves(): String {
        val fillers = (1..800).map { move("fillermove$it", "Filler Move $it") }
        return "{" + (listOf(move("tackle", "Tackle"), move("bite", "Bite"), move("slash", "Slash")) + fillers).joinToString(",") + "}"
    }

    private fun ls(vararg moveCodes: Pair<String, String>) =
        "{\"learnset\":{" + moveCodes.joinToString(",") { (m, c) -> "\"$m\":[" + c.split(",").joinToString(",") { "\"$it\"" } + "]" } + "}}"

    private fun learnsets() = "{" + listOf(
        "\"squeaker\":" + ls("tackle" to "9L1", "slash" to "9L5"),
        "\"squeakeralola\":" + ls("tackle" to "9L1", "bite" to "9L9", "slash" to "9M"), // its own learnset
        // no learnset for "pawcatgalar": it falls back to the base species'
        "\"pawcat\":" + ls("tackle" to "9L1", "bite" to "9L12"),
    ).joinToString(",") + "}"

    private val speciesCsv = """
id,identifier,generation_id,growth_rate_id,is_baby,is_legendary,is_mythical
19,squeaker,1,4,0,0,0
52,pawcat,1,2,1,0,0
128,hornbull,1,3,0,0,0
144,frostbird,1,1,0,1,0
157,flamelo,2,5,0,0,0
"""

    private val pokemonCsv = """
id,identifier,species_id,height,weight,base_experience,order,is_default
19,squeaker,19,3,35,51,1,1
10091,squeaker-alola,19,3,38,52,1,0
10092,squeakmaster-alola,20,7,255,146,2,0
10100,zapmaster-alola,26,7,210,243,3,0
10107,pawcat-alola,52,4,42,58,4,0
10108,pouncer-alola,53,11,330,154,5,0
10161,pawcat-galar,52,4,75,59,6,0
10162,ponyx-galar,77,10,240,82,7,0
10163,missing-galar,78,10,240,82,8,0
10169,frostbird-galar,144,17,509,290,9,0
10166,fowld-galar,83,8,420,132,10,0
10168,mr-mimic-galar,122,14,568,161,11,0
10177,blazeape-galar-standard,555,17,1200,168,12,0
10233,flamelo-hisui,157,16,698,240,13,0
10250,hornbull-paldea-combat-breed,128,14,1150,172,14,0
10251,hornbull-paldea-blaze-breed,128,14,1150,172,15,0
10252,hornbull-paldea-aqua-breed,128,14,1150,172,16,0
"""

    private val statsCsv = """
pokemon_id,stat_id,base_stat,effort
19,1,30,1
10091,3,35,2
10091,6,72,1
"""

    // evolved_species_id is a SPECIES id: 53's happiness is what the Alola line's friendship evolution uses
    private val evolutionCsv = """
id,evolved_species_id,evolution_trigger_id,minimum_level,minimum_happiness
1,53,1,,220
"""

    private fun experienceCsv(): String {
        val sb = StringBuilder("growth_rate_id,level,experience\n")
        for (r in 1..6) for (l in 1..100) sb.append("$r,$l,${(l - 1) * (l - 1) * r}\n")
        return sb.toString()
    }

    private fun itemsCsv(): String {
        val sb = StringBuilder("id,identifier,category_id,cost\n")
        for (i in 1..1100) sb.append("$i,item-$i,1,${i % 7 * 100}\n")
        return sb.toString()
    }

    private fun sources(pokemon: String = pokemonCsv, pokedex: String = pokedex()) =
        Parsers.Sources(pokedex, moves(), learnsets(), speciesCsv, pokemon, statsCsv, evolutionCsv, experienceCsv(), itemsCsv())

    private val data: GameData by lazy { Parsers.build(sources(), now = 99L) }

    // ---- The forms ---------------------------------------------------------------------------------------------

    @Test fun aFormIsBuiltFromItsOwnEntryRowAndTheBaseSpeciesFlags() {
        val f = data.sp(10091)
        assertEquals(10091, f.num); assertEquals(19, f.baseNum)
        assertEquals("squeakeralola", f.id); assertEquals("Alola", f.form)
        assertEquals("Alolan Squeaker", f.name)
        assertEquals(7, f.gen) // the region's generation, not the base species' (1)
        assertEquals(listOf(Type.DARK, Type.NORMAL), f.types)
        assertEquals(Stats(50, 60, 70, 80, 90, 100), f.base)
        assertEquals(52, f.baseExp) // pokemon.csv row of the FORM id (the base has 51)
        assertEquals(Stats(0, 0, 2, 0, 0, 1), f.evYield) // pokemon_stats of the form id, not the base's
        assertEquals(4, f.growthRate) // from the base species' row
        assertEquals(listOf(LevelMove(1, "tackle"), LevelMove(9, "bite")), f.levelMoves)
        assertEquals(setOf("slash"), f.tmMoves)
        assertNull(f.evoLevel)
    }

    @Test fun baseSpeciesStayAsTheyWere() {
        val b = data.sp(19)
        assertNull(b.form); assertEquals(19, b.baseNum); assertEquals("squeaker", b.spriteId); assertEquals("Squeaker", b.name)
        assertEquals(51, b.baseExp)
        assertEquals((1..1025).toList(), data.species.keys.filter { it <= 1025 }.toList())
    }

    @Test fun legendaryBabyFlagsAndGrowthComeFromTheBaseSpeciesRow() {
        assertTrue(data.sp(10169).isLegendary) // Galar form of a legendary: the Lv 50 gate applies
        assertTrue(data.sp(10107).isBaby)
        assertEquals(1, data.sp(10169).growthRate)
        assertFalse(data.sp(10091).isLegendary)
    }

    @Test fun threeBreedsAreNamedAlikeAndToldApartByTypeAndSprite() {
        val names = listOf(10250, 10251, 10252).map { data.sp(it).name }
        assertEquals(listOf("Paldean Hornbull", "Paldean Hornbull", "Paldean Hornbull"), names)
        assertEquals(listOf(Type.FIGHTING), data.sp(10250).types)
        assertEquals(listOf(Type.FIGHTING, Type.FIRE), data.sp(10251).types)
        assertEquals(listOf(Type.FIGHTING, Type.WATER), data.sp(10252).types)
        assertEquals(listOf("hornbull-paldeacombat", "hornbull-paldeablaze", "hornbull-paldeaaqua"), listOf(10250, 10251, 10252).map { data.sp(it).spriteId })
        assertEquals(9, data.sp(10250).gen)
    }

    @Test fun spriteIdIsTheBaseSpeciesAndFormeIdsJoinedByADash() {
        assertEquals("squeaker-alola", data.sp(10091).spriteId)
        assertEquals("mrmimic-galar", data.sp(10168).spriteId) // "Mr. Mimic": dot and space dropped
        assertEquals("fowld-galar", data.sp(10166).spriteId) // typographic apostrophe dropped
        assertEquals("flamelo-hisui", data.sp(10233).spriteId)
        assertEquals("Hisuian Flamelo", data.sp(10233).name)
        assertEquals("Galarian Mr. Mimic", data.sp(10168).name)
    }

    @Test fun identifierSuffixStandardIsDroppedForTheShowdownKey() {
        val f = data.sp(10177)
        assertEquals("blazeapegalar", f.id); assertEquals(555, f.baseNum); assertEquals("blazeape-galar", f.spriteId)
    }

    @Test fun aFormWithoutAnEntryRowOrFormeIsSkippedAndTheBuildStillWorks() {
        assertNull(data.species[10162]) // pokemon.csv row, Showdown entry has no "forme"
        assertNull(data.species[10163]) // pokemon.csv row, no Showdown entry at all
        // a form id without any pokemon.csv row never appears
        val noRows = Parsers.build(sources(pokemon = "id,identifier,species_id,height,weight,base_experience,order,is_default\n19,squeaker,19,3,35,51,1,1\n"))
        assertEquals(1025, noRows.species.size)
        assertTrue(noRows.species.keys.all { it <= 1025 })
    }

    @Test fun genderIsKeptOrInheritedFromTheBaseSpecies() {
        assertEquals(1.0, data.sp(10250).maleRatio!!, 0.0) // own gender
        assertNull(data.sp(10169).maleRatio) // the entry says nothing: genderless like the base
        assertEquals(0.25, data.sp(10168).maleRatio!!, 0.0) // genderRatio inherited
        assertEquals(0.5, data.sp(10091).maleRatio!!, 0.0)
    }

    @Test fun aFormWithoutALearnsetUsesTheBaseSpeciesLearnset() {
        assertEquals(data.sp(52).levelMoves, data.sp(10161).levelMoves)
        assertEquals(listOf(LevelMove(1, "tackle"), LevelMove(12, "bite")), data.sp(10161).levelMoves)
    }

    // ---- Evolutions --------------------------------------------------------------------------------------------

    @Test fun aFormEvolvesAlongItsOwnLine() {
        val f = data.sp(10091)
        val e = f.evolutions.single()
        assertEquals(10092, e.to)
        assertEquals(EvoKind.LEVEL, e.kind); assertEquals(20, e.level); assertEquals("night", e.time)
        assertEquals(10091, data.sp(10092).prevo)
        assertEquals(20, data.sp(10092).evoLevel)
    }

    @Test fun aBaseSpeciesNeverEvolvesIntoAForm() {
        assertEquals(listOf(20), data.sp(19).evolutions.map { it.to }) // "Squeakmaster-Alola" is dropped
        assertEquals(listOf(26), data.sp(25).evolutions.map { it.to })
        assertEquals(25, data.sp(26).prevo) // the form's prevo is the base species, the base's evolutions stay the base's
        assertEquals(25, data.sp(10100).prevo)
        assertEquals(EvoKind.ITEM, data.sp(25).evolutions.single().kind)
    }

    @Test fun aBaseSpeciesWhoseOldFormIsAFormGetsThatPrevo() {
        assertEquals(10161, data.sp(863).prevo)
        assertEquals(863, data.sp(10161).evolutions.single().to)
        assertEquals(28, data.sp(10161).evolutions.single().level)
        assertEquals(28, data.sp(863).evoLevel)
        assertEquals(null, data.sp(52).prevo)
    }

    @Test fun friendshipOfAFormEvolutionUsesTheBaseSpeciesHappiness() {
        val e = data.sp(10107).evolutions.single()
        assertEquals(10108, e.to); assertEquals(EvoKind.FRIENDSHIP, e.kind); assertEquals(220, e.minFriendship)
    }

    // ---- Cache -------------------------------------------------------------------------------------------------

    @Test fun cacheRoundTripKeepsTheFormFields() {
        val back = Parsers.decodeCache(Parsers.encodeCache(data))
        assertEquals(data.species.keys, back.species.keys)
        for (num in listOf(10091, 10250, 10168, 10161)) {
            val a = data.sp(num); val b = back.sp(num)
            assertEquals(a.form, b.form); assertEquals(a.baseNum, b.baseNum); assertEquals(a.spriteId, b.spriteId)
            assertEquals(a, b)
        }
        assertEquals("Alola", back.sp(10091).form)
        assertNull(back.sp(19).form); assertEquals("squeaker", back.sp(19).spriteId)
        assertNotNull(back.species[10092])
    }

    @Test fun anOlderCacheFormatIsRefusedSoDataStoreRebuilds() {
        val old = Parsers.encodeCache(data).replaceFirst("\"format\":2", "\"format\":1")
        try { Parsers.decodeCache(old); fail("format 1 must be refused") } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("format"))
        }
    }
}
