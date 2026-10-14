package com.husarp.ankigo.game

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EncountersTest {
    private val eps = 1e-9

    private fun pct(reviews: Int, avg: Int = 100, trainer: Int = 1, main: Int = 100) =
        Encounters.tierPercentages(reviews, avg, trainer, main)

    // ---- tierPercentages ----

    @Test fun lowRatioIsAllNormal() {
        val p = pct(39)
        assertEquals(100.0, p.getValue(Tier.NORMAL), eps)
        assertEquals(8, p.size)
        Tier.entries.filter { it != Tier.NORMAL }.forEach { assertEquals(0.0, p.getValue(it), eps) }
    }

    @Test fun zeroAverageCountsAsLowRatio() {
        assertEquals(100.0, pct(500, avg = 0).getValue(Tier.NORMAL), eps)
    }

    @Test fun babyBucket() {
        val p = pct(40)
        // baby 3.5, normal 92.8, ultra 1.5, leg .5, myth .2, mega .5, gmax .5; starter zeroed -> 99.5 before normalising
        assertEquals(3.5 / 99.5 * 100, p.getValue(Tier.BABY), 1e-9)
        assertEquals(92.8 / 99.5 * 100, p.getValue(Tier.NORMAL), 1e-9)
        assertEquals(100.0, p.values.sum(), 1e-9)
    }

    @Test fun ultraBucket() {
        val p = pct(60)
        // 3.0 ultra, 92.8 normal, 2 baby, .5 leg, .2 myth, .5 mega, .5 gmax (starter 0) -> sum 99.5, then normalised
        val total = 2.0 + 3.0 + 0.5 + 0.2 + 0.5 + 0.5 + 92.8
        assertEquals(3.0 / total * 100, p.getValue(Tier.ULTRA), 1e-9)
        assertEquals(92.8 / total * 100, p.getValue(Tier.NORMAL), 1e-9)
    }

    @Test fun highBucket() {
        val p = pct(80)
        val total = 2.0 + 3.0 + 1.0 + 0.4 + 1.0 + 1.0 + 90.6
        assertEquals(1.0 / total * 100, p.getValue(Tier.LEGENDARY), 1e-9)
        assertEquals(0.4 / total * 100, p.getValue(Tier.MYTHICAL), 1e-9)
        assertEquals(90.6 / total * 100, p.getValue(Tier.NORMAL), 1e-9)
    }

    @Test fun levelGates() {
        assertEquals(0.0, pct(80, main = 29).getValue(Tier.ULTRA), eps)
        assertTrue(pct(80, main = 30).getValue(Tier.ULTRA) > 0)
        assertEquals(0.0, pct(80, main = 49).getValue(Tier.LEGENDARY), eps)
        assertTrue(pct(80, main = 50).getValue(Tier.LEGENDARY) > 0)
        assertEquals(0.0, pct(80, main = 74).getValue(Tier.MYTHICAL), eps)
        assertTrue(pct(80, main = 75).getValue(Tier.MYTHICAL) > 0)
        assertEquals(0.0, pct(80, main = 59).getValue(Tier.MEGA), eps)
        assertTrue(pct(80, main = 60).getValue(Tier.MEGA) > 0)
        assertEquals(0.0, pct(80, main = 64).getValue(Tier.GMAX), eps)
        assertTrue(pct(80, main = 65).getValue(Tier.GMAX) > 0)
    }

    @Test fun gatedTierIsNotRedistributedBeforeNormalising() {
        // main 1: ultra/leg/myth/mega/gmax all 0; only normal + baby remain
        val p = pct(40, main = 1)
        // baby 3.5 + normal 92.8 survive: 96.3 total (the gated 0.0 tiers are simply dropped by the normalising)
        assertEquals(3.5 / 96.3 * 100, p.getValue(Tier.BABY), 1e-9)
        assertEquals(92.8 / 96.3 * 100, p.getValue(Tier.NORMAL), 1e-9)
    }

    @Test fun trainerLevelBonus() {
        val without = pct(80, trainer = 10)
        val with = pct(80, trainer = 11)
        // bucket 80 then +3.7 of rare boosts taken from normal: baby 2, ultra 4.5, leg 1.5, myth .6, mega 1.5, gmax 1.5, normal 86.9
        val total = 2.0 + 4.5 + 1.5 + 0.6 + 1.5 + 1.5 + 86.9
        assertEquals((3.0 + 1.5) / total * 100, with.getValue(Tier.ULTRA), 1e-9)
        assertTrue(with.getValue(Tier.ULTRA) > without.getValue(Tier.ULTRA))
        assertTrue(with.getValue(Tier.NORMAL) < without.getValue(Tier.NORMAL))
    }

    @Test fun trainerBonusSkipsTiersFoldedIntoNormal() {
        // ratio < 0.4: no rare tiers left, so nothing is added or subtracted
        val p = pct(0, trainer = 50)
        assertEquals(100.0, p.getValue(Tier.NORMAL), eps)
    }

    @Test fun starterAlwaysZeroAndSumIs100() {
        for (r in listOf(0, 40, 60, 80, 500)) for (tl in listOf(1, 11, 40)) for (m in listOf(1, 30, 60, 80, 100)) {
            val p = pct(r, trainer = tl, main = m)
            assertEquals(0.0, p.getValue(Tier.STARTER), eps)
            assertEquals(100.0, p.values.sum(), 1e-9)
            assertEquals(8, p.size)
        }
    }

    // ---- tierOf ----

    @Test fun tierOfKinds() {
        fun t(num: Int, b: Boolean = false, l: Boolean = false, m: Boolean = false) =
            Encounters.tierOf(TestData.species(num, isBaby = b, isLegendary = l, isMythical = m))
        assertEquals(Tier.STARTER, t(4))
        assertEquals(Tier.STARTER, t(906))
        assertNull(t(140))
        assertEquals(Tier.ULTRA, t(793, l = true))
        assertEquals(Tier.MYTHICAL, t(151, m = true))
        assertEquals(Tier.LEGENDARY, t(150, l = true))
        assertEquals(Tier.BABY, t(172, b = true))
        assertEquals(Tier.NORMAL, t(19))
    }

    @Test fun ankimonLegendaryListOverridesTheFlags() {
        fun t(num: Int, l: Boolean = false) = Encounters.tierOf(TestData.species(num, isLegendary = l))
        assertEquals(Tier.LEGENDARY, t(772))
        assertEquals(Tier.LEGENDARY, t(1009))
        listOf(772, 1009, 1010, 1020, 1021, 1022, 1023).forEach { assertEquals(Tier.LEGENDARY, t(it)); assertEquals(Tier.LEGENDARY, t(it, true)) }
        assertEquals(7, Encounters.LEGENDARY_EXTRA.size)
        assertEquals(Tier.LEGENDARY, t(150, true)) // a regular legendary is unchanged
        assertEquals(Tier.NORMAL, t(773))
        assertNull(t(718, true)); assertNull(t(718, false))
        val data = TestData.data(listOf(TestData.species(772), TestData.species(1009)))
        assertEquals(50, Encounters.minLevel(data, data.sp(772)))
        assertEquals(50, Encounters.minLevel(data, data.sp(1009)))
    }

    @Test fun zygardeNeverSpawns() {
        val data = TestData.data(listOf(
            TestData.species(718, isLegendary = true), TestData.species(772), TestData.species(1009), TestData.species(150, isLegendary = true),
        ))
        val s = Settings().also { it.gens.addAll(1..9) }
        val all = Tier.entries.flatMap { Encounters.pool(data, it, s, 100, setOf(716, 717)) }
        assertFalse(718 in all)
        assertEquals(listOf(150, 772, 1009), Encounters.pool(data, Tier.LEGENDARY, s, 100, setOf(151)).sorted())
        assertTrue(Encounters.pool(data, Tier.LEGENDARY, s, 49, emptySet()).isEmpty())
        val st = GameState().also { it.settings.gens.addAll(1..9); it.caught += setOf(716, 717) }
        assertFalse(Encounters.isValid(data, st, 718, 100))
        assertTrue(Encounters.isValid(data, st, 772, 100))
    }

    @Test fun listSizes() {
        assertEquals(81, Encounters.STARTERS.size)
        assertEquals(11, Encounters.ULTRA_BEASTS.size)
        assertEquals(9, Encounters.STARTER_CHOICES.size)
        Encounters.STARTER_CHOICES.values.forEach { l -> assertEquals(3, l.size); assertTrue(Encounters.STARTERS.containsAll(l)) }
        assertEquals(25, Encounters.FOSSILS.size)
    }

    // ---- minLevel ----

    private val world = TestData.world()

    @Test fun minLevels() {
        assertEquals(16, Encounters.minLevel(world, world.sp(64)))   // plain evolution level (Leafbud is #2, a starter line)
        assertEquals(30, Encounters.minLevel(world, world.sp(2)))    // starter floor beats evoLevel 16
        assertEquals(1, Encounters.minLevel(world, world.sp(25)))   // prevo is a baby
        assertEquals(100, Encounters.minLevel(world, world.sp(26))) // stone evolution
        assertEquals(1, Encounters.minLevel(world, world.sp(19)))
        assertEquals(50, Encounters.minLevel(world, world.sp(150)))
        assertEquals(75, Encounters.minLevel(world, world.sp(151)))
        val ub = TestData.species(795, isLegendary = true)
        assertEquals(30, Encounters.minLevel(world, ub))
        assertEquals(100, Encounters.minLevel(world, world.sp(65))) // trade evolution, prevo not a baby
    }

    // ---- wildLevel ----

    @Test fun wildLevelBounds() {
        val rng = Random(7)
        repeat(500) { assertTrue(Encounters.wildLevel(50, rng) in 47..53) }
        repeat(500) { assertTrue(Encounters.wildLevel(1, rng) in 1..4) }
        repeat(500) { assertTrue(Encounters.wildLevel(2, rng) in 1..5) }
        repeat(500) { assertTrue(Encounters.wildLevel(98, rng) in 95..100) }
        repeat(50) { assertEquals(100, Encounters.wildLevel(100, rng)) }
        val seen = (1..2000).map { Encounters.wildLevel(50, rng) }.toSet()
        assertEquals((47..53).toSet(), seen)
    }

    // ---- pool ----

    private fun extraWorld(extra: List<Species> = emptyList()): GameData {
        val base = world.species.values.toList()
        return TestData.data(base + extra)
    }

    @Test fun poolFiltersLevelAndSortsNormal() {
        val s = Settings()
        // starters 1-3 never; 25 appears (baby prevo fix) but 26 (stone) and 65 (trade) do not
        assertEquals(listOf(16, 19, 25, 54, 63, 92, 129, 133), Encounters.pool(world, Tier.NORMAL, s, 15, emptySet()))
        // level 16 adds Blinkier(64); 130 (20) is still out
        assertEquals(listOf(16, 19, 25, 54, 63, 64, 92, 129, 133), Encounters.pool(world, Tier.NORMAL, s, 16, emptySet()))
        assertEquals(listOf(16, 19, 25, 54, 63, 64, 92, 129, 130, 133), Encounters.pool(world, Tier.NORMAL, s, 20, emptySet()))
        assertEquals(listOf(16, 19, 25, 54, 63, 64, 92, 129, 130, 133, 26, 65, 134, 135, 700).sorted(), Encounters.pool(world, Tier.NORMAL, s, 100, emptySet()))
        assertEquals(listOf(25), Encounters.pool(world, Tier.NORMAL, s, 1, emptySet()).filter { it == 25 || it == 26 })
        assertEquals(listOf(172), Encounters.pool(world, Tier.BABY, s, 1, emptySet()))
    }

    @Test fun poolHonoursGenerations() {
        val s = Settings(gens = mutableSetOf(2))
        // all test species are gen 1 or 2 (172 is gen 2 -> baby only)
        assertEquals(emptyList<Int>(), Encounters.pool(world, Tier.NORMAL, s, 100, emptySet()))
        assertEquals(listOf(172), Encounters.pool(world, Tier.BABY, s, 100, emptySet()))
    }

    @Test fun megaGmaxPoolsEmpty() {
        assertTrue(Encounters.pool(world, Tier.MEGA, Settings(), 100, emptySet()).isEmpty())
        assertTrue(Encounters.pool(world, Tier.GMAX, Settings(), 100, emptySet()).isEmpty())
    }

    @Test fun prerequisiteNeedsBaseCaught() {
        val s = Settings()
        assertFalse(150 in Encounters.pool(world, Tier.LEGENDARY, s, 100, emptySet()))
        assertTrue(150 in Encounters.pool(world, Tier.LEGENDARY, s, 100, setOf(151)))
        assertFalse(150 in Encounters.pool(world, Tier.LEGENDARY, s, 49, setOf(151)))
    }

    @Test fun anyOfPrerequisiteFor1024() {
        val d = extraWorld(listOf(TestData.species(1024, isLegendary = true)))
        val s = Settings(gens = mutableSetOf(9))
        assertTrue(Encounters.pool(d, Tier.LEGENDARY, s, 100, emptySet()).isEmpty())
        assertEquals(listOf(1024), Encounters.pool(d, Tier.LEGENDARY, s, 100, setOf(1008)))
        assertEquals(listOf(1024), Encounters.pool(d, Tier.LEGENDARY, s, 100, setOf(1007)))
        assertTrue(Encounters.pool(d, Tier.LEGENDARY, s, 100, setOf(1009)).isEmpty())
    }

    @Test fun allOfPrerequisite() {
        val d = extraWorld(listOf(TestData.species(249, isLegendary = true)))
        val s = Settings()
        assertTrue(249 !in Encounters.pool(d, Tier.LEGENDARY, s, 100, setOf(144, 145)))
        assertTrue(249 in Encounters.pool(d, Tier.LEGENDARY, s, 100, setOf(144, 145, 146)))
    }

    @Test fun fossilsAndStartersNeverInNormalPool() {
        val d = extraWorld(listOf(TestData.species(138), TestData.species(4, evolutions = emptyList())))
        val s = Settings()
        val p = Encounters.pool(d, Tier.NORMAL, s, 100, emptySet())
        assertFalse(138 in p)
        assertFalse(4 in p)
    }

    // ---- roll ----

    @Test fun rollFallsBackFromEmptyRarePool() {
        // main level 100, high bucket, but nothing legendary/ultra/baby catchable: only the normal pool remains
        val d = TestData.data(listOf(TestData.species(16), TestData.species(150, isLegendary = true)))
        val st = TestData.state(d, 16, 100)
        st.reviewsToday = 1000
        st.trainerLevel = 30
        val rng = Random(3)
        repeat(300) {
            val r = Encounters.roll(d, st, 100, rng)
            assertEquals(Tier.NORMAL, r.tier)
            assertEquals(16, r.species)
        }
    }

    @Test fun rollGivesRareTiersWhenPossible() {
        val st = TestData.state(world, 25, 100)
        st.reviewsToday = 1000
        st.trainerLevel = 30
        st.caught += 151
        val rng = Random(11)
        val tiers = (1..4000).map { Encounters.roll(world, st, 100, rng).tier }.toSet()
        assertTrue(Tier.BABY in tiers && Tier.LEGENDARY in tiers && Tier.NORMAL in tiers)
        assertFalse(Tier.MEGA in tiers || Tier.GMAX in tiers || Tier.STARTER in tiers)
    }

    @Test fun rollFallsBackToRattata() {
        val d = TestData.data(listOf(TestData.species(150, isLegendary = true), TestData.species(2, evoLevel = 99)))
        val st = TestData.state(d, 150, 20)
        val r = Encounters.roll(d, st, 5, Random(1))
        assertEquals(19, r.species)
        assertEquals(Tier.NORMAL, r.tier)
    }

    @Test fun rollIsDeterministicAndUniform() {
        val st = TestData.state(world, 25, 20) // low reviews: all NORMAL
        val a = (1..50).map { Encounters.roll(world, st, 20, Random(it)) }
        val b = (1..50).map { Encounters.roll(world, st, 20, Random(it)) }
        assertEquals(a, b)
        val rng = Random(5)
        val counts = HashMap<Int, Int>()
        repeat(6000) { counts.merge(Encounters.roll(world, st, 20, rng).species, 1, Int::plus) }
        val pool = Encounters.pool(world, Tier.NORMAL, st.settings, 20, st.caught)
        assertEquals(pool.toSet(), counts.keys)
        counts.values.forEach { assertTrue(it > 6000 / pool.size / 2) }
    }

    @Test fun shinyRoughlyOneIn4096() {
        val st = TestData.state(world, 25, 20)
        val rng = Random(2026)
        val n = 120_000
        val shiny = (1..n).count { Encounters.roll(world, st, 20, rng).shiny }
        // expected ~29; loose bound
        assertTrue("shiny count $shiny", shiny in 8..70)
    }

    // ---- makeWild ----

    @Test fun makeWildEvs() {
        val rng = Random(99)
        for (level in listOf(1, 2, 5, 20, 37, 50, 77, 99, 100)) repeat(200) {
            val m = Encounters.makeWild(world, Encounters.Roll(16, Tier.NORMAL, false), level, rng, 5L)
            val budget = minOf(510, level * 51 / 10)
            val evs = m.evs.toList()
            assertTrue("level $level evs $evs", evs.sum() <= budget)
            assertTrue(evs.all { it in 0..252 })
            val total = evs.sum()
            // within 6 of the budget unless the 252 cap/shape (random split with capped part, level 99-100 fixed styles) stops it
            if (evs.none { it == 252 } && level < 98) assertTrue("level $level evs $evs budget $budget", total >= budget - 6)
            assertEquals(StatCalc.maxHp(world, m), m.hp)
        }
    }

    @Test fun makeWildKeepsRollFields() {
        val m = Encounters.makeWild(world, Encounters.Roll(150, Tier.LEGENDARY, true), 55, Random(1), 1234L)
        assertEquals(150, m.species)
        assertEquals(Tier.LEGENDARY, m.tier)
        assertTrue(m.shiny)
        assertEquals(55, m.level)
        assertEquals(0L, m.caughtAt) // wild: set only when caught (Progression.newMon contract)
    }

    @Test fun allFourEvStylesAppear() {
        val rng = Random(4)
        val shapes = (1..400).map { Encounters.makeWild(world, Encounters.Roll(16, Tier.NORMAL, false), 100, rng, 0L).evs }
        assertTrue(shapes.any { it == Stats(84, 84, 84, 84, 84, 84) })
        assertTrue(shapes.any { it == Stats(4, 0, 252, 0, 252, 0) })
        assertTrue(shapes.any { s -> s.toList().count { it == 252 } == 2 && s.toList().contains(4) && s != Stats(4, 0, 252, 0, 252, 0) })
        assertTrue(shapes.any { s -> s.toList().distinct().size > 3 })
    }
}

