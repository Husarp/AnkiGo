package com.husarp.ankigo.shots

import android.widget.LinearLayout
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.game.*
import com.husarp.ankigo.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class R21AShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    @Test fun detailsAndPopup() = paparazzi.shootWith("r21a-details", listOf(Size.PHONE)) { ctx ->
        val data = TestData.world()
        val mon = TestData.mon(data, 54, 25, listOf("tackle"), "ours")
        val stats = StatCalc.stats(data, mon)
        LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.c(ctx, com.husarp.ankigo.R.color.ag_base))
            addView(DetailSections.statsBody(ctx, stats, 25, Nature.ADAMANT, data.sp(54), false, intArrayOf(2, -2, 0, 0, 1, 0, 0)))
            addView(DetailSections.statsBody(ctx, stats, 25, Nature.HARDY, data.sp(54), false))
            addView(AppDialog.Builder(ctx).setTitle("Confirm action").setMessage("Main action first; Cancel below.")
                .setPositiveButton("Continue", null).setNegativeButton("Cancel", null).create().content())
        }
    }
    @Test fun xpVisibilityAndStages() = paparazzi.shootWith("r21a-battle", listOf(Size.PHONE)) { ctx ->
        FakeGame.install(ctx)
        ctx.getSharedPreferences("study", 0).edit().putBoolean("alwaysShowXpBar", false).commit()
        val panel = BattlePanelView(ctx)
        val b = com.husarp.ankigo.GameRepo.state!!.battle!!
        b.player.boosts[0] = -2; b.enemy.boosts[4] = 2
        panel.renderAll()
        val xp = getField(panel.arena, "pXp") as android.view.View
        assertEquals(android.view.View.GONE, xp.visibility)
        ctx.getSharedPreferences("study", 0).edit().putBoolean("alwaysShowXpBar", true).commit()
        panel.arena.refreshXpVisibility()
        assertEquals(android.view.View.VISIBLE, xp.visibility)
        ctx.getSharedPreferences("study", 0).edit().putBoolean("alwaysShowXpBar", false).commit()
        panel.arena.setPlayerXp(10, 100, true)
        assertEquals(android.view.View.VISIBLE, xp.visibility)
        panel.arena.cancelAnimations()
        assertEquals(android.view.View.GONE, xp.visibility)
        panel.arena.setPlayerXp(20, 100, true)
        assertEquals(android.view.View.VISIBLE, xp.visibility)
        panel.arena.resetFx()
        assertEquals(android.view.View.GONE, xp.visibility)
        panel
    }

    @Test fun frameChoices() = paparazzi.shootWith("r21a-frames", listOf(Size.PHONE)) { ctx ->
        LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            for (style in listOf(BattleFrameStyle.SIMPLE, BattleFrameStyle.AUTOMATIC, BattleFrameStyle.SEA)) {
                addView(BattleFramePreview(ctx, style), LinearLayout.LayoutParams(-1, Ui.dp(ctx, 100)))
            }
        }
    }

    @Test fun simpleFollowsElementAutomaticFollowsSceneryAndWavesKeepTheirPixels() {
        for (style in listOf(BattleFrameStyle.SIMPLE)) {
            val bitmap = android.graphics.Bitmap.createBitmap(40, 40, android.graphics.Bitmap.Config.ARGB_8888)
            val frame = BattleFrameDrawable(style, 2)
            frame.element = "water"; frame.setBounds(0, 0, 40, 40)
            frame.draw(android.graphics.Canvas(bitmap))
            assertEquals(0xFF0B3B5C.toInt(), bitmap.getPixel(20, 0))
            assertEquals(0xFFBFE9FF.toInt(), bitmap.getPixel(20, 2))
            frame.element = "fire"; frame.draw(android.graphics.Canvas(bitmap))
            assertEquals(0xFF2A0C08.toInt(), bitmap.getPixel(20, 0))
            assertEquals(0xFFFFD43B.toInt(), bitmap.getPixel(20, 2))
        }
        val bitmap = android.graphics.Bitmap.createBitmap(40, 40, android.graphics.Bitmap.Config.ARGB_8888)
        val automatic = BattleFrameDrawable(BattleFrameStyle.AUTOMATIC, 2).apply {
            scenery = "beach"; element = "water"; setBounds(0, 0, 40, 40)
        }
        automatic.draw(android.graphics.Canvas(bitmap))
        assertEquals(0xFFAB9264.toInt(), bitmap.getPixel(20, 0))
        assertEquals(0xFFF0DAA3.toInt(), bitmap.getPixel(20, 2))
        assertEquals(0xFF78694B.toInt(), bitmap.getPixel(20, 4))
        automatic.element = "fire"
        automatic.draw(android.graphics.Canvas(bitmap))
        assertEquals(0xFFAB9264.toInt(), bitmap.getPixel(20, 0)) // actual scenery wins over encounter element
        automatic.scenery = "lava-field"
        automatic.draw(android.graphics.Canvas(bitmap))
        assertEquals(0xFF59404B.toInt(), bitmap.getPixel(20, 0))
        assertEquals(0xFFB17B65.toInt(), bitmap.getPixel(20, 2))
        assertEquals(0xFF392B35.toInt(), bitmap.getPixel(20, 4))
        assertEquals(0xFF0B3B5C.toInt(), BattleFrameStyle.SEA.edge(0, 0, 't'))
        assertEquals(0xFFFFFFFF.toInt(), BattleFrameStyle.SEA.edge(0, 1, 't'))
        assertEquals(0xFF8FD3F4.toInt(), BattleFrameStyle.SEA.edge(0, 1, 'b'))
        assertEquals(0, BattleFrameStyle.SEA.cornerAt(7, 7))
    }

    @Test fun popupBlocks() = paparazzi.shootWith("r21a-popup", listOf(Size.PHONE, Size.SPLIT)) { ctx ->
        val dialog = AppDialog.Builder(ctx).setTitle("Confirm action").setMessage("Choose an action.")
            .setPositiveButton("Continue", null).setNeutralButton("Another action", null).setNegativeButton("Cancel", null).create()
        android.widget.FrameLayout(ctx).apply {
            setPadding(Ui.dp(ctx, 16), 0, Ui.dp(ctx, 16), 0)
            setBackgroundColor(Ui.c(ctx, com.husarp.ankigo.R.color.ag_base))
            addView(dialog.content(), android.widget.FrameLayout.LayoutParams(-1, -2, android.view.Gravity.CENTER))
        }
    }

    @Test fun allStagePillsFitTheDetailsHeader() = paparazzi.shootWith("r21a-stage-header", listOf(Size.PHONE, Size.SPLIT)) { ctx ->
        FakeGame.install(ctx)
        val data = com.husarp.ankigo.GameRepo.data!!
        val mon = com.husarp.ankigo.GameRepo.state!!.main!!
        val boosts = intArrayOf(2, -2, 1, -1, 3, -3, 6)
        DetailSections.section(ctx, DetailSections.memory(ctx), DetailSection.STATS, null, titleLevel = mon.level, boosts = boosts) {
            DetailSections.statsBody(ctx, StatCalc.stats(data, mon), mon.level, mon.nature, data.sp(mon.species), false, boosts)
        }
    }

    @Test fun backgroundChangeRetintsTheFrameWithoutChangingPanelSize() = paparazzi.shootWith("r21a-frame-retint", listOf(Size.PHONE)) { ctx ->
        FakeGame.install(ctx)
        AppPrefs.setBattleFrame(ctx, BattleFrameStyle.AUTOMATIC)
        val panel = BattlePanelView(ctx)
        panel.renderAll()
        val widthSpec = android.view.View.MeasureSpec.makeMeasureSpec(Ui.dp(ctx, 412), android.view.View.MeasureSpec.EXACTLY)
        val heightSpec = android.view.View.MeasureSpec.makeMeasureSpec(Ui.dp(ctx, 869), android.view.View.MeasureSpec.EXACTLY)
        fun layout() { panel.requestLayout(); panel.measure(widthSpec, heightSpec); panel.layout(0, 0, panel.measuredWidth, panel.measuredHeight) }
        layout()
        val backdrop = getField(panel.arena, "backdrop")!!
        setField(backdrop, "shown", BackdropCatalog.byId("beach"))
        layout()
        val frame = getField(panel, "frameDrawable") as BattleFrameDrawable
        assertEquals("water", frame.element)
        setField(backdrop, "shown", BackdropCatalog.byId("fire-savanna"))
        layout()
        assertEquals("fire", frame.element)
        panel
    }

}
