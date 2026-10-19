package com.husarp.ankigo.game

import com.husarp.ankigo.ui.AchStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

/** R4-V2: filters, header counts, ladder, source flags, badge -> row mapping, the popup's celebrations, icon keys. */
class AchievementPageMathTest {
    private val DAY = 24L * 60 * 60 * 1000
    private val NOW = 1_791_892_800_000L
    private val SINCE = NOW - 100 * DAY

    private fun view(tiers: List<Long> = listOf(10, 100, 500, 2_000, 5_000), tier: Int, value: Long, dates: List<Long> = emptyList(), oneOff: Boolean = false, id: String = "catches"): AchView {
        val next = tiers.getOrNull(tier + 1)
        return AchView(id, "Collection", "Pokémon caught", "d", "ball", oneOff, null, tier, if (tier >= 0) AchievementTable.tierName(tier) else null,
            dates, value, next, if (next == null) 1f else (value.toDouble() / next).coerceIn(0.0, 1.0).toFloat(), tiers)
    }

    // ---- filters ----

    @Test fun closeToNextMeansThreeQuartersOfTheWay() {
        assertFalse(AchPageMath.close(view(tier = 1, value = 374))) // 374 / 500
        assertTrue(AchPageMath.close(view(tier = 1, value = 375)))
        assertTrue(AchPageMath.close(view(tier = -1, value = 8))) // 8 / 10: nothing reached yet counts too
        assertFalse(AchPageMath.close(view(tier = 4, value = 6_000))) // top tier: no next
        assertFalse(AchPageMath.close(view(tiers = listOf(1), tier = -1, value = 0, oneOff = true))) // a one-off has no "next"
    }

    @Test fun recentlyReachedLooksAtTheLastTierWithinTwoWeeks() {
        val dates = listOf(NOW - 90 * DAY, NOW - 13 * DAY)
        assertTrue(AchPageMath.recent(view(tier = 1, value = 120, dates = dates), NOW, SINCE))
        assertFalse(AchPageMath.recent(view(tier = 1, value = 120, dates = listOf(NOW - 90 * DAY, NOW - 15 * DAY)), NOW, SINCE))
        assertFalse(AchPageMath.recent(view(tier = -1, value = 3), NOW, SINCE))
        // a tier dated at the start of tracking was already there: not recent, even right after the update
        assertFalse(AchPageMath.recent(view(tier = 0, value = 12, dates = listOf(SINCE)), SINCE + DAY, SINCE))
        // a date in the future (clock change) is not "recent" either
        assertFalse(AchPageMath.recent(view(tier = 0, value = 12, dates = listOf(NOW + DAY)), NOW, SINCE))
    }

    @Test fun matchesFollowsTheFilter() {
        val v = view(tier = 1, value = 450, dates = listOf(NOW - 50 * DAY, NOW - 2 * DAY))
        assertTrue(AchPageMath.matches(AchFilter.ALL, v, NOW, SINCE))
        assertTrue(AchPageMath.matches(AchFilter.CLOSE, v, NOW, SINCE))
        assertTrue(AchPageMath.matches(AchFilter.RECENT, v, NOW, SINCE))
        assertFalse(AchPageMath.matches(AchFilter.CLOSE, view(tier = 1, value = 150), NOW, SINCE))
    }

    // ---- groups (R4-V2b) ----

    private fun worldViews(): List<AchView> { val data = TestData.world(); return Achievements.view(TestData.state(data), data) }

    @Test fun groupsComeInPageOrderWithEveryRow() {
        val views = worldViews()
        val g = AchPageMath.groups(views, AchFilter.ALL, NOW, SINCE)
        assertEquals(AchievementTable.GROUPS, g.map { it.name })
        assertEquals(views.size, g.sumOf { it.all.size })
        assertTrue(g.all { it.shown.size == it.all.size && !it.empty })
        assertEquals(11, g.single { it.name == "Badges" }.all.size)
        assertEquals(3, g.single { it.name == "Special" }.all.size)
    }

    @Test fun aFilterHidesTheGroupsItLeavesEmpty() {
        val views = listOf(view(tier = 1, value = 450, id = "catches"), view(tier = -1, value = 0, id = "answers").let { v ->
            AchView(v.id, "Study", v.title, v.desc, v.icon, false, null, -1, null, emptyList(), 0, 10, 0f, v.tiers) })
        val g = AchPageMath.groups(views, AchFilter.CLOSE, NOW, SINCE)
        assertEquals(listOf("Study", "Collection"), g.map { it.name }) // groups with no row are left out altogether
        assertTrue(g.single { it.name == "Study" }.empty)              // nothing close: hidden, its chip greyed
        assertFalse(g.single { it.name == "Collection" }.empty)
    }

