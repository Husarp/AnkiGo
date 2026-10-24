package com.husarp.ankigo.shots

import android.content.Context
import android.view.View
import android.view.ViewGroup
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.StudyActivity
import com.husarp.ankigo.study.PanelHeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * R18 FIX-1 (NICE-7): the Study top bar's ⋯ ("Card actions") has a 48 x 48 dp touch area (it was 34), drawn bare as before; the bar is
 * 48 dp high ([PanelHeight.TOP_BAR] = bar + its line). PNGs: r18fix1-study-bar-(phone|split)-(light|dark).
 */
class R18FixStudyBarShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    private fun find(v: View, description: String): View? {
        if (v.contentDescription?.toString() == description) return v
        if (v is ViewGroup) for (i in 0 until v.childCount) find(v.getChildAt(i), description)?.let { return it }
        return null
    }

    private fun dp(ctx: Context, px: Int) = Math.round(px / ctx.resources.displayMetrics.density)

    @Test fun theCardActionsButtonHasA48DpTouchAreaAndTheBarKeepsItsLook() {
        paparazzi.shootWith("r18fix1-study-bar") { ctx ->
            val act = attach(StudyActivity(), ShotCtx(ctx))
            setField(act, "deckId", 2L); setField(act, "deckName", "Geography::Capitals")
            val root = act.buildContent()
            val d = ctx.resources.displayMetrics
            root.measure(View.MeasureSpec.makeMeasureSpec(d.widthPixels, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(d.heightPixels, View.MeasureSpec.EXACTLY))
            root.layout(0, 0, root.measuredWidth, root.measuredHeight)
            val more = find(root, "Card actions")!!
            assertEquals("48 dp wide", 48, dp(ctx, more.width)); assertEquals("48 dp high", 48, dp(ctx, more.height))
            val bar = getField(act, "topBar") as View
            assertEquals("the bar is its row + the 1 dp line", PanelHeight.TOP_BAR, dp(ctx, bar.height))
            assertTrue("the whole 48 dp button is inside the bar", more.height <= bar.height)
            assertTrue("it is clickable and bare (no tile): ripple only", more.isClickable && more.background != null)
            root
        }
    }
}
