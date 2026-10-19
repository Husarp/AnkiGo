package com.husarp.ankigo.game

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Round 10 (B22): who attacks in a round, decided per answer (Ankimon summed the points, so Again + Easy made the wild one
 * stay quiet). Buttons: 1 Again, 2 Hard, 3 Good, 4 Easy, 0 unknown (counts as Good).
 * - The wild one attacks when at least one answer is Again or Hard, at [Tuning.WILD_POWER] summed over the round.
 * - Your Main attacks when at least one answer is Good or Easy, with the multiplier [Tuning.MAIN_POWER] summed over the round.
 * Both can attack in one round. Pure Kotlin.
 */
object RoundRule {
    /** [multiplier] scales the Main's damage (only meaningful when [mainActs]); [enemyPower] scales the wild one's (only when [enemyActs]). */
    data class Plan(val mainActs: Boolean, val multiplier: Double, val enemyActs: Boolean, val enemyPower: Double)

    fun plan(buttons: List<Int>): Plan {
        var main = 0.0; var wild = 0.0; var mainActs = false; var enemyActs = false
        for (b in buttons) {
            val i = b.coerceIn(0, 4)
            if (Tuning.MAIN_POWER[i] > 0.0) mainActs = true
            if (Tuning.WILD_POWER[i] > 0.0) enemyActs = true
            main += Tuning.MAIN_POWER[i]; wild += Tuning.WILD_POWER[i]
        }
        return Plan(mainActs, tidy(main), enemyActs, tidy(wild))
    }

    /**
     * The buttons of [answers] answers worth [points] in all, for a battle saved before Round 10 (it kept only the point sum).
     * A single answer is exact; with more, each one is taken as the button whose points are nearest to the average.
     */
    fun restore(answers: Int, points: Int): List<Int> {
        if (answers <= 0) return emptyList()
        val avg = points.toDouble() / answers
        val button = (1..4).minByOrNull { abs(Tuning.POINTS[it] - avg) + if (it == 3) 0.0 else 1e-6 } ?: 3
        return List(answers) { button }
    }

    /** 0.1 + 0.2 style float noise out of the sums (the values are multiples of 0.05). */
    private fun tidy(d: Double) = (d * 1000).roundToInt() / 1000.0
}
