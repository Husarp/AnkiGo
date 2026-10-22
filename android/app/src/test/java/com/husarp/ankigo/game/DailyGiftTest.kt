package com.husarp.ankigo.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** R14-B2-D (PLAN 125 / R10 X3): the daily gift table, the seeded draw, the fallbacks, give() and the texts. Made-up species only. */
class DailyGiftTest {
    private val data = TestData.world() // two priced stones (thunder, moon) and one TM move (bodyslam)

    /** A world with several TMs, so a rare gift can be a TM. */
    private fun tmData(): GameData {
        val tmMon = TestData.species(900, "Tmmon", tmMoves = setOf("thunderbolt", "watergun", "ember", "vinewhip", "earthquake", "shadowball"))
        return GameData(data.species + (900 to tmMon), data.moves, data.growth, data.itemPrices)
    }

    private fun days(n: Int, from: String = "2026-01-01") = (0 until n).map { LocalDate.parse(from).plusDays(it.toLong()).toString() }

    private fun state(): GameState = TestData.state(data, 25, 20, listOf("tackle")).also { it.day = "2026-10-13" }

    // ---- the table ----

    @Test fun theChancesAtTheKeyDays() {
        val d1 = DailyGift.chances(1)
        assertEquals(75.0, d1.common, 1e-9); assertEquals(22.0, d1.uncommon, 1e-9); assertEquals(3.0, d1.rare, 1e-9)
        val d30 = DailyGift.chances(30)
        assertEquals(40.0, d30.common, 1e-9); assertEquals(40.0, d30.uncommon, 1e-9); assertEquals(20.0, d30.rare, 1e-9)
        val d2 = DailyGift.chances(2) // one 29th of the way
        assertEquals(3.0 + 17.0 / 29, d2.rare, 1e-9); assertEquals(22.0 + 18.0 / 29, d2.uncommon, 1e-9)
        val d15 = DailyGift.chances(15) // 14 / 29 of the way
        assertEquals(3.0 + 17.0 * 14 / 29, d15.rare, 1e-9); assertEquals(22.0 + 18.0 * 14 / 29, d15.uncommon, 1e-9)
    }

    @Test fun theChancesAddUpToOneHundredAndOnlyTheRareOnesGrow() {
        for (s in listOf(1, 2, 15, 30, 31, 400)) {
            val c = DailyGift.chances(s)
            assertEquals("day $s", 100.0, c.common + c.uncommon + c.rare, 1e-9)
            assertTrue(c.common > 0 && c.uncommon > 0 && c.rare > 0)
        }
        val all = (1..60).map { DailyGift.chances(it) }
        assertTrue(all.zipWithNext().all { (a, b) -> b.rare >= a.rare && b.uncommon >= a.uncommon && b.common <= a.common })
        assertTrue("strictly growing up to day 30", all.take(30).zipWithNext().all { (a, b) -> b.rare > a.rare && b.uncommon > a.uncommon && b.common < a.common })
    }

    @Test fun theStreakIsClampedToOneThroughThirty() {
        assertEquals(DailyGift.chances(1), DailyGift.chances(0))
        assertEquals(DailyGift.chances(1), DailyGift.chances(-5))
        assertEquals(DailyGift.chances(30), DailyGift.chances(31))
        assertEquals(DailyGift.chances(30), DailyGift.chances(400))
    }

    @Test fun thePoolsAreTheOnesInTheSpec() {
        assertEquals(listOf("poke-ball" to 3, "potion" to 2, "super-potion" to 1), Tuning.GIFT_COMMON)
        assertEquals(listOf("great-ball" to 2, "hyper-potion" to 1, "poke-ball" to 5), Tuning.GIFT_UNCOMMON)
        assertEquals(listOf("ultra-ball" to 1), Tuning.GIFT_RARE_ITEMS)
        for ((item, n) in Tuning.GIFT_COMMON + Tuning.GIFT_UNCOMMON + Tuning.GIFT_RARE_ITEMS) {
            assertNotNull(item, Items.def(item)); assertTrue(n >= 1)
            assertFalse("never a Master Ball", item == "master-ball")
        }
    }

