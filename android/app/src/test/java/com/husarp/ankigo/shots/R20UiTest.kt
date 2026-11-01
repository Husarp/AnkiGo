package com.husarp.ankigo.shots

import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.game.GameEvent
import com.husarp.ankigo.ui.*
import com.husarp.ankigo.stats.AnkiStatsStore
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class R20UiTest {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    @After fun clean() { Pickers.interceptApp = null; AwayWindows.dismissAll(); BattleShotKit.reset(); AnkiStatsStore.enabled = true }
    private fun ctx(): android.content.Context {
        val c = Shots.files(paparazzi.context)
        FakeGame.install(c)
        return c
    }
    private fun all(v: View): List<View> = listOf(v) + if (v is ViewGroup) (0 until v.childCount).flatMap { all(v.getChildAt(it)) } else emptyList()
    private fun texts(v: View) = all(v).filterIsInstance<TextView>().map { it.text.toString() }

    private fun render(page: GamePage) {
        var type: Class<*>? = page.javaClass
        while (type != null) {
            try { type.getDeclaredMethod("render").apply { isAccessible = true }.invoke(page); return }
            catch (_: NoSuchMethodException) { type = type.superclass }
        }
        error("missing render")
    }

    @Test fun detailsBackSharesThePinnedTitleHeader() {
        val c = ctx()
        var d: AppDialog? = null
        Pickers.interceptApp = { d = it }
        MonDetail.show(TeamPage(c, NoHost), GameRepo.data!!, GameRepo.state!!.main!!)
        val card = d!!.content() as LinearLayout
        val back = all(card).single { it.contentDescription == "Back to Pokémon" }
        val header = back.parent as ViewGroup
        assertSame(card, header.parent) // stays outside the scrolling body, after optional stack controls
        assertTrue(back.isClickable)
        assertEquals(listOf("Details"), texts(header))
        assertEquals(0, texts(card).count { it == "Close" })
    }

    @Test fun statsHasReviewsWithoutARedundantChartsHeading() {
        val c = ctx(); AnkiStatsStore.enabled = false
        val page = StatsPage(c, NoHost)
        val v = page.root
        render(page)
        assertFalse(texts(v).contains("Charts"))
        assertTrue(texts(v).any { it.contains("Reviews") })
        page.onGone()
    }

    @Test fun weekendSpecialPrecedesTheOrdinaryStock() {
        val c = ctx()
        GameRepo.state!!.day = "2026-10-31"
        val page = ShopPage(c, NoHost)
        val v = page.root; render(page)
        val ts = texts(v)
        assertTrue(ts.toString(), ts.indexOf("WEEKEND SPECIAL · SAT & SUN") >= 0)
        assertTrue(ts.toString(), ts.indexOf("BASICS") >= 0)
        assertTrue(ts.toString(), ts.indexOf("WEEKEND SPECIAL · SAT & SUN") < ts.indexOf("BASICS"))
        page.onGone()
    }

    @Test fun neutralAnswerKeepsItsDotWithoutAPopup() {
        val c = ctx()
        val panel = BattlePanelView(c).also { it.setStudyLayout(false, false); it.renderAll() }
        val player = EventPlayer(panel)
        player.enqueue(listOf(GameEvent.Counted(1, 0, 1, 0, false, true), GameEvent.Charge(0, 1, 2)))
        assertNull(panel.toastLayer.shown)
        player.clear()
    }

    @Test fun aFastCompareFlingTravelsBeyondOneColumn() {
        val c = ctx()
        val type = Class.forName("com.husarp.ankigo.ui.SnapScroll")
        val scroll = type.getDeclaredConstructor(android.content.Context::class.java).apply { isAccessible = true }.newInstance(c) as android.widget.HorizontalScrollView
        type.getDeclaredMethod("setStep", Int::class.javaPrimitiveType).apply { isAccessible = true }.invoke(scroll, 100)
        scroll.addView(View(c).apply { minimumWidth = 6000; minimumHeight = 400 }, ViewGroup.LayoutParams(6000, 400))
        scroll.measure(View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY))
        scroll.layout(0, 0, 600, 400)
        scroll.fling(6000)
        val scroller = android.widget.HorizontalScrollView::class.java.getDeclaredField("mScroller").apply { isAccessible = true }.get(scroll) as android.widget.OverScroller
        assertTrue("child ${scroll.getChildAt(0).width}, viewport ${scroll.width}, target ${scroller.finalX}", scroller.finalX > 500)
    }

    @Test fun returningClosesAllTrackedWindowsExactlyOnce() {
        var first = 0; var second = 0
        val a = Any(); val b = Any()
        AwayWindows.track(a) { first++ }; AwayWindows.track(b) { second++ }
        AwayWindows.dismissAll(); AwayWindows.dismissAll()
        assertEquals(1, first); assertEquals(1, second)
    }
}
