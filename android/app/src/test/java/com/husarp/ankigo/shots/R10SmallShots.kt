package com.husarp.ankigo.shots

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.Anki
import com.husarp.ankigo.R
import com.husarp.ankigo.stats.AnkiStatsStore
import com.husarp.ankigo.stats.StatsSnapshot
import com.husarp.ankigo.ui.Chip
import com.husarp.ankigo.ui.ContinueBar
import com.husarp.ankigo.ui.GamePage
import com.husarp.ankigo.ui.PageHost
import com.husarp.ankigo.ui.PickKit
import com.husarp.ankigo.ui.StatsPage
import com.husarp.ankigo.ui.Ui
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/*
 * R10-A (B2, B6, B15, B17): the Continue bar's play button is a rounded square, a ticked Compare badge is the same square as the
 * empty one, the Study deck rows have no AnkiDroid icon (the long-press menu keeps "Study in AnkiDroid"), and the Stats trainer /
 * streak chips (coins, Achievements, Best) sit straight on the card. Shots: r10small-*.
 */
class R10SmallShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    @Before fun storeOff() { AnkiStatsStore.enabled = false; AnkiStatsStore.snapshot = StatsSnapshot() }
    @After fun storeOn() { AnkiStatsStore.enabled = true; AnkiStatsStore.snapshot = StatsSnapshot() }

    private fun all(v: View, out: MutableList<View> = ArrayList()): List<View> {
        out += v
        if (v is ViewGroup) for (i in 0 until v.childCount) all(v.getChildAt(i), out)
        return out
    }

    // ------------------------------------------------------------------ B2

    @Test fun continueBarPlayButtonIsARoundedSquare() {
        val deck = Anki.Deck(7, "Spanish::Verbs", 3, 12, 5, false)
        paparazzi.shootWith("r10small-continue") { ctx ->
            val bar = ContinueBar.build(ctx, deck, {}, {})
            val play = all(bar).filterIsInstance<ImageView>().first { it.background is GradientDrawable && it.layoutParams.width == Ui.dp(ctx, 32) }
            val r = (play.background as GradientDrawable).cornerRadius
            assertEquals(Ui.dp(ctx, 10).toFloat(), r, 0.5f)
            assertTrue("not a circle", r < Ui.dp(ctx, 32) / 2f)
            FrameLayout(ctx).apply {
                setBackgroundColor(Ui.c(ctx, R.color.ag_base))
                addView(bar, FrameLayout.LayoutParams(-1, -2))
            }
        }
    }

    // ------------------------------------------------------------------ B6

    @Test fun tickedBadgeIsTheSameSquareAsTheEmptyOne() {
        paparazzi.shootWith("r10small-badges") { ctx ->
            val on = PickKit.badge(ctx); val off = PickKit.badge(ctx)
            PickKit.bindBadge(on, true, true); PickKit.bindBadge(off, true, false)
            val bg = (on.background as InsetDrawable).drawable as GradientDrawable
            assertTrue("rounded square, not a disc", bg.cornerRadius < Ui.dp(ctx, 26) / 4f)
            assertNull(off.background)
            val row = LinearLayout(ctx).apply {
                setBackgroundColor(Ui.c(ctx, R.color.ag_base)); setPadding(40, 40, 40, 40)
                addView(off, LinearLayout.LayoutParams(Ui.dp(ctx, 26), Ui.dp(ctx, 26)).also { it.marginEnd = 20 })
                addView(on, LinearLayout.LayoutParams(Ui.dp(ctx, 26), Ui.dp(ctx, 26)))
            }
            // same view box: both badges are laid out at the same size
            row.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.AT_MOST))
            assertEquals(off.measuredWidth, on.measuredWidth); assertEquals(off.measuredHeight, on.measuredHeight)
            row
        }
    }

    // ------------------------------------------------------------------ B15

    @Test fun deckRowsHaveNoAnkiDroidButtonButTheMenuKeepsIt() {
        paparazzi.shootWith("r10small-decks") { ctx ->
            val root = StudyDeckKit.build(ctx)
            val bad = all(root).filter { it.contentDescription?.toString()?.endsWith("in AnkiDroid") == true }
            assertTrue("no companion button: $bad", bad.isEmpty())
            root
        }
        for (dyn in listOf(false, true)) for (granted in listOf(true, false)) {
            val ss = ShotCtx(paparazzi.context, granted = granted)
            val home = StudyDeckKit.compose(ss).home
            val m = MenuKit.capture { home.deckMenu(Anki.Deck(3, "Leeches", 1, 2, 0, dyn), android.view.View(ss)) }
            assertNotNull(m)
            assertTrue(m!!.describe().any { it.second == "Study in AnkiDroid" })
        }
    }

    // ------------------------------------------------------------------ B17

    private class ActivityHost(val activity: Activity) : PageHost {
        override val hostActivity: Activity get() = activity
        override fun closePage() = Unit
        override fun startForResult(intent: Intent, onResult: (Int, Intent?) -> Unit) = Unit
    }

    @Test fun statsChipsHaveNoBox() {
        paparazzi.shootPage("r10small-stats") { ctx ->
            val page: GamePage = StatsPage(ctx, ActivityHost(attach(Activity(), ctx)))
            val screen = pageScreen(ctx, page)
            call(page, "render") // the page renders from a posted callback; do it now so the walk below sees the chips
            val chips = all(screen).filterIsInstance<Chip>()
            val plain = chips.filter { val t = it.label.text.toString(); t.startsWith("Achievements") }
            assertEquals(1, plain.size)
            plain.forEach { assertNull(it.background) }
            // the streak card's "Best N" is now a StreakBest IconLabel (no Chip, no box)
            val best = all(screen).filterIsInstance<com.husarp.ankigo.ui.IconLabel>().filter { it.label.text.toString().startsWith("Best") || it.label.text.toString().startsWith("New best") }
            assertEquals(1, best.size)
            assertNull(best[0].background)
            assertTrue("coins chip too", chips.count { it.background == null } >= 2)
            screen
        }
    }
}
