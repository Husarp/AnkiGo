package com.husarp.ankigo.shots

import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.game.TestData
import com.husarp.ankigo.ui.*
import org.junit.Rule
import org.junit.Test

/** PLAN 282 / R19: real six-column Box, singles, two and five copies, both themes at 360 dp. */
class R19QDupesShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    private fun shot(name: String, a: Boolean, c: Boolean) = paparazzi.shootPage(
        "r19q-$name", listOf(Variant("360", 360, 780), Variant("six-columns", 840, 1000)),
    ) { ctx ->
        org.junit.Assert.assertTrue(ctx.resources.configuration.screenWidthDp in listOf(360, 840))
        BoxDupePrefs.setStacked(ctx, a); BoxDupePrefs.setTwo(ctx, c)
        val data = GameRepo.data!!; val state = GameRepo.state!!
        state.mons.clear(); state.team.clear(); state.viewedMons.clear()
        val species = listOf(25, 16, 19, 1, 2, 3, 26, 54, 92, 133, 134, 135)
        species.forEachIndexed { index, sp ->
            val count = when (index % 3) { 0 -> 1; 1 -> 2; else -> 5 }
            repeat(count) { copy ->
                state.mons += TestData.mon(data, sp, 100 - copy, listOf("tackle"), "q$index-$copy").also {
                    it.caughtAt = System.currentTimeMillis()
                    it.favourite = index % 3 == 1 && copy == 0
                }
            }
        }
        state.team += state.mons.first().uid
        val page = TeamPage(ctx, NoHost)
        page.preset(TeamSection.BOX, group = true)
        pageScreen(ctx, page)
    }
    @Test fun recycledTileResetsBothLooks() {
        val ctx = Shots.files(paparazzi.context)
        FakeGame.install(ctx)
        val data = GameRepo.data!!
        val frame = android.widget.FrameLayout(ctx)
        val sprite = TileSprite(ctx)
        frame.addView(sprite, android.widget.FrameLayout.LayoutParams(-1, -1).apply {
            topMargin = Ui.dp(ctx, 17); bottomMargin = Ui.dp(ctx, 14)
        })
        BoxViews.wrapTile(frame, sprite)
        for (a in listOf(true, false)) for (c in listOf(true, false)) for (n in listOf(5, 1, 2)) {
            BoxDupePrefs.setStacked(ctx, a); BoxDupePrefs.setTwo(ctx, c)
            val tile = BoxTile(List(n) { TestData.mon(data, 25, 100 - it, uid = "copy$it") })
            BoxViews.tileBackground(frame, PickKit.cardBg(ctx, 10, false))
            BoxViews.bindDupes(frame, sprite, tile, data)
            val size = Ui.dp(ctx, 80)
            val spec = android.view.View.MeasureSpec.makeMeasureSpec(size, android.view.View.MeasureSpec.EXACTLY)
            frame.measure(spec, spec); frame.layout(0, 0, size, size)
            val content = frame.getChildAt(0) as android.widget.FrameLayout
            val inset = if (a && n > 1) Ui.dp(ctx, 10) else 0
            val rear = content.getChildAt(0)
            if (c && n > 1) {
                org.junit.Assert.assertEquals(sprite.top, rear.top)
                org.junit.Assert.assertEquals(sprite.bottom, rear.bottom)
                val frontLeft = sprite.left + sprite.width * (1f - sprite.scaleX) / 2f + sprite.translationX
                val rearRight = rear.right - rear.width * (1f - rear.scaleX) / 2f + rear.translationX
                org.junit.Assert.assertTrue(frontLeft >= -0.01f)
                org.junit.Assert.assertTrue(rearRight <= content.width + 0.01f)
            }
            org.junit.Assert.assertEquals(size - inset - if (inset > 0) Ui.dp(ctx, 3) else 0, content.width)
            org.junit.Assert.assertEquals(size - inset - if (inset > 0) Ui.dp(ctx, 2) else 0, content.height)
            org.junit.Assert.assertEquals(if (c && n > 1) 60f / 88f else 1f, sprite.scaleX, 0.001f)
            org.junit.Assert.assertEquals(if (c && n > 1) android.view.View.VISIBLE else android.view.View.GONE, content.getChildAt(0).visibility)
        }
    }

    @Test fun compareOutlineSurvivesPreferenceRebind() {
        val ctx = Shots.files(paparazzi.context)
        FakeGame.install(ctx)
        val data = GameRepo.data!!
        val frame = android.widget.FrameLayout(ctx)
        val sprite = TileSprite(ctx)
        frame.addView(sprite, android.widget.FrameLayout.LayoutParams(-1, -1))
        BoxViews.wrapTile(frame, sprite)
        val tile = BoxTile(List(3) { TestData.mon(data, 25, 100, uid = "picked$it") })
        val picked = PickKit.cardBg(ctx, 10, true)
        BoxViews.tileBackground(frame, picked, true)
        for (stacked in listOf(true, false, true)) {
            BoxDupePrefs.setStacked(ctx, stacked)
            BoxViews.bindDupes(frame, sprite, tile, data)
            org.junit.Assert.assertSame(picked, frame.getChildAt(0).background)
            org.junit.Assert.assertEquals(stacked, frame.background != null)
        }
        BoxViews.tileBackground(frame, PickKit.cardBg(ctx, 10, false))
        BoxViews.bindDupes(frame, sprite, BoxTile(listOf(tile.best)), data)
        org.junit.Assert.assertNull(frame.background)
        val margins = frame.getChildAt(0).layoutParams as android.widget.FrameLayout.LayoutParams
        org.junit.Assert.assertEquals(listOf(0, 0, 0, 0), listOf(margins.leftMargin, margins.topMargin, margins.rightMargin, margins.bottomMargin))
    }

    @Test fun offOff() = shot("off-off", false, false)
    @Test fun stackedOnly() = shot("a-only", true, false)
    @Test fun twoOnly() = shot("c-only", false, true)
    @Test fun both() = shot("both", true, true)
}
