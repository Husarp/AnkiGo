package com.husarp.ankigo.shots

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.Anki
import com.husarp.ankigo.StudyActivity
import com.husarp.ankigo.deck.DeckDialogs
import com.husarp.ankigo.deck.DeckOps
import com.husarp.ankigo.deck.DeckPick
import com.husarp.ankigo.deck.DeckPickerSheet
import com.husarp.ankigo.deck.DeckResult
import com.husarp.ankigo.deck.MoveAllJob
import com.husarp.ankigo.deck.NoteEditActivity
import com.husarp.ankigo.deck.NoteForEdit
import com.husarp.ankigo.study.TtsRules
import com.husarp.ankigo.ui.AppMenu
import com.husarp.ankigo.ui.StudyDecksActivity
import com.husarp.ankigo.ui.Ui
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * R3-V7 (PLAN 70, pick 7A): deck and card management screens, made-up decks and notes only.
 * PNGs (phone + split, light + dark): r3v7-deckmenu (long-press on a deck), r3v7-deckmenu-filtered, r3v7-newdeck(+-error, -sub),
 * r3v7-cardmenu, r3v7-edit (+-edited, -problem, -loading), r3v7-preview, r3v7-move (tree with a filtered deck, a picked deck, the
 * move-all variant), r3v7-delete, r3v7-moveall (running, finished, nothing to move). The card page in Preview is a WebView:
 * layoutlib draws it blank (as in the study shots).
 */
