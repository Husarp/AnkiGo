package com.husarp.ankigo.shots

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.ImageView
import android.widget.EditText
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.game.*
import com.husarp.ankigo.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class R27BTest {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    private fun all(v: View): List<View> = listOf(v) + if (v is ViewGroup) (0 until v.childCount).flatMap { all(v.getChildAt(it)) } else emptyList()

    @Test fun speciesHeaderFitsAllWidthsAndLargeFont() {
        for (width in listOf(320, 360, 412)) for (scale in listOf(1f, 2f)) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.SMALL, Theme.DARK).copy(screenWidth = width * 2, fontScale = scale))
            val ctx = Shots.files(paparazzi.context)
            FakeGame.install(ctx)
            val st = GameRepo.state!!
            val before = st.seen.toSet()
            val dialog = AppDialogShots.capture { DexDetail.show(TeamPage(ctx, NoHost), GameRepo.data!!.sp(st.main!!.species)) }!!
            val view = AppDialogShots.scene(ctx, dialog)
            paparazzi.snapshot(view, "r27b-header-$width-$scale")
            val label = all(view).filterIsInstance<TextView>().first { it.text.toString() == "Species info" }
            val title = all(view).filterIsInstance<TextView>().first { it.text.toString() == "Details" }
            assertEquals(1, title.layout.lineCount)
            assertTrue(label.width > 0)
            assertTrue(label.right <= (label.parent as View).width)
            for (line in 0 until label.layout.lineCount) assertTrue(label.layout.getLineWidth(line) <= label.width + 1)
            assertEquals(before, st.seen)
            AppDialogShots.capture { MonDetail.show(TeamPage(ctx, NoHost), GameRepo.data!!, st.main!!) }
            assertEquals(before, st.seen)
        }
    }

    @Test fun discoveryAndEvolutionStylesStaySeparate() {
        val ctx = Shots.files(paparazzi.context)
        val data = TestData.world()
        val st = TestData.state(data)
        val sp = data.sp(st.main!!.species)
        val before = st.seen.toSet()
        val rows = android.widget.LinearLayout(ctx).apply { orientation = 1 }
        val images = mutableListOf<ImageView>()
        for (status in 0..2) {
            st.caught.clear(); if (status == 2) st.caught += sp.num
            val row = EvolutionRows(ctx, data, EvoNode(sp, Evolution(sp.num, EvoKind.LEVEL, level = 20), emptyList()), sp,
                if (status == 1) setOf(sp.num) else emptySet(), null, st)
            images += all(row).filterIsInstance<ImageView>().first()
            rows.addView(row)
        }
        paparazzi.snapshot(rows, "r27b-evolution-unseen-seen-caught")
        assertNotNull(images[0].colorFilter)
        assertEquals(0.55f, images[1].alpha)
        assertEquals(1f, images[2].alpha)
        assertNull(images[2].colorFilter)
        assertEquals(before, st.seen)
    }

    @Test fun natureAndItemCopy() {
        val ctx = Shots.files(paparazzi.context)
        val data = TestData.world()
        val st = TestData.state(data)
        val mon = st.main!!
        val sp = data.sp(mon.species)
        val col = android.widget.LinearLayout(ctx).apply { orientation = 1 }
        val stats = DetailSections.statsBody(ctx, StatCalc.stats(sp, mon), mon.level, Nature.QUIET, sp, true)
        col.addView(stats)
        assertFalse(all(stats).filterIsInstance<TextView>().any { it.text.startsWith("Nature:") })
        val training = DetailSections.training(ctx, Mon(mon.uid, mon.species, mon.name, mon.level, 0, mon.ivs, mon.evs, Nature.QUIET, mon.gender, false, mon.ability, mon.moves, mon.hp), true)
        val natureLines = all(training).filterIsInstance<TextView>().map { it.text.toString() }
        assertTrue("Nature" in natureLines)
        assertTrue("SpA +10%" in natureLines)
        assertTrue("Spe −10%" in natureLines)
        val e = Evolution(sp.num, EvoKind.ITEM, item = "fire-stone")
        st.bag.clear()
        val missing = EvolutionRows.explanationContent(ctx, data, e, sp, setOf(sp.num), st, mon) {}
        assertEquals("This can't be undone.", all(missing).filterIsInstance<TextView>().last().text.toString())
        val missingText = all(missing).filterIsInstance<TextView>().mapNotNull { it.text as? android.text.Spanned }
            .single { it.toString().contains("Shop") }
        val links = missingText.getSpans(0, missingText.length, android.text.style.ClickableSpan::class.java)
        assertEquals(listOf("Shop"), links.map { missingText.subSequence(missingText.getSpanStart(it), missingText.getSpanEnd(it)).toString() })
        col.addView(missing)
        st.bag["fire-stone"] = 2
        col.addView(EvolutionRows.explanationContent(ctx, data, e, sp, setOf(sp.num), st, mon) {})
        paparazzi.snapshot(col, "r27b-nature-item-unowned-owned")
    }

    @Test fun boxSearchFiltersWithoutReplacingField() {
        paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.SMALL, Theme.LIGHT))
        val ctx = Shots.files(paparazzi.context)
        FakeGame.install(ctx)
        val page = TeamPage(ctx, NoHost, TeamSection.BOX)
        val view = pageScreen(ctx, page)
        paparazzi.snapshot(view, "r27b-box-search")
        val field = all(view).filterIsInstance<EditText>().first { it.hint.toString() == "Search name or number" }
        field.setText("no-such-pokemon")
        TeamPage::class.java.getDeclaredMethod("render").apply { isAccessible = true }.invoke(page)
        assertSame(field, all(view).first { it === field })
        assertTrue(all(view).filterIsInstance<TextView>().any { it.text.toString() == "No Pokémon match your search." })
        paparazzi.snapshot(view, "r27b-box-no-results")
        field.setText("25"); TeamPage::class.java.getDeclaredMethod("render").apply { isAccessible = true }.invoke(page)
        paparazzi.snapshot(view, "r27b-box-number-search")
        val data = GameRepo.data!!
        val mon = GameRepo.state!!.mons.first()
        assertTrue(BoxSearch.matches(data, mon, "#${data.sp(mon.species).baseNum}"))
        assertTrue(BoxSearch.matches(data, mon, data.sp(mon.species).name.lowercase()))
        assertFalse(BoxSearch.matches(data, mon, "no-such-pokemon"))
    }
}
