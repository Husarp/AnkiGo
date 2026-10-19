package com.husarp.ankigo.game

import com.husarp.ankigo.game.save.SaveCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** R3-X2 item 4: "Auto-release duplicates" (decisions in Catching.plan / Duplicates, the swap in Game, the save). */
class DuplicatesTest {
    private val data = TestData.world()

    /**
     * Team m (Sparkmouse 25, Lv 20, the main). Box: d (Birdie 16, Lv 20) is the plain duplicate-to-be; the setting is ON.
     * Mode 1 = "always catch", so any DEFEAT in these tests comes from the duplicate rule and not from the mode.
     */
    private fun state(on: Boolean = true): GameState {
        val s = TestData.state(data, 25, 20, listOf("tackle"))
        s.mons += TestData.mon(data, 16, 20, listOf("tackle"), uid = "d")
        s.caught += 16
        s.settings.autoMode = 1
        s.settings.autoReleaseDuplicates = on
        return s
    }

    private fun battle(species: Int, level: Int, shiny: Boolean = false, tier: Tier = Tier.NORMAL): Battle {
        val w = TestData.mon(data, species, level, uid = "wild")
        w.shiny = shiny
        return Battle(w, tier)
    }

    private fun plan(s: GameState, b: Battle) = Catching.plan(data, s, b)

    // ---- the decision ----

    @Test fun offKeepsTodaysRulesWhateverTheLevel() {
        val s = state(on = false)
        for (lv in listOf(5, 20, 40)) {
            val p = plan(s, battle(16, lv))
            assertEquals(Outcome.CATCH, p.outcome) // mode 1: always catch
            assertNull(p.duplicate)
        }
        s.settings.autoMode = 3 // owned species -> defeated, as before the setting existed
        assertEquals(Outcome.DEFEAT, plan(s, battle(16, 40)).outcome)
        assertNull(plan(s, battle(16, 40)).duplicate)
    }

    @Test fun notHigherThanTheBestCopyIsDefeated() {
        val s = state()
        s.mons += TestData.mon(data, 16, 12, listOf("tackle"), uid = "low")
        for (lv in listOf(3, 12, 19, 20)) { // lower than, and equal to, the best copy (Lv 20)
            val p = plan(s, battle(16, lv))
            assertEquals("Lv $lv", Outcome.DEFEAT, p.outcome)
            assertFalse(p.duplicate!!.higher); assertEquals(20, p.duplicate!!.bestLevel); assertNull(p.duplicate!!.release)
        }
    }

    @Test fun higherThanTheBestCopyIsCaughtAndSwapsTheBestCopy() {
        val s = state()
        s.mons += TestData.mon(data, 16, 12, listOf("tackle"), uid = "low")
        val p = plan(s, battle(16, 21))
        assertEquals(Outcome.CATCH, p.outcome)
        assertTrue(p.duplicate!!.higher)
        assertSame(s.mon("d"), p.duplicate!!.release) // the best copy goes; the weaker Lv 12 one stays
        assertNull(p.duplicate!!.kept)
    }

    @Test fun aSpeciesYouDoNotOwnFollowsTheNormalRules() {
        val s = state()
        s.caught += 54
        assertEquals(Outcome.CATCH, plan(s, battle(54, 10)).outcome) // mode 1
        assertNull(plan(s, battle(54, 10)).duplicate)
        s.settings.autoMode = 3 // known but released: the old rule (defeated), the setting does not apply without a copy
        assertEquals(Outcome.DEFEAT, plan(s, battle(54, 10)).outcome)
        assertNull(plan(s, battle(54, 10)).duplicate)
        s.caught -= 54
        assertEquals(Outcome.CATCH, plan(s, battle(54, 10)).outcome) // never caught: caught
    }

    @Test fun theOneOffTapIsTheirOwnChoice() {
        val s = state()
        val lower = battle(16, 5).also { it.override = Outcome.CATCH }
        assertEquals(Outcome.CATCH, plan(s, lower).outcome); assertNull(plan(s, lower).duplicate)
        val higher = battle(16, 40).also { it.override = Outcome.DEFEAT }
        assertEquals(Outcome.DEFEAT, plan(s, higher).outcome); assertNull(plan(s, higher).duplicate)
    }

