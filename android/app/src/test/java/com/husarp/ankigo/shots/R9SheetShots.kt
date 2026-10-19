package com.husarp.ankigo.shots

import android.content.Context
import android.graphics.Color
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.Anki
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.StudyActivity
import com.husarp.ankigo.deck.DeckPick
import com.husarp.ankigo.deck.DeckPickerSheet
import com.husarp.ankigo.game.TestData
import com.husarp.ankigo.remind.ReminderPrefs
import com.husarp.ankigo.remind.ReminderScreen
import com.husarp.ankigo.remind.ReminderStore
import com.husarp.ankigo.remind.ReminderTopic
import com.husarp.ankigo.ui.BoxCopiesSheet
import com.husarp.ankigo.ui.BoxFilter
import com.husarp.ankigo.ui.BoxTiles
import com.husarp.ankigo.ui.BottomNav
import com.husarp.ankigo.stats.ChartMetric
import com.husarp.ankigo.ui.ComparePage
import com.husarp.ankigo.ui.ComparePick
import com.husarp.ankigo.ui.CompareMode
import com.husarp.ankigo.ui.CompareStore
import com.husarp.ankigo.ui.CompareTable
import com.husarp.ankigo.ui.DragSheet
import com.husarp.ankigo.ui.GamePage
import com.husarp.ankigo.ui.RowChoice
import com.husarp.ankigo.ui.SheetDrag
import com.husarp.ankigo.ui.SheetKit
import com.husarp.ankigo.ui.SortKey
import com.husarp.ankigo.ui.SortSheet
import com.husarp.ankigo.ui.SortSpec
import com.husarp.ankigo.ui.SortText
import com.husarp.ankigo.ui.StatsSheets
import com.husarp.ankigo.ui.TeamPage
import com.husarp.ankigo.ui.TeamSection
import com.husarp.ankigo.ui.Ui
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * R9-W5-B: every bottom sheet shows the same handle at the top and drags down to close. For each sheet (the content of every Dialog sheet
 * of the app, through [SheetKit.host]; the layer sheets through [SheetKit.layer]): the handle is there, near the top; a pull on the handle past
 * 30 % closes (once, the dim fades), a shorter one springs back to 0, a fling closes at any distance; a scrolled list scrolls first and a pull
 * on a scroll at its top drags. PNGs: r9sheet-* (light + dark).
 */
