package com.husarp.ankigo.game

import com.husarp.ankigo.game.ankimon.AnkimonMerge
import com.husarp.ankigo.game.save.SaveCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * R17C-FIX items 6 and the Chipped Pot note.
 * 6: Full Restore removes Rest's fixed sleep length (`sleepTurns`) when it cures sleep, so a later sleep in the fight is the normal one.
 * Chipped Pot: not sold, not found, still in the catalogue (a bag that holds one keeps it, an Ankimon import and a save still know it),
 *    its texts say it does nothing here.
 */
class R17CFixBItemsTest {
    private val data = TestData.world()
    private val now = 1_790_000_000_000L

    // ---- item 6 ------------------------------------------------------------------------------------------------

    private fun fightWith(status: String?): GameState {
        val s = TestData.state(data, 25, 20, listOf("tackle"))
        s.main!!.hp = 3
        s.battle = Battle(TestData.mon(data, 19, 12, listOf("tackle"), uid = "wild"), Tier.NORMAL, startedAt = now, rounds = 0, answers = 0)
        s.battle!!.player.status = status
        Economy.give(s, "full-restore", 2); Economy.give(s, "max-potion", 1)
        return s
    }

    @Test fun fullRestoreRemovesTheRestSleepCounterWithTheSleep() {
        for (turns in listOf(0, 1, 2)) {
            val s = fightWith("slp")
            val side = s.battle!!.player
            side.statusTurns = 2
            side.volatiles[CoreRules.SLEEP_TURNS] = turns
            side.volatiles["confusion"] = 3                 // an unrelated condition stays
            val r = ItemUse.use(data, s, "full-restore", s.main!!.uid, TestData.rng(), now)
            assertTrue(r.message, r.ok)
            assertNull(side.status); assertEquals(0, side.statusTurns)
            assertFalse("sleepTurns $turns is gone", CoreRules.SLEEP_TURNS in side.volatiles)
            assertEquals(3, side.volatiles["confusion"])
        }
    }

    @Test fun anotherCureAlsoClearsItAndAnItemThatCuresNothingKeepsIt() {
        val burnt = fightWith("brn")
        burnt.battle!!.player.volatiles[CoreRules.SLEEP_TURNS] = 1   // stale counter of an earlier Rest: nothing to keep
        ItemUse.use(data, burnt, "full-restore", burnt.main!!.uid, TestData.rng(), now)
        assertFalse(CoreRules.SLEEP_TURNS in burnt.battle!!.player.volatiles)
        // a Max Potion heals HP only: the sleep and its counter stay
        val asleep = fightWith("slp")
        asleep.battle!!.player.volatiles[CoreRules.SLEEP_TURNS] = 2
        val r = ItemUse.use(data, asleep, "max-potion", asleep.main!!.uid, TestData.rng(), now)
        assertTrue(r.ok)
        assertEquals("slp", asleep.battle!!.player.status)
        assertEquals(2, asleep.battle!!.player.volatiles[CoreRules.SLEEP_TURNS])
    }

    // ---- the Chipped Pot ---------------------------------------------------------------------------------------

    private val priced = GameData(data.species, data.moves, data.growth, data.itemPrices + ("chipped-pot" to 38_000) + ("cracked-pot" to 38_000))

    @Test fun theChippedPotIsInTheCatalogueButNotSoldOrFound() {
        assertNotNull("a bag that has one keeps it", Items.def("chipped-pot"))
        assertEquals(ItemKind.EVOLUTION, Items.def("chipped-pot")!!.kind)
        assertEquals(setOf("chipped-pot"), Items.RETIRED)
        assertTrue("PokeAPI prices it", priced.price("chipped-pot") > 0)
        assertFalse("chipped-pot" in Items.shopPool(priced))
        assertTrue("its twin is still sold", "cracked-pot" in Items.shopPool(priced))
        assertFalse("chipped-pot" in Items.dropPool())
        assertTrue("cracked-pot" in Items.dropPool())
        // many random finds never give one
        val s = TestData.state(data)
        val events = mutableListOf<GameEvent>()
        val rng = Random(11)
        repeat(4000) { s.itemCountdown = 1; Economy.onReview(priced, s, rng, events) }
        assertTrue(events.filterIsInstance<GameEvent.ItemFound>().size > 1000)
        assertEquals(0, s.count("chipped-pot"))
    }

    @Test fun neitherTheDailyShopNorTheDealOffersItAndOldStockLeaves() {
        for (d in 1..300) {
            val day = "2026-%02d-%02d".format(1 + d / 28 % 12, 1 + d % 28)
            val (items, _) = Shop.stockFor(priced, day, 40)
            assertFalse(day, "chipped-pot" in items)
            assertFalse(day, Shop.dealFor(priced, day)?.item == "chipped-pot")
        }
        // a Chipped Pot that was drawn into today's stock before the update leaves it, and can't be bought
        val s = TestData.state(data)
        s.day = "2026-10-13"; s.shopDay = "2026-10-13"
        s.shopItems.clear(); s.shopItems += listOf("great-ball", "chipped-pot")
        s.cash = 1_000_000
        assertFalse(Shop.buy(priced, s, "chipped-pot", now).ok)
        Shop.ensureToday(priced, s)
        assertEquals(listOf("great-ball"), s.shopItems.toList())
        assertEquals(0, s.count("chipped-pot"))
        assertEquals(1_000_000, s.cash)
    }

    @Test fun aBagThatHoldsOneKeepsItThroughSaveAndAnkimonImport() {
        val s = TestData.state(data)
        s.bag["chipped-pot"] = 2
        val back = SaveCodec.decode(SaveCodec.encode(s))
        assertEquals(2, back.count("chipped-pot"))
        assertEquals("chipped-pot", AnkimonMerge.itemKey("Chipped Pot"))
        // using it does nothing and uses nothing up
        assertFalse(ItemUse.canUse(data, s, "chipped-pot", s.main))
        val r = ItemUse.use(data, s, "chipped-pot", s.main!!.uid, TestData.rng(), now)
        assertFalse(r.ok)
        assertEquals(2, s.count("chipped-pot"))
    }

    @Test fun itsTextsSayItDoesNothingHere() {
        assertEquals("Does nothing here", Items.def("chipped-pot")!!.note)
        assertEquals("Does nothing here", ItemInfo.short(data, "chipped-pot"))
        val line = ItemInfo.line(data, "chipped-pot")
        assertEquals("Does nothing here: no Pokémon uses it", line)
        assertTrue(line.length <= 40)
        assertEquals(line, ItemInfo.effect("chipped-pot"))
        val facts = ItemInfo.facts(data, "chipped-pot")
        val what = facts.first { it.title == "What it does" }.text
        assertTrue(what, what.startsWith("Does **nothing** in AnkiGo") && what.endsWith("not sold or found any more."))
        val how = facts.first { it.title == "How to use" }.text
        assertTrue(how, how.contains("can't use it") && how.contains("stays in the Bag"))
        assertTrue("no fight block: it takes no turn", facts.none { it.title == "In a fight" })
        assertEquals("No Pokémon in AnkiGo evolves with it, so it can't be used.", ItemInfo.howToUse("chipped-pot"))
        // its twin is untouched
        assertEquals("Evolution item", ItemInfo.short(data, "cracked-pot"))
        assertEquals("Evolves Pokémon that evolve with it", Items.def("cracked-pot")!!.note)
        assertTrue(ItemInfo.facts(data, "cracked-pot").any { it.title == "In a fight" })
    }
}