    @Test fun aWildShinyProtectedOrWishlistedOneIsNeverTurnedIntoADefeat() {
        val s = state()
        assertEquals(Outcome.CATCH, plan(s, battle(16, 5, shiny = true)).outcome)
        assertEquals(Outcome.CATCH, plan(s, battle(16, 5, tier = Tier.ULTRA)).outcome)
        s.settings.autoMode = 2 // mode 2 would defeat a plain duplicate: these three are still caught
        assertEquals(Outcome.CATCH, plan(s, battle(16, 5, shiny = true)).outcome)
        assertEquals(Outcome.CATCH, plan(s, battle(16, 5, tier = Tier.ULTRA)).outcome)
        s.settings.wishlist += 16
        assertEquals(Outcome.CATCH, plan(s, battle(16, 5)).outcome)
        // a higher shiny still swaps the plain copy
        s.settings.wishlist -= 16
        val p = plan(s, battle(16, 30, shiny = true))
        assertEquals(Outcome.CATCH, p.outcome); assertSame(s.mon("d"), p.duplicate!!.release)
    }

    @Test fun modeDoesNotMatterForADuplicate() {
        val s = state()
        for (mode in 1..3) {
            s.settings.autoMode = mode
            assertEquals("mode $mode lower", Outcome.DEFEAT, plan(s, battle(16, 10)).outcome)
            assertEquals("mode $mode higher", Outcome.CATCH, plan(s, battle(16, 30)).outcome)
        }
    }

    // ---- never release ----

    private fun keepOf(s: GameState, wildLevel: Int = 30): Duplicates.Verdict {
        val p = plan(s, battle(16, wildLevel))
        assertEquals(Outcome.CATCH, p.outcome) // still caught: both are kept
        return p.duplicate!!
    }

    @Test fun aFavouriteIsKept() {
        val s = state(); s.mon("d")!!.favourite = true
        val v = keepOf(s); assertNull(v.release); assertEquals(Duplicates.Keep.FAVOURITE, v.kept)
    }

    @Test fun aShinyCopyIsKept() {
        val s = state(); s.mon("d")!!.shiny = true
        val v = keepOf(s); assertNull(v.release); assertEquals(Duplicates.Keep.SHINY, v.kept)
    }

    @Test fun aTeamMemberIsKept() {
        val s = state(); s.team += "d"
        val v = keepOf(s); assertNull(v.release); assertEquals(Duplicates.Keep.TEAM, v.kept)
    }

    @Test fun theMainIsKept() {
        val s = state() // the main is Sparkmouse 25, Lv 20
        val p = plan(s, battle(25, 30))
        assertEquals(Outcome.CATCH, p.outcome)
        assertNull(p.duplicate!!.release); assertEquals(Duplicates.Keep.TEAM, p.duplicate!!.kept)
        assertEquals("main", s.main!!.uid)
    }

    @Test fun aPokemonHoldingAnItemIsKept() {
        val s = state(); s.mon("d")!!.heldItem = "everstone"
        val v = keepOf(s); assertNull(v.release); assertEquals(Duplicates.Keep.HOLDS_ITEM, v.kept)
    }

    @Test fun aProtectedKindIsKeptUnlessItsProtectionIsOff() {
        val s = state()
        s.mons += TestData.mon(data, 150, 40, listOf("tackle"), uid = "legend") // legendary species
        s.caught += 150
        val p = plan(s, battle(150, 50))
        assertEquals(Outcome.CATCH, p.outcome)
        assertNull(p.duplicate!!.release); assertEquals(Duplicates.Keep.PROTECTED_KIND, p.duplicate!!.kept)
        s.settings.protectLegendary = false // protection off: it is an ordinary duplicate
        val q = plan(s, battle(150, 50))
        assertSame(s.mon("legend"), q.duplicate!!.release)
    }

