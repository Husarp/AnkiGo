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
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/*
 * R14-G (PLAN 204), changed by R18-J (261): the full-screen Battle view's top bar no longer holds the small green Continue button.
 * The bar here mirrors GameActivity.buildTopBar. Shot: r14g-topbar-*.
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

    private fun bar(ctx: Context): View = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = android.view.Gravity.CENTER_VERTICAL
        setBackgroundColor(Ui.c(ctx, R.color.ag_base))
        setPadding(Ui.dp(ctx, 4), Ui.dp(ctx, 4), Ui.dp(ctx, 8), Ui.dp(ctx, 4))
        addView(icon(ctx, R.drawable.ic_back, "Back", R.color.ag_text))
        addView(Ui.text(ctx, 18f, 900, R.color.ag_text).apply { text = "Battle"; maxLines = 1 }, LinearLayout.LayoutParams(0, -2, 1f))
        addView(icon(ctx, R.drawable.ic_pip, "Pop out: small battle window"))
        addView(icon(ctx, R.drawable.ic_more_vert, "Menu"))
    }

    @Test fun topBarWithoutAContinueButton() {
        paparazzi.shootPage("r14g-topbar-none", listOf(Variants.PHONE)) { bar(it) }
    }

    /** R18-J (261): the top bar has no Continue button any more (the Continue bar under it is the only one); the shared button stays for the Pokémon tab's card. */
    @Test fun noContinueButtonInTheTopBar() {
        paparazzi.shootPage("r14g-topbar-tap", listOf(Variants.PHONE), themes = listOf(Theme.LIGHT)) { ctx ->
            val b = bar(ctx)
            assertNull(find(b, "Continue Verbs, 20 due"))
            val card = ContinueBar.button(ctx).also { ContinueBar.bindButton(it, deck) { } }
            assertEquals(View.VISIBLE, card.visibility)
            assertEquals("Continue Verbs, 20 due", card.contentDescription)
            b
        }
    }
}
