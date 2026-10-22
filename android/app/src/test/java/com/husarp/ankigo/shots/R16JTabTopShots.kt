package com.husarp.ankigo.shots

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.ListView
import android.widget.ScrollView
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.stats.AnkiStatsStore
import com.husarp.ankigo.stats.StatsSnapshot
import com.husarp.ankigo.ui.SettingsPage
import com.husarp.ankigo.ui.SettingsSection
import com.husarp.ankigo.ui.ShopPage
import com.husarp.ankigo.ui.StatsPage
import com.husarp.ankigo.ui.StatsSection
import com.husarp.ankigo.ui.TeamPage
import com.husarp.ankigo.ui.TeamSection
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/*
 * R16-3-J (PLAN 218): a tap on the bottom-bar tab you are on goes back to that tab's top page. Settings: a section returns to the home list,
 * the list scrolls to its top. Pokémon (Team / Box / Pokédex), Stats (Statistics / Achievements) and Shop (Shop / Bag) are sections or a switch,
 * not sub-pages: the shown one stays and scrolls to its top. The click path (BottomNav -> PageActivity.reselectTab -> page) needs a real Activity
 * and is not covered here; these tests call the page hook the click ends in.
 */
class R16JTabTopShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    @Before fun storeOff() { AnkiStatsStore.enabled = false; AnkiStatsStore.snapshot = StatsSnapshot() }
    @After fun storeOn() { AnkiStatsStore.enabled = true; AnkiStatsStore.snapshot = StatsSnapshot() }

    /** Lays [screen] out in a short window (so every page is taller than it and scrolls). */
    private fun layout(ctx: Context, screen: View) {
        val d = ctx.resources.displayMetrics.density
        val w = (412 * d).toInt(); val h = (360 * d).toInt()
        screen.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        screen.layout(0, 0, w, h)
    }

    /** The first visible ScrollView under [v] (depth first; a GONE branch is skipped). */
    private fun firstScroll(v: View): ScrollView? {
        if (v.visibility == View.GONE) return null
        if (v is ScrollView) return v
        if (v is ViewGroup) for (i in 0 until v.childCount) firstScroll(v.getChildAt(i))?.let { return it }
        return null
    }

    private fun setup(): Context = Shots.files(paparazzi.context).also { FakeGame.install(it) }

    @Test fun settingsSectionGoesBackToTheListAndTheListScrollsToTop() {
        val ctx = setup()
        val page = SettingsPage(ShotCtx(ctx), NoHost)
        val screen = pageScreen(ctx, page)
        layout(ctx, screen)

        page.open(SettingsSection.DOWNLOADS)
        assertEquals(SettingsSection.DOWNLOADS, page.section)
        page.onTabReselected()
        assertEquals("a reselect inside a section shows the home list", null, page.section)

        layout(ctx, screen)
        val sc = firstScroll(page.root)!!
        sc.scrollTo(0, 400)
        assertTrue("the home list is taller than the window (scrollY ${sc.scrollY})", sc.scrollY > 0)
        page.onTabReselected()
        assertEquals("a reselect on the list scrolls it to the top", 0, sc.scrollY)
        assertEquals("and stays on the list", null, page.section)
    }

    @Test fun settingsSectionScrollsBackToTopWhenItIsLeft() {
        val ctx = setup()
        val page = SettingsPage(ShotCtx(ctx), NoHost)
        val screen = pageScreen(ctx, page)
        page.open(SettingsSection.LOOK)
        layout(ctx, screen)
        val sc = firstScroll(page.root)!!
        sc.scrollTo(0, 300)
        page.onTabReselected()
        assertEquals(null, page.section)
        assertEquals(0, sc.scrollY)
    }

    @Test fun pokemonSectionStaysAndTeamScrollsToTop() {
        val ctx = setup()
        for (s in TeamSection.entries) {
            val page = TeamPage(ctx, NoHost)
            page.preset(s)
            val screen = pageScreen(ctx, page)
            call(page, "render")
            layout(ctx, screen)
            if (s == TeamSection.TEAM) {
                val sc = getField(page, "teamScroll") as ScrollView
                sc.scrollTo(0, 300)
                assertTrue("the team column is taller than the window (scrollY ${sc.scrollY})", sc.scrollY > 0)
                page.onTabReselected()
                assertEquals(0, sc.scrollY)
            } else if (s == TeamSection.BOX) {
                val list = getField(page, "boxList") as ListView
                list.setSelection(1)
                layout(ctx, screen)
                page.onTabReselected()
                layout(ctx, screen)
                assertEquals("the Box list is back at its first row", 0, list.firstVisiblePosition)
            } else {
                page.onTabReselected() // the Pokédex list: must not throw, covered by the section check below
            }
            assertEquals("a reselect keeps the $s section", s, getField(page, "section"))
        }
    }

    @Test fun statsSectionStaysAndStatisticsScrollsToTop() {
        val ctx = setup()
        for (s in StatsSection.entries) {
            val page = StatsPage(ctx, NoHost, s)
            val screen = pageScreen(ctx, page)
            call(page, "render")
            layout(ctx, screen)
            if (s == StatsSection.STATISTICS) {
                val sc = getField(page, "statsScroll") as ScrollView
                sc.scrollTo(0, 400)
                assertTrue("the statistics are taller than the window (scrollY ${sc.scrollY})", sc.scrollY > 0)
                page.onTabReselected()
                assertEquals(0, sc.scrollY)
            } else {
                page.achievementsView().scrollToY(300)
                page.onTabReselected()
                assertEquals(0, page.achievementsView().scrollY())
            }
            assertEquals("a reselect keeps the $s section", s, getField(page, "section"))
        }
    }

    @Test fun shopScrollsToTop() {
        val ctx = setup()
        val page = ShopPage(ctx, NoHost)
        val screen = pageScreen(ctx, page)
        call(page, "render")
        layout(ctx, screen)
        val sc = getField(page, "scroll") as ScrollView
        sc.scrollTo(0, 400)
        assertTrue("the shop is taller than the window (scrollY ${sc.scrollY})", sc.scrollY > 0)
        page.onTabReselected()
        assertEquals(0, sc.scrollY)
    }
}
