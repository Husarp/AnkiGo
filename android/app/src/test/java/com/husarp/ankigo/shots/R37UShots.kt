package com.husarp.ankigo.shots

import android.content.Context
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.data.Sprites
import com.husarp.ankigo.game.*
import com.husarp.ankigo.ui.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import kotlin.random.Random

class R37UShots {
    @get:Rule val paparazzi = Shots.rule()
    private val pages = mutableListOf<GamePage>()
    @After fun clean() { pages.forEach { it.onGone() }; ItemWindow.interceptShow = null; BattleShotKit.reset() }
    private fun setup(theme: Theme): Context {
        paparazzi.unsafeUpdateConfig(deviceConfig = Variant("p412", 412, 892).device(theme))
        val ctx = Shots.files(paparazzi.context)
        FakeGame.scenario = FakeGame.Scenario.MID_GAME; FakeGame.withSprites = true
        FakeGame.install(ctx); ShopCardKit.install()
        File("/tmp/r37u-item-icons").takeIf { it.isDirectory }?.listFiles()?.forEach { source ->
            val target = File(Sprites.dir(ctx), "item/${source.name}"); target.parentFile!!.mkdirs(); source.copyTo(target, overwrite = true)
        }
        val worker = Thread { for (id in listOf("berry-juice", "sweet-heart", "soda-pop")) Sprites.itemBitmap(ctx, id) }
        worker.start(); worker.join()
        return ctx
    }
    @Test fun threeItemWindowsInLightAndDark() {
        for (theme in Theme.entries) for (item in listOf("berry-juice", "sweet-heart", "soda-pop")) {
            val ctx = setup(theme)
            val (page, stage) = ShopCardKit.page(ctx, CardKeys.bagItem(item), bag = true, setup = {
                GameRepo.state!!.bag.clear(); GameRepo.state!!.bag[item] = 3
            })
            pages += page
            val text = ShopCardKit.texts(stage).joinToString(" ")
            assertTrue(text, text.contains("Outside battle"))
            assertTrue(text, text.contains(if (item == "soda-pop") "×1.25" else if (item == "sweet-heart") "70%" else "50% total"))
            paparazzi.snapshot(stage, "u-item-$item-${theme.name.lowercase()}", 300)
        }
    }
    @Test fun everyNewConditionIconInLightAndDarkOnTheRealBattleScreen() {
        val items = HealingItems.boosts.keys + listOf("berry-juice", "sweet-heart")
        for (theme in Theme.entries) for (item in items) {
            val ctx = setup(theme); val s = GameRepo.state!!; val mon = s.main!!
            mon.hp = 1; Economy.give(s, item)
            assertTrue(ItemUse.use(GameRepo.data!!, s, item, mon.uid, Random(0), 0).ok)
            val key = HealingItems.boosts[item] ?: if (item == "berry-juice") "heal.regen" else "heal.pending"
            val board = ConditionBoard(); board.syncFrom(s.battle)
            assertTrue(board.of(ConditionMarks.Slot.OURS).any { it.key == key })
            val screen = BattleShotKit.bound(ctx)
            paparazzi.snapshot(screen.root, "u-status-${key.replace('.', '-')}-${theme.name.lowercase()}", 300)
            assertTrue(screen.arena!!.conditions.of(ConditionMarks.Slot.OURS).any { it.key == key })
        }
    }
}
