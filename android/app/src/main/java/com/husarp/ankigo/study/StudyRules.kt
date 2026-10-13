package com.husarp.ankigo.study

object StudyRules {
    fun gameButton(buttonCount: Int, ease: Int): Int = when (buttonCount) {
        2 -> listOf(1, 3)[ease - 1]
        3 -> listOf(1, 3, 4)[ease - 1]
        else -> ease
    }
    fun buried(row: Map<String, String?>?) = row?.get("queue") in setOf("-2", "-3")
    fun suspended(row: Map<String, String?>?) = row?.get("queue") == "-1"
    enum class SpriteSource { ANIMATED, STATIC, PLACEHOLDER }
    fun spriteSource(animated: Boolean, static: Boolean) = when {
        animated -> SpriteSource.ANIMATED
        static -> SpriteSource.STATIC
        else -> SpriteSource.PLACEHOLDER
    }
}
