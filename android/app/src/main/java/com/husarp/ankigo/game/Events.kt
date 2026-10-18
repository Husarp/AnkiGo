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

    /** Money paid: [amount] just now, [total] in the wallet afterwards, [kind] = what for (sum the amounts for "earned"). */
    data class Cash(val amount: Int, val total: Int, val kind: CashKind = CashKind.ANSWER) : GameEvent()
    data class ItemFound(val item: String, val count: Int) : GameEvent()
    data class BadgeEarned(val id: Int, val title: String) : GameEvent()

    /** An achievement tier was reached (AchievementTable row [id], [tier] 0..4 = [tierName]); not sent for the one-offs that are Ankimon badges (BadgeEarned says so). */
    data class AchievementReached(val id: String, val tier: Int, val title: String, val tierName: String) : GameEvent()
    data class TrainerLevelUp(val level: Int) : GameEvent()
    data class DayChanged(val day: String) : GameEvent()

    // ---- battle ----
    /**
     * A new wild Pokémon appeared. [willCatch] = what the auto rule will do when it faints. [wildUid] = its uid (round 8: the
     * battle background is picked from it, so one fight keeps one picture; "" = not known).
     */
    data class Encounter(
        val species: Int, val name: String, val level: Int, val shiny: Boolean, val tier: Tier,
        val hp: Int, val maxHp: Int, val willCatch: Boolean, val newSpecies: Boolean,
        val wildUid: String = "",
    ) : GameEvent()

    /** A round starts. [enemyActs] is false when the multiplier is 1 or more (the wild one uses Splash). */
    data class RoundStart(val multiplier: Double, val points: Int, val enemyActs: Boolean) : GameEvent()

    /**
     * A move was used. [byPlayer] = our main used it. [damage] is the total dealt (0 for status moves / misses).
     * [targetHp]/[targetMaxHp] are the target's HP after the hit; [userHp]/[userMaxHp] after drain/recoil.
     * [moveId] (Showdown id, "" = not known) and [power] (base power, 0 = none) pick the move's attack animation (round 7).
     */
    data class Attack(
        val byPlayer: Boolean, val user: String, val target: String, val move: String, val moveType: Type?,
        val category: Category, val damage: Int, val hits: Int, val effectiveness: Double, val crit: Boolean,
        val missed: Boolean, val failed: Boolean,
        val targetHp: Int, val targetMaxHp: Int, val userHp: Int, val userMaxHp: Int,
        val moveId: String = "", val power: Int = 0,
    ) : GameEvent()

    /** The wild one did nothing because the round went well (Ankimon: Splash). */
    data class Splash(val name: String) : GameEvent()

    /**
     * Side effects: status applied/healed, stat stage change, can't move, burn/poison damage, drain/recoil text.
     * [status] = the affected side's status after this effect ("par" "brn" "psn" "tox" "slp" "frz", null = none).
     */
    data class Effect(
        val onPlayer: Boolean, val text: String, val hp: Int, val maxHp: Int, val status: String? = null,
        val kind: EffectKind = EffectKind.OTHER, val stat: String? = null, val stages: Int = 0, val amount: Int = 0,
    ) : GameEvent()

    data class Fainted(val player: Boolean, val name: String) : GameEvent()

    /** A ball was thrown (optional tap). [success] = it caught the wild Pokémon. [number] = which throw of this fight it was (1 = the first; 0 = not known, an event made without a fight). */
    data class BallThrow(val ball: String, val chance: Int, val success: Boolean, val left: Int, val number: Int = 0) : GameEvent()

    /** The wild Pokémon joined the collection. [newSpecies] = first of its kind (new Pokédex entry). */
    data class Caught(val uid: String, val species: Int, val name: String, val level: Int, val shiny: Boolean, val ball: String?, val newSpecies: Boolean, val toTeam: Boolean) : GameEvent()

    /** The wild Pokémon was defeated; XP follows as XpGained events. */
    data class Defeated(val species: Int, val name: String, val xp: Int) : GameEvent()

    /**
     * "Auto-release duplicates" acted on a wild [name] ([level]) of a species you own (best copy [ownedLevel]); comes after
     * the Caught / Defeated event it explains. DEFEATED = not caught; SWAPPED = caught, your [ownedLevel] copy ([releasedName]) was
     * released; KEPT_BOTH = caught, the old copy stayed because it is [keptBecause] (Duplicates.Keep label).
     */
    data class Duplicate(
        val name: String, val level: Int, val ownedLevel: Int, val result: DuplicateResult,
        val releasedName: String? = null, val keptBecause: String? = null,
    ) : GameEvent()

    data class XpGained(val uid: String, val name: String, val xp: Int, val level: Int, val xpInLevel: Int, val xpForLevel: Int) : GameEvent()
    data class LevelUp(val uid: String, val name: String, val level: Int, val hp: Int, val maxHp: Int) : GameEvent()
    data class MoveLearned(val uid: String, val name: String, val move: String, val forgot: String?) : GameEvent()
    data class MoveSkipped(val uid: String, val name: String, val move: String) : GameEvent()
    data class Evolved(val uid: String, val from: String, val to: String, val toSpecies: Int) : GameEvent()

    /** Our main fainted: fully healed at once, and this wild Pokémon is lost (Ankimon). */
    data class MainHealed(val uid: String, val name: String, val hp: Int) : GameEvent()

    /**
     * The MAIN healed by AnkiGo's own healing rules (PLAN 80): [amount] = HP really gained (always more than 0), [hp]/[maxHp]
     * after it. LEVEL_UP comes right after that level's LevelUp (amount = the HP the level added + 25 % of max HP); DEFEAT and
     * CATCH (auto rule or ball) come after the rewards. Bench rest, items and healing moves send no Healed event.
     */
    data class Healed(val uid: String, val name: String, val amount: Int, val hp: Int, val maxHp: Int, val reason: HealReason) : GameEvent()

    /** The wild Pokémon left without a result (escape item). */
    data class Fled(val name: String) : GameEvent()

    /** The main changed (team switch). */
    data class MainChanged(val uid: String, val name: String) : GameEvent()

    /** Generic message for item use etc. */
    data class Info(val text: String) : GameEvent()
}

