package com.husarp.ankigo.ui

import com.husarp.ankigo.GameRepo.Status
import com.husarp.ankigo.ui.HomeNav.Back
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** R4-02 + R4-V1: the view an Intent asks for, the screen the battle was opened from, what Back does, and the StudyReturn cases. */
class HomeNavTest {
    private fun parts(h: Float, view: HomeView, pip: Boolean = false) = HomeLayout.decide(h, pip, view, Status.READY)

    // ---------------- the view from an Intent

    @Test fun launcherStartIsStudy() {
        assertEquals(HomeView.STUDY, HomeNav.viewFromIntent(null, null))
        assertEquals(HomeView.STUDY, HomeNav.viewFromIntent("", null))
    }

    @Test fun anExtraNamesTheView() {
        assertEquals(HomeView.STUDY, HomeNav.viewFromIntent("study", HomeView.BATTLE))
        assertEquals(HomeView.BATTLE, HomeNav.viewFromIntent("battle", HomeView.STUDY))
        assertEquals(HomeView.BATTLE, HomeNav.viewFromIntent("battle", null))
    }

    @Test fun noExtraKeepsTheViewThatIsShown() {
        // the "reviewDeck" hand-over and old PendingIntents arrive without a view
        assertEquals(HomeView.BATTLE, HomeNav.viewFromIntent(null, HomeView.BATTLE))
        assertEquals(HomeView.STUDY, HomeNav.viewFromIntent(null, HomeView.STUDY))
        assertEquals(HomeView.BATTLE, HomeNav.viewFromIntent("nonsense", HomeView.BATTLE))
    }

    @Test fun viewKeysRoundTrip() {
        for (v in HomeView.entries) assertEquals(v, HomeView.of(v.key))
        assertNull(HomeView.of(null)); assertNull(HomeView.of("Study")); assertNull(HomeView.of("pokemon"))
    }

    // ---------------- the screen the battle was opened from

    @Test fun theBattleKeepsTheScreenThatOpenedIt() {
        assertEquals(Dest.TEAM, HomeNav.originAfter("battle", "TEAM", null))
        assertEquals("a newer start replaces it", null, HomeNav.originAfter("battle", null, Dest.TEAM))
        assertEquals(Dest.TEAM, HomeNav.originAfter("battle", "TEAM", Dest.BOX))
    }

    @Test fun studyHasNoOrigin() {
        assertNull(HomeNav.originAfter("study", "TEAM", Dest.TEAM))
        assertNull(HomeNav.originAfter("study", null, null))
    }

    @Test fun aStartWithoutAViewChangesNothing() {
        // the "reviewDeck" hand-over and old PendingIntents carry no view
        assertEquals(Dest.TEAM, HomeNav.originAfter(null, null, Dest.TEAM))
        assertNull(HomeNav.originAfter(null, "TEAM", null))
        assertEquals(Dest.TEAM, HomeNav.originAfter("nonsense", "BOX", Dest.TEAM))
    }

    @Test fun onlyRealOtherScreensAreOrigins() {
        assertNull(HomeNav.originAfter("battle", "nonsense", null))
        assertNull(HomeNav.originAfter("battle", "STUDY", null))
        assertNull(HomeNav.originAfter("battle", "BATTLE", null))
    }

    // ---------------- Back

    @Test fun backFromTheTallBattleCollapsesToStudy() {
        assertEquals(Back.TO_STUDY, HomeNav.back(parts(869f, HomeView.BATTLE)))
        assertEquals(Back.TO_STUDY, HomeNav.back(parts(560f, HomeView.BATTLE)))
        assertEquals(Back.TO_STUDY, HomeNav.back(parts(1280f, HomeView.BATTLE)))
    }

    @Test fun backFromTheBattleThePokemonCardOpenedReturnsThere() {
        assertEquals(Back.TO_ORIGIN, HomeNav.back(parts(869f, HomeView.BATTLE), Dest.TEAM))
        assertEquals(Back.TO_ORIGIN, HomeNav.back(parts(560f, HomeView.BATTLE), Dest.TEAM))
    }

