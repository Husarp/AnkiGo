package com.husarp.ankigo.shots

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import com.husarp.ankigo.Anki
import com.husarp.ankigo.R
import com.husarp.ankigo.ui.ContinueBar
import com.husarp.ankigo.ui.Ui
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/*
 * R14-G (PLAN 204): the small green Continue button in the full-screen Battle view's top bar (before Pop out), built by the
 * shared ContinueBar.button. The bar here mirrors GameActivity.buildTopBar. Shots: r14g-topbar-* (with a deck / without).
 */
class R14GBattleContinueShots {
    @get:Rule val paparazzi = Shots.rule()

    private val deck = Anki.Deck(7, "Spanish::Verbs", 3, 12, 5, false)

    private fun find(v: View, d: String): View? {
        if (v.contentDescription?.toString() == d) return v
        if (v is ViewGroup) for (i in 0 until v.childCount) find(v.getChildAt(i), d)?.let { return it }
        return null
    }

    private fun icon(ctx: Context, res: Int, desc: String, tone: Int = R.color.ag_sub1) = ImageView(ctx).apply {
        setImageResource(res)
        setColorFilter(Ui.c(ctx, tone), android.graphics.PorterDuff.Mode.SRC_IN)
        contentDescription = desc
        val p = Ui.dp(ctx, 12)
        setPadding(p, p, p, p)
        layoutParams = LinearLayout.LayoutParams(Ui.dp(ctx, 48), Ui.dp(ctx, 48))
    }

    private fun bar(ctx: Context, d: Anki.Deck?, taps: MutableList<Anki.Deck>): View = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = android.view.Gravity.CENTER_VERTICAL
        setBackgroundColor(Ui.c(ctx, R.color.ag_base))
        setPadding(Ui.dp(ctx, 4), Ui.dp(ctx, 4), Ui.dp(ctx, 8), Ui.dp(ctx, 4))
        addView(icon(ctx, R.drawable.ic_back, "Back", R.color.ag_text))
        addView(Ui.text(ctx, 18f, 900, R.color.ag_text).apply { text = "Battle"; maxLines = 1 }, LinearLayout.LayoutParams(0, -2, 1f))
        addView(ContinueBar.button(ctx).also { ContinueBar.bindButton(it, d) { x -> taps += x } })
        addView(icon(ctx, R.drawable.ic_pip, "Pop out: small battle window"))
        addView(icon(ctx, R.drawable.ic_more_vert, "Menu"))
    }

    @Test fun topBarWithAContinueButton() = paparazzi.shootPage("r14g-topbar-deck", listOf(Variants.PHONE)) { bar(it, deck, ArrayList()) }

    @Test fun topBarWithoutOne() = paparazzi.shootPage("r14g-topbar-none", listOf(Variants.PHONE)) { bar(it, null, ArrayList()) }

    @Test fun theButtonShowsWithADeckAndTapStudiesIt() {
        paparazzi.shootPage("r14g-topbar-tap", listOf(Variants.PHONE), themes = listOf(Theme.LIGHT)) { ctx ->
            val taps = ArrayList<Anki.Deck>()
            val b = bar(ctx, deck, taps)
            val btn = find(b, "Continue Verbs, 20 due")
            assertNotNull(btn)
            assertEquals(View.VISIBLE, btn!!.visibility)
            assertTrue(btn.performClick())
            assertEquals(listOf(deck), taps)
            b
        }
    }

    @Test fun noDeckOrPipOrNoBarHidesIt() {
        assertTrue(ContinueBar.topButtonShown(deck, topBarShown = true, inPip = false))
        assertFalse(ContinueBar.topButtonShown(null, topBarShown = true, inPip = false))
        assertFalse(ContinueBar.topButtonShown(deck, topBarShown = true, inPip = true))
        assertFalse(ContinueBar.topButtonShown(deck, topBarShown = false, inPip = false))
    }
}
