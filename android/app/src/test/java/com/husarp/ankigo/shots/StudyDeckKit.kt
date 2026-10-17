package com.husarp.ankigo.shots

import android.app.Activity
import android.content.Context
import android.view.View
import android.widget.LinearLayout
import com.husarp.ankigo.Anki
import com.husarp.ankigo.R
import com.husarp.ankigo.ui.BottomNav
import com.husarp.ankigo.ui.StudyHome
import com.husarp.ankigo.ui.Ui

/**
 * The deck list of the Study view (StudyHome) with fake AnkiDroid decks, composed as the old Study tab looked: app bar, the list,
 * the bottom bar. (The merged home screen with its battle card is [HomeShotKit].) Part of the StudyShots class (see below).
 */
object StudyDeckKit {
    internal class Built(val home: StudyHome, val root: View)

    /** StudyHome on a context that is already a [ShotCtx]: [Built.root] = [Study app bar][deck list][bottom bar]. */
    internal fun compose(ss: Context): Built {
        val act = attach(Activity(), ss)
        val home = StudyHome(act, object : StudyHome.Host {
            override fun studyInAnkiDroid(deckId: Long) {}
            override fun askAnkiAccess() {}
        })
        val root = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Ui.c(act, R.color.ag_base)) }
        root.addView(home.buildAppBar())
        root.addView(home.buildBody(), LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(BottomNav.create(act, BottomNav.Tab.STUDY))
        return Built(home, root)
    }

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
        expanded: Set<Long> = emptySet(), lastDeck: Long = 2, problem: String? = null, granted: Boolean = true,
    ): View {
        val ss = ShotCtx(ctx, ankiVersion = ankiVersion, granted = granted)
        ss.getSharedPreferences("study", 0).edit().putLong("lastDeck", lastDeck)
            .putLong("lastStudied:$lastDeck", System.currentTimeMillis() - 12 * 60_000L).apply()
        val b = compose(ss)
        if (problem != null) { b.home.showAccessProblem(problem); return b.root }
        setField(b.home, "decks", decks.sortedBy { it.name })
        setField(b.home, "expanded", expanded)
        call(b.home, "render")
        return b.root
    }
}
