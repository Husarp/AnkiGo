package com.husarp.ankigo.shots

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.R
import com.husarp.ankigo.ui.AppMenu
import com.husarp.ankigo.ui.MenuPlacement
import com.husarp.ankigo.ui.MonActions
import com.husarp.ankigo.ui.TeamPage
import com.husarp.ankigo.ui.Ui
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/*
 * TICKET R10-E (B16): "More" in the Pokémon options menu. The less used rows (Give / Take / Use item, Teach TM, Buddy widget) are hidden
 * behind a "More" row just above Release; tapping it expands them in place ("Less"). PNGs: r10more-<team|box|synth>-<closed|open>-<size>-<theme>
 * (the menu card placed like the popup would be, in a PHONE / FLOAT (340x480) / PIP-sized window).
 */
class R10MoreShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    private val st get() = GameRepo.state!!
    private val extras = listOf("Give item…", "Use item…", "Teach TM…")

    private fun menuOf(ctx: Context, uid: String): AppMenu = MenuKit.capture { MonActions(TeamPage(ctx, NoHost)).menu(st.mon(uid)!!, View(ctx)) }!!
    private fun labels(m: AppMenu) = m.describe().map { it.second }

    @Test fun collapsedHidesExtrasAndMoreSitsAboveRelease() {
        val ctx = Shots.files(paparazzi.context); FakeGame.install(ctx)
        for (uid in listOf("t1", "t2", "b1")) {
            val l = labels(menuOf(ctx, uid))
            for (x in extras) assertFalse("$uid collapsed shows $x", l.contains(x))
            assertFalse("$uid collapsed shows Take", l.any { it.startsWith("Take ") })
            assertEquals("$uid: More directly above Release", l.indexOf("Release…") - 1, l.indexOf("More"))
            assertFalse(l.contains("Less"))
        }
    }

    @Test fun expandedShowsExtrasInOrderAboveRelease() {
        val ctx = Shots.files(paparazzi.context); FakeGame.install(ctx)
        // a box Pokémon holding an item: Take sits between Give and Use
        st.mon("b1")!!.heldItem = "lucky-egg"
        val m = menuOf(ctx, "b1")
        val closed = labels(m)
        m.expandMore()
        val l = labels(m)
        assertTrue(m.isMoreExpanded)
        val take = l.indexOfFirst { it.startsWith("Take ") }
        assertTrue(take > 0)
        assertEquals(listOf("Less", "Give item…", l[take], "Use item…", "Teach TM…"), l.subList(l.indexOf("Less"), l.indexOf("Release…")).filter { it != "Show on Buddy widget" })
        assertEquals("Release…", l.last())
        assertEquals("the visible rows without the extras are the collapsed ones", closed.filter { it != "More" }, l.filter { it != "Less" && it != "Give item…" && it != l[take] && it != "Use item…" && it != "Teach TM…" })
        m.expandMore() // already open: no-op
        assertTrue(m.isMoreExpanded)
        m.toggleMore()
        assertEquals(closed, labels(m))
    }

    @Test fun noExtraRowNoMore() {
        val ctx = Shots.files(paparazzi.context); FakeGame.install(ctx)
        val m = AppMenu(View(ctx)).item(1, "A", R.drawable.ic_team).more(9).item(2, "B", R.drawable.ic_book)
        assertEquals(listOf("A", "B"), labels(m))
        m.toggleMore()
        assertFalse(m.isMoreExpanded)
        // extras without a More row simply show where they were added
        val n = AppMenu(View(ctx)).item(1, "A", R.drawable.ic_team).item(2, "X", R.drawable.ic_book, extra = true)
        assertEquals(listOf("A", "X"), labels(n))
    }

    @Test fun pickingAnExtraRowRunsItsAction() {
        val ctx = Shots.files(paparazzi.context); FakeGame.install(ctx)
        st.bag["lucky-egg"] = 1
        val m = menuOf(ctx, "b1")
        m.expandMore()
        val give = m.describe().first { it.second == "Give item…" }.first
        val d = DialogShots.capture { m.pick(give) }
        assertNotNull("Give item opens the item picker", d)
        // a generic menu: the id comes back like any row
        val picked = ArrayList<Int>()
        val g = AppMenu(View(ctx)).item(1, "A", R.drawable.ic_team).item(2, "X", R.drawable.ic_book, extra = true).more(3)
        MenuKit.capture { g.show { picked += it } }
        g.expandMore()
        g.pick(2)
        assertEquals(listOf(2), picked)
        assertNull(AppMenu.intercept)
    }

    @Test fun moreRowViewTogglesInPlace() {
        val ctx = Shots.files(paparazzi.context); FakeGame.install(ctx)
        val m = AppMenu(View(ctx)).item(1, "A", R.drawable.ic_team).item(2, "X", R.drawable.ic_book, extra = true).more(3)
        val picked = ArrayList<Int>()
        MenuKit.capture { m.show { picked += it } }
        val frame = m.content(ctx) as ViewGroup
        val card = (frame.getChildAt(0) as ViewGroup).getChildAt(0) as ViewGroup
        // A, More, X (hidden)
        assertEquals(3, card.childCount)
        assertEquals(View.GONE, card.getChildAt(2).visibility)
        card.getChildAt(1).performClick()
        assertEquals(View.VISIBLE, card.getChildAt(2).visibility)
        assertTrue(m.isMoreExpanded)
        assertTrue("the tap does not close the menu or report an id", picked.isEmpty())
        assertEquals("Expanded", androidx.core.view.ViewCompat.getStateDescription(card.getChildAt(1))?.toString())
        card.getChildAt(1).performClick()
        assertEquals(View.GONE, card.getChildAt(2).visibility)
        assertEquals("Collapsed", androidx.core.view.ViewCompat.getStateDescription(card.getChildAt(1))?.toString())
    }

    // ---- pictures: the card placed in a window of the given size, like the popup ----

    private val sizes = listOf(Variants.PHONE, Variant("float", Size.FLOAT.wDp, Size.FLOAT.hDp), Variant("pip", Size.PIP.wDp, Size.PIP.hDp))

    private fun place(ctx: Context, m: AppMenu, v: Variant, open: Boolean): View {
        if (open) m.expandMore()
        val frame = m.content(ctx)
        val w = Ui.dp(ctx, v.wDp); val h = Ui.dp(ctx, v.hDp)
        frame.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        val bounds = MenuPlacement.Box(0, 0, w, h)
        val anchor = MenuPlacement.Box(w - Ui.dp(ctx, 48), Ui.dp(ctx, 8), w, Ui.dp(ctx, 8 + 48))
        val spot = MenuPlacement.place(anchor, frame.measuredWidth, frame.measuredHeight, bounds, Ui.dp(ctx, 10), Ui.dp(ctx, 140))
        assertTrue("fits the window (${v.label})", spot.x >= 0 && spot.y >= 0 && spot.x + spot.width <= w && spot.y + spot.height <= h)
        // rows are at least 48 dp
        val scroll = frame.let { (it as ViewGroup).getChildAt(0) as ScrollView }
        val card = scroll.getChildAt(0) as LinearLayout
        var rowsSeen = 0
        for (i in 0 until card.childCount) {
            val c = card.getChildAt(i)
            if (c.visibility == View.VISIBLE && c.minimumHeight == Ui.dp(ctx, 48)) { rowsSeen++; assertTrue("row ≥ 48 dp", c.measuredHeight >= Ui.dp(ctx, 48)) }
        }
        assertTrue(rowsSeen > 3)
        // a menu taller than the window gets a window as tall as the screen allows and scrolls inside it (the assert above: it fits)
        return FrameLayout(ctx).apply {
            setBackgroundColor(android.graphics.Color.argb(60, 128, 128, 128))
            addView(FrameLayout(ctx).apply { addView(frame, FrameLayout.LayoutParams(-1, -1)) }, FrameLayout.LayoutParams(spot.width, spot.height).also { it.leftMargin = spot.x; it.topMargin = spot.y })
        }
    }

    private fun shoot(name: String, uid: String, open: Boolean) {
        for (v in sizes) paparazzi.shootPage("r10more-$name-${if (open) "open" else "closed"}", listOf(v)) { ctx ->
            if (uid == "b1") st.mon("b1")!!.heldItem = "lucky-egg"
            place(ctx, menuOf(ctx, uid), v, open)
        }
    }

    @Test fun teamClosed() = shoot("team", "t1", false)
    @Test fun teamOpen() = shoot("team", "t1", true)
    @Test fun boxClosed() = shoot("box", "b1", false)
    @Test fun boxOpen() = shoot("box", "b1", true)

    /** A menu with the Buddy widget row (the renderer has no widget manager, so the real menu never shows it here). */
    @Test fun synthWithBuddy() {
        for (open in listOf(false, true)) for (v in sizes) paparazzi.shootPage("r10more-synth-${if (open) "open" else "closed"}", listOf(v)) { ctx ->
            val m = AppMenu(View(ctx)).header("Sparkmouse  Lv 26")
                .item(1, "Make main", R.drawable.ic_star)
                .item(2, "Give item…", R.drawable.ic_gift, extra = true).item(3, "Take Lucky Egg", R.drawable.ic_gift, extra = true)
                .item(4, "Use item…", R.drawable.ic_ball, extra = true).item(5, "Teach TM…", R.drawable.ic_tm, extra = true)
                .item(6, "Show on Buddy widget", R.drawable.ic_open, extra = true)
                .item(7, "Rename…", R.drawable.ic_pencil).more(8)
                .item(9, "Release…", R.drawable.ic_trash, danger = true)
            if (open) assertEquals(listOf("Make main", "Rename…", "Less", "Give item…", "Take Lucky Egg", "Use item…", "Teach TM…", "Show on Buddy widget", "Release…"), labels(m).also { m.expandMore() }.let { labels(m) }.let { l -> listOf("Make main", "Rename…") + l.subList(l.indexOf("Less"), l.size) })
            place(ctx, m, v, open)
        }
    }
}
