package com.husarp.ankigo.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** R18-H (PLAN 238): an evolution item names only the Pokémon you have caught; the rest is "an unknown Pokémon". */
class R18HEvolutionTextTest {
    private fun sp(num: Int, name: String, vararg evos: Evolution) = TestData.species(num, name, evolutions = evos.toList())

    private val data: GameData = TestData.data(
        listOf(
            sp(10, "Applin", Evolution(11, EvoKind.ITEM, item = "tart-apple"), Evolution(12, EvoKind.ITEM, item = "sweet-apple")),
            sp(11, "Flapple"), sp(12, "Appletun"),
            sp(25, "Pikachu", Evolution(26, EvoKind.ITEM, item = "thunder-stone")), sp(26, "Raichu"),
            sp(133, "Eevee", Evolution(135, EvoKind.ITEM, item = "thunder-stone"), Evolution(136, EvoKind.ITEM, item = "fire-stone"),
                Evolution(134, EvoKind.ITEM, item = "water-stone")),
            sp(134, "Vaporeon"), sp(135, "Jolteon"), sp(136, "Flareon"),
            sp(215, "Sneasel", Evolution(461, EvoKind.ITEM, item = "razor-claw", time = "night")), sp(461, "Weavile"),
            // one Pokémon with two targets for the same stone
            sp(44, "Gloom", Evolution(45, EvoKind.ITEM, item = "leaf-stone"), Evolution(182, EvoKind.ITEM, item = "leaf-stone")),
            sp(45, "Vileplume"), sp(182, "Bellossom"),
            *(300..307).map { sp(it, "Fire$it", Evolution(it + 100, EvoKind.ITEM, item = "dawn-stone")) }.toTypedArray(),
            *(400..407).map { sp(it, "Evo$it") }.toTypedArray(),
        ),
    )

    private fun what(item: String, caught: Set<Int>?) = ItemInfo.facts(data, item, caught).first { it.title == "What it does" }.text
    private fun short(item: String, caught: Set<Int>?) = ItemInfo.short(data, item, caught)
    private fun line(item: String, caught: Set<Int>?) = ItemInfo.line(data, item, caught)

    // ---- nothing caught ----

    @Test fun nothingCaughtNamesNobody() {
        val none = emptySet<Int>()
        assertEquals("Evolves ???", short("tart-apple", none))
        assertEquals("Evolves an unknown Pokémon", line("tart-apple", none))
        assertEquals("Evolves **an unknown Pokémon**.", what("tart-apple", none))
        assertFalse(ItemInfo.facts(data, "tart-apple", none).joinToString { it.text }.contains("Applin"))
    }

    // ---- from caught, target not ----

    @Test fun aCaughtFromSpeciesIsNamedAndItsTargetIsUnknown() {
        val c = setOf(10)
        assertEquals("Evolves Applin", short("tart-apple", c))
        assertEquals("Evolves Applin into an unknown Pokémon", line("tart-apple", c))
        assertEquals("Evolves **Applin** into **an unknown Pokémon**.", what("tart-apple", c))
    }

    @Test fun seenIsNotCaught() {
        // only the caught set is looked at: a species that was merely seen is not in it
        assertEquals("Evolves ???", short("tart-apple", emptySet()))
    }

    // ---- both caught ----

    @Test fun whenBothAreCaughtTheTextIsTheFullOne() {
        val c = setOf(10, 11, 12)
        assertEquals("Evolves Applin", short("tart-apple", c))
        assertEquals("Evolves Applin into Flapple", line("tart-apple", c))
        assertEquals("Evolves **Applin** into **Flapple**.", what("tart-apple", c))
        assertEquals("Evolves **Applin** into **Appletun**.", what("sweet-apple", c))
    }

    @Test fun theTargetCaughtButNotTheFromSpecies() {
        val c = setOf(11)
        assertEquals("Evolves ???", short("tart-apple", c))
        assertEquals("Evolves an unknown Pokémon into Flapple", line("tart-apple", c))
        assertEquals("Evolves **an unknown Pokémon** into **Flapple**.", what("tart-apple", c))
    }

    // ---- several ----

    @Test fun severalFromSpeciesNameTheCaughtOnesAndCountTheRest() {
        // Pikachu and Eevee use a Thunder Stone
        assertEquals("Evolves ??? +1", short("thunder-stone", emptySet()))
        assertEquals("Evolves 2 unknown Pokémon", line("thunder-stone", emptySet()))
        assertEquals("Evolves **2 unknown Pokémon**.", what("thunder-stone", emptySet()))
        assertEquals("Evolves Pikachu +1", short("thunder-stone", setOf(25)))
        assertEquals("Evolves Pikachu and an unknown Pokémon", line("thunder-stone", setOf(25)))
        assertEquals("Evolves **Pikachu** into **an unknown Pokémon**, and **another unknown Pokémon**.", what("thunder-stone", setOf(25)))
        assertEquals("Evolves Pikachu and Eevee", line("thunder-stone", setOf(25, 133)))
        assertEquals("Evolves **Pikachu** into **Raichu**, **Eevee** into **Jolteon**.", what("thunder-stone", setOf(25, 26, 133, 135)))
    }