    // ---- the draw ----

    @Test fun theDrawIsStableForADayAndSeededByTheDayOnly() {
        for (day in days(20)) {
            val first = DailyGift.draw(tmData(), day, 7)
            repeat(3) { assertEquals(day, first, DailyGift.draw(tmData(), day, 7)) }
        }
    }

    @Test fun theDrawDiffersOverManyDays() {
        val gifts = days(200).map { DailyGift.draw(data, it, 15) }
        assertTrue("many different gifts", gifts.toSet().size >= 6)
        assertTrue(gifts.any { it!!.item == "poke-ball" } && gifts.any { it!!.item == "potion" })
    }

    @Test fun onDayOneTheTiersComeOutAboutSeventyFiveTwentyTwoAndThree() {
        val common = Tuning.GIFT_COMMON.map { it.first }.toSet() - "poke-ball"
        var rare = 0; var uncommon = 0; var commonN = 0
        val rareItems = setOf("ultra-ball") + DailyGift.stones(data)
        for (day in days(1000)) {
            val g = DailyGift.draw(tmData(), day, 1)!!
            when {
                g.tm != null || g.item in rareItems -> rare++
                g.item == "great-ball" || g.item == "hyper-potion" || (g.item == "poke-ball" && g.count == 5) -> uncommon++
                else -> { commonN++; assertTrue(g.item in common || (g.item == "poke-ball" && g.count == 3)) }
            }
        }
        assertEquals(1000, rare + uncommon + commonN)
        assertTrue("common $commonN", commonN in 700..800)
        assertTrue("uncommon $uncommon", uncommon in 180..260)
        assertTrue("rare $rare", rare in 10..55)
    }

    @Test fun onDayThirtyItIsAboutFortyFortyAndTwenty() {
        var rare = 0; var uncommon = 0
        val rareItems = setOf("ultra-ball") + DailyGift.stones(data)
        for (day in days(1000)) {
            val g = DailyGift.draw(tmData(), day, 30)!!
            if (g.tm != null || g.item in rareItems) rare++
            else if (g.item == "great-ball" || g.item == "hyper-potion" || (g.item == "poke-ball" && g.count == 5)) uncommon++
        }
        assertTrue("uncommon $uncommon", uncommon in 350..450)
        assertTrue("rare $rare", rare in 160..240)
    }

    @Test fun theSameDayDrawsARarerTierWithALongerStreak() {
        // one roll per day: a higher streak never moves a day to a COMMON tier it was not in
        fun rank(g: Gift): Int = when {
            g.tm != null || g.item == "ultra-ball" || g.item!!.endsWith("-stone") -> 2
            g.item == "great-ball" || g.item == "hyper-potion" || (g.item == "poke-ball" && g.count == 5) -> 1
            else -> 0
        }
        for (day in days(300)) {
            val ranks = listOf(1, 5, 15, 30).map { rank(DailyGift.draw(tmData(), day, it)!!) }
            assertEquals(day, ranks.sorted(), ranks)
        }
    }

    @Test fun everyGiftIsACatalogueItemOrAnUnownedTmAndNeverAMasterBall() {
        val d = tmData()
        val tms = DailyGift.unownedTms(d, emptySet()).toSet()
        for (day in days(400)) for (s in listOf(1, 30)) {
            val g = DailyGift.draw(d, day, s)!!
            assertTrue((g.item == null) != (g.tm == null))
            if (g.item != null) { assertNotNull(Items.def(g.item)); assertTrue(g.count >= 1); assertFalse(g.item == "master-ball") }
            else { assertEquals(1, g.count); assertTrue(g.tm in tms) }
        }
    }

    @Test fun aStoneIsAPricedEvolutionStone() {
        val stones = DailyGift.stones(data)
        assertEquals(listOf("moon-stone", "thunder-stone"), stones) // the world prices these two only
        assertTrue(stones.all { Items.def(it)!!.kind == ItemKind.EVOLUTION && data.price(it) > 0 })
        val seen = days(800).mapNotNull { DailyGift.draw(data, it, 30)!!.item }.filter { it.endsWith("-stone") }.toSet()
        assertEquals(stones.toSet(), seen)
    }

