package com.husarp.ankigo.shots

import android.widget.LinearLayout
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.R
import com.husarp.ankigo.ui.CoinText
import com.husarp.ankigo.ui.Bits
import com.husarp.ankigo.ui.Ui
import org.junit.Rule
import org.junit.Test

class R25bCoinShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    @Test fun enlargedInlineCoinsKeepReleasedArtworkProportionsAndLineRoom() {
        val ctx = Shots.files(paparazzi.context)
        val paint = android.graphics.Paint().apply { textSize = 40f }
        val text = CoinText.of(ctx, 40f, "+125") as android.text.Spanned
        val span = text.getSpans(0, 1, android.text.style.DynamicDrawableSpan::class.java).single()
        val fm = android.graphics.Paint.FontMetricsInt()
        val advance = span.getSize(paint, text, 0, 1, fm)
        val drawable = span.drawable
        val bounds = drawable.bounds
        org.junit.Assert.assertTrue(bounds.height() > paint.textSize)
        org.junit.Assert.assertEquals(drawable.intrinsicWidth.toFloat() / drawable.intrinsicHeight,
            bounds.width().toFloat() / bounds.height(), .03f)
        org.junit.Assert.assertTrue(advance > bounds.width())
        org.junit.Assert.assertTrue(fm.descent - fm.ascent >= bounds.height())
    }

    @Test fun chipsInlineRewardsAndMoneyAnimationAmounts() {
        for (theme in Theme.entries) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.PHONE, theme))
            val ctx = Shots.files(paparazzi.context)
            val body = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(Ui.dp(ctx, 20), Ui.dp(ctx, 20), Ui.dp(ctx, 20), Ui.dp(ctx, 20))
                setBackgroundColor(Ui.c(ctx, R.color.ag_card))
                addView(Ui.chip(ctx, R.drawable.ic_money, R.color.ag_yellow, plain = true).apply { setText("12,345") })
                addView(Bits.buyButton(ctx, 125, true) {})
                for (sp in listOf(11f, 14f, 20f)) addView(Ui.text(ctx, sp, 900, R.color.ag_yellow).apply { text = CoinText.of(this, "+125") })
            }
            paparazzi.snapshot(body, "r25b-coins-${theme.name.lowercase()}")
        }
    }
}
