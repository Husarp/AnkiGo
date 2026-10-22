package com.husarp.ankigo.ui

import com.husarp.ankigo.game.GameEvent
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** R6-07 item 20: achievements and found items share one queue (nothing shows without a screen in front, so everything waits here). */
class PopupQueueTest {
    @Before @After fun clean() = AchievementPopup.reset()

    @Test fun anItemFoundWaitsInTheQueue() {
        AchievementPopup.offer(listOf(GameEvent.ItemFound("potion", 1)))
        assertEquals(listOf("potion"), AchievementPopup.waitingItems().map { it.item })
        assertEquals(emptyList<String>(), AchievementPopup.waiting().map { it.rowId })
    }

    @Test fun oneBatchWithBothFillsBothQueues() {
        AchievementPopup.offer(listOf(GameEvent.ItemFound("potion", 1), GameEvent.AchievementReached("catches", 1, "Pokémon caught", "Rare")))
        assertEquals(listOf("catches"), AchievementPopup.waiting().map { it.rowId })
        assertEquals(listOf("potion"), AchievementPopup.waitingItems().map { it.item })
    }

    @Test fun theSameItemTwiceWaitsOnceWithTheNewerTotal() {
        AchievementPopup.offer(listOf(GameEvent.ItemFound("potion", 1)))
        AchievementPopup.offer(listOf(GameEvent.ItemFound("potion", 2), GameEvent.ItemFound("poke-ball", 4)))
        assertEquals(listOf("potion", "poke-ball"), AchievementPopup.waitingItems().map { it.item })
        assertEquals(2, AchievementPopup.waitingItems()[0].total)
    }

    @Test fun otherEventsQueueNothing() {
        AchievementPopup.offer(listOf(GameEvent.Info("hello")))
        assertEquals(0, AchievementPopup.waitingItems().size)
        assertEquals(0, AchievementPopup.waiting().size)
        assertEquals(0, AchievementPopup.waitingGifts().size)
    }

    // ---- R14-B2-D: the Daily gift popup shares the queue ----

    @Test fun aDailyGiftWaitsInItsOwnQueue() {
        AchievementPopup.offer(listOf(GameEvent.Info("Daily goal reached!"), GameEvent.DailyGift("poke-ball", null, 3, 6)))
        val g = AchievementPopup.waitingGifts().single()
        assertEquals("poke-ball", g.item); assertEquals(3, g.count); assertEquals(6, g.streakDays)
        assertEquals(0, AchievementPopup.waitingItems().size)
        assertEquals(0, AchievementPopup.waiting().size)
    }

    @Test fun aTmGiftWaitsToo() {
        AchievementPopup.offer(listOf(GameEvent.DailyGift(null, "thunderbolt", 1, 1)))
        val g = AchievementPopup.waitingGifts().single()
        assertEquals("thunderbolt", g.tm); assertEquals(null, g.item)
    }

    @Test fun oneBatchWithAGiftAnAchievementAndAnItemFillsAllThreeQueues() {
        AchievementPopup.offer(listOf(GameEvent.DailyGift("potion", null, 2, 2), GameEvent.ItemFound("potion", 3),
            GameEvent.AchievementReached("goals", 0, "Daily goal reached", "Common")))
        assertEquals(listOf("goals"), AchievementPopup.waiting().map { it.rowId })
        assertEquals(listOf("potion"), AchievementPopup.waitingItems().map { it.item })
        assertEquals(1, AchievementPopup.waitingGifts().size)
    }

    @Test fun giftsOfTwoDaysShowOneAfterTheOther() {
        AchievementPopup.offer(listOf(GameEvent.DailyGift("potion", null, 2, 2)))
        AchievementPopup.offer(listOf(GameEvent.DailyGift("great-ball", null, 1, 3)))
        assertEquals(listOf("potion", "great-ball"), AchievementPopup.waitingGifts().map { it.item })
    }
}