    // ---- TMs ----

    @Test fun aTmGiftIsNeverOneYouOwn() {
        val d = tmData()
        val all = DailyGift.unownedTms(d, emptySet())
        assertTrue(all.size >= 5)
        val owned = all.drop(1).toSet() // only the first is still missing
        var tmGifts = 0
        for (day in days(2000)) {
            val g = DailyGift.draw(d, day, 30, owned)!!
            if (g.tm != null) { tmGifts++; assertEquals(all.first(), g.tm) }
        }
        assertTrue("TM gifts happen ($tmGifts)", tmGifts > 20)
    }

    @Test fun whenEveryTmIsOwnedARareGiftIsAStoneOrAnUltraBallInstead() {
        val d = tmData()
        val owned = DailyGift.unownedTms(d, emptySet()).toSet()
        for (day in days(500)) {
            val g = DailyGift.draw(d, day, 30, owned)!!
            assertNull(g.tm)
        }
        // a day of the rare tier: the TM slot gives a stone, so stones are twice as likely as the Ultra Ball
        val rare = days(3000).map { DailyGift.draw(d, it, 30, owned)!! }.filter { it.item == "ultra-ball" || it.item!!.endsWith("-stone") }
        val stones = rare.count { it.item!!.endsWith("-stone") }
        assertTrue("stones $stones of ${rare.size}", stones > rare.size * 0.55 && stones < rare.size * 0.78)
    }

    @Test fun withoutAnyTmInTheDataAStoneStandsIn() {
        val noTm = GameData(data.species.mapValues { (_, sp) -> sp.copy(tmMoves = emptySet()) }, data.moves, data.growth, data.itemPrices)
        assertTrue(Shop.tmPool(noTm).isEmpty())
        for (day in days(500)) assertNull(DailyGift.draw(noTm, day, 30)!!.tm)
    }

    // ---- empty pools fall back ----

    @Test fun anEmptyRarePoolFallsBackDownTheTiers() {
        // no priced stone, no TM, no Ultra Ball entry: nothing rare can be given
        val bare = GameData(data.species.mapValues { (_, sp) -> sp.copy(tmMoves = emptySet()) }, data.moves, data.growth, emptyMap())
        val g = days(300).map { DailyGift.draw(bare, it, 30, rareItems = emptyList())!! }
        assertTrue(g.none { it.tm != null || it.item!!.endsWith("-stone") || it.item == "ultra-ball" })
        assertTrue("the uncommon and common pools still give", g.any { it.item == "great-ball" } && g.any { it.item == "potion" })
    }

    @Test fun anEmptyUncommonPoolFallsBackToTheCommonOne() {
        for (day in days(300)) {
            val g = DailyGift.draw(data, day, 1, uncommon = emptyList(), rareItems = emptyList())!!
            assertTrue(g.tm != null || g.item in setOf("poke-ball", "potion", "super-potion") || g.item!!.endsWith("-stone"))
        }
        // a day of the uncommon tier with the pool empty gives a COMMON gift
        val g = days(400).map { DailyGift.draw(data, it, 30, uncommon = emptyList())!! }
        assertFalse(g.any { it.item == "great-ball" || it.item == "hyper-potion" || (it.item == "poke-ball" && it.count == 5) })
    }

    @Test fun anItemTheCatalogueDoesNotHaveIsNeverGiven() {
        val junk = listOf("rare-candy" to 1, "master-ball-xyz" to 2)
        for (day in days(200)) {
            val g = DailyGift.draw(data, day, 15, common = junk + Tuning.GIFT_COMMON, uncommon = junk, rareItems = junk)!!
            if (g.item != null) assertNotNull(g.item, Items.def(g.item))
        }
        // nothing at all to give (no stone, no TM, no catalogue item in any pool): null, so no event
        val bare = GameData(emptyMap(), emptyMap(), emptyMap(), emptyMap())
        for (day in days(50)) for (s in listOf(1, 15, 30)) assertNull(DailyGift.draw(bare, day, s, common = junk, uncommon = junk, rareItems = junk))
    }

