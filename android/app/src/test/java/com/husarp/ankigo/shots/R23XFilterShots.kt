package com.husarp.ankigo.shots

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.Anki
import com.husarp.ankigo.StudyActivity
import com.husarp.ankigo.study.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class R23XFilterShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    private fun texts(v: View): List<TextView> = listOfNotNull(v as? TextView) +
        if (v is ViewGroup) (0 until v.childCount).flatMap { texts(v.getChildAt(it)) } else emptyList()

    @Test fun setupHasInlineDeckChoicesAndCountAboveStart() {
        paparazzi.shootWith("r23x-filter", listOf(Size.PHONE, Size.SPLIT)) { ctx ->
            val act = attach(FilterActivity(), ShotCtx(ctx))
            val root = act.buildContent()
            setField(act, "decks", listOf(Anki.Deck(1, "Japanese", 1, 5, 2, false), Anki.Deck(2, "Medicine", 0, 2, 0, false)))
            setField(act, "filter", StudyFilter(listOf(DeckFilter(1, "Japanese", setOf("task"), true, true)), setOf("vocabulary")))
            setField(act, "expanded", setOf(1L))
            call(act, "render", false)
            (getField(act, "count") as TextView).text = "186 cards match"
            val labels = texts(root).map { it.text.toString() }
            assertTrue(labels.contains("Choose what to study"))
            assertTrue(labels.contains("Ignore global tags"))
            assertTrue(labels.contains("Free study · all matching cards, including cards not due"))
            assertTrue(labels.indexOf("186 cards match") < labels.indexOf("Start"))
            root
        }
    }
    @Test fun filteredDueAnswersMarkOnlyTheirActualDeckAsStudied() {
        val ctx = ShotCtx(Shots.files(paparazzi.context))
        val act = attach(StudyActivity(), ctx)
        act.intent = Intent().putExtra("filterSession", "filter-session-test.json")
        setField(act, "deckId", 901L)
        call(act, "countSaved", QueuedAnswer(1, 902, 42, 0, 123, emptyMap(), 3, 100, 1))
        val prefs = ctx.getSharedPreferences("study", 0)
        assertTrue(prefs.getLong("lastStudied:902", 0) > 0)
        assertFalse(prefs.contains("lastStudied:901"))
    }
    @Test fun practiceNoticeIsSmallAndLivesAtCardEdge() {
        paparazzi.shootWith("r23x-practice-notice", listOf(Size.PHONE, Size.SPLIT)) { ctx ->
            val act = attach(StudyActivity(), ShotCtx(ctx))
            act.intent = Intent().putExtra("filterSession", "filter-session-test.json")
            val root = act.buildContent()
            act.getSharedPreferences("study", 0).edit().putBoolean(NewCardOk.PREF, true).apply()
            setField(act, "card", Anki.Review(42, 0, 4, emptyList(), emptyList()))
            setField(act, "locked", false)
            setField(act, "stats", mapOf("queue" to "0"))
            assertEquals(true, call(act, "okRow")) // the existing new-card OK preference still applies
            act.getSharedPreferences("study", 0).edit().putBoolean(NewCardOk.PREF, false).apply()
            assertEquals(false, call(act, "okRow"))
            val icon = getField(act, "practiceNotice") as View
            assertEquals(View.GONE, icon.visibility)
            icon.visibility = View.VISIBLE
            val lp = icon.layoutParams as android.widget.FrameLayout.LayoutParams
            assertEquals(android.view.Gravity.BOTTOM or android.view.Gravity.END, lp.gravity)
            assertEquals("Free study information", icon.contentDescription)
            setField(act, "deckName", "Geography")
            (getField(act, "header") as TextView).text = "Geography"
            (getField(act, "subtitle") as TextView).text = "Capitals · Card 1"
            call(act, "layout")
            val panel = getField(act, "panel") as com.husarp.ankigo.ui.BattlePanelView
            panel.renderAll(); panel.restoreStudyCharge()
            call(act, "showButtons")
            root
        }
    }
}
