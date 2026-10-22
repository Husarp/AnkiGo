package com.husarp.ankigo.ui

import com.husarp.ankigo.game.GameData
import com.husarp.ankigo.game.Shop
import com.husarp.ankigo.game.TestData
import com.husarp.ankigo.game.Tuning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.TimeZone

/** R10 W11: what the Shop tab shows (the price layout choice, the tile list and columns, the texts), and the rule of the Shop tab's dot. */
class ShopPlanTest {
    private val data: GameData = TestData.world()

    // ---- prices ----

    @Test fun theDealItemShowsTheOldPriceAndTheNewOneEverythingElseJustOne() {
        for (offset in 0 until 40L) {
            val day = LocalDate.parse("2026-10-01").plusDays(offset).toString()
            val deal = Shop.dealFor(data, day)!!
            val p = ShopPlan.price(data, day, deal.item)
            assertEquals(day, deal.price, p.now)
            assertEquals(day, deal.oldPrice, p.was)
            assertTrue(p.discounted)
            assertTrue("the new price is smaller", p.now < p.was!!)
            // a basic is never the deal and keeps its one normal price
            for (basic in Shop.basics()) {
                val b = ShopPlan.price(data, day, basic)
                assertEquals(data.price(basic), b.now)
                assertNull(b.was)
                assertFalse(b.discounted)
            }
        }
    }

    @Test fun theBuySheetAndTheTilesAgreeWithWhatTheBuyCharges() {
        // ShopPlan.price is Shop.priceOf with the old price attached: the same number the buy takes
        for (offset in 0 until 20L) {
            val day = LocalDate.parse("2026-10-01").plusDays(offset).toString()
            for (item in data.itemPrices.keys) assertEquals("$item $day", Shop.priceOf(data, day, item), ShopPlan.price(data, day, item).now)
        }
    }

    @Test fun aTmIsNeverDiscounted() {
        val p = ShopPlan.tmPrice()
        assertEquals(Tuning.TM_PRICE, p.now)
        assertNull(p.was)
    }

    // ---- tiles and columns ----

    @Test fun specialsAreTodaysItemsThenTodaysTmsInOneList() {
        val list = ShopPlan.specials(listOf("a", "b"), listOf("m1", "m2", "m3"))
        assertEquals(listOf(ShopPlan.Tile.Item("a"), ShopPlan.Tile.Item("b"), ShopPlan.Tile.Tm("m1"), ShopPlan.Tile.Tm("m2"), ShopPlan.Tile.Tm("m3")), list)
        assertTrue(ShopPlan.specials(emptyList(), emptyList()).isEmpty())
    }

    @Test fun threeTilesAcrossUnlessTheTextIsTooBigForTheWidth() {
        assertEquals(3, ShopPlan.columns(412, 1f))
        assertEquals(3, ShopPlan.columns(340, 1f)) // the floating card
        assertEquals(3, ShopPlan.columns(320, 1f))
        assertEquals(3, ShopPlan.columns(412, 1.3f))
        assertEquals(3, ShopPlan.columns(360, 1.15f))
        assertEquals("320 dp at font 2.0", 2, ShopPlan.columns(320, 2f))
        assertEquals("412 dp at font 2.0", 2, ShopPlan.columns(412, 2f))
        assertEquals(3, ShopPlan.columns(869, 2f)) // landscape
        assertEquals("a font scale under 1 counts as 1", 3, ShopPlan.columns(320, 0.85f))
    }

    // ---- PLAN 168: the open tile becomes a full-width card where its row was ----

    private fun items(vararg ids: String) = ids.map { ShopPlan.Tile.Item(it) }

    @Test fun withNoOpenTileTheRowsAreJustTheChunks() {
        val tiles = items("a", "b", "c", "d", "e")
        assertEquals(listOf(ShopPlan.Row.Tiles(items("a", "b", "c")), ShopPlan.Row.Tiles(items("d", "e"))), ShopPlan.rows(tiles, 3, null))
    }

    @Test fun theOpenTileIsACardAndTheRestOfItsRowFollowsBelow() {
        val tiles = items("a", "b", "c", "d", "e")
        assertEquals(
            listOf(ShopPlan.Row.Tiles(items("a", "b", "c")), ShopPlan.Row.Card(ShopPlan.Tile.Item("d")), ShopPlan.Row.Tiles(items("e"))),
            ShopPlan.rows(tiles, 3, ShopPlan.Tile.Item("d")),
        )
        assertEquals(
            listOf(ShopPlan.Row.Card(ShopPlan.Tile.Item("b")), ShopPlan.Row.Tiles(items("a", "c")), ShopPlan.Row.Tiles(items("d", "e"))),
            ShopPlan.rows(tiles, 3, ShopPlan.Tile.Item("b")),
        )
    }

    @Test fun aLoneOpenTileLeavesNoEmptyRowAndTwoColumnsWork() {
        assertEquals(listOf(ShopPlan.Row.Card(ShopPlan.Tile.Item("a"))), ShopPlan.rows(items("a"), 3, ShopPlan.Tile.Item("a")))
        assertEquals(
            listOf(ShopPlan.Row.Tiles(items("a", "b")), ShopPlan.Row.Card(ShopPlan.Tile.Item("c"))),
            ShopPlan.rows(items("a", "b", "c"), 2, ShopPlan.Tile.Item("c")),
        )
    }

