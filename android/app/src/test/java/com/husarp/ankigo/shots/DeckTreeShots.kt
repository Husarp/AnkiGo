package com.husarp.ankigo.shots

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.Anki
import org.junit.Rule
import org.junit.Test

/** U-02: Study tab deck tree (collapsed first open, nested expanded, AnkiDroid icon + fallback). */
class DeckTreeShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    private val nested = StudyDeckKit.decks + listOf(
        Anki.Deck(10, "Geography::Capitals::Asia", 1, 6, 2, false),
        Anki.Deck(11, "Geography::Capitals::Europe", 2, 8, 3, false),
        Anki.Deck(12, "Geography::Capitals::A really long deck name that must be cut off", 0, 6, 3, false),
    )

    /** A stand-in for AnkiDroid's launcher icon (the real one is read from the phone). */
    private fun fakeIcon() = GradientDrawable().apply { setColor(0xff3f51b5.toInt()); cornerRadius = 40f; setSize(96, 96); setStroke(6, Color.WHITE) }

    private fun build(ctx: android.content.Context, expanded: Set<Long>, icon: Boolean): android.view.View {
        val ss = ShotCtx(ctx)
        ss.getSharedPreferences("study", 0).edit().putLong("lastDeck", 2).putLong("lastStudied:2", System.currentTimeMillis() - 12 * 60_000L).apply()
        val b = StudyDeckKit.compose(ss)
        setField(b.home, "decks", nested.sortedBy { it.name })
        setField(b.home, "expanded", expanded)
        if (icon) { setField(b.home, "ankiIcon", fakeIcon()); setField(b.home, "ankiIconLoaded", true) }
        call(b.home, "render")
        return b.root
    }

    @Test fun tree() {
        val both = listOf(Size.PHONE, Size.SPLIT)
        paparazzi.shootWith("decktree-collapsed", both) { build(it, emptySet(), true) }
        paparazzi.shootWith("decktree-nested", both) { build(it, setOf(1L, 2L, 4L), true) }
        paparazzi.shootWith("decktree-fallback", both) { build(it, setOf(1L), false) }
    }
}
