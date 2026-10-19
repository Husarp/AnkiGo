package com.husarp.ankigo.help

import com.husarp.ankigo.game.Tuning
import com.husarp.ankigo.game.Type
import java.util.Locale

/** Which real in-app element a guide chapter shows (the visual ticket renders the real component for each id). */
enum class GuideVisualId {
    /** The type chips exactly as in the game ([GuideVisual.arg] = a game Type name such as "FIRE"; null = a row of a few types). */
    TYPE_CHIPS,
    HP_BAR,
    XP_BAR,
    /** An item's icon; [GuideVisual.arg] = the item id (always an id of game.Items). */
    ITEM_ICON,
    /** A Pokémon tag; [GuideVisual.arg] = a ui TagKind name ("CAN_EVOLVE", "SHINY", "MAIN"...). */
    TAG,
    /** The compare/Pokédex type matchup table. */
    MATCHUP_TABLE,
    /** The six stat bars of the details screen. */
    STAT_BARS,
    /** "Lv N" in every level-colour tier (bronze to master). */
    LEVEL_COLOURS,
    /** The round status badges (poison, burn, paralysis, sleep, freeze). */
    STATUS_BADGES,
}

data class GuideVisual(val id: GuideVisualId, val arg: String? = null, val caption: String = "")

/** One chapter of "How the game works": a [title], one [intro] sentence, a few short [points], and the [visuals] to show. */
data class GuideChapter(val id: String, val title: String, val intro: String, val points: List<String>, val visuals: List<GuideVisual>)

/**
 * Numbers of the new money and healing rules (PLAN 72, 80). All of them are the Tuning constants (R3-01 money, R3-02 healing).
 */
data class GuideNumbers(
    val cashPerAnswer: Int,
    val cashPerDefeat: Int,
    val dailyCashCap: Int,
    val dailyBonusStart: Int,
    val dailyBonusPerStreakDay: Int,
    val dailyBonusMax: Int,
    val dailyBonusMaxStreakDays: Int,
    val levelUpHealPercent: Int,
    val defeatHealPercent: Int,
    val benchRestPercentPerAnswer: Double,
) {
    companion object {
        /** Money (R3-01) and healing (R3-02) numbers, straight from Tuning. */
        fun pending() = GuideNumbers(
            cashPerAnswer = Tuning.CASH_PER_ANSWER, cashPerDefeat = Tuning.CASH_PER_DEFEAT, dailyCashCap = Tuning.CASH_DAILY_CAP,
            dailyBonusStart = Tuning.DAILY_BONUS_BASE, dailyBonusPerStreakDay = Tuning.DAILY_BONUS_PER_DAY,
            dailyBonusMax = Tuning.DAILY_BONUS_BASE + Tuning.DAILY_BONUS_PER_DAY * Tuning.DAILY_BONUS_MAX_DAYS,
            dailyBonusMaxStreakDays = Tuning.DAILY_BONUS_MAX_DAYS,
            levelUpHealPercent = Tuning.LEVEL_UP_HEAL_PERCENT, defeatHealPercent = Tuning.DEFEAT_HEAL_PERCENT,
            benchRestPercentPerAnswer = 100.0 / Tuning.BENCH_REST_DIVISOR,
        )
    }
}

/**
 * "How the game works" (PLAN 44): eight short chapters the player can jump between. All numbers come from [Tuning], except
 * money and healing, which come from [GuideNumbers.pending]. Pure Kotlin, no Android.
 */
object GameGuide {
    const val BATTLES = "battles-xp"
    const val LEVELS = "levels-evolutions"
    const val TYPES = "types-matchups"
    const val STATS = "stats-ivs-evs-natures"
    const val CATCHING = "catching-balls"
    const val MONEY = "money"
    const val HEALING = "healing"
    const val ITEMS = "items"

    private fun n(d: Double): String = if (d == d.toLong().toDouble()) d.toLong().toString() else String.format(Locale.US, "%.1f", d)
    private fun pct(d: Double): String = n(d * 100)
    /** A factor with up to two decimals, no trailing zeros: 0.35, 1.2, 2. */
    private fun x(d: Double): String = String.format(Locale.US, "%.2f", d).trimEnd('0').trimEnd('.')

