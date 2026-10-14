package com.husarp.ankigo.shots

import android.content.Context
import android.view.View
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.Anki
import com.husarp.ankigo.ui.StudyDecksActivity
import org.junit.Rule
import org.junit.Test

/** The Study deck picker (StudyDecksActivity) with fake AnkiDroid decks. Part of the StudyShots class (see below). */
object StudyDeckKit {
    // Deck(id, name, learn, review, new, dyn)
    val decks = listOf(
        Anki.Deck(1, "Geography", 4, 31, 12, false),
        Anki.Deck(2, "Geography::Capitals", 3, 20, 8, false),
        Anki.Deck(3, "Geography::Rivers", 1, 11, 4, false),
        Anki.Deck(4, "Languages", 2, 14, 10, false),
        Anki.Deck(5, "Languages::Spanish", 2, 9, 6, false),
        Anki.Deck(6, "Languages::Japanese", 0, 5, 4, false),
        Anki.Deck(7, "Medicine", 0, 0, 0, false),
        Anki.Deck(8, "Leeches", 1, 6, 0, true),
        Anki.Deck(9, "Default", 0, 0, 0, false),
    )

    fun build(
        ctx: Context, decks: List<Anki.Deck> = this.decks, ankiVersion: String? = "2.24.0",
        folded: Set<String> = emptySet(), lastDeck: Long = 2, problem: String? = null, granted: Boolean = true,
    ): View {
        val ss = ShotCtx(ctx, ankiVersion = ankiVersion, granted = granted)
        val act = attach(StudyDecksActivity(), ss)
        ss.getSharedPreferences("study", 0).edit().putLong("lastDeck", lastDeck)
            .putLong("lastStudied:$lastDeck", System.currentTimeMillis() - 12 * 60_000L).apply()
        val root = act.buildContent()
        if (problem != null) { act.showAccessProblem(problem); return root }
        setField(act, "decks", decks.sortedBy { it.name })
        setField(act, "folded", folded)
        call(act, "render")
        return root
    }
}
