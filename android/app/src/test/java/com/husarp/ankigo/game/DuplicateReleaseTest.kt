package com.husarp.ankigo.game

import com.husarp.ankigo.game.save.SaveCodec
import com.husarp.ankigo.ui.ReleaseDuplicatesText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * R10 B21 "Release duplicates" (DATA LOSS): the choice rule ([Duplicates.releasePlan]), the action ([Game.releaseDuplicates]) and the
 * words of the confirmation. Made-up species only. Team "main" (Sparkmouse 25, Lv 20, the main); everything else is added per test.
 */
class DuplicateReleaseTest {
    private val data = TestData.world()

    /** A Pokémon with all six IVs [iv] (TestData.mon has fixed IVs). */
    private fun mon(species: Int, level: Int, uid: String, iv: Int = 15, caught: Long = 1000L, edit: Mon.() -> Unit = {}): Mon {
        val sp = data.sp(species)
        val m = Mon(
            uid = uid, species = species, name = sp.name, level = level, xp = 0, ivs = Stats(iv, iv, iv, iv, iv, iv), evs = Stats.ZERO,
            nature = Nature.HARDY, gender = 'M', shiny = false, ability = "Testability", moves = mutableListOf("tackle"), hp = 1,
        )
        m.hp = StatCalc.maxHp(data, m)
        m.caughtAt = caught
        m.edit()
        return m
    }

    private fun state(vararg box: Mon): GameState {
        val s = TestData.state(data, 25, 20, listOf("tackle"))
        box.forEach { s.mons += it; s.seen += it.species; s.caught += it.species }
        return s
    }

    private fun game(s: GameState) = Game(data, s, Random(1)) { 0L }
    private fun plan(s: GameState) = Duplicates.releasePlan(data, s)

    /** The player confirms exactly what a plan made from the state right now says (nothing changed between dialog and tap). */
    private fun Game.releasePlanned() = releaseDuplicates(Duplicates.releasePlan(data, state).release.toSet())

    private fun monSnap(m: Mon) = "${m.uid}|${m.species}|${m.level}|${m.ivs.total}|${m.caughtAt}|${m.favourite}|${m.shiny}|${m.heldItem}|${m.nickname}|${m.tier}|${m.ankimonId}|${m.hp}|${m.xp}"

    /** Everything the action could touch, as text (a snapshot to compare). */
    private fun snap(s: GameState): String = buildString {
        for (m in s.mons) append(monSnap(m)).append(';')
        append("\nteam=${s.team}\nxp=${s.settings.xpShareUid}\nbag=${s.bag.toSortedMap()}\ncash=${s.cash}\nreleased=${s.releasedAnkimon}\ncaught=${s.caught.sorted()}")
    }

    // ---- the rule: who stays ----

    @Test fun theHighestLevelCopyStaysAndTheRestGo() {
        val s = state(mon(16, 10, "a"), mon(16, 30, "k"), mon(16, 20, "b"))
        val p = plan(s)
        assertEquals(listOf("b", "a"), p.release) // best copy first inside the species; never "k"
        assertEquals(2, p.count)
        assertEquals(listOf(Triple(16, "Birdie", 2)), p.groups.map { Triple(it.species, it.name, it.count) })
        assertEquals(0, p.keptCount)
    }

    @Test fun aLevelTieIsDecidedByTheIvTotal() {
        val s = state(mon(16, 20, "lowIv", iv = 10), mon(16, 20, "highIv", iv = 25), mon(16, 20, "midIv", iv = 15))
        assertEquals(listOf("midIv", "lowIv"), plan(s).release) // highIv stays
        // a higher level still beats a higher IV total
        val t = state(mon(16, 21, "lvl", iv = 0), mon(16, 20, "iv", iv = 31))
        assertEquals(listOf("iv"), plan(t).release)
    }

