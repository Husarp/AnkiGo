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
    }
}