class R9SheetShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    private val one = listOf(Variants.PHONE)

    private class Run(val host: DragSheet, val root: FrameLayout, var closed: Int = 0, val dims: MutableList<Float> = ArrayList())

    private fun still(ctx: Context) { ctx.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", true).commit() }

    /** [content] in a sheet host over a dark scene, laid out like a full-screen window with the sheet at the bottom. */
    private fun run(ctx: Context, content: View): Run {
        still(ctx)
        lateinit var r: Run
        val host = SheetKit.host(ctx, content, { r.closed++ }, { r.dims += it })
        val root = FrameLayout(ctx).apply {
            setBackgroundColor(Color.argb(122, 0, 0, 0))
            addView(host, FrameLayout.LayoutParams(Ui.dp(ctx, 412).coerceAtMost(ctx.resources.displayMetrics.widthPixels), -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL))
        }
        r = Run(host, root)
        layout(ctx, root)
        return r
    }

    private fun layout(ctx: Context, root: View) {
        val m = ctx.resources.displayMetrics
        root.measure(View.MeasureSpec.makeMeasureSpec(m.widthPixels, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(m.heightPixels, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, m.widthPixels, m.heightPixels)
    }

    private fun touch(v: View, action: Int, y: Float, downTime: Long, eventTime: Long = SystemClock.uptimeMillis(), x: Float = 100f) {
        val e = MotionEvent.obtain(downTime, eventTime, action, x, y, 0)
        v.dispatchTouchEvent(e)
        e.recycle()
    }

    /** Down at [y0], moves to [y0] + each of [dys], then up (one finger, slow: 100 ms between events). */
    private fun pull(root: View, y0: Float, dys: List<Float>, up: Boolean = true) {
        val t0 = 1_000L
        touch(root, MotionEvent.ACTION_DOWN, y0, t0, t0)
        var t = t0
        for (dy in dys) { t += 100; touch(root, MotionEvent.ACTION_MOVE, y0 + dy, t0, t) }
        if (up) touch(root, MotionEvent.ACTION_UP, y0 + dys.last(), t0, t + 100)
    }

    private fun handleY(ctx: Context, r: Run): Float = r.host.top + Ui.dp(ctx, 10).toFloat()

    /** The shared checks of one sheet; the sheet is rebuilt per check by [make] (a dragged sheet is left moved). */
    private fun check(ctx: Context, make: () -> View) {
        // the handle: there, a small bar near the top of the sheet
        run(ctx, make()).let { r ->
            val handle = SheetKit.findHandle(r.host)
            assertNotNull("the sheet shows its handle", handle)
            val loc = IntArray(2); handle!!.getLocationInWindow(loc)
            val hostLoc = IntArray(2); r.host.getLocationInWindow(hostLoc)
            assertTrue("the handle sits at the top of the sheet: ${loc[1] - hostLoc[1]}", loc[1] - hostLoc[1] < Ui.dp(ctx, 40))
            assertTrue("a 4 dp bar", handle.height in Ui.dp(ctx, 3)..Ui.dp(ctx, 5) && handle.width in Ui.dp(ctx, 30)..Ui.dp(ctx, 44))
        }
        // short pull: follows the finger, springs back, closes nothing
        run(ctx, make()).let { r ->
            val h = r.host.height.toFloat()
            pull(r.root, handleY(ctx, r), listOf(20f, h * 0.10f, h * 0.20f), up = false)
            assertEquals("follows the finger", h * 0.20f, r.host.translationY, h * 0.05f)
            assertTrue("the dim fades while it moves", r.dims.isNotEmpty() && r.dims.last() < SheetDrag.DIM)
            touch(r.root, MotionEvent.ACTION_UP, handleY(ctx, r) + h * 0.20f, 1_000L, 1_400L)
            assertEquals("springs back", 0f, r.host.translationY, 0.5f)
            assertEquals(0, r.closed)
            assertEquals("the dim is back", SheetDrag.DIM, r.dims.last(), 0.001f)
        }
        // long pull: closes once, the dim is nearly gone
        run(ctx, make()).let { r ->
            val h = r.host.height.toFloat()
            pull(r.root, handleY(ctx, r), listOf(20f, h * 0.2f, h * 0.45f))
            assertEquals("closed once", 1, r.closed)
            assertTrue("the dim faded", r.dims.last() < SheetDrag.DIM * 0.8f)
        }
        // a pull on the content (not scrolled) drags too; a sideways-only touch and a tap do not
        run(ctx, make()).let { r ->
            val h = r.host.height.toFloat()
            pull(r.root, r.host.top + h * 0.6f, listOf(20f, h * 0.2f, h * 0.45f))
            val scroll = SheetKit.findScroll(r.host)
            if (scroll == null || !scroll.canScrollVertically(-1)) assertEquals("a pull on the content at its top closes", 1, r.closed)
        }
        run(ctx, make()).let { r ->
            val y = r.host.top + r.host.height * 0.6f
            touch(r.root, MotionEvent.ACTION_DOWN, y, 1_000L, 1_000L); touch(r.root, MotionEvent.ACTION_UP, y, 1_000L, 1_050L)
            assertEquals(0, r.closed); assertEquals(0f, r.host.translationY, 0f)
        }
    }

    private fun scene(ctx: Context, r: Run): View = FrameLayout(ctx).apply {
        setBackgroundColor(Ui.c(ctx, com.husarp.ankigo.R.color.ag_base))
        addView(r.root, FrameLayout.LayoutParams(-1, -1))
    }

    // ---------- the Dialog sheets (content through SheetKit.host) ----------

    private val decks = listOf(
        Anki.Deck(1, "Japanese Core 2k", 8, 3, 42, false),
        Anki.Deck(2, "Geography", 5, 0, 9, false),
        Anki.Deck(3, "Geography::Capitals", 5, 0, 6, false),
    )

    @Test fun deckPicker() {
        paparazzi.shootPage("r9sheet-deckpicker", one) { ctx ->
            val make = { DeckPickerSheet(ctx, "Move to deck", decks, 3L, DeckPick.Mode.CARD, "Move", {}, {}).content() }
            check(ctx, make); scene(ctx, run(ctx, make()))
        }
    }

    @Test fun compareRows() {
        paparazzi.shootPage("r9sheet-comparerows", one) { ctx ->
            val picks = listOf(ComparePick.Owned("t1"), ComparePick.Owned("t2"), ComparePick.Dex(150), ComparePick.Owned("t6"), ComparePick.Dex(3))
            CompareStore.saveMode(ctx, CompareMode.VS_REFERENCE); CompareStore.saveRows(ctx, RowChoice())
            val p = ComparePage(ctx, NoHost, picks)
            val full = CompareTable.build(GameRepo.data!!, GameRepo.state!!, picks, false)
            val maxBody = (ctx.resources.displayMetrics.heightPixels * 0.9f).toInt() - Ui.dp(ctx, 150)
            val make = { p.rowsSheet(p.rowItems(full), maxBody) {} }
            check(ctx, make); scene(ctx, run(ctx, make()))
        }
    }

    @Test fun boxCopies() {
        paparazzi.shootPage("r9sheet-boxcopies", one) { ctx ->
            val gd = GameRepo.data!!; val st = GameRepo.state!!
            st.mons += TestData.mon(gd, 25, 31, listOf("tackle"), "x1").also { it.caughtAt = 1_760_100_000_000 }
            st.mons += TestData.mon(gd, 25, 12, listOf("tackle"), "x2").also { it.caughtAt = 1_760_200_000_000 }
            val p = TeamPage(ctx, NoHost); p.preset(TeamSection.BOX)
            val tile = BoxTiles.build(gd, st.mons.filter { it.species == 25 }, true).single()
            val make = { BoxCopiesSheet(p, tile, {}, { _, _, _ -> }).build((ctx.resources.displayMetrics.heightPixels * BoxCopiesSheet.LIST_SHARE).toInt()) }
            check(ctx, make); scene(ctx, run(ctx, make()))
        }
    }

    @Test fun sortSheet() {
        paparazzi.shootPage("r9sheet-sort", one) { ctx ->
            val p = TeamPage(ctx, NoHost); p.preset(TeamSection.BOX)
            val maxBody = (ctx.resources.displayMetrics.heightPixels * SortSheet.BODY_SHARE).toInt() - Ui.dp(ctx, 150)
            val make = {
                SortSheet(p, SortText.boxKeys, SortSpec.of(SortKey.LEVEL), SortSpec.DEFAULT, false, BoxFilter.entries.map { it.label }, 0,
                    SortSheet.GroupToggle(true) {}, count = { 54 }, onChange = { _, _ -> }, sortOnly = false).build(maxBody)
            }
            check(ctx, make); scene(ctx, run(ctx, make()))
        }
    }

    private class HostPage(ctx: Context) : GamePage(ctx, NoHost) {
        override val tab: BottomNav.Tab? = null
        override val needsGame = false
        override fun buildBar(): View = View(this)
        override fun buildBody(): View = View(this)
        override fun render() = Unit
    }

    @Test fun reminderSheet() {
        paparazzi.shootPage("r9sheet-reminder", one) { ctx ->
            ReminderStore.save(ReminderStore.prefs(ctx), ReminderPrefs(dueOn = true, dueMin = 20))
            val s = ReminderScreen(HostPage(ctx), loadDecks = { it(emptyList()) }, canPost = { true })
            val make = { s.sheetView(ReminderTopic.DUE) }
            check(ctx, make); scene(ctx, run(ctx, make()))
        }
    }

    @Test fun studyActions() {
        paparazzi.shootPage("r9sheet-study", one) { ctx ->
            val act = attach(StudyActivity(), ShotCtx(ctx))
            setField(act, "question", "<div>Capital of Australia?</div>")
            setField(act, "modelName", "Basic"); setField(act, "cardName", "Card 1"); setField(act, "deckName", "Geography::Capitals")
            val make = { act.actionSheetContent { } }
            check(ctx, make); scene(ctx, run(ctx, make()))
        }
    }

    // ---------- the layer sheets ----------

    @Test fun statsMetricLayer() {
        paparazzi.shootPage("r9sheet-statslayer", one) { ctx ->
            still(ctx)
            var closed = 0
            val scrim = View(ctx).apply { setBackgroundColor(Color.argb(122, 0, 0, 0)) }
            val sheet = StatsSheets.metricSheet(ctx, ChartMetric.entries.first()) { } as DragSheet
            SheetKit.layer(sheet, scrim) { closed++ }
            val root = FrameLayout(ctx).apply {
                addView(scrim, FrameLayout.LayoutParams(-1, -1))
                addView(sheet, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
            }
            layout(ctx, root)
            assertNotNull(SheetKit.findHandle(sheet))
            val h = sheet.height.toFloat(); val y = sheet.top + Ui.dp(ctx, 10).toFloat()
            pull(root, y, listOf(20f, h * 0.1f), up = true)
            assertEquals(0, closed); assertEquals(0f, sheet.translationY, 0.5f); assertEquals(1f, scrim.alpha, 0.001f)
            pull(root, y, listOf(20f, h * 0.2f, h * 0.5f))
            assertEquals(1, closed)
            root
        }
    }

    // ---------- the rules ----------

    /** A synthetic sheet with a tall list: the list scrolls first; at its top a downward pull drags; the handle strip always drags. */
    @Test fun aScrollingSheetScrollsFirst() {
        paparazzi.shootPage("r9sheet-scroll", one) { ctx ->
            val make = {
                LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    setBackgroundColor(Ui.c(ctx, com.husarp.ankigo.R.color.ag_card))
                    addView(ScrollView(ctx).apply {
                        addView(LinearLayout(ctx).apply {
                            orientation = LinearLayout.VERTICAL
                            for (i in 1..40) addView(TextView(ctx).apply { text = "Row $i"; minHeight = Ui.dp(ctx, 48) })
                        })
                    }, LinearLayout.LayoutParams(-1, Ui.dp(ctx, 400)))
                }
            }
            // no handle in the content: the helper adds one, as the first child
            val r = run(ctx, make())
            assertTrue("a handle was added", SheetKit.findHandle(r.host) != null)
            val scroll = SheetKit.findScroll(r.host) as ScrollView
            val h = r.host.height.toFloat()
            val mid = r.host.top + h * 0.6f
            // scrolled down: a downward pull scrolls the list, the sheet stays
            scroll.scrollTo(0, Ui.dp(ctx, 300))
            assertTrue(scroll.canScrollVertically(-1))
            pull(r.root, mid, listOf(20f, h * 0.2f, h * 0.45f))
            assertEquals("scrolled list: no drag", 0f, r.host.translationY, 0.5f); assertEquals(0, r.closed)
            // the handle strip always drags
            pull(r.root, handleY(ctx, r), listOf(20f, h * 0.2f, h * 0.45f))
            assertEquals("the handle drags even over a scrolled list", 1, r.closed)
            // at the top of the list the content drags
            val r2 = run(ctx, make())
            pull(r2.root, r2.host.top + r2.host.height * 0.6f, listOf(20f, r2.host.height * 0.2f, r2.host.height * 0.45f))
            assertEquals("the list at its top: a pull drags and closes", 1, r2.closed)
            scene(ctx, run(ctx, make()))
        }
    }

    /** The handle sits INSIDE the scrolling child and the sheet is scrolled a few px: the still-visible handle drags, the content scrolls. */
    @Test fun aHandleInsideTheScrollStillDrags() {
        paparazzi.shootPage("r9sheet-handleinscroll", one) { ctx ->
            val make = {
                LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    setBackgroundColor(Ui.c(ctx, com.husarp.ankigo.R.color.ag_card))
                    addView(ScrollView(ctx).apply {
                        addView(LinearLayout(ctx).apply {
                            orientation = LinearLayout.VERTICAL
                            addView(SheetKit.newHandle(ctx), LinearLayout.LayoutParams(Ui.dp(ctx, 36), Ui.dp(ctx, 4)).also { it.gravity = Gravity.CENTER_HORIZONTAL; it.topMargin = Ui.dp(ctx, 40); it.bottomMargin = Ui.dp(ctx, 8) })
                            for (i in 1..40) addView(TextView(ctx).apply { text = "Row $i"; minHeight = Ui.dp(ctx, 48) })
                        })
                    }, LinearLayout.LayoutParams(-1, Ui.dp(ctx, 400)))
                }
            }
            val r = run(ctx, make())
            val scroll = SheetKit.findScroll(r.host) as ScrollView
            val handle = SheetKit.findHandle(r.host)!!
            val h = r.host.height.toFloat()
            scroll.scrollTo(0, Ui.dp(ctx, 10))
            assertTrue(scroll.canScrollVertically(-1))
            // the handle's place in the root: host + content + scroll child, minus the scroll offset
            val hy = r.host.top + (scroll.top + (handle.parent as View).top + handle.top - scroll.scrollY) + handle.height / 2f
            pull(r.root, hy, listOf(20f, h * 0.2f, h * 0.45f))
            assertEquals("the visible handle drags the sheet though the list is scrolled", 1, r.closed)
            // a pull on the content below it scrolls instead
            val r2 = run(ctx, make())
            val s2 = SheetKit.findScroll(r2.host) as ScrollView
            s2.scrollTo(0, Ui.dp(ctx, 10))
            pull(r2.root, r2.host.top + r2.host.height * 0.6f, listOf(20f, h * 0.2f, h * 0.45f))
            assertEquals("content pull: no drag", 0, r2.closed); assertEquals(0f, r2.host.translationY, 0.5f)
            scene(ctx, run(ctx, make()))
        }
    }

    /** A fling down closes at any distance; a slow short pull does not. (Needs a working VelocityTracker: the unit-test renderer has one.) */
    @Test fun aFlingClosesAtAnyDistance() {
        paparazzi.shootPage("r9sheet-fling", one) { ctx ->
            val content = { LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.WHITE); addView(TextView(ctx).apply { text = "x"; minHeight = Ui.dp(ctx, 300) }) } }
            val r = run(ctx, content())
            val y = handleY(ctx, r)
            val t0 = 5_000L
            touch(r.root, MotionEvent.ACTION_DOWN, y, t0, t0)
            // 12 dp slop passed, then ~6 dp / 4 ms = way above 800 dp/s, total well under 30 % of the height
            for (i in 1..6) touch(r.root, MotionEvent.ACTION_MOVE, y + Ui.dp(ctx, 4) * i * 1f, t0, t0 + 4L * i)
            val total = Ui.dp(ctx, 4) * 6f
            assertTrue("short of 30 %", total < r.host.height * 0.3f)
            touch(r.root, MotionEvent.ACTION_UP, y + total, t0, t0 + 28)
            assertEquals("a fling closes", 1, r.closed)
            scene(ctx, run(ctx, content()))
        }
    }

    @Test fun theDimFollowsTheScrim() {
        assertEquals(0.48f, SheetDrag.dim(1f), 0.0001f)
        assertEquals(0.24f, SheetDrag.dim(0.5f), 0.0001f)
        assertEquals(0f, SheetDrag.dim(0f), 0f)
        assertEquals(0.48f, SheetDrag.dim(3f), 0.0001f)
        assertEquals(0f, SheetDrag.dim(-1f), 0f)
        assertFalse(SheetDrag.DIM <= 0f)
    }
}