/** Wishlist weighting (T42) and generation 9 pools, made-up species. */
class WishlistWeightTest {
    private val normals = (30..39).map { TestData.species(it) }          // 10 plain Normal species
    private val d = TestData.data(normals + TestData.species(19, "Ratty"))
    private fun st(wish: Set<Int>): GameState = TestData.state(d, 30, 20).also { s -> s.settings.wishlist.clear(); s.settings.wishlist.addAll(wish) }

    private fun counts(state: GameState, n: Int = 30_000, seed: Int = 5): Map<Int, Int> {
        val rng = Random(seed); val m = HashMap<Int, Int>()
        repeat(n) { m.merge(Encounters.roll(d, state, 20, rng).species, 1, Int::plus) }
        return m
    }

    @Test fun weightConstantIsTwo() = assertEquals(2, Tuning.WISHLIST_WEIGHT)

    @Test fun wishlistedSpeciesIsMetAboutTwiceAsOften() {
        val c = counts(st(setOf(33)))
        val pool = 11 // 30..39 + 19
        val expected = 30_000 * 2.0 / (pool + 1)
        assertEquals(expected, c.getValue(33).toDouble(), expected * 0.1)
        assertEquals(30_000 * 1.0 / (pool + 1), c.getValue(34).toDouble(), expected * 0.15)
    }

