package com.husarp.ankigo.game

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regional forms in the wild and in catching (T37): made-up species, REAL form ids. */
class RegionalFormsTest {
    private val ratty = TestData.species(19, "Ratty")

    /** A regional form of [base]: own number, region generation, same flags. */
    private fun formOf(base: Species, id: Int, gen: Int, form: String = "Alola", evoLevel: Int? = null) = base.copy(
        num = id, id = "${base.id}$form".lowercase(), name = "Regional ${base.name}", gen = gen, form = form, baseNum = base.num,
        spriteId = "${base.id}-${form.lowercase()}", evoLevel = evoLevel,
    )

    private fun world(vararg species: Species) = TestData.data(species.toList())
    private fun stateOf(d: GameData) = TestData.state(d, d.species.keys.min(), 20)

    private fun formShare(d: GameData, st: GameState, wildLevel: Int = 20, n: Int = 20_000, seed: Int = 1): Map<Int, Int> {
        val rng = Random(seed)
        val counts = HashMap<Int, Int>()
        repeat(n) { counts.merge(Encounters.roll(d, st, wildLevel, rng).species, 1, Int::plus) }
        return counts
    }

    // ---- constants ----

    @Test fun formListsFollowAnkimon() {
        assertEquals(57, Encounters.FORM_IDS.size)
        assertEquals(setOf("alola", "galar", "hisui", "paldea"), Encounters.REGIONAL_FORMS.keys)
        assertEquals((listOf(10091, 10092) + (10100..10115)), Encounters.REGIONAL_FORMS.getValue("alola"))
        assertEquals((10161..10177).toList() + listOf(10179, 10180), Encounters.REGIONAL_FORMS.getValue("galar"))
        assertEquals((10229..10244).toList(), Encounters.REGIONAL_FORMS.getValue("hisui"))
        assertEquals(listOf(10250, 10251, 10252, 10253), Encounters.REGIONAL_FORMS.getValue("paldea"))
        assertEquals(mapOf("alola" to 7, "galar" to 8, "hisui" to 8, "paldea" to 9), Encounters.REGION_GEN)
        assertEquals(57, Encounters.FORM_IDS.toSet().size) // no id twice
        assertEquals(0.07, Tuning.FORM_SWAP_CHANCE, 0.0)
    }

    // ---- the pool ----

    @Test fun poolHoldsBaseSpeciesOnly() {
        val d = world(ratty, formOf(ratty, 10091, 7))
        val st = stateOf(d)
        assertEquals(listOf(19), Encounters.pool(d, Tier.NORMAL, st.settings, 20, st.caught))
        // a legendary-flagged form is no legendary pool member either
        val bird = TestData.species(144, "Frosty", isLegendary = true)
        val d2 = world(bird, formOf(bird, 10169, 8, "Galar"))
        assertEquals(listOf(144), Encounters.pool(d2, Tier.LEGENDARY, st.settings, 60, st.caught))
    }

    // ---- the swap ----

    @Test fun oneEligibleFormTurnsInAboutSevenPercent() {
        val d = world(ratty, formOf(ratty, 10091, 7))
        val counts = formShare(d, stateOf(d))
        val forms = counts[10091] ?: 0
        assertEquals(20_000, forms + (counts[19] ?: 0))
        assertTrue("forms $forms of 20000", forms in 1200..1600) // 7 % = 1400, sigma 36
    }

    @Test fun threeEligibleFormsTurnInAboutTwentyOnePercentAndUniformly() {
        val tauros = TestData.species(128, "Hornbull")
        val d = world(tauros, formOf(tauros, 10250, 9, "Paldea-Combat"), formOf(tauros, 10251, 9, "Paldea-Blaze"), formOf(tauros, 10252, 9, "Paldea-Aqua"))
        val st = stateOf(d)
        st.settings.gens += 9
        val counts = formShare(d, st)
        val forms = listOf(10250, 10251, 10252).map { counts[it] ?: 0 }
        assertTrue("forms $forms of 20000", forms.sum() in 3800..4600) // 21 % = 4200, sigma 58
        forms.forEach { assertTrue("each about a third: $forms", it in 1100..1700) }
        assertEquals(20_000, forms.sum() + (counts[128] ?: 0))
    }

    @Test fun theTierDoesNotChangeWithTheSwap() {
        val d = world(ratty, formOf(ratty, 10091, 7))
        val st = stateOf(d)
        val rng = Random(9)
        repeat(2000) { assertEquals(Tier.NORMAL, Encounters.roll(d, st, 20, rng).tier) }
    }

