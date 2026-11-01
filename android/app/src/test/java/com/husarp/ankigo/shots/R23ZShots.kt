package com.husarp.ankigo.shots

import android.graphics.drawable.BitmapDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.R
import com.husarp.ankigo.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Selected designs: PLAN 359 B and 341 C. */
class R23ZShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    private fun views(v: View): List<View> = listOf(v) +
        if (v is ViewGroup) (0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList()

    @Test fun detailsBackHeader() {
        paparazzi.shoot("r23z-details", listOf(Size.PHONE, Size.SMALL)) { ctx ->
            FakeGame.install(ctx)
            val d = DialogShots.capture {
                MonDetail.show(TeamPage(ctx, NoHost), FakeGame.data, com.husarp.ankigo.GameRepo.state!!.mons.first())
            } ?: error("No details")
            DialogShots.scene(ctx, d).also { scene ->
                val back = views(scene).single { it.contentDescription == "Back to previous screen" }
                assertEquals(Ui.dp(ctx, 48), back.layoutParams.width)
                assertEquals(Ui.dp(ctx, 48), back.layoutParams.height)
                val labels = views(scene).filterIsInstance<TextView>().map { it.text.toString() }
                assertTrue("Details" in labels)
                assertTrue("Options" in labels)
                assertTrue("Close" in labels)
                assertTrue(back.isClickable)
                val heading = views(scene).filterIsInstance<TextView>().single { it.text.toString() == "Details" }
                assertSame(back.parent, heading.parent)
                var ancestor = back.parent
                while (ancestor is View) {
                    assertFalse("Back stays outside the scroller", ancestor is android.widget.ScrollView)
                    ancestor = ancestor.parent
                }
            }
        }
    }

    @Test fun coinAtDisplaySizesAndInMedals() {
        paparazzi.shootWith("r23z-coin", listOf(Size.SMALL)) { ctx ->
            android.widget.LinearLayout(ctx).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                setBackgroundColor(Ui.c(ctx, R.color.ag_base))
                setPadding(Ui.dp(ctx, 16), Ui.dp(ctx, 16), Ui.dp(ctx, 16), Ui.dp(ctx, 16))
                for (size in listOf(13, 14, 16)) {
                    addView(ShopViews.priceNormal(ctx, 1250, size.toFloat(), size))
                }
                for (tier in listOf(-1, 0, 4)) {
                    addView(AchMedal(ctx, 64).apply { set(R.drawable.ic_coin, tier) })
                }
            }
        }
    }

    @Test fun selectedCoinIsUnfilteredAndShared() {
        val ctx = Shots.files(paparazzi.context)
        val money = ctx.getDrawable(R.drawable.ic_money) as BitmapDrawable
        val coin = ctx.getDrawable(R.drawable.ic_coin) as BitmapDrawable
        assertFalse(money.paint.isFilterBitmap)
        assertFalse(coin.paint.isFilterBitmap)
        assertEquals(12, money.bitmap.width)
        assertEquals(13, money.bitmap.height)
        assertTrue(money.bitmap.sameAs(coin.bitmap))
        assertNull(money.colorFilter)
    }
}
