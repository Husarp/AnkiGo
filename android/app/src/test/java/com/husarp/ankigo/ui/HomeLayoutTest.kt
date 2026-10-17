package com.husarp.ankigo.ui

import com.husarp.ankigo.GameRepo.Status
import com.husarp.ankigo.ui.HomeLayout.Card
import com.husarp.ankigo.ui.HomeLayout.Slot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** R4-V1: which parts of the home screen show (Study = decks only, the battle full-screen without a bottom bar) - the layout decision table, tested on the JVM. */
class HomeLayoutTest {
    private fun decide(h: Float, view: HomeView, status: Status = Status.READY, pip: Boolean = false) =
        HomeLayout.decide(h, pip, view, status)

    // ---------------- tall window

    @Test fun tallStudyHasAppBarDecksAndBarAndNoBattle() {
        val p = decide(869f, HomeView.STUDY)
        assertEquals(HomeView.STUDY, p.view)
        assertTrue(p.tall)
        assertTrue(p.studyBar); assertFalse(p.battleBar)
        assertTrue(p.deckList); assertTrue(p.bottomBar)
        assertEquals("no battle card on the Study tab (it lives in the Pokémon tab)", Card.NONE, p.card)
    }

    @Test fun tallBattleHasTopBarAndNoBottomBarAndNoDeckList() {
        val p = decide(869f, HomeView.BATTLE)
        assertEquals(HomeView.BATTLE, p.view)
        assertTrue(p.battleBar); assertFalse(p.studyBar)
        assertFalse(p.deckList)
        assertFalse("the full-screen battle has no bottom bar", p.bottomBar)
        assertEquals(Card.FULL, p.card)
        assertFalse("the top bar has the tool buttons", p.footerTools)
    }

    @Test fun theBarsStartAt560dp() {
        assertTrue(decide(560f, HomeView.STUDY).tall)
        assertFalse(decide(559.9f, HomeView.STUDY).tall)
        assertTrue(decide(560f, HomeView.BATTLE).battleBar)
        assertFalse(decide(559.9f, HomeView.BATTLE).battleBar)
    }

    @Test fun tabletIsATallWindow() {
        assertEquals(Card.NONE, decide(1280f, HomeView.STUDY).card)
        assertTrue(decide(1280f, HomeView.BATTLE).battleBar)
    }

    // ---------------- short window and picture-in-picture: the battle only, whatever the view

    @Test fun shortWindowIsTheBattleOnlyInEitherView() {
        for (view in HomeView.entries) for (h in listOf(400f, 300f, 250f, 412f, 559f)) {
            val p = decide(h, view)
            assertEquals("$view $h", HomeView.BATTLE, p.view)
            assertFalse("$view $h", p.tall)
            assertFalse(p.studyBar); assertFalse(p.battleBar); assertFalse(p.deckList); assertFalse(p.bottomBar)
            assertEquals(Card.FULL, p.card)
            assertTrue("its tool buttons are in the panel footer", p.footerTools)
        }
    }

    @Test fun shortWindowsAreTheSameInEitherView() {
        assertEquals(decide(400f, HomeView.STUDY), decide(400f, HomeView.BATTLE))
    }

    @Test fun pictureInPictureIsTheBattleOnlyEvenWhenTheWindowIsStillTall() {
        // the first layout in PiP can still report the old tall size
        for (view in HomeView.entries) {
            val p = decide(869f, view, pip = true)
            assertEquals(HomeView.BATTLE, p.view)
            assertFalse(p.studyBar); assertFalse(p.battleBar); assertFalse(p.deckList); assertFalse(p.bottomBar)
            assertEquals(Card.FULL, p.card)
        }
        assertEquals(decide(400f, HomeView.STUDY), decide(869f, HomeView.STUDY, pip = true))
    }

    // ---------------- game status: the Study view's status slot

    @Test fun aPlayableGameOrALoadingOneShowsNoSlotInTheStudyView() {
        for (s in listOf(Status.READY, Status.LOADING)) assertEquals("$s", Card.NONE, decide(869f, HomeView.STUDY, s).card)
    }

    @Test fun aGameThatCannotBePlayedShowsTheStatusSlotInTheStudyView() {
        for (s in listOf(Status.NEED_DATA, Status.NEED_STARTER, Status.SAVE_ERROR)) {
            val p = decide(869f, HomeView.STUDY, s)
            assertEquals("$s", HomeView.STUDY, p.view)
            assertEquals("$s", Card.SLOT, p.card)
            assertTrue(p.studyBar); assertTrue(p.deckList); assertTrue(p.bottomBar)
        }
    }

    @Test fun aGameThatCannotBePlayedForcesTheStudyViewEvenWhenTheBattleWasAsked() {
        // a widget / floating window asked for the battle, but there is no battle to show
        for (s in listOf(Status.NEED_DATA, Status.NEED_STARTER, Status.SAVE_ERROR)) {
            val p = decide(869f, HomeView.BATTLE, s)
            assertEquals("$s", HomeView.STUDY, p.view)
            assertEquals("$s", Card.SLOT, p.card)
            assertFalse(p.battleBar)
        }
    }