class R3V7Shots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    private val both = listOf(Size.PHONE, Size.SPLIT)

    private val decks = listOf(
        Anki.Deck(1, "Japanese Core 2k", 8, 3, 42, false),
        Anki.Deck(10, "Japanese Core 2k::Kanji", 0, 0, 12, false),
        Anki.Deck(2, "Geography", 5, 0, 9, false),
        Anki.Deck(3, "Geography::Capitals", 5, 0, 6, false),
        Anki.Deck(4, "Geography::Rivers", 0, 0, 3, false),
        Anki.Deck(5, "Spanish verbs", 0, 0, 0, false),
        Anki.Deck(6, "Today's review", 1, 6, 0, true),
    )

    // ---------------------------------------------------------------- deck list

    private fun deckScreen(ctx: Context, granted: Boolean = true): Pair<StudyDecksActivity, View> {
        val ss = ShotCtx(ctx, granted = granted)
        val act = attach(StudyDecksActivity(), ss)
        ss.getSharedPreferences("study", 0).edit().putLong("lastDeck", 0).apply()
        val root = act.buildContent()
        setField(act, "decks", decks.sortedBy { it.name })
        setField(act, "expanded", setOf(1L, 2L))
        call(act, "render")
        return act to root
    }

    private fun labels(m: AppMenu) = m.describe().map { it.second }

    @Test fun deckMenu() {
        paparazzi.shootWith("r3v7-deckmenu", both) { ctx ->
            val (act, root) = deckScreen(ctx)
            val menu = MenuKit.capture { act.deckMenu(decks[2], View(ctx)) }!!
            assertEquals(listOf("New subdeck…", "Move all cards…", "Study in AnkiDroid"), labels(menu))
            MenuKit.over(ctx, root, menu, Gravity.TOP, 300)
        }
    }

    @Test fun deckMenuOnlyWhereItWorks() {
        paparazzi.shootWith("r3v7-deckmenu-filtered", listOf(Size.PHONE)) { ctx ->
            val (act, root) = deckScreen(ctx)
            val menu = MenuKit.capture { act.deckMenu(decks[6], View(ctx)) }!!
            // a filtered deck holds no subdeck and no cards of its own: only the AnkiDroid entry is left (nothing greyed)
            assertEquals(listOf("Study in AnkiDroid"), labels(menu))
            MenuKit.over(ctx, root, menu, Gravity.TOP, 420)
        }
        paparazzi.shootWith("r3v7-deckmenu-nopermission", listOf(Size.PHONE)) { ctx ->
            val (act, root) = deckScreen(ctx, granted = false)
            val menu = MenuKit.capture { act.deckMenu(decks[2], View(ctx)) }!!
            assertEquals(listOf("Study in AnkiDroid"), labels(menu))
            root
        }
    }

    // ---------------------------------------------------------------- name dialogs

    private fun nameScene(ctx: Context, title: String, inside: String?, typed: String, problem: String?): View {
        val n = DeckDialogs.Name(ctx, title, inside, if (inside == null) "Name, or Parent::Child" else "Subdeck name") { _, _ -> }
        val dlg = DialogShots.capture { n.show() }!!
        if (typed.isNotEmpty()) (getField(n, "input") as EditText).setText(typed)
        n.problem(problem)
        return DialogShots.scene(ctx, dlg)
    }

    @Test fun nameDialogs() {
        paparazzi.shootWith("r3v7-newdeck", both) { nameScene(it, "New deck", null, "", null) }
        paparazzi.shootWith("r3v7-newdeck-sub", both) { nameScene(it, "New subdeck", "Geography", "Capitals", null) }
        paparazzi.shootWith("r3v7-newdeck-error", both) {
            nameScene(it, "New subdeck", "Geography", "Capitals", "A deck called \"Geography::Capitals\" already exists (capital letters don't make a new name).")
        }
    }

    // ---------------------------------------------------------------- the study card

    private fun studyScreen(ctx: Context): Pair<StudyActivity, View> {
        val ss = ShotCtx(ctx, ankiVersion = "2.25.0")
        val act = attach(StudyActivity(), ss)
        setField(act, "deckId", 3L)
        setField(act, "deckName", "Geography::Capitals")
        val root = act.buildContent()
        setField(act, "card", Anki.Review(10L, 0, 4, listOf("<1m", "6m", "1d", "4d"), emptyList()))
        setField(act, "question", "<div>Capital of Mongolia?</div>")
        setField(act, "answerHtml", "<div>Ulaanbaatar</div>")
        setField(act, "modelName", "Basic (and reversed)")
        setField(act, "cardName", "Card 1")
        setField(act, "locked", false)
        (getField(act, "subtitle") as TextView).text = "Card 1 · card 8"
        act.showQueueCounts(decks[3], 2)
        call(act, "layout")
        val panel = getField(act, "panel") as com.husarp.ankigo.ui.BattlePanelView
        panel.renderAll(); panel.restoreStudyCharge()
        call(act, "showButtons")
        return act to root
    }

    private fun dim(ctx: Context, root: View, over: View, gravity: Int = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, w: Int = -2): View = FrameLayout(ctx).apply {
        addView(root, FrameLayout.LayoutParams(-1, -1))
        addView(View(ctx).apply { setBackgroundColor(Color.argb((.48f * 255).toInt(), 0, 0, 0)) }, FrameLayout.LayoutParams(-1, -1))
        addView(over, FrameLayout.LayoutParams(if (w == -2) minOf(ctx.resources.displayMetrics.widthPixels, Ui.dp(ctx, 600)) else w, -2, gravity))
    }

    private fun texts(v: View, out: MutableList<String> = ArrayList()): List<String> {
        if (v is TextView && v.text.isNotEmpty()) out += v.text.toString()
        if (v is ViewGroup) for (i in 0 until v.childCount) texts(v.getChildAt(i), out)
        return out
    }

    @Test fun cardMenu() {
        paparazzi.shootWith("r3v7-cardmenu", both) { ctx ->
            val (act, root) = studyScreen(ctx)
            val sheet = act.actionSheetContent { }
            val t = texts(sheet)
            // Edit, Move, Delete on top (once each: the old "open AnkiDroid's browser" edit row and the old delete row are gone), the rest below
            assertEquals(1, t.count { it == "Edit note" }); assertEquals(1, t.count { it == "Delete note…" })
            assertTrue(t.indexOf("Edit note") < t.indexOf("Move to deck…") && t.indexOf("Move to deck…") < t.indexOf("Delete note…"))
            assertTrue(t.indexOf("Delete note…") < t.indexOf("Bury card") && t.indexOf("Bury card") < t.indexOf("Continue in AnkiDroid"))
            dim(ctx, root, sheet)
        }
    }

    // ---------------------------------------------------------------- the edit page

    private val templates = listOf(
        TtsRules.Template(0, "{{Front}}", "{{FrontSide}}<hr id=answer>{{Back}}<div class=small>{{Extra}}</div>"),
        TtsRules.Template(1, "{{Back}}", "{{FrontSide}}<hr id=answer>{{Front}}"),
    )

    private fun editScreen(ctx: Context, edited: Boolean = false, preview: Boolean = false, answer: Boolean = false, problem: String? = null): View {
        val ss = ShotCtx(ctx)
        val act = attach(NoteEditActivity(), ss)
        val root = act.buildContent()
        val note = NoteForEdit(7, "Basic (and reversed)", listOf("Front", "Back", "Extra"), listOf("Capital of Mongolia?", "Ulaanbaatar", "Also: Ulan Bator"))
        act.bind(NoteEditActivity.Loaded(note, "Geography::Capitals", 2, "", false, templates))
        if (edited) {
            @Suppress("UNCHECKED_CAST") val edits = getField(act, "edits") as List<EditText>
            edits[1].setText("Ulaanbaatar (Ulan Bator)")
        }
        problem?.let { call(act, "setStatus", it) }
        if (preview) {
            setField(act, "answerSide", answer)
            var html = ""
            act.previewSink = { html = it }
            call(act, "showPreview", true)
            assertTrue(html.contains(if (answer) "Ulaanbaatar" else "Capital of Mongolia?"))
            if (!answer) assertFalse(html.contains("<hr id=answer>Ulaanbaatar"))
        }
        return root
    }

    @Test fun editPage() {
        paparazzi.shootWith("r3v7-edit", both) { editScreen(it) }
        paparazzi.shootWith("r3v7-edit-edited", both) { editScreen(it, edited = true) }
        paparazzi.shootWith("r3v7-edit-problem", listOf(Size.PHONE)) {
            editScreen(it, edited = true, problem = "The note was changed in AnkiDroid meanwhile. Open it again to edit the latest text. Nothing was changed.")
        }
    }

    @Test fun editPreview() {
        paparazzi.shootWith("r3v7-preview", both) { editScreen(it, edited = true, preview = true) }
        paparazzi.shootWith("r3v7-preview-answer", listOf(Size.PHONE)) { editScreen(it, edited = true, preview = true, answer = true) }
    }

    @Test fun editLoadingAndProblem() {
        paparazzi.shootWith("r3v7-edit-loading", listOf(Size.PHONE)) { ctx ->
            val act = attach(NoteEditActivity(), ShotCtx(ctx)); val root = act.buildContent()
            call(act, "showMessage", "Loading the note…", false); root
        }
        paparazzi.shootWith("r3v7-edit-cantopen", listOf(Size.PHONE)) { ctx ->
            val act = attach(NoteEditActivity(), ShotCtx(ctx)); val root = act.buildContent()
            call(act, "showMessage", "That note doesn't exist any more.", true); root
        }
    }

    // ---------------------------------------------------------------- move

    @Test fun moveTree() {
        paparazzi.shootWith("r3v7-move", both) { ctx ->
            val (_, root) = studyScreen(ctx)
            val sheet = DeckPickerSheet(ctx, "Move to deck", decks, 3L, DeckPick.Mode.CARD, "Move", {}, {}).content()
            dim(ctx, root, sheet)
        }
        paparazzi.shootWith("r3v7-move-all", both) { ctx ->
            val (_, root) = deckScreen(ctx)
            val sheet = DeckPickerSheet(ctx, "Move all cards to", decks, 2L, DeckPick.Mode.ALL, "Move", {}, {}).content()
            dim(ctx, root, sheet)
        }
    }

    // ---------------------------------------------------------------- delete and move-all dialogs

    @Test fun deleteConfirm() {
        paparazzi.shootWith("r3v7-delete", both) { ctx ->
            val (_, root) = studyScreen(ctx)
            val dlg = DialogShots.capture {
                DeckDialogs.confirm(ctx, "Delete this note?", com.husarp.ankigo.deck.DeckViews.withBold(ctx, com.husarp.ankigo.deck.DeckTexts.deleteBody(2, "Geography::Capitals")), "Delete note", true) {}
            }!!
            FrameLayout(ctx).apply { addView(root, FrameLayout.LayoutParams(-1, -1)); addView(DialogShots.scene(ctx, dlg), FrameLayout.LayoutParams(-1, -1)) }
        }
    }

    private fun progressScene(ctx: Context, job: MoveAllJob): View {
        val dlg = DialogShots.capture { DeckDialogs.MoveProgress(ctx, job) {}.show() }!!
        val (_, root) = deckScreen(ctx)
        return FrameLayout(ctx).apply { addView(root, FrameLayout.LayoutParams(-1, -1)); addView(DialogShots.scene(ctx, dlg), FrameLayout.LayoutParams(-1, -1)) }
    }

    @Test fun moveAllProgress() {
        paparazzi.shootWith("r3v7-moveall-running", both) { ctx -> progressScene(ctx, MoveAllJob("Geography", "Archive").apply { done = 42; total = 120 }) }
        paparazzi.shootWith("r3v7-moveall-done", both) { ctx ->
            progressScene(ctx, MoveAllJob("Geography", "Archive").apply {
                done = 120; total = 123
                result = DeckResult(true, "Moved 120 of 123 cards to \"Archive\" (3 are in a filtered deck). The cards of its subdecks were not touched.", changed = true, done = 120, total = 123, skipped = 3)
            })
        }
        paparazzi.shootWith("r3v7-moveall-stopping", listOf(Size.PHONE)) { ctx ->
            progressScene(ctx, MoveAllJob("Geography", "Archive").apply { done = 60; total = 120; cancel.set(true) })
        }
    }

    @Test fun confirmMoveAll() {
        paparazzi.shootWith("r3v7-moveall-confirm", listOf(Size.PHONE)) { ctx ->
            val dlg = DialogShots.capture {
                DeckDialogs.confirm(ctx, "Move all cards?", com.husarp.ankigo.deck.DeckViews.withBold(ctx, com.husarp.ankigo.deck.DeckTexts.moveAllBody(120, "Geography", "Archive", true)), "Move cards", false) {}
            }!!
            val (_, root) = deckScreen(ctx)
            FrameLayout(ctx).apply { addView(root, FrameLayout.LayoutParams(-1, -1)); addView(DialogShots.scene(ctx, dlg), FrameLayout.LayoutParams(-1, -1)) }
        }
        paparazzi.shootWith("r3v7-failed", listOf(Size.PHONE)) { ctx ->
            val dlg = DialogShots.capture { DeckDialogs.say(ctx, "Can't move the card", DeckOps.FILTERED_SOURCE_CARD) }!!
            val (_, root) = deckScreen(ctx)
            FrameLayout(ctx).apply { addView(root, FrameLayout.LayoutParams(-1, -1)); addView(DialogShots.scene(ctx, dlg), FrameLayout.LayoutParams(-1, -1)) }
        }
    }
}
