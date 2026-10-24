package com.husarp.ankigo.game

import com.husarp.ankigo.game.ankimon.AnkimonMerge
import com.husarp.ankigo.game.data.Parsers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * R17C-6: the nine evolution items that were missing from the catalogue (their Pokémon could never evolve; the tenth of the audit, the
 * Masterpiece Teacup, evolves a form AnkiGo does not have, so it is left out): each is in the catalogue as
 * an EVOLUTION item, evolves its species, reads right, is found (and, with a PokeAPI price, sold); with the real data (--real) every
 * ITEM / TRADE evolution's item is in the catalogue.
 */
class R17C6EvolutionItemsTest {
    /** item -> (species it evolves, species it becomes), the PokeAPI/Showdown pairs of the ticket. */
    private val pairs = listOf(
        Triple("auspicious-armor", "Charcadet", "Armarouge"), Triple("malicious-armor", "Charcadet", "Ceruledge"),
        Triple("black-augurite", "Scyther", "Kleavor"), Triple("peat-block", "Ursaring", "Ursaluna"),
        Triple("galarica-cuff", "Slowpoke-Galar", "Slowbro-Galar"), Triple("galarica-wreath", "Slowpoke-Galar", "Slowking-Galar"),
        Triple("syrupy-apple", "Applin", "Dipplin"), Triple("unremarkable-teacup", "Poltchageist", "Sinistcha"),
        Triple("metal-alloy", "Duraludon", "Archaludon"),
    )
    private val ids = pairs.map { it.first }
    /** PokeAPI prices (items.csv): the first six are sold, the last three (price 0) are only found. */
    private val prices = mapOf(
        "auspicious-armor" to 3000, "malicious-armor" to 750, "galarica-cuff" to 3000, "galarica-wreath" to 3000, "syrupy-apple" to 500,
        "unremarkable-teacup" to 400, "black-augurite" to 0, "peat-block" to 0, "metal-alloy" to 0,
    )

    /** A small world with one species per pair (numbers 1000+; from -> to by the item). */
    private fun world(): GameData {
        val base = TestData.world()
        val sp = pairs.flatMapIndexed { i, (item, from, to) ->
            val a = 1000 + i * 2; val b = a + 1
            listOf(
                TestData.species(a, from, evolutions = listOf(Evolution(b, EvoKind.ITEM, item = item))),
                TestData.species(b, to, prevo = a, evoType = "useItem"),
            )
        }
        return GameData(base.species + sp.associateBy { it.num }, base.moves, base.growth, base.itemPrices + prices)
    }

    @Test fun allNineAreEvolutionItemsInTheCatalogue() {
        for (id in ids) {
            assertEquals(id, ItemKind.EVOLUTION, Items.def(id)?.kind)
            assertTrue(id, Items.name(id).first().isUpperCase())
        }
        assertEquals(9, ids.toSet().size)
        assertEquals(41, Items.ALL.values.count { it.kind == ItemKind.EVOLUTION })
    }

    @Test fun eachItemEvolvesItsSpecies() {
        val d = world()
        for ((i, p) in pairs.withIndex()) {
            val s = TestData.state(d, 1000 + i * 2, 20)
            val mon = s.main!!
            Economy.give(s, p.first)
            assertTrue(p.first, ItemUse.canUse(d, s, p.first, mon))
            val r = ItemUse.use(d, s, p.first, mon.uid, TestData.rng(), 0)
            assertTrue(p.first + ": " + r.message, r.ok)
            assertEquals(p.third, mon.name)
            assertEquals(0, s.count(p.first))
        }
    }

    @Test fun anItemDoesNotEvolveAnotherSpecies() {
        val d = world()
        val s = TestData.state(d, 25, 20)
        for (id in ids) { Economy.give(s, id); assertFalse(id, ItemUse.canUse(d, s, id, s.main)) }
    }

    @Test fun theTextsNameTheirPokemon() {
        val d = world()
        for ((id, from, to) in pairs) {
            assertEquals("Evolves $from", ItemInfo.short(d, id))
            val line = ItemInfo.line(d, id)
            assertTrue("$id: $line", line.startsWith("Evolves $from") || line == "Evolves $from into $to")
            val what = ItemInfo.facts(d, id).first { it.title == "What it does" }.text
            assertEquals("Evolves **$from** into **$to**.", what)
        }
    }

