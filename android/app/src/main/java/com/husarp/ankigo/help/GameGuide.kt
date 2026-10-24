package com.husarp.ankigo.help

import com.husarp.ankigo.game.AchievementTable
import com.husarp.ankigo.game.DailyGift
import com.husarp.ankigo.game.FieldRules
import com.husarp.ankigo.game.MoveText
import com.husarp.ankigo.game.SideMoves
import com.husarp.ankigo.game.Tuning
import com.husarp.ankigo.game.Type
import java.util.Locale
import kotlin.math.roundToInt

/** Which real in-app element a guide chapter shows (the visual ticket renders the real component for each id). */
enum class GuideVisualId {
    /** The type chips exactly as in the game ([GuideVisual.arg] = a game Type name such as "FIRE"; null = a row of a few types). */
    TYPE_CHIPS,
    HP_BAR,
    XP_BAR,
    /** An item's icon; [GuideVisual.arg] = the item id (always an id of game.Items). */
    ITEM_ICON,
    /** A Pokémon mark (drawn as its icon, like the details header); [GuideVisual.arg] = a ui TagKind name ("CAN_EVOLVE", "SHINY", "MAIN"...). */
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
    val cashPerWin: Int,
    val cashTier: Int,
    val giftMaxStreakDays: Int,        // R14-B2-D: the daily gift's chances (percent) on goal-streak day 1 and on day [giftMaxStreakDays] and later
    val giftCommonFirst: Int,
    val giftUncommonFirst: Int,
    val giftRareFirst: Int,
    val giftCommonLast: Int,
    val giftUncommonLast: Int,
    val giftRareLast: Int,
    val masterBallStreakDays: Int,     // the top tier of "Best streak": it gives the Master Ball
    val levelUpHealPercent: Int,
    val defeatHealPercent: Int,
    val benchRestPercentPerAnswer: Double,
) {
    companion object {
        /** Money (R3-01) and healing (R3-02) numbers, straight from Tuning. */
        fun pending() = GuideNumbers(
            cashPerAnswer = Tuning.CASH_PER_ANSWER, cashPerWin = Tuning.CASH_PER_WIN, cashTier = Tuning.CASH_TIER,
            giftMaxStreakDays = Tuning.GIFT_MAX_DAYS,
            giftCommonFirst = DailyGift.chances(1).common.roundToInt(), giftUncommonFirst = DailyGift.chances(1).uncommon.roundToInt(),
            giftRareFirst = DailyGift.chances(1).rare.roundToInt(),
            giftCommonLast = DailyGift.chances(Tuning.GIFT_MAX_DAYS).common.roundToInt(), giftUncommonLast = DailyGift.chances(Tuning.GIFT_MAX_DAYS).uncommon.roundToInt(),
            giftRareLast = DailyGift.chances(Tuning.GIFT_MAX_DAYS).rare.roundToInt(),
            masterBallStreakDays = AchievementTable.row("streak-days")?.tiers?.last()?.toInt() ?: 365,
            levelUpHealPercent = Tuning.LEVEL_UP_HEAL_PERCENT, defeatHealPercent = Tuning.DEFEAT_HEAL_PERCENT,
            benchRestPercentPerAnswer = 100.0 / Tuning.BENCH_REST_DIVISOR,
        )
    }
}

/**
 * "How the game works" (PLAN 44, R17C-10: nine with Moves and effects): short chapters the player can jump between. All numbers come from [Tuning], except
 * money and healing, which come from [GuideNumbers.pending]. Pure Kotlin, no Android.
 */
