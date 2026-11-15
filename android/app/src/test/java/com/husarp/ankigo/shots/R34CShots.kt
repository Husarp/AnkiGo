package com.husarp.ankigo.shots

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.R
import com.husarp.ankigo.game.*
import com.husarp.ankigo.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.ZoneId

class R34CShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    private fun all(v: View): List<View> = listOf(v) + if (v is ViewGroup) (0 until v.childCount).flatMap { all(v.getChildAt(it)) } else emptyList()
    private fun layout(v: View) {
        val dm = v.resources.displayMetrics
        v.measure(View.MeasureSpec.makeMeasureSpec(dm.widthPixels, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(dm.heightPixels, View.MeasureSpec.EXACTLY))
        v.layout(0, 0, dm.widthPixels, dm.heightPixels)
    }
    private fun opaque(v: View) {
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        val bounds = android.graphics.Rect(v.background.bounds)
        v.background.setBounds(0, 0, 100, 100)
        v.background.draw(Canvas(bitmap))
        v.background.bounds = bounds
        assertEquals(255, Color.alpha(bitmap.getPixel(50, 50)))
        bitmap.recycle()
    }

    @Test fun useItemConfirmationAndFeedbackAreSolid() {
        paparazzi.shootPage("c-600-confirm", listOf(Variants.PHONE)) { ctx ->
            val dialog = DialogShots.capture { Pickers.hold(ctx, "Use Potion?", "Use Potion on Sparkmouse?", "Use") {} }!!
            opaque(dialog.content())
            DialogShots.scene(ctx, dialog)
        }
        paparazzi.shootPage("c-600-item-window", listOf(Variants.PHONE)) { ctx ->
            ShopCardKit.install()
            val (page, stage) = ShopCardKit.page(ctx, CardKeys.bagItem("potion"), bag = true)
            opaque((page.itemWindow!!.view as ViewGroup).getChildAt(0))
            stage
        }
        paparazzi.shootPage("c-600-feedback", listOf(Variants.PHONE)) { ctx ->
            ctx.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", false).commit()
            FrameLayout(ctx).apply {
                setBackgroundColor(Ui.c(ctx, R.color.ag_base))
                addView(BattleShotKit.bound(ctx).root, FrameLayout.LayoutParams(-1, -1))
                AppToast.show(this, "Used Potion on Sparkmouse.")
                val message = getChildAt(childCount - 1)
                assertEquals("item feedback must stay opaque during entrance", 1f, message.alpha, 0f)
                opaque(message)
            }
        }
    }

    @Test fun secretCategoryAppearsOnce() {
        paparazzi.shootPage("c-625-categories", listOf(Variants.PHONE)) { ctx ->
            val page = StatsPage(ctx, NoHost, StatsSection.ACHIEVEMENTS)
            pageScreen(ctx, page).also { screen ->
                call(page, "render")
                layout(screen)
                val strip = all(screen).filterIsInstance<HorizontalScrollView>().single()
                assertEquals(1, all(strip).filterIsInstance<TextView>().count { it.text.toString() == "Secret" })
                strip.scrollTo(strip.getChildAt(0).width, 0)
            }
        }
    }

    @Test fun earnedWithLineIsCenteredIncludingMissingHistoryAndLongNames() {
        for ((label, mons) in listOf("pokemon" to listOf(AchPokemon("one", "Sparkmouse", 25, false)),
            "missing" to emptyList(), "long" to listOf(AchPokemon("one", "A very long Pokémon nickname that wraps", 25, false)))) {
            paparazzi.shootPage("c-627-$label", listOf(Variants.PHONE)) { ctx ->
                val v = AchView("one-shot", "Secret", "One shot", "Win with a single hit", "battle", true, null,
                    0, "Earned", listOf(1_790_000_000_000L), 1, null, 1f, listOf(1L), pokemon = mons)
                val sheet = AchievementDetail.sheet(ctx, v, 0, ZoneId.of("UTC"))
                FrameLayout(ctx).apply {
                    setBackgroundColor(Ui.c(ctx, R.color.ag_base))
                    addView(sheet, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
                    layout(this)
                    val text = all(sheet).filterIsInstance<TextView>().single { it.text.startsWith("Earned with") }
                    val row = text.parent as LinearLayout
                    assertEquals(Gravity.CENTER, row.gravity)
                    assertEquals(Gravity.CENTER, text.gravity)
                    val start = row.getChildAt(0).left
                    val end = row.getChildAt(row.childCount - 1).right
                    assertTrue("witness group is centered", kotlin.math.abs(start + end - row.width) <= 2)
                }
            }
        }
    }

    @Test fun centeringGlidesAndRapidTapsContinueFromCurrentOffset() {
        paparazzi.shootPage("c-626-scroll", listOf(Variants.PHONE), themes = listOf(Theme.LIGHT)) { ctx ->
            ctx.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", false).commit()
            val row = LinearLayout(ctx)
            repeat(10) { i -> row.addView(Ui.text(ctx, 14f, 800, R.color.ag_text).apply { text = "Tab $i" }, LinearLayout.LayoutParams(Ui.dp(ctx, 100), Ui.dp(ctx, 48))) }
            val scroll = MotionHorizontalScrollView(ctx).apply { addView(row) }
            val frame = FrameLayout(ctx).apply { addView(scroll, FrameLayout.LayoutParams(-1, -2)); layout(this) }
            scroll.centerTab(row.getChildAt(5))
            assertEquals(0, scroll.scrollX)
            val first = getField(scroll, "centering") as android.animation.ValueAnimator
            assertEquals(250L, first.duration)
            first.currentPlayTime = 100
            val mid = scroll.scrollX
            assertTrue(mid > 0)
            scroll.centerTab(row.getChildAt(8))
            assertEquals("retargeting must not jump", mid, scroll.scrollX)
            val next = getField(scroll, "centering") as android.animation.ValueAnimator
            next.end()
            assertEquals((row.getChildAt(8).left + row.getChildAt(8).width / 2 - scroll.width / 2).coerceAtMost(row.width - scroll.width), scroll.scrollX)
            scroll.centerTab(row.getChildAt(0))
            (getField(scroll, "centering") as android.animation.ValueAnimator).end()
            assertEquals(0, scroll.scrollX)
            frame
        }
    }
}
