package com.husarp.ankigo.shots

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.game.AchFilter
import com.husarp.ankigo.game.AchPageMath
import com.husarp.ankigo.game.Achievements
import com.husarp.ankigo.game.AchievementTable
import com.husarp.ankigo.game.Metric
import com.husarp.ankigo.stats.AnkiStatsStore
import com.husarp.ankigo.stats.StatsSnapshot
import com.husarp.ankigo.ui.AchievementPopup
import com.husarp.ankigo.ui.AppMenu
import com.husarp.ankigo.ui.StatsPage
import com.husarp.ankigo.ui.StatsSection
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.ZoneId

/*
 * TICKET R10-W2-C (W2, D3-A): the Achievements page has TABS (All · Study · Battle · Collection · Economy · Special · Badges) that really
 * filter; under them "Battle · 12 of 50 tiers" and a "Show: All" button whose menu holds the All / Close to next / Recently reached filter.
 * PNGs: r10w2ach-<all|battle|menu|empty|seeit|x2-...>-<phone|w320|float>-<light|dark>.
 */
class R10W2AchievementsShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    @Before fun storeOff() { AnkiStatsStore.enabled = false; AnkiStatsStore.snapshot = StatsSnapshot(); AchievementPopup.reset() }
    @After fun storeOn() { AnkiStatsStore.enabled = true; AnkiStatsStore.snapshot = StatsSnapshot(); AchievementPopup.reset(); AppMenu.intercept = null }

    private val NOW = 1_791_892_800_000L
    private val DAY = 24L * 60 * 60 * 1000
    private val UTC = ZoneId.of("UTC")
    private val W320 = Variant("w320", 320, 640)
    private val FLOAT = Variant("float", 340, 480)
    private val ALLV = listOf(Variants.PHONE, W320, FLOAT)
    private val st get() = GameRepo.state!!
    private var lastPage: StatsPage? = null

    /** A mid-game save with a spread of tiers (the same idea as R4V2Shots). */
    private fun seed() {
        val d = GameRepo.data!!
        st.totalReviews = 31_240; st.bestStreakDays = 12; st.streakDays = 12; st.bestCardStreak = 118; st.defeats = 312; st.catches = 312
        st.evolutions = 12; st.history.clear()
        st.caught.clear(); st.caught += 1..212
        st.seen.clear(); st.seen += 1..260
        st.counters[Metric.DAMAGE_DEALT.name] = 8_420
        st.counters[Metric.MONEY_EARNED.name] = 48_300
        st.counters[Metric.ITEMS_USED.name] = 3
        st.counters[Metric.SHINIES.name] = 2
        st.counters[Metric.STUDY_MINUTES.name] = 2_460
        st.counters[Metric.NEW_CARDS.name] = 380
        st.counters[Metric.DAYS_STUDIED.name] = 41
        st.counters[Metric.GOOD_PRESSES.name] = 5_600
        st.counters[Metric.EASY_PRESSES.name] = 90
        st.badges.clear(); st.badges += listOf(1, 5, 7, 16)
        st.achTiers.clear(); st.badgeAt.clear(); st.achSince = 0
        Achievements.check(st, d, NOW - 60 * DAY, mutableListOf(), silent = true)
        st.achTiers["answers"]?.let { if (it.size > 2) it[2] = NOW - 3 * DAY }
        st.achInferred.removeAll(listOf("answers/2").toSet())
    }

    private fun page(ctx: Context, prep: (com.husarp.ankigo.ui.AchievementsView) -> Unit = {}): View {
        seed()
        val p = StatsPage(ctx, NoHost, StatsSection.ACHIEVEMENTS)
        lastPage = p
        val av = p.achievementsView()
        av.now = { NOW }; av.zone = UTC
        prep(av)
        return pageScreen(ctx, p).also { call(p, "render") }
    }

    private fun render() = call(lastPage!!, "render")

    private fun layoutNow(ctx: Context, v: View) {
        val dm = ctx.resources.displayMetrics
        v.measure(View.MeasureSpec.makeMeasureSpec(dm.widthPixels, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(dm.heightPixels, View.MeasureSpec.EXACTLY))
        v.layout(0, 0, dm.widthPixels, dm.heightPixels)
    }

    /** Renders [build] for every variant and theme (font [scale]); [check] runs on the laid-out page before the snapshot. */
    private fun shoot(name: String, variants: List<Variant>, scale: Float = 1f, themes: List<Theme> = Theme.entries,
                      check: (Context, View) -> Unit = { _, _ -> }, build: (Context) -> View) {
        for (v in variants) for (theme in themes) {
            paparazzi.unsafeUpdateConfig(deviceConfig = v.device(theme).copy(fontScale = scale))
            FakeGame.scenario = FakeGame.Scenario.MID_GAME
            FakeGame.withSprites = true
            val ctx = Shots.files(paparazzi.context)
            FakeGame.install(ctx)
            val view = build(ctx)
            val frame = FrameLayout(ctx).apply { addView(view, FrameLayout.LayoutParams(-1, -1)) }
            layoutNow(ctx, frame)
            try { check(ctx, view) } catch (e: Throwable) { throw AssertionError("${v.label}/${theme.name}: ${e.message}", e) }
            val label = if (scale == 1f) v.label else "${v.label}-x${scale.toString().replace('.', '_')}"
            paparazzi.snapshot(frame, "r10w2ach-$name-$label-${theme.name.lowercase()}", 1000)
        }
    }

    private fun texts(v: View, out: MutableList<String> = ArrayList()): List<String> {
        if (v.visibility == View.GONE) return out
        if (v is TextView) out += v.text.toString()
        if (v is ViewGroup) for (i in 0 until v.childCount) texts(v.getChildAt(i), out)
        return out
    }

    private fun findText(v: View, pred: (String) -> Boolean): TextView? {
        if (v is TextView && v.visibility != View.GONE && pred(v.text.toString())) return v
        if (v is ViewGroup) for (i in 0 until v.childCount) findText(v.getChildAt(i), pred)?.let { return it }
        return null
    }

    private fun clickable(v: View?): View? { var x = v; while (x != null && !x.isClickable) x = x.parent as? View; return x }

    private fun tap(screen: View, text: String) {
        val c = clickable(findText(screen) { it == text })
        assertNotNull("no '$text' to tap", c)
        c!!.performClick()
        render()
    }

    private val TABS = listOf("All") + AchievementTable.GROUPS

    // ---------- All ----------

    @Test fun allTabShowsTheTabsEveryGroupAndTheTotal() {
        shoot("all", ALLV, check = { _, screen ->
            val t = texts(screen)
            val views = Achievements.view(st, GameRepo.data!!)
            for (g in AchievementTable.GROUPS) assertTrue("tab $g", g in t)
            assertTrue(t.toString(), "All" in t)
            // the tab strip keeps its order
            val order = TABS.map { n -> t.indexOf(n) }
            assertEquals(order.sorted(), order)
            assertTrue(t.toString(), "All · ${AchPageMath.tiersReached(views)} of ${AchPageMath.tiersTotal(views)} tiers" in t)
            assertTrue("Show: All" in t)
            for (g in AchievementTable.GROUPS) assertTrue("header $g", g.uppercase() in t) // All = every group under its header
            assertTrue("Answers given" in t && "Wild Pokémon defeated" in t)
        }) { ctx -> page(ctx) }
    }

    // ---------- a group tab ----------

    @Test fun aTabShowsOnlyItsGroup() {
        shoot("battle", ALLV, check = { _, screen ->
            val t = texts(screen)
            assertEquals("Battle", lastPage!!.achievementsView().tab())
            val views = Achievements.view(st, GameRepo.data!!)
            val rows = views.filter { it.group == "Battle" }
            assertTrue(rows.isNotEmpty())
            for (r in rows) assertTrue(r.title, r.title in t)
            for (r in views.filter { it.group != "Battle" }) assertFalse("${r.title} is not Battle", r.title in t)
            for (g in AchievementTable.GROUPS) assertFalse("no header for $g", g.uppercase() in t) // no group headers inside a tab
            assertTrue(t.toString(), t.any { it.startsWith("Battle · ") && it.endsWith(" tiers") })
        }) { ctx -> page(ctx) { it.setTab("Battle") } }
    }

    @Test fun tappingATabSwitchesAndTheTabRowKeepsEveryTab() {
        shoot("tap", listOf(Variants.PHONE), themes = listOf(Theme.LIGHT), check = { _, _ -> }) { ctx ->
            val screen = page(ctx)
            assertEquals("All", lastPage!!.achievementsView().tab())
            tap(screen, "Economy")
            assertEquals("Economy", lastPage!!.achievementsView().tab())
            val t = texts(screen)
            assertTrue("Money earned" in t)
            assertFalse("Wild Pokémon defeated" in t)
            for (n in TABS) assertTrue("tab $n still there", n in t)
            tap(screen, "All")
            assertTrue("Wild Pokémon defeated" in texts(screen))
            screen
        }
    }

    // ---------- the Show menu ----------

    @Test fun showMenuListsTheThreeFiltersAndAppliesInsideTheTab() {
        shoot("menu", ALLV, check = { _, _ -> }) { ctx ->
            val screen = page(ctx) { it.setTab("Battle") }
            layoutNow(ctx, screen)
            val btn = clickable(findText(screen) { it == "Show: All" })
            assertNotNull(btn)
            val m = MenuKit.capture { btn!!.performClick() }
            assertNotNull(m)
            assertEquals(listOf("All", "Close to next", "Recently reached"), m!!.describe().map { it.second })
            val shown = MenuKit.over(ctx, screen, m, Gravity.TOP, 250)
            // picking "Close to next" filters INSIDE the Battle tab (the tab stays, only close rows remain)
            val m2 = MenuKit.capture { btn!!.performClick() }
            m2!!.pick(1)
            render()
            val av = lastPage!!.achievementsView()
            assertEquals(AchFilter.CLOSE, av.filter())
            assertEquals("Battle", av.tab())
            assertTrue("Show: Close to next" in texts(screen))
            val views = Achievements.view(st, GameRepo.data!!)
            val t = texts(screen)
            for (r in views.filter { it.group == "Battle" }) assertEquals(r.title, AchPageMath.close(r), r.title in t)
            shown
        }
    }

    // ---------- empty ----------

    @Test fun aFilteredEmptyTabStaysAndSaysSo() {
        shoot("empty", ALLV, check = { _, screen ->
            val t = texts(screen)
            assertTrue(t.toString(), "Nothing close to the next tier in Badges" in t)
            for (n in TABS) assertTrue("tab $n stays", n in t)
            assertTrue("Show: Close to next" in t)
            assertTrue(t.toString(), "Badges · 0 of 11" in t || t.any { it.startsWith("Badges · ") && it.contains(" of 11") })
        }) { ctx -> page(ctx) { it.setTab("Badges"); it.setFilter(AchFilter.CLOSE) } }
    }

    @Test fun anEmptyAllTabSaysSoToo() {
        shoot("empty-all", listOf(Variants.PHONE), themes = listOf(Theme.LIGHT), check = { _, screen ->
            assertTrue(texts(screen).toString(), texts(screen).any { it.startsWith("Nothing reached") })
        }) { ctx -> page(ctx) { it.setFilter(AchFilter.RECENT); it.setTab("Study") }.also { s ->
            // the Study tab has a row reached 3 days ago; point All at the empty case by dropping every recent date instead
            st.achTiers["answers"]?.let { if (it.size > 2) it[2] = NOW - 90 * DAY }
            render()
            lastPage!!.achievementsView().setTab("All")
            render()
        } }
    }

    // ---------- big text, narrow window ----------

    @Test fun bigTextAt320Dp() {
        shoot("x2", listOf(W320, Variants.PHONE), scale = 2.0f, check = { ctx, screen ->
            val t = texts(screen)
            assertTrue(t.toString(), "Show: Recently reached" in t)
            // the tab strip does not fit: it slides sideways
            val strip = hs(screen)
            assertNotNull(strip)
            assertTrue("the tabs scroll sideways", strip!!.getChildAt(0).width > strip.width)
            // the line and the button never overlap
            val label = findText(screen) { it.startsWith("Battle · ") }!!
            val btn = findText(screen) { it == "Show: Recently reached" }!!
            val lt = origin(label); val bt = origin(btn) // the frame is not attached: add up the parents' offsets by hand
            val apart = bt[0] >= lt[0] + label.width || bt[1] >= lt[1] + label.height
            assertTrue("the count line and the Show button overlap", apart)
            assertTrue(ctx.resources.configuration.fontScale == 2.0f)
        }) { ctx -> page(ctx) { it.setTab("Battle"); it.setFilter(AchFilter.RECENT) } }
        shoot("x2-all", listOf(W320), scale = 2.0f, themes = listOf(Theme.LIGHT)) { ctx -> page(ctx) }
    }

    private fun origin(v: View): IntArray {
        var x = 0; var y = 0; var c: View? = v
        while (c != null) { x += c.left - (c.parent as? View)?.scrollX.let { it ?: 0 }; y += c.top - (c.parent as? View)?.scrollY.let { it ?: 0 }; c = c.parent as? View }
        return intArrayOf(x, y)
    }

    private fun hs(v: View): HorizontalScrollView? {
        if (v is HorizontalScrollView) return v
        if (v is ViewGroup) for (i in 0 until v.childCount) hs(v.getChildAt(i))?.let { return it }
        return null
    }

    // ---------- "See it" ----------

    @Test fun seeItSelectsTheRowsTabAndScrollsToIt() {
        val last = AchievementTable.ROWS.last { it.group == "Badges" }.id
        shoot("seeit", listOf(Variants.SPLIT, W320), themes = listOf(Theme.LIGHT, Theme.DARK), check = { _, screen ->
            val av = lastPage!!.achievementsView()
            assertEquals("Badges", av.tab())
            assertEquals(AchFilter.ALL, av.filter()) // the filter would have hidden it: out of the way
            assertTrue("the row was scrolled into view (y=${av.scrollY()})", av.scrollY() > 0)
            assertTrue("the sheet of the row is open", texts(screen).any { it == "Not earned yet" || it.startsWith("Earned") })
        }) { ctx -> page(ctx) { it.setTab("Study"); it.setFilter(AchFilter.RECENT); it.open(last) } }
    }

    @Test fun tabsStayWhileTheSheetIsOpenedAndClosed() {
        shoot("sheet", listOf(Variants.PHONE), themes = listOf(Theme.LIGHT)) { ctx ->
            val screen = page(ctx) { it.setTab("Collection") }
            tap(screen, "Pokémon caught")
            assertEquals("Collection", lastPage!!.achievementsView().tab())
            assertTrue("Common · 10" in texts(screen))
            screen
        }
    }
}
