package com.husarp.ankigo.ui

import com.husarp.ankigo.game.ActionResult
import com.husarp.ankigo.game.GameEvent
import org.junit.Assert.*
import org.junit.Test

class R29HTmFeedbackTest {
    @Test fun teachingAndReplacementForwardMoveFeedbackWithoutSuccessToast() {
        for (forgot in listOf(null, "Growl")) {
            val events = listOf(GameEvent.MoveLearned("main", "Pikachu", "Thunderbolt", forgot),
                GameEvent.AchievementReached("tms", 0, "TMs taught", "Common"))
            var delivered: List<GameEvent>? = null
            BattleBag.tmResult(ActionResult(true, "Pikachu learned Thunderbolt", events),
                { fail("Successful teaching produced a toast: $it") }, { delivered = it })
            assertSame(events, delivered)
        }
    }

    @Test fun failedTeachingKeepsItsExactErrorAndForwardsEvents() {
        for (message in listOf("You don't own TM Thunderbolt.", "Pokémon not found.",
            "Pikachu can't learn Earthquake.", "Pikachu already knows Thunderbolt.", "Choose a move to forget.")) {
            val notices = mutableListOf<String>()
            var delivered: List<GameEvent>? = null
            val result = ActionResult(false, message)
            BattleBag.tmResult(result, { notices += it }, { delivered = it })
            assertEquals(listOf(message), notices)
            assertSame(result.events, delivered)
        }
    }
}
