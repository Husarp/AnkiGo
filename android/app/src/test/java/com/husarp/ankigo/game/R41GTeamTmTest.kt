package com.husarp.ankigo.game

import com.husarp.ankigo.game.save.SaveCodec
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class R41GTeamTmTest {
    private val base = TestData.world()
    private val data = GameData(base.species + (900 to TestData.species(900, types = listOf(Type.FIRE, Type.WATER),
        tmMoves = setOf("bodyslam", "thunderbolt", "watergun", "ember", "vinewhip", "earthquake", "shadowball"))),
        base.moves, base.growth, base.itemPrices)

    private class Roll(private val roll: Int, private val index: Int = 0) : Random() {
        override fun nextBits(bitCount: Int) = 0
        override fun nextInt(until: Int): Int = if (until == 100) roll else index.coerceAtMost(until - 1)
    }

    @Test fun thirtyPercentBoundaryAndBothTypesUseAnyTmOfThatType() {
        val team = listOf(listOf(Type.FIRE, Type.WATER))
        assertEquals(listOf("ember"), Shop.drawTms(data, Roll(29), 1, team, emptySet()))
        assertEquals(listOf("watergun"), Shop.drawTms(data, Roll(29, 1), 1, team, emptySet()))
        assertEquals(Shop.drawTms(data, Roll(30), 1, emptyList(), emptySet()),
            Shop.drawTms(data, Roll(30), 1, team, emptySet()))
        // Select a Pokémon first, then its type; its own learnset does not restrict the offer.
        assertEquals(listOf("ember"), Shop.drawTms(data, Roll(0, 1), 1,
            listOf(listOf(Type.WATER), listOf(Type.FIRE)), emptySet()))
        assertFalse("ember" in data.sp(1).tmMoves)
        assertEquals(listOf("vinewhip"), Shop.drawTms(data, Roll(0), 1,
            listOf(data.sp(1).types), emptySet()))
    }

    @Test fun exhaustedTypesAndPoolsFallBackWithoutDuplicatesOrOwnedMoves() {
        val owned = setOf("ember", "bodyslam")
        val tms = Shop.drawTms(data, Roll(0), 20, listOf(listOf(Type.FIRE)), owned)
        assertEquals(Shop.tmPool(data).filter { it !in owned }.toSet(), tms.toSet())
        assertEquals(tms.size, tms.distinct().size)
        assertTrue(Shop.drawTms(data, Roll(0), 20, listOf(listOf(Type.FIRE)), Shop.tmPool(data).toSet()).isEmpty())
        assertEquals(Shop.drawTms(data, Roll(0), 1, emptyList(), owned),
            Shop.drawTms(data, Roll(0), 1, listOf(emptyList()), owned))
    }

    @Test fun seededDailyStockAndRefreshUseTheTeamAndKeepPurchaseLimitsAfterReload() {
        val s = TestData.state(data).apply { day = "2026-11-16"; cash = 1_000_000; tms += "bodyslam" }
        val types = Shop.teamTypes(data, s)
        Shop.ensureToday(data, s)
        assertEquals(Shop.stockFor(data, s.day, s.trainerLevel, types, s.tms).second, s.shopTms)
        val snapshot = s.shopTms.toList()
        val restored = SaveCodec.decode(SaveCodec.encode(s))
        // Opening the same day's shop with a different team never changes its saved offers.
        restored.team.clear(); Shop.ensureToday(data, restored)
        assertEquals(snapshot, restored.shopTms)
        val first = s.shopTms.first()
        assertTrue(Shop.buyTm(data, s, first).ok)
        s.tms.remove(first)
        assertFalse(Shop.buyTm(data, SaveCodec.decode(SaveCodec.encode(s)), first).ok)
        s.tms += first
        assertTrue(Shop.reroll(data, s, Random(72)).ok)
        assertTrue(s.shopTms.none { it in s.tms })
        assertTrue(s.shopTms.size <= Shop.tmSlots(s.trainerLevel))
        val clone = SaveCodec.decode(SaveCodec.encode(s))
        assertTrue(Shop.reroll(data, s, Random(73)).ok)
        assertTrue(Shop.reroll(data, clone, Random(73)).ok)
        assertEquals(s.shopTms, clone.shopTms)
    }

    @Test fun seededSingleSlotDistributionHasThirtyPercentTeamBranch() {
        // With the ordinary chance of Grass included, expected Grass is .30 + .70 / pool size.
        val count = (0 until 10_000).count { seed -> Shop.drawTms(data, Random(seed), 1,
            listOf(listOf(Type.GRASS)), emptySet()).single() == "vinewhip" }
        val expected = 10_000 * (0.30 + 0.70 / Shop.tmPool(data).size)
        assertTrue("$count vs $expected", kotlin.math.abs(count - expected) < 180)
    }
}
