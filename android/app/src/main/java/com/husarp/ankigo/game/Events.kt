package com.husarp.ankigo.game

/**
 * What happened, in order, for the UI to play back (log line + animation per event).
 * Every event carries the numbers the UI needs, so the panel never has to guess intermediate HP values.
 */
sealed class GameEvent {
    /** An answer was counted. button 0..4, points per Tuning.POINTS. [neutral] (PLAN 155): the OK of a new card, no round and no combo change. */
    data class Counted(val button: Int, val points: Int, val reviewsToday: Int, val cardStreak: Int, val catchUp: Boolean, val neutral: Boolean = false) : GameEvent()

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

    /**
     * R14-B2-D (PLAN 125 / R10 X3): the daily gift, sent once a day when the daily goal is reached (it replaces the old money bonus). Exactly one of
     * [item] (an item id, [count] of it, already in the Bag) and [tm] (a TM's move id, already in the TMs) is set. [streakDays] = the goal streak
     * today, which sets the chances of the rarer tiers.
     */
    data class DailyGift(val item: String?, val tm: String?, val count: Int, val streakDays: Int) : GameEvent()

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

    /**
     * A round starts (RoundRule). [multiplier] scales the Main's damage and [mainActs] says whether it attacks at all (a round
     * without a Good or Easy answer: no); [enemyActs] says whether the wild one attacks (a round without an Again or Hard
     * answer: no, it uses Splash), at [enemyPower] of its normal damage. [points] = the answers' point sum.
     */
    data class RoundStart(
        val multiplier: Double, val points: Int, val enemyActs: Boolean,
        val mainActs: Boolean = true, val enemyPower: Double = if (enemyActs) 1.0 else 0.0,
    ) : GameEvent()

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
        /** R17C-1: the short float text the emitter chose ("Confused", "Protected", "Rain", "Seeded"); null = none (the kind decides). */
        val label: String? = null,
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

    /**
     * [xp] gained; [level] / [xpInLevel] / [xpForLevel] are AFTER the gain. PLAN 138 + 182: [beforeInLevel] = the XP inside the old level
     * before the gain (-1 = not recorded) and [crossedNeeds] = the XP each level the gain grew needed (the old level's first, one entry per
     * level gained; empty = no level), so a bar can be played as "fill to the end, restart, ..." from where it really stood.
     */
    data class XpGained(
        val uid: String, val name: String, val xp: Int, val level: Int, val xpInLevel: Int, val xpForLevel: Int,
        val beforeInLevel: Int = -1, val crossedNeeds: List<Int> = emptyList(),
    ) : GameEvent()
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

    /**
     * Round 10 (W10): comes right after a [MainChanged] in a fight. The wild one [wildName] attacks the incoming Pokémon [monName]
     * ONCE (the switch took one turn, one dot); the Attack / Effect / Fainted events of that one action follow. [hp] / [maxHp] = the
     * Pokémon's HP BEFORE the hit, so a panel can show it at that value until the hit lands (the saved game already has the HP after
     * it). R13 Part C: [item] set = not a switch but that item used on the Main in a fight (it took the turn); no [MainChanged] then.
     */
    data class SwitchHit(val wildName: String, val monName: String, val hp: Int, val maxHp: Int, val item: String? = null) : GameEvent()

    /**
     * PLAN 206: a switch was made while a card shows: [uid] ([name]) comes in right after that card is answered (the card you saw is
     * fought by the Pokémon that is out); then [MainChanged] (and the wild one's [SwitchHit]) follow, with that answer's events. Plays
     * no animation: the action bar says "Switching after this card…". [uid] null = the queue was cancelled (the main was picked again):
     * [name] is the Pokémon that stays out.
     */
    data class SwitchQueued(val uid: String?, val name: String) : GameEvent()

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
 * R17C-1: VOLATILE = a lasting condition starts / ends on one side ([GameEvent.Effect.stat] = its key, [GameEvent.Effect.stages] = +1 start / -1 end);
 * FIELD = weather, terrain, a room or a side condition starts / ends (stat = key, stages +1 / -1); RESIDUAL = HP lost to a lasting effect (amount);
 * BLOCKED = a move was stopped by protection, an ability or an immunity (stat = key). VOLATILE / FIELD / BLOCKED carry the float text in
 * [GameEvent.Effect.label]. CANT_MOVE's `stat` also takes "flinch" "attract" "recharge" "confusion" "truant" besides the status keys.
 */
enum class EffectKind { OTHER, STATUS_APPLIED, STATUS_CURED, CANT_MOVE, STAT_CHANGE, STAT_LIMIT, DRAIN, RECOIL, HEAL, STATUS_DAMAGE, CHARGE, VOLATILE, FIELD, RESIDUAL, BLOCKED }

/** Why a [GameEvent.Healed] happened: a level gained, a wild Pokémon defeated, or one caught (auto rule or ball). */
enum class HealReason { LEVEL_UP, DEFEAT, CATCH }

/**
 * What a [GameEvent.Cash] was paid for: a counted answer or a won fight ([DEFEAT]: a wild Pokémon defeated or caught, R13 Part C).
 * [DAILY_BONUS] is the old once-a-day goal bonus: R14-B2-D replaced it with [GameEvent.DailyGift], nothing sends it any more; the value stays
 * so old logs and saved text that name it still read.
 */
enum class CashKind { ANSWER, DEFEAT, DAILY_BONUS }

/** What [GameEvent.Duplicate] says happened to the wild one. */
enum class DuplicateResult { DEFEATED, SWAPPED, KEPT_BOTH }
