package com.husarp.ankigo.shots

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.game.DayLog
import com.husarp.ankigo.overlay.FloatBattleView
import com.husarp.ankigo.stats.AnkiStatsStore
import com.husarp.ankigo.stats.ChartMetric
import com.husarp.ankigo.stats.ChartPeriod
import com.husarp.ankigo.stats.StatsSnapshot
import com.husarp.ankigo.ui.AppMenu
import com.husarp.ankigo.ui.Bits
import com.husarp.ankigo.ui.BottomNav
import com.husarp.ankigo.ui.DexActivity
import com.husarp.ankigo.ui.DexPage
import com.husarp.ankigo.ui.Dest
import com.husarp.ankigo.ui.GameActivity
import com.husarp.ankigo.ui.Nav
import com.husarp.ankigo.ui.SettingsActivity
import com.husarp.ankigo.ui.ShopActivity
import com.husarp.ankigo.ui.ShopPage
import com.husarp.ankigo.ui.StatsActivity
import com.husarp.ankigo.ui.StatsPage
import com.husarp.ankigo.ui.StatsSection
import com.husarp.ankigo.ui.StudyDecksActivity
import com.husarp.ankigo.ui.TeamActivity
import com.husarp.ankigo.ui.TeamPage
import com.husarp.ankigo.ui.TeamSection
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

/*
 * R4-01 (PLAN 108): the new tab model. Checks (Nav intents, the bar and its highlight, the Shop's buy flow, the Stats tab switching sections
 * and keeping the chart state, the trainer-card chips) and the shots: r4n-pokemon-team|box|dex (phone, split, floating card),
 * r4n-stats-statistics|achievements (phone, split). R10 W11: the bar has 5 tabs and the Shop is its own tab (no Shop section of the Pokémon tab).
 */
