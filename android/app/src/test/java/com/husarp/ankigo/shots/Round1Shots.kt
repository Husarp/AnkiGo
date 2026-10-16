package com.husarp.ankigo.shots

import android.content.Context
import android.view.View
import android.view.ViewGroup
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.Anki
import com.husarp.ankigo.ui.PullRefreshLayout
import com.husarp.ankigo.ui.StudyDecksActivity
import com.husarp.ankigo.ui.StudyWidgetSetupActivity
import com.husarp.ankigo.ui.Ui
import org.junit.Rule
import org.junit.Test

/** Round 1: R1-08 (pull-to-refresh spinner in the deck list) and R1-11 (4/5-digit counts in the deck lists). */
class Round1Shots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    private val both = listOf(Size.PHONE, Size.SPLIT)

    // Deck(id, name, learn, review, new, dyn)
    private val big = listOf(
        Anki.Deck(1, "Medicine", 1000, 9999, 12345, false),
        Anki.Deck(2, "Medicine::Anatomy", 999, 1000, 9999, false),
        Anki.Deck(3, "Languages", 12345, 100, 10, false),
        Anki.Deck(4, "Languages::A very long deck name that must be cut with dots", 4, 31, 1000, false),
        Anki.Deck(5, "Huge", 120_000, 1_234_567, 99_999, false),
        Anki.Deck(6, "Default", 0, 0, 0, false),
    )

    private fun decks(ctx: Context, refreshing: Boolean = false, pulled: Boolean = false): View {
        val root = StudyDeckKit.build(ctx, decks = big, expanded = setOf(1L), lastDeck = 3)
        if (refreshing || pulled) {
            val pull = findPull(root)!!
            val spinner = pull.getChildAt(1)
            spinner.visibility = View.VISIBLE
            spinner.translationY = if (refreshing) Ui.dp(ctx, 12).toFloat() else Ui.dp(ctx, 4).toFloat()
            if (refreshing) setSpinning(spinner, true) else setProgress(spinner, .8f)
        }
        return root
    }

    private fun findPull(v: View): PullRefreshLayout? {
        if (v is PullRefreshLayout) return v
        if (v is ViewGroup) for (i in 0 until v.childCount) findPull(v.getChildAt(i))?.let { return it }
        return null
    }
    private fun setSpinning(spinner: View, on: Boolean) { spinner.javaClass.getMethod("setSpinning", Boolean::class.java).apply { isAccessible = true }.invoke(spinner, on) }
    private fun setProgress(spinner: View, p: Float) { spinner.javaClass.getMethod("setProgress", Float::class.java).apply { isAccessible = true }.invoke(spinner, p) }

    @Test fun bigCounts() = paparazzi.shootWith("r1-deck-bigcounts", both) { decks(it) }
    @Test fun pulling() = paparazzi.shootWith("r1-deck-pulling", both) { decks(it, pulled = true) }
    @Test fun refreshing() = paparazzi.shootWith("r1-deck-refreshing", both) { decks(it, refreshing = true) }

    @Test fun widgetSetupBigCounts() = paparazzi.shootWith("r1-widgetsetup-bigcounts", both) { ctx ->
        val act = attach(StudyWidgetSetupActivity(), ShotCtx(ctx))
        setField(act, "decks", big.sortedBy { it.name })
        setField(act, "currentDeck", 3L)
        val root = act.buildContent()
        act.render()
        root
    }
}