    @Test fun aFullTieKeepsTheEarliestCaughtThenTheLowestUid() {
        val s = state(mon(16, 20, "x", caught = 500), mon(16, 20, "y", caught = 200), mon(16, 20, "z", caught = 900))
        assertEquals(listOf("x", "z"), plan(s).release) // y, caught first, stays
        val t = state(mon(16, 20, "b", caught = 700), mon(16, 20, "a", caught = 700), mon(16, 20, "c", caught = 700))
        assertEquals(listOf("b", "c"), plan(t).release) // same date: the lowest uid "a" stays
        // a copy without a catch date (0) ranks after every dated one
        val u = state(mon(16, 20, "undated", caught = 0), mon(16, 20, "dated", caught = 5000))
        assertEquals(listOf("undated"), plan(u).release)
        val v = state(mon(16, 20, "u2", caught = 0), mon(16, 20, "u1", caught = 0))
        assertEquals(listOf("u2"), plan(v).release)
    }

    @Test fun theChoiceDoesNotDependOnTheListOrder() {
        val copies = listOf(mon(16, 20, "x", caught = 500), mon(16, 20, "y", caught = 500), mon(16, 25, "k"), mon(16, 20, "z", iv = 3), mon(19, 8, "r1"), mon(19, 8, "r2"))
        val base = plan(state(*copies.toTypedArray()))
        val rng = Random(7)
        repeat(20) {
            val shuffled = state(*copies.shuffled(rng).toTypedArray())
            val p = plan(shuffled)
            assertEquals(base.release, p.release)
            assertEquals(base.groups.map { it.species to it.count }, p.groups.map { it.species to it.count })
        }
    }

    @Test fun aSingleCopyIsNeverTouched() {
        val s = state(mon(16, 5, "only16"), mon(19, 40, "only19"), mon(54, 1, "only54"))
        assertTrue(plan(s).isEmpty)
        assertEquals(0, plan(s).keptCount)
        assertEquals(emptyList<String>(), plan(s).release)
    }

    @Test fun differentSpeciesAreNotDuplicatesOfEachOther() {
        // an evolution line is not a duplicate group: 25 (main) and 26 and 172 are three species
        val s = state(mon(26, 40, "evo"), mon(172, 5, "baby"))
        assertTrue(plan(s).isEmpty)
    }

    @Test fun aRegionalFormIsItsOwnSpeciesNumberAndItsOwnGroup() {
        val form = data.sp(19).copy(num = 10091, id = "rattyalola", name = "Alolan Ratty", gen = 7, form = "Alola", baseNum = 19, spriteId = "ratty-alola")
        val d = GameData(data.species + (10091 to form), data.moves, data.growth, data.itemPrices)
        fun m(species: Int, level: Int, uid: String) = TestData.mon(d, species, level, uid = uid)
        val s = TestData.state(d, 25, 20, listOf("tackle"))
        s.settings.protectRegional = false // otherwise a regional form is a protected kind (see below)
        s.mons += listOf(m(19, 30, "base"), m(10091, 10, "formOnly"))
        assertTrue("one base + one form: no duplicates", Duplicates.releasePlan(d, s).isEmpty)
        s.mons += listOf(m(19, 12, "base2"), m(10091, 20, "form2"))
        val p = Duplicates.releasePlan(d, s)
        assertEquals(listOf("base2", "formOnly"), p.release) // each group keeps its own best: base (Lv 30), form2 (Lv 20)
        assertEquals(setOf(19, 10091), p.groups.map { it.species }.toSet())
        s.settings.protectRegional = true // a regional form's copies are a protected kind: the base group goes, the form group is kept
        val q = Duplicates.releasePlan(d, s)
        assertEquals(listOf("base2"), q.release)
        assertEquals(mapOf(Duplicates.Keep.PROTECTED_KIND to 1), q.keptProtected)
    }

    // ---- the rule: what is never released ----

    /** What makes a copy protected, and how the test sets it up. */
    private val protections: List<Pair<Duplicates.Keep, (GameState, Mon) -> Unit>> = listOf(
        Duplicates.Keep.FAVOURITE to { _, m -> m.favourite = true },
        Duplicates.Keep.SHINY to { _, m -> m.shiny = true },
        Duplicates.Keep.TEAM to { s, m -> s.team += m.uid },
        Duplicates.Keep.HOLDS_ITEM to { _, m -> m.heldItem = "everstone" },
        Duplicates.Keep.PROTECTED_KIND to { _, m -> m.tier = Tier.ULTRA },
        Duplicates.Keep.EXP_SHARE to { s, m -> s.settings.xpShareUid = m.uid },
        Duplicates.Keep.NICKNAME to { _, m -> m.nickname = "Bobby" },
    )

