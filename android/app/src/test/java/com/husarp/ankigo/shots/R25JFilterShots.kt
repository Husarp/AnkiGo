package com.husarp.ankigo.shots

import android.view.View
import android.view.ViewGroup
import android.view.Gravity
import android.widget.*
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.Anki
import com.husarp.ankigo.study.*
import com.husarp.ankigo.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class R25JFilterShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    private fun views(v: View): List<View> = listOf(v) + if (v is ViewGroup) (0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList()
    private fun text(v: View, s: String) = views(v).filterIsInstance<TextView>().first { it.text.toString() == s }
    private fun act(ctx: android.content.Context): Pair<FilterActivity, View> {
        val act = attach(FilterActivity(), ShotCtx(ctx))
        val root = act.buildContent()
        setField(act, "decks", listOf(Anki.Deck(1, "Japanese", 1, 5, 2, false), Anki.Deck(2, "Geography", 0, 2, 0, false)))
        setField(act, "filter", StudyFilter(listOf(DeckFilter(1, "Japanese"), DeckFilter(2, "Geography"))))
        val cache = getField(act, "tagCache") as StudyTags
        cache.put("Japanese", listOf("jlpt::n5", "verbs", "vocabulary", "grammar"), cache.revision)
        cache.put("Geography", emptyList(), cache.revision)
        call(act, "render", false)
        (getField(act, "count") as TextView).text = "186 cards match"
        call(act, "enableStart", true)
        return act to root
    }
    @Test fun optionBAndRemoveDeck() {
        paparazzi.shootWith("r25j-choose-study", listOf(Size.PHONE, Size.SMALL, Size.SPLIT)) { ctx ->
            val (act, root) = act(ctx)
            val labels = views(root).filterIsInstance<TextView>().map { it.text.toString() }
            assertTrue("This deck has no tags" in labels)
            assertTrue("1 · STARTING POINT" in labels)
            assertTrue("2 · DECKS & TAGS" in labels)
            assertTrue("3 · KEEP THIS SETUP" in labels)
            assertTrue(views(root).any { it.contentDescription == "About this screen" })
            // Removing a deck changes the actual search, not just its chip.
            views(root).first { it.contentDescription == "Remove Geography" }.performClick()
            val filter = getField(act, "filter") as StudyFilter
            assertEquals(listOf("Japanese"), filter.decks.map { it.name })
            assertFalse(filter.search().contains("Geography"))
            // Restore the two-deck example for the image.
            setField(act, "filter", StudyFilter(listOf(DeckFilter(1, "Japanese"), DeckFilter(2, "Geography"))))
            call(act, "render", false)
            setField(act, "generation", (getField(act, "generation") as Int) + 1)
            (getField(act, "count") as TextView).text = "186 cards match"
            call(act, "enableStart", true)
            root
        }
    }
    private fun shotSheet(state: String) {
        paparazzi.shootWith("r25j-sheet-$state", listOf(Size.PHONE, Size.SPLIT)) { ctx ->
            val (_, root) = act(ctx)
            val sheet = FilterSheet(ctx, if (state == "info") "Choose your cards" else "Japanese tags") {}
            when (state) {
                "info" -> sheet.info {}
                "loading" -> sheet.loading()
                "error" -> sheet.error {}
                else -> sheet.choices(if (state == "empty") emptyList() else listOf("jlpt::n5", "verbs", "vocabulary", "grammar").map { it to it }, setOf("verbs"), "This deck has no tags", "Only tags found in Japanese. Choose any matching tag.", {}) {}
            }
            FrameLayout(ctx).apply {
                addView(root, FrameLayout.LayoutParams(-1, -1))
                addView(View(ctx).apply { setBackgroundColor(0x7a000000) }, FrameLayout.LayoutParams(-1, -1))
                addView(SheetKit.host(ctx, sheet.view, {}, {}), FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
            }
        }
    }
    @Test fun loadedTags() = shotSheet("loaded")
    @Test fun loadingTags() = shotSheet("loading")
    @Test fun emptyTags() = shotSheet("empty")
    @Test fun failedTags() = shotSheet("error")
    @Test fun infoSheet() = shotSheet("info")
    @Test fun searchKeepsDraftPicksAndApplyCommitsOnce() {
        paparazzi.shootWith("r25j-tags-selection", listOf(Size.PHONE)) { ctx ->
            var result: Set<String>? = null
            var applies = 0
            var closes = 0
            val sheet = FilterSheet(ctx, "Japanese tags") { closes++ }
            sheet.choices(listOf("task", "vocabulary").map { it to it }, setOf("task"), "This deck has no tags", "Choose tags", { closes++ }) { result = it; applies++ }
            val search = views(sheet.view).filterIsInstance<EditText>().single()
            search.setText("voca")
            (text(sheet.view, "vocabulary") as CheckBox).isChecked = true
            search.setText("task")
            assertTrue(views(sheet.view).filterIsInstance<CheckBox>().single { it.text.toString() == "task" }.isChecked)
            assertNull(result)
            text(sheet.view, "Apply").performClick()
            assertEquals(setOf("task", "vocabulary"), result)
            assertEquals(1, applies); assertEquals(1, closes)
            sheet.view
        }
    }
    @Test fun readyMadeSetupsReplaceAllPriorChoicesAndCachedReadsReturnImmediately() {
        paparazzi.shootWith("r25j-presets", listOf(Size.PHONE)) { ctx ->
            val (act, root) = act(ctx)
            setField(act, "filter", StudyFilter(listOf(DeckFilter(99, "Old", setOf("old"), true, true)), setOf("global")))
            // Buttons use the newest setup, even if they were created before it changed.
            text(root, "All decks · due cards\nDue cards across every deck branch.").performClick()
            val all = getField(act, "filter") as StudyFilter
            assertEquals(listOf(1L, 2L), all.decks.map { it.id })
            assertTrue(all.tags.isEmpty())
            assertTrue(all.decks.all { !it.free && !it.ignoreGlobal && it.tags.isEmpty() })
            var tags: List<String>? = null
            call(act, "requestTags", DeckFilter(1, "Japanese"), { r: Result<List<String>> -> tags = r.getOrThrow() })
            assertEquals(listOf("jlpt::n5", "verbs", "vocabulary", "grammar"), tags)
            assertTrue((getField(act, "tagReads") as Map<*, *>).isEmpty())
            text(root, "New cards\nUnstudied cards across every deck.").performClick()
            assertTrue((getField(act, "filter") as StudyFilter).newOnly)
            text(root, "Most cards waiting\nDue cards in your busiest deck branch.").performClick()
            assertEquals(StudyFilter(listOf(DeckFilter(1, "Japanese"))), getField(act, "filter"))
            setField(act, "generation", (getField(act, "generation") as Int) + 1)
            (getField(act, "count") as TextView).text = "Counting…"
            root
        }
    }
    @Test fun repeatedTagTapsReuseTheExistingWindowBeforeAnyReadStarts() {
        paparazzi.shootWith("r25j-single-sheet", listOf(Size.PHONE)) { ctx ->
            val (act, root) = act(ctx)
            val existing = object : android.app.Dialog(ctx) {
                override fun isShowing() = true
            }
            setField(act, "activeSheet", existing)
            repeat(4) {
                call(act, "tagsSheet", "Japanese tags", listOf(DeckFilter(1, "Japanese")), emptySet<String>(), { _: Set<String> -> fail("Duplicate sheet applied") })
            }
            assertSame(existing, getField(act, "activeSheet"))
            assertTrue((getField(act, "tagReads") as Map<*, *>).isEmpty())
            root
        }
    }
    @Test fun cancelLeavesOriginalSelectionAlone() {
        paparazzi.shootWith("r25j-tags-cancel", listOf(Size.PHONE)) { ctx ->
            var applied = false
            var closed = false
            val sheet = FilterSheet(ctx, "Tags") {}
            sheet.choices(listOf("task" to "task"), emptySet(), "This deck has no tags", "Choose tags", { closed = true }) { applied = true }
            (text(sheet.view, "task") as CheckBox).isChecked = true
            text(sheet.view, "Cancel").performClick()
            assertTrue(closed); assertFalse(applied)
            sheet.view
        }
    }
}
