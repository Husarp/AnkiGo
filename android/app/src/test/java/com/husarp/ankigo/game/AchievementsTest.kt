package com.husarp.ankigo.game

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import java.util.TimeZone
import kotlin.random.Random

/** R4-03: the achievement table, the metric values, counting from events, tiers + dates, the view. Made-up species only. */
class AchievementsTest {
    private val data = TestData.world()
    private val NOW = 1_791_892_800_000L // 2026-10-13 12:00 UTC: the game day of TestData.state
    private lateinit var savedZone: TimeZone

    @Before fun utcZone() {
        savedZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After fun restoreZone() = TimeZone.setDefault(savedZone)

    private fun state() = TestData.state(data, 25, 20, listOf("tackle"))

    /** A state whose rows have all been looked at once (silently), as after the first answer; value changes after this are loud. */
    private fun looked(s: GameState = state()): GameState {
        Achievements.check(s, data, NOW - 1000, mutableListOf(), silent = true)
        return s
    }

    private fun check(s: GameState, now: Long = NOW): List<GameEvent> =
        mutableListOf<GameEvent>().also { Achievements.check(s, data, now, it) }

    private fun reached(events: List<GameEvent>, id: String) =
        events.filterIsInstance<GameEvent.AchievementReached>().filter { it.id == id }

    private fun counter(s: GameState, m: Metric) = s.counters[m.name] ?: 0L

    // ---- the table ----

    @Test fun idsAreUniqueAndSimple() {
        val ids = AchievementTable.ROWS.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.all { id -> id.isNotEmpty() && id.all { it.isLowerCase() || it.isDigit() || it == '-' } })
    }

    @Test fun tieredRowsHaveFiveAscendingThresholdsAndOneOffsOne() {
        for (r in AchievementTable.ROWS) {
            if (r.ankimonBadge == null) {
                assertEquals(r.id, 5, r.tiers.size)
                assertTrue(r.id, r.metric != null)
                assertTrue(r.id, r.tiers.zipWithNext().all { (a, b) -> a < b })
                assertTrue(r.id, r.tiers.first() >= 1)
            } else {
                assertEquals(r.id, listOf(1L), r.tiers)
                assertNull(r.id, r.metric)
                assertTrue(r.oneOff)
            }
            assertTrue(r.id, r.title.isNotBlank() && r.desc.isNotBlank() && r.icon.isNotBlank())
        }
        assertEquals(5, AchievementTable.TIER_NAMES.size)
        assertEquals("Common", AchievementTable.tierName(0))
        assertEquals("Mythical", AchievementTable.tierName(4))
    }

    // R4-V2b: Adam's list edits
    private val DROPPED = listOf("combo", "good", "easy")
    private val BY_GROUP = mapOf(
        "Study" to listOf("answers", "streak-days", "cards-day", "days-studied", "study-time", "new-cards", "goals"),
        "Battle" to listOf("defeats", "damage-dealt", "damage-taken", "damage-blocked", "misses", "super-effective", "crits", "total-xp", "total-levels", "highest-level", "evolutions"),
        "Collection" to listOf("catches", "dex-caught", "dex-seen", "types", "balls-thrown", "ball-catches"),
        "Economy" to listOf("money-earned", "money-spent", "items-used", "tms", "items-found"),
        "Special" to listOf("shinies", "legendary-mythical", "comebacks"),
    )

    @Test fun theDroppedRowsAreGoneAndTheirIdsStayFree() {
        for (id in DROPPED) assertNull(id, AchievementTable.row(id))
        assertEquals(32, AchievementTable.ROWS.count { !it.oneOff })
        assertEquals(11, AchievementTable.ROWS.count { it.oneOff })
        // their counters stay in the engine
        assertTrue(Metric.entries.containsAll(listOf(Metric.BEST_COMBO, Metric.GOOD_PRESSES, Metric.EASY_PRESSES)))
        assertTrue(AchievementTable.ROWS.none { it.metric in listOf(Metric.BEST_COMBO, Metric.GOOD_PRESSES, Metric.EASY_PRESSES) })
        // Adam kept these
        assertEquals(listOf(15_000L), AchievementTable.row("defeats")!!.tiers.takeLast(1))
        for (id in listOf("damage-dealt", "damage-taken", "damage-blocked", "misses")) assertNotNull(id, AchievementTable.row(id))
    }

