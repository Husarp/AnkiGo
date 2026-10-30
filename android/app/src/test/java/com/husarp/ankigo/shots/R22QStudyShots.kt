package com.husarp.ankigo.shots

import app.cash.paparazzi.Paparazzi
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.husarp.ankigo.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class R22QStudyShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    private fun texts(view: View): List<TextView> = (if (view is TextView) listOf(view) else emptyList()) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { texts(view.getChildAt(it)) } else emptyList()

    @Test fun favouritesAndRememberedSections() {
        val ctx = ShotCtx(Shots.files(paparazzi.context))
        val prefs = ctx.getSharedPreferences("study", 0)
        prefs.edit().putString("favouriteDecks", "5,2").apply()
        val built = StudyDeckKit.compose(ctx)
        setField(built.home, "decks", StudyDeckKit.decks)
        built.home.render()
        val labels = texts(built.root).map { it.text.toString() }
        assertTrue(labels.indexOf("Spanish ★") < labels.indexOf("Capitals ★"))
        texts(built.root).first { it.text.toString().startsWith("FAVOURITES", true) }.let { (it.parent as View).performClick() }
        assertTrue(prefs.getBoolean("favouritesCollapsed", false))
        val recreated = StudyDeckKit.compose(ctx)
        setField(recreated.home, "decks", StudyDeckKit.decks)
        recreated.home.render()
        assertFalse(texts(recreated.root).any { it.text == "Spanish ★" })
        texts(recreated.root).first { it.text.toString().startsWith("DECKS", true) }.let { (it.parent as View).performClick() }
        assertTrue(prefs.getBoolean("decksCollapsed", false))
    }

    @Test fun clockInsideTimer() {
        paparazzi.shootWith("r22q-clock", listOf(Size.PHONE, Size.SPLIT)) { ctx ->
            val act = attach(com.husarp.ankigo.StudyActivity(), ShotCtx(ctx))
            val root = act.buildContent()
            val timer = getField(act, "timerPill") as TextView
            for (red in listOf(false, true)) {
                call(act, "styleTimer", red)
                assertNotNull(timer.compoundDrawablesRelative[0])
                assertEquals(android.view.Gravity.CENTER_VERTICAL, timer.gravity and android.view.Gravity.VERTICAL_GRAVITY_MASK)
            }
            timer.text = "0:24"
            timer.visibility = View.VISIBLE
            call(act, "styleTimer", false)
            root
        }
    }

    @Test fun searchableFoldingParentTree() {
        val act = attach(android.app.Activity(), ShotCtx(Shots.files(paparazzi.context)))
        var chosen: com.husarp.ankigo.Anki.Deck? = null
        val view = com.husarp.ankigo.deck.ParentDeckPicker.content(act, StudyDeckKit.decks, null) { chosen = it }
        assertFalse(texts(view).any { it.text.toString().contains("Capitals") })
        fun find(v: View, desc: String): View? {
            if (v.contentDescription == desc) return v
            if (v is ViewGroup) for (i in 0 until v.childCount) find(v.getChildAt(i), desc)?.let { return it }
            return null
        }
        find(view, "Fold or expand Geography")!!.performClick()
        assertTrue(texts(view).any { it.text.toString().contains("Capitals") })
        texts(view).filterIsInstance<android.widget.EditText>().single().setText("capitals")
        val labels = texts(view).map { it.text.toString() }
        assertTrue(labels.contains("○ Geography › Capitals"))
        assertFalse(labels.any { it == "○ Medicine" })
        texts(view).first { it.text.toString() == "○ Geography › Capitals" }.performClick()
        assertEquals(2L, chosen!!.id)
    }

    @Test fun parentControlKeepsName() {
        var opened = false
        val dialog = com.husarp.ankigo.deck.DeckDialogs.Name(paparazzi.context, "New deck", null, "Deck name", { opened = true }) { _, _ -> }
        val view = dialog.view()
        val input = texts(view).filterIsInstance<android.widget.EditText>().single()
        input.setText("Rivers of Asia")
        texts(view).first { it.text.toString().startsWith("Inside ·") }.performClick()
        assertTrue(opened)
        assertEquals("Rivers of Asia", dialog.typed)
    }

    @Test fun favouriteLooks() {
        for (look in DeckListLook.entries) paparazzi.shootWith("r22q-favourites-${look.name}", listOf(Size.PHONE, Size.SPLIT)) { base ->
            val ctx = ShotCtx(base)
            DeckListLook.put(ctx, look)
            ctx.getSharedPreferences("study", 0).edit().putString("favouriteDecks", "2,5").apply()
            val built = StudyDeckKit.compose(ctx)
            setField(built.home, "decks", StudyDeckKit.decks)
            setField(built.home, "expanded", setOf(1L, 4L))
            built.home.render()
            built.root
        }
    }

    @Test fun nextDeckHierarchyAndTap() {
        var clicked = false
        val view = StudyUi.finishedNextDeck(paparazzi.context, "Finance::Markets::Speculative assets", 124) { clicked = true }
        assertEquals(listOf("Finance › Markets", "Speculative assets", "124 due"), texts(view).map { it.text.toString() })
        view.performClick()
        assertTrue(clicked)
        paparazzi.shootWith("r22q-next-deck", listOf(Size.PHONE, Size.SPLIT)) { ctx ->
            StudyUi.column(ctx).apply { addView(StudyUi.finishedNextDeck(ctx, "Finance::Markets::Speculative assets", 124) {}) }
        }
    }
}
