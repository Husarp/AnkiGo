package com.husarp.ankigo.shots

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.R
import com.husarp.ankigo.game.GameData
import com.husarp.ankigo.game.TestData
import com.husarp.ankigo.game.Type
import com.husarp.ankigo.ui.BoxFilter
import com.husarp.ankigo.ui.DexFilter
import com.husarp.ankigo.ui.DexOwned
import com.husarp.ankigo.ui.DexPage
import com.husarp.ankigo.ui.SortKey
import com.husarp.ankigo.ui.SortSheet
import com.husarp.ankigo.ui.SortSpec
import com.husarp.ankigo.ui.TeamPage
import com.husarp.ankigo.ui.TeamSection
import com.husarp.ankigo.ui.TypeFilter
import com.husarp.ankigo.ui.TypeMatch
import com.husarp.ankigo.ui.Ui
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/*
 * R19-D (PLAN 250, 251, 252). Shots (-light / -dark): r19d-dex-scrolled (Pokédex scrolled, the back-to-top arrow), r19d-box-scrolled,
 * r19d-owner (the owner row with Seen on, 360 dp, normal / large / largest font), r19d-typesheet (the type sheet with the notice).
 * The checks run on the laid-out views, with "Reduce motion" on where a fade would be in the way (the fade itself is device-only).
 */
