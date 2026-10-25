package com.husarp.ankigo.ui

import com.husarp.ankigo.game.AskMe
import com.husarp.ankigo.game.CashKind
import com.husarp.ankigo.game.CatchChoice
import com.husarp.ankigo.game.GameEvent
import com.husarp.ankigo.game.Mon
import com.husarp.ankigo.game.Nature
import com.husarp.ankigo.game.PendingAsk
import com.husarp.ankigo.game.StatCalc
import com.husarp.ankigo.game.Stats
import com.husarp.ankigo.game.TestData
import com.husarp.ankigo.game.Tier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** R19-P (PLAN 281): the words and the plans behind the "Ask me" pop-up, the release window and the "Your call" result tile (pure). */
class R19PAskMeUiTest {
    private val data = TestData.world()

    private fun mon(species: Int, level: Int, uid: String, iv: Int = 90): Mon {
        val b = TestData.mon(data, species, level, listOf("tackle"), uid)
        val a = IntArray(6); var left = iv
        for (i in a.indices) { a[i] = minOf(31, left); left -= a[i] }
        return Mon(uid = uid, species = species, name = b.name, level = level, xp = 0, ivs = Stats(a[0], a[1], a[2], a[3], a[4], a[5]), evs = Stats.ZERO,
            nature = Nature.HARDY, gender = 'M', shiny = false, ability = "Testability", moves = mutableListOf("tackle"), hp = 1).also { it.hp = StatCalc.maxHp(data, it) }
    }

    private fun state() = TestData.state(data, 25, 20).also {
        it.mons += mon(16, 18, "old", iv = 142); it.caught += 16
        CatchChoice.apply(it.settings, CatchChoice.ASK_ME)
    }

    private fun ask(wild: Mon) = PendingAsk(PendingAsk.Kind.CATCH, wild.uid, wild, Tier.NORMAL)

    // ---- the pop-up's plan

    @Test fun theTitleNamesTheSpeciesAndBothSidesShowTheirNumbers() {
        val s = state()
        val m = CatchAskPlan.of(data, s, ask(mon(16, 34, "w", iv = 160)))!!
        assertEquals("A better ${m.wild.name}!", m.title)
        assertEquals("Lv 34", CatchAskPlan.levelText(m.wild)); assertEquals("IVs 160 / 186", CatchAskPlan.ivText(m.wild))
        assertEquals("old", m.yours!!.uid); assertEquals("IVs 142 / 186", CatchAskPlan.ivText(m.yours!!))
        assertTrue(m.levelUp && m.ivsUp)
        assertTrue(m.canReplace); assertEquals("release your Lv 18 one", m.replaceSub)
    }

    @Test fun onlyTheBetterNumberGetsTheArrow() {
        val s = state()
        val lv = CatchAskPlan.of(data, s, ask(mon(16, 34, "w", iv = 100)))!!
        assertTrue(lv.levelUp); assertFalse(lv.ivsUp)
        val iv = CatchAskPlan.of(data, s, ask(mon(16, 10, "w", iv = 150)))!!
        assertFalse(iv.levelUp); assertTrue(iv.ivsUp); assertTrue(iv.canReplace)
    }

    @Test fun replaceSaysWhyItIsOff() {
        val shiny = state().also { it.mon("old")!!.shiny = true }
        val a = CatchAskPlan.of(data, shiny, ask(mon(16, 34, "w")))!!
        assertFalse(a.canReplace); assertEquals("your shiny stays", a.replaceSub)
        val prot = state().also { it.mons += mon(150, 40, "legend"); it.caught += 150 }
        val b = CatchAskPlan.of(data, prot, ask(mon(150, 50, "w")))!!
        assertFalse(b.canReplace); assertEquals("a protected one stays", b.replaceSub)
        val better = state().also { it.mon("old")!!.let { m -> m.level = 40 } }       // yours overtook the wild one meanwhile
        val c = CatchAskPlan.of(data, better, ask(mon(16, 34, "w", iv = 100)))!!
        assertFalse(c.canReplace); assertEquals("yours is better now", c.replaceSub)
        val gone = state().also { it.mons.removeAll { m -> m.species == 16 } }
        val d = CatchAskPlan.of(data, gone, ask(mon(16, 34, "w")))!!
        assertNull(d.yours); assertFalse(d.canReplace); assertEquals("nothing to replace", d.replaceSub)
    }

    @Test fun aQuestionWithoutItsPokemonHasNoPlan() {
        assertNull(CatchAskPlan.of(data, state(), PendingAsk(PendingAsk.Kind.CATCH, "x")))
    }