    @Test fun anOpenTileThatIsNotInTheListChangesNothing() {
        assertEquals(ShopPlan.rows(items("a", "b"), 3, null), ShopPlan.rows(items("a", "b"), 3, ShopPlan.Tile.Item("zzz")))
    }

    @Test fun aTileAndATmOfTheSameNameHaveDifferentKeys() {
        assertEquals("item:x", ShopPlan.Tile.Item("x").key)
        assertEquals("tm:x", ShopPlan.Tile.Tm("x").key)
    }

    // ---- texts ----

    @Test fun theRibbonSaysTheDiscount() {
        assertEquals("−30 %", ShopPlan.ribbon())
    }

    @Test fun endsAtMidnightOrAtTheDayStartHour() {
        assertEquals("ends at midnight · 7 h", ShopPlan.endsText(0, 7))
        assertEquals("ends at 04:00 · 1 h", ShopPlan.endsText(4, 1))
    }

    @Test fun hoursToTheNewDayRoundUpAndAreAtLeastOne() {
        val old = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        try {
            val t = java.time.ZonedDateTime.parse("2026-10-14T17:10:00Z").toInstant().toEpochMilli()
            assertEquals("17:10 -> midnight is 6 h 50 min away", 7, ShopPlan.hoursToNewDay(t, 0))
            assertEquals("17:10 -> next 04:00", 11, ShopPlan.hoursToNewDay(t, 4))
            val justBefore = java.time.ZonedDateTime.parse("2026-10-14T23:59:30Z").toInstant().toEpochMilli()
            assertEquals(1, ShopPlan.hoursToNewDay(justBefore, 0))
            val exactly = java.time.ZonedDateTime.parse("2026-10-14T04:00:00Z").toInstant().toEpochMilli()
            assertEquals("at the day start the next one is a whole day away", 24, ShopPlan.hoursToNewDay(exactly, 4))
        } finally {
            TimeZone.setDefault(old)
        }
    }

    @Test fun talkBackReadsThePriceAndTheDeal() {
        assertEquals("Poké Ball, 200", ShopPlan.describe("Poké Ball", ShopPlan.Price(200)))
        assertEquals("Super Potion, deal, 490 instead of 700", ShopPlan.describe("Super Potion", ShopPlan.Price(490, 700)))
        assertEquals("Rare Candy, 4,800", ShopPlan.describe("Rare Candy", ShopPlan.Price(4800)))
        assertEquals("TM Surf, owned", ShopPlan.describe("TM Surf", ShopPlan.tmPrice(), owned = true))
    }

    // ---- the dot on the Shop tab ----

    @Test fun theDotShowsUntilTheShopTabWasOpenedOnTodaysGameDay() {
        assertTrue("never opened", BottomNav.dotShown(null, "2026-10-14"))
        assertTrue("opened yesterday", BottomNav.dotShown("2026-10-13", "2026-10-14"))
        assertFalse("opened today", BottomNav.dotShown("2026-10-14", "2026-10-14"))
        assertTrue("a new game day brings it back", BottomNav.dotShown("2026-10-14", "2026-10-15"))
        assertFalse("no game day yet", BottomNav.dotShown(null, ""))
        assertFalse("no game loaded", BottomNav.dotShown(null, null))
        // it is exactly the game rule
        for (seen in listOf(null, "2026-10-13", "2026-10-14")) for (today in listOf("", "2026-10-14")) assertEquals(Shop.unseen(seen, today), BottomNav.dotShown(seen, today))
    }

    @Test fun theLabelsKeepTheirSizeWhileTheyFitAndShrinkTogetherToFitWithAFloor() {
        assertEquals("fits", 20f, BottomNav.labelSizePx(tabWidthPx = 160f, maxPx = 20f, minPx = 10f, widthAtMax = 150f), 0f)
        assertEquals("exactly fits", 20f, BottomNav.labelSizePx(160f, 20f, 10f, 160f), 0f)
        assertEquals("too wide: scaled to fit", 16f, BottomNav.labelSizePx(160f, 20f, 10f, 200f), 0.001f)
        assertEquals("never under the floor", 10f, BottomNav.labelSizePx(60f, 20f, 10f, 400f), 0f)
        assertEquals("a floor above the max does not grow the text", 8f, BottomNav.labelSizePx(10f, 8f, 10f, 400f), 0f)
        assertEquals("nothing measured", 20f, BottomNav.labelSizePx(160f, 20f, 10f, 0f), 0f)
    }

    @Test fun talkBackSaysNewStockOnlyForTheShopTabWithTheDot() {
        assertEquals("Shop, new stock", BottomNav.describe(BottomNav.Tab.SHOP, true))
        assertEquals("Shop", BottomNav.describe(BottomNav.Tab.SHOP, false))
        for (t in BottomNav.Tab.entries.filter { it != BottomNav.Tab.SHOP }) assertEquals(t.label, BottomNav.describe(t, true))
    }
}
