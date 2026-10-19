package com.husarp.ankigo.shots

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.R
import com.husarp.ankigo.game.Mon
import com.husarp.ankigo.game.shownName
import com.husarp.ankigo.ui.AppMenu
import com.husarp.ankigo.ui.CompareFit
import com.husarp.ankigo.ui.CompareMode
import com.husarp.ankigo.ui.CompareRowText
import com.husarp.ankigo.ui.CompareState
import com.husarp.ankigo.ui.ComparePage
import com.husarp.ankigo.ui.ComparePick
import com.husarp.ankigo.ui.CompareGroup
import com.husarp.ankigo.ui.CompareStore
import com.husarp.ankigo.ui.CompareTable
import com.husarp.ankigo.ui.RowChoice
import com.husarp.ankigo.ui.SectionChoice
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
 * R10-G: Compare. B7 the Rows sheet (section headers, big abbreviation, two small lines), B12 sort by tap (tap cycle, long-press sort menu,
 * no sort chip), B13 the column menu's team actions and Details. PNGs: r10cmpsheet-<what>-<size>-<theme>.
 */
class R10CompareSheetShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    @After fun reset() {
        AppMenu.intercept = null
        val ctx = Shots.files(paparazzi.context)
        CompareStore.saveRows(ctx, RowChoice())
        CompareStore.saveCollapsed(ctx, SectionChoice())
        CompareStore.saveFit(ctx, CompareFit.AUTO)
    }

    private val five = listOf(ComparePick.Owned("t1"), ComparePick.Owned("t2"), ComparePick.Owned("t3"), ComparePick.Dex(150), ComparePick.Dex(3))
    private val vs = CompareMode.VS_REFERENCE
    private val phone = Variants.PHONE
    private val float = Variant("float", Size.FLOAT.wDp, Size.FLOAT.hDp)
    private val phoneTall = Variant("phone", 412, 2300)
    private val floatTall = Variant("float", 340, 2300)

    private fun all(v: View, out: MutableList<View> = ArrayList()): List<View> {
        out += v
        if (v is ViewGroup) for (i in 0 until v.childCount) all(v.getChildAt(i), out)
        return out
    }

    private fun page(ctx: Context, picks: List<ComparePick> = five, seed: (CompareState) -> Unit = {}): ComparePage {
        CompareStore.saveMode(ctx, vs); CompareStore.saveRows(ctx, RowChoice()); CompareStore.saveFit(ctx, CompareFit.AUTO)
        CompareStore.saveGroup(ctx, CompareGroup.STATS); CompareStore.saveCollapsed(ctx, SectionChoice())
        return ComparePage(ctx, NoHost, picks).also { it.onNewState = seed }
    }

    private fun shown(ctx: Context, p: ComparePage): View = pageScreen(ctx, p).also { p.render() }

    private fun labelCell(root: View, label: String): View = all(root).first { it.isClickable && it.contentDescription?.startsWith("Sort by $label.") == true }
    private fun infoCell(root: View, label: String): View = all(root).first { it.isClickable && it.contentDescription?.startsWith("$label.") == true }

    // ---------- B7: the Rows sheet ----------

    private fun sheetShoot(name: String, variants: List<Variant>, scales: List<Float>, check: (Context, View) -> Unit) {
        for (v in variants) for (scale in scales) for (theme in Theme.entries) {
            paparazzi.unsafeUpdateConfig(deviceConfig = v.device(theme).copy(fontScale = scale))
            val ctx = Shots.files(paparazzi.context)
            FakeGame.install(ctx)
            val p = page(ctx)
            val full = CompareTable.build(GameRepo.data!!, GameRepo.state!!, five, false)
            val sheet = p.rowsSheet(p.rowItems(full), Ui.dp(ctx, 4000)) {}
            val scene = FrameLayout(ctx).apply {
                setBackgroundColor(Color.argb(255, 40, 40, 50))
                addView(sheet, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
            }
            val label = if (scale == 1f) v.label else "${v.label}-x${scale.toString().replace('.', '_')}"
            paparazzi.snapshot(scene, "r10cmpsheet-$name-$label-${theme.name.lowercase()}", 1000)
            check(ctx, sheet)
        }
    }

    private fun rowsOf(sheet: View): List<LinearLayout> = all(sheet).filterIsInstance<LinearLayout>().filter { l -> l.childCount == 3 && l.getChildAt(2) is android.widget.Switch }

    @Test fun rowsSheetReadable() = sheetShoot("rows", listOf(phoneTall, floatTall), listOf(1f, 1.3f)) { ctx, sheet ->
        val rows = rowsOf(sheet)
        val full = CompareTable.build(GameRepo.data!!, GameRepo.state!!, five, false)
        assertEquals(full.rows.size, rows.size)
        // the section headers: accent, bold, uppercase, bigger than the small lines, each with a rule above (except the first)
        val titles = all(sheet).filterIsInstance<TextView>().filter { it.text.toString() in listOf("OVERVIEW", "STATS", "BASE STATS", "BUILD", "MOVES", "MATCHUPS", "EVOLUTION") }
        assertEquals(listOf("OVERVIEW", "STATS", "BASE STATS", "BUILD", "MOVES", "MATCHUPS", "EVOLUTION"), titles.map { it.text.toString() })
        val mauve = Ui.c(ctx, R.color.ag_mauve)
        assertTrue(titles.all { it.currentTextColor == mauve })
        val body = titles[0].parent as LinearLayout
        for (t in titles.drop(1)) {
            val rule = body.getChildAt(body.indexOfChild(t) - 1)
            assertEquals("a 1 dp rule above ${t.text}", Ui.dp(ctx, 1), rule.height)
        }
        for (r in rows) {
            val abbr = r.getChildAt(0) as TextView
            val text = r.getChildAt(1) as LinearLayout
            val subs = (0 until text.childCount).map { text.getChildAt(it) as TextView }
            val (fullName, sentence) = CompareRowText.of(full.rows[rows.indexOf(r)].key, abbr.text.toString())
            // the abbreviation as the table shows it, large and bright, in its own column
            assertEquals(full.rows[rows.indexOf(r)].label, abbr.text.toString())
            assertEquals(Ui.c(ctx, R.color.ag_text), abbr.currentTextColor)
            assertTrue("${abbr.text} (${abbr.textSize}) bigger than the small lines (${subs[0].textSize})", abbr.textSize > subs[0].textSize * 1.15f)
            // two grey lines: the full name, then the one sentence
            assertEquals(listOf(fullName, sentence), subs.map { it.text.toString() })
            assertEquals(Ui.c(ctx, R.color.ag_sub1), subs[0].currentTextColor)
            assertEquals(Ui.c(ctx, R.color.ag_sub0), subs[1].currentTextColor)
            // nothing clipped: the label's longest word fits its box, and the small lines are fully laid out beside it
            val word = abbr.text.toString().split(' ').maxBy { it.length }
            assertTrue("${abbr.text}: word ${abbr.paint.measureText(word)} fits ${abbr.width}", abbr.paint.measureText(word) <= abbr.width + 1)
            for (s in subs) assertTrue("${s.text}: all lines show", s.layout != null && s.layout.getEllipsisCount(s.lineCount - 1) == 0 && s.height >= s.layout.height)
            assertTrue("the small lines sit right of the abbreviation (${abbr.right} <= ${text.left})", abbr.right <= text.left)
        }
        // the abbreviation column has one width, so the small lines line up
        assertEquals(1, rows.map { (it.getChildAt(0) as TextView).width }.toSet().size)
        assertEquals(1, rows.map { (it.getChildAt(1) as View).left }.toSet().size)
        // Show all and Done stay
        val words = all(sheet).filterIsInstance<TextView>().map { it.text.toString() }
        assertTrue("Show all" in words && "Done" in words && "Rows" in words)
    }

    @Test fun switchStillTogglesFromTheRow() {
        val ctx = Shots.files(paparazzi.context); FakeGame.install(ctx)
        val p = page(ctx)
        val full = CompareTable.build(GameRepo.data!!, GameRepo.state!!, five, false)
        val sheet = p.rowsSheet(p.rowItems(full), Ui.dp(ctx, 4000)) {}
        val row = rowsOf(sheet).first { (it.getChildAt(0) as TextView).text == "Atk" }
        assertTrue(CompareStore.loadRows(ctx).visible("atk"))
        row.performClick()
        assertFalse(CompareStore.loadRows(ctx).visible("atk"))
        row.performClick()
        assertTrue(CompareStore.loadRows(ctx).visible("atk"))
    }

    // ---------- B12: sort by tap ----------

    @Test fun tapSortsThreeTimesThenClears() = paparazzi.shootPage("r10cmpsheet-sorted", listOf(phone, float)) { ctx ->
        var st: CompareState? = null
        val p = page(ctx) { st = it }
        val screen = shown(ctx, p)
        assertNull(st!!.sortKey)
        fun arrows() = all(labelCell(p.root, "Atk")).filterIsInstance<ImageView>().size
        assertEquals(0, arrows())
        assertTrue(labelCell(p.root, "Atk").contentDescription.contains("Tap to sort high to low"))
        labelCell(p.root, "Atk").performClick(); p.render()
        assertEquals("atk", st!!.sortKey); assertTrue(st!!.sortDesc); assertEquals(1, arrows())
        assertTrue(labelCell(p.root, "Atk").contentDescription.contains("Sorted high to low") && labelCell(p.root, "Atk").contentDescription.contains("Tap to sort low to high"))
        labelCell(p.root, "Atk").performClick(); p.render()
        assertEquals("atk", st!!.sortKey); assertFalse(st!!.sortDesc); assertEquals(1, arrows())
        assertTrue(labelCell(p.root, "Atk").contentDescription.contains("Tap to clear the sort"))
        labelCell(p.root, "Atk").performClick(); p.render()
        assertNull(st!!.sortKey); assertEquals(0, arrows())
        // another stat starts at high to low, whatever the last direction was
        labelCell(p.root, "Atk").performClick(); p.render(); labelCell(p.root, "Atk").performClick(); p.render()
        labelCell(p.root, "Def").performClick(); p.render()
        assertEquals("def", st!!.sortKey); assertTrue(st!!.sortDesc)
        assertEquals(0, arrows()); assertEquals(1, all(labelCell(p.root, "Def")).filterIsInstance<ImageView>().size)
        // render the asc state for the PNG
        labelCell(p.root, "Def").performClick(); p.render()
        assertFalse(st!!.sortDesc)
        screen
    }

    @Test fun longPressOpensTheSortMenuWithItsDescriptionAndNoInfoPopup() {
        val ctx = Shots.files(paparazzi.context); FakeGame.install(ctx)
        var st: CompareState? = null
        val p = page(ctx) { st = it }
        shown(ctx, p)
        var menu: AppMenu? = null
        val dialog = DialogShots.capture { menu = MenuKit.capture { assertTrue(labelCell(p.root, "Spe").performLongClick()) } }
        assertNull("no info popup", dialog)
        val m = menu!!
        assertEquals(listOf("High to low", "Low to high"), m.describe().map { it.second })
        val texts = all(m.content(ctx)).filterIsInstance<TextView>().map { it.text.toString() }
        assertTrue(texts.toString(), "Speed" in texts && texts.any { it.startsWith("Who attacks first") })
        m.pick(1)
        assertEquals("spe", st!!.sortKey); assertFalse(st!!.sortDesc)
        // now sorted: the menu also offers to clear
        p.render()
        val m2 = MenuKit.capture { labelCell(p.root, "Spe").performLongClick() }!!
        assertEquals(listOf("High to low", "Low to high", "Clear sort"), m2.describe().map { it.second })
        assertEquals("Sorted like this now", m2.subOf(1))
        m2.pick(2)
        assertNull(st!!.sortKey)
    }

    @Test fun otherRowsShowTheirInfoOnTapAndOnLongPress() {
        val ctx = Shots.files(paparazzi.context); FakeGame.install(ctx)
        val p = page(ctx)
        shown(ctx, p)
        for (label in listOf("Types", "Weak against", "Line")) {
            var menu: AppMenu? = null
            val tap = DialogShots.capture { menu = MenuKit.capture { infoCell(p.root, label).performClick() } }
            assertNotNull("$label tap shows its info", tap); assertNull(menu)
            val hold = DialogShots.capture { menu = MenuKit.capture { assertTrue(infoCell(p.root, label).performLongClick()) } }
            assertNotNull("$label long-press shows its info", hold); assertNull(menu)
        }
    }

    @Test fun noSortChipAtTheTop() = paparazzi.shootPage("r10cmpsheet-chips", listOf(phone, float)) { ctx ->
        val p = page(ctx) { it.setSort("atk", true) }
        val screen = shown(ctx, p)
        val words = all(p.root).filterIsInstance<TextView>().map { it.text.toString() }
        assertTrue(words.toString(), words.none { it.startsWith("Sort") })
        assertTrue("Rows: all" in words && "Fully evolved" in words)
        assertTrue("the sorted row keeps its arrow", all(labelCell(p.root, "Atk")).filterIsInstance<ImageView>().isNotEmpty())
        screen
    }

    // ---------- B13: the column menu ----------

    private val st get() = GameRepo.state!!
    private fun headMenu(p: ComparePage, pos: Int): AppMenu = MenuKit.capture {
        all(p.root).filter { it.contentDescription?.contains("Tap for options.") == true }[pos].performClick()
    }!!
    private fun labels(m: AppMenu) = m.describe().map { it.second }
    private fun idOf(m: AppMenu, label: String) = m.describe().first { it.second == label }.first

    private val mixed = listOf(ComparePick.Owned("t1"), ComparePick.Owned("t2"), ComparePick.Owned("b1"), ComparePick.Dex(150))

    @Test fun menuRowsPerKindOfColumn() {
        val ctx = Shots.files(paparazzi.context); FakeGame.install(ctx)
        val p = page(ctx, mixed)
        shown(ctx, p)
        // the team is full (6): a team member offers Remove from team + Set as main, a box Pokémon the swap picker
        assertEquals(listOf("Remove from compare", "Remove from team", "Details"), labels(headMenu(p, 0)))   // the reference (already the main)
        assertEquals(listOf("Make reference", "Remove from compare", "Remove from team", "Set as main", "Details"), labels(headMenu(p, 1)))
        val box = headMenu(p, 2)
        assertEquals(listOf("Make reference", "Remove from compare", "Add to team…", "Set as main", "Details"), labels(box))
        assertEquals("${st.main!!.shownName} goes to the box", box.subOf(idOf(box, "Set as main")))
        // a Pokédex column: just Details
        assertEquals(listOf("Make reference", "Remove from compare", "Details"), labels(headMenu(p, 3)))
    }

    @Test fun addToTeamAndSetAsMainFromTheBox() {
        val ctx = Shots.files(paparazzi.context); FakeGame.install(ctx)
        st.team.retainAll(listOf("t1", "t2", "t3"))
        val p = page(ctx, mixed)
        shown(ctx, p)
        val m = headMenu(p, 2)
        assertEquals(listOf("Make reference", "Remove from compare", "Add to team", "Set as main", "Details"), labels(m))
        m.pick(idOf(m, "Add to team"))
        assertEquals(listOf("t1", "t2", "t3", "b1"), st.team.toList())
        // in the team now: Remove from team, and Set as main moves it to the front
        p.render()
        val m2 = headMenu(p, 2)
        assertEquals(listOf("Make reference", "Remove from compare", "Remove from team", "Set as main", "Details"), labels(m2))
        m2.pick(idOf(m2, "Set as main"))
        assertEquals("b1", st.main!!.uid)
        // a box Pokémon straight to main, with room in the team
        st.team.retainAll(listOf("b1", "t1"))
        p.render()
        val m3 = headMenu(p, 1) // t2 is in the box now
        assertEquals(listOf("Make reference", "Remove from compare", "Add to team", "Set as main", "Details"), labels(m3))
        m3.pick(idOf(m3, "Set as main"))
        assertEquals(listOf("t2", "b1", "t1"), st.team.toList())
        // Remove from team goes through the game
        p.render()
        val m4 = headMenu(p, 2)
        m4.pick(idOf(m4, "Remove from team"))
        assertFalse("b1" in st.team)
    }

    @Test fun fullTeamSwapsAndMainGoesToTheBox() {
        val ctx = Shots.files(paparazzi.context); FakeGame.install(ctx)
        val p = page(ctx, mixed)
        shown(ctx, p)
        val before = st.team.toList()
        assertEquals(6, before.size)
        val m = headMenu(p, 2)
        m.pick(idOf(m, "Set as main")) // the box Pokémon takes the main's place, the main goes to the box
        assertEquals(listOf("b1") + before.drop(1), st.team.toList())
        // Add to team… opens the swap picker
        p.render()
        val box = headMenu(p, 3 - 1) // b1 is in the team now: its menu has no swap
        assertFalse(labels(box).contains("Add to team…"))
        val t1 = ComparePage(ctx, NoHost, listOf(ComparePick.Owned("t1"), ComparePick.Owned("t2"), ComparePick.Owned("b2")))
        shown(ctx, t1)
        val m2 = headMenu(t1, 2)
        assertTrue(labels(m2).contains("Add to team…"))
        val dlg = DialogShots.capture { m2.pick(idOf(m2, "Add to team…")) }
        assertNotNull("the swap picker opens", dlg)
        assertTrue(all(dlg!!.content()).filterIsInstance<TextView>().any { it.text.toString().contains("swap out", true) })
    }

    @Test fun onlyTeamMemberCannotLeave() {
        val ctx = Shots.files(paparazzi.context); FakeGame.install(ctx)
        st.team.retainAll(listOf("t1"))
        val p = page(ctx, mixed)
        shown(ctx, p)
        val m = headMenu(p, 0)
        val remove = m.describe().first { it.second == "Remove from team" }
        assertFalse("disabled", remove.third)
        assertEquals("Your team needs at least one Pokémon.", m.subOf(remove.first))
        assertFalse("a lone main has no Set as main", labels(m).contains("Set as main"))
    }

    @Test fun detailsGoThroughTheSeam() {
        val ctx = Shots.files(paparazzi.context); FakeGame.install(ctx)
        val p = page(ctx, mixed)
        var opened: String? = null
        var species: Int? = null
        p.openMon = { _, _, mon: Mon -> opened = mon.uid }
        p.openDex = { _, sp -> species = sp.num }
        shown(ctx, p)
        val m = headMenu(p, 2)
        m.pick(idOf(m, "Details"))
        assertEquals("b1", opened)
        val d = headMenu(p, 3)
        d.pick(idOf(d, "Details"))
        assertEquals(150, species)
        assertNull(species.takeIf { false })
    }

    @Test fun removeFromCompareStillWorksAndIsRenamed() {
        val ctx = Shots.files(paparazzi.context); FakeGame.install(ctx)
        var cs: CompareState? = null
        val p = page(ctx, mixed) { cs = it }
        shown(ctx, p)
        assertEquals(4, cs!!.size)
        val m = headMenu(p, 1)
        m.pick(3)
        assertEquals(3, cs!!.size)
        assertTrue(st.team.contains("t2")) // only off the screen, still in the team
    }

    @Test fun headMenuPictures() = paparazzi.shootPage("r10cmpsheet-headmenu", listOf(phone)) { ctx ->
        val p = page(ctx, mixed)
        val screen = shown(ctx, p)
        val m = headMenu(p, 2)
        FrameLayout(ctx).apply {
            addView(screen, FrameLayout.LayoutParams(-1, -1))
            addView(m.content(ctx), FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START).also { it.topMargin = Ui.dp(ctx, 150); it.marginStart = Ui.dp(ctx, 120) })
        }
    }

    @Test fun sortMenuPicture() = paparazzi.shootPage("r10cmpsheet-sortmenu", listOf(phone)) { ctx ->
        val p = page(ctx) { it.setSort("spe", false) }
        val screen = shown(ctx, p)
        val m = MenuKit.capture { labelCell(p.root, "Spe").performLongClick() }!!
        FrameLayout(ctx).apply {
            addView(screen, FrameLayout.LayoutParams(-1, -1))
            addView(m.content(ctx), FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START).also { it.topMargin = Ui.dp(ctx, 330); it.marginStart = Ui.dp(ctx, 50) })
        }
    }
}