class R4NavShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    @Before fun storeOff() { AnkiStatsStore.enabled = false; AnkiStatsStore.snapshot = StatsSnapshot() }
    @After fun storeOn() { AnkiStatsStore.enabled = true; AnkiStatsStore.snapshot = StatsSnapshot() }

    private val FLOAT = Variant("float", Size.FLOAT.wDp, Size.FLOAT.hDp)
    private val sizes = listOf(Variants.PHONE, Variants.SPLIT, FLOAT)
    private val st get() = GameRepo.state!!

    /** Takes what a page starts instead of starting it. */
    private class Capture(base: Context) : ContextWrapper(base) {
        val started = ArrayList<Intent>()
        override fun startActivity(intent: Intent) { started += intent }
    }

    /** The texts on screen (a GONE view hides its whole subtree). */
    private fun texts(v: View, out: MutableList<String> = ArrayList()): List<String> {
        if (v.visibility == View.GONE) return out
        if (v is TextView) out += v.text.toString()
        if (v is ViewGroup) for (i in 0 until v.childCount) texts(v.getChildAt(i), out)
        return out
    }

    private fun findText(v: View, pred: (String) -> Boolean): TextView? {
        if (v is TextView && pred(v.text.toString())) return v
        if (v is ViewGroup) for (i in 0 until v.childCount) findText(v.getChildAt(i), pred)?.let { return it }
        return null
    }

    /** Taps [tv]: the nearest clickable view around it (the chips are not clickable themselves). */
    private fun tap(tv: View) {
        var v: View? = tv
        while (v != null && !v.isClickable) v = v.parent as? View
        assertNotNull("nothing clickable around the text", v)
        v!!.performClick()
    }

    // ---------- Nav and the bar ----------

    @Test fun navIntentsForEveryDest() {
        val ctx = Shots.files(paparazzi.context)
        for (d in Dest.entries) {
            val i = Nav.intent(ctx, d)
            val t = Nav.target(d)
            assertEquals("$d", t.screen.name, i.component?.className)
            if (t.extra == null) assertFalse("$d", i.hasExtra("section"))
            else assertEquals("$d", t.extra!!.second, i.getStringExtra(t.extra!!.first))
        }
        // R10 W11: the Shop is a tab of its own, no longer a Pokémon-tab section
        assertEquals(ShopActivity::class.java.name, Nav.intent(ctx, Dest.SHOP).component?.className)
        assertFalse(Nav.intent(ctx, Dest.SHOP).hasExtra(TeamActivity.EXTRA_SECTION))
        assertEquals("ACHIEVEMENTS", Nav.intent(ctx, Dest.ACHIEVEMENTS).getStringExtra(StatsActivity.EXTRA_SECTION))
    }

    @Test fun barHasFiveTabsAndOneIsOn() {
        val act = attach(Activity(), Shots.files(paparazzi.context))
        for (cur in BottomNav.Tab.entries) {
            val bar = BottomNav.create(act, cur) as ViewGroup
            val row = bar.getChildAt(1) as ViewGroup // under the 1 dp divider
            // the Shop tab's description may carry ", new stock" (the dot): only its label is compared here
            assertEquals(listOf("Study", "Pokémon", "Shop", "Stats", "Settings"), (0 until row.childCount).map { row.getChildAt(it).contentDescription.toString().substringBefore(",") })
            // the highlighted tab has the accent pill behind its icon
            val on = (0 until row.childCount).filter { (row.getChildAt(it) as ViewGroup).getChildAt(0).background != null }
            assertEquals(listOf(cur.ordinal), on)
        }
    }

    @Test fun aTapOnTheScreenYouAreOnDoesNothing() {
        assertTrue(BottomNav.isHere(TeamActivity(), BottomNav.Tab.POKEMON))
        assertTrue(BottomNav.isHere(StatsActivity(), BottomNav.Tab.STATS))
        assertTrue(BottomNav.isHere(SettingsActivity(), BottomNav.Tab.SETTINGS))
        assertTrue(BottomNav.isHere(ShopActivity(), BottomNav.Tab.SHOP))
        // the old Pokédex screen is not the tab's screen: the tab opens it; the Shop screen is not the Pokémon tab
        assertFalse(BottomNav.isHere(DexActivity(), BottomNav.Tab.POKEMON))
        assertFalse(BottomNav.isHere(ShopActivity(), BottomNav.Tab.POKEMON))
        // the home screen is Study (R4-02): its Study tab is "here" (in the Battle view the activity switches to the Study view instead)
        assertTrue(BottomNav.isHere(GameActivity(), BottomNav.Tab.STUDY))
        assertFalse(BottomNav.isHere(GameActivity(), BottomNav.Tab.POKEMON))
    }

    @Test fun onlyTheHomeScreenIsHome() {
        assertTrue(BottomNav.isHome(GameActivity()))
        assertFalse(BottomNav.isHome(TeamActivity()))
        assertFalse(BottomNav.isHome(StudyDecksActivity())) // the old Study tab is a trampoline now
    }

    @Test fun eachScreenHighlightsItsTab() {
        val ctx = Shots.files(paparazzi.context)
        FakeGame.install(ctx)
        assertEquals(BottomNav.Tab.POKEMON, TeamPage(ctx, NoHost).tab)
        assertEquals(BottomNav.Tab.POKEMON, DexPage(ctx, NoHost).tab)
        assertEquals(BottomNav.Tab.SHOP, ShopPage(ctx, NoHost).tab)
        assertEquals(BottomNav.Tab.STATS, StatsPage(ctx, NoHost).tab)
    }

    @Test fun floatingBattleMenuSaysPokemon() {
        val ctx = Shots.files(paparazzi.context)
        FakeGame.install(ctx)
        val m = AppMenu(View(ctx))
        FloatBattleView(object : ContextWrapper(ctx) { override fun getApplicationContext(): Context = this }, {}, {}, {}).fillMenu(m)
        assertEquals(listOf("Open AnkiGo", "Pause watching AnkiDroid", "Pokémon", "Pokédex", "Shop", "Settings", "View settings", "Help"), m.describe().map { it.second })
        BattleShotKit.reset()
    }

    // ---------- the Pokémon tab ----------

    private fun pokemon(ctx: Context, v: Variant, s: TeamSection): View {
        val p = TeamPage(ctx, NoHost)
        p.preset(s)
        return if (v === FLOAT) p.root.also { p.onShow() } else pageScreen(ctx, p)
    }

    @Test fun teamSection() = shootAll("r4n-pokemon-team", TeamSection.TEAM)
    @Test fun boxSection() = shootAll("r4n-pokemon-box", TeamSection.BOX)
    @Test fun dexSection() = shootAll("r4n-pokemon-dex", TeamSection.DEX)

    private fun shootAll(name: String, s: TeamSection) {
        for (v in sizes) paparazzi.shootPage(name, listOf(v)) { ctx -> pokemon(ctx, v, s) }
    }

    /** R10 W11: the Shop is its own tab (a standalone ShopPage); its buy flow and the Bag switch work as they did inside the Pokémon tab. */
    @Test fun theShopTabShowsTheShopAndBuying() {
        paparazzi.shootPage("r4n-shop-buy", listOf(Variants.PHONE), themes = listOf(Theme.LIGHT)) { ctx ->
            val p = ShopPage(ctx, NoHost)
            val screen = pageScreen(ctx, p)
            st.cash = 5000
            call(p, "render")
            val shown = texts(screen)
            assertTrue(shown.toString(), "TODAY'S SPECIALS" in shown)
            assertTrue("the bar says Shop", "Shop" in shown)
            assertTrue("coins in the bar pill", "5,000" in shown)

            val item = st.shopItems.first()
            val price = com.husarp.ankigo.game.Shop.priceOf(GameRepo.data!!, st, item)
            val n0 = st.count(item)
            call(p, "buyItem", item)
            assertEquals(n0 + 1, st.count(item))
            assertEquals(5000 - price, st.cash)
            call(p, "render")
            assertTrue("the coins follow the buy", Bits.fmt(5000 - price) in texts(screen))

            // the Bag switch redraws the page at once
            val bagTab = findText(p.root) { it.startsWith("Bag") }!!
            tap(bagTab)
            call(p, "render") // a standalone page draws on the next message-loop turn (the embedded one used to draw at once)
            assertTrue("the Bag replaced the specials", "TODAY'S SPECIALS" !in texts(screen))
            screen
        }
    }

    @Test fun sectionsSwitchBodies() {
        paparazzi.shootPage("r4n-sections", listOf(Variants.PHONE), themes = listOf(Theme.LIGHT)) { ctx ->
            val p = TeamPage(ctx, NoHost)
            val screen = pageScreen(ctx, p)
            call(p, "render")
            assertTrue(texts(screen).any { it.startsWith("TEAM") }) // the section header on the Team body ("TEAM · n / 6")
            p.showSection(TeamSection.BOX)
            call(p, "render")
            assertEquals(View.VISIBLE, (getField(p, "boxList") as View).visibility)
            assertEquals(View.GONE, (getField(p, "teamScroll") as View).visibility)
            assertTrue(texts(screen).none { it.startsWith("TEAM") })
            p.showSection(TeamSection.TEAM)
            call(p, "render")
            assertEquals(View.GONE, (getField(p, "boxList") as View).visibility)
            assertTrue(texts(screen).any { it.startsWith("TEAM") })
            screen
        }
    }

    /** R10 W11: the Shop section left the Pokémon tab, so the Compare bar shows on every section (it used to hide over the Shop). */
    @Test fun compareTicksSurviveSectionSwitches() {
        paparazzi.shootPage("r4n-pick-sections", listOf(Variants.PHONE), themes = listOf(Theme.LIGHT)) { ctx ->
            val p = TeamPage(ctx, NoHost)
            p.preset(TeamSection.DEX, pick = listOf(com.husarp.ankigo.ui.ComparePick.Owned("t1"), com.husarp.ankigo.ui.ComparePick.Owned("t2")))
            val screen = pageScreen(ctx, p)
            call(p, "render")
            val bar = getField(p, "pickBar") as View
            assertEquals(View.VISIBLE, bar.visibility)
            p.showSection(TeamSection.TEAM)
            call(p, "render")
            assertEquals("the ticks are still there", View.VISIBLE, bar.visibility)
            assertTrue(texts(bar).any { it.contains("Compare (2)") })
            screen
        }
    }

    /** R6-06 item 15: the trainer card lives on the Stats tab (top of Statistics); its Achievements chip shows the Achievements section. */
    @Test fun trainerCardIsOnTheStatsTab() {
        paparazzi.shootPage("r4n-chips", listOf(Variants.PHONE), themes = listOf(Theme.LIGHT)) { ctx ->
            val team = TeamPage(ctx, NoHost)
            pageScreen(ctx, team)
            call(team, "render")
            assertTrue("no trainer card on the Pokémon tab", texts(team.root).none { it.startsWith("Trainer Lv") })
            assertTrue(texts(team.root).none { it.startsWith("Achievements") })

            val p = StatsPage(ctx, NoHost)
            val screen = pageScreen(ctx, p)
            call(p, "render")
            assertTrue(texts(p.root).any { it.startsWith("Trainer Lv") })
            assertTrue(texts(p.root).any { it.startsWith("XP ") })
            tap(findText(p.root) { it.startsWith("Achievements") }!!)
            call(p, "render")
            assertTrue("the Achievements section is showing", texts(p.root).none { it.startsWith("Trainer Lv") })
            screen
        }
    }

    // ---------- the Stats tab ----------

    private fun history() {
        val today = LocalDate.parse(GameRepo.game!!.dayKey())
        st.day = today.toString(); st.reviewsToday = 86; st.streakDays = 12; st.bestStreakDays = 20
        st.lastGoalDay = today.minusDays(1).toString()
        st.history.clear()
        for (i in 0 until 60) if (i % 5 != 3) st.history[today.minusDays(i.toLong()).toString()] = DayLog(20 + (i * 7) % 90, 3, 1, 0)
        st.badges.clear(); st.badges += listOf(1, 5, 7)
    }

    @Test fun statsStatistics() {
        paparazzi.shootPage("r4n-stats-statistics", listOf(Variants.PHONE, Variants.SPLIT)) { ctx ->
            history()
            pageScreen(ctx, StatsPage(ctx, NoHost))
        }
    }

    @Test fun statsAchievements() {
        paparazzi.shootPage("r4n-stats-achievements", listOf(Variants.PHONE, Variants.SPLIT)) { ctx ->
            history()
            val p = StatsPage(ctx, NoHost, StatsSection.ACHIEVEMENTS)
            pageScreen(ctx, p)
        }
    }

    @Test fun statsSwitchKeepsTheChartState() {
        paparazzi.shootPage("r4n-stats-switch", listOf(Variants.PHONE), themes = listOf(Theme.LIGHT)) { ctx ->
            history()
            val p = StatsPage(ctx, NoHost)
            p.preset(metric = ChartMetric.XP, period = ChartPeriod.D90, bar = 3)
            val screen = pageScreen(ctx, p)
            call(p, "render")
            assertTrue("Daily goal streak" in texts(screen))
            assertTrue("STUDY" !in texts(screen))

            p.showSection(StatsSection.ACHIEVEMENTS)
            call(p, "render")
            assertTrue("STUDY" in texts(screen)) // the achievements page (R4-V2)
            assertTrue("Cards in one day" in texts(screen))
            assertTrue("Daily goal streak" !in texts(screen))

            p.showSection(StatsSection.STATISTICS)
            call(p, "render")
            assertTrue("Daily goal streak" in texts(screen))
            assertEquals(ChartMetric.XP, getField(p, "metric"))
            assertEquals(ChartPeriod.D90, getField(p, "period"))
            assertEquals(3, getField(p, "barSel"))
            screen
        }
    }
}
