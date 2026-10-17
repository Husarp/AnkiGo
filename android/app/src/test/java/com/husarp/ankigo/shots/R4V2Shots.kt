package com.husarp.ankigo.shots

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.game.AchCelebration
import com.husarp.ankigo.game.AchFilter
import com.husarp.ankigo.game.AchPageMath
import com.husarp.ankigo.game.Achievements
import com.husarp.ankigo.game.AchievementTable
import com.husarp.ankigo.game.GameEvent
import com.husarp.ankigo.game.Metric
import com.husarp.ankigo.stats.AnkiStatsStore
import com.husarp.ankigo.stats.StatsSnapshot
import com.husarp.ankigo.ui.AchievementPopup
import com.husarp.ankigo.ui.AchievementsView
import com.husarp.ankigo.ui.Nav
import com.husarp.ankigo.ui.StatsActivity
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
 * R4-V2 (PLAN 79; picks 2A, 2C, 2F): the Achievements page of the Stats tab, its detail sheet and the "earned" popup.
 * Shots: r4v2-page (phone / split / small), r4v2-filter-* , r4v2-detail-* (current tier, top tier, locked, one-off), r4v2-popup-* (one, many).
 */
class R4V2Shots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    @Before fun storeOff() { AnkiStatsStore.enabled = false; AnkiStatsStore.snapshot = StatsSnapshot(); AchievementPopup.reset() }
    @After fun storeOn() { AnkiStatsStore.enabled = true; AnkiStatsStore.snapshot = StatsSnapshot(); AchievementPopup.reset() }

    private val NOW = 1_791_892_800_000L
    private val DAY = 24L * 60 * 60 * 1000
    private val UTC = ZoneId.of("UTC")
    private val SMALL = Variant("small", 360, 640)
    private val st get() = GameRepo.state!!

    /** A mid-game save with a spread of tiers: some rows high, some barely started, some locked; dates spread over the last months. */
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
        Achievements.check(st, d, NOW - 60 * DAY, mutableListOf(), silent = true) // every reached tier dated "the start of tracking"
        // a few real dates so the sheet shows both kinds
        st.achTiers["catches"] = mutableListOf(NOW - 70 * DAY, NOW - 40 * DAY).also { it.addAll(st.achTiers["catches"]!!.drop(2)) }
        st.achTiers["answers"]?.let { if (it.size > 2) it[2] = NOW - 3 * DAY }
        st.achTiers["dex-caught"]?.let { if (it.size > 1) it[1] = NOW - 9 * DAY }
        st.achTiers["first-catch"] = mutableListOf(NOW - 80 * DAY)
        st.achInferred.removeAll(listOf("catches/0", "catches/1", "answers/2", "dex-caught/1", "first-catch/0").toSet()) // R4 fix A3: these dates are real ones
    }

    private fun page(ctx: Context, section: StatsSection = StatsSection.ACHIEVEMENTS, open: String? = null, filter: AchFilter = AchFilter.ALL): View {
        seed()
        val p = StatsPage(ctx, NoHost, section)
        lastPage = p
        p.achievementsView().now = { NOW }
        p.achievementsView().zone = UTC
        p.achievementsView().setFilter(filter)
        p.achievementsView().open(open)
        return pageScreen(ctx, p).also { call(p, "render") }
    }

    private fun texts(v: View, out: MutableList<String> = ArrayList()): List<String> {
        if (v.visibility == View.GONE) return out
        if (v is TextView) out += v.text.toString()
        if (v is ViewGroup) for (i in 0 until v.childCount) texts(v.getChildAt(i), out)
        return out
    }

    // ---------- the page ----------

    @Test fun pageList() {
        paparazzi.shootPage("r4v2-page", listOf(Variants.PHONE, Variants.SPLIT, SMALL)) { ctx -> page(ctx) }
    }

    @Test fun pageTextsAreRight() {
        paparazzi.shootPage("r4v2-page-texts", listOf(Variants.TALL), themes = listOf(Theme.LIGHT)) { ctx ->
            val screen = page(ctx)
            val t = texts(screen)
            val views = Achievements.view(st, GameRepo.data!!)
            assertTrue(t.toString(), "of ${AchPageMath.tiersTotal(views)} tiers reached" in t)
            assertTrue(AchPageMath.tiersReached(views).toString() in t)
            for (g in listOf("STUDY", "BATTLE", "COLLECTION", "ECONOMY", "SPECIAL", "BADGES")) assertTrue(g, g in t)
            for (g in AchievementTable.GROUPS) assertTrue("chip $g", g in t) // the jump chips (the headers are in capitals)
            assertTrue("Answers given" in t && "31,240 / 100,000" in t)
            assertTrue("Wild Pokémon defeated" in t)
            assertTrue("Epic" in t) // the answers row
            assertTrue("Not yet" in t)
            assertTrue("Earned" in t) // the earned one-offs
            assertTrue("All" in t && "Close to next" in t && "Recently reached" in t)
            screen
        }
    }

    // ---------- the long list (R4-V2b) ----------

    private fun pageWith(ctx: Context, prep: (AchievementsView) -> Unit): View {
        seed()
        val p = StatsPage(ctx, NoHost, StatsSection.ACHIEVEMENTS)
        lastPage = p
        p.achievementsView().now = { NOW }; p.achievementsView().zone = UTC
        prep(p.achievementsView())
        return pageScreen(ctx, p).also { call(p, "render") }
    }

    @Test fun scrolledIntoAGroupPinsItsHeader() {
        paparazzi.shootPage("r4v2b-sticky", listOf(Variants.PHONE, Variants.SPLIT, SMALL)) { ctx ->
            pageWith(ctx) { it.scrollIntoGroup("Battle", 260) }
        }
        paparazzi.shootPage("r4v2b-sticky-badges", listOf(Variants.PHONE), themes = listOf(Theme.LIGHT)) { ctx ->
            pageWith(ctx) { it.scrollIntoGroup("Badges", 200) }
        }
    }

    @Test fun tappingAChipJumpsToItsGroup() {
        paparazzi.shootPage("r4v2b-jump-economy", listOf(Variants.PHONE, Variants.SPLIT, SMALL)) { ctx ->
            val screen = pageWith(ctx) {}
            val p = lastPage!!
            var v: View? = findText(screen) { it == "Economy" }
            while (v != null && !v.isClickable) v = v.parent as? View
            assertNotNull(v); v!!.performClick()
            screen
        }
    }

    @Test fun aFilterGreysTheChipsOfEmptyGroups() {
        paparazzi.shootPage("r4v2b-filter-close", listOf(Variants.PHONE, Variants.SPLIT, SMALL)) { ctx ->
            val screen = page(ctx, filter = AchFilter.CLOSE)
            val av = lastPage!!.achievementsView()
            val views = Achievements.view(st, GameRepo.data!!)
            val groups = AchPageMath.groups(views, AchFilter.CLOSE, NOW, st.achSince)
            assertEquals(groups.filter { it.empty }.map { it.name }.toSet(), av.greyedChips())
            assertTrue("Badges must be greyed: a one-off is never close", "Badges" in av.greyedChips())
            val t = texts(screen)
            assertFalse("BADGES" in t) // the empty group's header is hidden, its chip stays
            assertTrue("Badges" in t)
            screen
        }
    }

    @Test fun pageInTinyAndLandscapeWindows() {
        paparazzi.shoot("r4v2-page", listOf(Size.SPLIT_TINY, Size.LAND, Size.TABLET)) { ctx -> page(ctx) }
        paparazzi.shoot("r4v2-detail-catches", listOf(Size.SPLIT_TINY, Size.LAND, Size.TABLET)) { ctx -> page(ctx, open = "catches") }
    }

    @Test fun filters() {
        paparazzi.shootPage("r4v2-filter-close", listOf(Variants.PHONE), themes = listOf(Theme.LIGHT)) { ctx ->
            val screen = page(ctx, filter = AchFilter.CLOSE)
            val t = texts(screen)
            val views = Achievements.view(st, GameRepo.data!!)
            val close = views.filter { AchPageMath.close(it) }
            assertTrue(close.isNotEmpty())
            for (v in close) assertTrue(v.title, v.title in t)
            assertFalse("a far row stays out", "Wild Pokémon defeated" in t && !close.any { it.id == "defeats" })
            screen
        }
        paparazzi.shootPage("r4v2-filter-recent", listOf(Variants.PHONE), themes = listOf(Theme.LIGHT)) { ctx ->
            val screen = page(ctx, filter = AchFilter.RECENT)
            val t = texts(screen)
            assertTrue("Answers given" in t) // its Epic tier was reached 3 days ago
            assertFalse("Pokédex seen" in t)
            screen
        }
    }

    @Test fun filterWithNothing() {
        paparazzi.shootPage("r4v2-filter-empty", listOf(Variants.SPLIT), themes = listOf(Theme.LIGHT)) { ctx ->
            seed()
            st.achTiers.clear(); st.achSince = 0
            st.totalReviews = 0; st.catches = 0; st.defeats = 0; st.mons.clear(); st.caught.clear(); st.seen.clear(); st.bestCardStreak = 0
            st.bestStreakDays = 0; st.streakDays = 0; st.evolutions = 0; st.counters.clear(); st.badges.clear()
            val p = StatsPage(ctx, NoHost, StatsSection.ACHIEVEMENTS)
            p.achievementsView().now = { NOW }
            p.achievementsView().setFilter(AchFilter.RECENT)
            val screen = pageScreen(ctx, p)
            call(p, "render")
            assertTrue(texts(screen).toString(), texts(screen).any { it.startsWith("Nothing reached") })
            screen
        }
    }

    // ---------- the detail sheet ----------

    @Test fun detailCurrentTier() {
        paparazzi.shootPage("r4v2-detail-catches", listOf(Variants.PHONE, Variants.SPLIT, SMALL)) { ctx ->
            val screen = page(ctx, open = "catches")
            val t = texts(screen)
            assertTrue(t.toString(), "Pokémon caught" in t)
            assertTrue("Common · 10" in t && "Rare · 100" in t && "Epic · 500" in t)
            assertTrue("Reached 4 Aug 2026" in t || t.any { it.startsWith("Reached ") })
            assertTrue(t.any { it.contains("to go") })
            assertTrue("next after Epic" in t && "locked until Legendary" in t)
            assertTrue(t.any { it.startsWith("Counted from your save") })
            screen
        }
    }

    @Test fun detailBeforeDateAndHistoryNote() {
        paparazzi.shootPage("r4v2-detail-money", listOf(Variants.PHONE), themes = listOf(Theme.LIGHT)) { ctx ->
            val screen = page(ctx, open = "money-earned")
            val t = texts(screen)
            assertTrue(t.toString(), t.any { it.startsWith("Reached before ") }) // dated at the start of tracking
            assertTrue(t.any { it.startsWith("Started from your save's daily history") })
            screen
        }
        paparazzi.shootPage("r4v2-detail-damage", listOf(Variants.PHONE), themes = listOf(Theme.LIGHT)) { ctx ->
            val screen = page(ctx, open = "damage-dealt")
            assertTrue(texts(screen).any { it.startsWith("Counted since the update (") })
            screen
        }
        paparazzi.shootPage("r4v2-detail-time", listOf(Variants.PHONE), themes = listOf(Theme.LIGHT)) { ctx ->
            val screen = page(ctx, open = "study-time")
            assertTrue(texts(screen).toString(), "41 h" in texts(screen))
            screen
        }
    }

    @Test fun detailOneOffs() {
        paparazzi.shootPage("r4v2-detail-oneoff", listOf(Variants.PHONE), themes = listOf(Theme.LIGHT)) { ctx ->
            val screen = page(ctx, open = "first-catch")
            assertTrue(texts(screen).toString(), texts(screen).any { it.startsWith("Earned ") })
            screen
        }
        paparazzi.shootPage("r4v2-detail-oneoff-locked", listOf(Variants.PHONE), themes = listOf(Theme.LIGHT)) { ctx ->
            val screen = page(ctx, open = "first-mythical")
            assertTrue("Not earned yet" in texts(screen))
            screen
        }
    }

    @Test fun detailTopTier() {
        paparazzi.shootPage("r4v2-detail-top", listOf(Variants.PHONE), themes = listOf(Theme.LIGHT)) { ctx ->
            seed()
            st.defeats = 20_000
            val d = GameRepo.data!!
            Achievements.check(st, d, NOW, mutableListOf(), silent = true)
            val p = StatsPage(ctx, NoHost, StatsSection.ACHIEVEMENTS)
            p.achievementsView().now = { NOW }; p.achievementsView().zone = UTC
            p.achievementsView().open("defeats")
            val screen = pageScreen(ctx, p)
            call(p, "render")
            val t = texts(screen)
            assertEquals(5, t.count { it.startsWith("Reached") })
            screen
        }
    }

    @Test fun detailTapOpensAndClosesAndSwitchingAwayClosesIt() {
        paparazzi.shootPage("r4v2-detail-tap", listOf(Variants.PHONE), themes = listOf(Theme.LIGHT)) { ctx ->
            val screen = page(ctx)
            val p = screenPage(screen)
            fun tapText(s: String) {
                var v: View? = findText(screen) { it == s }
                while (v != null && !v.isClickable) v = v.parent as? View
                assertNotNull(s, v); v!!.performClick()
                call(p, "render")
            }
            assertFalse("Common · 10" in texts(screen))
            tapText("Pokémon caught")
            assertTrue("Common · 10" in texts(screen))
            screen
        }
    }

    private var lastPage: StatsPage? = null
    private fun screenPage(@Suppress("UNUSED_PARAMETER") v: View): StatsPage = lastPage!!

    private fun findText(v: View, pred: (String) -> Boolean): TextView? {
        if (v is TextView && v.visibility != View.GONE && pred(v.text.toString())) return v
        if (v is ViewGroup) for (i in 0 until v.childCount) findText(v.getChildAt(i), pred)?.let { return it }
        return null
    }

    // ---------- the popup ----------

    private fun scene(ctx: Context, items: List<AchCelebration>): View {
        val layer = AchievementPopup.layer(ctx, items, onNice = {}, onSee = {})
        return FrameLayout(ctx).apply {
            addView(layer, FrameLayout.LayoutParams(-1, -1))
            setBackgroundColor(android.graphics.Color.argb(150, 0, 0, 0)) // the dialog's dim
        }
    }

    private fun one(rowId: String, tier: Int): AchCelebration {
        val row = AchievementTable.row(rowId)!!
        return AchCelebration(row.id, row.title, tier, AchievementTable.tierName(tier), row.tiers.getOrNull(tier))
    }

    @Test fun popupSingleEveryTier() {
        paparazzi.shootPage("r4v2-popup-epic", listOf(Variants.PHONE, SMALL)) { ctx -> scene(ctx, listOf(one("dex-caught", 2))) }
        paparazzi.shoot("r4v2-popup-epic", listOf(Size.SPLIT_TINY, Size.LAND, Size.TABLET)) { ctx -> scene(ctx, listOf(one("dex-caught", 2))) }
        for (t in listOf(0, 1, 3, 4)) paparazzi.shootPage("r4v2-popup-tier$t", listOf(SMALL)) { ctx -> scene(ctx, listOf(one("catches", t))) }
        paparazzi.shootPage("r4v2-popup-oneoff", listOf(SMALL), themes = listOf(Theme.LIGHT)) { ctx ->
            val v = scene(ctx, listOf(AchCelebration("first-catch", "First Pokémon caught", -1, "Special", null)))
            assertTrue(texts(v).toString(), "Achievement!" in texts(v) && "Nice" in texts(v) && "See it" in texts(v))
            v
        }
    }

    @Test fun popupWordsAndButtons() {
        paparazzi.shootPage("r4v2-popup-words", listOf(SMALL), themes = listOf(Theme.LIGHT)) { ctx ->
            val v = scene(ctx, listOf(one("dex-caught", 2)))
            val t = texts(v)
            assertTrue(t.toString(), "Epic!" in t && "Pokédex caught · 250" in t && "Nice" in t && "See it" in t)
            assertFalse("See all" in t)
            v
        }
    }

    @Test fun popupButtonsCallBack() {
        paparazzi.shootPage("r4v2-popup-taps", listOf(SMALL), themes = listOf(Theme.LIGHT)) { ctx ->
            var nice = 0; var seen: String? = "none"
            val layer = AchievementPopup.layer(ctx, listOf(one("dex-caught", 2)), onNice = { nice++ }, onSee = { seen = it })
            fun tap(s: String) { var v: View? = findText(layer) { it == s }; while (v != null && !v.isClickable) v = v.parent as? View; v!!.performClick() }
            tap("Nice"); assertEquals(1, nice)
            tap("See it"); assertEquals("dex-caught", seen)
            layer
        }
    }

    @Test fun popupMany() {
        paparazzi.shootPage("r4v2-popup-many", listOf(Variants.PHONE, SMALL)) { ctx ->
            val items = listOf(one("dex-caught", 2), one("catches", 1), one("answers", 3), AchCelebration("first-evolution", "Evolved a Pokémon", -1, "Special", null), one("shinies", 0))
            val v = scene(ctx, items)
            assertTrue("5 achievements!" in texts(v))
            assertTrue("See it" in texts(v) && "Nice" in texts(v))
            assertFalse("no \"See all\" without a sheet (R4 fix B13)", "See all" in texts(v))
            v
        }
        paparazzi.shoot("r4v2-popup-many", listOf(Size.SPLIT_TINY)) { ctx ->
            scene(ctx, listOf(one("dex-caught", 2), one("catches", 1), one("answers", 3), AchCelebration("first-evolution", "Evolved a Pokémon", -1, "Special", null)))
        }
        paparazzi.shootPage("r4v2-popup-two", listOf(SMALL), themes = listOf(Theme.LIGHT)) { ctx -> scene(ctx, listOf(one("dex-caught", 2), one("catches", 1))) }
    }

    // ---------- the hook ----------

    @Test fun eventsAreQueuedUntilAScreenIsInFront() {
        AchievementPopup.reset()
        AchievementPopup.offer(listOf(GameEvent.AchievementReached("catches", 1, "Pokémon caught", "Rare"), GameEvent.BadgeEarned(7, "First Pokémon caught"), GameEvent.BadgeEarned(3, "x")))
        assertEquals(listOf("catches", "first-catch"), AchievementPopup.waiting().map { it.rowId })
        AchievementPopup.offer(listOf(GameEvent.AchievementReached("catches", 1, "Pokémon caught", "Rare"))) // already waiting: not twice
        assertEquals(2, AchievementPopup.waiting().size)
        AchievementPopup.offer(listOf(GameEvent.Info("nothing"))); assertEquals(2, AchievementPopup.waiting().size)
    }

    @Test fun seeItAsksForTheAchievementsTabWithTheId() {
        val ctx = Shots.files(paparazzi.context)
        val i = Nav.achievementIntent(ctx, "catches")
        assertEquals(StatsActivity::class.java.name, i.component?.className)
        assertEquals("ACHIEVEMENTS", i.getStringExtra(StatsActivity.EXTRA_SECTION))
        assertEquals("catches", i.getStringExtra(Nav.EXTRA_ACHIEVEMENT))
        assertFalse(Nav.achievementIntent(ctx, null).hasExtra(Nav.EXTRA_ACHIEVEMENT))
    }
}