    @Test fun regionGenerationOffMeansNeverAForm() {
        val d = world(ratty, formOf(ratty, 10091, 7))
        val st = stateOf(d)
        st.settings.gens.remove(7)
        assertEquals(mapOf(19 to 20_000), formShare(d, st))
        assertEquals(emptyList<Int>(), Encounters.eligibleForms(d, st.settings, 19, 20, st.caught))
    }

    @Test fun baseGenerationOffMeansNoFormEither() {
        val d = world(ratty, formOf(ratty, 10091, 7))
        val st = stateOf(d)
        st.settings.gens.clear(); st.settings.gens += 7
        assertEquals(emptyList<Int>(), Encounters.eligibleForms(d, st.settings, 19, 20, st.caught))
    }

    @Test fun theFormsOwnMinimumLevelGatesIt() {
        val d = world(ratty, formOf(ratty, 10091, 7, evoLevel = 36))
        val st = stateOf(d)
        assertEquals(mapOf(19 to 5000), formShare(d, st, wildLevel = 35, n = 5000))
        val at36 = formShare(d, st, wildLevel = 36, n = 5000)
        assertTrue((at36[10091] ?: 0) > 150)
    }

    @Test fun aLegendaryBaseKeepsItsGateForTheFormToo() {
        val bird = TestData.species(144, "Frosty", isLegendary = true)
        val d = world(bird, formOf(bird, 10169, 8, "Galar"))
        val st = stateOf(d)
        assertEquals(emptyList<Int>(), Encounters.eligibleForms(d, st.settings, 144, 49, st.caught))
        assertEquals(listOf(10169), Encounters.eligibleForms(d, st.settings, 144, 50, st.caught))
        assertEquals(Tier.LEGENDARY, Encounters.tierOf(d.sp(10169)))
        assertEquals(50, Encounters.minLevel(d, d.sp(10169)))
    }

    @Test fun capturePrerequisitesUseTheBaseNumber() {
        val deep = TestData.species(249, "Deepbird", isLegendary = true)
        val d = world(deep, formOf(deep, 10169, 8, "Galar"))
        val st = stateOf(d)
        assertEquals(emptyList<Int>(), Encounters.eligibleForms(d, st.settings, 249, 100, st.caught))
        assertFalse(Encounters.isValid(d, st, 10169, 100))
        st.caught += setOf(144, 145, 146)
        assertEquals(listOf(10169), Encounters.eligibleForms(d, st.settings, 249, 100, st.caught))
        assertTrue(Encounters.isValid(d, st, 10169, 100))
    }

    // ---- random numbers ----

    @Test fun noRandomNumberIsUsedWithoutAnEligibleForm() {
        val d = world(ratty, TestData.species(16, "Birdie")) // no forms at all
        val st = stateOf(d)
        val a = Random(77)
        val r = Encounters.roll(d, st, 20, a)
        // the picks the roll made, replayed by hand: tier (nextDouble), species (nextInt over the pool), shiny (nextInt 4096)
        val b = Random(77)
        b.nextDouble()
        val pool = Encounters.pool(d, Tier.NORMAL, st.settings, 20, st.caught)
        assertEquals(pool[b.nextInt(pool.size)], r.species)
        assertEquals(b.nextInt(Tuning.SHINY_ODDS) == 0, r.shiny)
        assertEquals(b.nextInt(), a.nextInt()) // both are exactly here: nothing extra was drawn
    }

    @Test fun noRandomNumberWhenTheFormsAreSwitchedOff() {
        val d = world(ratty, formOf(ratty, 10091, 7))
        val st = stateOf(d)
        st.settings.gens.remove(7)
        val a = Random(5)
        Encounters.roll(d, st, 20, a)
        val b = Random(5)
        b.nextDouble(); b.nextInt(1); b.nextInt(Tuning.SHINY_ODDS)
        assertEquals(b.nextInt(), a.nextInt())
    }

