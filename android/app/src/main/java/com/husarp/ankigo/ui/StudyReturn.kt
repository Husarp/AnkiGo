package com.husarp.ankigo.ui

/**
 * Where the battle screen goes after "Study in AnkiDroid" (Study tab → AnkiDroid icon → battle activity → AnkiDroid).
 * Coming back to AnkiGo (not through the PiP window) lands on the Study tab; Back from the battle that was expanded
 * from PiP returns to Study too. Pure logic, the activity only does the navigation.
 */
class StudyReturn {
    /** The battle was brought up by a Study screen to hand over to AnkiDroid: Back leads to Study. */
    var fromStudy = false; private set
    private var ankiStarted = false
    private var pipped = false

    /** The state as small ints, for onSaveInstanceState (a recreated battle activity still returns to Study). */
    fun toInts(): IntArray = intArrayOf(if (fromStudy) 1 else 0, if (ankiStarted) 1 else 0, if (pipped) 1 else 0)
    fun restore(v: IntArray?) {
        if (v == null || v.size < 3) return
        fromStudy = v[0] != 0; ankiStarted = v[1] != 0; pipped = v[2] != 0
    }

    /** A Study screen asked for AnkiDroid (the "reviewDeck" intent). */
    fun requested() { fromStudy = true; ankiStarted = false; pipped = false }
    /** Call right before AnkiDroid is started. */
    fun ankiStarting() { if (fromStudy) ankiStarted = true }
    /** AnkiDroid could not be started: nothing to come back from. */
    fun ankiFailed() { ankiStarted = false }
    /** The battle went into PiP while the hand-over was running. */
    fun pipEntered() { if (fromStudy) pipped = true }
    /** The user opened one of our own screens from the battle: Back is the normal Back again. */
    fun ownScreenOpened() { fromStudy = false; ankiStarted = false; pipped = false }

    /** The activity resumed. True = go to the Study tab now (back from AnkiDroid without the PiP window). */
    fun resumed(inPip: Boolean): Boolean {
        if (inPip || !ankiStarted) return false
        ankiStarted = false
        if (pipped) { pipped = false; return false } // expanded from the PiP window: the battle is fine
        fromStudy = false
        return true
    }

    /** Back was pressed. True = go to the Study tab instead of the normal Back. */
    fun back(): Boolean { val r = fromStudy; fromStudy = false; return r }
}
