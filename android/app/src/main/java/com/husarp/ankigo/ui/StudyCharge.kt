package com.husarp.ankigo.ui

import com.husarp.ankigo.game.GameEvent

/** Presentation state in event order. It never reads or changes scheduling, rewards or the battle save. */
internal class StudyCharge {
    private val choices = ArrayList<Int>()
    private var complete = false
    private var awaitingCharge = false
    var goal: Int = 2
        private set
    /** 0 = counted, but exact button unknown (catch-up or a restored multi-answer tally). */
    val filled: List<Int> get() = choices.toList()

    fun observe(event: GameEvent, roundSize: Int) {
        when (event) {
            is GameEvent.Counted -> {
                if (complete) { choices.clear(); complete = false }
                awaitingCharge = true
                goal = roundSize.coerceIn(1, 4)
                if (choices.size < goal) choices += event.button.coerceIn(0, 4)
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
            else -> {}
        }
    }

    fun restore(pending: Int, points: Int, roundSize: Int) {
        choices.clear(); complete = false; awaitingCharge = false
        goal = roundSize.coerceIn(1, 4)
        val button = if (pending != 1) 0 else when (points) { 0 -> 1; 5 -> 2; 10 -> 3; 20 -> 4; else -> 0 }
        repeat(pending.coerceIn(0, goal)) { choices += button }
    }
}
