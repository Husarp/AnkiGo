package com.husarp.ankigo.game

/** The picked neutral-nature perks. Carries belong to the individual, never to its team slot. */
object NaturePerks {
    fun description(nature: Nature): String? = when (nature) {
        Nature.HARDY -> "+3% reward XP after a win with the same Main for 6+ completed rounds"
        Nature.DOCILE -> "+4% Bond from counted study answers as the active fighter"
        Nature.SERIOUS -> "Damage rolls 90–100% instead of 85–100%, always"
        Nature.BASHFUL -> "+4% XP as the Exp. Share recipient, after split, held-item and Bond bonuses; fractional XP carries"
        Nature.QUIRKY -> "Damage rolls 80–105% instead of 85–100%"
        else -> null
    }

    fun damageRoll(nature: Nature, rng: kotlin.random.Random): Int = when (nature) {
        Nature.SERIOUS -> rng.nextInt(90, 101)
        Nature.QUIRKY -> rng.nextInt(80, 106)
        else -> rng.nextInt(Tuning.ROLL_MIN, 101)
    }

    fun sharedXp(state: GameState, recipient: Mon, xp: Int): Int {
        val base = Tuning.studyXp(xp, state.practiceRewards)
        if (recipient.nature != Nature.BASHFUL || recipient.uid == state.main?.uid ||
            recipient.uid != state.settings.xpShareUid || recipient.level >= Tuning.MAX_LEVEL) return base
        val scaled = xp.toLong() * (if (state.practiceRewards) 4 else 8) + recipient.natureXpCarry
        val bonus = (scaled / 200).toInt()
        recipient.natureXpCarry = (scaled % 200).toInt()
        return base + bonus
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
