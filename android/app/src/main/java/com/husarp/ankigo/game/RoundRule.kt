package com.husarp.ankigo.game

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Round 10 (B22): who attacks in a round, decided per answer (Ankimon summed the points, so Again + Easy made the wild one
 * stay quiet). Buttons: 1 Again, 2 Hard, 3 Good, 4 Easy, 0 unknown (counts as Good).
 * - The wild one attacks when at least one answer is Again or Hard, at [Tuning.WILD_POWER] summed over the round.
 * - Your Main attacks when at least one answer is Good or Easy, with the multiplier [Tuning.MAIN_POWER] summed over the round.
 * R23: two consecutive Agains boost wild Attack once per streak; three rounds without Good/Easy force the strongest pick.
 * OK pauses both streaks. Hard contributes 75% wild power.
 * Both can attack in one round. R19-T (PLAN 284): the OK of a new card ([Tuning.BUTTON_OK]) is one of the round's answers with no power at
 * all: only the other answer counts, and OK + OK is an empty round (nobody attacks). Pure Kotlin.
 */
object RoundRule {
    /** [multiplier] scales the Main's damage (only meaningful when [mainActs]); [enemyPower] scales the wild one's (only when [enemyActs]). */
    data class Plan(val mainActs: Boolean, val multiplier: Double, val enemyActs: Boolean, val enemyPower: Double)

    fun plan(buttons: List<Int>): Plan {
        var main = 0.0; var wild = 0.0; var mainActs = false; var enemyActs = false
        for (b in buttons) {
            if (b == Tuning.BUTTON_OK) continue   // R19-T (PLAN 284): a neutral move: neither side gains any power from it
            val i = b.coerceIn(0, 4)
            if (Tuning.MAIN_POWER[i] > 0.0) mainActs = true
            if (Tuning.WILD_POWER[i] > 0.0) enemyActs = true
            main += Tuning.MAIN_POWER[i]; wild += Tuning.WILD_POWER[i]
        }
        return Plan(mainActs, tidy(main), enemyActs, tidy(wild))
    }

    /** OK is transparent; every other non-Again answer starts a fresh streak. */
    fun answerBoost(battle: Battle, button: Int): Boolean {
        if (button == Tuning.BUTTON_OK) return false
        battle.againStreak = if (button == 1) (battle.againStreak + 1).coerceAtMost(Tuning.WILD_AGAIN_STREAK + 1) else 0
        return battle.againStreak == Tuning.WILD_AGAIN_STREAK
    }

    /** One forced strongest pick each third eligible round. Unknown answers count as Good. */
    fun strongestRound(battle: Battle, buttons: List<Int>): Boolean {
        val rated = buttons.filter { it != Tuning.BUTTON_OK }
        if (rated.isEmpty()) return false
        if (plan(rated).mainActs) { battle.pressuredRounds = 0; return false }
        battle.pressuredRounds++
        if (battle.pressuredRounds < Tuning.WILD_STRONGEST_ROUNDS) return false
        battle.pressuredRounds = 0
        return true
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
