package com.husarp.ankigo.shots

import android.view.View
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.game.GameEvent
import com.husarp.ankigo.ui.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class R23MMessageFlowTest {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    @After fun clean() { AchievementPopup.reset(); BattleShotKit.reset() }

    private fun panel(): BattlePanelView {
        val c = Shots.files(paparazzi.context)
        FakeGame.install(c)
        c.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", true).commit()
        return BattlePanelView(c).also { p ->
            p.renderAll()
            val d = c.resources.displayMetrics.density
            p.measure(View.MeasureSpec.makeMeasureSpec((412 * d).toInt(), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec((700 * d).toInt(), View.MeasureSpec.EXACTLY))
            p.layout(0, 0, p.measuredWidth, p.measuredHeight)
        }
    }
    private val xp = XpCard("friend", "Friend", 10, 5, 5, .1f, .3f, false)
    private fun active(p: BattlePanelView): Int = (ArenaView::class.java.getDeclaredField("xpActive").apply { isAccessible = true }.get(p.arena) as List<*>).size

    @Test fun teamCardsDoNotStackAndTrainerMessageWaitsForTheCardToLeave() {
        val p = panel()
        p.arena.showXpCard(xp) {}
        assertEquals(1, active(p))
        p.arena.showXpCard(xp.copy(uid = "other")) {}
        assertEquals(1, active(p))
        p.toast(ToastKind.TRAINER, "Trainer level 2!")
        assertNull(p.toastLayer.shownCard)
        assertTrue(p.toastLayer.hasMessages)
        // Cancelling the arena's animations releases its card and presents the waiting toast.
        p.arena.cancelAnimations()
        assertEquals(ToastKind.TRAINER, p.toastLayer.shown?.kind)
        assertEquals(0, active(p))
        paparazzi.snapshot(p, "r23m-trainer-after-xp")
    }

    @Test fun teamCardWaitsForTrainerToastToDisappear() {
        val p = panel()
        p.toast(ToastKind.TRAINER, "Trainer level 2!")
        p.arena.showXpCard(xp) {}
        assertEquals(0, active(p))
        p.toastLayer.finishNow()
        assertEquals(1, active(p))
    }

    @Test fun giftScreenIsQueuedWithoutADuplicateBattleAlert() {
        val p = panel()
        p.context.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", false).commit()
        val gift = GameEvent.DailyGift("potion", null, 2, 3)
        AchievementPopup.offer(listOf(gift))
        val player = EventPlayer(p)
        player.enqueue(listOf(gift))
        assertEquals(1, AchievementPopup.waitingGifts().size)
        assertNull(p.toastLayer.shown)
        player.clear()
        assertFalse(AchievementPopup.waitingForPlayback)
    }

    @Test fun toastKeepsClearOfBothPokemonConditionRows() {
        val p = panel()
        val battle = com.husarp.ankigo.GameRepo.state!!.battle!!
        battle.player.volatiles["confusion"] = 3
        battle.enemy.volatiles["confusion"] = 3
        p.arena.syncConditions(battle)
        p.measure(View.MeasureSpec.makeMeasureSpec(p.width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(p.height, View.MeasureSpec.EXACTLY))
        p.layout(0, 0, p.measuredWidth, p.measuredHeight)
        p.toast(ToastKind.TRAINER, "Trainer level 2!")
        val card = p.toastLayer.shownCard!!
        val lp = card.layoutParams as android.widget.FrameLayout.LayoutParams
        val rect = FloatSpot.Rect(lp.leftMargin - p.arena.left, lp.topMargin - p.arena.top,
            lp.leftMargin - p.arena.left + card.measuredWidth, lp.topMargin - p.arena.top + card.measuredHeight)
        val chips = p.arena.toastKeepOff().extra
        assertTrue(chips.isNotEmpty())
        assertTrue(chips.none { it.hits(rect) })
        paparazzi.snapshot(p, "r23m-top-right-with-both-condition-rows")
    }

    @Test fun finishingBattlePlaybackNaturallyReleasesPopupGate() {
        val p = panel()
        p.context.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", false).commit()
        val player = EventPlayer(p)
        player.enqueue(listOf(GameEvent.DailyGift("potion", null, 1, 1)))
        assertTrue(AchievementPopup.waitingForPlayback)
        paparazzi.snapshot(p, "r23m-battle-finished-before-gift-popup", 2000)
        assertTrue(player.idle)
        assertFalse(AchievementPopup.waitingForPlayback)
    }

    @Test fun clearingBattlePlaybackReleasesPopupGate() {
        val p = panel()
        p.context.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", false).commit()
        val player = EventPlayer(p)
        player.enqueue(listOf(GameEvent.DailyGift("potion", null, 1, 1)))
        assertTrue(AchievementPopup.waitingForPlayback)
        player.clear()
        assertFalse(AchievementPopup.waitingForPlayback)
    }
}
