package com.husarp.ankigo.ui

/**
 * The words of the ⋯ entry that flips catch <-> defeat for the fight on stage (R6-09, item 24; pure). Catch and defeat give the same
 * XP, so the entries say what really differs: a defeat gives money (₽) and no Pokémon.
 */
object CatchChoiceText {
    /** The menu entry: what a tap will do, given what the auto rule will do now ([willCatch]). */
    fun menuLabel(willCatch: Boolean): String = if (willCatch) "Don't catch this one" else "Catch this one"

    /** The message after the tap; [before] / [after] = [willCatch] before and after the flip. */
    fun toast(before: Boolean, after: Boolean): String = when {
        after != before && after -> "Will catch it"
        after != before -> "Won't catch it: you get ₽ instead"
        after -> "This one is always caught"
        else -> "No change"
    }
}
