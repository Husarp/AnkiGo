package com.husarp.ankigo.game

/** Stage factors start from an unchanged stat; later changes add to the current stage, capped at ±6. */
object StageText {
    private val stats = listOf("1", "1.5", "2", "2.5", "3", "3.5", "4")
    private val drops = listOf("1", "2/3", "1/2", "2/5", "1/3", "2/7", "1/4")
    private val aim = listOf("1", "4/3", "5/3", "2", "7/3", "8/3", "3")
    private val aimDrops = listOf("1", "3/4", "3/5", "1/2", "3/7", "3/8", "1/3")

    fun factor(stage: Int, accuracy: Boolean = false): String {
        val n = stage.coerceIn(-6, 6)
        return (if (accuracy) { if (n < 0) aimDrops else aim } else { if (n < 0) drops else stats })[kotlin.math.abs(n)]
    }

    fun amount(stage: Int, accuracy: Boolean = false): String =
        "${kotlin.math.abs(stage)} stage${if (kotlin.math.abs(stage) == 1) "" else "s"} (×${factor(stage, accuracy)})"

    /** Rule sets also describe stat changes; keep their short explanations consistent with ordinary boosts. */
    fun explain(text: String): String = Regex("by ([1-6])(?![0-9]| stage)").replace(text) { m ->
        val before = text.take(m.range.first)
        val verb = Regex("(?i)\\b(raises?|lowers?|lowered|falls|rise)\\b").findAll(before).lastOrNull()
        val clause = verb?.let { before.substring(it.range.first) }.orEmpty()
        if (verb == null || !Regex("(?i)Attack|Defense|Sp\\. Atk|Sp\\. Def|Speed|accuracy|evasiveness|stats?|them").containsMatchIn(clause + before.takeLast(30))) m.value
        else {
            val down = Regex("(?i)lower|falls").containsMatchIn(verb.value)
            "by " + amount(m.groupValues[1].toInt() * if (down) -1 else 1, Regex("(?i)accuracy|evasiveness").containsMatchIn(clause + before.takeLast(30)))
        }
    }

    val table: String = "Stat stages add up from 0 and stop at −6 and +6; switching out resets them. Factors below start from stage 0. " +
        "Attack, Defense, Sp. Atk, Sp. Def and Speed: " + (1..6).joinToString("; ") { "+$it ×${factor(it)}, −$it ×${factor(-it)}" } + ". " +
        "Accuracy and evasion: " + (1..6).joinToString("; ") { "+$it ×${factor(it, true)}, −$it ×${factor(-it, true)}" } +
        ". Subtract the target's evasion stage from its accuracy stage, clamp the result to −6…+6, then multiply accuracy by that stage's factor. For example, +2 accuracy against +1 evasion gives ×4/3. Smart move choice stops raising accuracy or evasion at ±${Tuning.SMART_ACC_EVA_LIMIT}."
}