/** Result of a between-battle action (shop, bag, team). */
data class ActionResult(val ok: Boolean, val message: String, val events: List<GameEvent> = emptyList())

/**
 * What an [GameEvent.Effect] is, so the UI can show a short floating text instead of the log sentence.
 * STAT_CHANGE carries [GameEvent.Effect.stat] (Showdown key atk/def/spa/spd/spe/accuracy/evasion) and the signed
 * [GameEvent.Effect.stages] really applied; STAT_LIMIT is "won't go higher/lower" (stages = the attempted sign).
 * STATUS_CURED carries the cured status key in `stat` ("slp" woke up, "frz" thawed). DRAIN/HEAL/RECOIL/STATUS_DAMAGE carry
 * the HP really gained / lost in [GameEvent.Effect.amount] (always positive). CHARGE = the charge turn of a two-turn move.
 */
enum class EffectKind { OTHER, STATUS_APPLIED, STATUS_CURED, CANT_MOVE, STAT_CHANGE, STAT_LIMIT, DRAIN, RECOIL, HEAL, STATUS_DAMAGE, CHARGE }

/** Why a [GameEvent.Healed] happened: a level gained, a wild Pokémon defeated, or one caught (auto rule or ball). */
enum class HealReason { LEVEL_UP, DEFEAT, CATCH }

/** What a [GameEvent.Cash] was paid for: a counted answer, a defeated wild Pokémon, or the once-a-day daily-goal bonus. */
enum class CashKind { ANSWER, DEFEAT, DAILY_BONUS }

/** What [GameEvent.Duplicate] says happened to the wild one. */
enum class DuplicateResult { DEFEATED, SWAPPED, KEPT_BOTH }
