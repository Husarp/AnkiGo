package com.husarp.ankigo.shots

import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.ui.*
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*

/** PLAN 163 / R19: the real Study panel and look chooser, all picks in both themes. */
class R19OHpColourShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    @Test fun allColours() {
        for (theme in Theme.entries) for (mode in HpColors.Mode.entries) for (hp in listOf(90,45,15)) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.SPLIT,theme))
            val ctx = Shots.files(paparazzi.context)
            FakeGame.install(ctx)
            HpColorPrefs.set(ctx,mode)
            AppPrefs.setInfoBoxStyle(ctx,InfoBoxStyle.INSIDE)
            AppPrefs.setInfoBoxLook(ctx,InfoBoxLook.TODAY)
            val panel = BattlePanelView(ctx).apply { setStudyLayout(false,false); renderAll() }
            panel.arena.setEnemyHp(hp,100,false); panel.arena.setPlayerHp(hp,100,false)
            paparazzi.snapshot(panel,"r19o-${mode.name.lowercase()}-$hp-${theme.name.lowercase()}")
        }
    }
    @Test fun arenaFollowsTheSettingLive() {
        val ctx = Shots.files(paparazzi.context)
        FakeGame.install(ctx)
        val arena = ArenaView(ctx)
        arena.setPlayerTypes(listOf(com.husarp.ankigo.game.Type.WATER))
        arena.setPlayerHp(90,100,false)
        val field = ArenaView::class.java.getDeclaredField("pHp").apply { isAccessible = true }
        val bar = field.get(arena)
        val color = BarView::class.java.getDeclaredField("color").apply { isAccessible = true }
        for (mode in HpColors.Mode.entries) {
            HpColorPrefs.set(ctx,mode)
            assertEquals(HpColors.color(.9f,mode,TypeChips.isDark(ctx),Ui.typeColor(com.husarp.ankigo.game.Type.WATER)),color.getInt(bar))
        }
    }
    @Test fun settingsRowAndPreferences() {
        for (theme in Theme.entries) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.SPLIT,theme))
            val ctx = Shots.files(paparazzi.context)
            ctx.getSharedPreferences("ankigo_ui",0).edit().remove(HpColorPrefs.KEY).apply()
            assertEquals(HpColors.Mode.B,HpColorPrefs.mode(ctx))
            ctx.getSharedPreferences("ankigo_ui",0).edit().putInt(HpColorPrefs.KEY,0).apply()
            assertEquals(HpColors.Mode.B,HpColorPrefs.mode(ctx))
            HpColorPrefs.set(ctx,HpColors.Mode.B)
            val row = HpColorPrefs.row(ctx,setOf("HP bar colours").toMutableSet())
            paparazzi.snapshot(row,"r19o-settings-${theme.name.lowercase()}")
        }
    }
}
