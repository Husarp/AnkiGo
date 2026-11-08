package com.husarp.ankigo.ui

import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.game.SimpleView

/** A brief live exception to the usual weather rules. */
internal object FieldExplanation {
    fun current(mark: ConditionMarks.Mark): String {
        if (mark.def.kind != ConditionMarks.Kind.WEATHER) return ""
        val data = GameRepo.data ?: return ""
        val state = GameRepo.state ?: return ""
        val battle = state.battle ?: return ""
        val main = state.main ?: return ""
        val view = SimpleView.of(data, battle, main)
        return if (view.weather() == null) "Cloud Nine or Air Lock suppresses this weather." else ""
    }
}
