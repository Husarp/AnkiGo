package com.husarp.ankigo.shots

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.ContextThemeWrapper
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.R
import com.husarp.ankigo.ui.TapWatchScroll
import com.husarp.ankigo.ui.Ui
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class R24FScrollbarShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    @Test fun legacyThemeAlsoUsesTheSharedThumb() {
        val ctx = ContextThemeWrapper(paparazzi.context, R.style.AppTheme)
        val attrs = ctx.obtainStyledAttributes(intArrayOf(
            android.R.attr.scrollbarThumbVertical, android.R.attr.scrollbarThumbHorizontal))
        try {
            for (i in 0..1) assertEquals(R.drawable.game_scrollbar_thumb, attrs.getResourceId(i, 0))
        } finally { attrs.recycle() }
        assertEquals(Ui.dp(ctx, 3), ScrollView(ctx).scrollBarSize)
    }

    @Test fun sharedThumbsOnBothAxesAndCustomScrollersInBothThemes() {
        paparazzi.shoot("r24f-scrollbars", listOf(Size.SPLIT), offsetMillis = 0) { ctx ->
            val attrs = ctx.obtainStyledAttributes(intArrayOf(
                android.R.attr.scrollbarThumbVertical, android.R.attr.scrollbarThumbHorizontal,
                android.R.attr.scrollbarSize))
            try {
                for (i in 0..1) {
                    assertEquals(R.drawable.game_scrollbar_thumb, attrs.getResourceId(i, 0))
                    val thumb = attrs.getDrawable(i)!!
                    val bitmap = Bitmap.createBitmap(12, 60, Bitmap.Config.ARGB_8888)
                    thumb.setBounds(0, 0, 12, 60)
                    thumb.draw(Canvas(bitmap))
                    assertEquals(ctx.getColor(R.color.ag_mauve), bitmap.getPixel(6, 30))
                    assertEquals("rounded corner", 0, bitmap.getPixel(0, 0))
                }
                assertEquals(Ui.dp(ctx, 3), attrs.getDimensionPixelSize(2, 0))
            } finally { attrs.recycle() }
            LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Ui.c(ctx, R.color.ag_base))
                for (custom in listOf(false, true)) {
                    val scroll = if (custom) TapWatchScroll(ctx) else ScrollView(ctx)
                    scroll.isScrollbarFadingEnabled = false
                    scroll.addView(LinearLayout(ctx).apply {
                        orientation = LinearLayout.VERTICAL
                        repeat(25) { n -> addView(Ui.text(ctx, 14f, 700, R.color.ag_text).apply {
                            text = "${if (custom) "Details" else "Page"} row ${n + 1}"
                            setPadding(24, 12, 24, 12)
                        }) }
                    })
                    assertEquals(Ui.dp(ctx, 3), scroll.scrollBarSize)
                    addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
                }
                addView(HorizontalScrollView(ctx).apply {
                    isHorizontalScrollBarEnabled = true
                    isScrollbarFadingEnabled = false
                    addView(TextView(ctx).apply {
                        text = "Horizontal scrolling  •  ".repeat(30)
                        setTextColor(Ui.c(ctx, R.color.ag_text))
                        setPadding(24, 20, 24, 24)
                        setSingleLine(true)
                    })
                }, LinearLayout.LayoutParams(-1, Ui.dp(ctx, 64)))
            }
        }
    }
}
