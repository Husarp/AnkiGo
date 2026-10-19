package com.husarp.ankigo.shots

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.Anki
import com.husarp.ankigo.R
import com.husarp.ankigo.ui.StudyUi
import com.husarp.ankigo.ui.Ui
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * R9-B3: the Study bar and the deck-finished screen have no Sync button any more (B3); "Back to decks" is a full-width button of at
 * least 48 dp (B8); the CONTINUE card stays on top while the deck list scrolls (B9a). Made-up decks only.
 * PNGs (phone + split, light + dark): r9-study-sticky (list scrolled down), r9-study-top (not scrolled), r9-finished (button column).
 */
class R9StudyShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    private val both = listOf(Size.PHONE, Size.SPLIT)

    private val decks = (1..24).map { Anki.Deck(it.toLong(), "Made-up deck %02d".format(it), it % 3, it % 7, it % 5, false) }

    private fun lay(ctx: Context, v: View, size: Size) {
        val d = ctx.resources.displayMetrics
        v.measure(View.MeasureSpec.makeMeasureSpec(Ui.dp(ctx, size.wDp), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(Ui.dp(ctx, size.hDp), View.MeasureSpec.EXACTLY))
        v.layout(0, 0, v.measuredWidth, v.measuredHeight)
        assertTrue(d.density > 0)
    }

    private fun scrollView(v: View): ScrollView? {
        if (v is ScrollView) return v
        if (v is ViewGroup) for (i in 0 until v.childCount) scrollView(v.getChildAt(i))?.let { return it }
        return null
    }

    private fun studyTab(ctx: Context, size: Size, scrolled: Boolean): View {
        val ss = ShotCtx(ctx)
        ss.getSharedPreferences("study", 0).edit().putLong("lastDeck", 3).putLong("lastStudied:3", System.currentTimeMillis() - 12 * 60_000L).apply()
        val b = StudyDeckKit.compose(ss)
        setField(b.home, "decks", decks)
        call(b.home, "render")
        lay(ctx, b.root, size)
        val slot = getField(b.home, "resumeSlot") as View
        assertEquals(View.VISIBLE, slot.visibility)
        assertTrue("the CONTINUE card has a size", slot.height > 0 && slot.childCount() == 1)
        if (scrolled) {
            val scroll = scrollView(b.root)!!
            val y = scroll.getChildAt(0).height
            scroll.scrollTo(0, y)
            lay(ctx, b.root, size)
            assertTrue("the list is scrolled", scroll.scrollY > 0)
            assertTrue("the CONTINUE card is not inside the scrolling list", !isInside(slot, scroll))
            assertEquals("the card stays at the top of the body", 0, slot.top)
        }
        return b.root
    }

    private fun View.childCount() = (this as ViewGroup).childCount
    private fun isInside(v: View, parent: View): Boolean { var p = v.parent; while (p != null) { if (p === parent) return true; p = p.parent }; return false }

    @Test fun stickyContinueAfterScrolling() {
        for (size in both) paparazzi.shootWith("r9-study-sticky", listOf(size)) { ctx -> studyTab(ctx, size, true) }
    }

    @Test fun continueAtTheTop() {
        for (size in both) paparazzi.shootWith("r9-study-top", listOf(size)) { ctx -> studyTab(ctx, size, false) }
    }

    @Test fun appBarHasNoSyncButton() {
        paparazzi.shootWith("r9-study-bar", listOf(Size.PHONE)) { ctx ->
            val b = StudyDeckKit.compose(ShotCtx(ctx))
            fun names(v: View, out: MutableList<String>) {
                v.contentDescription?.let { out += it.toString() }
                if (v is ViewGroup) for (i in 0 until v.childCount) names(v.getChildAt(i), out)
            }
            val found = mutableListOf<String>().also { names(b.home.buildAppBar(), it) }
            assertTrue("no Sync button in the bar: $found", found.none { it.contains("Sync", ignoreCase = true) })
            assertTrue("New deck stays: $found", found.contains("New deck"))
            b.root
        }
    }

    /** The button column of the deck-finished screen, built with the same helpers as StudyActivity. */
    @Test fun finishedButtons() {
        for (size in both) paparazzi.shootWith("r9-finished", listOf(size)) { ctx ->
            val col = StudyUi.column(ctx).apply {
                setBackgroundColor(Ui.c(ctx, R.color.ag_base))
                setPadding(Ui.dp(ctx, 12), Ui.dp(ctx, 8), Ui.dp(ctx, 12), Ui.dp(ctx, 10))
            }
            col.addView(StudyUi.finishedPrimary(ctx, "Next deck: Made-up deck 02 · 14 due") {})
            val back = StudyUi.secondary(ctx, "Back to decks") {}
            col.addView(back, StudyUi.spaced(ctx, 6))
            val cont = StudyUi.finishedPrimary(ctx, "Continue · learning cards are ready") {}
            col.addView(cont, StudyUi.spaced(ctx, 6))
            val root = FrameLayout(ctx).apply { setBackgroundColor(Ui.c(ctx, R.color.ag_base)) }
            root.addView(col, FrameLayout.LayoutParams(-1, -2, android.view.Gravity.BOTTOM))
            lay(ctx, root, size)
            val min = Ui.dp(ctx, 48)
            assertTrue("Back to decks is at least 48 dp tall: ${back.height}", back.height >= min)
            assertTrue("Continue is at least 48 dp tall: ${cont.height}", cont.height >= min)
            assertEquals("Back to decks fills the column", col.width - col.paddingLeft - col.paddingRight, back.width)
            assertEquals("same width as the primary buttons", cont.width, back.width)
            root
        }
    }
}