    @Test fun noWishlistedSpeciesInPoolKeepsTheOldPicks() {
        val pool = Encounters.pool(d, Tier.NORMAL, Settings(), 20, setOf(30))
        val a = Random(9); val b = Random(9)
        repeat(200) { assertEquals(pool[a.nextInt(pool.size)], Encounters.pickFrom(d, pool, setOf(999, 25), b)) }
        val c = Random(9); val e = Random(9)
        repeat(200) { assertEquals(pool[c.nextInt(pool.size)], Encounters.pickFrom(d, pool, emptySet(), e)) }
    }

    @Test fun wishlistedFormBoostsItsBaseSpecies() {
        val base = d.species.getValue(33)
        val form = base.copy(num = 10091, id = "alolan33", name = "Alolan 33", gen = 7, form = "Alola", baseNum = 33)
        val d2 = TestData.data(normals + TestData.species(19, "Ratty") + form)
        val pool = Encounters.pool(d2, Tier.NORMAL, Settings(), 20, setOf(30))
        val rng = Random(2); var hits = 0; val n = 20_000
        repeat(n) { if (Encounters.pickFrom(d2, pool, setOf(10091), rng) == 33) hits++ }
        assertEquals(n * 2.0 / (pool.size + 1), hits.toDouble(), n * 0.2 / pool.size)
    }

