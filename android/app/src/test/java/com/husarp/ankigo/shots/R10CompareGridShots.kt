package com.husarp.ankigo.shots

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.R
import com.husarp.ankigo.ui.Cell
import com.husarp.ankigo.ui.CompareFit
import com.husarp.ankigo.ui.CompareMode
import com.husarp.ankigo.ui.CompareMovePopup
import com.husarp.ankigo.ui.ComparePage
import com.husarp.ankigo.ui.ComparePick
import com.husarp.ankigo.ui.CompareScale
import com.husarp.ankigo.ui.CompareStore
import com.husarp.ankigo.ui.CompareTable
import com.husarp.ankigo.ui.RowChoice
import com.husarp.ankigo.ui.SectionChoice
import com.husarp.ankigo.ui.TypeChips
import com.husarp.ankigo.ui.Ui
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/*
 * R10-F: Compare. B8 colours vs the reference (grey reference cells, relative fills), B9 sections (gaps, headers, fold), B10 move cells
 * and the move popup, B11 the reference tint only behind cells. PNGs: r10cmp-<what>-<size>-<theme>.
 */
class R10CompareGridShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    @After fun reset() {
        CompareMovePopup.intercept = null
        CompareMovePopup.dismiss()
        val ctx = Shots.files(paparazzi.context)
        CompareStore.saveCollapsed(ctx, SectionChoice())
        CompareStore.saveRows(ctx, RowChoice())
        CompareStore.saveFit(ctx, CompareFit.AUTO)
    }

    private val float = Variant("float", Size.FLOAT.wDp, Size.FLOAT.hDp)
    private val phoneFloat = listOf(Variants.PHONE, float)
    private val five = listOf(ComparePick.Owned("t1"), ComparePick.Owned("t2"), ComparePick.Owned("t3"), ComparePick.Dex(150), ComparePick.Dex(3))
    private val vs = CompareMode.VS_REFERENCE
    private val best = CompareMode.BEST_OF_ALL

    private fun all(v: View, out: MutableList<View> = ArrayList()): List<View> {
        out += v
        if (v is ViewGroup) for (i in 0 until v.childCount) all(v.getChildAt(i), out)
        return out
    }

    private fun bg(v: View): Int? = (v.background as? ColorDrawable)?.color

    private val onlyMoves = RowChoice(setOf("level", "hp", "atk", "def", "spa", "spd", "spe", "total", "base_hp", "base_atk", "base_def", "base_spa", "base_spd",
        "base_spe", "base_total", "nature", "ivs", "weak", "resist", "immune", "evo", "becomes"))

    private fun page(ctx: Context, mode: CompareMode, picks: List<ComparePick> = five, collapsed: SectionChoice = SectionChoice(), rows: RowChoice = RowChoice()): ComparePage {
        CompareStore.saveMode(ctx, mode); CompareStore.saveRows(ctx, rows); CompareStore.saveFit(ctx, CompareFit.AUTO)
        CompareStore.saveCollapsed(ctx, collapsed)
        return ComparePage(ctx, NoHost, picks)
    }

    /** Page + screen, rendered once more so the views are the ones of the loaded game. */
    private fun shown(ctx: Context, p: ComparePage): View = pageScreen(ctx, p).also { p.render() }

    /** The data rows of the table: the horizontal LinearLayouts whose first child holds a label. */
    private fun dataRows(root: View, columns: Int): List<LinearLayout> = all(root).filterIsInstance<LinearLayout>().filter {
        it.orientation == LinearLayout.HORIZONTAL && it.childCount == columns + 1 && it.getChildAt(0) is FrameLayout && bg(it) != null &&
            all(it.getChildAt(0)).any { t -> t is TextView }
    }

    private fun labelOf(row: LinearLayout): String = all(row.getChildAt(0)).filterIsInstance<TextView>().first().text.toString()

    private fun headers(root: View): List<View> = all(root).filter { it.isClickable && it.contentDescription?.let { d -> d.endsWith(", expanded") || d.endsWith(", collapsed") } == true }

    private fun isGreen(c: Int) = ((c shr 8) and 0xFF) > ((c shr 16) and 0xFF) + 15 && ((c shr 8) and 0xFF) > (c and 0xFF) + 15
    private fun isRed(c: Int) = ((c shr 16) and 0xFF) > ((c shr 8) and 0xFF) + 15 && ((c shr 16) and 0xFF) > (c and 0xFF) + 15

    // ---------- B8: colours vs the reference ----------

    private fun checkVs(ctx: Context, root: View) {
        val data = GameRepo.data!!
        val table = CompareTable.build(data, GameRepo.state!!, five, false)
        val dark = TypeChips.isDark(ctx)
        val atk = table.row("atk")
        val refValue = (atk.cells[0] as Cell.Text).value!!
        val row = dataRows(root, five.size).first { labelOf(it) == "Atk" }
        val band = bg(row)!!
        // the reference's cell: a grey band, never green / red
        val refCell = row.getChildAt(1)
        val refBg = bg(refCell)
        assertNotNull("the reference cell has a tint", refBg)
        assertEquals(CompareScale.neutral(band, Ui.c(ctx, R.color.ag_ov)), refBg)
        assertFalse(isGreen(refBg!!) || isRed(refBg))
        // every other cell: the fill the rule gives (none = no background of its own)
        var better = 0; var worse = 0
        for (i in 1 until five.size) {
            val v = (atk.cells[i] as Cell.Text).value ?: continue
            val expected = CompareScale.vsRef(v, refValue, band, dark).fill
            assertEquals("atk column $i ($v vs $refValue)", expected, bg(row.getChildAt(i + 1)))
            if (expected != null && v > refValue) { better++; assertTrue(isGreen(expected)) }
            if (expected != null && v < refValue) { worse++; assertTrue(isRed(expected)) }
        }
        assertTrue("some cell is filled (better $better, worse $worse)", better + worse > 0)
        // a strong fill's text takes the fill's ink, and the +/− line too
        for (i in 1 until five.size) {
            val v = (atk.cells[i] as Cell.Text).value ?: continue
            val look = CompareScale.vsRef(v, refValue, band, dark)
            if (look.ink != null) {
                val texts = all(row.getChildAt(i + 1)).filterIsInstance<TextView>()
                assertTrue("all texts of a strongly filled cell wear the ink", texts.isNotEmpty() && texts.all { it.currentTextColor == look.ink })
            }
        }
        // the ref sub line stays on the reference's number
        assertTrue(all(refCell).filterIsInstance<TextView>().any { it.text == "ref" })
    }

    @Test fun vsReference() = paparazzi.shootPage("r10cmp-vs", phoneFloat) { ctx ->
        val p = page(ctx, vs)
        val screen = shown(ctx, p)
        checkVs(ctx, p.root)
        screen
    }

    @Test fun bestOfAllKeepsItsRule() = paparazzi.shootPage("r10cmp-best", phoneFloat) { ctx ->
        val p = page(ctx, best)
        val screen = shown(ctx, p)
        val table = CompareTable.build(GameRepo.data!!, GameRepo.state!!, five, false)
        val vals = table.row("atk").cells.map { (it as Cell.Text).value }
        val row = dataRows(p.root, five.size).first { labelOf(it) == "Atk" }
        val band = bg(row)!!
        val dark = TypeChips.isDark(ctx)
        val pos = com.husarp.ankigo.ui.CompareModes.scale(vals)
        for (i in 0 until five.size) {
            val want = pos[i]?.let { CompareScale.look(it, band, dark).fill }
            assertEquals("column $i", want, bg(row.getChildAt(i + 1)))
        }
        // no column is a reference there: no head says so, no cell is the grey
        assertTrue(all(p.root).filterIsInstance<TextView>().none { it.text == "reference" || it.text == "ref" })
        screen
    }

    @Test fun legendWords() = paparazzi.shootPage("r10cmp-legend", listOf(Variants.PHONE), themes = listOf(Theme.LIGHT)) { ctx ->
        val vsPage = page(ctx, vs)
        val s1 = shown(ctx, vsPage)
        val words = all(vsPage.root).filterIsInstance<TextView>().map { it.text.toString() }
        assertTrue(words.toString(), "worse" in words && "better than the reference" in words)
        val bestPage = page(ctx, best)
        shown(ctx, bestPage)
        val bw = all(bestPage.root).filterIsInstance<TextView>().map { it.text.toString() }
        assertTrue(bw.toString(), "worst" in bw && "best" in bw && "same value, same colour" in bw)
        s1
    }

    // ---------- B9 / B11: sections ----------

    @Test fun sectionsAndTint() = paparazzi.shootPage("r10cmp-sections", phoneFloat) { ctx ->
        val p = page(ctx, vs)
        val screen = shown(ctx, p)
        val hs = headers(p.root)
        assertTrue("Stats, Base stats, Build, Moves, Matchups, Evolution: ${hs.map { it.contentDescription }}", hs.size >= 5)
        assertTrue(hs.all { it.contentDescription.endsWith(", expanded") })
        val mantle = Ui.c(ctx, R.color.ag_mantle)
        val gap = Ui.dp(ctx, 11)
        for (h in hs) {
            // B11: nothing in a header carries the reference tint: its own background is the plain mantle and its children are text / icon only
            assertEquals(mantle, bg(h))
            assertTrue("no tinted child in a header", all(h).filter { it !== h }.none { bg(it) != null })
            // B9: a gap of page background (no background of its own) right above it, except at the very top
            val rows = h.parent as ViewGroup
            val at = rows.indexOfChild(h)
            if (at > 1) {
                val g = rows.getChildAt(at - 1)
                assertNull("the gap shows the page background", g.background)
                assertEquals(gap, g.layoutParams.height)
            }
        }
        // the section title is bigger than a row label
        val title = all(hs.first()).filterIsInstance<TextView>().first()
        val rowLabel = all(dataRows(p.root, five.size).first()).filterIsInstance<TextView>().first()
        assertTrue("title ${title.textSize} > label ${rowLabel.textSize}", title.textSize > rowLabel.textSize)
        // "Stats" is sortable: its hint stays
        assertTrue(hs.map { all(it).filterIsInstance<TextView>().first().text.toString() }.any { it.contains("TAP A NAME TO SORT") })
        screen
    }

    @Test fun foldASection() {
        paparazzi.shootPage("r10cmp-folded", phoneFloat) { ctx ->
            val p = page(ctx, vs)
            val screen = shown(ctx, p)
            val labelsBefore = dataRows(p.root, five.size).map(::labelOf)
            assertTrue("Atk" in labelsBefore)
            val stats = headers(p.root).first { it.contentDescription.startsWith("Stats,") }
            stats.performClick()
            assertEquals(setOf("Stats"), CompareStore.loadCollapsed(ctx).collapsed) // remembered
            p.render()
            val after = dataRows(p.root, five.size).map(::labelOf)
            assertFalse("the Stats rows are gone: $after", "Atk" in after)
            val h = headers(p.root).first { it.contentDescription.startsWith("Stats,") }
            assertEquals("Stats, collapsed", h.contentDescription)
            assertTrue("the other sections' rows are still there: $after", "Nature" in after)
            // a second tap opens it again
            h.performClick(); p.render()
            assertEquals(emptySet<String>(), CompareStore.loadCollapsed(ctx).collapsed)
            assertTrue("Atk" in dataRows(p.root, five.size).map(::labelOf))
            // and the fold from the prefs shows at the next start
            CompareStore.saveCollapsed(ctx, SectionChoice(setOf("Moves", "Base stats")))
            val p2 = ComparePage(ctx, NoHost, five)
            val screen2 = shown(ctx, p2)
            assertTrue(headers(p2.root).any { it.contentDescription == "Moves, collapsed" })
            assertFalse(dataRows(p2.root, five.size).map(::labelOf).contains("Moves"))
            screen2
        }
    }

    @Test fun foldedLook() = paparazzi.shootPage("r10cmp-folded-look", phoneFloat) { ctx ->
        val p = page(ctx, vs, collapsed = SectionChoice(setOf("Stats", "Moves")))
        val screen = shown(ctx, p)
        assertTrue(headers(p.root).any { it.contentDescription == "Stats, collapsed" })
        screen
    }

    // ---------- B10: move cells and the popup ----------

    private fun moveCells(root: View): List<View> = all(root).filter { it.isClickable && it.contentDescription?.endsWith(". Tap for details") == true }

    /** A timer the test fires by hand. */
    private class FakeTimer : CompareMovePopup.Timer {
        var pending: Runnable? = null
        var delay = 0L
        override fun after(ms: Long, r: Runnable) { pending = r; delay = ms }
        override fun cancel(r: Runnable) { if (pending === r) pending = null }
        fun fire() { val r = pending; pending = null; r?.run() }
    }

    @Test fun moveCellsHaveNoPowerLine() = paparazzi.shootPage("r10cmp-moves", phoneFloat) { ctx ->
        val p = page(ctx, vs, rows = onlyMoves)
        val screen = shown(ctx, p)
        val cells = moveCells(p.root)
        assertTrue("move cells: ${cells.size}", cells.size >= 3)
        for (c in cells) {
            val texts = all(c).filterIsInstance<TextView>().map { it.text.toString() }
            assertTrue("a move cell has no Pow line: $texts", texts.none { it.startsWith("Pow") })
            assertTrue("name + type: $texts", texts.isNotEmpty())
        }
        screen
    }

    @Test fun tapShowsThePopupAndItCloses() {
        paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.PHONE, Theme.LIGHT))
        val ctx = Shots.files(paparazzi.context)
        FakeGame.install(ctx)
        val timer = FakeTimer()
        val old = CompareMovePopup.timer
        CompareMovePopup.timer = timer
        try {
            val p = page(ctx, vs)
            shown(ctx, p)
            var card: View? = null
            CompareMovePopup.intercept = { card = it }
            val cell = moveCells(p.root).first { it.contentDescription.startsWith("Thunderbolt,") }
            cell.performClick()
            assertTrue(CompareMovePopup.isShowing)
            assertEquals(CompareMovePopup.SHOW_MS, timer.delay)
            val texts = all(card!!).filterIsInstance<TextView>().map { it.text.toString() }
            assertTrue(texts.toString(), "Thunderbolt" in texts && "Special" in texts && "90" in texts && "100" in texts && "Power" in texts && "Accuracy" in texts)
            assertTrue("effect line: $texts", texts.any { it.contains("Paralyze", true) || it.contains("paralyz", true) })
            // the timer closes it
            timer.fire()
            assertFalse(CompareMovePopup.isShowing)
            // a tap on the card closes it too
            cell.performClick()
            assertTrue(CompareMovePopup.isShowing)
            card!!.performClick()
            assertFalse(CompareMovePopup.isShowing)
            assertNull("the timer is cancelled with it", timer.pending)
            // one at a time: a second move replaces the first
            cell.performClick()
            val firstCard = card
            moveCells(p.root).first { it.contentDescription.startsWith("Tackle,") }.performClick()
            assertTrue(CompareMovePopup.isShowing)
            assertTrue(card !== firstCard)
            // scrolling the table closes it
            val hsv = all(p.root).filterIsInstance<HorizontalScrollView>().last() // the table's (the chip row above has one too)
            hsv.measure(View.MeasureSpec.makeMeasureSpec(Ui.dp(ctx, 400), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(Ui.dp(ctx, 600), View.MeasureSpec.EXACTLY))
            hsv.layout(0, 0, hsv.measuredWidth, hsv.measuredHeight)
            hsv.scrollTo(Ui.dp(ctx, 30), 0)
            assertFalse("a scroll closes the popup", CompareMovePopup.isShowing)
        } finally {
            CompareMovePopup.timer = old
            CompareMovePopup.intercept = null
            CompareMovePopup.dismiss()
        }
    }

    // R10-FIX (Codex 3): the popup goes with its screen: anchor detached -> closed, timer cancelled, no reference kept
    @Test fun popupClosesWhenItsAnchorGoes() {
        paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.PHONE, Theme.LIGHT))
        val ctx = Shots.files(paparazzi.context)
        FakeGame.install(ctx)
        val timer = FakeTimer()
        val old = CompareMovePopup.timer
        CompareMovePopup.timer = timer
        try {
            val p = page(ctx, vs)
            shown(ctx, p)
            CompareMovePopup.intercept = { }
            val cell = moveCells(p.root).first { it.contentDescription.startsWith("Thunderbolt,") }
            cell.performClick()
            assertTrue(CompareMovePopup.isShowing)
            assertNotNull("a pending auto-close timer", timer.pending)
            val l = CompareMovePopup.detachListener
            assertNotNull("the popup watches its anchor", l)
            l!!.onViewDetachedFromWindow(cell)
            assertFalse("closed with the anchor", CompareMovePopup.isShowing)
            assertNull("timer cancelled", timer.pending)
            assertNull("content reference dropped", CompareMovePopup.content)
            assertNull("listener reference dropped", CompareMovePopup.detachListener)
            // a normal close drops the listener too
            cell.performClick()
            assertNotNull(CompareMovePopup.detachListener)
            CompareMovePopup.dismiss()
            assertNull(CompareMovePopup.detachListener)
        } finally {
            CompareMovePopup.timer = old
            CompareMovePopup.intercept = null
            CompareMovePopup.dismiss()
        }
    }

    @Test fun popupContent() {
        for (theme in Theme.entries) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.PHONE, theme))
            val ctx = Shots.files(paparazzi.context)
            FakeGame.install(ctx)
            val d = GameRepo.data!!
            fun frame(id: String): View = FrameLayout(ctx).apply {
                setBackgroundColor(Ui.c(ctx, R.color.ag_base))
                addView(CompareMovePopup.content(ctx, d.move(id)!!), FrameLayout.LayoutParams(-2, -2).also { it.setMargins(Ui.dp(ctx, 40), Ui.dp(ctx, 40), 0, 0) })
            }
            // a damaging move (power + accuracy + effect), and a status move that never misses (neither number)
            val attack = frame("thunderbolt"); val status = frame("swordsdance")
            val st = all(status).filterIsInstance<TextView>().map { it.text.toString() }
            assertTrue(st.toString(), "Power" !in st && "Accuracy" !in st && "Status" in st)
            paparazzi.snapshot(FrameLayout(ctx).apply { addView(attack, FrameLayout.LayoutParams(-1, Ui.dp(ctx, 220))) }, "r10cmp-popup-attack-${theme.name.lowercase()}", 1000)
            paparazzi.snapshot(FrameLayout(ctx).apply { addView(status, FrameLayout.LayoutParams(-1, Ui.dp(ctx, 220))) }, "r10cmp-popup-status-${theme.name.lowercase()}", 1000)
        }
    }
}