    @Test fun aLoadingGameKeepsTheViewThatWasAsked() {
        assertEquals(HomeView.BATTLE, decide(869f, HomeView.BATTLE, Status.LOADING).view)
        assertEquals(Card.FULL, decide(869f, HomeView.BATTLE, Status.LOADING).card) // "Loading…" where the battle will be
        assertEquals(HomeView.STUDY, decide(869f, HomeView.STUDY, Status.LOADING).view)
    }

    @Test fun shortWindowShowsTheBattleAreaWhenThereIsABattleToCome() {
        // split screen: the battle's area is all there is (the game plays, or is still loading: "Loading…" where the battle will be)
        for (s in listOf(Status.READY, Status.LOADING)) for (v in HomeView.entries) {
            val p = decide(400f, v, s)
            assertEquals("$s $v", HomeView.BATTLE, p.view)
            assertEquals("$s $v", Card.FULL, p.card)
        }
    }

    /** R4 fix B4: no playable battle in a short window (study only, setup canceled, save error): the scrollable Study layout stays. */
    @Test fun shortWindowWithoutAPlayableBattleKeepsTheStudyLayout() {
        for (s in listOf(Status.NEED_DATA, Status.NEED_STARTER, Status.SAVE_ERROR)) for (v in HomeView.entries) for (h in listOf(250f, 300f, 400f, 559f)) {
            val p = decide(h, v, s)
            assertEquals("$s $v $h", HomeView.STUDY, p.view)
            assertFalse("$s $v $h", p.tall)
            assertTrue("$s $v $h: the app bar", p.studyBar)
            assertTrue("$s $v $h: the deck list, so studying stays possible", p.deckList)
            assertTrue("$s $v $h: navigation", p.bottomBar)
            assertFalse(p.battleBar)
            assertEquals("$s $v $h: the status slot above the list", Card.SLOT, p.card)
        }
    }

    @Test fun pictureInPictureWithoutAPlayableBattleStaysTheBattleArea() {
        // PiP is far too small for the Study layout
        for (s in listOf(Status.NEED_DATA, Status.NEED_STARTER, Status.SAVE_ERROR)) {
            val p = decide(136f, HomeView.STUDY, s, pip = true)
            assertEquals("$s", HomeView.BATTLE, p.view)
            assertEquals("$s", Card.FULL, p.card)
            assertFalse(p.deckList)
        }
    }

    @Test fun theShortStudyLayoutFollowsThePlayableStateBack() {
        // once the game plays (setup done) the short window is the battle again
        assertEquals(Card.FULL, decide(400f, HomeView.STUDY, Status.READY).card)
        assertEquals(HomeView.BATTLE, decide(400f, HomeView.STUDY, Status.READY).view)
        assertNotEquals(decide(400f, HomeView.STUDY, Status.NEED_DATA), decide(400f, HomeView.STUDY, Status.READY))
    }

    @Test fun needsSetupRule() {
        for (s in listOf(Status.NEED_DATA, Status.NEED_STARTER, Status.SAVE_ERROR)) assertTrue("$s", HomeLayout.needsSetup(s))
        for (s in listOf(Status.READY, Status.LOADING)) assertFalse("$s", HomeLayout.needsSetup(s))
    }

    @Test fun answersCompareByValue() {
        // the activity skips a layout pass whose answer did not change
        assertEquals(decide(869f, HomeView.STUDY), decide(869f, HomeView.STUDY))
        assertEquals("a taller window changes nothing for the Study view", decide(869f, HomeView.STUDY), decide(700f, HomeView.STUDY))
        assertNotEquals(decide(869f, HomeView.STUDY), decide(869f, HomeView.BATTLE))
        assertNotEquals(decide(869f, HomeView.STUDY), decide(869f, HomeView.STUDY, Status.SAVE_ERROR))
    }

    // ---------------- what the battle's area holds without a panel

    @Test fun slotTable() {
        assertEquals(Slot.NOTHING, HomeLayout.slot(Status.READY, studyOnly = false, firstRunCanceled = false))
        assertEquals("studyOnly means nothing once the game is there", Slot.NOTHING, HomeLayout.slot(Status.READY, studyOnly = true, firstRunCanceled = true))
        assertEquals(Slot.LOADING, HomeLayout.slot(Status.LOADING, false, false))
        for (s in listOf(Status.NEED_DATA, Status.NEED_STARTER)) {
            assertEquals("$s studying without the game", Slot.SETUP_ROW, HomeLayout.slot(s, studyOnly = true, firstRunCanceled = false))
            assertEquals("$s studyOnly wins over a canceled first run", Slot.SETUP_ROW, HomeLayout.slot(s, studyOnly = true, firstRunCanceled = true))
            assertEquals("$s canceled first run", Slot.SETUP_CARD, HomeLayout.slot(s, studyOnly = false, firstRunCanceled = true))
            assertEquals("$s the first run is about to start", Slot.LOADING, HomeLayout.slot(s, studyOnly = false, firstRunCanceled = false))
        }
        assertEquals(Slot.SAVE_ERROR, HomeLayout.slot(Status.SAVE_ERROR, false, false))
        assertEquals(Slot.SAVE_ERROR, HomeLayout.slot(Status.SAVE_ERROR, true, true))
    }
}