    @Test fun everyProtectionKeepsALowerLevelCopy() {
        assertEquals("a protection is missing from the test", Duplicates.Keep.entries.toSet(), protections.map { it.first }.toSet())
        for ((why, protect) in protections) {
            val p = mon(16, 10, "p")
            val s = state(mon(16, 30, "k"), p, mon(16, 5, "x"))
            protect(s, p)
            val plan = plan(s)
            assertEquals("$why: only the plain lower copy goes", listOf("x"), plan.release)
            assertEquals("$why: counted as kept for that reason", mapOf(why to 1), plan.keptProtected)
            assertEquals(1, plan.keptCount)
            // the control: the same copy without the protection is released too
            val plain = state(mon(16, 30, "k"), mon(16, 10, "p"), mon(16, 5, "x"))
            assertEquals(listOf("p", "x"), plan(plain).release)
        }
    }

    @Test fun theExpShareHolderIsKeptEvenWhenAnotherCopyOutranksIt() {
        val s = state(mon(16, 30, "k"), mon(16, 12, "holder"), mon(16, 12, "other"))
        s.settings.xpShareUid = "holder"
        assertEquals(listOf("other"), plan(s).release)
        assertEquals("holder", s.settings.xpShareUid)
    }

    @Test fun aProtectedKindSwitchedOffCanBeReleased() {
        val s = state(mon(16, 30, "k"), mon(16, 10, "u") { tier = Tier.ULTRA })
        assertTrue(plan(s).isEmpty)
        s.settings.protectUltra = false
        assertEquals(listOf("u"), plan(s).release)
        assertTrue(plan(s).keptProtected.isEmpty())
    }

    @Test fun aProtectedCopyThatIsTheHighestLevelIsTheKeeperAndTheOthersGo() {
        val s = state(mon(16, 30, "fav") { favourite = true }, mon(16, 20, "a"), mon(16, 10, "b"))
        val p = plan(s)
        assertEquals(listOf("a", "b"), p.release)
        assertEquals("the keeper is not counted as 'kept because protected'", 0, p.keptCount)
    }

    @Test fun whenEveryNonKeeperIsProtectedNothingIsReleased() {
        val s = state(mon(16, 30, "k"), mon(16, 20, "f") { favourite = true }, mon(16, 10, "s") { shiny = true }, mon(16, 5, "n") { nickname = "Bob" })
        val p = plan(s)
        assertTrue(p.isEmpty)
        assertEquals(3, p.keptCount)
        assertEquals(mapOf(Duplicates.Keep.FAVOURITE to 1, Duplicates.Keep.SHINY to 1, Duplicates.Keep.NICKNAME to 1), p.keptProtected)
    }

    @Test fun theFirstMatchingReasonIsTheOneCounted() {
        // a favourite shiny holding an item counts once, as the favourite (Duplicates.keepReason order)
        val s = state(mon(16, 30, "k"), mon(16, 10, "all") { favourite = true; shiny = true; heldItem = "everstone"; nickname = "X" })
        assertEquals(mapOf(Duplicates.Keep.FAVOURITE to 1), plan(s).keptProtected)
    }

    @Test fun theMainAndTheTeamAreNeverInThePlan() {
        // the main is a duplicate of a higher-level box copy, and so is a team member: both are protected
        val s = state(mon(25, 40, "boxBest"), mon(16, 35, "boxBird"), mon(16, 8, "teamBird"))
        s.team += "teamBird"
        val p = plan(s)
        assertTrue("main" !in p.release)
        assertTrue("teamBird" !in p.release)
        assertTrue(p.isEmpty)
        assertEquals(mapOf(Duplicates.Keep.TEAM to 2), p.keptProtected) // main (25) and teamBird (16)
    }

    @Test fun theGroupsAreCountedPerSpeciesMostFirstThenByName() {
        val s = state(
            mon(54, 30, "d0"), mon(54, 5, "d1"),                                   // Duckling: 1
            mon(19, 30, "r0"), mon(19, 5, "r1"), mon(19, 6, "r2"),                 // Ratty: 2
            mon(16, 30, "b0"), mon(16, 5, "b1"), mon(16, 6, "b2"), mon(16, 7, "b3"), // Birdie: 3
            mon(92, 30, "s0"), mon(92, 5, "s1"),                                   // Spooky: 1
        )
        val p = plan(s)
        assertEquals(listOf("Birdie" to 3, "Ratty" to 2, "Duckling" to 1, "Spooky" to 1), p.groups.map { it.name to it.count })
        assertEquals(7, p.count)
        assertEquals(p.release.size, p.groups.sumOf { it.count })
        assertEquals(p.release.toSet().size, p.release.size)
    }

