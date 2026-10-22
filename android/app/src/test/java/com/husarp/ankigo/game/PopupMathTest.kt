package com.husarp.ankigo.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** R6-07 items 19, 20: the popup titles and which found items a batch of events brings. */
class PopupMathTest {
    @Test fun achievementTitleSaysAchievementUnlocked() {
        assertEquals("Achievement unlocked", PopupMath.achievementTitle(1))
        assertEquals("3 achievements unlocked", PopupMath.achievementTitle(3))
        assertEquals("2 achievements unlocked", PopupMath.achievementTitle(2))
    }

    @Test fun itemTitle() {
        assertEquals("Item found", PopupMath.itemTitle(1))
        assertEquals("2 items found", PopupMath.itemTitle(2))
    }

    @Test fun findsTakeOnlyTheItemEventsInOrder() {
        val f = PopupMath.finds(listOf(GameEvent.Info("x"), GameEvent.ItemFound("potion", 1), GameEvent.Cash(5, 10, CashKind.ANSWER), GameEvent.ItemFound("fire-stone", 2)))
        assertEquals(listOf("potion", "fire-stone"), f.map { it.item })
        assertEquals(listOf(1, 2), f.map { it.total })
    }

    @Test fun theSameItemTwiceIsOneEntryWithTheHigherTotal() {
        val f = PopupMath.finds(listOf(GameEvent.ItemFound("potion", 1), GameEvent.ItemFound("potion", 2)))
        assertEquals(1, f.size)
        assertEquals(2, f.single().total)
    }

    @Test fun mergeKeepsThePlaceOfWhatWaits() {
        val m = PopupMath.merge(listOf(ItemFind("potion", 1), ItemFind("fire-stone", 1)), listOf(ItemFind("potion", 3), ItemFind("poke-ball", 5)))
        assertEquals(listOf("potion", "fire-stone", "poke-ball"), m.map { it.item })
        assertEquals(3, m[0].total)
    }

    @Test fun giftsAreTheDailyGiftEventsInOrder() { // R14-B2-D
        val g = PopupMath.gifts(listOf(GameEvent.Info("x"), GameEvent.DailyGift("poke-ball", null, 3, 6), GameEvent.ItemFound("potion", 1), GameEvent.DailyGift(null, "thunderbolt", 1, 7)))
        assertEquals(listOf("poke-ball", null), g.map { it.item })
        assertEquals(listOf(null, "thunderbolt"), g.map { it.tm })
        assertEquals(listOf(3, 1), g.map { it.count })
        assertEquals(listOf(6, 7), g.map { it.streakDays })
        assertEquals("Daily gift", PopupMath.GIFT_TITLE)
        assertTrue(PopupMath.gifts(listOf(GameEvent.ItemFound("potion", 1))).isEmpty())
    }

    @Test fun descriptionIsTheCatalogueNote() {
        assertTrue(PopupMath.description("potion").isNotBlank())
        assertEquals("No description.", PopupMath.description("not-an-item"))
    }
}