    @Test fun groupCountsReadTiersOrBadges() {
        val views = worldViews()
        val g = AchPageMath.groups(views, AchFilter.ALL, NOW, SINCE)
        assertEquals("0 of 35 tiers", AchPageMath.groupCount(g.single { it.name == "Study" }.all))
        assertEquals("0 of 11", AchPageMath.groupCount(g.single { it.name == "Badges" }.all)) // one-offs: "x of 11", never counted as tiers
        assertEquals("0 of 15 tiers", AchPageMath.groupCount(g.single { it.name == "Special" }.all))
        val oneOffsEarned = g.single { it.name == "Badges" }.all.map { AchView(it.id, it.group, it.title, it.desc, it.icon, true, null, 0, null, listOf(1L), 1, null, 1f, it.tiers) }
        assertEquals("11 of 11", AchPageMath.groupCount(oneOffsEarned))
    }

    // ---- header ----

    @Test fun tierCountsCountEveryReachedTierOfTieredRowsOnly() {
        val views = listOf(view(tier = 2, value = 600), view(tier = -1, value = 3), view(tier = 4, value = 9_000),
            view(tiers = listOf(1), tier = 0, value = 1, oneOff = true))
        assertEquals(listOf(2, 2, 2, 1, 1), AchPageMath.tierCounts(views).toList())
        assertEquals(8, AchPageMath.tiersReached(views))
        assertEquals(15, AchPageMath.tiersTotal(views)) // the one-off is not a tier
        assertEquals(1, AchPageMath.oneOffsEarned(views))
    }

    @Test fun theWholeTableTotalsItsTiers() {
        val data = TestData.world()
        val views = Achievements.view(TestData.state(data), data)
        assertEquals(AchievementTable.ROWS.filter { !it.oneOff }.sumOf { it.tiers.size }, AchPageMath.tiersTotal(views)) // 3 for "types" / "highest-level", else 5
        assertEquals(AchievementTable.ROWS.count { !it.oneOff } * 5 - 4, AchPageMath.tiersTotal(views))
    }

    // ---- ladder and texts ----

    @Test fun theLadderMarksReachedCurrentAndLocked() {
        val steps = AchPageMath.ladder(view(tier = 1, value = 312, dates = listOf(1L, 2L)))
        assertEquals(listOf(AchStep.State.REACHED, AchStep.State.REACHED, AchStep.State.CURRENT, AchStep.State.LOCKED, AchStep.State.LOCKED), steps.map { it.state })
        assertEquals(listOf("Common", "Rare", "Epic", "Legendary", "Mythical"), steps.map { it.name })
        assertEquals(listOf(10L, 100, 500, 2_000, 5_000), steps.map { it.threshold })
        assertEquals(listOf<Long?>(1L, 2L, null, null, null), steps.map { it.date })
        assertEquals("next after Epic", steps[3].lockedText)
        assertEquals("locked until Legendary", steps[4].lockedText)
        assertNull(steps[2].lockedText)
        // nothing reached: the first tier is the current one
        assertEquals(AchStep.State.CURRENT, AchPageMath.ladder(view(tier = -1, value = 3)).first().state)
        assertEquals("next after Common", AchPageMath.ladder(view(tier = -1, value = 3))[1].lockedText)
        // top tier: all reached
        assertTrue(AchPageMath.ladder(view(tier = 4, value = 9_000)).all { it.state == AchStep.State.REACHED })
    }

    @Test fun toGoAndNumbers() {
        assertEquals("188 to go", AchPageMath.toGo(view(tier = 1, value = 312)))
        assertEquals("", AchPageMath.toGo(view(tier = 4, value = 9_000)))
        assertEquals("31,240", AchPageMath.fmt(Metric.ANSWERS, 31_240))
        assertEquals("41 h", AchPageMath.fmt(Metric.STUDY_MINUTES, 2_500))
        assertEquals("30 min", AchPageMath.fmt(Metric.STUDY_MINUTES, 30))
    }

    @Test fun datesReadBeforeWhenTheyAreTheStartOfTracking() {
        val utc = ZoneId.of("UTC")
        assertEquals("Reached 13 Oct 2026", AchPageMath.reachedText(NOW, NOW - DAY, utc))
        assertEquals("Reached before 13 Oct 2026", AchPageMath.reachedText(NOW, NOW, utc))
        assertEquals("Reached", AchPageMath.reachedText(null, NOW, utc))
        assertEquals("Reached", AchPageMath.reachedText(0L, NOW, utc))
    }

    // ---- source flag ----