    @Test fun gen9PoolSkipsStartersAndHonoursPrerequisites() {
        val sp = listOf(
            TestData.species(906, "Grassstarter"), TestData.species(950, "Nine"), TestData.species(1007, "Cobalt", isLegendary = true),
            TestData.species(1014, "Okidogi", isLegendary = true), TestData.species(1015, "Munkidori", isLegendary = true),
            TestData.species(1016, "Fezandipiti", isLegendary = true), TestData.species(1024, "Terapagos", isLegendary = true),
            TestData.species(1025, "Pecharunt", isMythical = true),
        )
        val d9 = TestData.data(sp)
        val s = Settings(gens = mutableSetOf(9))
        assertEquals(listOf(950), Encounters.pool(d9, Tier.NORMAL, s, 100, emptySet()))
        assertTrue(Encounters.pool(d9, Tier.STARTER, s, 100, emptySet()).contains(906))
        assertEquals(listOf(1007, 1014, 1015, 1016), Encounters.pool(d9, Tier.LEGENDARY, s, 100, emptySet()))
        assertEquals(emptyList<Int>(), Encounters.pool(d9, Tier.MYTHICAL, s, 100, setOf(1014, 1015)))
        assertEquals(listOf(1025), Encounters.pool(d9, Tier.MYTHICAL, s, 100, setOf(1014, 1015, 1016)))
        assertTrue(1024 !in Encounters.pool(d9, Tier.LEGENDARY, s, 100, emptySet()))
        assertTrue(1024 in Encounters.pool(d9, Tier.LEGENDARY, s, 100, setOf(1008)))
        assertTrue(1024 in Encounters.pool(d9, Tier.LEGENDARY, s, 100, setOf(1007)))
    }
}