    @Test fun theExpShareHolderIsKept() {
        val s = state(); s.settings.xpShareUid = "d"
        val v = keepOf(s); assertNull(v.release); assertEquals(Duplicates.Keep.EXP_SHARE, v.kept)
    }

    @Test fun aNicknamedPokemonIsKept() {
        val s = state(); s.mon("d")!!.nickname = "Zappy"
        val v = keepOf(s); assertNull(v.release); assertEquals(Duplicates.Keep.NICKNAME, v.kept)
        s.mon("d")!!.nickname = "  " // blank is no nickname
        assertSame(s.mon("d"), keepOf(s).release)
    }

    @Test fun aPlainBoxCopyMayGo() {
        val s = state()
        assertNull(Duplicates.keepReason(data, s, s.mon("d")!!))
    }

    @Test fun ifTheBestCopyIsKeptALowerPlainCopyIsNotTouched() {
        val s = state()
        s.mon("d")!!.favourite = true // Lv 20, the best
        s.mons += TestData.mon(data, 16, 10, listOf("tackle"), uid = "low")
        val v = keepOf(s)
        assertNull(v.release); assertEquals(Duplicates.Keep.FAVOURITE, v.kept)
    }

    @Test fun amongEqualBestCopiesAReleasableOneIsChosen() {
        val s = state()
        s.mon("d")!!.favourite = true
        s.mons += TestData.mon(data, 16, 20, listOf("tackle"), uid = "twin")
        val v = keepOf(s)
        assertSame(s.mon("twin"), v.release); assertNull(v.kept)
    }

    // ---- the swap in the game ----

    private var now = 1_000L
    private var card = 1L

    private fun game(s: GameState) = Game(data, s, Random(1)) { now }.also { s.day = it.dayKey() } // no day rollover mid-test

    /** Puts a wild [species] [level] with 1 HP into the fight and plays two Good answers (one round) so it faints. */
    private fun Game.faint(species: Int, level: Int, shiny: Boolean = false): List<GameEvent> {
        val w = TestData.mon(data, species, level, listOf("tackle"), uid = "wild")
        w.shiny = shiny; w.hp = 1
        state.battle = Battle(w, Tier.NORMAL, startedAt = now)
        onAnswer(Answer(card++, 3, now))
        return onAnswer(Answer(card++, 3, now))
    }

    private inline fun <reified T : GameEvent> List<GameEvent>.of() = filterIsInstance<T>()

    @Test fun aHigherWildOneIsCaughtAndTheOldCopyIsReleased() {
        val s = state()
        s.mon("d")!!.ankimonId = "ank-7"
        val ev = game(s).faint(16, 30)
        val dup = ev.of<GameEvent.Duplicate>().single()
        assertEquals(DuplicateResult.SWAPPED, dup.result)
        assertEquals(30, dup.level); assertEquals(20, dup.ownedLevel); assertEquals("Birdie", dup.releasedName)
        val caught = ev.of<GameEvent.Caught>().single()
        assertEquals(30, s.mon(caught.uid)!!.level)
        assertTrue(ev.indexOf(caught) < ev.indexOf(dup)) // the explanation follows what it explains
        assertTrue(ev.of<GameEvent.Defeated>().isEmpty())
        assertNull(s.mon("d"))                                      // released
        assertEquals(listOf(30), s.mons.filter { it.species == 16 }.map { it.level }) // one Birdie left: the better one
        assertEquals(2, s.mons.size)                                // main + the swapped-in one
        assertTrue("ank-7" in s.releasedAnkimon)                    // the Ankimon export removes it, as after a manual release
        assertEquals(1, s.catches); assertEquals("main", s.team.first())
    }

    @Test fun aReleasedPokemonWithoutAnAnkimonIdIsRememberedByItsUid() {
        val s = state()
        game(s).faint(16, 30)
        assertTrue("d" in s.releasedAnkimon)
    }

