package com.husarp.ankigo.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** R3-X2 item 3, R14 batch 2 (PLAN 168 / 181): the texts of every item, built from the item data. */
class ItemInfoTest {
    private fun sp(num: Int, name: String, vararg evos: Evolution) = TestData.species(num, name, evolutions = evos.toList())

    /** A small world with every kind of evolution the texts have to show. */
    private val data: GameData = TestData.data(
        listOf(
            sp(10, "Applin", Evolution(11, EvoKind.ITEM, item = "tart-apple"), Evolution(12, EvoKind.ITEM, item = "sweet-apple")),
            sp(11, "Flapple"), sp(12, "Appletun"),
            sp(25, "Pikachu", Evolution(26, EvoKind.ITEM, item = "thunder-stone")), sp(26, "Raichu"),
            sp(133, "Eevee", Evolution(135, EvoKind.ITEM, item = "thunder-stone"), Evolution(136, EvoKind.ITEM, item = "fire-stone")),
            sp(135, "Jolteon"), sp(136, "Flareon"),
            sp(215, "Sneasel", Evolution(461, EvoKind.ITEM, item = "razor-claw", time = "night")), sp(461, "Weavile"),
            sp(361, "Snorunt", Evolution(478, EvoKind.ITEM, item = "ice-stone", gender = 'F')), sp(478, "Froslass"),
            sp(64, "Kadabra", Evolution(65, EvoKind.TRADE)), sp(65, "Alakazam"),
            sp(95, "Onix", Evolution(208, EvoKind.TRADE, item = "metal-coat")), sp(208, "Steelix"),
            sp(138, "Omanyte"),
            // eight Pokémon that all use a Fire Stone: the list must be cut with "and N more"
            *(300..307).map { sp(it, "Fire$it", Evolution(it + 100, EvoKind.ITEM, item = "dawn-stone")) }.toTypedArray(),
            *(400..407).map { sp(it, "Evo$it") }.toTypedArray(),
        ),
    )

    private fun all(item: String, d: GameData? = data) = ItemInfo.facts(d, item).joinToString(" ") { it.text }
    private fun fact(item: String, title: String, d: GameData? = data) = ItemInfo.facts(d, item).first { it.title == title }.text
    private fun marksBalanced(s: String) = (s.split("**").size - 1) % 2 == 0

    // ---- every item ----

    @Test fun everyCatalogueItemHasAShortALineAndFacts() {
        for (id in Items.ALL.keys) for (d in listOf(data, null)) {
            val short = ItemInfo.short(d, id)
            val line = ItemInfo.line(d, id)
            assertTrue("$id short '$short'", short.isNotBlank() && short != "No description." && short.length <= 22 && "**" !in short)
            assertTrue("$id line '$line'", line.isNotBlank() && line != "No description." && line.length <= 44 && "**" !in line)
            val facts = ItemInfo.facts(d, id)
            assertTrue("$id facts", facts.size in 2..3 && facts.all { it.text.isNotBlank() && it.text != "No description." })
            assertTrue("$id titles", facts.all { it.title in setOf("What it does", "How to use", "In a fight") })
            assertTrue("$id marks", facts.all { marksBalanced(it.text) })
            assertEquals("$id effect", ItemInfo.line(null, id), ItemInfo.effect(id))
        }
    }

    @Test fun theCatalogueNotesAreTheStaticFallbackAndNeverSayCash() {
        for (d in Items.ALL.values) assertFalse("${d.id} '${d.note}'", d.note.contains("cash", true) || d.note.startsWith("Held:"))
    }

    @Test fun anUnknownItemHasOneGenericBlock() {
        assertEquals(listOf(ItemFact("What it does", "No description.")), ItemInfo.facts(data, "not-an-item"))
        assertEquals("No description.", ItemInfo.short(data, "not-an-item"))
        assertEquals("No description.", ItemInfo.line(data, "not-an-item"))
        assertEquals("No description.", ItemInfo.effect("not-an-item"))
    }

    // ---- balls ----