    fun chapters(nums: GuideNumbers = GuideNumbers.pending()): List<GuideChapter> = listOf(
        GuideChapter(
            BATTLES, "Battles and XP",
            "Every answer you give is a move in a battle against a wild Pokémon.",
            listOf(
                "A battle round is ${Tuning.CARDS_PER_ROUND} answers, and each answer decides who attacks.",
                "Your Main attacks when the round has a Good or an Easy answer. Good adds ×${x(Tuning.MAIN_POWER[3])} and Easy adds ×${x(Tuning.MAIN_POWER[4])} to its damage (Good + Good = ×${x(2 * Tuning.MAIN_POWER[3])}, Easy + Easy = ×${x(2 * Tuning.MAIN_POWER[4])}).",
                "The wild Pokémon attacks when the round has an Again or a Hard answer. Again gives it ×${x(Tuning.WILD_POWER[1])} and Hard ×${x(Tuning.WILD_POWER[2])} of its normal damage (Again + Again = ×${x(2 * Tuning.WILD_POWER[1])}, Hard + Easy = ×${x(Tuning.WILD_POWER[2])}).",
                "Both can attack in the same round. A round of only Good and Easy answers is safe: the wild Pokémon does nothing.",
                "Switching your Main in the middle of a battle gives the wild Pokémon one free hit on the Pokémon you send in, at the damage of one Again (×${x(Tuning.SWITCH_HIT_POWER)}). It picks its move like in a round, and the answers you already gave stay as they were. This goes for every way of changing the Main (the switch menu, Team, Box, Compare). Replacing a Main that has fainted is free, and so is a switch with no wild Pokémon in the fight.",
                "Move choice (Settings, Game, Battle) is how both Pokémon pick their move, yours and the wild one alike. Smart (the default) uses an attack ${pct(Tuning.SMART_DAMAGE_SHARE)}% of the time when it also has a status move that can still change something, and always when it has none. Smartest also prefers the stronger attack. Random picks any of its moves by chance, as before.",
                "Smart and Smartest never use a status move that would do nothing (a stat already at its limit, a status the target already has or cannot get, healing at full HP) and never an attack the target is immune to unless nothing else is left. Accuracy and evasion stop at ±${Tuning.SMART_ACC_EVA_LIMIT} instead of ±6.",
                "Beat it and your main Pokémon gets XP: its base experience × its level ÷ ${n(Tuning.XP_DIVISOR)}, at most ${Tuning.MAX_LEVELS_PER_GAIN} levels at a time.",
                "An Exp. Share holder gets ${pct(Tuning.XP_SHARE_PART)}% of the XP and the main keeps the other half. A Lucky Egg holder gets ×${n(Tuning.LUCKY_EGG)} XP.",
            ),
            listOf(
                GuideVisual(GuideVisualId.HP_BAR, caption = "HP: when it is empty the Pokémon faints"), GuideVisual(GuideVisualId.XP_BAR, caption = "XP: full means a level up"),
                GuideVisual(GuideVisualId.LEVEL_COLOURS, caption = "Level colour = tier, every 10 levels"),
                GuideVisual(GuideVisualId.STATUS_BADGES, caption = "Status: tap a badge for what it does"),
                GuideVisual(GuideVisualId.TYPE_CHIPS, "FIRE", "Type: see Types"),
            ),
        ),
        GuideChapter(
            LEVELS, "Levels and evolutions",
            "Pokémon grow with XP up to level ${Tuning.MAX_LEVEL}, and some evolve.",
            listOf(
                "Level: some Pokémon evolve when they reach a level.",
                "Friendship: others evolve after a defeat once their friendship is high enough (usually ${Tuning.DEFAULT_MIN_FRIENDSHIP}). Friendship goes up by ${Tuning.FRIENDSHIP_GAIN.first} to ${Tuning.FRIENDSHIP_GAIN.last} with each defeat.",
                "Item: stones and similar items evolve the Pokémon that need them. Open the item in your bag and use it.",
                "A \"Can evolve\" tag shows when a Pokémon is ready. An Everstone in its hold stops evolving.",
                "Evolving heals the Pokémon fully.",
            ),
            listOf(GuideVisual(GuideVisualId.TAG, "CAN_EVOLVE", "Ready to evolve"), GuideVisual(GuideVisualId.ITEM_ICON, "thunder-stone", "An evolution stone"), GuideVisual(GuideVisualId.ITEM_ICON, "everstone", "Holds a Pokémon back")),
        ),
        GuideChapter(
            TYPES, "Types and matchups",
            "There are ${Type.entries.size} types. Each move has one, and so does each Pokémon.",
            listOf(
                "A move that is strong against the target's type does ×2 damage. A weak match does ×0.5.",
                "Some matchups do nothing at all (×0), for example a Normal move against a Ghost.",
                "STAB: a move of the same type as the Pokémon using it does ×${n(Tuning.STAB)} damage.",
                "Pokémon with two types multiply both matchups.",
            ),
            listOf(GuideVisual(GuideVisualId.TYPE_CHIPS, caption = "Type chips as in the game"), GuideVisual(GuideVisualId.MATCHUP_TABLE, caption = "Who beats whom")),
        ),
        GuideChapter(
            STATS, "Stats, IVs, EVs and natures",
            "Two Pokémon of the same kind are never exactly the same.",
            listOf(
                "Six stats: HP, Attack, Defense, Sp. Atk, Sp. Def and Speed.",
                "IVs are the Pokémon's born-in talent. They never change.",
                "EVs grow with every defeat: up to ${Tuning.EV_STAT_CAP} in one stat and ${Tuning.EV_TOTAL_CAP} in total. Macho Brace doubles them. Power items add extra to one stat.",
                "A nature makes one stat 10% higher and another 10% lower (some natures change nothing).",
            ),
            listOf(GuideVisual(GuideVisualId.STAT_BARS, caption = "The six stats"), GuideVisual(GuideVisualId.ITEM_ICON, "macho-brace", "Doubles EV gain")),
        ),
        GuideChapter(
            CATCHING, "Catching and balls",
            "Throw a ball at a wild Pokémon while it still has HP. If it reaches 0 HP, it is caught automatically.",
            listOf(
                "Poké Ball ${Tuning.BALL_CHANCE.getValue("poke-ball")}%, Great Ball ${Tuning.BALL_CHANCE.getValue("great-ball")}%, Ultra Ball ${Tuning.BALL_CHANCE.getValue("ultra-ball")}%, Master Ball ${Tuning.BALL_CHANCE.getValue("master-ball")}% per throw.",
                "Net Ball and Dive Ball add ${Tuning.BALL_TYPE_BONUS} points against the types they like.",
                "A catch counts as a win like a defeat: your trainer XP and defeat counts go up (an auto catch also gives your Pokémon the defeat XP). Only a defeat pays ₽.",
                "When you beat a wild Pokémon without a ball, Settings → Game → \"When you beat a wild Pokémon\" decides: always catch, catch only new species (the default), catch new species and better copies (a higher-level copy replaces your old one), or defeat (only shinies and protected kinds are caught). A defeat pays ${nums.cashPerDefeat} ₽ and the XP; a catch gives the Pokémon and the same XP.",
                "Your wishlist: you can add a Pokémon you have seen but not caught yet (open it in the Pokédex). Wishlisted Pokémon are always caught.",
                "Each wild Pokémon is shiny with a 1 in ${Tuning.SHINY_ODDS} chance.",
            ),
            listOf(
                GuideVisual(GuideVisualId.ITEM_ICON, "poke-ball"), GuideVisual(GuideVisualId.ITEM_ICON, "great-ball"),
                GuideVisual(GuideVisualId.ITEM_ICON, "ultra-ball"), GuideVisual(GuideVisualId.ITEM_ICON, "master-ball"),
                GuideVisual(GuideVisualId.TAG, "SHINY", "Shiny"),
            ),
        ),
        GuideChapter(
            MONEY, "Money and what's worth buying",
            "You earn money by answering and winning. Spend it in the Shop.",
            listOf(
                "+${nums.cashPerAnswer} for every answer, with any button, so a wrong answer never costs you.",
                "+${nums.cashPerDefeat} for defeating a wild Pokémon (not when you catch it).",
                "At most ${nums.dailyCashCap} a day from answers and defeats. A main Pokémon holding an Amulet Coin or a Luck Incense doubles that money (×2).",
                "Reach your daily goal for a bonus of ${nums.dailyBonusStart} ₽, plus ${nums.dailyBonusPerStreakDay} ₽ for every day of your streak before today, up to ${nums.dailyBonusMax} ₽ after ${nums.dailyBonusMaxStreakDays} days in a row. The bonus is not part of the daily limit, and Amulet Coin or Luck Incense does not double it.",
                "The Shop has ${Tuning.SHOP_BASE_SLOTS} items, plus one for every ${Tuning.SHOP_SLOTS_PER_LEVELS} trainer levels. A new offer costs ${Tuning.REROLL_PRICE}; a TM costs ${Tuning.TM_PRICE}.",
                "Poké Balls and Potions are always on sale at their normal price, so you are never stuck without the basics. They are not part of the daily items.",
                "Deal of the day: one item each day costs ${Tuning.DEAL_PERCENT}% less. Buy as many as you like at that price; a new offer doesn't change the deal.",
                "Weekend special: on Saturday and Sunday the Shop also sells one rare thing, an evolution stone or a strong TM (a move with power ${Tuning.WEEKEND_TM_MIN_POWER} or more), at its normal price. It is the same on both days.",
                "A small dot on the Shop tab means a new day's offers and deal are waiting and you haven't looked yet.",
                "Worth buying: evolution items for Pokémon that need them, and better balls when you hunt for a rare Pokémon. You don't need many healing items, because your Pokémon heal on their own (see Healing).",
            ),
            listOf(
                GuideVisual(GuideVisualId.ITEM_ICON, "amulet-coin", "Held: ×2 money"), GuideVisual(GuideVisualId.ITEM_ICON, "luck-incense", "Held: ×2 money"),
                GuideVisual(GuideVisualId.ITEM_ICON, "ultra-ball", "Balls"), GuideVisual(GuideVisualId.ITEM_ICON, "thunder-stone", "Evolution items"),
            ),
        ),
        GuideChapter(
            HEALING, "Healing",
            "Your Pokémon heal as you play, so you rarely need items.",
            listOf(
                "Level up: heals ${nums.levelUpHealPercent}% of max HP, on top of the HP the new level gives.",
                "Defeat or catch: your main heals ${nums.defeatHealPercent}% of max HP.",
                "Resting on the bench: team members that aren't the main heal ${n(nums.benchRestPercentPerAnswer)}% of their max HP with each answer. Partial points add up, so a Pokémon with 40 HP gains 1 HP every 5 answers.",
                "If your main faints, it is healed fully and that wild Pokémon is lost. Evolving heals fully.",
                "Healing items and healing moves still work as before. HP never goes above the maximum.",
            ),
            listOf(GuideVisual(GuideVisualId.HP_BAR, caption = "HP goes up in small steps"), GuideVisual(GuideVisualId.ITEM_ICON, "potion", "Healing items")),
        ),
        GuideChapter(
            ITEMS, "Items",
            "Items come from the Shop and, every so often, as a reward.",
            listOf(
                "A new random item drops after ${Tuning.ITEM_COUNTDOWN.first} to ${Tuning.ITEM_COUNTDOWN.last} answers.",
                "Balls catch Pokémon. Healing items restore HP. Evolution items evolve the Pokémon that need them.",
                "Held items work while a Pokémon holds them: Lucky Egg (×${n(Tuning.LUCKY_EGG)} XP), Everstone (never evolves), Macho Brace (×2 EVs) and more.",
                "Fossils bring back an old Pokémon at level ${Tuning.FOSSIL_LEVEL}.",
                "TMs are never used up: you keep them and can teach their move again.",
            ),
            listOf(
                GuideVisual(GuideVisualId.ITEM_ICON, "potion", "Healing"), GuideVisual(GuideVisualId.ITEM_ICON, "lucky-egg", "Held"),
                GuideVisual(GuideVisualId.ITEM_ICON, "helix-fossil", "Fossil"), GuideVisual(GuideVisualId.TAG, "HELD", "Holding an item"),
            ),
        ),
    )

    fun chapter(id: String): GuideChapter? = chapters().firstOrNull { it.id == id }
}
