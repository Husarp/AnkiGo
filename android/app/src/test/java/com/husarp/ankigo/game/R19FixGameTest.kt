package com.husarp.ankigo.game

import com.husarp.ankigo.game.R17C8.attacks
import com.husarp.ankigo.game.R17C8.effects
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * R19-FIX (FIX-1), the game parts: a saved release question never releases a Pokémon that changed meanwhile (CX-1), keeping its place never
 * doubles a team entry (CX-2), Imposter transforms before the round's moves are picked (CX-3) and not through Skill Swap (NICE-6).
 */
class R19FixGameTest {
    // ---------------------------------------------------------------- CX-1 / CX-2: the release question

    private val data = TestData.world()
    private var now = 1_000L

    private fun mon(species: Int, level: Int, uid: String, ivTotal: Int = 90): Mon {
        val a = IntArray(6); var left = ivTotal
        for (i in a.indices) { a[i] = minOf(31, left); left -= a[i] }
        val b = TestData.mon(data, species, level, listOf("tackle"), uid)
        return Mon(uid = uid, species = species, name = b.name, level = level, xp = 0, ivs = Stats(a[0], a[1], a[2], a[3], a[4], a[5]), evs = Stats.ZERO,
            nature = Nature.HARDY, gender = 'M', shiny = false, ability = "Testability", moves = mutableListOf("tackle"), hp = b.hp).also { it.hp = StatCalc.maxHp(data, it) }
    }

    /** Team: main (Sparkmouse 25). Box: d = Birdie 16, Lv 20, IV 90 (in the Team and a favourite, so a replace opens the question). */
    private fun state(): GameState {
        val s = TestData.state(data, 25, 20, listOf("tackle", "thunderbolt"))
        s.mons += mon(16, 20, "d"); s.caught += 16
        s.team += "d"; s.mon("d")!!.favourite = true
        CatchChoice.apply(s.settings, CatchChoice.ASK_ME)
        return s
    }

    private fun game(s: GameState) = Game(data, s, Random(1)) { now }.also { s.day = it.dayKey() }

    /** A Lv 30 wild Birdie beat the Lv 20 one and "Catch and replace" was chosen: the release question waits. Returns the game and the new copy. */
    private fun asked(s: GameState = state()): Triple<Game, GameState, Mon> {
        val g = game(s)
        s.asks += PendingAsk(PendingAsk.Kind.CATCH, "wild", mon(16, 30, "wild", ivTotal = 120), Tier.NORMAL)
        assertTrue(g.answerCatch("wild", AskChoice.REPLACE).ok)
        val q = s.asks.single()
        assertEquals(PendingAsk.Kind.REPLACE, q.kind)
        return Triple(g, s, s.mon(q.uid)!!)
    }

    private fun assertKeptBoth(g: Game, s: GameState, new: Mon, teamBefore: List<String>) {
        val r = g.answerReplace(new.uid, release = true, keepPlace = true)
        assertFalse(r.ok); assertEquals(AskMe.STALE, r.message)
        assertTrue("the question is answered", s.asks.isEmpty())
        assertNotNull("the old one stays", s.mon("d")); assertNotNull("the new one stays", s.mon(new.uid))
        assertEquals("the team is as it was", teamBefore, s.team)
        assertFalse("nothing is handed over", new.favourite)
        assertFalse("nothing is remembered as released", "d" in s.releasedAnkimon)
    }

    @Test fun anOldCopyThatLevelledPastTheNewOneIsKeptWhenItIsNotWorseByAnyNumber() {
        val (g, s, new) = asked()
        // it levelled up to 45 but its IVs (90) are still lower than the new copy's (120): "better" is level OR IV total, so the pair stands
        s.mon("d")!!.level = 45
        assertTrue(AskMe.stillReplaces(data, s, new, s.mon("d")!!))
        // the old copy is not worse by either number any more (Lv 45, IV 130): stale
        s.mons[s.mons.indexOf(s.mon("d")!!)] = mon(16, 45, "d", ivTotal = 130)
        assertKeptBoth(g, s, new, listOf("main", "d"))
    }

    @Test fun anEqualCopyIsNotWorseAndIsKept() {
        val (g, s, new) = asked()
        val twin = mon(16, new.level, "d", ivTotal = new.ivs.total)       // same level, same IV total: "equal is not better"
        s.mons[s.mons.indexOf(s.mon("d")!!)] = twin
        assertKeptBoth(g, s, new, listOf("main", "d"))
    }

    @Test fun anOldCopyThatEvolvedIsAnotherSpeciesAndIsKept() {
        val (g, s, new) = asked()
        s.mon("d")!!.species = 17           // Birdie evolved into another species meanwhile
        assertKeptBoth(g, s, new, listOf("main", "d"))
    }