    @Test fun ballsSayTheChanceAndThatAMissRunsAway() {
        assertEquals("20 % catch", ItemInfo.short(data, "poke-ball"))
        assertEquals("50 % catch", ItemInfo.short(data, "great-ball"))
        assertEquals("80 % catch", ItemInfo.short(data, "ultra-ball"))
        assertEquals("20 % to catch · a miss: it runs away", ItemInfo.line(data, "poke-ball"))
        for ((id, pct) in listOf("poke-ball" to "20 %", "great-ball" to "50 %", "ultra-ball" to "80 %")) {
            val what = fact(id, "What it does")
            assertTrue(id, what.contains("**$pct**") && what.contains("**runs away**") && what.contains("used up"))
            assertTrue(id, fact(id, "How to use").contains("**Throwing is not a turn**"))
            assertEquals(2, ItemInfo.facts(data, id).size)   // balls have no "In a fight" block: the rule is in How to use
        }
        assertTrue(fact("ultra-ball", "What it does").contains("**one day a week**"))
        assertFalse(fact("great-ball", "What it does").contains("one day a week"))
        assertTrue(fact("poke-ball", "How to use").contains("5 Poké Balls for 1 Great Ball"))
        assertTrue(fact("great-ball", "How to use").contains("3 Great Balls for 1 Ultra Ball"))
    }

    @Test fun theMasterBallAlwaysCatchesAndCannotBeBought() {
        assertEquals("Always catches", ItemInfo.short(data, "master-ball"))
        val what = fact("master-ball", "What it does")
        assertTrue(what.contains("can't be bought") && what.contains("Best streak") && what.contains("365"))
        assertFalse(what.contains("runs away"))
    }

    @Test fun anOldBallThrowsLikeAGreatBallAndCountsForTheTrade() {
        for (id in Tuning.OLD_BALLS) {
            assertEquals("Like a Great Ball", ItemInfo.short(data, id))
            assertTrue(id, fact(id, "What it does").contains("50 %") && fact(id, "What it does").contains("runs away"))
            assertTrue(id, fact(id, "How to use").contains("counts as a Great Ball"))
        }
    }

    // ---- healing ----

    @Test fun healingItemsSayHowMuchAndWhatHappensInAFight() {
        assertEquals("Heals 20 HP", ItemInfo.short(data, "potion"))
        assertEquals("Heals 60 HP", ItemInfo.line(data, "super-potion"))
        assertEquals("Heals all HP", ItemInfo.short(data, "max-potion"))
        assertTrue(fact("potion", "What it does").contains("**20 HP**") && fact("potion", "What it does").contains("never above"))
        assertTrue(fact("potion", "How to use").contains("**can't**"))
        val fight = fact("potion", "In a fight")
        assertTrue(fight.contains("**main**") && fight.contains("**one turn**") && fight.contains("**first answer**") && fight.contains("attacks once"))
        assertTrue(fight.contains("bench"))
        for (id in listOf("full-restore", "max-potion")) assertTrue(fact(id, "What it does").contains("**all HP**") && fact(id, "What it does").contains("status"))
    }

    // ---- evolution items ----

    @Test fun anAppleNamesItsPokemon() {
        assertEquals("Evolves Applin", ItemInfo.short(data, "tart-apple"))
        assertEquals("Evolves Applin into Flapple", ItemInfo.line(data, "tart-apple"))
        assertEquals("Evolves **Applin** into **Flapple**.", fact("tart-apple", "What it does"))
        assertEquals("Evolves **Applin** into **Appletun**.", fact("sweet-apple", "What it does"))
        assertTrue(fact("tart-apple", "How to use").contains("**Everstone**"))
        assertTrue(fact("tart-apple", "In a fight").contains("**one turn**"))
    }

    @Test fun aStoneListsEveryPairAndTheShortLineCountsTheRest() {
        assertEquals("Evolves Pikachu +1", ItemInfo.short(data, "thunder-stone"))
        assertEquals("Evolves Pikachu and Eevee", ItemInfo.line(data, "thunder-stone"))
        val what = fact("thunder-stone", "What it does")
        assertEquals("Evolves **Pikachu** into **Raichu**, **Eevee** into **Jolteon**.", what)
        assertEquals("Evolves Eevee", ItemInfo.short(data, "fire-stone"))
    }

    @Test fun aLongListIsCutWithAndNMore() {
        val what = fact("dawn-stone", "What it does")
        assertTrue(what, what.contains("**Fire300** into **Evo400**") && what.endsWith(", and 2 more."))
        assertFalse(what.contains("Fire306"))
        assertEquals("Evolves Fire300 and 7 more", ItemInfo.line(data, "dawn-stone"))
    }

    @Test fun aNightOnlyItemSaysSoAndAGenderOnlyPairIsMarked() {
        assertEquals("Evolves **Sneasel** into **Weavile**. Works only at **night**.", fact("razor-claw", "What it does"))
        assertEquals("Evolves **Snorunt** into **Froslass** (female only).", fact("ice-stone", "What it does"))
    }