    @Test fun everyRowIsInItsGroupAndTheSixGroupsComeInOrder() {
        assertEquals(listOf("Study", "Battle", "Collection", "Economy", "Special", "Badges"), AchievementTable.GROUPS)
        for ((g, ids) in BY_GROUP) assertEquals(g, ids, AchievementTable.ROWS.filter { it.group == g }.map { it.id })
        assertTrue(AchievementTable.ROWS.filter { it.group == "Badges" }.all { it.ankimonBadge != null })
        assertEquals(11, AchievementTable.ROWS.count { it.group == "Badges" })
        // ROWS follow the group order, so the page lists them as they are
        assertEquals(AchievementTable.GROUPS, AchievementTable.ROWS.map { it.group }.distinct())
        assertEquals(AchievementTable.ROWS.map { it.group }, AchievementTable.ROWS.map { it.group }.sortedBy { AchievementTable.GROUPS.indexOf(it) })
        // Total XP belongs with battle and progress, not Economy
        assertEquals("Battle", AchievementTable.row("total-xp")!!.group)
    }

    @Test fun everyAnkimonBadgeIsKeptOnceWithItsMeaning() {
        // The ids Ankimon has and AnkiGo awards: unchanged by the redesign.
        assertEquals(setOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 12, 13, 16, 17, 18, 19, 20), Badges.TITLES.keys)
        val oneOffs = AchievementTable.ROWS.filter { it.ankimonBadge != null }
        for (r in oneOffs) assertEquals(Badges.TITLES[r.ankimonBadge], r.title)
        val tierBadges = AchievementTable.ROWS.flatMap { it.tierBadges }
        // each badge sits in at most one place; the 300-cards badge (3) is still given but has no row
        val placed = oneOffs.map { it.ankimonBadge!! } + tierBadges
        assertEquals(placed.size, placed.toSet().size)
        assertEquals(Badges.TITLES.keys - 3, placed.toSet())
    }

    @Test fun theCardsInOneDayTiersAreTheReviewBadges() {
        val row = AchievementTable.row("cards-day")!!
        assertEquals(5, row.tierBadges.size)
        row.tiers.forEachIndexed { i, reviews -> assertEquals(Tuning.REVIEW_BADGES[reviews.toInt()], row.tierBadges[i]) }
        assertEquals(Metric.BEST_DAY, row.metric)
    }

    @Test fun countedMetricsCanBeBumpedAndDerivedOnesCannot() {
        val s = state()
        for (m in Metric.entries) {
            if (m.counted) Achievements.bump(s, m) else assertThrows(IllegalArgumentException::class.java) { Achievements.bump(s, m) }
        }
        assertEquals(Metric.entries.count { it.counted }, s.counters.size)
        assertTrue(s.counters.values.all { it == 1L })
        Achievements.bump(s, Metric.CRITS, 0); Achievements.bump(s, Metric.CRITS, -3) // nothing to add
        assertEquals(1L, counter(s, Metric.CRITS))
    }

    // ---- values ----

    @Test fun derivedValuesReadTheSave() {
        val s = state() // main: Sparkmouse Lv 20
        s.mons += TestData.mon(data, 150, 50, uid = "legend").also { it.xp = 77 }
        s.mons += TestData.mon(data, 1, 7, uid = "leaf").also { it.shiny = true }
        s.caught += setOf(1, 150, 151, 19)
        s.seen += setOf(1, 150, 151, 19, 16, 54)
        s.catches = 3; s.defeats = 40; s.evolutions = 2; s.totalReviews = 12_345
        s.bestCardStreak = 31; s.bestStreakDays = 4; s.streakDays = 6
        s.ballCatches["auto"] = 5; s.ballCatches["great-ball"] = 2; s.ballCatches["poke-ball"] = 1

        fun v(m: Metric) = Achievements.value(m, s, data)
        assertEquals(3L, v(Metric.CATCHES)) // catches 3 = owned 3
        assertEquals(12_345L, v(Metric.ANSWERS))
        assertEquals(40L, v(Metric.DEFEATS))
        assertEquals(2L, v(Metric.EVOLUTIONS))
        assertEquals(31L, v(Metric.BEST_COMBO))
        assertEquals(6L, v(Metric.BEST_STREAK_DAYS)) // the running streak counts when it is above the stored best
        assertEquals(7L, v(Metric.DEX_SEEN)) // the main's 25 + six more
        assertEquals(5L, v(Metric.DEX_CAUGHT)) // 25 + 1 + 150 + 151 + 19
        assertEquals(50L, v(Metric.HIGHEST_LEVEL))
        assertEquals(20L + 50 + 7, v(Metric.TOTAL_LEVELS))
        assertEquals(2L, v(Metric.LEGENDARY_MYTHICAL)) // 150 (legendary) and 151 (mythical)
        assertEquals(3L, v(Metric.BALL_CATCHES)) // not the auto rule's
        // Electric (25) + Grass/Poison (1) + Psychic (150, 151) + Normal (19) = 5 types
        assertEquals(5L, v(Metric.TYPES_CAUGHT))
        // medium growth = n^3: 20^3 + 50^3 (+ 77 progress) + 7^3; the leaf (growth rate 2) too
        assertEquals(8_000L + 343 + 77 + data.growth.getValue(data.sp(150).growthRate)[50], v(Metric.TOTAL_XP))
    }

    @Test fun catchesNeverFallBelowThePokemonYouOwn() {
        val s = state()
        s.catches = 1
        repeat(4) { s.mons += TestData.mon(data, 19, 5, uid = "r$it") }
        assertEquals(5L, Achievements.value(Metric.CATCHES, s, data)) // an Ankimon import brings Pokémon that were never "catches"
    }

    @Test fun shiniesNeverFallBelowTheOnesYouOwn() {
        val s = state()
        s.counters[Metric.SHINIES.name] = 1
        repeat(3) { s.mons += TestData.mon(data, 19, 5, uid = "r$it").also { m -> m.shiny = true } }
        assertEquals(3L, Achievements.value(Metric.SHINIES, s, data))
        s.counters[Metric.SHINIES.name] = 9
        assertEquals(9L, Achievements.value(Metric.SHINIES, s, data))
    }

    @Test fun anEmptyStateIsAllZero() {
        val s = GameState()
        for (m in Metric.entries) assertEquals(m.name, 0L, Achievements.value(m, s, data))
    }

    // ---- counting from events ----

    private fun atk(
        byPlayer: Boolean, damage: Int = 10, eff: Double = 1.0, crit: Boolean = false, missed: Boolean = false,
        failed: Boolean = false, cat: Category = Category.PHYSICAL, mainHp: Int = 50, mainMax: Int = 50,
    ) = GameEvent.Attack(
        byPlayer, if (byPlayer) "Main" else "Wild", if (byPlayer) "Wild" else "Main", "Tackle", Type.NORMAL, cat, damage, 1, eff, crit,
        missed, failed,
        targetHp = if (byPlayer) 30 else mainHp, targetMaxHp = if (byPlayer) 40 else mainMax,
        userHp = if (byPlayer) mainHp else 30, userMaxHp = if (byPlayer) mainMax else 40,
    )

    @Test fun eachAttackCountsOnceForEachThingItDid() {
        val s = state()
        Achievements.count(s, listOf(
            atk(true, damage = 30, eff = 2.0, crit = true),          // dealt 30, super effective, critical
            atk(true, damage = 12),                                  // dealt 12
            atk(true, damage = 0, missed = true),                    // a miss (yours)
            atk(true, damage = 0, cat = Category.STATUS),            // a status move: nothing
            atk(true, damage = 5, failed = true),                    // failed: nothing
            atk(false, damage = 8, eff = 0.5),                       // taken 8, resisted: a neutral hit would have done 16, so 8 blocked
            atk(false, damage = 0, eff = 0.0),                       // no effect = resisted, nothing taken
            atk(false, damage = 0, missed = true),                   // the wild one missed: also a miss
            atk(false, damage = 0, cat = Category.STATUS, eff = 0.5), // a status move is never "resisted"
            atk(false, damage = 20, eff = 2.0, crit = true),         // taken 20; a crit AGAINST you is not your crit
        ))
        assertEquals(42L, counter(s, Metric.DAMAGE_DEALT))
        assertEquals(28L, counter(s, Metric.DAMAGE_TAKEN))
        assertEquals(1L, counter(s, Metric.SUPER_EFFECTIVE))
        assertEquals(1L, counter(s, Metric.CRITS))
        assertEquals(2L, counter(s, Metric.MISSES)) // either side's
        assertEquals(2L, counter(s, Metric.RESISTED))
        assertEquals(8L, counter(s, Metric.DAMAGE_BLOCKED)) // the immune hit adds nothing: its full damage is unknown
    }

    @Test fun answersCountPressesDaysMoneyAndGoal() {
        val s = state()
        Achievements.count(s, listOf(
            GameEvent.Counted(3, 10, 1, 1, false), GameEvent.Cash(5, 5, CashKind.ANSWER),
            GameEvent.Counted(4, 20, 2, 2, false), GameEvent.Cash(5, 10, CashKind.ANSWER),
            GameEvent.Counted(1, 0, 3, 0, false), GameEvent.Counted(2, 5, 4, 1, true), GameEvent.Counted(0, 10, 5, 2, true),
            GameEvent.Cash(75, 85, CashKind.DAILY_BONUS), GameEvent.Cash(10, 95, CashKind.DEFEAT),
        ))
        assertEquals(1L, counter(s, Metric.GOOD_PRESSES)) // button 0 (unknown) is not a Good press
        assertEquals(1L, counter(s, Metric.EASY_PRESSES))
        assertEquals(1L, counter(s, Metric.DAYS_STUDIED)) // only the first answer of the day
        assertEquals(95L, counter(s, Metric.MONEY_EARNED))
        assertEquals(1L, counter(s, Metric.GOALS_REACHED))
    }

    @Test fun blockedDamageIsWhatAResistedHitDidNotDo() {
        val s = state()
        Achievements.count(s, listOf(atk(false, damage = 10, eff = 0.25), atk(false, damage = 7, eff = 0.5), atk(false, damage = 9, eff = 1.0)))
        assertEquals(30L + 7, counter(s, Metric.DAMAGE_BLOCKED)) // 40 - 10 and 14 - 7
    }

    @Test fun xpAndFoundItemsCount() {
        val s = state()
        Achievements.count(s, listOf(
            GameEvent.XpGained("main", "Sparkmouse", 120, 21, 5, 300), GameEvent.XpGained("other", "Ratty", 60, 5, 1, 90),
            GameEvent.ItemFound("potion", 1), GameEvent.ItemFound("potion", 2),
        ))
        assertEquals(180L, counter(s, Metric.TOTAL_XP))
        assertEquals(2L, counter(s, Metric.ITEMS_FOUND))
    }

    @Test fun totalXpIsNeverBelowTheXpOfThePokemonYouOwn() {
        val s = state() // Sparkmouse Lv 20: 20^3 = 8 000
        assertEquals(8_000L, Achievements.value(Metric.TOTAL_XP, s, data))
        s.counters[Metric.TOTAL_XP.name] = 9_500 // a released Pokémon's XP was counted too
        assertEquals(9_500L, Achievements.value(Metric.TOTAL_XP, s, data))
    }

    @Test fun bestDayTakesTheHistoryTodayAndTheBadgesAlreadyEarned() {
        val s = state()
        assertEquals(0L, Achievements.value(Metric.BEST_DAY, s, data))
        s.history["2026-10-10"] = DayLog(240); s.history["2026-10-11"] = DayLog(180)
        assertEquals(240L, Achievements.value(Metric.BEST_DAY, s, data))
        s.reviewsToday = 260
        assertEquals(260L, Achievements.value(Metric.BEST_DAY, s, data))
        s.badges += 4 // 500 cards in a day happened once, long ago (the history keeps 400 days)
        assertEquals(500L, Achievements.value(Metric.BEST_DAY, s, data))
    }

    @Test fun ballsAndShiniesCount() {
        val s = state()
        Achievements.count(s, listOf(
            GameEvent.BallThrow("poke-ball", 40, false, 3), GameEvent.BallThrow("poke-ball", 40, true, 2),
            GameEvent.Caught("u1", 19, "Ratty", 5, true, "poke-ball", true, true),
            GameEvent.Caught("u2", 19, "Ratty", 5, false, null, false, false),
        ))
        assertEquals(2L, counter(s, Metric.BALLS_THROWN))
        assertEquals(1L, counter(s, Metric.SHINIES))
    }

    @Test fun anIgnoredAnswerAddsNothing() {
        val s = state()
        Achievements.count(s, listOf(GameEvent.DayChanged("2026-10-14"), GameEvent.Ignored("Answer repeated after an undo")))
        assertTrue(s.counters.isEmpty())
    }

    @Test fun aCatchAtFivePercentIsAComeback() {
        val s = state()
        Achievements.count(s, listOf(
            atk(false, damage = 40, mainHp = 5, mainMax = 100), // the wild one's hit leaves the main at 5 %
            atk(true, damage = 30, mainHp = 5, mainMax = 100),
            GameEvent.Caught("u1", 19, "Ratty", 5, false, null, true, true),
            GameEvent.Healed("main", "Sparkmouse", 10, 15, 100, HealReason.CATCH),
        ))
        assertEquals(1L, counter(s, Metric.COMEBACKS))
    }

    @Test fun aDefeatUnderTenPercentIsAComebackButTenPercentIsNot() {
        fun comebacks(hp: Int, max: Int): Long {
            val s = state()
            Achievements.count(s, listOf(atk(true, damage = 30, mainHp = hp, mainMax = max), GameEvent.Defeated(19, "Ratty", 20)))
            return counter(s, Metric.COMEBACKS)
        }
        assertEquals(1L, comebacks(9, 100))
        assertEquals(0L, comebacks(10, 100)) // exactly 10 % is not under
        assertEquals(0L, comebacks(50, 100))
        assertEquals(0L, comebacks(0, 100))  // fainted: that is no comeback
        assertEquals(1L, comebacks(1, 47))
    }

    @Test fun aBallCatchTakesTheHpFromTheHealAfterIt() {
        val low = state()
        Achievements.count(low, listOf(
            GameEvent.BallThrow("poke-ball", 40, true, 1), GameEvent.Caught("u1", 19, "Ratty", 5, false, "poke-ball", true, true),
            GameEvent.Healed("main", "Sparkmouse", 5, 9, 100, HealReason.CATCH), // had 4 of 100 before the heal
        ))
        assertEquals(1L, counter(low, Metric.COMEBACKS))
        val healthy = state()
        Achievements.count(healthy, listOf(
            GameEvent.BallThrow("poke-ball", 40, true, 1), GameEvent.Caught("u1", 19, "Ratty", 5, false, "poke-ball", true, true),
            GameEvent.Healed("main", "Sparkmouse", 5, 60, 100, HealReason.CATCH),
        ))
        assertEquals(0L, counter(healthy, Metric.COMEBACKS))
        val unknown = state() // no fight and no heal in the batch: the HP is not known, nothing counts
        Achievements.count(unknown, listOf(GameEvent.Caught("u1", 19, "Ratty", 5, false, "poke-ball", true, true)))
        assertEquals(0L, counter(unknown, Metric.COMEBACKS))
    }

    @Test fun aMainSwitchForgetsTheHpOfTheOldMain() {
        val s = state()
        Achievements.count(s, listOf(
            atk(false, damage = 40, mainHp = 3, mainMax = 100), GameEvent.MainChanged("t1", "Other"),
            GameEvent.Defeated(19, "Ratty", 20),
        ))
        assertEquals(0L, counter(s, Metric.COMEBACKS))
    }

    // ---- counting through the game ----

    private var time = NOW
    private fun game(s: GameState = state(), seed: Int = 1) = Game(data, s, Random(seed)) { time }
    private fun Game.answer(button: Int, card: Long) = onAnswer(Answer(card, button, time))

    @Test fun answersThroughTheGameCountOnceAndTheRedoOfAnUndoNotAtAll() {
        val g = game()
        g.answer(3, 1); g.answer(4, 2)
        assertEquals(2L, g.state.totalReviews)
        assertEquals(1L, counter(g.state, Metric.GOOD_PRESSES)); assertEquals(1L, counter(g.state, Metric.EASY_PRESSES))
        assertEquals(1L, counter(g.state, Metric.DAYS_STUDIED))
        val money = counter(g.state, Metric.MONEY_EARNED)
        assertTrue(money > 0)

        g.onUndo(2, 1)                    // AnkiDroid undid card 2; the redo is the same answer again
        val redo = g.answer(4, 2)
        assertTrue(redo.any { it is GameEvent.Ignored })
        assertEquals(2L, g.state.totalReviews)
        assertEquals(1L, counter(g.state, Metric.EASY_PRESSES))
        assertEquals(money, counter(g.state, Metric.MONEY_EARNED))
        assertEquals(1L, counter(g.state, Metric.DAYS_STUDIED))
    }

    @Test fun attacksOfARoundCountThroughTheGame() {
        val g = game()
        g.state.battle = Battle(TestData.mon(data, 19, 15, listOf("tackle"), uid = "wild"), Tier.NORMAL, startedAt = time)
        g.state.settings.cardsPerRound = 1
        val events = g.answer(3, 1)
        val attacks = events.filterIsInstance<GameEvent.Attack>()
        assertTrue(attacks.isNotEmpty())
        assertEquals(attacks.filter { it.byPlayer && !it.missed && !it.failed }.sumOf { it.damage }.toLong(), counter(g.state, Metric.DAMAGE_DEALT))
        assertEquals(attacks.filter { !it.byPlayer && !it.missed && !it.failed }.sumOf { it.damage }.toLong(), counter(g.state, Metric.DAMAGE_TAKEN))
    }

    @Test fun theFirstAnswerOnlyLooksAndTheNextTierComesWithAnEvent() {
        val g = game()
        g.state.totalReviews = 498
        val first = g.answer(3, 1) // 499 answers; every row is looked at quietly
        assertTrue(first.none { it is GameEvent.AchievementReached })
        assertTrue(AchievementTable.ROWS.all { it.id in g.state.achTiers })
        assertEquals(time, g.state.achSince)
        val second = g.answer(3, 2) // 500
        val ev = reached(second, "answers").single()
        assertEquals(GameEvent.AchievementReached("answers", 0, "Answers given", "Common"), ev)
        assertEquals(listOf(time), g.state.achTiers["answers"])
        assertTrue(g.answer(3, 3).none { it is GameEvent.AchievementReached && it.id == "answers" })
    }

    @Test fun pickingTheStarterLooksAtEveryRowQuietly() {
        val s = GameState(createdAt = 1L)
        val g = game(s)
        val events = g.pickStarter(25)
        assertTrue(events.none { it is GameEvent.AchievementReached })
        assertEquals(time, s.achSince)
        assertEquals(AchievementTable.ROWS.map { it.id }.toSet(), s.achTiers.keys)
        assertTrue(s.achTiers.values.all { it.isEmpty() })
    }

    // ---- tiers and dates ----

    @Test fun crossingTwoTiersAtOnceGivesTwoDatesAndTwoEventsInOrder() {
        val s = looked()
        s.totalReviews = 6_000 // 500 and 5 000
        val events = check(s)
        assertEquals(
            listOf(
                GameEvent.AchievementReached("answers", 0, "Answers given", "Common"),
                GameEvent.AchievementReached("answers", 1, "Answers given", "Rare"),
            ),
            reached(events, "answers"),
        )
        assertEquals(listOf(NOW, NOW), s.achTiers["answers"])
        assertTrue(check(s, NOW + 5).none { it is GameEvent.AchievementReached }) // nothing again
        s.totalReviews = 26_000
        val later = reached(check(s, NOW + 9), "answers")
        assertEquals(listOf(2), later.map { it.tier })
        assertEquals(listOf(NOW, NOW, NOW + 9), s.achTiers["answers"])
    }

    @Test fun aSilentCheckDatesTheTiersWithoutEvents() {
        val s = looked()
        s.totalReviews = 6_000
        val events = mutableListOf<GameEvent>()
        Achievements.check(s, data, NOW, events, silent = true)
        assertTrue(events.isEmpty())
        assertEquals(listOf(NOW, NOW), s.achTiers["answers"])
        assertTrue(check(s, NOW + 1).isEmpty())
    }

    @Test fun aRowNeverLookedAtIsQuietEvenWithoutTheSilentFlag() {
        val s = state() // an old save: no achTiers at all
        s.totalReviews = 30_000
        s.bestStreakDays = 10
        val events = check(s)
        assertTrue(events.none { it is GameEvent.AchievementReached })
        assertEquals(3, s.achTiers["answers"]!!.size) // 500, 5 000, 25 000
        assertEquals(2, s.achTiers["streak-days"]!!.size) // 3, 7
        assertEquals(NOW, s.achSince)
    }

    @Test fun aRowAddedToTheListLaterIsLookedAtQuietly() {
        val s = looked()
        s.achTiers.remove("answers") // as if this row was new in this version
        s.totalReviews = 5_000
        assertTrue(check(s).none { it is GameEvent.AchievementReached })
        assertEquals(2, s.achTiers["answers"]!!.size)
        s.totalReviews = 25_000
        assertEquals(listOf(2), reached(check(s, NOW + 1), "answers").map { it.tier })
    }

    @Test fun reachedTiersNeverGoDownWhenAValueDrops() {
        val s = looked()
        repeat(10) { s.mons += TestData.mon(data, 19, 100, uid = "big$it") } // total levels 20 + 1 000 -> Common (100) and Rare (1 000)
        val first = check(s)
        assertEquals(listOf(0, 1), reached(first, "total-levels").map { it.tier })
        s.mons.removeAll { it.uid.startsWith("big") }                      // released
        assertEquals(20L, Achievements.value(Metric.TOTAL_LEVELS, s, data))
        assertTrue(check(s, NOW + 1).none { it is GameEvent.AchievementReached })
        assertEquals(listOf(NOW, NOW), s.achTiers["total-levels"])
        val view = Achievements.view(s, data).single { it.id == "total-levels" }
        assertEquals(1, view.tier)
        assertEquals("Rare", view.tierName)
        assertEquals(20L, view.value)
        assertEquals(5_000L, view.next)
    }

    @Test fun aBadgeOneOffIsReachedWhenTheBadgeIsInTheSetAndSendsNoSecondEvent() {
        val s = looked()
        val events = mutableListOf<GameEvent>()
        Badges.award(s, 7, events, now = 555L)
        assertEquals(listOf<GameEvent>(GameEvent.BadgeEarned(7, "First Pokémon caught")), events)
        assertEquals(555L, s.badgeAt[7])
        Badges.award(s, 7, events, now = 999L)
        assertEquals(555L, s.badgeAt[7]) // the first date stays
        assertEquals(1, events.size)
        val checked = check(s)
        assertTrue(checked.none { it is GameEvent.AchievementReached }) // BadgeEarned already said it
        assertEquals(listOf(555L), s.achTiers["first-catch"])
        val v = Achievements.view(s, data).single { it.id == "first-catch" }
        assertEquals(0, v.tier); assertEquals(listOf(555L), v.dates); assertNull(v.next); assertEquals(1f, v.progress, 0f)
        val other = Achievements.view(s, data).single { it.id == "first-ultra" }
        assertEquals(-1, other.tier); assertEquals(1L, other.next); assertEquals(0f, other.progress, 0f)
    }

    @Test fun anOldBadgeHasNoDateOfItsOwnButTheOneOffGetsTheCheckTime() {
        val s = state()
        s.badges += setOf(1, 5)
        check(s, NOW + 7)
        assertTrue(s.badgeAt.isEmpty()) // old badges: no date in badgeAt
        assertEquals(listOf(NOW + 7), s.achTiers["cards-day"]) // badge 1 (100 cards in a day) is that row's first tier
        assertEquals(listOf(NOW + 7), s.achTiers["first-level-up"])
        assertEquals(emptyList<Long>(), s.achTiers["first-item"])
    }

    @Test fun theBadgesTheGameAwardsStoreTheirDate() {
        val g = game()
        g.state.reviewsToday = 99
        g.answer(3, 1) // 100 cards today: badge 1
        assertTrue(1 in g.state.badges)
        assertEquals(time, g.state.badgeAt[1])
        assertEquals(listOf(time), g.state.achTiers["cards-day"])
    }

    @Test fun aBadgeTierOfARowSendsTheBadgeEventButNoSecondOne() {
        val g = game()
        g.answer(3, 1) // every row is looked at
        g.state.reviewsToday = 199
        val events = g.answer(3, 2) // 200 cards today: badge 2 = tier 1 of "Cards in one day"
        assertTrue(events.any { it == GameEvent.BadgeEarned(2, "200 cards in one day") })
        assertTrue(events.none { it is GameEvent.AchievementReached && it.id == "cards-day" })
        assertEquals(2, g.state.achTiers["cards-day"]!!.size) // badges 1 and 2 came together: two tiers, both from badges
        assertEquals(time, g.state.achTiers["cards-day"]!![1])
    }

    // ---- the view ----

    @Test fun theViewShowsProgressToTheNextTier() {
        val s = looked()
        s.totalReviews = 2_500
        check(s)
        val v = Achievements.view(s, data).single { it.id == "answers" }
        assertEquals(2_500L, v.value)
        assertEquals(0, v.tier); assertEquals("Common", v.tierName)
        assertEquals(5_000L, v.next)
        assertEquals(0.5f, v.progress, 0.0001f)
        assertEquals("Study", v.group)
        assertEquals(listOf(NOW), v.dates)
        assertFalse(v.oneOff)
        val none = Achievements.view(s, data).single { it.id == "catches" }
        assertEquals(-1, none.tier); assertNull(none.tierName); assertEquals(10L, none.next); assertEquals(emptyList<Long>(), none.dates)
    }

    @Test fun theTopTierHasNoNextAndFullProgress() {
        val s = looked()
        s.totalReviews = 500_000
        check(s)
        val v = Achievements.view(s, data).single { it.id == "answers" }
        assertEquals(4, v.tier); assertEquals("Mythical", v.tierName); assertNull(v.next); assertEquals(1f, v.progress, 0f)
        assertEquals(5, v.dates.size)
    }

    @Test fun unknownIdsAreKeptButNotShown() {
        val s = looked()
        s.achTiers["future-row"] = mutableListOf(1L, 2L)
        check(s)
        assertEquals(listOf(1L, 2L), s.achTiers["future-row"])
        assertTrue(Achievements.view(s, data).none { it.id == "future-row" })
        assertEquals(AchievementTable.ROWS.map { it.id }, Achievements.view(s, data).map { it.id })
    }

    @Test fun theDebugLogLineHasOnlyFacts() {
        assertEquals("AchievementReached id=answers tier=1", com.husarp.ankigo.debug.GameEventText.event(GameEvent.AchievementReached("answers", 1, "Answers given", "Rare")))
    }

    // ---- shop, items, TMs ----

    @Test fun shopMoneyCountsAndACrossedTierComesBackInTheResult() {
        val s = looked()
        s.cash = 5_000
        s.shopItems += "potion"
        val results = (1..5).map { Shop.buy(data, s, "potion", NOW) } // 5 x 200 = 1 000: Common
        assertEquals(1_000L, counter(s, Metric.MONEY_SPENT))
        assertTrue(results.take(4).all { r -> r.events.isEmpty() })
        assertEquals(listOf(GameEvent.AchievementReached("money-spent", 0, "Money spent", "Common")), results[4].events)
    }

    @Test fun healingAnItemCountsAsUsedAndATmAsTaught() {
        val s = looked()
        val main = s.main!!
        main.hp = 5
        Economy.give(s, "potion")
        val r = ItemUse.use(data, s, "potion", null, Random(1), NOW)
        assertTrue(r.ok)
        assertEquals(1L, counter(s, Metric.ITEMS_USED))
        s.tms += "bodyslam"
        val t = ItemUse.teachTm(data, s, "bodyslam", main.uid, 0, NOW)
        assertTrue(t.ok)
        assertEquals(1L, counter(s, Metric.TMS_TAUGHT))
        assertTrue(t.events.any { it == GameEvent.AchievementReached("tms", 0, "TMs taught", "Common") })
        // a failed use counts nothing
        val before = counter(s, Metric.ITEMS_USED)
        assertFalse(ItemUse.use(data, s, "potion", null, Random(1), NOW).ok)
        assertEquals(before, counter(s, Metric.ITEMS_USED))
    }

    @Test fun givingAHeldItemCountsAsUsed() {
        val s = looked()
        Economy.give(s, "lucky-egg")
        assertTrue(ItemUse.use(data, s, "lucky-egg", s.main!!.uid, Random(1), NOW).ok)
        assertEquals(1L, counter(s, Metric.ITEMS_USED))
        assertEquals("lucky-egg", s.main!!.heldItem)
    }

    @Test fun anEscapeItemCountsAsUsed() {
        val g = game()
        g.state.battle = Battle(TestData.mon(data, 19, 15, uid = "wild"), Tier.NORMAL, startedAt = time)
        Economy.give(g.state, "poke-doll")
        g.escape("poke-doll")
        assertEquals(1L, counter(g.state, Metric.ITEMS_USED))
    }
}