    @Test fun anEmptyPlanForAMainOnlyGame() {
        val p = plan(TestData.state(data, 25, 20, listOf("tackle")))
        assertTrue(p.isEmpty); assertEquals(0, p.count); assertEquals(0, p.keptCount)
        assertTrue(p.groups.isEmpty()); assertTrue(p.release.isEmpty())
    }

    @Test fun unsupportedPokemonAreNotPartOfIt() {
        val s = state(mon(16, 30, "k"), mon(16, 10, "x"))
        s.unsupported += UnsupportedMon("ank-1", 16, "Birdie", null, 5, false, "not in data", null)
        s.unsupported += UnsupportedMon("ank-2", 16, "Birdie", null, 6, false, "not in data", null)
        assertEquals(listOf("x"), plan(s).release)
        game(s).releasePlanned()
        assertEquals(listOf("ank-1", "ank-2"), s.unsupported.map { it.ankimonId })
    }

    @Test fun thePlanNeverChangesTheState() {
        val s = state(mon(16, 30, "k"), mon(16, 10, "x"), mon(19, 3, "r1"), mon(19, 4, "r2"))
        s.settings.xpShareUid = "k"
        val before = snap(s)
        plan(s); plan(s)
        assertEquals(before, snap(s))
    }

    // ---- the rule on many random games ----

    @Test fun manyRandomGamesKeepEveryInvariant() {
        val rng = Random(2026)
        val species = listOf(16, 19, 54, 92, 25, 133, 129, 63)
        repeat(400) { round ->
            val mons = (0 until rng.nextInt(1, 26)).map { i ->
                mon(species.random(rng), rng.nextInt(1, 6) * 5, "m$i", iv = listOf(10, 11, 12).random(rng), caught = listOf(0L, 100L, 200L).random(rng)) {
                    favourite = rng.nextInt(10) == 0; shiny = rng.nextInt(12) == 0
                    heldItem = if (rng.nextInt(10) == 0) "everstone" else null
                    nickname = if (rng.nextInt(10) == 0) "Nick" else null
                    tier = if (rng.nextInt(15) == 0) Tier.ULTRA else Tier.NORMAL
                }
            }
            val s = GameState(createdAt = 1L, day = "2026-10-13", starterChosen = true)
            s.mons += mons
            s.team += mons.shuffled(rng).take(rng.nextInt(1, 7)).map { it.uid }
            if (rng.nextInt(3) == 0) s.settings.xpShareUid = mons.random(rng).uid
            if (rng.nextInt(4) == 0) s.settings.protectUltra = false
            val before = snap(s)
            val p = plan(s)
            assertEquals("round $round: planning changes nothing", before, snap(s))
            val uids = mons.map { it.uid }.toSet()
            assertEquals("round $round: no uid twice", p.release.size, p.release.toSet().size)
            assertTrue("round $round: only existing Pokémon", uids.containsAll(p.release))
            for (uid in p.release) {
                val m = s.mon(uid)!!
                assertNull("round $round: $uid is protected", Duplicates.keepReason(data, s, m))
                assertFalse("round $round: $uid is in the team", uid in s.team)
            }
            assertFalse("round $round: the main is never released", s.team.first() in p.release)
            // the keeper of every species, worked out here on its own: highest level, IV total, earliest dated, uid
            for ((num, copies) in mons.groupBy { it.species }) {
                val keeper = copies.sortedWith(
                    compareByDescending<Mon> { it.level }.thenByDescending { it.ivs.total }
                        .thenBy { if (it.caughtAt > 0) it.caughtAt else Long.MAX_VALUE }.thenBy { it.uid },
                ).first()
                assertFalse("round $round: the keeper $keeper of $num is in the plan", keeper.uid in p.release)
                assertTrue("round $round: species $num lost every copy", copies.any { it.uid !in p.release })
                if (copies.size == 1) assertFalse(copies[0].uid in p.release)
            }
            // run it: exactly the plan goes, a second run finds nothing
            val g = game(s)
            val r = g.releasePlanned()
            assertEquals(p.release.toSet(), uids - s.mons.map { it.uid }.toSet())
            assertEquals(!p.isEmpty, r.ok)
            assertTrue("round $round: nothing left to release", plan(s).isEmpty)
            assertFalse(g.releasePlanned().ok)
        }
    }