    @Test fun aNewCopyThatEvolvedIsAnotherSpeciesAndTheOldOneIsKept() {
        val (g, s, new) = asked()
        new.species = 17
        assertKeptBoth(g, s, new, listOf("main", "d"))
    }

    @Test fun anOldCopyThatBecameShinyIsKept() {
        val (g, s, new) = asked()
        s.mon("d")!!.shiny = true
        assertKeptBoth(g, s, new, listOf("main", "d"))
    }

    @Test fun anOldCopyThatBecameProtectedIsKept() {
        val (g, s, new) = asked()
        s.settings.protectLegendary = true
        s.mon("d")!!.tier = Tier.LEGENDARY
        assertFalse(AskMe.canReplace(data, s, s.mon("d")!!))
        assertKeptBoth(g, s, new, listOf("main", "d"))
    }

    @Test fun aPairThatIsStillTheSameStillReleases() {
        val (g, s, new) = asked()
        val r = g.answerReplace(new.uid, release = true, keepPlace = true)
        assertTrue(r.ok); assertNull(s.mon("d")); assertEquals(listOf("main", new.uid), s.team); assertTrue(new.favourite)
    }

    @Test fun theStaleRuleIsAboutThePairNotAboutTheNewCopyAmongTheOwnedOnes() {
        val s = state(); val old = s.mon("d")!!
        val new = mon(16, 30, "n", ivTotal = 120); s.mons += new         // the new copy IS in the collection, as in a saved question
        assertTrue(AskMe.stillReplaces(data, s, new, old))
        assertFalse("the pair is compared, not the new copy with itself", AskMe.stillReplaces(data, s, new, new))
        assertFalse("not better than the old one the other way round", AskMe.stillReplaces(data, s, old, new))
        assertTrue("a better IV total alone is better", AskMe.betterThan(mon(16, 5, "x", ivTotal = 91), old))
        assertFalse(AskMe.betterThan(mon(16, 20, "x", ivTotal = 90), old))
    }

    @Test fun keepingItsPlaceNeverDoublesTheNewCopysTeamEntry() {
        // the reported repro: the question waited, the caught copy was put in the Team meanwhile, then "Keep its place"
        val (g, s, new) = asked()
        s.team.remove("d"); s.team.add(0, "d")                            // [d, main]: the old copy is the Main
        s.team += new.uid                                                  // [d, main, new]
        val r = g.answerReplace(new.uid, release = true, keepPlace = true)
        assertTrue(r.ok); assertNull(s.mon("d"))
        assertEquals("the new copy took the old slot, once", listOf(new.uid, "main"), s.team)
        assertEquals(s.team.toSet().size, s.team.size)
        assertNotNull("the Main is a real Pokémon", s.main)
    }

    @Test fun theOldSlotIsKeptWhenTheNewCopyWasBehindItAndWhenBeforeIt() {
        val (g, s, new) = asked()                                          // [main, d]
        s.team += new.uid                                                  // [main, d, new]
        assertTrue(g.answerReplace(new.uid, true, keepPlace = true).ok)
        assertEquals(listOf("main", new.uid), s.team)
        val (g2, s2, new2) = asked()
        s2.team.add(0, new2.uid)                                           // [new, main, d]: the new copy was in front
        assertTrue(g2.answerReplace(new2.uid, true, keepPlace = true).ok)
        assertEquals("one entry, in the old copy's slot", listOf("main", new2.uid), s2.team)
    }

    @Test fun theOnlyTeamMemberCanBeReplacedWithTheNewCopyAlreadyInTheTeam() {
        val s = TestData.state(data, 25, 20); val g = game(s)
        s.mons += mon(25, 5, "z"); s.caught += 25
        s.asks += PendingAsk(PendingAsk.Kind.CATCH, "wild", mon(25, 30, "wild"), Tier.NORMAL)
        g.answerCatch("wild", AskChoice.REPLACE)
        val q = s.asks.single(); assertEquals("main", q.oldUid)
        s.team += q.uid                                                    // [main, new]
        assertTrue(g.answerReplace(q.uid, true, keepPlace = true).ok)
        assertEquals(listOf(q.uid), s.team)
        assertEquals(q.uid, s.main!!.uid)
    }

    @Test fun withoutKeepingItsPlaceTheNewCopyInTheTeamStaysOnce() {
        val (g, s, new) = asked()
        s.team += new.uid                                                  // [main, d, new]
        assertTrue(g.answerReplace(new.uid, true, keepPlace = false).ok)
        assertEquals(listOf("main", new.uid), s.team)
    }

    // ---------------------------------------------------------------- CX-3: Imposter before the picks

    private val ditto = 807   // "Blobby" of R17C8: the Ditto with only Transform