    @Test fun twoTargetsOfOnePokemonAreMergedAndCounted() {
        val c = setOf(44)
        assertEquals("Evolves Gloom", short("leaf-stone", c))
        assertEquals("Evolves Gloom", line("leaf-stone", c))
        assertEquals("Evolves **Gloom** into **2 unknown Pokémon**.", what("leaf-stone", c))
        assertEquals("Evolves **Gloom** into **Vileplume**, **Gloom** into **an unknown Pokémon**.", what("leaf-stone", setOf(44, 45)))
        assertEquals("Evolves **Gloom** into **Vileplume**, **Gloom** into **Bellossom**.", what("leaf-stone", setOf(44, 45, 182)))
    }

    @Test fun aLongListMergesTheUnknownOnesAndKeepsTheCut() {
        val none = emptySet<Int>()
        assertEquals("Evolves **8 unknown Pokémon**.", what("dawn-stone", none))
        assertEquals("Evolves 8 unknown Pokémon", line("dawn-stone", none))
        assertEquals("Evolves ??? +7", short("dawn-stone", none))
        val c = (300..307).toSet() + (400..407).toSet()
        val full = what("dawn-stone", c)
        assertTrue(full, full.contains("**Fire300** into **Evo400**") && full.endsWith(", and 2 more."))
        assertEquals("Evolves Fire300 and 7 more", line("dawn-stone", c))
        // three caught, five not: the caught ones first
        val some = what("dawn-stone", setOf(300, 301, 302))
        assertEquals("Evolves **Fire300** into **an unknown Pokémon**, **Fire301** into **an unknown Pokémon**, " +
            "**Fire302** into **an unknown Pokémon**, and **5 other unknown Pokémon**.", some)
        assertEquals("Evolves Fire300 and 7 more", line("dawn-stone", setOf(300, 301, 302)))
    }

    @Test fun theTimeRuleStaysAndIsNotRepeated() {
        assertEquals("Evolves **Sneasel** into **Weavile**. Works only at **night**.", what("razor-claw", setOf(215, 461)))
        assertEquals("Evolves **Sneasel** into **an unknown Pokémon**. Works only at **night**.", what("razor-claw", setOf(215)))
        assertEquals("Evolves **an unknown Pokémon**. Works only at **night**.", what("razor-claw", emptySet()))
    }

    // ---- no state: every name shows (tests, previews) ----

    @Test fun noStateShowsEveryName() {
        assertEquals("Evolves Applin into Flapple", line("tart-apple", null))
        assertEquals("Evolves **Applin** into **Flapple**.", what("tart-apple", null))
        assertEquals("Evolves Pikachu +1", short("thunder-stone", null))
    }

    @Test fun theItemsOwnNameAndThePlainBlocksStay() {
        val facts = ItemInfo.facts(data, "tart-apple", emptySet())
        assertEquals(listOf("What it does", "How to use", "In a fight"), facts.map { it.title })
        assertEquals("Tart Apple", Items.name("tart-apple"))
        // other kinds do not depend on the Pokédex at all
        assertEquals(ItemInfo.facts(data, "potion", null), ItemInfo.facts(data, "potion", emptySet()))
        assertEquals(ItemInfo.short(data, "poke-ball", null), ItemInfo.short(data, "poke-ball", emptySet()))
    }

    @Test fun theNamesAppearOnceYouCatchThem() {
        val caught = mutableSetOf<Int>()
        assertEquals("Evolves ???", short("tart-apple", caught))
        caught += 10
        assertEquals("Evolves Applin", short("tart-apple", caught))
        assertEquals("Evolves Applin into an unknown Pokémon", line("tart-apple", caught))
        caught += 11
        assertEquals("Evolves Applin into Flapple", line("tart-apple", caught))
    }

    @Test fun everyTextStaysShortAndBalanced() {
        for (c in listOf(emptySet(), setOf(10), setOf(10, 11), setOf(25), (300..307).toSet(), (300..307).toSet() + (400..407).toSet())) {
            for (id in listOf("tart-apple", "thunder-stone", "water-stone", "leaf-stone", "dawn-stone", "razor-claw")) {
                assertTrue("$id $c short", ItemInfo.short(data, id, c).length <= 22)
                assertTrue("$id $c line", ItemInfo.line(data, id, c).length <= 44)
                assertTrue("$id $c marks", (what(id, c).split("**").size - 1) % 2 == 0)
            }
        }
    }
}