    // ---- the action ----

    /** main(25, team) + Birdie k Lv30, a Lv10, b Lv5 + Ratty r1 Lv20, r2 Lv9 + one item holder and one favourite Birdie. */
    private fun busy(): GameState {
        val s = state(
            mon(16, 30, "k"), mon(16, 10, "a") { ankimonId = "ank-a" }, mon(16, 5, "b"), mon(16, 4, "held") { heldItem = "everstone" }, mon(16, 3, "fav") { favourite = true },
            mon(19, 20, "r1"), mon(19, 9, "r2"), mon(54, 11, "solo"),
        )
        s.bag["potion"] = 3; s.bag["everstone"] = 1; s.cash = 777
        return s
    }

    @Test fun theActionReleasesExactlyThePlanAndNothingElseChanges() {
        val s = busy()
        val p = plan(s)
        assertEquals(listOf("a", "b", "r2"), p.release)
        val mainBefore = s.mon("main")!!
        val survivors = s.mons.filter { it.uid !in p.release }.associate { it.uid to monSnap(it) }
        val teamBefore = s.team.toList(); val bagBefore = s.bag.toMap(); val cashBefore = s.cash; val xpBefore = s.settings.xpShareUid
        val r = game(s).releasePlanned()
        assertTrue(r.ok)
        assertEquals("Released 3 Pokémon", r.message)
        assertTrue("same events as a normal release: none", r.events.isEmpty())
        assertEquals(listOf("main", "k", "held", "fav", "r1", "solo"), s.mons.map { it.uid })
        assertEquals(teamBefore, s.team); assertSame(mainBefore, s.main)
        assertEquals(xpBefore, s.settings.xpShareUid)
        assertEquals(bagBefore, s.bag.toMap()); assertEquals(cashBefore, s.cash)
        assertEquals("the item holder keeps its item and the bag got nothing back", "everstone", s.mon("held")!!.heldItem)
        for (m in s.mons) assertEquals("${m.uid} is untouched", survivors[m.uid], monSnap(m))
        assertTrue(s.mon("fav")!!.favourite)
    }

    private fun assertSame(a: Any?, b: Any?) = assertTrue("not the same object", a === b)

    @Test fun releasedPokemonAreRememberedForTheAnkimonExport() {
        val s = busy()
        game(s).releasePlanned()
        // "a" came from Ankimon (ankimonId), the others have none: their uid is remembered, as release() does
        assertEquals(listOf("ank-a", "b", "r2"), s.releasedAnkimon.toList())
    }

    @Test fun theActionDoesTheSameAsReleasingEachOneByHand() {
        val bulk = busy(); val byHand = busy()
        game(bulk).releasePlanned()
        val g = game(byHand)
        for (uid in plan(busy()).release) assertTrue(g.release(uid).ok)
        assertEquals(snap(byHand), snap(bulk))
    }

    @Test fun aSecondRunRightAfterFindsNothing() {
        val s = busy()
        val g = game(s)
        assertTrue(g.releasePlanned().ok)
        val after = snap(s)
        val r = g.releasePlanned()
        assertFalse(r.ok)
        assertEquals("No duplicates to release", r.message)
        assertTrue(r.events.isEmpty())
        assertEquals(after, snap(s))
    }

    @Test fun nothingToReleaseChangesNothing() {
        val s = state(mon(16, 30, "k"), mon(16, 10, "f") { favourite = true }, mon(19, 5, "solo"))
        val before = snap(s)
        val r = game(s).releasePlanned()
        assertFalse(r.ok)
        assertEquals(before, snap(s))
        val lone = TestData.state(data, 25, 20, listOf("tackle"))
        assertFalse(game(lone).releasePlanned().ok)
        assertEquals(1, lone.mons.size)
    }

