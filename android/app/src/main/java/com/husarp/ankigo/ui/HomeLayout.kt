package com.husarp.ankigo.ui

import com.husarp.ankigo.GameRepo

/**
 * The two faces of the home screen ([GameActivity]): the Study view (the deck list) and the full-screen Battle. [key]
 * travels in the Intent extra [EXTRA_VIEW] (see [Nav]).
 */
enum class HomeView(val key: String) {
    STUDY("study"), BATTLE("battle");

    companion object {
        const val EXTRA_VIEW = "homeView"
        /** The screen the battle was opened from (a [Dest] name, only with the Battle view): Back returns there ([HomeNav.back]). */
        const val EXTRA_FROM = "homeFrom"
        /** "Pop out" from the Pokémon tab's battle card: the home screen pops the battle out once it is ready (its view stays as it is). */
        const val EXTRA_POP_OUT = "homePopOut"
        /** The view named by [key] (an Intent extra or a saved state), null when there is none or it is unknown. */
        fun of(key: String?): HomeView? = entries.firstOrNull { it.key == key }
    }
}

/**
 * Which parts of the home screen show (PLAN 88 + 108, as Adam picked it): pure decision table, no Android. [GameActivity]
 * applies the answer on every layout pass, so the rules live here and are tested on the JVM.
 *
 * - Tall window (>= [TALL_DP], not picture-in-picture): the requested view.
 *   STUDY = Study app bar + deck list + bottom bar, nothing else (the battle card lives in the Pokémon tab). Only while the
 *   game cannot be played does a status slot sit between the bar and the list ("Set up the game", "Setup isn't finished",
 *   the save-error card). BATTLE = back arrow + "Battle" + Pop out + ⋯, the battle filling the window, no bottom bar
 *   (+ the Continue bar, which the activity adds when a deck is due).
 * - Short window (split-screen half, landscape phone) and picture-in-picture: the battle only, whatever the view
 *   (no bars), exactly as before the merge.
 * - A game that cannot be played (first run not done, save error) has no battle to show: the Study view comes whatever
 *   view was asked for. Loading shows no slot in the Study view, "Loading…" where the battle would be otherwise.
 *   That holds in a short window too (not in picture-in-picture): there is no battle to be "the only thing", so the
 *   Study layout stays (app bar, status slot, deck list, bottom bar) and the deck list scrolls; studying without the
 *   game must stay possible in a split-screen half or a landscape phone.
 */
object HomeLayout {
    /** The window height (dp) from which the bars show: the old "bottom bar fits" rule. */
    const val TALL_DP = 560f

    /** What fills the area of the battle. */
    enum class Card {
        /** The battle's area fills the content area (Battle view, short window, picture-in-picture). */
        FULL,
        /** Nothing: the Study view of a game that plays (or is still loading). */
        NONE,
        /** A status slot, as tall as its content, above the deck list (Study view while the game cannot be played). */
        SLOT,
    }

    data class Parts(
        /** The view that is really shown: the asked one, STUDY when the game cannot be played, BATTLE in a short window. */
        val view: HomeView,
        val tall: Boolean,
        /** Study app bar: title, AnkiDroid pill, Sync, New deck. */
        val studyBar: Boolean,
        /** The "Battle" top bar with Back, Pop out and the menu. */
        val battleBar: Boolean,
        val deckList: Boolean,
        /** The bottom bar (Study view only: the full-screen battle has none). */
        val bottomBar: Boolean,
        val card: Card,
        /** The panel's own Pop out / Stats / menu buttons: on in short windows, off where the top bar has them. */
        val footerTools: Boolean,
    )

    fun isTall(heightDp: Float, inPip: Boolean) = heightDp >= TALL_DP && !inPip

    /** The game cannot be played and has nothing to battle with: first run not done, or the save could not be read. */
    fun needsSetup(status: GameRepo.Status) =
        status == GameRepo.Status.NEED_DATA || status == GameRepo.Status.NEED_STARTER || status == GameRepo.Status.SAVE_ERROR

