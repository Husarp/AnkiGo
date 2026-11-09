package com.husarp.ankigo.shots

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.VectorDrawable
import android.widget.ImageView
import android.widget.LinearLayout
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.R
import com.husarp.ankigo.game.Achievements
import com.husarp.ankigo.help.*
import com.husarp.ankigo.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class R32SShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    @Test fun sharedStackHasTransparentInteriorsAndCoinSeparators() {
        paparazzi.shootWith("s-achievement-money", listOf(Size.PHONE)) { ctx ->
            assertEquals(AchStyle.icon("coin"), HelpViews.chapterIcon(GameGuide.MONEY))
            assertEquals(AchStyle.icon("coin"), SubtabIcons.achievement("Economy"))
            val drawable = ctx.getDrawable(AchStyle.icon("coin"))!!
            assertTrue(drawable is VectorDrawable)
            val bitmap = Bitmap.createBitmap(240, 240, Bitmap.Config.ARGB_8888)
            drawable.setBounds(0, 0, 240, 240); drawable.draw(Canvas(bitmap))
            for ((x, y) in listOf(85 to 50, 85 to 90, 85 to 140, 85 to 185, 170 to 145, 170 to 185, 110 to 130)) {
                assertEquals("Transparent interior at $x,$y (no hidden rear strokes)", 0, Color.alpha(bitmap.getPixel(x, y)))
            }
            for ((x, y) in listOf(35 to 120, 85 to 117, 85 to 163, 85 to 210, 155 to 170, 155 to 210, 205 to 140)) {
                assertTrue("Outline / coin separator at $x,$y", Color.alpha(bitmap.getPixel(x, y)) > 0)
            }
            FakeGame.install(ctx)
            val page = StatsPage(ctx, NoHost, StatsSection.ACHIEVEMENTS)
            LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(Ui.dp(ctx, 16), Ui.dp(ctx, 16), Ui.dp(ctx, 16), Ui.dp(ctx, 16))
                setBackgroundColor(Ui.c(ctx, R.color.ag_card))
                addView(LinearLayout(ctx).apply {
                    for (size in listOf(16, 20, 24)) addView(ImageView(ctx).apply {
                        setImageResource(AchStyle.icon("coin")); setColorFilter(Ui.c(ctx, R.color.ag_green))
                    }, LinearLayout.LayoutParams(Ui.dp(ctx, size), Ui.dp(ctx, size)))
                })
                for (tier in -1..4) addView(AchMedal(ctx, 48).apply { set(AchStyle.icon("coin"), tier) })
                addView(page.achievementsView().tile(Achievements.view(GameRepo.state!!, page.data).first { it.icon == "coin" }))
            }
        }
    }

    @Test fun guideMoneySection() {
        paparazzi.shootWith("s-guide-money", listOf(Size.PHONE)) { ctx ->
            val activity = attach(Activity(), ctx)
            val host = object : PageHost {
                override val hostActivity: Activity get() = activity
                override fun closePage() = Unit
                override fun startForResult(intent: Intent, onResult: (Int, Intent?) -> Unit) = Unit
            }
            pageScreen(ctx, HelpScreen(ctx, host, HelpRoute.Guide(GameGuide.MONEY), false))
        }
    }

    @Test fun shopKeepsGoldPricesAndBalance() {
        paparazzi.shootWith("s-shop-row", listOf(Size.PHONE)) { ctx ->
            FakeGame.install(ctx); ShopCardKit.install()
            val (page, stage) = ShopCardKit.page(ctx, null, cash = 20_000)
            page.onHide()
            assertTrue(ctx.getDrawable(R.drawable.ic_money) is android.graphics.drawable.BitmapDrawable)
            assertTrue(ctx.getDrawable(R.drawable.coin_stack) is android.graphics.drawable.BitmapDrawable)
            stage
        }
    }
}