    @Test fun onlyWhatWasConfirmedAndIsStillPlannedGoes() {
        val s = state(mon(16, 30, "k"), mon(16, 10, "a"), mon(16, 5, "b"))
        val shown = plan(s)
        assertEquals(listOf("a", "b"), shown.release)
        val confirmed = shown.release.toSet()
        // meanwhile: "a" becomes a favourite, another low copy appears (never confirmed)
        s.mon("a")!!.favourite = true
        s.mons += mon(16, 3, "e")
        val r = game(s).releaseDuplicates(confirmed)
        assertTrue(r.ok)
        assertEquals("Released 1 of 2 — your Pokémon changed", r.message)
        assertEquals(listOf("main", "k", "a", "e"), s.mons.map { it.uid })
    }

    @Test fun aHigherLevelCopyArrivingWhileTheDialogIsOpenDoesNotReleaseTheFormerKeeper() {
        val s = state(mon(16, 30, "k"), mon(16, 10, "a"), mon(16, 5, "b"))
        val confirmed = plan(s).release.toSet()
        assertEquals(setOf("a", "b"), confirmed)
        s.mons += mon(16, 40, "c") // "k" is no longer the keeper and would be in a fresh plan, but it was never confirmed
        assertTrue("k is releasable now", "k" in plan(s).release)
        val r = game(s).releaseDuplicates(confirmed)
        assertEquals("Released 2 Pokémon", r.message)
        assertEquals(listOf("main", "k", "c"), s.mons.map { it.uid })
    }

    @Test fun aConfirmedPokemonThatBecameAFavouriteOrTeamMemberStays() {
        val s = state(mon(16, 30, "k"), mon(16, 10, "a"), mon(16, 5, "b"), mon(16, 4, "t"))
        val confirmed = plan(s).release.toSet()
        assertEquals(setOf("a", "b", "t"), confirmed)
        s.mon("a")!!.favourite = true
        s.team += "t"
        val r = game(s).releaseDuplicates(confirmed)
        assertEquals("Released 1 of 3 — your Pokémon changed", r.message)
        assertEquals(listOf("main", "k", "a", "t"), s.mons.map { it.uid })
        assertEquals(listOf("main", "t"), s.team)
    }

    @Test fun aNewDuplicateThatAppearedWhileTheDialogIsOpenIsNotReleased() {
        val s = state(mon(16, 30, "k"), mon(16, 10, "a"))
        val confirmed = plan(s).release.toSet()
        s.mons += mon(16, 2, "n"); s.mons += mon(19, 20, "r1"); s.mons += mon(19, 5, "r2")
        assertEquals(setOf("a", "n", "r2"), plan(s).release.toSet())
        val r = game(s).releaseDuplicates(confirmed)
        assertEquals("Released 1 Pokémon", r.message)
        assertEquals(listOf("main", "k", "n", "r1", "r2"), s.mons.map { it.uid })
    }

    @Test fun nothingConfirmedCanStillGoChangesNothing() {
        val s = state(mon(16, 30, "k"), mon(16, 10, "a"), mon(16, 5, "b"))
        val confirmed = plan(s).release.toSet()
        // the player favourites both; a different, unconfirmed copy is now releasable
        s.mon("a")!!.favourite = true; s.mon("b")!!.favourite = true
        s.mons += mon(16, 1, "x")
        val before = snap(s)
        val r = game(s).releaseDuplicates(confirmed)
        assertFalse(r.ok)
        assertEquals("Nothing released — your Pokémon changed", r.message)
        assertTrue(r.events.isEmpty())
        assertEquals(before, snap(s))
        // an empty confirmation never releases anything either, whatever the plan says
        assertFalse(game(s).releaseDuplicates(emptySet()).ok)
        assertEquals(before, snap(s))
    }

    @Test fun anUnknownUidInTheConfirmationIsIgnored() {
        val s = state(mon(16, 30, "k"), mon(16, 10, "a"))
        val r = game(s).releaseDuplicates(setOf("a", "ghost"))
        assertEquals("Released 1 of 2 — your Pokémon changed", r.message)
        assertEquals(listOf("main", "k"), s.mons.map { it.uid })
    }