    // ---- the release window's plan

    @Test fun theSentenceIsTheOneAdamWrote() {
        val s = state(); val new = mon(16, 34, "new"); s.mons += new
        assertEquals("You caught a better ${new.name} (Lv 34). Release the old one (Lv 18)?", ReplacePlan.sentence(new, s.mon("old")!!))
    }

    @Test fun theWindowReadsTheOldOnesPlaceFromTheGame() {
        val s = state(); val new = mon(16, 34, "new"); s.mons += new
        val old = s.mon("old")!!; old.favourite = true; s.team += "old"; s.settings.xpShareUid = "old"
        val m = ReplacePlan.of(data, s, PendingAsk(PendingAsk.Kind.REPLACE, "new", oldUid = "old"))!!
        assertTrue(m.place.inTeam); assertEquals(1, m.place.teamSlot); assertTrue(m.place.favourite); assertTrue(m.place.expShare)
        assertEquals("new", m.new.uid); assertEquals("old", m.old.uid)
    }

    @Test fun aStaleReleaseQuestionHasNoWindow() {
        val s = state(); s.mons += mon(16, 34, "new")
        assertNull(ReplacePlan.of(data, s, PendingAsk(PendingAsk.Kind.REPLACE, "new", oldUid = "ghost")))
        assertNull(ReplacePlan.of(data, s, PendingAsk(PendingAsk.Kind.REPLACE, "ghost", oldUid = "old")))
        s.mon("old")!!.shiny = true
        assertNull(ReplacePlan.of(data, s, PendingAsk(PendingAsk.Kind.REPLACE, "new", oldUid = "old")))
        s.mon("old")!!.shiny = false
        assertNotNull(ReplacePlan.of(data, s, PendingAsk(PendingAsk.Kind.REPLACE, "new", oldUid = "old")))
    }

    @Test fun aMonWithoutAPlaceNeedsNoWindow() {
        val s = state(); val old = s.mon("old")!!
        assertFalse(AskMe.needsWindow(data, s, old))
        old.favourite = true
        assertTrue(AskMe.needsWindow(data, s, old))
    }

    // ---- the fight's batch and the "Your call" tile

    private fun batch(ask: Boolean) = listOf<GameEvent>(
        GameEvent.Defeated(16, "Birdie", 84, ask), GameEvent.XpGained("m", "Mainy", 84, 7, 0, 100), GameEvent.Cash(10, 20, CashKind.DEFEAT),
    )

    @Test fun theBatchOfAnAskedFightIsRecognised() {
        assertTrue(CatchAskPopup.waiting(batch(true)))
        assertFalse(CatchAskPopup.waiting(batch(false)))
        assertTrue(CatchAskPopup.waiting(listOf(GameEvent.Duplicate("Birdie", 30, 20, com.husarp.ankigo.game.DuplicateResult.KEPT_BOTH, ask = true))))
        assertFalse(CatchAskPopup.waiting(listOf(GameEvent.Duplicate("Birdie", 30, 20, com.husarp.ankigo.game.DuplicateResult.KEPT_BOTH))))
        assertFalse(CatchAskPopup.waiting(emptyList()))
    }

    @Test fun anAskedDefeatShowsYourCallInsteadOfDefeated() {
        val s = CatchSummary.of(batch(true))!!
        assertTrue(s.ask); assertFalse(s.caught)
        val big = CatchTilesPlan.tiles(s) { it }
        assertEquals(listOf(CatchTilesPlan.Kind.ASK, CatchTilesPlan.Kind.XP, CatchTilesPlan.Kind.COINS), big.map { it.kind })
        val row = CatchTilesPlan.rowTiles(s)
        assertEquals(listOf(CatchTilesPlan.Kind.ASK, CatchTilesPlan.Kind.XP, CatchTilesPlan.Kind.COINS), row.map { it.kind })
        assertEquals("Your call", row.first().value)
        assertTrue("TalkBack says what waits", row.first().desc.contains("Birdie"))
        assertEquals("Your call", CatchTilesPlan.rowTiles(s, compact = true).first().value)
    }

    @Test fun aPlainDefeatStaysDefeated() {
        val s = CatchSummary.of(batch(false))!!
        assertFalse(s.ask)
        assertEquals(CatchTilesPlan.Kind.DEFEATED, CatchTilesPlan.rowTiles(s).first().kind)
        assertEquals("Defeated", CatchTilesPlan.rowTiles(s).first().value)
    }
}
