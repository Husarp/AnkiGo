package com.husarp.ankigo.ui

import com.husarp.ankigo.game.GameEvent
import com.husarp.ankigo.game.Tuning

/** Presentation state in event order. It never reads or changes scheduling, rewards or the battle save. */
internal class StudyCharge {
    private val choices = ArrayList<Int>()
    private var complete = false
    private var awaitingCharge = false
    var goal: Int = 2
        private set
    /** 0 = counted, but exact button unknown (catch-up or a restored multi-answer tally); [OK] = the neutral OK of a new card (grey). */
    val filled: List<Int> get() = choices.toList()

    fun observe(event: GameEvent, roundSize: Int) {
        when (event) {
            is GameEvent.Counted -> {
                // R19-T (PLAN 284): the OK of a new card is one of the round's answers: it fills a dot, in grey (it has no power)
                if (complete) { choices.clear(); complete = false }
                awaitingCharge = true
                goal = roundSize.coerceIn(1, 4)
                if (choices.size < goal) choices += if (event.neutral) OK else event.button.coerceIn(0, 4)
            }
            is GameEvent.Charge -> { awaitingCharge = false; goal = event.of.coerceIn(1, 4) }
            is GameEvent.RoundStart -> { awaitingCharge = false; complete = true }
            is GameEvent.Encounter -> {
                // Game.ensureEncounter may follow Counted before Charge on the first answer of a battle.
                val current = if (awaitingCharge) choices.lastOrNull() else null
                choices.clear(); complete = false
                if (current != null) choices += current
            }
            is GameEvent.Fled -> { choices.clear(); complete = false; awaitingCharge = false }
            // R19-T (PLAN 288): a switch (MainChanged, queued or not) takes no answer slot and marks no dot: R18-B's mauve switch dot is gone.
            else -> {}
        }
    }

    /** Presentation only: the round has spent its dots; the next Counted still starts a fresh round. */
    fun consume() { choices.clear() }

    companion object {
        /** The "button" value of the grey dot of an OK answer ([Tuning.BUTTON_OK]); 1 to 4 are the answer buttons, 0 = unknown. */
        const val OK = Tuning.BUTTON_OK
    }

    /**
     * The dots from a saved battle: [pending] answers so far, [points] their sum, [buttons] = the saved buttons of those answers. With the exact buttons
     * (a save of R19-T and later, [buttons] as long as [pending]) every dot is exact, the grey OK too; without them (an older save) one pending
     * answer is read from its points and more stay unknown.
     */
    fun restore(pending: Int, points: Int, roundSize: Int, buttons: List<Int> = emptyList()) {
        choices.clear(); complete = false; awaitingCharge = false
        goal = roundSize.coerceIn(1, 4)
        if (pending > 0 && buttons.size == pending) {
            buttons.take(goal).mapTo(choices) { it.coerceIn(0, OK) }
            return
        }
        val button = if (pending != 1) 0 else when (points) { 0 -> 1; 5 -> 2; 10 -> 3; 20 -> 4; else -> 0 }
        repeat(pending.coerceIn(0, goal)) { choices += button }
    }
}
