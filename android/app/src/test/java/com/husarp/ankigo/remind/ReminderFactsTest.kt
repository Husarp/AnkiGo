package com.husarp.ankigo.remind

import com.husarp.ankigo.game.EvoKind
import com.husarp.ankigo.game.Evolution
import com.husarp.ankigo.game.GameData
import com.husarp.ankigo.game.Shop
import com.husarp.ankigo.game.TestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderFactsTest {
    /** The test world (a Sparkmouse that evolves with a Thunder Stone) with a few more priced items. */
    private val data: GameData = TestData.world().let { w ->
        GameData(w.species, w.moves, w.growth, w.itemPrices + mapOf("water-stone" to 3000, "fire-stone" to 3000, "fast-ball" to 1000))
    }

    private fun state() = TestData.state(data).also { it.settings.dailyAverage = 50 }

    @Test fun worthHavingMeansAnyBallOrAnEvolutionItemThatFitsATeamMember() {
        val s = state() // team: Sparkmouse (evolves with thunder-stone), nothing else
        val stock = listOf("poke-ball", "thunder-stone", "water-stone", "moon-stone", "potion", "lucky-egg", "helix-fossil", "great-ball")
        assertEquals(listOf("poke-ball", "thunder-stone", "great-ball"), ReminderFacts.worthHaving(data, s, stock))
    }

    @Test fun anEvolutionItemForAPokemonOutsideTheTeamIsNotWorthIt() {
        val s = state()
        s.mons += TestData.mon(data, 133, 10, uid = "boxed") // Fluffy (water-stone) sits in the box
        assertEquals(emptyList<String>(), ReminderFacts.worthHaving(data, s, listOf("water-stone")))
        s.team += "boxed"
        assertEquals(listOf("water-stone"), ReminderFacts.worthHaving(data, s, listOf("water-stone")))
    }

    @Test fun aPokemonHoldingAnEverstoneDoesNotNeedTheStone() {
        val s = state()
        s.main!!.heldItem = "everstone"
        assertEquals(emptyList<String>(), ReminderFacts.worthHaving(data, s, listOf("thunder-stone")))
    }

    @Test fun timeBoundEvolutionItemsStillCountAtAnyTimeOfDay() {
        val sp = TestData.species(900, "Daymon", evolutions = listOf(Evolution(901, EvoKind.ITEM, item = "dusk-stone", time = "night")))
        val d = GameData(data.species + (900 to sp) + (901 to TestData.species(901, "Nightmon")), data.moves, data.growth, data.itemPrices + ("dusk-stone" to 2000))
        val s = TestData.state(d, species = 900)
        assertEquals(listOf("dusk-stone"), ReminderFacts.worthHaving(d, s, listOf("dusk-stone")))
    }

    @Test fun factsTakeTodaysRealStockAndTomorrowsDrawnStock() {
        val s = state()
        s.shopDay = s.day
        s.shopItems.addAll(listOf("potion", "poke-ball", "thunder-stone")) // e.g. after a reroll
        val f = ReminderFacts.from(data, s)
        assertEquals("2026-10-13", f.day)
        assertEquals(listOf("poke-ball", "thunder-stone"), f.shopToday)
        val tomorrow = Shop.stockFor(data, "2026-10-14", s.trainerLevel).first
        assertEquals(ReminderFacts.worthHaving(data, s, tomorrow), f.shopTomorrow)
    }

    @Test fun anUnstockedShopFallsBackToTheDrawForTheDay() {
        val s = state() // shopDay is null: the shop is not stocked for the day yet
        val f = ReminderFacts.from(data, s)
        assertEquals(ReminderFacts.worthHaving(data, s, Shop.stockFor(data, s.day, s.trainerLevel).first), f.shopToday)
    }

    @Test fun factsCarryTheStreakAndTheGoal() {
        val s = state()
        s.reviewsToday = 17; s.streakDays = 6; s.lastGoalDay = "2026-10-12"; s.settings.dayStartHour = 5
        val f = ReminderFacts.from(data, s)
        assertEquals(17, f.reviewsToday); assertEquals(50, f.dailyGoal); assertEquals(6, f.streakDays)
        assertEquals("2026-10-12", f.lastGoalDay); assertEquals(5, f.dayStartHour)
        assertTrue(f.valid)
    }

    @Test fun noFactsBeforeTheStarterOrTheFirstDay() {
        val s = state()
        s.starterChosen = false
        assertEquals(ReminderFacts.NONE, ReminderFacts.from(data, s))
        val t = state(); t.day = ""
        assertEquals(ReminderFacts.NONE, ReminderFacts.from(data, t))
    }

    @Test fun theMainPokemonsCachedPictureIsKeptInTheFacts() {
        val s = state()
        assertEquals(null, ReminderFacts.from(data, s).mainSprite) // none cached: no large icon
        val f = ReminderFacts.from(data, s, "/data/sprites/static/25s.png")
        assertEquals("/data/sprites/static/25s.png", f.mainSprite)
        val p = com.husarp.ankigo.shots.Shots.MemPrefs()
        ReminderFacts.save(p, f)
        assertEquals(f, ReminderFacts.load(p))
        ReminderFacts.save(p, f.copy(mainSprite = null))
        assertEquals(null, ReminderFacts.load(p).mainSprite)
        assertEquals(null, ReminderFacts.NONE.mainSprite)
    }
}
