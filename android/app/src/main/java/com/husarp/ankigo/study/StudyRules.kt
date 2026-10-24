package com.husarp.ankigo.study

object StudyRules {
    fun gameButton(buttonCount: Int, ease: Int): Int = when (buttonCount) {
        2 -> listOf(1, 3)[ease - 1]
        3 -> listOf(1, 3, 4)[ease - 1]
        else -> ease
    }
    fun buried(row: Map<String, String?>?) = row?.get("queue") in setOf("-2", "-3")
    fun suspended(row: Map<String, String?>?) = row?.get("queue") == "-1"

    /**
     * The card's template name for the study screen's subtitle ("Card 1", "Reverse"): AnkiDroid's `card_name` from the card query
     * ([html], which always asks for it) or from the state query ([state]); "Card N" (N = ord + 1) only when neither has one.
     */
    fun cardName(html: Map<String, String?>?, state: Map<String, String?>?, ord: Int): String =
        listOf(html?.get("card_name"), state?.get("card_name")).firstOrNull { !it.isNullOrBlank() && it != "null" } ?: "Card ${ord + 1}"
    /**
     * PLAN 175: the answer side that was up when the study screen went to the background (a screenshot, another app). The screen
     * reloads on its return (a card may have been answered in AnkiDroid meanwhile); the answer shows again only if the reload finds
     * the SAME card in the SAME state ([before], AnkiDroid's row; an answer anywhere moves its reps). [typed]: a type-in answer's text;
     * [frozenAt]: the timer pill's stopped value (-1 = it runs).
     */
    data class AnswerKeep(val key: StudyLoop.Key, val before: Map<String, String?>, val typed: String, val frozenAt: Long) {
        /**
         * Same card, not answered / rescheduled / buried meanwhile: the scheduling columns agree ([AnswerState.COMPARE]). Every other
         * column may differ: a flag, a move to another deck and an edit of the note (PLAN 246) must not take the answer side away.
         */
        fun same(key: StudyLoop.Key, now: Map<String, String?>): Boolean = key == this.key && before["_id"] != null && AnswerState.same(before, now)
    }

    /** PLAN 246: what the study screen does when the write of a card action is over. */
    enum class AfterAction { ERROR, KEEP_CARD, NEXT_CARD }

    /** A failed write is an error; a flag keeps the card as it is (answer side, buttons); bury and suspend take the card out of the queue, so the next one loads. */
    fun afterAction(action: String, saved: Boolean): AfterAction = when {
        !saved -> AfterAction.ERROR
        action == "flag" -> AfterAction.KEEP_CARD
        else -> AfterAction.NEXT_CARD
    }

    /** PLAN 246: the card's row with its new flag (0 = none), as the screen shows it right after the flag was written. */
    fun withFlag(row: Map<String, String?>, value: Int): Map<String, String?> = row + ("flags" to value.coerceIn(0, 7).toString())

    enum class SpriteSource { ANIMATED, STATIC, PLACEHOLDER }
    fun spriteSource(animated: Boolean, static: Boolean) = when {
        animated -> SpriteSource.ANIMATED
        static -> SpriteSource.STATIC
        else -> SpriteSource.PLACEHOLDER
    }
}