    @Test fun everyRowHasASourceAndTheKindsAreRight() {
        fun src(id: String) = AchPageMath.source(AchievementTable.row(id)!!)
        assertEquals(AchSource.SAVE, src("catches"))
        assertEquals(AchSource.SAVE, src("cards-day"))
        assertEquals(AchSource.HISTORY, src("money-earned"))
        assertEquals(AchSource.HISTORY, src("balls-thrown")) // "good" left the list (R4-V2b)
        assertEquals(AchSource.SINCE_UPDATE, src("damage-dealt"))
        assertEquals(AchSource.SINCE_UPDATE, src("tms"))
        assertEquals(AchSource.EXTERNAL, src("study-time"))
        assertEquals(AchSource.EXTERNAL, src("new-cards"))
        assertEquals(AchSource.BADGE, src("first-catch"))
        // the live-counted metrics that Achievements.backfill fills are exactly the HISTORY ones
        val backfilled = Metric.entries.filter { it.counted && !it.external }.filter { m ->
            val s = GameState(createdAt = 1L, day = "2026-10-13")
            val day = DayLog(10, 1, 1, 0); day.cash = 5; day.spent = 5; day.xp = 5; day.healItems = 5; day.buttons[3] = 5; day.buttons[4] = 5; day.shinies = 5
            s.history["2026-10-12"] = day
            s.ballCatches["poke-ball"] = 3
            Achievements.backfill(s)
            (s.counters[m.name] ?: 0L) > 0
        }
        for (m in backfilled) assertTrue("$m", AchievementTable.ROWS.filter { it.metric == m }.all { AchPageMath.source(it) == AchSource.HISTORY })
        for (row in AchievementTable.ROWS) assertTrue(row.id, AchPageMath.sourceNote(row, "13 Oct 2026").isNotBlank())
    }

    // ---- badges and celebrations ----

    @Test fun badgesMapToTheirRowAndTier() {
        for (row in AchievementTable.ROWS.filter { it.ankimonBadge != null }) assertEquals(row.id to 0, AchPageMath.forBadge(row.ankimonBadge!!)?.let { it.first.id to it.second })
        val day = AchievementTable.row("cards-day")!!
        day.tierBadges.forEachIndexed { t, b -> assertEquals(day.id to t, AchPageMath.forBadge(b)?.let { it.first.id to it.second }) }
        assertNull(AchPageMath.forBadge(3)) // 300 cards in a day: a badge with no row
        assertNull(AchPageMath.forBadge(999))
        // every badge Ankimon awards, except 3, has a row
        for (id in Badges.TITLES.keys - 3) assertNotNull("badge $id", AchPageMath.forBadge(id))
    }

    @Test fun celebrationsCoverTiersOneOffsAndBadgeTiersWithoutDoubles() {
        val events = listOf(
            GameEvent.AchievementReached("catches", 1, "Pokémon caught", "Rare"),
            GameEvent.AchievementReached("catches", 2, "Pokémon caught", "Epic"),
            GameEvent.BadgeEarned(7, "First Pokémon caught"),
            GameEvent.BadgeEarned(2, "200 cards in one day"), // the 2nd tier of "cards-day"
            GameEvent.BadgeEarned(3, "300 cards in one day"), // no row
            GameEvent.AchievementReached("catches", 2, "Pokémon caught", "Epic"), // a double
            GameEvent.Info("x"),
        )
        val c = AchPageMath.celebrations(events)
        assertEquals(listOf("catches" to 1, "catches" to 2, "first-catch" to -1, "cards-day" to 1), c.map { it.rowId to it.tier })
        assertEquals(listOf<Long?>(100L, 500L, null, 200L), c.map { it.threshold })
        assertEquals(listOf("Rare!", "Epic!", "Achievement!", "Rare!"), c.map { it.word })
        assertEquals(listOf(3), AchPageMath.unmappedBadges(events).map { it.id })
        assertTrue(AchPageMath.celebrations(emptyList()).isEmpty())
    }

    // ---- icons ----

    @Test fun everyIconKeyOfTheTableHasADrawable() {
        for (row in AchievementTable.ROWS) assertTrue("${row.id}: icon '${row.icon}'", row.icon in AchStyle.ICONS)
        assertTrue(AchStyle.ICONS.values.all { it != 0 })
        // battle rows use the app's own swords
        assertEquals(com.husarp.ankigo.R.drawable.ic_swords, AchStyle.icon("crossed-swords"))
        assertEquals(com.husarp.ankigo.R.drawable.ic_ball, AchStyle.icon("ball"))
        assertEquals(com.husarp.ankigo.R.drawable.ic_flame, AchStyle.icon("flame"))
    }
}