    @Test fun backFromTheStudyViewIsTheNormalBack() {
        assertEquals(Back.SYSTEM, HomeNav.back(parts(869f, HomeView.STUDY)))
        assertEquals("an origin means nothing in the Study view", Back.SYSTEM, HomeNav.back(parts(869f, HomeView.STUDY), Dest.TEAM))
    }

    @Test fun backInAShortWindowOrPipIsTheNormalBack() {
        // split screen: the battle is all there is, whatever view was asked for
        for (v in HomeView.entries) {
            assertEquals(Back.SYSTEM, HomeNav.back(parts(400f, v)))
            assertEquals(Back.SYSTEM, HomeNav.back(parts(400f, v), Dest.TEAM))
            assertEquals(Back.SYSTEM, HomeNav.back(parts(136f, v, pip = true)))
            assertEquals(Back.SYSTEM, HomeNav.back(parts(869f, v, pip = true), Dest.TEAM))
        }
    }

    @Test fun backWithoutAPlayableGameLeaves() {
        // a game that cannot be played forces the Study view: Back leaves (never "collapses" to the view it is already on)
        assertEquals(Back.SYSTEM, HomeNav.back(HomeLayout.decide(869f, false, HomeView.BATTLE, Status.NEED_DATA)))
        assertEquals(Back.SYSTEM, HomeNav.back(HomeLayout.decide(869f, false, HomeView.BATTLE, Status.SAVE_ERROR), Dest.TEAM))
    }

    // ---------------- StudyReturn (the "Study in AnkiDroid" round trip) in the merged screen

    @Test fun backFromAnkiDroidWithoutPipShowsStudy() {
        val r = StudyReturn()
        r.requested(); r.ankiStarting()
        assertEquals("the activity switches to the Study view", true, r.resumed(inPip = false))
        assertEquals("the hand-over is over: nothing to return to", false, r.fromStudy)
    }

    @Test fun expandedFromPipStaysOnTheBattleWhoseBackGoesToStudy() {
        val r = StudyReturn()
        r.requested(); r.ankiStarting(); r.pipEntered()
        assertEquals("not a way back to Study: the battle is shown", false, r.resumed(inPip = false))
        assertEquals("the activity opens the Battle view while the hand-over is pending", true, r.fromStudy)
        // ... and Back from that Battle view collapses to Study whatever the hand-over says
        assertEquals(Back.TO_STUDY, HomeNav.back(parts(869f, HomeView.BATTLE)))
        assertEquals(true, r.back())
        assertEquals(false, r.fromStudy)
    }

    @Test fun openingAnOwnScreenEndsTheHandOver() {
        val r = StudyReturn()
        r.requested(); r.ankiStarting(); r.ownScreenOpened()
        assertEquals(false, r.resumed(inPip = false))
        assertEquals(false, r.back())
    }

    /** R4 fix B3: a widget / notification / float "BATTLE" start wins over a pending "Study in AnkiDroid" return. */
    @Test fun anExplicitDestinationEndsAPendingHandOver() {
        for (view in HomeView.entries.map { it.key }) {
            val r = StudyReturn()
            r.requested(); r.ankiStarting()
            HomeNav.onIntent(view, r)
            assertEquals("$view: onResume must not switch back to Study", false, r.resumed(inPip = false))
            assertEquals("$view: nothing to return to", false, r.fromStudy)
        }
    }

    @Test fun anIntentWithoutAViewLeavesTheHandOverAlone() {
        // the "reviewDeck" hand-over and old PendingIntents; a nonsense view is no destination
        for (extra in listOf(null, "", "nonsense")) {
            val r = StudyReturn()
            r.requested(); r.ankiStarting()
            HomeNav.onIntent(extra, r)
            assertEquals("$extra", true, r.resumed(inPip = false))
        }
    }

    @Test fun aHandOverSurvivesARecreation() {
        val r = StudyReturn()
        r.requested(); r.ankiStarting()
        val again = StudyReturn().also { it.restore(r.toInts()) }
        assertEquals(true, again.resumed(inPip = false))
    }
}