    @Test fun anOldSaveIsUnaffectedAndStillSavesAndLoads() {
        val old = """{"schema":1,"team":["main"],"settings":{"autoMode":1},"mons":[
            {"uid":"main","species":25,"level":20},{"uid":"d1","species":16,"level":12},{"uid":"d2","species":16,"level":9},{"uid":"d3","species":16,"level":12},
            {"uid":"lone","species":19,"level":7}]}"""
        val s = SaveCodec.decode(old)
        assertFalse(s.settings.autoReleaseDuplicates)
        val p = plan(s)
        assertEquals(listOf("d3", "d2"), p.release) // d1 and d3 tie on everything (old save: no IVs, no date): the lower uid d1 stays
        assertTrue(game(s).releasePlanned().ok)
        assertEquals(listOf("main", "d1", "lone"), s.mons.map { it.uid })
        val back = SaveCodec.decode(SaveCodec.encode(s))
        assertEquals(listOf("main", "d1", "lone"), back.mons.map { it.uid })
        assertEquals(listOf("d3", "d2"), back.releasedAnkimon.toList())
        assertEquals(SaveCodec.encode(s), SaveCodec.encode(back))
        assertTrue(plan(back).isEmpty)
    }

    @Test fun oneActionIsOneChangeNotOneReleasePerPokemon() {
        // The UI runs the action once (page.act { releaseDuplicates() } -> one GameRepo.run -> one scheduled save); the action itself is one
        // call that plans once, so a second plan after it finds nothing and the state changed in that single call.
        val s = busy()
        val g = game(s)
        val seq = s.seq
        g.releasePlanned()
        assertEquals("the game itself never saves (GameRepo does, once per run)", seq, s.seq)
        assertTrue(plan(s).isEmpty)
    }

    // ---- the words ----

    private fun planOf(vararg m: Mon): ReleasePlan = plan(state(*m))

    @Test fun theTitleAndButtonSayExactlyHowMany() {
        val p = plan(busy())
        assertEquals("Release 3 Pokémon?", ReleaseDuplicatesText.title(p))
        assertEquals("Release 3", ReleaseDuplicatesText.button(p))
    }

    @Test fun theSpeciesListIsCutAfterSixWithAndNMore() {
        val nums = listOf(16, 19, 54, 92, 129, 63, 133, 134) // 8 species, 2 copies each
        val s = state(*nums.flatMap { listOf(mon(it, 30, "k$it"), mon(it, 5, "x$it")) }.toTypedArray())
        val p = plan(s)
        assertEquals(8, p.groups.size)
        val text = ReleaseDuplicatesText.species(p)
        assertEquals(6, text.substringBefore(", and ").split(", ").size)
        assertTrue(text, text.endsWith(", and 2 more"))
        val six = plan(state(*nums.take(6).flatMap { listOf(mon(it, 30, "k$it"), mon(it, 5, "x$it")) }.toTypedArray()))
        assertFalse(ReleaseDuplicatesText.species(six).contains("more"))
        assertEquals("Birdie ×3, Ratty ×1", ReleaseDuplicatesText.species(planOf(mon(16, 30, "k"), mon(16, 1, "a"), mon(16, 2, "b"), mon(16, 3, "c"), mon(19, 30, "r"), mon(19, 1, "r2"))))
    }

    @Test fun theMessageStatesTheRuleTheProtectedCopiesTheRewardAndThatItCannotBeUndone() {
        val p = plan(busy()) // kept: the item holder and the favourite
        val m = ReleaseDuplicatesText.message(p)
        assertTrue(m, m.contains("Birdie ×2, Ratty ×1"))
        assertTrue(m, m.contains("Keeps the highest-level copy of each species."))
        assertTrue(m, m.contains("2 duplicates stay because they are protected (1 favourite, 1 holding an item)."))
        assertTrue(m, m.contains("Releasing gives no money or items."))
        assertTrue(m, m.contains("This can't be undone."))
        val one = ReleaseDuplicatesText.message(planOf(mon(16, 30, "k"), mon(16, 1, "x"), mon(16, 2, "f") { shiny = true }))
        assertTrue(one, one.contains("1 duplicate stays because it is protected (1 shiny)."))
        val none = ReleaseDuplicatesText.message(planOf(mon(16, 30, "k"), mon(16, 1, "x")))
        assertFalse(none, none.contains("protected"))
        assertNotNull(none)
    }
}
