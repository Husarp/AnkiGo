package com.husarp.ankigo.game

/**
 * What happened, in order, for the UI to play back (log line + animation per event).
 * Every event carries the numbers the UI needs, so the panel never has to guess intermediate HP values.
 */
sealed class GameEvent {
    /** An answer was counted. button 0..4, points per Tuning.POINTS. */
    data class Counted(val button: Int, val points: Int, val reviewsToday: Int, val cardStreak: Int, val catchUp: Boolean) : GameEvent()

    /** The answer was not counted (the redo of an answer that was undone in AnkiDroid). */
    data class Ignored(val reason: String) : GameEvent()

    /** The round is not complete yet: [answers] of [of] answers in, [points] so far. */
    data class Charge(val points: Int, val answers: Int, val of: Int) : GameEvent()

    data class Cash(val amount: Int, val total: Int) : GameEvent()
    data class ItemFound(val item: String, val count: Int) : GameEvent()
    data class BadgeEarned(val id: Int, val title: String) : GameEvent()
    data class TrainerLevelUp(val level: Int) : GameEvent()
    data class DayChanged(val day: String) : GameEvent()

    // ---- battle ----
    /** A new wild Pokémon appeared. [willCatch] = what the auto rule will do when it faints. */
    data class Encounter(
        val species: Int, val name: String, val level: Int, val shiny: Boolean, val tier: Tier,
        val hp: Int, val maxHp: Int, val willCatch: Boolean, val newSpecies: Boolean,
    ) : GameEvent()

    /** A round starts. [enemyActs] is false when the multiplier is 1 or more (the wild one uses Splash). */
    data class RoundStart(val multiplier: Double, val points: Int, val enemyActs: Boolean) : GameEvent()

    /**
     * A move was used. [byPlayer] = our main used it. [damage] is the total dealt (0 for status moves / misses).
     * [targetHp]/[targetMaxHp] are the target's HP after the hit; [userHp]/[userMaxHp] after drain/recoil.
     */
    data class Attack(
        val byPlayer: Boolean, val user: String, val target: String, val move: String, val moveType: Type?,
        val category: Category, val damage: Int, val hits: Int, val effectiveness: Double, val crit: Boolean,
        val missed: Boolean, val failed: Boolean,
        val targetHp: Int, val targetMaxHp: Int, val userHp: Int, val userMaxHp: Int,
    ) : GameEvent()

    /** The wild one did nothing because the round went well (Ankimon: Splash). */
    data class Splash(val name: String) : GameEvent()

    /**
     * Side effects: status applied/healed, stat stage change, can't move, burn/poison damage, drain/recoil text.
     * [status] = the affected side's status after this effect ("par" "brn" "psn" "tox" "slp" "frz", null = none).
     */
    data class Effect(val onPlayer: Boolean, val text: String, val hp: Int, val maxHp: Int, val status: String? = null) : GameEvent()

    data class Fainted(val player: Boolean, val name: String) : GameEvent()

    /** A ball was thrown (optional tap). [success] = it caught the wild Pokémon. */
    data class BallThrow(val ball: String, val chance: Int, val success: Boolean, val left: Int) : GameEvent()

    /** The wild Pokémon joined the collection. [newSpecies] = first of its kind (new Pokédex entry). */
    data class Caught(val uid: String, val species: Int, val name: String, val level: Int, val shiny: Boolean, val ball: String?, val newSpecies: Boolean, val toTeam: Boolean) : GameEvent()

    /** The wild Pokémon was defeated; XP follows as XpGained events. */
    data class Defeated(val species: Int, val name: String, val xp: Int) : GameEvent()

    data class XpGained(val uid: String, val name: String, val xp: Int, val level: Int, val xpInLevel: Int, val xpForLevel: Int) : GameEvent()
    data class LevelUp(val uid: String, val name: String, val level: Int, val hp: Int, val maxHp: Int) : GameEvent()
    data class MoveLearned(val uid: String, val name: String, val move: String, val forgot: String?) : GameEvent()
    data class MoveSkipped(val uid: String, val name: String, val move: String) : GameEvent()
    data class Evolved(val uid: String, val from: String, val to: String, val toSpecies: Int) : GameEvent()

    /** Our main fainted: fully healed at once, and this wild Pokémon is lost (Ankimon). */
    data class MainHealed(val uid: String, val name: String, val hp: Int) : GameEvent()

    /** The wild Pokémon left without a result (escape item). */
    data class Fled(val name: String) : GameEvent()

    /** The main changed (team switch). */
    data class MainChanged(val uid: String, val name: String) : GameEvent()

    /** Generic message for item use etc. */
    data class Info(val text: String) : GameEvent()
}

/** Result of a between-battle action (shop, bag, team). */
data class ActionResult(val ok: Boolean, val message: String, val events: List<GameEvent> = emptyList())