    @Test fun aWildOneThatIsNotHigherIsDefeatedForRewards() {
        val s = state()
        val g = game(s)
        val ev = g.faint(16, 20)
        val dup = ev.of<GameEvent.Duplicate>().single()
        assertEquals(DuplicateResult.DEFEATED, dup.result); assertEquals(20, dup.ownedLevel)
        assertEquals(1, ev.of<GameEvent.Defeated>().size)
        assertTrue(ev.of<GameEvent.Caught>().isEmpty())
        assertEquals(1, s.defeats); assertEquals(0, s.catches)
        assertEquals(listOf("main", "d"), s.mons.map { it.uid })    // nothing changed in the collection
        assertTrue(s.releasedAnkimon.isEmpty())
    }

    @Test fun whenTheCopyMayNotGoBothAreKeptAndTheLogSaysWhy() {
        val s = state(); s.mon("d")!!.favourite = true
        val ev = game(s).faint(16, 30)
        val dup = ev.of<GameEvent.Duplicate>().single()
        assertEquals(DuplicateResult.KEPT_BOTH, dup.result); assertEquals("a favourite", dup.keptBecause)
        assertEquals(1, ev.of<GameEvent.Caught>().size)
        assertEquals(2, s.mons.count { it.species == 16 })
        assertTrue(s.releasedAnkimon.isEmpty())
    }

    @Test fun offThereIsNoDuplicateEvent() {
        val s = state(on = false)
        val ev = game(s).faint(16, 30)
        assertTrue(ev.of<GameEvent.Duplicate>().isEmpty())
        assertEquals(1, ev.of<GameEvent.Caught>().size)
        assertEquals(2, s.mons.count { it.species == 16 }) // mode 1: caught as before
    }

    @Test fun willCatchFollowsTheRule() {
        val s = state()
        val g = game(s)
        s.battle = battle(16, 10); assertFalse(g.willCatch())
        s.battle = battle(16, 30); assertTrue(g.willCatch())
        s.settings.autoReleaseDuplicates = false
        s.battle = battle(16, 10); assertTrue(g.willCatch()) // mode 1
    }

    @Test fun aSwapKeepsASixMemberTeamUntouched() {
        val s = state()
        for (i in 1..5) { val m = TestData.mon(data, 19, 5 + i, listOf("tackle"), uid = "t$i"); s.mons += m; s.team += m.uid }
        assertEquals(6, s.team.size)
        val ev = game(s).faint(16, 30) // the new one goes to the box, the old one is gone
        assertEquals(DuplicateResult.SWAPPED, ev.of<GameEvent.Duplicate>().single().result)
        assertEquals(6, s.team.size)
        assertFalse(ev.of<GameEvent.Caught>().single().toTeam)
        assertEquals(listOf(30), s.mons.filter { it.species == 16 }.map { it.level })
    }

    // ---- the save ----

    @Test fun theSettingSurvivesTheSaveRoundTrip() {
        val s = state()
        s.settings.autoMode = 3 // R10-X2: the switch is kept only with mode 3 (CatchChoice.normalize on load)
        val back = SaveCodec.decode(SaveCodec.encode(s))
        assertTrue(back.settings.autoReleaseDuplicates)
        assertEquals(SaveCodec.encode(s), SaveCodec.encode(back))
        s.settings.autoReleaseDuplicates = false
        assertFalse(SaveCodec.decode(SaveCodec.encode(s)).settings.autoReleaseDuplicates)
    }

    @Test fun anOldSaveLoadsWithTheSettingOff() {
        val b = SaveCodec.decode("""{"schema":1,"settings":{"autoMode":1},"mons":[{"uid":"a","species":25,"level":5}]}""")
        assertFalse(b.settings.autoReleaseDuplicates)
        assertFalse(SaveCodec.decode("""{"schema":1,"mons":[]}""").settings.autoReleaseDuplicates) // no settings object at all
    }

    @Test fun aSwapSurvivesTheSaveRoundTrip() {
        val s = state()
        game(s).faint(16, 30)
        val back = SaveCodec.decode(SaveCodec.encode(s))
        assertNull(back.mon("d"))
        assertTrue("d" in back.releasedAnkimon)
        assertEquals(listOf(30), back.mons.filter { it.species == 16 }.map { it.level })
    }
}
