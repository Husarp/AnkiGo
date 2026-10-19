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
import com.husarp.ankigo.live.LiveSession
import com.husarp.ankigo.stats.AnkiStatsStore
import com.husarp.ankigo.stats.StatsSnapshot
import com.husarp.ankigo.ui.BattleCard
import com.husarp.ankigo.ui.Dest
import com.husarp.ankigo.ui.HomeView
import com.husarp.ankigo.ui.PageHost
import com.husarp.ankigo.ui.StatsPage
import com.husarp.ankigo.ui.StatsSection
import com.husarp.ankigo.ui.TeamPage
import com.husarp.ankigo.ui.TeamSection
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/*
 * R4-V1 (PLAN 88 + 108, Adam's pick 1): navigation as picked. The battle card at the top of the Pokémon tab's Team section
 * (live while on screen: it holds LiveSession only then; tap = the full battle, Back returns to the tab; Pop out through the
 * home screen), the four icon tabs with the accent square on the selected one, "Pokémon" + coins on every section, the plain
 * Stats switch without a back arrow, the Shop's "Refresh shop" button. Shots: r4v1-*.
 */
class R4V1Shots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    @Before fun storeOff() { AnkiStatsStore.enabled = false; AnkiStatsStore.snapshot = StatsSnapshot() }
    @After fun storeOn() { AnkiStatsStore.enabled = true; AnkiStatsStore.snapshot = StatsSnapshot() }

    private val st get() = GameRepo.state!!
    private val FLOAT = Variant("float", Size.FLOAT.wDp, Size.FLOAT.hDp)
    private val SMALL = Variant("small", Size.SMALL.wDp, Size.SMALL.hDp)
    private val TABLET = Variant("tablet", Size.TABLET.wDp, Size.TABLET.hDp)

    /** A page in an Activity (the card needs one; [NoHost] has none, like a floating card). */
    private class ActivityHost(val activity: Activity) : PageHost {
        var closed = 0
        override val hostActivity: Activity? get() = activity
        override fun closePage() { closed++ }
        override fun startForResult(intent: Intent, onResult: (Int, Intent?) -> Unit) = Unit
    }

    /** Takes what a page starts instead of starting it. */
    private class Capture(base: Context) : ContextWrapper(base) {
        val started = ArrayList<Intent>()
        override fun startActivity(intent: Intent) { started += intent }
    }

    /** The live session as the card sees it: counts who holds it and which sinks are on. */
    private class FakeLive : BattleCard.Live {
        override var paused = false
        val holders = LinkedHashSet<Any>()
        val sinks = ArrayList<LiveSession.Sink>()
        var pauseListeners = 0
        override fun hold(ctx: Context, owner: Any) { holders += owner }
        override fun release(ctx: Context, owner: Any) { holders -= owner }
        override fun addSink(s: LiveSession.Sink) { sinks += s }
        override fun removeSink(s: LiveSession.Sink) { sinks -= s }
        override fun addPauseListener(l: (Boolean) -> Unit) { pauseListeners++ }
        override fun removePauseListener(l: (Boolean) -> Unit) { pauseListeners-- }
    }

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

    private fun findDescription(v: View, d: String): View? {
        if (v.contentDescription?.toString() == d) return v
        if (v is ViewGroup) for (i in 0 until v.childCount) findDescription(v.getChildAt(i), d)?.let { return it }
        return null
    }

    private fun tap(v: View) {
        var x: View? = v
        while (x != null && !x.isClickable) x = x.parent as? View
        assertNotNull("nothing clickable around the view", x)
        x!!.performClick()
    }

    /** A Pokémon tab in an Activity, on [s], drawn once. */
    private fun page(ctx: Context, s: TeamSection = TeamSection.TEAM, session: FakeLive = FakeLive()): Pair<TeamPage, View> {
        val p = TeamPage(ctx, ActivityHost(attach(Activity(), ctx)))
        p.cardSession = session // never the real LiveSession: its detector needs AnkiDroid
        p.preset(s)
        val screen = pageScreen(ctx, p)
        call(p, "render")
        return p to screen
    }

    /** The Shop tab (R10 W11: a page of its own, no longer a Pokémon-tab section) in an Activity, drawn once. */
    private fun shopPage(ctx: Context): Pair<com.husarp.ankigo.ui.ShopPage, View> {
        val p = com.husarp.ankigo.ui.ShopPage(ctx, ActivityHost(attach(Activity(), ctx)))
        val screen = pageScreen(ctx, p)
        call(p, "render")
        return p to screen
    }

    // ---------------- pure rules

    @Test fun theCardShowsForAReadyGameInAnActivity() {
        assertTrue(BattleCard.wanted(ready = true, inActivity = true))
        assertFalse("no game", BattleCard.wanted(ready = false, inActivity = true))
        assertFalse("floating card", BattleCard.wanted(ready = true, inActivity = false))
    }

    @Test fun thePanelFollowsTheWindowBetween170And210dp() {
        assertEquals(210, BattleCard.panelDp(1280f))
        assertEquals(208, BattleCard.panelDp(869f))
        assertEquals(170, BattleCard.panelDp(640f))
        assertEquals(170, BattleCard.panelDp(560f))
        assertEquals("unknown height counts as tall", 170, BattleCard.panelDp(0f))
    }

    /** R4 fix B5: a short window (split-screen half, landscape phone) gets a smaller panel instead of no card. */
    @Test fun aShortWindowGetsASmallerPanel() {
        assertEquals(132, BattleCard.panelDp(400f))
        assertEquals(110, BattleCard.panelDp(300f))
        assertEquals(110, BattleCard.panelDp(250f))
        assertEquals(150, BattleCard.panelDp(559f))
        for (h in listOf(250f, 300f, 360f, 412f, 480f, 559f)) assertTrue("$h", BattleCard.panelDp(h) in 110..150)
        assertTrue("smaller than any tall one", BattleCard.panelDp(559f) < BattleCard.panelDp(560f))
    }

    // ---------------- the Pokémon tab: bar, switcher, counts

    @Test fun theBarSaysPokemonWithTheCoinsOnEverySection() {
        val ctx = Shots.files(paparazzi.context).also { FakeGame.install(it) }
        st.cash = 1240
        for (s in TeamSection.entries) {
            val (_, screen) = page(ctx, s)
            val shown = texts(screen)
            assertTrue("$s ${shown.take(8)}", "Pokémon" in shown)
            assertTrue("$s coins", "1,240" in shown)
        }
    }

    @Test fun theCountsThatWereInThePillAreInTheSections() {
        val ctx = Shots.files(paparazzi.context).also { FakeGame.install(it) }
        val (_, team) = page(ctx, TeamSection.TEAM)
        assertTrue(texts(team).any { it.startsWith("TEAM · ") && it.endsWith(" / 6") })
        // the Box and Pokédex headers sit in their lists (not laid out in a render pass): read them from the page
        val (boxPage, _) = page(ctx, TeamSection.BOX)
        val boxTexts = texts(getField(boxPage, "boxHeader") as View)
        assertTrue("Box count: $boxTexts", boxTexts.any { it.endsWith(" Pokémon") || it.contains(" Pokémon ·") })
        val (dexHost, _) = page(ctx, TeamSection.DEX)
        val dexPage = (getField(dexHost, "dexPage\$delegate") as Lazy<*>).value!!
        val dexTexts = texts(getField(dexPage, "topBox") as View)
        assertTrue("Pokédex numbers and generations: $dexTexts", dexTexts.any { it.startsWith("of ") && it.contains("Gen ") })
    }

    @Test fun theSwitcherHasThreeIconTabsAndOneSelected() { // R10 W11: the Shop left the switcher (it is a bottom tab)
        val ctx = Shots.files(paparazzi.context).also { FakeGame.install(it) }
        for (s in TeamSection.entries) {
            val (p, screen) = page(ctx, s)
            val box = getField(p, "switchBox") as ViewGroup
            assertEquals(listOf("Team", "Box", "Pokédex"), (0 until box.childCount).map { box.getChildAt(it).contentDescription.toString() })
            // each tab: an icon over its label; only the selected one has the accent rounded square
            val on = (0 until box.childCount).filter { box.getChildAt(it).background != null }
            assertEquals("$s", listOf(s.ordinal), on)
            for (i in 0 until box.childCount) {
                val tab = box.getChildAt(i) as ViewGroup
                assertTrue("icon first", tab.getChildAt(0) is android.widget.ImageView)
                assertTrue("label under it", tab.getChildAt(1) is TextView)
            }
            assertNotNull(screen)
        }
    }

    @Test fun aTapOnATabSwitchesTheSection() {
        val ctx = Shots.files(paparazzi.context).also { FakeGame.install(it) }
        val (p, screen) = page(ctx, TeamSection.TEAM)
        val box = getField(p, "switchBox") as ViewGroup
        box.getChildAt(TeamSection.BOX.ordinal).performClick()
        call(p, "render")
        assertEquals(View.VISIBLE, (getField(p, "boxList") as View).visibility)
        assertEquals(TeamSection.BOX, getField(p, "section"))
        assertNotNull(screen)
    }

    // ---------------- the battle card

    @Test fun theCardIsOnTheTeamSectionOnly() {
        val ctx = Shots.files(paparazzi.context).also { FakeGame.install(it) }
        val (p, screen) = page(ctx, TeamSection.TEAM, FakeLive())
        assertTrue("BATTLE" in texts(screen))
        assertTrue("Pop out" in texts(screen))
        for (s in listOf(TeamSection.BOX, TeamSection.DEX)) {
            p.showSection(s); call(p, "render")
            assertFalse("$s", "BATTLE" in texts(screen))
        }
        p.showSection(TeamSection.TEAM); call(p, "render")
        assertTrue("BATTLE" in texts(screen))
    }

    @Test fun theCardHoldsTheLiveSessionOnlyWhileItIsOnScreen() {
        val ctx = Shots.files(paparazzi.context).also { FakeGame.install(it) }
        val live = FakeLive()
        val (p, _) = page(ctx, TeamSection.TEAM, live)
        assertTrue(p.cardLive)
        assertEquals("held once, with a sink and a pause listener", 1, live.holders.size)
        assertEquals(1, live.sinks.size); assertEquals(1, live.pauseListeners)
        call(p, "render")
        assertEquals("a redraw holds nothing more", 1, live.holders.size)

        p.showSection(TeamSection.BOX); call(p, "render")
        assertFalse(p.cardLive)
        assertEquals("another section lets it go", 0, live.holders.size)
        assertEquals(0, live.sinks.size); assertEquals(0, live.pauseListeners)

        p.showSection(TeamSection.TEAM); call(p, "render")
        assertEquals("back on Team: held again", 1, live.holders.size)

        p.onHide()
        assertEquals("the page paused: nothing holds", 0, live.holders.size)
        assertEquals(0, live.sinks.size)
        p.onShow(); call(p, "render")
        assertEquals("the page is back: held again", 1, live.holders.size)

        p.onGone()
        assertEquals("the page is gone: nothing holds", 0, live.holders.size)
    }

    @Test fun noCardInAFloatingCardButOneInAShortWindow() {
        val ctx = Shots.files(paparazzi.context).also { FakeGame.install(it) }
        // a floating card has no Activity
        val live = FakeLive()
        val p = TeamPage(ctx, NoHost).also { it.cardSession = live; it.preset(TeamSection.TEAM) }
        val screen = pageScreen(ctx, p)
        call(p, "render")
        assertFalse("BATTLE" in texts(screen))
        assertEquals(0, live.holders.size)
        // R4 fix B5: a short window (split-screen half) has the card too (its Team section has no battle of its own): live, held once
        paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.SPLIT, Theme.LIGHT))
        val short = Shots.files(paparazzi.context).also { FakeGame.install(it) }
        val live2 = FakeLive()
        val (p2, screen2) = page(short, TeamSection.TEAM, live2)
        assertTrue("BATTLE" in texts(screen2))
        assertTrue(p2.cardLive)
        assertEquals(1, live2.holders.size)
        p2.showSection(TeamSection.BOX); call(p2, "render")
        assertEquals("another section lets it go, also in a short window", 0, live2.holders.size)
    }

    @Test fun aTapOnTheCardOpensTheBattleAndBackComesBackHere() {
        val ctx = Shots.files(paparazzi.context).also { FakeGame.install(it) }
        val cap = Capture(ctx)
        val p = TeamPage(cap, ActivityHost(attach(Activity(), ctx))).also { it.cardSession = FakeLive(); it.preset(TeamSection.TEAM) }
        val screen = pageScreen(cap, p)
        call(p, "render")
        tap(findDescription(screen, "Open the full battle")!!)
        tap(findText(screen) { it == "BATTLE" }!!)
        assertEquals(2, cap.started.size)
        for (i in cap.started) {
            assertEquals("com.husarp.ankigo.ui.GameActivity", i.component?.className)
            assertEquals("battle", i.getStringExtra(HomeView.EXTRA_VIEW))
            assertEquals("the home screen's Back returns to the Pokémon tab", Dest.TEAM.name, i.getStringExtra(HomeView.EXTRA_FROM))
            assertTrue(i.flags and Intent.FLAG_ACTIVITY_REORDER_TO_FRONT != 0)
        }
    }

    @Test fun popOutGoesThroughTheHomeScreenWithoutChangingItsView() {
        val ctx = Shots.files(paparazzi.context).also { FakeGame.install(it) }
        val cap = Capture(ctx)
        val host = ActivityHost(attach(Activity(), ctx))
        val p = TeamPage(cap, host).also { it.cardSession = FakeLive(); it.preset(TeamSection.TEAM) }
        val screen = pageScreen(cap, p)
        call(p, "render")
        tap(findText(screen) { it == "Pop out" }!!)
        assertEquals("the tab closes behind it, so Back from the home screen is not a stale tab", 1, host.closed)
        assertEquals(1, cap.started.size)
        val i = cap.started[0]
        assertEquals("com.husarp.ankigo.ui.GameActivity", i.component?.className)
        assertTrue(i.getBooleanExtra(HomeView.EXTRA_POP_OUT, false))
        assertNull("no view asked for: the home screen keeps its own", i.getStringExtra(HomeView.EXTRA_VIEW))
    }

    @Test fun navBattleIntentCarriesTheOriginOnlyForTheBattle() {
        val ctx = Shots.files(paparazzi.context)
        assertEquals("TEAM", com.husarp.ankigo.ui.Nav.intent(ctx, Dest.BATTLE, from = Dest.TEAM).getStringExtra(HomeView.EXTRA_FROM))
        assertNull(com.husarp.ankigo.ui.Nav.intent(ctx, Dest.BATTLE).getStringExtra(HomeView.EXTRA_FROM))
        assertNull("Study has no way back", com.husarp.ankigo.ui.Nav.intent(ctx, Dest.STUDY, from = Dest.TEAM).getStringExtra(HomeView.EXTRA_FROM))
    }

    // ---------------- the Stats tab

    @Test fun theStatsTabIsCalledStatsWithoutABackArrowOnBothSections() {
        val ctx = Shots.files(paparazzi.context).also { FakeGame.install(it) }
        for (s in StatsSection.entries) {
            val p = StatsPage(ctx, ActivityHost(attach(Activity(), ctx)))
            p.preset(section = s)
            val screen = pageScreen(ctx, p)
            call(p, "render")
            val shown = texts(screen)
            assertTrue("$s ${shown.take(6)}", "Stats" in shown)
            assertNull("$s a tab has no back arrow", findDescription(screen, "Back"))
            val box = getField(p, "switchBox") as ViewGroup
            assertEquals(listOf("Statistics", "Achievements"), (0 until box.childCount).map { box.getChildAt(it).contentDescription.toString() })
            // the plain segmented switch: only the labels (no icons)
            for (i in 0 until box.childCount) assertTrue("$s", (box.getChildAt(i) as ViewGroup).getChildAt(0) is TextView)
        }
    }

    // ---------------- the Shop's refresh button

    @Test fun theShopHasAnEasyToFindRefreshButton() {
        val ctx = Shots.files(paparazzi.context).also { FakeGame.install(it) }
        st.cash = 5000
        val (_, screen) = shopPage(ctx)
        val btn = findText(screen) { it.startsWith("Refresh shop") }
        assertNotNull(texts(screen).toString(), btn)
        assertEquals("Refresh shop · 100 ₽", btn!!.text.toString())
        var row: View? = btn
        while (row != null && !row.isClickable) row = row.parent as? View
        assertEquals("a full-strength button when the money is there", 1f, row!!.alpha, 0f)
        val (_, poor) = shopPage(ctx.also { st.cash = 20 })
        var weak: View? = findText(poor) { it.startsWith("Refresh shop") }
        while (weak != null && !weak.isClickable) weak = weak.parent as? View
        assertTrue("greyed out without the money", weak!!.alpha < 1f)
    }

    // ---------------- shots

    /** The Team section with the battle card (phone, small phone, tablet, and the split half with a smaller panel). */
    @Test fun teamWithTheBattleCard() {
        paparazzi.shootPage("r4v1-team-card", listOf(Variants.PHONE, SMALL, TABLET, Variants.SPLIT)) { ctx -> page(ctx, TeamSection.TEAM, FakeLive()).second }
    }

    /** All four sections in the 340 dp floating card (no card there) and on the phone: the icon tabs. */
    @Test fun switcherFloatingCard() {
        for (s in TeamSection.entries) paparazzi.shootPage("r4v1-tabs-${s.name.lowercase()}", listOf(FLOAT)) { ctx ->
            val p = TeamPage(ctx, NoHost).also { it.preset(s) }
            p.root.also { p.onShow() }
        }
    }

    @Test fun boxAndShopOnThePhone() {
        paparazzi.shootPage("r4v1-box", listOf(Variants.PHONE)) { ctx -> page(ctx, TeamSection.BOX).second }
        paparazzi.shootPage("r4v1-shop", listOf(Variants.PHONE, Variants.SPLIT)) { ctx -> shopPage(ctx).second }
    }

    @Test fun statsTab() {
        paparazzi.shootPage("r4v1-stats", listOf(Variants.PHONE)) { ctx ->
            val p = StatsPage(ctx, ActivityHost(attach(Activity(), ctx)))
            pageScreen(ctx, p)
        }
    }
}