    // ---- give ----

    @Test fun giveAddsItemsToTheBagAndTmsToTheTmsAndSendsOneEvent() {
        val d = tmData()
        var sawItem = false; var sawTm = false
        for (day in days(400)) {
            val s = state().also { it.day = day; it.streakDays = 30; it.bag.clear() }
            val ev = mutableListOf<GameEvent>()
            val g = DailyGift.give(d, s, ev)!!
            val e = ev.single() as GameEvent.DailyGift
            assertEquals(GameEvent.DailyGift(g.item, g.tm, g.count, 30), e)
            if (g.item != null) { sawItem = true; assertEquals(mapOf(g.item to g.count), s.bag.toMap()); assertTrue(s.tms.isEmpty()) }
            else { sawTm = true; assertTrue(g.tm in s.tms); assertTrue(s.bag.isEmpty()) }
        }
        assertTrue(sawItem && sawTm)
    }

    @Test fun giveAddsToWhatTheBagAlreadyHoldsAndNeverTouchesMoney() {
        val s = state(); s.cash = 77; s.cashToday = 40
        s.bag["poke-ball"] = 4
        var gift: Gift? = null
        for (day in days(100)) { s.day = day; gift = DailyGift.draw(data, day, 1); if (gift?.item == "poke-ball") break }
        assertEquals("poke-ball", gift!!.item)
        DailyGift.give(data, s, mutableListOf())
        assertEquals(4 + gift.count, s.count("poke-ball"))
        assertEquals(77, s.cash); assertEquals(40, s.cashToday)
    }

    @Test fun giveStillGivesFromTheFixedPoolsWhenTheDataHasNoStonesOrTms() {
        val s = state()
        val ev = mutableListOf<GameEvent>()
        val empty = GameData(emptyMap(), emptyMap(), emptyMap(), emptyMap())
        assertNotNull(DailyGift.give(empty, s, ev))
        assertEquals(1, ev.size)
        val e = ev.single() as GameEvent.DailyGift
        assertTrue(e.item != null && s.count(e.item) >= e.count)
    }

    // ---- texts ----

    @Test fun namesInWords() {
        assertEquals("3 Poké Balls", DailyGift.name(data, "poke-ball", null, 3))
        assertEquals("Great Ball", DailyGift.name(data, "great-ball", null, 1))
        assertEquals("2 Great Balls", DailyGift.name(data, "great-ball", null, 2))
        assertEquals("2 Potions", DailyGift.name(data, "potion", null, 2))
        assertEquals("Thunder Stone", DailyGift.name(data, "thunder-stone", null, 1))
        assertEquals("TM Thunderbolt", DailyGift.name(data, null, "thunderbolt", 1))
        assertEquals("TM thunderbolt", DailyGift.name(null, null, "thunderbolt", 1))
    }

    @Test fun logLineAndStreakLine() {
        assertEquals("Daily gift: 3 Poké Balls (6-day streak)", DailyGift.logText("3 Poké Balls", 6))
        assertEquals("Daily gift: Ultra Ball", DailyGift.logText("Ultra Ball", 1))
        assertEquals("Reach your goal tomorrow too for rarer gifts", DailyGift.streakLine(1))
        assertEquals("Goal streak: 6 days · rarer gifts as it grows, up to 30 days", DailyGift.streakLine(6))
        assertEquals("Goal streak: 29 days · rarer gifts as it grows, up to 30 days", DailyGift.streakLine(29))
        assertTrue(DailyGift.streakLine(45).startsWith("Goal streak: 45 days"))
    }

    @Test fun poolsInWordsForTheGuide() {
        assertEquals("3 Poké Balls, 2 Potions or a Super Potion", DailyGift.poolText(Tuning.GIFT_COMMON))
        assertEquals("2 Great Balls, a Hyper Potion or 5 Poké Balls", DailyGift.poolText(Tuning.GIFT_UNCOMMON))
        assertEquals("an Ultra Ball", DailyGift.poolText(Tuning.GIFT_RARE_ITEMS))
        assertEquals("a Master Ball", DailyGift.withArticle("Master Ball"))
    }
}