    @Test fun aWildImposterDittoWithOnlyTransformUsesARealCopiedMoveInItsFirstAction() {
        val m = R17C8.main("ember", "flamethrower")
        val w = R17C8.mon(ditto, 50, "transform"); w.ability = "Imposter"
        val b = R17C8.battle(w)
        val e = R17C8.round(m, b, Random(1), enemyActs = true, mainActs = false)
        val wildAttacks = e.attacks().filter { !it.byPlayer }
        assertEquals(1, wildAttacks.size)
        assertTrue("a move of the copied set, not Transform: ${wildAttacks[0].move}", wildAttacks[0].moveId in setOf("ember", "flamethrower"))
        assertFalse("it did not fail", wildAttacks[0].failed)
        assertTrue("it transformed first", e.indexOfFirst { it is GameEvent.Effect && it.stat == "transform" } < e.indexOf(wildAttacks[0]))
        assertTrue("no wasted \"But it failed!\"", e.effects().none { it.text.contains("failed") })
    }

    @Test fun ourImposterDittoUsesARealCopiedMoveInItsFirstAction() {
        val m = R17C8.mon(ditto, 50, "transform"); m.ability = "Imposter"
        val b = R17C8.battle(R17C8.wild("flamethrower"))
        val e = R17C8.round(m, b, Random(1), enemyActs = false, mainActs = true)
        val ours = e.attacks().filter { it.byPlayer }
        assertEquals(1, ours.size)
        assertEquals("flamethrower", ours[0].moveId); assertFalse(ours[0].failed)
        assertEquals(1, e.effects().count { it.stat == "transform" })
    }

    @Test fun theCopiedSpeedDecidesTheOrderOfThatRound() {
        // a Lv 100 Ditto is faster than the slow wild one; it copies the wild one's speed first, and a tie goes to the wild one
        val m = R17C8.mon(ditto, 100, "transform"); m.ability = "Imposter"
        val w = R17C8.wild("tackle")
        val b = R17C8.battle(w)
        val faster = StatCalc.stats(R17C8.data, m).spe > StatCalc.stats(R17C8.data, w).spe
        assertTrue("the Ditto is the faster one before copying", faster)
        val e = R17C8.round(m, b, Random(1), enemyActs = true, mainActs = true)
        val order = e.attacks().filter { !it.failed }.map { it.byPlayer }
        assertEquals("the wild one acts first after the copy", listOf(false, true), order)
    }

    @Test fun theFreeHitOfASwitchPicksAfterTheWildImposterHasTransformed() {
        val incoming = R17C8.main("ember", "flamethrower")
        val w = R17C8.mon(ditto, 50, "transform"); w.ability = "Imposter"
        val b = R17C8.battle(w)
        val e = BattleEngine.switchHit(R17C8.data, b, incoming, Random(1), MoveChoice.RANDOM, 0.2)
        val hit = e.attacks().single()
        assertTrue("a copied move: ${hit.move}", hit.moveId in setOf("ember", "flamethrower"))
        assertFalse(hit.failed)
        assertNotNull(MoveControl.transformLook(b.enemy))
    }

    @Test fun aRoundWithNoActionDoesNotMakeAnImposterEnter() {
        // OK + OK: nobody acts, as before nothing enters either
        val m = R17C8.main("ember")
        val w = R17C8.mon(ditto, 50, "transform"); w.ability = "Imposter"
        val b = R17C8.battle(w)
        R17C8.round(m, b, Random(1), enemyActs = false, mainActs = false)
        assertNull(MoveControl.transformLook(b.enemy))
        assertFalse(b.enemy.entered)
    }

    // ---------------------------------------------------------------- NICE-6: Skill Swap does not start Imposter

    @Test fun skillSwapHandingOverImposterDoesNotTransform() {
        val w = R17C9b.wild("Imposter", 801, "tackle"); val b = R17C9b.battle(w); val m = R17C9b.main("Static", "skillswap")
        b.player.entered = true; b.enemy.entered = true                      // both are in the fight already: no entry hooks of their own
        val e = R17C9b.round(m, b, "skillswap")
        assertEquals("the Main has Imposter now", "imposter", SimpleView.of(R17C9b.data, b, m).ability(true))
        assertEquals("static", SimpleView.of(R17C9b.data, b, m).ability(false))
        assertNull("but it did not transform", MoveControl.transformLook(b.player))
        assertTrue(e.effects().none { it.stat == "transform" })
    }

    @Test fun otherEntryAbilitiesStillStartThroughSkillSwap() {
        val w = R17C9b.wild("Levitate", 801, "tackle"); val b = R17C9b.battle(w); val m = R17C9b.main("Intimidate", "skillswap")
        R17C9b.round(m, b, "skillswap")
        assertEquals("the wild one's new Intimidate", -1, b.player.boosts[0])
    }
}