    @Test fun theSixSoldOnesJoinTheShopAndTheThreeOthersDoNot() {
        val d = world()
        val shop = Items.shopPool(d)
        for (id in ids) assertEquals(id, prices.getValue(id) > 0, id in shop)
        // every one of them is a random find
        val drops = Items.dropPool()
        for (id in ids) assertTrue(id, id in drops)
        // the weekend special and the daily gift keep to the "-stone" items
        assertTrue(DailyGift.stones(d).none { it in ids })
        for (day in listOf("2026-10-24", "2026-10-31", "2026-11-07")) assertTrue(Shop.weekendFor(d, day)?.item?.let { it !in ids } ?: true)
    }

    @Test fun ankimonNamesOfTheNewItemsImport() {
        assertEquals("black-augurite", AnkimonMerge.itemKey("Black Augurite"))
        assertEquals("peat-block", AnkimonMerge.itemKey("peat_block"))
        assertEquals("galarica-cuff", AnkimonMerge.itemKey("Galarica Cuff"))
        assertEquals("unremarkable-teacup", AnkimonMerge.itemKey("unremarkable teacup"))
        assertEquals("metal-alloy", AnkimonMerge.itemKey("MetalAlloy"))
        for (id in ids) assertEquals(id, AnkimonMerge.itemKey(Items.name(id)))
    }

    // ---- with the real data (run with --real) ----

    private fun real(): GameData? {
        val dir = System.getenv("ANKIGO_REAL_DATA")?.takeIf { it.isNotBlank() }?.let { File(it) } ?: return null
        fun text(vararg names: String) = names.map { File(dir, it) }.first { it.isFile }.readText()
        return Parsers.build(Parsers.Sources(
            pokedexJson = text("pokedex.json"), movesJson = text("moves.json"), learnsetsJson = text("learnsets.json"),
            speciesCsv = text("pokemon_species.csv", "species.csv"), pokemonCsv = text("pokemon.csv"), statsCsv = text("pokemon_stats.csv"),
            evolutionCsv = text("pokemon_evolution.csv"), experienceCsv = text("experience.csv"), itemsCsv = text("items.csv"),
        ))
    }

    @Test fun everyItemOrTradeEvolutionHasItsItemInTheCatalogue() {
        val d = real(); assumeTrue("ANKIGO_REAL_DATA not set", d != null)
        d!!
        val missing = d.species.values.flatMap { sp ->
            sp.evolutions.filter { (it.kind == EvoKind.ITEM || it.kind == EvoKind.TRADE) && it.item != null }.map { sp.name to it.item!! }
        }.filter { Items.def(it.second) == null }
        assertTrue("evolutions whose item is not in the catalogue: $missing", missing.isEmpty())
        // ... and the item does evolve that species (a real Mon of it)
        var checked = 0
        for (sp in d.species.values) for (e in sp.evolutions) {
            val item = e.item ?: continue
            if (e.kind != EvoKind.ITEM && e.kind != EvoKind.TRADE) continue
            if (!d.species.containsKey(e.to)) continue
            val mon = TestData.state(d, sp.num, 50).main!!
            e.gender?.let { mon.gender = it }   // Nidorina -> Nidoqueen is for females only
            assertNotNull("${sp.name} with $item", Progression.itemEvolutionAnyTime(d, mon, item)); checked++
        }
        println("REAL R17C-6: $checked item / trade-item evolutions checked")
        assertTrue(checked > 60)
        for (id in ids) {
            assertEquals("price of $id", prices.getValue(id), d.price(id))
            assertEquals(id, prices.getValue(id) > 0, id in Items.shopPool(d))
        }
        // every catalogue evolution item is used by some species (a dead item would be sold or found for nothing). Apart: the Linking Cord
        // (trade without an item) and the Chipped Pot, which was there before R17C-6 and evolves Polteageist-Antique, a form AnkiGo has no
        // species for (known, left as it is; the Masterpiece Teacup is not added for the same reason).
        val dead = Items.ALL.values.filter { it.kind == ItemKind.EVOLUTION && it.id != "linking-cord" && it.id != "chipped-pot" }.map { it.id }.filter { id ->
            d.species.values.none { sp -> sp.evolutions.any { (it.kind == EvoKind.ITEM || it.kind == EvoKind.TRADE) && it.item == id } }
        }
        assertTrue("evolution items no species uses: $dead", dead.isEmpty())
        // the nine species of the audit can evolve now
        for (id in ids) {
            val sp = d.species.values.firstOrNull { s -> s.evolutions.any { it.kind == EvoKind.ITEM && it.item == id } }
            assertNotNull("a species that evolves with $id", sp)
            sp!!
            val mon = TestData.state(d, sp.num, 50).main!!
            assertNotNull("${sp.name} with $id", Progression.itemEvolutionAnyTime(d, mon, id))
        }
    }
}