    @Test fun theLinkingCordEvolvesTradeEvolvers() {
        assertEquals("Trade evolvers", ItemInfo.short(data, "linking-cord"))
        assertEquals("Evolves Pokémon that evolve by trade", ItemInfo.line(data, "linking-cord"))
        val what = fact("linking-cord", "What it does")
        assertTrue(what, what.contains("**by trade**") && what.contains("**Kadabra** into **Alakazam**"))
        assertFalse("Onix needs a held Metal Coat, not the cord", what.contains("Onix"))
        assertTrue(fact("metal-coat", "What it does").contains("**Onix** into **Steelix**"))
    }

    @Test fun withoutSpeciesDataAnEvolutionItemStillReads() {
        assertEquals("Evolution item", ItemInfo.short(null, "thunder-stone"))
        assertEquals("Evolves Pokémon that evolve with it", ItemInfo.line(null, "thunder-stone"))
        assertTrue(fact("thunder-stone", "What it does", null).isNotBlank())
        assertEquals("Evolves Pokémon that evolve with it", ItemInfo.effect("thunder-stone"))
    }

    // ---- held items ----

    @Test fun moneyItemsSayMainAndDoubleMoneyAndNeverCashOrBonus() {
        for (id in listOf("amulet-coin", "luck-incense")) {
            assertEquals("Main: ×2 money", ItemInfo.short(data, id))
            assertEquals("Main holds it: ×2 money", ItemInfo.line(data, id))
            val text = ItemInfo.plain(all(id))
            assertTrue(id, text.contains("main") && text.contains("answers and wins") && text.contains("doubled"))
            assertFalse(id, text.contains("cash", true) || text.contains("bonus", true))
        }
    }

    @Test fun otherHeldItemsSayWhoTheyWorkFor() {
        assertEquals("×1.5 XP", ItemInfo.short(data, "lucky-egg"))
        assertTrue(fact("lucky-egg", "What it does").contains("Exp. Share"))
        assertEquals("Never evolves", ItemInfo.short(data, "everstone"))
        assertTrue(fact("everstone", "What it does").contains("level, friendship, stone or trade"))
        assertEquals("Main: ×2 EVs", ItemInfo.short(data, "macho-brace"))
        assertTrue(fact("macho-brace", "What it does").contains("252") && fact("macho-brace", "What it does").contains("510"))
        assertEquals("Main: +8 Speed EVs", ItemInfo.short(data, "power-anklet"))
        assertEquals("Main: +8 Sp. Atk EVs", ItemInfo.short(data, "power-lens"))
        assertTrue(fact("power-band", "What it does").contains("**8 extra Sp. Def EVs**"))
        for (id in Items.ALL.values.filter { it.kind == ItemKind.HELD }.map { it.id }) {
            assertTrue(id, fact(id, "How to use").contains("**one item**") && fact(id, "How to use").contains("old one goes back"))
            assertTrue(id, fact(id, "In a fight").contains("**not a turn**"))
        }
    }

    // ---- escape, fossils, TMs ----

    @Test fun escapeItemsDoNotAttack() {
        for (id in listOf("poke-doll", "fluffy-tail")) {
            assertEquals("Skip this wild one", ItemInfo.short(data, id))
            assertEquals("Skip this wild Pokémon", ItemInfo.line(data, id))
            assertTrue(fact(id, "What it does").contains("**no XP**") && fact(id, "What it does").contains("**no money**"))
            assertTrue(fact(id, "In a fight").contains("**doesn't attack**"))
        }
    }

    @Test fun aFossilNamesItsPokemonAndLevel() {
        assertEquals("Revives Omanyte", ItemInfo.short(data, "helix-fossil"))
        assertEquals("Revives Omanyte at Lv 5", ItemInfo.line(data, "helix-fossil"))
        val what = fact("helix-fossil", "What it does")
        assertTrue(what.contains("**Omanyte**") && what.contains("**Lv 5**") && what.contains("used up"))
        assertTrue(fact("helix-fossil", "In a fight").contains("not a turn"))
        assertEquals("Revives a Pokémon", ItemInfo.short(null, "helix-fossil"))
        assertEquals("Revives a Pokémon at Lv 5", ItemInfo.line(null, "helix-fossil"))
        assertEquals("Revives a Pokémon", ItemInfo.short(data, "dome-fossil"))   // species 140 is not in this small world
    }

