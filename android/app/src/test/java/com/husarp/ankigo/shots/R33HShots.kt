package com.husarp.ankigo.shots

import android.widget.FrameLayout
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.game.*
import com.husarp.ankigo.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class R33HShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    @Test fun dollInfo() {
        paparazzi.shootWith("h-item-info", listOf(Size.PHONE)) { ctx ->
            FakeGame.install(ctx)
            ShopCardKit.install()
            val (page, stage) = ShopCardKit.page(ctx, CardKeys.bagItem(PokeDoll.ITEM), bag = true, setup = {
                Economy.give(GameRepo.state!!, PokeDoll.ITEM, 30)
            })
            val text = ShopCardKit.texts(stage).joinToString(" ")
            assertTrue(text.contains("Level 4", ignoreCase = true)); assertTrue(text.contains("30 / 50"))
            assertTrue(text.contains("Next level", ignoreCase = true)); assertTrue(text.contains("35 min"))
            page.onHide()
            stage
        }
    }

    @Test fun bagUseShowsLockAndCooldownAboveTheOpenItemWindow() {
        val ctx = Shots.files(paparazzi.context)
        FakeGame.install(ctx)
        ctx.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", true).apply()
        ShopCardKit.install()
        val (page, stage) = ShopCardKit.page(ctx, CardKeys.bagItem(PokeDoll.ITEM), bag = true, setup = {
            Economy.give(GameRepo.state!!, PokeDoll.ITEM, 50)
        })
        val game = GameRepo.game!!
        game.escape(PokeDoll.ITEM)
        val battle = game.state.battle
        val action = BagActions(page, MonActions(page), page).spec(CardKeys.bagItem(PokeDoll.ITEM))!!.act!!
        assertTrue(action.enabled)
        action.onTap()
        assertTrue(ShopCardKit.texts(stage).contains(PokeDoll.LOCKED))
        assertSame(battle, game.state.battle)
        PokeDoll.won(game.state, game.clock())
        action.onTap()
        assertTrue(ShopCardKit.texts(stage).contains("Poké Doll: 30 min left"))
        assertSame(battle, game.state.battle)
        page.onGone()
    }

    @Test fun lockedBottomAlert() {
        paparazzi.shootWith("h-locked-alert", listOf(Size.PHONE)) { ctx ->
            FakeGame.install(ctx)
            ctx.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", true).apply()
            val state = GameRepo.state!!
            Economy.give(state, PokeDoll.ITEM, 50)
            GameRepo.game!!.escape(PokeDoll.ITEM)
            val text = GameRepo.game!!.escape(PokeDoll.ITEM).filterIsInstance<GameEvent.Info>().single().text
            assertEquals(PokeDoll.LOCKED, text)
            FrameLayout(ctx).apply {
                addView(BattlePanelView(ctx).apply { renderAll() }, FrameLayout.LayoutParams(-1, -1))
                AppToast.show(this, text)
            }
        }
    }
}
