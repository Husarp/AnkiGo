package com.husarp.ankigo.ui

import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.game.*

/** Read the current battle at tap time, including switches and ability suppression. */
internal object FieldExplanation {
    fun current(mark: ConditionMarks.Mark): String {
        if (FieldIcons.of(mark.key) == null) return ""
        val data = GameRepo.data ?: return ""
        val state = GameRepo.state ?: return ""
        val battle = state.battle ?: return ""
        val main = state.main ?: return ""
        val view = SimpleView.of(data, battle, main)
        val text = if (mark.slot == ConditionMarks.Slot.FIELD) FieldRules.explain(mark.key, view) else {
            val id = if (mark.key == "mp.fs") "futuresight" else mark.key
            val move = data.move(id) ?: return ""
            val who = if (mark.slot == ConditionMarks.Slot.OURS) "Your ${main.name}'s side" else "The wild ${battle.wild.name}'s side"
            "$who: " + MoveText.describe(move)
        }
        return if (text.isBlank()) "" else ". $text"
    }
}