    @Test fun anEligibleFormDrawsOneNumberAndTwoOnASwap() {
        val d = world(ratty, formOf(ratty, 10091, 7))
        val st = stateOf(d)
        var sawSwap = false; var sawKeep = false
        for (seed in 1..400) {
            val a = Random(seed)
            val r = Encounters.roll(d, st, 20, a)
            val b = Random(seed)
            b.nextDouble(); b.nextInt(1) // tier, species
            val swapped = b.nextDouble() < Tuning.FORM_SWAP_CHANCE
            if (swapped) b.nextInt(1)
            b.nextInt(Tuning.SHINY_ODDS)
            assertEquals(if (swapped) 10091 else 19, r.species)
            assertEquals(b.nextInt(), a.nextInt())
            if (swapped) sawSwap = true else sawKeep = true
        }
        assertTrue(sawSwap && sawKeep)
    }

    // ---- the pre-rolled next encounter ----

    @Test fun isValidAcceptsAFormWithTheSameChecks() {
        val d = world(ratty, formOf(ratty, 10091, 7, evoLevel = 36))
        val st = stateOf(d)
        assertFalse(Encounters.isValid(d, st, 10091, 35)) // level gate of the form
        assertTrue(Encounters.isValid(d, st, 10091, 36))
        st.settings.gens.remove(7)
        assertFalse(Encounters.isValid(d, st, 10091, 36)) // region generation off
        st.settings.gens += 7; st.settings.gens.remove(1)
        assertFalse(Encounters.isValid(d, st, 10091, 36)) // base species' generation off
        assertFalse(Encounters.isValid(d, st, 10095, 36)) // unknown id
    }

    @Test fun aStarterLineFormIsNeverWild() {
        val starter = TestData.species(157, "Flamer")
        val d = world(starter, formOf(starter, 10233, 8, "Hisui"))
        val st = stateOf(d)
        assertEquals(Tier.STARTER, Encounters.tierOf(d.sp(10233))) // by the base number
        assertFalse(Encounters.isValid(d, st, 10233, 100))
        assertEquals(listOf(157), Encounters.pool(d, Tier.STARTER, st.settings, 100, st.caught)) // the base species only, never the form
    }

    @Test fun fossilAndUltraBeastMembershipFollowsTheBaseNumber() {
        val fossil = formOf(TestData.species(138, "Shellfossil"), 10300, 8)
        val beast = formOf(TestData.species(794, "Buzzbeast"), 10301, 8)
        assertEquals(null, Encounters.tierOf(fossil))
        assertEquals(Tier.ULTRA, Encounters.tierOf(beast))
    }

    // ---- catching ----

    private val alolaRatty = formOf(ratty, 10091, 7)
    private val d = world(ratty, alolaRatty)

    @Test fun aRegionalFormIsProtectedWhenTheSettingIsOn() {
        val s = Settings()
        assertTrue(s.protectRegional) // default on
        assertTrue(Catching.isProtected(s, d.sp(10091), Tier.NORMAL))
        assertFalse(Catching.isProtected(s, d.sp(19), Tier.NORMAL)) // the base species is not
        s.protectRegional = false
        assertFalse(Catching.isProtected(s, d.sp(10091), Tier.NORMAL))
    }

    @Test fun aStarterLineFormIsProtectedAsAStarterToo() {
        val starter = TestData.species(157, "Flamer")
        val f = formOf(starter, 10233, 8, "Hisui")
        val s = Settings()
        s.protectRegional = false
        assertTrue(Catching.isProtected(s, f, Tier.NORMAL)) // starter list by base number
        s.protectStarter = false
        assertFalse(Catching.isProtected(s, f, Tier.NORMAL))
    }

    @Test fun decideCatchesAProtectedFormEvenWhenOwnedAndOnDefeatMode() {
        val s = TestData.state(d, 19, 20)
        s.caught += 10091
        s.settings.autoMode = 2
        val w = TestData.mon(d, 10091, 10, uid = "wild")
        val battle = Battle(w, Tier.NORMAL)
        assertEquals(Outcome.CATCH, Catching.decide(d, s, battle))
        s.settings.protectRegional = false
        assertEquals(Outcome.DEFEAT, Catching.decide(d, s, battle))
        s.settings.autoMode = 3 // a form you never caught is a new species
        s.caught -= 10091
        assertEquals(Outcome.CATCH, Catching.decide(d, s, battle))
    }

    @Test fun aWildFormIsAMonOfTheFormSpecies() {
        val m = Encounters.makeWild(d, Encounters.Roll(10091, Tier.NORMAL, false), 12, Random(1), 0L)
        assertEquals(10091, m.species)
        assertEquals("Regional Ratty", m.name)
        assertNotNull(d.species[m.species])
        assertEquals(StatCalc.maxHp(d, m), m.hp)
    }
}