    @Test fun aTmIsExplainedOnce() {
        val facts = ItemInfo.tmFacts()
        assertEquals(listOf("What it does", "How to use"), facts.map { it.title })
        assertTrue(facts[0].text.contains("**once**") && facts[0].text.contains("**never used up**") && facts[0].text.contains("every Pokémon"))
        assertTrue(facts[1].text.contains("**Teach…**") && facts[1].text.contains("Who can learn it") && facts[1].text.contains("4 moves"))
        assertFalse("the list is read-only: no teaching from it", facts[1].text.contains("or from **Who can learn it**"))
        assertTrue(facts.all { marksBalanced(it.text) })
    }

    @Test fun aMoveEffectIsOneShortLineWithBoldKeyWords() {
        assertEquals("May **paralyze** the target (10 %).", ItemInfo.moveEffect(data, "thunderbolt"))
        assertEquals("**Paralyzes** the target.", ItemInfo.moveEffect(data, "thunderwave"))
        assertEquals("", ItemInfo.moveEffect(data, "tackle"))
        assertEquals("", ItemInfo.moveEffect(data, "no-such-move"))
        for (m in data.moves.keys) assertTrue(m, marksBalanced(ItemInfo.moveEffect(data, m)))
        assertTrue(ItemInfo.moveEffect(data, "absorb").contains("**Heals you**"))
    }

    // ---- plurals, own lines, marks ----

    @Test fun pluralsAreNatural() {
        assertEquals("Great Ball", ItemInfo.plural("Great Ball", 1))
        assertEquals("Great Balls", ItemInfo.plural("Great Ball", 2))
        assertEquals("Power Lenses", ItemInfo.plural("Power Lens", 2))
        assertEquals("Thunder Stones", ItemInfo.plural("Thunder Stone", 5))
        assertEquals("Deep Sea Teeth", ItemInfo.plural("Deep Sea Tooth", 2))
        assertEquals("Deep Sea Tooth", ItemInfo.plural("Deep Sea Tooth", 1))
        assertEquals("King's Rocks", ItemInfo.plural("King's Rock", 2))
        assertEquals("Macho Braces", ItemInfo.plural("Macho Brace", 3))
        assertEquals("Soda Pops", ItemInfo.plural("Soda Pop", 2))
        for (mass in listOf("Fresh Water", "Moomoo Milk", "Energy Powder", "Berry Juice", "Lemonade", "Luck Incense", "Old Amber")) assertEquals(mass, ItemInfo.plural(mass, 3))
        assertEquals("Mix", ItemInfo.plural("Mix", 1))
        assertEquals("Mixes", ItemInfo.plural("Mix", 2))
        assertEquals("Buzzes", ItemInfo.plural("Buzz", 2))
        assertEquals("Peaches", ItemInfo.plural("Peach", 2))
        assertEquals("Dishes", ItemInfo.plural("Dish", 2))
    }

    @Test fun ownLinesUseThePlural() {
        assertEquals("You own **1 Great Ball**", ItemInfo.ownLine("great-ball", 1))
        assertEquals("You own **2 Great Balls**", ItemInfo.ownLine("great-ball", 2))
        assertEquals("You don't own any **Great Balls** yet", ItemInfo.ownLine("great-ball", 0))
        assertEquals("You own **3 Power Lenses**", ItemInfo.ownLine("power-lens", 3))
        assertEquals("You own **2 Fresh Water**", ItemInfo.ownLine("fresh-water", 2))
        assertEquals("You **own** this TM", ItemInfo.tmOwnLine(true))
        assertEquals("You don't own this TM yet", ItemInfo.tmOwnLine(false))
    }

    @Test fun plainStripsTheMarks() {
        assertEquals("You own 2 Great Balls", ItemInfo.plain("You own **2 Great Balls**"))
        assertEquals("no marks", ItemInfo.plain("no marks"))
        assertEquals("", ItemInfo.plain("****"))
    }

    // ---- kept for the old callers ----

    @Test fun theOldHelpersStillWork() {
        assertTrue(ItemInfo.howToUse("poke-ball").contains("Throw"))
        assertEquals("", ItemInfo.howToUse("not-an-item"))
        assertTrue(ItemInfo.tmHowToUse().contains("never used up"))
        assertEquals("Heals 20 HP", ItemInfo.effect("potion"))
    }
}