object GameGuide {
    const val BATTLES = "battles-xp"
    const val MOVES = "moves-effects"
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
                "A switch is not a round. While a card shows, a switch waits for your answer to it (the toast says \"Switching to X on your next move\"). That answer counts as normal; right after it the new Main comes in, and the wild Pokémon attacks it once, at the damage of one Again (×${x(Tuning.SWITCH_HIT_POWER)}). On the Study screen the dot of that answer turns mauve. It picks its move like in a round (it can also use a status move or start a charge); the answers you already gave stay as they were, and your next answer attacks as normal. With no card in front of you (a result is on screen) the switch happens at once. This goes for every way of changing the Main (the switch menu, Team, Box, Compare). Effects that count rounds don't count that attack.",
                "Using an item on your Main during a battle (a healing item or an evolution item) works at once, then the wild Pokémon attacks your Main once, the same way. No answer was made, so the dots of the round don't change. Balls have their own rule (see Catching), and items you give a Pokémon to hold are free.",
                "The wild Pokémon doesn't attack when you replace a Main that has fainted, when there is no wild Pokémon in the fight, or before you have answered a card in this battle.",
                "Move choice (Settings, Game, Battle) is how both Pokémon pick their move, yours and the wild one alike. Smart (the default) uses an attack ${pct(Tuning.SMART_DAMAGE_SHARE)}% of the time when it also has a status move that can still change something, and always when it has none. Smartest also prefers the stronger attack. Random picks any of its moves by chance, as before.",
                "Smart and Smartest never use a status move that would do nothing (a stat already at its limit, a status the target already has or cannot get, healing at full HP, a screen that is already up) and never an attack the target is immune to (by its type or by an ability, such as Wonder Guard). With nothing else to use, your Main uses Struggle and a wild Pokémon uses any move. Accuracy and evasion stop at ±${Tuning.SMART_ACC_EVA_LIMIT} instead of ±6.",
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
            MOVES, "Moves and effects",
            "Your Pokémon and the wild ones use their moves by the same rules, which follow the games.",
            listOf(
                "In the texts of moves and abilities a turn is one round (${Tuning.CARDS_PER_ROUND} answers). Effects that last count down at the end of each round.",
                "Charge moves (Solar Beam, Dig, Fly and a few more) take two moves: the first only charges, and your very next move is the hit, whatever you would have picked. Nobody hides underground or in the sky, so a charging Pokémon can be hit. After a recharge move (Hyper Beam, Giga Impact) hits, your next move is a rest turn.",
                "Flinch: a move that makes the target flinch only works if it moves before the target in that round. The target then loses its move.",
                "Confusion: a confused Pokémon hurts itself 1 time in ${Math.round(1 / Tuning.CONFUSION_SELF_HIT)} instead of moving (a typeless hit of power ${Tuning.CONFUSION_POWER}) and snaps out of it after ${Tuning.CONFUSION_TURNS.first} to ${Tuning.CONFUSION_TURNS.last} tries to move. In love: it can't move ${pct(Tuning.LOVE_STOPS)}% of the time, and it only works between a male and a female.",
                "Protect and the moves like it block everything for the rest of the round, so they only help when the other Pokémon attacks in that round. Used again right after, they often fail. Substitute costs ${MoveText.part(1, SideMoves.SUBSTITUTE_DIVISOR)} your max HP and takes the hits meant for you; it also stops status moves.",
                "Reflect, Light Screen and Aurora Veil halve the damage of physical or special moves for ${SideMoves.SCREEN_TURNS} rounds.",
                "Weather (rain, harsh sun, sandstorm, hail, snow) lasts ${FieldRules.WEATHER_TURNS} rounds and terrain ${FieldRules.TERRAIN_TURNS} rounds. Rain and sun make Water and Fire moves stronger or weaker, and a sandstorm or hail hurts a sixteenth of max HP each round. A terrain gives Pokémon on the ground a boost or a protection.",
                "Binding moves (Wrap, Fire Spin, Whirlpool) hurt the target ${MoveText.part(1, Tuning.BIND_DIVISOR)} its max HP at the end of each round for ${Tuning.BIND_TURNS.first} to ${Tuning.BIND_TURNS.last} rounds. They never stop anyone from switching.",
                "Abilities work in battle for your Pokémon and the wild ones (Intimidate, Static, Levitate, Sturdy and many more). The ability's line in a Pokémon's details says what it does here; some abilities have no effect in AnkiGo, and the details say so.",
                "Some moves are never used because they mean nothing in a one-on-one fight with a wild Pokémon (switching, partner, held-item, PP and critical-hit moves). The move's text says so. A Pokémon that knows only such moves uses Struggle (your Main) or does nothing (a wild one).",
            ),
            listOf(GuideVisual(GuideVisualId.STATUS_BADGES, caption = "Status: tap a badge for what it does")),
        ),
        GuideChapter(
            LEVELS, "Levels and evolutions",
            "Pokémon grow with XP up to level ${Tuning.MAX_LEVEL}, and some evolve.",
            listOf(
                "Level: some Pokémon evolve when they reach a level.",
                "Friendship: others evolve after a win once their friendship is high enough (usually ${Tuning.DEFAULT_MIN_FRIENDSHIP}). Your main's friendship goes up by ${Tuning.FRIENDSHIP_GAIN.first} to ${Tuning.FRIENDSHIP_GAIN.last} with each win, defeat or catch, up to ${Tuning.FRIENDSHIP_MAX}. A Soothe Bell makes it ×${Tuning.SOOTHE_BELL}.",
                "Item: stones and similar items evolve the Pokémon that need them. Open the item in your bag and use it.",
                "A green mark shows when a Pokémon is ready to evolve. An Everstone in its hold stops evolving.",
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
                "EVs grow with every win, defeat or catch, for your main: up to ${Tuning.EV_STAT_CAP} in one stat and ${Tuning.EV_TOTAL_CAP} in total. Macho Brace doubles them. Power items add extra to one stat.",
                "A nature makes one stat 10% higher and another 10% lower (some natures change nothing).",
            ),
            listOf(GuideVisual(GuideVisualId.STAT_BARS, caption = "The six stats"), GuideVisual(GuideVisualId.ITEM_ICON, "macho-brace", "Doubles EV gain")),
        ),
        GuideChapter(
            CATCHING, "Catching and balls",
            "Throw a ball at a wild Pokémon while it still has HP. If it reaches 0 HP, it is caught automatically.",
            listOf(
                "Poké Ball ${Tuning.BALL_CHANCE.getValue("poke-ball")}%, Great Ball ${Tuning.BALL_CHANCE.getValue("great-ball")}%, Ultra Ball ${Tuning.BALL_CHANCE.getValue("ultra-ball")}%, Master Ball ${Tuning.BALL_CHANCE.getValue("master-ball")}% per throw.",
                "A ball is used up whether it works or not. If it fails, the wild Pokémon breaks free and runs away: no XP, and the next wild Pokémon comes. Throwing a ball is not a turn: the wild Pokémon doesn't attack.",
                "The Master Ball can't be bought. You get one for the top tier of the \"Best streak\" achievement (365 days in a row with your daily goal reached).",
                "Balls from Ankimon (Net Ball, Dive Ball, Premier Ball and the others) still work if you have some: each one throws like a Great Ball (and counts as one when you trade balls up in the Bag). They aren't sold or found any more.",
                "A catch counts as a win like a defeat: your trainer XP and defeat counts go up, and your Pokémon get the same XP and you the same ${nums.cashPerWin} coins as for a defeat, with a ball or without.",
                "When you beat a wild Pokémon without a ball, Settings → Game → \"When you beat a wild Pokémon\" decides: always catch, catch only new species (the default), catch new species and better copies (a higher-level copy replaces your old one), or defeat (only shinies and protected kinds are caught). A defeat and a catch both give the XP, the EVs, the friendship and ${nums.cashPerWin} coins; a catch also gives you the Pokémon.",
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
            "You earn money by answering cards and winning battles. Spend it in the Shop.",
            listOf(
                "+${nums.cashPerAnswer} for every answer, with any button, so a wrong answer never costs you.",
                "+${nums.cashPerWin} for every wild Pokémon you defeat or catch (a ball that misses pays nothing).",
                "The more you earn in a day, the slower it comes: in the first ${nums.cashTier} coins every answer pays ${nums.cashPerAnswer} coins and every win ${nums.cashPerWin} coins; in the next ${nums.cashTier} coins half of that (an answer 2.5 coins, paid in whole coins as they add up), then a third, a quarter and so on. It starts again each day.",
                "A main Pokémon holding an Amulet Coin or a Luck Incense doubles the money of your answers and wins (×2), all day long.",
                "Daily gift: the first time you reach your daily goal each day, you get one gift in your Bag. It is not money, so it doesn't touch your money, and it is never a Master Ball.",
                "Which gift depends on your goal streak (days in a row you reached the goal). On day 1 it is common ${nums.giftCommonFirst}%, uncommon ${nums.giftUncommonFirst}% or rare ${nums.giftRareFirst}%. The chances move up day by day, to common ${nums.giftCommonLast}%, uncommon ${nums.giftUncommonLast}% and rare ${nums.giftRareLast}% after ${nums.giftMaxStreakDays} days in a row, and stay there.",
                "Common: ${DailyGift.poolText(Tuning.GIFT_COMMON)}. Uncommon: ${DailyGift.poolText(Tuning.GIFT_UNCOMMON)}. Rare: ${DailyGift.poolText(Tuning.GIFT_RARE_ITEMS)}, an evolution stone or a TM you don't own yet. Inside a kind, every gift is equally likely.",
                "The Shop has ${Tuning.SHOP_BASE_SLOTS} items, plus one for every ${Tuning.SHOP_SLOTS_PER_LEVELS} trainer levels. A new offer costs ${Tuning.REROLL_PRICE}; a TM costs ${Tuning.TM_PRICE}.",
                "Poké Balls and Potions are always on sale at their normal price, so you are never stuck without the basics. They are not part of the daily items.",
                "Balls: a Poké Ball costs ${Tuning.BALL_PRICE.getValue("poke-ball")} coins, a Great Ball ${Tuning.BALL_PRICE.getValue("great-ball")} coins (now and then among the daily items), an Ultra Ball ${String.format(Locale.US, "%,d", Tuning.BALL_PRICE.getValue("ultra-ball"))} coins and it is in the Shop only about one day a week. The Master Ball is never sold: you get one for reaching the top tier of Best streak (${nums.masterBallStreakDays} days in a row).",
                "Deal of the day: one item each day costs ${Tuning.DEAL_PERCENT}% less. Buy as many as you like at that price; a new offer doesn't change the deal.",
                "Weekend special: on Saturday and Sunday the Shop also sells one rare thing, an evolution stone or a strong TM (a move with power ${Tuning.WEEKEND_TM_MIN_POWER} or more), at its normal price. It is the same on both days.",
                "A small dot on the Shop tab means a new day's offers and deal are waiting and you haven't looked yet.",
                "Worth buying: evolution items for Pokémon that need them, and better balls when you hunt for a rare Pokémon. You don't need many healing items, because your Pokémon heal on their own (see Healing).",
            ),
            listOf(
                GuideVisual(GuideVisualId.ITEM_ICON, "amulet-coin", "Main: ×2 money"), GuideVisual(GuideVisualId.ITEM_ICON, "luck-incense", "Main: ×2 money"),
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
                "Healing items still work, and a healing move (Recover, Rest, Moonlight ...) heals the Pokémon that uses it in a battle. HP never goes above the maximum. A Full Restore also cures your main's burn, poison, paralysis, sleep or freeze in a battle; a Max Potion only heals. A healing item on your Main during a battle works at once, then the wild Pokémon attacks once.",
            ),
            listOf(GuideVisual(GuideVisualId.HP_BAR, caption = "HP goes up in small steps"), GuideVisual(GuideVisualId.ITEM_ICON, "potion", "Healing items")),
        ),
        GuideChapter(
            ITEMS, "Items",
            "Items come from the Shop, as random drops, and as the Daily gift when you reach your daily goal.",
            listOf(
                "A new random item drops after ${Tuning.ITEM_COUNTDOWN.first} to ${Tuning.ITEM_COUNTDOWN.last} answers.",
                "Open any item in the Shop or the Bag to read exactly what it does, how to use it and what it means in a fight.",
                "In the Bag you can trade balls up: 5 Poké Balls for 1 Great Ball, and 3 Great Balls for 1 Ultra Ball. Old Ankimon balls count as Great Balls.",
                "Balls catch Pokémon. Healing items restore HP. Evolution items evolve the Pokémon that need them; a few of them (Black Augurite, Peat Block, Metal Alloy) are never sold, you find them as random drops.",
                "Held items work while a Pokémon holds them: Lucky Egg (×${n(Tuning.LUCKY_EGG)} XP), Everstone (never evolves), Macho Brace (×2 EVs), Soothe Bell (×1.5 friendship) and more. A Pokémon holds one item at a time; giving it a new one sends the old one back to the Bag.",
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