    /** The parts for a window [heightDp] dp tall showing [view], with the game in [status]. */
    fun decide(heightDp: Float, inPip: Boolean, view: HomeView, status: GameRepo.Status): Parts {
        if (!isTall(heightDp, inPip)) {
            // a short window without a playable battle keeps the (scrolling) Study layout; PiP is too small for it
            if (!inPip && needsSetup(status)) {
                return Parts(HomeView.STUDY, tall = false, studyBar = true, battleBar = false, deckList = true, bottomBar = true,
                    card = Card.SLOT, footerTools = true)
            }
            return Parts(HomeView.BATTLE, tall = false, studyBar = false, battleBar = false, deckList = false, bottomBar = false,
                card = Card.FULL, footerTools = true)
        }
        val shown = if (needsSetup(status)) HomeView.STUDY else view
        return if (shown == HomeView.STUDY) {
            Parts(HomeView.STUDY, tall = true, studyBar = true, battleBar = false, deckList = true, bottomBar = true,
                card = if (status == GameRepo.Status.READY || status == GameRepo.Status.LOADING) Card.NONE else Card.SLOT, footerTools = true)
        } else {
            Parts(HomeView.BATTLE, tall = true, studyBar = false, battleBar = true, deckList = false, bottomBar = false,
                card = Card.FULL, footerTools = false)
        }
    }

    /** What the battle's area holds while there is no panel. */
    enum class Slot {
        /** Nothing (the game plays, or the first run is about to start by itself). */
        NOTHING,
        /** "Studying without the game" with its "Set up the game" button. */
        SETUP_ROW,
        /** "Setup isn't finished" after the first run was canceled, with "Continue setup". */
        SETUP_CARD,
        /** "Your save couldn't be read" with Restore / Start a new game. */
        SAVE_ERROR,
        /** The game is loading (or the first run is starting): a "Loading…" message where the battle would be; no slot in the Study view. */
        LOADING,
    }

    /** What the slot / the battle's area shows for a game [status]; [studyOnly] = "Study without the game" was chosen, [firstRunCanceled] = the first run was left. */
    fun slot(status: GameRepo.Status, studyOnly: Boolean, firstRunCanceled: Boolean): Slot = when (status) {
        GameRepo.Status.READY -> Slot.NOTHING
        GameRepo.Status.LOADING -> Slot.LOADING
        GameRepo.Status.NEED_DATA, GameRepo.Status.NEED_STARTER -> when {
            studyOnly -> Slot.SETUP_ROW
            firstRunCanceled -> Slot.SETUP_CARD
            else -> Slot.LOADING
        }
        GameRepo.Status.SAVE_ERROR -> Slot.SAVE_ERROR
    }
}

/** Where the home screen goes on an Intent and on Back (pure, tested on the JVM). */
object HomeNav {
    /** The view an Intent asks for: its extra, else the one already shown, else Study (the launcher start). */
    fun viewFromIntent(extra: String?, current: HomeView?): HomeView = HomeView.of(extra) ?: current ?: HomeView.STUDY

    /**
     * An Intent that arrives while the home screen is open: one that names a view (widget, reminder, floating window, PiP "Open",
     * a tab) is an explicit destination and ends a pending "Study in AnkiDroid" hand-over, so the resume that follows does not
     * switch back to Study. One without a view (the "reviewDeck" hand-over, old PendingIntents) leaves the hand-over alone.
     */
    fun onIntent(viewExtra: String?, studyReturn: StudyReturn) {
        if (HomeView.of(viewExtra) != null) studyReturn.ownScreenOpened()
    }

    /**
     * The screen the battle was opened from, after an Intent: one that names a view sets it (the Battle view keeps the
     * [fromExtra] screen, Study has none); one without a view (the "reviewDeck" hand-over, old PendingIntents) changes nothing.
     */
    fun originAfter(viewExtra: String?, fromExtra: String?, current: Dest?): Dest? = when (HomeView.of(viewExtra)) {
        null -> current
        HomeView.STUDY -> null
        HomeView.BATTLE -> Dest.entries.firstOrNull { it.name == fromExtra && it != Dest.STUDY && it != Dest.BATTLE }
    }

    enum class Back {
        /** The full-screen battle collapses to the Study view. */
        TO_STUDY,
        /** The full-screen battle was opened from another screen (the Pokémon tab's battle card): back to it. */
        TO_ORIGIN,
        /** The normal Back: leave the home screen (it is the end of the line, Back from any tab ends here first). */
        SYSTEM,
    }

    /**
     * What Back does. Only the Battle view of a tall window has somewhere to go: where it was opened from ([origin], the
     * Pokémon tab when its battle card opened it), else the Study view. The Study view itself, a short window and
     * picture-in-picture use the normal Back. A "Study in AnkiDroid" hand-over ([StudyReturn]) needs no rule of its own:
     * it ends in the Battle view, which has no origin, so Back goes to Study.
     */
    fun back(parts: HomeLayout.Parts, origin: Dest? = null): Back = when {
        !(parts.tall && parts.view == HomeView.BATTLE) -> Back.SYSTEM
        origin != null -> Back.TO_ORIGIN
        else -> Back.TO_STUDY
    }
}
