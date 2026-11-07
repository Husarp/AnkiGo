package com.husarp.ankigo.game

/** The two picked neutral-nature perks. Carries belong to the individual, never to its team slot. */
object NaturePerks {
    fun description(nature: Nature): String? = when (nature) {
        Nature.HARDY -> "+3% reward XP after a win with the same Main for 6+ completed rounds"
        Nature.DOCILE -> "+4% Bond from counted study answers as the active fighter"
        else -> null
    }

    fun completedRound(battle: Battle, main: Mon) {
        if (battle.natureMainUid != main.uid) {
            battle.natureMainUid = main.uid
            battle.natureMainRounds = 0
        }
        battle.natureMainRounds++
    }

    fun rewardXp(state: GameState, main: Mon, xp: Int): Int {
        val base = Tuning.studyXp(xp, state.practiceRewards)
        val battle = state.battle
        if (main.nature != Nature.HARDY || state.main?.uid != main.uid || battle?.natureMainUid != main.uid || battle.natureMainRounds < 6 || battle.natureMainRounds != battle.rounds ||
            main.level >= Tuning.MAX_LEVEL) return base
        // Apply practice scaling to the bonus before rounding, too.
        val scaled = xp.toLong() * (if (state.practiceRewards) 3 else 6) + main.natureXpCarry
        val bonus = (scaled / 200).toInt()
        main.natureXpCarry = (scaled % 200).toInt()
        return base + bonus
    }
}
