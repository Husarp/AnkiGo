package com.husarp.ankigo.shots

import android.widget.ScrollView
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.deck.DeckStatus
import com.husarp.ankigo.deck.DeckStatusSheet
import com.husarp.ankigo.R
import com.husarp.ankigo.ui.Ui
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class DeckStatusShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    @Test fun pickedStackAndLabels() {
        paparazzi.shootWith("deck-status", listOf(Size.PHONE, Size.SPLIT)) { ctx ->
            val content = DeckStatusSheet.statusView(ctx, DeckStatus.Snapshot(listOf(22, 11, 44, 143), 4, 2, 25, 18, 12, 7, true))
            val bar = (0 until content.childCount).map { content.getChildAt(it) }.filterIsInstance<android.widget.LinearLayout>().single()
            assertEquals(4, bar.childCount)
            assertTrue(bar.contentDescription.contains("Learning 11"))
            ScrollView(ctx).apply {
                setBackgroundColor(Ui.c(ctx, R.color.ag_card))
                setPadding(Ui.dp(ctx, 20), Ui.dp(ctx, 18), Ui.dp(ctx, 20), 0)
                addView(content)
            }
        }
    }
    @Test fun longPressOffersStatusForDeckAndSubdeck() {
        paparazzi.shootWith("deck-status-menu", listOf(Size.PHONE)) { ctx ->
            val ss = ShotCtx(ctx)
            val built = StudyDeckKit.compose(ss)
            var shown: com.husarp.ankigo.ui.AppMenu? = null
            com.husarp.ankigo.ui.AppMenu.intercept = { shown = it }
            try {
                for (deck in StudyDeckKit.decks.take(2)) {
                    built.home.deckMenu(deck, built.root)
                    val entries = shown!!.describe()
                    assertTrue(entries.any { it.second == "Deck status" && it.third })
                    assertTrue(entries.any { it.second == "Study in AnkiDroid" })
                }
                shown!!.content(ctx)
            } finally {
                com.husarp.ankigo.ui.AppMenu.intercept = null
            }
        }
    }
    @Test fun emptyDeck() {
        paparazzi.shootWith("deck-status-empty", listOf(Size.PHONE)) { ctx ->
            DeckStatusSheet.statusView(ctx, DeckStatus.Snapshot(listOf(0, 0, 0, 0), 3, 1, 0, 0, 0, 0, false)).apply {
                setBackgroundColor(Ui.c(ctx, R.color.ag_card))
            }
        }
    }
}
