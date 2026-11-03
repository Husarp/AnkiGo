package com.husarp.ankigo.shots

import android.view.View
import com.husarp.ankigo.game.GameEvent
import com.husarp.ankigo.ui.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class R29HDailyGiftAlertTest {
    @get:Rule val paparazzi = Shots.rule()
    @After fun clean() { AchievementPopup.reset(); BattleShotKit.reset() }

    private fun panel(): BattlePanelView {
        val ctx = Shots.files(paparazzi.context)
        FakeGame.install(ctx)
        ctx.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", false).commit()
        return BattlePanelView(ctx).also {
            it.renderAll()
            it.measure(View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(700, View.MeasureSpec.EXACTLY))
            it.layout(0, 0, 800, 700)
        }
    }

    @Test fun goalWithItemOrTmGiftLeavesOnlyTheGiftCard() {
        for (gift in listOf(GameEvent.DailyGift("potion", null, 2, 3),
            GameEvent.DailyGift(null, "thunderbolt", 1, 7))) {
            val p = panel()
            val batch = listOf(GameEvent.Info("Daily goal reached!"), gift)
            AchievementPopup.offer(batch)
            val player = EventPlayer(p)
            try {
                player.enqueue(batch)
                assertEquals(1, AchievementPopup.waitingGifts().size)
                assertFalse(p.toastLayer.hasMessages)
                assertTrue(FloatTexts.of(gift).isEmpty())
                assertTrue(AchievementPopup.waitingForPlayback)
                paparazzi.snapshot(p, "r29h-goal-with-${gift.item ?: "tm"}-gift", 300)
            } finally { player.clear(); AchievementPopup.reset() }
        }
    }

    @Test fun aGoalWithoutAGiftStillAnnouncesTheGoal() {
        val p = panel()
        val player = EventPlayer(p)
        try {
            player.enqueue(listOf(GameEvent.Info("Daily goal reached!")))
            assertEquals(ToastKind.GOAL, p.toastLayer.shown?.kind)
            assertEquals("Daily goal reached!", p.toastLayer.shown?.text)
            paparazzi.snapshot(p, "r29h-standalone-goal-alert", 300)
        } finally { player.clear() }
    }

    @Test fun aRandomItemOnTheGoalAnswerKeepsItsPopupWithoutAnAlert() {
        val p = panel()
        val batch = listOf(GameEvent.Info("Daily goal reached!"),
            GameEvent.ItemFound("potion", 3), GameEvent.DailyGift("potion", null, 2, 3))
        AchievementPopup.offer(batch)
        val player = EventPlayer(p)
        try {
            player.enqueue(batch)
            assertFalse(p.toastLayer.hasMessages)
            assertEquals(listOf("potion"), AchievementPopup.waitingItems().map { it.item })
            assertEquals(1, AchievementPopup.waitingGifts().size)
            paparazzi.snapshot(p, "r29h-item-card-without-duplicate-alert", 300)
        } finally { player.clear() }
    }

    @Test fun anItemWithoutItsCardKeepsTheFallbackAlert() {
        for (queued in listOf(emptyList(), listOf(GameEvent.ItemFound("potion", 2)),
            listOf(GameEvent.ItemFound("poke-ball", 3)))) {
            AchievementPopup.reset()
            val p = panel()
            AchievementPopup.offer(queued)
            val player = EventPlayer(p)
            try {
                player.enqueue(listOf(GameEvent.ItemFound("potion", 3)))
                assertEquals(ToastKind.ITEM, p.toastLayer.shown?.kind)
            } finally { player.clear() }
        }
    }

    @Test fun itemCardsRemainQueuedWithAchievementsOffAndReducedMotion() {
        val p = panel()
        AchievementPopup.setMode(p.context, 2)
        p.context.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", true).commit()
        val batch = listOf(GameEvent.ItemFound("potion", 3))
        AchievementPopup.offer(batch)
        val player = EventPlayer(p)
        try {
            player.enqueue(batch)
            assertNotEquals(ToastKind.ITEM, p.toastLayer.shown?.kind)
            assertEquals(listOf("potion"), AchievementPopup.waitingItems().map { it.item })
        } finally { player.clear(); AchievementPopup.setMode(p.context, 1) }
    }

    @Test fun otherInfoInAGiftBatchStillShows() {
        val p = panel()
        val player = EventPlayer(p)
        try {
            player.enqueue(listOf(GameEvent.Info("The Repeat Ball came back!"),
                GameEvent.DailyGift("poke-ball", null, 3, 6)))
            assertEquals(ToastKind.NEWS, p.toastLayer.shown?.kind)
            assertEquals("The Repeat Ball came back!", p.toastLayer.shown?.text)
        } finally { player.clear() }
    }
}
