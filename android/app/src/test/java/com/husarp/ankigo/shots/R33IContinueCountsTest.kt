package com.husarp.ankigo.shots

import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.husarp.ankigo.Anki
import com.husarp.ankigo.R
import com.husarp.ankigo.ui.DeckCounts
import com.husarp.ankigo.ui.Ui
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class R33IContinueCountsTest {
    @get:Rule val paparazzi = Shots.rule()

    private fun texts(view: View): List<TextView> = if (view is TextView) listOf(view) else if (view is ViewGroup)
        (0 until view.childCount).flatMap { texts(view.getChildAt(it)) } else emptyList()

    private fun slot(built: StudyDeckKit.Built) = getField(built.home, "resumeSlot") as LinearLayout

    private fun checkCounts(built: StudyDeckKit.Built, deck: Anki.Deck) {
        val card = slot(built).getChildAt(0)
        assertEquals("Continue ${deck.name}, New ${deck.new}, Learning ${deck.learn}, Due ${deck.review}", card.contentDescription)
        val values = listOf(deck.new, deck.learn, deck.review)
        val descriptions = listOf("New ${deck.new}", "Learn ${deck.learn}", "Review ${deck.review}")
        val tones = listOf(R.color.ag_blue, R.color.ag_red, R.color.ag_green)
        descriptions.forEachIndexed { i, description ->
            val count = texts(card).single { it.contentDescription == description }
            assertEquals(DeckCounts.format(values[i]), count.text.toString())
            assertEquals(Ui.c(card.context, if (values[i] == 0) R.color.ag_ov else tones[i]), count.currentTextColor)
            assertTrue(count.typeface.weight >= 700)
            assertEquals(View.VISIBLE, count.visibility)
            val matchingListCount = texts(built.root).filter { it.contentDescription == description }.last()
            assertEquals(count.text.toString(), matchingListCount.text.toString())
            assertEquals(count.currentTextColor, matchingListCount.currentTextColor)
        }
        assertTrue(texts(card).map { it.text.toString() }.containsAll(listOf("New", "Lrn", "Due")))
        val root = built.root
        root.measure(View.MeasureSpec.makeMeasureSpec(Ui.dp(root.context, 320), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(Ui.dp(root.context, 640), View.MeasureSpec.EXACTLY))
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)
        for (count in texts(card).filter { it.contentDescription in descriptions }) {
            assertTrue("count fits its cell", count.paint.measureText(count.text.toString()) <= count.width)
            var right = count.right
            var parent = count.parent as View
            while (parent !== card) { right += parent.left; parent = parent.parent as View }
            assertTrue("count fits the card", right <= card.width - card.paddingRight)
        }
    }

    private fun shot(name: String, deck: Anki.Deck) {
        paparazzi.shootPage("i-$name", listOf(Variant("320", 320, 640))) { base ->
            val ctx = ShotCtx(base)
            ctx.getSharedPreferences("study", 0).edit().putLong("lastDeck", deck.id)
                .putLong("lastStudied:${deck.id}", System.currentTimeMillis() - 12 * 60_000).apply()
            val built = StudyDeckKit.compose(ctx)
            built.home.loadsInBackground = false
            try {
                setField(built.home, "decks", listOf(deck))
                built.home.render()
                checkCounts(built, deck)
                built.root
            } finally { built.home.onDestroy() }
        }
    }

    @Test fun allPositiveInBothThemesAt320dp() = shot("positive", Anki.Deck(2, "Languages::Spanish", 6, 12, 50, false))

    @Test fun zeroCountsStayVisibleAndDimmedInBothThemes() = shot("zeros", Anki.Deck(2, "Languages::Spanish", 0, 12, 0, false))

    @Test fun largeCountsFitInBothThemes() = shot("large", Anki.Deck(2, "A long deck name that must be ellipsized", 99999, 99999, 99999, false))

    @Test fun renderUpdatesContinueAndDeckListFromTheSameSnapshot() {
        val built = StudyDeckKit.compose(ShotCtx(Shots.files(paparazzi.context)))
        built.home.loadsInBackground = false
        built.root.context.getSharedPreferences("study", 0).edit().putLong("lastDeck", 2).apply()
        try {
            for (deck in listOf(Anki.Deck(2, "Spanish", 6, 12, 50, false), Anki.Deck(2, "Spanish", 0, 7, 0, false),
                Anki.Deck(2, "Spanish", 0, 0, 0, false))) {
                setField(built.home, "decks", listOf(deck))
                built.home.render()
                checkCounts(built, deck)
            }
            setField(built.home, "decks", emptyList<Anki.Deck>())
            built.home.render()
            assertEquals(View.GONE, slot(built).visibility)
        } finally { built.home.onDestroy() }
    }
}
