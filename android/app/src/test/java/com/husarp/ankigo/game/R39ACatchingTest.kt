package com.husarp.ankigo.game

import com.husarp.ankigo.game.save.SaveCodec
import com.husarp.ankigo.ui.CatchAskPlan
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class R39ACatchingTest {
    private val data = TestData.world()
    private val now = 1_790_000_000_000L
    private fun state(choice: CatchChoice = CatchChoice.NEW_AND_BETTER) = TestData.state(data).also {
        CatchChoice.apply(it.settings, choice)
        it.settings.wishlist.clear()
        it.settings.protectStarter = false
    }
    private fun own(s: GameState, species: Int, uid: String = "owned$species", level: Int = 20) {
        s.mons += TestData.mon(data, species, level, uid = uid)
        s.caught += species
    }
    private fun battle(species: Int = 133, level: Int = 5) =
        Battle(TestData.mon(data, species, level, listOf("tackle"), "wild"), Tier.NORMAL)

    @Test fun missingSplitBranchesKeepCopiesWithoutReplacingEvenForABetterWild() {
        for (level in listOf(5, 40)) {
            val s = state(); own(s, 133)
            val plan = Catching.plan(data, s, battle(level = level))
            assertEquals(Outcome.CATCH, plan.outcome)
            assertNull(plan.duplicate)
            own(s, 133, "second"); own(s, 133, "third")
            val full = Catching.plan(data, s, battle(level = level))
            assertEquals(if (level > 20) Outcome.CATCH else Outcome.DEFEAT, full.outcome)
            assertNotNull(full.duplicate)
            assertEquals(level > 20, full.duplicate!!.higher)
        }
    }

    @Test fun ownedEvolutionsReduceTheTargetButHistoricalCatchesDoNot() {
        val s = state(); own(s, 133)
        s.caught += listOf(134, 135, 700)
        assertEquals(Outcome.CATCH, Catching.decide(data, s, battle()))
        own(s, 134)
        assertEquals(Outcome.CATCH, Catching.decide(data, s, battle()))
        own(s, 135)
        assertEquals(Outcome.DEFEAT, Catching.decide(data, s, battle()))
        s.mons.removeAll { it.species == 134 }
        assertEquals(Outcome.CATCH, Catching.decide(data, s, battle()))
        own(s, 134); own(s, 700)
        assertEquals(Outcome.DEFEAT, Catching.decide(data, s, battle()))
    }

    @Test fun oneCopyCoversALinearPathAndFinalFormsNeedNoMaterial() {
        val s = state()
        assertEquals(Outcome.CATCH, Catching.decide(data, s, battle(1)))
        own(s, 1)
        assertEquals(Outcome.DEFEAT, Catching.decide(data, s, battle(1)))
        own(s, 3)
        assertEquals(Outcome.DEFEAT, Catching.decide(data, s, battle(3)))
    }

    @Test fun branchesBeyondAnOwnedIntermediateStillNeedCopies() {
        val sp = data.sp(2).copy(evolutions = listOf(Evolution(3, EvoKind.LEVEL), Evolution(134, EvoKind.ITEM)))
        val world = TestData.data(data.species.values.filter { it.num != 2 } + sp)
        val s = state(); own(s, 1); own(s, 2)
        assertEquals(Outcome.CATCH, Catching.decide(world, s, battle(1)))
        own(s, 3)
        assertEquals(Outcome.DEFEAT, Catching.decide(world, s, battle(1)))
    }

    @Test fun otherModesAndManualChoicesKeepTheirRules() {
        for (choice in listOf(CatchChoice.NEW_ONLY, CatchChoice.ASK_ME, CatchChoice.DEFEAT)) {
            val s = state(choice); own(s, 133)
            val plan = Catching.plan(data, s, battle())
            assertEquals(Outcome.DEFEAT, plan.outcome); assertNull(plan.ask); assertFalse(plan.askEvery)
        }
        val s = state(); own(s, 133)
        assertEquals(Outcome.DEFEAT, Catching.decide(data, s, battle().also { it.override = Outcome.DEFEAT }))
    }

    @Test fun everyModeAsksForNewOwnedWorseShinyProtectedAndWishlistedWilds() {
        val s = state(CatchChoice.ASK_EVERY); own(s, 16)
        s.settings.wishlist += 133
        for (b in listOf(battle(54), battle(16), battle(16, 40), battle(133), battle(150),
            battle(19).also { it.wild.shiny = true })) {
            val plan = Catching.plan(data, s, b)
            assertEquals(Outcome.DEFEAT, plan.outcome)
            assertTrue(plan.askEvery); assertNull(plan.duplicate)
        }
    }

    private fun finish(s: GameState, b: Battle): Pair<Game, List<GameEvent>> {
        val g = Game(data, s, Random(1)) { now }.also { it.roundAnswers = 1; s.day = it.dayKey() }
        s.battle = b.also { it.wild.hp = 1 }
        return g to g.onAnswer(Answer(1L, 3, now))
    }

    @Test fun smartCatchActuallyKeepsTheOldCopyAndTheNewMaterial() {
        val s = state(); own(s, 133)
        val (_, events) = finish(s, battle())
        assertTrue(events.any { it is GameEvent.Caught })
        assertEquals(2, s.mons.count { it.species == 133 })
        assertNotNull(s.mon("owned133")); assertTrue(s.asks.isEmpty())
    }

    @Test fun everyQuestionPaysOnceAndAnsweringAfterRestartCatchesOnlyOnce() {
        val s = state(CatchChoice.ASK_EVERY)
        val (_, events) = finish(s, battle(54))
        assertTrue(events.filterIsInstance<GameEvent.Defeated>().single().ask)
        assertFalse(events.any { it is GameEvent.Caught }); assertEquals(0, s.catches)
        assertEquals("wild", s.asks.single().uid)
        val back = SaveCodec.decode(SaveCodec.encode(s))
        assertEquals(CatchChoice.ASK_EVERY, CatchChoice.of(back.settings))
        val coins = back.cash; val defeats = back.defeats; val xp = back.main!!.xp
        val g = Game(data, back, Random(1)) { now }
        assertTrue(g.answerCatch("wild", AskChoice.CATCH).ok)
        assertFalse(g.answerCatch("wild", AskChoice.CATCH).ok)
        assertEquals(1, back.catches); assertTrue(back.asks.isEmpty())
        assertEquals(coins, back.cash); assertEquals(defeats, back.defeats); assertEquals(xp, back.main!!.xp)
    }

    @Test fun decliningEveryQuestionLeavesTheWildUncaughtIncludingAShiny() {
        val s = state(CatchChoice.ASK_EVERY)
        val (g, _) = finish(s, battle(19).also { it.wild.shiny = true })
        assertTrue(g.answerCatch("wild", AskChoice.DONT).ok)
        assertEquals(0, s.catches); assertTrue(s.asks.isEmpty())
        assertFalse(s.mons.any { it.species == 19 })
    }

    @Test fun ballsAndExplicitBattleOverridesSkipTheQuestion() {
        val s = state(CatchChoice.ASK_EVERY)
        for (override in Outcome.entries) {
            val plan = Catching.plan(data, s, battle().also { it.override = override })
            assertEquals(override, plan.outcome); assertFalse(plan.askEvery)
        }
        s.battle = battle(); s.bag["master-ball"] = 1
        assertTrue(Game(data, s, Random(1)) { now }.throwBall("master-ball").any { it is GameEvent.Caught })
        assertTrue(s.asks.isEmpty())
    }

    @Test fun newQuestionTitlesDoNotCallNewOrWorseCopiesBetter() {
        val s = state(CatchChoice.ASK_EVERY); own(s, 16)
        for (species in listOf(54, 16)) {
            val w = battle(species).wild
            val model = CatchAskPlan.of(data, s, PendingAsk(PendingAsk.Kind.CATCH, w.uid, w))!!
            assertEquals("Catch ${w.name}?", model.title)
            assertFalse(model.canReplace)
        }
    }

    @Test fun newOptionNormalizesPersistsAndClearsWhenSwitchingChoices() {
        val s = Settings(autoMode = 3, askEveryCatch = true, askMe = true, autoReleaseDuplicates = true)
        CatchChoice.normalize(s)
        assertEquals(CatchChoice.ASK_EVERY, CatchChoice.of(s)); assertFalse(s.askMe); assertFalse(s.autoReleaseDuplicates)
        for (choice in CatchChoice.entries) {
            CatchChoice.apply(s, CatchChoice.ASK_EVERY); CatchChoice.apply(s, choice)
            assertEquals(choice == CatchChoice.ASK_EVERY, s.askEveryCatch)
        }
        for (mode in listOf(1, 2)) {
            s.askEveryCatch = true; s.autoMode = mode; CatchChoice.normalize(s)
            assertFalse(s.askEveryCatch); assertEquals(mode, s.autoMode)
        }
        val old = SaveCodec.decode("""{"schema":1,"settings":{"autoMode":3},"mons":[]}""")
        assertFalse(old.settings.askEveryCatch)
        assertFalse(SaveCodec.encode(old).contains("askEveryCatch"))
    }
}