class R19DDexShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    private val st get() = GameRepo.state!!
    private val w360 = Variant("w360", 360, 800)
    private val w412 = Variant("w412", 412, 869)
    private val tall360 = Variant("w360tall", 360, 1500)

    private fun all(v: View, out: MutableList<View> = ArrayList()): List<View> {
        out += v
        if (v is ViewGroup) for (i in 0 until v.childCount) all(v.getChildAt(i), out)
        return out
    }

    private fun arrow(v: View) = all(v).firstOrNull { it.contentDescription == "Back to top" }
    private fun layoutNow(ctx: Context, v: View) {
        val dm = ctx.resources.displayMetrics
        v.measure(View.MeasureSpec.makeMeasureSpec(dm.widthPixels, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(dm.heightPixels, View.MeasureSpec.EXACTLY))
        v.layout(0, 0, dm.widthPixels, dm.heightPixels)
    }

    private fun shoot(name: String, v: Variant, scale: Float = 1f, themes: List<Theme> = Theme.entries, reduceMotion: Boolean = false,
                      check: (Context, View, View) -> Unit = { _, _, _ -> }, build: (Context) -> View) {
        for (theme in themes) {
            paparazzi.unsafeUpdateConfig(deviceConfig = v.device(theme).copy(fontScale = scale))
            FakeGame.scenario = FakeGame.Scenario.MID_GAME
            FakeGame.withSprites = true
            val ctx = Shots.files(paparazzi.context)
            FakeGame.install(ctx)
            ctx.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", reduceMotion).apply()
            val view = build(ctx)
            val frame = FrameLayout(ctx).apply { addView(view, FrameLayout.LayoutParams(-1, -1)) }
            layoutNow(ctx, frame)
            try { check(ctx, view, frame) } catch (e: Throwable) { throw AssertionError("${v.label}/${theme.name}: ${e.message}", e) }
            val label = if (scale == 1f) v.label else "${v.label}-x${scale.toString().replace('.', '_')}"
            paparazzi.snapshot(frame, "r19d-$name-$label-${theme.name.lowercase()}", 1000)
        }
    }

    /** The test world plus ~135 more Gen 1 species (no sprites needed: a blob each), so the Pokédex is long enough to scroll a few screens. */
    private fun bigWorld(ctx: Context) {
        val base = FakeGame.data
        val extra = (4..151).filter { it !in base.species }.associateWith {
            TestData.species(it, "Mon$it", listOf(Type.entries[it % 18]))
        }
        val big = GameData(base.species + extra, base.moves, base.growth, base.itemPrices)
        fun set(field: String, v: Any?) = GameRepo::class.java.getDeclaredField(field).apply { isAccessible = true }.set(GameRepo, v)
        set("loadedData", big)
        GameRepo::class.java.getDeclaredMethod("rebuild").apply { isAccessible = true }.invoke(GameRepo)
        for (n in extra.keys) {
            val col = Ui.typeColor(extra.getValue(n).types.first())
            FakeSprites.write(FakeSprites.blob(96, col), File(com.husarp.ankigo.data.Sprites.dir(ctx), "static/$n.png"))
        }
        for (n in extra.keys) { if (n % 3 == 0) st.seen += n; if (n % 7 == 0) { st.seen += n; st.caught += n } }
    }

    // ------------------------------------------------------------------ 250: the Pokédex

    @Test fun dexScrolledShowsTheArrowAndATapGoesToTheTop() {
        shoot("dex-scrolled", w412, reduceMotion = true, check = { ctx, view, frame ->
            val a = arrow(view)!!
            val list = (getField(DexPageHolder.page!!, "list") as ListView)
            assertEquals("at the top: hidden", View.GONE, a.visibility)
            list.setSelectionFromTop(14, 0); layoutNow(ctx, frame)
            assertEquals("scrolled a few screens: shown (first ${list.firstVisiblePosition}, children ${list.childCount}, count ${list.count}, h ${list.height})", View.VISIBLE, a.visibility)
            assertEquals(1f, a.alpha, 0f)
            // 48 dp touch area in the bottom-right corner of the list's frame (above the tabs), clear of the last row's cells
            val lp = a.layoutParams as FrameLayout.LayoutParams
            assertEquals(Ui.dp(ctx, 48), lp.width); assertEquals(Ui.dp(ctx, 48), lp.height)
            assertEquals(android.view.Gravity.END or android.view.Gravity.BOTTOM, lp.gravity)
            assertTrue("the list keeps room below its last row", list.paddingBottom >= Ui.dp(ctx, 48))
            a.performClick(); layoutNow(ctx, frame)
            assertEquals(0, list.firstVisiblePosition)
            assertEquals("back at the top: hidden", View.GONE, a.visibility)
            list.setSelectionFromTop(14, 0); layoutNow(ctx, frame)
            assertEquals(View.VISIBLE, a.visibility)
        }) {
            bigWorld(it)
            val p = DexPage(it, NoHost); DexPageHolder.page = p; p.root; call(p, "render")
            val v = pageScreen(it, p)
            v
        }
    }

    // the page the checks look into (the build lambda makes it, the check runs right after)
    private object DexPageHolder { var page: DexPage? = null }

    // ------------------------------------------------------------------ 250: the Box

    @Test fun boxScrolledShowsTheArrowOnlyWhenLong() {
        shoot("box-scrolled", w412, reduceMotion = true, check = { ctx, view, frame ->
            val list = getField(boxPage!!, "boxList") as ListView
            val a = arrow(view)!!
            list.setSelectionFromTop(12, 0); layoutNow(ctx, frame)
            assertEquals("first ${list.firstVisiblePosition}, children ${list.childCount}, count ${list.count}, h ${list.height}", View.VISIBLE, a.visibility)
            assertTrue((list.parent as View).visibility == View.VISIBLE)
            assertEquals("the Pokédex (with its own arrow) is not in the Box section", View.GONE, (getField(boxPage!!, "dexBody") as View).visibility)
            a.performClick(); layoutNow(ctx, frame)
            assertEquals(View.GONE, a.visibility)
            list.setSelectionFromTop(12, 0); layoutNow(ctx, frame) // and scrolled again for the shot
            assertEquals(View.VISIBLE, a.visibility)
        }) { ctx ->
            for (i in 0 until 90) st.mons += TestData.mon(FakeGame.data, listOf(16, 19, 54, 129, 63, 64, 172, 1, 26)[i % 9], 5 + i % 30, listOf("tackle"), "x$i")
            val p = TeamPage(ctx, NoHost)
            p.preset(TeamSection.BOX, filter = BoxFilter.ALL, group = false, sort = SortSpec.of(SortKey.LEVEL), types = TypeFilter())
            boxPage = p
            p.root; call(p, "render")
            pageScreen(ctx, p)
        }
    }

    private var boxPage: TeamPage? = null

    @Test fun aShortBoxNeverShowsTheArrow() {
        shoot("box-short", w412, themes = listOf(Theme.LIGHT), reduceMotion = true, check = { ctx, view, frame ->
            val list = getField(boxPage!!, "boxList") as ListView
            list.setSelectionFromTop(list.count - 1, 0); layoutNow(ctx, frame)
            assertEquals(View.GONE, arrow(view)!!.visibility)
        }) { ctx ->
            val p = TeamPage(ctx, NoHost)
            p.preset(TeamSection.BOX, filter = BoxFilter.ALL, group = true, sort = SortSpec.of(SortKey.LEVEL), types = TypeFilter())
            boxPage = p
            pageScreen(ctx, p)
        }
    }

    // ------------------------------------------------------------------ 252: the owner row

    @Test fun theOwnerRowFitsFourSegmentsAtTheNarrowWidthAndWithBigFonts() {
        for (scale in listOf(1f, 1.3f, 2f)) shoot("owner", w360, scale, check = { _, view, _ ->
            val seg = all(view).first { it is ViewGroup && it.childCount == 4 && (it as ViewGroup).getChildAt(0).let { c -> c is TextView && c.text == "All" } } as ViewGroup
            val labels = (0 until 4).map { (seg.getChildAt(it) as TextView).text.toString() }
            assertEquals(listOf("All", "Owned", "Seen", "Not owned"), labels)
            for (i in 0 until 4) {
                val t = seg.getChildAt(i) as TextView
                val room = t.width - t.paddingLeft - t.paddingRight
                assertTrue("${t.text}: ${t.lineCount} lines", t.lineCount <= 2)
                for (w in t.text.split(" ")) assertTrue("the word $w fits at x$scale: ${t.paint.measureText(w)} <= $room", t.paint.measureText(w) <= room + 1)
                assertEquals("all four segments are as tall as the row", seg.getChildAt(0).height, t.height)
                assertTrue("48 dp control at scale $scale", t.height >= Ui.dp(t.context, 40))
            }
            assertTrue("Seen is the lit one", (seg.getChildAt(2) as TextView).background != null && (seg.getChildAt(0) as TextView).background == null)
        }) { ctx ->
            val p = DexPage(ctx, NoHost); p.root
            setField(p, "filter", DexFilter(owned = DexOwned.SEEN))
            call(p, "render")
            pageScreen(ctx, p)
        }
    }

    @Test fun seenShowsTheGreyOnesOfTheGrid() {
        shoot("seen-grid", w412, themes = listOf(Theme.LIGHT, Theme.DARK), check = { _, _, _ ->
            val shown = (getField(page2!!, "shown") as List<*>).map { (it as com.husarp.ankigo.game.Species).num }
            assertTrue(shown.isNotEmpty())
            assertTrue("only seen, never caught", shown.all { it in st.seen && it !in st.caught })
        }) { ctx ->
            val p = DexPage(ctx, NoHost); page2 = p; p.root
            st.seen += listOf(54, 63, 150, 151)
            setField(p, "filter", DexFilter(owned = DexOwned.SEEN))
            call(p, "render")
            pageScreen(ctx, p)
        }
    }

    private var page2: DexPage? = null

    // ------------------------------------------------------------------ 251: the notice

    private fun tap(v: View, text: String) { assertTrue("$text has a click listener", all(v).filterIsInstance<TextView>().first { it.text.toString() == text }.performClick()) }
    private fun hasNotice(v: View) = all(v).filterIsInstance<TextView>().any { it.text.toString() == TypeFilter.TOO_MANY }
    private fun typeFilter(p: DexPage) = (getField(p, "filter") as DexFilter).typeFilter

    @Test fun theTypeSheetExplainsTooManyTypesInTheSheetWithNoToast() {
        shoot("typesheet", tall360, check = { _, _, _ -> }) { ctx ->
            val p = DexPage(ctx, NoHost); p.root
            val sheet = p.sortSheet()
            val body = (ctx.resources.displayMetrics.heightPixels * SortSheet.BODY_SHARE).toInt() - Ui.dp(ctx, 150)
            val v = sheet.build(body)
            assertFalse("no notice at first", hasNotice(v))
            // Any of: three types are fine
            tap(v, "Fire"); tap(v, "Water"); tap(v, "Ice")
            assertEquals(3, typeFilter(p).types.size); assertFalse("Any of: no notice", hasNotice(v))
            // switching to All of keeps the last two and says why
            tap(v, "All of")
            assertEquals(listOf(Type.WATER, Type.ICE), typeFilter(p).types.toList())
            assertTrue("the drop is explained", hasNotice(v))
            // another change turns it off
            tap(v, "Ice")
            assertFalse(hasNotice(v)); assertEquals(setOf(Type.WATER), typeFilter(p).types)
            // a third type in All of is refused, with the notice right there
            tap(v, "Ice"); tap(v, "Fire")
            assertEquals(setOf(Type.WATER, Type.ICE), typeFilter(p).types)
            assertTrue("refused: the notice shows", hasNotice(v))
            // under the chips: the notice comes after the last type chip in the sheet
            val texts = all(v).filterIsInstance<TextView>().map { it.text.toString() }
            assertTrue(texts.indexOf(TypeFilter.TOO_MANY) > texts.indexOf("Water"))
            // a mode switch with 2 picks keeps them, no notice; Only with a third pick is valid
            tap(v, "Only"); assertFalse(hasNotice(v))
            tap(v, "Fire"); assertEquals(3, typeFilter(p).types.size); assertFalse(hasNotice(v))
            // back to the shot: All of with the third refused
            tap(v, "Any of"); tap(v, "Fire"); tap(v, "All of"); assertEquals(2, typeFilter(p).types.size)
            tap(v, "Fire"); assertTrue(hasNotice(v))
            ScrollView(ctx).apply { setBackgroundColor(Ui.c(ctx, R.color.ag_base)); addView(v) }
        }
    }
}
