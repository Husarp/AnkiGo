package com.husarp.ankigo.ui

/**
 * Where the home screen goes after "Study in AnkiDroid" (Study view → AnkiDroid icon → AnkiDroid). Coming back to AnkiGo
 * (not through the PiP window) lands on the Study view; the battle expanded from PiP shows the Battle view, whose Back
 * returns to Study ([HomeNav.back]). Pure logic, the activity only does the navigation.
 */
class StudyReturn {
    /** The home screen handed a deck over to AnkiDroid (or the in-app study screen did, through it) and the way back is not over yet. */
    var fromStudy = false; private set
    private var ankiStarted = false
    private var pipped = false

    /** The state as small ints, for onSaveInstanceState (a recreated battle activity still returns to Study). */
    fun toInts(): IntArray = intArrayOf(if (fromStudy) 1 else 0, if (ankiStarted) 1 else 0, if (pipped) 1 else 0)
    fun restore(v: IntArray?) {
        if (v == null || v.size < 3) return
        fromStudy = v[0] != 0; ankiStarted = v[1] != 0; pipped = v[2] != 0
    }

    /** The Study view (or the in-app study screen, by the "reviewDeck" intent) asked for AnkiDroid. */
    fun requested() { fromStudy = true; ankiStarted = false; pipped = false }
    /** Call right before AnkiDroid is started. */
    fun ankiStarting() { if (fromStudy) ankiStarted = true }
    /** AnkiDroid could not be started: nothing to come back from. */
    fun ankiFailed() { ankiStarted = false }
    /** The battle went into PiP while the hand-over was running. */
    fun pipEntered() { if (fromStudy) pipped = true }
    /** The user opened one of our own screens from the battle: Back is the normal Back again. */
    fun ownScreenOpened() { fromStudy = false; ankiStarted = false; pipped = false }

    /** The activity resumed. True = show the Study view now (back from AnkiDroid without the PiP window). */
    fun resumed(inPip: Boolean): Boolean {
        if (inPip || !ankiStarted) return false
        ankiStarted = false
        if (pipped) { pipped = false; return false } // expanded from the PiP window: the battle is fine
        fromStudy = false
        return true
    }

    /** Back was pressed: the hand-over is over. True = it was still pending. */
    fun back(): Boolean { val r = fromStudy; fromStudy = false; return r }
}
