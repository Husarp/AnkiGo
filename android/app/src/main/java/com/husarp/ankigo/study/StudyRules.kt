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
    enum class SpriteSource { ANIMATED, STATIC, PLACEHOLDER }
    fun spriteSource(animated: Boolean, static: Boolean) = when {
        animated -> SpriteSource.ANIMATED
        static -> SpriteSource.STATIC
        else -> SpriteSource.PLACEHOLDER
    }
}
