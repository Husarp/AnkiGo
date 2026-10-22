package com.husarp.ankigo.shots

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.game.Type
import com.husarp.ankigo.ui.AppLook
import com.husarp.ankigo.ui.DexFilter
import com.husarp.ankigo.ui.DexPage
import com.husarp.ankigo.ui.TypeMatch
import com.husarp.ankigo.ui.TypeStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** PLAN 212 / 192: the Pokédex type chips (Ground chip in its row, kept scroll, Any of / All of switch), light + dark. */
class R15CDexChipsShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    private fun views(v: View, out: MutableList<View> = ArrayList()): List<View> {
        out += v
        if (v is ViewGroup) for (i in 0 until v.childCount) views(v.getChildAt(i), out)
        return out
    }

    private fun scroller(p: DexPage) = (getField(p, "chipsBox") as ViewGroup).let { box -> views(box).filterIsInstance<HorizontalScrollView>().single() }

    /** The page drawn like the app does it, laid out at [size] (so the chip row can be scrolled before the shot). */
    private fun dex(ctx: Context, size: Size, style: TypeStyle, f: DexFilter = DexFilter(), scrollX: Int = 0, open: Boolean = false): Pair<DexPage, View> {
        AppLook.setTypeStyle(ctx, style)
        val p = DexPage(ctx, NoHost)
        setField(p, "filter", f)
        if (open) setField(p, "filtersOpen", true)
        val v = pageScreen(ctx, p)
        call(p, "render")
        layout(ctx, v, size)
        scroller(p).scrollTo(scrollX, 0)
        return p to v
    }

    private fun layout(ctx: Context, v: View, size: Size) {
        val d = ctx.resources.displayMetrics.density
        val w = (size.wDp * d).toInt(); val h = (size.hDp * d).toInt()
        v.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        v.layout(0, 0, w, h)
    }

    private fun shoot(name: String, size: Size, font: Float = 1f, build: (Context) -> View) {
        for (theme in Theme.entries) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(size, theme).copy(fontScale = font))
            val ctx = Shots.files(paparazzi.context)
            FakeGame.install(ctx)
            val frame = FrameLayout(ctx).apply { addView(build(ctx), FrameLayout.LayoutParams(-1, -1)) }
            paparazzi.snapshot(frame, "$name-${size.name.lowercase()}-${theme.name.lowercase()}", 1000)
        }
    }

    @Test fun groundChipShots() {
        for (style in TypeStyle.entries) {
            shoot("r15c-end-${style.name.lowercase()}", Size.PHONE) { ctx -> dex(ctx, Size.PHONE, style, scrollX = 100000).second }
        }
        shoot("r15c-end-large-font", Size.PHONE, font = 1.3f) { ctx -> dex(ctx, Size.PHONE, TypeStyle.ICON_TEXT, scrollX = 100000).second }
        shoot("r15c-start-large-font", Size.PHONE, font = 1.3f) { ctx -> dex(ctx, Size.PHONE, TypeStyle.ICON_TEXT).second }
    }

    @Test fun matchSwitchShots() {
        val two = setOf(Type.WATER, Type.FLYING)
        shoot("r15c-any", Size.PHONE) { ctx -> dex(ctx, Size.PHONE, TypeStyle.ICON_TEXT, DexFilter(types = two)).second }
        shoot("r15c-all", Size.PHONE) { ctx -> dex(ctx, Size.PHONE, TypeStyle.ICON_TEXT, DexFilter(types = two, typeMatch = TypeMatch.ALL)).second }
        // a short window: the switch lives with the chips behind the "Filters" pill
        shoot("r15c-all-open", Size.SPLIT) { ctx -> dex(ctx, Size.SPLIT, TypeStyle.ICON_TEXT, DexFilter(types = two, typeMatch = TypeMatch.ALL), open = true).second }
    }

    /** Every chip sits fully inside its row (inside the scroll view's height) and the two rows do not overlap: PLAN 212a. */
    @Test fun everyChipSitsInItsRow() {
        for (font in listOf(1f, 1.3f)) for (style in TypeStyle.entries) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.PHONE, Theme.LIGHT).copy(fontScale = font))
            val ctx = Shots.files(paparazzi.context)
            FakeGame.install(ctx)
            val (p, _) = dex(ctx, Size.PHONE, style)
            val sc = scroller(p)
            val rows = (sc.getChildAt(0) as ViewGroup)
            val chips = views(rows).filterIsInstance<TextView>()
            assertEquals(Type.entries.size, chips.size)
            val tops = chips.map { (it.parent as View).top + it.top }
            for (c in chips) {
                val top = (c.parent as View).top + c.top
                assertTrue("${c.text} sticks out of the scroll view ($font, $style)", top >= 0 && top + c.height <= sc.height)
                assertEquals("${c.text} is not 48 dp tall", Math.round(48 * ctx.resources.displayMetrics.density), c.height)
            }
            // chips of one row share a top, the second row is lower
            assertEquals(1, tops.take(9).toSet().size)
            assertEquals(1, tops.drop(9).toSet().size)
            assertTrue(tops[9] > tops[0])
        }
    }

    /** A chip tap or a mode change redraws the chips but the scroll view keeps its scroll position: PLAN 212b. */
    @Test fun aTapKeepsTheChipRowScroll() {
        paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.PHONE, Theme.LIGHT))
        val ctx = Shots.files(paparazzi.context)
        FakeGame.install(ctx)
        val (p, v) = dex(ctx, Size.PHONE, TypeStyle.ICON_TEXT, scrollX = 150)
        val before = scroller(p).scrollX
        assertTrue("the row could not be scrolled", before > 0)
        fun chip(name: String) = views(scroller(p)).filterIsInstance<TextView>().first { it.text == name }
        chip("Ground").performClick()
        call(p, "render"); layout(ctx, v, Size.PHONE)
        assertEquals(setOf(Type.GROUND), (getField(p, "filter") as DexFilter).types)
        assertEquals(before, scroller(p).scrollX)
        // the Any of / All of switch keeps it too
        views(getField(p, "chipsBox") as View).filterIsInstance<TextView>().first { it.text == "All of" }.performClick()
        call(p, "render"); layout(ctx, v, Size.PHONE)
        assertEquals(TypeMatch.ALL, (getField(p, "filter") as DexFilter).typeMatch)
        assertEquals(before, scroller(p).scrollX)
        // a third pick in All of is refused: nothing changes
        chip("Fire").performClick(); chip("Water").performClick()
        call(p, "render"); layout(ctx, v, Size.PHONE)
        assertEquals(setOf(Type.GROUND, Type.FIRE), (getField(p, "filter") as DexFilter).types)
        chip("Bug").performClick()
        call(p, "render"); layout(ctx, v, Size.PHONE)
        assertEquals(setOf(Type.GROUND, Type.FIRE), (getField(p, "filter") as DexFilter).types)
        assertEquals(before, scroller(p).scrollX)
    }
}
