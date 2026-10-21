package com.husarp.ankigo.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class EconomyTest {
    private val data = TestData.world()
    private fun state() = TestData.state(data)
    private fun events() = mutableListOf<GameEvent>()

    /** The world plus one species that can learn many TMs, so the daily TM stock has something to sample. */
    private fun shopData(): GameData {
        val tmMon = TestData.species(900, "Tmmon", tmMoves = setOf("thunderbolt", "watergun", "ember", "vinewhip", "earthquake", "shadowball", "nomove"))
        return GameData(data.species + (900 to tmMon), data.moves, data.growth, data.itemPrices)
    }

    // ---- names and pools ----

    @Test fun names() {
        assertEquals("Poké Ball", Items.name("poke-ball"))
        assertEquals("Poké Doll", Items.name("poke-doll"))
        assertEquals("King's Rock", Items.name("kings-rock"))
        assertEquals("Up-Grade", Items.name("up-grade"))
        assertEquals("Deep Sea Tooth", Items.name("deep-sea-tooth"))
        assertEquals("Thunder Stone", Items.name("thunder-stone"))
        assertEquals("TM Thunderbolt", Items.tmName(data, "thunderbolt"))
        assertEquals("TM zzz", Items.tmName(data, "zzz"))
    }

    @Test fun catalogueShape() {
        assertEquals(14, Items.ALL.values.count { it.kind == ItemKind.BALL })
        assertEquals(13, Items.ALL.values.count { it.kind == ItemKind.HEAL })
        assertEquals(32, Items.ALL.values.count { it.kind == ItemKind.EVOLUTION })
        assertEquals(11, Items.ALL.values.count { it.kind == ItemKind.HELD })
        assertEquals(2, Items.ALL.values.count { it.kind == ItemKind.ESCAPE })
        assertEquals(11, Items.ALL.values.count { it.kind == ItemKind.FOSSIL })
        assertEquals(60, Items.def("super-potion")!!.heal)
        assertEquals(Int.MAX_VALUE, Items.def("max-potion")!!.heal)
        assertEquals(Int.MAX_VALUE, Items.def("full-restore")!!.heal)
        assertEquals(140, Items.def("dome-fossil")!!.fossil)
        assertEquals("Catch chance 50 %. If it fails, the wild Pokémon runs away.", Items.def("great-ball")!!.note)   // PLAN 171
        assertEquals("Catch chance 100 %: it always catches.", Items.def("master-ball")!!.note)
        assertEquals("Throws as a Great Ball: catch chance 50 %. If it fails, the wild Pokémon runs away.", Items.def("dive-ball")!!.note)
        assertTrue(Items.ALL.values.filter { it.kind == ItemKind.BALL && it.id != "master-ball" }.all { it.note.endsWith(Items.MISS_RULE) })
        assertEquals("Heals 60 HP", Items.def("super-potion")!!.note)
        assertTrue(Items.ALL.values.all { it.note.isNotBlank() })
    }

    @Test fun shopPoolSkipsFreeItemsAndKeepsBalls() {
        val pool = Items.shopPool(data)
        assertFalse("master-ball" in pool)
        assertFalse("an old Ankimon ball is never sold, whatever PokeAPI says", "net-ball" in pool)
        assertTrue("ultra-ball" in pool)
        assertFalse("rare-candy" in pool) // not in the catalogue
        assertFalse("fire-stone" in pool) // no price in the fixtures
        assertTrue("poke-ball" in pool && "great-ball" in pool && "thunder-stone" in pool)
        assertEquals(pool.sorted(), pool)
    }

    @Test fun dropPoolHasNoBalls() {
        val pool = Items.dropPool()
        assertTrue(pool.none { Items.def(it)!!.kind == ItemKind.BALL })
        assertTrue("potion" in pool && "master-ball" !in pool)
        assertEquals(pool.sorted(), pool)
    }

    // ---- cash and drops ----

    private fun cash(ev: List<GameEvent>) = ev.filterIsInstance<GameEvent.Cash>()

    @Test fun everyAnswerPaysFive() {
        val s = state()
        val ev = events()
        for (n in 1..3) { s.reviewsToday = n; Economy.onReview(data, s, TestData.rng(), ev) }
        assertEquals(15, s.cash)
        assertEquals(15, s.cashToday)
        assertEquals(listOf(5, 10, 15), cash(ev).map { it.total })
        assertEquals(listOf(5, 5, 5), cash(ev).map { it.amount })
        assertTrue(cash(ev).all { it.kind == CashKind.ANSWER })
    }

    // ---- PLAN 125: the linear money curve (the old hard 500 ₽ a day is gone) ----

    /** [n] more counted answers; returns the Cash events they made. */
    private fun answers(s: GameState, n: Int): List<GameEvent.Cash> {
        val ev = events()
        repeat(n) { s.reviewsToday++; Economy.onReview(data, s, TestData.rng(), ev) }
        return cash(ev)
    }

    @Test fun theAnswersPerPaymentGrowByOneEveryTier() {
        assertEquals(1, Economy.answersPerPayment(0))
        assertEquals(1, Economy.answersPerPayment(495))
        assertEquals(2, Economy.answersPerPayment(500))
        assertEquals(2, Economy.answersPerPayment(995))
        assertEquals(3, Economy.answersPerPayment(1000))
        assertEquals(4, Economy.answersPerPayment(1500))
        assertEquals(11, Economy.answersPerPayment(5000))
        assertEquals(1, Economy.answersPerPayment(-5))
    }

    @Test fun eachNext500IsHarderToEarn() {
        val s = state()
        assertEquals("the first 500 ₽: every answer", 100, answers(s, 100).size)
        assertEquals(500, s.cash); assertEquals(500, s.cashToday)
        assertEquals("the next 500 ₽: every 2nd answer", 100, answers(s, 200).size)
        assertEquals(1000, s.cashToday)
        assertEquals("then every 3rd", 100, answers(s, 300).size)
        assertEquals(1500, s.cashToday)
        val fourth = answers(s, 8)
        assertEquals("then every 4th", 2, fourth.size)
        assertTrue(fourth.all { it.amount == Tuning.CASH_PER_ANSWER && it.kind == CashKind.ANSWER })
        assertEquals(1510, s.cash)
    }

    @Test fun theTierBoundaryIsExact() {
        val s = state()
        s.cashToday = 495
        assertEquals(GameEvent.Cash(5, 5, CashKind.ANSWER), answers(s, 1).single())   // the last payment of the first tier
        assertEquals(500, s.cashToday)
        assertTrue("the second tier waits for a 2nd answer", answers(s, 1).isEmpty())
        assertEquals(1, s.cashWait)
        assertEquals(GameEvent.Cash(5, 10, CashKind.ANSWER), answers(s, 1).single())
        assertEquals(0, s.cashWait)
    }

    @Test fun anOldSaveDayCarriesOnFromItsCashSoFar() {
        val s = state()
        s.cashToday = 380 // a day of the old rule (it never went past 500)
        s.reviewsToday = 90
        val paid = answers(s, 110)
        assertEquals("24 answers to 500, then every 2nd of the other 86", 24 + 43, paid.size)
        assertEquals(335, s.cash)
        assertEquals(715, s.cashToday)
    }

    @Test fun amuletCoinDoublesEveryPaymentAtEveryTier() {
        val s = state()
        s.main!!.heldItem = "amulet-coin"
        assertEquals(10, answers(s, 1).single().amount)
        assertEquals("the curve counts the money before the ×2", 5, s.cashToday)
        s.cashToday = 500
        s.main!!.heldItem = "luck-incense"
        val second = answers(s, 4)
        assertEquals(listOf(10, 10), second.map { it.amount })   // every 2nd answer, doubled
        assertEquals(30, s.cash); assertEquals(510, s.cashToday)
        s.main!!.heldItem = "everstone" // another held item pays the plain 5
        assertEquals(listOf(5), answers(s, 2).map { it.amount })
        // over a whole day the item doubles the money exactly: 600 answers pay 1500 ₽ without it, 3000 with it
        val plain = state(); answers(plain, 600)
        val coin = state(); coin.main!!.heldItem = "amulet-coin"; answers(coin, 600)
        assertEquals(1500, plain.cash); assertEquals(3000, coin.cash)
    }

    @Test fun aNewDayStartsTheCurveAgain() {
        val g = Game(data, state(), TestData.rng(), { 1_760_000_000_000L })
        g.state.cashToday = 1234; g.state.cashWait = 2
        g.state.day = "2000-01-01"
        g.tick()
        assertEquals(0, g.state.cashToday); assertEquals(0, g.state.cashWait)
    }

    @Test fun dailyBonusGrowsWithTheStreakBeforeToday() {
        assertEquals(50, Economy.dailyBonus(0))
        assertEquals(55, Economy.dailyBonus(1))
        assertEquals(80, Economy.dailyBonus(6))
        assertEquals(100, Economy.dailyBonus(10))
        assertEquals(100, Economy.dailyBonus(11))
        assertEquals(100, Economy.dailyBonus(400))
        assertEquals(50, Economy.dailyBonus(-3))
    }

    @Test fun dailyBonusIsOutsideTheCurveAndNeverDoubled() {
        val s = state()
        s.main!!.heldItem = "amulet-coin"
        s.cashToday = 500 // cap reached
        val ev = events()
        Economy.payDailyBonus(s, 6, ev)
        assertEquals(80, s.cash)
        assertEquals(500, s.cashToday)
        assertEquals(GameEvent.Cash(80, 80, CashKind.DAILY_BONUS), cash(ev).single())
    }

    @Test fun dailyBonusText() {
        assertEquals("Daily goal bonus: +75 ₽ (6-day streak)", Economy.dailyBonusText(75, 6))
        assertEquals("Daily goal bonus: +50 ₽", Economy.dailyBonusText(50, 1))
        assertEquals("Daily goal bonus: +100 ₽ (14-day streak)", Economy.dailyBonusText(100, 14))
    }

    @Test fun countdownRollsThenDecrements() {
        val s = state()
        assertEquals(0, s.itemCountdown)
        s.reviewsToday = 1
        Economy.onReview(data, s, Random(5), events())
        val first = s.itemCountdown
        assertTrue("rolled within 2..384 after one decrement: $first", first in 2..384)
        s.reviewsToday = 2
        Economy.onReview(data, s, Random(5), events())
        assertEquals(first - 1, s.itemCountdown)
        assertTrue(s.bag.isEmpty())
    }

    @Test fun countdownDropsAnItem() {
        val s = state()
        s.itemCountdown = 1
        s.reviewsToday = 1
        val ev = events()
        Economy.onReview(data, s, Random(7), ev)
        val found = ev.filterIsInstance<GameEvent.ItemFound>().single()
        assertEquals(1, s.bag.values.sum())
        assertEquals(1, s.count(found.item))
        assertEquals(1, found.count)
        assertTrue(found.item in Items.dropPool())
        assertTrue(6 in s.badges)
        assertEquals(1, ev.filterIsInstance<GameEvent.BadgeEarned>().count { it.id == 6 })
        assertTrue(s.itemCountdown in 3..385)
        // same seed, same item
        val s2 = state(); s2.itemCountdown = 1; s2.reviewsToday = 1
        Economy.onReview(data, s2, Random(7), events())
        assertEquals(s.bag, s2.bag)
    }

    @Test fun secondDropGivesBadgeOnlyOnce() {
        val s = state()
        val ev = events()
        repeat(2) { s.itemCountdown = 1; s.reviewsToday++; Economy.onReview(data, s, Random(it), ev) }
        assertEquals(2, s.bag.values.sum())
        assertEquals(1, ev.filterIsInstance<GameEvent.BadgeEarned>().size)
        assertEquals(2, ev.filterIsInstance<GameEvent.ItemFound>().size)
    }

    @Test fun takeRemovesWhenEmpty() {
        val s = state()
        assertFalse(Economy.take(s, "potion"))
        Economy.give(s, "potion", 2)
        assertTrue(Economy.take(s, "potion"))
        assertEquals(1, s.count("potion"))
        assertTrue(Economy.take(s, "potion"))
        assertFalse("potion" in s.bag)
    }

    // ---- shop ----

    @Test fun slotsByTrainerLevel() {
        assertEquals(3, Shop.slots(1))
        assertEquals(4, Shop.slots(5))
        assertEquals(5, Shop.slots(10))
    }

    @Test fun tmPoolIsUnionOfKnownMoves() {
        val d = shopData()
        val pool = Shop.tmPool(d)
        assertEquals(listOf("bodyslam", "earthquake", "ember", "shadowball", "thunderbolt", "vinewhip", "watergun"), pool) // "nomove" dropped
    }

    @Test fun ensureTodayIsDeterministicPerDay() {
        val d = shopData()
        val a = state(); val b = state()
        Shop.ensureToday(d, a); Shop.ensureToday(d, b)
        assertEquals("2026-10-13", a.shopDay)
        assertEquals(3, drawn(a).size); assertEquals(3, a.shopTms.size)
        assertEquals(a.shopItems, b.shopItems)
        assertEquals(a.shopTms, b.shopTms)
        assertEquals(drawn(a).toSet().size, 3)
        assertTrue(a.shopItems.all { it in Items.shopPool(d) })
        assertTrue(a.shopTms.all { it in Shop.tmPool(d) })

        val before = a.shopItems.toList()
        Shop.ensureToday(d, a) // same day: unchanged even if the generator state differs
        assertEquals(before, a.shopItems)

        val other = state(); other.day = "2026-10-14"
        Shop.ensureToday(d, other)
        assertEquals("2026-10-14", other.shopDay)
        assertTrue(other.shopItems != a.shopItems || other.shopTms != a.shopTms)
    }

    /** Today's drawn items: the stock without the Ultra Ball that its rare days add on top (PLAN 171). */
    private fun drawn(s: GameState) = s.shopItems.filter { it != "ultra-ball" }

    @Test fun ensureTodayRestocksOnNewDayAndUsesSlots() {
        val d = shopData()
        val s = state()
        s.trainerLevel = 10
        Shop.ensureToday(d, s)
        assertEquals(5, drawn(s).size); assertEquals(5, s.shopTms.size)
        s.day = "2026-10-14"
        Shop.ensureToday(d, s)
        assertEquals("2026-10-14", s.shopDay)
        assertEquals(5, drawn(s).size)
    }

    @Test fun buyItem() {
        val s = state()
        s.shopDay = s.day; s.shopItems += listOf("poke-ball", "great-ball")
        assertEquals(ActionResult(false, "Not in today's shop"), Shop.buy(data, s, "ultra-ball"))
        s.cash = 199
        val no = Shop.buy(data, s, "poke-ball")
        assertFalse(no.ok); assertEquals("Not enough money (need 200 ₽)", no.message)   // PLAN 171 / R13 Part C: 200 ₽
        assertEquals(199, s.cash); assertEquals(0, s.count("poke-ball"))
        s.cash = 1000
        val ok = Shop.buy(data, s, "poke-ball")
        assertTrue(ok.ok); assertEquals("Bought Poké Ball for 200 ₽", ok.message)
        assertEquals(800, s.cash); assertEquals(1, s.count("poke-ball"))
        Shop.buy(data, s, "poke-ball")
        assertEquals(2, s.count("poke-ball"))
        assertTrue(s.shopItems.contains("poke-ball")) // stock is unlimited
    }

    @Test fun buyTm() {
        val s = state()
        s.shopDay = s.day; s.shopTms += listOf("thunderbolt", "watergun")
        assertFalse(Shop.buyTm(data, s, "ember").ok)
        s.cash = 999
        assertEquals("Not enough money (need 1000 ₽)", Shop.buyTm(data, s, "thunderbolt").message)
        assertTrue("thunderbolt" !in s.tms)
        s.cash = 1500
        val ok = Shop.buyTm(data, s, "thunderbolt")
        assertTrue(ok.ok); assertEquals("Bought TM Thunderbolt for 1000 ₽", ok.message)
        assertEquals(500, s.cash); assertTrue("thunderbolt" in s.tms)
        s.cash = 5000
        val dup = Shop.buyTm(data, s, "thunderbolt")
        assertFalse(dup.ok); assertEquals(5000, s.cash)
    }

    @Test fun rerollChargesAndChangesStock() {
        val d = shopData()
        val s = state()
        Shop.ensureToday(d, s)
        val items = s.shopItems.toList(); val tms = s.shopTms.toList()
        val poor = Shop.reroll(d, s, TestData.rng(3))
        assertFalse(poor.ok); assertEquals("Not enough money (need 100 ₽)", poor.message)
        assertEquals(items, s.shopItems)
        s.cash = 250
        var changed = false
        for (seed in 1..5) {
            s.cash = 250
            assertTrue(Shop.reroll(d, s, TestData.rng(seed)).ok)
            assertEquals(150, s.cash)
            assertEquals(s.day, s.shopDay)
            assertEquals(3, drawn(s).size); assertEquals(3, s.shopTms.size)
            if (s.shopItems != items || s.shopTms != tms) changed = true
        }
        assertTrue(changed)
    }

    // ---- PLAN 171: ball prices and stock ----

    @Test fun ballPricesOverridePokeApi() {
        // the fixtures carry PokeAPI's 200 / 600 / 800 / 1000 (Net Ball): the Shop uses its own
        assertEquals(200, data.price("poke-ball")); assertEquals(700, data.price("great-ball")); assertEquals(1500, data.price("ultra-ball"))   // R13 Part C
        assertEquals("never sold", 0, data.price("master-ball"))
        for (old in Tuning.OLD_BALLS) assertEquals(old, 0, data.price(old))
        assertEquals("other items keep PokeAPI's price", 700, data.price("super-potion"))
        val s = state()
        s.shopDay = s.day; s.shopItems += "great-ball"; s.cash = 1000
        assertEquals("Bought Great Ball for 700 ₽", Shop.buy(data, s, "great-ball").message)
        s.shopItems += "net-ball"
        assertEquals("Not in today's shop", Shop.buy(data, s, "net-ball").message)
        assertEquals("Not in today's shop", Shop.buy(data, s, "master-ball").message)
    }

    private fun days(n: Int) = (0 until n).map { java.time.LocalDate.of(2026, 1, 1).plusDays(it.toLong()).toString() }

    @Test fun theUltraBallIsInStockAboutOneDayInSeven() {
        val ultraDays = days(730).count { Shop.ultraBallDay(it) }
        assertTrue("$ultraDays of 730", ultraDays in 70..140)   // ~104 expected
        assertEquals("date-seeded: the same answer every time", days(60).map { Shop.ultraBallDay(it) }, days(60).map { Shop.ultraBallDay(it) })
    }

    @Test fun theUltraBallComesOnTopOfTheDrawnItemsOnlyOnItsDays() {
        val d = shopData()
        val yes = days(400).first { Shop.ultraBallDay(it) }
        val no = days(400).first { !Shop.ultraBallDay(it) }
        val (items, _) = Shop.stockFor(d, yes, 0)
        assertEquals("ultra-ball", items.first()); assertEquals(Shop.slots(0) + 1, items.size)
        assertFalse("ultra-ball" in Shop.stockFor(d, no, 0).first)
        // never in the daily draw or the deal, whatever the day
        for (day in days(200)) {
            assertTrue(Shop.stockFor(d, day, 0).first.drop(if (Shop.ultraBallDay(day)) 1 else 0).none { it == "ultra-ball" })
            assertTrue(Shop.dealFor(d, day)?.item != "ultra-ball")
        }
        // a reroll on its day keeps it, a reroll on another day never brings it
        val s = state(); s.day = yes; Shop.ensureToday(d, s)
        for (seed in 1..20) { s.cash = 100; Shop.reroll(d, s, TestData.rng(seed)); assertEquals("ultra-ball", s.shopItems.first()) }
        val t = state(); t.day = no; Shop.ensureToday(d, t)
        for (seed in 1..20) { t.cash = 100; Shop.reroll(d, t, TestData.rng(seed)); assertFalse("ultra-ball" in t.shopItems) }
        // and it can be bought there
        s.cash = 2000
        assertEquals("Bought Ultra Ball for 1500 ₽", Shop.buy(d, s, "ultra-ball").message)
    }

    @Test fun anOldBallLeftInTodaysStockLeavesIt() {
        val s = state()
        s.shopDay = s.day; s.shopItems += listOf("net-ball", "potion")   // drawn before the update
        Shop.ensureToday(data, s)
        assertEquals(listOf("potion"), s.shopItems)
    }

    @Test fun noBallIsEverFound() {
        assertTrue(Items.dropPool().none { Items.def(it)?.kind == ItemKind.BALL })
        assertTrue(Tuning.OLD_BALLS.all { Items.def(it)?.kind == ItemKind.BALL })   // still known: the Ankimon import and old bags keep them
    }

    // ---- trainer ----

    @Test fun xpForNext() {
        assertEquals(50, Trainer.xpForNext(1))
        assertEquals(141, Trainer.xpForNext(2))
        assertEquals(559, Trainer.xpForNext(5))
        assertEquals(1581, Trainer.xpForNext(10))
    }

    @Test fun trainerLevelUps() {
        val s = state()
        val ev = events()
        repeat(4) { Trainer.addDefeatXp(s, Tier.NORMAL, ev) }
        assertEquals(40, s.trainerXp); assertEquals(1, s.trainerLevel)
        assertTrue(ev.isEmpty())
        Trainer.addDefeatXp(s, Tier.NORMAL, ev)
        assertEquals(2, s.trainerLevel); assertEquals(0, s.trainerXp)
        assertEquals(listOf<GameEvent>(GameEvent.TrainerLevelUp(2)), ev)
    }

    @Test fun trainerXpByTierAndMultiLevel() {
        val s = state()
        val ev = events()
        Trainer.addDefeatXp(s, Tier.MEGA, ev); Trainer.addDefeatXp(s, Tier.STARTER, ev); Trainer.addDefeatXp(s, Tier.GMAX, ev)
        assertEquals(0, s.trainerXp)
        Trainer.addDefeatXp(s, Tier.BABY, ev); assertEquals(16, s.trainerXp)
        Trainer.addDefeatXp(s, Tier.ULTRA, ev); assertEquals(46, s.trainerXp)
        s.trainerXp = 190 // + 160 = 350: two levels at once (50 + 141)
        Trainer.addDefeatXp(s, Tier.MYTHICAL, ev)
        assertEquals(3, s.trainerLevel) // 350 - 50 - 141 = 159 < 259 (floor(50*3^1.5) = 259)
        assertEquals(159, s.trainerXp)
        assertEquals(listOf(2, 3), ev.filterIsInstance<GameEvent.TrainerLevelUp>().map { it.level })
        Trainer.addDefeatXp(s, Tier.LEGENDARY, ev) // 279 -> level 4
        assertEquals(4, s.trainerLevel); assertEquals(20, s.trainerXp)
    }

    @Test fun rankLowestAndHighest() {
        val s = state()
        assertEquals("Novice Trainer", Trainer.rank(s))
        val top = GameState()
        top.trainerLevel = 100
        repeat(900) { top.caught += it + 1 }
        repeat(50) { i -> top.mons += TestData.mon(data, 25, if (i == 0) 99 else 5, uid = "s$i").also { it.shiny = true } }
        assertEquals("Legendary Trainer", Trainer.rank(top))
        top.mons.forEach { it.shiny = false }
        assertEquals("Grand Champion", Trainer.rank(top.also { it.trainerLevel = 80 }.also { t -> repeat(25) { t.mons[it].shiny = true }; t.mons[0].level = 95 }))
    }

    @Test fun rankMiddleSteps() {
        val s = GameState()
        repeat(150) { s.caught += it + 1 }
        s.mons += TestData.mon(data, 25, 30, uid = "a")
        s.trainerLevel = 10
        assertEquals("Rookie", Trainer.rank(s))
        s.trainerLevel = 9
        assertEquals("Novice Trainer", Trainer.rank(s))
        s.trainerLevel = 30
        repeat(150) { s.caught += 200 + it }
        s.mons[0].level = 50
        // Skilled Trainer needs a shiny and 250 species; 300 species without a shiny is still a Rookie
        assertEquals("Rookie", Trainer.rank(s))
        s.mons[0].shiny = true
        s.mons[0].level = 40; s.trainerLevel = 20; repeat(100) { s.caught += 500 + it }
        assertEquals("Skilled Trainer", Trainer.rank(s))
        s.mons[0].level = 50; s.trainerLevel = 30
        assertEquals("Skilled Trainer", Trainer.rank(s))
        s.badges.addAll(listOf(1, 2, 3, 4))
        repeat(100) { s.caught += 900 + it }
        assertEquals("Veteran", Trainer.rank(s))
    }

    // ---- badges ----

    @Test fun badgeTitles() {
        assertEquals(17, Badges.TITLES.size)
        assertEquals("100 cards in one day", Badges.TITLES[1])
        assertEquals("Revived a fossil", Badges.TITLES[19])
        assertEquals("Healing does good", Badges.TITLES[20])
        assertNull(Badges.TITLES[11]); assertNull(Badges.TITLES[14])
    }

    @Test fun badgesOnce() {
        val s = state()
        val ev = events()
        Badges.award(s, 7, ev); Badges.award(s, 7, ev)
        assertEquals(listOf<GameEvent>(GameEvent.BadgeEarned(7, "First Pokémon caught")), ev)
        Badges.award(s, 11, ev) // not an AnkiGo badge
        assertTrue(11 !in s.badges)
    }

    @Test fun reviewBadges() {
        val s = state()
        val ev = events()
        s.reviewsToday = 99; Badges.onReviews(s, ev); assertTrue(ev.isEmpty())
        s.reviewsToday = 100; Badges.onReviews(s, ev)
        assertEquals(setOf(1), s.badges)
        s.reviewsToday = 350; Badges.onReviews(s, ev)
        assertEquals(setOf(1, 2, 3), s.badges)
        s.reviewsToday = 2000; Badges.onReviews(s, ev)
        assertEquals(setOf(1, 2, 3, 4, 12, 13), s.badges)
        assertEquals(6, ev.size)
        Badges.onReviews(s, ev)
        assertEquals(6, ev.size)
    }

    // ---- item use ----

    private fun hurt(s: GameState, d: Int): Mon {
        val m = s.main!!
        m.hp = StatCalc.maxHp(data, m) - d
        return m
    }

    @Test fun healCapsAtMissingHp() {
        val s = state()
        val m = hurt(s, 10)
        Economy.give(s, "super-potion", 2)
        assertTrue(ItemUse.canUse(data, s, "super-potion", m))
        val r = ItemUse.use(data, s, "super-potion", null, TestData.rng(), 0)
        assertTrue(r.ok); assertEquals("Super Potion healed Sparkmouse by 10 HP", r.message)
        assertEquals(StatCalc.maxHp(data, m), m.hp)
        assertEquals(1, s.count("super-potion"))
        assertTrue(20 in s.badges)
        assertTrue(r.events.any { it is GameEvent.BadgeEarned && it.id == 20 })
        assertFalse(ItemUse.canUse(data, s, "super-potion", m))
        val full = ItemUse.use(data, s, "super-potion", "main", TestData.rng(), 0)
        assertFalse(full.ok); assertEquals("Sparkmouse is already healthy.", full.message)
        assertEquals(1, s.count("super-potion"))
    }

    @Test fun healPartialAndFull() {
        val s = state()
        val m = hurt(s, 50)
        Economy.give(s, "potion"); Economy.give(s, "max-potion")
        ItemUse.use(data, s, "potion", "main", TestData.rng(), 0)
        assertEquals(StatCalc.maxHp(data, m) - 30, m.hp)
        assertFalse("potion" in s.bag)
        val r = ItemUse.use(data, s, "max-potion", null, TestData.rng(), 0)
        assertTrue(r.ok); assertEquals(StatCalc.maxHp(data, m), m.hp) // no overflow with Int.MAX_VALUE
    }

    @Test fun healTargetsAnotherMon() {
        val s = state()
        val other = TestData.mon(data, 16, 10, uid = "bird"); other.hp = 1
        s.mons += other; s.team += other.uid
        Economy.give(s, "potion")
        assertFalse(ItemUse.canUse(data, s, "potion", s.main)) // main is full
        assertTrue(ItemUse.canUse(data, s, "potion", other))
        assertTrue(ItemUse.use(data, s, "potion", "bird", TestData.rng(), 0).ok)
        assertEquals(21, other.hp)
    }

    @Test fun itemNotInBagOrNotUsable() {
        val s = state()
        hurt(s, 5)
        assertEquals("You have no Potion.", ItemUse.use(data, s, "potion", null, TestData.rng(), 0).message)
        assertFalse(ItemUse.canUse(data, s, "potion", s.main))
        assertFalse(ItemUse.use(data, s, "rare-candy", null, TestData.rng(), 0).ok)
        Economy.give(s, "poke-ball"); Economy.give(s, "poke-doll")
        assertEquals("Use it from the battle screen.", ItemUse.use(data, s, "poke-ball", null, TestData.rng(), 0).message)
        assertEquals("Use it from the battle screen.", ItemUse.use(data, s, "poke-doll", null, TestData.rng(), 0).message)
        assertEquals(1, s.count("poke-ball")); assertEquals(1, s.count("poke-doll"))
        assertFalse(ItemUse.canUse(data, s, "poke-ball", s.main))
    }

    @Test fun stoneEvolves() {
        val s = state() // main is Sparkmouse Lv 20
        Economy.give(s, "thunder-stone")
        assertTrue(ItemUse.canUse(data, s, "thunder-stone", s.main))
        val r = ItemUse.use(data, s, "thunder-stone", "main", TestData.rng(), 0)
        assertTrue(r.ok); assertEquals("Sparkmouse evolved into Sparkrat!", r.message)
        assertEquals(26, s.main!!.species)
        assertEquals(0, s.count("thunder-stone"))
        assertTrue(r.events.any { it is GameEvent.Evolved && it.toSpecies == 26 })
        assertEquals(1, s.evolutions)
        assertTrue(16 in s.badges)
    }

    private fun clock(hour: Int): Long = java.util.Calendar.getInstance().apply {
        set(2026, java.util.Calendar.OCTOBER, 13, hour, 0, 0); set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun timedWorld(item: String, time: String): Pair<GameData, GameState> {
        val d = TestData.data(listOf(
            TestData.species(10, "Sneasel", evolutions = listOf(Evolution(11, EvoKind.ITEM, item = item, time = time))),
            TestData.species(11, "Weavile", prevo = 10),
        ))
        return d to TestData.state(d, 10, 30)
    }

    @Test fun timedItemEvolutionWorksOnlyAtTheRightTime() {
        val (d, s) = timedWorld("razor-claw", "night")
        Economy.give(s, "razor-claw")
        assertFalse(ItemUse.canUse(d, s, "razor-claw", s.main, clock(10)))
        assertTrue(ItemUse.canUse(d, s, "razor-claw", s.main, clock(22)))
        val day = ItemUse.use(d, s, "razor-claw", "main", TestData.rng(), clock(10))
        assertFalse(day.ok); assertEquals("Sneasel evolves with Razor Claw only at night.", day.message)
        assertEquals(1, s.count("razor-claw")); assertEquals(10, s.main!!.species); assertTrue(day.events.isEmpty())
        val night = ItemUse.use(d, s, "razor-claw", "main", TestData.rng(), clock(22))
        assertTrue(night.ok); assertEquals(11, s.main!!.species); assertEquals(0, s.count("razor-claw"))
    }

    @Test fun dayOnlyItemSaysSo() {
        val (d, s) = timedWorld("oval-stone", "day")
        Economy.give(s, "oval-stone")
        val r = ItemUse.use(d, s, "oval-stone", "main", TestData.rng(), clock(22))
        assertFalse(r.ok); assertEquals("Sneasel evolves with Oval Stone only during the day.", r.message)
        assertEquals(1, s.count("oval-stone"))
        assertTrue(ItemUse.use(d, s, "oval-stone", "main", TestData.rng(), clock(10)).ok)
    }

    @Test fun plainStoneAndEverstoneAtAnyHour() {
        for (h in listOf(10, 22)) {
            val s = state()
            Economy.give(s, "thunder-stone")
            assertTrue(ItemUse.canUse(data, s, "thunder-stone", s.main, clock(h)))
            assertTrue(ItemUse.use(data, s, "thunder-stone", "main", TestData.rng(), clock(h)).ok)
        }
        val (d, s) = timedWorld("razor-claw", "night")
        s.main!!.heldItem = "everstone"
        Economy.give(s, "razor-claw")
        val r = ItemUse.use(d, s, "razor-claw", "main", TestData.rng(), clock(22))
        assertFalse(r.ok); assertEquals("It won't have any effect.", r.message)
        assertEquals(1, s.count("razor-claw"))
    }

    @Test fun wrongStoneKeepsItem() {
        val s = state()
        Economy.give(s, "moon-stone")
        assertFalse(ItemUse.canUse(data, s, "moon-stone", s.main))
        val r = ItemUse.use(data, s, "moon-stone", "main", TestData.rng(), 0)
        assertFalse(r.ok); assertEquals("It won't have any effect.", r.message)
        assertEquals(1, s.count("moon-stone"))
        assertEquals(25, s.main!!.species)
        assertTrue(r.events.isEmpty())
    }

    @Test fun evolutionNeedsATarget() {
        val s = state()
        Economy.give(s, "thunder-stone")
        val r = ItemUse.use(data, s, "thunder-stone", null, TestData.rng(), 0)
        assertFalse(r.ok); assertEquals(1, s.count("thunder-stone"))
        assertFalse(ItemUse.canUse(data, s, "thunder-stone", null))
    }

    @Test fun fossilRevivesLevelFive() {
        val d = GameData(data.species + (138 to TestData.species(138, "Spiralmon")), data.moves, data.growth, data.itemPrices)
        val s = TestData.state(d)
        Economy.give(s, "helix-fossil")
        assertTrue(ItemUse.canUse(d, s, "helix-fossil", null))
        val r = ItemUse.use(d, s, "helix-fossil", null, TestData.rng(), 1234L)
        assertTrue(r.ok); assertEquals("Revived Spiralmon!", r.message)
        assertEquals(0, s.count("helix-fossil"))
        val revived = s.mons.single { it.species == 138 }
        assertEquals(Tuning.FOSSIL_LEVEL, revived.level)
        assertTrue(138 in s.caught)
        assertTrue(19 in s.badges)
        assertTrue(r.events.any { it is GameEvent.Caught && it.species == 138 })
        assertTrue(r.events.any { it is GameEvent.BadgeEarned && it.id == 19 })
    }

    @Test fun fossilWithoutSpeciesDataFailsAndKeepsItem() {
        val s = state()
        Economy.give(s, "helix-fossil")
        assertFalse(ItemUse.canUse(data, s, "helix-fossil", null))
        assertFalse(ItemUse.use(data, s, "helix-fossil", null, TestData.rng(), 0).ok)
        assertEquals(1, s.count("helix-fossil"))
    }

    @Test fun heldGiveAndSwap() {
        val s = state()
        Economy.give(s, "lucky-egg"); Economy.give(s, "everstone")
        assertFalse(ItemUse.use(data, s, "lucky-egg", null, TestData.rng(), 0).ok) // needs a target
        val a = ItemUse.use(data, s, "lucky-egg", "main", TestData.rng(), 0)
        assertTrue(a.ok); assertEquals("Sparkmouse now holds Lucky Egg", a.message)
        assertEquals("lucky-egg", s.main!!.heldItem); assertEquals(0, s.count("lucky-egg"))
        assertFalse(ItemUse.canUse(data, s, "lucky-egg", s.main))
        val b = ItemUse.use(data, s, "everstone", "main", TestData.rng(), 0)
        assertTrue(b.ok); assertTrue(b.message.contains("Lucky Egg went back to the bag"))
        assertEquals("everstone", s.main!!.heldItem)
        assertEquals(1, s.count("lucky-egg")); assertEquals(0, s.count("everstone"))
    }

    @Test fun takeHeld() {
        val s = state()
        assertFalse(ItemUse.takeHeld(s, "main").ok)
        assertFalse(ItemUse.takeHeld(s, "nobody").ok)
        s.main!!.heldItem = "macho-brace"
        val r = ItemUse.takeHeld(s, "main")
        assertTrue(r.ok); assertNull(s.main!!.heldItem); assertEquals(1, s.count("macho-brace"))
    }

    // ---- TMs ----

    private fun tmState(vararg moves: String): GameState {
        val s = TestData.state(data, 25, 20, listOf("tackle", "thunderwave"))
        s.main!!.moves.clear(); s.main!!.moves.addAll(moves.toList())
        return s
    }

    private val tmData: GameData by lazy {
        val sp = TestData.species(25, "Sparkmouse", tmMoves = setOf("bodyslam", "thunderbolt", "ember"))
        GameData(data.species + (25 to sp), data.moves, data.growth, data.itemPrices)
    }

    @Test fun teachTmAddsWhenRoom() {
        val s = tmState("tackle")
        s.tms += "thunderbolt"
        val r = ItemUse.teachTm(tmData, s, "thunderbolt", "main", null)
        assertTrue(r.ok); assertEquals("Sparkmouse learned Thunderbolt", r.message)
        assertEquals(listOf("tackle", "thunderbolt"), s.main!!.moves)
        assertTrue("thunderbolt" in s.tms) // never used up
        assertEquals(GameEvent.MoveLearned("main", "Sparkmouse", "Thunderbolt", null), r.events.single())
    }

    @Test fun teachTmReplacesWhenFull() {
        val s = tmState("tackle", "growl", "lick", "watergun")
        s.tms += "thunderbolt"
        val noChoice = ItemUse.teachTm(tmData, s, "thunderbolt", "main", null)
        assertFalse(noChoice.ok); assertEquals("Choose a move to forget.", noChoice.message)
        assertFalse(ItemUse.teachTm(tmData, s, "thunderbolt", "main", 4).ok)
        assertFalse(ItemUse.teachTm(tmData, s, "thunderbolt", "main", -1).ok)
        assertEquals(listOf("tackle", "growl", "lick", "watergun"), s.main!!.moves)
        val r = ItemUse.teachTm(tmData, s, "thunderbolt", "main", 2)
        assertTrue(r.ok); assertEquals("Sparkmouse forgot Lick and learned Thunderbolt", r.message)
        assertEquals(listOf("tackle", "growl", "thunderbolt", "watergun"), s.main!!.moves)
        assertEquals(GameEvent.MoveLearned("main", "Sparkmouse", "Thunderbolt", "Lick"), r.events.single())
        assertTrue("thunderbolt" in s.tms)
    }

    @Test fun teachTmRejections() {
        val s = tmState("tackle", "thunderbolt")
        assertEquals("You don't own TM Thunderbolt.", ItemUse.teachTm(tmData, s, "thunderbolt", "main", null).message)
        s.tms += setOf("thunderbolt", "earthquake")
        assertEquals("Sparkmouse already knows Thunderbolt.", ItemUse.teachTm(tmData, s, "thunderbolt", "main", null).message)
        assertEquals("Sparkmouse can't learn Earthquake.", ItemUse.teachTm(tmData, s, "earthquake", "main", null).message)
        assertFalse(ItemUse.teachTm(tmData, s, "thunderbolt", "ghost", null).ok)
        assertEquals(listOf("tackle", "thunderbolt"), s.main!!.moves)
    }

    @Test fun buyingTheSameTmTwiceIsRefusedAndKeepsTheMoney() {
        val s = state()
        s.shopDay = s.day; s.shopTms += "thunderbolt"; s.cash = 2500
        assertTrue(Shop.buyTm(data, s, "thunderbolt").ok)
        assertEquals(1500, s.cash)
        assertFalse(Shop.buyTm(data, s, "thunderbolt").ok)
        assertEquals(1500, s.cash); assertEquals(1, s.tms.count { it == "thunderbolt" })
    }

    @Test fun oneTmTeachesEveryPokemonAndStays() {
        val s = tmState("tackle")
        val other = TestData.mon(tmData, 25, 12, listOf("growl", "tackle", "lick", "watergun"), "other")
        s.mons += other
        s.tms += "thunderbolt"
        assertTrue(ItemUse.teachTm(tmData, s, "thunderbolt", "main", null).ok)
        assertTrue("thunderbolt" in s.tms)
        assertTrue(ItemUse.teachTm(tmData, s, "thunderbolt", "other", 1).ok)
        assertTrue("thunderbolt" in s.tms)
        assertEquals(listOf("growl", "thunderbolt", "lick", "watergun"), other.moves)
        assertTrue("thunderbolt" in s.main!!.moves)
    }

    @Test fun tmForAMoveTheSpeciesCannotLearnLeavesEverythingAlone() {
        val s = tmState("tackle", "growl", "lick", "watergun")
        s.tms += "earthquake"
        val r = ItemUse.teachTm(tmData, s, "earthquake", "main", 0)
        assertFalse(r.ok); assertEquals(listOf("tackle", "growl", "lick", "watergun"), s.main!!.moves); assertTrue(r.events.isEmpty())
    }
}
