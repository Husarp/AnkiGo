package com.husarp.ankigo.game

import com.husarp.ankigo.game.data.Parsers
import com.husarp.ankigo.game.save.SaveCodec
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.TimeZone
import kotlin.random.Random

class GameTest {
    private var data = TestData.world()
    private var now = utc(2026, 10, 13, 12, 0)
    private var nextCard = 1000L
    private lateinit var savedZone: TimeZone

    @Before fun utcZone() {
        savedZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After fun restoreZone() = TimeZone.setDefault(savedZone)

    // ---- helpers ----

    private fun utc(y: Int, mo: Int, d: Int, h: Int, mi: Int): Long =
        ZonedDateTime.of(y, mo, d, h, mi, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli()

    private fun day(n: Long) = n * 24 * 3_600_000L

    /** A game around a Sparkmouse Lv 20 main (HP 47) with only Tackle, so rounds are predictable. */
    private fun game(seed: Int = 1, state: GameState = TestData.state(data, 25, 20, listOf("tackle"))) =
        Game(data, state, Random(seed)) { now }

    /** Puts a made wild Pokémon into the fight. */
    private fun Game.fight(species: Int = 19, level: Int = 15, moves: List<String> = listOf("tackle"), hp: Int? = null, tier: Tier = Tier.NORMAL): Mon {
        val w = TestData.mon(data, species, level, moves, uid = "wild")
        if (hp != null) w.hp = hp
        state.battle = Battle(w, tier, startedAt = now)
        return w
    }

    private fun Game.answer(button: Int, card: Long = nextCard++, catchUp: Boolean = false) =
        onAnswer(Answer(card, button, now, catchUp))

    private inline fun <reified T : GameEvent> List<GameEvent>.of() = filterIsInstance<T>()

    private fun maxHp(m: Mon) = StatCalc.maxHp(data, m)

    /** A state with [teamSize] mons in the team (a..) and [boxSize] in the box (x..). */
    private fun roster(teamSize: Int, boxSize: Int = 0): GameState {
        val s = TestData.state(data, 25, 20, listOf("tackle"))
        s.team.clear(); s.mons.clear()
        val species = listOf(25, 16, 19, 54, 92, 133, 129, 63, 16)
        for (i in 0 until teamSize + boxSize) {
            val m = TestData.mon(data, species[i % species.size], 10 + i, listOf("tackle"), uid = if (i < teamSize) "t$i" else "b$i")
            s.mons += m
            if (i < teamSize) s.team += m.uid
            s.seen += m.species; s.caught += m.species
        }
        return s
    }

    // ---- day key and rollover ----

    @Test fun dayKeyUsesDayStartHour() {
        val g = game()
        assertEquals("2026-10-12", g.dayKey(utc(2026, 10, 13, 3, 59)))
        assertEquals("2026-10-13", g.dayKey(utc(2026, 10, 13, 4, 0)))
        assertEquals("2026-10-13", g.dayKey(utc(2026, 10, 14, 3, 59)))
        g.state.settings.dayStartHour = 0
        assertEquals("2026-10-14", g.dayKey(utc(2026, 10, 14, 0, 0)))
    }

    @Test fun dayKeyUsesPhoneTimeZone() {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Warsaw")) // restored in @After
        val g = game()
        // 03:00 UTC is 05:00 in Warsaw: after the 04:00 day start, so already the 14th (in UTC it would still be the 13th).
        assertEquals("2026-10-14", g.dayKey(utc(2026, 10, 14, 3, 0)))
    }

    @Test fun firstTickJustSetsTheDay() {
        val s = GameState(createdAt = 1L)
        val g = game(state = s)
        assertEquals("", s.day)
        assertTrue(g.tick().isEmpty())
        assertEquals("2026-10-13", s.day)
        assertNull(s.shopDay) // no starter yet: the shop is stocked when the starter is picked
        assertTrue(g.tick().isEmpty())
    }

    @Test fun dayRolloverResetsDailyCountersOnly() {
        val g = game()
        val s = g.state
        repeat(12) { g.answer(if (it == 11) 1 else 3) }
        s.itemCountdown = 77
        val cash = s.cash
        assertTrue(cash > 0)
        assertTrue(s.reviewsToday == 12 && s.cashToday > 0)
        val streak = s.cardStreak
        s.streakDays = 4; s.lastGoalDay = "2026-10-12"

        now += day(1)
        val ev = g.tick()
        assertEquals(listOf<GameEvent>(GameEvent.DayChanged("2026-10-14")), ev)
        assertEquals("2026-10-14", s.day)
        assertEquals(0, s.reviewsToday); assertEquals(0, s.cashToday)
        assertTrue(s.buttonsToday.all { it == 0 })
        assertEquals("2026-10-14", s.shopDay)
        assertEquals(3 + s.trainerLevel / 5, s.shopItems.size)
        // these live on
        assertEquals(cash, s.cash); assertEquals(streak, s.cardStreak); assertEquals(77, s.itemCountdown)
        assertEquals(4, s.streakDays); assertEquals(12L, s.totalReviews)
        assertTrue(g.tick().isEmpty()) // once per day
    }

    @Test fun answerAcrossMidnightRollsTheDayFirst() {
        val g = game()
        g.answer(3)
        now += day(1)
        val ev = g.answer(3)
        assertTrue(ev.first() is GameEvent.DayChanged)
        assertEquals(1, g.state.reviewsToday)
        assertEquals(2L, g.state.totalReviews)
    }

    // ---- bookkeeping per answer ----

    @Test fun countedEventAndButtonBookkeeping() {
        val g = game()
        assertEquals(GameEvent.Counted(4, 20, 1, 1, true), g.answer(4, catchUp = true).of<GameEvent.Counted>().single())
        assertEquals(1, g.state.reviewsToday); assertEquals(1, g.state.buttonsToday[4])
        // unknown (0) counts as Good and as a correct answer
        assertEquals(GameEvent.Counted(0, 10, 2, 2, false), g.answer(0).of<GameEvent.Counted>().single())
        assertEquals(1, g.state.buttonsToday[0])
        // out of range is clamped to Easy, not a crash
        assertEquals(4, g.answer(7).of<GameEvent.Counted>().single().button)
    }

    @Test fun cardStreakResetsOnAgain() {
        val g = game()
        g.answer(3); g.answer(4); g.answer(2)
        assertEquals(3, g.state.cardStreak)
        g.answer(1)
        assertEquals(0, g.state.cardStreak)
        assertEquals(3, g.state.bestCardStreak)
        g.answer(3)
        assertEquals(1, g.state.cardStreak)
        assertEquals(3, g.state.bestCardStreak)
    }

    @Test fun chargeThenRoundWithTwoAnswers() {
        val g = game()
        g.state.battle = null
        val first = g.answer(3)
        assertEquals(1, first.of<GameEvent.Encounter>().size) // the first answer starts the encounter
        assertEquals(GameEvent.Charge(10, 1, 2), first.last())
        assertTrue(first.of<GameEvent.RoundStart>().isEmpty())
        assertEquals(1, g.state.battle!!.answers)

        val second = g.answer(3)
        assertEquals(1, second.of<GameEvent.RoundStart>().size)
        assertTrue(second.of<GameEvent.Charge>().isEmpty())
        assertEquals(0, g.state.battle!!.answers); assertEquals(0, g.state.battle!!.tally)
        assertEquals(1, g.state.battle!!.rounds)
    }

    @Test fun goodGoodIsMultiplierOneAndTheEnemyStaysQuiet() {
        val g = game()
        val wild = g.fight(hp = 36)
        val hp = g.state.main!!.hp
        g.answer(3)
        val ev = g.answer(3)
        assertEquals(GameEvent.RoundStart(1.0, 20, false), ev.of<GameEvent.RoundStart>().single())
        assertEquals(1, ev.of<GameEvent.Splash>().size)
        assertEquals(1, ev.of<GameEvent.Attack>().size) // only ours
        val atk = ev.of<GameEvent.Attack>().single()
        assertTrue(atk.byPlayer && atk.damage in 2..36)
        assertEquals(hp, g.state.main!!.hp)
        assertEquals(36 - atk.damage, wild.hp)
        assertSame(wild, g.state.battle!!.wild) // still the same fight
    }

    @Test fun againAgainOnlyTheWildOneAttacksAtOnePointTwo() {
        val g = game()
        val wild = g.fight(hp = 36)
        val hp = g.state.main!!.hp
        g.answer(1)
        val ev = g.answer(1)
        val rs = ev.of<GameEvent.RoundStart>().single()
        assertEquals(GameEvent.RoundStart(0.0, 0, true, false, 2.0), rs) // R10-B22: no Good/Easy answer = the Main does not attack at all
        val attacks = ev.of<GameEvent.Attack>()
        assertTrue(attacks.none { it.byPlayer }) // not even the old 1 HP hit
        assertTrue(attacks.single { !it.byPlayer }.damage > 0)
        assertEquals(36, wild.hp)
        assertTrue(g.state.main!!.hp < hp)
        assertTrue(ev.of<GameEvent.Splash>().isEmpty())
    }

    @Test fun eachAnswerPairDecidesWhoAttacksAndHowHard() {
        // (first, second) -> main multiplier (null = the Main does not attack), wild power (null = the wild one does not attack)
        val cases = listOf(
            Triple(1 to 1, null, 2.0), Triple(1 to 2, null, 1.5), Triple(2 to 2, null, 1.0),
            Triple(1 to 3, 0.5, 1.0), Triple(1 to 4, 1.0, 1.0), Triple(2 to 3, 0.5, 0.5), Triple(2 to 4, 1.0, 0.5),
            Triple(3 to 3, 1.0, null), Triple(3 to 4, 1.5, null), Triple(4 to 4, 2.0, null), Triple(0 to 3, 1.0, null),
        )
        for ((pair, main, wild) in cases) {
            for ((x, y) in listOf(pair.first to pair.second, pair.second to pair.first)) {
                val g = game(); g.fight(hp = 36)
                g.answer(x)
                val rs = g.answer(y).of<GameEvent.RoundStart>().single()
                val label = "$x+$y"
                assertEquals(label, main != null, rs.mainActs)
                assertEquals(label, main ?: 0.0, rs.multiplier, 1e-9)
                assertEquals(label, wild != null, rs.enemyActs)
                assertEquals(label, wild ?: 0.0, rs.enemyPower, 1e-9)
            }
        }
    }

    @Test fun hardPlusEasyBothAttackAndHardHardOnlyTheWild() {
        val g = game()
        g.fight(hp = 36)
        g.answer(2) // Hard
        val ev = g.answer(4) // Easy
        assertEquals(GameEvent.RoundStart(1.0, 25, true, true, 0.5), ev.of<GameEvent.RoundStart>().single())
        val attacks = ev.of<GameEvent.Attack>()
        assertEquals(2, attacks.size) // both attack in one round
        assertTrue(ev.of<GameEvent.Splash>().isEmpty())
        val h = game(); h.fight(hp = 36)
        h.answer(2)
        val hh = h.answer(2)
        assertEquals(GameEvent.RoundStart(0.0, 10, true, false, 1.0), hh.of<GameEvent.RoundStart>().single()) // Hard+Hard
        assertTrue(hh.of<GameEvent.Attack>().none { it.byPlayer })
    }

    @Test fun theWildOnesHitIsScaledByThePowerOfTheBadAnswers() {
        fun wildHit(buttons: List<Int>, seed: Int): Int {
            val g = game(seed); g.fight(species = 150, level = 40, moves = listOf("tackle"), hp = 36)
            var last = emptyList<GameEvent>()
            for (b in buttons) last = g.answer(b)
            return last.of<GameEvent.Attack>().single { !it.byPlayer }.damage
        }
        for (seed in 1..8) {
            val full = wildHit(listOf(1, 1), seed) // 2.0
            val again = wildHit(listOf(1, 3), seed) // 1.0
            val hard = wildHit(listOf(2, 3), seed) // 0.5
            assertTrue("again $again > full $full", again <= full) // ×2 may be cut short by the Main's HP
            assertTrue("hard $hard < again $again", hard < again)
            assertTrue(hard >= 1)
        }
    }

    @Test fun anOldSavesRoundInProgressKeepsItsAnswer() {
        // A battle saved before Round 10 has the point sum only (Again 0 / Hard 5 / Good 10 / Easy 20) and no buttons.
        for ((points, first) in listOf(0 to 1, 5 to 2, 10 to 3, 20 to 4)) {
            val g = game(); g.fight(hp = 36)
            g.state.battle!!.tally = points; g.state.battle!!.answers = 1
            val rs = g.answer(3).of<GameEvent.RoundStart>().single() // + Good
            val expected = RoundRule.plan(listOf(first, 3))
            assertEquals("first=$first", expected.enemyActs, rs.enemyActs)
            assertEquals("first=$first", expected.mainActs, rs.mainActs)
            assertEquals("first=$first", expected.multiplier, rs.multiplier, 1e-9)
        }
    }

    @Test fun theButtonsOfTheRoundSurviveASaveAndReset() {
        val g = game(); g.fight(hp = 36)
        g.answer(1)
        assertEquals(listOf(1), g.state.battle!!.buttons)
        val back = com.husarp.ankigo.game.save.SaveCodec.decode(com.husarp.ankigo.game.save.SaveCodec.encode(g.state))
        assertEquals(listOf(1), back.battle!!.buttons)
        g.answer(4)
        assertTrue(g.state.battle!!.buttons.isEmpty())
    }

    @Test fun aRoundIsAlwaysTwoAnswers() {
        val g = game(); g.fight(hp = 36)
        assertEquals(GameEvent.Charge(10, 1, Tuning.CARDS_PER_ROUND), g.answer(3).last())
        assertEquals(GameEvent.RoundStart(1.0, 20, false), g.answer(3).of<GameEvent.RoundStart>().single())
    }

    @Test fun anOldSavesCardsPerRoundIsIgnored() {
        for (v in listOf(1, 3, 9)) {
            val o = org.json.JSONObject(com.husarp.ankigo.game.save.SaveCodec.encode(game().state))
            o.getJSONObject("settings").put("cardsPerRound", v)
            val g = Game(data, com.husarp.ankigo.game.save.SaveCodec.decode(o.toString()), Random(1)) { now }
            g.fight(hp = 36)
            assertEquals(GameEvent.Charge(10, 1, 2), g.answer(3).last())
            assertEquals(1, g.answer(3).of<GameEvent.RoundStart>().size)
        }
    }


    // ---- resolving a fight ----

    @Test fun newSpeciesFaintingIsCaught() {
        val g = game()
        val s = g.state
        val wild = g.fight(species = 54, level = 12, hp = 1)
        assertTrue(g.willCatch())
        g.answer(3)
        val ev = g.answer(3)
        val caught = ev.of<GameEvent.Caught>().single()
        assertEquals(54, caught.species); assertTrue(caught.newSpecies); assertFalse(caught.toTeam); assertNull(caught.ball)
        assertEquals(12, caught.level)
        assertTrue(ev.of<GameEvent.Fainted>().any { !it.player })
        // AnkiGo (Tuning.CATCH_GIVES_XP): a 0-HP catch gives the full defeat XP to the main; it counts as a win
        // (defeat counters once, trainer XP by tier) but is no Defeated event; it pays the win money (R13 Part C)
        assertEquals(Progression.xpForDefeat(data.sp(54), 12), ev.of<GameEvent.XpGained>().single { it.uid == s.main!!.uid }.xp)
        assertTrue(ev.of<GameEvent.Defeated>().isEmpty())
        assertEquals(1, s.catches); assertEquals(1, s.defeats); assertEquals(1, s.catchWins); assertEquals(1, s.main!!.defeats)
        assertEquals(Tuning.TRAINER_XP[Tier.NORMAL]!!, s.trainerXp)
        assertEquals(2 * Tuning.CASH_PER_ANSWER + Tuning.CASH_PER_WIN, s.cashToday) // the two answers and the catch
        assertTrue(54 in s.caught && 54 in s.seen)
        assertEquals(1, s.team.size)
        val mon = s.mon(caught.uid)!!
        assertEquals(maxHp(mon), mon.hp) // caught at full health
        // the next wild one is there at once
        val next = ev.of<GameEvent.Encounter>().single()
        assertNotSame(wild, s.battle!!.wild)
        assertEquals(next.species, s.battle!!.wild.species)
        assertTrue(s.battle!!.wild.hp > 0)
    }

    @Test fun knownSpeciesFaintingIsDefeatedWithXpAndTrainerXp() {
        val g = game()
        val s = g.state
        s.caught += 16
        g.fight(species = 16, level = 12, hp = 1)
        assertFalse(g.willCatch())
        g.answer(3)
        val ev = g.answer(3)
        val xp = Progression.xpForDefeat(data.sp(16), 12) // ceil(50*12/7) = 86
        assertEquals(86, xp)
        assertEquals(GameEvent.Defeated(16, "Birdie", xp), ev.of<GameEvent.Defeated>().single())
        assertEquals(xp, ev.of<GameEvent.XpGained>().single().xp)
        assertEquals(xp, s.main!!.xp)
        assertEquals(1, s.defeats); assertEquals(1, s.main!!.defeats); assertEquals(0, s.catches)
        assertEquals(10, s.trainerXp)
        assertTrue(ev.of<GameEvent.Caught>().isEmpty())
        assertEquals(1, s.team.size)
        assertEquals(1, ev.of<GameEvent.Encounter>().size)
    }

    @Test fun defeatingLevelsUpAndEvolvesTheMain() {
        val s = TestData.state(data, 1, 15, listOf("tackle")) // Leafling 15, evolves at 16
        val g = game(state = s)
        s.caught += 19
        val need = Progression.xpToNext(data, s.main!!)
        g.fight(species = 19, level = 40, hp = 1) // 51 * 40 / 7 = 292 XP
        s.main!!.xp = need - 1
        g.answer(3)
        val ev = g.answer(3)
        assertEquals(16, s.main!!.level)
        assertEquals(1, ev.of<GameEvent.LevelUp>().size)
        assertEquals(2, s.main!!.species)
        assertEquals(GameEvent.Evolved("main", "Leafling", "Leafbud", 2), ev.of<GameEvent.Evolved>().single())
        assertEquals(1, s.evolutions)
    }

    @Test fun overrideChangesTheOutcomeForOneFight() {
        val g = game()
        g.fight(species = 54, hp = 1)
        assertTrue(g.willCatch())
        g.setOverride(Outcome.DEFEAT)
        assertFalse(g.willCatch())
        g.answer(3)
        val ev = g.answer(3)
        assertEquals(1, ev.of<GameEvent.Defeated>().size)
        assertTrue(ev.of<GameEvent.Caught>().isEmpty())
        assertNull(g.state.battle!!.override) // the next fight starts clean

        val h = game()
        h.state.caught += 16
        h.fight(species = 16, hp = 1)
        h.setOverride(Outcome.CATCH)
        assertTrue(h.willCatch())
        h.setOverride(null)
        assertFalse(h.willCatch())
        h.setOverride(Outcome.CATCH)
        h.answer(3)
        assertEquals(1, h.answer(3).of<GameEvent.Caught>().size)
    }

    @Test fun wishlistAndProtectedAreCaughtEvenWhenKnown() {
        val g = game()
        g.state.caught += listOf(25, 150)
        g.fight(species = 25, hp = 1)
        assertTrue(g.willCatch()) // wishlist
        g.fight(species = 150, level = 5, hp = 1, tier = Tier.LEGENDARY)
        assertTrue(g.willCatch()) // protected
        g.state.settings.protectLegendary = false
        assertFalse(g.willCatch())
    }

    @Test fun defeatTapBeatsTheWishlistButNotProtection() {
        val g = game()
        g.state.caught += 25
        g.fight(species = 25, hp = 1) // wishlisted
        g.setOverride(Outcome.DEFEAT)
        assertFalse(g.willCatch())
        g.answer(3)
        val ev = g.answer(3)
        assertEquals(1, ev.of<GameEvent.Defeated>().size)
        assertTrue(ev.of<GameEvent.Caught>().isEmpty())

        val h = game()
        h.fight(species = 150, level = 5, hp = 1, tier = Tier.LEGENDARY)
        h.setOverride(Outcome.DEFEAT)
        assertTrue(h.willCatch()) // protected: still caught
        h.answer(3)
        val ev2 = h.answer(3)
        assertEquals(1, ev2.of<GameEvent.Caught>().size)
        assertTrue(ev2.of<GameEvent.Defeated>().isEmpty())
    }

    @Test fun fullTeamSendsTheCatchToTheBox() {
        val g = game(state = roster(6))
        g.fight(species = 129, hp = 1)
        g.answer(3)
        val caught = g.answer(3).of<GameEvent.Caught>().single()
        assertFalse(caught.toTeam)
        assertEquals(6, g.state.team.size)
        assertTrue(g.state.mon(caught.uid) in g.state.boxMons())
    }

    @Test fun mainFaintingIsHealedAtOnceAndTheWildOneIsLost() {
        val g = game()
        val s = g.state
        val main = s.main!!
        main.hp = 1
        val wild = g.fight(hp = 36)
        g.answer(1)
        val ev = g.answer(1)
        assertTrue(ev.of<GameEvent.Fainted>().any { it.player })
        assertEquals(GameEvent.MainHealed("main", main.name, maxHp(main)), ev.of<GameEvent.MainHealed>().single())
        assertEquals(maxHp(main), main.hp)
        assertTrue(ev.of<GameEvent.Caught>().isEmpty() && ev.of<GameEvent.Defeated>().isEmpty())
        assertEquals(0, s.catches); assertEquals(0, s.defeats)
        assertEquals(1, ev.of<GameEvent.Encounter>().size) // a new one right away
        assertNotSame(wild, s.battle!!.wild)
        assertEquals(0, s.battle!!.answers)
    }

    // ---- cash, goal, badges ----

    /** Answers [n] times with the fight reset each time, so no round ever completes: only the per-answer money is in play. */
    private fun Game.plainAnswers(n: Int, button: Int = 3): List<GameEvent> =
        (1..n).flatMap { fight(hp = 100_000); answer(button) }

    private fun List<GameEvent>.cash() = of<GameEvent.Cash>()

    @Test fun everyAnswerPaysFiveAgainToo() {
        val g = game()
        g.state.settings.dailyAverage = 1000
        listOf(1, 2, 3, 4).forEachIndexed { i, button ->
            g.fight(hp = 100_000)
            assertEquals(GameEvent.Cash(5, 5 * (i + 1), CashKind.ANSWER), g.answer(button).cash().single())
        }
        assertEquals(20, g.state.cash); assertEquals(20, g.state.cashToday)
    }

    @Test fun aHundredAnswersFillTheFirstTierThenEveryAnswerPaysHalf() {   // PLAN 125: the linear money curve
        val g = game()
        g.state.settings.dailyAverage = 1000
        val first = g.plainAnswers(100)
        assertEquals(100, first.cash().size)
        assertTrue(first.cash().all { it.kind == CashKind.ANSWER && it.amount == 5 })
        assertEquals(500, g.state.cash); assertEquals(500, g.state.cashToday)
        val next = g.plainAnswers(10)
        assertEquals("every answer of the second 500 pays 2.5 as whole coins: 2, 3, 2, 3 ...", listOf(2, 3, 2, 3, 2, 3, 2, 3, 2, 3), next.cash().map { it.amount })   // PLAN 198
        assertEquals(525, g.state.cash); assertEquals(525, g.state.cashToday)
    }

    @Test fun amuletCoinDoublesEveryAnswerAndWinPayment() {   // R13 Part C: a won fight pays again, doubled like an answer
        val g = game()
        g.state.settings.dailyAverage = 1000
        g.state.main!!.heldItem = "amulet-coin"
        assertEquals(GameEvent.Cash(10, 10, CashKind.ANSWER), g.plainAnswers(1).cash().single())
        g.state.caught += 16
        g.fight(species = 16, level = 12, hp = 1)
        g.answer(3)
        val ev = g.answer(3)
        assertEquals(1, ev.of<GameEvent.Defeated>().size)
        assertEquals(listOf(CashKind.ANSWER to 10, CashKind.DEFEAT to 20), ev.cash().map { it.kind to it.amount })
        assertEquals(50, g.state.cash)
        assertEquals("the curve counts the money before the ×2", 25, g.state.cashToday)
        g.state.cashToday = 500   // the second tier: an answer is 2.5 (2 then 3 coins, PLAN 198), still doubled
        g.state.main!!.heldItem = "luck-incense"
        assertEquals(GameEvent.Cash(4, 54, CashKind.ANSWER), g.plainAnswers(1).cash().single())
        assertEquals(GameEvent.Cash(6, 60, CashKind.ANSWER), g.plainAnswers(1).cash().single())
        assertEquals(505, g.state.cashToday)
    }

    @Test fun aDefeatAndAnAutoCatchEachPayTheWinMoney() {   // R13 Part C (PLAN 170 had taken it away)
        val g = game()
        g.state.settings.dailyAverage = 1000
        g.state.caught += 16
        g.fight(species = 16, level = 12, hp = 1) // known species: the auto rule defeats it
        g.answer(3)
        val ev = g.answer(3)
        assertEquals(1, ev.of<GameEvent.Defeated>().size)
        assertEquals(listOf(CashKind.ANSWER to 5, CashKind.DEFEAT to Tuning.CASH_PER_WIN), ev.cash().map { it.kind to it.amount })
        assertEquals(listOf(10, 20), ev.cash().map { it.total }) // first answer paid 5 before
        assertTrue("the money comes after the result", ev.indexOfFirst { it is GameEvent.Defeated } < ev.indexOfLast { it is GameEvent.Cash })
        assertEquals(20, g.state.cash); assertEquals(20, g.state.cashToday)

        val c = game()
        c.state.settings.dailyAverage = 1000
        c.fight(species = 54, level = 12, hp = 1) // new species: caught
        c.answer(3)
        val cev = c.answer(3)
        assertEquals(1, cev.of<GameEvent.Caught>().size)
        assertEquals(listOf(CashKind.ANSWER to 5, CashKind.DEFEAT to 10), cev.cash().map { it.kind to it.amount })
        assertEquals(20, c.state.cash) // two answers and the catch
    }

    @Test fun aDefeatTapOnAWishlistedOneAndABallCatchPayTheWinMoney() {   // R13 Part C
        val g = game()
        g.state.settings.dailyAverage = 1000
        g.state.caught += 25
        g.fight(species = 25, hp = 1)
        g.setOverride(Outcome.DEFEAT)
        g.answer(3)
        val dev = g.answer(3)
        assertEquals(1, dev.of<GameEvent.Defeated>().size)
        assertEquals(listOf(10), dev.cash().filter { it.kind == CashKind.DEFEAT }.map { it.amount })

        val b = game()
        Economy.give(b.state, "master-ball", 1)
        b.fight(species = 54)
        val ev = b.throwBall("master-ball")
        assertEquals(1, ev.of<GameEvent.Caught>().size)
        assertEquals(listOf(GameEvent.Cash(10, 10, CashKind.DEFEAT)), ev.cash())
        assertEquals(10, b.state.cash); assertEquals(10, b.state.cashToday)
    }

    @Test fun inTheSecondTierAWinIsWorthFiveAndSharesTheFractionWithTheAnswers() {   // R13 Part C, PLAN 198: base / tier, one exact fraction
        val g = game()
        g.state.settings.dailyAverage = 1000
        g.state.caught += 16
        g.state.cashToday = 500
        g.fight(species = 16, level = 12, hp = 1)
        val first = g.answer(3)
        assertEquals("answer 1 of tier 2: 2.5 pays 2", listOf(CashKind.ANSWER to 2), first.cash().map { it.kind to it.amount })
        val ev = g.answer(3)
        assertEquals(1, ev.of<GameEvent.Defeated>().size)
        assertEquals("answer 2 pays 3 (the half coin was kept), the win 10/2 = 5", listOf(CashKind.ANSWER to 3, CashKind.DEFEAT to 5), ev.cash().map { it.kind to it.amount })
        assertEquals(0L, g.state.cashFracNum)
        g.fight(species = 16, level = 12, hp = 1)
        g.answer(3)
        val ev2 = g.answer(3)
        assertEquals(listOf(CashKind.ANSWER to 3, CashKind.DEFEAT to 5), ev2.cash().map { it.kind to it.amount })
        assertEquals(20, g.state.cash); assertEquals(520, g.state.cashToday)
        g.state.cashFracNum = 1; g.state.cashFracDen = 2
        now += day(1)
        g.plainAnswers(1)
        assertEquals("a new day starts the fraction again too (the new day's first answer pays 5 and leaves 0)", 0L, g.state.cashFracNum)
        assertEquals(1L, g.state.cashFracDen)
    }

    @Test fun cashSoFarOfAnOldSaveCarriesOverAndTheNewDayStartsAtZero() {
        val g = game()
        g.state.settings.dailyAverage = 1000
        g.state.cashToday = 380 // a day of the old rule
        // 24 answers of 5 to 500, then the other 16 are 2.5 each = 40 in two-coin / three-coin steps (8 + 8 pairs): 16 more payments
        assertEquals(24 + 16, g.plainAnswers(40).cash().size)
        assertEquals(500 + 40, g.state.cashToday)
        now += day(1)
        assertEquals(GameEvent.Cash(5, 165, CashKind.ANSWER), g.plainAnswers(1).cash().single())
        assertEquals(5, g.state.cashToday); assertEquals(0L, g.state.cashFracNum)
    }

    @Test fun undoThenRedoOfAnAnswerPaysNothing() {
        val g = game()
        g.state.settings.dailyAverage = 1000
        assertEquals(1, g.answer(3, card = 5).cash().size)
        g.onUndo(5, 1)
        val ev = g.answer(3, card = 5)
        assertEquals(1, ev.of<GameEvent.Ignored>().size)
        assertTrue(ev.cash().isEmpty())
        assertEquals(5, g.state.cash); assertEquals(5, g.state.cashToday)
    }

    // ---- the daily gift (R14-B2-D: it replaced the daily money bonus) ----

    private fun List<GameEvent>.gifts() = of<GameEvent.DailyGift>()

    @Test fun reachingTheGoalGivesOneGiftIntoTheBagAndNoMoney() {
        val g = game()
        g.state.settings.dailyAverage = 3
        g.state.itemCountdown = 1000 // no random item drop in these answers: only the gift changes the bag
        val bagBefore = g.state.bag.values.sum(); val tmsBefore = g.state.tms.toSet()
        val ev = g.plainAnswers(3)
        val gift = ev.gifts().single()
        assertEquals(1, gift.streakDays)
        assertTrue("no money bonus any more", ev.cash().none { it.kind == CashKind.DAILY_BONUS })
        assertEquals(15, g.state.cash) // the three answers only
        assertEquals(15, g.state.cashToday)
        assertTrue(ev.indexOf(GameEvent.Info("Daily goal reached!")) < ev.indexOf(gift)) // the goal line, then its gift
        if (gift.item != null) {
            assertNull(gift.tm)
            assertEquals(bagBefore + gift.count, g.state.bag.values.sum())
            assertTrue(gift.item in Items.ALL)
        } else {
            assertTrue(gift.tm!! !in tmsBefore && gift.tm in g.state.tms)
            assertEquals(bagBefore, g.state.bag.values.sum())
        }
        assertTrue("once a day", g.plainAnswers(5).gifts().isEmpty())
        assertEquals(15 + 25, g.state.cash) // five more answers, still no bonus
    }

    @Test fun theGiftComesAgainTheNextDayAndTheStreakGrowsWithIt() {
        val g = game()
        g.state.settings.dailyAverage = 3
        val streaks = ArrayList<Int>()
        repeat(3) {
            streaks += g.plainAnswers(3).gifts().single().streakDays
            assertTrue(g.plainAnswers(2).gifts().isEmpty())
            now += day(1)
        }
        assertEquals(listOf(1, 2, 3), streaks)
    }

    @Test fun theGiftCarriesTheStreakOfToday() {
        fun streakOfGift(streak: Int, lastGoal: String): Int {
            val g = game()
            g.state.settings.dailyAverage = 2
            g.state.streakDays = streak; g.state.lastGoalDay = lastGoal
            return g.plainAnswers(2).gifts().single().streakDays
        }
        assertEquals(7, streakOfGift(6, "2026-10-12")) // six goal days in a row until yesterday, today makes seven
        assertEquals(24, streakOfGift(23, "2026-10-12"))
        assertEquals(1, streakOfGift(9, "2026-10-11")) // a day was missed: the streak starts again
        assertEquals(1, streakOfGift(0, ""))
    }

    @Test fun theGiftNeverTouchesTheMoneyCurveOrTheAmuletCoin() {
        val h = game()
        h.state.settings.dailyAverage = 3
        h.state.main!!.heldItem = "amulet-coin"
        h.state.cashToday = 500 // the second tier: the 3 answers pay 2, 3, 2 (doubled)
        val ev = h.plainAnswers(3)
        assertEquals(listOf(CashKind.ANSWER to 4, CashKind.ANSWER to 6, CashKind.ANSWER to 4), ev.cash().map { it.kind to it.amount })
        assertEquals(1, ev.gifts().size)
        assertEquals(507, h.state.cashToday)
        assertEquals(14, h.state.cash)
    }

    @Test fun aGoalLoweredDuringTheDayStillGivesTheGiftOnce() {
        val g = game()
        g.state.settings.dailyAverage = 1000
        assertTrue(g.plainAnswers(5).gifts().isEmpty())
        g.state.settings.dailyAverage = 3 // already 5 answers today: the very next answer reaches the lowered goal
        assertEquals(1, g.plainAnswers(1).gifts().size)
        assertTrue(g.plainAnswers(4).gifts().isEmpty())
    }

    @Test fun theGiftSurvivesASaveAndLoadAndIsNotGivenAgainAfterARestart() {
        val g = game()
        g.state.settings.dailyAverage = 3
        val gift = g.plainAnswers(3).gifts().single()
        val back = SaveCodec.decode(SaveCodec.encode(g.state))
        assertEquals(g.state.lastGoalDay, back.lastGoalDay)
        assertEquals(g.state.bag, back.bag); assertEquals(g.state.tms, back.tms)
        if (gift.tm != null) assertTrue(gift.tm in back.tms)
        val again = Game(data, back, Random(2)) { now } // the app was killed and opened again the same day
        assertTrue(again.plainAnswers(4).gifts().isEmpty())
        assertEquals(7, back.reviewsToday)
    }

    @Test fun theGiftOfADayIsTheSameAfterARestart() {
        fun giftOf(seed: Int): GameEvent.DailyGift {
            val g = game(seed = seed)
            g.state.settings.dailyAverage = 2
            return g.plainAnswers(2).gifts().single()
        }
        assertEquals("seeded by the game day, not by the random generator", giftOf(1), giftOf(99))
    }

    @Test fun anAnswerThatReachesTheGoalCountsInTheStatsLogAndTheAchievementCounterToo() {
        val g = game()
        g.state.settings.dailyAverage = 1
        val ev = g.answer(3)
        assertEquals(listOf(CashKind.ANSWER), ev.cash().map { it.kind })
        assertEquals(1, ev.gifts().size)
        assertEquals(5, g.state.history[g.state.day]!!.cash) // money earned that day: the answer only
        assertEquals(1L, g.state.counters[Metric.GOALS_REACHED.name]) // counted on the DailyGift event
    }

    @Test fun reviewBadgeComesWithTheHundredthAnswer() {
        val g = game()
        g.state.reviewsToday = 99
        val ev = g.answer(3)
        assertTrue(ev.of<GameEvent.BadgeEarned>().any { it.id == 1 })
        assertTrue(1 in g.state.badges)
    }

    @Test fun dailyGoalReachedOncePerDayAndStreakCountsConsecutiveDays() {
        val g = game()
        val s = g.state
        s.settings.dailyAverage = 3
        fun dayOf3(): Int {
            var goals = 0
            repeat(5) { goals += g.answer(3).count { it == GameEvent.Info("Daily goal reached!") } }
            return goals
        }
        assertEquals(1, dayOf3())
        assertEquals(1, s.streakDays); assertEquals("2026-10-13", s.lastGoalDay)
        now += day(1)
        assertEquals(1, dayOf3())
        assertEquals(2, s.streakDays)
        now += day(1)
        assertEquals(1, dayOf3())
        assertEquals(3, s.streakDays)
        now += day(2) // a day missed
        assertEquals(1, dayOf3())
        assertEquals(1, s.streakDays); assertEquals("2026-10-17", s.lastGoalDay)
    }

    @Test fun missingTheGoalLeavesTheStreakAndAnOldLastGoalDay() {
        val g = game()
        g.state.settings.dailyAverage = 50
        g.state.streakDays = 6; g.state.lastGoalDay = "2026-10-12"
        repeat(10) { g.answer(3) }
        assertEquals(6, g.state.streakDays)
        assertEquals("2026-10-12", g.state.lastGoalDay)
    }

    @Test fun loweringTheGoalMidDayStillPaysOutOnce() {
        val g = game()
        repeat(7) { g.answer(3) }
        g.state.settings.dailyAverage = 5
        val goals = (1..3).sumOf { g.answer(3).count { e -> e == GameEvent.Info("Daily goal reached!") } }
        assertEquals(1, goals)
        assertEquals(1, g.state.streakDays)
    }

    // ---- undo ----

    @Test fun redoAfterUndoIsIgnoredOnce() {
        val g = game()
        g.answer(3, card = 5)
        assertEquals(1, g.state.reviewsToday)
        g.onUndo(5, 1)
        val ev = g.answer(3, card = 5)
        assertEquals(1, ev.of<GameEvent.Ignored>().size)
        assertTrue(ev.of<GameEvent.Counted>().isEmpty())
        assertEquals(1, g.state.reviewsToday); assertEquals(1L, g.state.totalReviews)
        assertTrue(g.state.redo.isEmpty())
        assertEquals(1, g.answer(3, card = 5).of<GameEvent.Counted>().size) // the next one counts again
    }

    @Test fun undoOfSeveralAnswersIgnoresThatManyAndOnlyThatCard() {
        val g = game()
        g.onUndo(8, 2)
        assertEquals(1, g.answer(3, card = 9).of<GameEvent.Counted>().size)
        assertEquals(1, g.answer(3, card = 8).of<GameEvent.Ignored>().size)
        assertEquals(1, g.state.redo[8])
        assertEquals(1, g.answer(3, card = 8).of<GameEvent.Ignored>().size)
        assertTrue(g.state.redo.isEmpty())
        assertEquals(1, g.answer(3, card = 8).of<GameEvent.Counted>().size)
    }

    @Test fun undoDoesNotRewindTheBattle() {
        val g = game()
        val w = g.fight(hp = 36)
        g.answer(3, card = 1); g.answer(3, card = 2)
        val hp = w.hp
        val rounds = g.state.battle!!.rounds
        g.onUndo(2, 1)
        assertEquals(hp, w.hp); assertEquals(rounds, g.state.battle!!.rounds)
        assertEquals(2L, g.state.totalReviews)
    }

    @Test fun staleRedoIsClearedAtTheNextDay() {
        val g = game()
        g.onUndo(3, 1)
        now += day(1)
        val ev = g.answer(3, card = 3)
        assertEquals(1, ev.of<GameEvent.Counted>().size)
    }

    // ---- no starter ----

    @Test fun nothingCountsBeforeTheStarter() {
        val s = GameState(createdAt = 1L)
        val g = game(state = s)
        val ev = g.answer(3)
        assertTrue(ev.of<GameEvent.Counted>().isEmpty())
        assertEquals(0, s.reviewsToday); assertEquals(0L, s.totalReviews)
        assertNull(s.battle)
        assertTrue(g.ensureEncounter().isEmpty())
    }

    @Test fun pickStarterOnlyOnce() {
        val s = GameState(createdAt = 1L)
        val g = game(state = s)
        val ev = g.pickStarter(1)
        assertTrue(s.starterChosen)
        val m = s.main!!
        assertEquals(1, m.species); assertEquals(5, m.level); assertEquals(now, m.caughtAt)
        assertEquals(listOf(m.uid), s.team); assertEquals(listOf(m), s.mons)
        assertTrue(1 in s.seen && 1 in s.caught)
        assertEquals(Tuning.START_CASH, s.cash)
        assertEquals("2026-10-13", s.day); assertEquals("2026-10-13", s.shopDay)
        assertEquals(maxHp(m), m.hp)
        assertTrue(m.moves.size in 1..4)
        assertNotNull(s.battle)
        assertEquals(1, ev.of<GameEvent.Encounter>().size)

        val before = SaveCodec.encode(s)
        assertTrue(g.pickStarter(4).isEmpty())
        assertEquals(before, SaveCodec.encode(s))
    }

    @Test fun pickStarterRejectsUnknownSpecies() {
        val s = GameState(createdAt = 1L)
        val g = game(state = s)
        assertTrue(g.pickStarter(99999).isEmpty())
        assertFalse(s.starterChosen); assertTrue(s.mons.isEmpty())
    }

    // ---- encounters ----

    @Test fun ensureEncounterDoesNothingDuringAFight() {
        val g = game()
        val w = g.fight()
        assertTrue(g.ensureEncounter().isEmpty())
        assertSame(w, g.state.battle!!.wild)
    }

    /** A seed whose draws (wild level of a [mainLevel] main, then the roll) give a roll that satisfies [pred] in [st]. */
    private fun seedRolling(st: GameState, mainLevel: Int = 20, pred: (Encounters.Roll) -> Boolean): Long =
        (0L..20_000L).first { val r = Random(it); pred(Encounters.roll(data, st, Encounters.wildLevel(mainLevel, r), r)) }

    @Test fun encounterIsRolledFromTheSeedAndTheNextSeedIsDrawn() {
        val g = game()
        val s = g.state
        s.caught += 16
        s.nextSeed = seedRolling(s) { it.species == 54 && it.tier == Tier.NORMAL }
        val seed = s.nextSeed!!
        val ev = g.ensureEncounter().single() as GameEvent.Encounter
        assertEquals(54, ev.species); assertTrue(ev.newSpecies); assertTrue(ev.willCatch)
        assertEquals(Encounters.wildLevel(20, Random(seed)), ev.level)
        assertEquals(54, s.battle!!.wild.species)
        assertTrue(54 in s.seen)
        assertEquals(ev.maxHp, ev.hp)
        assertEquals(ev.level, s.battle!!.wild.level)
        assertNotNull(s.nextSeed); assertTrue(s.nextSeed != seed) // a new seed for the one after
        assertNotNull(s.nextSpecies)
    }

    @Test fun predictionEqualsTheEncounterWhenNothingChanged() {
        val g = game()
        val s = g.state
        g.ensureEncounter() // first one: from the game rng (no seed yet), then the seed and prediction are set
        assertNotNull(s.nextSeed)
        repeat(30) {
            val predicted = Triple(s.nextSpecies, s.nextTier, s.nextShiny)
            val seed = s.nextSeed!!
            s.battle = null
            val ev = g.ensureEncounter().single() as GameEvent.Encounter
            assertEquals(predicted, Triple(ev.species, ev.tier, ev.shiny))
            assertEquals(Encounters.wildLevel(20, Random(seed)), ev.level)
        }
    }

    @Test fun anOldSaveWithoutASeedUsesTheGameRngAndThenGetsOne() {
        val g = game()
        assertNull(g.state.nextSeed)
        g.ensureEncounter()
        assertNotNull(g.state.nextSeed); assertNotNull(g.state.nextSpecies)
    }

    @Test fun aNewDayDoesNotUseTheStaleRareRoll() {
        val g = game()
        val s = g.state
        s.reviewsToday = 100 // the daily goal is 100: the rare tiers are open today
        s.caught += 16
        val seed = seedRolling(s) { it.tier == Tier.BABY }
        s.nextSeed = seed
        s.nextTier = Tier.BABY; s.nextSpecies = 172 // yesterday's prediction
        now += day(1)
        val events = g.answer(3)
        val ev = events.of<GameEvent.Encounter>().first()
        assertEquals(Tier.NORMAL, ev.tier) // reviewsToday restarted at 0: below 40 % of the goal, only Normal
        assertTrue(ev.species != 172)
        assertEquals(1, s.reviewsToday)
    }

    @Test fun theDayChangeRefreshesThePrediction() {
        val g = game()
        val s = g.state
        s.reviewsToday = 100
        s.caught += 16
        s.nextSeed = seedRolling(s) { it.tier == Tier.BABY }
        s.nextTier = Tier.BABY; s.nextSpecies = 172
        g.fight()
        now += day(1)
        g.tick()
        assertEquals(Tier.NORMAL, s.nextTier)
        assertTrue(s.nextSpecies != 172)
    }

    @Test fun aMainLevelChangeMovesTheWildLevel() {
        val g = game()
        val s = g.state
        s.nextSeed = 123L
        s.main!!.level = 60
        val ev = g.ensureEncounter().single() as GameEvent.Encounter
        assertEquals(Encounters.wildLevel(60, Random(123L)), ev.level)
        assertTrue(ev.level in 57..63)
    }

    @Test fun changingTheMainRefreshesThePrediction() {
        val g = game(state = roster(2))
        val s = g.state
        s.main!!.level = 20; s.mon("t1")!!.level = 90
        s.caught += setOf(16, 54)
        fun rollAt(seed: Long, level: Int): Encounters.Roll { val r = Random(seed); return Encounters.roll(data, s, Encounters.wildLevel(level, r), r) }
        val seed = (0L..20_000L).first { rollAt(it, 20).species != rollAt(it, 90).species }
        s.nextSeed = seed
        s.nextSpecies = null
        assertTrue(g.cycleMain().ok)
        val expected = rollAt(seed, 90)
        assertEquals(expected.species, s.nextSpecies)
        assertEquals(expected.tier, s.nextTier)
    }

    @Test fun encounterOfAKnownSpeciesIsNotNewAndWillBeDefeated() {
        val g = game()
        g.state.caught += 16
        g.state.nextSeed = seedRolling(g.state) { it.species == 16 }
        val ev = g.ensureEncounter().single() as GameEvent.Encounter
        assertEquals(16, ev.species)
        assertFalse(ev.newSpecies); assertFalse(ev.willCatch)
    }

    // ---- balls and escape ----

    private fun seedWithFirstDraw(pred: (Int) -> Boolean): Int =
        (0..1000).first { pred(Random(it).nextInt(100)) }

    @Test fun masterBallAlwaysCatches() {
        val g = game()
        Economy.give(g.state, "master-ball", 2)
        val wild = g.fight(species = 54)
        val ev = g.throwBall("master-ball")
        assertEquals(GameEvent.BallThrow("master-ball", 100, true, 1, 1), ev.of<GameEvent.BallThrow>().single())
        val caught = ev.of<GameEvent.Caught>().single()
        assertEquals("master-ball", caught.ball); assertEquals(54, caught.species); assertTrue(caught.newSpecies)
        assertEquals(1, g.state.count("master-ball"))
        assertEquals(1, g.state.catches)
        assertNotSame(wild, g.state.battle!!.wild) // next wild one is there
        assertEquals(1, ev.of<GameEvent.Encounter>().size)
        assertEquals(1, g.state.team.size)
    }

    @Test fun aBallCatchIsAWinLikeTheAutoCatchWithTheSameXp() {
        val g = game()
        Economy.give(g.state, "master-ball", 1)
        g.fight(species = 54, level = 12)
        val cash = g.state.cash
        val ev = g.throwBall("master-ball")
        val s = g.state
        assertEquals(1, s.catches); assertEquals(1, s.defeats); assertEquals(1, s.catchWins); assertEquals(1, s.main!!.defeats)
        assertEquals(Tuning.TRAINER_XP[Tier.NORMAL]!!, s.trainerXp)
        assertEquals("the win money for a catch (R13 Part C)", cash + Tuning.CASH_PER_WIN, s.cash)
        // PLAN 170: a ball catch gives the Pokémon XP a defeat or an auto catch gives (it used to give none)
        assertEquals(Progression.xpForDefeat(data.sp(54), 12), ev.of<GameEvent.XpGained>().single { it.uid == s.main!!.uid }.xp)
        assertTrue("the XP comes after the catch", ev.indexOfFirst { it is GameEvent.Caught } < ev.indexOfFirst { it is GameEvent.XpGained })
        assertEquals("one win, counted once", 1, StudyStats.summary(s, s.day).battlesWon)
    }

    @Test fun pokeBallSuccessAndFailureFollowTheChance() {
        // success: first draw < 20 (PLAN 171: a Poké Ball catches 20 % of the time)
        val win = game(seed = seedWithFirstDraw { it < 20 })
        Economy.give(win.state, "poke-ball", 3)
        win.fight(species = 54)
        val w = win.throwBall("poke-ball")
        assertEquals(GameEvent.BallThrow("poke-ball", 20, true, 2, 1), w.of<GameEvent.BallThrow>().single())
        assertEquals(1, w.of<GameEvent.Caught>().size)

        // failure: first draw >= 20
        val lose = game(seed = seedWithFirstDraw { it >= 20 })
        Economy.give(lose.state, "poke-ball", 3)
        lose.fight(species = 54, hp = 20)
        val l = lose.throwBall("poke-ball")
        assertEquals(GameEvent.BallThrow("poke-ball", 20, false, 2, 1), l.of<GameEvent.BallThrow>().single())
        assertEquals(listOf(GameEvent.Info("Duckling broke free!")), l.of<GameEvent.Info>())
        assertTrue(l.of<GameEvent.Caught>().isEmpty())
        assertEquals(2, lose.state.count("poke-ball"))
        assertEquals(0, lose.state.catches)
    }

    @Test fun aMissUsesTheBallUpAndTheWildOneRunsAway() {   // PLAN 171
        val g = game(seed = seedWithFirstDraw { it >= 50 })
        Economy.give(g.state, "great-ball", 2)
        val wild = g.fight(species = 54, hp = 20)
        val trainerXp = g.state.trainerXp
        val ev = g.throwBall("great-ball")
        assertEquals(1, g.state.count("great-ball"))
        // the ball breaks (its own animation), the wild one runs away, then the next one comes, as after a defeat
        val i = ev.indexOf(GameEvent.Info("Duckling broke free!"))
        val f = ev.indexOf(GameEvent.Fled("Duckling"))
        val e = ev.indexOfFirst { it is GameEvent.Encounter }
        assertTrue("$ev", i in 0 until f && f < e)
        assertNotSame(wild, g.state.battle!!.wild)
        // no win: no XP, no defeat count, no trainer XP, no money
        assertTrue(ev.of<GameEvent.XpGained>().isEmpty() && ev.of<GameEvent.Defeated>().isEmpty() && ev.of<GameEvent.Cash>().isEmpty())
        assertEquals(0, g.state.defeats); assertEquals(0, g.state.main!!.defeats); assertEquals(0, g.state.catches)
        assertEquals(trainerXp, g.state.trainerXp)
        assertEquals(0, StudyStats.summary(g.state, g.state.day).battlesWon)
    }

    @Test fun anOldAnkimonBallThrowsAsAGreatBallWithNoTypeBonus() {   // PLAN 171
        val g = game()
        Economy.give(g.state, "net-ball")
        g.fight(species = 54) // water: the Net Ball's old bonus is gone
        assertEquals(50, g.throwBall("net-ball").of<GameEvent.BallThrow>().single().chance)
    }

    @Test fun throwWithoutABallOrABattleDoesNothing() {
        val g = game()
        g.fight()
        assertTrue(g.throwBall("poke-ball").isEmpty())
        Economy.give(g.state, "potion")
        assertTrue(g.throwBall("potion").isEmpty())
        assertEquals(1, g.state.count("potion"))
        Economy.give(g.state, "poke-ball")
        g.state.battle = null
        assertTrue(g.throwBall("poke-ball").isEmpty())
        assertEquals(1, g.state.count("poke-ball"))
    }

    @Test fun escapeItemMakesTheWildOneLeave() {
        val g = game()
        Economy.give(g.state, "poke-doll")
        val wild = g.fight(species = 54)
        val ev = g.escape("poke-doll")
        assertEquals(GameEvent.Fled("Duckling"), ev.of<GameEvent.Fled>().single())
        assertEquals(1, ev.of<GameEvent.Encounter>().size)
        assertEquals(0, g.state.count("poke-doll"))
        assertNotSame(wild, g.state.battle!!.wild)
        assertEquals(0, g.state.catches + g.state.defeats)
    }

    @Test fun escapeNeedsTheItemAndAnEscapeItem() {
        val g = game()
        val wild = g.fight()
        assertTrue(g.escape("poke-doll").isEmpty())
        Economy.give(g.state, "potion")
        assertTrue(g.escape("potion").isEmpty())
        assertSame(wild, g.state.battle!!.wild)
    }

    // ---- team ----

    @Test fun setMainFromTheBoxTakesAFreePlace() {
        val g = game(state = roster(3, 1))
        val r = g.setMain("b3")
        assertTrue(r.ok)
        assertEquals(listOf("b3", "t0", "t1", "t2"), g.state.team)
        assertEquals(listOf<GameEvent>(GameEvent.MainChanged("b3", g.state.mon("b3")!!.name)), r.events)
    }

    @Test fun setMainFromTheBoxWithAFullTeamSendsTheLastOneToTheBox() {
        val g = game(state = roster(6, 1))
        val last = g.state.mon("t5")!!
        val r = g.setMain("b6")
        assertTrue(r.ok)
        assertEquals(listOf("b6", "t0", "t1", "t2", "t3", "t4"), g.state.team)
        assertTrue(last in g.state.boxMons())
        assertEquals(6, g.state.team.size)
    }

    @Test fun setMainOfATeamMemberMovesItToTheFront() {
        val g = game(state = roster(4))
        assertTrue(g.setMain("t2").ok)
        assertEquals(listOf("t2", "t0", "t1", "t3"), g.state.team)
        val again = g.setMain("t2")
        assertTrue(again.ok && again.events.isEmpty())
        assertFalse(g.setMain("nope").ok)
    }

    @Test fun changingTheMainMidFightResetsOnlyOurSideAndKeepsTheNewMainsHp() {
        val g = game(state = roster(3))
        val wild = g.fight()
        g.state.battle!!.player.boosts[0] = 2
        g.state.battle!!.player.status = "par"
        g.state.battle!!.enemy.boosts[1] = -1
        g.state.battle!!.tally = 10; g.state.battle!!.answers = 1
        g.state.mon("t1")!!.hp = 5
        g.state.main!!.hp = 0 // R10 W10: a switch away from a fainted main is free; a costly one (the wild hit) is in SwitchHitTest
        val r = g.cycleMain()
        assertTrue(r.ok)
        assertEquals("t1", g.state.main!!.uid)
        assertEquals(5, g.state.main!!.hp)
        assertEquals(listOf(GameEvent.MainChanged("t1", g.state.main!!.name)), r.events)
        val b = g.state.battle!!
        assertSame(wild, b.wild)
        assertTrue(b.player.boosts.all { it == 0 } && b.player.status == null)
        assertEquals(-1, b.enemy.boosts[1])
        assertEquals(1, b.answers); assertEquals(10, b.tally) // the charge carries on
    }

    @Test fun changingTheMainMidFightDropsTheChargedMoves() {
        val g = game(state = roster(3))
        g.fight()
        g.state.battle!!.player.charging = "solarbeam"
        g.state.battle!!.enemy.charging = "fly"
        assertTrue(g.setMain("t2").ok)
        assertNull(g.state.battle!!.player.charging)
        assertEquals("fly", g.state.battle!!.enemy.charging) // the wild one keeps its charge (this switch is free: nothing answered yet; a costly one releases it, SwitchHitTest)
        g.state.battle!!.player.charging = "dig"
        assertTrue(g.cycleMain(cardShowing = false).ok) // PLAN 206: with a card showing the cycle only queues the switch (SwitchQueueTest)
        assertNull(g.state.battle!!.player.charging)
    }

    @Test fun cycleMainRotatesTheFirstThree() {
        val g = game(state = roster(4))
        assertTrue(g.cycleMain().ok)
        assertEquals(listOf("t1", "t2", "t0", "t3"), g.state.team)
        assertTrue(g.cycleMain().ok)
        assertEquals(listOf("t2", "t0", "t1", "t3"), g.state.team)
        assertTrue(g.cycleMain().ok)
        assertEquals(listOf("t0", "t1", "t2", "t3"), g.state.team)

        val two = game(state = roster(2))
        two.cycleMain()
        assertEquals(listOf("t1", "t0"), two.state.team)
        val one = game(state = roster(1))
        assertFalse(one.cycleMain().ok)
        assertEquals(listOf("t0"), one.state.team)
    }

    @Test fun cycleMainBlockerSaysWhyOrIsNull() {
        assertEquals("Needs 2+ Pokémon in your team", game(state = roster(1)).cycleMainBlocker())
        assertNull(game(state = roster(2)).cycleMainBlocker())
        assertNull(game(state = roster(6, 3)).cycleMainBlocker())
        val noTeam = roster(2); noTeam.team.clear()
        assertEquals("Pick your first Pokémon first", game(state = noTeam).cycleMainBlocker())
    }

    @Test fun cycleMainFailsWithTheBlockerReasonAndChangesNothing() {
        val one = game(state = roster(1))
        val r = one.cycleMain()
        assertFalse(r.ok)
        assertEquals("Needs 2+ Pokémon in your team", r.message)
        assertTrue(r.events.isEmpty())
        assertEquals(listOf("t0"), one.state.team)
        val none = roster(2); none.team.clear()
        val g = game(state = none)
        assertEquals("Pick your first Pokémon first", g.cycleMain().message)
        assertTrue(g.state.team.isEmpty())
    }

    @Test fun cycleMainWithTwoSwapsAndNamesTheNewMain() {
        val g = game(state = roster(2))
        val r = g.cycleMain()
        assertTrue(r.ok)
        assertEquals(listOf("t1", "t0"), g.state.team)
        assertEquals("${g.state.mon("t1")!!.name} is your main now.", r.message)
        assertEquals(listOf<GameEvent>(GameEvent.MainChanged("t1", g.state.mon("t1")!!.name)), r.events)
    }

    @Test fun cycleMainWithFourGivesBCAD() {
        val g = game(state = roster(4))
        assertTrue(g.cycleMain().ok)
        assertEquals(listOf("t1", "t2", "t0", "t3"), g.state.team)
    }

    @Test fun cycleMainBetweenFightsHasNoBattleToReset() {
        val g = game(state = roster(3))
        g.state.battle = null
        val r = g.cycleMain()
        assertTrue(r.ok)
        assertNull(g.state.battle)
        assertEquals(1, r.events.of<GameEvent.MainChanged>().size)
    }

    @Test fun moveInTeamShiftsAndClamps() {
        val g = game(state = roster(4))
        val a = g.moveInTeam("t2", -1)
        assertTrue(a.ok && a.events.isEmpty()) // the main did not change
        assertEquals(listOf("t0", "t2", "t1", "t3"), g.state.team)
        val b = g.moveInTeam("t2", -1)
        assertTrue(b.ok)
        assertEquals(listOf("t2", "t0", "t1", "t3"), g.state.team)
        assertEquals(1, b.events.size) // now t2 is the main
        assertFalse(g.moveInTeam("t2", -1).ok)
        assertFalse(g.moveInTeam("t3", 1).ok)
        assertTrue(g.moveInTeam("t2", 9).ok)
        assertEquals(listOf("t0", "t1", "t3", "t2"), g.state.team)
        assertFalse(g.moveInTeam("nope", 1).ok)
    }

    @Test fun toBoxAndToTeamKeepTheTeamBetweenOneAndSix() {
        val g = game(state = roster(2, 6))
        assertTrue(g.toBox("t1").ok)
        assertEquals(listOf("t0"), g.state.team)
        assertFalse(g.toBox("t0").ok) // the last member stays
        assertFalse(g.toBox("t1").ok) // not in the team any more
        for (i in 2..6) assertTrue(g.toTeam("b$i").ok) // fills up
        assertEquals(6, g.state.team.size)
        assertFalse(g.toTeam("b7").ok) // full
        assertFalse(g.toTeam("t0").ok) // already in
        assertFalse(g.toTeam("nope").ok)
    }

    @Test fun swapIntoTeamTakesTheSlotAndSendsTheOldOneToTheBox() {
        val g = game(state = roster(6, 8))
        val r = g.swapIntoTeam("b6", "t3")
        assertTrue(r.ok); assertTrue(r.events.isEmpty()) // the main did not change
        assertEquals(listOf("t0", "t1", "t2", "b6", "t4", "t5"), g.state.team)
        assertNotNull(g.state.mon("t3")) // nothing lost, it is in the box now
        assertEquals(14, g.state.mons.size)
        assertTrue("t3" !in g.state.team)
    }

    @Test fun swapIntoTeamForTheMainMakesTheNewOneMain() {
        val g = game(state = roster(6, 8))
        g.fight()
        g.state.battle!!.player.boosts[2] = 3
        g.state.battle!!.rounds = 1 // R11-135: a round was played, so the switch costs the hit
        val r = g.swapIntoTeam("b6", "t0")
        assertTrue(r.ok)
        assertEquals(listOf("b6", "t1", "t2", "t3", "t4", "t5"), g.state.team)
        assertEquals(GameEvent.MainChanged("b6", g.state.mon("b6")!!.name), r.events[0])
        assertTrue(r.events[1] is GameEvent.SwitchHit) // R10 W10: switching in a fight costs a hit (the details are in SwitchHitTest)
        assertEquals(0, g.state.battle!!.player.boosts[2])
    }

    @Test fun swapIntoTeamRefusesWhatDoesNotFit() {
        val g = game(state = roster(6, 8))
        val before = g.state.team.toList()
        assertFalse(g.swapIntoTeam("nope", "t1").ok)  // unknown in
        assertFalse(g.swapIntoTeam("b6", "nope").ok)  // unknown out
        assertFalse(g.swapIntoTeam("t2", "t1").ok)    // already in the team
        assertFalse(g.swapIntoTeam("b6", "b7").ok)    // out is not in the team
        assertEquals(before, g.state.team)
    }

    @Test fun boxingTheMainPromotesTheNextOne() {
        val g = game(state = roster(3))
        val wild = g.fight()
        g.state.battle!!.player.boosts[2] = 3
        g.state.battle!!.rounds = 1 // R11-135: a round was played, so the switch costs the hit
        val r = g.toBox("t0")
        assertEquals(listOf("t1", "t2"), g.state.team)
        assertEquals(GameEvent.MainChanged("t1", g.state.mon("t1")!!.name), r.events[0])
        assertTrue(r.events[1] is GameEvent.SwitchHit) // R10 W10: switching in a fight costs a hit (the details are in SwitchHitTest)
        assertEquals(0, g.state.battle!!.player.boosts[2])
        assertSame(wild, g.state.battle!!.wild)
    }

    @Test fun xpShareCanBeSetAndCleared() {
        val g = game(state = roster(2))
        assertTrue(g.setXpShare("t1").ok)
        assertEquals("t1", g.state.settings.xpShareUid)
        assertFalse(g.setXpShare("nope").ok)
        assertEquals("t1", g.state.settings.xpShareUid)
        assertTrue(g.setXpShare(null).ok)
        assertNull(g.state.settings.xpShareUid)
    }

    @Test fun releaseRules() {
        val only = game(state = roster(1))
        assertFalse(only.release("t0").ok)
        assertEquals(1, only.state.mons.size)

        val g = game(state = roster(1, 2))
        assertFalse(g.release("t0").ok) // the only team member while others sit in the box
        assertTrue(g.release("b1").ok)
        assertEquals(2, g.state.mons.size)
        assertNull(g.state.mon("b1"))

        val h = game(state = roster(3))
        h.state.mon("t1")!!.heldItem = "lucky-egg"
        h.state.settings.xpShareUid = "t1"
        assertTrue(h.release("t1").ok)
        assertEquals(1, h.state.count("lucky-egg"))
        assertNull(h.state.settings.xpShareUid)
        assertEquals(listOf("t0", "t2"), h.state.team)
        assertFalse(h.release("t1").ok) // gone
    }

    @Test fun releasingTheMainHandsOverToTheNextOne() {
        val g = game(state = roster(3))
        g.fight()
        val r = g.release("t0")
        assertTrue(r.ok)
        assertEquals("t1", g.state.main!!.uid)
        assertEquals(1, r.events.filterIsInstance<GameEvent.MainChanged>().size)
        assertTrue(25 in g.state.caught) // the Pokédex keeps it
    }

    // ---- rename ----

    @Test fun renameCleansAndCutsTheText() {
        val g = game()
        val r = g.rename("main", "  Zappy \t  the\u0007  Great   and Powerful Mouse  ")
        assertTrue(r.ok)
        val n = g.state.main!!.nickname!!
        assertTrue(n.length <= Tuning.NICKNAME_MAX)
        assertEquals("Zappy the Great and", n)
        assertEquals("Sparkmouse is now called Zappy the Great and.", r.message)
        assertEquals("Zappy the Great and", g.state.main!!.shownName)
    }

    @Test fun renameBlankOrSpeciesNameRemovesTheNickname() {
        val g = game()
        g.rename("main", "Zap")
        assertEquals("Zap", g.state.main!!.shownName)
        assertEquals("Sparkmouse's nickname was removed.", g.rename("main", "   ").message)
        assertNull(g.state.main!!.nickname)
        g.rename("main", "Zap")
        assertTrue(g.rename("main", "sPARKmouse").ok)
        assertNull(g.state.main!!.nickname); assertEquals("Sparkmouse", g.state.main!!.shownName)
        assertFalse(g.rename("ghost", "X").ok)
    }

    @Test fun renameDoesNotTouchTheBattle() {
        val g = game()
        val w = g.fight()
        val hp = w.hp; val answers = g.state.battle!!.answers
        g.rename("main", "Zap")
        assertEquals(hp, g.state.battle!!.wild.hp); assertEquals(answers, g.state.battle!!.answers)
    }

    // ---- the big one ----

    private fun checkInvariants(g: Game, step: Int) {
        val s = g.state
        val where = "after answer $step"
        assertNotNull("main $where", s.main)
        assertTrue("team size ${s.team.size} $where", s.team.size in 1..6)
        assertEquals("team uids unique $where", s.team.size, s.team.toSet().size)
        assertEquals("mon uids unique $where", s.mons.size, s.mons.map { it.uid }.toSet().size)
        assertTrue("team in mons $where", s.team.all { s.mon(it) != null })
        for (m in s.mons) {
            val max = maxHp(m)
            assertTrue("${m.name} hp ${m.hp}/$max $where", m.hp in 0..max)
            assertTrue("${m.name} level ${m.level} $where", m.level in 1..100)
            assertTrue("${m.name} moves ${m.moves} $where", m.moves.size in 1..4 && m.moves.toSet().size == m.moves.size)
            assertTrue("${m.name} species caught $where", m.species in s.caught && m.species in s.seen)
            assertTrue("${m.name} xp ${m.xp} $where", m.xp >= 0)
        }
        assertTrue("main hp $where", s.main!!.hp > 0)
        assertNotNull("a battle exists $where", s.battle)
        val b = s.battle!!
        assertTrue("wild hp $where", b.wild.hp in 1..maxHp(b.wild))
        assertTrue("wild moves $where", b.wild.moves.size in 1..4)
        assertTrue("tally $where", b.answers in 0..3 && b.tally >= 0)
        assertEquals("buttons vs answers $where", b.answers, b.buttons.size)
        assertTrue("cash ${s.cash} $where", s.cash >= 0 && s.cashToday >= 0 && s.cashFracDen >= 1 && s.cashFracNum in 0 until s.cashFracDen)
        assertTrue("bag $where", s.bag.values.all { it > 0 })
        assertEquals("buttons vs reviews $where", s.reviewsToday, s.buttonsToday.sum())
        assertTrue("counters $where", s.reviewsToday >= 0 && s.totalReviews >= s.reviewsToday)
        assertTrue("xpShare owned $where", s.settings.xpShareUid == null || s.mon(s.settings.xpShareUid) != null)
        assertTrue("trainer $where", s.trainerLevel >= 1 && s.trainerXp in 0 until Trainer.xpForNext(s.trainerLevel))
    }

    /**
     * Random buttons, undo/redo, day jumps and (with chance [tapsPerMille] after each answer) a random optional tap,
     * with every invariant checked after every answer. Ends with a save round trip and 100 more answers on the loaded copy.
     * Returns the number of DayChanged events.
     */
    private fun simulate(state: GameState, seed: Int, answers: Int, tapsPerMille: Int, againPercent: Int = 15): Int {
        val g = game(seed = seed, state = state)
        val r = Random(seed + 1000)
        g.tick()
        Economy.give(state, "master-ball", 5); Economy.give(state, "poke-ball", 60); Economy.give(state, "poke-doll", 5)
        var counted = state.totalReviews
        var days = 0
        repeat(answers) { i ->
            now += 4_000
            if (i > 0 && i % 140 == 0) now += (5 + r.nextInt(30)) * 3_600_000L
            val card = (1 + r.nextInt(40)).toLong()
            val x = r.nextInt(100)
            val button = when {
                x < againPercent -> 1
                x < againPercent + 8 -> 2
                x < 80 -> 3
                x < 97 -> 4
                x == 97 -> 0
                else -> 9 // out of range
            }
            if (r.nextInt(100) < 4) g.onUndo(card, 1 + r.nextInt(2))
            val ev = g.onAnswer(Answer(card, button, now, catchUp = r.nextInt(25) == 0))
            counted += ev.count { it is GameEvent.Counted }
            days += ev.count { it is GameEvent.DayChanged }

            if (r.nextInt(1000) < tapsPerMille) {
                val ids = state.mons.map { it.uid }
                fun any() = ids[r.nextInt(ids.size)]
                when (r.nextInt(11)) {
                    0 -> g.cycleMain()
                    1 -> g.throwBall(listOf("poke-ball", "master-ball", "great-ball")[r.nextInt(3)])
                    2 -> g.escape("poke-doll")
                    3 -> g.setMain(any())
                    4 -> g.toBox(any())
                    5 -> g.toTeam(any())
                    6 -> g.moveInTeam(any(), r.nextInt(5) - 2)
                    7 -> g.setXpShare(if (r.nextBoolean()) any() else null)
                    8 -> if (ids.size > 4) g.release(any())
                    9 -> g.setOverride(listOf(null, Outcome.CATCH, Outcome.DEFEAT)[r.nextInt(3)])
                    else -> Economy.give(state, "poke-ball", 5)
                }
            }
            checkInvariants(g, i)
        }
        assertEquals(counted, state.totalReviews)

        val json = SaveCodec.encode(state)
        val back = SaveCodec.decode(json)
        assertEquals(json, SaveCodec.encode(back))
        val g2 = Game(data, back, Random(9)) { now }
        repeat(100) { i ->
            now += 4_000
            g2.onAnswer(Answer((1 + r.nextInt(40)).toLong(), 3, now))
            checkInvariants(g2, answers + i)
        }
        return days
    }

    @Test fun threeThousandRandomAnswersKeepEveryInvariant() {
        val state = GameState(createdAt = 1L)
        Game(data, state, Random(1)) { now }.also { it.tick(); it.pickStarter(1) }
        val start = state.day
        val days = simulate(state, seed = 42, answers = 3000, tapsPerMille = 110)
        assertTrue("days changed: $days", days >= 8 && state.day != start)
        assertTrue("catches ${state.catches}", state.catches > 0)
        assertTrue("defeats ${state.defeats}", state.defeats > 0)
        assertTrue("cash ${state.cash}", state.cash > 0)
        assertTrue("badges ${state.badges}", state.badges.isNotEmpty())
    }

    @Test fun aSteadyMainLevelsUpEvolvesAndLearns() {
        val state = GameState(createdAt = 1L)
        Game(data, state, Random(2)) { now }.also { it.tick(); it.pickStarter(1) }
        simulate(state, seed = 5, answers = 3000, tapsPerMille = 0, againPercent = 5)
        assertTrue("main level ${state.main!!.level}", state.main!!.level >= 16)
        assertTrue("evolutions ${state.evolutions}", state.evolutions >= 1)
        assertTrue("defeats ${state.defeats}", state.defeats > 100)
    }

    @Test fun aHighLevelMainMeetsTheRareTiersAndKeepsTheInvariants() {
        val state = TestData.state(data, 3, 78, listOf("tackle", "absorb", "doubleedge"))
        state.trainerLevel = 15
        val g = Game(data, state, Random(3)) { now }
        g.tick()
        val days = simulate(state, seed = 11, answers = 2500, tapsPerMille = 20, againPercent = 8)
        assertTrue(days >= 5)
        assertTrue("seen ${state.seen}", state.seen.size >= 5)
    }

    /**
     * Dev check with the real game data: skipped unless env ANKIGO_REAL_DATA points at a folder with the 9 source files
     * (see RealDataCheck). The files never go into the repo.
     */
    @Test fun realDataSimulation() {
        val dir = System.getenv("ANKIGO_REAL_DATA")?.takeIf { it.isNotBlank() }?.let { File(it) }
        assumeTrue("ANKIGO_REAL_DATA not set", dir != null)
        fun text(vararg names: String) = names.map { File(dir!!, it) }.first { it.isFile }.readText()
        data = Parsers.build(Parsers.Sources(
            pokedexJson = text("pokedex.json"), movesJson = text("moves.json"), learnsetsJson = text("learnsets.json"),
            speciesCsv = text("pokemon_species.csv", "species.csv"), pokemonCsv = text("pokemon.csv"), statsCsv = text("pokemon_stats.csv"),
            evolutionCsv = text("pokemon_evolution.csv"), experienceCsv = text("experience.csv"), itemsCsv = text("items.csv"),
        ))
        for ((i, starter) in listOf(1, 4, 7, 152, 810, 906).withIndex()) {
            now = utc(2026, 10, 13, 12, 0)
            val state = GameState(createdAt = 1L)
            Game(data, state, Random(i)) { now }.also { it.tick(); it.pickStarter(starter) }
            simulate(state, seed = i + 1, answers = 4000, tapsPerMille = 30, againPercent = 5 + 4 * i)
            println("REAL sim #$i starter=$starter: catches=${state.catches} defeats=${state.defeats} evolutions=${state.evolutions} " +
                "main=${state.main!!.name} L${state.main!!.level} seen=${state.seen.size} caught=${state.caught.size}")
        }
    }
}
