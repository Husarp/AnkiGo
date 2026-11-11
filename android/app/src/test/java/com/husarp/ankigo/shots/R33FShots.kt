package com.husarp.ankigo.shots

import android.app.Dialog
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.game.*
import com.husarp.ankigo.ui.*
import com.husarp.ankigo.stats.AnkiStatsStore
import org.junit.Assert.*
import org.junit.After
import org.junit.Rule
import org.junit.Test

class R33FShots {
    @get:Rule val paparazzi = Shots.rule()
    @After fun clean() { AnkiStatsStore.enabled = true }
    private fun all(v: View): List<View> = if (v.visibility == View.GONE) emptyList() else listOf(v) +
        if (v is ViewGroup) (0 until v.childCount).flatMap { all(v.getChildAt(it)) } else emptyList()

    @Test fun statsEffectsFollowTheirCardAndDetachCleanly() {
        for (theme in Theme.entries) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.PHONE, theme))
            val ctx = Shots.files(paparazzi.context)
            FakeGame.install(ctx)
            ctx.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", true).commit()
            AnkiStatsStore.enabled = false
            GameRepo.state!!.day = GameRepo.game!!.dayKey()
            GameRepo.state!!.reviewsToday = 300
            val page = StatsPage(ctx, NoHost)
            val screen = pageScreen(ctx, page)
            // Settle the queued page render before retaining views for attachment/transform checks.
            call(page, "doRender")
            page.onHide(); page.onGone()
            val bar = all(screen).filterIsInstance<BarView>().single { getField(it, "sparkLevel") as Int > 0 }
            val card = (bar.parent as View).parent as ViewGroup
            assertSame(card, getField(bar, "goalEffectHost"))
            var checked = false
            screen.viewTreeObserver.addOnPreDrawListener(object : android.view.ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    screen.viewTreeObserver.removeOnPreDrawListener(this)
                    call(bar, "refreshSparks")
                    assertSame(card, getField(bar, "sparkHost"))
                    assertNotSame(screen.rootView, getField(bar, "sparkHost"))
                    checked = true
                    return true
                }
            })
            paparazzi.snapshot(screen, "f-589-stats-${theme.name.lowercase()}")
            assertTrue(checked)
            // A transformed ancestor must carry the bar and its effects together, as with stretch overscroll.
            card.pivotY = 0f
            card.scaleY = 1.15f
            card.translationY = 16f
            paparazzi.snapshot(screen, "f-589-stretch-${theme.name.lowercase()}")
            assertNull(getField(bar, "sparkDrawable"))
        }
    }

    @Test fun tmPickerUsesDexFiltersAndSelectsOnlyOnce() {
        for (theme in Theme.entries) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.PHONE, theme))
            val ctx = Shots.files(paparazzi.context)
            FakeGame.install(ctx)
            val state = GameRepo.state!!
            val data = GameRepo.data!!
            val target = state.mons.first()
            val move = data.sp(target.species).tmMoves.first()
            // Avoid a duplicate-copy dialog for this single-pick regression.
            state.mons.removeAll { it.species == target.species && it.uid != target.uid }
            val source = TeamPage(ctx, NoHost)
            var picker: DexPage? = null
            var dialog: Dialog? = null
            var selected: Mon? = null
            var selections = 0
            DexMonPicker.interceptShow = { d, p ->
                (p.root.parent as? ViewGroup)?.removeView(p.root)
                picker = p; dialog = d
            }
            try {
                DexMonPicker.show(source, "Teach ${Items.tmName(data, move)} to…", { move in data.sp(it.species).tmMoves }) { selected = it; selections++ }
                val p = picker!!
                call(p, "render")
                assertNull(p.tab)
                val labels = all(p.root).filterIsInstance<TextView>().map { it.text.toString() }
                assertFalse(labels.any { it in listOf("Team", "Box", "All", "Owned", "Seen", "Not owned") })
                @Suppress("UNCHECKED_CAST")
                fun shown() = getField(p, "shown") as List<Species>
                assertTrue(shown().isNotEmpty())
                assertTrue(shown().all { move in it.tmMoves && state.mons.any { m -> m.species == it.num } })
                paparazzi.snapshot(p.root, "f-592-tm-dex-${theme.name.lowercase()}", 300)
                assertFalse("No unknown-species legend after layout", all(p.root).filterIsInstance<TextView>().any { it.text.toString() == "?" })
                val search = all(p.root).filterIsInstance<EditText>().single()
                search.setText("no-such-pokemon")
                assertTrue(shown().isEmpty())
                search.setText("")
                val sheet = p.sortSheet().build(Ui.dp(ctx, 650))
                val sheetLabels = all(sheet).filterIsInstance<TextView>().map { it.text.toString() }
                assertTrue(sheetLabels.any { it.contains("Type", true) })
                assertTrue(sheetLabels.any { it.contains("Generation", true) })
                paparazzi.snapshot(sheet, "f-592-tm-sort-${theme.name.lowercase()}")
                val type = data.sp(target.species).types.first()
                call(p, "setTypeFilter", TypeFilter(setOf(type)))
                call(p, "render")
                assertTrue(shown().all { type in it.types && move in it.tmMoves })
                call(p, "setSort", SortSpec(key = SortKey.NAME))
                call(p, "render")
                assertEquals(shown().map { it.name.lowercase() }.sorted(), shown().map { it.name.lowercase() })
                search.setText(data.sp(target.species).name)
                paparazzi.snapshot(p.root, "f-592-tm-search-${theme.name.lowercase()}", 300)
                val name = all(p.root).filterIsInstance<TextView>().first { it.text.toString().contains(data.sp(target.species).name) }
                (name.parent as View).performClick()
                val request = getField(p, "recipient") as DexPage.Recipient
                request.picked(data.sp(target.species))
                assertSame(target, selected)
                assertEquals(1, selections)
            } finally {
                DexMonPicker.interceptShow = null
                dialog?.dismiss(); picker?.onHide(); picker?.onGone(); source.onGone()
            }
        }
    }
    @Test fun duplicateSpeciesOffersOnlyEligibleCopiesAndCancelDoesNothing() {
        val ctx = Shots.files(paparazzi.context)
        FakeGame.install(ctx)
        val state = GameRepo.state!!
        val first = state.mons.first()
        val duplicate = TestData.mon(GameRepo.data!!, first.species, 10, listOf("tackle"), "duplicate")
        state.mons += duplicate
        var picker: DexPage? = null
        var dialog: Dialog? = null
        var copies: AppDialog? = null
        var selected: Mon? = null
        val source = TeamPage(ctx, NoHost)
        DexMonPicker.interceptShow = { d, p -> dialog = d; picker = p }
        Pickers.interceptApp = { copies = it }
        try {
            DexMonPicker.show(source, "Teach TM to…", { it.species == first.species && it.uid != first.uid }) { selected = it }
            val request = getField(picker!!, "recipient") as DexPage.Recipient
            request.picked(GameRepo.data!!.sp(first.species))
            assertSame("Ineligible copy is never offered", duplicate, selected)
            assertNull(copies)
            selected = null
            picker!!.onHide(); picker!!.onGone()
            DexMonPicker.show(source, "Teach TM to…", { it.species == first.species }) { selected = it }
            val both = getField(picker!!, "recipient") as DexPage.Recipient
            both.picked(GameRepo.data!!.sp(first.species))
            assertNotNull("Multiple copies require a choice", copies)
            assertNull(selected)
            copies!!.dismiss()
            assertNull("Cancel never picks a copy", selected)
            assertTrue(state.mons.contains(first) && state.mons.contains(duplicate))
        } finally {
            Pickers.interceptApp = null; DexMonPicker.interceptShow = null
            dialog?.dismiss(); picker?.onHide(); picker?.onGone(); source.onGone()
        }
    }

}
